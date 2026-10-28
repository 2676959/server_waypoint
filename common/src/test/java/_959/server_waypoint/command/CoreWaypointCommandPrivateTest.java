package _959.server_waypoint.command;

import _959.server_waypoint.command.CoreWaypointCommandListTest.TestMessageSender;
import _959.server_waypoint.command.CoreWaypointCommandListTest.TestSource;
import _959.server_waypoint.command.CoreWaypointCommandListTest.TestWaypointCommand;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import _959.server_waypoint.navigation.*;
import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

class CoreWaypointCommandPrivateTest {
    @TempDir Path root;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final TestSource source = new TestSource("overworld", new WaypointPos(10, 64, 20));
    private final Map<String, Boolean> grants = new HashMap<>();
    private final Map<String, Integer> permissionChecks = new HashMap<>();
    private final List<Component> messages = new ArrayList<>();
    private final List<Component> errors = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private UUID executor;
    private boolean queue;
    private Config previous;
    private WaypointServerCore server;
    private CommandDispatcher<TestSource> dispatcher;
    private NavigationService<Object> navigation;
    private int teleports;

    @BeforeEach void setUp() {
        previous = WaypointServerCore.CONFIG;
        WaypointServerCore.CONFIG = new Config();
        server = new WaypointServerCore(root) {
            @Override protected boolean isRegisteredIconItem(_959.server_waypoint.util.NamespacedId icon) {
                return icon.toString().equals("minecraft:diamond");
            }
        };
        executor = alice;
        var sender = new TestMessageSender() {
            @Override public void sendMessage(TestSource s, Component message) {
                assertSame(source, s, "Preserve the platform's executing-source and commander feedback routing");
                messages.add(message);
            }
            @Override public void sendError(TestSource s, Component message) { errors.add(message); }
            @Override public void sendPlayerMessage(Object player, Component message) { messages.add(message); }
            @Override public PlayerActionLog.Actor playerActor(Object player) {
                return new PlayerActionLog.Actor((UUID) player, "same-name");
            }
            @Override public PlayerActionLog.Actor commandSenderActor(TestSource s) {
                return new PlayerActionLog.Actor(bob, "commander");
            }
            @Override public void broadcastChunkedMessage(Iterable<?> players,
                    _959.server_waypoint.core.network.ChunkedMessage message) { fail("Private update broadcast"); }
        };
        var permissions = new PermissionManager<TestSource, String, Object>(new PermissionStringKeys()) {
            @Override public boolean hasPermission(TestSource s, PermissionKeys<String>.PermissionKey key, int level) {
                permissionChecks.merge(key.getKey(), 1, Integer::sum);
                return grants.getOrDefault(key.getKey(), level == 0);
            }
            @Override public boolean checkPlayerPermission(Object p, PermissionKeys<String>.PermissionKey key, int level) {
                return grants.getOrDefault(key.getKey(), level == 0);
            }
        };
        navigation = new NavigationService<>(new NavigationPlatform<>() {
            @Override public UUID playerUuid(Object player) { return (UUID) player; }
            @Override public void executePlayer(UUID id, java.util.function.Consumer<Object> action) { action.accept(id); }
            @Override public NavigationSnapshot snapshot(Object player, NavigationTarget target) {
                return NavigationMath.snapshot("overworld", 0, 64, 0, 0, target);
            }
        }, List.of(new NavigationMethodHandler<Object>() {
            @Override public NavigationMethod method() { return NavigationMethod.BOSSBAR; }
            @Override public NavigationResult enable(Object player, NavigationSession session, NavigationSnapshot snapshot) {
                return NavigationResult.success();
            }
            @Override public void update(Object player, NavigationSession session, NavigationSnapshot snapshot) {}
            @Override public void disable(Object player, NavigationSession session) {}
        }));
        var command = new TestWaypointCommand(server, sender, permissions, navigation) {
            @Override protected Object getPlayer(TestSource s) { return executor; }
            @Override protected java.util.concurrent.CompletionStage<Boolean> teleportPlayer(TestSource s, Object player,
                    String dimension, WaypointPos pos, int yaw) {
                teleports++;
                assertEquals(source.position(), pos);
                return java.util.concurrent.CompletableFuture.completedFuture(true);
            }
            @Override protected void executeByServer(TestSource s, Runnable task) {
                if (queue) queued.add(task); else task.run();
            }
        };
        dispatcher = new CommandDispatcher<>();
        command.register(dispatcher);
    }

