package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.util.ColorHelper;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;

/**
 * Edits a waypoint with one atomic request that carries only the fields that changed, and keeps the
 * entered values until a matching server result accepts the edit.
 */
public class WaypointEditScreen extends AbstractWaypointPropertiesScreen {
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();

    private final String listDisplayName;
    private final int expectedListRevision;
    private final TranslucentButton saveButton;
    private final TranslucentButton resetButton;
    private final EditResponseDeadline responseDeadline = new EditResponseDeadline();

    public WaypointEditScreen(
            Screen previousScreen,
            String dimensionName,
            String listName,
            SimpleWaypoint waypoint
    ) {
        this(previousScreen, dimensionName, listName, listName, waypoint);
    }

    public WaypointEditScreen(
            Screen previousScreen,
            String dimensionName,
            String listName,
            String listDisplayName,
            SimpleWaypoint waypoint
    ) {
        super(
                previousScreen,
                Component.translatable("waypoint.edit.screen.title",
                        parseFormattedText(WaypointFormPatch.Saved.of(waypoint).titleName())),
                dimensionName,
                listName,
                waypoint
        );
        this.listDisplayName = listDisplayName;
        WaypointFileManager fileManager = WaypointClientMod.getInstance()
                .getWaypointFileManager(dimensionName);
        WaypointList waypointList = fileManager == null
                ? null
                : fileManager.getWaypointListByName(listName);
        this.expectedListRevision = waypointList == null ? 0 : waypointList.getSyncNum();
        this.saveButton = TranslucentButton.fitted(Component.translatable("waypoint.save.button"), this::submitForm);
        this.resetButton = TranslucentButton.fitted(Component.translatable("waypoint.reset.button"), this::resetProperties);
        this.nameEditBox.setSuggestionsProvider(
                () -> WaypointClientMod.getAllWaypointNames(this.dimensionName, this.listName)
        );
    }

    public static void handleResult(WaypointEditResultMessage result) {
        //? if >=26.2 {
        /*Screen screen = Minecraft.getInstance().gui.screen();
        *///?} else {
        Screen screen = Minecraft.getInstance().screen;
        //?}
        if (screen instanceof WaypointEditScreen editScreen) {
            editScreen.acceptResult(result);
        }
    }

    @Override
    protected List<LeadingRow> leadingRows() {
        return List.of();
    }

    /** "In <list> · <dimension>", with the dimension in the color the details panel uses. */
    @Override
    protected @Nullable Component subtitle() {
        int dimensionColor = ColorHelper.scaleRgb(
                0xFF000000 | DimensionStyle.colorOf(this.dimensionName).value(),
                0.8F
        ) & 0xFFFFFF;
        return Component.translatable(
                "waypoint.edit.screen.location",
                parseFormattedText(this.listDisplayName),
                Component.literal(this.dimensionName).withStyle(style -> style.withColor(dimensionColor))
        );
    }

    @Override
    protected boolean hasDisplayNameRow() {
        return true;
    }

    @Override
    protected List<TranslucentButton> footerButtons() {
        return List.of(this.resetButton, this.cancelButton, this.saveButton);
    }

    @Override
    protected TranslucentButton primaryButton() {
        return this.saveButton;
    }

    @Override
    protected WaypointFormCheck.Input checkInput() {
        return new WaypointFormCheck.Input(
                false,
                this.dimensionName,
                this.listName,
                this.nameEditBox.getValue(),
                this.displayNameEditBox.getValue(),
                this.keywordsEditBox.getValue(),
                this.descriptionEditBox.getValue(),
                this.savedWaypoint().name()
        );
    }

    /** Whether the form would change the waypoint: its patch has a field to set or clear. */
    @Override
    protected boolean hasChanges() {
        return WaypointFormPatch.changesAnything(this.buildPatch());
    }

    private WaypointFormPatch.Saved savedWaypoint() {
        return Objects.requireNonNull(this.saved);
    }

