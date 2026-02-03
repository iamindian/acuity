package com.acuity.config;

import com.moandjiezana.toml.Toml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Configuration for the Acuity Tunnel Server loaded from TOML file
 */
public class ServerConfig {
    private int port;
    private String sharedKey;
    private int bossGroupSize;
    private int workerGroupSize;
    private long idleTimeoutSeconds;
    private int soBacklog;
    private boolean soKeepalive;
    private boolean tcpNodelay;

    // Default values
    public ServerConfig() {
        this.port = 7000;
        this.sharedKey = null;
        this.bossGroupSize = 1;
        this.workerGroupSize = 0; // 0 means use number of CPUs * 2
        this.idleTimeoutSeconds = 60;
        this.soBacklog = 128;
        this.soKeepalive = true;
        this.tcpNodelay = true;
    }

    /**
     * Load configuration from TOML file
     */
    public static ServerConfig loadFromFile(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        Toml toml = new Toml().read(content);

        ServerConfig config = new ServerConfig();

        if (toml.contains("server")) {
            Toml serverConfig = toml.getTable("server");
            if (serverConfig.contains("port")) {
                config.port = serverConfig.getLong("port").intValue();
            }
            if (serverConfig.contains("sharedKey")) {
                config.sharedKey = serverConfig.getString("sharedKey");
            }
            if (serverConfig.contains("bossGroupSize")) {
                config.bossGroupSize = serverConfig.getLong("bossGroupSize").intValue();
            }
            if (serverConfig.contains("workerGroupSize")) {
                config.workerGroupSize = serverConfig.getLong("workerGroupSize").intValue();
            }
            if (serverConfig.contains("idleTimeoutSeconds")) {
                config.idleTimeoutSeconds = serverConfig.getLong("idleTimeoutSeconds");
            }
            if (serverConfig.contains("soBacklog")) {
                config.soBacklog = serverConfig.getLong("soBacklog").intValue();
            }
            if (serverConfig.contains("soKeepalive")) {
                config.soKeepalive = serverConfig.getBoolean("soKeepalive");
            }
            if (serverConfig.contains("tcpNodelay")) {
                config.tcpNodelay = serverConfig.getBoolean("tcpNodelay");
            }
        }

        return config;
    }

    // Getters
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    public int getBossGroupSize() {
        return bossGroupSize;
    }

    public int getWorkerGroupSize() {
        return workerGroupSize;
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public int getSoBacklog() {
        return soBacklog;
    }

    public boolean isSoKeepalive() {
        return soKeepalive;
    }

    public boolean isTcpNodelay() {
        return tcpNodelay;
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "port=" + port +
                ", sharedKey=" + (sharedKey != null ? "***" : "null") +
                ", bossGroupSize=" + bossGroupSize +
                ", workerGroupSize=" + workerGroupSize +
                ", idleTimeoutSeconds=" + idleTimeoutSeconds +
                ", soBacklog=" + soBacklog +
                ", soKeepalive=" + soKeepalive +
                ", tcpNodelay=" + tcpNodelay +
                '}';
    }
}
