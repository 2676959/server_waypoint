package _959.server_waypoint.command;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.handoff.*;
import java.util.concurrent.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.util.StringCommandBuilder;
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

    @BeforeEach void setup() throws Exception {
        var commands = new RemoteWaypointCommand<String>(() -> { assertTrue(allowed || tpAllowed, "Denied readers must not access the catalog"); return new RemoteCatalogStore(index); }, (source, text) -> messages.add(text),
                (source, text) -> errors.add(text), () -> 5, source -> allowed, source -> tpAllowed, handoffs,
                source -> Component.text("Remote help"), this::viewer);
        dispatcher.register(LiteralArgumentBuilder.<String>literal("wp").then(commands.build()));
        Map<String, RemoteWaypointSnapshot> waypoints = new HashMap<>();
        for (int i = 0; i < 12; i++) waypoints.put("base " + i, waypoint("Display " + i, i));
        publish(A, Map.of(DIMENSION, Map.of("search", new RemoteListSnapshot("Public display", new RemoteRevision(1), waypoints),
                "", new RemoteListSnapshot("Empty identity", new RemoteRevision(1), Map.of()))));
        publish(B, Map.of(DIMENSION, Map.of("search", new RemoteListSnapshot("Other display", new RemoteRevision(1),
                Map.of("base 0", waypoint("Other waypoint", 0))))));
    }
    /** "plain" reads plain text like the console; every other source reads chat. */
    private Viewer viewer(String source) {
        Set<Viewer.Permission> permissions = EnumSet.noneOf(Viewer.Permission.class);
        if (allowed) permissions.add(Viewer.Permission.REMOTE_LIST);
        if (tpAllowed) permissions.add(Viewer.Permission.REMOTE_TP);
        return new Viewer(permissions, false, source.equals("plain"), null, null, 0F);
    }
    private static RemoteWaypointSnapshot waypoint(String display, int position) {
        return new RemoteWaypointSnapshot(display, "B", new WaypointPos(position, 64, 0), position % 2 == 0 ? 0xFF0000 : 0x00FF00,
                0, false, List.of("village"), "description", null);
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
    private String tpTarget() { return target().replace("remote list", "remote tp") + " " + quote("base 0"); }
    private String detailsTarget() { return target().replace("remote list", "remote details") + " " + quote("base 0"); }
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
        assertTrue(ChatAssert.render(last()).endsWith("No waypoints yet."));
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
    @Test void generatedLinksKeepTheExactScopeInTheCanonicalOptionOrder() throws Exception {
        assertEquals(1, dispatcher.execute(target() + " search village sort name order descending limit 2", "console"));
        String next = clicks(last()).stream().filter(value -> value.endsWith(" page 2")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search village sort name order descending limit 2 page 2", next);
        assertEquals(1, dispatcher.execute(next.substring(1), "console"));
        assertFalse(ChatAssert.render(last()).contains("Other waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.startsWith("/wp remote list ") || value.startsWith("/wp remote details ")));
        for (String suffix : List.of("search village", "sort color order ascending search village", "page 1 limit 3 view tree search village",
                "sort name page 1 limit 2 view flat", "limit 3 search village", "view flat search village", "view lists")) {
            assertEquals(1, dispatcher.execute(target() + " " + suffix, "console"));
        }
    }
    @Test void listControlsKeepTheScopeAndOfferNoDistanceSorting() throws Exception {
        dispatcher.execute(target() + " search village sort color order descending limit 2 page 2", "console");
        Component output = last();
        assertTrue(ChatAssert.render(output).contains("Sort Default · Name · Color ↓"));
        assertFalse(ChatAssert.render(output).contains("Distance"));
        String search = ChatAssert.suggestions(output).stream().filter(value -> value.endsWith(" search ")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search ", search);
        assertEquals(1, dispatcher.execute(search.substring(1) + "village", "console"));
        for (String command : ChatAssert.runCommands(output)) {
            assertEquals(1, dispatcher.execute(command.substring(1), "console"), command);
        }
    }
    @Test void remoteDimensionsAreNamedAndColouredByTheirIds() throws Exception {
        publish(new RemoteServerId("colored"), Map.of("minecraft:the_nether", Map.of("list",
                new RemoteListSnapshot("list", new RemoteRevision(1), Map.of("point", waypoint("point", 0))))));
        for (String command : List.of("wp remote list colored", "wp remote list colored " + quote("minecraft:the_nether"),
                "wp remote details colored " + quote("minecraft:the_nether") + " list point")) {
            assertEquals(1, dispatcher.execute(command, "console"));
            assertEquals(NamedTextColor.RED, ChatAssert.colorOf(last(), "Nether"), command);
        }
    }
    @Test void initialsTeleportOnlyWhenAvailableAndPermittedWhileNamesOpenDetails() throws Exception {
        tpAllowed = true;
        dispatcher.execute(target(), "player");
        Component output = last();
        assertEquals("/" + tpTarget(), ChatAssert.clickOf(output, "[B]"));
        assertEquals("/" + detailsTarget(), ChatAssert.clickOf(output, "Display 0"));
        assertTrue(ChatAssert.tooltipOf(output, "Display 0").contains("description"));
        assertEquals(1, dispatcher.execute(tpTarget(), "player"));
        assertNotNull(preparation);
        tpAllowed = false;
        dispatcher.execute(target(), "player");
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        tpAllowed = true;
        index.disconnected(A, owner);
        dispatcher.execute(target(), "player");
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        assertEquals("Off while Server search is stale", ChatAssert.tooltipOf(last(), "[B]"));
    }
    @Test void detailsResolveExactCachedIdentitiesAndRespectAvailabilityAndPermissions() throws Exception {
        dispatcher.execute(target(), "console");
        List<String> details = clicks(last()).stream().filter(value -> value.startsWith("/wp remote details ")).toList();
        assertEquals(5, details.size());
        for (String command : details) assertEquals(1, dispatcher.execute(command.substring(1), "console"));
        String rendered = ChatAssert.render(last());
        assertTrue(rendered.contains("Position: ") && rendered.contains("Keywords: village")
                && rendered.contains("Description: description"));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        String waypointDetails = details.get(0).substring(1);
        tpAllowed = true;
        dispatcher.execute(waypointDetails, "player");
        assertTrue(ChatAssert.runCommands(last()).stream().anyMatch(value -> value.startsWith("/wp remote tp ")));
        index.disconnected(A, owner);
        dispatcher.execute(waypointDetails, "player");
        assertEquals(NamedTextColor.DARK_GRAY, ChatAssert.colorOf(last(), "[Teleport]"));
        assertTrue(ChatAssert.runCommands(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        time.set(11_000_000); index.maintain();
        assertEquals(0, dispatcher.execute(waypointDetails, "player"));
        assertFalse(ChatAssert.render(last()).contains("description"));
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
        assertTrue(ChatAssert.render(last()).contains("Long waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.length() <= 256));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ") || value.startsWith("/wp remote details ")));
    }
    @Test void staleUnreachableAndEmptyServersStayDistinct() throws Exception {
        index.disconnected(A, owner);
        dispatcher.execute(target(), "console");
        assertTrue(ChatAssert.render(last()).contains("Display"));
        assertEquals("Stale\nTeleporting is off until it refreshes", ChatAssert.tooltipOf(last(), "●"));
        time.set(11_000_000); index.maintain();
        dispatcher.execute(target(), "console");
        assertEquals(List.of("● Server search ⏷", "Server search can't be reached right now. Servers"), ChatAssert.lines(last()));
        assertTrue(suggestions("wp remote list \"search\" ").stream().noneMatch(value -> value.equals(quote(DIMENSION))));
        publish(new RemoteServerId("empty"), Map.of());
        dispatcher.execute("wp remote list empty", "console");
        assertEquals(List.of("● Server empty ⏷", "Nothing published yet."), ChatAssert.lines(last()));
        assertEquals("Available", ChatAssert.tooltipOf(last(), "●"));
    }
    @Test void missingScopesAndDistanceAreErrorLines() throws Exception {
        assertEquals(0, dispatcher.execute("wp remote list missing", "console"));
        assertEquals("✘ No server called missing. Servers", ChatAssert.render(errors.get(0)));
        assertEquals(0, dispatcher.execute("wp remote list other missing", "console"));
        assertEquals("✘ Server other has no dimension missing. Browse", ChatAssert.render(errors.get(1)));
        assertEquals(0, dispatcher.execute("wp remote list other " + quote(DIMENSION) + " missing", "console"));
        assertTrue(ChatAssert.render(errors.get(2)).startsWith("✘ Server other has no list missing in "));
        assertEquals(0, dispatcher.execute(target() + " sort distance", "console"));
        assertEquals("✘ Remote waypoints can't be sorted by distance.", ChatAssert.render(errors.get(3)));
        assertEquals(1, dispatcher.execute(target() + " page 2147483647", "console"));
        assertTrue(ChatAssert.render(last()).startsWith("✘ Page 2147483647 does not exist; the last page is 3."));
    }
    @Test void remoteListsSearchAndSortLikeLocalLists() {
        var servers = RemoteCatalogQuery.servers(new RemoteCatalogStore(index).snapshot());
        WaypointQueryEngine.Query fuzzy = new WaypointQueryEngine.Query("vilage", WaypointSorting.SortMode.NAME, null, null, false);
        assertEquals(13, servers.stream().mapToInt(server -> WaypointQueryEngine.queryLists(server.dimensions(), fuzzy).waypointCount()).sum());
        var search = servers.stream().filter(server -> server.id().equals(A)).findFirst().orElseThrow();
        List<String> ascending = WaypointQueryEngine.queryLists(search.dimensions(), fuzzy).dimensions().get(0).lists().stream()
                .flatMap(list -> list.waypoints().stream()).map(SimpleWaypoint::name).toList();
        List<String> descending = WaypointQueryEngine.queryLists(search.dimensions(),
                        new WaypointQueryEngine.Query("vilage", WaypointSorting.SortMode.NAME, null, null, true))
                .dimensions().get(0).lists().stream().flatMap(list -> list.waypoints().stream()).map(SimpleWaypoint::name).toList();
        List<String> reversed = new ArrayList<>(ascending); Collections.reverse(reversed);
        assertEquals(reversed, descending);
        assertThrows(UnsupportedOperationException.class, () -> search.dimensions().clear());
    }
    @Test void thePickerAndItsPagesOfferOnlyReadOnlyActions() throws Exception {
        assertEquals(1, dispatcher.execute("wp remote", "console"));
        assertEquals("Remote servers  2 servers connected", ChatAssert.lines(last()).get(0));
        for (String command : ChatAssert.runCommands(last())) assertEquals(1, dispatcher.execute(command.substring(1), "console"), command);
        assertEquals(1, dispatcher.execute("wp remote page 1", "console"));
        var remote = dispatcher.getRoot().getChild("wp").getChild("remote");
        assertNull(remote.getChild("servers"));
        assertNotNull(remote.getChild("tp"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> dispatcher.execute(
                "wp remote details \"search\" " + quote(DIMENSION) + " \"search\"", "console"));
        allowed = false; tpAllowed = true;
        dispatcher.execute("wp remote", "player");
        assertEquals("Remote help", text(last()));
    }
    @Test void permissionDenialAndRevocationBlockCommandsAndCachedSuggestions() throws Exception {
        var parsed = dispatcher.parse(target(), "console");
        var suggestionParse = dispatcher.parse("wp remote list ", "console");
        allowed = false;
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("wp remote page 1", "console"));
        assertEquals(0, dispatcher.execute(parsed));
        assertTrue(dispatcher.getCompletionSuggestions(suggestionParse).join().getList().stream()
                .noneMatch(suggestion -> Set.of("\"search\"", "other").contains(suggestion.getText())));
        assertTrue(messages.isEmpty());
        assertTrue(errors.isEmpty());
        allowed = true;
        assertEquals(1, dispatcher.execute("wp remote page 1", "console"));
    }
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
        assertEquals(1, transfers);
        assertEquals("Switching you to Server search for [B] Display 0…", ChatAssert.render(last()));
        assertTrue(errors.isEmpty());
    }
    @Test void teleportPreparationRejectionReportsExactReasonWithoutTransfer() throws Exception {
        tpAllowed = true; dispatcher.execute(tpTarget(), "player");
        prepareReply.complete(new ApplicationMessage.HandoffRejected(ApplicationMessage.Result.UNSUPPORTED));
        assertEquals(0, transfers); assertTrue(keys(errors.get(0)).contains("wp.remote.tp.unsupported"));
    }
    @Test void teleportPermissionsAreIndependentAndRecheckedAfterParsing() throws Exception {
        tpAllowed = true; allowed = false;
        var parsed = dispatcher.parse(tpTarget(), "player");
        var suggestionParse = dispatcher.parse("wp remote tp ", "player");
        dispatcher.execute("wp remote", "player");
        assertEquals("Remote help", text(last()));
        tpAllowed = false;
        assertEquals(0, dispatcher.execute(parsed)); assertNull(preparation);
        assertTrue(keys(errors.get(0)).contains("wp.remote.tp.unauthorized"));
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
        assertTrue(keys(errors.get(0)).contains("wp.remote.tp.unavailable"));
    }
    @Test void missingAndStaleTargetsNeverPrepare() throws Exception {
        tpAllowed = true;
        assertEquals(0, dispatcher.execute(tpTarget().replace("base 0", "Display 0"), "player"));
        assertTrue(keys(errors.get(0)).contains("wp.remote.tp.not_found"));
        index.disconnected(A, owner);
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(1)).contains("wp.remote.tp.stale_catalog"));
        time.set(11_000_000); index.maintain();
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(2)).contains("wp.remote.tp.unavailable"));
        assertNull(preparation); assertEquals(0, transfers);
    }
    @Test void unauthorizedViewsNeverExposeRetainedCoordinates() {
        var existing = index.views().get(A);
        var hidden = new CatalogReceiver.View(existing.snapshot(), RemoteCatalogState.UNAUTHORIZED, existing.displayName(), existing.mode(), "minecraft:compass");
        var server = RemoteCatalogQuery.servers(Map.of(A, hidden)).get(0);
        assertFalse(server.readable());
        assertTrue(server.dimensions().isEmpty());
        assertEquals(0, server.waypointCount());
    }
}
