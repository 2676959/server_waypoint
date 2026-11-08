package _959.server_waypoint.navigation;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable navigation state for one player. Platform player objects are never
 * retained here.
 */
public record NavigationSession(
        UUID playerUuid,
        NavigationTarget target,
        Set<NavigationMethod> enabledMethods,
        TextDisplayTransformation textDisplayTransformation,
        float worldMarkerSize,
        float worldMarkerDistance
) {
    public static final float DEFAULT_WORLD_MARKER_SIZE = 1.0F;
    public static final float MIN_WORLD_MARKER_SIZE = 0.25F;
    public static final float MAX_WORLD_MARKER_SIZE = 4.0F;
    public static final float DEFAULT_WORLD_MARKER_DISTANCE = 12.0F;
    public static final float MIN_WORLD_MARKER_DISTANCE = 1.0F;
    public static final float MAX_WORLD_MARKER_DISTANCE = 32.0F;

    public NavigationSession {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(target, "target");
        enabledMethods = NavigationMethod.immutableSet(Objects.requireNonNull(enabledMethods, "enabledMethods"));
        Objects.requireNonNull(textDisplayTransformation, "textDisplayTransformation");
        validateWorldMarkerSize(worldMarkerSize);
        validateWorldMarkerDistance(worldMarkerDistance);
    }

    static void validateWorldMarkerSize(float size) {
        if (!Float.isFinite(size) || size < MIN_WORLD_MARKER_SIZE || size > MAX_WORLD_MARKER_SIZE) {
            throw new IllegalArgumentException("World marker size out of range: " + size);
        }
    }

    static void validateWorldMarkerDistance(float distance) {
        if (!Float.isFinite(distance) || distance < MIN_WORLD_MARKER_DISTANCE
                || distance > MAX_WORLD_MARKER_DISTANCE) {
            throw new IllegalArgumentException("World marker distance out of range: " + distance);
        }
    }

    public boolean isEnabled(NavigationMethod method) {
        return this.enabledMethods.contains(method);
    }

    public NavigationSession withTarget(NavigationTarget newTarget) {
        return new NavigationSession(
                this.playerUuid,
                newTarget,
                this.enabledMethods,
                this.textDisplayTransformation,
                this.worldMarkerSize,
                this.worldMarkerDistance
        );
    }

    public NavigationSession withEnabledMethods(Set<NavigationMethod> methods) {
        return new NavigationSession(
                this.playerUuid,
                this.target,
                methods,
                this.textDisplayTransformation,
                this.worldMarkerSize,
                this.worldMarkerDistance
        );
    }

    public NavigationSession withTextDisplayTransformation(
            TextDisplayTransformation transformation
    ) {
        return new NavigationSession(
                this.playerUuid,
                this.target,
                this.enabledMethods,
                transformation,
                this.worldMarkerSize,
                this.worldMarkerDistance
        );
    }

    NavigationSession withWorldMarkerSize(float size) {
        return new NavigationSession(
                this.playerUuid,
                this.target,
                this.enabledMethods,
                this.textDisplayTransformation,
                size,
                this.worldMarkerDistance
        );
    }

    NavigationSession withWorldMarkerDistance(float distance) {
        return new NavigationSession(
                this.playerUuid,
                this.target,
                this.enabledMethods,
                this.textDisplayTransformation,
                this.worldMarkerSize,
                distance
        );
    }
}
