package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.api.DimensionListCallback;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DimensionIconLayoutTest {
    @Test
    void dimensionListWidgetExposesTheSharedLayoutFlowTypes() throws NoSuchMethodException {
        assertEquals(LayoutFlow.Orientation.class, DimensionListWidget.class.getMethod("getOrientation").getReturnType());
        assertEquals(LayoutFlow.Direction.class, DimensionListWidget.class.getMethod("getDirection").getReturnType());
        DimensionListWidget.class.getConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                Screen.class,
                Font.class,
                DimensionListCallback.class,
                LayoutFlow.Orientation.class,
                LayoutFlow.Direction.class
        );
        DimensionListWidget.class.getConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                Screen.class,
                Font.class,
                DimensionListCallback.class,
                LayoutFlow.Orientation.class,
                LayoutFlow.Direction.class,
                int.class
        );
        DimensionListWidget.class.getConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                Screen.class,
                Font.class,
                DimensionListCallback.class,
                LayoutFlow.Orientation.class,
                LayoutFlow.Direction.class,
                int.class,
                int.class,
                int.class
        );
    }

    @Test
    void horizontalForwardLayoutStartsAtTopLeftAndScrollsTowardTheStart() {
        DimensionIconLayout layout = new DimensionIconLayout(20, LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD);
        DimensionIconLayout.Bounds viewport = layout.viewport(240, 31, 11);

        assertEquals(new DimensionIconLayout.Bounds(0, 11, 240, 20), viewport);
        assertEquals(new DimensionIconLayout.Position(0, 11), layout.iconPosition(0, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(40, 11), layout.iconPosition(2, 0, viewport));
        assertEquals(-20, layout.scrollBy(0, -20, 13, viewport));
        assertEquals(1, layout.iconIndexAt(10, 15, -20, 13, viewport));
    }

    @Test
    void horizontalReverseLayoutStartsAtTopRightAndPreservesListOrder() {
        DimensionIconLayout layout = new DimensionIconLayout(20, LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.REVERSE);
        DimensionIconLayout.Bounds viewport = layout.viewport(240, 31, 11);

        assertEquals(new DimensionIconLayout.Position(220, 11), layout.iconPosition(0, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(200, 11), layout.iconPosition(1, 0, viewport));
        assertEquals(0, layout.iconIndexAt(225, 15, 0, 13, viewport));
        assertEquals(1, layout.iconIndexAt(205, 15, 0, 13, viewport));
        assertEquals(new DimensionIconLayout.Position(240, 11), layout.iconPosition(0, -20, viewport));
        assertEquals(1, layout.iconIndexAt(225, 15, -20, 13, viewport));
    }

    @Test
    void verticalLayoutsUseAvailableBodyHeightAsTheirMainAxis() {
        DimensionIconLayout forward = new DimensionIconLayout(20, LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        DimensionIconLayout reverse = new DimensionIconLayout(20, LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.REVERSE);
        DimensionIconLayout.Bounds viewport = forward.viewport(80, 111, 11);

        assertEquals(new DimensionIconLayout.Bounds(0, 11, 20, 100), viewport);
        assertEquals(new DimensionIconLayout.Position(0, 11), forward.iconPosition(0, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(0, 31), forward.iconPosition(1, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(0, 91), reverse.iconPosition(0, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(0, 71), reverse.iconPosition(1, 0, viewport));
        assertEquals(1, reverse.iconIndexAt(5, 75, 0, 6, viewport));
        assertEquals(-20, reverse.scrollBy(0, -100, 6, viewport));
    }

    @Test
    void scrollIsResetWhenAllIconsFitTheViewport() {
        DimensionIconLayout layout = new DimensionIconLayout(20, LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD);
        DimensionIconLayout.Bounds viewport = layout.viewport(240, 31, 11);

        assertEquals(0, layout.clampScroll(-80, 12, viewport));
        assertEquals(0, layout.clampScroll(10, 20, viewport));
    }

    @Test
    void verticalSpacingCreatesNonInteractiveGapsInBothDirections() {
        DimensionIconLayout forward = new DimensionIconLayout(
                18,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        DimensionIconLayout reverse = new DimensionIconLayout(
                18,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.REVERSE,
                2
        );
        DimensionIconLayout.Bounds viewport = forward.viewport(18, 58, 0);

        assertEquals(new DimensionIconLayout.Position(0, 0), forward.iconPosition(0, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(0, 20), forward.iconPosition(1, 0, viewport));
        assertEquals(0, forward.iconIndexAt(5, 17, 0, 3, viewport));
        assertEquals(-1, forward.iconIndexAt(5, 18, 0, 3, viewport));
        assertEquals(-1, forward.iconIndexAt(5, 19, 0, 3, viewport));
        assertEquals(1, forward.iconIndexAt(5, 20, 0, 3, viewport));

        assertEquals(new DimensionIconLayout.Position(0, 40), reverse.iconPosition(0, 0, viewport));
        assertEquals(new DimensionIconLayout.Position(0, 20), reverse.iconPosition(1, 0, viewport));
        assertEquals(1, reverse.iconIndexAt(5, 37, 0, 3, viewport));
        assertEquals(-1, reverse.iconIndexAt(5, 38, 0, 3, viewport));
        assertEquals(-1, reverse.iconIndexAt(5, 39, 0, 3, viewport));
        assertEquals(0, reverse.iconIndexAt(5, 40, 0, 3, viewport));
    }

    @Test
    void spacingParticipatesInScrollExtentAndRejectsNegativeValues() {
        DimensionIconLayout layout = new DimensionIconLayout(
                20,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD,
                4
        );
        DimensionIconLayout.Bounds viewport = layout.viewport(100, 20, 0);

        assertEquals(4, layout.iconSpacing());
        assertEquals(-16, layout.clampScroll(-100, 5, viewport));
        assertThrows(IllegalArgumentException.class, () -> new DimensionIconLayout(
                20,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD,
                -1
        ));
    }
}
