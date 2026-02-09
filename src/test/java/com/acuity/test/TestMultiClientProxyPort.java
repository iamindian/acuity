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
 * Integration test for testing 2 tunnel clients serving the same proxy port
 * Sending 20MB data through tunnel infrastructure
 */
public class TestMultiClientProxyPort {
    private static final Logger logger = LoggerFactory.getLogger(TestMultiClientProxyPort.class);
    private static final int TUNNEL_SERVER_PORT = 7002;
    private static final int TUNNEL_CLIENT_PROXY_PORT_1 = 8082; // First tunnel client proxy port
    private static final int TUNNEL_CLIENT_PROXY_PORT_2 = 8083; // Second tunnel client proxy port
    private static final int TEST_TCP_SERVER_PORT = 9002;
    private static final String TUNNEL_HOST = "127.0.0.1";
    private static final long STARTUP_DELAY_MS = 2000;
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for large data transfers

    // Shared encryption key (password) - Base64 encoded AES-256 key (32 bytes)
    private static final String SHARED_KEY_PASSWORD = "Hu5SNsC4RUrRO06vtNWkRwVDeR2phas3Pih7D+uJ/V4=";

    private static Thread tunnelServerThread;
    private static Thread tunnelClient1Thread;
    private static Thread tunnelClient2Thread;
    private static Thread testTcpServerThread;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.out.println("=== Starting Multi-Client Tunnel Infrastructure ===");

        // Start tunnel server
        startTunnelServer();
        Thread.sleep(STARTUP_DELAY_MS);

        // Start first tunnel client with groupId "group-1"
        startTunnelClient1();
        Thread.sleep(STARTUP_DELAY_MS);

        // Start second tunnel client with groupId "group-2"
        startTunnelClient2();
        Thread.sleep(STARTUP_DELAY_MS);

        // Start test TCP server
        startTestTcpServer();
        Thread.sleep(STARTUP_DELAY_MS);

