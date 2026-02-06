package com.acuity.client;

import com.acuity.server.TunnelAction;
import com.acuity.server.TunnelMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TunnelControlHandler extends ChannelInboundHandlerAdapter {
    private final TunnelClientApp clientApp;

    // Streaming configuration
    private static final int CHUNK_SIZE = 8192; // 8KB chunks for streaming

    // Thread pool for handling TCP requests asynchronously
    private static final ExecutorService executor = new ThreadPoolExecutor(
        10,                                         // core pool size
        50,                                         // maximum pool size
        60L, TimeUnit.SECONDS,                      // keep-alive time
        new LinkedBlockingQueue<>(200),             // bounded task queue
        new ThreadPoolExecutor.CallerRunsPolicy()   // rejection policy
    );

    // Channel context for sending data to tunnel server
    private static volatile ChannelHandlerContext tunnelServerCtx;

    public TunnelControlHandler(TunnelClientApp clientApp) {
        this.clientApp = clientApp;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // Store the context for later use in streaming
        tunnelServerCtx = ctx;

        String action = TunnelAction.ADDPROXY.toString(String.valueOf(clientApp.proxyPort));
        TunnelMessage msg = new TunnelMessage(null, action, new byte[0]);
        ctx.writeAndFlush(Unpooled.copiedBuffer(msg.toBytes()));
        System.out.println("[TunnelClient] Sent control message: " + action);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        try {
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            TunnelMessage tunnelMessage = TunnelMessage.fromBytes(bytes);

            System.out.println("[TunnelClient] Control channel received: " + tunnelMessage);

            TunnelAction action = tunnelMessage.getAction();
            if (action == null) {
                return;
            }

            if (action == TunnelAction.FORWARD) {
                String userChannelId = tunnelMessage.getUserChannelId();
                byte[] requestBytes = tunnelMessage.getData();

                // Execute TCP request asynchronously using thread pool
                executor.submit(() -> {
                    try {
                        byte[] responseBytes = TcpRequestExecutor.execute(requestBytes, clientApp.targetHost, clientApp.targetPort);

                        // Stream response if it's large, otherwise send as single FORWARD
                        if (responseBytes.length > CHUNK_SIZE) {
                            streamDataToServer(userChannelId, responseBytes, ctx);
                        } else {
                            TunnelMessage responseMsg = new TunnelMessage(userChannelId, TunnelAction.FORWARD, responseBytes);
                            ctx.writeAndFlush(Unpooled.copiedBuffer(responseMsg.toBytes()));
                        }
                    } catch (Exception e) {
                        System.err.println("[TunnelClient] Error executing TCP request: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else if (action == TunnelAction.RESPONSE) {
                System.out.println("[TunnelClient] Proxy " + clientApp.proxyPort + " has been opened.");
            } else if (action == TunnelAction.ERROR) {
                String errorMsg = new String(tunnelMessage.getData(), StandardCharsets.UTF_8);
                System.err.println("[TunnelClient] Tunnel server error: " + errorMsg);
            }
        } finally {
            byteBuf.release();
        }
    }

    /**
     * Stream large data to tunnel server in chunks
     * Sends STREAM_START, followed by STREAM_DATA chunks, then STREAM_END
     */
    public static void streamDataToServer(String userChannelId, byte[] data, ChannelHandlerContext ctx) {
        if (data.length <= CHUNK_SIZE) {
            // Small data: send as single FORWARD message
            System.out.println("[TunnelClient] Sending small message (" + data.length + " bytes) to tunnel server");
            TunnelMessage tunnelMessage = new TunnelMessage(userChannelId, TunnelAction.FORWARD, data);
            ctx.writeAndFlush(Unpooled.copiedBuffer(tunnelMessage.toBytes()));
            return;
        }

        // Large data: stream in chunks
        System.out.println("[TunnelClient] Streaming large message (" + data.length + " bytes) to tunnel server in " + CHUNK_SIZE + " byte chunks");

        // Send STREAM_START message
        TunnelMessage startMessage = new TunnelMessage(userChannelId, TunnelAction.STREAM_START,
            String.valueOf(data.length).getBytes(StandardCharsets.UTF_8));
        ctx.write(Unpooled.copiedBuffer(startMessage.toBytes()));

        // Send data in chunks
        int offset = 0;
        int chunkNumber = 1;
        while (offset < data.length) {
            int chunkLength = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[chunkLength];
            System.arraycopy(data, offset, chunk, 0, chunkLength);

            TunnelMessage chunkMessage = new TunnelMessage(userChannelId, TunnelAction.STREAM_DATA, chunk);
            ctx.write(Unpooled.copiedBuffer(chunkMessage.toBytes()));

            System.out.println("[TunnelClient] Sent chunk " + chunkNumber +
                " (" + chunkLength + " bytes, offset: " + offset + ")");

            offset += chunkLength;
            chunkNumber++;
        }

        // Send STREAM_END message
        TunnelMessage endMessage = new TunnelMessage(userChannelId, TunnelAction.STREAM_END, new byte[0]);
        ctx.write(Unpooled.copiedBuffer(endMessage.toBytes()));
        ctx.flush();

        System.out.println("[TunnelClient] Stream completed: " +
            (chunkNumber - 1) + " chunks sent");
    }

    /**
     * Get the tunnel server context for streaming data
     */
    public static ChannelHandlerContext getTunnelServerContext() {
        return tunnelServerCtx;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * Shutdown the thread pool gracefully
     */
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}


