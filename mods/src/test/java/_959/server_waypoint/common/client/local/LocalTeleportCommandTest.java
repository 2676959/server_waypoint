package _959.server_waypoint.common.client.local;

import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class LocalTeleportCommandTest {
    private final SimpleWaypoint target = new SimpleWaypoint("Base", "B", new WaypointPos(-12, 64, 30), 0x123456, 90, true);

    @Test void expandsExactCommandIndependentlyOfLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("execute in minecraft:the_nether run tp @s -12 64 30 90 ~",
                    LocalTeleportCommand.expand(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, "minecraft:the_nether", target));
            assertEquals("tp @s -12 64 30", LocalTeleportCommand.expand(" /tp @s {x} {y} {z} ", "minecraft:overworld", target));
        } finally { Locale.setDefault(before); }
    }

    @Test void rejectsInvalidTemplatesAndDimensionInjection() {
        for (String template : new String[]{"", " / ", "tp {unknown}", "tp {x", "tp x}", "tp {{x}}", "tp\nkill @s", "tp\rkill @s", "tp\t@s", "tp\u0000@s"}) {
            assertThrows(IllegalArgumentException.class, () -> LocalTeleportCommand.expand(template, "minecraft:overworld", target), template);
        }
        assertThrows(IllegalArgumentException.class, () -> LocalTeleportCommand.expand("tp {dimension}", "minecraft:overworld run kill @s", target));
    }
}
