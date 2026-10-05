package _959.server_waypoint.crossserver;

import _959.server_waypoint.command.permission.*;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the production offline adapter with an operator-list fixture, without booting Minecraft. */
class ModOfflineTeleportPermissionContractTest {
    @TempDir Path temporary;

    @Test void preparationRequiresBothNodesAndTheirSeparateOperatorFallbackLevels() throws Exception {
        String adapterName = "_959.server_waypoint.common.server.handoff.ModOfflineTeleportPermission";
        Path repository = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(repository.resolve("mods"))) repository = repository.getParent();
        Map<String, String> sources = new HashMap<>();
        sources.put(adapterName, Files.readString(repository.resolve("mods/src/main/java/"
                + adapterName.replace('.', '/') + ".java")));
        sources.put("net.minecraft.server.level.ServerPlayer", """
                package net.minecraft.server.level;
                public class ServerPlayer {}
                """);
        sources.put("net.minecraft.server.players.NameAndId", """
                package net.minecraft.server.players;
                public record NameAndId(java.util.UUID id, String name) {}
                """);
        sources.put("net.minecraft.server.MinecraftServer", """
                package net.minecraft.server;
                public class MinecraftServer {
                    public int level;
                    public boolean isStopped() { return false; }
                    public void execute(Runnable task) { task.run(); }
                    public PlayerList getPlayerList() { return new PlayerList(level); }
                    public record PlayerList(int level) {
                        public Ops getOps() { return new Ops(level); }
                    }
                    public record Ops(int level) {
                        public Entry get(net.minecraft.server.players.NameAndId identity) {
                            return level == 0 ? null : new Entry(level);
                        }
                    }
                    public record Entry(int value) {
                        public Permissions permissions() { return new Permissions(value); }
                    }
                    public record Permissions(int value) {
                        public Level level() { return new Level(value); }
                    }
                    public record Level(int id) {}
                }
                """);
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", temporary.toString(),
                "-classpath", Path.of(PermissionManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()));
        for (var source : sources.entrySet()) {
            Path path = temporary.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, source.getValue());
            arguments.add(path.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));

        UUID player = new UUID(23, 47);
        Map<String, Boolean> assignments = new HashMap<>();
        Map<String, CompletableFuture<Boolean>> pending = new HashMap<>();
        PermissionManager<Object, String, Object> permissions = new PermissionManager<>(new PermissionStringKeys()) {
            @Override public boolean hasPermission(Object source, PermissionKeys<String>.PermissionKey key, int level) {
                throw new AssertionError("Offline preparation must not use a command source");
            }
            @Override public boolean checkPlayerPermission(Object source, PermissionKeys<String>.PermissionKey key, int level) {
                throw new AssertionError("Offline preparation must not use a live player");
            }
            @Override public CompletionStage<Boolean> checkOfflinePermission(UUID id, PermissionKeys<String>.PermissionKey key, boolean fallback) {
                assertEquals(player, id);
                return pending.getOrDefault(key.getKey(), CompletableFuture.completedFuture(
                        assignments.getOrDefault(key.getKey(), fallback)));
            }
        };
        Config original = WaypointServerCore.CONFIG;
        try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
            WaypointServerCore.CONFIG = new Gson().fromJson("{\"CommandPermission\":{\"tp\":1,\"remoteTp\":3}}", Config.class);
            Class<?> serverClass = loader.loadClass("net.minecraft.server.MinecraftServer");
            Object server = serverClass.getConstructor().newInstance();
            Class<?> adapter = loader.loadClass(adapterName);
            var constructor = adapter.getDeclaredConstructor(serverClass, PermissionManager.class);
            constructor.setAccessible(true);
            Object lookup = constructor.newInstance(server, permissions);
            var check = adapter.getMethod("check", UUID.class);
            check.setAccessible(true);
            java.util.function.Supplier<CompletableFuture<?>> result = () -> {
                try { return ((CompletionStage<?>) check.invoke(lookup, player)).toCompletableFuture(); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            };
            serverClass.getField("level").setInt(server, 1);
            assertEquals(false, result.get().join());
            serverClass.getField("level").setInt(server, 3);
            assertEquals(true, result.get().join());
            assignments.put("server_waypoint.command.remote.tp", false);
            assertEquals(false, result.get().join());
            assignments.put("server_waypoint.command.remote.tp", true);
            assignments.put("server_waypoint.command.tp", false);
            assertEquals(false, result.get().join());
            assignments.put("server_waypoint.command.tp", true);
            serverClass.getField("level").setInt(server, 0);
            assertEquals(true, result.get().join());
            var remote = new CompletableFuture<Boolean>();
            pending.put("server_waypoint.command.remote.tp", remote);
            var waiting = result.get();
            assertFalse(waiting.isDone());
            remote.complete(false);
            assertEquals(false, waiting.join());
            pending.put("server_waypoint.command.remote.tp", CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
            assertThrows(CompletionException.class, () -> result.get().join());
        } finally {
            WaypointServerCore.CONFIG = original;
        }
    }
}
