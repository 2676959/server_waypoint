package _959.server_waypoint.crossserver;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.CommandPermission;
import _959.server_waypoint.crossserver.authorization.RemotePermissions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual checked-in adapters against minimal platform API doubles, without booting a game. */
class PlatformRemotePermissionContractTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"paper", "fabric", "forge", "neoforge"})
    @SuppressWarnings("unchecked")
    void adapterAssignmentsFallbackConsoleAndRevocation(String platform) throws Exception {
        Map<String, String> sources = new HashMap<>();
        sources.put("fixture.Subject", """
                package fixture;
                public class Subject {
                    public int level;
                    public boolean op;
                    public java.util.Map<String, Boolean> assignments = new java.util.HashMap<>();
                    public boolean isPermissionSet(String node) { return assignments.containsKey(node); }
                    public boolean hasPermission(String node) { return assignments.getOrDefault(node, false); }
                    public boolean isOp() { return op; }
                    public boolean hasPermission(int level) { return this.level >= level; }
                    public boolean hasPermissions(int level) { return this.level >= level; }
                    public net.minecraft.server.permissions.PermissionSet permissions() {
                        return new net.minecraft.server.permissions.LevelBasedPermissionSet(
                            net.minecraft.server.permissions.PermissionLevel.byId(level));
                    }
                }
                """);
        sources.put("net.minecraft.server.permissions.PermissionLevel", """
                package net.minecraft.server.permissions;
                public record PermissionLevel(int value) {
                    public static PermissionLevel byId(int value) { return new PermissionLevel(value); }
                    public boolean isEqualOrHigherThan(PermissionLevel other) { return value >= other.value; }
                }
                """);
        sources.put("net.minecraft.server.permissions.Permission", """
                package net.minecraft.server.permissions;
                public interface Permission { record HasCommandLevel(PermissionLevel level) implements Permission {} }
                """);
        sources.put("net.minecraft.server.permissions.PermissionSet", """
                package net.minecraft.server.permissions;
                public interface PermissionSet { boolean hasPermission(Permission permission); }
                """);
        sources.put("net.minecraft.server.permissions.LevelBasedPermissionSet", """
                package net.minecraft.server.permissions;
                public record LevelBasedPermissionSet(PermissionLevel level) implements PermissionSet {
                    public boolean hasPermission(Permission permission) {
                        return level.isEqualOrHigherThan(((Permission.HasCommandLevel) permission).level());
                    }
                }
                """);
        sources.put("net.minecraft.server.level.ServerPlayer",
                "package net.minecraft.server.level; public class ServerPlayer extends fixture.Subject {}");
        sources.put("net.minecraft.commands.CommandSourceStack", """
                package net.minecraft.commands;
                public class CommandSourceStack extends fixture.Subject {
                    public net.minecraft.server.level.ServerPlayer player;
                    public net.minecraft.server.level.ServerPlayer getPlayer() { return player; }
                }
                """);
        sources.put("me.lucko.fabric.api.permissions.v0.Permissions", """
                package me.lucko.fabric.api.permissions.v0;
                public class Permissions {
                    public static java.util.concurrent.CompletableFuture<Boolean> check(java.util.UUID id, String node, boolean fallback) {
                        return java.util.concurrent.CompletableFuture.completedFuture(fallback);
                    }
                    public static boolean check(fixture.Subject subject, String node,
                            net.minecraft.server.permissions.PermissionLevel level) {
                        return subject.assignments.getOrDefault(node, subject.level >= level.value());
                    }
                }
                """);
        sources.put("org.bukkit.entity.Player", "package org.bukkit.entity; public class Player extends fixture.Subject {}");
        sources.put("io.papermc.paper.command.brigadier.CommandSourceStack", """
                package io.papermc.paper.command.brigadier;
                public class CommandSourceStack {
                    public fixture.Subject sender = new fixture.Subject();
                    public fixture.Subject getSender() { return sender; }
                }
                """);
        String title = switch (platform) {
            case "paper" -> "Paper";
            case "fabric" -> "Fabric";
            case "forge" -> "Forge";
            default -> "NeoForge";
        };
        String packageName = platform.equals("paper") ? "_959.server_waypoint.server.command.permission"
                : "_959.server_waypoint." + platform + ".permission";
        String className = packageName + "." + title + "PermissionManager";
        Path repository = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(repository.resolve("common"))) repository = repository.getParent();
        Path adapter = repository.resolve(platform.equals("paper") ? "paper" : "mods")
                .resolve("src/main/java/" + className.replace('.', '/') + ".java");
        sources.put(className, Files.readString(adapter));
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-d", temporary.toString(),
                "-classpath", Path.of(PermissionManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()));
        for (var entry : sources.entrySet()) {
            Path source = temporary.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, entry.getValue());
            arguments.add(source.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> adapterClass = loader.loadClass(className);
            PermissionManager<Object, String, Object> manager = (PermissionManager<Object, String, Object>) adapterClass.getConstructor().newInstance();
            Object player = loader.loadClass(platform.equals("paper") ? "org.bukkit.entity.Player" : "net.minecraft.server.level.ServerPlayer")
                    .getConstructor().newInstance();
            Object source = loader.loadClass(platform.equals("paper") ? "io.papermc.paper.command.brigadier.CommandSourceStack"
                    : "net.minecraft.commands.CommandSourceStack").getConstructor().newInstance();
            Object console = source;
            Object subject = platform.equals("paper") ? source.getClass().getField("sender").get(source) : source;
            var permissions = new RemotePermissions<>(manager, CommandPermission::new, actual -> actual == player ? player : null);
            assertFalse(permissions.canRequestTeleport(console));
            assertEquals(!platform.equals("paper"), permissions.canList(source));
            assertFalse(permissions.canTeleportOnArrival(player));
            player.getClass().getField("level").setInt(player, 2);
            player.getClass().getField("op").setBoolean(player, true);
            assertTrue(permissions.canRequestTeleport(player));
            assertTrue(permissions.canTeleportOnArrival(player));
            subject.getClass().getField("level").setInt(subject, 4);
            subject.getClass().getField("op").setBoolean(subject, true);
            assertTrue(permissions.canList(console));
            if (platform.equals("fabric")) adapterClass.getMethod("setFabricPermissionAPILoaded", boolean.class).invoke(null, true);
            Map<String, Boolean> assignments = (Map<String, Boolean>) player.getClass().getField("assignments").get(player);
            if (platform.equals("paper") || platform.equals("fabric")) {
                assignments.put("server_waypoint.command.remote.tp", false);
                assertFalse(permissions.canRequestTeleport(player));
                assertTrue(permissions.canTeleportOnArrival(player));
                assignments.put("server_waypoint.command.remote.tp", true);
                assignments.put("server_waypoint.command.tp", false);
                assertFalse(permissions.canRequestTeleport(player));
                assertFalse(permissions.canTeleportOnArrival(player));
                player.getClass().getField("level").setInt(player, 0);
                player.getClass().getField("op").setBoolean(player, false);
                assignments.put("server_waypoint.command.tp", true);
                assertTrue(permissions.canRequestTeleport(player));
                Map<String, Boolean> sourceAssignments = (Map<String, Boolean>) subject.getClass().getField("assignments").get(subject);
                sourceAssignments.put("server_waypoint.command.remote.list", false);
                assertFalse(permissions.canList(console));
                sourceAssignments.put("server_waypoint.command.remote.list", true);
                assertTrue(permissions.canList(console));
            } else {
                // These adapters currently use vanilla levels; arbitrary node maps have no effect.
                assignments.put("server_waypoint.command.remote.tp", false);
                assertTrue(permissions.canRequestTeleport(player));
                player.getClass().getField("level").setInt(player, 0);
                assertFalse(permissions.canRequestTeleport(player));
                assertFalse(permissions.canTeleportOnArrival(player));
            }
        }
    }
}
