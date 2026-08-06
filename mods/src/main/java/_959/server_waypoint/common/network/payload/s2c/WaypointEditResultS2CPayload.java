//~ resource_location_import
package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointEditResultBuffer;
import _959.server_waypoint.core.network.codec.WaypointEditResultBufferCodec;
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
import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_EDIT_RESULT;

public record WaypointEditResultS2CPayload(WaypointEditResultBuffer result) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    PAYLOAD_ID = modId(WAYPOINT_EDIT_RESULT);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<WaypointEditResultS2CPayload> ID = new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, WaypointEditResultS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointEditResultS2CPayload value) {
            WaypointEditResultBufferCodec.encode(buf, value.result());
        }

        @Override
        public WaypointEditResultS2CPayload decode(ByteBuf buf) {
            return new WaypointEditResultS2CPayload(WaypointEditResultBufferCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointEditResultS2CPayload> ID = PacketType.create(PAYLOAD_ID, WaypointEditResultS2CPayload::new);

    public WaypointEditResultS2CPayload(FriendlyByteBuf buf) {
        this(WaypointEditResultBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointEditResultBufferCodec.encode(buf, result);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?} else if neoforge || forge {
    /*public WaypointEditResultS2CPayload(FriendlyByteBuf buf) {
        this(WaypointEditResultBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointEditResultBufferCodec.encode(buf, result);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return PAYLOAD_ID;
    }
    ^///?}
*///?}
}
