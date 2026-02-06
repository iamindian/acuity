package com.acuity.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCP server that handles both PING/PONG and large binary data transfers
 */
public class TestTcpServer {
    private static final Logger logger = LoggerFactory.getLogger(TestTcpServer.class);
    private final int port;
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for reading

    public TestTcpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("[TestTcpServer] Test TCP Server started on port {}", port);
            logger.info("[TestTcpServer] Waiting for client connections...");

            while (true) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    int clientPort = clientSocket.getPort();
                    logger.info("[TestTcpServer] Client connected from {}:{}", clientAddress, clientPort);

                    // Handle client in a separate thread (passing socket directly)
                    Socket finalSocket = clientSocket;
                    new Thread(() -> handleClient(finalSocket)).start();
                } catch (IOException e) {
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                        } catch (IOException ex) {
                            logger.debug("[TestTcpServer] Error closing socket", ex);
                        }
                    }
                    logger.error("[TestTcpServer] Error accepting client connection", e);
                }
            }
        }
    }

    /**
     * Handle client connection - detect and route to appropriate handler
     */
    private void handleClient(Socket clientSocket) {
        try {
            clientSocket.setTcpNoDelay(true);
            clientSocket.setReceiveBufferSize(1024 * 1024);
            clientSocket.setSendBufferSize(1024 * 1024);

            // Use DataInputStream to read data safely
            DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

            // Read first 4 bytes - could be either size (binary) or part of "PING"
            byte[] firstBytes = new byte[4];
            int bytesRead = dataIn.read(firstBytes, 0, 4);

            if (bytesRead == 4) {
                // Check if it looks like a size (4-byte integer for binary data)
                // Binary data will have writeInt() which produces binary data
                // Text data will be "PING" which is 0x50494E47
                int possibleSize = ((firstBytes[0] & 0xFF) << 24) |
                                  ((firstBytes[1] & 0xFF) << 16) |
                                  ((firstBytes[2] & 0xFF) << 8) |
                                  (firstBytes[3] & 0xFF);

                // If size looks reasonable (between 1KB and 100MB), treat as binary
                if (possibleSize > 1024 && possibleSize < (100 * 1024 * 1024)) {
                    logger.info("[TestTcpServer] Detected binary data transfer (size: {} bytes)", possibleSize);
                    handleBinaryDataClient(clientSocket, dataIn, possibleSize);
                } else if ("PING".equals(new String(firstBytes))) {
                    logger.info("[TestTcpServer] Detected text protocol (PING/PONG)");
                    handleTextClient(clientSocket, firstBytes);
                } else {
                    logger.warn("[TestTcpServer] Unable to detect protocol, closing connection");
                }
            }
        } catch (IOException e) {
            logger.error("[TestTcpServer] Error handling client", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.debug("[TestTcpServer] Error closing client socket", e);
            }
        }
    }

    /**
     * Handle text-based PING/PONG protocol
     */
    private void handleTextClient(Socket clientSocket, byte[] firstBytes) throws IOException {
        String message = new String(firstBytes).trim();

        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        logger.info("[TestTcpServer] Received: {}", message);

        if ("PING".equals(message)) {
            String pongResponse = "PONG";
            logger.info("[TestTcpServer] Sending: {}", pongResponse);
            out.println(pongResponse);
        } else {
            logger.warn("[TestTcpServer] Unexpected message: {}", message);
        }

        logger.info("[TestTcpServer] Client disconnected");
    }

    /**
     * Handle binary data transfer
     */
    private void handleBinaryDataClient(Socket clientSocket, DataInputStream in, int dataSize) throws IOException {
        try {
            clientSocket.setReceiveBufferSize(1024 * 1024); // 1MB receive buffer
            clientSocket.setSendBufferSize(1024 * 1024);    // 1MB send buffer

            logger.info("[TestTcpServer] Starting to receive {} bytes of data", dataSize);

            // Receive data
            byte[] data = new byte[dataSize];
            int totalRead = 0;
            int chunkNumber = 1;

            while (totalRead < dataSize) {
                int bytesToRead = Math.min(BUFFER_SIZE, dataSize - totalRead);
                int bytesRead = in.read(data, totalRead, bytesToRead);

                if (bytesRead < 0) {
                    throw new IOException("Connection closed unexpectedly after reading " + totalRead + " bytes");
                }

                totalRead += bytesRead;

                if (chunkNumber % 100 == 0) {
                    logger.info("[TestTcpServer] Received chunk {} ({}/{} bytes)",
                        chunkNumber, totalRead, dataSize);
                }

                chunkNumber++;
            }

            logger.info("[TestTcpServer] Successfully received {} bytes in {} chunks", totalRead, chunkNumber - 1);

            // Send response: echo back the size and generate response data
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            // Generate response (echo back the same size)
            int responseSize = dataSize;
            logger.info("[TestTcpServer] Sending response of {} bytes", responseSize);

            out.writeInt(responseSize);
            out.flush();

            // Send response data in chunks
            chunkNumber = 1;
            int sent = 0;
            byte[] responseData = new byte[Math.min(BUFFER_SIZE, responseSize)];

            while (sent < responseSize) {
                int chunkSize = Math.min(BUFFER_SIZE, responseSize - sent);
                out.write(responseData, 0, chunkSize);
                sent += chunkSize;

                if (chunkNumber % 100 == 0) {
                    logger.info("[TestTcpServer] Sent response chunk {} ({}/{} bytes)",
                        chunkNumber, sent, responseSize);
                }

                chunkNumber++;
            }

            out.flush();
            logger.info("[TestTcpServer] Successfully sent {} bytes response in {} chunks", sent, chunkNumber - 1);
            logger.info("[TestTcpServer] Client disconnected");

        } catch (IOException e) {
            logger.error("[TestTcpServer] Error handling binary data client", e);
            throw e;
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
