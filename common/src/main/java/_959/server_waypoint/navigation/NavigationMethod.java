package _959.server_waypoint.navigation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A navigation method supported by the shared session model.
 */
public enum NavigationMethod {
    COMPASS("compass", NavigationMethodKind.ITEM),
    MAP("map", NavigationMethodKind.ITEM),
    BOSSBAR("bossbar", NavigationMethodKind.LIVE_DISPLAY),
    ACTIONBAR("actionbar", NavigationMethodKind.LIVE_DISPLAY),
    TEXT_DISPLAY("text_display", NavigationMethodKind.LIVE_DISPLAY);

    private static final Set<NavigationMethod> BUILT_IN_DEFAULT_METHODS =
            immutableSet(EnumSet.of(ACTIONBAR));
    private static final Set<NavigationMethod> DEFINED_METHODS =
            immutableSet(EnumSet.allOf(NavigationMethod.class));

    private final String id;
    private final NavigationMethodKind kind;

    NavigationMethod(String id, NavigationMethodKind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String id() {
        return this.id;
    }

    public NavigationMethodKind kind() {
        return this.kind;
    }

    public boolean ownsItem() {
        return this.kind == NavigationMethodKind.ITEM;
    }

    public boolean isLiveDisplay() {
        return this.kind == NavigationMethodKind.LIVE_DISPLAY;
    }

    /**
     * Returns the built-in fallback used when no configured defaults are available.
     * Use {@link NavigationService#defaultNavigationMethods()} for a service's defaults.
     */
    public static Set<NavigationMethod> builtInDefaultMethods() {
        return BUILT_IN_DEFAULT_METHODS;
    }

    /**
     * Returns every method defined by the shared navigation model.
     * Use {@link NavigationService#supportedNavigationMethods()} for methods available
     * on a specific platform.
     */
    public static Set<NavigationMethod> definedMethods() {
        return DEFINED_METHODS;
    }

    public static Optional<NavigationMethod> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalizedId = id.toLowerCase(Locale.ROOT);
        for (NavigationMethod method : values()) {
            if (method.id.equals(normalizedId)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    static Set<NavigationMethod> immutableSet(Set<NavigationMethod> methods) {
        EnumSet<NavigationMethod> copy = methods.isEmpty()
                ? EnumSet.noneOf(NavigationMethod.class)
                : EnumSet.copyOf(methods);
        return Collections.unmodifiableSet(copy);
    }
}
