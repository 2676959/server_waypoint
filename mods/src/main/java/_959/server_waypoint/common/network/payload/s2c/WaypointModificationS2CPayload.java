//~ resource_location_import
package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
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


import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_MODIFICATION;

public record WaypointModificationS2CPayload(WaypointModificationBuffer waypointModification) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    WAYPOINT_MODIFICATION_PAYLOAD_ID = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, WAYPOINT_MODIFICATION);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<WaypointModificationS2CPayload> ID = new CustomPacketPayload.Type<>(WAYPOINT_MODIFICATION_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, WaypointModificationS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointModificationS2CPayload value) {
            WaypointModificationBufferCodec.encode(buf, value.waypointModification());
        }

        @Override
        public WaypointModificationS2CPayload decode(ByteBuf buf) {
            return new WaypointModificationS2CPayload(WaypointModificationBufferCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WaypointModificationS2CPayload> ID = PacketType.create(WAYPOINT_MODIFICATION_PAYLOAD_ID, WaypointModificationS2CPayload::new);

    public WaypointModificationS2CPayload(FriendlyByteBuf buf) {
        this(WaypointModificationBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointModificationBufferCodec.encode(buf, waypointModification);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?} else if neoforge || forge {
    /*public WaypointModificationS2CPayload(FriendlyByteBuf buf) {
        this(WaypointModificationBufferCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        WaypointModificationBufferCodec.encode(buf, waypointModification);
    }

    //? if neoforge {
    /^@Override
    public net.minecraft.resources.Identifier id() {
        return WAYPOINT_MODIFICATION_PAYLOAD_ID;
    }
    ^///?}
*///?}
}
