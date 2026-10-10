package _959.server_waypoint.common.network;

import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.SinglePacketMessageEncoder;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.server.command.CommandChatIcons;
import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.common.util.PlayerLocaleHelper;
import _959.server_waypoint.mixin.CommandSourceStackAccessor;
import _959.server_waypoint.text.chat.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;


import static _959.server_waypoint.common.network.MessagePayloadMapping.getPayload;
//? if >= 1.20.3 {
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.ComponentSerialization;
//?}
//? if fabric {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif forge {
/*import _959.server_waypoint.forge.ServerWaypointForge;
import net.minecraftforge.network.PacketDistributor;
*///?} elif neoforge {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}
//? if neoforge && = 1.20.2
/*import _959.server_waypoint.neoforge.ServerWaypointNeoForge;*/

public class ModMessageSender implements PlatformMessageSender<CommandSourceStack, ServerPlayer> {
    private static final ModMessageSender INSTANCE = new ModMessageSender();
    private final Set<UUID> chunkedMessageCapablePlayers = ConcurrentHashMap.newKeySet();

    public static ModMessageSender getInstance() {
        return INSTANCE;
    }

    public static net.minecraft.network.chat.Component toVanillaText(Component component) {
        //? if >= 1.20.3 {
        var result = ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, TextHelper.JSON.serializeToTree(component)).result();
        if (result.isPresent()) {
            return result.get().getFirst();
        } else {
            return net.minecraft.network.chat.Component.literal("failed to decode message component");
        }
        //?} else {
        /*return net.minecraft.network.chat.Component.Serializer.fromJson(TextHelper.JSON.serializeToTree(component));
        *///?}
    }

    /**
     * The player whose view the feedback is: the player a command runs as, such as /execute as a
     * player, else the player who owns the stack's command source. Null for the console, RCON and
     * command blocks, which read plain text.
     */
    @Nullable
    private static ServerPlayer getViewingPlayer(CommandSourceStack source) {
        ServerPlayer executor = source.getPlayer();
        return executor != null ? executor : getReceivingPlayer(source);
    }

    @Override
    public boolean isPlainTextReceiver(CommandSourceStack source) {
        return getViewingPlayer(source) == null;
    }

    /**
     * The stack of the player the feedback is viewed as when a commander ran it for them, so the
     * view shows what that player may do; otherwise the stack itself.
     */
    @Override
    public CommandSourceStack viewingSource(CommandSourceStack source) {
        ServerPlayer viewer = getViewingPlayer(source);
        return viewer == null || viewer == getReceivingPlayer(source) ? source : viewer.createCommandSourceStack();
    }

    /** The player who owns this stack's command source, or null for the console, RCON and command blocks. */
    @Nullable
    private static ServerPlayer getReceivingPlayer(CommandSourceStack source) {
        CommandSource receiver = ((CommandSourceStackAccessor) source).serverWaypoint$getSource();
        //? if >= 1.21.2 {
        // Since 1.21.2 a player holds a separate command source instead of being one; vanilla's admin
        // broadcast finds the player the same way.
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.commandSource() == receiver) {
                return player;
            }
        }
        return null;
        //?} else {
        /*return receiver instanceof ServerPlayer player ? player : null;
        *///?}
    }

    public net.minecraft.network.chat.Component getTranslatedText(ServerPlayer player, Component component) {
        return toVanillaText(GlobalTranslator.render(component, PlayerLocaleHelper.forPlayer(player)));
    }

    /**
     * The feedback goes to the player whose view it is, in their language. When a commander ran it
     * for that player with /execute as, the commander also gets the view, under a "Viewed as" line.
     * Vanilla's {@code CommandSourceStack.sendSystemMessage} would pick the player a command runs as
     * on its own; this decides the receivers itself so that the view and its receivers always agree.
     */
    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        CommandSourceStackAccessor accessor = (CommandSourceStackAccessor) source;
        if (accessor.serverWaypoint$isSilent()) {
            // Vanilla's sendSystemMessage drops the output of a suppressed stack the same way.
            return;
        }
        CommandSource commander = accessor.serverWaypoint$getSource();
        ServerPlayer viewer = getViewingPlayer(source);
        if (viewer == null) {
            commander.sendSystemMessage(toVanillaText(GlobalTranslator.render(component, Locale.getDefault())));
            return;
        }
        viewer.sendSystemMessage(getTranslatedText(viewer, PlatformMessageSender.forPlayer(component)));
        ServerPlayer commanderPlayer = getReceivingPlayer(source);
        if (commanderPlayer != viewer) {
            commander.sendSystemMessage(getViewedAsText(viewer, commanderPlayer, component));
        }
    }

    /**
     * The commander's copy of a player's view: a "Viewed as" line in the commander's language, then
     * the feedback in the player's. A player commander gets a screen's trailing newline, as the
     * player does; the console, RCON and command blocks read plain text without it or chat objects.
     */
    private net.minecraft.network.chat.Component getViewedAsText(
            ServerPlayer viewer,
            @Nullable ServerPlayer commanderPlayer,
            Component component
    ) {
        Locale commanderLocale = commanderPlayer == null ? Locale.getDefault() : PlayerLocaleHelper.forPlayer(commanderPlayer);
        Component line = GlobalTranslator.render(Chat.viewedAs(viewer.getName().getString()), commanderLocale);
        Component view = GlobalTranslator.render(
                commanderPlayer == null ? component : PlatformMessageSender.forPlayer(component),
                PlayerLocaleHelper.forPlayer(viewer)
        );
        return toVanillaText(PlatformMessageSender.forCommander(line,
                commanderPlayer == null ? CommandChatIcons.INSTANCE.withoutIcons(view) : view));
    }

    @Override
    public void sendPlayerMessage(ServerPlayer player, Component component) {
        player.sendSystemMessage(getTranslatedText(player, PlatformMessageSender.forPlayer(component)));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        this.sendMessage(source, component);
    }

    @Override
    public Collection<ServerPlayer> getBroadcastPlayers(CommandSourceStack source) {
        return source.getServer().getPlayerList().getPlayers();
    }

    @Override
    public Collection<ServerPlayer> getBroadcastPlayersFromPlayer(ServerPlayer player) {
        return WaypointServerMod.MINECRAFT_SERVER == null
                ? java.util.List.of(player)
                : WaypointServerMod.MINECRAFT_SERVER.getPlayerList().getPlayers();
    }

    @Override
    public PlayerActionLog.Actor playerActor(ServerPlayer player) {
        return new PlayerActionLog.Actor(player.getUUID(), player.getName().getString());
    }

    @Override
    public PlayerActionLog.Actor commandSenderActor(CommandSourceStack source) {
        ServerPlayer player = getReceivingPlayer(source);
        if (player != null) {
            return this.playerActor(player);
        }
        CommandSource receiver = ((CommandSourceStackAccessor) source).serverWaypoint$getSource();
        String name;
        if (receiver == source.getServer()) {
            name = "Console";
        } else if (receiver instanceof net.minecraft.server.rcon.RconConsoleSource) {
            name = "RCON";
        } else if (receiver == CommandSource.NULL) {
            name = "Function";
        } else {
            // Custom and command-block sources may not expose a name. Preserve the actual
            // source instance rather than using the executor's overwritten stack name.
            name = receiver.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(receiver));
        }
        return new PlayerActionLog.Actor(null, name);
    }

    @Override
    public Component getSenderName(CommandSourceStack source) {
        return Component.text(source.getTextName());
    }

    @Override
    public void broadcastPacket(SinglePacketMessage message) {
        if (WaypointServerMod.MINECRAFT_SERVER != null) {
            WaypointServerMod.MINECRAFT_SERVER.getPlayerList().getPlayers()
                    .forEach(player -> sendPlayerPacket(player, message));
        }
    }

    @Override
    public void broadcastChunkedMessage(ChunkedMessage message) {
        if (WaypointServerMod.MINECRAFT_SERVER != null) {
            this.broadcastChunkedMessage(
                    WaypointServerMod.MINECRAFT_SERVER.getPlayerList().getPlayers(),
                    message
            );
        }
    }

    @Override
    public void sendPlayerPacket(ServerPlayer player, SinglePacketMessage message) {
        this.sendPlayerPacketTracked(player, message);
    }

    @Override
    public CompletionStage<ChunkedMessageSendResult> sendPlayerPacketTracked(
            ServerPlayer player,
            SinglePacketMessage message
    ) {
        try {
            byte[] encodedMessage = SinglePacketMessageEncoder.encode(message);
        //? if fabric {
        ServerPlayNetworking.send(player, getPayload(message, encodedMessage));
        //?} elif forge {
        /*//? if <= 1.20.1 {
        /^ServerWaypointForge.PACKET_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), getPayload(message, encodedMessage));
        ^///?} else {
        ServerWaypointForge.PACKET_CHANNEL.send(getPayload(message, encodedMessage), PacketDistributor.PLAYER.with(player));
        //?}
        *///?} elif neoforge && = 1.20.2 {
        /*ServerWaypointNeoForge.PACKET_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), getPayload(message, encodedMessage));
        *///?} elif neoforge && = 1.20.4 {
        /*PacketDistributor.PLAYER.with(player).send(getPayload(message, encodedMessage));
        *///?} else {
        /*PacketDistributor.sendToPlayer(player, getPayload(message, encodedMessage));
         *///?}
            return CompletableFuture.completedFuture(ChunkedMessageSendResult.DELIVERED);
        } catch (MessageEncodingException exception) {
            WaypointServerMod.LOGGER.warn(
                    "Failed to encode single-packet message type {} within the {}-byte packet budget",
                    message.getClass().getSimpleName(),
                    SinglePacketMessageEncoder.MAX_ENCODED_BYTES,
                    exception
            );
            return CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.ENCODING_FAILED
            );
        } catch (RuntimeException exception) {
            WaypointServerMod.LOGGER.warn(
                    "Failed to deliver single-packet message type {}",
                    message.getClass().getSimpleName(),
                    exception
            );
            return CompletableFuture.completedFuture(
                    ChunkedMessageSendResult.DELIVERY_FAILED
            );
        }
    }

    @Override
    public void sendPacket(CommandSourceStack source, SinglePacketMessage message) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            sendPlayerPacket(player, message);
        }
    }

    @Override
    public ChunkedMessageDelivery sendChunkedMessage(
            CommandSourceStack source,
            ChunkedMessage message
    ) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return this.sendPlayerChunkedMessageTracked(player, message);
        }
        return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
    }

    @Override
    public void setChunkedMessageCapable(ServerPlayer player, boolean capable) {
        UUID playerId = player.getUUID();
        if (capable) {
            this.chunkedMessageCapablePlayers.add(playerId);
        } else {
            this.chunkedMessageCapablePlayers.remove(playerId);
            PlatformMessageSender.super.disconnectChunkedMessages(player);
        }
    }

    @Override
    public boolean canSendChunkedMessage(ServerPlayer player) {
        return this.chunkedMessageCapablePlayers.contains(player.getUUID());
    }

    @Override
    public void disconnectChunkedMessages(ServerPlayer player) {
        this.setChunkedMessageCapable(player, false);
    }
}
