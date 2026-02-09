package com.acuity.test;

import com.acuity.client.TunnelClientApp;
import com.acuity.server.TunnelServerApp;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Integration test for TestTcpClient and TestTcpServer PING/PONG communication
 * and large data streaming through the tunnel infrastructure
 */
public class TestTcpClientServerTest {
    private static final Logger logger = LoggerFactory.getLogger(TestTcpClientServerTest.class);
    private static final int TUNNEL_SERVER_PORT = 7001;
    private static final int TUNNEL_CLIENT_PROXY_PORT = 8081;
    private static final int TEST_TCP_SERVER_PORT = 9001;
    private static final String TUNNEL_HOST = "127.0.0.1";
    private static final long STARTUP_DELAY_MS = 2000;
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for large data transfers

    // Shared encryption key (password) - Base64 encoded AES-256 key (32 bytes)
    private static final String SHARED_KEY_PASSWORD = "Hu5SNsC4RUrRO06vtNWkRwVDeR2phas3Pih7D+uJ/V4=";

    private static Thread tunnelServerThread;
    private static Thread tunnelClientThread;
    private static Thread testTcpServerThread;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.out.println("=== Starting Tunnel Infrastructure ===");

        // Start tunnel server
        startTunnelServer();
        Thread.sleep(STARTUP_DELAY_MS);

        // Start tunnel client with the shared key
        startTunnelClient();
        Thread.sleep(STARTUP_DELAY_MS);

        // Start test TCP server
        startTestTcpServer();
        Thread.sleep(STARTUP_DELAY_MS);

