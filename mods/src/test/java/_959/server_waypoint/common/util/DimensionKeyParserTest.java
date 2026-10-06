package _959.server_waypoint.common.util;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DimensionKeyParserTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rejectsMalformedDimensionsWithoutThrowing() {
        for (String dimension : new String[]{null, "", "overworld", "minecraft:", ":overworld",
                "minecraft:overworld:extra", "Minecraft:overworld", "minecraft:bad dimension"}) {
            assertNull(DimensionKeyParser.getDimensionKey(dimension), dimension);
        }
    }

    @Test
    void preservesValidDimensionKeys() {
        assertEquals(Level.OVERWORLD, DimensionKeyParser.getDimensionKey("minecraft:overworld"));
        assertEquals(Level.NETHER, DimensionKeyParser.getDimensionKey("minecraft:the_nether"));
        assertEquals(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocationHelper.mcId("example", "custom/world")),
                DimensionKeyParser.getDimensionKey("example:custom/world"));
    }
}
