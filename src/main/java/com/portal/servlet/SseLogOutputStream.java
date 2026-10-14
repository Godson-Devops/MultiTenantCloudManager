package com.portal.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class SseLogOutputStream extends OutputStream {
    private final Writer writer;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();

    public SseLogOutputStream(Writer writer) {
        this.writer = writer;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        if (b == '\r') {
            return;
        }
        if (b == '\n') {
            flushLine();
            return;
        }
        line.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            write(b[i]);
        }
    }

    public synchronized void sendEvent(String event, String data) throws IOException {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data.replace("\n", " ") + "\n\n");
        writer.flush();
    }

    public synchronized void keepAlive() throws IOException {
        writer.write(": keep-alive\n\n");
        writer.flush();
    }

    private void flushLine() throws IOException {
        if (line.size() == 0) {
            writer.write("data: \n\n");
        } else {
            writer.write("data: ");
            writer.write(line.toString(StandardCharsets.UTF_8));
            writer.write("\n\n");
        }
        line.reset();
        writer.flush();
    }

    @Override
    public synchronized void flush() throws IOException {
        if (line.size() > 0) {
            flushLine();
        } else {
            writer.flush();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        flush();
        super.close();
    }
}
