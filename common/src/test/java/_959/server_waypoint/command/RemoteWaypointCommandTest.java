package _959.server_waypoint.command;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.util.StringCommandBuilder;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RemoteWaypointCommandTest {
    private static final RemoteServerId A = new RemoteServerId("search"), B = new RemoteServerId("other");
    private static final String DIMENSION = "world \"quoted\"\\zone";
    private final AtomicLong time = new AtomicLong();
    private final CatalogIndex index = new CatalogIndex(new CatalogCacheLimits(4, 100000, 400000, 10), time::get);
    private final Object owner = new Object();
    private final List<Component> messages = new ArrayList<>(), errors = new ArrayList<>();
    private final CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
    private final RemoteCatalogQuery queries = new RemoteCatalogQuery();

    @BeforeEach void setup() throws Exception {
        var commands = new RemoteWaypointCommand<String>(() -> new RemoteCatalogStore(index), (source, text) -> messages.add(text),
                (source, text) -> errors.add(text), () -> 5);
        dispatcher.register(LiteralArgumentBuilder.<String>literal("wp").then(commands.build()));
        Map<String, RemoteWaypointSnapshot> waypoints = new HashMap<>();
        for (int i = 0; i < 12; i++) waypoints.put("base " + i, waypoint("Display " + i, i));
        publish(A, Map.of(DIMENSION, Map.of("search", new RemoteListSnapshot("Public display", new RemoteRevision(1), waypoints),
                "", new RemoteListSnapshot("Empty identity", new RemoteRevision(1), Map.of()))));
        publish(B, Map.of(DIMENSION, Map.of("search", new RemoteListSnapshot("Other display", new RemoteRevision(1),
                Map.of("base 0", waypoint("Other waypoint", 0))))));
    }
    private static RemoteWaypointSnapshot waypoint(String display, int position) {
        return new RemoteWaypointSnapshot(display, "B", new WaypointPos(position, 64, 0), position % 2 == 0 ? 0xFF0000 : 0x00FF00,
                0, false, List.of("village"), "description");
    }
    private void publish(RemoteServerId id, Map<String, Map<String, RemoteListSnapshot>> data) throws Exception {
        index.connected(id, owner, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT);
        RemoteCatalogSnapshot snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(1), data, Instant.EPOCH);
        UUID request = UUID.randomUUID(); byte[] bytes = new ApplicationCodec(ProtocolLimits.DEFAULT).encodeCatalog(snapshot);
        index.receive(id, owner, received(request, new ApplicationMessage.CatalogMetadata(id, "Server " + id.value(), new RemoteRevision(1), CatalogExportPolicy.PUBLIC), null));
        index.receive(id, owner, received(request, new ApplicationMessage.CatalogSnapshot(id, new RemoteRevision(1), UUID.randomUUID(), 0,
                bytes.length, new ApplicationMessage.Bytes(bytes)), snapshot));
    }
    private static TcpChannel.Received received(UUID request, ApplicationMessage message, RemoteCatalogSnapshot catalog) {
        return new TcpChannel.Received(new ApplicationEnvelope(0, request, message), catalog);
    }
    private static String quote(String value) { return StringCommandBuilder.escapeListName(value); }
    private String target() { return "wp remote list " + quote(A.value()) + " " + quote(DIMENSION) + " \"search\""; }
    private ListOptions options(String filter, WaypointSorting.SortMode mode, boolean reversed, boolean grouped) {
        return new ListOptions(filter, mode, reversed, 1, 100, grouped);
    }
    private Component last() { return messages.get(messages.size() - 1); }
    private List<String> suggestions(String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, "console")).join().getList().stream().map(value -> value.getText()).toList();
    }
    private static List<Component> components(Component root) {
        List<Component> all = new ArrayList<>(); all.add(root);
        root.children().forEach(child -> all.addAll(components(child))); return all;
    }
    private static String text(Component root) {
        return components(root).stream().filter(TextComponent.class::isInstance).map(TextComponent.class::cast).map(TextComponent::content).reduce("", String::concat);
    }
    private static List<String> keys(Component root) {
        return components(root).stream().filter(TranslatableComponent.class::isInstance).map(TranslatableComponent.class::cast).map(TranslatableComponent::key).toList();
    }
    private static List<String> clicks(Component root) {
        return components(root).stream().map(Component::clickEvent).filter(Objects::nonNull).map(ClickEvent::value).toList();
    }
    @Test void suggestionsUseOnlyLocalCacheAndRoundTripReservedQuotedAndEmptyIdentities() throws Exception {
        assertTrue(suggestions("wp remote list ").contains("\"search\""));
        assertTrue(suggestions("wp remote list \"search\" ").contains(quote(DIMENSION)));
        assertTrue(suggestions("wp remote list \"search\" " + quote(DIMENSION) + " ").containsAll(List.of("\"search\"", "\"\"")));
        assertTrue(suggestions("wp remote list \"se").contains("\"search\""));
        assertEquals(1, dispatcher.execute(target(), "console"));
        assertEquals(1, dispatcher.execute("wp remote list \"search\" " + quote(DIMENSION) + " \"\"", "console"));
        assertTrue(keys(last()).contains("waypoint.remote.empty"));
    }
    @Test void combinedOptionsAndGeneratedPageLinksRetainExactScope() throws Exception {
        assertEquals(1, dispatcher.execute(target() + " search village sort name order descending page 1 limit 2 view flat", "console"));
        String next = clicks(last()).stream().filter(value -> value.contains("page 2")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search village sort name order descending page 2 limit 2 view flat", next);
        assertEquals(1, dispatcher.execute(next.substring(1), "console"));
        assertTrue(text(last()).contains("search")); assertFalse(text(last()).contains("Other waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.startsWith("/wp remote list ")));
        for (String suffix : List.of("search village", "sort color order ascending search village", "page 1 limit 3 view tree search village",
                "sort name page 1 limit 2 view flat", "limit 3 search village", "view flat search village")) {
            assertEquals(1, dispatcher.execute(target() + " " + suffix, "console"));
        }
    }
    @Test void staleAndUnavailableRemainDifferentFromSuccessfulEmptyCatalogs() throws Exception {
        index.disconnected(A, owner);
        dispatcher.execute(target(), "console"); assertTrue(keys(last()).contains("waypoint.remote.state.stale"));
        assertTrue(text(last()).contains("Display"));
        time.set(11_000_000); index.maintain();
        dispatcher.execute(target(), "console");
        assertTrue(keys(last()).contains("waypoint.remote.state.unavailable"));
        assertFalse(keys(last()).contains("waypoint.remote.empty")); assertFalse(text(last()).contains("Display"));
        assertTrue(suggestions("wp remote list \"search\" ").stream().noneMatch(value -> value.equals(quote(DIMENSION))));
        publish(new RemoteServerId("empty"), Map.of());
        dispatcher.execute("wp remote list empty", "console");
        assertTrue(keys(last()).containsAll(List.of("waypoint.remote.state.available", "waypoint.remote.empty")));
    }
    @Test void missingScopesAndDistanceHaveLocalizedErrorsWithoutUsingLocalCoordinates() throws Exception {
        assertEquals(0, dispatcher.execute("wp remote list missing", "console"));
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.unknown_server"));
        assertEquals(0, dispatcher.execute("wp remote list other missing", "console"));
        assertEquals(0, dispatcher.execute("wp remote list other " + quote(DIMENSION) + " missing", "console"));
        assertEquals(0, dispatcher.execute(target() + " sort distance", "console"));
        assertTrue(keys(errors.get(3)).contains("waypoint.remote.distance_unavailable"));
        assertEquals(0, dispatcher.execute(target() + " page 2147483647", "console"));
        assertTrue(keys(errors.get(4)).contains("waypoint.list.page.invalid"));
    }
    @Test void nameColorAndFuzzyFilteringReuseLocalSemanticsWithoutIdentityCollisions() {
        var captured = new RemoteCatalogStore(index).snapshot();
        var scope = new RemoteCatalogQuery.Scope(null, null, null);
        var result = queries.query(captured, scope, options("vilage", WaypointSorting.SortMode.NAME, false, false));
        assertEquals(13, result.totalRows());
        assertEquals(2, result.rows().stream().filter(row -> row.waypointName().equals("base 0")).count());
        assertEquals(Set.of(A, B), new HashSet<>(result.rows().stream().map(RemoteCatalogQuery.Row::server).toList()));
        var descending = queries.query(captured, scope, options("village", WaypointSorting.SortMode.NAME, true, false));
        List<RemoteCatalogQuery.Row> reversed = new ArrayList<>(result.rows()); Collections.reverse(reversed);
        assertEquals(reversed, descending.rows());
        var colored = queries.query(captured, scope, options("village", WaypointSorting.SortMode.COLOR, false, false));
        List<RemoteCatalogQuery.Row> expected = new ArrayList<>(result.rows());
        _959.server_waypoint.util.ColorUtils.sortWaypointColors(expected, row -> row.waypoint().rgb(),
                WaypointSorting.<RemoteCatalogQuery.Row>byName(RemoteCatalogQuery.Row::waypointName)
                        .thenComparing(row -> row.server().value()).thenComparing(RemoteCatalogQuery.Row::dimension).thenComparing(RemoteCatalogQuery.Row::list));
        assertEquals(expected, colored.rows());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> captured.clear());
    }
    @Test void serverPagesAndHelpHaveOnlyReadOnlyActionsWithNeutralParents() throws Exception {
        dispatcher.execute("wp remote servers page 1 limit 1", "console");
        assertTrue(clicks(last()).contains("/wp remote servers page 2 limit 1"));
        assertTrue(components(last()).stream().filter(c -> c.clickEvent() != null).allMatch(c -> c.children().isEmpty()));
        for (String command : clicks(last())) assertEquals(1, dispatcher.execute(command.substring(1), "console"));
        dispatcher.execute("wp remote", "console");
        assertTrue(keys(last()).contains("waypoint.help.remote.summary"));
        assertFalse(text(last()).contains("/wp remote tp"));
        assertNull(dispatcher.getRoot().getChild("wp").getChild("remote").getChild("tp"));
    }
    @Test void unauthorizedViewsNeverExposeRetainedCoordinates() {
        var existing = index.views().get(A);
        var hidden = new CatalogReceiver.View(existing.snapshot(), RemoteCatalogState.UNAUTHORIZED, existing.displayName(), existing.mode());
        var result = queries.query(Map.of(A, hidden), new RemoteCatalogQuery.Scope(null, null, null), options("", WaypointSorting.SortMode.DEFAULT, false, true));
        assertEquals(1, result.rows().size()); assertNull(result.rows().get(0).waypoint()); assertNull(result.rows().get(0).dimension());
    }
}
