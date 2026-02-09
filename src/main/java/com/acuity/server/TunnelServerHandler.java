package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler for tunnel server connections (main server on port 7000)
 */
public class TunnelServerHandler extends ServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(TunnelServerHandler.class);

    // Map: groupId:proxyPort -> targetPort
    private static final Map<String, Integer> proxyTargetPortMap = new ConcurrentHashMap<>();

    // Map: groupId:proxyPort -> List of proxy client channels (for load balancing)
    private static final Map<String, List<String>> proxyClientChannelsMap = new ConcurrentHashMap<>();

    // Load balancing round-robin counter per group:port
    private static final Map<String, AtomicInteger> loadBalanceCounter = new ConcurrentHashMap<>();

    public TunnelServerHandler(Map<Integer, List<TunnelServerApp>> proxyClientInstances, Map<Integer, TunnelServerApp> userClientInstances, Map<Integer, TunnelServerApp> serverInstances) {
        super(proxyClientInstances, userClientInstances, serverInstances);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String serverChannelId = ctx.channel().id().asShortText();
        logger.info("[TunnelServer] [Channel: {}] Tunnel server client connected: {}", serverChannelId, ctx.channel().remoteAddress());
    }

    @Override
    protected void handleTunnelMessage(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String serverChannelId) {
        TunnelAction action = tunnelMessage.getAction();

        if (action == null) {
            logger.warn("[TunnelServer] [Channel: {}] Received message with null action", serverChannelId);
            return;
        }

        // Handle ADDPROXY action
        if (action == TunnelAction.ADDPROXY) {
            try {
                String rawAction = tunnelMessage.getRawAction();
                String payload = rawAction.substring(rawAction.indexOf(':') + 1).trim();
                String[] parts = payload.split(":", -1);

                int proxyPort = Integer.parseInt(parts[0].trim());
                String groupId = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "default";
                int targetPort = -1;
                if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                    targetPort = Integer.parseInt(parts[2].trim());
                }

                // Store proxy->target mapping for group (if provided)
                String mapKey = groupId + ":" + proxyPort;
                if (targetPort > 0) {
                    proxyTargetPortMap.put(mapKey, targetPort);
                    logger.info("[TunnelServer] [Channel: {}] Registered mapping: groupId={}, proxyPort={}, targetPort={}", serverChannelId, groupId, proxyPort, targetPort);
                }

                // Add this proxy client to the list of clients for this group:port combination
                proxyClientChannelsMap.computeIfAbsent(mapKey, k -> new CopyOnWriteArrayList<>())
                    .add(serverChannelId);

                // Initialize load balance counter if not exists
                loadBalanceCounter.computeIfAbsent(mapKey, k -> new AtomicInteger(0));

                int clientCount = proxyClientChannelsMap.get(mapKey).size();
                logger.info("[TunnelServer] [Channel: {}] Added proxy client to group. Total clients for {}:{}: {}",
                    serverChannelId, groupId, proxyPort, clientCount);

                // Start proxy server only if not already running for this port
                List<TunnelServerApp> appsOnPort = proxyClientInstances.get(proxyPort);
                boolean proxyAlreadyRunning = appsOnPort != null && !appsOnPort.isEmpty();

                if (!proxyAlreadyRunning) {
                    TunnelServerApp newApp = new TunnelServerApp(proxyPort, TunnelServerApp.ClientType.PROXY);
                    new Thread(() -> {
                        try {
                            newApp.start();
                        } catch (InterruptedException e) {
                            logger.error("[TunnelServer] Failed to start proxy server on port {}", proxyPort, e);
                        }
                    }).start();
                }

                String response = clientCount == 1
                    ? "Proxy server started on port " + proxyPort + " (groupId=" + groupId + ", client #1)"
                    : "Proxy client #" + clientCount + " added to group " + groupId + " on port " + proxyPort;

                // Send response back
                TunnelMessage responseMsg = new TunnelMessage(
                    tunnelMessage.getUserChannelId(),
                    TunnelAction.RESPONSE,
                    response.getBytes(CharsetUtil.UTF_8)
                );
                ctx.writeAndFlush(Unpooled.copiedBuffer(responseMsg.toBytes()));

                logger.info("[TunnelServer] [Channel: {}] {}", serverChannelId, response);
            } catch (NumberFormatException e) {
                String errorMsg = "Invalid ADDPROXY payload: " + tunnelMessage.getRawAction();
                TunnelMessage errorResponse = new TunnelMessage(
                    tunnelMessage.getUserChannelId(),
                    TunnelAction.ERROR,
                    errorMsg.getBytes(CharsetUtil.UTF_8)
                );
                ctx.writeAndFlush(Unpooled.copiedBuffer(errorResponse.toBytes()));
                logger.error("[TunnelServer] [Channel: {}] {}", serverChannelId, errorMsg);
            }
        } else {
            // Delegate to parent for standard actions (FORWARD, PING, EXIT, etc.)
            super.handleTunnelMessage(ctx, tunnelMessage, serverChannelId);
        }
    }

    public static Integer getTargetPortForProxy(String groupId, int proxyPort) {
        String resolvedGroupId = (groupId == null || groupId.isEmpty()) ? "default" : groupId;
        return proxyTargetPortMap.get(resolvedGroupId + ":" + proxyPort);
    }

    /**
     * Get all proxy client channels for a specific group and proxy port
     */
    public static List<String> getProxyClientChannels(String groupId, int proxyPort) {
        String resolvedGroupId = (groupId == null || groupId.isEmpty()) ? "default" : groupId;
        String mapKey = resolvedGroupId + ":" + proxyPort;
        return proxyClientChannelsMap.getOrDefault(mapKey, new CopyOnWriteArrayList<>());
    }

    /**
     * Get the next proxy client channel using round-robin load balancing
     */
    public static String getNextProxyClientChannel(String groupId, int proxyPort) {
        String resolvedGroupId = (groupId == null || groupId.isEmpty()) ? "default" : groupId;
        String mapKey = resolvedGroupId + ":" + proxyPort;

        List<String> channels = proxyClientChannelsMap.get(mapKey);
        if (channels == null || channels.isEmpty()) {
            return null;
        }

        // Round-robin selection
        AtomicInteger counter = loadBalanceCounter.get(mapKey);
        if (counter == null) {
            counter = new AtomicInteger(0);
            loadBalanceCounter.put(mapKey, counter);
        }

        int index = counter.getAndIncrement() % channels.size();
        return channels.get(index);
    }

    /**
     * Remove proxy client channel when it disconnects
     */
    public static void removeProxyClientChannel(String groupId, int proxyPort, String channelId) {
        String resolvedGroupId = (groupId == null || groupId.isEmpty()) ? "default" : groupId;
        String mapKey = resolvedGroupId + ":" + proxyPort;
        List<String> channels = proxyClientChannelsMap.get(mapKey);
        if (channels != null) {
            channels.remove(channelId);
            logger.info("[TunnelServer] Removed proxy client {} from group {}. Remaining clients: {}",
                channelId, mapKey, channels.size());
            if (channels.isEmpty()) {
                proxyClientChannelsMap.remove(mapKey);
                loadBalanceCounter.remove(mapKey);
            }
        }
    }
}
