package _959.server_waypoint.common.client.gui.layout;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetPackTest {
    @Test
    void horizontalChildrenStackInwardFromTheirAnchoredSides() {
        WidgetPack pack = new WidgetPack(10, 20, 100, 30, LayoutFlow.Orientation.HORIZONTAL);
        TestElement startA = new TestElement(20, 8);
        TestElement endA = new TestElement(15, 9);
        TestElement startB = new TestElement(25, 10);
        TestElement endB = new TestElement(10, 11);

        pack.addChild(startA, LayoutFlow.Direction.FORWARD);
        pack.addChild(endA, LayoutFlow.Direction.REVERSE);
        pack.addChild(startB, LayoutFlow.Direction.FORWARD);
        pack.addChild(endB, LayoutFlow.Direction.REVERSE);

        assertPosition(startA, 10, 20);
        assertPosition(startB, 30, 20);
        assertPosition(endA, 95, 20);
        assertPosition(endB, 85, 20);
        assertEquals(100, pack.getWidth());
        assertEquals(30, pack.getHeight());
    }

    @Test
    void verticalChildrenStackInwardFromTheirAnchoredSides() {
        WidgetPack pack = new WidgetPack(5, 7, 40, 90, LayoutFlow.Orientation.VERTICAL);
        TestElement top = new TestElement(8, 20);
        TestElement bottom = new TestElement(9, 30);
        TestElement defaultTop = new TestElement(10, 15);

        pack.addChild(top, LayoutFlow.Direction.FORWARD);
        pack.addChild(bottom, LayoutFlow.Direction.REVERSE);
        pack.addChild(defaultTop);

        assertPosition(top, 5, 7);
        assertPosition(defaultTop, 5, 27);
        assertPosition(bottom, 5, 67);
        assertEquals(40, pack.getWidth());
        assertEquals(90, pack.getHeight());
    }

    @Test
    void movingAndResizingPackRelayoutsBothAnchors() {
        WidgetPack pack = new WidgetPack(60, 20, LayoutFlow.Orientation.HORIZONTAL);
        TestElement start = new TestElement(10, 5);
        TestElement end = new TestElement(15, 5);
        pack.addChild(start, LayoutFlow.Direction.FORWARD);
        pack.addChild(end, LayoutFlow.Direction.REVERSE);

        pack.setPosition(20, 30);
        pack.setDimensions(90, 25);

        assertPosition(start, 20, 30);
        assertPosition(end, 95, 30);
        assertEquals(90, pack.getWidth());
        assertEquals(25, pack.getHeight());
    }

    @Test
    void paddedChildrenStackUsingVisualBounds() {
        WidgetPack pack = new WidgetPack(50, 60, 70, 30, LayoutFlow.Orientation.HORIZONTAL);
        PaddedElement start = new PaddedElement(10, 5, 4, 6);
        TestElement next = new TestElement(7, 5);
        PaddedElement end = new PaddedElement(8, 5, 2, 3);

        pack.addChild(start, LayoutFlow.Direction.FORWARD);
        pack.addChild(next, LayoutFlow.Direction.FORWARD);
        pack.addChild(end, LayoutFlow.Direction.REVERSE);

        assertEquals(50, start.getVisualX());
        assertEquals(60, start.getVisualY());
        assertPosition(next, 64, 60);
        assertEquals(110, end.getVisualX());
        assertEquals(60, end.getVisualY());
    }

    @Test
    void nestedPackRelayoutsItsChildrenWhenParentMovesIt() {
        WidgetPack parent = new WidgetPack(10, 20, 100, 30, LayoutFlow.Orientation.HORIZONTAL);
        WidgetPack nested = new WidgetPack(30, 20, LayoutFlow.Orientation.VERTICAL);
        TestElement nestedEnd = new TestElement(5, 6);
        nested.addChild(nestedEnd, LayoutFlow.Direction.REVERSE);

        parent.addChild(nested, LayoutFlow.Direction.REVERSE);

        assertEquals(80, nested.getX());
        assertEquals(20, nested.getY());
        assertPosition(nestedEnd, 80, 34);
    }

    private static void assertPosition(TestElement element, int x, int y) {
        assertEquals(x, element.getX());
        assertEquals(y, element.getY());
    }

    private static class TestElement implements LayoutElement {
        private int x;
        private int y;
        private final int width;
        private final int height;

        private TestElement(int width, int height) {
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
    }

    private static final class PaddedElement extends TestElement implements Padding {
        private final int leftPadding;
        private final int topPadding;

        private PaddedElement(int width, int height, int leftPadding, int topPadding) {
            super(width, height);
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
    }
}
