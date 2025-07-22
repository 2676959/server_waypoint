package _959.server_waypoint.fabric.network.payload.s2c;

import _959.server_waypoint.fabric.ServerWaypointFabric;
import _959.server_waypoint.fabric.network.waypoint.WorldWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WorldWaypointS2CPayload(WorldWaypoint worldWaypoint, int edition) implements CustomPayload {
    public static final Identifier WORLD_WAYPOINT_PAYLOAD_ID = Identifier.of(ServerWaypointFabric.MOD_ID, "world_waypoint");
    public static final CustomPayload.Id<WorldWaypointS2CPayload> ID = new CustomPayload.Id<>(WORLD_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, WorldWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            WorldWaypoint.PACKET_CODEC, WorldWaypointS2CPayload::worldWaypoint,
            PacketCodecs.INTEGER, WorldWaypointS2CPayload::edition,
            WorldWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
