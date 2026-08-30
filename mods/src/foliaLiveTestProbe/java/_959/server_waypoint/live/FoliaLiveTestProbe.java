package _959.server_waypoint.live;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.buffer.ClientHandshakeBuffer;
import _959.server_waypoint.core.network.buffer.MessageChunkBuffer;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.codec.ClientHandshakeCodec;
import _959.server_waypoint.core.network.codec.MessageChunkCodec;
import _959.server_waypoint.core.network.codec.ServerHandshakeCodec;
import _959.server_waypoint.core.network.codec.UploadChunkCodec;
import _959.server_waypoint.core.network.codec.UploadRequestCodec;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

public final class FoliaLiveTestProbe implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_folia_probe");
    private static final String NAMESPACE = "server_waypoint";
    private static final int MAX_FRAMES_PER_TICK = 8;
    private static final int MAX_BYTES_PER_TICK = 192 * 1_024;
    private static final int SATURATION_DOWNLOADS = 9;

    private final Queue<UploadChunkBuffer> pendingFrames = new ArrayDeque<>();
    private final ProbeMode mode = ProbeMode.parse(System.getProperty("serverWaypointProbe.mode", "valid"));
    private final int selectedFrame = positiveProperty("serverWaypointProbe.selectedFrame", 1);
    private final int waypointCount = boundedProperty("serverWaypointProbe.waypoints", 4_096, 1, 4_096);
    private boolean disconnectAfterQueue;
    private boolean commandIssued;
    private int sentFrames;

    @Override
    public void onInitializeClient() {
        registerPayloadTypes();
        ClientPlayNetworking.registerGlobalReceiver(
                ServerHandshakePayload.ID,
                (payload, context) -> context.client().execute(
                        () -> this.onServerHandshake(payload.handshake())
                )
        );
        ClientPlayNetworking.registerGlobalReceiver(
                UploadRequestPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> this.onUploadRequest(payload.request())
                )
        );
        ClientPlayNetworking.registerGlobalReceiver(
                MessageChunkPayload.ID,
                (payload, context) -> LOGGER.info(
                        "SW_PROBE event=received_download_frame transfer={} sequence={} chunks={}",
                        payload.chunk().transferId(),
                        payload.chunk().sequence(),
                        payload.chunk().chunkCount()
                )
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            this.pendingFrames.clear();
            this.sentFrames = 0;
            this.commandIssued = false;
            this.disconnectAfterQueue = false;
            ClientPlayNetworking.send(new ClientHandshakePayload(new ClientHandshakeBuffer()));
            LOGGER.info(
                    "SW_PROBE event=client_handshake version={} mode={} selectedFrame={} waypoints={}",
                    ProtocolVersion.PROTOCOL_VERSION,
                    this.mode.propertyValue,
                    this.selectedFrame,
                    this.waypointCount
            );
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.drainFrames(client));
    }

    private static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(
                ClientHandshakePayload.ID,
                ClientHandshakePayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
                UploadChunkPayload.ID,
                UploadChunkPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                ServerHandshakePayload.ID,
                ServerHandshakePayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                UploadRequestPayload.ID,
                UploadRequestPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                MessageChunkPayload.ID,
                MessageChunkPayload.CODEC
        );
    }

    private void onServerHandshake(ServerHandshakeBuffer handshake) {
        LOGGER.info(
                "SW_PROBE event=server_handshake version={} serverId={} compression={}",
                handshake.version(),
                handshake.serverId(),
                handshake.compressChunkedMessages()
        );
        if (handshake.version() != ProtocolVersion.PROTOCOL_VERSION) {
            LOGGER.error(
                    "SW_PROBE event=incompatible expected={} actual={}",
                    ProtocolVersion.PROTOCOL_VERSION,
                    handshake.version()
            );
            return;
        }
        if (this.commandIssued) {
            return;
        }
        this.commandIssued = true;
        if (this.mode == ProbeMode.SATURATE) {
            for (int index = 0; index < SATURATION_DOWNLOADS; index++) {
                Minecraft.getInstance().getConnection().sendCommand("wp download");
            }
            LOGGER.info(
                    "SW_PROBE event=saturation_commands command=wp_download count={}",
                    SATURATION_DOWNLOADS
            );
        } else {
            Minecraft.getInstance().getConnection().sendCommand("wp upload");
            LOGGER.info("SW_PROBE event=upload_command command=wp_upload");
        }
    }

    private void onUploadRequest(UploadRequestBuffer request) {
        LOGGER.info(
                "SW_PROBE event=upload_request request={} dimensions={} list={} waypoint={}",
                request.requestId(),
                request.dimensionNames(),
                request.listName(),
                request.waypointName()
        );
        if (this.mode == ProbeMode.SATURATE) {
            return;
        }
        List<MessageChunkBuffer> frames = new ArrayList<>(ChunkedMessageManager.prepare(
                createUpload(request),
                false
        ).frames());
        switch (this.mode) {
            case BAD_CHECKSUM -> frames.replaceAll(FoliaLiveTestProbe::corruptChecksum);
            case BAD_HEADER -> frames = List.of(corruptHeader(frames.get(0)));
            case VALID, PARTIAL, DISCONNECT -> {
            }
            case SATURATE -> throw new IllegalStateException("Handled above");
        }

        int queued = switch (this.mode) {
            case PARTIAL, DISCONNECT -> Math.min(this.selectedFrame, frames.size());
            default -> frames.size();
        };
        for (int index = 0; index < queued; index++) {
            this.pendingFrames.add(new UploadChunkBuffer(request.requestId(), frames.get(index)));
        }
        this.disconnectAfterQueue = this.mode == ProbeMode.DISCONNECT;
        LOGGER.info(
                "SW_PROBE event=frames_queued request={} transfer={} mode={} queued={} total={}",
                request.requestId(),
                frames.get(0).transferId(),
                this.mode.propertyValue,
                queued,
                frames.size()
        );
    }

    private WaypointData createUpload(UploadRequestBuffer request) {
        List<String> dimensionNames = request.dimensionNames().isEmpty()
                ? List.of("minecraft:overworld")
                : request.dimensionNames();
        List<DimensionWaypointData> dimensions = new ArrayList<>(dimensionNames.size());
        int remaining = this.waypointCount;
        for (int dimensionIndex = 0; dimensionIndex < dimensionNames.size(); dimensionIndex++) {
            int remainingDimensions = dimensionNames.size() - dimensionIndex;
            int count = remaining / remainingDimensions;
            remaining -= count;
            List<SimpleWaypoint> waypoints = new ArrayList<>(count);
            for (int waypointIndex = 0; waypointIndex < count; waypointIndex++) {
                int globalIndex = dimensionIndex * this.waypointCount + waypointIndex;
                String requestedName = request.waypointName();
                String name = requestedName == null
                        ? "probe-" + globalIndex + "-" + deterministicHex(globalIndex)
                        : requestedName;
                waypoints.add(new SimpleWaypoint(
                        name,
                        "P" + globalIndex % 10,
                        new WaypointPos(globalIndex * 2, 65 + globalIndex % 20, -globalIndex * 3),
                        (globalIndex * 1_103_515_245 + 12_345) & 0xFFFFFF,
                        globalIndex % 360 - 180,
                        globalIndex % 19 == 0
                ));
                if (requestedName != null) {
                    break;
                }
            }
            String listName = request.listName() == null ? "probe" : request.listName();
            dimensions.add(new DimensionWaypointData(
                    dimensionNames.get(dimensionIndex),
                    List.of(new WaypointList(listName, WaypointList.SERVER_N, waypoints))
            ));
        }
        return WaypointData.upload(request.requestId(), UploadStatus.SUCCESS, dimensions);
    }

    private void drainFrames(Minecraft client) {
        if (client.getConnection() == null || this.pendingFrames.isEmpty()) {
            return;
        }
        int frames = 0;
        int bytes = 0;
        while (!this.pendingFrames.isEmpty() && frames < MAX_FRAMES_PER_TICK) {
            UploadChunkBuffer next = this.pendingFrames.peek();
            int frameBytes = next.messageChunk().dataLength();
            if (frames > 0 && bytes + frameBytes > MAX_BYTES_PER_TICK) {
                break;
            }
            this.pendingFrames.remove();
            ClientPlayNetworking.send(new UploadChunkPayload(next));
            frames++;
            bytes += frameBytes;
            this.sentFrames++;
        }
        if (frames > 0) {
            LOGGER.info(
                    "SW_PROBE event=frames_sent batchFrames={} batchBytes={} sentTotal={} remaining={}",
                    frames,
                    bytes,
                    this.sentFrames,
                    this.pendingFrames.size()
            );
        }
        if (this.pendingFrames.isEmpty() && this.disconnectAfterQueue) {
            this.disconnectAfterQueue = false;
            LOGGER.info("SW_PROBE event=disconnect selectedFrame={}", this.selectedFrame);
            client.getConnection().getConnection().disconnect(
                    Component.literal("Server Waypoint live-test probe disconnect")
            );
        }
    }

    private static MessageChunkBuffer corruptChecksum(MessageChunkBuffer frame) {
        return MessageChunkBuffer.chunk(
                frame.transferId(),
                frame.messageTypeId(),
                frame.sequence(),
                frame.chunkCount(),
                frame.compressed(),
                frame.uncompressedSize(),
                frame.checksum() ^ 0x5A5A5A5A,
                frame.data()
        );
    }

    private static MessageChunkBuffer corruptHeader(MessageChunkBuffer frame) {
        return MessageChunkBuffer.chunk(
                frame.transferId(),
                frame.messageTypeId(),
                frame.chunkCount(),
                frame.chunkCount(),
                frame.compressed(),
                frame.uncompressedSize(),
                frame.checksum(),
                frame.data()
        );
    }

    private static String deterministicHex(int index) {
        try {
            byte[] first = MessageDigest.getInstance("SHA-256").digest(
                    ("probe-a-" + index).getBytes(StandardCharsets.UTF_8)
            );
            byte[] second = MessageDigest.getInstance("SHA-256").digest(
                    ("probe-b-" + index).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(first) + HexFormat.of().formatHex(second, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static int positiveProperty(String name, int defaultValue) {
        return boundedProperty(name, defaultValue, 1, Integer.MAX_VALUE);
    }

    private static int boundedProperty(String name, int defaultValue, int minimum, int maximum) {
        int value = Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum
            );
        }
        return value;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    private enum ProbeMode {
        VALID("valid"),
        PARTIAL("partial"),
        BAD_CHECKSUM("bad-checksum"),
        BAD_HEADER("bad-header"),
        SATURATE("saturate"),
        DISCONNECT("disconnect");

        private final String propertyValue;

        ProbeMode(String propertyValue) {
            this.propertyValue = propertyValue;
        }

        private static ProbeMode parse(String value) {
            String normalized = value.toLowerCase(Locale.ROOT);
            for (ProbeMode mode : values()) {
                if (mode.propertyValue.equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown probe mode: " + value);
        }
    }

    private record ClientHandshakePayload(ClientHandshakeBuffer handshake)
            implements CustomPacketPayload {
        private static final Type<ClientHandshakePayload> ID = new Type<>(id("client_handshake"));
        private static final StreamCodec<ByteBuf, ClientHandshakePayload> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buffer, ClientHandshakePayload payload) {
                ClientHandshakeCodec.encode(buffer, payload.handshake());
            }

            @Override
            public ClientHandshakePayload decode(ByteBuf buffer) {
                return new ClientHandshakePayload(ClientHandshakeCodec.decode(buffer));
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private record ServerHandshakePayload(ServerHandshakeBuffer handshake)
            implements CustomPacketPayload {
        private static final Type<ServerHandshakePayload> ID = new Type<>(id("server_handshake"));
        private static final StreamCodec<ByteBuf, ServerHandshakePayload> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buffer, ServerHandshakePayload payload) {
                ServerHandshakeCodec.encode(buffer, payload.handshake());
            }

            @Override
            public ServerHandshakePayload decode(ByteBuf buffer) {
                return new ServerHandshakePayload(ServerHandshakeCodec.decode(buffer));
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private record UploadRequestPayload(UploadRequestBuffer request)
            implements CustomPacketPayload {
        private static final Type<UploadRequestPayload> ID = new Type<>(id("upload_request"));
        private static final StreamCodec<ByteBuf, UploadRequestPayload> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buffer, UploadRequestPayload payload) {
                buffer.writeBytes(SinglePacketMessageEncoder.encode(payload.request()));
            }

            @Override
            public UploadRequestPayload decode(ByteBuf buffer) {
                UploadRequestBuffer request = UploadRequestCodec.decode(
                        buffer,
                        new DecodingContext(SinglePacketMessageEncoder.MAX_ENCODED_BYTES, 4_096)
                );
                if (buffer.isReadable()) {
                    throw new IllegalArgumentException("Upload request has trailing bytes");
                }
                return new UploadRequestPayload(request);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private record UploadChunkPayload(UploadChunkBuffer chunk)
            implements CustomPacketPayload {
        private static final Type<UploadChunkPayload> ID = new Type<>(id("upload_chunk"));
        private static final StreamCodec<ByteBuf, UploadChunkPayload> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buffer, UploadChunkPayload payload) {
                UploadChunkCodec.encode(buffer, payload.chunk());
            }

            @Override
            public UploadChunkPayload decode(ByteBuf buffer) {
                return new UploadChunkPayload(UploadChunkCodec.decode(buffer));
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    private record MessageChunkPayload(MessageChunkBuffer chunk)
            implements CustomPacketPayload {
        private static final Type<MessageChunkPayload> ID = new Type<>(id("message_chunk"));
        private static final StreamCodec<ByteBuf, MessageChunkPayload> CODEC = new StreamCodec<>() {
            @Override
            public void encode(ByteBuf buffer, MessageChunkPayload payload) {
                MessageChunkCodec.encode(buffer, payload.chunk());
            }

            @Override
            public MessageChunkPayload decode(ByteBuf buffer) {
                return new MessageChunkPayload(MessageChunkCodec.decode(buffer));
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
