<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object podCreateUser = session.getAttribute("user_id");
  if (podCreateUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  request.setAttribute("pageHeading", "Create Pod");
  request.setAttribute("activeTab", "pods");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>Create Pod &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(podCreateUser) %>">
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>

<main>
  <div class="card" style="max-width:680px">
    <div class="card-head">
      <div class="titles">
        <h2>CREATE POD</h2>
        <span class="sub">Deploy a container into your Kubernetes namespace</span>
      </div>
      <div class="card-head-actions">
        <button type="button" class="secondary" id="cancelBtn">Cancel</button>
      </div>
    </div>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>
    <div id="quotaNotice" class="inline-help">Checking your Pod quota...</div>

    <form id="podForm">
      <div class="grid-2">
        <label>POD NAME <span style="color:var(--muted)">(display name)</span>
          <input type="text" id="podName" name="podName" required maxlength="120"
                 placeholder="my-app-pod" autocomplete="off">
        </label>
        <label>NAMESPACE
          <input type="text" id="namespace" readonly tabindex="-1">
        </label>
      </div>

      <div id="namePreview" class="inline-help">Kubernetes pod name: —</div>

      <label>IMAGE NAME
        <input type="text" id="image" name="image" required maxlength="200" list="imagePresets"
               placeholder="nginx:1.25" autocomplete="off">
        <datalist id="imagePresets">
          <option value="nginx:1.25"></option>
          <option value="httpd:2.4"></option>
          <option value="redis:7"></option>
          <option value="busybox:1.36"></option>
        </datalist>
      </label>

      <label>PORT NO
        <input type="number" id="port" name="port" required min="1" max="65535" value="8080">
      </label>

      <div class="action-row">
        <button type="submit" id="submitBtn">Create Pod</button>
      </div>
    </form>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  let quota = null;

  function normalizePodName(value) {
    let cleaned = String(value || '').toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '');
    if (cleaned.length > 63) cleaned = cleaned.substring(0, 63).replace(/-+$/g, '');
    return cleaned;
  }

  function sanitizeNamespace(value) {
    let cleaned = String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    return cleaned || 'user';
  }

  function refreshSubmitState() {
    const btn = document.getElementById('submitBtn');
    const quotaFull = quota && quota.used >= quota.max;
    const normalized = normalizePodName(document.getElementById('podName').value);
    btn.disabled = quotaFull || !normalized;
    btn.title = quotaFull ? 'Pod quota reached'
      : !normalized ? 'Pod name must contain at least one letter or number' : '';
  }

  function renderQuota() {
    const notice = document.getElementById('quotaNotice');
    if (!quota) {
      notice.className = 'inline-help';
      notice.textContent = 'Your quota could not be loaded. The server will still enforce it when you create the pod.';
      refreshSubmitState();
      return;
    }
    const remaining = Math.max(0, quota.max - quota.used);
    const full = remaining === 0;
    notice.className = 'inline-help' + (full ? ' quota-full' : '');
    notice.textContent = full
      ? 'You have used all ' + quota.max + ' pods in your quota. Delete a pod or ask an administrator for more capacity.'
      : quota.used + ' of ' + quota.max + ' pods used · ' + remaining + ' available.';
    refreshSubmitState();
  }

  async function loadQuota() {
    try {
      const { status, body } = await Portal.get('pod/list');
      if (status === 401) { window.location.href = 'login.jsp'; return; }
      quota = status === 200 ? (body.quota || null) : null;
    } catch (e) {
      quota = null;
    }
    renderQuota();
  }

  function updatePreview() {
    const normalized = normalizePodName(document.getElementById('podName').value);
    const preview = document.getElementById('namePreview');
    preview.textContent = 'Kubernetes pod name: ' + (normalized || '—');
    preview.className = 'inline-help' + (!normalized && document.getElementById('podName').value ? ' warn' : '');
    refreshSubmitState();
  }

  const currentUser = document.body.dataset.currentUser || '';
  document.getElementById('namespace').value = 'user-' + sanitizeNamespace(currentUser);
  document.getElementById('podName').addEventListener('input', updatePreview);
  updatePreview();

  document.getElementById('cancelBtn').addEventListener('click', function () {
    if (Portal.inPanel()) Portal.closePanel(false);
    else window.location.href = 'pod-dashboard.jsp';
  });

  document.getElementById('podForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    if (quota && quota.used >= quota.max) {
      Portal.show('msg', 'You have reached your Pod quota.', 'err');
      return;
    }

    const btn = document.getElementById('submitBtn');
    Portal.busy(btn, 'Creating...');
    Portal.hide('msg');

    let result;
    try {
      result = await Portal.post('pod/create', {
        podName: document.getElementById('podName').value,
        image: document.getElementById('image').value,
        port: document.getElementById('port').value
      });
    } catch (err) {
      Portal.show('msg', 'Could not reach the server. The pod was not created.', 'err');
      Portal.resetBusy(btn);
      refreshSubmitState();
      return;
    }
    const { status, body } = result;

    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not create the pod', 'err');
      Portal.resetBusy(btn);
      refreshSubmitState();
      return;
    }

    Portal.show('msg', 'Pod "' + body.podName + '" created in ' + body.namespace + '.', 'ok');
    setTimeout(function () {
      if (Portal.inPanel()) {
        Portal.closePanel(true, 'Pod "' + body.podName + '" was created.');
      } else {
        window.location.href = 'pod-details.jsp?id=' + encodeURIComponent(body.podId);
      }
    }, 700);
  });

  loadQuota();
</script>
</body>
</html>
