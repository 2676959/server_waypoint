package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.UpdatesBundleBuffer;
import _959.server_waypoint.core.network.codec.UpdatesBundleCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.UPDATES_BUNDLE;

public record UpdatesBundleS2CPayload(UpdatesBundleBuffer updatesBundleBuffer) implements ModPayload {
    public static final ResourceLocation UPDATES_BUNDLE_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, UPDATES_BUNDLE);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<UpdatesBundleS2CPayload> ID = new CustomPacketPayload.Type<>(UPDATES_BUNDLE_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, UpdatesBundleS2CPayload> PACKET_CODEC = new StreamCodec<>() {
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<UpdatesBundleS2CPayload> ID = PacketType.create(UPDATES_BUNDLE_PAYLOAD_ID, UpdatesBundleS2CPayload::new);

    public UpdatesBundleS2CPayload(PacketByteBuf buf) {
        this(UpdatesBundleCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        UpdatesBundleCodec.encode(buf, updatesBundleBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
