package com.portal.servlet;

import com.portal.util.WebUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public abstract class BaseServlet extends HttpServlet {

    public static final String SESSION_USER_ID = "user_id";

    protected static final int TOO_MANY_REQUESTS = 429;

    protected String currentUser(HttpServletRequest request) {
        var session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return null;
        }
        String id = userId.toString().trim();
        return id.isEmpty() ? null : id;
    }

    protected boolean requireSession(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (currentUser(request) == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return false;
        }
        return true;
    }

    protected boolean requireOwner(String ownerId, String sessionUserId,
                                   HttpServletResponse response) throws IOException {
        if (ownerId == null || !ownerId.equals(sessionUserId)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return false;
        }
        return true;
    }

    protected void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        WebUtil.writeJson(response, status, Map.of("error", message, "status", status));
    }

    protected void sendProviderError(HttpServletResponse response, int status,
                                     String operation, RuntimeException cause) {
        getServletContext().log(operation + " failed: " + cause, cause);
        try {
            sendError(response, status, operation + " failed. Please try again later.");
        } catch (IOException e) {

            getServletContext().log("Could not send " + operation + " error reply", e);
        }
    }

    protected void sendOk(HttpServletResponse response, Map<String, Object> body)
            throws IOException {
        WebUtil.writeJson(response, HttpServletResponse.SC_OK, body);
    }
}
