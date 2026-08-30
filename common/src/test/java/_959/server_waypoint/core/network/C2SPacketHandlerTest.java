package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.WaypointEditResult;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.message.ClientUpdateRequestMessage;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.PreparedMessage;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.network.upload.UploadConflictPolicy;
import _959.server_waypoint.core.network.upload.UploadScope;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C2SPacketHandlerTest {
    @TempDir
    private Path tempDir;

    @Test
    void editPermissionDenialDoesNotResolveOrMutateTheTarget() {
        TestSender sender = new TestSender();
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
        };
        C2SPacketHandler<String, String, String> handler = new C2SPacketHandler<>(
                sender,
                server,
                new TestPermissionManager(false),
                navigationService(),
                uploadCoordinator(server)
        );

        handler.onWaypointEditRequest("player", request());

        assertEquals(EditResultStatus.PERMISSION_DENIED, sender.lastResult().status());
        assertNull(server.getWaypointFileManager("minecraft:overworld"));
    }

    @Test
    void malformedRequestFailureReturnsStructuredStatus() {
        TestSender sender = new TestSender();
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
            @Override
            public WaypointEditResult updateWaypoint(
                    EditTarget target,
                    Integer expectedSyncNum,
                    WaypointPatch patch,
                    Consumer<WaypointEditResult> preCommitAction,
                    Consumer<WaypointEditResult> resultAction
            ) {
                throw new IllegalArgumentException("malformed target");
            }
        };
        C2SPacketHandler<String, String, String> handler = new C2SPacketHandler<>(
                sender,
                server,
                new TestPermissionManager(true),
                navigationService(),
                uploadCoordinator(server)
        );

        handler.onWaypointEditRequest("player", request());

        assertEquals(EditResultStatus.MALFORMED_REQUEST, sender.lastResult().status());
        assertEquals(17L, sender.lastResult().requestId());
    }

    @Test
    void encodingFailureReturnsStructuredStatusWithoutEscapingTheBoundary() {
        TestSender sender = new TestSender();
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
            @Override
            public WaypointEditResult updateWaypoint(
                    EditTarget target,
                    Integer expectedSyncNum,
                    WaypointPatch patch,
                    Consumer<WaypointEditResult> preCommitAction,
                    Consumer<WaypointEditResult> resultAction
            ) {
                throw new MessageEncodingException("simulated encoding failure");
            }
        };
        C2SPacketHandler<String, String, String> handler = new C2SPacketHandler<>(
                sender,
                server,
                new TestPermissionManager(true),
                navigationService(),
                uploadCoordinator(server)
        );

        handler.onWaypointEditRequest("player", request());

        assertEquals(EditResultStatus.ENCODING_FAILED, sender.lastResult().status());
        assertEquals(17L, sender.lastResult().requestId());
    }

    @Test
    void oneBroadcastFailureDoesNotStopLaterRecipients() {
        FailingBroadcastSender sender = new FailingBroadcastSender();
        WaypointModificationMessage modification = new WaypointModificationMessage(
                "minecraft:overworld",
                "list",
                "list",
                "waypoint",
                new SimpleWaypoint(
                        "waypoint",
                        "W",
                        new WaypointPos(0, 64, 0),
                        0,
                        0,
                        false
                ),
                WaypointModificationType.ADD,
                2
        );

        sender.broadcastWaypointModification("source", modification);

        assertEquals(List.of("first", "second"), sender.attemptedRecipients);
        assertEquals(1, sender.prepareCalls);
    }

    @Test
    void generalChunksRequireACompatibleHandshake() {
        TestSender sender = new TestSender();
        sender.capable = false;
        C2SPacketHandler<String, String, String> handler = handler(sender);

        for (MessageChunkBuffer frame : frames(new ClientUpdateRequestMessage(List.of()))) {
            handler.onMessageChunk("player", frame);
        }

        assertEquals(0, sender.receivedChunks);
        assertTrue(sender.packets.isEmpty());
    }

    @Test
    void handshakeCapabilityIsClearedOnDisconnect() {
        TestSender sender = new TestSender();
        sender.capable = false;
        C2SPacketHandler<String, String, String> handler = handler(sender);

        handler.onClientHandshake("player", new ClientHandshakeBuffer());
        assertTrue(sender.capable);

        handler.onDisconnect("player");
        assertFalse(sender.capable);
    }

    @Test
    void outboundChunkedMessagesAreRejectedUntilHandshake() {
        TestSender sender = new TestSender();
        sender.capable = false;

        ChunkedMessageSendResult result = sender.sendPlayerChunkedMessage(
                "player",
                new ClientUpdateRequestMessage(List.of())
        );

        assertEquals(ChunkedMessageSendResult.UNSUPPORTED, result);
        assertTrue(sender.packets.isEmpty());
    }

    @Test
    void generalChannelRejectsClientboundMessageTypesBeforeReassembly() {
        TestSender sender = new TestSender();
        C2SPacketHandler<String, String, String> handler = handler(sender);
        MessageChunkBuffer frame = MessageChunkBuffer.chunk(
                UUID.randomUUID(),
                ChunkedMessageRegistry.WAYPOINT_EDIT_RESULT.id(),
                0,
                1,
                false,
                1,
                0,
                new byte[]{0}
        );

        handler.onMessageChunk("player", frame);

        assertEquals(0, sender.receivedChunks);
        assertTrue(sender.packets.isEmpty());
    }

    @Test
    void updateRequestRejectsDuplicateDimensionsBeforeServerQueries() {
        TestSender sender = new TestSender();
        C2SPacketHandler<String, String, String> handler = handler(sender);
        ClientUpdateRequestMessage request = new ClientUpdateRequestMessage(List.of(
                new DimensionSyncIdentifier("minecraft:overworld", List.of()),
                new DimensionSyncIdentifier("minecraft:overworld", List.of())
        ));

        for (MessageChunkBuffer frame : frames(request)) {
            handler.onMessageChunk("player", frame);
        }

        assertEquals(1, sender.receivedChunks);
        assertTrue(sender.packets.isEmpty());
    }

    @Test
    void malformedGeneralTransferDoesNotEmitAutomaticSnapshot() {
        TestSender sender = new TestSender();
        C2SPacketHandler<String, String, String> handler = handler(sender);
        MessageChunkBuffer original = frames(new ClientUpdateRequestMessage(List.of())).get(0);
        MessageChunkBuffer badChecksum = MessageChunkBuffer.chunk(
                original.transferId(),
                original.messageTypeId(),
                original.sequence(),
                original.chunkCount(),
                original.compressed(),
                original.uncompressedSize(),
                original.checksum() + 1,
                original.data()
        );

        handler.onMessageChunk("player", badChecksum);

        assertEquals(1, sender.receivedChunks);
        assertTrue(sender.packets.isEmpty());
    }

    @Test
    void uploadTransportBindsExactPlayerRequestAndFirstTransfer() {
        TestSender sender = new TestSender();
        HandlerFixture fixture = handlerFixture(sender);
        UploadRequestBuffer request = beginUpload(fixture.coordinator(), "player");
        UUID firstTransfer = UUID.randomUUID();
        UUID secondTransfer = UUID.randomUUID();

        fixture.handler().onUploadChunk(
                "player",
                new UploadChunkBuffer(request.requestId(), partialUploadFrame(firstTransfer))
        );
        fixture.handler().onUploadChunk(
                "other",
                new UploadChunkBuffer(request.requestId(), partialUploadFrame(firstTransfer))
        );
        fixture.handler().onUploadChunk(
                "player",
                new UploadChunkBuffer(request.requestId(), partialUploadFrame(secondTransfer))
        );
        fixture.handler().onUploadTransportFailure(uploadFailure("player", secondTransfer));

        assertTrue(fixture.coordinator().acceptsUploadChunk("player", request.requestId()));

        fixture.handler().onUploadTransportFailure(uploadFailure("player", firstTransfer));
        assertFalse(fixture.coordinator().acceptsUploadChunk("player", request.requestId()));
    }

    @Test
    void staleUploadFailureCannotCancelReplacementSession() {
        TestSender sender = new TestSender();
        HandlerFixture fixture = handlerFixture(sender);
        UploadRequestBuffer oldRequest = beginUpload(fixture.coordinator(), "old-player");
        UUID oldTransfer = UUID.randomUUID();
        fixture.handler().onUploadChunk(
                "old-player",
                new UploadChunkBuffer(oldRequest.requestId(), partialUploadFrame(oldTransfer))
        );
        fixture.handler().onDisconnect("old-player");
        sender.capable = true;

        UploadRequestBuffer replacement = beginUpload(fixture.coordinator(), "new-player");
        UUID replacementTransfer = UUID.randomUUID();
        fixture.handler().onUploadChunk(
                "new-player",
                new UploadChunkBuffer(replacement.requestId(), partialUploadFrame(replacementTransfer))
        );
        fixture.handler().onUploadTransportFailure(uploadFailure("old-player", oldTransfer));

        assertTrue(fixture.coordinator().acceptsUploadChunk("new-player", replacement.requestId()));
        fixture.handler().onUploadTransportFailure(uploadFailure("new-player", replacementTransfer));
    }

    @Test
    void matchingMalformedTransferClearsTransportAndCoordinator() {
        TestSender sender = new TestSender();
        HandlerFixture fixture = handlerFixture(sender);
        UploadRequestBuffer request = beginUpload(fixture.coordinator(), "player");
        UUID transferId = UUID.randomUUID();
        fixture.handler().onUploadChunk(
                "player",
                new UploadChunkBuffer(request.requestId(), partialUploadFrame(transferId))
        );

        fixture.handler().onUploadChunk(
                "player",
                new UploadChunkBuffer(request.requestId(), conflictingUploadFrame(transferId))
        );

        assertFalse(fixture.coordinator().acceptsUploadChunk("player", request.requestId()));
        beginUpload(fixture.coordinator(), "other");
        fixture.handler().resetSession();
    }

    @Test
    void successfulUploadClearsMatchingTransportSession() {
        TestSender sender = new TestSender();
        HandlerFixture fixture = handlerFixture(sender);
        UploadRequestBuffer first = beginUpload(fixture.coordinator(), "first");
        sendCompletedUpload(fixture.handler(), "first", first);

        UploadRequestBuffer second = beginUpload(fixture.coordinator(), "second");
        sendCompletedUpload(fixture.handler(), "second", second);

        assertFalse(fixture.coordinator().acceptsUploadChunk("second", second.requestId()));
    }

    @Test
    void disconnectAndResetClearDedicatedUploadTransport() {
        TestSender sender = new TestSender();
        HandlerFixture fixture = handlerFixture(sender);
        UploadRequestBuffer disconnected = beginUpload(fixture.coordinator(), "first");
        fixture.handler().onUploadChunk(
                "first",
                new UploadChunkBuffer(disconnected.requestId(), partialUploadFrame(UUID.randomUUID()))
        );

        fixture.handler().onDisconnect("first");
        sender.capable = true;
        UploadRequestBuffer replacement = beginUpload(fixture.coordinator(), "second");
        fixture.handler().onUploadChunk(
                "second",
                new UploadChunkBuffer(replacement.requestId(), partialUploadFrame(UUID.randomUUID()))
        );

        fixture.handler().resetSession();
        UploadRequestBuffer afterReset = beginUpload(fixture.coordinator(), "second");
        sendCompletedUpload(fixture.handler(), "second", afterReset);
        assertFalse(fixture.coordinator().acceptsUploadChunk("second", afterReset.requestId()));
    }

    private C2SPacketHandler<String, String, String> handler(TestSender sender) {
        return this.handlerFixture(sender).handler();
    }

    private HandlerFixture handlerFixture(TestSender sender) {
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
        };
        UploadCoordinator<String> coordinator = uploadCoordinator(server);
        return new HandlerFixture(new C2SPacketHandler<>(
                sender,
                server,
                new TestPermissionManager(true),
                navigationService(),
                coordinator
        ), coordinator);
    }

    private static List<MessageChunkBuffer> frames(ChunkedMessage message) {
        return ChunkedMessageManager.prepare(message, false).frames();
    }

    private static UploadRequestBuffer beginUpload(
            UploadCoordinator<String> coordinator,
            String player
    ) {
        UploadCoordinator.BeginResult result = coordinator.begin(
                player,
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );
        assertEquals(UploadCoordinator.BeginStatus.STARTED, result.status());
        return result.request();
    }

    private static MessageChunkBuffer partialUploadFrame(UUID transferId) {
        return MessageChunkBuffer.chunk(
                transferId,
                ChunkedMessageRegistry.WAYPOINT_DATA.id(),
                0,
                2,
                false,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE + 1,
                0,
                new byte[ChunkedMessageManager.MAX_CHUNK_DATA_SIZE]
        );
    }

    private static MessageChunkBuffer conflictingUploadFrame(UUID transferId) {
        return MessageChunkBuffer.chunk(
                transferId,
                ChunkedMessageRegistry.WAYPOINT_DATA.id(),
                1,
                2,
                false,
                ChunkedMessageManager.MAX_CHUNK_DATA_SIZE + 1,
                1,
                new byte[]{0}
        );
    }

    private static ChunkedMessageManager.ReceiveFailure<String> uploadFailure(
            String player,
            UUID transferId
    ) {
        return new ChunkedMessageManager.ReceiveFailure<>(
                player,
                ChunkedMessageRegistry.WAYPOINT_DATA.id(),
                ChunkedMessageManager.FailureReason.MALFORMED,
                Optional.of(transferId)
        );
    }

    private static void sendCompletedUpload(
            C2SPacketHandler<String, String, String> handler,
            String player,
            UploadRequestBuffer request
    ) {
        WaypointData data = WaypointData.upload(
                request.requestId(),
                UploadStatus.XAERO_NOT_READY,
                List.of()
        );
        for (MessageChunkBuffer frame : frames(data)) {
            handler.onUploadChunk(player, new UploadChunkBuffer(request.requestId(), frame));
        }
    }

    private static WaypointEditRequestMessage request() {
        return new WaypointEditRequestMessage(
                17L,
                "minecraft:overworld",
                "list",
                "waypoint",
                1,
                WaypointPatch.empty()
        );
    }

    private static NavigationService<String> navigationService() {
        return new NavigationService<>(new NavigationPlatform<>() {
            @Override
            public UUID playerUuid(String player) {
                return UUID.nameUUIDFromBytes(player.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            @Override
            public void executePlayer(UUID playerUuid, Consumer<String> action) {
            }

            @Override
            public NavigationSnapshot snapshot(String player, NavigationTarget target) {
                return NavigationSnapshot.wrongDimension();
            }
        }, List.of());
    }

    private static UploadCoordinator<String> uploadCoordinator(WaypointServerCore server) {
        return new UploadCoordinator<>(
                server,
                (player, message) -> {
                },
                packet -> {
                },
                player -> true,
                player -> true,
                navigationService(),
                player -> UUID.nameUUIDFromBytes(player.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }

    private static final class TestPermissionManager extends PermissionManager<String, String, String> {
        private final boolean allowed;

        private TestPermissionManager(boolean allowed) {
            super(new TestPermissionKeys());
            this.allowed = allowed;
        }

        @Override
        public boolean hasPermission(String source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
            return this.allowed;
        }

        @Override
        public boolean checkPlayerPermission(String player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
            return this.allowed;
        }
    }

    private static final class TestPermissionKeys extends PermissionKeys<String> {
        @Override
        protected PermissionKey createAddPermissionKey() {
            return new PermissionKey("add");
        }

        @Override
        protected PermissionKey createEditPermissionKey() {
            return new PermissionKey("edit");
        }

        @Override
        protected PermissionKey createRemovePermissionKey() {
            return new PermissionKey("remove");
        }

        @Override
        protected PermissionKey createNavigatePermissionKey() {
            return new PermissionKey("navigate");
        }

        @Override
        protected PermissionKey createTpPermissionKey() {
            return new PermissionKey("tp");
        }

        @Override
        protected PermissionKey createReloadPermissionKey() {
            return new PermissionKey("reload");
        }

        @Override
        protected PermissionKey createUploadPermissionKey() {
            return new PermissionKey("upload");
        }

        @Override
        protected PermissionKey createUploadDeletePermissionKey() {
            return new PermissionKey("upload.delete");
        }
    }

    private record HandlerFixture(
            C2SPacketHandler<String, String, String> handler,
            UploadCoordinator<String> coordinator
    ) {
    }

    private static class TestSender implements PlatformMessageSender<String, String> {
        private final List<NetworkMessage> packets = new ArrayList<>();
        private boolean capable = true;
        private int receivedChunks;

        private WaypointEditResultMessage lastResult() {
            return (WaypointEditResultMessage) this.packets.get(this.packets.size() - 1);
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
            this.packets.add(message);
        }

        @Override
        public void sendPlayerPacket(String player, SinglePacketMessage message) {
            this.packets.add(message);
        }

        @Override
        public void broadcastPacket(SinglePacketMessage message) {
        }

        @Override
        public ChunkedMessageDelivery sendChunkedMessage(
                String source,
                ChunkedMessage message
        ) {
            this.packets.add(message);
            return ChunkedMessageDelivery.queued(CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.DELIVERED
            ));
        }

        @Override
        public ChunkedMessageSendResult sendPlayerChunkedMessage(String player, ChunkedMessage message) {
            return this.sendPlayerChunkedMessageTracked(player, message).admissionResult();
        }

        @Override
        public ChunkedMessageDelivery sendPlayerChunkedMessageTracked(
                String player,
                ChunkedMessage message
        ) {
            if (!this.capable) {
                return PlatformMessageSender.super.sendPlayerChunkedMessageTracked(
                        player,
                        message
                );
            }
            this.packets.add(message);
            return ChunkedMessageDelivery.queued(CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.DELIVERED
            ));
        }

        @Override
        public void setChunkedMessageCapable(String player, boolean capable) {
            this.capable = capable;
        }

        @Override
        public boolean canSendChunkedMessage(String player) {
            return this.capable;
        }

        @Override
        public void disconnectChunkedMessages(String player) {
            this.capable = false;
            PlatformMessageSender.super.disconnectChunkedMessages(player);
        }

        @Override
        public boolean receiveChunkedMessage(
                String player,
                MessageChunkBuffer packet,
                ChunkedMessageManager.ReceiveLimits limits,
                Consumer<ChunkedMessage> handler
        ) {
            this.receivedChunks++;
            return PlatformMessageSender.super.receiveChunkedMessage(
                    player,
                    packet,
                    limits,
                    handler
            );
        }

        @Override
        public Iterable<? extends String> getBroadcastPlayers(String source) {
            return List.of(source);
        }

        @Override
        public Component getSenderName(String source) {
            return Component.text(source);
        }
    }

    private static final class FailingBroadcastSender extends TestSender {
        private final List<String> attemptedRecipients = new ArrayList<>();
        private int prepareCalls;

        @Override
        public PreparedMessage prepareChunkedMessage(ChunkedMessage message) {
            this.prepareCalls++;
            return super.prepareChunkedMessage(message);
        }

        @Override
        public Iterable<? extends String> getBroadcastPlayers(String source) {
            return List.of("first", "second");
        }

        @Override
        public ChunkedMessageSendResult sendPlayerPreparedChunkedMessage(
                String player,
                PreparedMessage message
        ) {
            this.attemptedRecipients.add(player);
            return player.equals("first")
                    ? ChunkedMessageSendResult.PEER_BUSY
                    : ChunkedMessageSendResult.QUEUED;
        }
    }
}
