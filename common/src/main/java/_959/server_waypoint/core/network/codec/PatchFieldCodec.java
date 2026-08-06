package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.edit.PatchField;
import io.netty.buffer.ByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class PatchFieldCodec {
    private PatchFieldCodec() {
    }

    public static <T> void encode(
            ByteBuf buf,
            PatchField<T> field,
            BiConsumer<ByteBuf, T> valueEncoder
    ) {
        buf.writeByte(field.operation().ordinal());
        if (field.isSet()) {
            valueEncoder.accept(buf, field.requiredValue());
        }
    }

    public static <T> PatchField<T> decode(
            ByteBuf buf,
            Function<ByteBuf, T> valueDecoder
    ) {
        int operationIndex = buf.readUnsignedByte();
        PatchField.Operation[] operations = PatchField.Operation.values();
        if (operationIndex >= operations.length) {
            throw new IllegalArgumentException("Unknown patch operation " + operationIndex);
        }
        return switch (operations[operationIndex]) {
            case UNCHANGED -> PatchField.unchanged();
            case SET -> PatchField.set(valueDecoder.apply(buf));
            case CLEAR -> PatchField.clear();
        };
    }
}
