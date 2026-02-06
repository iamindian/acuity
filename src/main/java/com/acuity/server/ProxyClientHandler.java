package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for proxy client connections with streaming support
 */
public class ProxyClientHandler extends ServerHandler {

    // Track streaming sessions: userChannelId -> ByteBuffer for reassembling chunks
    private static final Map<String, StreamingSession> streamingSessions = new HashMap<>();

    public ProxyClientHandler(Map<Integer, List<TunnelServerApp>> proxyClientInstances) {
        super(proxyClientInstances, null, null);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String proxyChannelId = ctx.channel().id().asShortText();
        proxyClientContexts.put(proxyChannelId, ctx);
        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Proxy client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String proxyChannelId = ctx.channel().id().asShortText();

        // Clean up any orphaned streaming sessions for inactive user channels
        cleanupOrphanedSessions();

        // Call parent cleanup
        super.channelInactive(ctx);

        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Proxy client disconnected");
    }

    /**
     * Clean up streaming sessions for inactive user channels
     * This method is public static so it can be called from UserClientHandler when a user disconnects
     */
    public static void cleanupOrphanedSessions() {
        streamingSessions.entrySet().removeIf(entry -> {
            String userChannelId = entry.getKey();
            ChannelHandlerContext userCtx = userClientContexts.get(userChannelId);

            // Remove if user channel is null or inactive
            if (userCtx == null || !userCtx.channel().isActive()) {
                System.out.println("[TunnelServer] Cleaning up orphaned streaming session for inactive user channel: " + userChannelId);
                return true;
            }
            return false;
        });
    }

    /**
     * Clean up streaming session for a specific user channel when it becomes inactive
     * @param userChannelId The user channel ID that became inactive
     */
    public static void cleanupSessionForUser(String userChannelId) {
        StreamingSession removed = streamingSessions.remove(userChannelId);
        if (removed != null) {
            System.out.println("[TunnelServer] Cleaned up streaming session for disconnected user channel: " + userChannelId);
        }
    }

    @Override
    protected void handleTunnelMessage(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId) {
        TunnelAction action = tunnelMessage.getAction();
        String userChannelId = tunnelMessage.getUserChannelId();

        // Handle streaming-specific actions
        if (action == TunnelAction.STREAM_START) {
            handleStreamStart(ctx, tunnelMessage, proxyChannelId, userChannelId);
        } else if (action == TunnelAction.STREAM_DATA) {
            handleStreamData(ctx, tunnelMessage, proxyChannelId, userChannelId);
        } else if (action == TunnelAction.STREAM_END) {
            handleStreamEnd(ctx, tunnelMessage, proxyChannelId, userChannelId);
        } else if (action == TunnelAction.FORWARD) {
            handleForwardAction(ctx, tunnelMessage, proxyChannelId);
        } else {
            // Delegate to parent for other actions
            super.handleTunnelMessage(ctx, tunnelMessage, proxyChannelId);
        }
    }

    /**
     * Handle STREAM_START message - initialize streaming session
     */
    private void handleStreamStart(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId, String userChannelId) {
        byte[] data = tunnelMessage.getData();
        long totalSize = Long.parseLong(new String(data, StandardCharsets.UTF_8));

        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Stream START: userChannel=" + userChannelId + ", totalSize=" + totalSize + " bytes");

        // Check if user channel is still active
        ChannelHandlerContext userCtx = userClientContexts.get(userChannelId);
        if (userCtx == null || !userCtx.channel().isActive()) {
            System.err.println("[TunnelServer] [Channel: " + proxyChannelId + "] ERROR: User channel " + userChannelId + " is not active, cannot start stream");
            return;
        }

        // Clean up any existing session for this user channel (prevents orphans)
        StreamingSession existingSession = streamingSessions.get(userChannelId);
        if (existingSession != null) {
            System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] WARNING: Replacing existing streaming session for userChannel=" + userChannelId);
        }

        // Create streaming session
        StreamingSession session = new StreamingSession(userChannelId, totalSize);
        streamingSessions.put(userChannelId, session);
    }

    /**
     * Handle STREAM_DATA message - accumulate chunk
     */
    private void handleStreamData(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId, String userChannelId) {
        byte[] chunk = tunnelMessage.getData();

        StreamingSession session = streamingSessions.get(userChannelId);
        if (session == null) {
            System.err.println("[TunnelServer] [Channel: " + proxyChannelId + "] ERROR: Received STREAM_DATA without STREAM_START for " + userChannelId);
            return;
        }

        session.addChunk(chunk);
        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Stream DATA: userChannel=" + userChannelId + ", chunkSize=" + chunk.length + " bytes, accumulated=" + session.getAccumulatedSize() + "/" + session.getTotalSize());
    }

    /**
     * Handle STREAM_END message - forward complete data to user channel
     */
    private void handleStreamEnd(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId, String userChannelId) {
        StreamingSession session = streamingSessions.get(userChannelId);
        if (session == null) {
            System.err.println("[TunnelServer] [Channel: " + proxyChannelId + "] ERROR: Received STREAM_END without STREAM_START for " + userChannelId);
            return;
        }

        byte[] completeData = session.getCompleteData();
        streamingSessions.remove(userChannelId);

        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Stream END: userChannel=" + userChannelId + ", totalData=" + completeData.length + " bytes");

        // Forward to user channel
        forwardDataToUserClient(ctx, userChannelId, completeData, proxyChannelId);
    }

    @Override
    protected void handleForwardAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId) {
        // Proxy receives FORWARD message and should forward data to the actual target
        String userChannelId = tunnelMessage.getUserChannelId();
        byte[] data = tunnelMessage.getData();

        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Proxy forwarding data from user channel: " + userChannelId + ", data length: " + (data != null ? data.length : 0));

        if (userChannelId == null) {
            System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Missing userChannelId; drop data");
            return;
        }

        forwardDataToUserClient(ctx, userChannelId, data, proxyChannelId);
    }

    /**
     * Forward data to user client channel
     */
    private void forwardDataToUserClient(ChannelHandlerContext ctx, String userChannelId, byte[] data, String proxyChannelId) {
        ChannelHandlerContext userCtx = userClientContexts.get(userChannelId);
        if (userCtx == null || !userCtx.channel().isActive()) {
            System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] User channel not active: " + userChannelId);
            return;
        }

        if (data == null || data.length == 0) {
            System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] No data to forward to user channel: " + userChannelId);
            return;
        }

        userCtx.writeAndFlush(Unpooled.copiedBuffer(data));
    }

    /**
     * Inner class to track streaming session state
     */
    private static class StreamingSession {
        private final String userChannelId;
        private final long totalSize;
        private final ByteArrayBuilder buffer;

        public StreamingSession(String userChannelId, long totalSize) {
            this.userChannelId = userChannelId;
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
