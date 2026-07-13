package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.api.DimensionListCallback;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
