//~ resource_location_import
package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.codec.UploadRequestCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.Identifier;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?} else if neoforge || forge {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}

import static _959.server_waypoint.common.util.ResourceLocationHelper.modId;
import static _959.server_waypoint.core.network.PayloadID.UPLOAD_REQUEST;

public record UploadRequestS2CPayload(
        UploadRequestBuffer uploadRequestBuffer,
        byte[] encodedMessage
) implements ModPayload {
    public UploadRequestS2CPayload {
        encodedMessage = java.util.Arrays.copyOf(encodedMessage, encodedMessage.length);
    }

    public UploadRequestS2CPayload(UploadRequestBuffer uploadRequestBuffer) {
        this(uploadRequestBuffer, SinglePacketMessageEncoder.encode(uploadRequestBuffer));
    }

    @Override
    public byte[] encodedMessage() {
        return java.util.Arrays.copyOf(this.encodedMessage, this.encodedMessage.length);
    }

    private static UploadRequestBuffer decodeMessage(ByteBuf buf) {
        UploadRequestBuffer result = UploadRequestCodec.decode(
                buf,
                new DecodingContext(SinglePacketMessageEncoder.MAX_ENCODED_BYTES, 4_096)
        );
        if (buf.isReadable()) {
            throw new IllegalArgumentException("Upload-request payload has trailing bytes");
        }
        return result;
    }
    public static final
    //$ resource_location_type_swap
    Identifier
    UPLOAD_REQUEST_PAYLOAD_ID = modId(UPLOAD_REQUEST);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<UploadRequestS2CPayload> ID = new CustomPacketPayload.Type<>(UPLOAD_REQUEST_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, UploadRequestS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, UploadRequestS2CPayload value) {
            buf.writeBytes(value.encodedMessage());
        }

        @Override
        public UploadRequestS2CPayload decode(ByteBuf buf) {
            return new UploadRequestS2CPayload(decodeMessage(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<UploadRequestS2CPayload> ID = PacketType.create(UPLOAD_REQUEST_PAYLOAD_ID, UploadRequestS2CPayload::new);

    public UploadRequestS2CPayload(FriendlyByteBuf buf) {
        this(decodeMessage(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(encodedMessage);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?} else if neoforge || forge {
    /*public UploadRequestS2CPayload(FriendlyByteBuf buf) {
        this(decodeMessage(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(encodedMessage);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return UPLOAD_REQUEST_PAYLOAD_ID;
    }
    ^///?}
*///?}
}