    @AfterEach void tearDown() { WaypointServerCore.CONFIG = previous; }

    private void run(String command) throws Exception { dispatcher.execute(command.isEmpty() ? "wp" : "wp " + command, source); }
    private SimpleWaypoint waypoint(UUID owner, String name) throws Exception {
        return server.withCollection(WaypointCollectionKey.privateFor(owner), files -> {
            var dimension = files.getWaypointFileManager("overworld");
            var list = dimension == null ? null : dimension.getWaypointListByName("Homes");
            return list == null ? null : list.getWaypointByName(name);
        });
    }
    private void add() throws Exception { run("private add overworld Homes position Base B FFAA00 90 false \"home, base\" \"My home\""); }
    private static List<String> clicks(Component component) {
        List<String> result = new ArrayList<>();
        if (component.clickEvent() != null) result.add(component.clickEvent().value());
        for (Component child : component.children()) result.addAll(clicks(child));
        if (component instanceof TranslatableComponent text) {
            for (var argument : text.arguments()) if (argument.value() instanceof Component value) result.addAll(clicks(value));
        }
        return result;
    }

    @Test void invalidListOptionsFailAtTheArgumentBeforeStorageOrScheduling() {
        queue = true;
        for (String prefix : List.of("wp private list", "wp private list all", "wp private list overworld",
                "wp private list overworld Homes")) {
            for (String suffix : List.of("sort invalid", "sort NAME", "sort name order invalid",
                    "sort default order descending", "view invalid", "page 0", "limit 101")) {
                String input = prefix + " " + suffix;
                CommandSyntaxException failure = assertThrows(CommandSyntaxException.class,
                        () -> dispatcher.execute(input, source), input);
                String invalid = suffix.substring(suffix.lastIndexOf(' ') + 1);
                assertEquals(input.lastIndexOf(invalid), failure.getCursor(), input);
                assertTrue(queued.isEmpty(), "Invalid options must not schedule an owner operation");
                assertFalse(Files.exists(root.resolve("private-waypoints")), input);
                assertTrue(messages.isEmpty());
                assertTrue(errors.isEmpty(), "Brigadier must receive the original syntax error");
            }
        }
    }

    @Test void boundedOptionSuggestionsDoNotLoadAnOwnerStore() {
        String input = "wp private list all sort na";
        assertEquals(List.of("name"), dispatcher.getCompletionSuggestions(dispatcher.parse(input, source))
                .join().getList().stream().map(s -> s.getText()).toList());
        assertFalse(Files.exists(root.resolve("private-waypoints")));
    }

    @Test void commandTreeCopyChecksPrivateListPermissionOnceForTheBranch() {
        var list = dispatcher.getRoot().getChild("wp").getChild("private").getChild("list");
        permissionChecks.clear();
        assertTrue(usableNodes(list) > 1000);
        assertEquals(1, permissionChecks.get("server_waypoint.command.private.list"));
        grants.put("server_waypoint.command.private.list", false);
        permissionChecks.clear();
        assertEquals(0, usableNodes(list));
        assertEquals(1, permissionChecks.get("server_waypoint.command.private.list"));
    }

    private int usableNodes(com.mojang.brigadier.tree.CommandNode<TestSource> node) {
        if (!node.canUse(source)) return 0;
        return 1 + node.getChildren().stream().mapToInt(this::usableNodes).sum();
    }

