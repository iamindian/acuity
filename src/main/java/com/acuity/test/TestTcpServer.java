package com.acuity.test;

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
    private final int port;

    public TestTcpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Test TCP Server started on port " + port);
            System.out.println("Waiting for client connections...");

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    int clientPort = clientSocket.getPort();
                    System.out.println("Client connected from " + clientAddress + ":" + clientPort);

                    // Read from client
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                    String message = in.readLine();
                    System.out.println("Received: " + message);

                    if ("PING".equals(message)) {
                        String pongResponse = "PONG";
                        System.out.println("Sending: " + pongResponse);
                        out.println(pongResponse);
                    } else {
                        System.out.println("Unexpected message: " + message);
                    }

                    System.out.println("Client disconnected");
                } catch (IOException e) {
                    System.err.println("Error handling client: " + e.getMessage());
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
