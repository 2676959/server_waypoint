package _959.server_waypoint.common.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class DimensionFileHelper {
    public static String getDimensionString(RegistryKey<World> dimKey) {
        return dimKey.getValue().toString();
    }

    public static RegistryKey<World> getDimensionKey(String dimensionName) {
        String[] idParts = dimensionName.split(":");
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(idParts[0], idParts[1]));
    }
}
