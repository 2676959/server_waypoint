package _959.server_waypoint.util;

import java.util.regex.Pattern;

/** A canonical identifier independent of any loader's registry types. */
public record NamespacedId(String namespace, String path) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public NamespacedId {
        if (namespace == null || path == null
                || !NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid namespaced ID");
        }
    }

    public static NamespacedId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid namespaced ID");
        }
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Invalid namespaced ID");
        }
        return new NamespacedId(value.substring(0, colon), value.substring(colon + 1));
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
