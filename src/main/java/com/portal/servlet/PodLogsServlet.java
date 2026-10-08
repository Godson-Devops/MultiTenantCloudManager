package com.portal.servlet;

import com.portal.dao.PodDao;
import com.portal.model.PodDetails;
import com.portal.service.ServiceRegistry;
import com.portal.util.AppConfig;
import com.portal.util.WebUtil;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class PodLogsServlet extends BaseServlet {

    private static final long DEFAULT_MAX_DURATION_MS = 60_000;
    private static final long POLL_INTERVAL_MS = 200;
    private static final int BRIDGE_BUFFER_BYTES = 8192;
    private static final int READ_BUFFER_BYTES = 4096;

    private final PodDao podDao = new PodDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!requireSession(request, response)) {
            return;
        }
        String userId = currentUser(request);
        String podId = WebUtil.param(request, "id");

        if (podId == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "id is required");
            return;
        }

        PodDetails pod = podDao.findByIdAndUser(podId, userId);
        if (!requireOwner(pod == null ? null : pod.getUserId(), userId, response)) {
            return;
        }

        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);

        PipedOutputStream sink = new PipedOutputStream();
        PipedInputStream source = new PipedInputStream(sink, BRIDGE_BUFFER_BYTES);

        LogWatch watch = null;
        try {
            watch = ServiceRegistry.kubernetes()
                    .watchLog(pod.getNamespace(), pod.getPodName(), sink);

            sendEvent(writer, "open", "streaming logs for " + pod.getPodName());

            long deadline = System.currentTimeMillis()
                    + AppConfig.getLong("pod.logs.max_duration_ms", DEFAULT_MAX_DURATION_MS);

            byte[] buffer = new byte[READ_BUFFER_BYTES];
            while (System.currentTimeMillis() < deadline) {

                if (source.available() == 0) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    writer.write(": keep-alive\n\n");
                    writer.flush();
                    continue;
                }
                int read = source.read(buffer, 0, buffer.length);
                if (read > 0) {

                    writer.write("data: "
                            + new String(buffer, 0, read, StandardCharsets.UTF_8)
                                .replace("\r\n", "\n").replace("\n", "\ndata: ")
                            + "\n\n");
                    writer.flush();
                }
            }

            sendEvent(writer, "end", "stream window elapsed, reconnecting");
        } catch (IOException e) {

            getServletContext().log("Log stream closed early for " + podId);
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            getServletContext().log("Log stream failed for " + podId, e);
            try {

                sendEvent(writer, "error", "log stream failed");
            } catch (IOException ignored) {

            }
        } finally {
            close(watch);
            closeQuietly(source);
        }
    }

    private void sendEvent(Writer writer, String event, String data) throws IOException {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data.replace("\n", " ") + "\n\n");
        writer.flush();
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

    private void closeQuietly(PipedInputStream source) {
        try {
            source.close();
        } catch (IOException ignored) {

        }
    }
}
