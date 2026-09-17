package _959.server_waypoint.core.network;

/** A bounded message could not be encoded without violating its wire contract. */
public final class MessageEncodingException extends RuntimeException {
    public MessageEncodingException(String message) {
        super(message);
    }

    public MessageEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
