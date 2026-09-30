package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists the caller's pods. MongoDB only, no live Kubernetes call. */
public class PodListServlet extends BaseServlet {

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PodDetails pod : podDao.findByUser(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("podId", pod.getPodId());
            row.put("podName", pod.getPodName());
            row.put("namespace", pod.getNamespace());
            row.put("image", pod.getImage());
            row.put("status", pod.getStatus());
            row.put("podIp", pod.getPodIp());
            row.put("nodeName", pod.getNodeName());
            rows.add(row);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("pods", rows);
        sendOk(response, body);
    }
}
