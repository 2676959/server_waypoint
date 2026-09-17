package _959.server_waypoint.common.client.gui.layout;

public enum AnchorMode {
    CONTENT,
    OUTLINE;

    public static AnchorMode normalize(AnchorMode anchorMode) {
        return anchorMode == null ? CONTENT : anchorMode;
    }

    public int getContentX(int x, int outlineLeftPadding) {
        return x + this.getContentOffset(outlineLeftPadding);
    }

    public int getContentY(int y, int outlineTopPadding) {
        return y + this.getContentOffset(outlineTopPadding);
    }

    public int getAnchorX(int contentX, int outlineLeftPadding) {
        return contentX - this.getContentOffset(outlineLeftPadding);
    }

    public int getAnchorY(int contentY, int outlineTopPadding) {
        return contentY - this.getContentOffset(outlineTopPadding);
    }

    private int getContentOffset(int outlinePadding) {
        return this == OUTLINE ? outlinePadding : 0;
    }
}
