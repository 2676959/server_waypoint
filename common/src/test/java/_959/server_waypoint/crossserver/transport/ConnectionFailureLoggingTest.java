package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.handoff.TeleportCoordinatorLog;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionFailureLoggingTest {
    @Test void logUsesUnderlyingReasonWithoutExposingExceptionText() {
        StaticLoggerBinder.clear();
        IOException failure = new IOException("private coordinator configuration",
                new ConnectException("secret endpoint"));
        TeleportCoordinatorLog.connectionFailure(TeleportCoordinatorLog.BACKEND,
                new RemoteServerId("building"), failure);
        assertEquals(java.util.List.of("server_waypoint.teleport_coordinator.backend warn "
                + "connection_failed server=building cause=CONNECT_FAILED exception=IOException"),
                StaticLoggerBinder.events());
    }

    @Test void proxyLogUsesTypedRejectionWithUnidentifiedPeer() {
        StaticLoggerBinder.clear();
        TeleportCoordinatorLog.connectionFailure(TeleportCoordinatorLog.PROXY, null,
                new IOException("private transcript", new ConnectionFailureException(
                        ConnectionFailureReason.TRANSPORT_MODE_MISMATCH)));
        assertEquals(java.util.List.of("server_waypoint.teleport_coordinator.proxy warn "
                + "connection_failed server=unidentified cause=TRANSPORT_MODE_MISMATCH exception=IOException"),
                StaticLoggerBinder.events());
    }

    @Test void exceptionMessagesNeverDetermineTheReason() {
        assertEquals(ConnectionFailureReason.IO_ERROR,
                ConnectionFailureReason.from(new IOException("TRANSPORT_MODE_MISMATCH")));
        assertEquals(ConnectionFailureReason.PEER_DISCONNECTED,
                ConnectionFailureReason.from(new IOException("TCP receive rejected", new EOFException())));
        assertEquals(ConnectionFailureReason.SOCKET_ERROR,
                ConnectionFailureReason.from(new IOException("TCP receive rejected", new SocketException())));
    }

    @Test void cyclicCauseChainHasBoundedFallback() {
        IOException first = new IOException();
        IOException second = new IOException();
        first.initCause(second);
        second.initCause(first);
        assertEquals(ConnectionFailureReason.IO_ERROR, ConnectionFailureReason.from(first));
    }
}
