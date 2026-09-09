package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.RemoteClientCatalogs;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import com.mojang.brigadier.StringReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RemoteBrowserModelTest {
    private final AtomicLong clock = new AtomicLong();
    private final RemoteClientCatalogs cache = new RemoteClientCatalogs(clock::get);
    private final RemoteServerId a = new RemoteServerId("a"), b = new RemoteServerId("b");
    private final RemoteWaypointSnapshot waypoint = new RemoteWaypointSnapshot("Same label", "S",
            new WaypointPos(1, 2, 3), 0x123456, 0, true, List.of("keyword"), "Description");

    private CatalogReceiver.View view(RemoteServerId id, RemoteCatalogState state, long revision, String... names) {
        Map<String, RemoteWaypointSnapshot> values = new HashMap<>();
        for (String name : names) values.put(name, waypoint);
        var snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(revision), Map.of("dimension with spaces",
                Map.of("", new RemoteListSnapshot("Same list", new RemoteRevision(1), values))), Instant.EPOCH);
        return new CatalogReceiver.View(state == RemoteCatalogState.UNAVAILABLE ? null : snapshot, state, "Same server", null);
    }

    private void install(RemoteCatalogState state, Map<RemoteServerId, CatalogReceiver.View> servers) {
        clock.addAndGet(5_000_000_000L);
        assertTrue(cache.apply(new RemoteCatalogMessage(cache.poll().requestId(), state, servers)));
    }

    private List<RemoteBrowserModel.Node> leaves(List<RemoteBrowserModel.Node> nodes) {
        List<RemoteBrowserModel.Node> result = new ArrayList<>();
        for (var node : nodes) {
            if (node.path().key() != null) result.add(node);
            result.addAll(leaves(node.children()));
        }
        return result;
    }

    @Test void duplicateLabelsRetainServerAndExactIdentityThroughFilteringAndReverseSort() {
        var views = Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, "z", "a"),
                b, view(b, RemoteCatalogState.AVAILABLE, 1, "z", "a"));
        var roots = RemoteBrowserModel.roots(views, "keyword", true);
        var rows = leaves(roots);
        assertEquals(List.of("z", "a", "z", "a"), rows.stream().map(n -> n.path().waypoint()).toList());
        assertEquals(List.of(a, a, b, b), rows.stream().map(n -> n.path().server()).toList());
        assertEquals(4, rows.stream().map(n -> n.path().key()).distinct().count());
        assertTrue(roots.get(0).label().contains("[a]"));
        assertTrue(leaves(RemoteBrowserModel.roots(views, "no-match", false)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> roots.clear());
    }

    @Test void quotedEmptyAndUnicodeArgumentsRoundTripWithoutUsingLabels() throws Exception {
        String name = "a \\\" 名称";
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, name)));
        var key = new RemoteWaypointKey(a, "dimension with spaces", "", name);
        var confirmation = RemoteBrowserModel.prepare(cache, key);
        assertNotNull(confirmation);
        StringReader reader = new StringReader(confirmation.command());
        for (String value : List.of("wp", "remote", "tp", "a", "dimension with spaces", "", name)) {
            assertEquals(value, reader.readString());
            reader.skipWhitespace();
        }
        assertFalse(reader.canRead());
        assertTrue(RemoteBrowserModel.isCurrent(cache, confirmation));
    }

    @Test void staleDataIsBrowsableButNotActionableAndUnavailableHasNoRows() {
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.STALE, 1, "name"),
                b, view(b, RemoteCatalogState.UNAVAILABLE, 1)));
        var roots = RemoteBrowserModel.roots(cache.snapshot(), "", false);
        assertEquals(1, leaves(roots).size());
        assertEquals(RemoteCatalogState.STALE, leaves(roots).get(0).state());
        assertNull(RemoteBrowserModel.prepare(cache, new RemoteWaypointKey(a, "dimension with spaces", "", "name")));
        assertTrue(roots.get(1).children().isEmpty());
    }

    @Test void confirmationRejectsRevisionChangeRemovalRevocationAndSessionReplacement() {
        var key = new RemoteWaypointKey(a, "dimension with spaces", "", "name");
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, "name")));
        var confirmation = RemoteBrowserModel.prepare(cache, key);
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 2, "name")));
        assertFalse(RemoteBrowserModel.isCurrent(cache, confirmation));
        confirmation = RemoteBrowserModel.prepare(cache, key);
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 3)));
        assertFalse(RemoteBrowserModel.isCurrent(cache, confirmation));
        install(RemoteCatalogState.UNAUTHORIZED, Map.of());
        assertFalse(RemoteBrowserModel.isCurrent(cache, confirmation));
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 2, "name")));
        confirmation = RemoteBrowserModel.prepare(cache, key);
        cache.clear();
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 2, "name")));
        assertFalse(RemoteBrowserModel.isCurrent(cache, confirmation));
    }

    @Test void unsafeOrOversizeChatIdentitiesAreNeverTruncatedOrSent() {
        for (String name : List.of("x".repeat(257), "line\nbreak", "color§code", "del\u007f")) {
            install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, name)));
            assertEquals(name, leaves(RemoteBrowserModel.roots(cache.snapshot(), "", false)).get(0).path().waypoint());
            assertNull(RemoteBrowserModel.prepare(cache, new RemoteWaypointKey(a, "dimension with spaces", "", name)));
        }
    }

    @Test void emptyAvailableServerRemainsDistinctFromUnavailableAndDenied() {
        var empty = new CatalogReceiver.View(new RemoteCatalogSnapshot(a, new RemoteRevision(1), Map.of(), Instant.EPOCH),
                RemoteCatalogState.AVAILABLE, "empty", null);
        var roots = RemoteBrowserModel.roots(Map.of(a, empty, b, view(b, RemoteCatalogState.UNAUTHORIZED, 1, "hidden")), "", false);
        assertEquals(1, roots.size());
        assertEquals(RemoteCatalogState.AVAILABLE, roots.get(0).state());
        assertTrue(roots.get(0).children().isEmpty());
    }
}
