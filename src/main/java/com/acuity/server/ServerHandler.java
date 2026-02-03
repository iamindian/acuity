package com.acuity.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base handler for tunnel server with shared functionality
 */
public class ServerHandler extends ChannelInboundHandlerAdapter {
    // Static maps shared across all handlers
    protected static final Map<String, Channel> pipedChannels = new ConcurrentHashMap<>();
    protected static final Map<String, ChannelHandlerContext> proxyClientContexts = new ConcurrentHashMap<>();
    protected static final Map<String, ChannelHandlerContext> browserClientContexts = new ConcurrentHashMap<>();

    protected final Map<Integer, List<TunnelServerApp>> proxyClientInstances;
    protected final Map<Integer, TunnelServerApp> userClientInstances;
    protected final Map<Integer, TunnelServerApp> serverInstances;

    public ServerHandler(Map<Integer, List<TunnelServerApp>> proxyClientInstances, Map<Integer, TunnelServerApp> userClientInstances, Map<Integer, TunnelServerApp> serverInstances) {
        this.proxyClientInstances = proxyClientInstances;
        this.userClientInstances = userClientInstances;
        this.serverInstances = serverInstances;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        String channelId = ctx.channel().id().asShortText();

        try {
            // Read all bytes from the ByteBuf
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);

            // Deserialize TunnelMessage
            TunnelMessage tunnelMessage = TunnelMessage.fromBytes(bytes);

            System.out.println("[TunnelServer] [Channel: " + channelId + "] Server received TunnelMessage: " + tunnelMessage);

            // Handle the tunnel message
            handleTunnelMessage(ctx, tunnelMessage, channelId);

        } catch (IllegalArgumentException e) {
            // If deserialization fails, log the error
            System.err.println("[TunnelServer] [Channel: " + channelId + "] Failed to deserialize TunnelMessage: " + e.getMessage());
        } finally {
            byteBuf.release();
        }
    }

    protected void handleTunnelMessage(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        // Default implementation - subclasses can override
        String action = tunnelMessage.getAction();

        if (action == null) {
            System.out.println("[TunnelServer] [Channel: " + channelId + "] Received message with null action");
            return;
        }

        switch (action) {
            case "FORWARD":
                handleForwardAction(ctx, tunnelMessage, channelId);
                break;
            case "PING":
                handlePingAction(ctx, tunnelMessage, channelId);
                break;
            case "EXIT":
                handleExitAction(ctx, tunnelMessage, channelId);
                break;
            default:
                System.out.println("[TunnelServer] [Channel: " + channelId + "] Unknown action: " + action);
        }
    }

    protected void handleForwardAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        // Default implementation - subclasses should override
        System.out.println("[TunnelServer] [Channel: " + channelId + "] Handling FORWARD action with data length: " +
            (tunnelMessage.getData() != null ? tunnelMessage.getData().length : 0));
    }

    protected void handlePingAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        System.out.println("[TunnelServer] [Channel: " + channelId + "] Received PING");
        // Send PONG response
        TunnelMessage pong = new TunnelMessage(
            tunnelMessage.getBrowserChannelId(),
            "PONG",
            new byte[0]
        );
        ctx.writeAndFlush(Unpooled.copiedBuffer(pong.toBytes()));
    }

    protected void handleExitAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        System.out.println("[TunnelServer] [Channel: " + channelId + "] Received EXIT, closing connection");
        ctx.flush();
        ctx.close();
    }

    @Deprecated
    protected String handleCommand(ChannelHandlerContext ctx, String command, String channelId, String defaultResponse) {
        // Deprecated - kept for backward compatibility
        return defaultResponse;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        System.out.println("[TunnelServer] [Channel: " + channelId + "] Client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            String channelId = ctx.channel().id().asShortText();
            System.out.println("[TunnelServer] [Channel: " + channelId + "] Idle detected: " + event.state());

            // Send PING to keep connection alive
            ctx.writeAndFlush(Unpooled.copiedBuffer("PING\n", CharsetUtil.UTF_8));
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();

        // Remove this channel from piped channels
        pipedChannels.entrySet().removeIf(entry -> entry.getValue().id().equals(ctx.channel().id()));

        proxyClientContexts.remove(channelId);
        browserClientContexts.remove(channelId);

        System.out.println("[TunnelServer] [Channel: " + channelId + "] Client disconnected: " + ctx.channel().remoteAddress());
    }

    public static Map<String, ChannelHandlerContext> getProxyClientContexts() {
        return new ConcurrentHashMap<>(proxyClientContexts);
    }

    public static Map<String, ChannelHandlerContext> getBrowserClientContexts() {
        return new ConcurrentHashMap<>(browserClientContexts);
    }
}
