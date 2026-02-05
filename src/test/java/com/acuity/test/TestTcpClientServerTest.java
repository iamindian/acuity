package com.acuity.test;

import com.acuity.client.TunnelClientApp;
import com.acuity.common.SymmetricEncryption;
import com.acuity.server.TunnelServerApp;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

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
    // Generated using PowerShell: [System.Convert]::ToBase64String((1..32 | ForEach-Object { [byte](Get-Random -Maximum 256) }))
    private static final String SHARED_KEY_PASSWORD = "Hu5SNsC4RUrRO06vtNWkRwVDeR2phas3Pih7D+uJ/V4=";

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
}
