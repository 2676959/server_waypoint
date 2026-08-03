//~ resource_location_import
package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
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
*///?}

import static _959.server_waypoint.core.network.PayloadID.UPLOAD_CHUNK;

public record UploadChunkC2SPayload(UploadChunkBuffer uploadChunkBuffer) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    UPLOAD_CHUNK_PAYLOAD_ID = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, UPLOAD_CHUNK);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<UploadChunkC2SPayload> ID = new CustomPacketPayload.Type<>(UPLOAD_CHUNK_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, UploadChunkC2SPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, UploadChunkC2SPayload value) {
            UploadChunkCodec.encode(buf, value.uploadChunkBuffer());
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
    /*public static final PacketType<UploadChunkC2SPayload> ID = PacketType.create(UPLOAD_CHUNK_PAYLOAD_ID, UploadChunkC2SPayload::new);

    public UploadChunkC2SPayload(FriendlyByteBuf buf) {
        this(UploadChunkCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        UploadChunkCodec.encode(buf, uploadChunkBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
