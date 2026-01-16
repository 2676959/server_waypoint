package _959.server_waypoint.common.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public final class ClientCommandUtils {
    public static boolean sendCommand(String command) {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler != null) {
            networkHandler.sendCommand(command);
            return true;
        } else {
            return false;
        }
    }
}
