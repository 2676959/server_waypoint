package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.getAvailableDimensionNames;
import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.mergeDimensionNames;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_NAME_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;
import static _959.server_waypoint.util.StringCommandBuilder.addCmd;

/**
 * Adds a waypoint with {@code /wp add}. There is no reply to that command, so after sending it the
 * form locks and waits for the waypoint to show up in the synced data, then closes.
 */
public class WaypointAddScreen extends AbstractWaypointPropertiesScreen {
    private final ComboBoxWidget dimensionField;
    private final ComboBoxWidget listNameField;
    private final TranslucentButton addButton;
    private final PendingAdd pendingAdd = new PendingAdd();

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName) {
        this(previousScreen, dimensionName, listName, null);
    }

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName, WaypointPos defaultPos) {
        super(previousScreen, Component.translatable("waypoint.add.screen.title"), dimensionName, listName, null);
        List<String> dimensions = mergeDimensionNames(
                WaypointClientMod.getAllAvailableDimensionNames(),
                List.of(dimensionName)
        );
        this.dimensionField = new ComboBoxWidget(0, 0, 155, Component.translatable("waypoint.form.dimension"), this.font,
                dimensions, dimensionName, value -> this.onDimensionEdited());
        this.dimensionField.setRenderPopupSeparately(true);
        this.dimensionField.useResourceIdMatching();
        this.listNameField = new ComboBoxWidget(0, 0, 90, Component.translatable("waypoint.form.list"), this.font,
                WaypointClientMod.getAllWaypointListNames(dimensionName), listName, value -> this.onFormEdited());
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
        getAvailableDimensionNames().thenAccept(dimensions -> {
            this.dimensionField.setValues(mergeDimensionNames(dimensions, List.of(this.dimensionName)));
            this.onDimensionEdited();
        });
    }

    /** The list choices belong to the chosen dimension, so they follow it; the list name already typed stays. */
    private void onDimensionEdited() {
        this.listNameField.setValues(WaypointClientMod.getAllWaypointListNames(this.dimensionField.getResolvedValue()));
        this.onFormEdited();
    }

    private void configureSuggestions() {
        this.nameEditBox.setSuggestionsProvider(() -> WaypointClientMod.getAllWaypointNames(this.dimensionField.getResolvedValue(), this.listNameField.getValue()));
    }

    @Override
    protected List<LeadingRow> leadingRows() {
        return List.of(
                new LeadingRow(FormField.DIMENSION, this.dimensionField, this.dimensionField::setWidth),
                new LeadingRow(FormField.LIST, this.listNameField, this.listNameField::setWidth)
        );
    }

    @Override
    protected @Nullable Component subtitle() {
        return null;
    }

    @Override
    protected boolean hasDisplayNameRow() {
        return false;
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
                "",
                this.keywordsEditBox.getValue(),
                this.descriptionEditBox.getValue(),
                null
        );
    }

    @Override
    protected void submit() {
        String dimension = this.dimensionField.getResolvedValue();
        String list = this.listNameField.getValue();
        String name = this.nameEditBox.getValue();
        if (!sendCommand(addCmd(dimension, list, this.toWaypoint(), false))) {
            this.showResult(Component.translatable("waypoint.form.status.send_failed"), WaypointFormCheck.Field.NONE);
            return;
        }
        this.pendingAdd.begin(dimension, list, name, System.nanoTime());
    }

    private SimpleWaypoint toWaypoint() {
        return new SimpleWaypoint(
                this.nameEditBox.getValue(),
                this.nameEditBox.getValue(),
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
        if (hasWaypoint(this.pendingAdd.dimension(), this.pendingAdd.list(), this.pendingAdd.name())) {
            this.pendingAdd.clear();
            this.onClose();
        } else if (this.pendingAdd.expire(System.nanoTime())) {
            this.showResult(Component.translatable("waypoint.form.status.add_timeout"), WaypointFormCheck.Field.NONE);
        }
    }
}
