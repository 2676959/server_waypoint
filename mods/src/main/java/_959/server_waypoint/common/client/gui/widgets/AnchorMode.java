package _959.server_waypoint.common.client.gui.widgets;

public enum AnchorMode {
    CONTENT,
    OUTLINE;

    static AnchorMode normalize(AnchorMode anchorMode) {
        return anchorMode == null ? CONTENT : anchorMode;
    }

    int getContentX(int x, int outlineLeftPadding) {
        return x + this.getContentOffset(outlineLeftPadding);
    }

    int getContentY(int y, int outlineTopPadding) {
        return y + this.getContentOffset(outlineTopPadding);
    }

    int getAnchorX(int contentX, int outlineLeftPadding) {
        return contentX - this.getContentOffset(outlineLeftPadding);
    }

    int getAnchorY(int contentY, int outlineTopPadding) {
        return contentY - this.getContentOffset(outlineTopPadding);
    }

    private int getContentOffset(int outlinePadding) {
        return this == OUTLINE ? outlinePadding : 0;
    }
}
