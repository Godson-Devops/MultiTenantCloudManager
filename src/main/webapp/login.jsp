<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%

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
  <link rel="stylesheet" href="css/portal.css?v=white1">
  <link rel="stylesheet" href="css/auth.css?v=white1">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Sora:wght@600;700&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
</head>
<body class="auth-page">
<div class="auth-shell">
  <div class="auth-brand">
    <span class="mark" aria-hidden="true">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M17.5 19a4.5 4.5 0 0 0 .5-8.96A6 6 0 0 0 6.2 9.2 4.4 4.4 0 0 0 6.5 19z"/>
      </svg>
    </span>
    Cloud Portal
  </div>

  <div class="auth-panel">
    <h1>Welcome back</h1>
    <p class="sub">Sign in to manage your VMs and Pods</p>

    <div id="msg" class="msg hidden"></div>

    <form id="loginForm" autocomplete="off">
      <div class="field">
        <label for="userId">Username</label>
        <input type="text" id="userId" name="userId" placeholder="your username" required autofocus>
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input type="password" id="password" name="password" placeholder="&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;" required>
      </div>
      <button type="submit" class="auth-btn" id="submitBtn">Login</button>
    </form>

    <div class="auth-links">
      No account? <a href="register.jsp">Create one</a>
    </div>
  </div>
</div>

<script src="js/portal.js"></script>
<script>
  document.getElementById('loginForm').addEventListener('submit', async function (e) {
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
