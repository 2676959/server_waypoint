package _959.server_waypoint.fabric.network.payload.s2c;

import _959.server_waypoint.fabric.ServerWaypointFabric;
import _959.server_waypoint.fabric.server.waypoint.WaypointList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public record WaypointListS2CPayload(RegistryKey<World> dimKey, WaypointList waypointList) implements CustomPayload {
    public static final Identifier WAYPOINT_LIST_PAYLOAD_ID = Identifier.of(ServerWaypointFabric.MOD_ID, "waypoint_list");
    public static final CustomPayload.Id<WaypointListS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_LIST_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, WaypointListS2CPayload> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.registryCodec(World.CODEC), WaypointListS2CPayload::dimKey,
            WaypointList.PACKET_CODEC, WaypointListS2CPayload::waypointList,
            WaypointListS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}