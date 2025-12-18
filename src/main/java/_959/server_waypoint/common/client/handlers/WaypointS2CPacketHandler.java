package _959.server_waypoint.common.client.handlers;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.network.payload.s2c.*;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.buffer.*;
import net.minecraft.network.packet.CustomPayload;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
 *///?}

public class WaypointS2CPacketHandler {
    private static final BufferHandler xaeroMinimapPacketHandler = new BufferHandlerForXaerosMinimap();

    public interface CustomPayloadHandler<B extends MessageBuffer, P extends CustomPayload> {
        void bufferHandler(B buffer);
        B payloadToBuffer(P payload);
        default void handle(
                P payload,
                //? if fabric {
                ClientPlayNetworking.Context context
                //?} elif neoforge {
                /*IPayloadContext context
                 *///?}
        ) {
            this.bufferHandler(this.payloadToBuffer(payload));
        }
    }

    public static class ServerHandshakeHandler implements CustomPayloadHandler<ServerHandshakeBuffer, ServerHandshakeS2CPayload> {
        @Override
        public void bufferHandler(ServerHandshakeBuffer buffer) {
            WaypointClientMod.getInstance().onServerHandshake(buffer);
        }

        @Override
        public ServerHandshakeBuffer payloadToBuffer(ServerHandshakeS2CPayload payload) {
            return payload.serverHandshakeBuffer();
        }
    }

    public static class UpdatesBundleHandler implements CustomPayloadHandler<UpdatesBundleBuffer, UpdatesBundleS2CPayload> {
        @Override
        public void bufferHandler(UpdatesBundleBuffer buffer) {
            WaypointClientMod.getInstance().onUpdatesBundle(buffer);
        }

        @Override
        public UpdatesBundleBuffer payloadToBuffer(UpdatesBundleS2CPayload payload) {
            return payload.updatesBundleBuffer();
        }
    }

    public static class WaypointListHandler implements CustomPayloadHandler<WaypointListBuffer, WaypointListS2CPayload> {
        @Override
        public WaypointListBuffer payloadToBuffer(WaypointListS2CPayload payload) {
            return payload.waypointListBuffer();
        }

        @Override
        public void bufferHandler(WaypointListBuffer buffer) {
            xaeroMinimapPacketHandler.onWaypointList(buffer);
        }
    }

    public static class DimensionWaypointHandler implements CustomPayloadHandler<DimensionWaypointBuffer, DimensionWaypointS2CPayload> {
        @Override
        public DimensionWaypointBuffer payloadToBuffer(DimensionWaypointS2CPayload payload) {
            return payload.dimensionWaypointBuffer();
        }

        @Override
        public void bufferHandler(DimensionWaypointBuffer buffer) {
            xaeroMinimapPacketHandler.onDimensionWaypoint(buffer);
        }
    }

    public static class WorldWaypointHandler implements CustomPayloadHandler<WorldWaypointBuffer, WorldWaypointS2CPayload> {
        @Override
        public WorldWaypointBuffer payloadToBuffer(WorldWaypointS2CPayload payload) {
            return payload.worldWaypointBuffer();
        }

        @Override
        public void bufferHandler(WorldWaypointBuffer buffer) {
            if (WaypointServerMod.isDedicated) {
                WaypointClientMod.getInstance().onWorldWaypoint(buffer);
            }
            xaeroMinimapPacketHandler.onWorldWaypoint(buffer);
        }
    }

    public static class WaypointModificationHandler implements CustomPayloadHandler<WaypointModificationBuffer, WaypointModificationS2CPayload> {
        @Override
        public WaypointModificationBuffer payloadToBuffer(WaypointModificationS2CPayload payload) {
            return payload.waypointModification();
        }

        @Override
        public void bufferHandler(WaypointModificationBuffer buffer) {
            if (WaypointServerMod.isDedicated) {
                WaypointClientMod.getInstance().onWaypointModification(buffer);
            }
            xaeroMinimapPacketHandler.onWaypointModification(buffer);
        }
    }
}
