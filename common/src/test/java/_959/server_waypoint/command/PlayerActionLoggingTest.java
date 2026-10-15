package _959.server_waypoint.command;

import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.RemoteRevision;
import _959.server_waypoint.crossserver.RemoteWaypointSnapshot;
import _959.server_waypoint.crossserver.RemoteCatalogSnapshot;
import _959.server_waypoint.crossserver.RemoteListSnapshot;
import _959.server_waypoint.crossserver.CatalogExportPolicy;
import _959.server_waypoint.crossserver.TeleportPermissionCheck;
import _959.server_waypoint.crossserver.catalog.CatalogIndex;
import _959.server_waypoint.crossserver.catalog.CatalogCacheLimits;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogStore;
import _959.server_waypoint.crossserver.protocol.ApplicationCodec;
import _959.server_waypoint.crossserver.protocol.ApplicationEnvelope;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ProtocolLimits;
import _959.server_waypoint.crossserver.transport.TransportMode;
import _959.server_waypoint.crossserver.transport.TcpChannel;
import _959.server_waypoint.crossserver.handoff.RemoteTeleportInitiator;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.impl.StaticLoggerBinder;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlayerActionLoggingTest {
    @TempDir Path directory;
    private Config previous;
    private CommandHarness harness;

    @BeforeEach
    void setUp() {
        previous = WaypointServerCore.CONFIG;
        WaypointServerCore.CONFIG = new Config();
        harness = new CommandHarness(directory);
        StaticLoggerBinder.clear();
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = previous;
        StaticLoggerBinder.clear();
    }

    @Test
    void additionLogsActorAndTargetOnlyOnceAfterItSucceeds() {
        harness.run(CommandHarness.player(), "wp add minecraft:overworld Test");
        var events = StaticLoggerBinder.events().stream().filter(s -> s.contains("action=list_add")).toList();
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("player_id=00000000-0000-0000-0000-000000000000"));
        assertTrue(events.get(0).contains("player=Alex"));
        assertTrue(events.get(0).contains("dimension=minecraft:overworld"));
        assertTrue(events.get(0).contains("list=Test"));
        assertTrue(events.get(0).contains("outcome=success"));
        StaticLoggerBinder.clear();
        harness.run(CommandHarness.player(), "wp add minecraft:overworld Test");
        assertTrue(StaticLoggerBinder.events().stream().noneMatch(s -> s.contains("outcome=success")));
    }
    @Test
    void saveFailureNeverClaimsSuccessAndDelayedSaveDoesNotLogEarly() {
        var tasks = new java.util.ArrayList<Runnable>();
        harness.serverExecutor = tasks::add;
        harness.run(CommandHarness.player(), "wp add minecraft:overworld Test");
        assertTrue(actions().isEmpty());
        harness.failSave = true;
        assertThrows(RuntimeException.class, () -> tasks.remove(0).run());
        assertEquals(1, actions().size());
        assertTrue(actions().get(0).contains("outcome=save_failed"));
        assertFalse(actions().get(0).contains("outcome=success"));
    }

    @Test
    void teleportLogsOnlyWhenItsCompletionIsKnown() throws Exception {
        harness.run(CommandHarness.player(), "wp add minecraft:overworld Test 0 64 0 Home");
        harness.teleportCompletion = new java.util.concurrent.CompletableFuture<>();
        StaticLoggerBinder.clear();
        harness.dispatcher.execute("wp tp minecraft:overworld Test Home", CommandHarness.player());
        assertTrue(actions().isEmpty());
        harness.teleportCompletion.complete(false);
        assertEquals(1, actions().size());
        assertTrue(actions().get(0).contains("action=tp"));
        assertTrue(actions().get(0).contains("outcome=failed"));
        StaticLoggerBinder.clear();
        harness.teleportCompletion = java.util.concurrent.CompletableFuture.completedFuture(true);
        harness.dispatcher.execute("wp tp minecraft:overworld Test Home", CommandHarness.player());
        assertTrue(actions().get(0).contains("outcome=success"));
    }

    @Test
    void mutationsAndRestoreAreAuditedButReadingListsIsQuiet() {
        var player = CommandHarness.player();
        harness.run(player, "wp add minecraft:overworld Test 0 64 0 Home");
        harness.run(player, "wp edit waypoint minecraft:overworld Test Home set yaw 90");
        var removed = harness.run(player, "wp remove minecraft:overworld Test Home");
        String restore = _959.server_waypoint.text.chat.ChatAssert.runCommands(removed).stream()
                .filter(c -> c.startsWith("/wp restore ")).findFirst().orElseThrow();
        harness.run(player, restore.substring(1));
        for (String action : java.util.List.of("add", "edit", "remove", "restore")) {
            assertEquals(1, actions().stream().filter(e -> e.contains("action=" + action + " ")).count());
        }
        StaticLoggerBinder.clear();
        harness.run(player, "wp list");
        assertTrue(actions().isEmpty());
    }

    @Test
    void consoleIsExplicitlyIdentifiedAndNamesCannotInjectLogLines() {
        harness.run(CommandHarness.console(), "wp add minecraft:overworld Test");
        assertTrue(actions().get(0).contains("player_id=non-player"));
        StaticLoggerBinder.clear();
        PlayerActionLog.log(
                new PlayerActionLog.Actor(new java.util.UUID(0, 1), "Alex\nforged"),
                "edit", "success", "waypoint", "Home\r\nforged\u2028line");
        assertEquals(1, actions().size());
        assertFalse(actions().get(0).contains("\n"));
        assertFalse(actions().get(0).contains("\r"));
        assertFalse(actions().get(0).contains("\u2028"));
    }

    @Test
    void executeAsKeepsTheOriginalPlayerSenderThroughTheDelayedSave() {
        var executor = CommandHarness.player();
        var commander = new CommandHarness.Source("Steve", executor.dimension(), executor.position(), executor.yaw(),
                true, false, executor.permissions());
        harness.sender.commandSenders.put(executor, commander);
        var tasks = new java.util.ArrayList<Runnable>();
        harness.serverExecutor = tasks::add;
        harness.run(executor, "wp add minecraft:overworld Test");
        harness.sender.commandSenders.put(executor, CommandHarness.console());
        tasks.remove(0).run();
        var event = actions().get(0);
        assertTrue(event.contains("player=Alex player_id=00000000-0000-0000-0000-000000000000"));
        assertTrue(event.contains("sender=Steve sender_id=00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void executeAsRetainsTheConsoleSenderUntilTeleportCompletes() throws Exception {
        var executor = CommandHarness.player();
        harness.run(executor, "wp add minecraft:overworld Test 0 64 0 Home");
        harness.sender.commandSenders.put(executor, CommandHarness.console());
        harness.teleportCompletion = new java.util.concurrent.CompletableFuture<>();
        StaticLoggerBinder.clear();
        harness.dispatcher.execute("wp tp minecraft:overworld Test Home", executor);
        harness.sender.commandSenders.clear();
        harness.teleportCompletion.complete(true);
        var event = actions().get(0);
        assertTrue(event.contains("player=Alex"));
        assertTrue(event.contains("sender=Server sender_id=non-player"));
    }

    @Test
    void remotePermissionFailureLogsBothStatusesAndRetainsOriginalSender() throws Exception {
        var id = new RemoteServerId("survival");
        var revision = new RemoteRevision(1);
        var index = new CatalogIndex(
                CatalogCacheLimits.DEFAULT);
        Object owner = new Object();
        index.connected(id, owner, TransportMode.NOISE_KK,
                ProtocolLimits.DEFAULT);
        var waypoint = new RemoteWaypointSnapshot("Home", "H",
                new WaypointPos(0, 64, 0), 0, 0, false,
                java.util.List.of(), "", null);
        var snapshot = new RemoteCatalogSnapshot(id, revision,
                java.util.Map.of("minecraft:overworld", java.util.Map.of("Public",
                        new RemoteListSnapshot("Public", revision,
                                java.util.Map.of("Home", waypoint)))), java.time.Instant.EPOCH);
        var codec = new ApplicationCodec(
                ProtocolLimits.DEFAULT);
        byte[] bytes = codec.encodeCatalog(snapshot);
        var request = java.util.UUID.randomUUID();
        index.receive(id, owner, new TcpChannel.Received(
                new ApplicationEnvelope(0, request,
                        new ApplicationMessage.CatalogMetadata(
                                id, "Survival", revision, CatalogExportPolicy.PUBLIC,
                                "minecraft:compass")), null));
        index.receive(id, owner, new TcpChannel.Received(
                new ApplicationEnvelope(1, request,
                        new ApplicationMessage.CatalogSnapshot(
                                id, revision, java.util.UUID.randomUUID(), 0, bytes.length,
                                new ApplicationMessage.Bytes(bytes))), snapshot));
        harness.server.setRemoteCatalogStore(new RemoteCatalogStore(index));
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<
                RemoteTeleportInitiator.Feedback>> callback = new java.util.concurrent.atomic.AtomicReference<>();
        harness.command.setRemoteTeleportInitiator((source, selection, feedback) -> callback.set(feedback));
        var executor = CommandHarness.player();
        harness.sender.commandSenders.put(executor, CommandHarness.console());
        harness.dispatcher.execute("wp remote tp survival minecraft:overworld Public Home", executor);
        harness.sender.commandSenders.clear();
        StaticLoggerBinder.clear();
        callback.get().accept(new RemoteTeleportInitiator.Feedback(
                ApplicationMessage.Result.UNAUTHORIZED, id,
                new TeleportPermissionCheck(false, true)));
        assertEquals(1, actions().size());
        String event = actions().get(0);
        assertTrue(event.contains("action=remote_tp"));
        assertTrue(event.contains("outcome=unauthorized"));
        assertTrue(event.contains("player=Alex player_id=00000000-0000-0000-0000-000000000000"));
        assertTrue(event.contains("sender=Server sender_id=non-player"));
        assertTrue(event.contains("Remote teleport failed: permission check failed in survival: tp ✘ · remote.tp ✔"));
    }

    private java.util.List<String> actions() {
        return StaticLoggerBinder.events().stream().filter(s -> s.startsWith("server_waypoint.actions ")).toList();
    }

}
