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
  <title>VM Details &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white1">
</head>
<body>
<header class="topbar">
  <h1>VM Details</h1>
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
  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>VM DETAILS</h2>
        <span class="sub">Live state and network info for this machine</span>
      </div>
      <a class="back-link" href="vm-dashboard.jsp">Back to dashboard</a>
    </div>

    <div id="msg" class="msg hidden"></div>

    <div class="grid-2">
      <div><div class="metric-label">VM NAME</div><div id="vmName">&mdash;</div></div>
      <div><div class="metric-label">STATUS</div><div id="status">&mdash;</div></div>
      <div><div class="metric-label">PRIVATE IP</div><div id="vmIp">&mdash;</div></div>
      <div><div class="metric-label">FLOATING IP</div><div id="floatIp">&mdash;</div></div>
      <div><div class="metric-label">KEY PAIR NAME</div><div id="sshKey">&mdash;</div></div>
      <div><div class="metric-label">PROJECT</div><div id="projectName">&mdash;</div></div>
    </div>

    <div class="action-row" style="margin-top:20px">
      <a id="metricsLink" class="btn secondary" href="#">VIEW METRICS</a>
      <button class="secondary" data-action="start">START</button>
      <button class="secondary" data-action="restart">RESTART</button>
      <button class="secondary" data-action="stop">STOP</button>
      <button class="danger" data-action="delete">DELETE</button>
    </div>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  const VM_ID = <%= vmId == null ? "null" : "\"" + vmId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ").replace("\r", " ") + "\"" %>;

  async function loadVm() {
    if (!VM_ID) {
      document.getElementById('msg').className = 'msg err';
      document.getElementById('msg').textContent = 'No VM id supplied';
      return;
    }
    const { status, body } = await Portal.get('vm/details?id=' + encodeURIComponent(VM_ID));

    if (status === 401) { window.location.href = 'login.jsp'; return; }
    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not load this VM', 'err');
      return;
    }

    document.getElementById('vmName').textContent = body.vmName || '-';
    document.getElementById('status').innerHTML = Portal.badge(body.status);
    document.getElementById('vmIp').textContent = body.vmIp || '-';
    document.getElementById('floatIp').textContent = body.floatIp || '-';
    document.getElementById('sshKey').textContent = body.sshKey || '-';
    document.getElementById('projectName').textContent = body.projectName || '-';
    document.getElementById('metricsLink').href =
      'vm-monitor.jsp?id=' + encodeURIComponent(VM_ID);
  }

  document.querySelectorAll('button[data-action]').forEach(function (btn) {
    btn.addEventListener('click', async function () {
      const action = btn.dataset.action;
      if (action === 'delete' && !confirm('Delete this VM? This cannot be undone.')) return;

      btn.disabled = true;
      Portal.hide('msg');
      const { status, body } = await Portal.post('vm/action', { id: VM_ID, action });

      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || ('Action ' + action + ' failed'), 'err');
        btn.disabled = false;
        return;
      }
      if (action === 'delete') {
        window.location.href = 'vm-dashboard.jsp';
        return;
      }
      Portal.show('msg', 'Action ' + action + ' submitted.', 'ok');
      setTimeout(function () { btn.disabled = false; loadVm(); }, 1200);
    });
  });

  loadVm();
  setInterval(loadVm, 15000);
</script>
</body>
</html>
