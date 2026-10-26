<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jspf/csrf.jspf" %>
<%
  Object createUser = session.getAttribute("user_id");
  if (createUser == null) {
    response.sendRedirect("login.jsp");
    return;
  }
  request.setAttribute("pageHeading", "Create VM");
  request.setAttribute("activeTab", "vms");
%>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="csrf-token" content="<%= csrfToken %>">
  <title>Create VM &middot; Cloud Provisioning Portal</title>
  <link rel="stylesheet" href="css/portal.css?v=white2">
</head>
<body data-current-user="<%= com.portal.util.Html.escape(createUser) %>">
<%@ include file="/WEB-INF/jspf/topbar.jspf" %>

<main>
  <div class="card" style="max-width:720px">
    <div class="card-head">
      <div class="titles">
        <h2>CREATE VM</h2>
        <span class="sub">Launch a new OpenStack instance</span>
      </div>
      <div class="card-head-actions">
        <button type="button" class="secondary" id="cancelBtn">Cancel</button>
      </div>
    </div>

    <div id="msg" class="msg hidden" role="status" aria-live="polite"></div>
    <div id="progress" class="msg hidden" role="status" aria-live="polite"></div>
    <div id="quotaNotice" class="inline-help">Checking your VM quota...</div>
    <div id="optionsMsg" class="inline-help err" hidden></div>

    <form id="vmForm">
      <div class="grid-2">
        <label>VM NAME
          <input type="text" id="vmName" name="vmName" required maxlength="120"
                 placeholder="my-web-server" autocomplete="off">
        </label>
        <label>PROJECT NAME
          <input type="text" id="projectName" name="projectName" required maxlength="120"
                 placeholder="my-openstack-project" autocomplete="off">
        </label>
        <label>IMAGE NAME
          <select id="image" name="image" required>
            <option value="">Loading images...</option>
          </select>
        </label>
        <label>FLAVOR
          <select id="flavor" name="flavor" required>
            <option value="">Loading flavors...</option>
          </select>
        </label>
      </div>

      <div id="compatHint" class="inline-help warn" hidden></div>

      <label>KEY PAIR NAME <span style="color:var(--muted)">(optional)</span>
        <input type="text" id="keyPair" name="keyPair" maxlength="120"
               placeholder="my-keypair" autocomplete="off">
      </label>

      <div class="action-row">
        <button type="submit" id="submitBtn">Create VM</button>
        <button type="button" class="secondary" id="reloadOptions" hidden>Reload options</button>
      </div>
    </form>
  </div>
</main>

