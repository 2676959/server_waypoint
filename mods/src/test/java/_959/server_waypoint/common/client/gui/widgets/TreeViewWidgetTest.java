//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeViewWidgetTest {
    @Test
    void visibleRowsFollowExpandedNestedNodes() {
        Node first = new Node("first");
        Node childA = new Node("child-a");
        Node childB = new Node("child-b");
        Node grandchild = new Node("grandchild");
        Node second = new Node("second");
        first.children.add(childA);
        first.children.add(childB);
        childB.children.add(grandchild);

        TestTreeView tree = new TestTreeView(20);
        tree.updateRoots(List.of(first, second));

        assertEquals(5, tree.visibleRowCount());
        assertRow(tree, 0, first, 0);
        assertRow(tree, 1, childA, 1);
        assertRow(tree, 2, childB, 1);
        assertRow(tree, 3, grandchild, 2);
        assertRow(tree, 4, second, 0);
        assertEquals(100, tree.getContentHeight());
    }

    @Test
    void collapsedNodesHideDescendantsAndUpdateContentHeight() {
        Node root = new Node("root");
        root.children.add(new Node("child"));
        Node sibling = new Node("sibling");
        root.expanded = false;

        TestTreeView tree = new TestTreeView(15);
        tree.updateRoots(List.of(root, sibling));

        assertEquals(2, tree.visibleRowCount());
        assertRow(tree, 0, root, 0);
        assertRow(tree, 1, sibling, 0);
        assertEquals(30, tree.getContentHeight());
    }

    @Test
    void clicksUnhandledExpandableRowsToggleExpansion() {
        Node root = new Node("root");
        root.children.add(new Node("child"));

        TestTreeView tree = new TestTreeView(10);
        tree.updateRoots(List.of(root));

        boolean handled = tree.mouseClicked(5, 5, 0);

        assertTrue(handled);
        assertFalse(root.expanded);
        assertEquals(1, tree.visibleRowCount());
        assertEquals(10, tree.getContentHeight());
    }

    @Test
    void visibleRenderRangeOnlyIncludesRowsIntersectingViewport() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            nodes.add(new Node("node-" + i));
        }

        TestTreeView tree = new TestTreeView(20);
        tree.updateRoots(nodes);
        tree.setScrollY(25);

        TreeViewWidget.VisibleRowRange range = tree.visibleRenderRange();

        assertEquals(1, range.startRow());
        assertEquals(5, range.endRow());
    }

    private static void assertRow(TestTreeView tree, int index, Node node, int depth) {
        TreeViewWidget.TreeEntry<Node> entry = tree.visibleEntry(index);
        assertEquals(node, entry.value());
        assertEquals(depth, entry.depth());
        assertEquals(index, entry.row());
    }

    private static final class TestTreeView extends TreeViewWidget<Node> {
        private TestTreeView(int rowHeight) {
            super(0, 0, 100, 60, rowHeight, Component.literal("test"));
        }

        private int visibleRowCount() {
            return visibleEntryCount();
        }

        private TreeEntry<Node> visibleEntry(int index) {
            return getVisibleEntry(index);
        }

        private VisibleRowRange visibleRenderRange() {
            return getVisibleRenderRange(getScrollY());
        }

        @Override
        protected List<Node> getChildren(Node value) {
            return value.children;
        }

        @Override
        protected boolean isExpanded(Node value) {
            return value.expanded;
        }

        @Override
        protected void setExpanded(Node value, boolean expanded) {
            value.expanded = expanded;
        }

        @Override
        protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        protected void renderEntry(GuiGraphicsExtractor context, TreeEntry<Node> entry, boolean hovered, int rowY, int contentWidth, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }

    private static final class Node {
        private final String name;
        private final List<Node> children = new ArrayList<>();
        private boolean expanded = true;

        private Node(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
