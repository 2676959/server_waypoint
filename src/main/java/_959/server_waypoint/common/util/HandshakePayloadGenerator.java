package _959.server_waypoint.common.util;

import _959.server_waypoint.common.network.payload.c2s.HandshakeC2SPayload;
import _959.server_waypoint.core.network.buffer.HandshakeBuffer;

public class HandshakePayloadGenerator {
    public static HandshakeC2SPayload generate() {
        int edition = LocalEditionFileManager.readEdition(XaeroMinimapHelper.getMinimapSession());
        return new HandshakeC2SPayload(new HandshakeBuffer(edition));
    }
}
