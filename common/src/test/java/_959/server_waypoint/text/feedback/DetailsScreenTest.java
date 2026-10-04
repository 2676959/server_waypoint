package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.find;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailsScreenTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final SimpleWaypoint MAIN_HOME = HOME.getWaypointByName("Main Home");
    private static final String EDIT = "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" ";

    private static Component mainHome() {
        return DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME, null);
    }

    @Test
    void waypointDetailsListEveryPropertyWithItsEditButton() {
        assertEquals(List.of(
                "Overworld › Home Bases › [MH] Main Home",
                "[✎] Display name: Main Home",
                "[✎] Identifier: Main Home",
                "[✎] Initials: MH",
                "[✎] Icon: none",
                "[✎] Position: 120, 64, -35 [Here]",
                "[✎] Color: █ #FFAA00",
                "[✎] Yaw: 0° (south)",
                "[✎] Visibility: Global",
                "[✎] Keywords: home, base [×]",
                "[✎] Description: Where the beds are [×]",
                "[Navigate] [Teleport] [Download] [Remove] [Back]"), lines(mainHome()));
    }

    @Test
    void theBreadcrumbOpensTheDimensionAndTheListAndTheNameIsGold() {
        Component details = mainHome();

        assertEquals("/wp list minecraft:overworld", clickOf(details, "Overworld"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(details, "Home Bases"));
        assertEquals(NamedTextColor.WHITE, colorOf(details, "Home Bases"));
        assertEquals("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(details, "[MH]"));
        assertEquals(NamedTextColor.GOLD, colorOf(details, "Main Home"));
        assertNull(clickOf(details, "Main Home"));
    }

    @Test
    void editButtonsSuggestTheCurrentValueWhileColorYawAndVisibilityRun() {
        Component details = mainHome();

        assertTrue(suggestions(details).containsAll(List.of(
                EDIT + "set display-name \"Main Home\"",
                EDIT + "set identifier \"Main Home\"",
                EDIT + "set initials MH",
                EDIT + "set icon ",
                EDIT + "set position 120 64 -35",
                EDIT + "set keywords \"home, base\"",
                EDIT + "set description \"Where the beds are\"",
                EDIT + "clear keywords",
                EDIT + "clear description",
                "/wp remove minecraft:overworld \"Home Bases\" \"Main Home\"")));
        assertTrue(runCommands(details).containsAll(List.of(
                EDIT + "set position ~ ~ ~",
                EDIT + "set color",
                EDIT + "set yaw",
                EDIT + "set visibility local",
                "/wp navigate minecraft:overworld \"Home Bases\" \"Main Home\"",
                "/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"",
                "/wp download minecraft:overworld \"Home Bases\" \"Main Home\"",
                "/wp list minecraft:overworld \"Home Bases\"")));
        assertEquals(NamedTextColor.YELLOW, colorOf(details, "[✎]"));
        assertEquals(NamedTextColor.RED, colorOf(details, "[×]"));
        assertEquals(NamedTextColor.GREEN, colorOf(details, "[Here]"));
        assertEquals("Clear the keywords\nPress Enter to confirm", tooltipOf(details, "[×]"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, colorOf(details, "[Navigate]"));
        assertEquals(NamedTextColor.AQUA, colorOf(details, "[Download]"));
        assertEquals("Remove Main Home\nPress Enter to confirm", tooltipOf(details, "[Remove]"));
        assertEquals("Back to Home Bases", tooltipOf(details, "[Back]"));
    }

    @Test
    void labelsAreGrayValuesWhiteAndNoneDarkGrayItalics() {
        Component details = mainHome();

        assertEquals(NamedTextColor.GRAY, colorOf(details, "Initials"));
        assertEquals(NamedTextColor.WHITE, colorOf(details, "MH"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(details, "none"));
        assertEquals(TextDecoration.State.TRUE, find(details, "none").style().decoration(TextDecoration.ITALIC));
    }

    @Test
    void withoutEditPermissionTheEditColumnClearAndHereAreHidden() {
        List<String> lines = lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.member()), OVERWORLD, HOME, MAIN_HOME, null));

        assertEquals("Display name: Main Home", lines.get(1));
        assertEquals("Position: 120, 64, -35", lines.get(5));
        assertEquals("Keywords: home, base", lines.get(9));
        assertEquals("[Navigate] [Back]", lines.get(11));
    }

    @Test
    void hereOnlyAppearsInTheWaypointsDimension() {
        List<String> lines = lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.in(Fixtures.player(), NETHER)),
                OVERWORLD, HOME, MAIN_HOME, null));

        assertEquals("[✎] Position: 120, 64, -35", lines.get(5));
    }

    @Test
    void displayNameOverridesAndIconsCanBeCleared() {
        SimpleWaypoint renamed = new SimpleWaypoint("main_home", "Home", "MH", new WaypointPos(0, 64, 0), 0xFFAA00, 90, false,
                List.of(), "", _959.server_waypoint.util.NamespacedId.parse("minecraft:diamond"));
        List<String> lines = lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, renamed, null));

        assertEquals("[✎] Display name: Home [×]", lines.get(1));
        assertEquals("[✎] Identifier: main_home", lines.get(2));
        assertEquals("[✎] Icon: minecraft:diamond [×]", lines.get(4));
        assertEquals("[✎] Yaw: 90° (west)", lines.get(7));
        assertEquals("[✎] Visibility: Local", lines.get(8));
        assertEquals("[✎] Keywords: none", lines.get(9));
    }

    @Test
    void blankIdentifiersShowInQuotes() {
        SimpleWaypoint blank = new SimpleWaypoint("", "E", new WaypointPos(0, 64, 0), 0, 0, true);

        assertEquals("[✎] Identifier: \"\"",
                lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, blank, null)).get(2));
    }

    @Test
    void identifiersLongEnoughToPassTheCommandLimitLoseOnlyTheirClicks() {
        SimpleWaypoint gate = Fixtures.waypoint("G".repeat(240), "GT", 0xFFFFFF, 0, 64, 0);
        Component details = DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, gate, null);

        assertEquals(12, lines(details).size());
        assertTrue(suggestions(details).stream().allMatch(command -> command.length() <= 256));
        assertTrue(runCommands(details).stream().allMatch(command -> command.length() <= 256));
        assertNull(clickOf(details, "[Teleport]"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(details, "[Back]"));
    }

    @Test
    void anEditPutsItsResultOnTop() {
        Component details = DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME,
                DetailsScreen.updated(patch(PatchField.unchanged(), PatchField.set(0x55FF55), PatchField.unchanged())));

        assertEquals("✔ Updated the colour", lines(details).get(0));
        assertEquals(NamedTextColor.GREEN, colorOf(details, "✔ "));
        assertEquals("Overworld › Home Bases › [MH] Main Home", lines(details).get(1));
    }

    @Test
    void theResultNamesTheFirstPropertyThePatchChanges() {
        assertEquals("Updated the facing", render(DetailsScreen.updated(
                patch(PatchField.unchanged(), PatchField.unchanged(), PatchField.set(90)))));
        assertEquals("Cleared the display name", render(DetailsScreen.updated(
                new WaypointListPatch(PatchField.unchanged(), PatchField.clear()))));
        assertEquals("Updated the identifier", render(DetailsScreen.updated(
                new WaypointListPatch(PatchField.set("farms"), PatchField.unchanged()))));
        assertNull(DetailsScreen.updated(WaypointPatch.empty()));
    }

    @Test
    void plainTextDetailsKeepEveryValueWithoutButtons() {
        Component details = DetailsScreen.waypoint(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, MAIN_HOME, null);

        assertEquals(List.of(
                "Overworld (minecraft:overworld) › Home Bases › [MH] Main Home",
                "Display name: Main Home",
                "Identifier: Main Home",
                "Initials: MH",
                "Icon: none",
                "Position: 120, 64, -35",
                "Color: █ #FFAA00",
                "Yaw: 0° (south)",
                "Visibility: Global",
                "Keywords: home, base",
                "Description: Where the beds are"), lines(details));
        assertTrue(runCommands(details).isEmpty());
        assertTrue(suggestions(details).isEmpty());
    }

    @Test
    void listDetailsShowTheCountAndOnlyEmptyListsCanBeRemoved() {
        Component details = DetailsScreen.list(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, null);

        assertEquals(List.of(
                "Overworld › Home Bases  3 waypoints",
                "[✎] Display name: Home Bases",
                "[✎] Identifier: Home Bases",
                "[Open list] [+ Waypoint here] [Download] [Remove] [Back]"), lines(details));
        assertEquals(NamedTextColor.GOLD, colorOf(details, "Home Bases"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(details, "[Open list]"));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" ", clickOf(details, "[+ Waypoint here]"));
        assertEquals("/wp download minecraft:overworld \"Home Bases\"", clickOf(details, "[Download]"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(details, "[Remove]"));
        assertNull(clickOf(details, "[Remove]"));
        assertEquals("Only empty lists can be removed\nRemove its 3 waypoints first", tooltipOf(details, "[Remove]"));
        assertEquals("/wp list minecraft:overworld", clickOf(details, "[Back]"));
    }

    @Test
    void anEmptyListElsewhereAddsByCoordinatesAndCanBeRemoved() {
        Component details = DetailsScreen.list(Fixtures.dims(Fixtures.player()), NETHER, new WaypointList("Storage", 1, List.of()), null);

        assertEquals("Nether › Storage  0 waypoints", lines(details).get(0));
        assertEquals("[Open list] [+ Waypoint] [Remove] [Back]", lines(details).get(3));
        assertEquals("/wp add minecraft:the_nether Storage ", clickOf(details, "[+ Waypoint]"));
        assertEquals(NamedTextColor.RED, colorOf(details, "[Remove]"));
        assertEquals("/wp remove minecraft:the_nether Storage", clickOf(details, "[Remove]"));
    }

    @Test
    void renamedListsCanClearTheirDisplayNameAndPlainTextShowsTheIdentifier() {
        WaypointList renamed = new WaypointList("farms", "Farms", 1, Fixtures.farms().simpleWaypoints());

        assertEquals("[✎] Display name: Farms [×]",
                lines(DetailsScreen.list(Fixtures.dims(Fixtures.player()), OVERWORLD, renamed, null)).get(1));
        assertEquals(List.of(
                "Overworld (minecraft:overworld) › Farms (farms)  7 waypoints",
                "Display name: Farms",
                "Identifier: farms"), lines(DetailsScreen.list(Fixtures.dims(Fixtures.console()), OVERWORLD, renamed, null)));
    }

    private static WaypointPatch patch(PatchField<WaypointPos> position, PatchField<Integer> color, PatchField<Integer> yaw) {
        return new WaypointPatch(PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), position, color, yaw,
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }
}
