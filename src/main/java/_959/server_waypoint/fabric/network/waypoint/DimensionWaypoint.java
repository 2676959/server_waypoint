package _959.server_waypoint.fabric.network.waypoint;

import _959.server_waypoint.fabric.server.waypoint.WaypointList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public record DimensionWaypoint(RegistryKey<World> dimKey, List<WaypointList> waypointLists) {
    public static final PacketCodec<RegistryByteBuf, DimensionWaypoint> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.registryCodec(World.CODEC), DimensionWaypoint::dimKey,
            PacketCodecs.collection(length -> new ArrayList<>(), WaypointList.PACKET_CODEC), DimensionWaypoint::waypointLists,
            DimensionWaypoint::new
    );
}
