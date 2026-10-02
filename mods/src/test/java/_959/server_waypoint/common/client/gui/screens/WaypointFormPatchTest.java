package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointFormPatch.Saved;
import _959.server_waypoint.common.client.gui.screens.WaypointFormPatch.Values;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormPatchTest {
    private static final WaypointPos HERE = new WaypointPos(120, 64, -35);
    private static final NamespacedId DIAMOND = new NamespacedId("minecraft", "diamond");

    @Test
    void anUntouchedFormChangesNothing() {
        WaypointPatch patch = WaypointFormPatch.build(saved(null, null), same());

        assertFalse(WaypointFormPatch.changesAnything(patch));
    }

    @Test
    void noOverrideAndAnEmptyFieldLeavesTheDisplayNameUnchanged() {
        assertEquals(PatchField.unchanged(), WaypointFormPatch.displayName(null, ""));
    }

    @Test
    void noOverrideAndTextSetsTheOverride() {
        assertEquals(PatchField.set("Home Base"), WaypointFormPatch.displayName(null, "Home Base"));
    }

    @Test
    void anOverrideKeptAsItIsLeavesTheDisplayNameUnchanged() {
        assertEquals(PatchField.unchanged(), WaypointFormPatch.displayName("Home Base", "Home Base"));
    }

    @Test
    void anOverrideEmptiedInTheFieldClearsIt() {
        assertEquals(PatchField.clear(), WaypointFormPatch.displayName("Home Base", ""));
    }

    @Test
    void anOverrideReplacedByOtherTextSetsTheNewText() {
        assertEquals(PatchField.set("Camp"), WaypointFormPatch.displayName("Home Base", "Camp"));
    }

    @Test
    void anEmptyOverrideLeftEmptyLeavesTheDisplayNameUnchanged() {
        assertEquals(PatchField.unchanged(), WaypointFormPatch.displayName("", ""));
    }

    @Test
    void anEmptyOverrideGivenTextSetsTheText() {
        assertEquals(PatchField.set("Camp"), WaypointFormPatch.displayName("", "Camp"));
    }

    @Test
    void theDisplayNameRowsReachThePatch() {
        Values typed = new Values("Home", "Home Base", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", null);

        assertEquals(PatchField.set("Home Base"), WaypointFormPatch.build(saved(null, null), typed).displayName());
        assertEquals(PatchField.clear(), WaypointFormPatch.build(saved("Home Base", null), same()).displayName());
    }

    @Test
    void aChangedFieldIsSetAndTheRestStayUnchanged() {
        Values moved = new Values("Home", "", "H", new WaypointPos(1, 64, -35), 0xFFAA00, 0, true,
                List.of("home", "base"), "Beds upstairs", null);

        WaypointPatch patch = WaypointFormPatch.build(saved(null, null), moved);

        assertEquals(PatchField.set(new WaypointPos(1, 64, -35)), patch.position());
        assertTrue(patch.identifier().isUnchanged());
        assertTrue(patch.displayName().isUnchanged());
        assertTrue(patch.initials().isUnchanged());
        assertTrue(patch.color().isUnchanged());
        assertTrue(patch.yaw().isUnchanged());
        assertTrue(patch.visibility().isUnchanged());
        assertTrue(patch.keywords().isUnchanged());
        assertTrue(patch.description().isUnchanged());
        assertTrue(patch.icon().isUnchanged());
        assertTrue(WaypointFormPatch.changesAnything(patch));
    }

    @Test
    void everyOrdinaryFieldIsSetWhenItChanges() {
        Values changed = new Values("Camp", "", "C", HERE, 0x112233, 90, false, List.of(), "Beds", DIAMOND);

        WaypointPatch patch = WaypointFormPatch.build(saved(null, null), changed);

        assertEquals(PatchField.set("Camp"), patch.identifier());
        assertEquals(PatchField.set("C"), patch.initials());
        assertEquals(PatchField.set(0x112233), patch.color());
        assertEquals(PatchField.set(90), patch.yaw());
        assertEquals(PatchField.set(false), patch.visibility());
        assertEquals(PatchField.set("Beds"), patch.description());
        assertEquals(PatchField.set(DIAMOND), patch.icon());
    }

    @Test
    void theColorComparesWithoutItsAlpha() {
        Values opaque = new Values("Home", "", "H", HERE, 0xFFFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", null);

        assertFalse(WaypointFormPatch.changesAnything(WaypointFormPatch.build(saved(null, null), opaque)));
    }

    @Test
    void keywordsAreSetOnlyWhenTheParsedListsDiffer() {
        Saved saved = saved(null, null);

        assertTrue(WaypointFormPatch.build(saved, withKeywords(List.of("home", "base"))).keywords().isUnchanged());
        assertEquals(PatchField.set(List.of("base", "home")),
                WaypointFormPatch.build(saved, withKeywords(List.of("base", "home"))).keywords());
        assertEquals(PatchField.set(List.of()), WaypointFormPatch.build(saved, withKeywords(List.of())).keywords());
    }

    @Test
    void theDescriptionComparesAsText() {
        Saved saved = saved(null, null);

        assertTrue(WaypointFormPatch.build(saved, withDescription("Beds upstairs")).description().isUnchanged());
        assertEquals(PatchField.set(""), WaypointFormPatch.build(saved, withDescription("")).description());
    }

    @Test
    void anIconIsSetClearedOrLeftAlone() {
        Saved withIcon = saved(null, DIAMOND);

        assertTrue(WaypointFormPatch.build(withIcon, withIcon(DIAMOND)).icon().isUnchanged());
        assertEquals(PatchField.clear(), WaypointFormPatch.build(withIcon, withIcon(null)).icon());
        assertEquals(PatchField.set(DIAMOND), WaypointFormPatch.build(saved(null, null), withIcon(DIAMOND)).icon());
    }

    @Test
    void savedReadsTheOverrideRatherThanTheDisplayName() {
        SimpleWaypoint plain = new SimpleWaypoint("Home", "Home", "H", HERE, 0xFFAA00, 0, true, List.of("home"), "Beds");
        SimpleWaypoint override = new SimpleWaypoint("home", "Home Base", "H", HERE, 0xFFAA00, 0, true, List.of("home"), "Beds");
        SimpleWaypoint empty = new SimpleWaypoint("home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home"), "Beds");

        assertNull(Saved.of(plain).displayNameOverride());
        assertEquals("Home Base", Saved.of(override).displayNameOverride());
        assertEquals("", Saved.of(empty).displayNameOverride());
        assertEquals(List.of("home"), Saved.of(plain).keywords());
        assertEquals("Beds", Saved.of(plain).description());
    }

    @Test
    void theTitleShowsTheOverrideOrElseTheName() {
        assertEquals("Home", saved(null, null).titleName());
        assertEquals("Home Base", saved("Home Base", null).titleName());
        // An empty override makes the marker show no name, but the title still names the waypoint.
        assertEquals("Home", saved("", null).titleName());
    }

    private static Saved saved(String override, NamespacedId icon) {
        return new Saved("Home", override, "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", icon);
    }

    /** The form as it opens on the waypoint from {@code saved(null, null)}: nothing edited. */
    private static Values same() {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", null);
    }

    private static Values withKeywords(List<String> keywords) {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, keywords, "Beds upstairs", null);
    }

    private static Values withDescription(String description) {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), description, null);
    }

    private static Values withIcon(NamespacedId icon) {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", icon);
    }
}
