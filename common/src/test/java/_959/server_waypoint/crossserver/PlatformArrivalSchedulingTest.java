package _959.server_waypoint.crossserver;

import _959.server_waypoint.crossserver.handoff.DestinationPlatform;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual adapter scheduling against API doubles: join precedes player lookup installation. */
class PlatformArrivalSchedulingTest {
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(strings = {"mods", "paper"})
    @SuppressWarnings("unchecked")
    void arrivalWaitsForRegistrationAndRechecksRetirement(String project) throws Exception {
        Map<String, String> sources = new HashMap<>();
        sources.put("fixture.State", """
                package fixture;
                public class State {
                    public static Object current;
                    public static boolean stopped;
                    public static java.util.Queue<Runnable> queue = new java.util.ArrayDeque<>();
                    public static void drain() { while (!queue.isEmpty()) queue.remove().run(); }
                }
                """);
        sources.put("net.minecraft.server.TickTask", """
                package net.minecraft.server;
                public record TickTask(int tick, Runnable task) implements Runnable {
                    public void run() { task.run(); }
                }
                """);
        sources.put("net.minecraft.server.MinecraftServer", """
                package net.minecraft.server;
                public class MinecraftServer {
                    public boolean isStopped() { return fixture.State.stopped; }
                    public boolean isSameThread() { return true; }
                    public int getTickCount() { return 1; }
                    public void execute(Runnable r) { r.run(); }
                    public void schedule(TickTask r) { fixture.State.queue.add(r); }
                    public MinecraftServer getPlayerList() { return this; }
                    public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id) {
                        return (net.minecraft.server.level.ServerPlayer) fixture.State.current;
                    }
                    public java.util.List<net.minecraft.server.level.ServerLevel> getAllLevels() { return java.util.List.of(); }
                }
                """);
        sources.put("net.minecraft.server.level.ServerLevel", """
                package net.minecraft.server.level;
                public class ServerLevel {
                    public ServerLevel dimension() { return this; }
                    public String identifier() { return "minecraft:overworld"; }
                }
                """);
        sources.put("net.minecraft.server.level.ServerPlayer", """
                package net.minecraft.server.level;
                public class ServerPlayer {
                    public java.util.UUID getUUID() { return new java.util.UUID(19, 1); }
                    public boolean hasDisconnected() { return false; }
                    public boolean teleportTo(ServerLevel l, double x, double y, double z,
                            java.util.Set<?> flags, float yaw, float pitch, boolean camera) { return true; }
                }
                """);
        sources.put("org.bukkit.Bukkit", """
                package org.bukkit;
                public class Bukkit { public static boolean isOwnedByCurrentRegion(Object p) { return true; } }
                """);
        sources.put("org.bukkit.NamespacedKey", """
                package org.bukkit;
                public class NamespacedKey { public static NamespacedKey fromString(String s) { return new NamespacedKey(); } }
                """);
        sources.put("org.bukkit.Location", """
                package org.bukkit;
                public record Location(Object world, double x, double y, double z, float yaw, float pitch) { }
                """);
        sources.put("org.bukkit.event.player.PlayerTeleportEvent", """
                package org.bukkit.event.player;
                public class PlayerTeleportEvent { public enum TeleportCause { PLUGIN } }
                """);
        sources.put("org.bukkit.plugin.java.JavaPlugin", """
                package org.bukkit.plugin.java;
                public class JavaPlugin {
                    public boolean isEnabled() { return !fixture.State.stopped; }
                    public JavaPlugin getServer() { return this; }
                    public org.bukkit.entity.Player getPlayer(java.util.UUID id) { return (org.bukkit.entity.Player) fixture.State.current; }
                    public Object getWorld(org.bukkit.NamespacedKey key) { return null; }
                }
                """);
        sources.put("org.bukkit.entity.Player", """
                package org.bukkit.entity;
                public class Player {
                    public java.util.UUID getUniqueId() { return new java.util.UUID(19, 1); }
                    public boolean isOnline() { return fixture.State.current == this; }
                    public Player getScheduler() { return this; }
                    public boolean execute(org.bukkit.plugin.java.JavaPlugin plugin, Runnable task, Runnable retired, long delay) {
                        if (delay < 1) throw new AssertionError("owner tick required");
                        fixture.State.queue.add(task); return true;
                    }
                    public java.util.concurrent.CompletionStage<Boolean> teleportAsync(org.bukkit.Location l,
                            org.bukkit.event.player.PlayerTeleportEvent.TeleportCause c) {
                        return java.util.concurrent.CompletableFuture.completedFuture(true);
                    }
                }
                """);
        String name = project.equals("mods") ? "_959.server_waypoint.common.server.handoff.ModDestinationPlatform"
                : "_959.server_waypoint.handoff.PaperDestinationPlatform";
        Path repository = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(repository.resolve("common"))) repository = repository.getParent();
        sources.put(name, Files.readString(repository.resolve(project + "/src/main/java/" + name.replace('.', '/') + ".java")));
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", temporary.toString(), "-classpath",
                Path.of(DestinationPlatform.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()));
        for (var entry : sources.entrySet()) {
            Path file = temporary.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent()); Files.writeString(file, entry.getValue()); arguments.add(file.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> state = loader.loadClass("fixture.State");
            Class<?> owner = loader.loadClass(project.equals("mods") ? "net.minecraft.server.MinecraftServer" : "org.bukkit.plugin.java.JavaPlugin");
            Class<?> playerClass = loader.loadClass(project.equals("mods") ? "net.minecraft.server.level.ServerPlayer" : "org.bukkit.entity.Player");
            Object player = playerClass.getConstructor().newInstance();
            var adapter = (DestinationPlatform<Object>) loader.loadClass(name).getConstructor(owner, Predicate.class)
                    .newInstance(owner.getConstructor().newInstance(), (Predicate<Object>) ignored -> true);
            int[] ran = {0}, retired = {0};
            Runnable action = () -> ran[0]++, retirement = () -> retired[0]++;
            assertTrue(adapter.execute(player, action, retirement));
            assertEquals(0, ran[0]); assertEquals(0, retired[0]);
            state.getField("current").set(null, player);
            state.getMethod("drain").invoke(null);
            assertEquals(1, ran[0]); assertEquals(0, retired[0]);
            assertTrue(adapter.execute(player, action, retirement));
            state.getField("current").set(null, playerClass.getConstructor().newInstance());
            state.getMethod("drain").invoke(null);
            assertEquals(1, ran[0]); assertEquals(1, retired[0]);
            state.getField("current").set(null, player);
            assertTrue(adapter.execute(player, action, retirement));
            state.getField("stopped").setBoolean(null, true);
            state.getMethod("drain").invoke(null);
            assertEquals(1, ran[0]); assertEquals(2, retired[0]);
            assertFalse(adapter.execute(player, action, retirement));
        }
    }
}
