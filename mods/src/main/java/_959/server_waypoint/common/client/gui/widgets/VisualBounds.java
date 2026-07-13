package _959.server_waypoint.common.client.gui.widgets;

record VisualBounds(int leftPadding, int topPadding, int rightPadding, int bottomPadding) {
    int x(int contentX) {
        return contentX - this.leftPadding;
    }

    int y(int contentY) {
        return contentY - this.topPadding;
    }

    int width(int contentWidth) {
        return contentWidth + this.leftPadding + this.rightPadding;
    }

    int height(int contentHeight) {
        return contentHeight + this.topPadding + this.bottomPadding;
    }

    int contentWidth(int visualWidth) {
        return visualWidth - this.leftPadding - this.rightPadding;
    }

    int contentHeight(int visualHeight) {
        return visualHeight - this.topPadding - this.bottomPadding;
    }
}
