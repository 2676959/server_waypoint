package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.core.network.buffer.DimensionWaypointBuffer;
import _959.server_waypoint.core.network.codec.DimensionWaypointCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.DIMENSION_WAYPOINT;

public record DimensionWaypointS2CPayload(DimensionWaypointBuffer dimensionWaypointBuffer) implements CustomPayload {
    public static final Identifier DIM_WAYPOINT_PAYLOAD_ID = Identifier.of(GROUP_ID, DIMENSION_WAYPOINT);
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
}
