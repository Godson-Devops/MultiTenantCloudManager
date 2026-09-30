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
  <title>VM Dashboard &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css">
</head>
<body>
<header class="topbar">
  <h1>VM Dashboard</h1>
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
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px">
      <h2 style="margin:0">LIST OF VMS</h2>
      <a href="vm-create.jsp"><button>CREATE VM</button></a>
    </div>

    <div id="msg" class="msg hidden"></div>

    <table>
      <thead>
        <tr>
          <th>LIST OF VM NAMES</th>
          <th>VM STATUS</th>
          <th>PROJECT_NAME</th>
          <th>DETAILS</th>
        </tr>
      </thead>
      <tbody id="vmBody">
        <tr><td colspan="4" class="empty">Loading...</td></tr>
      </tbody>
    </table>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  async function loadVms() {
    const { status, body } = await Portal.get('vm/list');

    if (status === 401) { window.location.href = 'login.jsp'; return; }
    if (status !== 200) {
      document.getElementById('vmBody').innerHTML =
        '<tr><td colspan="4" class="empty">Could not load VMs</td></tr>';
      return;
    }

    const vms = body.vms || [];
    if (vms.length === 0) {
      document.getElementById('vmBody').innerHTML =
        '<tr><td colspan="4" class="empty">No VMs yet. Click CREATE VM to get started.</td></tr>';
      return;
    }

    document.getElementById('vmBody').innerHTML = vms.map(vm => `
      <tr>
        <td>\${Portal.esc(vm.vmName)}</td>
        <td>\${Portal.badge(vm.status)}</td>
        <td>\${Portal.esc(vm.projectName)}</td>
        <td><a href="vm-details.jsp?id=\${encodeURIComponent(vm.vmId)}">View details</a></td>
      </tr>
    `).join('');
  }

  loadVms();
  setInterval(loadVms, 15000);
</script>
</body>
</html>
