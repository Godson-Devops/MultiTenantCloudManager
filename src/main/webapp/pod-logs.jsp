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
  <link rel="stylesheet" href="css/portal.css?v=white1">
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
    <div class="card-head">
      <div class="titles">
        <h2>POD LOGS <span id="state" class="stream-state">idle</span></h2>
        <span class="sub">Live container output streamed over SSE</span>
      </div>
      <div class="action-row">
        <a id="backLink" class="btn secondary" href="pod-dashboard.jsp">BACK</a>
        <button id="stopBtn" class="danger" disabled>STOP STREAMING</button>
      </div>
    </div>

    <div id="msg" class="msg hidden"></div>

    <div class="log-toolbar">
      <span class="hint">Tip: press START STREAMING to attach to the container&rsquo;s stdout.</span>
    </div>

    <div class="log-viewer" id="logViewer">Press START STREAMING to begin.</div>

    <div class="action-row" style="margin-top:14px">
      <button id="startBtn">START STREAMING</button>
    </div>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  const POD_ID = <%= podId == null ? "null" : "\"" + podId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"" %>;
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

  function setState(text, cls) {
    const el = document.getElementById('state');
    el.textContent = text;
    el.className = 'stream-state' + (cls ? ' ' + cls : '');
  }

  function stop() {
    if (source) { source.close(); source = null; }
    document.getElementById('startBtn').disabled = false;
    document.getElementById('stopBtn').disabled = true;
    setState('stopped', 'off');
  }

  document.getElementById('startBtn').addEventListener('click', function () {
    if (!POD_ID) {
      Portal.show('msg', 'No pod id supplied', 'err');
      return;
    }
    Portal.hide('msg');
    document.getElementById('logViewer').textContent = '';
    document.getElementById('startBtn').disabled = true;
    document.getElementById('stopBtn').disabled = false;
    setState('streaming', 'live');

    source = new EventSource('pod/logs?id=' + encodeURIComponent(POD_ID));

    source.onmessage = function (e) { append(e.data + '\n'); };

    source.addEventListener('open', function (e) {
      if (e.data != null) append('--- ' + e.data + ' ---\n');
    });

    source.addEventListener('end', function () {
      setState('reconnecting', 'wait');
    });

    source.addEventListener('error', function (e) {
      if (e.data != null) {
        Portal.show('msg', e.data, 'err');
        stop();
        return;
      }
      if (source && source.readyState === EventSource.CLOSED) {
        Portal.show('msg', 'The log stream ended.', 'err');
        stop();
      } else if (source) {
        setState('reconnecting', 'wait');
      }
    });
  });

  document.getElementById('stopBtn').addEventListener('click', stop);
  window.addEventListener('beforeunload', function () { if (source) source.close(); });
</script>
</body>
</html>
