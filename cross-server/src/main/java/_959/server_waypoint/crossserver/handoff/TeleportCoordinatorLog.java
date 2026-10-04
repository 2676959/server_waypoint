package _959.server_waypoint.crossserver.handoff;

import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Dedicated audit categories; never log credentials, raw packets, coordinates or exception messages. */
public final class TeleportCoordinatorLog {
    public static final Logger BACKEND = LoggerFactory.getLogger("server_waypoint.teleport_coordinator.backend");
    public static final Logger PROXY = LoggerFactory.getLogger("server_waypoint.teleport_coordinator.proxy");

    private TeleportCoordinatorLog() { }

    public static String safe(Object value) {
        String text = String.valueOf(value);
        StringBuilder result = new StringBuilder();
        text.codePoints().limit(256).forEach(c -> result.appendCodePoint(Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || c == 0x2028 || c == 0x2029 ? '?' : c));
        return result.toString();
    }

    public static void activity(Logger logger, String direction, RemoteServerId peer, UUID requestId, ApplicationMessage message) {
        UUID player = null;
        Object target = "-";
        Object result = "-";
        if (message instanceof PrepareHandoff prepare) { player = prepare.playerId(); target = prepare.target(); }
        else if (message instanceof HandoffPrepared prepared) { player = prepared.binding().playerId(); target = prepared.binding().target(); }
        else if (message instanceof HandoffClaimed claimed) { player = claimed.binding().playerId(); target = claimed.binding().target(); }
        else if (message instanceof ClaimHandoff claim) { player = claim.playerId(); target = claim.destination(); }
        else if (message instanceof CompleteHandoff complete) { player = complete.playerId(); target = complete.destination(); result = complete.result(); }
        else if (message instanceof CancelHandoff cancel) result = cancel.reason();
        else if (message instanceof HandoffRejected rejected) result = rejected.reason();
        else if (message instanceof ApplicationMessage.Error error) result = error.reason();
        else return;
        logger.info("handoff direction={} peer={} request={} player={} phase={} target={} result={}",
                direction, safe(peer.value()), requestId, player, message.getClass().getSimpleName(), safe(target), result);
    }
}
