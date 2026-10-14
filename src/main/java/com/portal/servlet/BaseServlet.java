package com.portal.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String SESSION_USER_ID = "user_id";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    protected static void writeJson(HttpServletResponse response, int status,
                                    Map<String, Object> body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }

    protected String currentUser(HttpServletRequest request) {
        var session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return null;
        }
        String id = userId.toString().trim();
        return id.isEmpty() ? null : id;
    }

    protected String requireLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String userId = currentUser(request);
        if (userId == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return null;
        }
        return userId;
    }

    protected String requireId(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String id = param(request, "id");
        if (id == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id is required");
            return null;
        }
        return id;
    }

    protected <T> boolean requireOwned(T entity, Function<T, String> ownerOf,
                                       String userId, HttpServletResponse response)
            throws IOException {
        if (entity != null && userId != null && userId.equals(ownerOf.apply(entity))) {
            return true;
        }
        sendError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return false;
    }

    protected void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        writeJson(response, status, Map.of("error", message, "status", status));
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
        writeJson(response, HttpServletResponse.SC_OK, body);
    }
}
