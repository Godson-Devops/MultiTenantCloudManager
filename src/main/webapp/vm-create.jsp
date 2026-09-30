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
  <title>Create VM &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css">
</head>
<body>
<header class="topbar">
  <h1>Create VM</h1>
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
  <div class="card" style="max-width:620px">
    <h2>VM DETAILS</h2>

    <div id="msg" class="msg hidden"></div>
    <div id="progress" class="msg hidden"></div>

    <form id="vmForm">
      <div class="grid-2">
        <label>VM NAME
          <input type="text" id="vmName" name="vmName" required placeholder="my-web-server">
        </label>
        <label>PROJECT NAME
          <input type="text" id="projectName" name="projectName" required
                 placeholder="my-openstack-project">
        </label>
        <label>IMAGE NAME
          <input type="text" id="image" name="image" required placeholder="Ubuntu 22.04">
        </label>
        <label>FLAVOR
          <input type="text" id="flavor" name="flavor" required placeholder="m1.small">
        </label>
      </div>
      <label>KEY PAIR NAME <span style="color:var(--muted)">(optional)</span>
        <input type="text" id="keyPair" name="keyPair" placeholder="my-keypair">
      </label>
      <button type="submit" id="submitBtn">Create VM</button>
    </form>
  </div>
</main>

<script src="js/portal.js"></script>
<script>
  document.getElementById('vmForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('submitBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Creating...';
    Portal.hide('msg');
    Portal.hide('progress');

    const { status, body } = await Portal.post('vm/create', {
      vmName: document.getElementById('vmName').value,
      projectName: document.getElementById('projectName').value,
      image: document.getElementById('image').value,
      flavor: document.getElementById('flavor').value,
      keyPair: document.getElementById('keyPair').value
    });

    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not create the VM', 'err');
      btn.disabled = false;
      btn.textContent = 'Create VM';
      return;
    }

    // Spec section G: poll the detail endpoint until the VM leaves "creating".
    Portal.show('progress', 'Creating...', 'ok');
    const vmId = body.vmId;
    const timer = setInterval(async () => {
      const res = await Portal.get('vm/details?id=' + encodeURIComponent(vmId));
      if (res.status !== 200) { clearInterval(timer); return; }
      if (res.body.status === 'running') {
        clearInterval(timer);
        Portal.show('progress', 'VM is running.', 'ok');
        setTimeout(() => {
          window.location.href = 'vm-details.jsp?id=' + encodeURIComponent(vmId);
        }, 900);
      } else if (res.body.status === 'error') {
        clearInterval(timer);
        Portal.show('progress', 'The VM entered an error state.', 'err');
        btn.disabled = false;
        btn.textContent = 'Create VM';
      }
    }, 5000);
  });
</script>
</body>
</html>
