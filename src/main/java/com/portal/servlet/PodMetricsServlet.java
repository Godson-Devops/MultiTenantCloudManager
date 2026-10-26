package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
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

public class PodMetricsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final String UNAVAILABLE = "metrics unavailable";

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }
        String podId = requireId(request, response);
        if (podId == null) {
            return;
        }

        boolean admin = AdminUsers.isAdmin(userId);
        PodDetails pod = admin ? podDao.findById(podId) : podDao.findByIdAndUser(podId, userId);
        if (admin) {
            if (pod == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Pod not found");
                return;
            }
        } else if (!requireOwned(pod, PodDetails::getUserId, userId, response)) {
            return;
        }

        long windowSeconds = PrometheusService.parseWindow(param(request, "range"));
        PrometheusService prometheus = ServiceRegistry.prometheus();
        List<double[]> cpu = prometheus.queryPodCpu(pod.getPodName(), pod.getNamespace(), windowSeconds);
        List<double[]> memory = prometheus.queryPodMemory(pod.getPodName(), pod.getNamespace(), windowSeconds);
        List<double[]> network = prometheus.queryPodNetwork(pod.getPodName(), pod.getNamespace(), windowSeconds);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("windowSeconds", windowSeconds);
        body.put("podId", podId);
        body.put("podName", pod.getPodName());
        body.put("namespace", pod.getNamespace());
        body.put("cpu", cpu);
        body.put("memory", memory);
        body.put("network", network);

        if (cpu.isEmpty() && memory.isEmpty() && network.isEmpty()) {
            body.put("error", UNAVAILABLE);
        }
        sendOk(response, body);
    }
}
