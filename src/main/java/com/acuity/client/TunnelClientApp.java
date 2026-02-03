package com.acuity.client;

import com.acuity.common.SymmetricDecryptionHandler;
import com.acuity.common.SymmetricEncryption;
import com.acuity.common.SymmetricEncryptionHandler;
import com.acuity.config.ClientConfig;
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
    final String sharedKey;

    public TunnelClientApp(String tunnelHost, int tunnelPort, int proxyPort, String targetHost, int targetPort) {
        this(tunnelHost, tunnelPort, proxyPort, targetHost, targetPort, null);
    }

    public TunnelClientApp(String tunnelHost, int tunnelPort, int proxyPort, String targetHost, int targetPort, String sharedKey) {
        this.tunnelHost = tunnelHost;
        this.tunnelPort = tunnelPort;
        this.proxyPort = proxyPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.sharedKey = sharedKey;
    }

    public void start() throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // Initialize symmetric encryption key
            if (sharedKey != null && !sharedKey.isEmpty()) {
                SymmetricEncryption.setSecretKeyFromBase64(sharedKey);
                System.out.println("[TunnelClient] Using provided shared encryption key");
            } else {
                SymmetricEncryption.getOrGenerateKey();
                System.out.println("[TunnelClient] Warning: Generated new encryption key - must match server's key!");
            }

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
            System.out.println("[TunnelClient] Connected to tunnel server at " + tunnelHost + ":" + tunnelPort + " with symmetric encryption");

            try {
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                System.out.println("[TunnelClient] Client interrupted, shutting down gracefully");
                future.channel().close();
            }
        } catch (InterruptedException e) {
            System.out.println("[TunnelClient] Interrupted during startup");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }


    public static void main(String[] args) throws InterruptedException {
        ClientConfig config = new ClientConfig();

        // If first argument is a TOML file, load from it
        if (args.length > 0 && args[0].endsWith(".toml")) {
            try {
                config = ClientConfig.loadFromFile(args[0]);
                System.out.println("[TunnelClient] Configuration loaded from: " + args[0]);
            } catch (Exception e) {
                System.err.println("[TunnelClient] Failed to load configuration file: " + e.getMessage());
                System.exit(1);
            }
        } else {
            // Otherwise, use command line arguments (legacy mode)
            config = new ClientConfig();
            if (args.length > 0) {
                config.setTunnelHost(args[0]);
            }
            if (args.length > 1) {
                config.setTunnelPort(Integer.parseInt(args[1]));
            }
            if (args.length > 2) {
                config.setProxyPort(Integer.parseInt(args[2]));
            }
            if (args.length > 3) {
                config.setTargetHost(args[3]);
            }
            if (args.length > 4) {
                config.setTargetPort(Integer.parseInt(args[4]));
            }
            if (args.length > 5) {
                config.setSharedKey(args[5]);
            }
        }

        if (config.getSharedKey() == null || config.getSharedKey().isEmpty()) {
            System.err.println("[TunnelClient] Error: Shared encryption key is required!");
            System.err.println("[TunnelClient] Usage: java TunnelClientApp <config-file.toml>");
            System.err.println("[TunnelClient] Or (legacy): java TunnelClientApp <tunnelHost> <tunnelPort> <proxyPort> <targetHost> <targetPort> <sharedKey>");
            System.err.println("[TunnelClient] Get the shared key from the tunnel server output when it starts.");
            System.exit(1);
        }

        System.out.println("[TunnelClient] " + config);
        new TunnelClientApp(config.getTunnelHost(), config.getTunnelPort(), config.getProxyPort(),
                           config.getTargetHost(), config.getTargetPort(), config.getSharedKey()).start();
    }
}
