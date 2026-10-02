package _959.server_waypoint.crossserver.transport;

import java.util.concurrent.*;

/** Serial lifecycle/admin owner with a bounded queue and reserved start/stop slots. */
public abstract class AsyncTransportLifecycle implements TransportLifecycle {
    private final ThreadPoolExecutor control;
    private final Semaphore commands = new Semaphore(64);
    private CompletableFuture<TransportResult> started;
    private CompletableFuture<TransportResult> stopped;
    protected volatile boolean stopping;

    protected AsyncTransportLifecycle(String threadName) {
        control = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(66), task -> {
            Thread thread = new Thread(task, threadName); thread.setDaemon(true); return thread;
        });
    }
    @Override public final synchronized CompletionStage<TransportResult> start() {
        if (stopping) return CompletableFuture.completedFuture(TransportResult.UNAVAILABLE);
        if (started != null) return started.minimalCompletionStage();
        started = new CompletableFuture<>();
        control.execute(() -> {
            TransportResult result;
            try { result = stopping ? TransportResult.UNAVAILABLE : startResources(); }
            catch (Exception failure) {
                try { stopResources(); } catch (Exception ignored) { }
                onStartFailure(failure);
                result = failure instanceof IllegalArgumentException ? TransportResult.INVALID_CONFIGURATION : TransportResult.UNAVAILABLE;
            }
            started.complete(result);
        });
        return started.minimalCompletionStage();
    }
    @Override public final synchronized CompletionStage<TransportResult> stop() {
        if (stopped != null) return stopped.minimalCompletionStage();
        stopping = true;
        stopped = new CompletableFuture<>();
        control.execute(() -> {
            TransportResult result = TransportResult.SUCCESS;
            try { stopResources(); } catch (Exception failure) { result = TransportResult.UNAVAILABLE; }
            control.shutdown();
            stopped.complete(result);
        });
        return stopped.minimalCompletionStage();
    }
    protected final synchronized CompletionStage<TransportResult> command(Callable<TransportResult> action) {
        if (stopping || started == null || !commands.tryAcquire()) return CompletableFuture.completedFuture(TransportResult.UNAVAILABLE);
        CompletableFuture<TransportResult> result = new CompletableFuture<>();
        control.execute(() -> {
            TransportResult value;
            try { value = action.call(); } catch (Exception failure) { value = TransportResult.UNAVAILABLE; }
            commands.release(); result.complete(value);
        });
        return result.minimalCompletionStage();
    }
    protected abstract TransportResult startResources() throws Exception;
    protected void onStartFailure(Exception failure) { }
    protected abstract void stopResources() throws Exception;

    /** Called only by the control owner after all sockets have been closed. */
    protected static void terminate(ExecutorService executor) throws InterruptedException {
        if (executor == null) return;
        executor.shutdownNow();
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) { }
    }
    protected static ThreadFactory daemonThreads(String name) {
        return task -> { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; };
    }
}
