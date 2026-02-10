package com.acuity.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler for user client connections with streaming data support across multiple proxy channels
 */
public class UserClientHandler extends ServerHandler {
    // Streaming configuration
    private static final int CHUNK_SIZE = 8192; // 8KB chunks for streaming

    // Stream ID counter per user channel for generating unique stream IDs
    private static final Map<String, AtomicInteger> streamIdCounters = new ConcurrentHashMap<>();

    // Load balancing counter for distributing streams across proxy clients
    private static final AtomicInteger proxyRoundRobinCounter = new AtomicInteger(0);

    public UserClientHandler(Map<Integer, TunnelServerApp> userClientInstances) {
        super(null, userClientInstances, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        userClientContexts.put(channelId, ctx);
        streamIdCounters.put(channelId, new AtomicInteger(1)); // Start stream IDs from 1
        System.out.println("[TunnelServer] [Channel: " + channelId + "] User client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String userChannelId = ctx.channel().id().asShortText();

        // Clean up any streaming sessions for this user channel
        ProxyClientHandler.cleanupSessionsForUser(userChannelId);

        // Remove stream ID counter
        streamIdCounters.remove(userChannelId);

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

        // Generate a new stream ID for this transfer
        AtomicInteger counter = streamIdCounters.get(userChannelId);
        if (counter == null) {
            counter = new AtomicInteger(1);
            streamIdCounters.put(userChannelId, counter);
        }
        String streamId = String.valueOf(counter.getAndIncrement());

        // Select proxy channel using round-robin load balancing
        int selectedIndex = Math.abs(proxyRoundRobinCounter.getAndIncrement()) % proxyChannelIds.size();
        String selectedProxyChannelId = proxyChannelIds.get(selectedIndex);
        ChannelHandlerContext proxyCtx = proxyClientContexts.get(selectedProxyChannelId);

        // Validate selected proxy
        if (proxyCtx == null || !proxyCtx.channel().isActive()) {
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Selected proxy channel " + selectedProxyChannelId + " is not active");
            proxyClientContexts.remove(selectedProxyChannelId);
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: Proxy channel not available\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " -> Proxy: " + selectedProxyChannelId + " (load balanced)");

        // Stream the data in chunks if it's large
        streamDataToProxy(userChannelId, streamId, data, proxyCtx);

        byteBuf.release();
    }

    /**
     * Stream data from user client to proxy client in chunks across multiple proxy channels
     * Supports concurrent streams by using streamId in message protocol
     */
    private void streamDataToProxy(String userChannelId, String streamId, byte[] data, ChannelHandlerContext proxyCtx) {
        // Validate proxy is still active before starting stream
        if (proxyCtx == null || !proxyCtx.channel().isActive()) {
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Proxy channel became inactive before stream " + streamId + " start");
            return;
        }

        if (data.length <= CHUNK_SIZE) {
            // Small data: send as single FORWARD message
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Sending small message (" + data.length + " bytes) to proxy");
            TunnelMessage tunnelMessage = new TunnelMessage(userChannelId, streamId, TunnelAction.FORWARD, data);
            byte[] serializedMessage = tunnelMessage.toBytes();

            try {
                proxyCtx.write(Unpooled.copiedBuffer(serializedMessage));
                proxyCtx.flush();
            } catch (Exception e) {
                System.err.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Error sending to proxy: " + e.getMessage());
            }
        } else {
            // Large data: stream in chunks
            System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Streaming large message (" + data.length + " bytes) in " + CHUNK_SIZE + " byte chunks");

            // Send STREAM_START message with streamId
            TunnelMessage startMessage = new TunnelMessage(userChannelId, streamId, TunnelAction.STREAM_START,
                String.valueOf(data.length).getBytes(CharsetUtil.UTF_8));

            try {
                proxyCtx.write(Unpooled.copiedBuffer(startMessage.toBytes()));

                // Send data in chunks
                int offset = 0;
                int chunkNumber = 1;
                while (offset < data.length) {
                    // Check if proxy is still active during streaming
                    if (!proxyCtx.channel().isActive()) {
                        System.err.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Proxy became inactive at chunk " + chunkNumber);
                        return;
                    }

                    int chunkLength = Math.min(CHUNK_SIZE, data.length - offset);
                    byte[] chunk = new byte[chunkLength];
                    System.arraycopy(data, offset, chunk, 0, chunkLength);

                    TunnelMessage chunkMessage = new TunnelMessage(userChannelId, streamId, TunnelAction.STREAM_DATA, chunk);
                    proxyCtx.write(Unpooled.copiedBuffer(chunkMessage.toBytes()));

                    if (chunkNumber % 10 == 0) { // Log every 10 chunks to reduce spam
                        System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Chunk " + chunkNumber +
                            " (" + chunkLength + " bytes, offset: " + offset + ")");
                    }

                    offset += chunkLength;
                    chunkNumber++;
                }

                // Send STREAM_END message with streamId
                TunnelMessage endMessage = new TunnelMessage(userChannelId, streamId, TunnelAction.STREAM_END, new byte[0]);
                proxyCtx.write(Unpooled.copiedBuffer(endMessage.toBytes()));
                proxyCtx.flush();

                System.out.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Completed: " +
                    (chunkNumber - 1) + " chunks sent");
            } catch (Exception e) {
                System.err.println("[TunnelServer] [Channel: " + userChannelId + "] Stream " + streamId + " - Error during streaming: " + e.getMessage());
            }
        }
    }
}
