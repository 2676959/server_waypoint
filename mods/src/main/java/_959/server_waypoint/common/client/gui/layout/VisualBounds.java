package _959.server_waypoint.common.client.gui.layout;

public record VisualBounds(int leftPadding, int topPadding, int rightPadding, int bottomPadding) {
    public int x(int contentX) {
        return contentX - this.leftPadding;
    }

    public int y(int contentY) {
        return contentY - this.topPadding;
    }

    public int width(int contentWidth) {
        return contentWidth + this.leftPadding + this.rightPadding;
    }

    public int height(int contentHeight) {
        return contentHeight + this.topPadding + this.bottomPadding;
    }

    public int contentWidth(int visualWidth) {
        return visualWidth - this.leftPadding - this.rightPadding;
    }

    public int contentHeight(int visualHeight) {
        return visualHeight - this.topPadding - this.bottomPadding;
    }
}
