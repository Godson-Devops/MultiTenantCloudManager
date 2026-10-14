var Portal = (function () {

  var STATUS = Object.freeze({
    CREATING: 'creating', RUNNING: 'running', STOPPED: 'stopped',
    ERROR: 'error', DELETED: 'deleted'
  });
  var KNOWN_STATUS = new Set(['creating', 'running', 'stopped', 'error', 'deleted']);

  function toResult(res) {
    return res.json().catch(function () { return {}; }).then(function (body) {
      return { status: res.status, body: body, ok: res.ok };
    });
  }

  function post(url, data) {
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams(data).toString()
    }).then(toResult);
  }

  function get(url) {
    return fetch(url, { headers: { 'Accept': 'application/json' } }).then(toResult);
  }

  function show(id, text, kind) {
    var el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.className = 'msg ' + kind;
  }

  function hide(id) {
    var el = document.getElementById(id);
    if (el) el.className = 'msg hidden';
  }

  function badge(status) {
    var cls = KNOWN_STATUS.has(status) ? status : STATUS.STOPPED;
    return '<span class="badge ' + cls + '">' + (esc(status) || 'unknown') + '</span>';
  }

  function esc(value) {
    if (value === null || value === undefined || value === '') return '&mdash;';
    var div = document.createElement('div');
    div.textContent = String(value);
    return div.innerHTML;
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

  return {
    STATUS: STATUS, post: post, get: get, show: show, hide: hide,
    badge: badge, esc: esc, toChart: toChart, noData: noData, clearNoData: clearNoData
  };
})();
