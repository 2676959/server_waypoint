//~ resource_location_import
package _959.server_waypoint.common.network.payload.s2c;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.network.payload.ModPayload;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.network.codec.DimensionWaypointsListCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.resources.Identifier;
//? if >= 1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else if fabric {
/*import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
*///?}

import static _959.server_waypoint.core.network.PayloadID.WORLD_WAYPOINT;

public record WorldWaypointS2CPayload(WorldWaypointBuffer worldWaypointBuffer) implements ModPayload {
    public static final
    //$ resource_location_type_swap
    Identifier
    WORLD_WAYPOINT_PAYLOAD_ID = _959.server_waypoint.common.util.ResourceLocationHelper.id(ModInfo.MOD_ID, WORLD_WAYPOINT);
//? if >= 1.20.5 {
    public static final CustomPacketPayload.Type<WorldWaypointS2CPayload> ID = new CustomPacketPayload.Type<>(WORLD_WAYPOINT_PAYLOAD_ID);
    public static final StreamCodec<ByteBuf, WorldWaypointS2CPayload> PACKET_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, WorldWaypointS2CPayload value) {
            DimensionWaypointsListCodec.encode(buf, value.worldWaypointBuffer());
        }

        @Override
        public WorldWaypointS2CPayload decode(ByteBuf buf) {
            return new WorldWaypointS2CPayload(DimensionWaypointsListCodec.decode(buf, WorldWaypointBuffer::new));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
//?} else if fabric {
    /*public static final PacketType<WorldWaypointS2CPayload> ID = PacketType.create(WORLD_WAYPOINT_PAYLOAD_ID, WorldWaypointS2CPayload::new);

    public WorldWaypointS2CPayload(FriendlyByteBuf buf) {
        this(DimensionWaypointsListCodec.decode(buf, WorldWaypointBuffer::new));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        DimensionWaypointsListCodec.encode(buf, worldWaypointBuffer);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
*///?}
}
