<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object vmUser = session.getAttribute("user_id");
  if (vmUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  request.setAttribute("pageHeading", "VM Dashboard");
  request.setAttribute("activeTab", "vms");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>VM Dashboard &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(vmUser) %>">
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>

<main>
  <div id="stats" class="summary" aria-live="polite"></div>

  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>LIST OF VMS</h2>
        <span class="sub">All virtual machines provisioned in your projects</span>
      </div>
      <div class="card-head-actions">
        <span id="freshness" class="freshness"></span>
        <button type="button" class="secondary" id="refreshBtn">Refresh</button>
        <a id="createVm" class="btn" href="vm-create.jsp"
           onclick="return openCreate(this.href)">+ CREATE VM</a>
      </div>
    </div>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

    <div class="table-shell">
      <table>
        <thead>
          <tr>
            <th>VM NAME</th>
            <th>STATUS</th>
            <th>PROJECT</th>
            <th>DETAILS</th>
          </tr>
        </thead>
        <tbody id="vmBody">
          <tr><td colspan="4" class="empty">Loading...</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  let allVms = [];
  let quota = null;

  function summaryCard(label, value, meta) {
    return '<div class="summary-card"><span class="label">' + label +
      '</span><span class="num">' + value +
      '</span><span class="meta">' + meta + '</span></div>';
  }

  function renderSummary() {
    const running = allVms.filter(v => v.status === 'running').length;
    const stopped = allVms.filter(v => v.status === 'stopped').length;
    const attention = allVms.filter(v => v.status === 'error' || v.status === 'creating').length;
    const quotaValue = quota ? quota.used + ' / ' + quota.max : String(allVms.length);
    const quotaMeta = quota
      ? (quota.used >= quota.max ? 'Quota reached' : Math.max(0, quota.max - quota.used) + ' available')
      : 'Your account quota';
    document.getElementById('stats').innerHTML =
      summaryCard('VMs', quotaValue, quotaMeta) +
      summaryCard('Running', String(running), 'Healthy now') +
      summaryCard('Stopped', String(stopped), 'Not running') +
      summaryCard('Attention', String(attention), 'Creating or error');
  }

  function renderTable() {
    const body = document.getElementById('vmBody');
    if (allVms.length === 0) {
      body.innerHTML = '<tr><td colspan="4" class="empty">No VMs yet. Click CREATE VM to get started.</td></tr>';
      return;
    }
    body.innerHTML = allVms.map(function (vm) {
      return `
      <tr>
        <td>\${Portal.esc(vm.vmName)}</td>
        <td>\${Portal.badge(vm.status)}</td>
        <td>\${Portal.esc(vm.projectName)}</td>
        <td><a class="link-pill" href="vm-details.jsp?id=\${encodeURIComponent(vm.vmId)}"
               onclick="return Portal.openMini(this.href, 1000, 780)">View details</a></td>
      </tr>`;
    }).join('');
  }

  function updateCreateButton() {
    const link = document.getElementById('createVm');
    const full = quota && quota.used >= quota.max;
    link.setAttribute('aria-disabled', full ? 'true' : 'false');
    link.title = full ? 'VM quota reached' : 'Create a new VM';
  }

  async function loadVms(manual) {
    if (!manual && document.hidden) return;
    try {
      const { status, body } = await Portal.get('vm/list');
      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || 'Could not load VMs', 'err');
        return;
      }
      Portal.hide('msg');
      allVms = body.vms || [];
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
      Portal.show('msg', 'You have reached your VM quota (' + quota.max + '). Delete a VM or ask an administrator for more capacity.', 'err');
      return false;
    }
    return Portal.openMini(url, 900, 780);
  }

  document.getElementById('refreshBtn').addEventListener('click', function () { loadVms(true); });
  window.portalRefresh = function () { loadVms(true); };
  Portal.poll(function () { return loadVms(false); }, 15000);
</script>
</body>
</html>
