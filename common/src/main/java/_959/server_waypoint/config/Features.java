package _959.server_waypoint.config;

public class Features {
    boolean addWaypointFromChatSharing = true;
    boolean sendXaerosWorldId = true;

    public boolean addWaypointFromChatSharing() {
        return this.addWaypointFromChatSharing;
    }

    public boolean sendXaerosWorldId() {
        return this.sendXaerosWorldId;
    }

    public void sendXaerosWorldId(boolean enable) {
        this.sendXaerosWorldId = enable;
    }

    @Override
    public String toString() {
        return "{addWaypointFromChatSharing=" + addWaypointFromChatSharing  + ", sendXaerosWorldId=" + sendXaerosWorldId + "}";
    }
}
