package com.acuity.server;

/**
 * Enum representing all possible tunnel message actions
 */
public enum TunnelAction {
    /** Forward data from one channel to another */
    FORWARD,

    /** Add a new proxy server (format: ADDPROXY:port) */
    ADDPROXY,

    /** Response message from server to client */
    RESPONSE,

    /** Error message */
    ERROR,

    /** Start streaming large data (includes total size) */
    STREAM_START,

    /** Stream data chunk */
    STREAM_DATA,

    /** End streaming session */
    STREAM_END,

    /** Ping to keep connection alive */
    PING,

    /** Pong response to ping */
    PONG,

    /** Exit/close connection */
    EXIT;

    /**
     * Parse action string to enum
     * Handles special case for ADDPROXY:port format
     */
    public static TunnelAction fromString(String action) {
        if (action == null || action.isEmpty()) {
            return null;
        }

        String actionUpper = action.toUpperCase();

        // Handle ADDPROXY:port format
        if (actionUpper.startsWith("ADDPROXY:")) {
            return ADDPROXY;
        }

        try {
            return TunnelAction.valueOf(actionUpper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Convert enum to string representation
     * For ADDPROXY, use the provided port parameter
     */
    public String toString(String... params) {
        if (this == ADDPROXY && params.length > 0) {
            return "ADDPROXY:" + params[0];
        }
        return this.name();
    }

    @Override
    public String toString() {
        return this.name();
    }
}
