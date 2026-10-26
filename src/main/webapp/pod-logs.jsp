<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object logsUser = session.getAttribute("user_id");
  if (logsUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  String podId = request.getParameter("id");
  boolean popup = "1".equals(request.getParameter("popup"));
  String fromAdmin = "admin".equals(request.getParameter("from")) ? "&from=admin" : "";
  request.setAttribute("pageHeading", "Pod Logs");
  request.setAttribute("activeTab", "pods");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>Pod Logs &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(logsUser) %>">
<% if (!popup) { %>
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>
<% } %>

<main>
  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>POD LOGS <span id="state" class="stream-state">idle</span></h2>
        <span class="sub">Live container output streamed over SSE</span>
      </div>
      <div class="card-head-actions">
        <a id="backLink" class="btn secondary" href="pod-dashboard.jsp" data-panel="off">Back</a>
        <button type="button" class="secondary" id="startBtn">Start</button>
        <button type="button" class="danger" id="stopBtn" disabled>Stop</button>
      </div>
    </div>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

    <div class="log-toolbar">
      <span class="hint">Auto-reconnects after the server's 60-second stream window. <span id="lineCount">0</span> / 2000 lines.</span>
      <div class="action-row">
        <label><input type="checkbox" id="autoscroll" checked> Auto-scroll</label>
        <button type="button" class="secondary" id="clearBtn">Clear</button>
        <button type="button" class="secondary" id="copyBtn">Copy</button>
        <button type="button" class="secondary" id="downloadBtn">Download</button>
      </div>
    </div>

    <div class="log-viewer" id="logViewer">Logs will stream here automatically.</div>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  const POD_ID = <%= podId == null ? "null" : "\"" + podId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ").replace("\r", " ") + "\"" %>;
  const POPUP = <%= popup ? "true" : "false" %>;
  const FROM_ADMIN = <%= fromAdmin.isEmpty() ? "false" : "true" %>;
  const MAX_LINES = 2000;
  const timestampPattern = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\s/;

  let source = null;
  let reconnectTimer = null;
  let streaming = false;
  let reconnectAttempts = 0;
  let connectCount = 0;
  let lastTimestamp = '';
  let lines = [];
  let renderScheduled = false;

  if (POD_ID) {
    document.getElementById('backLink').href =
      'pod-details.jsp?id=' + encodeURIComponent(POD_ID)
      + (FROM_ADMIN ? '&from=admin' : '') + (POPUP ? '&popup=1' : '');
  }

  function setState(text, cls) {
    const el = document.getElementById('state');
    el.textContent = text;
    el.className = 'stream-state' + (cls ? ' ' + cls : '');
  }

  function renderLogs() {
    renderScheduled = false;
    const viewer = document.getElementById('logViewer');
    const wasAtBottom = viewer.scrollHeight - viewer.scrollTop - viewer.clientHeight < 40;
    viewer.textContent = lines.length ? lines.join('\n') : '';
    if (!lines.length) viewer.textContent = 'Logs will stream here automatically.';
    if (document.getElementById('autoscroll').checked || wasAtBottom) {
      viewer.scrollTop = viewer.scrollHeight;
    }
    document.getElementById('lineCount').textContent = String(lines.length);
  }

  function scheduleRender() {
    if (renderScheduled) return;
    renderScheduled = true;
    setTimeout(renderLogs, 120);
  }

  function append(text) {
    const incoming = String(text || '').split(/\r?\n/);
    incoming.forEach(function (raw) {
      if (raw === '') return;
      const match = raw.match(timestampPattern);
      let display = raw;
      if (match) {
        lastTimestamp = match[1];
        const displayTimestamp = match[1].replace(/(\.\d{3})\d+Z$/, '$1Z');
        const date = new Date(displayTimestamp);
        if (!isNaN(date.getTime())) {
          display = date.toLocaleTimeString([], { hour12: false }) + ' ' + raw.slice(match[0].length);
        }
      }
      lines.push(display);
    });

    if (lines.length > MAX_LINES) {
      lines.splice(0, lines.length - MAX_LINES);
    }
    if (incoming.length || lines.length) scheduleRender();
  }

  function closeSource() {
    if (source) {
      source.close();
      source = null;
    }
  }

  function scheduleReconnect(delay) {
    closeSource();
    if (!streaming) return;
    setState('reconnecting', 'wait');
    clearTimeout(reconnectTimer);
    reconnectTimer = setTimeout(function () {
      reconnectTimer = null;
      if (streaming) connect();
    }, delay);
  }

  function connect() {
    closeSource();
    if (!streaming) return;

    setState(reconnectAttempts ? 'reconnecting' : 'connecting', 'wait');
    const since = lastTimestamp || (connectCount > 0 ? new Date().toISOString() : '');
    connectCount++;
    const url = 'pod/logs?id=' + encodeURIComponent(POD_ID)
      + (since ? '&since=' + encodeURIComponent(since) : '');
    source = new EventSource(url);

    source.onmessage = function (event) {
      reconnectAttempts = 0;
      append(event.data + '\n');
      setState('live', 'live');
    };

    source.addEventListener('open', function (event) {
      if (event.data == null) return;
      reconnectAttempts = 0;
      Portal.hide('msg');
      setState('live', 'live');
    });

    source.addEventListener('end', function () {
      if (!streaming) return;
      reconnectAttempts = 0;
      scheduleReconnect(250);
    });

    source.addEventListener('error', function (event) {
      if (event.data != null) {
        Portal.show('msg', event.data, 'err');
        stopStreaming();
        return;
      }
      if (!streaming) return;
      reconnectAttempts++;
      if (reconnectAttempts > 8) {
        Portal.show('msg', 'Could not maintain the log stream after several attempts. Check your session and try again.', 'err');
        stopStreaming();
        return;
      }
      scheduleReconnect(Math.min(15000, 1000 * Math.pow(2, Math.min(reconnectAttempts - 1, 4))));
    });
  }

  function startStreaming() {
    if (!POD_ID) {
      Portal.show('msg', 'No pod id supplied.', 'err');
      return;
    }
    Portal.hide('msg');
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
    streaming = true;
    reconnectAttempts = 0;
    connectCount = 0;
    lastTimestamp = '';
    lines = [];
    renderLogs();
    document.getElementById('startBtn').disabled = true;
    document.getElementById('stopBtn').disabled = false;
    connect();
  }

  function stopStreaming() {
    streaming = false;
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
    closeSource();
    document.getElementById('startBtn').disabled = false;
    document.getElementById('stopBtn').disabled = true;
    setState('stopped', 'off');
  }

  document.getElementById('startBtn').addEventListener('click', startStreaming);
  document.getElementById('stopBtn').addEventListener('click', stopStreaming);
  document.getElementById('autoscroll').addEventListener('change', function () {
    if (this.checked) {
      const viewer = document.getElementById('logViewer');
      viewer.scrollTop = viewer.scrollHeight;
    }
  });
  document.getElementById('clearBtn').addEventListener('click', function () {
    lines = [];
    renderLogs();
    Portal.hide('msg');
  });
  document.getElementById('copyBtn').addEventListener('click', function () {
    if (!lines.length) {
      Portal.show('msg', 'There are no logs to copy yet.', 'warn');
      return;
    }
    Portal.copy(lines.join('\n'));
  });
  document.getElementById('downloadBtn').addEventListener('click', function () {
    if (!lines.length) {
      Portal.show('msg', 'There are no logs to download yet.', 'warn');
      return;
    }
    const blob = new Blob([lines.join('\n') + '\n'], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'pod-logs-' + POD_ID + '.txt';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  });
  window.addEventListener('beforeunload', function () {
    streaming = false;
    clearTimeout(reconnectTimer);
    closeSource();
  });

  if (POD_ID) startStreaming();
  else Portal.show('msg', 'No pod id supplied.', 'err');
</script>
</body>
</html>
