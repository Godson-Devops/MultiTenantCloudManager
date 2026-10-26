package com.portal.servlet;

import com.portal.dao.UserDao;
import com.portal.dao.VmDao;
import com.portal.model.Status;
import com.portal.model.VmDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.AdminUsers;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class VmActionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final Set<String> SUPPORTED_ACTIONS =
            Set.of("start", "stop", "restart", "delete");

    private final VmDao vmDao = new VmDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }
        if (!requireCsrf(request, response)) {
            return;
        }

        String vmId = param(request, "id");
        String action = param(request, "action");

        if (vmId == null || action == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id and action are required");
            return;
        }
        String verb = action.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.contains(verb)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
            return;
        }

        boolean admin = AdminUsers.isAdmin(userId);
        VmDetails vm = admin ? vmDao.findById(vmId) : vmDao.findByIdAndUser(vmId, userId);
        if (admin) {
            if (vm == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "VM not found");
                return;
            }
        } else if (!requireOwned(vm, VmDetails::getUserId, userId, response)) {
            return;
        }

        String ownerId = vm.getUserId();

        Status status;
        try {
            status = apply(verb, vmId, ownerId);
        } catch (RuntimeException e) {
            getServletContext().log("OpenStack action " + verb + " failed for " + vmId, e);
            vmDao.updateStatus(vmId, ownerId, Status.ERROR);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "OpenStack VM " + verb, e);
            return;
        }

        sendOk(response, Map.of(
                "action", verb,
                "vmId", vmId,
                "status", status.code()));
    }

    private Status apply(String verb, String vmId, String ownerId) {
        switch (verb) {
            case "start":
                ServiceRegistry.openStack().start(vmId);
                return track(vmId, ownerId, Status.RUNNING);
            case "stop":
                ServiceRegistry.openStack().stop(vmId);
                return track(vmId, ownerId, Status.STOPPED);
            case "restart":
                ServiceRegistry.openStack().restart(vmId);

                return track(vmId, ownerId, Status.RUNNING);
            default:
                ServiceRegistry.openStack().delete(vmId);
                if (vmDao.delete(vmId, ownerId)) {
                    userDao.releaseVmSlot(ownerId);
                }
                return Status.DELETED;
        }
    }

    private Status track(String vmId, String ownerId, Status status) {
        vmDao.updateStatus(vmId, ownerId, status);
        return status;
    }
}
