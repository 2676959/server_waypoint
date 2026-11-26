package _959.server_waypoint.perf.paper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;

/** Disposable-fixture trigger and deferred packet observations, never production logic. */
final class CommandTreeAssertions implements Listener {
    private final PaperPerformanceProbe plugin;
    private String role;
    private String connection;
    private UUID playerId;
    private boolean refreshed;
    private boolean armed;
    private final JsonArray ticks = new JsonArray();
    private final List<String> errors = new ArrayList<>();
    private final List<org.bukkit.permissions.PermissionAttachment> attachments = new ArrayList<>();

    CommandTreeAssertions(PaperPerformanceProbe plugin) {
        this.plugin = plugin;
    }

    void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "swperf:tree_refresh", (channel, player, bytes) -> {
            try {
                JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!armed || refreshed || bytes.length > 1024 || !connection.equals(value.get("connectionId").getAsString())
                        || value.get("sequence").getAsInt() != 2 || Bukkit.getOnlinePlayers().size() != 1) {
                    throw new IllegalStateException("unmatched or overlapping refresh trigger");
                }
                playerId = player.getUniqueId();
                refreshed = true;
                boolean grant = role.equals("ordinary");
                var attachment = player.addAttachment(plugin);
                attachments.add(attachment);
                attachment.setPermission("server_waypoint.command.tp", grant);
                player.recalculatePermissions();
                player.updateCommands();
            } catch (Exception failure) {
                errors.add(failure.toString());
            }
        });
    }

    private Object agent(String operation) throws Exception {
        Class<?> agent = Class.forName("_959.server_waypoint.perf.paper.CommandTreeHooks", true, null);
        return agent.getMethod(operation).invoke(null);
    }

    JsonObject arm(JsonObject request) throws Exception {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            throw new IllegalStateException("empty server required before command-tree arm");
        }
        role = request.get("role").getAsString();
        connection = request.get("connectionId").getAsString();
        if (!List.of("ordinary", "operator").contains(role) || connection.length() > 128 || connection.isEmpty()) {
            throw new IllegalArgumentException("invalid role/connection");
        }
        playerId = null;
        refreshed = false;
        ticks.asList().clear();
        errors.clear();
        agent("enable");
        armed = true;
        JsonObject result = new JsonObject();
        result.addProperty("role", role);
        result.addProperty("connectionId", connection);
        result.add("storage", StorageAssertions.snapshot());
        return result;
    }

    @EventHandler public void tick(ServerTickEndEvent event) {
        if (armed && ticks.size() < 10000) {
            JsonObject value = new JsonObject();
            value.addProperty("tick", event.getTickNumber());
            value.addProperty("durationNs", Math.round(event.getTickDuration() * 1_000_000));
            value.addProperty("clock", "server-monotonic");
            ticks.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    JsonObject snapshot() throws Exception {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) agent("snapshot");
        JsonArray samples = new JsonArray();
        int sequence = 0;
        for (Map<String, Object> value : raw) {
            ServerPlayer nativePlayer = (ServerPlayer) value.get("player");
            if (playerId != null && !playerId.equals(nativePlayer.getUUID())) {
                errors.add("send belongs to another connection");
            }
            JsonObject sample = new JsonObject();
            sample.addProperty("role", role);
            sample.addProperty("connectionId", connection);
            sample.addProperty("playerUuid", nativePlayer.getUUID().toString());
            sample.addProperty("sequence", ++sequence);
            sample.addProperty("trigger", sequence == 1 ? "login" : "refresh");
            sample.addProperty("clock", "server-monotonic");
            sample.addProperty("instrumented", !value.containsKey("error"));
            for (String field : List.of("dispatchStart", "dispatchEnd", "asyncStart", "asyncEnd", "syncStart", "handoff")) {
                if (!value.containsKey(field)) {
                    throw new IllegalStateException("missing native boundary: " + field);
                }
                sample.addProperty(field, (Long) value.get(field));
                sample.addProperty(field + "Thread", (String) value.get(field + "Thread"));
            }
            sample.addProperty("sendDurationNs", (Long) value.get("handoff") - (Long) value.get("dispatchStart"));
            sample.addProperty("dispatchNs", (Long) value.get("dispatchEnd") - (Long) value.get("dispatchStart"));
            sample.addProperty("asyncWorkNs", (Long) value.get("asyncEnd") - (Long) value.get("asyncStart"));
            sample.addProperty("syncWorkNs", (Long) value.get("handoff") - (Long) value.get("syncStart"));
            ClientboundCommandsPacket packet = (ClientboundCommandsPacket) value.get("packet");
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundCommandsPacket.STREAM_CODEC.encode(buffer, packet);
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.getBytes(0, bytes);
                CRC32 crc = new CRC32();
                crc.update(bytes);
                sample.addProperty("packetBodyBytes", bytes.length);
                sample.addProperty("packetCrc32", crc.getValue());
                var entries = ClientboundCommandsPacket.class.getDeclaredField("entries");
                entries.setAccessible(true);
                sample.addProperty("nodeCount", ((List<?>) entries.get(packet)).size());
                // Observe the selected view from each actual packet, not later mutable player permissions.
                var root = packet.getRoot(net.minecraft.commands.CommandBuildContext.simple(
                        ((CraftServer) Bukkit.getServer()).getServer().registryAccess(),
                        net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS), new ClientboundCommandsPacket.NodeBuilder<Object>() {
                    @Override public com.mojang.brigadier.builder.ArgumentBuilder<Object, ?> createLiteral(String name) {
                        return com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(name);
                    }
                    @Override public com.mojang.brigadier.builder.ArgumentBuilder<Object, ?> configure(
                            com.mojang.brigadier.builder.ArgumentBuilder<Object, ?> builder, boolean executable, boolean restricted) {
                        return executable ? builder.executes(context -> 0) : builder;
                    }
                    @Override public com.mojang.brigadier.builder.ArgumentBuilder<Object, ?> createArgument(String name,
                            com.mojang.brigadier.arguments.ArgumentType<?> type, net.minecraft.resources.Identifier suggestions) {
                        return com.mojang.brigadier.builder.RequiredArgumentBuilder.argument(name, type);
                    }
                });
                sample.addProperty("permission", root.getChild("wp") != null && root.getChild("wp").getChild("tp") != null);
            } finally {
                buffer.release();
            }
            samples.add(sample);
        }
        JsonObject result = new JsonObject();
        result.add("samples", samples);
        result.add("ticks", ticks.deepCopy());
        result.add("errors", new Gson().toJsonTree(errors));
        result.addProperty("refreshed", refreshed);
        return result;
    }
}
