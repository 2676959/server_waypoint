package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

/** Canonical context-aware codec for one logical network message. */
public final class MessageCodec<T> {
    private final Encoder<T> encoder;
    private final Decoder<T> decoder;

    private MessageCodec(Encoder<T> encoder, Decoder<T> decoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    public static <T> MessageCodec<T> of(Encoder<T> encoder, Decoder<T> decoder) {
        return new MessageCodec<>(encoder, decoder);
    }

    public void encode(ByteBuf buffer, T value, EncodingContext context) {
        this.encoder.encode(buffer, value, context);
    }

    public T decode(ByteBuf buffer, DecodingContext context) {
        return this.decoder.decode(buffer, context);
    }

    @FunctionalInterface
    public interface Encoder<T> {
        void encode(ByteBuf buffer, T value, EncodingContext context);
    }

    @FunctionalInterface
    public interface Decoder<T> {
        T decode(ByteBuf buffer, DecodingContext context);
    }
}
