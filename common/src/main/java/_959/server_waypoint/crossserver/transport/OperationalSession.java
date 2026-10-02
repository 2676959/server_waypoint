package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.protocol.ApplicationEnvelope;

/** Connection-scoped operational dispatch, separate from heartbeat/catalog traffic. */
public interface OperationalSession extends AutoCloseable {
    boolean receive(ApplicationEnvelope envelope);
    void maintain();
    @Override void close();
}
