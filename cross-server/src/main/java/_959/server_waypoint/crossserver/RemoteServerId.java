package _959.server_waypoint.crossserver;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, explicitly configured identity; never derived from Config.serverId or a display name. */
public record RemoteServerId(String value) {
    private static final Pattern VALID_ID = Pattern.compile(CrossServerProtocol.SERVER_ID_PATTERN);

    public RemoteServerId {
        Objects.requireNonNull(value, "value");
        if (value.length() > CrossServerProtocol.MAX_SERVER_ID_LENGTH || !VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Server ID must match " + CrossServerProtocol.SERVER_ID_PATTERN);
        }
    }
}
