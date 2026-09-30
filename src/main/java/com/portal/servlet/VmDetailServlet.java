package com.portal.servlet;

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

/**
 * Returns one VM's detail card, going live to OpenStack for fresh IPs while
 * still enforcing ownership against the database row.
 */
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

        // Scoped lookup: findByIdAndUser filters on both fields, so another
        // user's VM simply is not found and we answer 403 below.
        VmDetails vm = vmDao.findByIdAndUser(vmId, userId);
        if (!requireOwner(vm == null ? null : vm.getUserId(), userId, response)) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("vmId", vm.getVmId());
        body.put("vmName", vm.getVmName());
        body.put("status", vm.getStatus());
        body.put("vmIp", vm.getVmIp());
        body.put("floatIp", vm.getFloatIp());
        body.put("sshKey", vm.getSshKey());
        body.put("projectName", vm.getProjectName());
        body.put("flavor", vm.getFlavor());
        body.put("image", vm.getImage());

        // Refresh live values, but never fail the request over it: the cached
        // row is still useful when OpenStack is unreachable.
        try {
            var server = ServiceRegistry.openStack().refreshServer(vmId);
            if (server == null) {
                body.put("status", "error");
            } else {
                if (server.getStatus() != null) {
                    body.put("status", WebUtil.mapOpenStackStatus(server.getStatus().name()));
                }
                String floating = ServiceRegistry.openStack().findFloatingIp(vmId, vm.getVmIp());
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
