package _959.server_waypoint.fabric.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.fabric.util.DimensionFileHelper.getDimensionKey;

public class XaeroDimensionStringConverter {
    @Nullable
    public static RegistryKey<World> convert(String dimString) {
        return switch (dimString) {
            case "overworld" -> World.OVERWORLD;
            case "the-nether" -> World.NETHER;
            case "the-end" -> World.END;
            default -> getDimensionKey(dimString);
        };
    }
}