    @Test void privateOptionsAndOwnerSuggestionsWorkThroughNestedRedirects() throws Exception {
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<TestSource>literal("run")
                .redirect(dispatcher.getRoot()));
        add();
        for (String prefix : List.of("", "run ", "run run ")) {
            String input = prefix + "wp private list all sort invalid";
            var failure = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(input, source));
            assertEquals(input.indexOf("invalid"), failure.getCursor());
            String completion = prefix + "wp private list all sort di";
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(completion, source)).join().getList();
            assertEquals(List.of("distance"), suggestions.stream().map(s -> s.getText()).toList());
            assertEquals(completion.length() - 2, suggestions.get(0).getRange().getStart());
            assertEquals(1, dispatcher.execute(prefix + "wp private list all sort name order descending limit 3 view tree page 1", source));
            assertTrue(errors.isEmpty());
            String ownerCompletion = prefix + "wp private remove overworld ";
            assertEquals(List.of("Homes"), dispatcher.getCompletionSuggestions(dispatcher.parse(ownerCompletion, source))
                    .join().getList().stream().map(s -> s.getText()).toList());
        }
    }

    @Test void isolatesIdenticalNamesAndPersistsFullProperties() throws Exception {
        server.putWaypointList("overworld", new WaypointList("Homes", 1, List.of(
                new SimpleWaypoint("Base", "B", new WaypointPos(1, 2, 3), 0x123456, 0, true))));
        add();
        executor = bob;
        add();
        run("private edit waypoint overworld Homes Base set identifier Mine");
        assertNotNull(waypoint(alice, "Base"));
        assertNull(waypoint(bob, "Base"));
        assertNotNull(waypoint(bob, "Mine"));
        assertEquals(new WaypointPos(1, 2, 3), server.getWaypointFileManager("overworld")
                .getWaypointListByName("Homes").getWaypointByName("Base").pos());
        assertEquals("My home", waypoint(alice, "Base").description());
        assertEquals(90, waypoint(alice, "Base").yaw());
        assertTrue(Files.exists(root.resolve("private-waypoints").resolve(alice.toString())));
    }

    @Test void menuHelpDetailsAndActionsKeepPrivatePrefix() throws Exception {
        add();
        for (String command : List.of("private", "private help", "private help add", "private list all",
                "private details waypoint overworld Homes Base", "private details list overworld Homes")) run(command);
        var commands = messages.stream().flatMap(message -> clicks(message).stream()).toList();
        assertFalse(commands.isEmpty());
        assertTrue(commands.stream().allMatch(command -> command.startsWith("/wp private")), commands.toString());
        assertFalse(commands.stream().anyMatch(command -> command.contains("upload") || command.contains("download")));
    }

    @Test void noPlayerFailsBeforeStorageAndPrivateTeleportUsesDedicatedGrant() throws Exception {
        executor = null;
        run("private list");
        assertFalse(errors.isEmpty());
        assertFalse(Files.exists(root.resolve("private-waypoints")));
        executor = alice;
        add();
        grants.put("server_waypoint.command.tp", true);
        assertThrows(CommandSyntaxException.class, () -> run("private tp overworld Homes Base"));
        grants.put("server_waypoint.command.private.tp", true);
        assertDoesNotThrow(() -> run("private tp overworld Homes Base"));
    }

    @Test void restoreIsOwnerAndCollectionBoundAndSingleUse() throws Exception {
        add();
        messages.clear();
        run("private remove overworld Homes Base");
        String restore = messages.stream().flatMap(message -> clicks(message).stream())
                .filter(command -> command.startsWith("/wp private restore ")).findFirst().orElseThrow();
        executor = bob;
        dispatcher.execute(restore.substring(1), source);
        assertNull(waypoint(bob, "Base"));
        executor = alice;
        run(restore.substring("/wp private ".length()));
        assertNull(waypoint(alice, "Base"));
        dispatcher.execute(restore.substring(1), source);
        assertNotNull(waypoint(alice, "Base"));
        run("private remove overworld Homes Base");
        dispatcher.execute(restore.substring(1), source);
        assertNull(waypoint(alice, "Base"));
    }

    @Test void suggestionsUseOnlyExecutingOwner() throws Exception {
        add();
        executor = bob;
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("wp private remove overworld ", source)).join();
        assertTrue(suggestions.isEmpty());
        executor = alice;
        suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("wp private remove overworld ", source)).join();
        assertEquals(List.of("Homes"), suggestions.getList().stream().map(s -> s.getText()).toList());
    }

    @Test void executeAsUsesExecutingPlayerInsteadOfTheOriginalCommander() throws Exception {
        add();
        assertNotNull(waypoint(alice, "Base"));
        assertNull(waypoint(bob, "Base"));
        assertFalse(messages.isEmpty());
    }

    @Test void queuedWorkRetainsAuthenticatedOwnerAndRechecksPermission() throws Exception {
        queue = true;
        add();
        executor = bob;
        queued.remove(0).run();
        assertNotNull(waypoint(alice, "Base"));
        assertNull(waypoint(bob, "Base"));
        add();
        grants.put("server_waypoint.command.private.add", false);
        queued.remove(0).run();
        assertNull(waypoint(bob, "Base"));
    }

    @Test void privateNavigationRefreshAndRemovalKeepCollectionIdentity() throws Exception {
        add();
        run("private navigate overworld Homes Base bossbar");
        assertEquals(WaypointCollectionKey.privateFor(alice), navigation.status(alice).session().target().collectionKey());
        executor = bob;
        add();
        run("private edit waypoint overworld Homes Base set identifier BobBase");
        assertEquals("Base", navigation.status(alice).session().target().waypointName());
        executor = alice;
        run("private edit list overworld Homes set identifier Renamed");
        assertEquals("Renamed", navigation.status(alice).session().target().listName());
        run("private remove overworld Renamed Base");
        assertEquals(NavigationResult.Code.NO_ACTIVE_SESSION, navigation.status(alice).code());
    }

    @Test void sharedMenuAndNavigationUseThePrivateTargetsTeleportPermission() throws Exception {
        add();
        run("private navigate overworld Homes Base bossbar");
        grants.put("server_waypoint.command.tp", true);
        messages.clear();
        run("navigate");
        run("");
        assertTrue(messages.stream().flatMap(message -> clicks(message).stream())
                .noneMatch(command -> command.startsWith("/wp private tp")));
        grants.put("server_waypoint.command.private.tp", true);
        messages.clear();
        run("navigate");
        assertTrue(messages.stream().flatMap(message -> clicks(message).stream())
                .anyMatch(command -> command.startsWith("/wp private tp")));
    }

    @Test void quotaAndCollisionFailuresDoNotConsumeRestore() throws Exception {
        add();
        messages.clear();
        run("private remove overworld Homes Base");
        String restore = messages.stream().flatMap(message -> clicks(message).stream())
                .filter(command -> command.startsWith("/wp private restore ")).findFirst().orElseThrow().substring(1);
        add();
        dispatcher.execute(restore, source);
        assertFalse(errors.isEmpty());
        run("private edit waypoint overworld Homes Base set identifier Occupied");
        WaypointServerCore.CONFIG = new Gson().fromJson("{\"privateWaypoints\":{\"maxWaypointsPerPlayer\":1}}", Config.class);
        dispatcher.execute(restore, source);
        assertNull(waypoint(alice, "Base"));
        run("private remove overworld Homes Occupied");
        dispatcher.execute(restore, source);
        assertNotNull(waypoint(alice, "Base"));
    }

    @Test void emptyListLifecycleAndQuotaFailureDoNotLeakLists() throws Exception {
        run("private add overworld Empty");
        run("private details list overworld Empty");
        run("private edit list overworld Empty set display-name Pretty");
        run("private remove overworld Empty");
        add();
        WaypointServerCore.CONFIG = new Gson().fromJson("{\"privateWaypoints\":{\"maxListsPerPlayer\":1}}", Config.class);
        run("private add overworld Rejected position Extra");
        assertEquals(Integer.valueOf(1), server.withCollection(WaypointCollectionKey.privateFor(alice), files ->
                files.getWaypointFileManager("overworld").getWaypointLists().size()));
        assertFalse(errors.isEmpty());
    }

    @Test void failedPersistenceSuppressesSuccessAndPreservesRestoreToken() throws Exception {
        add();
        run("private remove overworld Homes Base");
        String restore = messages.stream().flatMap(message -> clicks(message).stream())
                .filter(command -> command.startsWith("/wp private restore ")).findFirst().orElseThrow().substring(1);
        Path file = server.withCollection(WaypointCollectionKey.privateFor(alice), files ->
                files.getWaypointFileManager("overworld").getDimensionFile());
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("block-save"), "blocked");
        messages.clear();
        dispatcher.execute(restore, source);
        assertTrue(messages.isEmpty(), "No durable success before save");
        assertFalse(errors.isEmpty());
        Files.delete(file.resolve("block-save"));
        Files.delete(file);
        run("private remove overworld Homes Base");
        dispatcher.execute(restore, source);
        assertNotNull(waypoint(alice, "Base"));
    }

    @Test void privateListDenialHidesBrowseAndHelpAndSharedMenuExposesPrivate() throws Exception {
        run("");
        assertTrue(messages.stream().flatMap(message -> clicks(message).stream()).anyMatch("/wp private"::equals));
        grants.put("server_waypoint.command.private.list", false);
        assertThrows(CommandSyntaxException.class, () -> run("private list"));
        messages.clear();
        run("private");
        run("private help");
        assertTrue(messages.stream().flatMap(message -> clicks(message).stream())
                .noneMatch(command -> command.startsWith("/wp private list") || command.contains("help list")
                        || command.contains("help details")));
    }

    @Test void propertyEditsAndClearOperationsUseThePrivateStore() throws Exception {
        add();
        for (String tail : List.of("set display-name Pretty", "set initials P", "set position position",
                "set color 123456", "set yaw 45", "set visibility global", "set keywords \"mine, home\"",
                "set description Updated", "set icon minecraft:diamond")) {
            run("private edit waypoint overworld Homes Base " + tail);
        }
        SimpleWaypoint updated = waypoint(alice, "Base");
        assertEquals("Pretty", updated.displayName());
        assertEquals("P", updated.initials());
        assertEquals(0x123456, updated.rgb());
        assertEquals(45, updated.yaw());
        assertTrue(updated.global());
        assertEquals("Updated", updated.description());
        assertEquals("minecraft:diamond", updated.icon().toString());
        for (String property : List.of("display-name", "keywords", "description", "icon")) {
            run("private edit waypoint overworld Homes Base clear " + property);
        }
        updated = waypoint(alice, "Base");
        assertEquals("Base", updated.displayName());
        assertTrue(updated.keywords().isEmpty());
        assertTrue(updated.description().isEmpty());
        assertNull(updated.icon());
        assertTrue(server.getFileManagerMap().isEmpty());
    }

    @Test void scopedClickGuardMeasuresThePrivatePrefix() {
        var viewer = new _959.server_waypoint.text.chat.Viewer(java.util.Set.of(), false, false,
                null, null, 0).withScope(_959.server_waypoint.core.collection.WaypointScope.PRIVATE);
        Component link = _959.server_waypoint.text.chat.Chat.link(viewer, Component.text("Long command"),
                net.kyori.adventure.text.format.NamedTextColor.AQUA,
                _959.server_waypoint.text.chat.Click.run("/wp " + "a".repeat(252)), null);
        assertNull(link.clickEvent());
    }
}
