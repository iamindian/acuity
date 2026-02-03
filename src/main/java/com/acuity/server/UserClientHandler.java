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
    private static final String STREAM_START_ACTION = "STREAM_START";
    private static final String STREAM_DATA_ACTION = "STREAM_DATA";
    private static final String STREAM_END_ACTION = "STREAM_END";

    public UserClientHandler(Map<Integer, TunnelServerApp> userClientInstances) {
        super(null, userClientInstances, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        browserClientContexts.put(channelId, ctx);
        System.out.println("[TunnelServer] [Channel: " + channelId + "] User client connected: " + ctx.channel().remoteAddress());
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
            System.out.println("[TunnelServer] [Channel: " + browserChannelId + "] No proxy channels available");
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: No proxy channels available\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        // Select a random proxy channel
        String selectedProxyChannelId = proxyChannelIds.get(random.nextInt(proxyChannelIds.size()));
        ChannelHandlerContext proxyCtx = proxyClientContexts.get(selectedProxyChannelId);

        if (proxyCtx == null || !proxyCtx.channel().isActive()) {
            System.out.println("[TunnelServer] [Channel: " + browserChannelId + "] Selected proxy channel is not active");
            ctx.writeAndFlush(Unpooled.copiedBuffer("Error: Proxy channel not active\n", CharsetUtil.UTF_8));
            byteBuf.release();
            return;
        }

        // Stream the data in chunks if it's large
        streamDataToProxy(browserChannelId, data, proxyCtx);

        byteBuf.release();
    }

    /**
     * Stream data from user client to proxy client in chunks
     * Sends STREAM_START, followed by STREAM_DATA chunks, then STREAM_END
     */
    private void streamDataToProxy(String browserChannelId, byte[] data, ChannelHandlerContext proxyCtx) {
        if (data.length <= CHUNK_SIZE) {
            // Small data: send as single FORWARD message
            System.out.println("[TunnelServer] [Channel: " + browserChannelId + "] Sending small message (" + data.length + " bytes) to proxy");
            TunnelMessage tunnelMessage = new TunnelMessage(browserChannelId, "FORWARD", data);
            byte[] serializedMessage = tunnelMessage.toBytes();
            proxyCtx.write(Unpooled.copiedBuffer(serializedMessage));
            proxyCtx.flush();
        } else {
            // Large data: stream in chunks
            System.out.println("[TunnelServer] [Channel: " + browserChannelId + "] Streaming large message (" + data.length + " bytes) to proxy in " + CHUNK_SIZE + " byte chunks");

            // Send STREAM_START message
            TunnelMessage startMessage = new TunnelMessage(browserChannelId, STREAM_START_ACTION,
                String.valueOf(data.length).getBytes(CharsetUtil.UTF_8));
            proxyCtx.write(Unpooled.copiedBuffer(startMessage.toBytes()));

            // Send data in chunks
            int offset = 0;
            int chunkNumber = 1;
            while (offset < data.length) {
                int chunkLength = Math.min(CHUNK_SIZE, data.length - offset);
                byte[] chunk = new byte[chunkLength];
                System.arraycopy(data, offset, chunk, 0, chunkLength);

                TunnelMessage chunkMessage = new TunnelMessage(browserChannelId, STREAM_DATA_ACTION, chunk);
                proxyCtx.write(Unpooled.copiedBuffer(chunkMessage.toBytes()));

                System.out.println("[TunnelServer] [Channel: " + browserChannelId + "] Sent chunk " + chunkNumber +
                    " (" + chunkLength + " bytes, offset: " + offset + ")");

                offset += chunkLength;
                chunkNumber++;
            }

            // Send STREAM_END message
            TunnelMessage endMessage = new TunnelMessage(browserChannelId, STREAM_END_ACTION, new byte[0]);
            proxyCtx.write(Unpooled.copiedBuffer(endMessage.toBytes()));
            proxyCtx.flush();

            System.out.println("[TunnelServer] [Channel: " + browserChannelId + "] Stream completed: " +
                (chunkNumber - 1) + " chunks sent");
        }
    }
}
