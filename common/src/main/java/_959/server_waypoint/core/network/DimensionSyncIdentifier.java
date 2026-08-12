package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.codec.ListCodec;
import _959.server_waypoint.core.network.codec.UtfStringCodec;
import io.netty.buffer.ByteBuf;

import java.util.List;

public record DimensionSyncIdentifier(String dimensionName, List<WaypointListSyncIdentifier> listSyncIds) {
    public static void encode(ByteBuf buf, DimensionSyncIdentifier identifier, EncodingContext context) {
        UtfStringCodec.encode(buf, identifier.dimensionName, context);
        ListCodec.encode(buf, identifier.listSyncIds, WaypointListSyncIdentifier::encode, context);
    }

    public static DimensionSyncIdentifier decode(ByteBuf buf, DecodingContext context) {
        String dimensionName = UtfStringCodec.decode(buf, context);
        List<WaypointListSyncIdentifier> listSyncIds =
                ListCodec.decode(buf, WaypointListSyncIdentifier::decode, context);
        return new DimensionSyncIdentifier(dimensionName, listSyncIds);
    }
}
