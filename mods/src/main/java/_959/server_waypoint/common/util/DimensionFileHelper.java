package _959.server_waypoint.common.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class DimensionFileHelper {
    @Nullable
    public static ResourceKey<Level> getDimensionKey(String dimensionName) {
        String[] idParts = dimensionName.split(":");
        if (idParts.length != 2) {
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, _959.server_waypoint.common.util.ResourceLocationHelper.id(idParts[0], idParts[1]));
    }
}
