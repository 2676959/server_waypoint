package _959.server_waypoint.util;

import _959.server_waypoint.network.payload.c2s.HandshakeC2SPayload;

public class HandshakePayloadGenerator {
    public static HandshakeC2SPayload generate() {
        int edition = LocalEditionFileManager.readEdition(XaeroMinimapHelper.getMinimapSession());
        return new HandshakeC2SPayload(edition);
    }
}
