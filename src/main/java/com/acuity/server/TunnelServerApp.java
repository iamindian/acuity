package com.acuity.server;

import com.acuity.common.SymmetricDecryptionHandler;
import com.acuity.common.SymmetricEncryption;
import com.acuity.common.SymmetricEncryptionHandler;
import com.acuity.config.ServerConfig;
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
        USER
    }

    private static final Map<Integer, List<TunnelServerApp>> proxyClientInstances = new HashMap<>();
    private static final Map<Integer, TunnelServerApp> userClientInstances = new HashMap<>();
    private static final Map<Integer, TunnelServerApp> serverInstances = new HashMap<>();

    private final int port;
    private final ClientType clientType;
    private final String sharedKey;

    public TunnelServerApp(int port, ClientType clientType) {
        this(port, clientType, null);
    }

    public TunnelServerApp(int port, ClientType clientType, String sharedKey) {
        this.port = port;
        this.clientType = clientType;
        this.sharedKey = sharedKey;
        if (clientType == ClientType.PROXY) {
            proxyClientInstances.computeIfAbsent(port, k -> new ArrayList<>()).add(this);
        } else if (clientType == ClientType.USER) {
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

    public static TunnelServerApp getUserClientInstance(int port) {
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
            if (sharedKey != null && !sharedKey.isEmpty()) {
                SymmetricEncryption.setSecretKeyFromBase64(sharedKey);
                System.out.println("[TunnelServer] Using provided shared encryption key");
            } else {
                SymmetricEncryption.getOrGenerateKey();
                System.out.println("[TunnelServer] Generated encryption key: " + SymmetricEncryption.getKeyAsString());
            }

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
                            } else if (clientType == ClientType.USER) {
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
            System.out.println("[TunnelServer] Tunnel Server started on port " + port + " with symmetric encryption");

            try {
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                System.out.println("[TunnelServer] Server interrupted, shutting down gracefully");
                future.channel().close();
            }
        } catch (InterruptedException e) {
            System.out.println("[TunnelServer] Interrupted during startup");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ServerConfig config = new ServerConfig();

        // If first argument is a TOML file, load from it
        if (args.length > 0 && args[0].endsWith(".toml")) {
            try {
                config = ServerConfig.loadFromFile(args[0]);
                System.out.println("[TunnelServer] Configuration loaded from: " + args[0]);
            } catch (Exception e) {
                System.err.println("[TunnelServer] Failed to load configuration file: " + e.getMessage());
                System.exit(1);
            }
        } else {
            // Otherwise, use command line arguments (legacy mode)
            if (args.length > 0) {
                config = new ServerConfig();
                config.setPort(Integer.parseInt(args[0]));
            }
            if (args.length > 1) {
                config.setSharedKey(args[1]);
            }
        }

        try {
            if (config.getSharedKey() != null && !config.getSharedKey().isEmpty()) {
                // Set the shared key from configuration (Base64-encoded)
                SymmetricEncryption.setSecretKeyFromBase64(config.getSharedKey());
                System.out.println("[TunnelServer] Using provided shared encryption key");
            } else {
                // Generate a new key and print it for sharing with clients
                SymmetricEncryption.getOrGenerateKey();
                System.out.println("[TunnelServer] Generated encryption key: " + SymmetricEncryption.getKeyAsString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption key", e);
        }

        System.out.println("[TunnelServer] " + config);
        new TunnelServerApp(config.getPort(), ClientType.SERVER, config.getSharedKey()).start();
    }
}
