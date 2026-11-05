package com.iflytek.skillhub.observability.business;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Raw local HTTP peer for responses that HttpServer deliberately cannot produce. */
final class BusinessLogsTestReceiver implements AutoCloseable {
    private final ServerSocket server;
    private final Thread worker;
    private final String firstResponse;
    final AtomicInteger requests = new AtomicInteger();
    private volatile Throwable failure;

    BusinessLogsTestReceiver(String firstResponse) throws IOException {
        this.firstResponse = firstResponse;
        server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        worker = new Thread(this::receive, "skillhub-1009-http-test");
        worker.setDaemon(true);
        worker.start();
    }

    String endpoint() { return "http://127.0.0.1:" + server.getLocalPort() + "/v1/logs"; }

    private void receive() {
        try {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new BufferedInputStream(socket.getInputStream());
                    int length = 0;
                    String line;
                    while (!(line = line(input)).isEmpty()) {
                        if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                            length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                        }
                    }
                    if (input.readNBytes(length).length != length) { throw new IOException("Incomplete test request"); }
                    int count = requests.incrementAndGet();
                    String response = count == 1 ? firstResponse
                            : "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.ISO_8859_1));
                    socket.getOutputStream().flush();
                }
            }
        } catch (IOException ex) {
            if (!server.isClosed()) { failure = ex; }
        } catch (RuntimeException ex) { failure = ex; }
    }

    private static String line(BufferedInputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') { return bytes.toString(StandardCharsets.ISO_8859_1).trim(); }
            bytes.write(value);
        }
        throw new IOException("Incomplete test headers");
    }

    @Override
    public void close() throws Exception {
        server.close();
        worker.join(6000);
        if (worker.isAlive()) { throw new AssertionError("HTTP test peer did not stop"); }
        if (failure != null) { throw new AssertionError("HTTP test peer failed", failure); }
    }
}
