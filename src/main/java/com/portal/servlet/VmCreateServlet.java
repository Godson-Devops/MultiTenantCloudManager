package com.portal.servlet;

import com.portal.dao.UserDao;
import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Creates a VM after reserving a quota slot. */
public class VmCreateServlet extends BaseServlet {

    private final VmDao vmDao = new VmDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);

        String vmName = WebUtil.param(request, "vmName");
        String image = WebUtil.param(request, "image");
        String flavor = WebUtil.param(request, "flavor");
        String projectName = WebUtil.param(request, "projectName");
        String keyPair = WebUtil.param(request, "keyPair");

        if (vmName == null || image == null || flavor == null || projectName == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "vmName, image, flavor and projectName are required");
            return;
        }

        if (vmDao.existsByName(userId, vmName)) {
            sendError(response, HttpServletResponse.SC_CONFLICT,
                    "You already have a VM named " + vmName);
            return;
        }

        // Reserve the quota slot first. This is an atomic check-and-increment,
        // so two simultaneous requests cannot both slip past the limit.
        if (!userDao.reserveVmSlot(userId)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "VM quota exceeded");
            return;
        }

        VmDetails created;
        try {
            created = ServiceRegistry.openStack()
                    .createVm(vmName, image, flavor, keyPair, projectName);
        } catch (RuntimeException e) {
            // Release the reserved slot, otherwise a failed create would
            // permanently consume quota.
            userDao.releaseVmSlot(userId);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "OpenStack VM creation", e);
            return;
        }

        created.setUserId(userId);
        created.setStatus(WebUtil.mapOpenStackStatus(created.getStatus()));
        try {
            vmDao.insert(created);
        } catch (RuntimeException e) {
            // Roll back the slot and best-effort delete the orphaned VM so we
            // do not leak a real VM the portal no longer tracks.
            userDao.releaseVmSlot(userId);
            try {
                ServiceRegistry.openStack().delete(created.getVmId());
            } catch (RuntimeException ignored) {
                // Best effort. There is no DB row, so the sync worker will not
                // clean this up -- log it so it is at least traceable.
                getServletContext().log("Orphaned OpenStack VM " + created.getVmId(), ignored);
            }
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not record the new VM, please try again");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("vmId", created.getVmId());
        body.put("status", created.getStatus());
        body.put("message", "creating");
        sendOk(response, body);
    }
}
