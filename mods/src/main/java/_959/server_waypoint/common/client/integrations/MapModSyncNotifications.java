package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientNotifications;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.network.upload.UploadTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class MapModSyncNotifications {
    private MapModSyncNotifications() {
    }

    static void showSyncSuccess(UploadTarget target, Component chatMessage) {
        String toastKey = switch (target) {
            case XAERO -> "server_waypoint.sync.toast.synced.xaero";
            case VOXELMAP -> "server_waypoint.sync.toast.synced.voxelmap";
        };
        showSyncNotification(chatMessage, Component.translatable(toastKey));
    }

    static void showSyncFailure(Component message) {
        showSyncNotification(message, message);
    }

    private static void showSyncNotification(Component chatMessage, Component toastMessage) {
        ClientNotifications.show(Minecraft.getInstance(), WaypointClientMod.getClientConfig().getSyncNotificationMode(),
                chatMessage, toastMessage);
    }
}
