package _959.server_waypoint.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

public class DimensionFileHelper {
    public static NamespacedKey OVERWORLD;
    public static NamespacedKey THE_NETHER;
    public static NamespacedKey THE_END;
    static {
        OVERWORLD = NamespacedKey.minecraft("overworld");
        THE_NETHER = NamespacedKey.minecraft("the_nether");
        THE_END = NamespacedKey.minecraft("the_end");
    }

    public static String getFileName(NamespacedKey dimKey) {
        if (OVERWORLD.equals(dimKey)) {
            return "dim%0";
        } else if (THE_NETHER.equals(dimKey)) {
            return "dim%-1";
        } else if (THE_END.equals(dimKey)) {
            return "dim%1";
        } else {
            String namespace = dimKey.getNamespace();
            String key = dimKey.getKey();
            return "dim%" + namespace + "$" + key.replace('/', '%');
        }
    }

    @Nullable
    public static NamespacedKey getNamespacedKey(String folderName) {
        String dimId = folderName.substring(4);
        switch (dimId) {
            case "0" -> {
                return OVERWORLD;
            }
            case "1" -> {
                return THE_END;
            }
            case "-1" -> {
                return THE_NETHER;
            }
            default -> {
                String[] idParts = dimId.split("\\$");
                return idParts.length < 2 ? null : new NamespacedKey(idParts[0], idParts[1].replace('%', '/'));
            }
        }
    }

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
    public static ResourceKey<Level> getResourceKey(String folderName) {
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
