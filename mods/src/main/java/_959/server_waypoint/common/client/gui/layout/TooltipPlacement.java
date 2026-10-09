package _959.server_waypoint.common.client.gui.layout;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Mth;

/**
 * Where a tooltip's text block goes on the screen. The rules are vanilla's
 * {@code DefaultTooltipPositioner}, {@code MenuTooltipPositioner} and
 * {@code BelowOrAboveWidgetTooltipPositioner}, which are the same from 1.20.1 to 26.3, with two changes:
 * the box's top stays on the screen, and a control with no height counts as one pixel high, because the
 * rule for tooltips beside a control divides by the height.
 * <p>
 * {@code width} and {@code height} are the text block's size. Each method returns the text block's
 * top-left corner; the box is drawn 4 pixels outside it.
 */
public final class TooltipPlacement {
    /** The lowest top for a text block whose box extends 4 pixels above it. */
    private static final int MIN_Y = 4;

    private TooltipPlacement() {
    }

    public record Position(int x, int y) {
    }

    /** Right of the pointer and above it, for a tooltip that belongs to a hovered item. */
    public static Position atPointer(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height) {
        int x = mouseX + 12;
        int y = mouseY - 12;
        if (x + width > screenWidth) {
            x = Math.max(mouseX - 12 - width, 4);
        }
        if (y + height + 3 > screenHeight) {
            y = screenHeight - height - 3;
        }
        return new Position(x, Math.max(y, MIN_Y));
    }

    /**
     * Right of the pointer and below the control, or above the control near the bottom of the screen,
     * for a tooltip that belongs to a hovered control.
     */
    public static Position besideControl(int screenWidth, int screenHeight, int mouseX, int mouseY,
                                         int width, int height, ScreenRectangle control) {
        int x = mouseX + 12;
        if (x + width > screenWidth - 5) {
            x = Math.max(mouseX - 12 - width, 9);
        }
        int y = mouseY + 3;
        int paddedHeight = height + 6;
        int lowestPossibleY = control.bottom() + 3 + offset(0, 0, control.height());
        if (lowestPossibleY + paddedHeight <= screenHeight - 5) {
            y += offset(y, control.top(), control.height());
        } else {
            y -= paddedHeight + offset(y, control.bottom(), control.height());
        }
        return new Position(x, Math.max(y, MIN_Y));
    }

    /** Below the control, or above it near the bottom of the screen, for a control focused by keyboard. */
    public static Position belowOrAboveControl(int screenWidth, int screenHeight, int width, int height,
                                               ScreenRectangle control) {
        int x = control.left() + 3;
        int y = control.bottom() + 4;
        if (y + height + 3 > screenHeight) {
            y = control.top() - height - 4;
        }
        if (x + width > screenWidth) {
            x = Math.max(control.right() - width - 3, 4);
        }
        return new Position(x, Math.max(y, MIN_Y));
    }

    /**
     * Vanilla's {@code MenuTooltipPositioner.getOffset}, in float arithmetic so the rounding matches on
     * every version: the nearer {@code a} is to {@code b}, the more the tooltip overlaps the control.
     */
    private static int offset(int a, int b, int controlHeight) {
        int height = Math.max(1, controlHeight); // the zero-height guard
        int distance = Math.min(Math.abs(a - b), height);
        return Math.round(Mth.lerp((float) distance / (float) height, (float) (height - 3), 5.0F));
    }
}
