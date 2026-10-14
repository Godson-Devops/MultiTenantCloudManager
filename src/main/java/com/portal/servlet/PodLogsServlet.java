package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.AppConfig;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class PodLogsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final long DEFAULT_MAX_DURATION_MS = 60_000;
    private static final long POLL_INTERVAL_MS = 200;
    private static final long KEEPALIVE_INTERVAL_MS = 2_000;

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String userId = requireLogin(request, response);
        if (userId == null) {
            return;
        }
        String podId = requireId(request, response);
        if (podId == null) {
            return;
        }

        PodDetails pod = podDao.findByIdAndUser(podId, userId);
        if (!requireOwned(pod, PodDetails::getUserId, userId, response)) {
            return;
        }

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        SseLogOutputStream sink = new SseLogOutputStream(writer);

        LogWatch watch = null;
        try {
            watch = ServiceRegistry.kubernetes()
                    .watchLog(pod.getNamespace(), pod.getPodName(), sink);

            sink.sendEvent("open", "streaming logs for " + pod.getPodName());

            long deadline = System.currentTimeMillis()
                    + AppConfig.getLong("pod.logs.max_duration_ms", DEFAULT_MAX_DURATION_MS);
            long nextKeepAlive = System.currentTimeMillis() + KEEPALIVE_INTERVAL_MS;

            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MS);
                long now = System.currentTimeMillis();
                if (now >= nextKeepAlive && now < deadline) {
                    sink.keepAlive();
                    nextKeepAlive = now + KEEPALIVE_INTERVAL_MS;
                }
            }

            sink.sendEvent("end", "stream window elapsed, reconnecting");
        } catch (IOException e) {

            getServletContext().log("Log stream closed early for " + podId);
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            getServletContext().log("Log stream failed for " + podId, e);
            try {

                sink.sendEvent("error", "log stream failed");
            } catch (IOException ignored) {

            }
        } finally {
            close(watch);
            flushQuietly(sink);
        }
    }

    private void close(LogWatch watch) {
        if (watch == null) {
            return;
        }
        try {
            watch.close();
        } catch (RuntimeException ignored) {

        }
    }

    private void flushQuietly(SseLogOutputStream sink) {
        try {
            sink.flush();
        } catch (IOException ignored) {

        }
    }
}
