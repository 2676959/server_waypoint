package _959.server_waypoint.common.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientCommandUtils {
    public static boolean sendCommand(String command) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        if (networkHandler != null) {
            networkHandler.sendCommand(command);
            return true;
        } else {
            return false;
        }
    }
}
