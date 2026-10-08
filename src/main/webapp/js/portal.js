const Portal = (function () {

  const STATUS = Object.freeze({
    CREATING: 'creating', RUNNING: 'running', STOPPED: 'stopped',
    ERROR: 'error', DELETED: 'deleted'
  });
  const KNOWN_STATUS = new Set(Object.values(STATUS));
  async function post(url, data) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams(data).toString()
    });
    return { status: res.status, body: await res.json().catch(function () { return {}; }), ok: res.ok };
  }

  async function get(url) {
    const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
    return { status: res.status, body: await res.json().catch(function () { return {}; }), ok: res.ok };
  }

  function show(id, text, kind) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text;
    el.className = 'msg ' + kind;
  }

  function hide(id) {
    const el = document.getElementById(id);
    if (el) el.className = 'msg hidden';
  }

  function badge(status) {
    const cls = KNOWN_STATUS.has(status) ? status : STATUS.STOPPED;
    return `<span class="badge ${cls}">${esc(status) || 'unknown'}</span>`;
  }

  function esc(value) {
    if (value === null || value === undefined || value === '') return '&mdash;';
    const div = document.createElement('div');
    div.textContent = String(value);
    return div.innerHTML;
  }

  function toChart(series, scale) {
    if (!Array.isArray(series) || series.length === 0) {
      return { labels: [], data: [], empty: true };
    }
    const labels = series.map(function (p) { return new Date(p[0] * 1000).toLocaleTimeString(); });
    const data = series.map(function (p) { return scale ? p[1] / (1024 * 1024) : p[1]; });
    return { labels, data, empty: false };
  }

  function noData(target) {
    const canvas = typeof target === 'string' ? document.getElementById(target) : target;
    if (!canvas || !canvas.parentElement) return;
    const box = canvas.parentElement;
    let msg = box.querySelector('.no-data');
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
    const canvas = typeof target === 'string' ? document.getElementById(target) : target;
    if (!canvas || !canvas.parentElement) return;
    const msg = canvas.parentElement.querySelector('.no-data');
    if (msg) msg.remove();
  }

  return { STATUS, post, get, show, hide, badge, esc, toChart, noData, clearNoData };
})();
