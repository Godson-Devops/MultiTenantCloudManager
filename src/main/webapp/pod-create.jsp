<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%
  if (session.getAttribute("user_id") == null) {
    response.sendRedirect("login.jsp");
    return;
  }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Create Pod &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white1">
</head>
<body>
<header class="topbar">
  <h1>Create Pod</h1>
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
  <div class="card" style="max-width:620px">
    <div class="card-head">
      <div class="titles">
        <h2>CREATE POD</h2>
        <span class="sub">Deploy a container into your namespace</span>
      </div>
      <a class="back-link" href="pod-dashboard.jsp">Back to dashboard</a>
    </div>

    <div id="msg" class="msg hidden"></div>

    <form id="podForm">
      <label>POD NAME
        <input type="text" id="podName" name="podName" required placeholder="my-app-pod">
      </label>
      <label>IMAGE NAME
        <input type="text" id="image" name="image" required placeholder="nginx:1.25">
      </label>
      <label>PORT NO
        <input type="number" id="port" name="port" required min="1" max="65535" value="8080">
      </label>
      <button type="submit" id="submitBtn">CREATE POD</button>
    </form>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  document.getElementById('podForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const btn = document.getElementById('submitBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Creating...';
    Portal.hide('msg');

    const { status, body } = await Portal.post('pod/create', {
      podName: document.getElementById('podName').value,
      image: document.getElementById('image').value,
      port: document.getElementById('port').value
    });

    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not create the pod', 'err');
      btn.disabled = false;
      btn.textContent = 'CREATE POD';
      return;
    }
    Portal.show('msg', 'Pod ' + body.podName + ' created in ' + body.namespace, 'ok');
    setTimeout(function () {
      window.location.href = 'pod-details.jsp?id=' + encodeURIComponent(body.podId);
    }, 1000);
  });
</script>
</body>
</html>
