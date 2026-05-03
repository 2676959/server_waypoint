package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.codec.WaypointListBufferCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_LIST;

public record WaypointListS2CPayload(WaypointListBuffer waypointListBuffer) implements ModPayload {
    public static final ResourceLocation WAYPOINT_LIST_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, WAYPOINT_LIST);
//? if >= 1.20.5 {

    public static final CustomPacketPayload.Type<WaypointListS2CPayload> ID = new CustomPacketPayload.Type<>(WAYPOINT_LIST_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, WaypointListS2CPayload> PACKET_CODEC = new StreamCodec<>() {
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointListS2CPayload> ID = PacketType.create(WAYPOINT_LIST_PAYLOAD_ID, WaypointListS2CPayload::new);

    public WaypointListS2CPayload(PacketByteBuf buf) {
        this(WaypointListBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        WaypointListBufferCodec.encode(buf, waypointListBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}