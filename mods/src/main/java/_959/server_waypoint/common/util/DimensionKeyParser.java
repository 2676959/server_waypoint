package _959.server_waypoint.common.util;

import _959.server_waypoint.util.NamespacedId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.util.ResourceLocationHelper.mcId;

public class DimensionKeyParser {
    @Nullable
    public static ResourceKey<Level> getDimensionKey(String dimensionName) {
        NamespacedId id;
        try {
            id = NamespacedId.parse(dimensionName);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, mcId(id.namespace(), id.path()));
    }
}
