package _959.server_waypoint.common.client.gui.api;

/**
 * A control with a transient popup, such as a menu or suggestion list, that Escape dismisses and
 * the mouse wheel may scroll.
 */
public interface PopupOwner {
    /** Closes the popup if it is open and reports whether it was open. */
    boolean closePopupIfOpen();

    /**
     * Scrolls the open popup with the mouse wheel when the pointer is over it and reports whether the
     * wheel was used. A screen offers the wheel to its focused control first, because a popup hangs
     * outside its owner and vanilla would otherwise hand the event to whatever is under the pointer.
     * By default a popup does not scroll this way.
     */
    default boolean scrollPopupIfOver(double mouseX, double mouseY, double verticalAmount) {
        return false;
    }
}
