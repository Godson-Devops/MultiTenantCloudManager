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
import java.util.Map;

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

        if (!userDao.reserveVmSlot(userId)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "VM quota exceeded");
            return;
        }

        VmDetails created;
        try {
            created = ServiceRegistry.openStack()
                    .createVm(vmName, image, flavor, WebUtil.param(request, "keyPair"), projectName);
        } catch (RuntimeException e) {

            userDao.releaseVmSlot(userId);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "OpenStack VM creation", e);
            return;
        }

        created.setUserId(userId);
        try {
            vmDao.insert(created);
        } catch (RuntimeException e) {
            rollbackQuota(created);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not record the new VM, please try again");
            return;
        }

        sendOk(response, Map.of(
                "vmId", created.getVmId(),
                "status", created.getStatus().code()));
    }

    private void rollbackQuota(VmDetails created) {
        String userId = created.getUserId();
        userDao.releaseVmSlot(userId);
        try {
            ServiceRegistry.openStack().delete(created.getVmId());
        } catch (RuntimeException ignored) {

            getServletContext().log("Orphaned OpenStack VM " + created.getVmId(), ignored);
        }
    }
}
