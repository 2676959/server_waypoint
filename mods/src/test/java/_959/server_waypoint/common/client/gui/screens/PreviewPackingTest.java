package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.PreviewPacking.Placement;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The gaps are the preview's: 6 pixels between the samples of a family and 12 between families. */
class PreviewPackingTest {
    @Test
    void familiesShareARowWhenTheyFit() {
        assertEquals(List.of(List.of(new Placement(0, 0, 0), new Placement(0, 1, 46), new Placement(1, 0, 98))),
                PreviewPacking.pack(List.of(List.of(40, 40), List.of(30)), 200, 6, 12));
    }

    @Test
    void aFamilyThatDoesNotFitTheRestOfTheRowStartsANewRow() {
        assertEquals(List.of(List.of(new Placement(0, 0, 0)), List.of(new Placement(1, 0, 0))),
                PreviewPacking.pack(List.of(List.of(100), List.of(100)), 150, 6, 12));
    }

    @Test
    void aFamilyWiderThanARowWrapsItsSamplesAndTheNextFamilyCanFollow() {
        assertEquals(List.of(
                        List.of(new Placement(0, 0, 0), new Placement(0, 1, 70)),
                        List.of(new Placement(0, 2, 0), new Placement(1, 0, 42))),
                PreviewPacking.pack(List.of(List.of(64, 30, 30), List.of(40)), 100, 6, 12));
    }

    @Test
    void aSampleWiderThanTheRowGetsARowOfItsOwn() {
        assertEquals(List.of(List.of(new Placement(0, 0, 0)), List.of(new Placement(0, 1, 0)),
                        List.of(new Placement(1, 0, 0))),
                PreviewPacking.pack(List.of(List.of(20, 112), List.of(30)), 100, 6, 12));
    }

    @Test
    void aFamilyWiderThanARowDoesNotShareTheRowBeforeIt() {
        // The first row ends at 20, so the 64 would fit after it at 32, but the 136 wide family doesn't.
        assertEquals(List.of(
                        List.of(new Placement(0, 0, 0)),
                        List.of(new Placement(1, 0, 0), new Placement(1, 1, 70)),
                        List.of(new Placement(1, 2, 0))),
                PreviewPacking.pack(List.of(List.of(20), List.of(64, 30, 30)), 100, 6, 12));
    }

    @Test
    void aFamilyCountsTheGapsBetweenItsSamplesWhenItAsksForRoom() {
        // The family is 86 wide with the gap between its samples, and 40 + 12 + 86 is 138, past the row's 134.
        assertEquals(List.of(List.of(new Placement(0, 0, 0)),
                        List.of(new Placement(1, 0, 0), new Placement(1, 1, 46))),
                PreviewPacking.pack(List.of(List.of(40), List.of(40, 40)), 134, 6, 12));
    }

    @Test
    void aFamilyThatFillsTheRestOfTheRowExactlyStaysOnIt() {
        // 40 + 12 + 40 is 92.
        assertEquals(List.of(List.of(new Placement(0, 0, 0), new Placement(1, 0, 52))),
                PreviewPacking.pack(List.of(List.of(40), List.of(40)), 92, 6, 12));
        assertEquals(List.of(List.of(new Placement(0, 0, 0)), List.of(new Placement(1, 0, 0))),
                PreviewPacking.pack(List.of(List.of(40), List.of(40)), 91, 6, 12));
    }
}
