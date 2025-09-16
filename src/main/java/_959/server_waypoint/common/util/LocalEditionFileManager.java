package _959.server_waypoint.common.util;

import _959.server_waypoint.common.client.ServerWaypointClientMod;
import net.minecraft.world.World;
import xaero.hud.minimap.module.MinimapSession;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalEditionFileManager {
    public static Path getEditionFile(MinimapSession session) {
        Path configDir = session.getWorldManager().getAutoRootContainer().getDirectoryPath();
//        ServerWaypointClient.LOGGER.info(configDir.toString());
        String node = XaeroMinimapHelper.getMinimapWorldNode(session, World.OVERWORLD);
        return configDir.resolve("EDITION$" + node);
    }

    // read edition from file and check whether it exists, if not creates a new one
    public static int readEdition(MinimapSession session) {
        Path editionFile = getEditionFile(session);
        if (!Files.exists(editionFile)) {
            return -1;
        } else {
            try (DataInputStream in = new DataInputStream(new FileInputStream(editionFile.toFile()))) {
                return in.readInt();
            } catch (IOException e) {
                ServerWaypointClientMod.LOGGER.error("Failed to read edition from file, sync may not work properly.", e);
                return -1;
            }
        }
    }

    public static void writeEdition(MinimapSession session, int edition) {
        Path editionFile = getEditionFile(session);
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(editionFile.toFile()))) {
            out.writeInt(edition);
        } catch (IOException e) {
            ServerWaypointClientMod.LOGGER.error("Failed to write edition to file, sync may not work properly.", e);
        }
    }
}
