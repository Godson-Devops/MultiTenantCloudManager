<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.portal.util.AdminUsers" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  String adminUser = (String) session.getAttribute("user_id");
  if (adminUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  if (!AdminUsers.isAdmin(adminUser)) {
    response.sendRedirect("vm-dashboard.jsp");
    return;
  }
  request.setAttribute("pageHeading", "All VMs");
  request.setAttribute("activeTab", "admin-vms");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>All VMs &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(adminUser) %>">
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>

<main>
  <div id="stats" class="summary" aria-live="polite"></div>

  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>ALL VMS</h2>
        <span class="sub">Every virtual machine across all users and projects</span>
      </div>
      <div class="card-head-actions">
        <span id="freshness" class="freshness"></span>
        <button type="button" class="secondary" id="refreshBtn">Refresh</button>
      </div>
    </div>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

    <div class="toolbar">
      <div class="fields">
        <label>SEARCH
          <input type="search" id="search" placeholder="Name, owner or project">
        </label>
        <label>STATUS
          <select id="statusFilter">
            <option value="">All statuses</option>
            <option value="running">Running</option>
            <option value="creating">Creating</option>
            <option value="stopped">Stopped</option>
            <option value="error">Error</option>
          </select>
        </label>
      </div>
    </div>

    <div class="table-shell">
      <table>
        <thead>
          <tr>
            <th><button type="button" class="sort-btn" data-sort="vmName">VM NAME</button></th>
            <th><button type="button" class="sort-btn" data-sort="userId">OWNER</button></th>
            <th><button type="button" class="sort-btn" data-sort="status">STATUS</button></th>
            <th><button type="button" class="sort-btn" data-sort="projectName">PROJECT</button></th>
            <th>DETAILS</th>
          </tr>
        </thead>
        <tbody id="vmBody">
          <tr><td colspan="5" class="empty">Loading...</td></tr>
        </tbody>
      </table>
    </div>

    <div class="pager">
      <span class="meta" id="pagerMeta"></span>
      <div class="action-row">
        <button type="button" class="secondary" id="prevPage">Previous</button>
        <button type="button" class="secondary" id="nextPage">Next</button>
      </div>
    </div>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  const PAGE_SIZE = 25;
  let allVms = [];
  let lastPayload = '';
  let query = '';
  let statusFilter = '';
  let sortKey = 'vmName';
  let sortDir = 'asc';
  let page = 1;

  function summaryCard(label, value, meta) {
    return '<div class="summary-card"><span class="label">' + label +
      '</span><span class="num">' + value +
      '</span><span class="meta">' + meta + '</span></div>';
  }

  function renderSummary() {
    const running = allVms.filter(v => v.status === 'running').length;
    const stopped = allVms.filter(v => v.status === 'stopped').length;
    const errors = allVms.filter(v => v.status === 'error').length;
    document.getElementById('stats').innerHTML =
      summaryCard('Total VMs', String(allVms.length), 'Across all users') +
      summaryCard('Running', String(running), 'Healthy now') +
      summaryCard('Stopped', String(stopped), 'Not running') +
      summaryCard('Errors', String(errors), 'Needs attention');
  }

  function filteredVms() {
    const q = query.toLowerCase();
    return allVms.filter(function (vm) {
      if (statusFilter && vm.status !== statusFilter) return false;
      if (!q) return true;
      return String(vm.vmName || '').toLowerCase().includes(q)
        || String(vm.userId || '').toLowerCase().includes(q)
        || String(vm.projectName || '').toLowerCase().includes(q);
    }).sort(function (a, b) {
      const av = String(a[sortKey] || '').toLowerCase();
      const bv = String(b[sortKey] || '').toLowerCase();
      return av.localeCompare(bv) * (sortDir === 'asc' ? 1 : -1);
    });
  }

  function renderTable() {
    const rows = filteredVms();
    const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
    if (page > pages) page = pages;
    const visible = rows.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
    const body = document.getElementById('vmBody');

    if (visible.length === 0) {
      body.innerHTML = '<tr><td colspan="5" class="empty">' +
        (allVms.length === 0 ? 'No VMs have been provisioned yet.' : 'No VMs match these filters.') +
        '</td></tr>';
    } else {
      body.innerHTML = visible.map(function (vm) {
        return `
        <tr>
          <td>\${Portal.esc(vm.vmName)}</td>
          <td>\${Portal.esc(vm.userId)}</td>
          <td>\${Portal.badge(vm.status)}</td>
          <td>\${Portal.esc(vm.projectName)}</td>
          <td><a class="link-pill" href="vm-details.jsp?id=\${encodeURIComponent(vm.vmId)}&from=admin"
                 onclick="return Portal.openMini(this.href, 1000, 780)">View details</a></td>
        </tr>`;
      }).join('');
    }

    document.getElementById('pagerMeta').textContent =
      rows.length + ' VM' + (rows.length === 1 ? '' : 's') + ' · Page ' + page + ' of ' + pages;
    document.getElementById('prevPage').disabled = page <= 1;
    document.getElementById('nextPage').disabled = page >= pages;

    document.querySelectorAll('.sort-btn').forEach(function (btn) {
      btn.classList.toggle('asc', btn.dataset.sort === sortKey && sortDir === 'asc');
      btn.classList.toggle('desc', btn.dataset.sort === sortKey && sortDir === 'desc');
      const th = btn.closest('th');
      if (th) {
        if (btn.dataset.sort === sortKey) th.setAttribute('aria-sort', sortDir === 'asc' ? 'ascending' : 'descending');
        else th.removeAttribute('aria-sort');
      }
    });
  }

  function renderAll() {
    renderSummary();
    renderTable();
  }

  async function loadVms(manual) {
    if (!manual && document.hidden) return;
    try {
      const { status, body } = await Portal.get('admin/vm/list');
      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status === 403) { window.location.href = 'vm-dashboard.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || 'Could not load VMs', 'err');
        return;
      }
      Portal.hide('msg');
      const payload = JSON.stringify(body.vms || []);
      if (payload !== lastPayload) {
        allVms = body.vms || [];
        lastPayload = payload;
        renderAll();
      }
      Portal.freshness('freshness');
    } catch (e) {
      Portal.show('msg', 'Could not reach the server. Check your connection and try again.', 'err');
    }
  }

  document.getElementById('search').addEventListener('input', function (e) {
    query = e.target.value;
    page = 1;
    renderTable();
  });
  document.getElementById('statusFilter').addEventListener('change', function (e) {
    statusFilter = e.target.value;
    page = 1;
    renderTable();
  });
  document.querySelectorAll('.sort-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      const key = btn.dataset.sort;
      if (sortKey === key) sortDir = sortDir === 'asc' ? 'desc' : 'asc';
      else { sortKey = key; sortDir = 'asc'; }
      renderTable();
    });
  });
  document.getElementById('prevPage').addEventListener('click', function () {
    if (page > 1) { page--; renderTable(); }
  });
  document.getElementById('nextPage').addEventListener('click', function () {
    page++; renderTable();
  });
  document.getElementById('refreshBtn').addEventListener('click', function () { loadVms(true); });
  window.portalRefresh = function () { loadVms(true); };
  Portal.poll(function () { return loadVms(false); }, 15000);
</script>
</body>
</html>
