//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.VisualPositioning;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
import _959.server_waypoint.common.client.gui.render.WaypointRowRenderer;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Arrangement;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Columns;
import _959.server_waypoint.common.client.gui.widgets.ColorHexCodeField;
import _959.server_waypoint.common.client.gui.widgets.ColorSquareButton;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.CoordinateField;
import _959.server_waypoint.common.client.gui.widgets.IntegerField;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SeparatorWidget;
import _959.server_waypoint.common.client.gui.widgets.SuggestingTextInput;
import _959.server_waypoint.common.client.gui.widgets.SwatchWidget;
import _959.server_waypoint.common.client.gui.widgets.ToggleButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.gui.widgets.WaypointIconPicker;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.util.CoordinateInputParser;
import _959.server_waypoint.common.util.CoordinateSuggestions;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scheduleTooltipAtPointer;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_DESCRIPTION_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_NAME_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.ColorUtils.BLUE;
import static _959.server_waypoint.util.ColorUtils.GREEN;
import static _959.server_waypoint.util.ColorUtils.RED;
import static _959.server_waypoint.util.ColorUtils.randomColor;
import static _959.server_waypoint.util.WaypointInitials.getInitialsCandidatesFromName;

/**
 * The waypoint add and edit forms: one compact, fixed panel with a label column and a control column,
 * laid out by {@link WaypointFormLayout}. This base owns the shared fields, their checks, the layout
 * and the drawing; a subclass supplies what differs: the rows above Name, the footer buttons and what
 * Add or Save does. {@code init()} builds the form after both constructors have finished, so the
 * constructors call no method a subclass overrides.
 */
public abstract class AbstractWaypointPropertiesScreen extends MovementAllowedScreen {
    private static final float TITLE_SCALE = 1.2F;
    private static final long TOOLTIP_DELAY_NANOS = 500_000_000L;
    /**
     * The Visibility toggle's own fills, the same in every theme. They are the previous release's Local
     * (#04E500) and Global (#005AE5) at 60% over a button fill of 53% black, flattened into the one layer
     * the toggle paints, so over the panel they look as the two layers did.
     */
    static final int LOCAL_TOGGLE_COLOR = 0xCF03A900;
    static final int GLOBAL_TOGGLE_COLOR = 0xCF0043A9;
    private static final int PREVIEW_SIZE = 11;
    private static final int MIN_STATUS_WIDTH = 100;
    private static final String AXIS_LETTERS = "XYZRUF";
    private static final Component ELLIPSIS = Component.literal("…");
    private static final WaypointFormCheck.Lookup CLIENT_DATA = new ClientData();

