package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.codec.WaypointListBufferCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_LIST;

public record WaypointListS2CPayload(WaypointListBuffer waypointListBuffer) implements ModPayload {
    public static final Identifier WAYPOINT_LIST_PAYLOAD_ID = Identifier.of(ModInfo.MOD_ID, WAYPOINT_LIST);
//? if >= 1.20.5 {

    public static final CustomPayload.Id<WaypointListS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_LIST_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, WaypointListS2CPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointListS2CPayload value) {
            WaypointListBufferCodec.encode(buf, value.waypointListBuffer());
        }

        @Override
        public WaypointListS2CPayload decode(ByteBuf buf) {
            return new WaypointListS2CPayload(WaypointListBufferCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointListS2CPayload> TYPE = PacketType.create(WAYPOINT_LIST_PAYLOAD_ID, WaypointListS2CPayload::new);

    public WaypointListS2CPayload(PacketByteBuf buf) {
        this(WaypointListBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        WaypointListBufferCodec.encode(buf, waypointListBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}