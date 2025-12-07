package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.network.buffer.UpdatesBundleBuffer;
import _959.server_waypoint.core.network.codec.UpdatesBundleCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.core.network.PayloadID.UPDATES_BUNDLE;

public record UpdatesBundleS2CPayload(UpdatesBundleBuffer updatesBundleBuffer) implements CustomPayload {
    public static final Identifier UPDATES_BUNDLE_PAYLOAD_ID = Identifier.of(ModInfo.MOD_ID, UPDATES_BUNDLE);
    public static final CustomPayload.Id<UpdatesBundleS2CPayload> ID = new CustomPayload.Id<>(UPDATES_BUNDLE_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, UpdatesBundleS2CPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, UpdatesBundleS2CPayload value) {
            UpdatesBundleCodec.encode(buf, value.updatesBundleBuffer());
        }

        @Override
        public UpdatesBundleS2CPayload decode(ByteBuf buf) {
            return new UpdatesBundleS2CPayload(UpdatesBundleCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
