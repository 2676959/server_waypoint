package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.util.ColorHelper;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.WaypointInitials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;

public class WaypointEditScreen extends AbstractWaypointPropertiesScreen {
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();

    private final String listDisplayName;
    private final int expectedListRevision;
    private TranslucentButton updateButton;
    private TranslucentButton resetButton;
    private TranslucentButton clearDisplayNameButton;
    private boolean clearDisplayName;
    private final EditResponseDeadline responseDeadline = new EditResponseDeadline();

    @Override
    protected @NotNull WidgetStack createTitleRow() {
        ScalableText titleLabel = new ScalableText(
                0,
                0,
                this.getTitle(),
                WidgetThemeVariable.TEXT_PRIMARY,
                font
        );
        WidgetStack infoRow = new WidgetStack(0, 0, 5);
        ScalableText dimensionLabel = new ScalableText(
                0,
                0,
                Component.translatable("waypoint.dimension.info", ""),
                0.8F,
                WidgetThemeVariable.TEXT_MUTED,
                font
        );
        int dimensionColor = ColorHelper.scaleRgb(
                0xFF000000 | getDimensionColor(this.dimensionName).value(),
                0.8F
        );
        ScalableText dimensionNameLabel = new ScalableText(
                0,
                0,
                Component.nullToEmpty(this.dimensionName),
                0.8F,
                dimensionColor,
                font
        );
        ScalableText listNameLabel = new ScalableText(
                0,
                0,
                Component.translatable(
                        "waypoint.list_name.info",
                        parseFormattedText(this.listDisplayName)
                ),
                0.8F,
                WidgetThemeVariable.TEXT_MUTED,
                font
        );
        infoRow.addChild(dimensionLabel, 0);
        infoRow.addChild(dimensionNameLabel, 0);
        infoRow.addChild(listNameLabel);
        WidgetStack titleRow = new WidgetStack(0, 0, 2, true, false);
        titleRow.addChild(titleLabel, 0);
        titleRow.addChild(infoRow);
        return titleRow;
    }

    @Override
    protected @NotNull WidgetStack createButtonRow() {
        WidgetStack buttonRow = new WidgetStack(0, 0, 6, false);
        this.updateButton = new TranslucentButton(
                0,
                0,
                50,
                11,
                Component.translatable("waypoint.update.button"),
                this::sendEditRequest
        );
        this.resetButton = new TranslucentButton(
                0,
                0,
                50,
                11,
                Component.translatable("waypoint.reset.button"),
                this::resetProperties
        );
        this.clearDisplayNameButton = new TranslucentButton(
                0,
                0,
                72,
                11,
                Component.translatable("waypoint.display_name.clear.button"),
                this::toggleDisplayNameClear
        );
        buttonRow.addChild(this.cancelButton, 2);
        buttonRow.addChild(this.resetButton);
        buttonRow.addChild(this.clearDisplayNameButton);
        buttonRow.addChild(this.updateButton);
        return buttonRow;
    }

    @Override
    protected List<AbstractWidget> getTitleRowClickableWidgets() {
        return List.of(this.displayNameEditBox);
    }

