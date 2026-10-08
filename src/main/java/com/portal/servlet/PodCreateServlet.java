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
import java.util.Locale;
import java.util.Map;

public class PodCreateServlet extends BaseServlet {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

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

        if (podName == null || image == null || portParam == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "podName, image and port are required");
            return;
        }
        int port = parsePort(portParam, response);
        if (port == 0) {
            return;
        }

        String namespace = AppConfig.get("k8s.namespace_prefix", "user-") + sanitize(userId);

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
            created = ServiceRegistry.kubernetes().createPod(podName, namespace, image, port);
        } catch (RuntimeException e) {
            userDao.releasePodSlot(userId);
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Kubernetes pod creation", e);
            return;
        }

        created.setUserId(userId);
        try {
            podDao.insert(created);
        } catch (RuntimeException e) {
            rollbackQuota(created);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not record the new pod, please try again");
            return;
        }

        sendOk(response, Map.of(
                "podId", created.getPodId(),
                "podName", created.getPodName(),
                "namespace", created.getNamespace(),
                "status", created.getStatus().code()));
    }

    private int parsePort(String value, HttpServletResponse response) throws IOException {
        int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "port must be a number");
            return 0;
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "port must be " + MIN_PORT + "-" + MAX_PORT);
            return 0;
        }
        return port;
    }

    private void rollbackQuota(PodDetails created) {
        String userId = created.getUserId();
        userDao.releasePodSlot(userId);
        try {
            ServiceRegistry.kubernetes().delete(created.getNamespace(), created.getPodName());
        } catch (RuntimeException ignored) {
            getServletContext().log("Orphaned Kubernetes pod " + created.getPodName(), ignored);
        }
    }

    static String sanitize(String value) {
        String cleaned = (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "user" : cleaned;
    }
}
