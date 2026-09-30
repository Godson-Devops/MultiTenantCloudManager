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
  <title>Pod Dashboard &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css">
</head>
<body>
<header class="topbar">
  <h1>Pod Dashboard</h1>
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
      <h2 style="margin:0">LIST OF PODS</h2>
      <a href="pod-create.jsp"><button>CREATE POD</button></a>
    </div>

    <div id="msg" class="msg hidden"></div>

    <table>
      <thead>
        <tr>
          <th>LIST OF PODS_NAME</th>
          <th>NAMESPACE</th>
          <th>IMAGE</th>
          <th>STATUS</th>
          <th>DETAILS</th>
        </tr>
      </thead>
      <tbody id="podBody">
        <tr><td colspan="5" class="empty">Loading...</td></tr>
      </tbody>
    </table>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  async function loadPods() {
    const { status, body } = await Portal.get('pod/list');

    if (status === 401) { window.location.href = 'login.jsp'; return; }
    if (status !== 200) {
      document.getElementById('podBody').innerHTML =
        '<tr><td colspan="5" class="empty">Could not load pods</td></tr>';
      return;
    }

    const pods = body.pods || [];
    if (pods.length === 0) {
      document.getElementById('podBody').innerHTML =
        '<tr><td colspan="5" class="empty">No pods yet. Click CREATE POD to get started.</td></tr>';
      return;
    }

    document.getElementById('podBody').innerHTML = pods.map(p => `
      <tr>
        <td>\${Portal.esc(p.podName)}</td>
        <td>\${Portal.esc(p.namespace)}</td>
        <td>\${Portal.esc(p.image)}</td>
        <td>\${Portal.badge(p.status)}</td>
        <td><a href="pod-details.jsp?id=\${encodeURIComponent(p.podId)}">View details</a></td>
      </tr>
    `).join('');
  }

  loadPods();
  setInterval(loadPods, 15000);
</script>
</body>
</html>
