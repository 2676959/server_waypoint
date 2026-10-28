package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.command.permission.PermissionStringKeys;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.*;
import _959.server_waypoint.util.NamespacedId;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

/** Measures the actual CoreWaypointCommand registration with an empty catalog and all navigation methods. */
public final class PrivateCommandTreeProbe {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path directory = Files.createTempDirectory("private-command-tree-probe-");
        try {
            var server = new WaypointServerCore(directory) {};
            var navigation = navigation();
            var command = new ProbeCommand(server, sender(), navigation);
            var dispatcher = new com.mojang.brigadier.CommandDispatcher<Object>();
            command.register(dispatcher);
            var root = dispatcher.getRoot();
            root.addChild(literal("waypoint").redirect(root.getChild("wp")).build());
            root.addChild(literal("run").redirect(root).build());
            CommandTreeProbe.measure(root, true);
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static NavigationService<Object> navigation() {
        NavigationPlatform<Object> platform = new NavigationPlatform<>() {
            @Override public UUID playerUuid(Object player) { return new UUID(0, 0); }
            @Override public void executePlayer(UUID playerId, java.util.function.Consumer<Object> task) {}
            @Override public NavigationSnapshot snapshot(Object player, NavigationTarget target) {
                return NavigationSnapshot.wrongDimension();
            }
        };
        List<NavigationMethodHandler<Object>> handlers = NavigationMethod.definedMethods().stream()
                .map(method -> (NavigationMethodHandler<Object>) new NavigationMethodHandler<Object>() {
                    @Override public NavigationMethod method() { return method; }
                    @Override public NavigationResult enable(Object player, NavigationSession session, NavigationSnapshot snapshot) {
                        return NavigationResult.success();
                    }
                    @Override public void update(Object player, NavigationSession session, NavigationSnapshot snapshot) {}
                    @Override public void disable(Object player, NavigationSession session) {}
                }).toList();
        return new NavigationService<>(platform, handlers);
    }

    @SuppressWarnings("unchecked")
    private static PlatformMessageSender<Object, Object> sender() {
        // Copy/packet measurement never sends feedback. Fail if a future requirement starts doing so.
        return (PlatformMessageSender<Object, Object>) Proxy.newProxyInstance(
                PrivateCommandTreeProbe.class.getClassLoader(), new Class<?>[]{PlatformMessageSender.class},
                (proxy, method, args) -> { throw new AssertionError("Unexpected sender access: " + method); });
    }

    private static PermissionManager<Object, String, Object> permissions() {
        return new PermissionManager<>(new PermissionStringKeys()) {
            @Override public boolean hasPermission(Object source, PermissionKeys<String>.PermissionKey key, int level) {
                return (Boolean) source;
            }
            @Override public boolean checkPlayerPermission(Object player, PermissionKeys<String>.PermissionKey key, int level) {
                return (Boolean) player;
            }
        };
    }

    /** Uses serializable native Brigadier arguments; models and execution are outside this probe. */
    private static final class ProbeCommand extends CoreWaypointCommand<Object, String, Object, String, String, String> {
        private ProbeCommand(WaypointServerCore server, PlatformMessageSender<Object, Object> sender,
                             NavigationService<Object> navigation) {
            super(server, sender, permissions(), navigation,
                    new UploadCoordinator<>(server, (player, message) -> {}, data -> {}, player -> true, player -> true,
                            navigation, player -> new PlayerActionLog.Actor(new UUID(0, 0), "probe")),
                    StringArgumentType::string, StringArgumentType::string, StringArgumentType::string);
        }

        @Override protected String toDimensionName(String dimension) { return dimension; }
        @Override protected NamespacedId toIconId(String icon) { return NamespacedId.parse(icon); }
        @Override protected CompletableFuture<Suggestions> suggestIconIds(CommandContext<Object> context, SuggestionsBuilder builder) {
            return builder.buildFuture();
        }
        @Override protected WaypointPos toWaypointPos(Object source, String position) { return new WaypointPos(0, 64, 0); }
        @Override protected boolean isDimensionValid(Object source, String dimension) { return true; }
        @Override protected void executeByServer(Object source, Runnable task) { task.run(); }
        @Override protected String getSourceDimension(Object source) { return "minecraft:overworld"; }
        @Override protected WaypointPos getSourcePosition(Object source) { return new WaypointPos(0, 64, 0); }
        @Override protected float getSourceYaw(Object source) { return 0; }
        @Override protected Object getPlayer(Object source) { return (Boolean) source ? source : null; }
        @Override protected boolean isServerConsoleWithHighestPermission(Object source) { return false; }
        @Override protected String getPlayerName(Object player) { return "probe"; }
        @Override protected Component getPlayerDisplayName(Object player) { return Component.text("probe"); }
        @Override protected CompletionStage<Boolean> teleportPlayer(Object source, Object player, String dimension,
                                                                   WaypointPos position, int yaw) {
            return CompletableFuture.completedFuture(true);
        }
        @Override protected Message getMessageFromComponent(Component component) { return component::toString; }
        @Override protected List<String> getAvailableDimensionNames(Object source) { return List.of("minecraft:overworld"); }
        @Override protected Map<String, String> getDimensionTypes(Object source) {
            return Map.of("minecraft:overworld", "minecraft:overworld");
        }
    }
}
