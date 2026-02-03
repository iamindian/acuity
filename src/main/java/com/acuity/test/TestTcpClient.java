package com.acuity.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Simple TCP client that sends PING messages and receives PONG responses
 */
public class TestTcpClient {
    private final String host;
    private final int port;

    public TestTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected to server at " + host + ":" + port);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send PING message
            String pingMessage = "PING";
            System.out.println("Sending: " + pingMessage);
            out.println(pingMessage);

            // Receive PONG response
            String response = in.readLine();
            System.out.println("Received: " + response);

            if ("PONG".equals(response)) {
                System.out.println("Successfully received PONG response!");
            } else {
                System.out.println("Unexpected response: " + response);
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
