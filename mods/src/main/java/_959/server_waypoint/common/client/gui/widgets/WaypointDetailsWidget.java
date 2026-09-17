//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.util.ColorHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.translate;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColorSupplier;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;

public final class WaypointDetailsWidget extends ShiftableScrollableWidget implements Expandable {
    private static final int CONTENT_PADDING = 4;
    private static final int ROW_GAP = 2;
    private static final int SECTION_GAP = 5;
    private static final int SWATCH_SIZE = 9;
    private static final int SWATCH_GAP = 3;
    private final Font textRenderer;
    private @Nullable WaypointListWidget.WaypointSelection selection;
    private List<DetailRow> rows = List.of();
    private int contentHeight;

    public WaypointDetailsWidget(int x, int y, int width, int height, Font textRenderer) {
        super(x, y, width, height, Component.translatable("waypoint.details.title"));
        this.textRenderer = textRenderer;
        this.setX(x);
        this.setY(y);
        this.rows = this.createRows();
        this.rebuildContentHeight();
    }

    public void setSelection(@Nullable WaypointListWidget.WaypointSelection selection) {
        boolean sameSelection = isSameSelection(this.selection, selection);
        double previousScrollY = this.getScrollY();
        this.selection = selection;
        this.rows = this.createRows();
        this.rebuildContentHeight();
        this.setScrollY(sameSelection ? previousScrollY : 0.0D);
    }

    private static boolean isSameSelection(
            @Nullable WaypointListWidget.WaypointSelection left,
            @Nullable WaypointListWidget.WaypointSelection right
    ) {
        return left != null
                && right != null
                && Objects.equals(left.dimensionName(), right.dimensionName())
                && Objects.equals(left.listName(), right.listName())
                && Objects.equals(left.waypoint().name(), right.waypoint().name());
    }

    @Override
    public int getContentHeight() {
        return this.contentHeight;
    }