<script src="js/portal.js?v=5"></script>
<script>
  const GIB = 1024 * 1024 * 1024;
  const imageRequiredBytes = {};
  const flavorDiskBytes = {};
  let quota = null;
  let diskMismatch = false;

  function setCompat(message) {
    const hint = document.getElementById('compatHint');
    diskMismatch = !!message;
    hint.hidden = !message;
    hint.textContent = message || '';
    refreshSubmitState();
  }

  function refreshSubmitState() {
    const btn = document.getElementById('submitBtn');
    const quotaFull = quota && quota.used >= quota.max;
    const missingImage = !document.getElementById('image').value;
    const missingFlavor = !document.getElementById('flavor').value;
    btn.disabled = quotaFull || diskMismatch || missingImage || missingFlavor;
    btn.title = quotaFull ? 'VM quota reached'
      : diskMismatch ? 'Choose a flavor with enough disk'
      : missingImage || missingFlavor ? 'Choose an image and flavor' : '';
  }

  function renderQuota() {
    const notice = document.getElementById('quotaNotice');
    if (!quota) {
      notice.className = 'inline-help';
      notice.textContent = 'Your quota could not be loaded. The server will still enforce it when you create the VM.';
      refreshSubmitState();
      return;
    }
    const remaining = Math.max(0, quota.max - quota.used);
    const full = remaining === 0;
    notice.className = 'inline-help' + (full ? ' quota-full' : '');
    notice.textContent = full
      ? 'You have used all ' + quota.max + ' VMs in your quota. Delete a VM or ask an administrator for more capacity.'
      : quota.used + ' of ' + quota.max + ' VMs used · ' + remaining + ' available.';
    refreshSubmitState();
  }

  function resetOptionsMessage() {
    const box = document.getElementById('optionsMsg');
    box.hidden = true;
    box.textContent = '';
  }

  function optionsFailed(message) {
    const box = document.getElementById('optionsMsg');
    box.hidden = false;
    box.textContent = message + ' Use Reload options to try again.';
    document.getElementById('reloadOptions').hidden = false;
  }

  async function loadQuota() {
    try {
      const { status, body } = await Portal.get('vm/list');
      if (status === 401) { window.location.href = 'login.jsp'; return; }
      quota = status === 200 ? (body.quota || null) : null;
    } catch (e) {
      quota = null;
    }
    renderQuota();
  }

  async function loadOptions() {
    resetOptionsMessage();
    document.getElementById('reloadOptions').hidden = true;

    try {
      const res = await Portal.get('images/list');
      const select = document.getElementById('image');
      select.innerHTML = '';
      if (res.status === 200 && Array.isArray(res.body.images) && res.body.images.length) {
        res.body.images.forEach(function (img) {
          const required = Number(img.virtualSize) || Number(img.sizeBytes)
            || (Number(img.minDiskGb) * GIB) || 0;
          imageRequiredBytes[img.name] = required;
          const option = document.createElement('option');
          option.value = img.name;
          option.textContent = img.name + (required ? ' · ' + Portal.formatBytes(required) + ' disk' : '');
          select.appendChild(option);
        });
      } else {
        select.innerHTML = '<option value="">No images available</option>';
        optionsFailed(res.body.error || 'Could not load images.');
      }
    } catch (e) {
      document.getElementById('image').innerHTML = '<option value="">Failed to load images</option>';
      optionsFailed('Could not reach OpenStack to load images.');
    }

    try {
      const res = await Portal.get('flavors/list');
      const select = document.getElementById('flavor');
      select.innerHTML = '';
      if (res.status === 200 && Array.isArray(res.body.flavors) && res.body.flavors.length) {
        res.body.flavors.forEach(function (fl) {
          const diskBytes = Number(fl.diskGb) * GIB;
          flavorDiskBytes[fl.name] = diskBytes;
          const option = document.createElement('option');
          option.value = fl.name;
          option.textContent = fl.name + ' · ' + fl.vcpus + ' vCPU, '
            + (Number(fl.ramMb) / 1024).toFixed(Number(fl.ramMb) % 1024 === 0 ? 0 : 1) + ' GB RAM, '
            + fl.diskGb + ' GB disk';
          select.appendChild(option);
        });
      } else {
        select.innerHTML = '<option value="">No flavors available</option>';
        optionsFailed(res.body.error || 'Could not load flavors.');
      }
    } catch (e) {
      document.getElementById('flavor').innerHTML = '<option value="">Failed to load flavors</option>';
      optionsFailed('Could not reach OpenStack to load flavors.');
    }

    refreshSubmitState();
    validateSelection();
  }

  function validateSelection() {
    const image = document.getElementById('image').value;
    const flavor = document.getElementById('flavor').value;
    const required = imageRequiredBytes[image] || 0;
    const disk = flavorDiskBytes[flavor] || 0;
    if (required && disk && required > disk) {
      setCompat('This image needs ' + Portal.formatBytes(required)
        + ' of disk, but ' + flavor + ' only provides ' + Portal.formatBytes(disk)
        + '. Choose a larger flavor.');
    } else {
      setCompat('');
    }
  }

  document.getElementById('image').addEventListener('change', validateSelection);
  document.getElementById('flavor').addEventListener('change', validateSelection);
  document.getElementById('reloadOptions').addEventListener('click', loadOptions);
  document.getElementById('cancelBtn').addEventListener('click', function () {
    if (Portal.inPanel()) Portal.closePanel(false);
    else window.location.href = 'vm-dashboard.jsp';
  });

  document.getElementById('vmForm').addEventListener('submit', async function (e) {
    e.preventDefault();
    if (diskMismatch) return;
    if (quota && quota.used >= quota.max) {
      Portal.show('msg', 'You have reached your VM quota.', 'err');
      return;
    }

    const btn = document.getElementById('submitBtn');
    Portal.busy(btn, 'Creating...');
    Portal.hide('msg');
    Portal.hide('progress');

    let result;
    try {
      result = await Portal.post('vm/create', {
        vmName: document.getElementById('vmName').value,
        projectName: document.getElementById('projectName').value,
        image: document.getElementById('image').value,
        flavor: document.getElementById('flavor').value,
        keyPair: document.getElementById('keyPair').value
      });
    } catch (err) {
      Portal.show('msg', 'Could not reach the server. The VM was not created.', 'err');
      Portal.resetBusy(btn);
      refreshSubmitState();
      return;
    }
    const { status, body } = result;

    if (status !== 200) {
      Portal.show('msg', body.error || 'Could not create the VM', 'err');
      Portal.resetBusy(btn);
      refreshSubmitState();
      return;
    }

    Portal.show('progress', 'Creating VM… waiting for OpenStack to finish.', 'ok');
    const vmId = body.vmId;
    let attempts = 0;
    let transientErrors = 0;
    const timer = setInterval(async function () {
      attempts++;
      if (attempts > 120) {
        clearInterval(timer);
        Portal.show('progress', 'The VM is still being created. You can close this window and check the dashboard shortly.', 'ok');
        Portal.resetBusy(btn);
        return;
      }

      let res;
      try {
        res = await Portal.get('vm/details?id=' + encodeURIComponent(vmId));
      } catch (e) {
        transientErrors++;
        if (transientErrors >= 3) {
          clearInterval(timer);
          Portal.show('progress', 'Lost contact with the server while waiting for the VM. Check the dashboard shortly.', 'err');
          Portal.resetBusy(btn);
        }
        return;
      }

      if (res.status !== 200) {
        transientErrors++;
        if (transientErrors >= 3) {
          clearInterval(timer);
          Portal.show('progress', res.body.error || 'Could not check the VM status.', 'err');
          Portal.resetBusy(btn);
        }
        return;
      }
      transientErrors = 0;

      if (res.body.status === Portal.STATUS.RUNNING) {
        clearInterval(timer);
        Portal.show('progress', 'VM is running.', 'ok');
        setTimeout(function () {
          if (Portal.inPanel()) {
            Portal.closePanel(true, 'VM "' + (res.body.vmName || vmId) + '" is running.');
          } else {
            window.location.href = 'vm-details.jsp?id=' + encodeURIComponent(vmId);
          }
        }, 900);
      } else if (res.body.status === Portal.STATUS.ERROR) {
        clearInterval(timer);
        Portal.show('progress', 'The VM entered an error state. ' + (res.body.error || ''), 'err');
        Portal.resetBusy(btn);
        refreshSubmitState();
      }
    }, 5000);
  });

  loadQuota();
  loadOptions();
</script>
</body>
</html>
