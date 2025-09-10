package _959.server_waypoint.core.network;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;

public class ClientHandshakeHandler<S, P> {
    private final PlatformMessageSender<S, P> sender;

    public ClientHandshakeHandler(PlatformMessageSender<S, P> messageSender) {
        this.sender = messageSender;
    }

    public void onHandshake(P player, int edition) {
        LOGGER.info("new connection with client edition: {}", edition);
        if (edition != WaypointServerCore.EDITION) {
            WorldWaypointBuffer buffer = WaypointServerCore.INSTANCE.toWorldWaypointBuffer();
            if (buffer != null) {
                this.sender.sendPlayerPacket(player, buffer);
            }
        }
    };
}
