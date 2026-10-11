package _959.server_waypoint.crossserver.transport;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.crypto.AEADBadTagException;

/** Local diagnostics only: never infer a remote rejection from an unexplained socket close. */
public enum ConnectionFailureReason {
    HOST_RESOLUTION_FAILED,
    CONNECT_FAILED,
    HOST_UNREACHABLE,
    TIMEOUT,
    PEER_DISCONNECTED,
    SOCKET_ERROR,
    INVALID_PREFACE,
    TRANSPORT_VERSION_MISMATCH,
    APPLICATION_VERSION_MISMATCH,
    TRANSPORT_MODE_MISMATCH,
    CIPHER_SUITE_MISMATCH,
    HANDSHAKE_FAILED,
    HANDSHAKE_TRANSCRIPT_MISMATCH,
    AUTHENTICATION_FAILED,
    IDENTITY_NOT_ADMITTED,
    DUPLICATE_SERVER_ID,
    ADMISSION_CHANGED,
    CONNECTION_LIMIT_REACHED,
    REGISTRATION_REJECTED,
    REGISTRATION_TRANSCRIPT_MISMATCH,
    INVALID_FRAME,
    PROTOCOL_VIOLATION,
    REQUEST_EXPIRED,
    CHANNEL_CLOSED,
    GENERATION_EXHAUSTED,
    IO_ERROR,
    INTERNAL_ERROR;

    /** Bounded cause traversal, without parsing or exposing exception text. */
    public static ConnectionFailureReason from(Throwable failure) {
        return from(failure, 32);
    }

    private static ConnectionFailureReason from(Throwable failure, int remaining) {
        if (failure == null || remaining == 0) return INTERNAL_ERROR;
        if (failure instanceof ConnectionFailureException diagnostic) return diagnostic.reason();
        if (failure instanceof SocketTimeoutException || failure instanceof TimeoutException) return TIMEOUT;
        if (failure instanceof UnknownHostException) return HOST_RESOLUTION_FAILED;
        if (failure instanceof NoRouteToHostException) return HOST_UNREACHABLE;
        if (failure instanceof ConnectException) return CONNECT_FAILED;
        if (failure instanceof EOFException) return PEER_DISCONNECTED;
        if (failure instanceof AEADBadTagException) return AUTHENTICATION_FAILED;
        ConnectionFailureReason nested = from(failure.getCause(), remaining - 1);
        if (nested != INTERNAL_ERROR && nested != IO_ERROR) return nested;
        if (failure instanceof SocketException) return SOCKET_ERROR;
        return failure instanceof IOException ? IO_ERROR : nested;
    }
}