    protected final Screen previousScreen;
    protected final String dimensionName;
    protected final String listName;
    /** The waypoint being edited as saved; null on Add. */
    protected final @Nullable WaypointFormPatch.Saved saved;
    protected final TranslucentTextField nameEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.name"), font);
    protected final TranslucentTextField displayNameEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.display_name"), font);
    protected final TranslucentTextField initialsEditBox = new TranslucentTextField(0, 0, WaypointFormLayout.SMALL_FIELD_WIDTH, Component.translatable("waypoint.form.initials"), font);
    protected final ColorHexCodeField colorEditBox = new ColorHexCodeField(0, 0, Component.translatable("waypoint.form.color"), font);
    protected final ColorSquareButton colorPickerButton = new ColorSquareButton(0, 0, 9, this::openSwatch);
    protected final CoordinateField xEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("X"), font);
    protected final CoordinateField yEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("Y"), font);
    protected final CoordinateField zEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("Z"), font);
    protected final IntegerField yawEditBox = new IntegerField(0, 0, WaypointFormLayout.SMALL_FIELD_WIDTH, Component.translatable("waypoint.form.yaw"), font);
    protected final TranslucentTextField keywordsEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.keywords"), font);
    protected final TranslucentTextField descriptionEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.description"), font);
    protected final ToggleButton globalToggle = new ToggleButton(
            0,
            0,
            WaypointFormLayout.TOGGLE_WIDTH,
            11,
            Component.translatable("waypoint.local"),
            Component.translatable("waypoint.global"),
            LOCAL_TOGGLE_COLOR,
            GLOBAL_TOGGLE_COLOR,
            state -> this.onFormEdited()
    );
    protected final SwatchWidget swatchWidget = new SwatchWidget(0, 0, font, color -> {
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.closeSwatch();
        this.onFormEdited();
    });
    protected final TranslucentButton cancelButton = TranslucentButton.fitted(
            Component.translatable("server_waypoint.cancel.button"), this::onClose);
    protected final WaypointIconPicker iconPicker;
    protected WaypointPos coordinateDefaultPos;

    private final ScalableText titleText;
    private final ScalableText statusText;
    private final ScalableText xLabel = new ScalableText(0, 0, Component.nullToEmpty("X"), RED, font);
    private final ScalableText yLabel = new ScalableText(0, 0, Component.nullToEmpty("Y"), GREEN, font);
    private final ScalableText zLabel = new ScalableText(0, 0, Component.nullToEmpty("Z"), BLUE, font);
    private final Map<FormField, ScalableText> labels = new EnumMap<>(FormField.class);
    private final Map<FormField, List<LayoutElement>> hoverParts = new EnumMap<>(FormField.class);
    private final SpacerElement iconPreview = new SpacerElement(PREVIEW_SIZE, PREVIEW_SIZE);
    private final boolean emptyDisplayNameOverride;
    private @Nullable ScalableText subtitleText;
    private List<LeadingRow> leading = List.of();
    private List<TranslucentButton> buttons = List.of();
    private List<AbstractWidget> tabOrder = List.of();
    private List<AbstractWidget> fieldControls = List.of();
    private List<ComboBoxWidget> dropdowns = List.of();
    private List<TranslucentTextField> suggestionFields = List.of();
    private List<Renderable> staticParts = List.of();
    private @Nullable Arrangement arrangement;
    private boolean built;
    private boolean formReady;
    private boolean focusChosen;
    private boolean hasStatus;
    private @Nullable Component shownStatus;
    private @Nullable Component resultMessage;
    private WaypointFormCheck.Field resultField = WaypointFormCheck.Field.NONE;
    private @Nullable FormField hoveredField;
    private long hoveredSince;
    private String lastName;
    private boolean enforcingCoordinateMode;

    /** The rows above Name, in order: Add's Dimension and List. */
    protected abstract List<LeadingRow> leadingRows();

    /** The muted line under the title, or null for none. */
    protected abstract @Nullable Component subtitle();

    /** Whether the panel has a Display name row below Name. */
    protected abstract boolean hasDisplayNameRow();

    /** The footer's buttons, left to right. */
    protected abstract List<TranslucentButton> footerButtons();

    /** The footer's Add or Save button: it is active only when the form can be sent. */
    protected abstract TranslucentButton primaryButton();

    /** The values the checks run on. */
    protected abstract WaypointFormCheck.Input checkInput();

    /** Sends the form; called only when the checks pass and the primary button is active. */
    protected abstract void submit();

    /** The message to show while a request is pending, or null when none is; it locks the form. */
    protected abstract @Nullable Component pendingMessage();

    /**
     * Sets the footer buttons' {@code active} flags. {@code locked} covers the color picker and a
     * pending request; {@code canSubmit} adds the checks and, on Edit, whether anything changed.
     */
    protected abstract void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed);

    /** Whether the form differs from what it started with; Edit's Save and Reset need this. */
    protected boolean hasChanges() {
        return true;
    }

    /** The control to focus when the screen opens, or null for none. */
    protected @Nullable GuiEventListener initialFocus() {
        return null;
    }

    /** Runs every tick, before the checks: a subclass watches its pending request here. */
    protected void onTick() {
    }

    public AbstractWaypointPropertiesScreen(Screen previousScreen, Component title, String dimensionName, String listName, @Nullable SimpleWaypoint waypoint) {
        super(title);
        this.previousScreen = previousScreen;
        this.dimensionName = dimensionName;
        this.listName = listName;
        this.saved = waypoint == null ? null : WaypointFormPatch.Saved.of(waypoint);
        this.emptyDisplayNameOverride = waypoint != null && "".equals(waypoint.displayNameOverride());
        this.iconPicker = new WaypointIconPicker(174, ignored -> this.onFormEdited());
        this.titleText = new ScalableText(0, 0, title, TITLE_SCALE, TEXT_PRIMARY, font);
        this.statusText = new ScalableText(0, 0, Component.empty(), 1.0F, TEXT_MUTED, MIN_STATUS_WIDTH, font);
        this.swatchWidget.visible = false;

        this.nameEditBox.setMaxLength(65_535);
        this.displayNameEditBox.setMaxLength(MAX_NAME_LENGTH);
        this.keywordsEditBox.setMaxLength(WaypointFormCheck.MAX_KEYWORDS_TEXT_LENGTH);
        this.descriptionEditBox.setMaxLength(MAX_DESCRIPTION_LENGTH);
        this.yawEditBox.setMaxLength(4);

        int rgb;
        String initialName = "";
        if (waypoint == null) {
            rgb = 0xFF000000 | randomColor();
            this.coordinateDefaultPos = new WaypointPos(0, 0, 0);
            this.xEditBox.setValue("0");
            this.yEditBox.setValue("0");
            this.zEditBox.setValue("0");
            this.yawEditBox.setValue("0");
            this.globalToggle.setState(true);
            this.iconPicker.setSelectedIcon(null);
        } else {
            WaypointFormPatch.Saved values = Objects.requireNonNull(this.saved);
            initialName = values.name();
            rgb = 0xFF000000 | values.rgb();
            this.coordinateDefaultPos = values.position();
            this.nameEditBox.setValue(values.name());
            this.displayNameEditBox.setValue(values.displayNameOverride() == null ? "" : values.displayNameOverride());
            this.initialsEditBox.setValue(values.initials());
            this.keywordsEditBox.setValue(String.join(", ", values.keywords()));
            this.descriptionEditBox.setValue(values.description());
            this.xEditBox.setValue(Integer.toString(values.position().x()));
            this.xEditBox.setDefaultValue(values.position().x());
            this.yEditBox.setValue(Integer.toString(values.position().y()));
            this.yEditBox.setDefaultValue(values.position().y());
            this.zEditBox.setValue(Integer.toString(values.position().z()));
            this.zEditBox.setDefaultValue(values.position().z());
            this.yawEditBox.setValue(Integer.toString(values.yaw()));
            this.yawEditBox.setDefaultValue(values.yaw());
            this.globalToggle.setState(values.global());
            this.iconPicker.setSelectedIcon(values.icon());
        }
        this.lastName = initialName;
        this.colorEditBox.setColor(rgb);
        this.colorPickerButton.setColor(rgb);
        this.swatchWidget.setColor(rgb);
        this.swatchWidget.setPreviousColor(rgb);

        this.displayNameEditBox.setPlaceholder(() -> this.emptyDisplayNameOverride
                ? Component.translatable("waypoint.form.empty_display_name")
                : Component.literal(this.nameEditBox.getValue()));
        this.keywordsEditBox.setPlaceholder(() -> Component.translatable("waypoint.form.optional"));
        this.descriptionEditBox.setPlaceholder(() -> Component.translatable("waypoint.form.optional"));
        this.initialsEditBox.setSuggestionsProvider(this::getWaypointInitialsSuggestions);
        this.configureResponders();
        this.configureCoordinateModeEnforcement();
        this.configureCoordinateSuggestions();
    }

    private void configureResponders() {
        this.nameEditBox.setResponder(name -> {
            String initials = WaypointFormInitials.afterNameChange(this.lastName, name, this.initialsEditBox.getValue());
            this.lastName = name;
            if (!initials.equals(this.initialsEditBox.getValue())) {
                this.initialsEditBox.setValue(initials);
            }
            this.onFormEdited();
        });
        this.colorEditBox.setResponder(text -> {
            this.colorPickerButton.setColor(this.colorEditBox.getColor());
            this.onFormEdited();
        });
        for (EditBox field : List.of(this.displayNameEditBox, this.initialsEditBox, this.yawEditBox,
                this.keywordsEditBox, this.descriptionEditBox)) {
            field.setResponder(text -> this.onFormEdited());
        }
    }

    // ------------------------------------------------------------------ building

    /** One labelled row above Name whose single control fills the control column. */
    protected record LeadingRow(FormField field, AbstractWidget control, IntConsumer setWidth) {
    }

    /** The form's fields, each with the tooltip shown when the pointer rests on its label or controls. */
    enum FormField {
        DIMENSION("waypoint.form.dimension", "waypoint.form.dimension.tooltip"),
        LIST("waypoint.form.list", "waypoint.form.list.tooltip"),
        NAME("waypoint.form.name", "waypoint.form.name.tooltip"),
        INITIALS("waypoint.form.initials", "waypoint.form.initials.tooltip"),
        DISPLAY_NAME("waypoint.form.display_name", "waypoint.form.display_name.tooltip"),
        ICON("waypoint.icon.label", "waypoint.form.icon.tooltip"),
        COLOR("waypoint.form.color", "waypoint.form.color.tooltip"),
        VISIBILITY("waypoint.form.visibility", "waypoint.form.visibility.tooltip"),
        POSITION("waypoint.form.position", "waypoint.form.position.tooltip"),
        YAW("waypoint.form.yaw", "waypoint.form.yaw.tooltip"),
        KEYWORDS("waypoint.form.keywords", "waypoint.form.keywords.tooltip"),
        DESCRIPTION("waypoint.form.description", "waypoint.form.description.tooltip");

        private final String labelKey;
        private final String tooltipKey;

        FormField(String labelKey, String tooltipKey) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
        }

        String labelKey() {
            return this.labelKey;
        }

        String tooltipKey() {
            return this.tooltipKey;
        }
    }

    /** Builds what depends on the subclass, once both constructors have finished. */
    private void buildForm() {
        this.leading = List.copyOf(this.leadingRows());
        this.buttons = List.copyOf(this.footerButtons());
        boolean displayName = this.hasDisplayNameRow();
        @Nullable Component subtitle = this.subtitle();
        if (subtitle != null) {
            this.subtitleText = new ScalableText(0, 0, subtitle, TEXT_MUTED, font);
        }
        for (FormField field : FormField.values()) {
            this.labels.put(field, new ScalableText(0, 0, Component.translatable(field.labelKey()), TEXT_PRIMARY, font));
        }

        List<AbstractWidget> controls = new ArrayList<>();
        List<ComboBoxWidget> dropdownList = new ArrayList<>();
        List<TranslucentTextField> textFields = new ArrayList<>();
        for (LeadingRow row : this.leading) {
            controls.add(row.control());
            if (row.control() instanceof ComboBoxWidget combo) {
                dropdownList.add(combo);
            } else if (row.control() instanceof TranslucentTextField field) {
                textFields.add(field);
            }
            this.hoverParts.put(row.field(), List.of(this.labels.get(row.field()), row.control()));
        }
        controls.add(this.nameEditBox);
        controls.add(this.initialsEditBox);
        if (displayName) {
            controls.add(this.displayNameEditBox);
        }
        controls.add(this.iconPicker.menu());
        controls.add(this.iconPicker.clearButton());
        controls.add(this.colorPickerButton);
        controls.add(this.colorEditBox);
        controls.add(this.globalToggle);
        controls.add(this.xEditBox);
        controls.add(this.yEditBox);
        controls.add(this.zEditBox);
        controls.add(this.yawEditBox);
        controls.add(this.keywordsEditBox);
        controls.add(this.descriptionEditBox);
        dropdownList.add(this.iconPicker.menu());
        textFields.addAll(List.of(this.nameEditBox, this.initialsEditBox, this.xEditBox, this.yEditBox,
                this.zEditBox, this.yawEditBox));
        if (displayName) {
            textFields.add(this.displayNameEditBox);
        }
        this.fieldControls = List.copyOf(controls);
        this.dropdowns = List.copyOf(dropdownList);
        this.suggestionFields = List.copyOf(textFields);
        List<AbstractWidget> order = new ArrayList<>(controls);
        order.addAll(this.buttons);
        this.tabOrder = List.copyOf(order);

        this.hoverParts.put(FormField.NAME, List.of(this.labels.get(FormField.NAME), this.nameEditBox));
        this.hoverParts.put(FormField.INITIALS, List.of(this.labels.get(FormField.INITIALS), this.initialsEditBox));
        if (displayName) {
            this.hoverParts.put(FormField.DISPLAY_NAME, List.of(this.labels.get(FormField.DISPLAY_NAME), this.displayNameEditBox));
        }
        // The remove button has its own tooltip, so it isn't part of the Icon field's area.
        this.hoverParts.put(FormField.ICON, List.of(this.labels.get(FormField.ICON), this.iconPreview, this.iconPicker.menu()));
        this.hoverParts.put(FormField.COLOR, List.of(this.labels.get(FormField.COLOR), this.colorPickerButton, this.colorEditBox));
        this.hoverParts.put(FormField.VISIBILITY, List.of(this.labels.get(FormField.VISIBILITY), this.globalToggle));
        this.hoverParts.put(FormField.POSITION, List.of(this.labels.get(FormField.POSITION), this.xLabel, this.xEditBox,
                this.yLabel, this.yEditBox, this.zLabel, this.zEditBox));
        this.hoverParts.put(FormField.YAW, List.of(this.labels.get(FormField.YAW), this.yawEditBox));
        this.hoverParts.put(FormField.KEYWORDS, List.of(this.labels.get(FormField.KEYWORDS), this.keywordsEditBox));
        this.hoverParts.put(FormField.DESCRIPTION, List.of(this.labels.get(FormField.DESCRIPTION), this.descriptionEditBox));
        this.built = true;
    }

    @Override
    protected void init() {
        super.init();
        if (!this.built) {
            this.buildForm();
        }
        for (AbstractWidget widget : this.tabOrder) {
            this.addRenderableWidget(widget);
        }
        this.addRenderableWidget(this.swatchWidget);
        this.formReady = true;
        this.refreshControlStates();
        this.layoutForm();
        if (!this.focusChosen) {
            this.focusChosen = true;
            GuiEventListener focus = this.initialFocus();
            if (focus != null) {
                this.setInitialFocus(focus);
            }
        }
    }

    //? if >= 1.20.5 {
    /** The form chooses its own first focus in {@code init}; vanilla's would take the first Tab stop after a keyboard press. */
    @Override
    void pickInitialFocus() {
    }
    //?}

    /** A resize keeps the widgets, so their values, focus, status and pending request stay. */
    @Override
    protected void repositionElements() {
        this.layoutForm();
    }

    @Override
    int getContentWidth() {
        return this.arrangement == null ? 0 : this.arrangement.panelWidth();
    }

    @Override
    int getContentHeight() {
        return this.arrangement == null ? 0 : this.arrangement.groupHeight();
    }

    // ------------------------------------------------------------------ layout

    /** A row or divider that knows how tall it is and how to place its parts once the layout says where. */
    private record PanelItem(WaypointFormLayout.Item size, RowPlacement placement) {
    }

    private interface RowPlacement {
        void place(Arrangement layout, int top, int height);
    }

    private void layoutForm() {
        if (!this.built) {
            return;
        }
        int axisLetter = this.axisLetterWidth();
        ScalableText initialsLabel = this.labels.get(FormField.INITIALS);
        ScalableText visibilityLabel = this.labels.get(FormField.VISIBILITY);
        ScalableText yawLabel = this.labels.get(FormField.YAW);
        int colorGroup = VisualPositioning.getVisualWidth(this.colorPickerButton)
                + WaypointFormLayout.CONTROL_GAP + VisualPositioning.getVisualWidth(this.colorEditBox);
        List<FormField> leftFields = this.leftColumnFields();
        int widest = 0;
        for (FormField field : leftFields) {
            widest = Math.max(widest, font.width(Component.translatable(field.labelKey())));
        }
        Columns columns = WaypointFormLayout.columns(this.width, widest, WaypointFormLayout.minimumControlWidth(
                axisLetter, yawLabel.getWidth(), colorGroup, visibilityLabel.getWidth()));
        for (FormField field : leftFields) {
            this.labels.get(field).setMaxWidth(columns.labelTextWidth());
        }

        List<PanelItem> items = new ArrayList<>();
        for (LeadingRow row : this.leading) {
            items.add(this.stretchedRow(row.field(), row.control(), row.setWidth(), columns));
        }
        if (!this.leading.isEmpty()) {
            items.add(this.dividerItem());
        }
        items.add(this.nameRow(columns, initialsLabel));
        if (this.hasDisplayNameRow()) {
            items.add(this.stretchedRow(FormField.DISPLAY_NAME, this.displayNameEditBox, this.displayNameEditBox::setWidth, columns));
        }
        items.add(this.iconRow(columns));
        items.add(this.colorRow(columns, visibilityLabel));
        items.add(this.positionRow(columns, axisLetter, yawLabel));
        items.add(this.dividerItem());
        items.add(this.stretchedRow(FormField.KEYWORDS, this.keywordsEditBox, this.keywordsEditBox::setWidth, columns));
        items.add(this.stretchedRow(FormField.DESCRIPTION, this.descriptionEditBox, this.descriptionEditBox::setWidth, columns));

        this.titleText.setText(cutToWidth(font, this.getTitle(), (int) (columns.panelWidth() / TITLE_SCALE)));
        int headerHeight = this.titleText.getHeight();
        if (this.subtitleText != null) {
            this.subtitleText.setText(cutToWidth(font, this.subtitle(), columns.panelWidth()));
            headerHeight += WaypointFormLayout.TITLE_SUBTITLE_GAP + this.subtitleText.getHeight();
        }
        int[] buttonWidths = new int[this.buttons.size()];
        int buttonHeight = 0;
        for (int i = 0; i < buttonWidths.length; i++) {
            buttonWidths[i] = VisualPositioning.getVisualWidth(this.buttons.get(i));
            buttonHeight = Math.max(buttonHeight, VisualPositioning.getVisualHeight(this.buttons.get(i)));
        }
        int buttonsWidth = WaypointFormLayout.buttonsWidth(buttonWidths);
        this.statusText.setMaxWidth(WaypointFormLayout.statusWidth(columns.panelWidth(), buttonsWidth));
        int statusHeight = this.hasStatus ? this.statusText.getHeight() : 0;

        List<WaypointFormLayout.Item> sizes = new ArrayList<>();
        for (PanelItem item : items) {
            sizes.add(item.size());
        }
        Arrangement layout = WaypointFormLayout.arrange(this.width, this.height, columns, headerHeight, sizes,
                buttonsWidth, buttonHeight, statusHeight);
        this.arrangement = layout;

        List<Renderable> parts = new ArrayList<>();
        parts.add(this.titleText);
        this.titleText.setPosition(layout.panelX(), layout.groupTop());
        if (this.subtitleText != null) {
            this.subtitleText.setPosition(layout.panelX(),
                    layout.groupTop() + this.titleText.getHeight() + WaypointFormLayout.TITLE_SUBTITLE_GAP);
            parts.add(this.subtitleText);
        }
        for (int i = 0; i < items.size(); i++) {
            items.get(i).placement().place(layout, layout.itemTops().get(i), sizes.get(i).height());
        }
        for (FormField field : leftFields) {
            parts.add(this.labels.get(field));
        }
        parts.add(initialsLabel);
        parts.add(visibilityLabel);
        parts.add(yawLabel);
        parts.add(this.xLabel);
        parts.add(this.yLabel);
        parts.add(this.zLabel);
        int dividerWidth = columns.labelWidth() + columns.controlWidth();
        for (int i = 0; i < items.size(); i++) {
            if (sizes.get(i).isDivider()) {
                parts.add(new SeparatorWidget(layout.labelX(), layout.itemTops().get(i), dividerWidth,
                        WaypointFormLayout.DIVIDER_HEIGHT));
            }
        }
        this.staticParts = List.copyOf(parts);

        WidgetPack footer = new WidgetPack(layout.panelX(), layout.buttonsY(), layout.panelWidth(),
                layout.buttonsHeight(), Orientation.HORIZONTAL);
        footer.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        for (int i = this.buttons.size() - 1; i >= 0; i--) {
            footer.addChild(this.buttons.get(i), Direction.REVERSE);
            if (i > 0) {
                footer.addChild(SpacerElement.width(WaypointFormLayout.FOOTER_BUTTON_GAP), Direction.REVERSE);
            }
        }
        this.statusText.setPosition(layout.panelX(), layout.statusY());
        this.swatchWidget.setPosition(
                layout.panelX() + centered(layout.panelWidth(), this.swatchWidget.getWidth()),
                layout.panelY() + centered(layout.panelHeight(), this.swatchWidget.getHeight())
        );
    }

    /** The fields whose labels are in the label column, top to bottom. */
    private List<FormField> leftColumnFields() {
        List<FormField> fields = new ArrayList<>();
        for (LeadingRow row : this.leading) {
            fields.add(row.field());
        }
        fields.add(FormField.NAME);
        if (this.hasDisplayNameRow()) {
            fields.add(FormField.DISPLAY_NAME);
        }
        fields.add(FormField.ICON);
        fields.add(FormField.COLOR);
        fields.add(FormField.POSITION);
        fields.add(FormField.KEYWORDS);
        fields.add(FormField.DESCRIPTION);
        return fields;
    }

    private int axisLetterWidth() {
        int width = 0;
        for (char letter : AXIS_LETTERS.toCharArray()) {
            width = Math.max(width, font.width(String.valueOf(letter)));
        }
        return width;
    }

    private PanelItem stretchedRow(FormField field, AbstractWidget control, IntConsumer setWidth, Columns columns) {
        setWidth.accept(columns.controlWidth());
        ScalableText label = this.labels.get(field);
        int height = Math.max(label.getHeight(), VisualPositioning.getVisualHeight(control));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(control, layout.controlX(), top, rowHeight);
        });
    }

    private PanelItem dividerItem() {
        return new PanelItem(WaypointFormLayout.Item.divider(), (layout, top, height) -> {
        });
    }

    private PanelItem nameRow(Columns columns, ScalableText initialsLabel) {
        int rightGroup = initialsLabel.getWidth() + WaypointFormLayout.INLINE_GAP + WaypointFormLayout.SMALL_FIELD_WIDTH;
        this.nameEditBox.setWidth(WaypointFormLayout.stretchedWidth(columns.controlWidth(), rightGroup));
        this.initialsEditBox.setWidth(WaypointFormLayout.SMALL_FIELD_WIDTH);
        ScalableText label = this.labels.get(FormField.NAME);
        int height = Math.max(label.getHeight(), VisualPositioning.getVisualHeight(this.nameEditBox));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(this.nameEditBox, layout.controlX(), top, rowHeight);
            int initialsX = layout.controlX() + columns.controlWidth() - WaypointFormLayout.SMALL_FIELD_WIDTH;
            placeInRow(this.initialsEditBox, initialsX, top, rowHeight);
            placeInRow(initialsLabel, initialsX - WaypointFormLayout.INLINE_GAP - initialsLabel.getWidth(), top, rowHeight);
        });
    }

    private PanelItem iconRow(Columns columns) {
        ComboBoxWidget menu = this.iconPicker.menu();
        AbstractWidget clear = this.iconPicker.clearButton();
        int clearWidth = VisualPositioning.getVisualWidth(clear);
        menu.setWidth(WaypointFormLayout.iconDropdownWidth(columns.controlWidth(), PREVIEW_SIZE, clearWidth));
        ScalableText label = this.labels.get(FormField.ICON);
        int height = Math.max(label.getHeight(), Math.max(PREVIEW_SIZE,
                Math.max(VisualPositioning.getVisualHeight(menu), VisualPositioning.getVisualHeight(clear))));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(this.iconPreview, layout.controlX(), top, rowHeight);
            placeInRow(menu, layout.controlX() + PREVIEW_SIZE + WaypointFormLayout.CONTROL_GAP, top, rowHeight);
            placeInRow(clear, layout.controlX() + columns.controlWidth() - clearWidth, top, rowHeight);
        });
    }

    private PanelItem colorRow(Columns columns, ScalableText visibilityLabel) {
        ScalableText label = this.labels.get(FormField.COLOR);
        int height = Math.max(label.getHeight(), Math.max(VisualPositioning.getVisualHeight(this.colorPickerButton),
                Math.max(VisualPositioning.getVisualHeight(this.colorEditBox), VisualPositioning.getVisualHeight(this.globalToggle))));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(this.colorPickerButton, layout.controlX(), top, rowHeight);
            placeInRow(this.colorEditBox, layout.controlX() + VisualPositioning.getVisualWidth(this.colorPickerButton)
                    + WaypointFormLayout.CONTROL_GAP, top, rowHeight);
            int toggleX = layout.controlX() + columns.controlWidth() - VisualPositioning.getVisualWidth(this.globalToggle);
            placeInRow(this.globalToggle, toggleX, top, rowHeight);
            placeInRow(visibilityLabel, toggleX - WaypointFormLayout.INLINE_GAP - visibilityLabel.getWidth(), top, rowHeight);
        });
    }

    private PanelItem positionRow(Columns columns, int axisLetter, ScalableText yawLabel) {
        int fieldWidth = WaypointFormLayout.coordinateFieldWidth(columns.controlWidth(), axisLetter, yawLabel.getWidth());
        this.xEditBox.setWidth(fieldWidth);
        this.yEditBox.setWidth(fieldWidth);
        this.zEditBox.setWidth(fieldWidth);
        this.yawEditBox.setWidth(WaypointFormLayout.SMALL_FIELD_WIDTH);
        ScalableText label = this.labels.get(FormField.POSITION);
        int height = Math.max(label.getHeight(), VisualPositioning.getVisualHeight(this.xEditBox));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            ScalableText[] letters = {this.xLabel, this.yLabel, this.zLabel};
            CoordinateField[] fields = {this.xEditBox, this.yEditBox, this.zEditBox};
            int x = layout.controlX();
            for (int i = 0; i < letters.length; i++) {
                placeInRow(letters[i], x, top, rowHeight);
                x += axisLetter + WaypointFormLayout.AXIS_GAP;
                placeInRow(fields[i], x, top, rowHeight);
                x += fieldWidth + WaypointFormLayout.FIELD_GAP;
            }
            int yawX = layout.controlX() + columns.controlWidth() - WaypointFormLayout.SMALL_FIELD_WIDTH;
            placeInRow(this.yawEditBox, yawX, top, rowHeight);
            placeInRow(yawLabel, yawX - WaypointFormLayout.INLINE_GAP - yawLabel.getWidth(), top, rowHeight);
        });
    }

    /** Moves an element so the top-left corner of its outline is at ({@code x}, {@code y}). */
    private static void placeOutline(LayoutElement element, int x, int y) {
        element.setPosition(x, y);
        // Padded widgets anchor their content, not their outline; move them by the difference.
        element.setPosition(x + (x - VisualPositioning.getVisualX(element)), y + (y - VisualPositioning.getVisualY(element)));
    }

    /** Places an element at {@code x}, centered vertically on a row. */
    private static void placeInRow(LayoutElement element, int x, int rowTop, int rowHeight) {
        placeOutline(element, x, rowTop + ((rowHeight - VisualPositioning.getVisualHeight(element)) >> 1));
    }

    /** {@code text} cut to fit {@code width}, ending in an ellipsis when it doesn't fit. */
    static Component cutToWidth(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        FormattedText head = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        MutableComponent cut = Component.empty();
        head.visit((style, contents) -> {
            cut.append(Component.literal(contents).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return cut.append(ELLIPSIS);
    }

    // ------------------------------------------------------------------ state

    /**
     * Sets every control's {@code active} flag and the status message from the current state: the color
     * picker, a pending request, the checks and whether Edit's form differs from the saved waypoint.
     */
    protected final void refreshControlStates() {
        if (!this.formReady) {
            return;
        }
        boolean modal = this.swatchWidget.visible;
        Component pending = this.pendingMessage();
        boolean locked = modal || pending != null;
        WaypointFormCheck.Problem problem = WaypointFormCheck.firstProblem(this.checkInput(), CLIENT_DATA);
        boolean changed = this.hasChanges();
        boolean canSubmit = !locked && !(problem != null && problem.blocks()) && changed;

        for (AbstractWidget control : this.fieldControls) {
            setControlActive(control, !locked);
        }
        this.iconPicker.clearButton().active = !locked && this.iconPicker.getSelectedIcon() != null;
        this.refreshButtons(modal, locked, canSubmit, changed);

        WaypointFormCheck.Field errorField = problem != null && problem.kind() == WaypointFormCheck.Kind.ERROR
                ? problem.field() : WaypointFormCheck.Field.NONE;
        this.nameEditBox.setInvalid(this.marks(WaypointFormCheck.Field.NAME, errorField));
        this.displayNameEditBox.setInvalid(this.marks(WaypointFormCheck.Field.DISPLAY_NAME, errorField));
        this.keywordsEditBox.setInvalid(this.marks(WaypointFormCheck.Field.KEYWORDS, errorField));
        this.descriptionEditBox.setInvalid(this.marks(WaypointFormCheck.Field.DESCRIPTION, errorField));

        if (pending != null) {
            this.showStatus(pending, TEXT_MUTED);
        } else if (this.resultMessage != null) {
            this.showStatus(this.resultMessage, DANGER);
        } else if (problem != null) {
            this.showStatus(this.messageFor(problem), problem.kind() == WaypointFormCheck.Kind.ERROR ? DANGER : TEXT_MUTED);
        } else {
            this.showStatus(null, TEXT_MUTED);
        }
    }

    /** Locks text editing as well as activation: older EditBox input ignores the active flag. */
    static void setControlActive(AbstractWidget control, boolean active) {
        if (control.active != active && control instanceof EditBox field) {
            field.setEditable(active);
        }
        control.active = active;
    }

    private boolean marks(WaypointFormCheck.Field field, WaypointFormCheck.Field errorField) {
        return field == errorField || field == this.resultField;
    }

    /** Whether the client's waypoint data has a waypoint with exactly this name in that list. */
    protected static boolean hasWaypoint(String dimension, String list, String name) {
        return CLIENT_DATA.hasWaypoint(dimension, list, name);
    }

    /** Shows a message from the server or a failed send in the footer, marking a field when it names one. */
    protected final void showResult(Component message, WaypointFormCheck.Field field) {
        this.resultMessage = message;
        this.resultField = field;
        this.refreshControlStates();
    }

    /** Editing any field removes a message from a server result or a timeout; the checks decide what shows next. */
    protected final void onFormEdited() {
        if (!this.formReady) {
            return;
        }
        this.resultMessage = null;
        this.resultField = WaypointFormCheck.Field.NONE;
        this.refreshControlStates();
    }

    /** Runs the checks again and, when nothing blocks, hands over to the screen's submit action. */
    protected final void submitForm() {
        this.refreshControlStates();
        if (!this.primaryButton().active) {
            return;
        }
        this.submit();
        this.refreshControlStates();
    }

    private Component messageFor(WaypointFormCheck.Problem problem) {
        Object[] arguments = problem.arguments().toArray();
        if (problem.message() == WaypointFormCheck.Message.NAME_TAKEN) {
            arguments[0] = parseFormattedText((String) arguments[0]);
        }
        return Component.translatable(problem.message().translationKey(), arguments);
    }

    /** Changes the footer message; the layout runs again because a wrapped message changes the footer's height. */
    private void showStatus(@Nullable Component message, WidgetThemeVariable color) {
        this.statusText.setColor(color);
        if (Objects.equals(message, this.shownStatus)) {
            return;
        }
        this.shownStatus = message;
        this.hasStatus = message != null;
        if (message != null) {
            this.statusText.setText(message);
        }
        this.layoutForm();
    }

    @Override
    public void tick() {
        super.tick();
        this.onTick();
        this.refreshControlStates();
    }

    // ------------------------------------------------------------------ the color picker

    private void openSwatch() {
        this.swatchWidget.visible = true;
        this.swatchWidget.setColor(this.colorPickerButton.getColor());
        this.refreshControlStates();
        this.setFocused(this.swatchWidget);
    }

    protected void closeSwatch() {
        this.swatchWidget.visible = false;
        this.refreshControlStates();
        this.setFocused(this.colorPickerButton);
    }

    // ------------------------------------------------------------------ coordinates

    protected WaypointPos resolveCoordinateFields() {
        PlayerCoordinates playerCoordinates = getPlayerCoordinates();
        return CoordinateInputParser.resolve(
                this.xEditBox.getValue(),
                this.yEditBox.getValue(),
                this.zEditBox.getValue(),
                playerCoordinates.pos(),
                this.coordinateDefaultPos,
                playerCoordinates.pitch(),
                playerCoordinates.yaw()
        );
    }

    protected List<String> getWaypointInitialsSuggestions() {
        return getInitialsCandidatesFromName(plainText(this.nameEditBox.getValue()));
    }

    private void configureCoordinateModeEnforcement() {
        this.xEditBox.setValueChangedCallback(this::coordinateEdited);
        this.yEditBox.setValueChangedCallback(this::coordinateEdited);
        this.zEditBox.setValueChangedCallback(this::coordinateEdited);
    }

    private void coordinateEdited(CoordinateField editedField) {
        this.enforceCoordinateMode(editedField);
        this.onFormEdited();
    }

    private void configureCoordinateSuggestions() {
        this.xEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.X));
        this.yEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.Y));
        this.zEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.Z));
        this.yawEditBox.setSuggestionsProvider(this::getYawSuggestions);
    }

    private List<String> getCoordinateSuggestions(CoordinateSuggestions.Axis axis) {
        return CoordinateSuggestions.forAxis(axis, getLookedAtBlockPos());
    }

    private List<String> getYawSuggestions() {
        return CoordinateSuggestions.forYaw(getPlayerCoordinates().yaw());
    }

    private void enforceCoordinateMode(CoordinateField editedField) {
        if (this.enforcingCoordinateMode) {
            return;
        }
        this.enforcingCoordinateMode = true;
        try {
            if (isLocalCoordinateField(editedField)) {
                setLocalIfNeeded(this.xEditBox);
                setLocalIfNeeded(this.yEditBox);
                setLocalIfNeeded(this.zEditBox);
                setCoordinateLabelsLocal(true);
            } else if (hasLocalCoordinateField()) {
                setDefaultAbsoluteIfLocal(this.xEditBox);
                setDefaultAbsoluteIfLocal(this.yEditBox);
                setDefaultAbsoluteIfLocal(this.zEditBox);
                setCoordinateLabelsLocal(false);
            } else {
                setCoordinateLabelsLocal(false);
            }
        } finally {
            this.enforcingCoordinateMode = false;
        }
    }

    private void setCoordinateLabelsLocal(boolean local) {
        this.xLabel.setText(local ? "R" : "X");
        this.yLabel.setText(local ? "U" : "Y");
        this.zLabel.setText(local ? "F" : "Z");
    }

    private boolean hasLocalCoordinateField() {
        return isLocalCoordinateField(this.xEditBox) || isLocalCoordinateField(this.yEditBox) || isLocalCoordinateField(this.zEditBox);
    }

    private boolean isLocalCoordinateField(CoordinateField field) {
        return CoordinateInputParser.isLocalCoordinateExpression(field.getValue());
    }

    private void setLocalIfNeeded(CoordinateField field) {
        if (!isLocalCoordinateField(field)) {
            field.setValue("^");
        }
    }

    private void setDefaultAbsoluteIfLocal(CoordinateField field) {
        if (isLocalCoordinateField(field)) {
            field.setValue(Integer.toString(getDefaultCoordinate(field)));
        }
    }

    private int getDefaultCoordinate(CoordinateField field) {
        if (field == this.xEditBox) {
            return this.coordinateDefaultPos.x();
        }
        if (field == this.yEditBox) {
            return this.coordinateDefaultPos.y();
        }
        return this.coordinateDefaultPos.z();
    }

    private PlayerCoordinates getPlayerCoordinates() {
        Minecraft minecraftClient = Minecraft.getInstance();
        Entity entity = minecraftClient.player != null ? minecraftClient.player : minecraftClient.getCameraEntity();
        if (entity == null) {
            return new PlayerCoordinates(this.coordinateDefaultPos, 0.0F, 0.0F);
        }
        BlockPos blockPos = entity.blockPosition();
        return new PlayerCoordinates(
                new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                entity.getXRot(),
                entity.getYRot()
        );
    }

    private @Nullable WaypointPos getLookedAtBlockPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }

        Vec3 start = mc.player.getEyePosition(1.0F);
        double reach;
        //? if >= 1.20.5 {
        reach = mc.player.blockInteractionRange();
        //?} else {
        /*reach = mc.gameMode == null ? 4.5D : mc.gameMode.getPickRange();
        *///?}
        Vec3 end = start.add(mc.player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos blockPos = hit.getBlockPos();
        if (mc.level.getBlockState(blockPos).isAir()) {
            return null;
        }
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    // ------------------------------------------------------------------ input

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (this.clickDropdown(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button())) {
            return true;
        }
        if (this.mouseClickedTextFieldSuggestion(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button())) {
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClicked);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.clickDropdown(mouseX, mouseY, button)) {
            return true;
        }
        if (this.mouseClickedTextFieldSuggestion(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    //? if >= 1.21.9 {
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.releaseDropdown(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }
    //?} else {
    /*@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.releaseDropdown(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    *///?}

    //? if <= 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (this.scrollDropdown(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollDropdown(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = this.getFocused();
        this.acceptMovementKeys(!(focused instanceof EditBox) && !(focused instanceof ComboBoxWidget));
        if (keyCode == InputConstants.KEY_ESCAPE && this.swatchWidget.visible) {
            this.closeSwatch();
            return true;
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && focused instanceof EditBox field && this.enterInTextField(field)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Enter in a text field picks the highlighted suggestion while a list is open, and otherwise sends
     * the form if Add or Save is active. Returns false when Enter isn't used, so the field sees it.
     */
    private boolean enterInTextField(EditBox field) {
        if (field instanceof SuggestingTextInput input && input.acceptHighlightedSuggestion()) {
            return true;
        }
        if (!this.primaryButton().active) {
            return false;
        }
        this.submitForm();
        return true;
    }

    private boolean clickDropdown(double mouseX, double mouseY, int button) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isMouseOver(mouseX, mouseY) && dropdown.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(dropdown);
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    this.setDragging(true);
                }
                return true;
            }
            dropdown.closeMenuIfOutside(mouseX, mouseY);
        }
        return false;
    }

    private boolean releaseDropdown(double mouseX, double mouseY, int button) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.mouseReleased(mouseX, mouseY, button)) {
                this.setDragging(false);
                return true;
            }
        }
        return false;
    }

    private boolean scrollDropdown(double mouseX, double mouseY, double verticalAmount) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isExpanded() && dropdown.mouseScrolled(mouseX, mouseY, 0, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    private boolean mouseClickedTextFieldSuggestion(double mouseX, double mouseY, int button) {
        GuiEventListener focused = this.getFocused();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && focused instanceof TranslucentTextField textField
                && textField.mouseClickedSuggestion(mouseX, mouseY)) {
            this.setDragging(true);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Arrangement layout = this.arrangement;
        if (layout == null) {
            return;
        }
        // Popup placement must be current before deciding which layer owns hover.
        for (ComboBoxWidget dropdown : this.dropdowns) {
            dropdown.layoutPopup(this.height, 8);
        }
        // Covered controls still calculate hover and request cursors during rendering.
        boolean blockContentHover = this.swatchWidget.visible || this.isMouseOverPopup(mouseX, mouseY);
        int contentMouseX = blockContentHover ? NO_MOUSE : mouseX;
        int contentMouseY = blockContentHover ? NO_MOUSE : mouseY;
        int popupMouseX = this.swatchWidget.visible ? NO_MOUSE : mouseX;
        int popupMouseY = this.swatchWidget.visible ? NO_MOUSE : mouseY;
        context.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(),
                layout.panelY() + layout.panelHeight(), getColor(PANEL_BACKGROUND));
        renderOutline(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), getColor(BORDER));
        for (Renderable part : this.staticParts) {
            part.
            //$ render_method_swap
            extractRenderState
                    (context, contentMouseX, contentMouseY, delta);
        }
        for (AbstractWidget widget : this.tabOrder) {
            widget.
            //$ render_method_swap
            extractRenderState
                    (context, contentMouseX, contentMouseY, delta);
        }
        if (this.hasStatus) {
            this.statusText.
            //$ render_method_swap
            extractRenderState
                    (context, contentMouseX, contentMouseY, delta);
        }
        this.drawIconPreview(context, popupMouseX, popupMouseY);
        nextLayer(context);
        for (TranslucentTextField field : this.suggestionFields) {
            field.renderSuggestions(context, popupMouseX, popupMouseY);
        }
        previousLayer(context);
        for (ComboBoxWidget dropdown : this.dropdowns) {
            dropdown.renderPopup(context, popupMouseX, popupMouseY, delta);
        }
        this.renderFieldTooltip(context, contentMouseX, contentMouseY);
        nextLayer(context);
        this.swatchWidget.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
        previousLayer(context);
    }

    private boolean isMouseOverPopup(double mouseX, double mouseY) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isMouseOverPopup(mouseX, mouseY)) {
                return true;
            }
        }
        for (TranslucentTextField field : this.suggestionFields) {
            if (field.isMouseOverSuggestion(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void drawIconPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        var resolvedIcon = this.iconPicker.preview(mouseX, mouseY);
        int previewX = this.iconPreview.getX();
        int previewY = this.iconPreview.getY();
        if (resolvedIcon.kind() == WaypointIconRenderer.Kind.INITIALS) {
            WaypointRowRenderer.initials(context, font, this.initialsEditBox.getValue(), previewX,
                    previewY + (PREVIEW_SIZE - font.lineHeight) / 2,
                    this.colorPickerButton.getColor(), WidgetThemeManager.getColor(TEXT_PRIMARY));
        } else {
            WaypointIconRenderer.drawForWaypoint(context, resolvedIcon, previewX, previewY, PREVIEW_SIZE,
                    this.colorPickerButton.getColor());
        }
    }

    /** After the pointer rests on a field's label or controls for 500 ms, shows that field's tooltip at the pointer. */
    private void renderFieldTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        FormField field = this.tooltipBlocked() ? null : this.fieldAt(mouseX, mouseY);
        long now = System.nanoTime();
        if (field != this.hoveredField) {
            this.hoveredField = field;
            this.hoveredSince = now;
            return;
        }
        if (field == null || now - this.hoveredSince < TOOLTIP_DELAY_NANOS) {
            return;
        }
        scheduleTooltipAtPointer(context, Tooltip.create(this.tooltipText(field)).toCharSequence(Minecraft.getInstance()),
                mouseX, mouseY);
    }

    /** No field tooltip shows while a popup or the color picker is open, or while a request is pending. */
    private boolean tooltipBlocked() {
        if (this.swatchWidget.visible || this.pendingMessage() != null) {
            return true;
        }
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isExpanded() || dropdown.isSuggestionListOpen()) {
                return true;
            }
        }
        for (TranslucentTextField field : this.suggestionFields) {
            if (field.isSuggestionListOpen()) {
                return true;
            }
        }
        return false;
    }

    private @Nullable FormField fieldAt(int mouseX, int mouseY) {
        for (Map.Entry<FormField, List<LayoutElement>> entry : this.hoverParts.entrySet()) {
            for (LayoutElement part : entry.getValue()) {
                int left = VisualPositioning.getVisualX(part);
                int top = VisualPositioning.getVisualY(part);
                if (mouseX >= left && mouseX < left + VisualPositioning.getVisualWidth(part)
                        && mouseY >= top && mouseY < top + VisualPositioning.getVisualHeight(part)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Component tooltipText(FormField field) {
        if (field == FormField.VISIBILITY) {
            return Component.translatable(field.tooltipKey(), WaypointClientMod.getClientConfig().getViewDistance());
        }
        return Component.translatable(field.tooltipKey());
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, this.previousScreen);
    }

    private record PlayerCoordinates(WaypointPos pos, float pitch, float yaw) {
    }

    /** The client's waypoint data for the checks: the synced files, or the integrated server's in singleplayer. */
    private static final class ClientData implements WaypointFormCheck.Lookup {
        @Override
        public boolean listExists(String dimension, String list) {
            return list(dimension, list) != null;
        }

        @Override
        public boolean hasWaypoint(String dimension, String list, String name) {
            WaypointList found = list(dimension, list);
            return found != null && found.getWaypointByName(name) != null;
        }

        @Override
        public String listDisplayName(String dimension, String list) {
            WaypointList found = list(dimension, list);
            return found == null ? list : found.displayName();
        }

        private static @Nullable WaypointList list(String dimension, String list) {
            WaypointFileManager manager = WaypointClientMod.getInstance().getWaypointFileManager(dimension);
            return manager == null ? null : manager.getWaypointListByName(list);
        }
    }
}
