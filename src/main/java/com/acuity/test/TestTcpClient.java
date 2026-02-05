package com.acuity.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Simple TCP client that sends PING messages and receives PONG responses
 */
public class TestTcpClient {
    private static final Logger logger = LoggerFactory.getLogger(TestTcpClient.class);
    private final String host;
    private final int port;

    public TestTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        try (Socket socket = new Socket(host, port)) {
            logger.info("[TestTcpClient] Connected to server at {}:{}", host, port);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send PING message
            String pingMessage = "PING";
            logger.info("[TestTcpClient] Sending: {}", pingMessage);
            out.println(pingMessage);

            // Receive PONG response
            String response = in.readLine();
            logger.info("[TestTcpClient] Received: {}", response);

            if ("PONG".equals(response)) {
                logger.info("[TestTcpClient] Successfully received PONG response!");
            } else {
                logger.warn("[TestTcpClient] Unexpected response: {}", response);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String host = "127.0.0.1";
        int port = 9000;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        TestTcpClient client = new TestTcpClient(host, port);
        client.connect();
    }
}
