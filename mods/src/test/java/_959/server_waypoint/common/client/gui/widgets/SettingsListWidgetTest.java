//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.TestFont;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which widgets a row owns, including every widget of a composite control, and which rows
 * {@link SettingsListWidget#reveal} brings into view as the neighbors of a focused row. Vanilla Tab
 * skips inactive widgets, so a row whose widgets are all inactive can't be the next stop. A
 * composite control with a widget not fully in view is drawn whole, clipped, with the mouse
 * off-screen.
 */
class SettingsListWidgetTest {
    @Test
    void conditionalActionTracksValuesAndStaysHiddenAfterRelayoutAndScrolling() {
        AtomicBoolean differsFromDefault = new AtomicBoolean(false);
        IconButton reset = button(true);
        SettingsListWidget.Row row = row(button(false), null)
                .action(reset, differsFromDefault::get);
        SettingsListWidget list = new SettingsListWidget(new TestFont());
        list.setWidth(200);
        list.setHeight(80);
        list.setEntries(List.of(row));

        assertFalse(reset.visible);
        assertFalse(row.isInteractive());
        assertFalse(row.isOverAction(reset.getX(), reset.getY()));

        differsFromDefault.set(true);
        list.refreshWidgetVisibility();
        assertTrue(reset.visible);
        assertTrue(row.isInteractive());
        int controlX = reset.getX();

        differsFromDefault.set(false);
        list.refreshWidgetVisibility();
        list.relayout();
        list.setScrollY(0);
        assertFalse(reset.visible);
        assertFalse(row.isInteractive());
        assertEquals(controlX, reset.getX());

        differsFromDefault.set(true);
        list.refreshWidgetVisibility();
        assertTrue(reset.visible);
        assertEquals(controlX, reset.getX());
    }

    @Test
    void aRowWithAnActiveControlCanTakeFocus() {
        assertTrue(row(button(true), null).isInteractive());
    }

    @Test
    void aRowWhoseControlAndActionAreInactiveCannotTakeFocus() {
        assertFalse(row(button(false), button(false)).isInteractive());
    }

    @Test
    void anActiveActionLetsARowWithAnInactiveControlTakeFocus() {
        assertTrue(row(button(false), button(true)).isInteractive());
    }

    @Test
    void aRowOwnsEveryWidgetInACompositeControl() {
        IconButton first = button(true);
        IconButton second = button(true);
        SettingsListWidget.Row row = new SettingsListWidget.Row(Component.literal("Row"), composite(first, second));

        assertTrue(row.owns(first));
        assertTrue(row.owns(second));
    }

    @Test
    void aCompositeControlLetsItsRowTakeFocusWhileOneOfItsWidgetsIsActive() {
        IconButton inactive = button(false);
        IconButton active = button(true);
        SettingsListWidget.Row row = new SettingsListWidget.Row(Component.literal("Row"), composite(inactive, active));

        assertTrue(row.isInteractive());
        active.active = false;
        assertFalse(row.isInteractive());
    }

    @Test
    void aCompositeControlWithAClippedWidgetIsDrawnWholeWithTheMouseOffScreen() {
        IconButton clipped = button(true);
        IconButton shown = button(true);
        RecordingStack control = new RecordingStack(clipped, shown);
        // What Row.position does to a widget that isn't fully inside the viewport.
        clipped.visible = false;

        SettingsListWidget.renderPart(null, control, 20, 30, 0.0F);

        assertEquals(List.of(new Draw(List.of(true, true), NO_MOUSE, NO_MOUSE)), control.draws);
        assertFalse(clipped.visible);
        assertTrue(shown.visible);
    }

    @Test
    void aCompositeControlWithNoClippedWidgetIsDrawnWithTheMouse() {
        RecordingStack control = new RecordingStack(button(true), button(true));

        SettingsListWidget.renderPart(null, control, 20, 30, 0.0F);

        assertEquals(List.of(new Draw(List.of(true, true), 20, 30)), control.draws);
    }

    private static WidgetStack composite(IconButton... buttons) {
        WidgetStack stack = new WidgetStack(0, 0, 2, LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD);
        for (IconButton button : buttons) {
            stack.addClickable(button);
        }
        return stack;
    }

    private static SettingsListWidget.Row row(IconButton control, @Nullable IconButton action) {
        SettingsListWidget.Row row = new SettingsListWidget.Row(Component.literal("Row"), control);
        if (action != null) {
            row.action(action);
        }
        return row;
    }

    private static IconButton button(boolean active) {
        IconButton button = new IconButton(0, 0, 13, 13, Component.literal("Button"), null, () -> {
        });
        button.active = active;
        return button;
    }

    /** A composite control of buttons that records what each draw sees instead of drawing. */
    private static final class RecordingStack extends WidgetStack {
        private final List<Draw> draws = new ArrayList<>();

        private RecordingStack(IconButton... buttons) {
            super(0, 0, 2, LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD);
            for (IconButton button : buttons) {
                this.addClickable(button);
            }
        }

        @Override
        public void
        //$ render_method_swap
        extractRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            List<Boolean> visible = new ArrayList<>();
            this.visitWidgets(widget -> visible.add(widget.visible));
            this.draws.add(new Draw(visible, mouseX, mouseY));
        }
    }

    /** One draw of a control: whether each of its widgets was visible, and the mouse position it got. */
    private record Draw(List<Boolean> visible, int mouseX, int mouseY) {
    }
}
