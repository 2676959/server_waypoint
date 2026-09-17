package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.message.ClientUpdateRequestMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platform-contract coverage shared by the Paper maintenance task and the mod
 * server/client tick loops: one manager-wide tick grants all outbound work
 * under one aggregate budget, and received messages apply inside the
 * accounting boundary.
 */
class PlatformMessageSenderTransportTest {
    @Test
    void managerWideTickGrantsAggregateOutboundAcrossPeers() {
        RecordingSender sender = new RecordingSender();
        List<String> peers = List.of("a", "b", "c", "d", "e");
        List<ChunkedMessageDelivery> deliveries = new ArrayList<>();
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                worldMessage("x".repeat(300_000)),
                false
        );
        for (String peer : peers) {
            deliveries.add(sender.sendPlayerPreparedChunkedMessageTracked(peer, prepared));
        }
        // Admission alone emits nothing until the manager-wide tick runs.
        assertTrue(sender.packets.isEmpty());

        int firstTickFrames = sender.tickAndCountFrames();
        assertTrue(firstTickFrames > 0);
        assertTrue(
                firstTickFrames <= ChunkedMessageManager.MAX_FRAMES_PER_TICK,
                "aggregate grant exceeded the manager limit: " + firstTickFrames
        );

        int ticks = 0;
        while (sender.packets.size() < peers.size() * prepared.frames().size()) {
            assertTrue(ticks++ < 100);
            int grantedFrames = sender.tickAndCountFrames();
            assertTrue(
                    grantedFrames <= ChunkedMessageManager.MAX_FRAMES_PER_TICK,
                    "aggregate grant exceeded the manager limit: " + grantedFrames
            );
        }
        for (ChunkedMessageDelivery delivery : deliveries) {
            assertEquals(
                    ChunkedMessageSendResult.DELIVERED,
                    delivery.completion().toCompletableFuture().join()
            );
        }
        assertEquals(peers.size() * prepared.frames().size(), sender.packets.size());
    }

    @Test
    void receiveCallbackAppliesWithinAccountingBoundary() {
        RecordingSender sender = new RecordingSender();
        List<ChunkedMessage> applied = new ArrayList<>();
        MessageChunkBuffer frame = ChunkedMessageManager.createTransfer(
                new ClientUpdateRequestMessage(List.of()),
                false
        ).get(0);

        assertTrue(sender.receiveChunkedMessage("player", frame, applied::add));
        assertEquals(1, applied.size());
        assertTrue(applied.get(0) instanceof ClientUpdateRequestMessage);
        assertFalse(sender.hasPendingChunkedMessages("player"));

        // Application failures propagate raw instead of as transport failures.
        MessageChunkBuffer valid = ChunkedMessageManager.createTransfer(
                new ClientUpdateRequestMessage(List.of()),
                false
        ).get(0);
        AtomicBoolean handlerRan = new AtomicBoolean(false);
        RuntimeException applicationFailure = assertThrows(
                RuntimeException.class,
                () -> sender.receiveChunkedMessage("player", valid, ignored -> {
                    handlerRan.set(true);
                    throw new IllegalStateException("handler rejected the update");
                })
        );
        assertTrue(handlerRan.get());
        assertTrue(applicationFailure instanceof IllegalStateException);
        assertFalse(
                applicationFailure instanceof ChunkedMessageManager.ReceiveException
        );
    }

    @Test
    void trackedDeliveryCompletesAfterAsynchronousBatchCompletion() {
        RecordingSender sender = new RecordingSender();
        CompletableFuture<ChunkedMessageSendResult> batch = new CompletableFuture<>();
        sender.deferBatch = ignored -> batch;
        ChunkedMessageManager.PreparedMessage prepared = ChunkedMessageManager.prepare(
                worldMessage("async"),
                false
        );

        ChunkedMessageDelivery delivery = sender.sendPlayerPreparedChunkedMessageTracked(
                "player",
                prepared
        );
        sender.tickAndCountFrames();
        assertFalse(delivery.completion().toCompletableFuture().isDone());
        assertEquals(1, sender.packets.size());

        batch.complete(ChunkedMessageSendResult.DELIVERED);

        assertEquals(
                ChunkedMessageSendResult.DELIVERED,
                delivery.completion().toCompletableFuture().join()
        );
        assertFalse(sender.hasPendingChunkedMessages("player"));
    }

    private static WaypointData worldMessage(String description) {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "",
                "",
                "",
                new WaypointPos(0, 64, 0),
                0,
                0,
                false,
                List.of(),
                description
        );
        return WaypointData.world(List.of(new DimensionWaypointData(
                "",
                List.of(new WaypointList("", "", 1, List.of(waypoint)))
        )));
    }

    private static class RecordingSender implements PlatformMessageSender<String, String> {
        private final List<MessageChunkBuffer> packets = new ArrayList<>();
        private Function<
                List<MessageChunkBuffer>,
                CompletionStage<ChunkedMessageSendResult>
        > deferBatch;
        private int framesEmittedDuringTick;

        int tickAndCountFrames() {
            this.framesEmittedDuringTick = 0;
            this.tickChunkedMessages();
            return this.framesEmittedDuringTick;
        }

        @Override
        public void sendMessage(String source, Component component) {
        }

        @Override
        public void sendPlayerMessage(String player, Component component) {
        }

        @Override
        public void sendError(String source, Component component) {
        }

        @Override
        public void sendPacket(String source, SinglePacketMessage message) {
        }

        @Override
        public void sendPlayerPacket(String player, SinglePacketMessage message) {
        }

        @Override
        public void broadcastPacket(SinglePacketMessage message) {
        }

        @Override
        public ChunkedMessageDelivery sendChunkedMessage(String source, ChunkedMessage message) {
            return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
        }

        @Override
        public Iterable<? extends String> getBroadcastPlayers(String source) {
            return List.of(source);
        }

        @Override
        public Component getSenderName(String source) {
            return Component.text(source);
        }

        @Override
        public CompletionStage<ChunkedMessageSendResult> sendPlayerPacketBatch(
                String player,
                List<MessageChunkBuffer> packets
        ) {
            this.packets.addAll(packets);
            this.framesEmittedDuringTick += packets.size();
            if (this.deferBatch != null) {
                return this.deferBatch.apply(packets);
            }
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        }
    }
}
