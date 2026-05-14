package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.codec.DimensionWaypointCodec;
import io.netty.buffer.ByteBuf;
//? if >= 1.21.11
/*import net.minecraft.resources.Identifier;*/
//? if < 1.21.11
import net.minecraft.resources.ResourceLocation;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.DIMENSION_WAYPOINT;

public record DimensionWaypointS2CPayload(DimensionWaypointBuffer dimensionWaypointBuffer) implements ModPayload {
    public static final /*? if < 1.21.11 {*/ResourceLocation/*?} else {*/ /*Identifier *//*?}*/ DIM_WAYPOINT_PAYLOAD_ID = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, DIMENSION_WAYPOINT);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<DimensionWaypointS2CPayload> ID = new CustomPacketPayload.Type<>(DIM_WAYPOINT_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, DimensionWaypointS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, DimensionWaypointS2CPayload value) {
            DimensionWaypointCodec.encode(buf, value.dimensionWaypointBuffer());
        }

        @Override
        public DimensionWaypointS2CPayload decode(ByteBuf buf) {
            return new DimensionWaypointS2CPayload(DimensionWaypointCodec.decode(buf));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<DimensionWaypointS2CPayload> ID = PacketType.create(DIM_WAYPOINT_PAYLOAD_ID, DimensionWaypointS2CPayload::new);

    public DimensionWaypointS2CPayload(FriendlyByteBuf buf) {
        this(DimensionWaypointCodec.decode(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        DimensionWaypointCodec.encode(buf, dimensionWaypointBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
