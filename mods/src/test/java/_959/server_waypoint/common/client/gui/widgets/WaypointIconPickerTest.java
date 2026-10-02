package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointIconPickerTest {
    @Test
    void filtersCaseInsensitivelyWithoutDuplicates() {
        var diamond = NamespacedId.parse("minecraft:diamond");
        var gem = NamespacedId.parse("examplemod:gem");
        var star = NamespacedId.parse("voxelmap:star");
        assertEquals(List.of(gem), WaypointIconPicker.filter(List.of(diamond, gem, star, gem), "GEM"));
    }
}
