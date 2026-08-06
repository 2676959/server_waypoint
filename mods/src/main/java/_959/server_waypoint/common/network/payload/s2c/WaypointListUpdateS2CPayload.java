//~ resource_location_import
package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointListUpdateBuffer;
import _959.server_waypoint.core.network.codec.WaypointListUpdateBufferCodec;
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
import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_LIST_UPDATE;

public record WaypointListUpdateS2CPayload(WaypointListUpdateBuffer update) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    PAYLOAD_ID = modId(WAYPOINT_LIST_UPDATE);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<WaypointListUpdateS2CPayload> ID = new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, WaypointListUpdateS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointListUpdateS2CPayload value) {
            WaypointListUpdateBufferCodec.encode(buf, value.update());
        }

        @Override
        public WaypointListUpdateS2CPayload decode(ByteBuf buf) {
            return new WaypointListUpdateS2CPayload(WaypointListUpdateBufferCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointListUpdateS2CPayload> ID = PacketType.create(PAYLOAD_ID, WaypointListUpdateS2CPayload::new);

    public WaypointListUpdateS2CPayload(FriendlyByteBuf buf) {
        this(WaypointListUpdateBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointListUpdateBufferCodec.encode(buf, update);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?} else if neoforge || forge {
    /*public WaypointListUpdateS2CPayload(FriendlyByteBuf buf) {
        this(WaypointListUpdateBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointListUpdateBufferCodec.encode(buf, update);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return PAYLOAD_ID;
    }
    ^///?}
*///?}
}
