package _959.server_waypoint.core.network;

import _959.server_waypoint.core.network.codec.MessageCodec;

import java.util.Objects;

/** Stable wire identifier and canonical codec for one logical chunked message. */
public final class ChunkedMessageType<T extends ChunkedMessage> {
    private final int id;
    private final MessageCodec<T> codec;

    ChunkedMessageType(int id, MessageCodec<T> codec) {
        if (id < 0) {
            throw new IllegalArgumentException("Chunked-message type ID cannot be negative");
        }
        this.id = id;
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public int id() {
        return this.id;
    }

    public MessageCodec<T> codec() {
        return this.codec;
    }
}
