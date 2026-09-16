package _959.server_waypoint.core.network;

/** Nonthrowing transport-admission result for one logical chunked message. */
public enum ChunkedMessageSendResult {
    QUEUED,
    DELIVERED,
    UNSUPPORTED,
    PEER_BUSY,
    ENCODING_FAILED,
    DELIVERY_FAILED;

    public boolean queued() {
        return this == QUEUED;
    }

    public boolean delivered() {
        return this == DELIVERED;
    }
}
