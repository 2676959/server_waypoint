package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_MODIFICATION;

public record WaypointModificationS2CPayload(WaypointModificationBuffer waypointModification) implements ModPayload {
    public static final Identifier WAYPOINT_MODIFICATION_PAYLOAD_ID = Identifier.of(ModInfo.MOD_ID, WAYPOINT_MODIFICATION);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<WaypointModificationS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_MODIFICATION_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, WaypointModificationS2CPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointModificationS2CPayload value) {
            WaypointModificationBufferCodec.encode(buf, value.waypointModification());
        }

        @Override
        public WaypointModificationS2CPayload decode(ByteBuf buf) {
            return new WaypointModificationS2CPayload(WaypointModificationBufferCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointModificationS2CPayload> TYPE = PacketType.create(WAYPOINT_MODIFICATION_PAYLOAD_ID, WaypointModificationS2CPayload::new);

    public WaypointModificationS2CPayload(PacketByteBuf buf) {
        this(WaypointModificationBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        WaypointModificationBufferCodec.encode(buf, waypointModification);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}