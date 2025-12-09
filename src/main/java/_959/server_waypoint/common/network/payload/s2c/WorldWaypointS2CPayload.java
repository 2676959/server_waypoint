package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.codec.WorldWaypointBufferCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.WORLD_WAYPOINT;

public record WorldWaypointS2CPayload(WorldWaypointBuffer worldWaypointBuffer) implements ModPayload {
    public static final Identifier WORLD_WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, WORLD_WAYPOINT);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<WorldWaypointS2CPayload> ID = new CustomPayload.Id<>(WORLD_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, WorldWaypointS2CPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, WorldWaypointS2CPayload value) {
            WorldWaypointBufferCodec.encode(buf, value.worldWaypointBuffer());
        }

        @Override
        public WorldWaypointS2CPayload decode(ByteBuf buf) {
            return new WorldWaypointS2CPayload(WorldWaypointBufferCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WorldWaypointS2CPayload> TYPE = PacketType.create(WORLD_WAYPOINT_PAYLOAD_ID, WorldWaypointS2CPayload::new);

    public WorldWaypointS2CPayload(PacketByteBuf buf) {
        this(WorldWaypointBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        WorldWaypointBufferCodec.encode(buf, worldWaypointBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}
