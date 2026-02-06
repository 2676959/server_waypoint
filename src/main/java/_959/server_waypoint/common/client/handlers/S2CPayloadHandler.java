package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.core.network.buffer.*;

//? if fabric && >= 1.20.5 {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.network.ClientPlayerEntity;
*///?} elif neoforge {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
 *///?}

/**
 * handle mod custom payloads for fabric and neoforge
 * */
public class S2CPayloadHandler {
    private static final BufferHandler xaeroMinimapPacketHandler = new HandlerForXaerosMinimap();

    public interface CustomPayloadHandler<B extends MessageBuffer, P extends ModPayload> {
        void bufferHandler(B buffer);
        B payloadToBuffer(P payload);
        default void handle(
                P payload,
                //? if fabric && >= 1.20.5 {
                ClientPlayNetworking.Context context
                //?} elif fabric {
                /*ClientPlayerEntity player, PacketSender responseSender
                // }
                *///?} elif neoforge {
                /*IPayloadContext context
                 *///?}
        ) {
            this.bufferHandler(this.payloadToBuffer(payload));
        }
    }

    public static class ServerHandshakeHandler implements CustomPayloadHandler<ServerHandshakeBuffer, ServerHandshakeS2CPayload> {
        @Override
        public ServerHandshakeBuffer payloadToBuffer(ServerHandshakeS2CPayload payload) {
            return payload.serverHandshakeBuffer();
        }

        @Override
        public void bufferHandler(ServerHandshakeBuffer buffer) {
            WaypointClientMod.getInstance().onServerHandshake(buffer);
        }
    }

    public static class UpdatesBundleHandler implements CustomPayloadHandler<UpdatesBundleBuffer, UpdatesBundleS2CPayload> {
        @Override
        public UpdatesBundleBuffer payloadToBuffer(UpdatesBundleS2CPayload payload) {
            return payload.updatesBundleBuffer();
        }

        @Override
        public void bufferHandler(UpdatesBundleBuffer buffer) {
            WaypointClientMod.getInstance().onUpdatesBundle(buffer);
        }
    }

    public static class WaypointListHandler implements CustomPayloadHandler<WaypointListBuffer, WaypointListS2CPayload> {
        @Override
        public WaypointListBuffer payloadToBuffer(WaypointListS2CPayload payload) {
            return payload.waypointListBuffer();
        }

        @Override
        public void bufferHandler(WaypointListBuffer buffer) {
            WaypointClientMod.getInstance().onWaypointList(buffer);
            if (WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap()) xaeroMinimapPacketHandler.onWaypointList(buffer);
        }
    }

    public static class DimensionWaypointHandler implements CustomPayloadHandler<DimensionWaypointBuffer, DimensionWaypointS2CPayload> {
        @Override
        public DimensionWaypointBuffer payloadToBuffer(DimensionWaypointS2CPayload payload) {
            return payload.dimensionWaypointBuffer();
        }

        @Override
        public void bufferHandler(DimensionWaypointBuffer buffer) {
            WaypointClientMod.getInstance().onDimensionWaypoint(buffer);
            if (WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap()) xaeroMinimapPacketHandler.onDimensionWaypoint(buffer);
        }
    }

    public static class WorldWaypointHandler implements CustomPayloadHandler<WorldWaypointBuffer, WorldWaypointS2CPayload> {
        @Override
        public WorldWaypointBuffer payloadToBuffer(WorldWaypointS2CPayload payload) {
            return payload.worldWaypointBuffer();
        }

        @Override
        public void bufferHandler(WorldWaypointBuffer buffer) {
            WaypointClientMod.getInstance().onWorldWaypoint(buffer);
            if (WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap()) xaeroMinimapPacketHandler.onWorldWaypoint(buffer);
        }
    }

    public static class WaypointModificationHandler implements CustomPayloadHandler<WaypointModificationBuffer, WaypointModificationS2CPayload> {
        @Override
        public WaypointModificationBuffer payloadToBuffer(WaypointModificationS2CPayload payload) {
            return payload.waypointModification();
        }

        @Override
        public void bufferHandler(WaypointModificationBuffer buffer) {
            WaypointClientMod.getInstance().onWaypointModification(buffer);
            if (WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap()) xaeroMinimapPacketHandler.onWaypointModification(buffer);
        }
    }
}