    @Override
    protected List<AbstractWidget> getButtonRowClickableWidgets() {
        return List.of(
                this.updateButton,
                this.resetButton,
                this.clearDisplayNameButton,
                this.cancelButton
        );
    }

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
                Component.translatable(
                        "waypoint.edit.screen.title",
                        parseFormattedText(waypoint.displayName())
                ),
                dimensionName,
                listName,
                waypoint,
                true
        );
        this.listDisplayName = listDisplayName;
        WaypointFileManager fileManager = WaypointClientMod.getInstance()
                .getWaypointFileManager(dimensionName);
        WaypointList waypointList = fileManager == null
                ? null
                : fileManager.getWaypointListByName(listName);
        this.expectedListRevision = waypointList == null ? 0 : waypointList.getSyncNum();
        this.configureSuggestions();
        this.buttonRow.setXOffset(CONTENT_WIDTH);
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

    private void sendEditRequest() {
        if (this.responseDeadline.pending()) {
            return;
        }
        WaypointPos resolvedPos = this.resolveCoordinateFields();
        WaypointPatch patch = new WaypointPatch(
                changed(this.waypointName, this.nameEditBox.getValue()),
                this.displayNamePatch(),
                changed(this.initials, this.initialsEditBox.getValue()),
                changed(new WaypointPos(this.x, this.y, this.z), resolvedPos),
                changed(this.rgb & 0xFFFFFF, this.colorPickerButton.getColor() & 0xFFFFFF),
                changed(this.yaw, this.yawEditBox.getIntValue()),
                changed(this.global, this.globalToggle.getState()),
                PatchField.unchanged(),
                PatchField.unchanged()
        );
        long requestId = NEXT_REQUEST_ID.incrementAndGet();
        this.responseDeadline.begin(requestId, System.nanoTime());
        this.updateButton.active = false;
        this.clearFieldErrors();
        boolean sent = WaypointClientMod.getInstance().sendChunkedMessageToServer(new WaypointEditRequestMessage(
                requestId,
                this.dimensionName,
                this.listName,
                this.waypointName,
                this.expectedListRevision,
                patch
        ));
        if (!sent) {
            this.responseDeadline.clear();
            this.updateButton.active = true;
        }
    }

    private PatchField<String> displayNamePatch() {
        if (this.clearDisplayName) {
            return PatchField.clear();
        }
        if (!this.waypointDisplayName.equals(this.displayNameEditBox.getValue())) {
            return PatchField.set(this.displayNameEditBox.getValue());
        }
        return PatchField.unchanged();
    }

    private void acceptResult(WaypointEditResultMessage result) {
        if (!this.responseDeadline.clearIfMatches(result.requestId())) {
            return;
        }
        this.updateButton.active = true;
        if (result.status() == EditResultStatus.SUCCESS) {
            this.onClose();
            return;
        }
        Component error = Component.translatable(
                "waypoint.edit.error." + result.status().name().toLowerCase(java.util.Locale.ROOT)
        );
        if (result.status() == EditResultStatus.IDENTIFIER_COLLISION) {
            this.nameEditBox.setTooltip(Tooltip.create(error));
        } else if (result.status() == EditResultStatus.INVALID_DISPLAY_TEXT) {
            this.displayNameEditBox.setTooltip(Tooltip.create(error));
        } else {
            this.updateButton.setTooltip(Tooltip.create(error));
        }
    }

    public void resetProperties() {
        this.nameEditBox.setValue(this.waypointName);
        this.displayNameEditBox.setValue(this.waypointDisplayName);
        this.clearDisplayName = false;
        this.syncDisplayNameClearState();
        this.initialsEditBox.setValue(this.initials);
        int color = 0xFF000000 | this.rgb;
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.swatchWidget.visible = false;
        this.xEditBox.setValue(Integer.toString(this.x));
        this.yEditBox.setValue(Integer.toString(this.y));
        this.zEditBox.setValue(Integer.toString(this.z));
        this.yawEditBox.setValue(Integer.toString(this.yaw));
        this.globalToggle.setState(this.global);
        this.clearFieldErrors();
    }

    private void toggleDisplayNameClear() {
        this.clearDisplayName = !this.clearDisplayName;
        this.syncDisplayNameClearState();
    }

    private void syncDisplayNameClearState() {
        this.displayNameEditBox.active = !this.clearDisplayName;
        this.clearDisplayNameButton.setMessage(Component.translatable(
                this.clearDisplayName
                        ? "waypoint.display_name.keep.button"
                        : "waypoint.display_name.clear.button"
        ));
    }

    @Override
    protected void onSwatchClosed() {
        this.syncDisplayNameClearState();
        if (this.responseDeadline.pending()) {
            this.updateButton.active = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.responseDeadline.expire(System.nanoTime())) {
            return;
        }
        this.updateButton.active = true;
        this.updateButton.setTooltip(Tooltip.create(
                Component.translatable("waypoint.edit.error.response_timeout")
        ));
    }

    private void clearFieldErrors() {
        this.nameEditBox.setTooltip(null);
        this.displayNameEditBox.setTooltip(null);
        this.updateButton.setTooltip(null);
    }

    private void configureSuggestions() {
        this.nameEditBox.setSuggestionsProvider(
                () -> WaypointClientMod.getAllWaypointNames(this.dimensionName, this.listName)
        );
        this.initialsEditBox.setSuggestionsProvider(this::getWaypointInitialsSuggestions);
    }

    private List<String> getWaypointInitialsSuggestions() {
        return WaypointInitials.getInitialsCandidatesFromName(plainText(this.nameEditBox.getValue()));
    }

    private static <T> PatchField<T> changed(T original, T updated) {
        return java.util.Objects.equals(original, updated)
                ? PatchField.unchanged()
                : PatchField.set(updated);
    }
}
