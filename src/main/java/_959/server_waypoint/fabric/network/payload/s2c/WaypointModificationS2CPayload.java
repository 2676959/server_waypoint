package _959.server_waypoint.fabric.network.payload.s2c;

import _959.server_waypoint.fabric.ServerWaypointFabric;
import _959.server_waypoint.fabric.server.waypoint.SimpleWaypoint;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public record WaypointModificationS2CPayload(
    RegistryKey<World> dimKey,
    String listName,
    SimpleWaypoint waypoint,
    ModificationType type,
    int edition
) implements CustomPayload {
    public static final Identifier WAYPOINT_MODIFICATION_PAYLOAD_ID = Identifier.of(ServerWaypointFabric.MOD_ID, "waypoint_modification");
    public static final CustomPayload.Id<WaypointModificationS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_MODIFICATION_PAYLOAD_ID);
    
    public static final PacketCodec<RegistryByteBuf, WaypointModificationS2CPayload> PACKET_CODEC = PacketCodec.tuple(
        PacketCodecs.registryCodec(World.CODEC), WaypointModificationS2CPayload::dimKey,
        PacketCodecs.STRING, WaypointModificationS2CPayload::listName,
        SimpleWaypoint.PACKET_CODEC, WaypointModificationS2CPayload::waypoint,
        PacketCodecs.STRING.xmap(ModificationType::valueOf, ModificationType::name), WaypointModificationS2CPayload::type,
        PacketCodecs.INTEGER, WaypointModificationS2CPayload::edition,
        WaypointModificationS2CPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum ModificationType {
        ADD,
        REMOVE,
        UPDATE;

        @Override
        public String toString() {
            return switch (this) {
                case ADD -> "added";
                case REMOVE -> "removed";
                case UPDATE -> "updated";
            };
        }
    }
} 