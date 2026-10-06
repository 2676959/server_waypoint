package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaypointIconPickerTest {
    private static final List<NamespacedId> ITEMS = List.of(
            NamespacedId.parse("minecraft:diamond"),
            NamespacedId.parse("minecraft:diamond_sword"),
            NamespacedId.parse("examplemod:ruby_sword"),
            NamespacedId.parse("voxelmap:star"));

    @Test
    void voxelMapIconsRequireMatchingTheirNamespace() {
        assertEquals(List.of(), WaypointIconPicker.filter(ITEMS, "star"));
        assertEquals(List.of(ITEMS.get(3)), WaypointIconPicker.filter(ITEMS, "vox"));
        assertEquals(List.of(ITEMS.get(3)), WaypointIconPicker.filter(ITEMS, "voxelmap:st"));
        assertEquals(List.of(ITEMS.get(2), ITEMS.get(0), ITEMS.get(1)),
                WaypointIconPicker.filter(ITEMS, ""));
    }

    @Test
    void resolvesOnlyCompleteKnownIdsAndDefaultsBareIdsToMinecraft() {
        assertEquals(ITEMS.get(0), WaypointIconPicker.resolveInput(ITEMS, "diamond"));
        assertEquals(ITEMS.get(0), WaypointIconPicker.resolveInput(ITEMS, "minecraft:diamond"));
        assertEquals(ITEMS.get(3), WaypointIconPicker.resolveInput(ITEMS, "voxelmap:star"));
        assertNull(WaypointIconPicker.resolveInput(ITEMS, "diam"));
        assertNull(WaypointIconPicker.resolveInput(ITEMS, "star"));
        assertNull(WaypointIconPicker.resolveInput(ITEMS, "minecraft:missing"));
        assertNull(WaypointIconPicker.resolveInput(ITEMS, "DIAMOND"));
        assertNull(WaypointIconPicker.resolveInput(ITEMS, "minecraft::diamond"));
        assertNull(WaypointIconPicker.resolveInput(ITEMS, ""));
    }

    @Test
    void validUnfocusedInputUsesTheNormalFieldColor() {
        assertEquals(WidgetThemeState.text(true), WaypointIconPicker.inputColor(ITEMS, "diamond", false));
        assertEquals(WidgetThemeState.text(true), WaypointIconPicker.inputColor(ITEMS, "voxelmap:star", false));
    }

    @Test
    void colorsValidIdsYellowAndInvalidOrPartialIdsRed() {
        assertEquals(0xFFFFFF55, WaypointIconPicker.inputColor(ITEMS, "diamond", true));
        assertEquals(0xFFFFFF55, WaypointIconPicker.inputColor(ITEMS, "voxelmap:star", true));
        for (boolean focused : new boolean[]{true, false}) {
            assertEquals(0xFFFF5555, WaypointIconPicker.inputColor(ITEMS, "diam", focused));
            assertEquals(0xFFFF5555, WaypointIconPicker.inputColor(ITEMS, "voxelmap:", focused));
            assertEquals(0xFFFF5555, WaypointIconPicker.inputColor(ITEMS, "missing", focused));
            assertEquals(WidgetThemeState.text(true), WaypointIconPicker.inputColor(ITEMS, "", focused));
        }
    }

    @Test
    void previewsHoveredChoiceThenReturnsToTheCurrentInput() {
        assertEquals(ITEMS.get(3), WaypointIconPicker.previewId(ITEMS, "diamond", "voxelmap:star"));
        assertEquals(ITEMS.get(0), WaypointIconPicker.previewId(ITEMS, "diamond", null));
        assertEquals(ITEMS.get(1), WaypointIconPicker.previewId(ITEMS, "diam", "minecraft:diamond_sword"));
        assertNull(WaypointIconPicker.previewId(ITEMS, "diam", null));
        assertNull(WaypointIconPicker.previewId(ITEMS, "", null));
    }

    @Test
    void matchesItemNamesWithoutRequiringTheNamespace() {
        assertEquals(ITEMS.subList(0, 2), WaypointIconPicker.filter(ITEMS, "DIAM"));
    }

    @Test
    void matchesWordsAfterUnderscoresLikeVanillaItemSuggestions() {
        assertEquals(List.of(ITEMS.get(2), ITEMS.get(1)), WaypointIconPicker.filter(ITEMS, "sword"));
        assertEquals(List.of(), WaypointIconPicker.filter(ITEMS, "amond"));
        assertEquals(List.of(), WaypointIconPicker.filter(ITEMS, "word"));
    }

    @Test
    void matchesNamespacesAndExplicitNamespacedQueries() {
        assertEquals(ITEMS.subList(0, 2), WaypointIconPicker.filter(ITEMS, "mine"));
        assertEquals(ITEMS.subList(0, 2), WaypointIconPicker.filter(ITEMS, "minecraft:diam"));
        assertEquals(List.of(), WaypointIconPicker.filter(ITEMS, "craft:diam"));
        assertEquals(List.of(), WaypointIconPicker.filter(ITEMS, "minecraft:sword"));
    }

    @Test
    void filtersCaseInsensitivelyWithoutDuplicates() {
        var diamond = NamespacedId.parse("minecraft:diamond");
        var gem = NamespacedId.parse("examplemod:gem");
        var star = NamespacedId.parse("voxelmap:star");
        assertEquals(List.of(gem), WaypointIconPicker.filter(List.of(diamond, gem, star, gem), "GEM"));
    }
}
