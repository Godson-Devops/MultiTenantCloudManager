<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%
  // Already signed in? Skip the login form.
  if (session.getAttribute("user_id") != null) {
    response.sendRedirect("vm-dashboard.jsp");
    return;
  }
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Login &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css">
</head>
<body>
<div class="centered-page">
  <div class="card auth-card">
    <h1>Cloud Provisioning Portal</h1>
    <p class="sub">Sign in to manage your VMs and Pods</p>

    <div id="msg" class="msg hidden"></div>

    <form id="loginForm" autocomplete="off">
      <label>USERNAME
        <input type="text" id="userId" name="userId" required autofocus>
      </label>
      <label>PASSWORD
        <input type="password" id="password" name="password" required>
      </label>
      <button type="submit" id="submitBtn" style="width:100%">LOGIN</button>
    </form>

    <div class="auth-foot">
      No account? <a href="register.jsp">REGISTER</a>
    </div>
  </div>
</div>

<script src="js/portal.js"></script>
<script>
  document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('submitBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Signing in...';
    Portal.hide('msg');

    const { status, body } = await Portal.post('login', {
      userId: document.getElementById('userId').value,
      password: document.getElementById('password').value
    });

    if (status === 200) {
      window.location.href = body.redirect || 'vm-dashboard.jsp';
      return;
    }
    Portal.show('msg', body.error || 'Login failed', 'err');
    btn.disabled = false;
    btn.textContent = 'LOGIN';
  });
</script>
</body>
</html>
