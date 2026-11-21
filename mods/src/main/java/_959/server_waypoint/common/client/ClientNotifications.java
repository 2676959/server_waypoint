package _959.server_waypoint.common.client;

import _959.server_waypoint.common.client.ClientConfig.SyncNotificationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Delivers a client notification using the selected display mode. */
public final class ClientNotifications {
    private ClientNotifications() {
    }

    public static void show(Minecraft minecraft, SyncNotificationMode mode, Component chatMessage, Component toastMessage) {
        var player = minecraft.player;
        if (player == null) {
            return;
        }
        switch (mode) {
            case OFF -> { }
            case CHAT -> {
                //? if >=26 {
                player.sendSystemMessage(chatMessage);
                //?} else {
                /*player.displayClientMessage(chatMessage, false);
                *///?}
            }
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
