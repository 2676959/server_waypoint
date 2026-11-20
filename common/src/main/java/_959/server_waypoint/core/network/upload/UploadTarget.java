package _959.server_waypoint.core.network.upload;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** The client map mod that should export waypoints for an upload request. */
public enum UploadTarget {
    XAERO("xaero"),
    VOXELMAP("voxelmap");

    private static final Set<UploadTarget> SUPPORTED_TARGETS = Collections.unmodifiableSet(EnumSet.allOf(UploadTarget.class));
    private final String id;

    UploadTarget(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static Set<UploadTarget> supportedTargets() {
        return SUPPORTED_TARGETS;
    }

    public static Optional<UploadTarget> fromId(String id) {
        String normalizedId = id.toLowerCase(Locale.ROOT);
        for (UploadTarget target : SUPPORTED_TARGETS) {
            if (target.id.equals(normalizedId)) {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }
}
