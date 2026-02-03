package com.acuity.client;

import com.acuity.common.SymmetricDecryptionHandler;
import com.acuity.common.SymmetricEncryption;
import com.acuity.common.SymmetricEncryptionHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;

public class TunnelClientApp {
    final String tunnelHost;
    final int tunnelPort;
    final int proxyPort;
    final String targetHost;
    final int targetPort;

    public TunnelClientApp(String tunnelHost, int tunnelPort, int proxyPort, String targetHost, int targetPort) {
        this.tunnelHost = tunnelHost;
        this.tunnelPort = tunnelPort;
        this.proxyPort = proxyPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public void start() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // Initialize symmetric encryption key
            SymmetricEncryption.getOrGenerateKey();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // Add encryption/decryption handlers
                        ch.pipeline().addLast(new SymmetricEncryptionHandler());
                        ch.pipeline().addLast(new SymmetricDecryptionHandler());

                        ch.pipeline().addLast(new IdleStateHandler(60, 60, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new TunnelControlHandler(TunnelClientApp.this));
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);

            ChannelFuture future = bootstrap.connect(tunnelHost, tunnelPort).sync();
            System.out.println("Connected to tunnel server at " + tunnelHost + ":" + tunnelPort + " with symmetric encryption");
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start tunnel client", e);
        } finally {
            group.shutdownGracefully();
        }
    }


    public static void main(String[] args) throws InterruptedException {
        String tunnelHost = "127.0.0.1";
        int tunnelPort = 7000;
        int proxyPort = 8080;
        String targetHost = "127.0.0.1";
        int targetPort = 80;

        if (args.length > 0) {
            tunnelHost = args[0];
        }
        if (args.length > 1) {
            tunnelPort = Integer.parseInt(args[1]);
        }
        if (args.length > 2) {
            proxyPort = Integer.parseInt(args[2]);
        }
        if (args.length > 3) {
            targetHost = args[3];
        }
        if (args.length > 4) {
            targetPort = Integer.parseInt(args[4]);
        }

        new TunnelClientApp(tunnelHost, tunnelPort, proxyPort, targetHost, targetPort).start();
    }
}
