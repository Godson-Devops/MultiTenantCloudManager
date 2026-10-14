package com.portal.servlet;

import com.portal.dao.VmDao;
import com.portal.model.VmDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class VmListServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final VmDao vmDao = new VmDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }

        List<Map<String, Object>> rows =
                vmDao.findByUser(userId).stream().map(VmDetails::toMap).toList();
        sendOk(response, Map.of("vms", rows));
    }
}
