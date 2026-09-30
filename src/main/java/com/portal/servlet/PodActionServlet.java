package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.dao.UserDao;
import com.portal.model.PodDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** RESTART / STOP / DELETE for a pod the caller owns. */
public class PodActionServlet extends BaseServlet {

    private final PodDao podDao = new PodDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);

        String podId = WebUtil.param(request, "id");
        String action = WebUtil.param(request, "action");

        if (podId == null || action == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id and action are required");
            return;
        }

        PodDetails pod = podDao.findByIdAndUser(podId, userId);
        if (!requireOwner(pod == null ? null : pod.getUserId(), userId, response)) {
            return;
        }

        try {
            switch (action.toLowerCase()) {
                case "restart" -> restartPod(pod, userId, response);
                case "stop", "delete" -> {
                    // Kubernetes has no stop for a bare Pod -- there is no
                    // scale to zero without an owning Deployment -- so both
                    // map to delete. See section N of the spec.
                    ServiceRegistry.kubernetes().delete(pod.getNamespace(), pod.getPodName());
                    if (podDao.delete(podId, userId)) {
                        userDao.releasePodSlot(userId);
                    }
                }
                default -> {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                            "Unknown action: " + action);
                    return;
                }
            }
        } catch (RuntimeException e) {
            getServletContext().log("Kubernetes action " + action + " failed for " + podId, e);
            podDao.updateStatus(podId, userId, "error");
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Kubernetes pod " + action, e);
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        body.put("podId", podId);
        body.put("status", "delete".equalsIgnoreCase(action)
                || "stop".equalsIgnoreCase(action) ? "deleted" : "ok");
        sendOk(response, body);
    }

    /**
     * Recreates the pod with the same spec, preserving the original UID where
     * the cluster allows it. Quota is not touched: the slot is released only
     * when the row itself is removed.
     */
    private void restartPod(PodDetails pod, String userId, HttpServletResponse response)
            throws IOException {
        ServiceRegistry.kubernetes().delete(pod.getNamespace(), pod.getPodName());

        PodDetails recreated = ServiceRegistry.kubernetes().createPod(
                pod.getPodName(),
                pod.getNamespace(),
                pod.getImage(),
                pod.getPodPort());
        recreated.setUserId(userId);
        recreated.setStatus(WebUtil.mapPodPhase(recreated.getStatus()));

        if (podDao.delete(pod.getPodId(), userId)) {
            podDao.insert(recreated);
        }
    }
}
