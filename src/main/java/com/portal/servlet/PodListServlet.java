package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.dao.UserDao;
import com.portal.model.PodDetails;
import com.portal.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PodListServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final PodDao podDao = new PodDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }

        List<Map<String, Object>> rows =
                podDao.findByUser(userId).stream().map(PodDetails::toMap).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pods", rows);

        User user = userDao.findById(userId);
        if (user != null) {
            Map<String, Object> quota = new LinkedHashMap<>();
            quota.put("used", user.getUsedPod());
            quota.put("max", user.getMaxPod());
            body.put("quota", quota);
        }
        sendOk(response, body);
    }
}
