package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadScreensTest {
    @Test
    void thePanelOffersEveryMapModWithItsThreeModes() {
        Component panel = UploadScreens.panel(Fixtures.player());

        assertEquals(List.of(
                "Upload from your map mod",
                "Xaero's Minimap  Merge · Prefer mine · Mirror",
                "VoxelMap  Merge · Prefer mine · Mirror",
                "Uploads every dimension; add a dimension to limit it."), lines(panel));
        assertEquals("/wp upload xaero", clickOf(panel, "Merge"));
        assertEquals(NamedTextColor.AQUA, colorOf(panel, "Merge"));
        assertEquals("/wp upload xaero force local", clickOf(panel, "Prefer mine"));
        assertEquals(NamedTextColor.YELLOW, colorOf(panel, "Prefer mine"));
        assertEquals("/wp upload xaero force local delete", clickOf(panel, "Mirror"));
        assertEquals(NamedTextColor.RED, colorOf(panel, "Mirror"));
        assertEquals("Make the server match your map mod\nServer waypoints your map mod doesn't have are removed\n"
                + "Press Enter to confirm", tooltipOf(panel, "Mirror"));
    }

    @Test
    void mirrorNeedsTheDeletePermission() {
        Viewer uploader = new Viewer(EnumSet.of(Viewer.Permission.UPLOAD), true, false, Fixtures.OVERWORLD,
                new WaypointPos(0, 64, 0), 0F);

        assertEquals("Xaero's Minimap  Merge · Prefer mine", lines(UploadScreens.panel(uploader)).get(1));
    }

    @Test
    void theOutcomeShowsTheNonZeroCountsAndOffersToPreferMine() {
        Component result = UploadScreens.result(new UploadScreens.Outcome(UploadTarget.XAERO, 3, 0, 0, 9, 2, 0, 0, false,
                false, "/wp upload xaero force local", "/wp upload xaero"));

        assertEquals(List.of(
                "✔ Uploaded from Xaero's Minimap",
                "3 added · 9 unchanged · 2 conflicts",
                "2 conflicts kept the server's version.  Prefer mine"), lines(result));
        assertEquals("/wp upload xaero force local", clickOf(result, "Prefer mine"));
        assertEquals("Same name, different properties", tooltipOf(result, "2 conflicts"));
    }

    @Test
    void aStoppedUploadSaysSoAndOffersToTryAgain() {
        Component result = UploadScreens.result(new UploadScreens.Outcome(UploadTarget.VOXELMAP, 1, 2, 3, 0, 0, 4, 2, true,
                true, null, "/wp upload voxelmap force local delete"));

        assertEquals(List.of(
                "✘ Upload from VoxelMap stopped early",
                "1 added · 2 replaced · 3 removed · 4 skipped",
                "2 dimensions changed meanwhile; not updated. Try again",
                "✘ Some uploaded waypoints couldn't be saved to disk."), lines(result));
        assertEquals("/wp upload voxelmap force local delete", clickOf(result, "Try again"));
        assertEquals(List.of("✔ Uploaded from VoxelMap", "Nothing changed"), lines(UploadScreens.result(
                new UploadScreens.Outcome(UploadTarget.VOXELMAP, 0, 0, 0, 0, 0, 0, 0, false, false, null, "/wp upload voxelmap"))));
    }

    @Test
    void requestsAndDownloadsAreOneLineEach() {
        assertEquals("Asking your map mod for its waypoints…", render(UploadScreens.requested(false)));
        assertEquals(List.of("Asking your map mod for its waypoints…", "Server waypoints it doesn't have will be removed."),
                lines(UploadScreens.requested(true)));
        assertEquals("✔ Sent 12 waypoints to your map mod", render(Results.sent(12)));
        assertEquals("✔ Sent 1 waypoint to your map mod", render(Results.sent(1)));
    }
}
