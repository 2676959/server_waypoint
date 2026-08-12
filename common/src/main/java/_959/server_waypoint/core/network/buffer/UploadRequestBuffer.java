package _959.server_waypoint.core.network.buffer;

import _959.server_waypoint.core.network.MessageChannelID;
import _959.server_waypoint.core.network.codec.UploadRequestCodec;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static _959.server_waypoint.core.network.MessageChannelID.UPLOAD_REQUEST_CHANNEL;

/** Server-to-client request to export waypoints from Xaero's Minimap. */
public record UploadRequestBuffer(
        UUID requestId,
        List<String> dimensionNames,
        @Nullable String listName,
        @Nullable String waypointName
) implements MessageBuffer {
    public UploadRequestBuffer {
        dimensionNames = List.copyOf(dimensionNames);
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
