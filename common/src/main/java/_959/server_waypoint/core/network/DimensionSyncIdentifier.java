package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.codec.ListCodec;
import _959.server_waypoint.core.network.codec.UtfStringCodec;
import io.netty.buffer.ByteBuf;

import java.util.List;

public record DimensionSyncIdentifier(String dimensionName, List<WaypointListSyncIdentifier> listSyncIds) {
    public static void encode(ByteBuf buf, DimensionSyncIdentifier identifier) {
        UtfStringCodec.encode(buf, identifier.dimensionName);
        ListCodec.encode(buf, identifier.listSyncIds, WaypointListSyncIdentifier::encode);
    }

    public static DimensionSyncIdentifier decode(ByteBuf buf) {
        String dimensionName = UtfStringCodec.decode(buf);
        List<WaypointListSyncIdentifier> listSyncIds = ListCodec.decode(buf, WaypointListSyncIdentifier::decode);
        return new DimensionSyncIdentifier(dimensionName, listSyncIds);
    }
}