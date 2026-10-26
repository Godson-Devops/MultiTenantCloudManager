<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
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
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>Login &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
  <link rel="stylesheet" href="css/auth.css?v=white1">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com">
  <link href="https://fonts.googleapis.com/css2?family=Sora:wght@600;700&family=Inter:wght@400;500;600&display=swap" rel="stylesheet">
</head>
<body class="auth-page">
<div class="auth-shell">
  <div class="auth-brand">
    <span class="mark" aria-hidden="true"></span>
    Cloud Portal
  </div>

  <div class="auth-panel">
    <h1>Welcome back</h1>
    <p class="sub">Sign in to manage your VMs and Pods</p>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

    <form id="loginForm">
      <div class="field">
        <label for="userId">Username</label>
        <input type="text" id="userId" name="userId" placeholder="your username"
               autocomplete="username" maxlength="64" required autofocus>
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input type="password" id="password" name="password" placeholder="&bull;&bull;&bull;&bull;&bull;&bull;&bull;&bull;"
               autocomplete="current-password" maxlength="72" required>
      </div>
      <button type="submit" class="auth-btn" id="submitBtn">Login</button>
    </form>

    <div class="auth-links">
      No account? <a href="register.jsp">Create one</a>
    </div>
  </div>
</div>

<script src="js/portal.js?v=5"></script>
<script>
  document.getElementById('loginForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const btn = document.getElementById('submitBtn');
    Portal.busy(btn, 'Signing in...');
    Portal.hide('msg');

    let result;
    try {
      result = await Portal.post('login', {
        userId: document.getElementById('userId').value,
        password: document.getElementById('password').value
      });
    } catch (err) {
      Portal.show('msg', 'Could not reach the server. Check your connection and try again.', 'err');
      Portal.resetBusy(btn);
      return;
    }
    const { status, body } = result;

    if (status === 200) {
      window.location.href = body.redirect || 'vm-dashboard.jsp';
      return;
    }
    Portal.show('msg', body.error || 'Login failed', 'err');
    Portal.resetBusy(btn);
  });
</script>
</body>
</html>
