package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.dao.UserDao;
import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.service.ServiceRegistry;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PodActionServlet extends BaseServlet {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of("restart", "stop", "delete");

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
        String verb = action.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.contains(verb)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
            return;
        }

        PodDetails pod = podDao.findByIdAndUser(podId, userId);
        if (!requireOwner(pod == null ? null : pod.getUserId(), userId, response)) {
            return;
        }

        Status status;
        try {
            status = "restart".equals(verb)
                    ? restartPod(pod, userId)
                    : deletePod(pod, userId);
        } catch (RuntimeException e) {
            getServletContext().log("Kubernetes action " + verb + " failed for " + podId, e);
            podDao.updateStatus(podId, userId, Status.ERROR);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Kubernetes pod " + verb, e);
            return;
        }

        sendOk(response, Map.of(
                "action", verb,
                "podId", podId,
                "status", status.code()));
    }

    private Status restartPod(PodDetails pod, String userId) {
        var kubernetes = ServiceRegistry.kubernetes();
        kubernetes.delete(pod.getNamespace(), pod.getPodName());

        PodDetails recreated = kubernetes.createPod(
                pod.getPodName(), pod.getNamespace(), pod.getImage(), pod.getPodPort());
        recreated.setUserId(userId);

        if (podDao.delete(pod.getPodId(), userId)) {
            podDao.insert(recreated);
        }
        return recreated.getStatus();
    }

    private Status deletePod(PodDetails pod, String userId) {
        ServiceRegistry.kubernetes().delete(pod.getNamespace(), pod.getPodName());
        if (podDao.delete(pod.getPodId(), userId)) {
            userDao.releasePodSlot(userId);
        }
        return Status.DELETED;
    }
}
