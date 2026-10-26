<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object podUser = session.getAttribute("user_id");
  if (podUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  request.setAttribute("pageHeading", "Pod Dashboard");
  request.setAttribute("activeTab", "pods");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>Pod Dashboard &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(podUser) %>">
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>

<main>
  <div id="stats" class="summary" aria-live="polite"></div>

  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>LIST OF PODS</h2>
        <span class="sub">All Kubernetes pods running in your namespaces</span>
      </div>
      <div class="card-head-actions">
        <span id="freshness" class="freshness"></span>
        <button type="button" class="secondary" id="refreshBtn">Refresh</button>
        <a id="createPod" class="btn" href="pod-create.jsp"
           onclick="return openCreate(this.href)">+ CREATE POD</a>
      </div>
    </div>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

    <div class="table-shell">
      <table>
        <thead>
          <tr>
            <th>POD NAME</th>
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
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  let allPods = [];
  let quota = null;

  function summaryCard(label, value, meta) {
    return '<div class="summary-card"><span class="label">' + label +
      '</span><span class="num">' + value +
      '</span><span class="meta">' + meta + '</span></div>';
  }

  function renderSummary() {
    const running = allPods.filter(p => p.status === 'running').length;
    const stopped = allPods.filter(p => p.status === 'stopped').length;
    const attention = allPods.filter(p => p.status === 'error' || p.status === 'creating').length;
    const quotaValue = quota ? quota.used + ' / ' + quota.max : String(allPods.length);
    const quotaMeta = quota
      ? (quota.used >= quota.max ? 'Quota reached' : Math.max(0, quota.max - quota.used) + ' available')
      : 'Your account quota';
    document.getElementById('stats').innerHTML =
      summaryCard('Pods', quotaValue, quotaMeta) +
      summaryCard('Running', String(running), 'Healthy now') +
      summaryCard('Stopped', String(stopped), 'Not running') +
      summaryCard('Attention', String(attention), 'Creating or error');
  }

  function renderTable() {
    const body = document.getElementById('podBody');
    if (allPods.length === 0) {
      body.innerHTML = '<tr><td colspan="5" class="empty">No pods yet. Click CREATE POD to get started.</td></tr>';
      return;
    }
    body.innerHTML = allPods.map(function (p) {
      return `
      <tr>
        <td>\${Portal.esc(p.podName)}</td>
        <td>\${Portal.esc(p.namespace)}</td>
        <td>\${Portal.esc(p.image)}</td>
        <td>\${Portal.badge(p.status)}</td>
        <td><a class="link-pill" href="pod-details.jsp?id=\${encodeURIComponent(p.podId)}"
               onclick="return Portal.openMini(this.href, 1000, 780)">View details</a></td>
      </tr>`;
    }).join('');
  }

  function updateCreateButton() {
    const link = document.getElementById('createPod');
    const full = quota && quota.used >= quota.max;
    link.setAttribute('aria-disabled', full ? 'true' : 'false');
    link.title = full ? 'Pod quota reached' : 'Create a new pod';
  }

  async function loadPods(manual) {
    if (!manual && document.hidden) return;
    try {
      const { status, body } = await Portal.get('pod/list');
      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || 'Could not load pods', 'err');
        return;
      }
      Portal.hide('msg');
      allPods = body.pods || [];
      quota = body.quota || null;
      renderSummary();
      renderTable();
      updateCreateButton();
      Portal.freshness('freshness');
    } catch (e) {
      Portal.show('msg', 'Could not reach the server. Check your connection and try again.', 'err');
    }
  }

  function openCreate(url) {
    if (quota && quota.used >= quota.max) {
      Portal.show('msg', 'You have reached your Pod quota (' + quota.max + '). Delete a pod or ask an administrator for more capacity.', 'err');
      return false;
    }
    return Portal.openMini(url, 820, 720);
  }

  document.getElementById('refreshBtn').addEventListener('click', function () { loadPods(true); });
  window.portalRefresh = function () { loadPods(true); };
  Portal.poll(function () { return loadPods(false); }, 15000);
</script>
</body>
</html>
