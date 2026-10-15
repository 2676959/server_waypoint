package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.TeleportPermissionCheck;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Backend adapter. No method may block waiting for a server thread or an asynchronous teleport. */
public interface DestinationPlatform<P> {
    /** Resolve the destination permission for an absent player before admitting a transfer. Fail closed. */
    CompletionStage<TeleportPermissionCheck> canPrepare(UUID playerId);
    /** Queue once on the player's owner. Return false on rejection; call retired if the owner disappears. */
    boolean execute(P player, Runnable task, Runnable retired);
    boolean ownsThread(P player);
    /** These reads and teleport are invoked only after ownsThread succeeds. */
    UUID playerId(P player);
    boolean isCurrentPlayer(P player);
    /** Check the destination's current tp and remote tp permissions, not forwarded source permissions. */
    TeleportPermissionCheck checkTeleportPermissions(P player);
    /** Initiate once on the owner and complete true only if the teleport actually succeeds. */
    CompletionStage<Boolean> teleport(P player, DestinationResolver.Target target);
}
