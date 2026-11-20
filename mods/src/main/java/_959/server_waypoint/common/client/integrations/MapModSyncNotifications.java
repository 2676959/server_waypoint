package _959.server_waypoint.common.client.integrations;

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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        switch (WaypointClientMod.getClientConfig().getSyncNotificationMode()) {
            case OFF -> { }
            case CHAT -> MapModChatHelper.displayClientMessage(minecraft.player, chatMessage);
            case TOAST -> showToast(minecraft, toastMessage);
        }
    }

    private static void showToast(Minecraft minecraft, Component message) {
        WaypointSyncToast toast = new WaypointSyncToast(minecraft.font, message);
        //? if >=26.3 {
        /*minecraft.gui.toastManager().addToast(toast);
        *///?} elif >=1.21.2 {
        minecraft.getToastManager().addToast(toast);
        //?} else {
        /*minecraft.getToasts().addToast(toast);
        *///?}
    }
}