    @Override
    public double getDeltaYPerScroll() {
        return 8.0D;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        return this.isMouseOver(mouseX, mouseY)
                && super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void setWidth(int width) {
        this.width = Math.max(0, width);
        this.rebuildContentHeight();
    }

    @Override
    public void setHeight(int height) {
        this.height = Math.max(0, height);
        this.refreshScroll();
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = this.getX();
        int y = this.getY();
        context.enableScissor(x, y, x + this.width, y + this.height);
        push(context);
        translate(context, x + CONTENT_PADDING, y + CONTENT_PADDING - (float)this.getScrollY());
        this.renderRows(context, this.rows);
        pop(context);
        context.disableScissor();
        this.drawScrollbar(context);
    }

    private void renderRows(GuiGraphicsExtractor context, List<DetailRow> rows) {
        int y = 0;
        for (DetailRow row : rows) {
            y += row.gapBefore();
            SwatchValue swatchValue = row.swatchValue();
            int textX = 0;
            if (swatchValue != null) {
                drawText(context, this.textRenderer, row.text(), 0, y, row.color().getAsInt(), true);
                int swatchX = this.textRenderer.width(row.text());
                context.fill(
                        swatchX,
                        y,
                        swatchX + SWATCH_SIZE,
                        y + SWATCH_SIZE,
                        0xFF000000 | swatchValue.color()
                );
                renderOutline(context, swatchX, y, SWATCH_SIZE, SWATCH_SIZE, getColor(BORDER));
                textX = swatchX + SWATCH_SIZE + SWATCH_GAP;
            }
            Component text = swatchValue == null ? row.text() : swatchValue.text();
            IntSupplier color = swatchValue == null ? row.color() : swatchValue.textColor();
            List<FormattedCharSequence> lines = this.wrap(text, textX);
            for (FormattedCharSequence line : lines) {
                drawText(context, this.textRenderer, line, textX, y, color.getAsInt(), true);
                y += this.textRenderer.lineHeight;
            }
        }
    }

    private void rebuildContentHeight() {
        int height = CONTENT_PADDING * 2;
        for (DetailRow row : this.rows) {
            height += row.gapBefore();
            SwatchValue swatchValue = row.swatchValue();
            Component text = swatchValue == null ? row.text() : swatchValue.text();
            int textX = swatchValue == null
                    ? 0
                    : this.textRenderer.width(row.text()) + SWATCH_SIZE + SWATCH_GAP;
            height += Math.max(1, this.wrap(text, textX).size()) * this.textRenderer.lineHeight;
        }
        this.contentHeight = height;
        this.refreshScroll();
    }

    private List<FormattedCharSequence> wrap(Component text, int textX) {
        int availableWidth = Math.max(
                1,
                this.width - CONTENT_PADDING * 2 - this.SCROLLBAR_WIDTH - textX
        );
        return this.textRenderer.split(text, availableWidth);
    }

    private List<DetailRow> createRows() {
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow(
                Component.translatable("waypoint.details.title"),
                getColorSupplier(TEXT_PRIMARY),
                0,
                null
        ));
        if (this.selection == null) {
            rows.add(new DetailRow(
                    Component.translatable("waypoint.details.select"),
                    getColorSupplier(TEXT_MUTED),
                    SECTION_GAP,
                    null
            ));
            return rows;
        }

        SimpleWaypoint liveWaypoint = this.selection.waypoint();
        boolean rendered = liveWaypoint.isRendered();
        SimpleWaypoint waypoint = new SimpleWaypoint(liveWaypoint);
        Component none = Component.translatable("waypoint.details.none");
        Component keywords = waypoint.keywords().isEmpty()
                ? none
                : Component.literal(String.join(", ", waypoint.keywords()));
        Component description = waypoint.description().isEmpty()
                ? none
                : parseFormattedText(waypoint.description());

        rows.add(new DetailRow(
                parseFormattedText(waypoint.displayName()),
                getColorSupplier(TEXT_PRIMARY),
                SECTION_GAP,
                null
        ));
        rows.add(detail("waypoint.details.name", Component.literal(waypoint.name())));
        rows.add(detail("waypoint.details.display_name", parseFormattedText(waypoint.displayName())));
        rows.add(detail("waypoint.details.initials", Component.literal(waypoint.initials())));
        rows.add(coloredDetail(
                "waypoint.details.dimension",
                Component.literal(this.selection.dimensionName()),
                () -> getDisplayDimensionColor(this.selection.dimensionName())
        ));
        rows.add(detail("waypoint.details.list_name", Component.literal(this.selection.listName())));
        rows.add(detail(
                "waypoint.details.list_display_name",
                parseFormattedText(this.selection.listDisplayName())
        ));
        rows.add(detail("waypoint.details.position", Component.literal(waypoint.pos().toShortString())));
        rows.add(detail("waypoint.details.yaw", Component.literal(Integer.toString(waypoint.yaw()))));
        rows.add(detail(
                "waypoint.details.visibility",
                Component.translatable(waypoint.global() ? "waypoint.global" : "waypoint.local")
        ));
        rows.add(new DetailRow(
                Component.translatable("waypoint.details.color", Component.empty()),
                getColorSupplier(TEXT_MUTED),
                ROW_GAP,
                new SwatchValue(
                        Component.literal(rgbToHexCode(waypoint.rgb(), true)),
                        getColorSupplier(TEXT_PRIMARY),
                        waypoint.rgb()
                )
        ));
        rows.add(coloredDetail(
                "waypoint.details.rendered",
                Component.translatable(rendered
                        ? "server_waypoint.config.true"
                        : "server_waypoint.config.false"),
                getColorSupplier(rendered ? SUCCESS : DANGER)
        ));
        rows.add(detail("waypoint.details.keywords", keywords));
        rows.add(detail("waypoint.details.description", description));
        return rows;
    }

    private static DetailRow detail(String translationKey, Component value) {
        return coloredDetail(translationKey, value, getColorSupplier(TEXT_PRIMARY));
    }

    private static DetailRow coloredDetail(
            String translationKey,
            Component value,
            IntSupplier color
    ) {
        Component coloredValue = value.copy().withStyle(style ->
                style.withColor(color.getAsInt() & 0x00FFFFFF));
        return new DetailRow(
                Component.translatable(translationKey, coloredValue),
                getColorSupplier(TEXT_MUTED),
                ROW_GAP,
                null
        );
    }

    private static int getDisplayDimensionColor(String dimensionName) {
        return ColorHelper.scaleRgb(
                0xFF000000 | getDimensionColor(dimensionName).value(),
                0.8F
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    private record DetailRow(
            Component text,
            IntSupplier color,
            int gapBefore,
            @Nullable SwatchValue swatchValue
    ) {
    }

    private record SwatchValue(Component text, IntSupplier textColor, int color) {
    }
}
