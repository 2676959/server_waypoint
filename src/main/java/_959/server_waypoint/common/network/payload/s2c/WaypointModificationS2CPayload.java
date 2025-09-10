package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.codec.WaypointModificationBufferCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import static _959.server_waypoint.common.server.WaypointServerMod.GROUP_ID;
import static _959.server_waypoint.core.network.PayloadID.WAYPOINT_MODIFICATION;

public record WaypointModificationS2CPayload(WaypointModificationBuffer waypointModification) implements CustomPayload {
    public static final Identifier WAYPOINT_MODIFICATION_PAYLOAD_ID = Identifier.of(GROUP_ID, WAYPOINT_MODIFICATION);
    public static final CustomPayload.Id<WaypointModificationS2CPayload> ID = new CustomPayload.Id<>(WAYPOINT_MODIFICATION_PAYLOAD_ID);
    public static final PacketCodec<ByteBuf, WaypointModificationS2CPayload> PACKET_CODEC = new PacketCodec<>() {
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}