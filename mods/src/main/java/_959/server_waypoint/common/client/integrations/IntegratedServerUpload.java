package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.server.LocalWaypointUpload;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.data.WaypointData;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class IntegratedServerUpload {
    private IntegratedServerUpload() {
    }

    public static void register(Minecraft client) {
        LocalWaypointUpload.register((server, player, request, receiver) -> {
            var playerId = player.getUUID();
            return collect(
                    client::execute,
                    server::execute,
                    () -> client.getSingleplayerServer() == server && client.player != null
                            && client.player.getUUID().equals(playerId),
                    () -> server.getPlayerList().getPlayer(playerId) == player,
                    () -> MapModIntegrations.collectUpload(request),
                    receiver
            );
        });
    }

    static CompletableFuture<ChunkedMessageSendResult> collect(
            Executor clientExecutor, Executor serverExecutor,
            BooleanSupplier clientSessionActive, BooleanSupplier serverSessionActive,
            Supplier<WaypointData> collector, Consumer<WaypointData> receiver
    ) {
        CompletableFuture<ChunkedMessageSendResult> completion = new CompletableFuture<>();
        clientExecutor.execute(() -> {
            if (!clientSessionActive.getAsBoolean()) {
                serverExecutor.execute(() -> completion.complete(ChunkedMessageSendResult.DELIVERY_FAILED));
                return;
            }
            WaypointData data;
            try {
                data = collector.get();
            } catch (RuntimeException exception) {
                serverExecutor.execute(() -> completion.completeExceptionally(exception));
                return;
            }
            serverExecutor.execute(() -> {
                if (!serverSessionActive.getAsBoolean()) {
                    completion.complete(ChunkedMessageSendResult.DELIVERY_FAILED);
                    return;
                }
                try {
                    receiver.accept(data);
                    completion.complete(ChunkedMessageSendResult.DELIVERED);
                } catch (RuntimeException exception) {
                    completion.completeExceptionally(exception);
                }
            });
        });
        return completion;
    }
}
