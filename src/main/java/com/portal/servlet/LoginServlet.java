package com.portal.servlet;

import com.portal.dao.UserDao;
import com.portal.util.RateLimiter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Verifies credentials and establishes the session. */
public class LoginServlet extends BaseServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String client = clientKey(request);

        // Rate limit before touching Mongo so a brute-force attempt cannot
        // turn the database into the attack surface.
        if (!RateLimiter.tryAcquire("login:" + client, 10, 60_000)) {
            sendError(response, TOO_MANY_REQUESTS,
                    "Too many login attempts, please wait a minute");
            return;
        }

        String userId = com.portal.util.WebUtil.param(request, "userId");
        String password = com.portal.util.WebUtil.param(request, "password");

        if (userId == null || password == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "userId and password required");
            return;
        }

        if (!userDao.verifyPassword(userId, password)) {
            // Same message whether the user is unknown or the password is
            // wrong, so this does not enumerate valid accounts.
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
            return;
        }

        // Rotate the session id on privilege change to prevent fixation.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER_ID, userId);
        session.setMaxInactiveInterval(30 * 60);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("redirect", "vm-dashboard.jsp");
        sendOk(response, body);
    }

    /** Identifies the caller for rate limiting, preferring the remote address. */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
