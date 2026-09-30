package com.portal.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Common plumbing for the portal servlets: session lookup, JSON error replies
 * and ownership enforcement.
 */
public abstract class BaseServlet extends HttpServlet {

    public static final String SESSION_USER_ID = "user_id";

    /**
     * HTTP 429. Jakarta Servlet 6.0 does not define SC_TOO_MANY_REQUESTS
     * (that constant arrives in 6.1), so it is declared here.
     */
    protected static final int TOO_MANY_REQUESTS = 429;

    /**
     * Returns the logged-in user id, or null when there is no usable session.
     * Callers that need a session must reject the request when this is null.
     */
    protected String currentUser(HttpServletRequest request) {
        if (request.getSession(false) == null) {
            return null;
        }
        Object userId = request.getSession().getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return null;
        }
        String id = userId.toString().trim();
        return id.isEmpty() ? null : id;
    }

    /**
     * Enforces "logged in". Sends 401 and returns false when the caller has no
     * session, so subclasses can bail out with a single guard.
     */
    protected boolean requireSession(HttpServletRequest request, HttpServletResponse response)
            throws java.io.IOException {
        if (currentUser(request) == null) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return false;
        }
        return true;
    }

    /**
     * Enforces ownership. Returns 403 (not 404) for a resource owned by
     * someone else, matching the spec's "do not leak existence" rule -- and
     * the same 403 for a resource that does not exist at all, so the two
     * cases are indistinguishable to the caller.
     */
    protected boolean requireOwner(String ownerId, String sessionUserId,
                                   HttpServletResponse response) throws java.io.IOException {
        if (ownerId == null || !ownerId.equals(sessionUserId)) {
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
            return false;
        }
        return true;
    }

    protected void sendError(HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status);
        com.portal.util.WebUtil.writeJson(response, status, body);
    }

    /**
     * Reports a failure originating in an external provider.
     *
     * The provider's own message is deliberately not echoed to the client. It
     * is untrusted input from another system and frequently describes the
     * provider's internals -- in testing, an "OpenStack rejected the request:
     * Bad Request" turned out to be an unrelated nginx error page sitting on
     * the configured port. The detail is written to the servlet log where
     * operators can correlate it, and the client gets a stable message.
     *
     * @param safeMessage what the user is told; must not embed provider text
     */
    protected void sendProviderError(HttpServletResponse response, int status,
                                     String operation, RuntimeException cause) {
        getServletContext().log(operation + " failed: " + cause, cause);
        try {
            sendError(response, status, operation + " failed. Please try again later.");
        } catch (java.io.IOException e) {
            // The response is already committed or the client is gone.
            getServletContext().log("Could not send " + operation + " error reply", e);
        }
    }

    protected void sendOk(HttpServletResponse response, Map<String, Object> body)
            throws java.io.IOException {
        com.portal.util.WebUtil.writeJson(response, HttpServletResponse.SC_OK, body);
    }
}
