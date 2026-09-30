package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import com.portal.service.PrometheusService;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Prometheus cAdvisor series for a pod the caller owns. */
public class PodMetricsServlet extends BaseServlet {

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);
        String podId = WebUtil.param(request, "id");

        if (podId == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id is required");
            return;
        }

        PodDetails pod = podDao.findByIdAndUser(podId, userId);
        if (!requireOwner(pod == null ? null : pod.getUserId(), userId, response)) {
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("podId", podId);
        body.put("podName", pod.getPodName());
        body.put("namespace", pod.getNamespace());

        PrometheusService prometheus = ServiceRegistry.prometheus();
        List<double[]> cpu = prometheus.queryPodCpu(pod.getPodName(), pod.getNamespace());
        List<double[]> memory = prometheus.queryPodMemory(pod.getPodName(), pod.getNamespace());
        List<double[]> network =
                prometheus.queryPodNetwork(pod.getPodName(), pod.getNamespace());

        body.put("cpu", cpu);
        body.put("memory", memory);
        body.put("network", network);

        if (cpu.isEmpty() && memory.isEmpty() && network.isEmpty()) {
            body.put("error", "metrics unavailable");
        }
        sendOk(response, body);
    }
}
