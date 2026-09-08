package _959.server_waypoint.crossserver.handoff;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Backend adapter. No method may block waiting for a server thread or an asynchronous teleport. */
public interface DestinationPlatform<P> {
    /** Queue once on the player's owner. Return false on rejection; call retired if the owner disappears. */
    boolean execute(P player, Runnable task, Runnable retired);
    boolean ownsThread(P player);
    /** These reads and teleport are invoked only after ownsThread succeeds. */
    UUID playerId(P player);
    boolean isCurrentPlayer(P player);
    /** Check the destination's current local teleport permission, not a forwarded source permission. */
    boolean canTeleport(P player);
    /** Initiate once on the owner and complete true only if the teleport actually succeeds. */
    CompletionStage<Boolean> teleport(P player, DestinationResolver.Target target);
}
