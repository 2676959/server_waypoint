package _959.server_waypoint.network.payload;

import _959.server_waypoint.ServerWaypoint;
import _959.server_waypoint.network.waypoint.WorldWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WorldWaypointS2CPayload(WorldWaypoint worldWaypoint) implements CustomPayload {
    public static final Identifier WORLD_WAYPOINT_PAYLOAD_ID = Identifier.of(ServerWaypoint.MOD_ID, "world_waypoint");
    public static final CustomPayload.Id<WorldWaypointS2CPayload> ID = new CustomPayload.Id<>(WORLD_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, WorldWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            WorldWaypoint.PACKET_CODEC, WorldWaypointS2CPayload::worldWaypoint,
            WorldWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
