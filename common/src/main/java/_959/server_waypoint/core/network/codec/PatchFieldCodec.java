package _959.server_waypoint.core.network.codec;

import _959.server_waypoint.core.network.DecodingContext;
import _959.server_waypoint.core.network.EncodingContext;
import _959.server_waypoint.core.edit.PatchField;
import io.netty.buffer.ByteBuf;

public final class PatchFieldCodec {
    private PatchFieldCodec() {
    }

    public static <T> void encode(
            ByteBuf buf,
            PatchField<T> field,
            MessageCodec.Encoder<T> valueEncoder,
            EncodingContext context
    ) {
        buf.writeByte(field.operation().ordinal());
        if (field.isSet()) {
            valueEncoder.encode(buf, field.requiredValue(), context);
        }
    }

    public static <T> PatchField<T> decode(
            ByteBuf buf,
            MessageCodec.Decoder<T> valueDecoder,
            DecodingContext context
    ) {
        int operationIndex = buf.readUnsignedByte();
        PatchField.Operation[] operations = PatchField.Operation.values();
        if (operationIndex >= operations.length) {
            throw new IllegalArgumentException("Unknown patch operation " + operationIndex);
        }
        return switch (operations[operationIndex]) {
            case UNCHANGED -> PatchField.unchanged();
            case SET -> PatchField.set(valueDecoder.decode(buf, context));
            case CLEAR -> PatchField.clear();
        };
    }
}
