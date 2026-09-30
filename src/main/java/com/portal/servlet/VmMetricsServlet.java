package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.service.PrometheusService;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns Prometheus series for a VM the caller owns, shaped for Chart.js.
 *
 * When Prometheus is down every series comes back empty and the response
 * carries an "error" flag, so the JSP renders "No data" rather than breaking.
 */
public class VmMetricsServlet extends BaseServlet {

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

        Map<String, Object> body = new HashMap<>();
        body.put("vmId", vmId);

        String vmIp = vm.getVmIp();
        if (vmIp == null || vmIp.isBlank()) {
            body.put("error", "metrics unavailable");
            body.put("reason", "VM has no private IP yet");
            sendOk(response, body);
            return;
        }

        PrometheusService prometheus = com.portal.service.ServiceRegistry.prometheus();
        List<double[]> cpu = prometheus.queryVmCpu(vmIp);
        List<double[]> memory = prometheus.queryVmMemory(vmIp);
        List<double[]> disk = prometheus.queryVmDisk(vmIp);
        List<double[]> network = prometheus.queryVmNetwork(vmIp);
        List<double[]> up = prometheus.queryVmUp(vmIp);

        body.put("instance", vmIp + ":9100");
        body.put("cpu", cpu);
        body.put("memory", memory);
        body.put("disk", disk);
        body.put("network", network);
        body.put("up", up);

        if (cpu.isEmpty() && memory.isEmpty() && disk.isEmpty() && network.isEmpty()) {
            body.put("error", "metrics unavailable");
        }
        sendOk(response, body);
    }
}
