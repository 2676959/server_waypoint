package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.RemoteClientCatalogs;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointSorting;
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
            new WaypointPos(1, 2, 3), 0x123456, 0, true, List.of("keyword"), "Description", null);

    private CatalogReceiver.View view(RemoteServerId id, RemoteCatalogState state, long revision, String... names) {
        Map<String, RemoteWaypointSnapshot> values = new HashMap<>();
        for (String name : names) values.put(name, waypoint);
        var snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(revision), Map.of("custom:remote_world",
                Map.of("", new RemoteListSnapshot("Same list", new RemoteRevision(1), values))), Instant.EPOCH);
        return new CatalogReceiver.View(state == RemoteCatalogState.UNAVAILABLE ? null : snapshot, state, "Same server", null, "minecraft:compass");
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

    @Test void sidebarScopeFiltersBothListAndFlatViewsByExactServerAndDimension() {
        var views = Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, "target"),
                b, view(b, RemoteCatalogState.AVAILABLE, 1, "target"));
        for (boolean grouped : List.of(true, false)) {
            var rows = leaves(RemoteBrowserModel.scopedRoots(views, b, "custom:remote_world", "",
                    grouped, WaypointSorting.SortMode.NAME, false));
            assertEquals(1, rows.size());
            assertEquals(b, rows.get(0).path().server());
            assertTrue(RemoteBrowserModel.scopedRoots(views, b, "missing", "", grouped,
                    WaypointSorting.SortMode.NAME, false).isEmpty());
            assertTrue(RemoteBrowserModel.scopedRoots(views, null, null, "", grouped,
                    WaypointSorting.SortMode.NAME, false).isEmpty());
        }
    }

    @Test void duplicateLabelsRetainServerAndExactIdentityThroughFilteringAndReverseSort() {
        var views = Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, "z", "a"),
                b, view(b, RemoteCatalogState.AVAILABLE, 1, "z", "a"));
        var roots = RemoteBrowserModel.roots(views, "keyword", true, WaypointSorting.SortMode.NAME, true);
        var rows = leaves(roots);
        assertEquals(List.of("z", "a", "z", "a"), rows.stream().map(n -> n.path().waypoint()).toList());
        assertEquals(List.of(a, a, b, b), rows.stream().map(n -> n.path().server()).toList());
        assertEquals(4, rows.stream().map(n -> n.path().key()).distinct().count());
        assertTrue(roots.get(0).label().contains("[a]"));
        assertTrue(leaves(RemoteBrowserModel.roots(views, "no-match", true, WaypointSorting.SortMode.NAME, false)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> roots.clear());
    }

    @Test void flatModeSortsAcrossServersAndPreservesExactIdentities() {
        var views = Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, "z", "a"),
                b, view(b, RemoteCatalogState.STALE, 1, "b"));
        var flat = RemoteBrowserModel.roots(views, "", false, WaypointSorting.SortMode.NAME, false);
        assertEquals(List.of("a", "b", "z"), flat.stream().map(node -> node.path().waypoint()).toList());
        assertTrue(flat.stream().allMatch(node -> node.children().isEmpty()));
        assertEquals(new RemoteWaypointKey(b, "custom:remote_world", "", "b"), flat.get(1).path().key());
        assertEquals(RemoteCatalogState.STALE, flat.get(1).state());
        var reversed = RemoteBrowserModel.roots(views, "", false, WaypointSorting.SortMode.NAME, true);
        assertEquals(List.of("z", "b", "a"), reversed.stream().map(node -> node.path().waypoint()).toList());
        assertTrue(RemoteBrowserModel.roots(views, "missing", false, WaypointSorting.SortMode.NAME, false).isEmpty());
    }

    @Test void defaultOrderIgnoresReverseAndColorSortWorksInBothViews() {
        var views = Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, "z", "a"));
        for (boolean grouped : List.of(true, false)) {
            assertEquals(RemoteBrowserModel.roots(views, "", grouped, WaypointSorting.SortMode.DEFAULT, false),
                    RemoteBrowserModel.roots(views, "", grouped, WaypointSorting.SortMode.DEFAULT, true));
            var rows = leaves(RemoteBrowserModel.roots(views, "", grouped, WaypointSorting.SortMode.COLOR, true));
            assertEquals(List.of("z", "a"), rows.stream().map(node -> node.path().waypoint()).toList());
        }
    }

    @Test void quotedEmptyAndUnicodeArgumentsRoundTripWithoutUsingLabels() throws Exception {
        String name = "a \\\" 名称";
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.AVAILABLE, 1, name)));
        var key = new RemoteWaypointKey(a, "custom:remote_world", "", name);
        var confirmation = RemoteBrowserModel.prepare(cache, key);
        assertNotNull(confirmation);
        StringReader reader = new StringReader(confirmation.command());
        for (String value : List.of("wp", "remote", "tp", "a")) {
            assertEquals(value, reader.readString());
            reader.skipWhitespace();
        }
        // The emitted dimension is consumed by the native parser; list and waypoint remain quoted strings.
        //? if >=1.21.11 {
        assertEquals("custom:remote_world", net.minecraft.commands.arguments.IdentifierArgument.id().parse(reader).toString());
        //?} else {
        /*assertEquals("custom:remote_world", net.minecraft.commands.arguments.ResourceLocationArgument.id().parse(reader).toString());
        *///?}
        reader.skipWhitespace();
        for (String value : List.of("", name)) {
            assertEquals(value, reader.readString());
            reader.skipWhitespace();
        }
        assertFalse(reader.canRead());
        assertTrue(RemoteBrowserModel.isCurrent(cache, confirmation));
    }

    @Test void staleDataIsBrowsableButNotActionableAndUnavailableHasNoRows() {
        install(RemoteCatalogState.AVAILABLE, Map.of(a, view(a, RemoteCatalogState.STALE, 1, "name"),
                b, view(b, RemoteCatalogState.UNAVAILABLE, 1)));
        var roots = RemoteBrowserModel.roots(cache.snapshot(), "", true, WaypointSorting.SortMode.NAME, false);
        assertEquals(1, leaves(roots).size());
        assertEquals(RemoteCatalogState.STALE, leaves(roots).get(0).state());
        assertNull(RemoteBrowserModel.prepare(cache, new RemoteWaypointKey(a, "custom:remote_world", "", "name")));
        assertTrue(roots.get(1).children().isEmpty());
    }

    @Test void confirmationRejectsRevisionChangeRemovalRevocationAndSessionReplacement() {
        var key = new RemoteWaypointKey(a, "custom:remote_world", "", "name");
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
            assertEquals(name, leaves(RemoteBrowserModel.roots(cache.snapshot(), "", true, WaypointSorting.SortMode.NAME, false)).get(0).path().waypoint());
            assertNull(RemoteBrowserModel.prepare(cache, new RemoteWaypointKey(a, "custom:remote_world", "", name)));
        }
    }

    @Test void emptyAvailableServerRemainsDistinctFromUnavailableAndDenied() {
        var empty = new CatalogReceiver.View(new RemoteCatalogSnapshot(a, new RemoteRevision(1), Map.of(), Instant.EPOCH),
                RemoteCatalogState.AVAILABLE, "empty", null, "minecraft:compass");
        var roots = RemoteBrowserModel.roots(Map.of(a, empty, b, view(b, RemoteCatalogState.UNAUTHORIZED, 1, "hidden")), "", true, WaypointSorting.SortMode.NAME, false);
        assertEquals(1, roots.size());
        assertEquals(RemoteCatalogState.AVAILABLE, roots.get(0).state());
        assertTrue(roots.get(0).children().isEmpty());
    }

    @Test void emptyReasonReportsCatalogFailuresFirst() {
        var available = view(a, RemoteCatalogState.AVAILABLE, 1, "name");
        assertEquals(RemoteBrowserModel.EmptyReason.UNAUTHORIZED, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.UNAUTHORIZED, true, available, "query", "custom:remote_world"));
        assertEquals(RemoteBrowserModel.EmptyReason.NO_SERVERS, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.UNAVAILABLE, false, null, "query", null));
        assertEquals(RemoteBrowserModel.EmptyReason.NO_SERVERS, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, false, null, "", null));
    }

    @Test void emptyReasonReportsAnUnusableSelectedServerBeforeTheSearch() {
        var available = view(a, RemoteCatalogState.AVAILABLE, 1, "name");
        var retained = new CatalogReceiver.View(available.snapshot(), RemoteCatalogState.UNAVAILABLE,
                "retained", null, "minecraft:compass");
        var noSnapshot = new CatalogReceiver.View(null, RemoteCatalogState.STALE, "empty", null, "minecraft:compass");
        for (var server : Arrays.asList(null, retained, noSnapshot)) {
            assertEquals(RemoteBrowserModel.EmptyReason.SERVER_UNAVAILABLE, RemoteBrowserModel.emptyReason(
                    RemoteCatalogState.AVAILABLE, true, server, "query", "custom:remote_world"));
        }
    }

    @Test void emptyReasonSeparatesSearchMissesFromEmptyScopes() {
        var available = view(a, RemoteCatalogState.AVAILABLE, 1, "name");
        var stale = view(a, RemoteCatalogState.STALE, 1, "name");
        assertEquals(RemoteBrowserModel.EmptyReason.NO_MATCHES, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, true, available, "query", "custom:remote_world"));
        assertEquals(RemoteBrowserModel.EmptyReason.DIMENSION_EMPTY, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, true, available, " ", "custom:remote_world"));
        assertEquals(RemoteBrowserModel.EmptyReason.SERVER_EMPTY, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, true, stale, "", null));
    }

    @Test void emptyReasonsUseTheirTranslationKeys() {
        assertEquals("waypoint.remote.empty.unauthorized",
                RemoteBrowserModel.EmptyReason.UNAUTHORIZED.translationKey());
        assertEquals("waypoint.remote.no_servers",
                RemoteBrowserModel.EmptyReason.NO_SERVERS.translationKey());
        assertEquals("waypoint.remote.empty.server_unavailable",
                RemoteBrowserModel.EmptyReason.SERVER_UNAVAILABLE.translationKey());
        assertEquals("waypoint.empty.no_matches",
                RemoteBrowserModel.EmptyReason.NO_MATCHES.translationKey());
        assertEquals("waypoint.remote.empty.server",
                RemoteBrowserModel.EmptyReason.SERVER_EMPTY.translationKey());
        assertEquals("waypoint.remote.empty.dimension",
                RemoteBrowserModel.EmptyReason.DIMENSION_EMPTY.translationKey());
    }

    @Test void railDimensionsUseVanillaOrderAndHideUnavailableSnapshots() {
        var snapshot = new RemoteCatalogSnapshot(a, new RemoteRevision(1), Map.of(
                "custom:zeta", Map.of(), "minecraft:the_end", Map.of(),
                "minecraft:overworld", Map.of(), "custom:alpha", Map.of()), Instant.EPOCH);
        var stale = new CatalogReceiver.View(snapshot, RemoteCatalogState.STALE, "a", null, "minecraft:compass");
        var unavailable = new CatalogReceiver.View(snapshot, RemoteCatalogState.UNAVAILABLE, "a", null,
                "minecraft:compass");
        assertEquals(List.of("minecraft:overworld", "minecraft:the_end", "custom:alpha", "custom:zeta"),
                RemoteBrowserModel.dimensionNames(stale));
        assertTrue(RemoteBrowserModel.dimensionNames(unavailable).isEmpty());
        assertTrue(RemoteBrowserModel.dimensionNames(null).isEmpty());
    }
}
