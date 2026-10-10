//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.VisualPositioning;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.screens.PreviewPacking.Placement;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.common.client.gui.widgets.OnOffToggleButton;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import _959.server_waypoint.common.client.gui.widgets.ShiftableWidget;
import _959.server_waypoint.common.client.gui.widgets.ToggleButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTooltip;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ACCENT;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ACCENT_HOVER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DIALOG_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.POPUP_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_THUMB;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_TRACK;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_ON_ACCENT;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.WARNING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.WARNING_BACKGROUND;

/**
 * The theme editor's preview panel, the right column of the screen: a {@link SettingsListWidget} that paints
 * the panel and holds a "Preview" header and rows of samples, one real widget or drawn element for each
 * {@link PreviewSample}, at full size. {@link PreviewPacking} puts the families into rows again whenever the
 * panel's width changes; each row is a {@link SettingsListWidget.WideRow} over a {@link SampleRow}. The
 * widgets keep their state when the rows are made again.
 * <p>
 * The preview marks every sample that draws the selected key. Its combobox and dropdown draw their popups
 * separately, after the screen's content: the screen places them with {@link #layoutPopups}, gives them clicks
 * and the wheel first through {@link #clickPopup} and {@link #scroll}, and draws them with
 * {@link #renderPopups}. A popup closes whenever the list scrolls, as well as when its owner loses focus.
 */
final class WidgetThemePreview {
    /** The markers' color: a fixed magenta, not a theme color, so a marker shows whatever the theme looks like. */
    static final int MARKER_COLOR = 0xFFFF4FD8;
    /** Between the samples of a family. */
    static final int SAMPLE_GAP = 6;
    /** Between families that share a row. */
    static final int FAMILY_GAP = 12;
    /** How far outside the marked rectangle a marker's outline is. */
    private static final int MARKER_OUTSET = 2;
    /** What {@link SettingsListWidget} keeps between its rows and its scrollbar. */
    private static final int LIST_SCROLLBAR_GAP = 2;
    private static final int TEXT_FIELD_WIDTH = 104;
    private static final int COMBOBOX_WIDTH = 112;
    private static final int DROPDOWN_WIDTH = 72;
    private static final int CONTROL_HEIGHT = 11;
    private static final int TOGGLE_WIDTH = 64;
    private static final int ON_OFF_WIDTH = 30;
    private static final int SLIDER_TRACK_WIDTH = 60;
    private static final int SLIDER_FIELD_WIDTH = 26;
    private static final int SLIDER_MAX = 100;
    private static final int SLIDER_VALUE = 65;
    private static final int DISABLED_SLIDER_VALUE = 40;
    private static final int CHIP_HEIGHT = 11;
    /** The Popup and Dialog chips. */
    private static final int SURFACE_CHIP_HEIGHT = 14;
    private static final int COMBOBOX_POPUP_ROWS = 6;
    /** The dropdown opens upward when this many rows below it would pass the screen's bottom margin. */
    private static final int DROPDOWN_POPUP_ROWS = 3;
    /** The room a popup leaves at the screen's edge, as {@link ComboBoxWidget#layoutPopup} leaves. */
    private static final int POPUP_SCREEN_MARGIN = 4;
    /** {@link ComboBoxWidget} puts its text, two pixels inside its outline, at the position it is given. */
    private static final int COMBOBOX_TEXT_INSET = 2;
    private static final List<String> DIMENSIONS =
            List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    /** The samples that show the inactive look, so they are never active. */
    private static final Set<PreviewSample> NEVER_ACTIVE =
            EnumSet.of(PreviewSample.DISABLED_BUTTON, PreviewSample.DISABLED_SLIDER);

    private final Map<PreviewSample, Sample> samples = new EnumMap<>(PreviewSample.class);
    /** Each family's samples, in {@link PreviewSample.Family} order, as {@link PreviewPacking#pack} takes them. */
    private final List<List<Sample>> families = new ArrayList<>();
    private final ComboBoxWidget combobox;
    private final SampleDropdown dropdown;
    private final SettingsListWidget.Header header;
    private final PreviewList list;
    /** The row width the rows were packed for, or -1 before the first layout. */
    private int packedWidth = -1;

