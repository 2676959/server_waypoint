package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.WaypointPacketCodecs;
import _959.server_waypoint.core.waypoint.DimensionWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.waypoint.WaypointTypeID.DIMENSION_WAYPOINT;

public record DimensionWaypointS2CPayload(DimensionWaypoint dimensionWaypoint) implements CustomPayload {
    public static final Identifier DIM_WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, DIMENSION_WAYPOINT);
    public static final CustomPayload.Id<DimensionWaypointS2CPayload> ID = new CustomPayload.Id<>(DIM_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, DimensionWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            WaypointPacketCodecs.DIMENSION_WAYPOINT, DimensionWaypointS2CPayload::dimensionWaypoint,
            DimensionWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