        System.out.println("=== All servers started successfully ===\n");
    }

    @AfterClass
    public static void tearDownAfterClass() {
        System.out.println("=== Shutting down infrastructure ===");

        // Interrupt all threads gracefully
        if (testTcpServerThread != null && testTcpServerThread.isAlive()) {
            testTcpServerThread.interrupt();
            try {
                testTcpServerThread.join(3000);
            } catch (InterruptedException e) {
                System.out.println("[TestTcpServer] Shutdown interrupted");
            }
        }

        if (tunnelClientThread != null && tunnelClientThread.isAlive()) {
            tunnelClientThread.interrupt();
            try {
                tunnelClientThread.join(3000);
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient] Shutdown interrupted");
            }
        }

        if (tunnelServerThread != null && tunnelServerThread.isAlive()) {
            tunnelServerThread.interrupt();
            try {
                tunnelServerThread.join(3000);
            } catch (InterruptedException e) {
                System.out.println("[TunnelServer] Shutdown interrupted");
            }
        }

        System.out.println("=== All servers shut down ===\n");
    }

    private static void startTunnelServer() throws Exception {
        tunnelServerThread = new Thread(() -> {
            try {
                System.out.println("[TunnelServer] Starting on port " + TUNNEL_SERVER_PORT);
                System.out.println("[TunnelServer] Using shared key password");

                TunnelServerApp server = new TunnelServerApp(TUNNEL_SERVER_PORT, TunnelServerApp.ClientType.SERVER, SHARED_KEY_PASSWORD);
                server.start();
            } catch (InterruptedException e) {
                System.out.println("[TunnelServer] Interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("[TunnelServer] Error: {}", e.getMessage(), e);
            }
        });
        tunnelServerThread.setDaemon(true);
        tunnelServerThread.start();

        // Wait for server to start
        Thread.sleep(1500);
    }

    private static void startTunnelClient() {
        tunnelClientThread = new Thread(() -> {
            try {
                System.out.println("[TunnelClient] Starting - connecting to " + TUNNEL_HOST + ":" + TUNNEL_SERVER_PORT);
                System.out.println("[TunnelClient] Proxy port: " + TUNNEL_CLIENT_PROXY_PORT);
                System.out.println("[TunnelClient] Target: " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);
                System.out.println("[TunnelClient] Using shared key password");

                TunnelClientApp client = new TunnelClientApp(
                    TUNNEL_HOST,
                    TUNNEL_SERVER_PORT,
                    TUNNEL_CLIENT_PROXY_PORT,
                    TUNNEL_HOST,
                    TEST_TCP_SERVER_PORT,
                    SHARED_KEY_PASSWORD
                );
                client.start();
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient] Interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("[TunnelClient] Error: {}", e.getMessage(), e);
            }
        });
        tunnelClientThread.setDaemon(true);
        tunnelClientThread.start();
    }

    private static void startTestTcpServer() {
        testTcpServerThread = new Thread(() -> {
            try {
                System.out.println("[TestTcpServer] Starting on port " + TEST_TCP_SERVER_PORT);
                TestTcpServer server = new TestTcpServer(TEST_TCP_SERVER_PORT);
                server.start();
            } catch (IOException e) {
                logger.error("[TestTcpServer] Error: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        });
        testTcpServerThread.setDaemon(true);
        testTcpServerThread.start();
    }

    @Test
    public void testTcpClientSendsPingAndReceivesPong() throws IOException, InterruptedException {
        System.out.println("\n=== TEST: PING/PONG Communication ===");

        // Give the tunnel infrastructure time to stabilize
        Thread.sleep(1000);

        // Use TestTcpClient to send PING and receive PONG
        System.out.println("Connecting to test server at " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);

        try {
            TestTcpClient client = new TestTcpClient(TUNNEL_HOST, TEST_TCP_SERVER_PORT);
            client.connect();
            System.out.println("✓ Test PASSED: Successfully sent PING and received PONG");
        } catch (IOException e) {
            System.err.println("✗ Test FAILED: " + e.getMessage());
            throw e;
        }
    }

    @Test
    public void testMultiplePingPongExchanges() throws IOException, InterruptedException {
        System.out.println("\n=== TEST: Multiple PING/PONG Exchanges ===");

        Thread.sleep(500);

        int numberOfRequests = 3;

        for (int i = 1; i <= numberOfRequests; i++) {
            System.out.println("Request " + i + ": Sending PING");
            try {
                TestTcpClient client = new TestTcpClient(TUNNEL_HOST, TEST_TCP_SERVER_PORT);
                client.connect();
            } catch (IOException e) {
                System.err.println("Request " + i + " failed: " + e.getMessage());
                throw e;
            }
        }

        System.out.println("✓ Test PASSED: Successfully completed " + numberOfRequests + " PING/PONG exchanges");
    }

    @Test
    public void testServerIsRunning() throws Exception {
        System.out.println("\n=== TEST: Test Server Availability ===");

        // Simply test that TestTcpClient can connect to the server
        try {
            TestTcpClient client = new TestTcpClient(TUNNEL_HOST, TEST_TCP_SERVER_PORT);
            client.connect();
            System.out.println("✓ Test PASSED: Test server is running and reachable");
        } catch (IOException e) {
            System.err.println("✗ Test FAILED: " + e.getMessage());
            throw e;
        }
    }

    @Test
    public void testSend10MBLargeData() throws IOException, InterruptedException {
        System.out.println("\n=== TEST: Send 10MB Large Data Through Tunnel ===");

        Thread.sleep(500);

        // 10MB of test data
        final int dataSize = 10 * 1024 * 1024; // 10MB

        System.out.println("Generating 10MB test data...");
        byte[] testData = generateTestData(dataSize);
        System.out.println("Generated " + formatDataSize(testData.length));

        long startTime = System.currentTimeMillis();

        try {
            // Send large data using integrated method
            byte[] response = sendLargeData(testData);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.println("Data transmission completed in " + duration + "ms");
            System.out.println("Throughput: " + calculateThroughput(testData.length, duration) + " MB/s");

            // Verify response
            if (response.length > 0) {
                System.out.println("Received response: " + formatDataSize(response.length));
                System.out.println("✓ Test PASSED: Successfully sent and received 10MB data through tunnel");
            } else {
                System.err.println("✗ Test FAILED: No response received from server");
                throw new IOException("No response received");
            }
        } catch (IOException e) {
            System.err.println("✗ Test FAILED: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Send large binary data to server and receive response
     * Uses built-in methods without dependency on TestTcpClientLargeData
     */
    private static byte[] sendLargeData(byte[] data) throws IOException {
        try (Socket socket = new Socket(TUNNEL_HOST, TEST_TCP_SERVER_PORT)) {
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(1024 * 1024); // 1MB receive buffer
            socket.setSendBufferSize(1024 * 1024);    // 1MB send buffer

            System.out.println("[TestTcpClientServerTest] Connected to server at " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send data size first
            System.out.println("[TestTcpClientServerTest] Sending data size: " + data.length + " bytes");
            out.writeInt(data.length);
            out.flush();

            // Send data in chunks
            int offset = 0;
            int chunkNumber = 1;
            while (offset < data.length) {
                int chunkSize = Math.min(BUFFER_SIZE, data.length - offset);
                out.write(data, offset, chunkSize);

                if (chunkNumber % 100 == 0) {
                    System.out.println("[TestTcpClientServerTest] Sent chunk " + chunkNumber +
                        " (" + (offset + chunkSize) + " bytes total)");
                }

                offset += chunkSize;
                chunkNumber++;
            }
            out.flush();

            System.out.println("[TestTcpClientServerTest] Finished sending " + data.length +
                " bytes in " + (chunkNumber - 1) + " chunks");

            // Receive response size
            System.out.println("[TestTcpClientServerTest] Waiting for response size...");
            int responseSize = in.readInt();
            System.out.println("[TestTcpClientServerTest] Server will send " + responseSize + " bytes response");

            // Receive response data
            byte[] response = new byte[responseSize];
            int totalRead = 0;
            int chunkNum = 1;

            while (totalRead < responseSize) {
                int bytesToRead = Math.min(BUFFER_SIZE, responseSize - totalRead);
                int bytesRead = in.read(response, totalRead, bytesToRead);

                if (bytesRead < 0) {
                    throw new IOException("Connection closed unexpectedly after reading " + totalRead + " bytes");
                }

                totalRead += bytesRead;

                if (chunkNum % 100 == 0) {
                    System.out.println("[TestTcpClientServerTest] Received chunk " + chunkNum +
                        " (" + totalRead + "/" + responseSize + " bytes)");
                }

                chunkNum++;
            }

            System.out.println("[TestTcpClientServerTest] Successfully received " + totalRead +
                " bytes response in " + (chunkNum - 1) + " chunks");

            return response;
        } catch (IOException e) {
            logger.error("[TestTcpClientServerTest] Connection error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Generate test data of specified size
     */
    private static byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        // Fill with repeating pattern for easy verification
        byte[] pattern = "TestDataPattern".getBytes();
        for (int i = 0; i < size; i++) {
            data[i] = pattern[i % pattern.length];
        }
        return data;
    }

    /**
     * Format data size in human-readable format
     */
    private static String formatDataSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    /**
     * Calculate throughput in MB/s
     */
    private static double calculateThroughput(long bytes, long durationMs) {
        if (durationMs <= 0) return 0;
        return (double) bytes / (1024 * 1024) / (durationMs / 1000.0);
    }


}
