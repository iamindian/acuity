package com.acuity.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler for proxy client connections with streaming support across multiple channels
 */
public class ProxyClientHandler extends ServerHandler {
    // Streaming configuration
    private static final int CHUNK_SIZE = 8192; // 8KB chunks

    // Track streaming sessions: userChannelId:streamId -> StreamingSession for stream multiplexing
    private static final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();

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
        super.channelInactive(ctx);
        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Proxy client disconnected");
    }

    /**
     * Clean up all streaming sessions for a specific user channel when it becomes inactive
     */
    public static void cleanupSessionsForUser(String userChannelId) {
        List<String> sessionsToRemove = new ArrayList<>();
        for (String key : streamingSessions.keySet()) {
            if (key.startsWith(userChannelId + ":")) {
                sessionsToRemove.add(key);
            }
        }
        for (String key : sessionsToRemove) {
            streamingSessions.remove(key);
            System.out.println("[TunnelServer] Cleaned up streaming session: " + key);
        }
    }

    /**
     * Backward compatibility wrapper
     */
    public static void cleanupSessionForUser(String userChannelId) {
        cleanupSessionsForUser(userChannelId);
    }

    @Override
    protected void handleTunnelMessage(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId) {
        TunnelAction action = tunnelMessage.getAction();
        String userChannelId = tunnelMessage.getUserChannelId();
        String streamId = tunnelMessage.getStreamId();
        String streamKey = tunnelMessage.getStreamKey();

        // Handle streaming-specific actions
        if (action == TunnelAction.STREAM_START) {
            handleStreamStart(ctx, tunnelMessage, proxyChannelId, userChannelId, streamId, streamKey);
        } else if (action == TunnelAction.STREAM_DATA) {
            handleStreamData(ctx, tunnelMessage, proxyChannelId, userChannelId, streamId, streamKey);
        } else if (action == TunnelAction.STREAM_END) {
            handleStreamEnd(ctx, tunnelMessage, proxyChannelId, userChannelId, streamId, streamKey);
        } else if (action == TunnelAction.FORWARD) {
            handleForwardAction(ctx, tunnelMessage, proxyChannelId);
        } else {
            super.handleTunnelMessage(ctx, tunnelMessage, proxyChannelId);
        }
    }

    /**
     * Handle STREAM_START message - initialize streaming session with streamId
     */
    private void handleStreamStart(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId,
                                   String userChannelId, String streamId, String streamKey) {
        byte[] data = tunnelMessage.getData();
        long totalSize = Long.parseLong(new String(data, StandardCharsets.UTF_8));

        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Stream START: streamKey=" + streamKey + ", totalSize=" + totalSize + " bytes");

        ChannelHandlerContext userCtx = userClientContexts.get(userChannelId);
        if (userCtx == null || !userCtx.channel().isActive()) {
            System.err.println("[TunnelServer] [Channel: " + proxyChannelId + "] ERROR: User channel " + userChannelId + " is not active");
            return;
        }

        StreamingSession session = new StreamingSession(streamKey, totalSize);
        streamingSessions.put(streamKey, session);
    }

    /**
     * Handle STREAM_DATA message - accumulate chunk for specific stream
     */
    private void handleStreamData(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId,
                                  String userChannelId, String streamId, String streamKey) {
        byte[] chunk = tunnelMessage.getData();
        StreamingSession session = streamingSessions.get(streamKey);

        if (session == null) {
            System.err.println("[TunnelServer] [Channel: " + proxyChannelId + "] ERROR: No session for stream " + streamKey);
            return;
        }

        session.addChunk(chunk);
        if (session.getAccumulatedSize() % (CHUNK_SIZE * 10) == 0) {
            System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Stream " + streamId + " DATA: accumulated=" +
                session.getAccumulatedSize() + "/" + session.getTotalSize());
        }
    }

    /**
     * Handle STREAM_END message - complete stream and forward to user channel
     */
    private void handleStreamEnd(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId,
                                 String userChannelId, String streamId, String streamKey) {
        StreamingSession session = streamingSessions.remove(streamKey);

        if (session == null) {
            System.err.println("[TunnelServer] [Channel: " + proxyChannelId + "] ERROR: No session for stream " + streamKey);
            return;
        }

        byte[] completeData = session.getCompleteData();
        System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Stream " + streamId + " END: totalData=" +
            completeData.length + " bytes");

        forwardDataToUserClient(ctx, userChannelId, completeData, proxyChannelId);
    }

    /**
     * Handle FORWARD action
     */
    protected void handleForwardAction(ChannelHandlerContext ctx, TunnelMessage tunnelMessage, String proxyChannelId) {
        String userChannelId = tunnelMessage.getUserChannelId();
        byte[] data = tunnelMessage.getData();

        if (userChannelId == null) {
            System.out.println("[TunnelServer] [Channel: " + proxyChannelId + "] Missing userChannelId; dropping data");
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
            return;
        }

        userCtx.writeAndFlush(Unpooled.copiedBuffer(data));
    }

    /**
     * Inner class to track streaming session state with streamId support
     */
    private static class StreamingSession {
        private final String streamKey;
        private final long totalSize;
        private final ByteArrayBuilder buffer;

        public StreamingSession(String streamKey, long totalSize) {
            this.streamKey = streamKey;
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
