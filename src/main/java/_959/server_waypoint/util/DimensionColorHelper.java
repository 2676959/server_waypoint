package _959.server_waypoint.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

public class DimensionColorHelper {
    public static Formatting getDimensionColor(RegistryKey<World> dimension) {
        if (dimension == World.OVERWORLD) {
            return Formatting.GREEN;
        } else if (dimension == World.NETHER) {
            return Formatting.RED;
        } else if (dimension == World.END) {
            return Formatting.LIGHT_PURPLE;
        } else {
            return Formatting.YELLOW;
        }
    }
}
