package com.acuity.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple TCP server that responds to PING with PONG
 */
public class TestTcpServer {
    private static final Logger logger = LoggerFactory.getLogger(TestTcpServer.class);
    private final int port;

    public TestTcpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("[TestTcpServer] Test TCP Server started on port {}", port);
            logger.info("[TestTcpServer] Waiting for client connections...");

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    int clientPort = clientSocket.getPort();
                    logger.info("[TestTcpServer] Client connected from {}:{}", clientAddress, clientPort);

                    // Read from client
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String message = in.readLine();
                    logger.info("[TestTcpServer] Received: {}", message);

                    if ("PING".equals(message)) {
                        String pongResponse = "PONG";
                        logger.info("[TestTcpServer] Sending: {}", pongResponse);
                        out.println(pongResponse);
                    } else {
                        logger.warn("[TestTcpServer] Unexpected message: {}", message);
                    }

                    logger.info("[TestTcpServer] Client disconnected");
                } catch (IOException e) {
                    logger.error("[TestTcpServer] Error handling client", e);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 9000;

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        TestTcpServer server = new TestTcpServer(port);
        server.start();
    }
}
