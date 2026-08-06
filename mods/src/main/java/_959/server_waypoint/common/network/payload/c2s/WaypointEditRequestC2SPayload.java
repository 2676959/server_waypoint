//~ resource_location_import
package _959.server_waypoint.common.network.payload.c2s;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointEditRequestBuffer;
import _959.server_waypoint.core.network.codec.WaypointEditRequestBufferCodec;
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
import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_EDIT_REQUEST;

public record WaypointEditRequestC2SPayload(WaypointEditRequestBuffer request) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    PAYLOAD_ID = modId(WAYPOINT_EDIT_REQUEST);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<WaypointEditRequestC2SPayload> ID = new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, WaypointEditRequestC2SPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointEditRequestC2SPayload value) {
            WaypointEditRequestBufferCodec.encode(buf, value.request());
        }

        @Override
        public WaypointEditRequestC2SPayload decode(ByteBuf buf) {
            return new WaypointEditRequestC2SPayload(WaypointEditRequestBufferCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointEditRequestC2SPayload> ID = PacketType.create(PAYLOAD_ID, WaypointEditRequestC2SPayload::new);

    public WaypointEditRequestC2SPayload(FriendlyByteBuf buf) {
        this(WaypointEditRequestBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointEditRequestBufferCodec.encode(buf, request);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?} else if neoforge || forge {
    /*public WaypointEditRequestC2SPayload(FriendlyByteBuf buf) {
        this(WaypointEditRequestBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointEditRequestBufferCodec.encode(buf, request);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return PAYLOAD_ID;
    }
    ^///?}
*///?}
}
