package com.portal.util;

import java.util.Map;

/** Helpers shared by the servlets for JSON responses and request parsing. */
public final class WebUtil {

    private WebUtil() {
    }

    /**
     * Maps an OpenStack Nova server status onto the portal's status vocabulary.
     * See the mapping table in the spec: BUILD->creating, ACTIVE->running,
     * SHUTOFF->stopped, ERROR->error.
     */
    public static String mapOpenStackStatus(String raw) {
        if (raw == null) {
            return "error";
        }
        return switch (raw.toUpperCase()) {
            case "BUILD" -> "creating";
            case "ACTIVE" -> "running";
            case "SHUTOFF" -> "stopped";
            case "ERROR" -> "error";
            // REBUILD / DELETED / PAUSED / SUSPENDED and anything else are not
            // states the UI knows about; treat them as errors rather than
            // inventing new vocabulary the JSPs would not render.
            default -> "error";
        };
    }

    /** Maps a Kubernetes pod phase onto the portal's status vocabulary. */
    public static String mapPodPhase(String raw) {
        if (raw == null) {
            return "creating";
        }
        return switch (raw) {
            case "Pending" -> "creating";
            case "Running" -> "running";
            case "Failed" -> "error";
            case "Succeeded" -> "stopped";
            default -> "creating";
        };
    }

    /** Extracts a single string parameter, returning null when absent/blank. */
    public static String param(jakarta.servlet.http.HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    /** Writes a JSON body with the given status code. */
    public static void writeJson(jakarta.servlet.http.HttpServletResponse resp,
                                 int status,
                                 Map<String, Object> body) throws java.io.IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        resp.getWriter().write(mapper.writeValueAsString(body));
    }
}
