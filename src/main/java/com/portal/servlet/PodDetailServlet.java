package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import com.portal.service.KubernetesService;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PodDetailServlet extends BaseServlet {

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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("podId", pod.getPodId());
        body.put("podName", pod.getPodName());
        body.put("namespace", pod.getNamespace());
        body.put("status", pod.getStatus());
        body.put("podIp", pod.getPodIp());
        body.put("nodeName", pod.getNodeName());
        body.put("restartCount", pod.getRestartCount());
        body.put("podPort", pod.getPodPort());
        body.put("image", pod.getImage());

        try {
            Pod live = ServiceRegistry.kubernetes().get(pod.getNamespace(), pod.getPodName());
            body.put("status", KubernetesService.statusOf(live));
            if (live != null) {
                String ip = KubernetesService.ipOf(live);
                if (ip != null) {
                    body.put("podIp", ip);
                }
                String node = live.getSpec() == null ? null : live.getSpec().getNodeName();
                if (node != null) {
                    body.put("nodeName", node);
                }
                body.put("restartCount", KubernetesService.restartCount(live));
            }
        } catch (RuntimeException e) {
            getServletContext().log("Kubernetes refresh failed for " + podId, e);
            body.put("live", false);
        }

        sendOk(response, body);
    }
}
