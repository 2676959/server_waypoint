package _959.server_waypoint.common.client.gui.layout;

import net.minecraft.client.gui.layouts.SpacerElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualPositioningTest {
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
