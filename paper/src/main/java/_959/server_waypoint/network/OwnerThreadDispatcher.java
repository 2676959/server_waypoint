package _959.server_waypoint.network;

import _959.server_waypoint.core.network.ChunkedMessageSendResult;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Schedules one player-owned delivery and reports its final outcome exactly once. */
final class OwnerThreadDispatcher<P> {
    private final Predicate<P> ownershipCheck;
    private final BiFunction<P, Runnable, Boolean> scheduler;

    OwnerThreadDispatcher(
            Predicate<P> ownershipCheck,
            BiFunction<P, Runnable, Boolean> scheduler
    ) {
        this.ownershipCheck = Objects.requireNonNull(ownershipCheck, "ownershipCheck");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    CompletionStage<ChunkedMessageSendResult> dispatch(
            P peer,
            Supplier<ChunkedMessageSendResult> ownedAction
    ) {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(ownedAction, "ownedAction");
        CompletableFuture<ChunkedMessageSendResult> completion = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                ChunkedMessageSendResult result = Objects.requireNonNull(
                        ownedAction.get(),
                        "owned delivery result"
                );
                completion.complete(result.queued()
                        ? ChunkedMessageSendResult.DELIVERY_FAILED
                        : result);
            } catch (RuntimeException exception) {
                completion.complete(ChunkedMessageSendResult.DELIVERY_FAILED);
            }
        };
        try {
            if (this.ownershipCheck.test(peer)) {
                task.run();
            } else if (!this.scheduler.apply(peer, task)) {
                completion.complete(ChunkedMessageSendResult.DELIVERY_FAILED);
            }
        } catch (RuntimeException exception) {
            completion.complete(ChunkedMessageSendResult.DELIVERY_FAILED);
        }
        return completion;
    }
}
