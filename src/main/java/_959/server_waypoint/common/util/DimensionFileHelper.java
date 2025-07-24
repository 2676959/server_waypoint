package _959.server_waypoint.common.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public class DimensionFileHelper {
    public static String getFileName(RegistryKey<World> dimKey) {
        if (dimKey == World.OVERWORLD) {
            return "dim%0";
        } else if (dimKey == World.NETHER) {
            return "dim%-1";
        } else if (dimKey == World.END) {
            return "dim%1";
        } else {
            Identifier identifier = dimKey.getValue();
            return "dim%" + identifier.getNamespace() + "$" + identifier.getPath().replace('/', '%');
        }
    }

    @Nullable
    public static RegistryKey<World> getDimensionKey(String folderName) {
        String dimId = folderName.substring(4);
        switch (dimId) {
            case "0" -> {
                return World.OVERWORLD;
            }
            case "1" -> {
                return World.END;
            }
            case "-1" -> {
                return World.NETHER;
            }
            default -> {
                String[] idParts = dimId.split("\\$");
                return idParts.length < 2 ? null : RegistryKey.of(RegistryKeys.WORLD, Identifier.of(idParts[0], idParts[1].replace('%', '/')));
            }
        }
    }
}
