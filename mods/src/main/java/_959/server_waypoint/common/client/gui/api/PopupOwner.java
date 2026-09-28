package _959.server_waypoint.common.client.gui.api;

/** A control with a transient popup, such as a menu or suggestion list, that Escape dismisses. */
public interface PopupOwner {
    /** Closes the popup if it is open and reports whether it was open. */
    boolean closePopupIfOpen();
}
