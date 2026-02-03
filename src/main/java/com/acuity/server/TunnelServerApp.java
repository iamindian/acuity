package com.acuity.server;

import com.acuity.common.SymmetricDecryptionHandler;
import com.acuity.common.SymmetricEncryption;
import com.acuity.common.SymmetricEncryptionHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * TCP Server using Netty
 */
public class TunnelServerApp {
    public enum ClientType {
        SERVER,
        PROXY,
        BROWSER
    }

    private static final Map<Integer, List<TunnelServerApp>> proxyClientInstances = new HashMap<>();
    private static final Map<Integer, TunnelServerApp> userClientInstances = new HashMap<>();
    private static final Map<Integer, TunnelServerApp> serverInstances = new HashMap<>();

    private final int port;
    private final ClientType clientType;

    public TunnelServerApp(int port, ClientType clientType) {
        this.port = port;
        this.clientType = clientType;
        if (clientType == ClientType.PROXY) {
            proxyClientInstances.computeIfAbsent(port, k -> new ArrayList<>()).add(this);
        } else if (clientType == ClientType.BROWSER) {
            userClientInstances.put(port, this);
        } else {
            serverInstances.put(port, this);
        }
    }

    public static TunnelServerApp getProxyClientInstance(int port) {
        List<TunnelServerApp> apps = proxyClientInstances.get(port);
        return (apps != null && !apps.isEmpty()) ? apps.get(0) : null;
    }

    public static List<TunnelServerApp> getProxyClientInstances(int port) {
        return new ArrayList<>(proxyClientInstances.getOrDefault(port, new ArrayList<>()));
    }

    public static TunnelServerApp getBrowserClientInstance(int port) {
        return userClientInstances.get(port);
    }

    public static TunnelServerApp getServerInstance(int port) {
        return serverInstances.get(port);
    }

    public static Map<Integer, List<TunnelServerApp>> getAllProxyClientInstances() {
        Map<Integer, List<TunnelServerApp>> result = new HashMap<>();
        for (Map.Entry<Integer, List<TunnelServerApp>> entry : proxyClientInstances.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    public static Map<Integer, TunnelServerApp> getAllUserClientInstances() {
        return new HashMap<>(userClientInstances);
    }

    public static Map<Integer, TunnelServerApp> getAllServerInstances() {
        return new HashMap<>(serverInstances);
    }

    public void start() throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // Initialize symmetric encryption key
            SymmetricEncryption.getOrGenerateKey();

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // Add encryption/decryption handlers
                            ch.pipeline().addLast(new SymmetricEncryptionHandler());
                            ch.pipeline().addLast(new SymmetricDecryptionHandler());

                            ch.pipeline()
                                    .addLast(new IdleStateHandler(60, 60, 0, TimeUnit.SECONDS));

                            if (clientType == ClientType.PROXY) {
                                ch.pipeline().addLast(new ProxyClientHandler(proxyClientInstances));
                            } else if (clientType == ClientType.BROWSER) {
                                ch.pipeline().addLast(new UserClientHandler(userClientInstances));
                            } else {
                                ch.pipeline().addLast(new TunnelServerHandler(proxyClientInstances, userClientInstances, serverInstances));
                            }
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("Tunnel Server started on port " + port + " with symmetric encryption");

            future.channel().closeFuture().sync();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start tunnel server", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int tunnelServerPort = 7000;
        if (args.length > 0) {
            tunnelServerPort = Integer.parseInt(args[0]);
        }
        new TunnelServerApp(tunnelServerPort, ClientType.SERVER).start();
    }
}