    WidgetThemePreview(Font font) {
        this.combobox = new ComboBoxWidget(0, 0, COMBOBOX_WIDTH, Component.empty(), font, DIMENSIONS, DIMENSIONS.get(0),
                value -> {
                });
        // Moved back by the inset, so a row places the combobox by its outline, like every other sample.
        this.combobox.setOffsets(COMBOBOX_TEXT_INSET, COMBOBOX_TEXT_INSET);
        this.combobox.setRenderPopupSeparately(true);
        this.dropdown = new SampleDropdown(font);
        this.dropdown.setRenderPopupSeparately(true);
        this.list = new PreviewList(font, List.of(this.combobox, this.dropdown));

        this.put(PreviewSample.PRIMARY_TEXT, new ScalableText(0, 0,
                Component.translatable("server_waypoint.theme.preview.primary"), TEXT_PRIMARY, font));
        this.put(PreviewSample.MUTED_TEXT, new ScalableText(0, 0,
                Component.translatable("server_waypoint.theme.preview.muted"), TEXT_MUTED, font));
        this.put(PreviewSample.DISABLED_TEXT, new ScalableText(0, 0,
                Component.translatable("server_waypoint.theme.preview.disabled"), TEXT_DISABLED, font));
        Component placeholder = Component.translatable("server_waypoint.theme.preview.placeholder");
        this.put(PreviewSample.TEXT_FIELD, new TranslucentTextField(0, 0, TEXT_FIELD_WIDTH, placeholder, font))
                .setPlaceholder(() -> placeholder);
        this.put(PreviewSample.COMBOBOX, this.combobox);
        this.put(PreviewSample.DROPDOWN, this.dropdown);
        this.put(PreviewSample.BUTTON, TranslucentButton.fitted(
                Component.translatable("server_waypoint.theme.preview.button"), () -> {
                }));
        this.put(PreviewSample.DISABLED_BUTTON, TranslucentButton.fitted(
                Component.translatable("server_waypoint.theme.preview.disabled"), () -> {
                }));
        this.put(PreviewSample.SELECTED_TOGGLE, new ToggleButton(0, 0, TOGGLE_WIDTH, CONTROL_HEIGHT,
                Component.translatable("server_waypoint.theme.preview.normal"),
                Component.translatable("server_waypoint.theme.preview.selected"),
                CONTROL_BACKGROUND, CONTROL_SELECTED_BACKGROUND, state -> {
                })).setState(true);
        this.put(PreviewSample.ON_TOGGLE, onOffToggle(true));
        this.put(PreviewSample.OFF_TOGGLE, onOffToggle(false));
        this.put(PreviewSample.SLIDER, new IntegerSlider(0, 0, SLIDER_TRACK_WIDTH, SLIDER_FIELD_WIDTH, 0, SLIDER_MAX,
                SLIDER_VALUE, value -> {
                }, font));
        this.put(PreviewSample.DISABLED_SLIDER, new IntegerSlider(0, 0, SLIDER_TRACK_WIDTH, SLIDER_FIELD_WIDTH, 0,
                SLIDER_MAX, DISABLED_SLIDER_VALUE, value -> {
                }, font));
        this.put(PreviewSample.ACCENT_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.accent"), ACCENT, TEXT_ON_ACCENT, CHIP_HEIGHT));
        this.put(PreviewSample.HOVERED_ACCENT_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.hover"), ACCENT_HOVER, TEXT_ON_ACCENT,
                CHIP_HEIGHT));
        this.put(PreviewSample.TOOLTIP, new TranslucentTooltip(font))
                .setMessage(Component.translatable("server_waypoint.theme.preview.tooltip"));
        this.put(PreviewSample.POPUP_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.popup"), POPUP_BACKGROUND, TEXT_PRIMARY,
                SURFACE_CHIP_HEIGHT));
        this.put(PreviewSample.DIALOG_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.dialog"), DIALOG_BACKGROUND, TEXT_PRIMARY,
                SURFACE_CHIP_HEIGHT));
        this.put(PreviewSample.SUCCESS_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.success"), SUCCESS_BACKGROUND, SUCCESS,
                CHIP_HEIGHT));
        this.put(PreviewSample.WARNING_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.warning"), WARNING_BACKGROUND, WARNING,
                CHIP_HEIGHT));
        this.put(PreviewSample.DANGER_CHIP, new Chip(font,
                Component.translatable("server_waypoint.theme.preview.danger"), DANGER_BACKGROUND, DANGER,
                CHIP_HEIGHT));
        this.put(PreviewSample.SCROLLBAR, new ScrollbarSample(SCROLLBAR_THUMB));
        this.put(PreviewSample.ACTIVE_SCROLLBAR, new ScrollbarSample(SCROLLBAR_THUMB_ACTIVE));
        this.put(PreviewSample.DISABLED_SCROLLBAR, new ScrollbarSample(SCROLLBAR_THUMB_DISABLED));

        for (PreviewSample.Family family : PreviewSample.Family.values()) {
            List<Sample> members = new ArrayList<>();
            for (PreviewSample sample : PreviewSample.samplesOf(family)) {
                members.add(Objects.requireNonNull(this.samples.get(sample), sample.name()));
            }
            this.families.add(List.copyOf(members));
        }
        this.header = new SettingsListWidget.Header(Component.translatable("server_waypoint.theme.preview.title"));
        this.list.setEntries(List.of(this.header));
        this.setActive(true);
    }

    private <C extends LayoutElement & Renderable> C put(PreviewSample sample, C element) {
        this.samples.put(sample, Sample.of(element));
        return element;
    }

    /** A 30-pixel On/Off toggle showing On or Off. */
    private static OnOffToggleButton onOffToggle(boolean on) {
        OnOffToggleButton toggle = new OnOffToggleButton(0, 0, state -> {
        });
        toggle.setWidth(ON_OFF_WIDTH);
        toggle.setState(on);
        return toggle;
    }

    /** The list that scrolls the preview, for Tab and focus handling. */
    SettingsListWidget list() {
        return this.list;
    }

    /**
     * Sizes and places the panel by its visual bounds, outline included, and packs the samples into rows
     * again when the rows' width changed.
     */
    void setBounds(int visualX, int visualY, int visualWidth, int visualHeight) {
        this.list.setVisualWidth(visualWidth);
        this.list.setVisualHeight(visualHeight);
        this.list.setPosition(visualX + SettingsListWidget.PANEL_PADDING, visualY + SettingsListWidget.PANEL_PADDING);
        int rowWidth = Math.max(0, this.list.getWidth() - this.list.SCROLLBAR_WIDTH - LIST_SCROLLBAR_GAP);
        if (rowWidth != this.packedWidth) {
            this.packedWidth = rowWidth;
            this.packRows(rowWidth);
        }
    }

    /** Packs the families into rows {@code rowWidth} wide and gives the list a wide row for each. */
    private void packRows(int rowWidth) {
        List<List<Integer>> widths = new ArrayList<>();
        for (List<Sample> family : this.families) {
            widths.add(family.stream().map(sample -> VisualPositioning.getVisualWidth(sample.element())).toList());
        }
        List<SettingsListWidget.Entry> entries = new ArrayList<>();
        entries.add(this.header);
        for (List<Placement> row : PreviewPacking.pack(widths, rowWidth, SAMPLE_GAP, FAMILY_GAP)) {
            entries.add(new SettingsListWidget.WideRow(new SampleRow(rowWidth, row, this.families)));
        }
        this.list.setEntries(entries);
    }

    /** Visits the widgets of the preview's rows, then the list, in the order the screen registers them. */
    void visitWidgets(Consumer<AbstractWidget> consumer) {
        this.list.visitWidgets(consumer);
    }

    /**
     * Whether the preview takes input. While the color picker is open no sample is active; otherwise every
     * sample is, except the Disabled button and the second slider, which are never active.
     */
    void setActive(boolean active) {
        this.list.active = active;
        this.samples.forEach((sample, part) -> {
            if (part.element() instanceof AbstractWidget widget) {
                AbstractWaypointPropertiesScreen.setControlActive(widget, sampleActive(sample, active));
            }
        });
        this.list.refreshWidgetVisibility();
    }

    /** Whether {@code sample} is active while the preview's activity is {@code previewActive}. */
    static boolean sampleActive(PreviewSample sample, boolean previewActive) {
        return previewActive && !NEVER_ACTIVE.contains(sample);
    }

    /**
     * Draws the panel and its rows, then marks each sample that draws {@code marked} and is at least partly
     * in view. {@code marked} is the selected key, or null for none.
     */
    void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta,
                @Nullable WidgetThemeVariable marked) {
        this.list.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        if (marked != null) {
            this.renderMarkers(context, marked);
        }
    }

    /**
     * The samples' markers, clipped to the rows' area grown by a marker's distance, so the marker of a
     * partly visible sample is cut where the sample is.
     */
    private void renderMarkers(GuiGraphicsExtractor context, WidgetThemeVariable key) {
        int left = this.list.getX();
        int top = this.list.getY();
        int bottom = top + this.list.getHeight();
        context.enableScissor(left - MARKER_OUTSET, top - MARKER_OUTSET,
                left + this.list.getWidth() + MARKER_OUTSET, bottom + MARKER_OUTSET);
        this.samples.forEach((sample, part) -> {
            LayoutElement element = part.element();
            int y = VisualPositioning.getVisualY(element);
            int height = VisualPositioning.getVisualHeight(element);
            if (sample.uses(key) && partlyInView(y, y + height, top, bottom)) {
                drawMarker(context, VisualPositioning.getVisualX(element), y,
                        VisualPositioning.getVisualWidth(element), height);
            }
        });
        context.disableScissor();
    }

    /** Whether the span from {@code top} to {@code bottom} overlaps the viewport. */
    static boolean partlyInView(int top, int bottom, int viewportTop, int viewportBottom) {
        return bottom > viewportTop && top < viewportBottom;
    }

    /** A marker around a rectangle: a 1-pixel outline in {@link #MARKER_COLOR}, 2 pixels outside it. */
    static void drawMarker(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        renderOutline(context, x - MARKER_OUTSET, y - MARKER_OUTSET,
                width + MARKER_OUTSET * 2, height + MARKER_OUTSET * 2, MARKER_COLOR);
    }

    // ------------------------------------------------------------------ popups

    /**
     * Places the popups on a screen {@code screenHeight} high: the combobox's on its roomier side, at most
     * six rows, and the dropdown's upward when three rows below it would pass the screen's bottom margin.
     * The screen calls it before drawing, so the pointer and clicks find the popups where they are drawn.
     */
    void layoutPopups(int screenHeight) {
        this.combobox.layoutPopup(screenHeight, COMBOBOX_POPUP_ROWS);
        int rowsBottom = this.dropdown.getY() + this.dropdown.getHeight() * (1 + DROPDOWN_POPUP_ROWS);
        this.dropdown.setExpansionDirection(rowsBottom > screenHeight - POPUP_SCREEN_MARGIN
                ? Direction.REVERSE : Direction.FORWARD);
    }

    /** Draws the open popups, after everything but the color picker. */
    void renderPopups(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.list.renderPopups(context, mouseX, mouseY, delta);
    }

    /** Whether an open popup of the preview is under the pointer. */
    boolean isMouseOverPopup(double mouseX, double mouseY) {
        return this.list.isMouseOverPopup(mouseX, mouseY);
    }

    /** See {@link PreviewList#clickPopup}. */
    @Nullable AbstractDropdownMenuWidget clickPopup(double mouseX, double mouseY, int button) {
        return this.list.clickPopup(mouseX, mouseY, button);
    }

    /** Closes the open popup, as on a resize or when the color picker opens. */
    void closePopups() {
        this.list.closePopups();
    }

    /** See {@link PreviewList#scroll}. */
    boolean scroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.list.scroll(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ------------------------------------------------------------------ parts

    /** A sample's element, which a row places by its visual bounds, and how the row draws it. */
    record Sample(LayoutElement element, Renderable renderer) {
        static <C extends LayoutElement & Renderable> Sample of(C element) {
            return new Sample(element, element);
        }
    }

    /**
     * A row of samples, at the places the packing gives them and centered on the tallest; the wide row
     * around it adds the padding above and below. It draws its samples in order and reports the widgets
     * among them, so the list's wide row owns those: it shows each only while it is entirely in view and
     * makes it a Tab stop.
     */
    static final class SampleRow implements LayoutElement, Renderable {
        private final WidgetPack pack;
        private final List<Renderable> parts = new ArrayList<>();

        /**
         * @param width the row's width
         * @param placements where the row's samples go, from {@link PreviewPacking#pack}
         * @param families each family's samples, in the order {@link PreviewPacking#pack} took their widths
         */
        SampleRow(int width, List<Placement> placements, List<List<Sample>> families) {
            List<Sample> samples = placements.stream()
                    .map(placement -> families.get(placement.family()).get(placement.sample()))
                    .toList();
            int tallest = 0;
            for (Sample sample : samples) {
                tallest = Math.max(tallest, VisualPositioning.getVisualHeight(sample.element()));
            }
            this.pack = new WidgetPack(0, 0, width, tallest, Orientation.HORIZONTAL);
            this.pack.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
            int end = 0;
            for (int i = 0; i < samples.size(); i++) {
                Sample sample = samples.get(i);
                int x = placements.get(i).x();
                if (x > end) {
                    this.pack.addChild(SpacerElement.width(x - end), Direction.FORWARD);
                }
                this.pack.addChild(sample.element(), Direction.FORWARD);
                this.parts.add(sample.renderer());
                end = x + VisualPositioning.getVisualWidth(sample.element());
            }
        }

        @Override
        public void setX(int x) {
            this.pack.setX(x);
        }

        @Override
        public void setY(int y) {
            this.pack.setY(y);
        }

        @Override
        public void setPosition(int x, int y) {
            this.pack.setPosition(x, y);
        }

        @Override
        public int getX() {
            return this.pack.getX();
        }

        @Override
        public int getY() {
            return this.pack.getY();
        }

        @Override
        public int getWidth() {
            return this.pack.getWidth();
        }

        @Override
        public int getHeight() {
            return this.pack.getHeight();
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> consumer) {
            this.pack.visitWidgets(consumer);
        }

        @Override
        public void
        //$ render_method_swap
        extractRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            for (Renderable part : this.parts) {
                part.
                //$ render_method_swap
                extractRenderState
                        (context, mouseX, mouseY, deltaTicks);
            }
        }
    }

    /**
     * The preview's list. Its rows' combobox and dropdown draw their popups separately, after the screen's
     * content, because the list's scissor would clip them. An open popup takes clicks and the wheel before
     * anything under it, and it closes whenever the list scrolls, since it would hang where its owner was.
     */
    static final class PreviewList extends SettingsListWidget {
        private final List<AbstractDropdownMenuWidget> popupOwners;

        PreviewList(Font font, List<? extends AbstractDropdownMenuWidget> popupOwners) {
            super(font);
            this.popupOwners = List.copyOf(popupOwners);
        }

        /** Any change of the scroll position, by the wheel, the scrollbar or a reveal, closes the popups. */
        @Override
        public void setScrollY(double scrollY) {
            double before = this.getScrollY();
            super.setScrollY(scrollY);
            if (this.getScrollY() != before) {
                this.closePopups();
            }
        }

        /** Whether an open popup is under the pointer. */
        boolean isMouseOverPopup(double mouseX, double mouseY) {
            for (AbstractDropdownMenuWidget owner : this.popupOwners) {
                if (owner.isMouseOverPopup(mouseX, mouseY)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Offers a click to the popup owners before the screen's other widgets: an open popup under the
         * pointer takes it first, because it is drawn over everything, another owner's control included;
         * then the owner under the pointer. Every owner that doesn't take the click closes its popup unless
         * the pointer is over it. Returns the owner that took the click, or null to let the click go on.
         */
        @Nullable AbstractDropdownMenuWidget clickPopup(double mouseX, double mouseY, int button) {
            for (AbstractDropdownMenuWidget owner : this.popupOwners) {
                if (owner.isMouseOverPopup(mouseX, mouseY)) {
                    return owner.mouseClicked(mouseX, mouseY, button) ? owner : null;
                }
            }
            for (AbstractDropdownMenuWidget owner : this.popupOwners) {
                if (owner.isMouseOver(mouseX, mouseY) && owner.mouseClicked(mouseX, mouseY, button)) {
                    return owner;
                }
                owner.closeMenuIfOutside(mouseX, mouseY);
            }
            return null;
        }

        /**
         * The wheel over the list: an open popup under the pointer takes it; otherwise, while the list
         * overflows and the pointer is over it, the popups close and the list scrolls. Returns false when
         * the wheel is left to the widget under the pointer.
         */
        boolean scroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            for (AbstractDropdownMenuWidget owner : this.popupOwners) {
                if (owner.isMouseOverPopup(mouseX, mouseY)) {
                    return owner.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                }
            }
            if (!this.isMouseOver(mouseX, mouseY) || !this.overflows()) {
                return false;
            }
            this.closePopups();
            return this.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        /** Closes the open popup: a choice list, or the combobox's typed suggestions. */
        void closePopups() {
            for (AbstractDropdownMenuWidget owner : this.popupOwners) {
                owner.closePopupIfOpen();
            }
        }

        /** Draws the open popup on a later layer; an owner without one draws nothing. */
        void renderPopups(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            for (AbstractDropdownMenuWidget owner : this.popupOwners) {
                owner.renderPopup(context, mouseX, mouseY, delta);
            }
        }
    }

    /** The dropdown sample: Choice 1 to Choice 3, which keeps the choice made. */
    static final class SampleDropdown extends TextChoiceDropdown {
        private static final int CHOICES = 3;
        private int chosen;

        SampleDropdown(Font font) {
            super(DROPDOWN_WIDTH, choiceLabel(0), font);
            for (int choice = 0; choice < CHOICES; choice++) {
                int index = choice;
                this.addMenuItem(new TextChoice(choiceLabel(index), () -> this.chosen = index));
            }
        }

        /** "Choice 1" for index 0, and so on. */
        static Component choiceLabel(int index) {
            return Component.translatable("server_waypoint.theme.preview.choice", String.valueOf(index + 1));
        }

        @Override
        protected Component triggerLabel() {
            return choiceLabel(this.chosen);
        }

        @Override
        protected int getSelectedMenuItemIndex() {
            return this.chosen;
        }
    }

    /**
     * A surface sample, which takes no input: a fill in one key with a {@code BORDER} outline, and a label
     * in a text key 6 pixels in from each side, all resolved each time it is drawn.
     */
    private static final class Chip extends ShiftableWidget {
        private static final int LABEL_INSET = 6;
        private final Font font;
        private final Component label;
        private final WidgetThemeVariable fill;
        private final WidgetThemeVariable text;

        private Chip(Font font, Component label, WidgetThemeVariable fill, WidgetThemeVariable text, int height) {
            super(0, 0, font.width(label) + LABEL_INSET * 2, height);
            this.font = font;
            this.label = label;
            this.fill = fill;
            this.text = text;
        }

        @Override
        public void
        //$ render_method_swap
        extractRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int x = this.getX();
            int y = this.getY();
            context.fill(x, y, x + this.getWidth(), y + this.getHeight(), getColor(this.fill));
            renderOutline(context, x, y, this.getWidth(), this.getHeight(), getColor(BORDER));
            // One pixel below the centered line, as TranslucentButton places its label.
            int textY = y + MovementAllowedScreen.centered(this.getHeight(), this.font.lineHeight) + 1;
            drawText(context, this.font, this.label, x + LABEL_INSET, textY, getColor(this.text));
        }
    }

    /** A scrollbar sample, which takes no input: a 3 by 20 track with an 8-pixel thumb in one of the thumb keys. */
    private static final class ScrollbarSample extends ShiftableWidget {
        private static final int WIDTH = 3;
        private static final int HEIGHT = 20;
        private static final int THUMB_HEIGHT = 8;
        private final WidgetThemeVariable thumb;

        private ScrollbarSample(WidgetThemeVariable thumb) {
            super(0, 0, WIDTH, HEIGHT);
            this.thumb = thumb;
        }

        @Override
        public void
        //$ render_method_swap
        extractRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            int x = this.getX();
            int y = this.getY();
            context.fill(x, y, x + WIDTH, y + HEIGHT, getColor(SCROLLBAR_TRACK));
            int thumbY = y + MovementAllowedScreen.centered(HEIGHT, THUMB_HEIGHT);
            context.fill(x, thumbY, x + WIDTH, thumbY + THUMB_HEIGHT, getColor(this.thumb));
        }
    }
}
