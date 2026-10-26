<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object podMonitorUser = session.getAttribute("user_id");
  if (podMonitorUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  String podId = request.getParameter("id");
  boolean popup = "1".equals(request.getParameter("popup"));
  String fromAdmin = "admin".equals(request.getParameter("from")) ? "&from=admin" : "";
  request.setAttribute("pageHeading", "Pod Monitoring");
  request.setAttribute("activeTab", "pods");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>Pod Monitoring &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body data-current-user="<%= com.portal.util.Html.escape(podMonitorUser) %>">
<% if (!popup) { %>
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>
<% } %>

<main>
  <div class="action-row" style="margin-bottom:16px">
    <a class="back-link" data-panel="off" href="pod-details.jsp?id=<%= podId == null ? "" : java.net.URLEncoder.encode(podId, "UTF-8") %><%= fromAdmin %><%= popup ? "&popup=1" : "" %>">Back to pod details</a>
  </div>

  <div class="toolbar">
    <div class="fields">
      <label>RANGE
        <select id="range">
          <option value="15m">Last 15 minutes</option>
          <option value="1h" selected>Last hour</option>
          <option value="6h">Last 6 hours</option>
          <option value="24h">Last 24 hours</option>
        </select>
      </label>
    </div>
    <div class="action-row">
      <span id="freshness" class="freshness"></span>
      <button type="button" class="secondary" id="pauseBtn">Pause</button>
      <button type="button" class="secondary" id="refreshBtn">Refresh</button>
    </div>
  </div>

  <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

  <div class="grid-2">
    <div class="card">
      <div class="card-head">
        <div class="titles">
          <h2>MEMORY USAGE</h2>
          <span class="sub">Total pod memory across containers</span>
        </div>
      </div>
      <div class="chart-box"><canvas id="memoryChart"></canvas></div>
    </div>
    <div class="card">
      <div class="card-head">
        <div class="titles">
          <h2>CPU UTILIZATION</h2>
          <span class="sub">Aggregate CPU cores used</span>
        </div>
      </div>
      <div class="chart-box"><canvas id="cpuChart"></canvas></div>
    </div>
  </div>

  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>POD NETWORK</h2>
        <span class="sub">Transmit + receive throughput</span>
      </div>
    </div>
    <div class="chart-box"><canvas id="networkChart"></canvas></div>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  const POD_ID = <%= podId == null ? "null" : "\"" + podId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ").replace("\r", " ") + "\"" %>;
  const charts = {};
  const COLORS = { memory: '#7c5cfb', cpu: '#059669', network: '#d946ef' };
  let loading = false;
  let paused = false;

  function render(id, series, label, color, unit, yTitle) {
    const box = document.getElementById(id);
    const chart = Portal.toChart(series, unit === 'bytes');
    if (unit === 'network') {
      chart.data = chart.data.map(function (v) { return v / 1024; });
    }

    if (chart.empty) {
      Portal.noData(box);
      if (charts[id]) { charts[id].destroy(); delete charts[id]; }
      return;
    }
    Portal.clearNoData(box);

    if (charts[id]) {
      charts[id].data.labels = chart.labels;
      charts[id].data.datasets[0].data = chart.data;
      charts[id].update('none');
      return;
    }

    charts[id] = new Chart(document.getElementById(id), {
      type: 'line',
      data: {
        labels: chart.labels,
        datasets: [{
          label: label,
          data: chart.data,
          borderColor: color,
          backgroundColor: color + '22',
          fill: true,
          tension: .3,
          pointRadius: 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: { legend: { labels: { color: '#17132b' } } },
        scales: {
          x: {
            title: { display: true, text: 'Time', color: '#6b7280' },
            ticks: { color: '#6b7280', maxTicksLimit: 6 },
            grid: { color: 'rgba(23,19,43,.07)' }
          },
          y: {
            title: { display: true, text: yTitle || '', color: '#6b7280' },
            ticks: { color: '#6b7280' },
            grid: { color: 'rgba(23,19,43,.07)' }
          }
        }
      }
    });
  }

  async function refresh(manual) {
    if (loading || (!manual && (document.hidden || paused))) return;
    if (!POD_ID) {
      Portal.show('msg', 'No pod id supplied.', 'err');
      return;
    }

    loading = true;
    if (manual) document.getElementById('refreshBtn').disabled = true;
    try {
      const range = document.getElementById('range').value;
      const { status, body } = await Portal.get('pod/metrics?id=' + encodeURIComponent(POD_ID)
        + '&range=' + encodeURIComponent(range));

      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || 'Could not load metrics.', 'err');
        return;
      }
      if (body.error) {
        Portal.show('msg', 'Metrics are currently unavailable. Panels show "No data".', 'warn');
      } else {
        Portal.hide('msg');
      }

      render('memoryChart', body.memory, 'Memory', COLORS.memory, 'bytes', 'MiB');
      render('cpuChart', body.cpu, 'CPU', COLORS.cpu, 'none', 'cores');
      render('networkChart', body.network, 'Network', COLORS.network, 'network', 'KiB/s');
      Portal.freshness('freshness');
    } catch (e) {
      Portal.show('msg', 'Could not reach the server. These charts may be out of date.', 'err');
    } finally {
      loading = false;
      document.getElementById('refreshBtn').disabled = false;
    }
  }

  let poller = Portal.poll(function () { return refresh(false); }, 30000);

  document.getElementById('range').addEventListener('change', function () { refresh(true); });
  document.getElementById('refreshBtn').addEventListener('click', function () { refresh(true); });
  document.getElementById('pauseBtn').addEventListener('click', function () {
    paused = !paused;
    if (paused) {
      poller.stop();
      this.textContent = 'Resume';
      Portal.show('msg', 'Auto-refresh paused. Use Refresh to update the charts.', 'warn');
    } else {
      Portal.hide('msg');
      this.textContent = 'Pause';
      poller = Portal.poll(function () { return refresh(false); }, 30000);
    }
  });
</script>
</body>
</html>
