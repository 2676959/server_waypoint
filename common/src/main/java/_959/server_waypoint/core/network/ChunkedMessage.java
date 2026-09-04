package _959.server_waypoint.core.network;

/** A logical message transported through the shared message-chunk channel. */
public interface ChunkedMessage extends NetworkMessage {
    ChunkedMessageType<?> getType();
}
