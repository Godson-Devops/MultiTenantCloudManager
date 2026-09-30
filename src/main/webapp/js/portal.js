/* Shared helpers for the portal JSPs. */
const Portal = (() => {

  async function post(url, data) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams(data).toString()
    });
    return { status: res.status, body: await res.json().catch(() => ({})), ok: res.ok };
  }

  async function get(url) {
    const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
    return { status: res.status, body: await res.json().catch(() => ({})), ok: res.ok };
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
    const known = ['running', 'creating', 'stopped', 'error', 'deleted'];
    const cls = known.includes(status) ? status : 'stopped';
    const label = status || 'unknown';
    return `<span class="badge ${cls}">${label}</span>`;
  }

  function esc(value) {
    if (value === null || value === undefined || value === '') return '&mdash;';
    const div = document.createElement('div');
    div.textContent = String(value);
    return div.innerHTML;
  }

  /**
   * Converts a Prometheus [[ts, value], ...] series into Chart.js labels and
   * data, scaling bytes to MiB when the series looks like a memory reading.
   */
  function toChart(series, scale) {
    if (!Array.isArray(series) || series.length === 0) {
      return { labels: [], data: [], empty: true };
    }
    const labels = series.map(p => new Date(p[0] * 1000).toLocaleTimeString());
    const data = series.map(p => scale ? p[1] / (1024 * 1024) : p[1]);
    return { labels, data, empty: false };
  }

  /** Renders the "No data" placeholder a chart panel expects. */
  function noData(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
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

  function clearNoData(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;
    const msg = canvas.parentElement.querySelector('.no-data');
    if (msg) msg.remove();
  }

  return { post, get, show, hide, badge, esc, toChart, noData, clearNoData };
})();
