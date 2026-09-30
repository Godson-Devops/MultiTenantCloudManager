<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%
  if (session.getAttribute("user_id") == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  String podId = request.getParameter("id");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Pod Monitoring &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css">
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body>
<header class="topbar">
  <h1>Pod Monitoring</h1>
  <nav class="tabs">
    <a href="vm-dashboard.jsp">VMs</a>
    <a href="pod-dashboard.jsp" class="active">Pods</a>
    <span style="color:var(--muted);align-self:center;margin-left:10px">
      <%= session.getAttribute("user_id") %> &middot;
      <a href="#" onclick="document.getElementById('logoutForm').submit()">Logout</a>
    </span>
  </nav>
</header>

<form id="logoutForm" method="post" action="logout" style="display:none"></form>

<main>
  <div style="margin-bottom:14px">
    <a href="pod-dashboard.jsp">&larr; Back to dashboard</a>
  </div>

  <div id="msg" class="msg hidden"></div>

  <div class="grid-2">
    <div class="card">
      <h2>MEMORY USAGE</h2>
      <div class="chart-box"><canvas id="memoryChart"></canvas></div>
    </div>
    <div class="card">
      <h2>CPU UTILIZATION</h2>
      <div class="chart-box"><canvas id="cpuChart"></canvas></div>
    </div>
  </div>

  <div class="card">
    <h2>POD NETWORK</h2>
    <div class="chart-box"><canvas id="networkChart"></canvas></div>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  const POD_ID = <%= podId == null ? "null" : "\"" + podId.replace("\"", "") + "\"" %>;
  const charts = {};
  const COLORS = { memory: '#38bdf8', cpu: '#4ade80', network: '#c084fc' };

  function render(id, series, color, unit) {
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
        plugins: { legend: { labels: { color: '#e2e8f0' } } },
        scales: {
          x: { ticks: { color: '#94a3b8', maxTicksLimit: 6 }, grid: { color: '#33415555' } },
          y: { ticks: { color: '#94a3b8' }, grid: { color: '#33415555' } }
        }
      }
    });
  }

  async function refresh() {
    if (!POD_ID) {
      Portal.show('msg', 'No pod id supplied', 'err');
      return;
    }
    const { status, body } = await Portal.get('pod/metrics?id=' + encodeURIComponent(POD_ID));

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

    render('memoryChart', body.memory, COLORS.memory, 'bytes');
    render('cpuChart', body.cpu, COLORS.cpu, 'none');
    render('networkChart', body.network, COLORS.network, 'none');
  }

  refresh();
  setInterval(refresh, 30000);
</script>
</body>
</html>
