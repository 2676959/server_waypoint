package _959.server_waypoint.common.network.waypoint;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.List;

public record WorldWaypoint(List<DimensionWaypoint> dimensionWaypoints) {
    public static final PacketCodec<RegistryByteBuf, WorldWaypoint> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, DimensionWaypoint.PACKET_CODEC), WorldWaypoint::dimensionWaypoints,
            WorldWaypoint::new
    );
}
