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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Streams a pod's logs to the Live Logs Viewer as Server-Sent Events.
 *
 * The stream is bounded by a maximum duration and closed on client disconnect,
 * so it never pins a thread indefinitely across page reloads. The JSP
 * reconnects with EventSource, which is why a hard cap is safe here.
 */
public class PodLogsServlet extends BaseServlet {

    private static final long DEFAULT_MAX_DURATION_MS = 60_000;
    private static final long POLL_INTERVAL_MS = 200;

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

        // A PipedOutputStream lets us forward Fabric8's line callbacks to the
        // SSE writer; the 8KB buffer decouples the watch thread from a slow
        // client rather than blocking it.
        java.io.PipedOutputStream sink = new java.io.PipedOutputStream();
        java.io.PipedInputStream source = new java.io.PipedInputStream(sink, 8192);

        LogWatch watch = null;
        try {
            watch = ServiceRegistry.kubernetes()
                    .watchLog(pod.getNamespace(), pod.getPodName(), sink);

            sendEvent(writer, "open", "streaming logs for " + pod.getPodName());

            long deadline = System.currentTimeMillis()
                    + AppConfig.getLong("pod.logs.max_duration_ms", DEFAULT_MAX_DURATION_MS);

            byte[] buffer = new byte[4096];
            while (System.currentTimeMillis() < deadline) {
                // read() would block until Fabric8 produced a line, which
                // never returns if the pod is quiet, so the deadline would
                // never be reached. Poll available() instead and keep the
                // connection alive with a periodic comment frame.
                if (source.available() == 0) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    writer.write(": keep-alive\n\n");
                    writer.flush();
                    continue;
                }
                int read = source.read(buffer, 0, buffer.length);
                if (read > 0) {
                    // Prefix every line: SSE treats a bare newline as the end
                    // of a frame, so multi-line chunks must be split by hand.
                    writer.write("data: "
                            + new String(buffer, 0, read, StandardCharsets.UTF_8)
                                .replace("\r\n", "\n").replace("\n", "\ndata: "));
                    writer.write("\n\n");
                    writer.flush();
                }
            }

            sendEvent(writer, "end", "stream window elapsed, reconnecting");
        } catch (IOException e) {
            // This is the normal client-disconnect path: the browser navigated
            // away and the write to the closed socket failed.
            getServletContext().log("Log stream closed early for " + podId);
        } catch (InterruptedException e) {
            // Container shutting down or the request thread recycled: restore
            // the flag and stop streaming rather than swallowing it.
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            getServletContext().log("Log stream failed for " + podId, e);
            try {
                // Fixed text, not e.getMessage(): the browser renders this
                // inline and the provider message may describe internals.
                sendEvent(writer, "error", "log stream failed");
            } catch (IOException ignored) {
                // Client already gone; nothing left to report to.
            }
        } finally {
            if (watch != null) {
                try {
                    watch.close();
                } catch (RuntimeException ignored) {
                    // Nothing useful to do while tearing down.
                }
            }
            try {
                source.close();
            } catch (IOException ignored) {
                // Nothing useful to do while tearing down.
            }
        }
    }

    private void sendEvent(Writer writer, String event, String data) throws IOException {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data.replace("\n", " ") + "\n\n");
        writer.flush();
    }
}
