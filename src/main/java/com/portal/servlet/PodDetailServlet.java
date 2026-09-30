package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Returns one pod's detail card with live phase, IP and restart count. */
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

        Map<String, Object> body = new HashMap<>();
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
            Pod live = ServiceRegistry.kubernetes()
                    .get(pod.getNamespace(), pod.getPodName());
            if (live == null) {
                body.put("status", "error");
            } else {
                if (live.getStatus() != null && live.getStatus().getPhase() != null) {
                    body.put("status", WebUtil.mapPodPhase(live.getStatus().getPhase()));
                }
                if (live.getStatus() != null && live.getStatus().getPodIP() != null) {
                    body.put("podIp", live.getStatus().getPodIP());
                }
                if (live.getSpec() != null && live.getSpec().getNodeName() != null) {
                    body.put("nodeName", live.getSpec().getNodeName());
                }
                body.put("restartCount", ServiceRegistry.kubernetes().restartCount(live));
            }
        } catch (RuntimeException e) {
            getServletContext().log("Kubernetes refresh failed for " + podId, e);
            body.put("live", false);
        }

        sendOk(response, body);
    }
}
