package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.WaypointCodecs;
import _959.server_waypoint.common.network.waypoint.DimensionWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;

public record WorldWaypointS2CPayload(List<DimensionWaypoint> dimensionWaypoints, int edition) implements CustomPayload {
    public static final Identifier WORLD_WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, "world_waypoint");
    public static final CustomPayload.Id<WorldWaypointS2CPayload> ID = new CustomPayload.Id<>(WORLD_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, WorldWaypointS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, WaypointCodecs.DIMENSION_WAYPOINT), WorldWaypointS2CPayload::dimensionWaypoints,
            PacketCodecs.INTEGER, WorldWaypointS2CPayload::edition,
            WorldWaypointS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
