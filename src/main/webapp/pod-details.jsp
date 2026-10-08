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
  <title>Pod Details &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white1">
</head>
<body>
<header class="topbar">
  <h1>Pod Details</h1>
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
  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>POD DETAILS</h2>
        <span class="sub">Live state and metadata for this container</span>
      </div>
      <a class="back-link" href="pod-dashboard.jsp">Back to dashboard</a>
    </div>

    <div id="msg" class="msg hidden"></div>

    <div class="grid-2">
      <div><div class="metric-label">POD NAME</div><div id="podName">&mdash;</div></div>
      <div><div class="metric-label">STATUS</div><div id="status">&mdash;</div></div>
      <div><div class="metric-label">NAMESPACE</div><div id="namespace">&mdash;</div></div>
      <div><div class="metric-label">POD IP</div><div id="podIp">&mdash;</div></div>
      <div><div class="metric-label">NODE</div><div id="nodeName">&mdash;</div></div>
      <div><div class="metric-label">RESTARTS</div><div id="restartCount">&mdash;</div></div>
      <div><div class="metric-label">PORT NO</div><div id="portNo">&mdash;</div></div>
      <div><div class="metric-label">IMAGE</div><div id="image">&mdash;</div></div>
    </div>

    <div class="action-row" style="margin-top:20px">
      <a id="monitorLink" class="btn secondary" href="#">VIEW METRICS</a>
      <a id="logsLink" class="btn secondary" href="#">VIEW LOGS</a>
      <button class="secondary" data-action="start">START</button>
      <button class="secondary" data-action="restart">RESTART</button>
      <button class="secondary" data-action="stop">STOP</button>
      <button class="danger" data-action="delete">DELETE</button>
    </div>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  const POD_ID = <%= podId == null ? "null" : "\"" + podId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ").replace("\r", " ") + "\"" %>;

  async function loadPod() {
    if (!POD_ID) {
      Portal.show('msg', 'No pod id supplied', 'err');
      return;
    }
    const { status, body } = await Portal.get('pod/details?id=' + encodeURIComponent(POD_ID));

    if (status === 401) { window.location.href = 'login.jsp'; return; }
    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not load this pod', 'err');
      return;
    }

    document.getElementById('podName').textContent = body.podName || '-';
    document.getElementById('status').innerHTML = Portal.badge(body.status);
    document.getElementById('namespace').textContent = body.namespace || '-';
    document.getElementById('podIp').textContent = body.podIp || '-';
    document.getElementById('nodeName').textContent = body.nodeName || '-';
    document.getElementById('restartCount').textContent = body.restartCount ?? '-';
    document.getElementById('portNo').textContent = body.podPort || '-';
    document.getElementById('image').textContent = body.image || '-';

    document.getElementById('monitorLink').href =
      'pod-monitor.jsp?id=' + encodeURIComponent(POD_ID);
    document.getElementById('logsLink').href =
      'pod-logs.jsp?id=' + encodeURIComponent(POD_ID);
  }

  document.querySelectorAll('button[data-action]').forEach(function (btn) {
    btn.addEventListener('click', async function () {
      const action = btn.dataset.action;
      if (action === 'delete' && !confirm('Delete this pod? This cannot be undone.')) return;

      btn.disabled = true;
      Portal.hide('msg');
      const { status, body } = await Portal.post('pod/action', { id: POD_ID, action });

      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || ('Action ' + action + ' failed'), 'err');
        btn.disabled = false;
        return;
      }
      if (action === 'delete') {
        window.location.href = 'pod-dashboard.jsp';
        return;
      }
      Portal.show('msg', 'Action ' + action + ' submitted.', 'ok');
      setTimeout(function () { btn.disabled = false; loadPod(); }, 1200);
    });
  });

  loadPod();
  setInterval(loadPod, 15000);
</script>
</body>
</html>
