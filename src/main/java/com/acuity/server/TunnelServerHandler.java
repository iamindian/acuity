package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import java.util.Map;
import java.util.List;

/**
 * Handler for tunnel server connections (main server on port 7000)
 */
public class TunnelServerHandler extends ServerHandler {

    public TunnelServerHandler(Map<Integer, List<TunnelServerApp>> proxyClientInstances, Map<Integer, TunnelServerApp> userClientInstances, Map<Integer, TunnelServerApp> serverInstances) {
        super(proxyClientInstances, userClientInstances, serverInstances);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        System.out.println("[TunnelServer] [Channel: " + channelId + "] Tunnel server client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void handleTunnelMessage(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        String action = tunnelMessage.getAction();

        if (action == null) {
            System.out.println("[TunnelServer] [Channel: " + channelId + "] Received message with null action");
            return;
        }

        String actionUpper = action.toUpperCase();
        // Handle ADDPROXY action
        if (actionUpper.startsWith("ADDPROXY:")) {
            try {
                String portStr = action.substring(action.indexOf(':') + 1).trim();
                int proxyPort = Integer.parseInt(portStr);

                TunnelServerApp newApp = new TunnelServerApp(proxyPort, TunnelServerApp.ClientType.PROXY);
                // Start the new app in a separate thread
                new Thread(() -> {
                    try {
                        newApp.start();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();

                List<TunnelServerApp> appsOnPort = proxyClientInstances.get(proxyPort);
                int instanceCount = (appsOnPort != null) ? appsOnPort.size() : 1;
                String response = "Proxy server #" + instanceCount + " started on port " + proxyPort;

                // Send response back
                TunnelMessage responseMsg = new TunnelMessage(
                    tunnelMessage.getBrowserChannelId(),
                    "RESPONSE",
                    response.getBytes(CharsetUtil.UTF_8)
                );
                ctx.writeAndFlush(Unpooled.copiedBuffer(responseMsg.toBytes()));

                System.out.println("[TunnelServer] [Channel: " + channelId + "] " + response);
            } catch (NumberFormatException e) {
                String errorMsg = "Invalid port number in proxy command: " + action;
                TunnelMessage errorResponse = new TunnelMessage(
                    tunnelMessage.getBrowserChannelId(),
                    "ERROR",
                    errorMsg.getBytes(CharsetUtil.UTF_8)
                );
                ctx.writeAndFlush(Unpooled.copiedBuffer(errorResponse.toBytes()));
                System.err.println("[TunnelServer] [Channel: " + channelId + "] " + errorMsg);
            }
        } else {
            // Delegate to parent for standard actions (FORWARD, PING, EXIT, etc.)
            super.handleTunnelMessage(ctx, tunnelMessage, channelId);
        }
    }
}
