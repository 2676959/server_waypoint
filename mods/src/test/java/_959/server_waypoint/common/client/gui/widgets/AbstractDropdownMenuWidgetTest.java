//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractDropdownMenuWidgetTest {
    @ParameterizedTest
    @MethodSource("expansionCases")
    void laysOutItemsInEveryExpansionDirection(
            LayoutFlow.Orientation orientation,
            LayoutFlow.Direction direction,
            int firstX,
            int firstY,
            int secondX,
            int secondY
    ) {
        TestDropdown dropdown = new TestDropdown(100, 100, 20, 20, orientation, direction, 3);
        TestMenuItem first = dropdown.addItem(10, 8, () -> {
        });
        TestMenuItem second = dropdown.addItem(12, 6, () -> {
        });

        assertPosition(first, firstX, firstY);
        assertPosition(second, secondX, secondY);
    }

    @ParameterizedTest
    @MethodSource("expansionCases")
    void selectedMiddleItemIsOmittedWithoutLeavingALayoutGap(
            LayoutFlow.Orientation orientation,
            LayoutFlow.Direction direction,
            int firstX,
            int firstY,
            int secondX,
            int secondY
    ) {
        TestDropdown dropdown = new TestDropdown(100, 100, 20, 20, orientation, direction, 3);
        TestMenuItem first = dropdown.addItem(10, 8, () -> {
        });
        dropdown.addItem(30, 30, () -> {
        });
        TestMenuItem remaining = dropdown.addItem(12, 6, () -> {
        });
        dropdown.selectedMenuItemIndex = 1;

        dropdown.setExpanded(true);

        assertEquals(2, dropdown.getPopupItemCount());
        assertPosition(first, firstX, firstY);
        assertPosition(remaining, secondX, secondY);
    }

    @ParameterizedTest
    @MethodSource("flowDirections")
    void expandedItemBoundsParticipateInHitTesting(
            LayoutFlow.Orientation orientation,
            LayoutFlow.Direction direction
    ) {
        TestDropdown dropdown = new TestDropdown(40, 50, 20, 20, orientation, direction, 2);
        TestMenuItem first = dropdown.addItem(10, 10, () -> {
        });
        TestMenuItem second = dropdown.addItem(10, 10, () -> {
        });

        assertFalse(dropdown.isMouseOver(centerX(first), centerY(first)));
        dropdown.setExpanded(true);
        assertTrue(dropdown.isMouseOver(centerX(first), centerY(first)));
        assertTrue(dropdown.isMouseOver(centerX(second), centerY(second)));
        assertFalse(dropdown.isMouseOver(-100, -100));

        dropdown.active = false;
        assertFalse(dropdown.isMouseOver(centerX(first), centerY(first)));
        dropdown.active = true;
        dropdown.visible = false;
        assertFalse(dropdown.isMouseOver(centerX(first), centerY(first)));
    }

    @Test
    void triggerTogglesAndSelectingAnItemClosesTheMenu() {
        AtomicInteger selections = new AtomicInteger();
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.REVERSE,
                2
        );
        TestMenuItem item = dropdown.addItem(16, 16, selections::incrementAndGet);

        assertTrue(dropdown.mouseClicked(18, 28, 0));
        assertTrue(dropdown.isExpanded());
        assertEquals(-1, dropdown.getHighlightedItemIndex());
        assertFalse(item.isFocused());

        assertTrue(dropdown.mouseClicked(centerX(item), centerY(item), 0));
        assertEquals(1, selections.get());
        assertFalse(dropdown.isExpanded());
    }

    @Test
    void mouseOpeningDoesNotPreHighlightAnyRemainingItem() {
        AtomicInteger selections = new AtomicInteger();
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        TestMenuItem first = dropdown.addItem(16, 16, selections::incrementAndGet);
        TestMenuItem selected = dropdown.addItem(16, 16, selections::incrementAndGet);
        TestMenuItem last = dropdown.addItem(16, 16, selections::incrementAndGet);
        dropdown.selectedMenuItemIndex = 1;

        assertTrue(dropdown.mouseClicked(18, 28, 0));

        assertTrue(dropdown.isExpanded());
        assertEquals(-1, dropdown.getHighlightedItemIndex());
        assertFalse(first.isFocused());
        assertFalse(selected.isFocused());
        assertFalse(last.isFocused());
        assertFalse(dropdown.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(0, selections.get());
        assertTrue(dropdown.isExpanded());
    }

    @Test
    void invalidClicksAndHiddenItemsDoNotSelect() {
        AtomicInteger selections = new AtomicInteger();
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        TestMenuItem item = dropdown.addItem(16, 16, selections::incrementAndGet);

        assertFalse(dropdown.mouseClicked(18, 28, 1));
        assertFalse(dropdown.mouseClicked(centerX(item), centerY(item), 0));
        dropdown.setExpanded(true);
        item.visible = false;
        assertFalse(dropdown.isMouseOver(centerX(item), centerY(item)));
        assertFalse(dropdown.mouseClicked(centerX(item), centerY(item), 0));
        assertEquals(0, selections.get());
    }

    @Test
    void movingAndOffsettingTheDropdownRelayoutsItems() {
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        TestMenuItem item = dropdown.addItem(12, 10, () -> {
        });

        dropdown.setPosition(40, 50);
        assertPosition(item, 58, 53);
        dropdown.setXOffset(5);
        dropdown.setYOffset(-3);
        assertEquals(45, dropdown.getX());
        assertEquals(47, dropdown.getY());
        assertPosition(item, 63, 50);
    }

    @Test
    void emptyDropdownCannotOpen() {
        TestDropdown dropdown = new TestDropdown(
                0,
                0,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                0
        );

        dropdown.setExpanded(true);

        assertFalse(dropdown.isExpanded());
        assertEquals(0, dropdown.expandedChangeCount);
    }

    @Test
    void dropdownWithOnlyTheSelectedItemCannotOpen() {
        TestDropdown dropdown = new TestDropdown(
                0,
                0,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                0
        );
        dropdown.addItem(16, 16, () -> {
        });
        dropdown.selectedMenuItemIndex = 0;

        dropdown.setExpanded(true);

        assertEquals(0, dropdown.getPopupItemCount());
        assertFalse(dropdown.isExpanded());
        assertEquals(0, dropdown.expandedChangeCount);
    }

    @Test
    void closeMenuIfOutsideKeepsInsideClicksAndClosesForOutsideClicks() {
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        TestMenuItem item = dropdown.addItem(16, 16, () -> {
        });
        dropdown.setExpanded(true);

        assertFalse(dropdown.closeMenuIfOutside(18, 28));
        assertFalse(dropdown.closeMenuIfOutside(centerX(item), centerY(item)));
        assertTrue(dropdown.closeMenuIfOutside(-100, -100));
        assertFalse(dropdown.isExpanded());
        assertEquals(2, dropdown.expandedChangeCount);
    }

    @Test
    void keyboardActivationTogglesAndEscapeCloses() {
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        dropdown.addItem(16, 16, () -> {
        });

        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(dropdown.isExpanded());
        assertEquals(0, dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertFalse(dropdown.isExpanded());
        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertTrue(dropdown.isExpanded());
        assertEquals(0, dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_KP_ENTER, 0, 0));
        assertFalse(dropdown.isExpanded());
        assertEquals(4, dropdown.expandedChangeCount);
    }

    @ParameterizedTest
    @MethodSource("flowDirections")
    void arrowNavigationFollowsTheVisualExpansionDirection(
            LayoutFlow.Orientation orientation,
            LayoutFlow.Direction direction
    ) {
        AtomicInteger selectedItem = new AtomicInteger(-1);
        TestDropdown dropdown = new TestDropdown(10, 20, 16, 16, orientation, direction, 2);
        dropdown.addItem(16, 16, () -> selectedItem.set(0));
        dropdown.addItem(16, 16, () -> selectedItem.set(1));
        assertTrue(dropdown.mouseClicked(18, 28, 0));

        int forwardKey = switch (orientation) {
            case HORIZONTAL -> direction == LayoutFlow.Direction.FORWARD
                    ? GLFW.GLFW_KEY_RIGHT
                    : GLFW.GLFW_KEY_LEFT;
            case VERTICAL -> direction == LayoutFlow.Direction.FORWARD
                    ? GLFW.GLFW_KEY_DOWN
                    : GLFW.GLFW_KEY_UP;
        };
        int backwardKey = switch (orientation) {
            case HORIZONTAL -> forwardKey == GLFW.GLFW_KEY_RIGHT
                    ? GLFW.GLFW_KEY_LEFT
                    : GLFW.GLFW_KEY_RIGHT;
            case VERTICAL -> forwardKey == GLFW.GLFW_KEY_DOWN
                    ? GLFW.GLFW_KEY_UP
                    : GLFW.GLFW_KEY_DOWN;
        };
        assertEquals(-1, dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(backwardKey, 0, 0));
        assertEquals(1, dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(forwardKey, 0, 0));
        assertEquals(0, dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(forwardKey, 0, 0));
        assertEquals(1, dropdown.getHighlightedItemIndex());
        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, selectedItem.get());
        assertFalse(dropdown.isExpanded());
    }

    @Test
    void popupItemCountExcludesSelectedAndHiddenItems() {
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.REVERSE,
                2
        );
        dropdown.addItem(16, 16, () -> {
        });
        dropdown.addItem(16, 16, () -> {
        });
        TestMenuItem hidden = dropdown.addItem(16, 16, () -> {
        });
        dropdown.selectedMenuItemIndex = 1;
        hidden.visible = false;

        assertEquals(1, dropdown.getPopupItemCount());
    }

    @Test
    void keyboardOpeningHighlightsTheSubclassPreferredSelectableItem() {
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.REVERSE,
                2
        );
        dropdown.addItem(16, 16, () -> {
        });
        TestMenuItem preferred = dropdown.addItem(16, 16, () -> {
        });
        dropdown.addItem(16, 16, () -> {
        });
        dropdown.initialHighlightedItemIndex = 1;

        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));

        assertEquals(1, dropdown.getHighlightedItemIndex());
        assertTrue(preferred.isFocused());
    }

    @Test
    void keyboardOpeningSkipsAnInactivePreferredItem() {
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        dropdown.addItem(16, 16, () -> {
        });
        TestMenuItem inactive = dropdown.addItem(16, 16, () -> {
        });
        dropdown.addItem(16, 16, () -> {
        });
        inactive.active = false;
        dropdown.initialHighlightedItemIndex = 1;

        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));

        assertEquals(2, dropdown.getHighlightedItemIndex());
    }

    @ParameterizedTest
    @MethodSource("flowDirections")
    void keyboardFocusAndNavigationSkipTheSelectedItem(
            LayoutFlow.Orientation orientation,
            LayoutFlow.Direction direction
    ) {
        TestDropdown dropdown = new TestDropdown(10, 20, 16, 16, orientation, direction, 2);
        TestMenuItem first = dropdown.addItem(16, 16, () -> {
        });
        TestMenuItem selected = dropdown.addItem(16, 16, () -> {
        });
        TestMenuItem last = dropdown.addItem(16, 16, () -> {
        });
        dropdown.selectedMenuItemIndex = 1;

        assertTrue(dropdown.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));

        assertEquals(0, dropdown.getHighlightedItemIndex());
        assertFalse(selected.isFocused());
        assertTrue(first.isFocused());

        int forwardKey = switch (orientation) {
            case HORIZONTAL -> direction == LayoutFlow.Direction.FORWARD
                    ? GLFW.GLFW_KEY_RIGHT
                    : GLFW.GLFW_KEY_LEFT;
            case VERTICAL -> direction == LayoutFlow.Direction.FORWARD
                    ? GLFW.GLFW_KEY_DOWN
                    : GLFW.GLFW_KEY_UP;
        };
        assertTrue(dropdown.keyPressed(forwardKey, 0, 0));
        assertEquals(2, dropdown.getHighlightedItemIndex());
        assertTrue(last.isFocused());
        assertFalse(selected.isFocused());
    }

    @Test
    void selectedItemDoesNotParticipateInMouseInteraction() {
        AtomicInteger selectedActivations = new AtomicInteger();
        TestDropdown dropdown = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        dropdown.addItem(16, 16, () -> {
        });
        TestMenuItem selected = dropdown.addItem(16, 16, selectedActivations::incrementAndGet);
        dropdown.addItem(16, 16, () -> {
        });
        dropdown.selectedMenuItemIndex = 1;
        dropdown.setExpanded(true);
        selected.setPosition(200, 200);

        assertFalse(dropdown.isMouseOver(centerX(selected), centerY(selected)));
        assertFalse(dropdown.mouseClicked(centerX(selected), centerY(selected), 0));
        assertEquals(0, selectedActivations.get());
        assertTrue(dropdown.isExpanded());
    }

    @Test
    void resizingRelayoutsForwardItemsAndRecentersTheCrossAxis() {
        TestDropdown horizontal = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        TestMenuItem horizontalItem = horizontal.addItem(10, 8, () -> {
        });
        horizontal.setWidth(30);
        horizontal.setHeight(20);
        assertPosition(horizontalItem, 42, 26);

        TestDropdown vertical = new TestDropdown(
                10,
                20,
                16,
                16,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                2
        );
        TestMenuItem verticalItem = vertical.addItem(8, 10, () -> {
        });
        vertical.setWidth(20);
        vertical.setHeight(30);
        assertPosition(verticalItem, 16, 52);
    }

    private static Stream<Arguments> expansionCases() {
        return Stream.of(
                Arguments.of(LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD, 123, 106, 136, 107),
                Arguments.of(LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.REVERSE, 87, 106, 72, 107),
                Arguments.of(LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD, 105, 123, 104, 134),
                Arguments.of(LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.REVERSE, 105, 89, 104, 80)
        );
    }

    private static Stream<Arguments> flowDirections() {
        return Stream.of(
                Arguments.of(LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD),
                Arguments.of(LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.REVERSE),
                Arguments.of(LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD),
                Arguments.of(LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.REVERSE)
        );
    }

    private static int centerX(TestMenuItem item) {
        return item.getX() + item.getWidth() / 2;
    }

    private static int centerY(TestMenuItem item) {
        return item.getY() + item.getHeight() / 2;
    }

    private static void assertPosition(TestMenuItem item, int x, int y) {
        assertEquals(x, item.getX());
        assertEquals(y, item.getY());
    }

    private static final class TestDropdown extends AbstractDropdownMenuWidget {
        private int expandedChangeCount;
        private int initialHighlightedItemIndex;
        private int selectedMenuItemIndex = -1;

        private TestDropdown(
                int x,
                int y,
                int width,
                int height,
                LayoutFlow.Orientation orientation,
                LayoutFlow.Direction direction,
                int spacing
        ) {
            super(x, y, width, height, Component.literal("Dropdown"), orientation, direction, spacing);
        }

        private TestMenuItem addItem(int width, int height, Runnable callback) {
            return this.addMenuItem(new TestMenuItem(width, height, callback));
        }

        @Override
        protected void renderDropdownControl(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
        }

        @Override
        protected void onExpandedChanged(boolean expanded) {
            this.expandedChangeCount++;
        }

        @Override
        protected int getInitialHighlightedItemIndex() {
            return this.initialHighlightedItemIndex;
        }

        @Override
        protected int getSelectedMenuItemIndex() {
            return this.selectedMenuItemIndex;
        }
    }

    private static final class TestMenuItem extends AbstractDropdownMenuWidget.AbstractMenuItem {
        private final Runnable callback;

        private TestMenuItem(int width, int height, Runnable callback) {
            super(width, height, Component.literal("Item"));
            this.callback = callback;
        }

        @Override
        protected void onSelected() {
            this.callback.run();
        }

        @Override
        protected void renderMenuItem(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
        }
    }
}
