package _959.server_waypoint.common.client.gui.layout;

import net.minecraft.client.gui.layouts.LayoutElement;

/**
 * Reads and moves an element by its visual bounds: a {@link Padding} element's complete drawn
 * rectangle, and any other element's own bounds.
 */
public final class VisualPositioning {
    private VisualPositioning() {
    }

    /** Centers an element's visible content in a horizontal row, or its visual bounds by default. */
    public static void setCenteredRowPosition(LayoutElement widget, int visualX, int rowTop, int rowHeight) {
        int offset = widget instanceof VerticalAlignment alignment
                ? Math.round(rowHeight / 2.0F - alignment.getVerticalAlignmentCenter())
                : Math.floorDiv(rowHeight - getVisualHeight(widget), 2);
        int visualY = rowTop + offset;
        setVisualPosition(widget, visualX, visualY);
    }

    /** Places the drawn bounds, accounting for content anchors and display offsets on any element. */
    public static void setVisualPosition(LayoutElement widget, int visualX, int visualY) {
        widget.setPosition(visualX, visualY);
        int deltaX = visualX - getVisualX(widget);
        int deltaY = visualY - getVisualY(widget);
        if (deltaX != 0 || deltaY != 0) {
            widget.setPosition(visualX + deltaX, visualY + deltaY);
        }
    }

    static void setVisualX(LayoutElement widget, int visualX) {
        widget.setX(visualX);
        int delta = visualX - getVisualX(widget);
        if (delta != 0) {
            widget.setX(visualX + delta);
        }
    }

    static void setVisualY(LayoutElement widget, int visualY) {
        widget.setY(visualY);
        int delta = visualY - getVisualY(widget);
        if (delta != 0) {
            widget.setY(visualY + delta);
        }
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
}
