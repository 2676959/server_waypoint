package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpandableManagerTest {
    @Test
    void horizontalForwardLayoutResizesAndPositionsChildren() {
        ExpandableManager manager = new ExpandableManager(10, 20, 120, 40, ExpandableManager.Orientation.HORIZONTAL, ExpandableManager.Direction.FORWARD);
        TestElement fixed = new TestElement(0, 0, 20, 10);
        TestElement flexibleA = new TestElement(0, 0, 0, 8);
        TestElement flexibleB = new TestElement(0, 0, 0, 12);

        manager.addChild(fixed, 0, 1);
        manager.addChild(flexibleA, 1, 1);
        manager.addChild(flexibleB, 3, 1);

        assertElement(fixed, 10, 20, 20, 40);
        assertElement(flexibleA, 30, 20, 25, 40);
        assertElement(flexibleB, 55, 20, 75, 40);

        manager.setWidth(100);

        assertElement(fixed, 10, 20, 20, 40);
        assertElement(flexibleA, 30, 20, 20, 40);
        assertElement(flexibleB, 50, 20, 60, 40);
    }

    @Test
    void verticalReverseLayoutStacksChildrenBottomToTop() {
        ExpandableManager manager = new ExpandableManager(3, 4, 50, 100, ExpandableManager.Orientation.VERTICAL, ExpandableManager.Direction.REVERSE);
        TestElement bottom = new TestElement(0, 0, 14, 30);
        TestElement top = new TestElement(0, 0, 12, 0);

        manager.addChild(bottom, 1, 0);
        manager.addChild(top, 1, 1);

        assertElement(bottom, 3, 74, 50, 30);
        assertElement(top, 3, 4, 50, 70);
    }

    @Test
    void nestedManagersRelayoutTheirOwnChildrenWhenParentResizesThem() {
        ExpandableManager parent = new ExpandableManager(0, 0, 100, 30, ExpandableManager.Orientation.HORIZONTAL, ExpandableManager.Direction.FORWARD);
        ExpandableManager nested = new ExpandableManager(ExpandableManager.Orientation.VERTICAL, ExpandableManager.Direction.FORWARD);
        TestElement nestedTop = new TestElement(0, 0, 0, 0);
        TestElement nestedBottom = new TestElement(0, 0, 0, 0);
        TestElement fixed = new TestElement(0, 0, 25, 12);

        nested.addChild(nestedTop, 1, 1);
        nested.addChild(nestedBottom, 1, 2);
        parent.addChild(fixed, 0, 1);
        parent.addChild(nested, 1, 1);

        assertElement(fixed, 0, 0, 25, 30);
        assertEquals(25, nested.getX());
        assertEquals(0, nested.getY());
        assertEquals(75, nested.getWidth());
        assertEquals(30, nested.getHeight());
        assertElement(nestedTop, 25, 0, 75, 10);
        assertElement(nestedBottom, 25, 10, 75, 20);

        parent.setDimensions(85, 45);

        assertElement(fixed, 0, 0, 25, 45);
        assertEquals(25, nested.getX());
        assertEquals(0, nested.getY());
        assertEquals(60, nested.getWidth());
        assertEquals(45, nested.getHeight());
        assertElement(nestedTop, 25, 0, 60, 15);
        assertElement(nestedBottom, 25, 15, 60, 30);
    }

    @Test
    void paddedChildrenUseVisualBoundsForLayout() {
        ExpandableManager manager = new ExpandableManager(50, 60, 70, 30, ExpandableManager.Orientation.HORIZONTAL, ExpandableManager.Direction.FORWARD);
        PaddedElement padded = new PaddedElement(10, 5, 4, 6);
        TestElement flexible = new TestElement(0, 0, 0, 0);

        manager.addChild(padded, 0, 0);
        manager.addChild(flexible, 1, 1);

        assertEquals(54, padded.getX());
        assertEquals(66, padded.getY());
        assertEquals(10, padded.getWidth());
        assertEquals(5, padded.getHeight());
        assertElement(flexible, 64, 60, 56, 30);
    }

    private static void assertElement(TestElement element, int x, int y, int width, int height) {
        assertEquals(x, element.getX());
        assertEquals(y, element.getY());
        assertEquals(width, element.getWidth());
        assertEquals(height, element.getHeight());
    }

    private static class TestElement implements LayoutElement, Expandable {
        private int x;
        private int y;
        private int width;
        private int height;

        private TestElement(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public void setX(int x) {
            this.x = x;
        }

        @Override
        public void setY(int y) {
            this.y = y;
        }

        @Override
        public int getX() {
            return this.x;
        }

        @Override
        public int getY() {
            return this.y;
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> consumer) {
        }

        @Override
        public void setWidth(int width) {
            this.width = width;
        }

        @Override
        public void setHeight(int height) {
            this.height = height;
        }
    }

    private static final class PaddedElement extends TestElement implements Padding {
        private final int leftPadding;
        private final int topPadding;

        private PaddedElement(int width, int height, int leftPadding, int topPadding) {
            super(0, 0, width, height);
            this.leftPadding = leftPadding;
            this.topPadding = topPadding;
        }

        @Override
        public int getVisualHeight() {
            return getHeight() + this.topPadding;
        }

        @Override
        public int getVisualWidth() {
            return getWidth() + this.leftPadding;
        }

        @Override
        public int getVisualX() {
            return getX() - this.leftPadding;
        }

        @Override
        public int getVisualY() {
            return getY() - this.topPadding;
        }

        @Override
        public void setPaddedX(int x) {
            setX(x + this.leftPadding);
        }

        @Override
        public void setPaddedY(int y) {
            setY(y + this.topPadding);
        }

        @Override
        public void setVisualWidth(int width) {
            setWidth(width - this.leftPadding);
        }

        @Override
        public void setVisualHeight(int height) {
            setHeight(height - this.topPadding);
        }
    }
}
