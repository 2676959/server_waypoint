package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.BroadcastFeedback;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.ChatIcons;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.Broadcasts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;

/** Queues shared waypoint updates and sends the matching chat line to each eligible viewer. */
public final class WaypointBroadcast<S, K, P> {
    private final PlatformMessageSender<S, P> sender;
    private final PermissionManager<S, K, P> permissions;

    public WaypointBroadcast(PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissions) {
        this.sender = sender;
        this.permissions = permissions;
    }

    void sendEditFromPlayer(P player, WaypointModificationMessage update, WaypointList list) {
        ChatIcons icons = this.sender.broadcastChatIcons();
        this.send(this.sender.getBroadcastPlayersFromPlayer(player), player, update,
                this.sender::isBroadcastOperator, () -> this.sender.broadcastDimensionTypes(player), icons, dims -> {
                    PlayerActionLog.Actor actor = this.sender.playerActor(player);
                    Component name = playerName(icons, actor.playerId(), Component.text(actor.name()));
                    return Broadcasts.updated(dims, name, update.dimensionName(), list, Objects.requireNonNull(update.waypoint()));
                });
    }

    public void send(Iterable<? extends P> recipients, @Nullable P actor, WaypointScopedMessage update,
                     Predicate<P> isOperator, Supplier<Map<String, String>> dimensionTypes,
                     ChatIcons icons, Function<DimensionStyle, Component> line) {
        this.sender.broadcastChunkedMessage(recipients, update);
        BroadcastFeedback feedback = CONFIG.Features().broadcastFeedback();
        if (feedback == BroadcastFeedback.OFF) {
            return;
        }

        Map<String, String> types = dimensionTypes.get();
        for (P player : recipients) {
            if (player.equals(actor)) {
                continue;
            }
            try {
                if (feedback == BroadcastFeedback.OPS && !isOperator.test(player)) {
                    continue;
                }
                this.sender.sendPlayerMessage(player, line.apply(DimensionStyle.local(
                        this.recipientViewer(player, update.scope(), icons), types)));
            } catch (RuntimeException exception) {
                PlayerActionLog.Actor recipient = this.sender.playerActor(player);
                WaypointServerCore.LOGGER.warn("Failed to send waypoint broadcast chat to {} ({})",
                        recipient.name(), recipient.playerId(), exception);
            }
        }
    }

    /** Waypoint chat links use the reader's teleport permission and have no distance context. */
    public Viewer recipientViewer(P player, WaypointScope scope, ChatIcons icons) {
        boolean privateScope = scope == WaypointScope.PRIVATE;
        boolean teleport = this.permissions.checkPlayerPermission(player,
                privateScope ? this.permissions.keys.privateTp() : this.permissions.keys.tp(),
                privateScope ? CONFIG.CommandPermission().privateTp() : CONFIG.CommandPermission().tp());
        return new Viewer(teleport ? Set.of(Viewer.Permission.TP) : Set.of(), false, false, null, null, 0F, icons, scope);
    }

    /** The space after a head belongs to the icon so text-only rendering drops them together. */
    public static Component playerName(ChatIcons icons, UUID playerId, Component name) {
        Component head = icons.playerHead(playerId);
        return head == null ? name : Component.empty().append(head.color(NamedTextColor.WHITE).appendSpace()).append(name);
    }
}
