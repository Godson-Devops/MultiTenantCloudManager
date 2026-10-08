package com.portal.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public final class WebUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebUtil() {
    }

    public static String param(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public static void writeJson(HttpServletResponse resp, int status, Map<String, Object> body)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
