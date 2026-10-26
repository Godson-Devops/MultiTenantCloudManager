<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object detailUser = session.getAttribute("user_id");
  if (detailUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  String vmId = request.getParameter("id");
  boolean popup = "1".equals(request.getParameter("popup"));
  boolean adminContext = "admin".equals(request.getParameter("from"));
  request.setAttribute("pageHeading", "VM Details");
  request.setAttribute("activeTab", "vms");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>VM Details &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(detailUser) %>">
<% if (!popup) { %>
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>
<% } %>

<main>
  <div class="card">
    <div class="card-head">
      <div class="titles">
        <h2>VM DETAILS</h2>
        <span class="sub">Live state, metadata and network information for this machine</span>
      </div>
      <div class="card-head-actions">
        <span id="freshness" class="freshness"></span>
        <button type="button" class="secondary" id="refreshBtn">Refresh</button>
      </div>
    </div>

    <% if (adminContext) { %>
      <div class="inline-help warn">Administrator view. Actions below affect the owner's VM.</div>
    <% } %>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>

    <div class="grid-2">
      <div><div class="metric-label">VM NAME</div><div id="vmName">&mdash;</div></div>
      <div><div class="metric-label">STATUS</div><div id="status">&mdash;</div></div>
      <div><div class="metric-label">OWNER</div><div id="owner">&mdash;</div></div>
      <div><div class="metric-label">PROJECT</div><div id="projectName">&mdash;</div></div>
      <div><div class="metric-label">FLAVOR</div><div id="flavor">&mdash;</div></div>
      <div><div class="metric-label">IMAGE</div><div id="image">&mdash;</div></div>
      <div>
        <div class="metric-label">PRIVATE IP</div>
        <div class="value-copy"><span id="vmIp">&mdash;</span>
          <button type="button" class="copy-btn secondary" data-copy="vmIp" hidden>Copy</button>
        </div>
      </div>
      <div>
        <div class="metric-label">FLOATING IP</div>
        <div class="value-copy"><span id="floatIp">&mdash;</span>
          <button type="button" class="copy-btn secondary" data-copy="floatIp" hidden>Copy</button>
        </div>
      </div>
      <div><div class="metric-label">KEY PAIR NAME</div><div id="sshKey">&mdash;</div></div>
    </div>

    <div class="action-row" style="margin-top:20px">
      <a id="metricsLink" class="btn secondary" href="#"
         onclick="return Portal.openMini(this.href, 1100, 820)">VIEW METRICS</a>
      <button type="button" class="secondary" data-action="start">START</button>
      <button type="button" class="secondary" data-action="restart">RESTART</button>
      <button type="button" class="secondary" data-action="stop">STOP</button>
      <button type="button" class="danger" data-action="delete">DELETE</button>
    </div>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  const VM_ID = <%= vmId == null ? "null" : "\"" + vmId.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace("\n", " ").replace("\r", " ") + "\"" %>;
  const ADMIN_CONTEXT = <%= adminContext ? "true" : "false" %>;
  let currentStatus = null;

  function updateActionButtons(status) {
    const creating = status === Portal.STATUS.CREATING;
    const running = status === Portal.STATUS.RUNNING;
    const stopped = status === Portal.STATUS.STOPPED;
    const errored = status === Portal.STATUS.ERROR;
    const start = document.querySelector('button[data-action="start"]');
    const stop = document.querySelector('button[data-action="stop"]');
    const restart = document.querySelector('button[data-action="restart"]');
    start.disabled = creating || running;
    stop.disabled = creating || stopped || errored;
    restart.disabled = creating;
    start.title = creating ? 'Wait until the VM finishes creating' : '';
    stop.title = creating ? 'Wait until the VM finishes creating' : '';
    restart.title = creating ? 'Wait until the VM finishes creating' : '';
  }

  function setField(id, value) {
    document.getElementById(id).textContent = value || '-';
  }

  function updateCopyButtons() {
    document.querySelectorAll('button[data-copy]').forEach(function (btn) {
      const value = document.getElementById(btn.dataset.copy).textContent.trim();
      btn.hidden = !value || value === '—' || value === '-';
    });
  }

  async function loadVm(manual) {
    if (!manual && document.hidden) return;
    if (!VM_ID) {
      Portal.show('msg', 'No VM id supplied.', 'err');
      return;
    }

    try {
      const { status, body } = await Portal.get('vm/details?id=' + encodeURIComponent(VM_ID));
      if (status === 401) { window.location.href = 'login.jsp'; return; }
      if (status !== 200) {
        Portal.show('msg', body.error || 'Could not load this VM.', 'err');
        return;
      }

      Portal.hide('msg');
      setField('vmName', body.vmName);
      document.getElementById('status').innerHTML = Portal.badge(body.status);
      currentStatus = body.status;
      updateActionButtons(currentStatus);
      setField('owner', body.userId);
      setField('projectName', body.projectName);
      setField('flavor', body.flavor);
      setField('image', body.image);
      setField('vmIp', body.vmIp);
      setField('floatIp', body.floatIp);
      setField('sshKey', body.sshKey);
      updateCopyButtons();

      document.getElementById('metricsLink').href =
        'vm-monitor.jsp?id=' + encodeURIComponent(VM_ID) + (ADMIN_CONTEXT ? '&from=admin' : '');

      if (body.live === false) {
        Portal.show('msg', 'Live OpenStack data is temporarily unavailable. Showing the last known state.', 'warn');
      }
      Portal.freshness('freshness');
    } catch (e) {
      Portal.show('msg', 'Could not reach the server. Showing the last known state.', 'err');
    }
  }

  document.querySelectorAll('button[data-copy]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      Portal.copy(document.getElementById(btn.dataset.copy).textContent.trim());
    });
  });

  document.querySelectorAll('button[data-action]').forEach(function (btn) {
    btn.addEventListener('click', async function () {
      const action = btn.dataset.action;
      if (action === 'delete') {
        const confirmed = await Portal.confirmAction({
          title: 'Delete this VM?',
          message: 'This permanently deletes the VM and releases its quota slot. This cannot be undone.',
          confirmLabel: 'Delete VM',
          danger: true
        });
        if (!confirmed) return;
      }

      Portal.busy(btn, Portal.actionLabel(action) + '...');
      Portal.hide('msg');
      let result;
      try {
        result = await Portal.post('vm/action', { id: VM_ID, action: action });
      } catch (e) {
        Portal.resetBusy(btn);
        updateActionButtons(currentStatus);
        Portal.show('msg', 'Could not reach the server. The action was not submitted.', 'err');
        return;
      }

      if (result.status === 401) { window.location.href = 'login.jsp'; return; }
      if (result.status !== 200) {
        Portal.resetBusy(btn);
        updateActionButtons(currentStatus);
        Portal.show('msg', result.body.error || ('Action ' + action + ' failed.'), 'err');
        return;
      }

      if (action === 'delete') {
        const fallback = ADMIN_CONTEXT ? 'admin-vm-dashboard.jsp' : 'vm-dashboard.jsp';
        if (Portal.inPanel()) Portal.closePanel(true, 'VM deleted.', fallback);
        else window.location.href = fallback;
        return;
      }

      Portal.resetBusy(btn);
      Portal.show('msg', Portal.actionLabel(action) + ' requested. The status updates automatically.', 'ok');
      setTimeout(function () { loadVm(true); }, 1200);
    });
  });

  document.getElementById('refreshBtn').addEventListener('click', function () { loadVm(true); });
  Portal.poll(function () { return loadVm(false); }, 15000);
</script>
</body>
</html>
