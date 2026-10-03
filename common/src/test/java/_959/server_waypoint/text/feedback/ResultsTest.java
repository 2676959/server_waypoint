package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.util.StringCommandBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsTest {
    private static final WaypointList FARMS = Fixtures.farms();
    private static final SimpleWaypoint IRON = FARMS.getWaypointByName("Iron Farm");
    private static final WaypointList STORAGE = new WaypointList("Storage", 1, List.of());

    private static DimensionStyle dims() {
        return Fixtures.dims(Fixtures.player());
    }

    @Test
    void addingAnswersWithDetailsNavigateAndUndo() {
        Component result = Results.added(dims(), OVERWORLD, FARMS, IRON);

        assertEquals("✔ Added [IF] Iron Farm to Farms   Details · Navigate · Undo", render(result));
        assertEquals(NamedTextColor.GREEN, colorOf(result, "✔ Added "));
        assertEquals(NamedTextColor.WHITE, colorOf(result, "Iron Farm"));
        assertEquals("/wp details waypoint minecraft:overworld Farms \"Iron Farm\"", clickOf(result, "Details"));
        assertEquals("/wp navigate minecraft:overworld Farms \"Iron Farm\"", clickOf(result, "Navigate"));
        assertEquals("/wp remove minecraft:overworld Farms \"Iron Farm\"", clickOf(result, "Undo"));
        assertEquals(NamedTextColor.RED, colorOf(result, "Undo"));
        assertEquals("Remove Iron Farm again\nPress Enter to confirm", tooltipOf(result, "Undo"));
        assertEquals("✔ Added [IF] Iron Farm to Farms", render(Results.added(Fixtures.dims(Fixtures.console()), OVERWORLD, FARMS, IRON)));
    }

    @Test
    void creatingAListOffersToAddToItAndToOpenIt() {
        Component here = Results.createdList(dims(), OVERWORLD, STORAGE);
        Component nether = Results.createdList(dims(), NETHER, STORAGE);

        assertEquals("✔ Created the list Storage in Overworld   Add here · Open", render(here));
        assertEquals("/wp add ~ ~ ~ Storage ", clickOf(here, "Add here"));
        assertEquals("/wp list minecraft:overworld Storage", clickOf(here, "Open"));
        assertEquals("✔ Created the list Storage in Nether   Add waypoint · Open", render(nether));
        assertEquals(NamedTextColor.RED, colorOf(nether, "Nether"));
    }

    @Test
    void removingOffersRestoreAndPlainTextPrintsTheCommand() {
        Component result = Results.removed(dims(), OVERWORLD, FARMS, IRON, "r12");

        assertEquals("✔ Removed [IF] Iron Farm from Farms   Restore", render(result));
        assertEquals("/wp restore r12", clickOf(result, "Restore"));
        assertNull(clickOf(result, "[IF]"));
        assertEquals("✔ Removed [IF] Iron Farm from Farms. Restore with /wp restore r12",
                render(Results.removed(Fixtures.dims(Fixtures.console()), OVERWORLD, FARMS, IRON, "r12")));
    }

    @Test
    void removedListsCanBeCreatedAgainAndRestoresAndTeleportsAreOneLine() {
        Component removed = Results.removedList(dims(), NETHER, STORAGE);

        assertEquals("✔ Removed the list Storage from Nether   Undo", render(removed));
        assertEquals("/wp add minecraft:the_nether Storage", clickOf(removed, "Undo"));
        assertEquals(NamedTextColor.GREEN, colorOf(removed, "Undo"));
        assertEquals("✔ Restored [IF] Iron Farm to Farms", render(Results.restored(dims(), OVERWORLD, FARMS, IRON)));
        assertEquals("✔ Teleported Steve to [IF] Iron Farm",
                render(Results.teleported(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals(List.of("✔ Reloaded the configuration and language files", "Languages: en_us, zh_cn"),
                lines(Results.reloaded(List.of("en_us", "zh_cn"))));
        assertEquals(List.of("✔ Reloaded the configuration and language files"), lines(Results.reloaded(List.of())));
    }

    @Test
    void teleportingNamesThePlayerInWhiteUnlessTheDisplayNameHasItsOwnColour() {
        Component plain = Results.teleported(dims(), text("Steve"), OVERWORLD, FARMS, IRON);
        Component team = Results.teleported(dims(), text("Steve", NamedTextColor.GOLD), OVERWORLD, FARMS, IRON);

        assertEquals(NamedTextColor.GREEN, colorOf(plain, "✔ Teleported "));
        assertEquals(NamedTextColor.WHITE, colorOf(plain, "Steve"));
        assertEquals(NamedTextColor.GOLD, colorOf(team, "Steve"));
    }

    @Test
    void broadcastsNameThePlayerInWhiteWithGrayVerbsAndClickableReferences() {
        Component added = Broadcasts.added(dims(), text("Steve"), OVERWORLD, FARMS, IRON);

        assertEquals("Steve added [IF] Iron Farm to Farms", render(added));
        assertEquals(NamedTextColor.WHITE, colorOf(added, "Steve"));
        assertEquals(NamedTextColor.GRAY, colorOf(added, " added "));
        assertEquals("/wp tp minecraft:overworld Farms \"Iron Farm\"", clickOf(added, "[IF]"));
        assertEquals("Steve updated [IF] Iron Farm", render(Broadcasts.updated(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals("Steve removed [IF] Iron Farm from Farms",
                render(Broadcasts.removed(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals("Steve restored [IF] Iron Farm to Farms",
                render(Broadcasts.restored(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals("Steve created the list Farms in Overworld",
                render(Broadcasts.createdList(dims(), text("Steve"), OVERWORLD, FARMS)));
        assertEquals("Steve updated the list Farms in Overworld",
                render(Broadcasts.updatedList(dims(), text("Steve"), OVERWORLD, FARMS)));
        assertEquals("Steve removed the list Storage from Nether",
                render(Broadcasts.removedList(dims(), text("Steve"), NETHER, STORAGE)));
    }

    @Test
    void errorsAreOneRedLineWithARecoveryLink() {
        Component noWaypoint = Errors.noWaypoint(dims(), OVERWORLD, "Farms", "Iron Farn");

        assertEquals("✘ No waypoint called Iron Farn in Farms. Open Farms", render(noWaypoint));
        assertEquals(NamedTextColor.RED, colorOf(noWaypoint, "✘ "));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(noWaypoint, "Open Farms"));
        assertEquals("✘ No waypoint called Iron Farn in Farms.",
                render(Errors.noWaypoint(Fixtures.dims(Fixtures.console()), OVERWORLD, "Farms", "Iron Farn")));
        assertEquals("✘ Overworld has no lists yet. New list", render(Errors.noLists(dims(), OVERWORLD)));
        assertEquals("✘ Overworld already has a list called Farms. Open", render(Errors.listExists(dims(), OVERWORLD, FARMS)));
        assertEquals("✘ Farms already has a waypoint called Iron Farm. Details",
                render(Errors.waypointExists(dims(), OVERWORLD, FARMS, IRON)));
        assertEquals("✘ Only empty lists can be removed. Open Farms", render(Errors.listNotEmpty(dims(), OVERWORLD, FARMS)));
        assertEquals("Farms · 7 waypoints\nClick to open", tooltipOf(Errors.listNotEmpty(dims(), OVERWORLD, FARMS), "Open Farms"));
    }

    @Test
    void editErrorsExplainTheStatus() {
        Component stale = Errors.edit(dims(), EditResultStatus.STALE_REVISION, OVERWORLD, "Farms", "Iron Farm");

        assertEquals("✘ Iron Farm changed while you were editing. Reload details", render(stale));
        assertEquals("/wp details waypoint minecraft:overworld Farms \"Iron Farm\"", clickOf(stale, "Reload details"));
        assertEquals("/wp details list minecraft:overworld Farms",
                clickOf(Errors.edit(dims(), EditResultStatus.STALE_REVISION, OVERWORLD, "Farms", null), "Reload details"));
        assertEquals("✘ Nothing changed.", render(Errors.edit(dims(), EditResultStatus.IDENTICAL, OVERWORLD, "Farms", "Iron Farm")));
        assertEquals("✘ That identifier is already in use.",
                render(Errors.edit(dims(), EditResultStatus.IDENTIFIER_COLLISION, OVERWORLD, "Farms", "Iron Farm")));
        assertEquals("✘ No list called Farms in Overworld. Browse lists",
                render(Errors.edit(dims(), EditResultStatus.LIST_NOT_FOUND, OVERWORLD, "Farms", "Iron Farm")));
    }

    @Test
    void theSharingPromptOffersEveryListOfTheDimension() {
        Component prompt = SharingPrompt.found(dims(), OVERWORLD, IRON, Fixtures.overworldLists());

        assertEquals(List.of("Shared waypoint [IF] Iron Farm in Overworld", "Save it to  Home Bases · Farms · Exploration"),
                lines(prompt));
        assertEquals(StringCommandBuilder.addCmd(OVERWORLD, "Farms", IRON), clickOf(prompt, "Farms"));
        assertEquals("Save it to Farms\nPress Enter to confirm", tooltipOf(prompt, "Farms"));
        assertEquals(List.of("Shared waypoint [IF] Iron Farm in Overworld", "No lists yet. New list"),
                lines(SharingPrompt.found(dims(), OVERWORLD, IRON, List.of())));
        assertEquals("✘ This server has no dimension mars:mars.",
                render(SharingPrompt.unknownDimension(dims(), "mars:mars", IRON)));
        assertTrue(runCommands(prompt).isEmpty());
    }
}
