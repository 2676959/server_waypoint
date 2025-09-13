package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.core.network.buffer.XaerosWorldIdBuffer;
import _959.server_waypoint.core.network.codec.XaerosWorldIdBufferCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record XaerosWorldIdS2CPayload(XaerosWorldIdBuffer worldIdBuffer) implements CustomPayload {
    public static final Identifier XAEROS_WORLD_ID_PAYLOAD_ID = Identifier.of("xaerominimap", "main");
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
}
