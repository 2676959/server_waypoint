package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.util.NamespacedId;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A /wp command on a test platform. A player source is also its own player object; a plain-text
 * source reads its feedback like the console. Permissions are plain strings such as "add".
 */
final class CommandHarness {
    static final Set<String> EVERY_PERMISSION = Set.of("add", "edit", "remove", "tp", "navigate", "reload",
            "upload", "upload.delete", "remote.list", "remote.tp");

    record Source(String name, String dimension, WaypointPos position, float yaw, boolean player,
                  boolean plainText, Set<String> permissions) {
        Source withPermissions(String... permissions) {
            return new Source(this.name, this.dimension, this.position, this.yaw, this.player, this.plainText,
                    Set.of(permissions));
        }

        Source in(String dimension) {
            return new Source(this.name, dimension, this.position, this.yaw, this.player, this.plainText,
                    this.permissions);
        }

        /** The same player run from the console with /execute as: the console reads the feedback. */
        Source readByConsole() {
            return new Source(this.name, this.dimension, this.position, this.yaw, this.player, true,
                    this.permissions);
        }
    }

    record Teleport(Source player, String dimension, WaypointPos position, int yaw) {
    }

    static Source player() {
        return new Source("Alex", "minecraft:overworld", new WaypointPos(100, 64, -20), 37F, true, false,
                EVERY_PERMISSION);
    }

    static Source console() {
        return new Source("Server", "minecraft:overworld", new WaypointPos(0, 64, 0), 0F, false, true,
                EVERY_PERMISSION);
    }

    final WaypointServerCore server;
    final Sender sender = new Sender();
    final Map<String, String> dimensionTypes = new LinkedHashMap<>();
    final List<Teleport> teleports = new ArrayList<>();
    final TestCommand command;
    final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();

    CommandHarness(Path directory) {
        this.server = new WaypointServerCore(directory) {
            @Override
            protected boolean isRegisteredIconItem(NamespacedId icon) {
                return icon.toString().equals("minecraft:diamond");
            }
        };
        this.dimensionTypes.put("minecraft:overworld", "minecraft:overworld");
        this.dimensionTypes.put("minecraft:the_nether", "minecraft:the_nether");
        this.dimensionTypes.put("minecraft:the_end", "minecraft:the_end");
        this.command = new TestCommand(this);
        this.command.register(this.dispatcher);
    }

    /** Runs the command and returns the last message it sent back to the source. */
    Component run(Source source, String command) {
        int before = this.sender.received.size();
        try {
            this.dispatcher.execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new AssertionError(command, exception);
        }
        if (this.sender.received.size() == before) {
            throw new AssertionError("No feedback for " + command);
        }
        return this.sender.received.get(this.sender.received.size() - 1);
    }

    /** The command does not parse for this source. */
    void fails(Source source, String command) {
        assertThrows(CommandSyntaxException.class, () -> this.dispatcher.execute(command, source), command);
    }

    void addList(String dimension, String list, SimpleWaypoint... waypoints) {
        this.server.putWaypointList(dimension, new WaypointList(list, 1, List.of(waypoints)));
    }

    static SimpleWaypoint waypoint(String name, String initials, int rgb, int x, int y, int z) {
        return new SimpleWaypoint(name, initials, new WaypointPos(x, y, z), rgb, 0, true);
    }

    static final class Sender implements PlatformMessageSender<Source, Object> {
        final List<Component> received = new ArrayList<>();
        final List<Component> errors = new ArrayList<>();
        final List<Map.Entry<Object, Component>> toPlayers = new ArrayList<>();
        final List<Object> online = new ArrayList<>();
        boolean handshake = true;

        @Override
        public void sendMessage(Source source, Component component) {
            this.received.add(component);
        }

        @Override
        public void sendPlayerMessage(Object player, Component component) {
            this.toPlayers.add(Map.entry(player, component));
        }

        @Override
        public void sendError(Source source, Component component) {
            this.received.add(component);
            this.errors.add(component);
        }

        @Override
        public boolean isPlainTextReceiver(Source source) {
            return source.plainText();
        }

        @Override
        public void sendPacket(Source source, SinglePacketMessage message) {
        }

        @Override
        public void sendPlayerPacket(Object player, SinglePacketMessage message) {
        }

        @Override
        public void broadcastPacket(SinglePacketMessage message) {
        }

        @Override
        public ChunkedMessageDelivery sendChunkedMessage(Source source, ChunkedMessage message) {
            return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
        }

        @Override
        public Iterable<?> getBroadcastPlayers(Source source) {
            return this.online;
        }

        @Override
        public Component getSenderName(Source source) {
            return Component.text(source.name());
        }

        @Override
        public boolean canSendChunkedMessage(Object player) {
            return this.handshake;
        }
    }

    static final class TestCommand extends CoreWaypointCommand<Source, String, Object, String, String, NamespacedId> {
        private final CommandHarness harness;

        private TestCommand(CommandHarness harness) {
            super(harness.server, harness.sender, permissions(), navigation(),
                    new UploadCoordinator<>(harness.server, (player, message) -> {
                    }, packet -> {
                    }, player -> true, player -> true, navigation(), player -> new UUID(0L, 0L)),
                    () -> word(), CommandHarness::position, () -> reader -> NamespacedId.parse(readWord(reader)));
            this.harness = harness;
        }

