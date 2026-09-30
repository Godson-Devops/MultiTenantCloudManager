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
  <link rel="stylesheet" href="css/portal.css">
</head>
<body>
<div class="centered-page">
  <div class="card auth-card">
    <h1>Create an account</h1>
    <p class="sub">You get a default quota of 3 VMs and 3 Pods</p>

    <div id="msg" class="msg hidden"></div>

    <form id="registerForm" autocomplete="off">
      <label>USERNAME
        <input type="text" id="userId" name="userId" required autofocus>
      </label>
      <label>PASSWORD
        <input type="password" id="password" name="password" required minlength="8">
      </label>
      <label>CONFIRM PASSWORD
        <input type="password" id="confirm" name="confirm" required minlength="8">
      </label>
      <button type="submit" id="submitBtn" style="width:100%">REGISTER</button>
    </form>

    <div class="auth-foot">
      Already registered? <a href="login.jsp">LOGIN</a>
    </div>
  </div>
</div>

<script src="js/portal.js"></script>
<script>
  document.getElementById('registerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const pwd = document.getElementById('password').value;
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
      setTimeout(() => { window.location.href = 'login.jsp'; }, 1200);
      return;
    }
    Portal.show('msg', body.error || 'Registration failed', 'err');
    btn.disabled = false;
    btn.textContent = 'REGISTER';
  });
</script>
</body>
</html>
