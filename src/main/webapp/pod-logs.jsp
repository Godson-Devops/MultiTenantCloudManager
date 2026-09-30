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
  <title>Pod Logs &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css">
</head>
<body>
<header class="topbar">
  <h1>Pod Logs</h1>
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
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px">
      <h2 style="margin:0">POD LOGS <span id="state" style="color:var(--muted);font-weight:400"></span></h2>
      <div style="display:flex;gap:8px;align-items:center">
        <a id="backLink" href="pod-dashboard.jsp"><button class="secondary">BACK</button></a>
        <button id="stopBtn" class="danger" disabled>STOP STREAMING</button>
      </div>
    </div>

    <div id="msg" class="msg hidden"></div>

    <div class="log-viewer" id="logViewer">Press START STREAMING to begin.</div>

    <div style="margin-top:14px">
      <button id="startBtn">START STREAMING</button>
    </div>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  const POD_ID = <%= podId == null ? "null" : "\"" + podId.replace("\"", "") + "\"" %>;
  let source = null;

  if (POD_ID) {
    document.getElementById('backLink').href = 'pod-details.jsp?id=' + encodeURIComponent(POD_ID);
  }

  function append(text) {
    const viewer = document.getElementById('logViewer');
    if (viewer.textContent === 'Press START STREAMING to begin.') viewer.textContent = '';
    viewer.textContent += text;
    viewer.scrollTop = viewer.scrollHeight;
  }

  function stop() {
    if (source) { source.close(); source = null; }
    document.getElementById('startBtn').disabled = false;
    document.getElementById('stopBtn').disabled = true;
    document.getElementById('state').textContent = '(stopped)';
  }

  document.getElementById('startBtn').addEventListener('click', () => {
    if (!POD_ID) {
      Portal.show('msg', 'No pod id supplied', 'err');
      return;
    }
    Portal.hide('msg');
    document.getElementById('logViewer').textContent = '';
    document.getElementById('startBtn').disabled = true;
    document.getElementById('stopBtn').disabled = false;
    document.getElementById('state').textContent = '(streaming)';

    // The servlet holds the request open and streams the pod log as SSE.
    // Log lines arrive as unnamed "data:" frames (onmessage); "open",
    // "end" and "error" are named events.
    source = new EventSource('pod/logs?id=' + encodeURIComponent(POD_ID));

    source.onmessage = (e) => append(e.data + '\n');

    source.addEventListener('open', (e) => append('--- ' + e.data + ' ---\n'));

    source.addEventListener('end', () => {
      document.getElementById('state').textContent = '(reconnecting)';
    });

    source.addEventListener('error', (e) => {
      Portal.show('msg', e.data || 'The log stream failed.', 'err');
      stop();
    });

    source.onerror = () => {
      // EventSource reconnects automatically; only report if it stays down.
      if (source && source.readyState === EventSource.CLOSED) {
        Portal.show('msg', 'The log stream ended.', 'err');
        stop();
      }
    };
  });

  document.getElementById('stopBtn').addEventListener('click', stop);
  window.addEventListener('beforeunload', () => { if (source) source.close(); });
</script>
</body>
</html>
