package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.dao.UserDao;
import com.portal.model.PodDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.AppConfig;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Creates a pod after reserving a quota slot. */
public class PodCreateServlet extends BaseServlet {

    private final PodDao podDao = new PodDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);

        String podName = WebUtil.param(request, "podName");
        String image = WebUtil.param(request, "image");
        String portParam = WebUtil.param(request, "port");
        // Each user is pinned to their own namespace so Kubernetes-side
        // ResourceQuota applies per user too.
        String namespace = AppConfig.get("k8s.namespace_prefix", "user-") + sanitize(userId);

        if (podName == null || image == null || portParam == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "podName, image and port are required");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portParam);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "port must be a number");
            return;
        }
        if (port < 1 || port > 65535) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "port must be 1-65535");
            return;
        }

        if (podDao.existsByName(userId, podName, namespace)) {
            sendError(response, HttpServletResponse.SC_CONFLICT,
                    "You already have a pod named " + podName);
            return;
        }

        if (!userDao.reservePodSlot(userId)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "Pod quota exceeded");
            return;
        }

        PodDetails created;
        try {
            created = ServiceRegistry.kubernetes()
                    .createPod(podName, namespace, image, port);
        } catch (RuntimeException e) {
            userDao.releasePodSlot(userId);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Kubernetes pod creation", e);
            return;
        }

        created.setUserId(userId);
        created.setStatus(WebUtil.mapPodPhase(created.getStatus()));
        try {
            podDao.insert(created);
        } catch (RuntimeException e) {
            userDao.releasePodSlot(userId);
            try {
                ServiceRegistry.kubernetes().delete(namespace, podName);
            } catch (RuntimeException ignored) {
                getServletContext().log("Orphaned Kubernetes pod " + podName, ignored);
            }
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not record the new pod, please try again");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("podId", created.getPodId());
        body.put("podName", created.getPodName());
        body.put("namespace", created.getNamespace());
        body.put("status", created.getStatus());
        body.put("message", "creating");
        sendOk(response, body);
    }

    /** Reduces a user id to something safe to use as a namespace name. */
    static String sanitize(String value) {
        String cleaned = value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        cleaned = cleaned.replaceAll("-+", "-").replaceAll("^-|-$", "");
        return cleaned.isEmpty() ? "user" : cleaned;
    }
}
