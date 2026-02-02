package com.acuity.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Handler for browser client connections
 */
public class BrowserClientHandler extends ServerHandler {
    private final Random random = new Random();

    public BrowserClientHandler(Map<Integer, TunnelServerApp> browserClientInstances) {
        super(null, browserClientInstances, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        browserClientContexts.put(channelId, ctx);
        System.out.println("[Channel: " + channelId + "] Browser client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        String browserChannelId = ctx.channel().id().asShortText();

        // Get all available proxy client contexts
        List<String> proxyChannelIds = new ArrayList<>(proxyClientContexts.keySet());

        if (proxyChannelIds.isEmpty()) {
            System.out.println("[Channel: " + browserChannelId + "] No proxy channels available");
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: No proxy channels available\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        // Select a random proxy channel
        String selectedProxyChannelId = proxyChannelIds.get(random.nextInt(proxyChannelIds.size()));
        ChannelHandlerContext proxyCtx = proxyClientContexts.get(selectedProxyChannelId);

        if (proxyCtx == null || !proxyCtx.channel().isActive()) {
            System.out.println("[Channel: " + browserChannelId + "] Selected proxy channel is not active");
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: Proxy channel not active\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        // Create TunnelMessage
        TunnelMessage tunnelMessage = new TunnelMessage(browserChannelId, "FORWARD", data);

        System.out.println("[Channel: " + browserChannelId + "] Encapsulating message and sending to proxy channel: " + selectedProxyChannelId);
        System.out.println("[Channel: " + browserChannelId + "] TunnelMessage: " + tunnelMessage);

        // Serialize and send the TunnelMessage to the proxy channel
        byte[] serializedMessage = tunnelMessage.toBytes();
        proxyCtx.writeAndFlush(Unpooled.copiedBuffer(serializedMessage));

        byteBuf.release();
    }
}
