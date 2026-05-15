//~ resource_location_import
package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.XaerosWorldIdBuffer;
import _959.server_waypoint.core.network.codec.XaerosWorldIdBufferCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.Identifier;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?}


public record XaerosWorldIdS2CPayload(XaerosWorldIdBuffer worldIdBuffer) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    XAEROS_WORLD_ID_PAYLOAD_ID = _959.server_waypoint.common.util.ResourceLocationHelper.id("xaerominimap", "main");
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<XaerosWorldIdS2CPayload> ID = new CustomPacketPayload.Type<>(XAEROS_WORLD_ID_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, XaerosWorldIdS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, XaerosWorldIdS2CPayload value) {
            XaerosWorldIdBufferCodec.encode(buf, value.worldIdBuffer());
        }

        @Override
        public XaerosWorldIdS2CPayload decode(ByteBuf buf) {
            return new XaerosWorldIdS2CPayload(XaerosWorldIdBufferCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<XaerosWorldIdS2CPayload> ID = PacketType.create(XAEROS_WORLD_ID_PAYLOAD_ID, XaerosWorldIdS2CPayload::new);

    public XaerosWorldIdS2CPayload(FriendlyByteBuf buf) {
        this(XaerosWorldIdBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        XaerosWorldIdBufferCodec.encode(buf, worldIdBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
