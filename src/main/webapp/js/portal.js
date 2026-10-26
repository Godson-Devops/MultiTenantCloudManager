var Portal = (function () {

  var STATUS = Object.freeze({
    CREATING: 'creating', RUNNING: 'running', STOPPED: 'stopped',
    ERROR: 'error', DELETED: 'deleted'
  });
  var KNOWN_STATUS = new Set(['creating', 'running', 'stopped', 'error', 'deleted']);
  var ACTION_LABELS = {
    start: 'Starting', stop: 'Stopping', restart: 'Restarting', delete: 'Deleting'
  };

  var panel = null;
  var panelListenerReady = false;
  var freshnessTimers = {};

  function csrfToken() {
    var meta = document.querySelector('meta[name="csrf-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function toResult(res) {
    return res.json().catch(function () { return {}; }).then(function (body) {
      return { status: res.status, body: body, ok: res.ok };
    });
  }

  function fetchTimeout(url, options, timeoutMs) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, timeoutMs);
    options.signal = controller.signal;
    return fetch(url, options).finally(function () { clearTimeout(timer); });
  }

  function post(url, data) {
    var headers = {
      'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
      'Accept': 'application/json'
    };
    var token = csrfToken();
    if (token) headers['X-CSRF-Token'] = token;
    return fetchTimeout(url, {
      method: 'POST',
      headers: headers,
      body: new URLSearchParams(data).toString()
    }, 90000).then(toResult);
  }

  function get(url) {
    return fetchTimeout(url, {
      headers: { 'Accept': 'application/json' },
      cache: 'no-store'
    }, 20000).then(toResult);
  }

  function show(id, text, kind) {
    var el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.className = 'msg ' + kind;
    el.setAttribute('role', kind === 'err' ? 'alert' : 'status');
    el.setAttribute('aria-live', kind === 'err' ? 'assertive' : 'polite');
  }

  function hide(id) {
    var el = document.getElementById(id);
    if (el) el.className = 'msg hidden';
  }

  function esc(value) {
    if (value === null || value === undefined || value === '') return '&mdash;';
    var div = document.createElement('div');
    div.textContent = String(value);
    return div.innerHTML;
  }

  function badge(status) {
    var code = status === null || status === undefined ? '' : String(status);
    var cls = KNOWN_STATUS.has(code) ? code : STATUS.STOPPED;
    return '<span class="badge ' + cls + '">' + (esc(code) || 'unknown') + '</span>';
  }

  function formatBytes(bytes) {
    var value = Number(bytes);
    if (!Number.isFinite(value) || value <= 0) return '';
    if (value >= 1024 * 1024 * 1024) return (value / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
    if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(0) + ' MB';
    return Math.round(value / 1024) + ' KB';
  }

  function normalizePodName(value) {
    return String(value || '')
      .toLowerCase()
      .replace(/[^a-z0-9-]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 63)
      .replace(/-+$/g, '');
  }

  function actionLabel(action) {
    return ACTION_LABELS[action] || action;
  }

  function toast(message, kind) {
    if (!message) return;
    var container = document.getElementById('portalToasts');
    if (!container) {
      container = document.createElement('div');
      container.id = 'portalToasts';
      container.className = 'toast-stack';
      document.body.appendChild(container);
    }
    var item = document.createElement('div');
    item.className = 'portal-toast ' + (kind || 'ok');
    item.setAttribute('role', 'status');
    item.setAttribute('aria-live', 'polite');
    item.textContent = message;
    container.appendChild(item);
    setTimeout(function () { item.remove(); }, 5000);
  }

  function busy(button, label) {
    if (!button) return;
    if (!button.dataset.originalHtml) button.dataset.originalHtml = button.innerHTML;
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.innerHTML = '<span class="spinner"></span> ' + esc(label || 'Working...');
  }

  function resetBusy(button) {
    if (!button) return;
    button.disabled = false;
    button.removeAttribute('aria-busy');
    if (button.dataset.originalHtml) {
      button.innerHTML = button.dataset.originalHtml;
      delete button.dataset.originalHtml;
    }
  }

  function inPanel() {
    try {
      return window.parent !== window && window.frameElement
        && window.frameElement.classList.contains('panel-frame');
    } catch (e) {
      return false;
    }
  }

  function panelOpen() {
    return panel !== null;
  }

  function withPopup(url) {
    return url + (url.indexOf('?') === -1 ? '?' : '&') + 'popup=1';
  }

  function refreshPage() {
    if (typeof window.portalRefresh === 'function') {
      window.portalRefresh();
    } else {
      window.location.reload();
    }
  }

  function destroyPanel() {
    if (!panel) return;
    var focusTarget = panel.lastFocus;
    panel.overlay.remove();
    panel = null;
    document.body.classList.remove('panel-open');
    if (focusTarget && focusTarget.isConnected) focusTarget.focus();
  }

  function closePanel(refresh, message, fallbackUrl) {
    if (inPanel()) {
      try {
        window.parent.postMessage({
          type: 'portal-panel-close',
          refresh: !!refresh,
          message: message || null
        }, window.location.origin);
        return;
      } catch (e) {
        /* fall through to local fallback */
      }
    }
    if (fallbackUrl) {
      window.location.href = fallbackUrl;
    } else if (refresh) {
      refreshPage();
    }
  }

  function ensurePanelListener() {
    if (panelListenerReady) return;
    panelListenerReady = true;
    window.addEventListener('message', function (event) {
      if (event.origin !== window.location.origin) return;
      var data = event.data || {};
      if (data.type !== 'portal-panel-close') return;
      destroyPanel();
      if (data.refresh) refreshPage();
      if (data.message) toast(data.message, 'ok');
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && panel) destroyPanel();
    });
  }

  function openMini(url, width, height) {
    if (inPanel()) {
      window.location.href = withPopup(url);
      return false;
    }

    ensurePanelListener();
    destroyPanel();

    var overlay = document.createElement('div');
    overlay.className = 'panel-overlay';

    var box = document.createElement('div');
    box.className = 'panel-box';
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-modal', 'true');
    box.setAttribute('aria-label', 'Resource panel');
    if (width) box.style.maxWidth = width + 'px';

    var bar = document.createElement('div');
    bar.className = 'panel-bar';

    var title = document.createElement('strong');
    title.textContent = 'Cloud Portal';

    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'panel-close';
    close.setAttribute('aria-label', 'Close panel');
    close.textContent = 'Close';
    close.addEventListener('click', destroyPanel);

    var frame = document.createElement('iframe');
    frame.className = 'panel-frame';
    frame.title = 'Resource details';
    frame.src = withPopup(url);

    bar.appendChild(title);
    bar.appendChild(close);
    box.appendChild(bar);
    box.appendChild(frame);
    overlay.appendChild(box);
    document.body.appendChild(overlay);
    document.body.classList.add('panel-open');

    panel = { overlay: overlay, lastFocus: document.activeElement };
    close.focus();

    overlay.addEventListener('mousedown', function (event) {
      if (event.target === overlay) destroyPanel();
    });

    return false;
  }

  function confirmAction(options) {
    options = options || {};
    return new Promise(function (resolve) {
      var overlay = document.createElement('div');
      overlay.className = 'confirm-overlay';

      var box = document.createElement('div');
      box.className = 'confirm-box';
      box.setAttribute('role', 'alertdialog');
      box.setAttribute('aria-modal', 'true');

      var title = document.createElement('h3');
      title.textContent = options.title || 'Please confirm';
      var text = document.createElement('p');
      text.textContent = options.message || 'Continue with this action?';

      var row = document.createElement('div');
      row.className = 'action-row confirm-actions';

      var cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'secondary';
      cancel.textContent = options.cancelLabel || 'Cancel';

      var ok = document.createElement('button');
      ok.type = 'button';
      ok.className = options.danger ? 'danger' : '';
      ok.textContent = options.confirmLabel || 'Confirm';

      function finish(result) {
        overlay.remove();
        document.removeEventListener('keydown', onKey);
        resolve(result);
      }

      function onKey(event) {
        if (event.key === 'Escape') finish(false);
      }

      cancel.addEventListener('click', function () { finish(false); });
      ok.addEventListener('click', function () { finish(true); });
      overlay.addEventListener('mousedown', function (event) {
        if (event.target === overlay) finish(false);
      });
      document.addEventListener('keydown', onKey);

      row.appendChild(cancel);
      row.appendChild(ok);
      box.appendChild(title);
      box.appendChild(text);
      box.appendChild(row);
      overlay.appendChild(box);
      document.body.appendChild(overlay);
      cancel.focus();
    });
  }

  function copy(value) {
    var text = value === null || value === undefined ? '' : String(value);
    if (!text || text === '-' || text === '\u2014') return;
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text)
        .then(function () { toast('Copied to clipboard.', 'ok'); })
        .catch(function () { fallbackCopy(text); });
    } else {
      fallbackCopy(text);
    }
  }

  function fallbackCopy(text) {
    var area = document.createElement('textarea');
    area.value = text;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try {
      document.execCommand('copy');
      toast('Copied to clipboard.', 'ok');
    } catch (e) {
      toast('Could not copy automatically.', 'err');
    }
    area.remove();
  }

  function freshness(id) {
    var el = document.getElementById(id);
    if (!el) return;
    var updatedAt = Date.now();
    if (freshnessTimers[id]) clearInterval(freshnessTimers[id]);

    function render() {
      var seconds = Math.max(0, Math.round((Date.now() - updatedAt) / 1000));
      el.textContent = seconds < 5 ? 'Updated just now'
        : 'Updated ' + (seconds < 60 ? seconds + 's ago' : Math.round(seconds / 60) + 'm ago');
    }

    render();
    freshnessTimers[id] = setInterval(render, 5000);
  }

  function poll(fn, intervalMs) {
    var stopped = false;

    function run(force) {
      if (stopped) return Promise.resolve();
      if (!force && (document.hidden || panelOpen())) return Promise.resolve();
      try {
        return Promise.resolve(fn());
      } catch (e) {
        return Promise.reject(e);
      }
    }

    run(false);
    var timer = setInterval(function () { run(false); }, intervalMs);
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) run(false);
    });

    return {
      refresh: function () { return run(true); },
      stop: function () {
        stopped = true;
        clearInterval(timer);
      }
    };
  }


  var DIALOG_PAGES = {
    'vm-create.jsp': [900, 780],
    'vm-details.jsp': [1000, 780],
    'vm-monitor.jsp': [1100, 820],
    'pod-create.jsp': [820, 720],
    'pod-details.jsp': [1000, 780],
    'pod-monitor.jsp': [1100, 820],
    'pod-logs.jsp': [1000, 760]
  };

  function dialogPage(url) {
    var clean = String(url || '').split('#')[0].split('?')[0];
    var file = clean.substring(clean.lastIndexOf('/') + 1);
    return DIALOG_PAGES[file] || null;
  }

  function installDialogLinks() {
    document.addEventListener('click', function (event) {
      if (event.defaultPrevented || event.button !== 0
          || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
      if (!link || link.hasAttribute('download')) return;
      if (link.target && link.target !== '_self' && link.target !== '') return;
      if (link.getAttribute('data-panel') === 'off') return;

      var href = link.getAttribute('href');
      var size = dialogPage(href);
      if (!size) return;

      event.preventDefault();
      var width = Number(link.getAttribute('data-panel-width')) || size[0];
      var height = Number(link.getAttribute('data-panel-height')) || size[1];
      openMini(href, width, height);
    });
  }

  function toChart(series, scale) {
    if (!Array.isArray(series) || series.length === 0) {
      return { labels: [], data: [], empty: true };
    }
    var labels = series.map(function (p) { return new Date(p[0] * 1000).toLocaleTimeString(); });
    var data = series.map(function (p) { return scale ? p[1] / (1024 * 1024) : p[1]; });
    return { labels: labels, data: data, empty: false };
  }

  function noData(target) {
    var canvas = typeof target === 'string' ? document.getElementById(target) : target;
    if (!canvas || !canvas.parentElement) return;
    var box = canvas.parentElement;
    var msg = box.querySelector('.no-data');
    if (!msg) {
      msg = document.createElement('div');
      msg.className = 'no-data';
      msg.style.cssText =
        'position:absolute;inset:0;display:flex;align-items:center;justify-content:center;' +
        'color:var(--muted);font-size:13px;';
      box.appendChild(msg);
    }
    msg.textContent = 'No data';
  }

  function clearNoData(target) {
    var canvas = typeof target === 'string' ? document.getElementById(target) : target;
    if (!canvas || !canvas.parentElement) return;
    var msg = canvas.parentElement.querySelector('.no-data');
    if (msg) msg.remove();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installDialogLinks);
  } else {
    installDialogLinks();
  }

  return {
    STATUS: STATUS,
    post: post,
    get: get,
    show: show,
    hide: hide,
    openMini: openMini,
    closePanel: closePanel,
    inPanel: inPanel,
    panelOpen: panelOpen,
    confirmAction: confirmAction,
    toast: toast,
    busy: busy,
    resetBusy: resetBusy,
    copy: copy,
    freshness: freshness,
    poll: poll,
    esc: esc,
    badge: badge,
    formatBytes: formatBytes,
    normalizePodName: normalizePodName,
    actionLabel: actionLabel,
    toChart: toChart,
    noData: noData,
    clearNoData: clearNoData
  };
})();
