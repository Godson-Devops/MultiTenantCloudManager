package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AdminPodListServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireAdmin(request, response);
        if (userId == null) {
            return;
        }

        List<Map<String, Object>> rows =
                podDao.findAll().stream().map(PodDetails::toMap).toList();
        sendOk(response, Map.of("pods", rows));
    }
}
