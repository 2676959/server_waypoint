package _959.server_waypoint.navigation;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The result of a shared navigation operation or platform preflight.
 */
public record NavigationResult(
        Code code,
        @Nullable NavigationSession session,
        @Nullable NavigationMethod method,
        int requiredSlots,
        int availableSlots
) {
    public static final int SLOT_COUNT_NOT_APPLICABLE = -1;

    public NavigationResult {
        Objects.requireNonNull(code, "code");
        if (requiredSlots < SLOT_COUNT_NOT_APPLICABLE || availableSlots < SLOT_COUNT_NOT_APPLICABLE) {
            throw new IllegalArgumentException("Slot counts must be non-negative or -1 when not applicable");
        }
    }

    public boolean successful() {
        return this.code.successful();
    }

    public boolean hasSlotCounts() {
        return this.requiredSlots != SLOT_COUNT_NOT_APPLICABLE
                && this.availableSlots != SLOT_COUNT_NOT_APPLICABLE;
    }

    public NavigationResult withSession(@Nullable NavigationSession newSession) {
        return new NavigationResult(this.code, newSession, this.method, this.requiredSlots, this.availableSlots);
    }

    public NavigationResult withMethod(@Nullable NavigationMethod newMethod) {
        return new NavigationResult(this.code, this.session, newMethod, this.requiredSlots, this.availableSlots);
    }

    public static NavigationResult success() {
        return result(Code.SUCCESS, null, null);
    }

    public static NavigationResult failure(Code code) {
        if (code.successful()) {
            throw new IllegalArgumentException("Expected an unsuccessful result code");
        }
        return result(code, null, null);
    }

    public static NavigationResult insufficientInventory(int requiredSlots, int availableSlots) {
        if (requiredSlots < 0 || availableSlots < 0) {
            throw new IllegalArgumentException("Inventory slot counts must be non-negative");
        }
        return new NavigationResult(
                Code.INSUFFICIENT_INVENTORY,
                null,
                null,
                requiredSlots,
                availableSlots
        );
    }

    static NavigationResult result(
            Code code,
            @Nullable NavigationSession session,
            @Nullable NavigationMethod method
    ) {
        return new NavigationResult(
                code,
                session,
                method,
                SLOT_COUNT_NOT_APPLICABLE,
                SLOT_COUNT_NOT_APPLICABLE
        );
    }

    public enum Code {
        SUCCESS(true),
        NAVIGATION_STARTED(true),
        TARGET_CHANGED(true),
        SELECTION_REPLACED(true),
        METHOD_ENABLED(true),
        METHOD_ALREADY_ENABLED(true),
        METHOD_DISABLED(true),
        METHOD_ALREADY_DISABLED(true),
        NAVIGATION_DISABLED(true),
        STATUS(true),
        NO_ACTIVE_SESSION(false),
        INVALID_SELECTION(false),
        METHOD_UNAVAILABLE(false),
        TARGET_UNAVAILABLE(false),
        INSUFFICIENT_INVENTORY(false),
        HANDLER_FAILED(false),
        PLATFORM_REJECTED(false);

        private final boolean successful;

        Code(boolean successful) {
            this.successful = successful;
        }

        public boolean successful() {
            return this.successful;
        }
    }
}
