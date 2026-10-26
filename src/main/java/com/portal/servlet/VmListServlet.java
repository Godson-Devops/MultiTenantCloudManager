package com.portal.servlet;

import com.portal.dao.UserDao;
import com.portal.dao.VmDao;
import com.portal.model.User;
import com.portal.model.VmDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VmListServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final VmDao vmDao = new VmDao();
    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }

        List<Map<String, Object>> rows =
                vmDao.findByUser(userId).stream().map(VmDetails::toMap).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vms", rows);

        User user = userDao.findById(userId);
        if (user != null) {
            Map<String, Object> quota = new LinkedHashMap<>();
            quota.put("used", user.getUsedVm());
            quota.put("max", user.getMaxVm());
            body.put("quota", quota);
        }
        sendOk(response, body);
    }
}
