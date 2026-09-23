package _959.server_waypoint.command;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.handoff.*;
import java.util.concurrent.*;
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
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RemoteWaypointCommandTest {
    private static final RemoteServerId A = new RemoteServerId("search"), B = new RemoteServerId("other");
    private static final String DIMENSION = "world \"quoted\"\\zone";
    private boolean allowed = true, tpAllowed;
    private final UUID playerId = UUID.randomUUID();
    private final CompletableFuture<ApplicationMessage> prepareReply = new CompletableFuture<>();
    private ApplicationMessage.PrepareHandoff preparation;
    private int transfers;
    private final SourceHandoffService<String> handoffs = new SourceHandoffService<>(new RemoteServerId("source"), new SourceHandoffService.Platform<>() {
        public boolean ownsThread(String source) { return true; }
        public UUID playerId(String source) { return playerId; }
        public boolean isCurrentPlayer(String source, UUID id) { return playerId.equals(id); }
        public boolean canTeleport(String source) { return tpAllowed; }
        public boolean execute(String source, Runnable task, Runnable retired) { task.run(); return true; }
    }, new SourceHandoffService.Link() {
        public CompletionStage<ApplicationMessage> prepare(UUID id, ApplicationMessage.PrepareHandoff request) {
            preparation = request; return prepareReply;
        }
        public CompletionStage<ApplicationMessage.Result> transfer(UUID id, ApplicationMessage.HandoffBinding binding) {
            transfers++; return CompletableFuture.completedFuture(ApplicationMessage.Result.SUCCESS);
        }
        public void cancel(UUID id, ApplicationMessage.CancelHandoff cancel) { }
    });
    @AfterEach void closeHandoffs() { handoffs.close(); }

    private final AtomicLong time = new AtomicLong();
    private final CatalogIndex index = new CatalogIndex(new CatalogCacheLimits(4, 100000, 400000, 10), time::get);
    private final Object owner = new Object();
    private final List<Component> messages = new ArrayList<>(), errors = new ArrayList<>();
    private final CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
    private final RemoteCatalogQuery queries = new RemoteCatalogQuery();

    @BeforeEach void setup() throws Exception {
        var commands = new RemoteWaypointCommand<String>(() -> { assertTrue(allowed || tpAllowed, "Denied readers must not access the catalog"); return new RemoteCatalogStore(index); }, (source, text) -> messages.add(text),
                (source, text) -> errors.add(text), () -> 5, source -> allowed, source -> tpAllowed, handoffs);
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
        index.receive(id, owner, received(request, new ApplicationMessage.CatalogMetadata(id, "Server " + id.value(), new RemoteRevision(1), CatalogExportPolicy.PUBLIC, "minecraft:compass"), null));
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
    @Test void redirectedSuggestionsResolveArgumentsAtEveryRemoteDepth() {
        tpAllowed = true;
        var execute = dispatcher.register(LiteralArgumentBuilder.<String>literal("execute"));
        execute.addChild(LiteralArgumentBuilder.<String>literal("as")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<String, String>argument("target",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                        .fork(execute, context -> List.of("player"))).build());
        execute.addChild(LiteralArgumentBuilder.<String>literal("run").redirect(dispatcher.getRoot()).build());
        for (String prefix : List.of("execute as 7c00 run ", "execute as 7c00 run execute as 7c00 run ")) {
            for (String command : List.of("list", "details", "tp")) {
                String input = "wp remote " + command + " ";
                assertEquals(suggestions(input), suggestions(prefix + input));
                input += quote(A.value()) + " ";
                assertTrue(suggestions(input).contains(quote(DIMENSION)));
                assertEquals(suggestions(input), suggestions(prefix + input));
                input += quote(DIMENSION) + " ";
                assertTrue(suggestions(input).contains("\"search\""));
                assertEquals(suggestions(input), suggestions(prefix + input));
                if (!command.equals("list")) {
                    input += "\"search\" ";
                    assertTrue(suggestions(input).contains(quote("base 0")));
                    assertEquals(suggestions(input), suggestions(prefix + input));
                    assertEquals(suggestions(input + "\"base"), suggestions(prefix + input + "\"base"));
                }
            }
        }
    }
    @Test void combinedOptionsAndGeneratedPageLinksRetainExactScope() throws Exception {
        assertEquals(1, dispatcher.execute(target() + " search village sort name order descending page 1 limit 2 view flat", "console"));
        String next = clicks(last()).stream().filter(value -> value.contains("page 2")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search village sort name order descending page 2 limit 2 view flat", next);
        assertEquals(1, dispatcher.execute(next.substring(1), "console"));
        assertTrue(text(last()).contains("search")); assertFalse(text(last()).contains("Other waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.startsWith("/wp remote list ") || value.startsWith("/wp remote details ")));
        for (String suffix : List.of("search village", "sort color order ascending search village", "page 1 limit 3 view tree search village",
                "sort name page 1 limit 2 view flat", "limit 3 search village", "view flat search village")) {
            assertEquals(1, dispatcher.execute(target() + " " + suffix, "console"));
        }
    }
    @Test void listControlsPreserveScopeAndOptionsAndUseLocalStyles() throws Exception {
        dispatcher.execute(target() + " search village sort color order descending page 2 limit 2", "console");
        Component output = last();
        assertNoInheritedClick(output, null, "Display");
        assertTrue(keys(output).containsAll(List.of("waypoint.list.view.flat", "waypoint.list.sort.label", "waypoint.list.page")));
        assertFalse(keys(output).contains("waypoint.sort.distance"));
        var events = components(output).stream().map(Component::clickEvent).filter(Objects::nonNull).toList();
        String search = events.stream().filter(event -> event.action() == ClickEvent.Action.SUGGEST_COMMAND)
                .map(ClickEvent::value).findFirst().orElseThrow();
        assertEquals("/" + target() + " sort color order descending page 1 limit 2 search ", search);
        assertEquals(1, dispatcher.execute(search.substring(1) + "village", "console"));
        String flat = events.stream().map(ClickEvent::value).filter(value -> value.endsWith("view flat")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search village sort color order descending page 2 limit 2 view flat", flat);
        for (ClickEvent event : events) {
            if (event.action() == ClickEvent.Action.RUN_COMMAND) assertEquals(1, dispatcher.execute(event.value().substring(1), "console"));
        }
        dispatcher.execute(target() + " view flat", "console");
        assertTrue(keys(last()).contains("waypoint.list.view.tree"));
        assertTrue(clicks(last()).stream().anyMatch(value -> value.contains("sort name order descending")));
    }

    @Test void remoteDimensionUsesLocalDimensionColorInListsAndDetails() throws Exception {
        publish(new RemoteServerId("colored"), Map.of("minecraft:the_nether", Map.of("list",
                new RemoteListSnapshot("list", new RemoteRevision(1), Map.of("point", waypoint("point", 0))))));
        for (String command : List.of("wp remote list colored", "wp remote list colored view flat",
                "wp remote details colored " + quote("minecraft:the_nether") + " list point")) {
            assertEquals(1, dispatcher.execute(command, "console"));
            assertTrue(components(last()).stream().anyMatch(component -> component instanceof TextComponent value
                    && value.content().equals("minecraft:the_nether")
                    && component.color() == NamedTextColor.RED), command);
        }
        assertTrue(components(last()).stream().noneMatch(component -> component instanceof TextComponent value
                && value.content().equals("minecraft:the_nether") && component.clickEvent() != null));
    }

    @Test void initialsTeleportOnlyWhenAvailableAndPermittedAndDoNotLeakToNames() throws Exception {
        tpAllowed = true;
        dispatcher.execute(target(), "player");
        Component output = last();
        String teleportClick = clicks(output).stream().filter(value -> value.startsWith("/wp remote tp ")).findFirst().orElseThrow();
        assertEquals("/" + tpTarget(), teleportClick);
        assertTrue(text(output).contains("[B]"));
        assertNoInheritedClick(output, null, "Display 0");
        Component name = components(output).stream().filter(c -> c instanceof TextComponent t && t.content().equals("Display 0"))
                .findFirst().orElseThrow();
        // The hover lives on the neutral display-label wrapper, outside the teleport control.
        assertTrue(components(output).stream().anyMatch(c -> c.hoverEvent() != null
                && c.hoverEvent().value() instanceof Component hover && text(hover).contains("description")));
        assertNull(name.clickEvent());
        assertEquals(1, dispatcher.execute(teleportClick.substring(1), "player"));
        assertNotNull(preparation);
        tpAllowed = false;
        dispatcher.execute(target(), "player");
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        tpAllowed = true;
        index.disconnected(A, owner);
        dispatcher.execute(target(), "player");
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
    }

    private static void assertNoInheritedClick(Component component, ClickEvent inherited, String match) {
        ClickEvent effective = component.clickEvent() == null ? inherited : component.clickEvent();
        if (component instanceof TextComponent text && text.content().contains(match)) assertNull(effective);
        if (component instanceof TranslatableComponent translated && translated.key().equals("waypoint.list.page")) assertNull(effective);
        for (Component child : component.children()) assertNoInheritedClick(child, effective, match);
    }

    @Test void detailsLinksResolveExactCachedIdentityAndRespectAvailabilityAndPermissions() throws Exception {
        dispatcher.execute(target(), "console");
        List<String> details = clicks(last()).stream().filter(value -> value.startsWith("/wp remote details ")).toList();
        assertEquals(6, details.size());
        for (String command : details) assertEquals(1, dispatcher.execute(command.substring(1), "console"));
        assertTrue(keys(last()).containsAll(List.of("waypoint.details.waypoint.title", "waypoint.details.description",
                "waypoint.details.color", "waypoint.details.keywords")));
        assertTrue(text(last()).contains("description"));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        String waypointDetails = details.get(1).substring(1);
        tpAllowed = true;
        dispatcher.execute(waypointDetails, "player");
        assertTrue(clicks(last()).contains("/" + tpTarget()));
        index.disconnected(A, owner);
        dispatcher.execute(waypointDetails, "player");
        assertTrue(keys(last()).contains("waypoint.remote.state.stale"));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        time.set(11_000_000); index.maintain();
        assertEquals(0, dispatcher.execute(waypointDetails, "player"));
        assertFalse(text(last()).contains("description"));
        var parsed = dispatcher.parse(waypointDetails, "player");
        allowed = false;
        int count = messages.size();
        assertEquals(0, dispatcher.execute(parsed));
        assertEquals(count, messages.size());
    }

    @Test void longIdentitiesNeverProduceTruncatedOrOversizedActions() throws Exception {
        String longList = "x".repeat(240);
        publish(new RemoteServerId("long"), Map.of(DIMENSION, Map.of(longList,
                new RemoteListSnapshot("Long list", new RemoteRevision(1), Map.of("waypoint", waypoint("Long waypoint", 0))))));
        tpAllowed = true;
        dispatcher.execute("wp remote list long " + quote(DIMENSION) + " " + quote(longList), "player");
        assertTrue(text(last()).contains("Long waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.length() <= 256));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ") || value.startsWith("/wp remote details ")));
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
        assertTrue(clicks(last()).containsAll(List.of("/wp remote servers", "/wp remote list",
                "/wp remote details ", "/wp help remote")));
        assertNotNull(dispatcher.getRoot().getChild("wp").getChild("remote").getChild("tp"));
    }
    @Test void permissionDenialAndRevocationBlockCommandsAndCachedSuggestions() throws Exception {
        var parsed = dispatcher.parse(target(), "console");
        var suggestionParse = dispatcher.parse("wp remote list ", "console");
        allowed = false;
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("wp remote servers", "console"));
        assertEquals(0, dispatcher.execute(parsed));
        assertTrue(dispatcher.getCompletionSuggestions(suggestionParse).join().getList().stream()
                .noneMatch(suggestion -> Set.of("\"search\"", "other").contains(suggestion.getText())));
        assertTrue(messages.isEmpty());
        assertTrue(errors.isEmpty());
        allowed = true;
        assertEquals(1, dispatcher.execute("wp remote servers", "console"));
    }
    private String tpTarget() { return target().replace("remote list", "remote tp") + " " + quote("base 0"); }
    @Test void teleportCommandReachesFakeTransferOnlyAfterMatchingPreparation() throws Exception {
        tpAllowed = true;
        assertEquals(1, dispatcher.execute(tpTarget(), "player"));
        assertNotNull(preparation); assertEquals(0, transfers);
        assertEquals(new RemoteWaypointKey(A, DIMENSION, "search", "base 0"), preparation.target());
        assertEquals(new RemoteRevision(1), preparation.observedCatalogRevision());
        assertEquals(new RemoteRevision(1), preparation.observedListRevision());
        assertEquals(playerId, preparation.playerId());
        prepareReply.complete(new ApplicationMessage.HandoffPrepared(new ApplicationMessage.HandoffBinding(UUID.randomUUID(), playerId,
                preparation.source(), preparation.target(), preparation.action(), System.currentTimeMillis() + 15000)));
        assertEquals(1, transfers); assertTrue(keys(last()).contains("waypoint.remote.tp.success"));
    }
    @Test void teleportPreparationRejectionReportsExactReasonWithoutTransfer() throws Exception {
        tpAllowed = true; dispatcher.execute(tpTarget(), "player");
        prepareReply.complete(new ApplicationMessage.HandoffRejected(ApplicationMessage.Result.UNSUPPORTED));
        assertEquals(0, transfers); assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.unsupported"));
    }
    @Test void teleportPermissionsAreIndependentAndRecheckedAfterParsing() throws Exception {
        tpAllowed = true; allowed = false;
        var parsed = dispatcher.parse(tpTarget(), "player");
        var suggestionParse = dispatcher.parse("wp remote tp ", "player");
        dispatcher.execute("wp remote", "player");
        assertTrue(text(last()).contains("/wp remote tp")); assertFalse(text(last()).contains("/wp remote list"));
        assertTrue(clicks(last()).contains("/wp remote tp "));
        assertFalse(clicks(last()).contains("/wp remote servers"));
        tpAllowed = false;
        assertEquals(0, dispatcher.execute(parsed)); assertNull(preparation);
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.unauthorized"));
        assertTrue(dispatcher.getCompletionSuggestions(suggestionParse).join().getList().isEmpty());
    }
    @Test void teleportSuggestionsUseExactCachedWaypointNamesAndHideUnavailableData() throws Exception {
        tpAllowed = true;
        String prefix = target().replace("remote list", "remote tp") + " ";
        assertTrue(suggestions(prefix).contains(quote("base 0")));
        assertTrue(suggestions(prefix + "\"base").contains(quote("base 0")));
        assertFalse(suggestions(prefix).contains(quote("Display 0")));
        index.disconnected(A, owner); time.set(11_000_000); index.maintain();
        assertTrue(suggestions(prefix).isEmpty());
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.unavailable"));
    }
    @Test void missingAndStaleTargetsNeverPrepare() throws Exception {
        tpAllowed = true;
        assertEquals(0, dispatcher.execute(tpTarget().replace("base 0", "Display 0"), "player"));
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.not_found"));
        index.disconnected(A, owner);
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(1)).contains("waypoint.remote.tp.stale_catalog"));
        time.set(11_000_000); index.maintain();
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(2)).contains("waypoint.remote.tp.unavailable"));
        assertNull(preparation); assertEquals(0, transfers);
    }
    @Test void unauthorizedViewsNeverExposeRetainedCoordinates() {
        var existing = index.views().get(A);
        var hidden = new CatalogReceiver.View(existing.snapshot(), RemoteCatalogState.UNAUTHORIZED, existing.displayName(), existing.mode(), "minecraft:compass");
        var result = queries.query(Map.of(A, hidden), new RemoteCatalogQuery.Scope(null, null, null), options("", WaypointSorting.SortMode.DEFAULT, false, true));
        assertEquals(1, result.rows().size()); assertNull(result.rows().get(0).waypoint()); assertNull(result.rows().get(0).dimension());
    }
}
