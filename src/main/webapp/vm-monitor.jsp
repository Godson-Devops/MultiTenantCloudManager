<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%
  if (session.getAttribute("user_id") == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  String vmId = request.getParameter("id");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>VM Monitoring &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white1">
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body>
<header class="topbar">
  <h1>VM Monitoring</h1>
  <nav class="tabs">
    <a href="vm-dashboard.jsp" class="active">VMs</a>
    <a href="pod-dashboard.jsp">Pods</a>
    <span style="color:var(--muted);align-self:center;margin-left:10px">
      <%= session.getAttribute("user_id") %> &middot;
      <a href="#" onclick="document.getElementById('logoutForm').submit()">Logout</a>
    </span>
  </nav>
</header>

<form id="logoutForm" method="post" action="logout" style="display:none"></form>

<main>
  <div class="action-row" style="margin-bottom:16px">
    <a class="back-link" href="vm-details.jsp?id=<%= vmId == null ? "" : java.net.URLEncoder.encode(vmId, "UTF-8") %>">Back to VM details</a>
  </div>

  <div id="msg" class="msg hidden"></div>

  <div class="grid-2">
    <div class="card">
      <div class="card-head">
        <div class="titles">
          <h2>MEMORY</h2>
          <span class="sub">RAM used vs total</span>
        </div>
      </div>
      <div class="chart-box"><canvas id="memoryChart"></canvas></div>
    </div>
    <div class="card">
      <div class="card-head">
        <div class="titles">
          <h2>CPU UTILIZATION</h2>
          <span class="sub">Percentage of compute in use</span>
        </div>
      </div>
      <div class="chart-box"><canvas id="cpuChart"></canvas></div>
    </div>
    <div class="card">
      <div class="card-head">
        <div class="titles">
          <h2>DISK IO &amp; SPACE</h2>
          <span class="sub">Root filesystem usage</span>
        </div>
      </div>
      <div class="chart-box"><canvas id="diskChart"></canvas></div>
    </div>
    <div class="card">
      <div class="card-head">
        <div class="titles">
          <h2>NETWORK TRAFFIC</h2>
          <span class="sub">Receive + transmit bytes per second</span>
        </div>
      </div>
      <div class="chart-box"><canvas id="networkChart"></canvas></div>
    </div>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  const VM_ID = <%= vmId == null ? "null" : "\"" + vmId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ").replace("\r", " ") + "\"" %>;
  const charts = {};
  const COLORS = { memory: '#7c5cfb', cpu: '#059669', disk: '#d97706', network: '#d946ef' };

  function render(id, series, label, color, unit) {
    const box = document.getElementById(id);
    const chart = Portal.toChart(series, unit === 'bytes');

    if (chart.empty) {
      Portal.noData(box);
      if (charts[id]) { charts[id].destroy(); delete charts[id]; }
      return;
    }
    Portal.clearNoData(box);

    if (charts[id]) charts[id].destroy();
    charts[id] = new Chart(document.getElementById(id), {
      type: 'line',
      data: {
        labels: chart.labels,
        datasets: [{
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
        plugins: { legend: { labels: { color: '#17132b' } } },
        scales: {
          x: { ticks: { color: '#6b7280', maxTicksLimit: 6 }, grid: { color: 'rgba(23,19,43,.07)' } },
          y: { ticks: { color: '#6b7280' }, grid: { color: 'rgba(23,19,43,.07)' } }
        }
      }
    });
  }

  async function refresh() {
    if (!VM_ID) {
      Portal.show('msg', 'No VM id supplied', 'err');
      return;
    }
    const { status, body } = await Portal.get('vm/metrics?id=' + encodeURIComponent(VM_ID));

    if (status === 401) { window.location.href = 'login.jsp'; return; }
    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not load metrics', 'err');
      return;
    }
    if (body.error) {
      Portal.show('msg', 'Metrics are currently unavailable. Panels will show "No data".', 'err');
    } else {
      Portal.hide('msg');
    }

    render('memoryChart', body.memory, 'Memory', COLORS.memory, 'bytes');
    render('cpuChart', body.cpu, 'CPU %', COLORS.cpu, 'none');
    render('diskChart', body.disk, 'Disk', COLORS.disk, 'bytes');
    render('networkChart', body.network, 'Network', COLORS.network, 'none');
  }

  refresh();
  setInterval(refresh, 30000);
</script>
</body>
</html>
