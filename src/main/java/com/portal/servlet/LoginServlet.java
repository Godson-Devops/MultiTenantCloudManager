package com.portal.servlet;

import com.portal.dao.UserDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

public class LoginServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final int SESSION_TIMEOUT_SECONDS = 30 * 60;

    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireCsrf(request, response)) {
            return;
        }

        String userId = param(request, "userId");
        String password = param(request, "password");

        if (userId == null || password == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "userId and password required");
            return;
        }
        if (!userDao.verifyPassword(userId, password)) {

            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials");
            return;
        }

        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER_ID, userId);
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);

        sendOk(response, Map.of(
                "userId", userId,
                "redirect", "vm-dashboard.jsp"));
    }
}
