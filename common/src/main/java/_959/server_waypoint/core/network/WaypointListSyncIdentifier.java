package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.codec.PairCodec;
import _959.server_waypoint.core.network.codec.UtfStringCodec;
import _959.server_waypoint.util.Pair;
import io.netty.buffer.ByteBuf;

public class WaypointListSyncIdentifier extends Pair<String, Integer> {
    public WaypointListSyncIdentifier(String listName, Integer syncNum) {
        super(listName, syncNum);
    }

    public String listName() {
        return this.left;
    }

    public Integer syncNum() {
        return this.right;
    }

    public static void encode(ByteBuf buf, WaypointListSyncIdentifier listSyncId, EncodingContext context) {
        PairCodec.encode(
                buf,
                listSyncId,
                UtfStringCodec::encode,
                (target, value, ignored) -> target.writeInt(value),
                context
        );
    }

    public static WaypointListSyncIdentifier decode(ByteBuf buf, DecodingContext context) {
        return PairCodec.decode(
                buf,
                UtfStringCodec::decode,
                (target, ignored) -> target.readInt(),
                WaypointListSyncIdentifier::new,
                context
        );
    }
}
