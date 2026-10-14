package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.service.ServiceRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public class PodDetailServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

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

        PodDetails pod = podDao.findByIdAndUser(podId, userId);
        if (!requireOwned(pod, PodDetails::getUserId, userId, response)) {
            return;
        }

        Map<String, Object> body = pod.toMap();

        try {
            PodDetails live = ServiceRegistry.kubernetes().get(pod.getNamespace(), pod.getPodName());
            if (live != null) {
                body.put("status", live.getStatus());
                if (live.getPodIp() != null) {
                    body.put("podIp", live.getPodIp());
                }
                if (live.getNodeName() != null) {
                    body.put("nodeName", live.getNodeName());
                }
            } else if (pod.getStatus() != Status.STOPPED) {
                body.put("status", Status.ERROR);
            }
        } catch (RuntimeException e) {
            getServletContext().log("Kubernetes refresh failed for " + podId, e);
            body.put("live", false);
        }

        sendOk(response, body);
    }
}
