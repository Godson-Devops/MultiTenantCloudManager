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
  <title>Register &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white1">
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
    <h1>Create account</h1>
    <p class="sub">You get a default quota of 3 VMs and 3 Pods</p>

    <div id="msg" class="msg hidden"></div>

    <form id="registerForm" autocomplete="off">
      <div class="field">
        <label for="userId">Username</label>
        <input type="text" id="userId" name="userId" placeholder="choose a username" required autofocus>
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input type="password" id="password" name="password" placeholder="min. 8 characters" required>
      </div>
      <div class="field">
        <label for="confirm">Confirm password</label>
        <input type="password" id="confirm" name="confirm" placeholder="repeat password" required>
      </div>
      <button type="submit" class="auth-btn" id="submitBtn">Register</button>
    </form>

    <div class="auth-links">
      Already registered? <a href="login.jsp">Sign in</a>
    </div>
  </div>
</div>

<script src="js/portal.js"></script>
<script>
  document.getElementById('registerForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    const pwd = document.getElementById('password').value;
    if (pwd.length < 8) {
      Portal.show('msg', 'Password must be at least 8 characters', 'err');
      return;
    }
    if (pwd !== document.getElementById('confirm').value) {
      Portal.show('msg', 'Passwords do not match', 'err');
      return;
    }

    const btn = document.getElementById('submitBtn');
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner"></span> Creating...';
    Portal.hide('msg');

    const { status, body } = await Portal.post('register', {
      userId: document.getElementById('userId').value,
      password: pwd
    });

    if (status === 200) {
      Portal.show('msg', body.message || 'Registration successful', 'ok');
      setTimeout(function () { window.location.href = 'login.jsp'; }, 1200);
      return;
    }
    Portal.show('msg', body.error || 'Registration failed', 'err');
    btn.disabled = false;
    btn.textContent = 'REGISTER';
  });
</script>
</body>
</html>
