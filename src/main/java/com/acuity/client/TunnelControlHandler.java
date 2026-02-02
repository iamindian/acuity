package com.acuity.client;

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

    // Thread pool for handling TCP requests asynchronously
    private static final ExecutorService executor = new ThreadPoolExecutor(
        10,                                         // core pool size
        50,                                         // maximum pool size
        60L, TimeUnit.SECONDS,                      // keep-alive time
        new LinkedBlockingQueue<>(200),             // bounded task queue
        new ThreadPoolExecutor.CallerRunsPolicy()   // rejection policy
    );

    public TunnelControlHandler(TunnelClientApp clientApp) {
        this.clientApp = clientApp;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String action = "ADDPROXY:" + clientApp.proxyPort;
        TunnelMessage msg = new TunnelMessage(null, action, new byte[0]);
        ctx.writeAndFlush(Unpooled.copiedBuffer(msg.toBytes()));
        System.out.println("Sent control message: " + action);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        try {
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            TunnelMessage tunnelMessage = TunnelMessage.fromBytes(bytes);

            System.out.println("Control channel received: " + tunnelMessage);

            String action = tunnelMessage.getAction();
            if (action == null) {
                return;
            }

            if ("FORWARD".equalsIgnoreCase(action)) {
                String browserChannelId = tunnelMessage.getBrowserChannelId();
                byte[] requestBytes = tunnelMessage.getData();

                // Execute TCP request asynchronously using thread pool
                executor.submit(() -> {
                    try {
                        byte[] responseBytes = TcpRequestExecutor.execute(requestBytes, clientApp.targetHost, clientApp.targetPort);
                        TunnelMessage responseMsg = new TunnelMessage(browserChannelId, "FORWARD", responseBytes);
                        ctx.writeAndFlush(Unpooled.copiedBuffer(responseMsg.toBytes()));
                    } catch (Exception e) {
                        System.err.println("Error executing TCP request: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else if ("RESPONSE".equalsIgnoreCase(action)) {
                System.out.println("Proxy " + clientApp.proxyPort + " has been opened.");
            } else if ("ERROR".equalsIgnoreCase(action)) {
                String errorMsg = new String(tunnelMessage.getData(), StandardCharsets.UTF_8);
                System.err.println("Tunnel server error: " + errorMsg);
            }
        } finally {
            byteBuf.release();
        }
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


