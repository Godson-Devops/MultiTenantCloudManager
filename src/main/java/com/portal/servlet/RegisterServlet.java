package com.portal.servlet;

import com.portal.dao.UserDao;
import com.portal.util.RateLimiter;
import com.portal.util.WebUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public class RegisterServlet extends BaseServlet {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int ATTEMPTS_PER_MINUTE = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private final UserDao userDao = new UserDao();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!RateLimiter.tryAcquire(
                "register:" + request.getRemoteAddr(), ATTEMPTS_PER_MINUTE, WINDOW_MILLIS)) {
            sendError(response, TOO_MANY_REQUESTS,
                    "Too many registrations, please wait a minute");
            return;
        }

        String userId = WebUtil.param(request, "userId");
        String password = WebUtil.param(request, "password");

        if (userId == null || password == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "userId and password required");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            return;
        }
        if (!userDao.createUser(userId, password)) {
            sendError(response, HttpServletResponse.SC_CONFLICT, "User id already taken");
            return;
        }

        sendOk(response, Map.of(
                "userId", userId,
                "message", "Registration successful, please log in"));
    }
}
