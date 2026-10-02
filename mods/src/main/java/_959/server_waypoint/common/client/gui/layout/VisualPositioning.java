package _959.server_waypoint.common.client.gui.layout;

import net.minecraft.client.gui.layouts.LayoutElement;

/**
 * Reads and moves an element by its visual bounds: a {@link Padding} element's complete drawn
 * rectangle, and any other element's own bounds.
 */
public final class VisualPositioning {
    private VisualPositioning() {
    }

    static void setVisualPosition(LayoutElement widget, int visualX, int visualY) {
        if (widget instanceof Padding padding) {
            setVisualX(widget, padding, visualX);
            setVisualY(widget, padding, visualY);
        } else {
            widget.setPosition(visualX, visualY);
        }
    }

    static void setVisualX(LayoutElement widget, int visualX) {
        setVisualX(widget, (Padding) widget, visualX);
    }

    static void setVisualY(LayoutElement widget, int visualY) {
        setVisualY(widget, (Padding) widget, visualY);
    }

    public static int getVisualX(LayoutElement widget) {
        return widget instanceof Padding padding ? padding.getVisualX() : widget.getX();
    }

    public static int getVisualY(LayoutElement widget) {
        return widget instanceof Padding padding ? padding.getVisualY() : widget.getY();
    }

    public static int getVisualWidth(LayoutElement widget) {
        return widget instanceof Padding padding ? padding.getVisualWidth() : widget.getWidth();
    }

    public static int getVisualHeight(LayoutElement widget) {
        return widget instanceof Padding padding ? padding.getVisualHeight() : widget.getHeight();
    }

    private static void setVisualX(LayoutElement widget, Padding padding, int visualX) {
        int x = visualX;
        widget.setX(x);
        int delta = visualX - padding.getVisualX();
        if (delta != 0) {
            widget.setX(x + delta);
        }
    }

    private static void setVisualY(LayoutElement widget, Padding padding, int visualY) {
        int y = visualY;
        widget.setY(y);
        int delta = visualY - padding.getVisualY();
        if (delta != 0) {
            widget.setY(y + delta);
        }
    }
}
