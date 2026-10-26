package com.portal.servlet;

import com.portal.dao.UserDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public class RegisterServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private static final java.util.regex.Pattern USER_ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{3,64}");

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
        if (!USER_ID.matcher(userId).matches()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Username must be 3-64 characters using letters, digits, dot, underscore or hyphen");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Password must be " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " characters");
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