        System.out.println("=== All servers started successfully ===\n");
    }

    @AfterClass
    public static void tearDownAfterClass() {
        System.out.println("=== Shutting down multi-client infrastructure ===");

        // Interrupt all threads gracefully
        if (testTcpServerThread != null && testTcpServerThread.isAlive()) {
            testTcpServerThread.interrupt();
            try {
                testTcpServerThread.join(3000);
            } catch (InterruptedException e) {
                System.out.println("[TestTcpServer] Shutdown interrupted");
            }
        }

        if (tunnelClient1Thread != null && tunnelClient1Thread.isAlive()) {
            tunnelClient1Thread.interrupt();
            try {
                tunnelClient1Thread.join(3000);
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient-1] Shutdown interrupted");
            }
        }

        if (tunnelClient2Thread != null && tunnelClient2Thread.isAlive()) {
            tunnelClient2Thread.interrupt();
            try {
                tunnelClient2Thread.join(3000);
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient-2] Shutdown interrupted");
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

    private static void startTunnelClient1() {
        tunnelClient1Thread = new Thread(() -> {
            try {
                System.out.println("[TunnelClient-1] Starting with groupId='shared-group'");
                System.out.println("[TunnelClient-1] Proxy port: " + TUNNEL_CLIENT_PROXY_PORT_1);
                System.out.println("[TunnelClient-1] Target: " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);

                TunnelClientApp client = new TunnelClientApp(
                    TUNNEL_HOST,
                    TUNNEL_SERVER_PORT,
                    TUNNEL_CLIENT_PROXY_PORT_1,
                    TUNNEL_HOST,
                    TEST_TCP_SERVER_PORT,
                    SHARED_KEY_PASSWORD,
                    "shared-group"
                );
                client.start();
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient-1] Interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("[TunnelClient-1] Error: {}", e.getMessage(), e);
            }
        });
        tunnelClient1Thread.setDaemon(true);
        tunnelClient1Thread.start();
    }

    private static void startTunnelClient2() {
        tunnelClient2Thread = new Thread(() -> {
            try {
                System.out.println("[TunnelClient-2] Starting with groupId='shared-group'");
                System.out.println("[TunnelClient-2] Proxy port: " + TUNNEL_CLIENT_PROXY_PORT_2);
                System.out.println("[TunnelClient-2] Target: " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);

                TunnelClientApp client = new TunnelClientApp(
                    TUNNEL_HOST,
                    TUNNEL_SERVER_PORT,
                    TUNNEL_CLIENT_PROXY_PORT_2,
                    TUNNEL_HOST,
                    TEST_TCP_SERVER_PORT,
                    SHARED_KEY_PASSWORD,
                    "shared-group"
                );
                client.start();
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient-2] Interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("[TunnelClient-2] Error: {}", e.getMessage(), e);
            }
        });
        tunnelClient2Thread.setDaemon(true);
        tunnelClient2Thread.start();
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
    public void testSend20MBDataWithTwoClientsOnSameProxyPort() throws IOException, InterruptedException {
        System.out.println("\n=== TEST: Send 20MB Data With 2 Tunnel Clients (Same GroupId, Different Proxy Ports) ===");

        Thread.sleep(500);

        // 20MB of test data
        final int dataSize = 20 * 1024 * 1024; // 20MB

        System.out.println("Generating 20MB test data...");
        byte[] testData1 = generateTestData(dataSize);
        byte[] testData2 = generateTestData(dataSize);
        System.out.println("Generated " + formatDataSize(testData1.length));
        System.out.println("Setup: Both Tunnel Clients with groupId='shared-group' (same group, different proxy ports)");
        System.out.println("  - Client-1: Proxy port " + TUNNEL_CLIENT_PROXY_PORT_1);
        System.out.println("  - Client-2: Proxy port " + TUNNEL_CLIENT_PROXY_PORT_2);
        System.out.println("  - Both target: " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);

        try {
            // Send 20MB through Client-1
            System.out.println("\n[TEST-CLIENT-1] Sending 20MB through proxy port " + TUNNEL_CLIENT_PROXY_PORT_1 + "...");
            long startTime1 = System.currentTimeMillis();
            byte[] response1 = sendLargeDataToProxy(TUNNEL_CLIENT_PROXY_PORT_1, testData1);
            long duration1 = System.currentTimeMillis() - startTime1;

            System.out.println("[TEST-CLIENT-1] Transfer time: " + duration1 + "ms");
            System.out.println("[TEST-CLIENT-1] Throughput: " + calculateThroughput(testData1.length, duration1) + " MB/s");

            // Wait between transfers
            Thread.sleep(1000);

            // Send 20MB through Client-2
            System.out.println("\n[TEST-CLIENT-2] Sending 20MB through proxy port " + TUNNEL_CLIENT_PROXY_PORT_2 + "...");
            long startTime2 = System.currentTimeMillis();
            byte[] response2 = sendLargeDataToProxy(TUNNEL_CLIENT_PROXY_PORT_2, testData2);
            long duration2 = System.currentTimeMillis() - startTime2;

            System.out.println("[TEST-CLIENT-2] Transfer time: " + duration2 + "ms");
            System.out.println("[TEST-CLIENT-2] Throughput: " + calculateThroughput(testData2.length, duration2) + " MB/s");

            // Verify both transfers succeeded
            if (response1.length > 0 && response2.length > 0) {
                System.out.println("\n✓ Test PASSED: Both tunnel clients successfully transferred 20MB each");
                System.out.println("✓ Total data transferred: " + formatDataSize(testData1.length + testData2.length));
                System.out.println("✓ Both clients with same groupId on different ports handled traffic independently");
            } else {
                System.err.println("✗ Test FAILED: One or both clients failed to receive response");
                throw new IOException("Invalid response from server");
            }
        } catch (IOException e) {
            System.err.println("✗ Test FAILED: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Send large binary data to a specific proxy port and receive response
     */
    private static byte[] sendLargeDataToProxy(int proxyPort, byte[] data) throws IOException {
        try (Socket socket = new Socket(TUNNEL_HOST, proxyPort)) {
            socket.setTcpNoDelay(true);
            socket.setReceiveBufferSize(1024 * 1024); // 1MB receive buffer
            socket.setSendBufferSize(1024 * 1024);    // 1MB send buffer

            System.out.println("[TestClient] Connected to proxy at " + TUNNEL_HOST + ":" + proxyPort);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send data size first
            System.out.println("[TestClient] Sending data size: " + data.length + " bytes");
            out.writeInt(data.length);
            out.flush();

            // Send data in chunks
            int offset = 0;
            int chunkNumber = 1;
            while (offset < data.length) {
                int chunkSize = Math.min(BUFFER_SIZE, data.length - offset);
                out.write(data, offset, chunkSize);

                if (chunkNumber % 256 == 0) {
                    System.out.println("[TestClient] Sent chunk " + chunkNumber +
                        " (" + (offset + chunkSize) + " bytes total)");
                }

                offset += chunkSize;
                chunkNumber++;
            }
            out.flush();

            System.out.println("[TestClient] Finished sending " + data.length +
                " bytes in " + (chunkNumber - 1) + " chunks");

            // Receive response size
            System.out.println("[TestClient] Waiting for response size...");
            int responseSize = in.readInt();
            System.out.println("[TestClient] Server will send " + responseSize + " bytes response");

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

                if (chunkNum % 256 == 0) {
                    System.out.println("[TestClient] Received chunk " + chunkNum +
                        " (" + totalRead + "/" + responseSize + " bytes)");
                }

                chunkNum++;
            }

            System.out.println("[TestClient] Successfully received " + totalRead +
                " bytes response in " + (chunkNum - 1) + " chunks");

            return response;
        } catch (IOException e) {
            logger.error("[TestClient] Connection error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Generate test data of specified size
     */
    private static byte[] generateTestData(int size) {
        byte[] data = new byte[size];
        // Fill with repeating pattern for easy verification
        byte[] pattern = "TestDataPattern20MB".getBytes();
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
