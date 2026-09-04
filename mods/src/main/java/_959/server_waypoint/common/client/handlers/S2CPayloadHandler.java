package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.network.upload.UploadStatus;

import java.util.List;

//? if fabric && >= 1.20.5 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.player.LocalPlayer;
*///?}

/**
 * handle mod custom payloads for fabric and neoforge
 * */
public class S2CPayloadHandler {
    public interface CustomPayloadHandler<M extends SinglePacketMessage, P extends ModPayload> {
        void messageHandler(M message);
        M payloadToMessage(P payload);
        default void handle(
                P payload,
                //? if fabric && >= 1.20.5 {
                ClientPlayNetworking.Context context
                //?} elif fabric {
                /*LocalPlayer player, PacketSender responseSender
                // }
                *///?} elif neoforge || forge {
                /*Object context
                *///?}
        ) {
            this.messageHandler(this.payloadToMessage(payload));
        }
    }

    public static class ServerHandshakeHandler implements CustomPayloadHandler<ServerHandshakeBuffer, ServerHandshakeS2CPayload> {
        @Override
        public ServerHandshakeBuffer payloadToMessage(ServerHandshakeS2CPayload payload) {
            return payload.serverHandshakeBuffer();
        }

        @Override
        public void messageHandler(ServerHandshakeBuffer buffer) {
            WaypointClientMod.getInstance().onServerHandshake(buffer);
        }
    }

    public static class MessageChunkHandler implements CustomPayloadHandler<MessageChunkBuffer, MessageChunkS2CPayload> {
        @Override
        public MessageChunkBuffer payloadToMessage(MessageChunkS2CPayload payload) {
            return payload.messageChunk();
        }

        @Override
        public void messageHandler(MessageChunkBuffer buffer) {
            WaypointClientMod.getInstance().onMessageChunk(buffer);
        }
    }

    public static class UploadRequestHandler implements CustomPayloadHandler<UploadRequestBuffer, UploadRequestS2CPayload> {
        @Override
        public UploadRequestBuffer payloadToMessage(UploadRequestS2CPayload payload) {
            return payload.uploadRequestBuffer();
        }

        @Override
        public void messageHandler(UploadRequestBuffer buffer) {
            MapModIntegrations.findUploadCollector(buffer.target())
                    .ifPresentOrElse(
                            integration -> integration.uploadToServer(buffer),
                            () -> sendMissingUploadTarget(buffer)
                    );
        }

        private static void sendMissingUploadTarget(UploadRequestBuffer buffer) {
            UploadStatus status = switch (buffer.target()) {
                case XAERO -> UploadStatus.XAERO_NOT_INSTALLED;
                case VOXELMAP -> UploadStatus.VOXELMAP_NOT_INSTALLED;
            };
            WaypointClientMod.getInstance().sendChunkedMessageToServer(WaypointData.upload(
                    buffer.requestId(), status, List.of()
            ));
        }
    }

}
