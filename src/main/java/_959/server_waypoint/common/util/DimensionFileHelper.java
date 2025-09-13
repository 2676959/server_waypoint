package _959.server_waypoint.common.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DimensionFileHelper {
    @Nullable
    public static RegistryKey<World> getDimensionKey(String dimensionName) {
        String[] idParts = dimensionName.split(":");
        if (idParts.length != 2) {
            return null;
        }
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(idParts[0], idParts[1]));
    }
}
