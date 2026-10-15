package _959.server_waypoint.common.util;

import org.junit.jupiter.api.Test;

import static _959.server_waypoint.common.util.TestDimensions.NETHER;
import static _959.server_waypoint.common.util.TestDimensions.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DimensionKeyParserTest {
    @Test
    void rejectsMalformedDimensionsWithoutThrowing() {
        for (String dimension : new String[]{null, "", "overworld", "minecraft:", ":overworld",
                "minecraft:overworld:extra", "Minecraft:overworld", "minecraft:bad dimension"}) {
            assertNull(DimensionKeyParser.getDimensionKey(dimension), dimension);
        }
    }

    @Test
    void preservesValidDimensionKeys() {
        assertEquals(OVERWORLD, DimensionKeyParser.getDimensionKey("minecraft:overworld"));
        assertEquals(NETHER, DimensionKeyParser.getDimensionKey("minecraft:the_nether"));
        assertEquals(TestDimensions.of("example", "custom/world"),
                DimensionKeyParser.getDimensionKey("example:custom/world"));
    }
}
