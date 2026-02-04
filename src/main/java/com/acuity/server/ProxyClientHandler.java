package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for proxy client connections with streaming support
 */
public class ProxyClientHandler extends ServerHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProxyClientHandler.class);

    // Track streaming sessions: browserChannelId -> ByteBuffer for reassembling chunks
    private static final Map<String, StreamingSession> streamingSessions = new HashMap<>();

    public ProxyClientHandler(Map<Integer, List<TunnelServerApp>> proxyClientInstances) {
        super(proxyClientInstances, null, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asShortText();
        proxyClientContexts.put(channelId, ctx);
        logger.info("[TunnelServer] [Channel: {}] Proxy client connected: {}", channelId, ctx.channel().remoteAddress());
    }

    @Override
    protected void handleTunnelMessage(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        String action = tunnelMessage.getAction();
        String browserChannelId = tunnelMessage.getBrowserChannelId();

        // Handle streaming-specific actions
        if ("STREAM_START".equals(action)) {
            handleStreamStart(ctx, tunnelMessage, channelId, browserChannelId);
        } else if ("STREAM_DATA".equals(action)) {
            handleStreamData(ctx, tunnelMessage, channelId, browserChannelId);
        } else if ("STREAM_END".equals(action)) {
            handleStreamEnd(ctx, tunnelMessage, channelId, browserChannelId);
        } else if ("FORWARD".equals(action)) {
            handleForwardAction(ctx, tunnelMessage, channelId);
        } else {
            // Delegate to parent for other actions
            super.handleTunnelMessage(ctx, tunnelMessage, channelId);
        }
    }

    /**
     * Handle STREAM_START message - initialize streaming session
     */
    private void handleStreamStart(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId, String browserChannelId) {
        byte[] data = tunnelMessage.getData();
        long totalSize = Long.parseLong(new String(data, StandardCharsets.UTF_8));

        logger.info("[TunnelServer] [Channel: {}] Stream START: browserChannel={}, totalSize={} bytes",
            channelId, browserChannelId, totalSize);

        // Create streaming session
        StreamingSession session = new StreamingSession(browserChannelId, totalSize);
        streamingSessions.put(browserChannelId, session);
    }

    /**
     * Handle STREAM_DATA message - accumulate chunk
     */
    private void handleStreamData(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId, String browserChannelId) {
        byte[] chunk = tunnelMessage.getData();

        StreamingSession session = streamingSessions.get(browserChannelId);
        if (session == null) {
            logger.error("[TunnelServer] [Channel: {}] ERROR: Received STREAM_DATA without STREAM_START for {}",
                channelId, browserChannelId);
            return;
        }

        session.addChunk(chunk);
        logger.info("[TunnelServer] [Channel: {}] Stream DATA: browserChannel={}, chunkSize={} bytes, accumulated={}/{}",
            channelId, browserChannelId, chunk.length, session.getAccumulatedSize(), session.getTotalSize());
    }

    /**
     * Handle STREAM_END message - forward complete data to browser channel
     */
    private void handleStreamEnd(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId, String browserChannelId) {
        StreamingSession session = streamingSessions.get(browserChannelId);
        if (session == null) {
            logger.error("[TunnelServer] [Channel: {}] ERROR: Received STREAM_END without STREAM_START for {}",
                channelId, browserChannelId);
            return;
        }

        byte[] completeData = session.getCompleteData();
        streamingSessions.remove(browserChannelId);

        logger.info("[TunnelServer] [Channel: {}] Stream END: browserChannel={}, totalData={} bytes",
            channelId, browserChannelId, completeData.length);

        // Forward to browser channel
        forwardDataToUserClient(ctx, browserChannelId, completeData, channelId);
    }

    @Override
    protected void handleForwardAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String channelId) {
        // Proxy receives FORWARD message and should forward data to the actual target
        String browserChannelId = tunnelMessage.getBrowserChannelId();
        byte[] data = tunnelMessage.getData();

        logger.info("[TunnelServer] [Channel: {}] Proxy forwarding data from browser channel: {}, data length: {}",
            channelId, browserChannelId, (data != null ? data.length : 0));

        if (browserChannelId == null) {
            logger.warn("[TunnelServer] [Channel: {}] Missing browserChannelId; drop data", channelId);
            return;
        }

        forwardDataToUserClient(ctx, browserChannelId, data, channelId);
    }

    /**
     * Forward data to user client channel
     */
    private void forwardDataToUserClient(ChannelHandlerContext ctx, String browserChannelId, byte[] data, String channelId) {
        ChannelHandlerContext browserCtx = userClientContexts.get(browserChannelId);
        if (browserCtx == null || !browserCtx.channel().isActive()) {
            logger.warn("[TunnelServer] [Channel: {}] Browser channel not active: {}", channelId, browserChannelId);
            return;
        }

        if (data == null || data.length == 0) {
            logger.warn("[TunnelServer] [Channel: {}] No data to forward to browser channel: {}", channelId, browserChannelId);
            return;
        }

        browserCtx.writeAndFlush(Unpooled.copiedBuffer(data));
    }

    /**
     * Inner class to track streaming session state
     */
    private static class StreamingSession {
        private final String browserChannelId;
        private final long totalSize;
        private final ByteArrayBuilder buffer;

        public StreamingSession(String browserChannelId, long totalSize) {
            this.browserChannelId = browserChannelId;
            this.totalSize = totalSize;
            this.buffer = new ByteArrayBuilder((int) Math.min(totalSize, Integer.MAX_VALUE));
        }

        public void addChunk(byte[] chunk) {
            buffer.append(chunk);
        }

        public byte[] getCompleteData() {
            return buffer.toByteArray();
        }

        public long getTotalSize() {
            return totalSize;
        }

        public long getAccumulatedSize() {
            return buffer.size();
        }
    }

    /**
     * Helper class to build byte arrays efficiently
     */
    private static class ByteArrayBuilder {
        private byte[] buffer;
        private int size;

        public ByteArrayBuilder(int capacity) {
            this.buffer = new byte[capacity];
            this.size = 0;
        }

        public void append(byte[] data) {
            if (size + data.length > buffer.length) {
                // Expand buffer
                byte[] newBuffer = new byte[Math.max(buffer.length * 2, size + data.length)];
                System.arraycopy(buffer, 0, newBuffer, 0, size);
                buffer = newBuffer;
            }
            System.arraycopy(data, 0, buffer, size, data.length);
            size += data.length;
        }

        public byte[] toByteArray() {
            byte[] result = new byte[size];
            System.arraycopy(buffer, 0, result, 0, size);
            return result;
        }

        public int size() {
            return size;
        }
    }
}
