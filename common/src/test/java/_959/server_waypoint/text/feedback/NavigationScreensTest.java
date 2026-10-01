package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static net.kyori.adventure.text.Component.translatable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationScreensTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final PlacedWaypoint MAIN_HOME = new PlacedWaypoint(OVERWORLD, HOME, HOME.getWaypointByName("Main Home"));
    private static final Set<NavigationMethod> ALL = EnumSet.allOf(NavigationMethod.class);
    private static final String TRANSFORMATION = "/wp navigate config text_display transformation ";

    @Test
    void thePanelShowsTheTargetEveryMethodAndTheActions() {
        Component panel = NavigationScreens.panel(Fixtures.dims(Fixtures.player()), MAIN_HOME,
                EnumSet.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR), ALL, null);

        assertEquals(List.of(
                "Navigating to [MH] Main Home · 25 m",
                "✔ Compass · Map · ✔ Bossbar · Actionbar · Text display",
                "Stop · Change target"), lines(panel));
        assertEquals(NamedTextColor.GREEN, colorOf(panel, "✔ Compass"));
        assertEquals("/wp navigate disable compass", clickOf(panel, "✔ Compass"));
        assertEquals("Compass is on\nClick to turn it off", tooltipOf(panel, "✔ Compass"));
        assertEquals(NamedTextColor.GRAY, colorOf(panel, "Map"));
        assertEquals("/wp navigate use map", clickOf(panel, "Map"));
        assertEquals(NamedTextColor.RED, colorOf(panel, "Stop"));
        assertEquals("/wp navigate disable", clickOf(panel, "Stop"));
        assertEquals("/wp list", clickOf(panel, "Change target"));
    }

    @Test
    void adjustingTheTextDisplayAppearsOnlyWhileItIsOnAndUpdatesGoOnTop() {
        Component panel = NavigationScreens.panel(Fixtures.dims(Fixtures.player()), MAIN_HOME,
                EnumSet.of(NavigationMethod.TEXT_DISPLAY), ALL,
                translatable("wp.navigation.turned_on", NavigationScreens.methodName(NavigationMethod.TEXT_DISPLAY)));

        assertEquals("✔ Text display on", lines(panel).get(0));
        assertEquals("Stop · Change target · Adjust text display", lines(panel).get(3));
        assertEquals(NamedTextColor.YELLOW, colorOf(panel, "Adjust text display"));
        assertEquals("/wp navigate config text_display", clickOf(panel, "Adjust text display"));
    }

    @Test
    void elsewhereThePanelNamesTheTargetsDimensionAndOnlySupportedMethodsShow() {
        Component panel = NavigationScreens.panel(Fixtures.dims(Fixtures.in(Fixtures.player(), NETHER)), MAIN_HOME,
                EnumSet.of(NavigationMethod.ACTIONBAR), EnumSet.of(NavigationMethod.ACTIONBAR, NavigationMethod.BOSSBAR), null);

        assertEquals("Navigating to [MH] Main Home · Overworld", lines(panel).get(0));
        assertEquals("Bossbar · ✔ Actionbar", lines(panel).get(1));
    }

    @Test
    void withoutATargetThePanelSaysWhereToStartAndStoppingOffersResume() {
        assertEquals(List.of("Not navigating", "Open a waypoint and choose Navigate:  This dimension · All"),
                lines(NavigationScreens.idle(Fixtures.dims(Fixtures.player()))));
        assertEquals(List.of("Not navigating"), lines(NavigationScreens.idle(Fixtures.dims(Fixtures.console()))));

        Component stopped = NavigationScreens.stopped(Fixtures.dims(Fixtures.player()), MAIN_HOME);
        assertEquals("✔ Stopped navigating   Resume", render(stopped));
        assertEquals("/wp navigate minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(stopped, "Resume"));
        assertEquals("✔ Stopped navigating", render(NavigationScreens.stopped(Fixtures.dims(Fixtures.player()), null)));
        assertEquals("✘ You aren't navigating. Browse waypoints", render(NavigationScreens.notNavigating(Fixtures.player())));
        assertEquals("/wp list", clickOf(NavigationScreens.notNavigating(Fixtures.player()), "Browse waypoints"));
    }

    @Test
    void failuresAreErrorLines() {
        assertEquals("✘ Navigation items need 2 free slots; you have 1.",
                render(NavigationScreens.failure(NavigationResult.insufficientInventory(2, 1))));
        assertEquals("✘ The navigation target no longer exists.",
                render(NavigationScreens.failure(NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE))));
        assertEquals("✘ Choose at least one navigation method.",
                render(NavigationScreens.failure(NavigationResult.failure(NavigationResult.Code.INVALID_SELECTION))));
        assertEquals("✘ Compass isn't available right now.", render(NavigationScreens.failure(
                NavigationResult.failure(NavigationResult.Code.METHOD_UNAVAILABLE).withMethod(NavigationMethod.COMPASS))));
    }

    @Test
    void theTextDisplayPanelNudgesEachValue() {
        Component panel = NavigationScreens.textDisplay(Fixtures.player(), TextDisplayTransformation.defaultValue(), null);

        assertEquals(List.of(
                "Text display  offsets from the default placement",
                "Move  X [−][+]  Y [−][+]  Z [−][+]  0, 0, 0 [✎]",
                "Turn  X [−][+]  Y [−][+]  Z [−][+]  0°, 0°, 0° [✎]",
                "Size  [−][+]  1× [✎]",
                "[Reset] [Back]"), lines(panel));
        assertEquals(TRANSFORMATION + "translation -0.05 0 0", clickOf(panel, "[−]"));
        assertEquals(TRANSFORMATION + "translation 0.05 0 0", clickOf(panel, "[+]"));
        assertEquals(TRANSFORMATION + "translation 0 0 0", clickOf(panel, "[✎]"));
        assertTrue(suggestions(panel).contains(TRANSFORMATION + "rotation 0 0 0"));
        assertTrue(suggestions(panel).contains(TRANSFORMATION + "scale 1 1 1"));
        assertEquals(TRANSFORMATION + "reset", clickOf(panel, "[Reset]"));
        assertEquals("/wp navigate", clickOf(panel, "[Back]"));
    }

    @Test
    void nudgesStopAtTheLimitsAndUnevenSizesShowEachAxis() {
        TextDisplayTransformation moved = new TextDisplayTransformation(new Vector3f(16F, 0.1F, -0.05F),
                new Vector3f(355F, 0F, -5F), new Vector3f(0.05F, 2F, 1F));
        Component panel = NavigationScreens.textDisplay(Fixtures.player(), moved,
                translatable("wp.text_display.updated"));

        assertEquals(List.of(
                "✔ Updated the text display",
                "Text display  offsets from the default placement",
                "Move  X [−][+]  Y [−][+]  Z [−][+]  16, 0.1, -0.05 [✎]",
                "Turn  X [−][+]  Y [−][+]  Z [−][+]  355°, 0°, -5° [✎]",
                "Size  [−][+]  0.05×, 2×, 1× [✎]",
                "[Reset] [Back]"), lines(panel));
        assertEquals(TRANSFORMATION + "translation 16 0.1 -0.05", clickOf(panel, "[+]"));
        assertTrue(runCommands(panel).containsAll(List.of(
                TRANSFORMATION + "rotation 360 0 -5",
                TRANSFORMATION + "scale 0.05 0.05 0.05",
                TRANSFORMATION + "scale 0.1 0.1 0.1")));
        assertTrue(lines(NavigationScreens.textDisplay(Fixtures.console(), moved, null))
                .contains("Size  0.05×, 2×, 1×"));
    }
}
