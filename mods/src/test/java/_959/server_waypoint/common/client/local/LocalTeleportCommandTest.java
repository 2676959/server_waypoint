package _959.server_waypoint.common.client.local;

import _959.server_waypoint.common.client.local.LocalTeleportCommand.Placeholder;
import _959.server_waypoint.common.client.local.LocalTeleportCommand.Problem;
import _959.server_waypoint.common.client.local.LocalTeleportCommand.ProblemKind;
import _959.server_waypoint.common.client.local.LocalTeleportCommand.Token;
import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    @Test void tokensSplitTheWholeTemplateIntoTextPlaceholdersAndMistakes() {
        assertEquals(List.of(new Token(0, 4, null, false), new Token(4, 7, Placeholder.X, false),
                new Token(7, 10, Placeholder.Y, false), new Token(10, 12, null, false),
                new Token(12, 17, Placeholder.YAW, false)), LocalTeleportCommand.tokens("/tp {x}{y} ~{yaw}"));
        assertEquals(List.of(new Token(0, 3, null, false), new Token(3, 10, null, true),
                new Token(10, 11, null, false), new Token(11, 12, null, true)), LocalTeleportCommand.tokens("tp {pitch} }"));
        assertEquals(List.of(new Token(0, 3, null, false), new Token(3, 7, null, true)), LocalTeleportCommand.tokens("tp {x y"));
        assertEquals(List.of(), LocalTeleportCommand.tokens(""));
    }

    @Test void problemsFollowTheOrderExpandChecksIn() {
        assertEquals(Optional.empty(), LocalTeleportCommand.problem(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND));
        assertEquals(Optional.of(new Problem(ProblemKind.CONTROL, "")), LocalTeleportCommand.problem("tp {pitch}\n"));
        assertEquals(Optional.of(new Problem(ProblemKind.EMPTY, "")), LocalTeleportCommand.problem(" / "));
        assertEquals(Optional.of(new Problem(ProblemKind.EXTRA_SLASH, "")), LocalTeleportCommand.problem("//tp @s"));
        assertEquals(Optional.of(new Problem(ProblemKind.UNKNOWN, "{pitch}")), LocalTeleportCommand.problem("tp {pitch} }"));
        assertEquals(Optional.of(new Problem(ProblemKind.STRAY_CLOSE, "}")), LocalTeleportCommand.problem("tp } {pitch}"));
        assertEquals(Optional.of(new Problem(ProblemKind.UNCLOSED, "{x y")), LocalTeleportCommand.problem("tp {x y"));
    }

    @Test void problemAgreesWithExpandOnEveryTemplate() {
        for (String template : new String[]{"", " / ", "tp {unknown}", "tp {x", "tp x}", "tp {{x}}", "tp\nkill @s",
                "tp\rkill @s", "tp\t@s", "tp\u0000@s", "//tp", LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, " /tp @s {x} {y} {z} "}) {
            boolean expands;
            try { LocalTeleportCommand.expand(template, "minecraft:overworld", target); expands = true; }
            catch (IllegalArgumentException exception) { expands = false; }
            assertEquals(expands, LocalTeleportCommand.problem(template).isEmpty(), template);
        }
    }
}