        private static ArgumentType<String> word() {
            return CommandHarness::readWord;
        }

        @Override
        protected String toDimensionName(String dimensionArgument) {
            return dimensionArgument;
        }

        @Override
        protected NamespacedId toIconId(NamespacedId iconArgument) {
            return iconArgument;
        }

        @Override
        protected CompletableFuture<Suggestions> suggestIconIds(CommandContext<Source> context, SuggestionsBuilder builder) {
            return builder.buildFuture();
        }

        @Override
        protected WaypointPos toWaypointPos(Source source, String position) {
            String[] parts = position.split(" ");
            return new WaypointPos(coordinate(parts[0], source.position().x()),
                    coordinate(parts[1], source.position().y()), coordinate(parts[2], source.position().z()));
        }

        private static int coordinate(String part, int origin) {
            if (part.startsWith("~")) {
                return origin + (part.length() == 1 ? 0 : Integer.parseInt(part.substring(1)));
            }
            return Integer.parseInt(part);
        }

        @Override
        protected boolean isDimensionValid(Source source, String dimensionArgument) {
            return this.harness.dimensionTypes.containsKey(dimensionArgument);
        }

        @Override
        protected void executeByServer(Source source, Runnable task) {
            task.run();
        }

        /** Like Paper's help-map source, a source without a dimension has no level to read. */
        @Override
        protected String getSourceDimension(Source source) {
            return java.util.Objects.requireNonNull(source.dimension(), "the source has no level");
        }

        @Override
        protected WaypointPos getSourcePosition(Source source) {
            return java.util.Objects.requireNonNull(source.position(), "the source has no level");
        }

        @Override
        protected float getSourceYaw(Source source) {
            return source.yaw();
        }

        @Override
        protected Object getPlayer(Source source) {
            return source.player() ? source : null;
        }

        @Override
        protected boolean isServerConsoleWithHighestPermission(Source source) {
            return !source.player();
        }

        @Override
        protected String getPlayerName(Object player) {
            return ((Source) player).name();
        }

        @Override
        protected void teleportPlayer(Source source, Object player, String dimension, WaypointPos pos, int yaw) {
            this.harness.teleports.add(new Teleport((Source) player, dimension, pos, yaw));
        }

        @Override
        protected Message getMessageFromComponent(Component component) {
            return component::toString;
        }

        @Override
        protected List<String> getAvailableDimensionNames(Source source) {
            return List.copyOf(this.harness.dimensionTypes.keySet());
        }

        @Override
        protected Map<String, String> getDimensionTypes(Source source) {
            return Map.copyOf(this.harness.dimensionTypes);
        }
    }

    /** Three coordinates such as "~ ~ ~" or "1 64 -2", like a block position argument. */
    private static ArgumentType<String> position() {
        return reader -> {
            int start = reader.getCursor();
            for (int part = 0; part < 3; part++) {
                if (part > 0) {
                    reader.expect(' ');
                }
                if (!readWord(reader).matches("~(-?\\d+)?|-?\\d+")) {
                    throw new SimpleCommandExceptionType(() -> "Expected a coordinate").createWithContext(reader);
                }
            }
            return reader.getString().substring(start, reader.getCursor());
        };
    }

    private static String readWord(com.mojang.brigadier.StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static PermissionManager<Source, String, Object> permissions() {
        PermissionKeys<String> keys = new PermissionKeys<>() {
            @Override
            protected PermissionKey createAddPermissionKey() {
                return new PermissionKey("add");
            }

            @Override
            protected PermissionKey createEditPermissionKey() {
                return new PermissionKey("edit");
            }

            @Override
            protected PermissionKey createRemovePermissionKey() {
                return new PermissionKey("remove");
            }

            @Override
            protected PermissionKey createNavigatePermissionKey() {
                return new PermissionKey("navigate");
            }

            @Override
            protected PermissionKey createTpPermissionKey() {
                return new PermissionKey("tp");
            }

            @Override
            protected PermissionKey createReloadPermissionKey() {
                return new PermissionKey("reload");
            }

            @Override
            protected PermissionKey createUploadPermissionKey() {
                return new PermissionKey("upload");
            }

            @Override
            protected PermissionKey createUploadDeletePermissionKey() {
                return new PermissionKey("upload.delete");
            }

            @Override
            protected PermissionKey createRemoteListPermissionKey() {
                return new PermissionKey("remote.list");
            }

            @Override
            protected PermissionKey createRemoteTpPermissionKey() {
                return new PermissionKey("remote.tp");
            }
        };
        return new PermissionManager<>(keys) {
            @Override
            public boolean hasPermission(Source source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
                return source.permissions().contains(key.getKey());
            }

            @Override
            public boolean checkPlayerPermission(Object player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
                return ((Source) player).permissions().contains(key.getKey());
            }
        };
    }

    private static NavigationService<Object> navigation() {
        return new NavigationService<>(new NavigationPlatform<>() {
            @Override
            public UUID playerUuid(Object player) {
                return new UUID(0L, 0L);
            }

            @Override
            public void executePlayer(UUID playerUuid, Consumer<Object> action) {
            }

            @Override
            public NavigationSnapshot snapshot(Object player, NavigationTarget target) {
                return NavigationSnapshot.wrongDimension();
            }
        }, List.of());
    }
}
