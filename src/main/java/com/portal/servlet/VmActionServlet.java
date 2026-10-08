package com.portal.servlet;

import com.portal.dao.UserDao;
import com.portal.dao.VmDao;
import com.portal.model.Status;
import com.portal.model.VmDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class VmActionServlet extends BaseServlet {

    private static final Set<String> SUPPORTED_ACTIONS =
            Set.of("start", "stop", "restart", "delete");

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
        String verb = action.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.contains(verb)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
            return;
        }

        VmDetails vm = vmDao.findByIdAndUser(vmId, userId);
        if (!requireOwner(vm == null ? null : vm.getUserId(), userId, response)) {
            return;
        }

        Status status;
        try {
            status = apply(verb, vmId, userId);
        } catch (RuntimeException e) {
            getServletContext().log("OpenStack action " + verb + " failed for " + vmId, e);
            vmDao.updateStatus(vmId, userId, Status.ERROR);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "OpenStack VM " + verb, e);
            return;
        }

        sendOk(response, Map.of(
                "action", verb,
                "vmId", vmId,
                "status", status.code()));
    }

    private Status apply(String verb, String vmId, String userId) {
        switch (verb) {
            case "start":
                ServiceRegistry.openStack().start(vmId);
                return track(vmId, userId, Status.RUNNING);
            case "stop":
                ServiceRegistry.openStack().stop(vmId);
                return track(vmId, userId, Status.STOPPED);
            case "restart":
                ServiceRegistry.openStack().restart(vmId);

                return track(vmId, userId, Status.RUNNING);
            default:
                ServiceRegistry.openStack().delete(vmId);
                if (vmDao.delete(vmId, userId)) {
                    userDao.releaseVmSlot(userId);
                }
                return Status.DELETED;
        }
    }

    private Status track(String vmId, String userId, Status status) {
        vmDao.updateStatus(vmId, userId, status);
        return status;
    }
}
