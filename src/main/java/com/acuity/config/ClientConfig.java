package com.acuity.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Configuration for the Acuity Tunnel Client loaded from TOML file
 */
public class ClientConfig {
    private String tunnelHost;
    private int tunnelPort;
    private int proxyPort;
    private String targetHost;
    private int targetPort;
    private String sharedKey;
    private String groupId;
    private int corePoolSize;
    private int maxPoolSize;
    private long keepAliveTimeSeconds;
    private int queueCapacity;
    private long idleTimeoutSeconds;
    private boolean soKeepalive;
    private boolean tcpNodelay;

    // Default values
    public ClientConfig() {
        this.tunnelHost = "127.0.0.1";
        this.tunnelPort = 7000;
        this.proxyPort = 8080;
        this.targetHost = "127.0.0.1";
        this.targetPort = 80;
        this.sharedKey = null;
        this.groupId = "default";
        this.corePoolSize = 10;
        this.maxPoolSize = 50;
        this.keepAliveTimeSeconds = 60;
        this.queueCapacity = 200;
        this.idleTimeoutSeconds = 60;
        this.soKeepalive = true;
        this.tcpNodelay = true;
    }

    /**
     * Load configuration from TOML file
     */
    public static ClientConfig loadFromFile(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        Toml toml = new Toml().read(content);

        ClientConfig config = new ClientConfig();

        if (toml.contains("client")) {
            Toml clientConfig = toml.getTable("client");
            if (clientConfig.contains("tunnelHost")) {
                config.tunnelHost = clientConfig.getString("tunnelHost");
            }
            if (clientConfig.contains("tunnelPort")) {
                config.tunnelPort = clientConfig.getLong("tunnelPort").intValue();
            }
            if (clientConfig.contains("proxyPort")) {
                config.proxyPort = clientConfig.getLong("proxyPort").intValue();
            }
            if (clientConfig.contains("targetHost")) {
                config.targetHost = clientConfig.getString("targetHost");
            }
            if (clientConfig.contains("targetPort")) {
                config.targetPort = clientConfig.getLong("targetPort").intValue();
            }
            if (clientConfig.contains("sharedKey")) {
                config.sharedKey = clientConfig.getString("sharedKey");
            }
            if (clientConfig.contains("groupId")) {
                config.groupId = clientConfig.getString("groupId");
            }
        }

        if (toml.contains("threadPool")) {
            Toml threadPoolConfig = toml.getTable("threadPool");
            if (threadPoolConfig.contains("corePoolSize")) {
                config.corePoolSize = threadPoolConfig.getLong("corePoolSize").intValue();
            }
            if (threadPoolConfig.contains("maxPoolSize")) {
                config.maxPoolSize = threadPoolConfig.getLong("maxPoolSize").intValue();
            }
            if (threadPoolConfig.contains("keepAliveTimeSeconds")) {
                config.keepAliveTimeSeconds = threadPoolConfig.getLong("keepAliveTimeSeconds");
            }
            if (threadPoolConfig.contains("queueCapacity")) {
                config.queueCapacity = threadPoolConfig.getLong("queueCapacity").intValue();
            }
        }

        if (toml.contains("netty")) {
            Toml nettyConfig = toml.getTable("netty");
            if (nettyConfig.contains("idleTimeoutSeconds")) {
                config.idleTimeoutSeconds = nettyConfig.getLong("idleTimeoutSeconds");
            }
            if (nettyConfig.contains("soKeepalive")) {
                config.soKeepalive = nettyConfig.getBoolean("soKeepalive");
            }
            if (nettyConfig.contains("tcpNodelay")) {
                config.tcpNodelay = nettyConfig.getBoolean("tcpNodelay");
            }
        }

        return config;
    }

    // Getters
    public String getTunnelHost() {
        return tunnelHost;
    }

    public void setTunnelHost(String tunnelHost) {
        this.tunnelHost = tunnelHost;
    }

    public int getTunnelPort() {
        return tunnelPort;
    }

    public void setTunnelPort(int tunnelPort) {
        this.tunnelPort = tunnelPort;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public void setTargetHost(String targetHost) {
        this.targetHost = targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getKeepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public boolean isSoKeepalive() {
        return soKeepalive;
    }

    public boolean isTcpNodelay() {
        return tcpNodelay;
    }

    @Override
    public String toString() {
        return "ClientConfig{" +
                "tunnelHost='" + tunnelHost + '\'' +
                ", tunnelPort=" + tunnelPort +
                ", proxyPort=" + proxyPort +
                ", targetHost='" + targetHost + '\'' +
                ", targetPort=" + targetPort +
                ", sharedKey=" + (sharedKey != null ? "***" : "null") +
                ", groupId='" + groupId + '\'' +
                ", corePoolSize=" + corePoolSize +
                ", maxPoolSize=" + maxPoolSize +
                ", keepAliveTimeSeconds=" + keepAliveTimeSeconds +
                ", queueCapacity=" + queueCapacity +
                ", idleTimeoutSeconds=" + idleTimeoutSeconds +
                ", soKeepalive=" + soKeepalive +
                ", tcpNodelay=" + tcpNodelay +
                '}';
    }
}
