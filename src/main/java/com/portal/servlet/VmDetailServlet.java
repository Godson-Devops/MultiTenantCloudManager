package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.service.OpenStackService;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class VmDetailServlet extends BaseServlet {

    private final VmDao vmDao = new VmDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);
        String vmId = WebUtil.param(request, "id");

        if (vmId == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id is required");
            return;
        }

        VmDetails vm = vmDao.findByIdAndUser(vmId, userId);
        if (!requireOwner(vm == null ? null : vm.getUserId(), userId, response)) {
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vmId", vm.getVmId());
        body.put("vmName", vm.getVmName());
        body.put("status", vm.getStatus());
        body.put("vmIp", vm.getVmIp());
        body.put("floatIp", vm.getFloatIp());
        body.put("sshKey", vm.getSshKey());
        body.put("projectName", vm.getProjectName());
        body.put("flavor", vm.getFlavor());
        body.put("image", vm.getImage());

        try {
            OpenStackService openStack = ServiceRegistry.openStack();
            var server = openStack.refreshServer(vmId);
            body.put("status", OpenStackService.statusOf(server));
            if (server != null) {
                String floating = openStack.findFloatingIp(vmId, vm.getVmIp());
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
