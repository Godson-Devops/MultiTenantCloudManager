package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import com.portal.service.PrometheusService;
import com.portal.service.ServiceRegistry;
import com.portal.util.AdminUsers;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VmMetricsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final String UNAVAILABLE = "metrics unavailable";

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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vmId", vmId);

        String vmIp = vm.getVmIp();
        String floatIp = null;
        try {
            floatIp = vm.getFloatIp();
        } catch (Exception ignored) {
        }
        String scrapeIp = null;
        if (vmIp != null && !vmIp.isBlank()) {
            scrapeIp = vmIp;
        } else if (floatIp != null && !floatIp.isBlank()) {
            scrapeIp = floatIp;
        }
        if (scrapeIp == null || scrapeIp.isBlank()) {
            body.put("error", UNAVAILABLE);
            body.put("reason", "VM has no IP yet");
            sendOk(response, body);
            return;
        }

        long windowSeconds = PrometheusService.parseWindow(param(request, "range"));
        PrometheusService prometheus = ServiceRegistry.prometheus();
        List<double[]> cpu = prometheus.queryVmCpu(scrapeIp, windowSeconds);
        List<double[]> memory = prometheus.queryVmMemory(scrapeIp, windowSeconds);
        List<double[]> disk = prometheus.queryVmDisk(scrapeIp, windowSeconds);
        List<double[]> network = prometheus.queryVmNetwork(scrapeIp, windowSeconds);

        body.put("windowSeconds", windowSeconds);
        body.put("instance", prometheus.nodeExporterInstance(scrapeIp));
        body.put("cpu", cpu);
        body.put("memory", memory);
        body.put("disk", disk);
        body.put("network", network);
        body.put("up", prometheus.queryVmUp(scrapeIp));

        if (cpu.isEmpty() && memory.isEmpty() && disk.isEmpty() && network.isEmpty()) {
            body.put("error", UNAVAILABLE);
        }
        sendOk(response, body);
    }
}
