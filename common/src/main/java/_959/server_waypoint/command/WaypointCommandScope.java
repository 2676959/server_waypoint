package _959.server_waypoint.command;

import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.util.StringCommandBuilder;

import java.util.Objects;

/** One resolved invocation. Private managers may only be used inside the owner borrowing callback. */
public record WaypointCommandScope(WaypointCollectionKey collection, WaypointFilesManagerCore files) {
    public WaypointCommandScope {
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(files, "files");
    }

    public String prefix() {
        return StringCommandBuilder.commandPrefix(collection.scope());
    }

    public boolean broadcasts() {
        return collection.scope() == WaypointScope.SHARED;
    }
}
