package _959.server_waypoint.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/** Isolated Minecraft 26.3 command-copy and packet probe; never starts a server. */
public final class CommandTreeProbe {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var list = literal("list").requires(source -> (Boolean) source);
        configure(list);
        var all = literal("all");
        configure(all);
        var dimension = argument("dimension", string());
        configure(dimension);
        var localList = argument("list identifier", string());
        configure(localList);
        list.then(all).then(dimension.then(localList));

        var remote = literal("remote").requires(source -> (Boolean) source);
        var remoteList = literal("list");
        configure(remoteList);
        var server = argument("remote server", string());
        configure(server);
        var remoteDimension = argument("remote dimension", string());
        configure(remoteDimension);
        var remoteIdentity = argument("remote list", string());
        configure(remoteIdentity);
        remote.then(remoteList.then(server.then(remoteDimension.then(remoteIdentity))));
        var root = new RootCommandNode<Object>();
        var wp = literal("wp").then(list).then(remote).build();
        root.addChild(wp);
        root.addChild(literal("waypoint").redirect(wp).build());

        var fill = Commands.class.getDeclaredMethod("fillUsableCommands",
                CommandNode.class, CommandNode.class, Object.class, java.util.Map.class);
        fill.setAccessible(true);
        var inspector = new ClientboundCommandsPacket.NodeInspector<Object>() {
            public boolean isExecutable(CommandNode<Object> node) { return node.getCommand() != null; }
            public boolean isRestricted(CommandNode<Object> node) { return false; }
            public Identifier suggestionId(ArgumentCommandNode<Object, ?> node) {
                return node.getCustomSuggestions() == null ? null : Identifier.withDefaultNamespace("ask_server");
            }
        };
        var nodeBuilder = new ClientboundCommandsPacket.NodeBuilder<Object>() {
            public ArgumentBuilder<Object, ?> createLiteral(String name) { return literal(name); }
            public ArgumentBuilder<Object, ?> createArgument(String name,
                    com.mojang.brigadier.arguments.ArgumentType<?> type, Identifier suggestions) {
                return argument(name, type);
            }
            public ArgumentBuilder<Object, ?> configure(ArgumentBuilder<Object, ?> builder, boolean executable, boolean restricted) {
                return executable ? builder.executes(context -> 1) : builder;
            }
        };
        var denied = new RootCommandNode<Object>();
        fill.invoke(null, root, denied, false, new HashMap<CommandNode<Object>, CommandNode<Object>>());
        if (!denied.getChild("wp").getChildren().isEmpty()) throw new AssertionError("Permission filter failed");

        for (int sample = -3; sample < 7; sample++) {
            var copied = new RootCommandNode<Object>();
            var converted = new HashMap<CommandNode<Object>, CommandNode<Object>>();
            converted.put(root, copied);
            long begin = System.nanoTime();
            fill.invoke(null, root, copied, true, converted);
            long copyEnd = System.nanoTime();
            var packet = new ClientboundCommandsPacket(copied, inspector);
            long packetEnd = System.nanoTime();
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundCommandsPacket.STREAM_CODEC.encode(buffer, packet);
                int bytes = buffer.readableBytes();
                var decoded = ClientboundCommandsPacket.STREAM_CODEC.decode(buffer);
                var restored = decoded.getRoot(CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS), nodeBuilder);
                if (restored.getChild("wp").getChild("remote") == null
                        || restored.getChild("waypoint").getRedirect() != restored.getChild("wp") || buffer.isReadable()) {
                    throw new AssertionError("Packet round trip failed");
                }
                if (sample >= 0) {
                    System.out.printf(java.util.Locale.ROOT,
                            "ISSUE35 sample=%d serverNodes=%d copiedNodes=%d packetNodes=%d bytes=%d copyMs=%.3f packetMs=%.3f%n",
                            sample, count(root), count(copied), count(restored), bytes,
                            (copyEnd - begin) / 1_000_000.0, (packetEnd - copyEnd) / 1_000_000.0);
                }
            } finally {
                buffer.release();
            }
        }
    }

    private static void configure(ArgumentBuilder<Object, ?> node) {
        new ListCommandOptions<Object>((mode, reversed, view) -> context ->
                mode.ordinal() + view.ordinal() + (reversed ? 1 : 0), null).configure(node);
    }

    private static int count(CommandNode<?> root) {
        Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        visit(root, seen);
        return seen.size();
    }

    private static void visit(CommandNode<?> node, Set<CommandNode<?>> seen) {
        if (seen.add(node)) node.getChildren().forEach(child -> visit(child, seen));
    }
}
