package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.WaypointPos;
import io.netty.buffer.ByteBuf;

public final class WaypointPatchCodec {
    private WaypointPatchCodec() {
    }

    public static void encode(ByteBuf buf, WaypointPatch patch, EncodingContext context) {
        PatchFieldCodec.encode(buf, patch.identifier(), UtfStringCodec::encode, context);
        PatchFieldCodec.encode(buf, patch.displayName(), UtfStringCodec::encode, context);
        PatchFieldCodec.encode(buf, patch.initials(), UtfStringCodec::encode, context);
        PatchFieldCodec.encode(buf, patch.position(), (target, position, ignored) -> {
            target.writeInt(position.x());
            target.writeInt(position.y());
            target.writeInt(position.z());
        }, context);
        PatchFieldCodec.encode(buf, patch.color(), (target, value, ignored) -> target.writeInt(value), context);
        PatchFieldCodec.encode(buf, patch.yaw(), (target, value, ignored) -> target.writeInt(value), context);
        PatchFieldCodec.encode(buf, patch.visibility(), (target, value, ignored) -> target.writeBoolean(value), context);
        PatchFieldCodec.encode(
                buf,
                patch.keywords(),
                (target, keywords, nestedContext) ->
                        ListCodec.encode(target, keywords, UtfStringCodec::encode, nestedContext),
                context
        );
        PatchFieldCodec.encode(buf, patch.description(), UtfStringCodec::encode, context);
    }

    public static WaypointPatch decode(ByteBuf buf, DecodingContext context) {
        return new WaypointPatch(
                PatchFieldCodec.decode(buf, UtfStringCodec::decode, context),
                PatchFieldCodec.decode(buf, UtfStringCodec::decode, context),
                PatchFieldCodec.decode(buf, UtfStringCodec::decode, context),
                PatchFieldCodec.decode(
                        buf,
                        (target, ignored) -> new WaypointPos(
                                target.readInt(),
                                target.readInt(),
                                target.readInt()
                        ),
                        context
                ),
                PatchFieldCodec.decode(buf, (target, ignored) -> target.readInt(), context),
                PatchFieldCodec.decode(buf, (target, ignored) -> target.readInt(), context),
                PatchFieldCodec.decode(buf, (target, ignored) -> target.readBoolean(), context),
                PatchFieldCodec.decode(
                        buf,
                        (target, nestedContext) ->
                                ListCodec.decode(target, UtfStringCodec::decode, nestedContext),
                        context
                ),
                PatchFieldCodec.decode(buf, UtfStringCodec::decode, context)
        );
    }
}
