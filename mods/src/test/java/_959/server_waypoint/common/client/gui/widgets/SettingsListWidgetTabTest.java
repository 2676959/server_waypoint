package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tab and Shift-Tab through a scrolled {@link SettingsListWidget}. Vanilla skips hidden widgets, so
 * without {@link SettingsListWidget#revealTabTarget} it jumps past the rows out of view.
 */
class SettingsListWidgetTabTest {
    // A row holding a 13-pixel button is 21 pixels high: the button plus 4 above and below, at least 21.
    private static final int ROW_HEIGHT = 21;
    // A wide row holding a 13-pixel button is 19 pixels high: the button plus 3 above and below, at least 17.
    private static final int WIDE_ROW_HEIGHT = 19;

    @Test
    void tabFromDoneWrapsAroundToTheFirstRowWhileTheListIsScrolledToTheEnd() {
        TestScreen screen = new TestScreen();
        screen.list.setScrollY(screen.list.getMaxScroll());
        screen.setFocused(screen.done);

        screen.pressTab(true);

        assertSame(screen.rows[0], screen.getFocused());
    }

    @Test
    void tabFromDoneWrapsAroundToTheFirstWideRowWhileTheListIsScrolledToTheEnd() {
        TestScreen screen = new TestScreen(true);
        screen.list.setScrollY(screen.list.getMaxScroll());
        screen.setFocused(screen.done);

        screen.pressTab(true);

        assertSame(screen.rows[0], screen.getFocused());
    }

    @Test
    void shiftTabFromTheFooterReachesTheLastRowWhileTheListIsAtTheTop() {
        TestScreen screen = new TestScreen();
        screen.setFocused(screen.reset);

        screen.pressTab(false);

        assertSame(screen.rows[4], screen.getFocused());
    }

    @Test
    void tabReachesTheNextRowWhenAClickFocusedTheLastRowInView() {
        TestScreen screen = new TestScreen();
        screen.setFocused(screen.rows[1]);

        screen.pressTab(true);

        assertSame(screen.rows[2], screen.getFocused());
    }

    @Test
    void tabSkipsRowsWhoseWidgetsAreInactive() {
        TestScreen screen = new TestScreen();
        screen.rows[2].active = false;
        screen.rows[3].active = false;
        screen.setFocused(screen.rows[1]);

        screen.pressTab(true);

        assertSame(screen.rows[4], screen.getFocused());
    }

    @Test
    void tabWithNothingFocusedStartsAtTheFirstRowWhileTheListIsScrolledToTheEnd() {
        TestScreen screen = new TestScreen();
        screen.list.setScrollY(screen.list.getMaxScroll());

        screen.pressTab(true);

        assertSame(screen.rows[0], screen.getFocused());
    }

    @Test
    void shiftTabFromTheFirstRowWrapsAroundToDoneWithoutScrolling() {
        TestScreen screen = new TestScreen();
        screen.setFocused(screen.rows[0]);

        screen.pressTab(false);

        assertSame(screen.done, screen.getFocused());
        assertEquals(0.0, screen.list.getScrollY());
    }

    @Test
    void tabFromTheLastRowMovesToTheFooterWithoutScrolling() {
        TestScreen screen = new TestScreen();
        screen.list.setScrollY(screen.list.getMaxScroll());
        double scroll = screen.list.getScrollY();
        screen.setFocused(screen.rows[4]);

        screen.pressTab(true);

        assertSame(screen.reset, screen.getFocused());
        assertEquals(scroll, screen.list.getScrollY());
    }

    private static IconButton button() {
        return new IconButton(0, 0, 13, 13, Component.literal("Button"), null, () -> {
        });
    }

    /**
     * Five rows in a list two rows high, then Reset and Done, registered in that order as
     * {@code ClientConfigScreen} registers its widgets. With {@code wideRows}, each button sits in a
     * one-button {@link WidgetStack} in a {@link SettingsListWidget.WideRow} instead of a labelled row.
     */
    private static final class TestScreen extends AbstractContainerEventHandler {
        private final IconButton[] rows = {button(), button(), button(), button(), button()};
        private final IconButton reset = button();
        private final IconButton done = button();
        private final SettingsListWidget list = new SettingsListWidget(new TestFont());
        private final List<GuiEventListener> children = new ArrayList<>();

        private TestScreen() {
            this(false);
        }

        private TestScreen(boolean wideRows) {
            List<SettingsListWidget.Entry> entries = new ArrayList<>();
            for (IconButton row : this.rows) {
                if (wideRows) {
                    WidgetStack control = new WidgetStack(0, 0, 2, LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD);
                    control.addClickable(row);
                    entries.add(new SettingsListWidget.WideRow(control));
                } else {
                    entries.add(new SettingsListWidget.Row(Component.literal("Row"), row));
                }
            }
            this.list.setEntries(entries);
            this.list.setWidth(200);
            this.list.setHeight((wideRows ? WIDE_ROW_HEIGHT : ROW_HEIGHT) * 2);
            this.list.visitWidgets(this.children::add);
            this.children.add(this.reset);
            this.children.add(this.done);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        /** What the screen does for Tab, then what vanilla does: move to the next stop, wrapping around. */
        private void pressTab(boolean forward) {
            this.list.revealTabTarget(this, forward);
            FocusNavigationEvent.TabNavigation tab = new FocusNavigationEvent.TabNavigation(forward);
            ComponentPath path = this.nextFocusPath(tab);
            if (path == null) {
                this.setFocused(null);
                path = this.nextFocusPath(tab);
            }
            if (path != null) {
                this.setFocused(null);
                path.applyFocus(true);
            }
        }
    }
}
