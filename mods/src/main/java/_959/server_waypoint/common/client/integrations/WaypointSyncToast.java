//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.client.gui.render.DrawContextHelper;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
//? if >=1.21.2 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//?} else {
/*import net.minecraft.client.gui.components.toasts.ToastComponent;
*///?}
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} elif >1.21 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.network.chat.Component;
//? if >1.20.1
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static _959.server_waypoint.common.util.ResourceLocationHelper.mcId;

/** Uses vanilla's advancement background and layout for a client-side sync message. */
final class WaypointSyncToast implements Toast {
    //? if >1.20.1 {
    private static final
    //$ resource_location_type_swap
    Identifier
    BACKGROUND = mcId("minecraft", "toast/advancement");
    //?}
    private static final int TEXT_X = 30;
    private static final int TEXT_WIDTH = 125;
    private static final int LINES_PER_PAGE = 3;
    private static final int TITLE_TIME_MS = 1500;
    private static final int PAGE_TIME_MS = AdvancementToast.DISPLAY_TIME - TITLE_TIME_MS;
    private static final int FADE_TIME_MS = 300;
    private static final int TITLE_COLOR = 0xFF87CEEB;
    private static final int MESSAGE_COLOR = 0xFFFFFFFF;

    private final ItemStack icon = new ItemStack(Items.COMPASS);
    private final List<FormattedCharSequence> titleLines;
    private final List<FormattedCharSequence> messageLines;
    private final int pageCount;
    private final long durationMs;
    private long displayTimeMs;
    private Visibility visibility = Visibility.HIDE;

    WaypointSyncToast(Font font, Component message) {
        this.titleLines = font.split(Component.translatable("server_waypoint.mod_name"), TEXT_WIDTH);
        this.messageLines = font.split(message, TEXT_WIDTH);
        this.pageCount = Math.max(1, (this.messageLines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        this.durationMs = TITLE_TIME_MS + (long) this.pageCount * PAGE_TIME_MS;
    }

    //? if >=1.21.2 {
    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.updateDisplayTime(fullyVisibleForMs, manager.getNotificationDisplayTimeMultiplier());
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        this.drawToast(graphics, font);
    }
    //?} else {
    /*@Override
    public Visibility render(GuiGraphics graphics, ToastComponent manager, long fullyVisibleForMs) {
        this.updateDisplayTime(fullyVisibleForMs, manager.getNotificationDisplayTimeMultiplier());
        this.drawToast(graphics, manager.getMinecraft().font);
        return this.visibility;
    }
    *///?}

    private void updateDisplayTime(long fullyVisibleForMs, double multiplier) {
        this.displayTimeMs = multiplier > 0 ? (long) (fullyVisibleForMs / multiplier) : this.durationMs;
        this.visibility = this.displayTimeMs < this.durationMs ? Visibility.SHOW : Visibility.HIDE;
    }

    private void drawToast(GuiGraphicsExtractor graphics, Font font) {
        //? if >=1.21.6 {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());
        //?} elif >1.21 {
        /*graphics.blitSprite(RenderType::guiTextured, BACKGROUND, 0, 0, this.width(), this.height());
        *///?} elif >1.20.1 {
        /*graphics.blitSprite(BACKGROUND, 0, 0, this.width(), this.height());
        *///?} else {
        /*graphics.blit(Toast.TEXTURE, 0, 0, 0, 0, this.width(), this.height());
        *///?}
        DrawContextHelper.drawItem(graphics, this.icon, 8, 8);

        if (this.titleLines.size() == 1 && this.messageLines.size() == 1) {
            DrawContextHelper.drawText(graphics, font, this.titleLines.get(0), TEXT_X, 7, TITLE_COLOR, false);
            DrawContextHelper.drawText(graphics, font, this.messageLines.get(0), TEXT_X, 18, MESSAGE_COLOR, false);
        } else if (this.displayTimeMs < TITLE_TIME_MS || this.messageLines.isEmpty()) {
            long fadeTime = this.messageLines.isEmpty() ? FADE_TIME_MS : TITLE_TIME_MS - this.displayTimeMs;
            this.drawPage(graphics, font, this.titleLines, 0, fade(TITLE_COLOR, fadeTime));
        } else {
            long messageTime = Math.min(this.displayTimeMs, this.durationMs - 1) - TITLE_TIME_MS;
            int page = (int) (messageTime / PAGE_TIME_MS);
            this.drawPage(graphics, font, this.messageLines, page, fade(MESSAGE_COLOR, messageTime % PAGE_TIME_MS));
        }
    }

    private void drawPage(GuiGraphicsExtractor graphics, Font font, List<FormattedCharSequence> lines,
                          int page, int color) {
        int start = page * LINES_PER_PAGE;
        int end = Math.min(start + LINES_PER_PAGE, lines.size());
        int y = (this.height() - (end - start) * font.lineHeight) / 2;
        for (int index = start; index < end; index++) {
            DrawContextHelper.drawText(graphics, font, lines.get(index), TEXT_X, y, color, false);
            y += font.lineHeight;
        }
    }

    private static int fade(int color, long fadeTime) {
        // Legacy fonts interpret alpha below 4 as opaque.
        int alpha = (int) Math.min(255, 4 + fadeTime * 251 / FADE_TIME_MS);
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
