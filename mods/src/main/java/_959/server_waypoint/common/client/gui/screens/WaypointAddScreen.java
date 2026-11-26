package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.ClientWaypointAccess;
import _959.server_waypoint.common.client.ClientWaypointSelection;
import _959.server_waypoint.common.client.ClientWaypointSource;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.mergeDimensionNames;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_NAME_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;

/**
 * Adds through a captured collection. Local completes after persistence; server commands keep the
 * form pending until their waypoint appears in that collection's authoritative data.
 */
public class WaypointAddScreen extends AbstractWaypointPropertiesScreen {
    private final DestinationDropdown destinationField;
    private final java.util.Map<ClientWaypointSource, ClientWaypointSelection> destinations = new java.util.EnumMap<>(ClientWaypointSource.class);
    private final ComboBoxWidget dimensionField;
    private final ComboBoxWidget listNameField;
    private final TranslucentButton addButton;
    private final PendingAdd pendingAdd = new PendingAdd();
    private final @Nullable ClientWaypointSelection copySource;

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName) {
        this(previousScreen, dimensionName, listName, null);
    }

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName, WaypointPos defaultPos) {
        this(previousScreen, dimensionName, listName, defaultPos, null);
    }

    /** Opens an add form with a detached map-share draft; no server mutation happens until Add. */
    public static WaypointAddScreen fromWaypoint(Screen previousScreen, String dimensionName, String listName,
            SimpleWaypoint defaults) {
        return new WaypointAddScreen(previousScreen, dimensionName, listName, defaults.pos(), defaults);
    }

    /** Opens an independent copy while retaining its source-read requirement through submission. */
    public static WaypointAddScreen fromCopy(Screen previousScreen, ClientWaypointAccess access, ClientWaypointAccess.CopyDraft draft) {
        SimpleWaypoint defaults = draft.waypoint();
        return new WaypointAddScreen(previousScreen, access, draft.destination(), defaults.pos(), defaults, draft.source());
    }

    private WaypointAddScreen(Screen previousScreen, String dimensionName, String listName, WaypointPos defaultPos,
            @Nullable SimpleWaypoint defaults) {
        this(previousScreen, WaypointClientMod.getInstance().access(),
                WaypointClientMod.getInstance().access().capture(ClientWaypointSource.SHARED, dimensionName, listName, null), defaultPos, defaults);
    }

    /** Explicit source/destination constructor; a view change cannot redirect this form. */
    public WaypointAddScreen(Screen previousScreen, ClientWaypointAccess access, ClientWaypointSelection destination,
            @Nullable WaypointPos defaultPos, @Nullable SimpleWaypoint defaults) {
        this(previousScreen, access, destination, defaultPos, defaults, null);
    }

    private WaypointAddScreen(Screen previousScreen, ClientWaypointAccess access, ClientWaypointSelection destination,
            @Nullable WaypointPos defaultPos, @Nullable SimpleWaypoint defaults, @Nullable ClientWaypointSelection copySource) {
        super(previousScreen, Component.translatable("waypoint.add.screen.title"), destination.dimensionName(), destination.listIdentifier(), defaults, access, destination);
        this.copySource = copySource;
        for (var source : ClientWaypointSource.values()) {
            ClientWaypointSelection captured;
            try { captured = access.capture(source, destination.dimensionName(), destination.listIdentifier(), null); }
            catch (IllegalArgumentException exception) { continue; } // Local has no profile after a load failure.
            if (access.allowed(captured, ClientWaypointAccess.Action.ADD) || source == destination.source()) {
                destinations.put(source, captured);
            }
        }
        this.destinationField = new DestinationDropdown(font,
                destinations.entrySet().stream().filter(entry -> access.allowed(entry.getValue(), ClientWaypointAccess.Action.ADD))
                        .map(java.util.Map.Entry::getKey).toList(), () -> this.selection.source(), this::chooseDestination);
        this.destinationField.setRenderPopupSeparately(true);
        String dimensionName = destination.dimensionName();
        String listName = destination.listIdentifier();
        List<String> dimensions = mergeDimensionNames(
                this.access.dimensions(this.selection),
                List.of(dimensionName)
        );
        this.dimensionField = new ComboBoxWidget(0, 0, 155, Component.translatable("waypoint.form.dimension"), this.font,
                dimensions, dimensionName, value -> this.onDimensionEdited());
        this.dimensionField.setRenderPopupSeparately(true);
        this.dimensionField.useResourceIdMatching();
        this.listNameField = new ComboBoxWidget(0, 0, 90, Component.translatable("waypoint.form.list"), this.font,
                this.access.lists(this.selection), listName, value -> this.onFormEdited());
        this.listNameField.setRenderPopupSeparately(true);
        this.listNameField.setMaxLength(MAX_NAME_LENGTH);
        this.addButton = TranslucentButton.fitted(Component.translatable("waypoint.add.button"), this::submitForm);
        this.configureSuggestions();
        this.setDefaultPos(defaultPos == null ? this.getCurrentDefaultPos() : defaultPos);
        this.refreshDimensionChoices();
    }

    private WaypointPos getCurrentDefaultPos() {
        Minecraft minecraftClient = Minecraft.getInstance();
        //? if >= 1.21.11 {
        BlockPos defaultPos = MinecraftClientHelper.getMainCamera(minecraftClient).blockPosition();
        //?} else {
        /*BlockPos defaultPos = minecraftClient.gameRenderer.getMainCamera().getBlockPosition();
        *///?}
        if (minecraftClient.getCameraEntity() != null) {
            defaultPos = minecraftClient.getCameraEntity().blockPosition();
        }
        return new WaypointPos(defaultPos.getX(), defaultPos.getY(), defaultPos.getZ());
    }

    private void setDefaultPos(WaypointPos defaultPos) {
        int x = defaultPos.x();
        int y = defaultPos.y();
        int z = defaultPos.z();
        this.coordinateDefaultPos = defaultPos;
        this.xEditBox.setDefaultValue(x);
        this.yEditBox.setDefaultValue(y);
        this.zEditBox.setDefaultValue(z);
        this.xEditBox.setValue(Integer.toString(x));
        this.yEditBox.setValue(Integer.toString(y));
        this.zEditBox.setValue(Integer.toString(z));
    }

    private void refreshDimensionChoices() {
        this.dimensionField.setValues(mergeDimensionNames(this.access.dimensions(this.selection), List.of(this.dimensionName)));
        this.onDimensionEdited();
    }

    /** The list choices belong to the chosen dimension, so they follow it; the list name already typed stays. */
    private void onDimensionEdited() {
        this.listNameField.setValues(this.access.lists(this.destination()));
        this.onFormEdited();
    }

    private void configureSuggestions() {
        this.nameEditBox.setSuggestionsProvider(() -> this.access.names(this.destination()));
    }

    @Override
    protected List<LeadingRow> leadingRows() {
        return List.of(
                new LeadingRow(FormField.DESTINATION, this.destinationField, this.destinationField::setWidth),
                new LeadingRow(FormField.DIMENSION, this.dimensionField, this.dimensionField::setWidth),
                new LeadingRow(FormField.LIST, this.listNameField, this.listNameField::setWidth)
        );
    }

    @Override
    protected @Nullable Component subtitle() {
        var client = WaypointClientMod.getInstance();
        var profile = client.localProfiles().active();
        Component result = Component.translatable("waypoint.form.destination.tooltip");
        if (profile != null) result = result.copy().append(" · ").append(Component.translatable("waypoint.profile.current", profile.name()));
        for (var source : ClientWaypointSource.values()) {
            var state = client.collections().state(source);
            var captured = destinations.get(source);
            if (captured != null && access.allowed(captured, ClientWaypointAccess.Action.ADD)) continue;
            String reason = state == _959.server_waypoint.common.client.ClientWaypointCollections.State.READY ? "denied"
                    : state.name().toLowerCase(java.util.Locale.ROOT);
            result = result.copy().append(" · ").append(sourceLabel(source)).append(": ")
                    .append(Component.translatable("waypoint.collection.state." + reason));
        }
        return result;
    }

    private static Component sourceLabel(ClientWaypointSource source) {
        return Component.translatable("waypoint.collection." + source.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void chooseDestination(ClientWaypointSource source) {
        selectDestination(source);
        onFormEdited();
    }

    /** Explicit source choice uses only the destinations captured when this form opened. */
    boolean selectDestination(ClientWaypointSource source) {
        var destination = destinations.get(source);
        if (destination == null || destination.generation() != selection.generation()
                || !access.allowed(destination, ClientWaypointAccess.Action.ADD)) return false;
        retargetDestination(destination);
        dimensionField.setValues(access.dimensions(selection));
        onDimensionEdited();
        return true;
    }

    @Override
    protected boolean destinationAvailable() {
        return access.allowed(selection, ClientWaypointAccess.Action.ADD);
    }

    @Override
    protected boolean hasDisplayNameRow() {
        return this.saved != null;
    }

    @Override
    protected List<TranslucentButton> footerButtons() {
        return List.of(this.cancelButton, this.addButton);
    }

    @Override
    protected TranslucentButton primaryButton() {
        return this.addButton;
    }

    /** The first empty field the player must fill: the list when it's empty, otherwise the name. */
    @Override
    protected @Nullable GuiEventListener initialFocus() {
        return this.listNameField.getValue().isEmpty() ? this.listNameField : this.nameEditBox;
    }

    @Override
    protected WaypointFormCheck.Input checkInput() {
        return new WaypointFormCheck.Input(
                true,
                this.dimensionField.getResolvedValue(),
                this.listNameField.getValue(),
                this.nameEditBox.getValue(),
                this.displayNameEditBox.getValue(),
                this.keywordsEditBox.getValue(),
                this.descriptionEditBox.getValue(),
                null
        );
    }

    private ClientWaypointSelection destination() {
        return this.selection.at(this.dimensionField.getResolvedValue(), this.listNameField.getValue(), null);
    }

    @Override
    protected void submit() {
        var result = this.copySource == null ? this.access.add(this.destination(), this.toWaypoint())
                : this.access.addCopy(new ClientWaypointAccess.CopyDraft(this.copySource, this.destination(), this.toWaypoint()));
        if (result.status() == ClientWaypointAccess.Status.SAVED) { this.onClose(); return; }
        if (result.status() != ClientWaypointAccess.Status.AWAITING_SERVER) { this.showOutcome(result); return; }
        this.pendingAdd.begin(this.dimensionField.getResolvedValue(), this.listNameField.getValue(), this.nameEditBox.getValue(), System.nanoTime());
    }

    private SimpleWaypoint toWaypoint() {
        return new SimpleWaypoint(
                this.nameEditBox.getValue(),
                WaypointFormPatch.draftDisplayName(this.saved, this.displayNameEditBox.getValue(), this.nameEditBox.getValue()),
                this.initialsEditBox.getValue(),
                this.resolveCoordinateFields(),
                this.colorPickerButton.getColor() & 0xFFFFFF,
                this.yawEditBox.getIntValue(),
                this.globalToggle.getState(),
                parseKeywords(this.keywordsEditBox.getValue()),
                this.descriptionEditBox.getValue(),
                this.iconPicker.getSelectedIcon()
        );
    }

    @Override
    protected @Nullable Component pendingMessage() {
        return this.pendingAdd.pending() ? Component.translatable("waypoint.form.status.adding") : null;
    }

    @Override
    protected void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed) {
        this.cancelButton.active = !modal;
        this.addButton.active = canSubmit;
    }

    /** Closes once the waypoint shows up in the synced data, and unlocks with a message after 5 seconds. */
    @Override
    protected void onTick() {
        if (!this.pendingAdd.pending()) {
            return;
        }
        if (!this.access.valid(this.selection)) {
            this.pendingAdd.clear();
            this.showOutcome(ClientWaypointAccess.Outcome.of(ClientWaypointAccess.Status.OBSOLETE));
        } else if (hasWaypoint(this.pendingAdd.dimension(), this.pendingAdd.list(), this.pendingAdd.name())) {
            this.pendingAdd.clear();
            this.onClose();
        } else if (this.pendingAdd.expire(System.nanoTime())) {
            this.showResult(Component.translatable("waypoint.form.status.add_timeout"), WaypointFormCheck.Field.NONE);
        }
    }

    /**
     * The Collection row's dropdown: one choice for each collection that accepted Add when the form opened.
     * The trigger shows the form's current collection, which the popup leaves out.
     */
    private static final class DestinationDropdown extends TextChoiceDropdown {
        private final List<ClientWaypointSource> choices;
        private final Supplier<ClientWaypointSource> selected;

        private DestinationDropdown(Font font, List<ClientWaypointSource> choices, Supplier<ClientWaypointSource> selected,
                Consumer<ClientWaypointSource> onChosen) {
            super(widestTriggerWidth(font), Component.translatable("waypoint.form.destination"), font);
            this.choices = choices;
            this.selected = selected;
            for (ClientWaypointSource source : choices) {
                this.addMenuItem(new TextChoice(sourceLabel(source), () -> onChosen.accept(source)));
            }
        }

        /** As wide as the widest collection name, whichever is shown; the form stretches it to its column. */
        private static int widestTriggerWidth(Font font) {
            int widest = 0;
            for (ClientWaypointSource source : ClientWaypointSource.values()) {
                widest = Math.max(widest, font.width(sourceLabel(source)));
            }
            return triggerWidth(widest);
        }

        @Override
        protected Component triggerLabel() {
            return sourceLabel(this.selected.get());
        }

        @Override
        protected int getSelectedMenuItemIndex() {
            return this.choices.indexOf(this.selected.get());
        }
    }
}
