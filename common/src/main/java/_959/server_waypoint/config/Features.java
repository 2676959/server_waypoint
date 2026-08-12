package _959.server_waypoint.config;

public class Features {
    public static volatile boolean noXaerosMod = true;
    volatile boolean addWaypointFromChatSharing = true;
    volatile boolean sendXaerosWorldId = true;
    volatile boolean compressChunkedMessages = true;

    public Features() {
    }

    public boolean addWaypointFromChatSharing() {
        return this.addWaypointFromChatSharing;
    }

    public boolean sendXaerosWorldId() {
        return noXaerosMod && this.sendXaerosWorldId;
    }

    public void sendXaerosWorldId(boolean enable) {
        this.sendXaerosWorldId = enable;
    }

    public boolean compressChunkedMessages() {
        return this.compressChunkedMessages;
    }

    @Override
    public String toString() {
        return "{addWaypointFromChatSharing=" + addWaypointFromChatSharing
                + ", sendXaerosWorldId=" + sendXaerosWorldId
                + ", compressChunkedMessages=" + compressChunkedMessages + "}";
    }
}
