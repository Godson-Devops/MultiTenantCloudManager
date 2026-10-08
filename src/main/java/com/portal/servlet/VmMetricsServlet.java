package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.service.PrometheusService;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VmMetricsServlet extends BaseServlet {

    private static final String UNAVAILABLE = "metrics unavailable";

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
        body.put("vmId", vmId);

        String vmIp = vm.getVmIp();
        if (vmIp == null || vmIp.isBlank()) {
            body.put("error", UNAVAILABLE);
            body.put("reason", "VM has no private IP yet");
            sendOk(response, body);
            return;
        }

        PrometheusService prometheus = ServiceRegistry.prometheus();
        List<double[]> cpu = prometheus.queryVmCpu(vmIp);
        List<double[]> memory = prometheus.queryVmMemory(vmIp);
        List<double[]> disk = prometheus.queryVmDisk(vmIp);
        List<double[]> network = prometheus.queryVmNetwork(vmIp);

        body.put("instance", prometheus.nodeExporterInstance(vmIp));
        body.put("cpu", cpu);
        body.put("memory", memory);
        body.put("disk", disk);
        body.put("network", network);
        body.put("up", prometheus.queryVmUp(vmIp));

        if (cpu.isEmpty() && memory.isEmpty() && disk.isEmpty() && network.isEmpty()) {
            body.put("error", UNAVAILABLE);
        }
        sendOk(response, body);
    }
}
