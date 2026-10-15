package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import java.util.List;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualPositioningTest {
    @Test
    void visualPlacementAccountsForDisplayOffsetsWithoutClearingThemOrDrifting() {
        ScalableText text = new ScalableText(0, 0, Component.literal("Name"), 0xFFFFFFFF, new TestFont());
        text.setOffsets(3, -2);

        for (int i = 0; i < 3; i++) {
            VisualPositioning.setVisualPosition(text, 10, 20);
            assertEquals(10, text.getX());
            assertEquals(20, text.getY());
        }
        text.setPosition(0, 0);
        assertEquals(3, text.getX(), "placement preserves the widget's own display offset");
        assertEquals(-2, text.getY());
    }

    @Test
    void iconRowAlignsAnUncompensatedComboboxWithItsLabelAndRemoveButton() {
        TestFont font = new TestFont();
        ScalableText label = new ScalableText(0, 0, Component.literal("Icon"), 0xFFFFFFFF, font);
        ComboBoxWidget menu = new ComboBoxWidget(0, 0, 100, Component.empty(), font,
                List.of("minecraft:oak_boat"), "minecraft:oak_boat", value -> { });
        IconButton clear = new IconButton(0, 0, 13, 13, Component.empty(), null, () -> { });

        for (int top : new int[] {0, 30, 100}) {
            VisualPositioning.setCenteredRowPosition(label, 10, top, 13);
            VisualPositioning.setCenteredRowPosition(menu, 60, top, 13);
            VisualPositioning.setCenteredRowPosition(clear, 170, top, 13);

            assertEquals(60, menu.getX(), "the menu draws its border at getX()");
            assertEquals(top + 1, menu.getY(), "the 11-pixel border is centered beside the 13-pixel button");
            assertEquals(top + 3, label.getY());
            assertEquals(label.getY(), menu.getY() + 2, "the input draws two pixels below that border");
            assertEquals(top, VisualPositioning.getVisualY(clear));
        }
    }

    @Test
    void aPaddedElementIsMeasuredByItsVisualBounds() {
        PaddedSpacer element = new PaddedSpacer(10, 20, 30, 40);

        assertEquals(8, VisualPositioning.getVisualX(element));
        assertEquals(17, VisualPositioning.getVisualY(element));
        assertEquals(34, VisualPositioning.getVisualWidth(element));
        assertEquals(46, VisualPositioning.getVisualHeight(element));
    }

    @Test
    void anElementWithoutPaddingIsMeasuredByItsOwnBounds() {
        SpacerElement element = new SpacerElement(10, 20, 30, 40);

        assertEquals(10, VisualPositioning.getVisualX(element));
        assertEquals(20, VisualPositioning.getVisualY(element));
        assertEquals(30, VisualPositioning.getVisualWidth(element));
        assertEquals(40, VisualPositioning.getVisualHeight(element));
    }

    /** Draws 2 pixels outside its content on the left and right, and 3 above and below. */
    private static final class PaddedSpacer extends SpacerElement implements Padding {
        private PaddedSpacer(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        public int getVisualX() {
            return this.getX() - 2;
        }

        @Override
        public int getVisualY() {
            return this.getY() - 3;
        }

        @Override
        public int getVisualWidth() {
            return this.getWidth() + 4;
        }

        @Override
        public int getVisualHeight() {
            return this.getHeight() + 6;
        }
    }
}
