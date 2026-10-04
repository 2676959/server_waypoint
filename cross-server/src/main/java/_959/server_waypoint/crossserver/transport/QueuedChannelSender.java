package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import java.util.UUID;
import java.util.concurrent.*;

/** Bounded nonblocking enqueue; only the private worker may wait on socket output. */
public final class QueuedChannelSender implements AutoCloseable {
    private final TcpChannel channel;
    private final ThreadPoolExecutor worker;
    public QueuedChannelSender(TcpChannel channel) {
        this.channel = channel;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), task -> {
            Thread thread = new Thread(task, "server-waypoint-handoff-writer"); thread.setDaemon(true); return thread;
        });
    }
    public boolean send(UUID request, ApplicationMessage message) {
        if (channel.isClosed()) return false;
        try {
            worker.execute(() -> {
                try { channel.send(request, message); } catch (Exception failure) { channel.close(); }
            });
            return true;
        } catch (RejectedExecutionException full) { channel.close(); return false; }
    }
    @Override public void close() { worker.shutdownNow(); }
}