    private WaypointPatch buildPatch() {
        return WaypointFormPatch.build(this.savedWaypoint(), new WaypointFormPatch.Values(
                this.nameEditBox.getValue(),
                this.displayNameEditBox.getValue(),
                this.initialsEditBox.getValue(),
                this.resolveCoordinateFields(),
                this.colorPickerButton.getColor() & 0xFFFFFF,
                this.yawEditBox.getIntValue(),
                this.globalToggle.getState(),
                parseKeywords(this.keywordsEditBox.getValue()),
                this.descriptionEditBox.getValue(),
                this.iconPicker.getSelectedIcon()
        ));
    }

    @Override
    protected void submit() {
        WaypointPatch patch = this.buildPatch();
        long requestId = NEXT_REQUEST_ID.incrementAndGet();
        this.responseDeadline.begin(requestId, System.nanoTime());
        boolean sent = WaypointClientMod.getInstance().sendChunkedMessageToServer(new WaypointEditRequestMessage(
                requestId,
                this.dimensionName,
                this.listName,
                this.savedWaypoint().name(),
                this.expectedListRevision,
                patch
        ));
        if (!sent) {
            this.responseDeadline.clear();
            this.showResult(Component.translatable("waypoint.form.status.send_failed"), WaypointFormCheck.Field.NONE);
        }
    }

    @Override
    protected @Nullable Component pendingMessage() {
        return this.responseDeadline.pending() ? Component.translatable("waypoint.form.status.saving") : null;
    }

    /** Lets the integrated server tick and deliver its queued reply while saving. */
    @Override
    public boolean isPauseScreen() {
        return !this.responseDeadline.pending();
    }

    @Override
    protected void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed) {
        this.cancelButton.active = !modal;
        this.saveButton.active = canSubmit;
        this.resetButton.active = !locked && changed;
    }

    @Override
    protected void onTick() {
        if (this.responseDeadline.expire(System.nanoTime())) {
            this.showResult(Component.translatable("waypoint.edit.error.response_timeout"), WaypointFormCheck.Field.NONE);
        }
    }

    private void acceptResult(WaypointEditResultMessage result) {
        if (!this.responseDeadline.clearIfMatches(result.requestId())) {
            return;
        }
        if (result.status() == EditResultStatus.SUCCESS) {
            this.onClose();
            return;
        }
        Component error = Component.translatable(
                "waypoint.edit.error." + result.status().name().toLowerCase(Locale.ROOT)
        );
        this.showResult(error, fieldOf(result.status()));
    }

    /** The field a rejected edit points at. */
    private static WaypointFormCheck.Field fieldOf(EditResultStatus status) {
        return switch (status) {
            case IDENTIFIER_COLLISION -> WaypointFormCheck.Field.NAME;
            case INVALID_DISPLAY_TEXT -> WaypointFormCheck.Field.DISPLAY_NAME;
            case DUPLICATE_KEYWORD -> WaypointFormCheck.Field.KEYWORDS;
            default -> WaypointFormCheck.Field.NONE;
        };
    }

    /** Puts every field back to the saved waypoint and clears the status and the field highlights. */
    private void resetProperties() {
        WaypointFormPatch.Saved values = this.savedWaypoint();
        this.nameEditBox.setValue(values.name());
        this.displayNameEditBox.setValue(values.displayNameOverride() == null ? "" : values.displayNameOverride());
        this.initialsEditBox.setValue(values.initials());
        int color = 0xFF000000 | values.rgb();
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.xEditBox.setValue(Integer.toString(values.position().x()));
        this.yEditBox.setValue(Integer.toString(values.position().y()));
        this.zEditBox.setValue(Integer.toString(values.position().z()));
        this.yawEditBox.setValue(Integer.toString(values.yaw()));
        this.globalToggle.setState(values.global());
        this.keywordsEditBox.setValue(String.join(", ", values.keywords()));
        this.descriptionEditBox.setValue(values.description());
        this.iconPicker.setSelectedIcon(values.icon());
        this.onFormEdited();
    }
}
