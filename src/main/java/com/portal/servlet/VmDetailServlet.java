package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.service.OpenStackService;
import com.portal.service.ServiceRegistry;
import com.portal.util.AdminUsers;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public class VmDetailServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final VmDao vmDao = new VmDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }
        String vmId = requireId(request, response);
        if (vmId == null) {
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

        Map<String, Object> body = vm.toMap();

        try {
            OpenStackService openStack = ServiceRegistry.openStack();
            var server = openStack.refreshServer(vmId);
            body.put("status", OpenStackService.statusOf(server));
            if (server != null) {
                String privateIp = OpenStackService.resolvePrivateIp(server);
                if (privateIp != null) {
                    body.put("vmIp", privateIp);
                    vm.setVmIp(privateIp);
                }
                String floating = openStack.findFloatingIp(vmId, privateIp != null ? privateIp : vm.getVmIp());
                if (floating != null) {
                    body.put("floatIp", floating);
                }
            }
        } catch (RuntimeException e) {
            getServletContext().log("OpenStack refresh failed for " + vmId, e);
            body.put("live", false);
        }

        sendOk(response, body);
    }
}
