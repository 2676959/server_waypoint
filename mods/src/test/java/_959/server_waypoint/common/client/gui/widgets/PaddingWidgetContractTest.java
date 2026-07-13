package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Padding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaddingWidgetContractTest {
    @Test
    void outlineAndBackgroundWidgetsImplementPadding() {
        assertPadding(TranslucentButton.class);
        assertPadding(ToggleButton.class);
        assertPadding(TranslucentTextField.class);
        assertPadding(ColorHexCodeField.class);
        assertPadding(IntegerField.class);
        assertPadding(CoordinateField.class);
        assertPadding(WaypointSearchBarWidget.class);
        assertPadding(ColorSquareButton.class);
        assertPadding(SwatchWidget.class);
        assertPadding(DialogWidget.class);
        assertPadding(ConfirmationDialog.class);
        assertPadding(DimensionListWidget.class);
        assertPadding(TreeViewWidget.class);
    }

    private static void assertPadding(Class<?> widgetClass) {
        assertTrue(Padding.class.isAssignableFrom(widgetClass), widgetClass.getSimpleName());
    }
}
