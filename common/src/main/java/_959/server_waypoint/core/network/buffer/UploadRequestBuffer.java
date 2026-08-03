package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.UploadRequestCodec;
import _959.server_waypoint.core.network.upload.UploadConflictPolicy;
import _959.server_waypoint.core.network.upload.UploadScope;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static _959.server_waypoint.core.network.MessageChannelID.UPLOAD_REQUEST_CHANNEL;

/** Server-to-client request to export waypoints from Xaero's Minimap. */
public record UploadRequestBuffer(
        UUID requestId,
        UploadScope scope,
        UploadConflictPolicy conflictPolicy,
        boolean deleteMissing,
        List<String> dimensionNames,
        @Nullable String listName,
        @Nullable String waypointName
) implements MessageBuffer {
    public UploadRequestBuffer {
        dimensionNames = List.copyOf(dimensionNames);
        if (deleteMissing && conflictPolicy != UploadConflictPolicy.LOCAL) {
            throw new IllegalArgumentException("Only force-local uploads can delete missing waypoints");
        }
    }

    @Override
    public MessageChannelID getChannelId() {
        return UPLOAD_REQUEST_CHANNEL;
    }

    @Override
    public void encoderFunction(ByteBuf byteBuf) {
        UploadRequestCodec.encode(byteBuf, this);
    }

    @Override
    public MessageBuffer decoderFunction(ByteBuf byteBuf) {
        return UploadRequestCodec.decode(byteBuf);
    }
}
