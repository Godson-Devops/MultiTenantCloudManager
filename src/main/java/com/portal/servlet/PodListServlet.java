package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PodListServlet extends BaseServlet {

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        List<PodDetails> pods = podDao.findByUser(currentUser(request));

        List<Map<String, Object>> rows = new ArrayList<>(pods.size());
        for (PodDetails pod : pods) {
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
        sendOk(response, Map.of("pods", rows));
    }
}
