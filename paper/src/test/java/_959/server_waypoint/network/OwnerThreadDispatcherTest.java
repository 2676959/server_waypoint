package _959.server_waypoint.network;

import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OwnerThreadDispatcherTest {
    @Test
    void deferredOwnedActionCompletesOnlyAfterScheduledTaskRuns() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        OwnerThreadDispatcher<String> dispatcher = new OwnerThreadDispatcher<>(
                ignored -> false,
                (peer, task) -> scheduled.compareAndSet(null, task)
        );

        var completion = dispatcher.dispatch(
                "peer",
                () -> ChunkedMessageSendResult.DELIVERED
        ).toCompletableFuture();

        assertFalse(completion.isDone());
        scheduled.get().run();
        assertEquals(ChunkedMessageSendResult.DELIVERED, completion.join());
    }

    @Test
    void schedulerRejectionIsAFinalDeliveryFailure() {
        OwnerThreadDispatcher<String> dispatcher = new OwnerThreadDispatcher<>(
                ignored -> false,
                (peer, task) -> false
        );

        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                dispatcher.dispatch(
                        "peer",
                        () -> ChunkedMessageSendResult.DELIVERED
                ).toCompletableFuture().join()
        );
    }

    @Test
    void ownedActionFailureIsContained() {
        OwnerThreadDispatcher<String> dispatcher = new OwnerThreadDispatcher<>(
                ignored -> true,
                (peer, task) -> {
                    throw new AssertionError("scheduler must not run");
                }
        );

        assertEquals(
                ChunkedMessageSendResult.DELIVERY_FAILED,
                dispatcher.dispatch("peer", () -> {
                    throw new IllegalStateException("send failed");
                }).toCompletableFuture().join()
        );
    }
}
