package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import java.time.Instant;
import java.util.*;

/** Whole-list replacement delta rules, shared by backend publication and coordinator validation. */
public final class CatalogDelta {
    public static ApplicationMessage.CatalogDelta between(RemoteCatalogSnapshot before, RemoteCatalogSnapshot after) {
        Map<String, Map<String, RemoteListSnapshot>> changed = new HashMap<>();
        Map<String, Set<String>> removedLists = new HashMap<>();
        Set<String> removedDimensions = new HashSet<>(before.dimensions().keySet());
        removedDimensions.removeAll(after.dimensions().keySet());
        after.dimensions().forEach((dimension, lists) -> {
            Map<String, RemoteListSnapshot> old = before.dimensions().get(dimension);
            if (old == null) { changed.put(dimension, lists); return; }
            Map<String, RemoteListSnapshot> replacements = new HashMap<>();
            lists.forEach((name, list) -> { if (!list.equals(old.get(name))) replacements.put(name, list); });
            if (!replacements.isEmpty()) changed.put(dimension, replacements);
            Set<String> removed = new HashSet<>(old.keySet()); removed.removeAll(lists.keySet());
            if (!removed.isEmpty()) removedLists.put(dimension, removed);
        });
        return new ApplicationMessage.CatalogDelta(after.serverId(), before.catalogRevision(), after.catalogRevision(),
                changed, removedLists, removedDimensions);
    }
    public static RemoteCatalogSnapshot apply(RemoteCatalogSnapshot before, ApplicationMessage.CatalogDelta delta) {
        if (!before.serverId().equals(delta.serverId()) || !before.catalogRevision().equals(delta.baseRevision())) {
            throw new IllegalArgumentException("Catalog revision gap");
        }
        Map<String, Map<String, RemoteListSnapshot>> dimensions = new HashMap<>();
        before.dimensions().forEach((dimension, lists) -> dimensions.put(dimension, new HashMap<>(lists)));
        for (String dimension : delta.removedDimensions()) {
            if (dimensions.remove(dimension) == null) throw new IllegalArgumentException("Missing removed dimension");
        }
        delta.removedLists().forEach((dimension, names) -> {
            Map<String, RemoteListSnapshot> lists = dimensions.get(dimension);
            if (lists == null) throw new IllegalArgumentException("Missing dimension");
            for (String name : names) if (lists.remove(name) == null) throw new IllegalArgumentException("Missing removed list");
        });
        delta.replacements().forEach((dimension, replacements) -> {
            Map<String, RemoteListSnapshot> lists = dimensions.computeIfAbsent(dimension, ignored -> new HashMap<>());
            replacements.forEach((name, replacement) -> {
                RemoteListSnapshot old = lists.get(name);
                if (old != null && replacement.listRevision().compareTo(old.listRevision()) <= 0) {
                    throw new IllegalArgumentException("List revision did not advance");
                }
                if (replacement.listRevision().compareTo(delta.revision()) > 0) throw new IllegalArgumentException("Future list revision");
                lists.put(name, replacement);
            });
        });
        return new RemoteCatalogSnapshot(before.serverId(), delta.revision(), dimensions, Instant.now());
    }
    private CatalogDelta() { }
}
