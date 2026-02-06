package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;

/**
 * Handler for tunnel server connections (main server on port 7000)
 */
public class TunnelServerHandler extends ServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(TunnelServerHandler.class);

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
                String portStr = rawAction.substring(rawAction.indexOf(':') + 1).trim();
                int proxyPort = Integer.parseInt(portStr);

                TunnelServerApp newApp = new TunnelServerApp(proxyPort, TunnelServerApp.ClientType.PROXY);
                // Start the new app in a separate thread
                new Thread(() -> {
                    try {
                        newApp.start();
                    } catch (InterruptedException e) {
                        logger.error("[TunnelServer] Failed to start proxy server on port {}", proxyPort, e);
                    }
                }).start();

                List<TunnelServerApp> appsOnPort = proxyClientInstances.get(proxyPort);
                int instanceCount = (appsOnPort != null) ? appsOnPort.size() : 1;
                String response = "Proxy server #" + instanceCount + " started on port " + proxyPort;

                // Send response back
                TunnelMessage responseMsg = new TunnelMessage(
                    tunnelMessage.getUserChannelId(),
                    TunnelAction.RESPONSE,
                    response.getBytes(CharsetUtil.UTF_8)
                );
                ctx.writeAndFlush(Unpooled.copiedBuffer(responseMsg.toBytes()));

                logger.info("[TunnelServer] [Channel: {}] {}", serverChannelId, response);
            } catch (NumberFormatException e) {
                String errorMsg = "Invalid port number in proxy command: " + tunnelMessage.getRawAction();
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
}
