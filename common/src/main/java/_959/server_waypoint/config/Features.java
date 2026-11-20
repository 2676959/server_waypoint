package _959.server_waypoint.config;

import _959.server_waypoint.core.network.upload.UploadTarget;
import com.google.gson.annotations.JsonAdapter;

import java.util.Set;

public class Features {
    public static volatile boolean noXaerosMod = true;
    volatile boolean addWaypointFromChatSharing = true;
    volatile boolean sendXaerosWorldId = true;
    volatile boolean compressChunkedMessages = true;
    @JsonAdapter(value = UploadTargetSetJsonAdapter.class, nullSafe = false)
    private Set<UploadTarget> waypointUploadPlatforms = UploadTarget.supportedTargets();
    private BroadcastFeedback broadcastFeedback = BroadcastFeedback.ALL;

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

    public Set<UploadTarget> waypointUploadPlatforms() {
        return this.waypointUploadPlatforms;
    }

    public BroadcastFeedback broadcastFeedback() {
        return this.broadcastFeedback;
    }

    @Override
    public String toString() {
        return "{addWaypointFromChatSharing=" + addWaypointFromChatSharing
                + ", sendXaerosWorldId=" + sendXaerosWorldId
                + ", compressChunkedMessages=" + compressChunkedMessages
                + ", waypointUploadPlatforms=" + waypointUploadPlatforms
                + ", broadcastFeedback=" + broadcastFeedback + "}";
    }
}
