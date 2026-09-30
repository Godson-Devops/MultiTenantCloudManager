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

/** START / STOP / RESTART / DELETE for a VM the caller owns. */
public class VmActionServlet extends BaseServlet {

    private final VmDao vmDao = new VmDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);

        String vmId = WebUtil.param(request, "id");
        String action = WebUtil.param(request, "action");

        if (vmId == null || action == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id and action are required");
            return;
        }

        VmDetails vm = vmDao.findByIdAndUser(vmId, userId);
        if (!requireOwner(vm == null ? null : vm.getUserId(), userId, response)) {
            return;
        }

        try {
            switch (action.toLowerCase()) {
                case "start" -> {
                    ServiceRegistry.openStack().start(vmId);
                    vmDao.updateStatus(vmId, userId, "running");
                }
                case "stop" -> {
                    ServiceRegistry.openStack().stop(vmId);
                    vmDao.updateStatus(vmId, userId, "stopped");
                }
                case "restart" -> {
                    ServiceRegistry.openStack().restart(vmId);
                    // A soft reboot leaves the VM ACTIVE in Nova; reflect that
                    // rather than a state the UI has no vocabulary for.
                    vmDao.updateStatus(vmId, userId, "running");
                }
                case "delete" -> {
                    ServiceRegistry.openStack().delete(vmId);
                    if (vmDao.delete(vmId, userId)) {
                        userDao.releaseVmSlot(userId);
                    }
                }
                default -> {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                            "Unknown action: " + action);
                    return;
                }
            }
        } catch (RuntimeException e) {
            getServletContext().log("OpenStack action " + action + " failed for " + vmId, e);
            vmDao.updateStatus(vmId, userId, "error");
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "OpenStack VM " + action, e);
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        body.put("vmId", vmId);
        body.put("status", "delete".equalsIgnoreCase(action) ? "deleted" : "ok");
        sendOk(response, body);
    }
}
