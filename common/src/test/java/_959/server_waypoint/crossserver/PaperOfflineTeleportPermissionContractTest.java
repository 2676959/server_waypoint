package _959.server_waypoint.crossserver;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import com.google.gson.Gson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionStage;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises destination preflight without starting a Paper server or a permission provider. */
class PaperOfflineTeleportPermissionContractTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void destinationUsesConfiguredLevelsWithAndWithoutLuckPerms(boolean luckPerms) throws Exception {
        String adapterName = "_959.server_waypoint.handoff.PaperOfflineTeleportPermission";
        Path repository = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(repository.resolve("paper"))) repository = repository.getParent();
        Map<String, String> sources = new HashMap<>();
        sources.put(adapterName, Files.readString(repository.resolve("paper/src/main/java/"
                + adapterName.replace('.', '/') + ".java")));
        sources.put("org.bukkit.plugin.java.JavaPlugin", """
                package org.bukkit.plugin.java;
                public class JavaPlugin { public boolean isEnabled() { return true; } }
                """);
        sources.put("org.bukkit.Bukkit", """
                package org.bukkit;
                public class Bukkit {
                    public static int level;
                    public static boolean luckPerms;
                    public static java.util.UUID player;
                    public static org.bukkit.craftbukkit.CraftServer getServer() {
                        return new org.bukkit.craftbukkit.CraftServer();
                    }
                    public static java.util.Set<OfflinePlayer> getOperators() {
                        return level > 0 ? java.util.Set.of(new OfflinePlayer(player)) : java.util.Set.of();
                    }
                    public record OfflinePlayer(java.util.UUID getUniqueId) {}
                    public static Scheduler getGlobalRegionScheduler() { return new Scheduler(); }
                    public static PluginManager getPluginManager() { return new PluginManager(); }
                    public static class Scheduler {
                        public void execute(org.bukkit.plugin.java.JavaPlugin plugin, Runnable task) { task.run(); }
                    }
                    public static class PluginManager {
                        public boolean isPluginEnabled(String name) { return luckPerms; }
                    }
                }
                """);
        sources.put("org.bukkit.craftbukkit.CraftServer", """
                package org.bukkit.craftbukkit;
                public class CraftServer {
                    public CraftServer getHandle() { return this; }
                    public net.minecraft.server.MinecraftServer getServer() {
                        return new net.minecraft.server.MinecraftServer();
                    }
                }
                """);
        sources.put("net.minecraft.server.players.NameAndId", """
                package net.minecraft.server.players;
                public record NameAndId(java.util.UUID id, String name) {}
                """);
        sources.put("net.minecraft.server.MinecraftServer", """
                package net.minecraft.server;
                public class MinecraftServer {
                    public Permissions getProfilePermissions(net.minecraft.server.players.NameAndId profile) {
                        if (!profile.id().equals(org.bukkit.Bukkit.player)) throw new AssertionError("Wrong player");
                        return new Permissions(org.bukkit.Bukkit.level);
                    }
                    public record Permissions(int value) { public Level level() { return new Level(value); } }
                    public record Level(int id) {}
                }
                """);
        sources.put("net.luckperms.api.util.Tristate", """
                package net.luckperms.api.util;
                public enum Tristate { TRUE, FALSE, UNDEFINED;
                    public boolean asBoolean() { return this == TRUE; }
                }
                """);
        sources.put("net.luckperms.api.LuckPermsProvider", """
                package net.luckperms.api;
                public class LuckPermsProvider { public static LuckPerms get() { return new LuckPerms(); } }
                """);
        sources.put("net.luckperms.api.LuckPerms", """
                package net.luckperms.api;
                public class LuckPerms {
                    public static String tp = "UNDEFINED", remoteTp = "UNDEFINED";
                    public UserManager getUserManager() { return new UserManager(); }
                    public ContextManager getContextManager() { return new ContextManager(); }
                    public static class UserManager {
                        public java.util.concurrent.CompletableFuture<User> loadUser(java.util.UUID id) {
                            if (!id.equals(org.bukkit.Bukkit.player)) throw new AssertionError("Wrong player");
                            return java.util.concurrent.CompletableFuture.completedFuture(new User());
                        }
                    }
                    public static class ContextManager { public Object getStaticQueryOptions() { return "static"; } }
                    public static class User { public Data getCachedData() { return new Data(); } }
                    public static class Data {
                        public Data getPermissionData(Object options) { return this; }
                        public net.luckperms.api.util.Tristate checkPermission(String node) {
                            String value = switch (node) {
                                case "server_waypoint.command.tp" -> tp;
                                case "server_waypoint.command.remote.tp" -> remoteTp;
                                default -> throw new AssertionError("Unexpected node: " + node);
                            };
                            return net.luckperms.api.util.Tristate.valueOf(value);
                        }
                    }
                }
                """);
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", temporary.toString(),
                "-classpath", Path.of(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        + java.io.File.pathSeparator
                        + Path.of(TeleportPermissionCheck.class.getProtectionDomain().getCodeSource().getLocation().toURI())));
        for (var entry : sources.entrySet()) {
            Path source = temporary.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, entry.getValue());
            arguments.add(source.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        Config original = WaypointServerCore.CONFIG;
        try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
            UUID player = new UUID(23, 47);
            Class<?> bukkit = loader.loadClass("org.bukkit.Bukkit");
            bukkit.getField("player").set(null, player);
            bukkit.getField("luckPerms").setBoolean(null, luckPerms);
            Class<?> pluginClass = loader.loadClass("org.bukkit.plugin.java.JavaPlugin");
            Object plugin = pluginClass.getConstructor().newInstance();
            var check = loader.loadClass(adapterName).getDeclaredMethod("check", pluginClass, UUID.class);
            check.setAccessible(true);
            java.util.function.Supplier<TeleportPermissionCheck> result = () -> {
                try { return ((CompletionStage<TeleportPermissionCheck>) check.invoke(null, plugin, player)).toCompletableFuture().join(); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            };
            WaypointServerCore.CONFIG = config(0, 0);
            assertEquals(new TeleportPermissionCheck(true, true), result.get());
            WaypointServerCore.CONFIG = config(1, 3);
            assertEquals(new TeleportPermissionCheck(false, false), result.get());
            bukkit.getField("level").setInt(null, 1);
            assertEquals(new TeleportPermissionCheck(true, false), result.get());
            bukkit.getField("level").setInt(null, 3);
            assertEquals(new TeleportPermissionCheck(true, true), result.get());
            if (luckPerms) {
                Class<?> provider = loader.loadClass("net.luckperms.api.LuckPerms");
                bukkit.getField("level").setInt(null, 0);
                provider.getField("remoteTp").set(null, "TRUE");
                WaypointServerCore.CONFIG = config(0, 0);
                assertEquals(new TeleportPermissionCheck(true, true), result.get());
                provider.getField("tp").set(null, "FALSE");
                assertEquals(new TeleportPermissionCheck(false, true), result.get());
                provider.getField("tp").set(null, "UNDEFINED");
                provider.getField("remoteTp").set(null, "FALSE");
                assertEquals(new TeleportPermissionCheck(true, false), result.get());
            }
        } finally {
            WaypointServerCore.CONFIG = original;
        }
    }

    private static Config config(int tp, int remoteTp) {
        return new Gson().fromJson("{\"CommandPermission\":{\"tp\":" + tp + ",\"remoteTp\":" + remoteTp + "}}", Config.class);
    }
}
