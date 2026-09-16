package _959.server_waypoint.core.network;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Admission and final-delivery state for one logical chunked message. */
public final class ChunkedMessageDelivery {
    private final ChunkedMessageSendResult admissionResult;
    private final CompletionStage<ChunkedMessageSendResult> completion;

    private ChunkedMessageDelivery(
            ChunkedMessageSendResult admissionResult,
            CompletionStage<ChunkedMessageSendResult> completion
    ) {
        this.admissionResult = Objects.requireNonNull(admissionResult, "admissionResult");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    public static ChunkedMessageDelivery queued(
            CompletionStage<ChunkedMessageSendResult> completion
    ) {
        return new ChunkedMessageDelivery(ChunkedMessageSendResult.QUEUED, completion);
    }

    public static ChunkedMessageDelivery rejected(ChunkedMessageSendResult result) {
        if (result.queued() || result.delivered()) {
            throw new IllegalArgumentException("Rejected delivery requires a failure result");
        }
        return new ChunkedMessageDelivery(result, CompletableFuture.completedFuture(result));
    }

    public ChunkedMessageSendResult admissionResult() {
        return this.admissionResult;
    }

    public CompletionStage<ChunkedMessageSendResult> completion() {
        return this.completion;
    }

    public boolean queued() {
        return this.admissionResult.queued();
    }
}
