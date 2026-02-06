package com.acuity.server;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Message class for tunnel communication
 */
public class TunnelMessage {
    private final String userChannelId;
    private final TunnelAction action;
    private final String rawAction; // For special cases like ADDPROXY:port
    private final byte[] data;

    public TunnelMessage(String userChannelId, TunnelAction action, byte[] data) {
        this.userChannelId = userChannelId;
        this.action = action;
        this.rawAction = action != null ? action.toString() : null;
        this.data = data;
    }

    public TunnelMessage(String userChannelId, String actionString, byte[] data) {
        this.userChannelId = userChannelId;
        this.rawAction = actionString;
        this.action = TunnelAction.fromString(actionString);
        this.data = data;
    }

    public String getUserChannelId() {
        return userChannelId;
    }

    public TunnelAction getAction() {
        return action;
    }

    public String getRawAction() {
        return rawAction;
    }

    public byte[] getData() {
        return data;
    }

    /**
     * Serialize the TunnelMessage to a byte array for transmission
     */
    public byte[] toBytes() {
        String encodedData = data != null ? Base64.getEncoder().encodeToString(data) : "";
        String serialized = String.format("%s|%s|%s",
            userChannelId != null ? userChannelId : "",
            rawAction != null ? rawAction : "",
            encodedData);
        return serialized.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserialize a TunnelMessage from a byte array
     */
    public static TunnelMessage fromBytes(byte[] bytes) {
        String serialized = new String(bytes, StandardCharsets.UTF_8);
        String[] parts = serialized.split("\\|", 3);

        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid TunnelMessage format");
        }

        String userChannelId = parts[0].isEmpty() ? null : parts[0];
        String actionString = parts[1].isEmpty() ? null : parts[1];
        byte[] data = parts[2].isEmpty() ? new byte[0] : Base64.getDecoder().decode(parts[2]);

        return new TunnelMessage(userChannelId, actionString, data);
    }

    @Override
    public String toString() {
        return "TunnelMessage{" +
                "userChannelId='" + userChannelId + '\'' +
                ", action=" + action +
                ", data.length=" + (data != null ? data.length : 0) +
                '}';
    }
}
