package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.codec.WaypointListBufferCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_LIST;

public record WaypointListS2CPayload(WaypointListBuffer waypointListBuffer) implements CustomPayload {
    public static final Identifier WAYPOINT_LIST_PAYLOAD_ID = Identifier.of(GROUP_ID, WAYPOINT_LIST);
    public static final CustomPayload.Id<WaypointListS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_LIST_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, WaypointListS2CPayload> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public void encode(ByteBuf buf, WaypointListS2CPayload value) {
            WaypointListBufferCodec.encode(buf, value.waypointListBuffer());
        }

        @Override
        public WaypointListS2CPayload decode(ByteBuf buf) {
            return new WaypointListS2CPayload(WaypointListBufferCodec.decode(buf));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}