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
        TextDisplayTransformation textDisplayTransformation
) {
    public NavigationSession {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(target, "target");
        enabledMethods = NavigationMethod.immutableSet(Objects.requireNonNull(enabledMethods, "enabledMethods"));
        Objects.requireNonNull(textDisplayTransformation, "textDisplayTransformation");
    }

    public boolean isEnabled(NavigationMethod method) {
        return this.enabledMethods.contains(method);
    }

    public NavigationSession withTarget(NavigationTarget newTarget) {
        return new NavigationSession(
                this.playerUuid,
                newTarget,
                this.enabledMethods,
                this.textDisplayTransformation
        );
    }

    public NavigationSession withEnabledMethods(Set<NavigationMethod> methods) {
        return new NavigationSession(
                this.playerUuid,
                this.target,
                methods,
                this.textDisplayTransformation
        );
    }

    public NavigationSession withTextDisplayTransformation(
            TextDisplayTransformation transformation
    ) {
        return new NavigationSession(
                this.playerUuid,
                this.target,
                this.enabledMethods,
                transformation
        );
    }
}
