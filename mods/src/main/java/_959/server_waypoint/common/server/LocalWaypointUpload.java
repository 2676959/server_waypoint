package _959.server_waypoint.common.server;

import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.WaypointData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Client-installed bridge without client class references on dedicated servers. */
public final class LocalWaypointUpload {
    private static volatile Handler handler;

    private LocalWaypointUpload() {
    }

    public static void register(Handler clientHandler) {
        handler = clientHandler;
    }

    public static boolean isAvailable() {
        return handler != null;
    }

    public static CompletionStage<ChunkedMessageSendResult> dispatch(
            MinecraftServer server, ServerPlayer player, UploadRequestBuffer request,
            Consumer<WaypointData> receiver
    ) {
        Handler current = handler;
        return current == null
                ? CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERY_FAILED)
                : current.collect(server, player, request, receiver);
    }

    @FunctionalInterface
    public interface Handler {
        CompletionStage<ChunkedMessageSendResult> collect(
                MinecraftServer server, ServerPlayer player, UploadRequestBuffer request,
                Consumer<WaypointData> receiver
        );
    }
}
