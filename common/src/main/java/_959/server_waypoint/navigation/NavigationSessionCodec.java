package _959.server_waypoint.navigation;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/** Encodes the platform-neutral portion of a navigation session. */
public final class NavigationSessionCodec {
    public static final int CURRENT_VERSION = 1;
    private static final int MAX_ENCODED_LENGTH = 16_384;
    private static final int MAX_NAME_LENGTH = 1_024;
    private static final Gson GSON = new Gson();

    private NavigationSessionCodec() {
    }

    public static String encode(NavigationSession session) {
        List<String> methodIds = session.enabledMethods().stream()
                .map(NavigationMethod::id)
                .toList();
        Payload payload = new Payload(
                CURRENT_VERSION,
                session.target().dimensionName(),
                session.target().listName(),
                session.target().waypointName(),
                methodIds
        );
        return GSON.toJson(payload);
    }

    public static Optional<StoredNavigationSession> decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            return Optional.empty();
        }
        try {
            Payload payload = GSON.fromJson(encoded, Payload.class);
            if (payload == null
                    || payload.version() != CURRENT_VERSION
                    || !validName(payload.dimension())
                    || !validName(payload.list())
                    || !validName(payload.waypoint())
                    || payload.methods() == null) {
                return Optional.empty();
            }

            EnumSet<NavigationMethod> methods = EnumSet.noneOf(NavigationMethod.class);
            for (String methodId : payload.methods()) {
                Optional<NavigationMethod> method = NavigationMethod.fromId(methodId);
                if (method.isEmpty() || !methods.add(method.get())) {
                    return Optional.empty();
                }
            }
            return Optional.of(new StoredNavigationSession(
                    payload.dimension(),
                    payload.list(),
                    payload.waypoint(),
                    methods
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static boolean validName(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_NAME_LENGTH;
    }

    private record Payload(
            int version,
            String dimension,
            String list,
            String waypoint,
            List<String> methods
    ) {
        private Payload {
            methods = methods == null ? null : new ArrayList<>(methods);
        }
    }
}
