package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.ClientNotifications;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.integrations.MapModIntegration.SyncSuccess;
import _959.server_waypoint.core.network.upload.UploadTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.function.BiConsumer;

final class MapModSyncNotifications {
    private final EnumMap<UploadTarget, SyncSuccess> successes = new EnumMap<>(UploadTarget.class);
    private final BiConsumer<Component, Component> display;

    MapModSyncNotifications() {
        this(MapModSyncNotifications::showSyncNotification);
    }

    MapModSyncNotifications(BiConsumer<Component, Component> display) {
        this.display = display;
    }

    void recordSuccess(UploadTarget target, SyncSuccess success) {
        if (success != null) {
            successes.put(target, success);
        }
    }

    boolean hasSuccess() {
        return !successes.isEmpty();
    }

    void flush() {
        SyncSuccess xaero = successes.get(UploadTarget.XAERO);
        SyncSuccess voxelMap = successes.get(UploadTarget.VOXELMAP);
        clear();
        if (xaero != null && voxelMap != null) {
            // A deferred full sync also applies the change reported by the other map.
            SyncSuccess success = xaero.equals(SyncSuccess.FULL_SYNC) ? voxelMap : xaero;
            display.accept(chatMessage(success, "both_maps"),
                    Component.translatable("server_waypoint.sync.toast.synced.both_maps"));
        } else if (xaero != null) {
            displaySuccess(UploadTarget.XAERO, xaero, display);
        } else if (voxelMap != null) {
            displaySuccess(UploadTarget.VOXELMAP, voxelMap, display);
        }
    }

    void clear() {
        successes.clear();
    }

    static void showSyncSuccess(UploadTarget target, SyncSuccess success) {
        if (success != null) {
            displaySuccess(target, success, MapModSyncNotifications::showSyncNotification);
        }
    }

    private static void displaySuccess(UploadTarget target, SyncSuccess success, BiConsumer<Component, Component> display) {
        String chatSuffix = target == UploadTarget.XAERO ? "xaeros" : "voxelmap";
        String toastSuffix = target == UploadTarget.XAERO ? "xaero" : "voxelmap";
        display.accept(chatMessage(success, chatSuffix),
                Component.translatable("server_waypoint.sync.toast.synced." + toastSuffix));
    }

    private static Component chatMessage(SyncSuccess success, String destination) {
        String key = success.messageKey() + "." + destination;
        return success.subject() == null ? Component.translatable(key) : Component.translatable(key, success.subject());
    }

    static void showSyncFailure(Component message) {
        showSyncNotification(message, message);
    }

    private static void showSyncNotification(Component chatMessage, Component toastMessage) {
        ClientNotifications.show(Minecraft.getInstance(), WaypointClientMod.getClientConfig().getSyncNotificationMode(),
                chatMessage, toastMessage);
    }
}
