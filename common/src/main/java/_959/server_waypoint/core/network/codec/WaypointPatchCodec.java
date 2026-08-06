package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;

public final class WaypointPatchCodec {
    private WaypointPatchCodec() {
    }

    public static void encode(ByteBuf buf, WaypointPatch patch) {
        PatchFieldCodec.encode(buf, patch.identifier(), UtfStringCodec::encode);
        PatchFieldCodec.encode(buf, patch.displayName(), UtfStringCodec::encode);
        PatchFieldCodec.encode(buf, patch.initials(), UtfStringCodec::encode);
        PatchFieldCodec.encode(buf, patch.position(), (target, position) -> {
            target.writeInt(position.x());
            target.writeInt(position.y());
            target.writeInt(position.z());
        });
        PatchFieldCodec.encode(buf, patch.color(), ByteBuf::writeInt);
        PatchFieldCodec.encode(buf, patch.yaw(), ByteBuf::writeInt);
        PatchFieldCodec.encode(buf, patch.visibility(), ByteBuf::writeBoolean);
        PatchFieldCodec.encode(
                buf,
                patch.keywords(),
                (target, keywords) -> ListCodec.encode(target, keywords, UtfStringCodec::encode)
        );
        PatchFieldCodec.encode(buf, patch.description(), UtfStringCodec::encode);
    }

    public static WaypointPatch decode(ByteBuf buf) {
        return new WaypointPatch(
                PatchFieldCodec.decode(buf, UtfStringCodec::decode),
                PatchFieldCodec.decode(buf, UtfStringCodec::decode),
                PatchFieldCodec.decode(buf, UtfStringCodec::decode),
                PatchFieldCodec.decode(
                        buf,
                        target -> new WaypointPos(
                                target.readInt(),
                                target.readInt(),
                                target.readInt()
                        )
                ),
                PatchFieldCodec.decode(buf, ByteBuf::readInt),
                PatchFieldCodec.decode(buf, ByteBuf::readInt),
                PatchFieldCodec.decode(buf, ByteBuf::readBoolean),
                PatchFieldCodec.decode(
                        buf,
                        target -> ListCodec.decode(target, UtfStringCodec::decode)
                ),
                PatchFieldCodec.decode(buf, UtfStringCodec::decode)
        );
    }
}
