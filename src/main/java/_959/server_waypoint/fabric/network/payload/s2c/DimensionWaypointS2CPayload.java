package _959.server_waypoint.fabric.network.payload.s2c;

import _959.server_waypoint.fabric.ServerWaypointFabric;
import _959.server_waypoint.fabric.network.waypoint.DimensionWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DimensionWaypointS2CPayload(DimensionWaypoint dimensionWaypoint) implements CustomPayload {
    public static final Identifier DIM_WAYPOINT_PAYLOAD_ID = Identifier.of(ServerWaypointFabric.MOD_ID, "dim_waypoint");
    public static final CustomPayload.Id<DimensionWaypointS2CPayload> ID = new CustomPayload.Id<>(DIM_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, DimensionWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            DimensionWaypoint.PACKET_CODEC, DimensionWaypointS2CPayload::dimensionWaypoint,
            DimensionWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
