package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.codec.DimensionWaypointCodec;
import net.minecraft.util.Identifier;

//? if >= 1.20.5 {
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import io.netty.buffer.ByteBuf;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
*///?}

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.DIMENSION_WAYPOINT;

public record DimensionWaypointS2CPayload(DimensionWaypointBuffer dimensionWaypointBuffer) implements ModPayload {
    public static final Identifier DIM_WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, DIMENSION_WAYPOINT);
//? if >= 1.20.5 {
    public static final CustomPayload.Id<DimensionWaypointS2CPayload> ID = new CustomPayload.Id<>(DIM_WAYPOINT_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, DimensionWaypointS2CPayload> PACKET_CODEC = new PacketCodec<>() {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<DimensionWaypointS2CPayload> TYPE = PacketType.create(DIM_WAYPOINT_PAYLOAD_ID, DimensionWaypointS2CPayload::new);

    public DimensionWaypointS2CPayload(PacketByteBuf buf) {
        this(DimensionWaypointCodec.decode(buf));
    }

    @Override
    public void write(PacketByteBuf buf) {
        DimensionWaypointCodec.encode(buf, dimensionWaypointBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
*///?}
}
