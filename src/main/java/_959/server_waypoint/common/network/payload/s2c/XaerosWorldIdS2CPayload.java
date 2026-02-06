package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.XaerosWorldIdBuffer;
import _959.server_waypoint.core.network.codec.XaerosWorldIdBufferCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

public record XaerosWorldIdS2CPayload(XaerosWorldIdBuffer worldIdBuffer) implements ModPayload {
    public static final Identifier XAEROS_WORLD_ID_PAYLOAD_ID = Identifier.of("xaerominimap", "main");
//? if >= 1.20.5 {
    public static final CustomPayload.Id<XaerosWorldIdS2CPayload> ID = new CustomPayload.Id<>(XAEROS_WORLD_ID_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, XaerosWorldIdS2CPayload> PACKET_CODEC = new PacketCodec<>() {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<XaerosWorldIdS2CPayload> ID = PacketType.create(XAEROS_WORLD_ID_PAYLOAD_ID, XaerosWorldIdS2CPayload::new);

    public XaerosWorldIdS2CPayload(PacketByteBuf buf) {
        this(XaerosWorldIdBufferCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        XaerosWorldIdBufferCodec.encode(buf, worldIdBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
