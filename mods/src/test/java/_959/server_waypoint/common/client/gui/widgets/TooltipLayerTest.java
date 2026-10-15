//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.layout.Padding;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TooltipLayerTest {
    private static final ScreenRectangle CONTROL = new ScreenRectangle(100, 50, 40, 20);
    private static final ScreenRectangle VISUAL = new ScreenRectangle(99, 49, 42, 22);

    @BeforeEach
    @AfterEach
    void clearLayer() {
        TooltipLayer.clear();
    }

    @Test
    void firstPointerRequestWins() {
        TooltipLayer.scheduleAtPointer(Component.literal("first"), 10, 20);
        TooltipLayer.scheduleAtPointer(Component.literal("second"), 30, 40);
        assertEquals(new TooltipLayer.Request(Component.literal("first"), TooltipLayer.Anchor.POINTER, 10, 20, null),
                TooltipLayer.scheduled());
    }

    @Test
    void focusedControlReplacesAnEarlierRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, false, true, true, 10, 20);
        assertEquals(new TooltipLayer.Request(Component.literal("control"),
                TooltipLayer.Anchor.BELOW_OR_ABOVE_CONTROL, 10, 20, CONTROL), TooltipLayer.scheduled());
    }

    @Test
    void unfocusedControlDoesNotReplaceAnEarlierRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, true, false, false, 110, 60);
        assertEquals(Component.literal("pointer"), TooltipLayer.scheduled().text());
    }

    @Test
    void pointerRequestDoesNotReplaceAControlRequest() {
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, true, false, false, 110, 60);
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        assertEquals(new TooltipLayer.Request(Component.literal("control"),
                TooltipLayer.Anchor.BESIDE_CONTROL, 110, 60, CONTROL), TooltipLayer.scheduled());
    }

    @Test
    void focusedControlWithoutKeyboardNavigationRequestsNothing() {
        TooltipLayer.scheduleForControl(Component.literal("control"), CONTROL, false, true, false, 10, 20);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void clearDropsTheRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipLayer.clear();
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void renderDrawsNothingWithoutARequestOrForEmptyText() {
        // A null context fails on the first draw call, so returning normally means nothing was drawn.
        TooltipLayer.render(null, new TestFont(), 320, 240);
        TooltipLayer.scheduleAtPointer(Component.empty(), 10, 20);
        TooltipLayer.render(null, new TestFont(), 320, 240);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void renderDropsTheRequestEvenWhenDrawingFails() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        assertThrows(NullPointerException.class, () -> TooltipLayer.render(null, new TestFont(), 320, 240));
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void hoveredControlRequestsItsTooltipBesideItsVisualBounds() {
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.hover();
        widget.scheduleTooltip(110, 60);
        assertEquals(new TooltipLayer.Request(Component.literal("tip"), TooltipLayer.Anchor.BESIDE_CONTROL, 110, 60, VISUAL),
                TooltipLayer.scheduled());
    }

    @Test
    void keyboardFocusedControlRequestsItsTooltipBelowOrAbove() {
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.setFocused(true);
        widget.keyboardNavigating = true;
        widget.scheduleTooltip(10, 20);
        assertEquals(new TooltipLayer.Request(Component.literal("tip"),
                TooltipLayer.Anchor.BELOW_OR_ABOVE_CONTROL, 10, 20, VISUAL), TooltipLayer.scheduled());
    }

    @Test
    void mouseFocusedControlThatIsNotHoveredRequestsNothing() {
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.setFocused(true);
        widget.scheduleTooltip(10, 20);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void controlWithoutATooltipNeverReadsTheInputState() {
        TooltipWidget widget = new TooltipWidget();
        widget.readingInputFails = true;
        widget.hover();
        widget.setFocused(true);
        widget.scheduleTooltip(110, 60);
        widget.setTooltip(Component.literal("tip"));
        widget.setTooltip((Component) null);
        widget.scheduleTooltip(110, 60);
        assertNull(TooltipLayer.scheduled());
    }

    @Test
    void inactiveControlStillShowsItsTooltip() {
        TooltipWidget widget = new TooltipWidget();
        widget.active = false;
        widget.setTooltip(Component.literal("Why it is disabled"));
        widget.hover();
        widget.scheduleTooltip(110, 60);
        assertEquals(Component.literal("Why it is disabled"), TooltipLayer.scheduled().text());
    }

    @Test
    void controlFocusedByAClickAndHoveredReplacesAnEarlierRequest() {
        TooltipLayer.scheduleAtPointer(Component.literal("pointer"), 10, 20);
        TooltipWidget widget = new TooltipWidget();
        widget.setTooltip(Component.literal("tip"));
        widget.hover();
        widget.setFocused(true);
        widget.scheduleTooltip(110, 60);
        assertEquals(new TooltipLayer.Request(Component.literal("tip"), TooltipLayer.Anchor.BESIDE_CONTROL, 110, 60, VISUAL),
                TooltipLayer.scheduled());
    }

    /**
     * A control at (100, 50), 40 by 20, whose visual bounds are one pixel outside its content, like
     * {@link ColorSquareButton}'s. Tests set its hover and focus, and its keyboard state is a field.
     */
    private static final class TooltipWidget extends ShiftableClickableWidget implements Padding {
        boolean keyboardNavigating;
        boolean readingInputFails;

        private TooltipWidget() {
            super(100, 50, 40, 20, Component.literal("Control"));
            this.setX(100);
            this.setY(50);
        }

        void hover() {
            this.isHovered = true;
        }

        @Override
        protected boolean isKeyboardNavigating() {
            if (this.readingInputFails) {
                throw new AssertionError("The widget read the game's input state");
            }
            return this.keyboardNavigating;
        }

        @Override
        public int getVisualX() {
            return this.getX() - 1;
        }

        @Override
        public int getVisualY() {
            return this.getY() - 1;
        }

        @Override
        public int getVisualWidth() {
            return this.width + 2;
        }

        @Override
        public int getVisualHeight() {
            return this.height + 2;
        }

        @Override
        public void
        //$ render_widget_method_swap
        extractWidgetRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }
}
