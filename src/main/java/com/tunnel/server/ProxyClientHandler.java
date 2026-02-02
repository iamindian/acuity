package com.tunnel.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.util.Map;
import java.util.List;

/**
 * Handler for proxy client connections
 */
public class ProxyClientHandler extends ServerHandler {

    public ProxyClientHandler(Map<Integer, List<TunnelServerApp>> proxyClientInstances) {
        super(proxyClientInstances, null, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        proxyClientContexts.put(channelId, ctx);
        System.out.println("[Channel: " + channelId + "] Proxy client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void handleForwardAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        // Proxy receives FORWARD message and should forward data to the actual target
        String browserChannelId = tunnelMessage.getBrowserChannelId();
        byte[] data = tunnelMessage.getData();

        System.out.println("[Channel: " + channelId + "] Proxy forwarding data from browser channel: " +
            browserChannelId + ", data length: " + (data != null ? data.length : 0));

        if (browserChannelId == null) {
            System.out.println("[Channel: " + channelId + "] Missing browserChannelId; drop data");
            return;
        }

        ChannelHandlerContext browserCtx = browserClientContexts.get(browserChannelId);
        if (browserCtx == null || !browserCtx.channel().isActive()) {
            System.out.println("[Channel: " + channelId + "] Browser channel not active: " + browserChannelId);
            return;
        }

        if (data == null || data.length == 0) {
            System.out.println("[Channel: " + channelId + "] No data to forward to browser channel: " + browserChannelId);
            return;
        }

        browserCtx.writeAndFlush(Unpooled.copiedBuffer(data));
    }
}
