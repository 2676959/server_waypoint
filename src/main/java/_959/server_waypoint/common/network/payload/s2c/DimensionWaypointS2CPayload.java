package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.waypoint.DimensionWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.ServerWaypointMod.MOD_ID;

public record DimensionWaypointS2CPayload(DimensionWaypoint dimensionWaypoint) implements CustomPayload {
    public static final Identifier DIM_WAYPOINT_PAYLOAD_ID = Identifier.of(MOD_ID, "dim_waypoint");
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
