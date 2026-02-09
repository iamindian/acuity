package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler for tunnel server connections (main server on port 7000)
 */
public class TunnelServerHandler extends ServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(TunnelServerHandler.class);

    // Map: groupId:proxyPort -> targetPort
    private static final Map<String, Integer> proxyTargetPortMap = new ConcurrentHashMap<>();

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
                if (targetPort > 0) {
                    String mapKey = groupId + ":" + proxyPort;
                    proxyTargetPortMap.put(mapKey, targetPort);
                    logger.info("[TunnelServer] [Channel: {}] Registered mapping: groupId={}, proxyPort={}, targetPort={}", serverChannelId, groupId, proxyPort, targetPort);
                }

                // Start proxy server only if not already registered for this port
                List<TunnelServerApp> appsOnPort = proxyClientInstances.get(proxyPort);
                boolean proxyAlreadyRunning = appsOnPort != null && !appsOnPort.isEmpty();

                if (!proxyAlreadyRunning) {
                    TunnelServerApp newApp = new TunnelServerApp(proxyPort, TunnelServerApp.ClientType.PROXY);
                    // Start the new app in a separate thread
                    new Thread(() -> {
                        try {
                            newApp.start();
                        } catch (InterruptedException e) {
                            logger.error("[TunnelServer] Failed to start proxy server on port {}", proxyPort, e);
                        }
                    }).start();
                }

                int instanceCount = (appsOnPort != null) ? appsOnPort.size() : 1;
                String response = proxyAlreadyRunning
                    ? "Proxy server already running on port " + proxyPort + " (groupId=" + groupId + ")"
                    : "Proxy server #" + instanceCount + " started on port " + proxyPort + " (groupId=" + groupId + ")";

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
}
