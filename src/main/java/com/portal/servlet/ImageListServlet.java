package com.portal.servlet;

import com.portal.service.ServiceRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ImageListServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }
        try {
            List<Map<String, String>> images = ServiceRegistry.openStack().listImages();
            sendOk(response, Map.of("images", images));
        } catch (RuntimeException e) {
            sendProviderError(response, HttpServletResponse.SC_BAD_GATEWAY, "List images", e);
        }
    }
}
