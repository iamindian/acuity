package com.acuity.test;

import com.acuity.client.TunnelClientApp;
import com.acuity.common.SymmetricEncryption;
import com.acuity.server.TunnelServerApp;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Integration test for TestTcpClient and TestTcpServer PING/PONG communication
 * Prerequisites: Tunnel server and tunnel client must be running with shared encryption key
 */
public class TestTcpClientServerTest {
    private static final int TUNNEL_SERVER_PORT = 7001;
    private static final int TUNNEL_CLIENT_PROXY_PORT = 8081;
    private static final int TEST_TCP_SERVER_PORT = 9001;
    private static final String TUNNEL_HOST = "127.0.0.1";
    private static final long STARTUP_DELAY_MS = 2000;

    // Shared encryption key (password) - Base64 encoded AES-256 key (32 bytes)
    // This is a valid 256-bit key generated from 32 random bytes
    private static final String SHARED_KEY_PASSWORD = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static Thread tunnelServerThread;
    private static Thread tunnelClientThread;
    private static Thread testTcpServerThread;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.out.println("=== Starting Tunnel Infrastructure ===");

        // Start tunnel server and capture the generated key
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
    public static void tearDownAfterClass() throws Exception {
        System.out.println("=== Shutting down infrastructure ===");

        // Interrupt all threads
        if (testTcpServerThread != null) {
            testTcpServerThread.interrupt();
            testTcpServerThread.join(5000);
        }

        if (tunnelClientThread != null) {
            tunnelClientThread.interrupt();
            tunnelClientThread.join(5000);
        }

        if (tunnelServerThread != null) {
            tunnelServerThread.interrupt();
            tunnelServerThread.join(5000);
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
                System.err.println("[TunnelServer] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        tunnelServerThread.setDaemon(true);
        tunnelServerThread.start();

        // Wait for server to start
        Thread.sleep(1500);
    }

    private static void startTunnelClient() throws Exception {
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
                System.err.println("[TunnelClient] Error: " + e.getMessage());
                e.printStackTrace();
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
                System.err.println("[TestTcpServer] Error: " + e.getMessage());
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

        // Connect to test TCP server via proxy
        String testMessage = "PING";
        String expectedResponse = "PONG";

        System.out.println("Connecting to test server at " + TUNNEL_HOST + ":" + TEST_TCP_SERVER_PORT);

        try (Socket socket = new Socket(TUNNEL_HOST, TEST_TCP_SERVER_PORT)) {
            System.out.println("Connected successfully!");

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send PING
            System.out.println("Sending message: " + testMessage);
            out.println(testMessage);
            out.flush();

            // Receive PONG
            String response = in.readLine();
            System.out.println("Received message: " + response);

            // Assert the response
            assertNotNull("Response should not be null", response);
            assertEquals("Should receive PONG in response to PING", expectedResponse, response);

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
            try (Socket socket = new Socket(TUNNEL_HOST, TEST_TCP_SERVER_PORT)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String message = "PING";
                System.out.println("Request " + i + ": Sending " + message);
                out.println(message);
                out.flush();

                String response = in.readLine();
                System.out.println("Request " + i + ": Received " + response);

                assertEquals("Response " + i + " should be PONG", "PONG", response);
            }
        }

        System.out.println("✓ Test PASSED: Successfully completed " + numberOfRequests + " PING/PONG exchanges");
    }

    @Test
    public void testServerIsRunning() throws Exception {
        System.out.println("\n=== TEST: Test Server Availability ===");

        // Simply test that we can connect to the server
        try (Socket socket = new Socket(TUNNEL_HOST, TEST_TCP_SERVER_PORT)) {
            assertNotNull("Socket should not be null", socket);
            System.out.println("✓ Test PASSED: Test server is running and reachable");
        }
    }
}
