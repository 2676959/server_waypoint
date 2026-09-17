//~ resource_location_import
package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.codec.UploadChunkCodec;
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
import static _959.server_waypoint.core.network.PayloadID.UPLOAD_CHUNK;

public record UploadChunkC2SPayload(
        UploadChunkBuffer uploadChunk,
        byte[] encodedMessage
) implements ModPayload {
    public UploadChunkC2SPayload {
        encodedMessage = java.util.Arrays.copyOf(encodedMessage, encodedMessage.length);
    }

    public UploadChunkC2SPayload(UploadChunkBuffer uploadChunk) {
        this(uploadChunk, SinglePacketMessageEncoder.encode(uploadChunk));
    }

    @Override
    public byte[] encodedMessage() {
        return java.util.Arrays.copyOf(this.encodedMessage, this.encodedMessage.length);
    }

    public static final
    //$ resource_location_type_swap
    Identifier
    UPLOAD_CHUNK_PAYLOAD_ID = modId(UPLOAD_CHUNK);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<UploadChunkC2SPayload> ID =
            new CustomPacketPayload.Type<>(UPLOAD_CHUNK_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, UploadChunkC2SPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, UploadChunkC2SPayload value) {
            buf.writeBytes(value.encodedMessage());
        }

        @Override
        public UploadChunkC2SPayload decode(ByteBuf buf) {
            return new UploadChunkC2SPayload(UploadChunkCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<UploadChunkC2SPayload> ID =
            PacketType.create(UPLOAD_CHUNK_PAYLOAD_ID, UploadChunkC2SPayload::new);

    public UploadChunkC2SPayload(FriendlyByteBuf buf) {
        this(UploadChunkCodec.decode(buf));
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
    /*public UploadChunkC2SPayload(FriendlyByteBuf buf) {
        this(UploadChunkCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBytes(encodedMessage);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return UPLOAD_CHUNK_PAYLOAD_ID;
    }
    ^///?}
*///?}
}
