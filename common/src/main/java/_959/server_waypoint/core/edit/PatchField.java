package _959.server_waypoint.core.edit;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** A tri-state patch field: leave the value unchanged, set it, or clear it. */
public record PatchField<T>(Operation operation, @Nullable T value) {
    public PatchField {
        Objects.requireNonNull(operation, "operation");
        if (operation == Operation.SET) {
            Objects.requireNonNull(value, "value");
        } else if (value != null) {
            throw new IllegalArgumentException("Only SET fields may contain a value");
        }
    }

    public static <T> PatchField<T> unchanged() {
        return new PatchField<>(Operation.UNCHANGED, null);
    }

    public static <T> PatchField<T> set(T value) {
        return new PatchField<>(Operation.SET, value);
    }

    public static <T> PatchField<T> clear() {
        return new PatchField<>(Operation.CLEAR, null);
    }

    public boolean isUnchanged() {
        return this.operation == Operation.UNCHANGED;
    }

    public boolean isSet() {
        return this.operation == Operation.SET;
    }

    public boolean isClear() {
        return this.operation == Operation.CLEAR;
    }

    public T requiredValue() {
        if (!this.isSet()) {
            throw new IllegalStateException("Patch field is not set");
        }
        return Objects.requireNonNull(this.value);
    }

    public enum Operation {
        UNCHANGED,
        SET,
        CLEAR
    }
}
