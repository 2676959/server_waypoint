//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.TooltipPlacement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextTooltipLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousTooltipLayer;

/**
 * The tooltip of the current frame. Controls and lists request one while they render, and
 * {@code MovementAllowedScreen} draws the winning request last, on its own layer. The first request of a
 * frame wins, and only a focused control's request replaces it.
 * <p>
 * This is static frame state for the render thread only. {@code MovementAllowedScreen} clears it when a
 * frame starts, and {@link #render} clears it again, so a request made while one of the mod's screens
 * renders never outlives its frame.
 */
public final class TooltipLayer {
    enum Anchor { POINTER, BESIDE_CONTROL, BELOW_OR_ABOVE_CONTROL }

    /** {@code control} is the control's visual bounds, and {@code null} for a {@code POINTER} request. */
    record Request(Component text, Anchor anchor, int mouseX, int mouseY, @Nullable ScreenRectangle control) {
    }

    private static @Nullable Request scheduled;
    private static @Nullable TranslucentTooltip surface;

    private TooltipLayer() {
    }

    /**
     * Requests a tooltip at the pointer, unless the frame already has one. The coordinates are
     * screen-space, never coordinates after a render translation.
     */
    public static void scheduleAtPointer(Component text, int mouseX, int mouseY) {
        if (scheduled == null) {
            scheduled = new Request(text, Anchor.POINTER, mouseX, mouseY, null);
        }
    }

    /**
     * Requests a control's tooltip: beside the control while it is hovered, below or above it while it
     * is focused by keyboard, and not at all otherwise. A focused control's request replaces an earlier one.
     */
    static void scheduleForControl(Component text, ScreenRectangle control, boolean hovered, boolean focused,
                                   boolean keyboardNavigating, int mouseX, int mouseY) {
        Anchor anchor;
        if (hovered) {
            anchor = Anchor.BESIDE_CONTROL;
        } else if (focused && keyboardNavigating) {
            anchor = Anchor.BELOW_OR_ABOVE_CONTROL;
        } else {
            return;
        }
        if (scheduled == null || focused) {
            scheduled = new Request(text, anchor, mouseX, mouseY, control);
        }
    }

    /** Draws the frame's request, if it has one with any text, and clears it. */
    public static void render(GuiGraphicsExtractor context, Font font, int screenWidth, int screenHeight) {
        // Take the request out first: a frame whose drawing throws must leave nothing behind.
        Request request = scheduled;
        scheduled = null;
        if (request == null) {
            return;
        }
        if (surface == null) {
            surface = new TranslucentTooltip(font);
        }
        surface.setMessage(request.text());
        if (surface.isEmpty()) {
            return;
        }
        int width = surface.getWidth();
        int height = surface.getHeight();
        TooltipPlacement.Position position = switch (request.anchor()) {
            case POINTER -> TooltipPlacement.atPointer(
                    screenWidth, screenHeight, request.mouseX(), request.mouseY(), width, height);
            case BESIDE_CONTROL -> TooltipPlacement.besideControl(
                    screenWidth, screenHeight, request.mouseX(), request.mouseY(), width, height, request.control());
            case BELOW_OR_ABOVE_CONTROL -> TooltipPlacement.belowOrAboveControl(
                    screenWidth, screenHeight, width, height, request.control());
        };
        surface.setPosition(position.x(), position.y());
        nextTooltipLayer(context);
        try {
            surface.
            //$ render_method_swap
            extractRenderState
                    (context, NO_MOUSE, NO_MOUSE, 0.0F);
        } finally {
            previousTooltipLayer(context);
        }
    }

    /** Drops the request. */
    public static void clear() {
        scheduled = null;
    }

    static @Nullable Request scheduled() {
        return scheduled;
    }
}
