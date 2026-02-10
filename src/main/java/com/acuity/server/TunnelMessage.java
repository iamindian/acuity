package com.acuity.server;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Message class for tunnel communication
 */
public class TunnelMessage {
    private final String userChannelId;
    private final String streamId; // Stream ID for multiplexing multiple streams per user
    private final TunnelAction action;
    private final String rawAction; // For special cases like ADDPROXY:port
    private final byte[] data;

    public TunnelMessage(String userChannelId, TunnelAction action, byte[] data) {
        this(userChannelId, "0", action, data);
    }

    public TunnelMessage(String userChannelId, String streamId, TunnelAction action, byte[] data) {
        this.userChannelId = userChannelId;
        this.streamId = streamId != null ? streamId : "0";
        this.action = action;
        this.rawAction = action != null ? action.toString() : null;
        this.data = data;
    }

    public TunnelMessage(String userChannelId, String actionString, byte[] data) {
        this(userChannelId, "0", actionString, data);
    }

    public TunnelMessage(String userChannelId, String streamId, String actionString, byte[] data) {
        this.userChannelId = userChannelId;
        this.streamId = streamId != null ? streamId : "0";
        this.rawAction = actionString;
        this.action = TunnelAction.fromString(actionString);
        this.data = data;
    }

    public String getUserChannelId() {
        return userChannelId;
    }

    public String getStreamId() {
        return streamId;
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
     * Get composite key for stream tracking: userChannelId:streamId
     */
    public String getStreamKey() {
        return userChannelId + ":" + streamId;
    }

    /**
     * Serialize the TunnelMessage to a byte array for transmission
     * Format: userChannelId|streamId|action|base64Data
     */
    public byte[] toBytes() {
        String encodedData = data != null ? Base64.getEncoder().encodeToString(data) : "";
        String serialized = String.format("%s|%s|%s|%s",
            userChannelId != null ? userChannelId : "",
            streamId != null ? streamId : "0",
            rawAction != null ? rawAction : "",
            encodedData);
        return serialized.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserialize a TunnelMessage from a byte array
     * Format: userChannelId|streamId|action|base64Data
     */
    public static TunnelMessage fromBytes(byte[] bytes) {
        String serialized = new String(bytes, StandardCharsets.UTF_8);
        String[] parts = serialized.split("\\|", 4);

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid TunnelMessage format");
        }

        String userChannelId = parts[0].isEmpty() ? null : parts[0];
        String streamId = (parts.length > 1 && !parts[1].isEmpty()) ? parts[1] : "0";
        String actionString = parts[2].isEmpty() ? null : parts[2];
        byte[] data = (parts.length > 3 && !parts[3].isEmpty()) ? Base64.getDecoder().decode(parts[3]) : new byte[0];

        return new TunnelMessage(userChannelId, streamId, actionString, data);
    }

    @Override
    public String toString() {
        return "TunnelMessage{" +
                "userChannelId='" + userChannelId + '\'' +
                ", streamId='" + streamId + '\'' +
                ", action=" + action +
                ", data.length=" + (data != null ? data.length : 0) +
                '}';
    }
}
