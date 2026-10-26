package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.dao.UserDao;
import com.portal.model.PodDetails;
import com.portal.model.Status;
import com.portal.service.ServiceRegistry;
import com.portal.util.AdminUsers;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PodActionServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final Set<String> SUPPORTED_ACTIONS =
            Set.of("start", "stop", "restart", "delete");

    private final PodDao podDao = new PodDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }
        if (!requireCsrf(request, response)) {
            return;
        }

        String podId = param(request, "id");
        String action = param(request, "action");

        if (podId == null || action == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id and action are required");
            return;
        }
        String verb = action.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ACTIONS.contains(verb)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
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

        String ownerId = pod.getUserId();

        Status status;
        try {
            status = apply(verb, pod, ownerId);
        } catch (RuntimeException e) {
            getServletContext().log("Kubernetes action " + verb + " failed for " + podId, e);
            podDao.updateStatus(podId, ownerId, Status.ERROR);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Kubernetes pod " + verb, e);
            return;
        }

        sendOk(response, Map.of(
                "action", verb,
                "podId", podId,
                "status", status.code()));
    }

    private Status apply(String verb, PodDetails pod, String userId) {
        var kubernetes = ServiceRegistry.kubernetes();
        switch (verb) {
            case "start":
                if (kubernetes.get(pod.getNamespace(), pod.getPodName()) == null) {
                    kubernetes.createPod(pod.getPodName(), pod.getNamespace(),
                            pod.getImage(), pod.getPodPort());
                    podDao.incrementRestartCount(pod.getPodId(), userId);
                    return track(pod, userId, Status.CREATING);
                }
                return track(pod, userId, Status.RUNNING);
            case "stop":
                kubernetes.delete(pod.getNamespace(), pod.getPodName());
                return track(pod, userId, Status.STOPPED);
            case "restart":
                kubernetes.recreate(pod.getNamespace(), pod.getPodName(),
                        pod.getImage(), pod.getPodPort());
                podDao.incrementRestartCount(pod.getPodId(), userId);
                return track(pod, userId, Status.CREATING);
            default:
                kubernetes.delete(pod.getNamespace(), pod.getPodName());
                if (podDao.delete(pod.getPodId(), userId)) {
                    userDao.releasePodSlot(userId);
                }
                return Status.DELETED;
        }
    }

    private Status track(PodDetails pod, String userId, Status status) {
        podDao.updateStatus(pod.getPodId(), userId, status);
        return status;
    }
}
