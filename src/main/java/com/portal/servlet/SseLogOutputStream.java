package com.portal.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class SseLogOutputStream extends OutputStream {
    private final Writer writer;
    private final StringBuilder line = new StringBuilder();

    public SseLogOutputStream(Writer writer) {
        this.writer = writer;
    }

    @Override
    public void write(int b) throws IOException {
        char c = (char) (b & 0xFF);
        if (c == '\r') {

            return;
        }
        if (c == '\n') {
            flushLine();
            return;
        }
        line.append(c);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            write(b[i]);
        }
    }

    private void flushLine() throws IOException {
        if (line.length() == 0) {
            writer.write("data: \n\n");
        } else {
            writer.write("data: ");
            writer.write(line.toString());
            writer.write("\n\n");
        }
        line.setLength(0);
        writer.flush();
    }

    @Override
    public void flush() throws IOException {
        if (line.length() > 0) {
            flushLine();
        } else {
            writer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        super.close();
    }
}
