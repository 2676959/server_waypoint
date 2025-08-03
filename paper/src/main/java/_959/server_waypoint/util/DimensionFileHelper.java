package _959.server_waypoint.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class DimensionFileHelper {
    public static String getFileName(ResourceKey<Level> dimKey) {
        if (dimKey == Level.OVERWORLD) {
            return "dim%0";
        } else if (dimKey == Level.NETHER) {
            return "dim%-1";
        } else if (dimKey == Level.END) {
            return "dim%1";
        } else {
            String namespace = dimKey.location().getNamespace();
            String path = dimKey.location().getPath();
            return "dim%" + namespace + "$" + path.replace('/', '%');
        }
    }

    @Nullable
    public static ResourceKey<Level> getDimensionKey(String folderName) {
        String dimId = folderName.substring(4);
        switch (dimId) {
            case "0" -> {
                return Level.OVERWORLD;
            }
            case "1" -> {
                return Level.END;
            }
            case "-1" -> {
                return Level.NETHER;
            }
            default -> {
                String[] idParts = dimId.split("\\$");
                return idParts.length < 2 ? null : ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.fromNamespaceAndPath(idParts[0], idParts[1].replace('%', '/')));
            }
        }
    }
}
