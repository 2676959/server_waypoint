package _959.server_waypoint.crossserver.transport;

import java.io.IOException;
import java.util.Objects;

/** Retains the underlying failure while exposing a safe, stable diagnostic value. */
public final class ConnectionFailureException extends IOException {
    private final ConnectionFailureReason reason;

    public ConnectionFailureException(ConnectionFailureReason reason) {
        this(reason, null);
    }

    public ConnectionFailureException(ConnectionFailureReason reason, Throwable cause) {
        super(Objects.requireNonNull(reason).name(), cause);
        this.reason = reason;
    }

    public ConnectionFailureReason reason() { return reason; }

    public static ConnectionFailureException wrap(ConnectionFailureReason fallback, Throwable failure) {
        ConnectionFailureReason reason = ConnectionFailureReason.from(failure);
        return new ConnectionFailureException(reason == ConnectionFailureReason.IO_ERROR
                || reason == ConnectionFailureReason.INTERNAL_ERROR ? fallback : reason, failure);
    }
}
