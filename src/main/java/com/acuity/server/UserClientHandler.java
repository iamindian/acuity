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
 * Handler for user client connections with streaming data support
 */
public class UserClientHandler extends ServerHandler {
    private final Random random = new Random();

    // Streaming configuration
    private static final int CHUNK_SIZE = 8192; // 8KB chunks for streaming

    public UserClientHandler(Map<Integer, TunnelServerApp> userClientInstances) {
        super(null, userClientInstances, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        userClientContexts.put(channelId, ctx);
        System.out.println("[TunnelServer] [Channel: " + channelId + "] User client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String userChannelId = ctx.channel().id().asShortText();

        // Clean up any streaming session for this user channel immediately
        ProxyClientHandler.cleanupSessionForUser(userChannelId);

        // Call parent cleanup (removes from userClientContexts)
        super.channelInactive(ctx);

        System.out.println("[TunnelServer] [Channel: " + userChannelId + "] User client disconnected");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        String userChannelId = ctx.channel().id().asShortText();

        // Get all available proxy client contexts
        List<String> proxyChannelIds = new ArrayList<>(proxyClientContexts.keySet());

        if (proxyChannelIds.isEmpty()) {
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] No proxy channels available");
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: No proxy channels available\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        // Select a random proxy channel
        String selectedProxyChannelId = proxyChannelIds.get(random.nextInt(proxyChannelIds.size()));
        ChannelHandlerContext proxyCtx = proxyClientContexts.get(selectedProxyChannelId);

        // Validate proxy channel is active before processing
        if (proxyCtx == null || !proxyCtx.channel().isActive()) {
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Selected proxy channel " + selectedProxyChannelId + " is not active");

            // Remove inactive proxy from map to prevent future selection
            proxyClientContexts.remove(selectedProxyChannelId);

            // Send error to user
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: Proxy channel not available\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        // Stream the data in chunks if it's large
        streamDataToProxy(userChannelId, data, proxyCtx);

        byteBuf.release();
    }

    /**
     * Stream data from user client to proxy client in chunks
     * Sends STREAM_START, followed by STREAM_DATA chunks, then STREAM_END
     */
    private void streamDataToProxy(String userChannelId, byte[] data, ChannelHandlerContext proxyCtx) {
        // Validate proxy is still active before starting stream
        if (proxyCtx == null || !proxyCtx.channel().isActive()) {
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Proxy channel became inactive before stream start");
            return;
        }

        if (data.length <= CHUNK_SIZE) {
            // Small data: send as single FORWARD message
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Sending small message (" + data.length + " bytes) to proxy");
            TunnelMessage tunnelMessage = new TunnelMessage(userChannelId, TunnelAction.FORWARD, data);
            byte[] serializedMessage = tunnelMessage.toBytes();

            try {
                proxyCtx.write(Unpooled.copiedBuffer(serializedMessage));
                proxyCtx.flush();
            } catch (Exception e) {
                System.err.println("[TunnelServer] [Channel: " + userChannelId + "] Error sending to proxy: " + e.getMessage());
            }
        } else {
            // Large data: stream in chunks
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Streaming large message (" + data.length + " bytes) to proxy in " + CHUNK_SIZE + " byte chunks");

            // Send STREAM_START message
            TunnelMessage startMessage = new TunnelMessage(userChannelId, TunnelAction.STREAM_START,
                String.valueOf(data.length).getBytes(CharsetUtil.UTF_8));

            try {
                proxyCtx.write(Unpooled.copiedBuffer(startMessage.toBytes()));

                // Send data in chunks
                int offset = 0;
                int chunkNumber = 1;
                while (offset < data.length) {
                    // Check if proxy is still active during streaming
                    if (!proxyCtx.channel().isActive()) {
                        System.err.println("[TunnelServer] [Channel: " + userChannelId + "] Proxy channel became inactive during streaming at chunk " + chunkNumber);
                        return;
                    }

                    int chunkLength = Math.min(CHUNK_SIZE, data.length - offset);
                    byte[] chunk = new byte[chunkLength];
                    System.arraycopy(data, offset, chunk, 0, chunkLength);

                    TunnelMessage chunkMessage = new TunnelMessage(userChannelId, TunnelAction.STREAM_DATA, chunk);
                    proxyCtx.write(Unpooled.copiedBuffer(chunkMessage.toBytes()));

                    System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Sent chunk " + chunkNumber +
                        " (" + chunkLength + " bytes, offset: " + offset + ")");

                    offset += chunkLength;
                    chunkNumber++;
                }

                // Send STREAM_END message
                TunnelMessage endMessage = new TunnelMessage(userChannelId, TunnelAction.STREAM_END, new byte[0]);
                proxyCtx.write(Unpooled.copiedBuffer(endMessage.toBytes()));
                proxyCtx.flush();

                System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Stream completed: " +
                    (chunkNumber - 1) + " chunks sent");
            } catch (Exception e) {
                System.err.println("[TunnelServer] [Channel: " + userChannelId + "] Error during streaming: " + e.getMessage());
            }
        }
    }
}
