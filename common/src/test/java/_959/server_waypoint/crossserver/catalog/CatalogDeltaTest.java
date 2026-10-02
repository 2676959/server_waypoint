package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogDeltaTest {
    private final RemoteServerId id = new RemoteServerId("backend");
    private RemoteListSnapshot list(String display, long revision) { return new RemoteListSnapshot(display, new RemoteRevision(revision), Map.of()); }
    private RemoteCatalogSnapshot snapshot(long revision, Map<String, Map<String, RemoteListSnapshot>> dimensions) {
        return new RemoteCatalogSnapshot(id, new RemoteRevision(revision), dimensions, Instant.EPOCH);
    }
    @Test void wholeListDeltasPreserveUnchangedRevisionsAndEmptyDimensions() {
        var keep = list("keep", 1);
        var before = snapshot(1, Map.of("dimension", Map.of("keep", keep, "change", list("old", 1)), "remove", Map.of()));
        var after = snapshot(2, Map.of("dimension", Map.of("keep", keep, "change", list("new", 2)), "empty", Map.of()));
        var delta = CatalogDelta.between(before, after);
        assertEquals(Set.of("change"), delta.replacements().get("dimension").keySet());
        var applied = CatalogDelta.apply(before, delta);
        assertEquals(after.dimensions(), applied.dimensions());
        assertEquals(1, applied.dimensions().get("dimension").get("keep").listRevision().value());
        var emptyLists = snapshot(3, Map.of("dimension", Map.of(), "empty", Map.of()));
        assertEquals(emptyLists.dimensions(), CatalogDelta.apply(after, CatalogDelta.between(after, emptyLists)).dimensions());
    }
    @Test void staleOrFutureListRevisionsCannotReplaceCurrentData() {
        var before = snapshot(2, Map.of("dimension", Map.of("list", list("current", 2))));
        for (long revision : new long[]{1, 2, 4}) {
            var delta = new ApplicationMessage.CatalogDelta(id, new RemoteRevision(2), new RemoteRevision(3),
                    Map.of("dimension", Map.of("list", list("invalid", revision))), Map.of(), Set.of());
            assertThrows(IllegalArgumentException.class, () -> CatalogDelta.apply(before, delta));
        }
    }
}
