//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ConfirmationDialog;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.local.LocalTeleportCommand;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles.ActiveReason;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles.Profile;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * Every Local profile of the current server or world: use, create, rename or delete any of them,
 * and edit the active profile's teleport command. The screen is bound to the Local session it
 * opened in, and turns its actions off once that session changes.
 */
public final class LocalWaypointProfilesScreen extends MovementAllowedScreen {
    private static final float TITLE_SCALE = 1.2F;
    private static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 3;
    private static final int MIN_COMMAND_WIDTH = 240;
    private static final int STATUS_GAP = 10;
    private static final int DIALOG_TEXT_WIDTH = 280;
    private static final int DIALOG_LINE_GAP = 8;

    enum Dialog { CREATE, RENAME, DELETE, DISCARD }

    /** A profile's controls, created once and kept while the profile exists, so focus and hover survive updates. */
    record ProfileRow(TranslucentButton use, IconButton rename, IconButton delete, WidgetStack actions) {
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws IOException;
    }

    private record DialogLine(Component text, WidgetThemeVariable color) {
    }

    private final Runnable close;
    private final WaypointClientMod client;
    private final LocalWaypointProfiles profiles;
    private final String place;
    private final Map<UUID, ProfileRow> rows = new HashMap<>();
    private final ScalableText heading;
    private final ScalableText inUse;
    private final ScalableText status;
    private final SettingsListWidget settings;
    private final TranslucentButton newProfileButton;
    private final TranslucentTextField command;
    private final TranslucentButton saveButton;
    private final IconButton resetButton;
    private final TranslucentButton doneButton;
    private final TranslucentTextField name;
    private Component activeStatus = Component.empty();
    private Component commandTitle = Component.empty();
    private ActiveReason reason = ActiveReason.FALLBACK;
    private Component statusMessage = Component.empty();
    private boolean statusError;
    private boolean showingProblem;
    private @Nullable Dialog dialog;
    private @Nullable ConfirmationDialog dialogWidget;
    private List<Component> dialogLines = List.of();
    private @Nullable UUID dialogProfile;
    private @Nullable AbstractWidget dialogOpener;
    private @Nullable UUID discardSwitchTo;
    private @Nullable Profile nameConflict;
    private @Nullable GuiEventListener pendingFocus;
    private @Nullable Runnable pendingFocusAction;
    private @Nullable ProfileLayout layout;
    private int footerHeight;
    private long generation;
    private boolean initialized;

    public LocalWaypointProfilesScreen(Screen parent, WaypointClientMod client) {
        this(client, () -> MinecraftClientHelper.setScreen(parent));
    }

    LocalWaypointProfilesScreen(WaypointClientMod client, Runnable close) {
        super(Component.translatable("waypoint.profile.manage"));
        this.client = client;
        this.close = close;
        this.profiles = client.localProfiles();
        this.generation = client.collections().generation();
        this.place = Objects.requireNonNullElse(this.profiles.place(), "");
        this.heading = new ScalableText(0, 0, this.title, TITLE_SCALE, TEXT_PRIMARY, this.font);
        this.inUse = new ScalableText(0, 0, Component.empty(), SettingsListWidget.ROW_TEXT_SCALE, SUCCESS, this.font);
        this.status = new ScalableText(0, 0, Component.empty(), TEXT_MUTED, this.font);
        this.settings = new SettingsListWidget(this.font);
        this.newProfileButton = rowButton(Component.translatable("waypoint.profile.new"), this::openCreateDialog);
        this.command = new TranslucentTextField(0, 0, MIN_COMMAND_WIDTH, Component.translatable("waypoint.profile.command"), this.font);
        this.command.setMaxLength(32767);
        this.command.setTextFormatter((shown, offset) -> TeleportCommandStyle.format(this.command.getValue(), shown, offset));
        this.saveButton = rowButton(Component.translatable("waypoint.profile.save_command"), this::saveCommand);
        Component resetLabel = Component.translatable("waypoint.profile.reset_command", LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND);
        this.resetButton = new IconButton(0, 0, ICON_SIZE, ICON_SIZE, resetLabel, WidgetTextures.RESET_ICON, this::resetCommand)
                .withoutBackground()
                .withIconPadding(2)
                .withIconRegion(9, 9, 46, 43, 64, 64);
        this.resetButton.setTooltip(resetLabel);
        this.doneButton = TranslucentButton.fitted(CommonComponents.GUI_DONE, this::onClose);
        this.name = new TranslucentTextField(0, 0, DIALOG_TEXT_WIDTH, Component.translatable("waypoint.profile.name"), this.font);
        this.name.setMaxLength(ProfileNames.MAX_LENGTH);
        Profile active = this.profiles.active();
        if (active != null) {
            this.command.setValue(active.teleportCommand());
        }
        this.command.setResponder(ignored -> this.onCommandEdited());
        this.name.setResponder(ignored -> this.onNameEdited());
        this.refresh();
        if (active == null) {
            this.showObsolete();
        } else {
            this.onCommandEdited();
        }
    }

    ProfileRow row(UUID id) {
        return Objects.requireNonNull(this.rows.get(id), "No row for this profile");
    }

    TranslucentButton newProfileButton() {
        return this.newProfileButton;
    }

    TranslucentTextField commandField() {
        return this.command;
    }

    TranslucentButton saveButton() {
        return this.saveButton;
    }

    TranslucentButton doneButton() {
        return this.doneButton;
    }

    IconButton resetButton() {
        return this.resetButton;
    }

    SettingsListWidget settingsList() {
        return this.settings;
    }

    TranslucentTextField nameField() {
        return this.name;
    }

    @Nullable Dialog openDialog() {
        return this.dialog;
    }

    @Nullable ConfirmationDialog dialogWidget() {
        return this.dialogWidget;
    }

    /** The open dialog's text lines, top to bottom. */
    List<Component> dialogLines() {
        return this.dialogLines;
    }

    Component listTitle() {
        return this.place.isBlank() ? Component.translatable("waypoint.profile.list_section.plain")
                : Component.translatable("waypoint.profile.list_section", this.place);
    }

    /** The command section's header. */
    Component commandTitle() {
        return this.commandTitle;
    }

    /** What the active row shows in place of a Use button. */
    Component activeStatus() {
        return this.activeStatus;
    }

    /** The footer status, or an empty component when there is none. */
    Component statusMessage() {
        return this.statusMessage;
    }

    boolean statusIsError() {
        return this.statusError;
    }

    private boolean currentSession() {
        return this.client.collections().generation() == this.generation && this.profiles.active() != null;
    }

    private @Nullable Profile find(@Nullable UUID id) {
        for (Profile profile : this.profiles.profiles()) {
            if (profile.id().equals(id)) {
                return profile;
            }
        }
        return null;
    }

    private static TranslucentButton rowButton(Component label, Runnable action) {
        TranslucentButton button = TranslucentButton.fitted(label, action::run);
        button.setTextScale(SettingsListWidget.ROW_TEXT_SCALE);
        button.setWidth(Math.round(button.getWidth() * SettingsListWidget.ROW_TEXT_SCALE));
        button.setHeight(Math.round(button.getHeight() * SettingsListWidget.ROW_TEXT_SCALE));
        return button;
    }

    /** The profile's cached controls, with tooltips naming it as it's called now. */
    private ProfileRow row(Profile profile) {
        UUID id = profile.id();
        ProfileRow row = this.rows.computeIfAbsent(id, ignored -> {
            TranslucentButton use = rowButton(Component.translatable("waypoint.profile.use"), () -> this.use(id));
            IconButton rename = new IconButton(0, 0, ICON_SIZE, ICON_SIZE, Component.empty(), WidgetTextures.EDIT_ICON,
                    () -> this.openRenameDialog(id)).withIconPadding(1);
            IconButton delete = new IconButton(0, 0, ICON_SIZE, ICON_SIZE, Component.empty(), WidgetTextures.REMOVE_ICON,
                    () -> this.openDeleteDialog(id)).withIconPadding(1).withIconColor(DANGER);
            WidgetStack actions = new WidgetStack(0, 0, ICON_GAP, Orientation.HORIZONTAL, Direction.FORWARD);
            actions.addClickable(rename, 0);
            actions.addClickable(delete);
            return new ProfileRow(use, rename, delete, actions);
        });
        row.use().setTooltip(Component.translatable("waypoint.profile.use.tooltip", profile.name()));
        Component renameLabel = Component.translatable("waypoint.profile.rename.tooltip", profile.name());
        row.rename().setMessage(renameLabel);
        row.rename().setTooltip(renameLabel);
        Component deleteLabel = Component.translatable("waypoint.profile.delete.tooltip", profile.name());
        row.delete().setMessage(deleteLabel);
        row.delete().setTooltip(deleteLabel);
        return row;
    }

    /**
     * Rebuilds the entries from the cached controls, in storage order so rows never move when the
     * active one changes. Once {@link #init} has run, the widgets are registered again in display
     * order and the list keeps its scroll position.
     */
    private void refresh() {
        List<Profile> list = this.profiles.profiles();
        this.rows.keySet().removeIf(id -> list.stream().noneMatch(profile -> profile.id().equals(id)));
        Profile active = this.profiles.active();
        ActiveReason activeReason = this.profiles.activeReason();
        this.reason = activeReason == null ? ActiveReason.FALLBACK : activeReason;
        this.activeStatus = Component.translatable(this.reason == ActiveReason.MANUAL
                ? "waypoint.profile.in_use.manual" : "waypoint.profile.in_use.automatic");
        this.inUse.setText(this.activeStatus);
        List<SettingsListWidget.Entry> entries = new ArrayList<>();
        entries.add(new SettingsListWidget.Header(this.listTitle()));
        for (Profile profile : list) {
            ProfileRow row = this.row(profile);
            boolean isActive = active != null && active.id().equals(profile.id());
            SettingsListWidget.Row entry = isActive
                    ? new SettingsListWidget.Row(Component.literal(profile.name()), this.inUse)
                    : new SettingsListWidget.Row(Component.literal(profile.name()), row.use());
            entry.action(row.actions());
            if (isActive) {
                entry.tooltip(() -> Component.translatable("waypoint.profile.reason." + this.reason.name().toLowerCase(Locale.ROOT)));
            }
            entries.add(entry);
        }
        entries.add(new SettingsListWidget.WideRow(this.newProfileButton));
        this.commandTitle = active == null ? Component.translatable("waypoint.profile.command_section.plain")
                : Component.translatable("waypoint.profile.command_section", active.name());
        entries.add(new SettingsListWidget.Header(this.commandTitle));
        entries.add(new SettingsListWidget.WideRow(this.command));
        entries.add(new SettingsListWidget.Row(TeleportCommandStyle.legend(), this.saveButton)
                .labelColor(TEXT_MUTED)
                .action(this.resetButton, () -> !LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND.equals(this.savedCommand()))
                .tooltip(() -> Component.translatable("waypoint.profile.placeholders.tooltip")));
        double scroll = this.settings.getScrollY();
        this.settings.setEntries(entries);
        if (this.initialized) {
            this.layoutContent();
            this.settings.setScrollY(scroll);
            this.register();
        }
        this.updateControlStates();
    }

    /** Registers the widgets for input in display order; the list, the footer and the dialog draw them. */
    private void register() {
        this.clearWidgets();
        this.settings.visitWidgets(this::addRenderableWidget);
        this.addRenderableWidget(this.doneButton);
        if (this.dialogWidget != null) {
            this.dialogWidget.visitWidgets(this::addRenderableWidget);
            // The dialog visits only its buttons.
            if (this.dialog == Dialog.CREATE || this.dialog == Dialog.RENAME) {
                this.addRenderableWidget(this.name);
            }
        }
    }

    /**
     * Runs a storage operation for this screen's session. On success the screen takes the new
     * generation and rebuilds its rows; on failure it says why and leaves any dialog and its text,
     * following storage only where the operation changed it before failing.
     */
    private boolean perform(Operation operation) {
        if (!this.currentSession()) {
            this.showObsolete();
            return false;
        }
        List<Profile> before = this.profiles.profiles();
        try {
            operation.run();
        } catch (IOException exception) {
            WaypointClientMod.LOGGER.error("Local profile operation failed", exception);
            // A delete whose files can't be removed has already left the index.
            if (!before.equals(this.profiles.profiles())) {
                this.followStorage();
            }
            this.showStatus(Component.translatable("waypoint.local.storage_failed"), true);
            return false;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            this.showObsolete();
            return false;
        }
        this.followStorage();
        return true;
    }

    /** Takes the generation this screen's own change produced and shows storage as it is now. */
    private void followStorage() {
        this.generation = this.client.collections().generation();
        this.client.refreshCurrentCollections();
        this.refresh();
    }

    /** Switches profiles, asking first when the command has an unsaved edit. */
    private void use(UUID id) {
        if (this.currentSession() && this.commandEdited()) {
            this.openDiscardDialog(this.row(id).use(), id);
        } else {
            this.switchTo(id);
        }
    }

    /** Switches profiles; on success it closes a Discard dialog and replaces the edit with the new profile's command. */
    private void switchTo(UUID id) {
        if (this.perform(() -> this.profiles.select(id))) {
            if (this.dialog != null) {
                this.endDialog();
            }
            this.loadCommand();
            this.showResult(Component.translatable("waypoint.profile.status.switched", this.profiles.active().name()));
            this.requestFocus(this.row(id).rename());
        }
    }

    private void saveCommand() {
        String template = this.command.getValue();
        if (LocalTeleportCommand.problem(template).isPresent()) {
            return;
        }
        if (this.perform(() -> this.profiles.setTeleportCommand(template))) {
            this.showResult(Component.translatable("waypoint.profile.status.command_saved"));
        }
    }

    /** An explicit replacement, so it doesn't ask about an unsaved edit. */
    private void resetCommand() {
        if (this.perform(() -> this.profiles.setTeleportCommand(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND))) {
            this.command.setValue(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND);
            this.showResult(Component.translatable("waypoint.profile.status.command_reset"));
            // The reset icon hides itself, so focus goes to what it reset.
            this.requestFocus(this.command);
        }
    }

    /** Marks a template problem in the field and the footer, and clears that status once it's fixed. */
    private void onCommandEdited() {
        Optional<LocalTeleportCommand.Problem> problem = LocalTeleportCommand.problem(this.command.getValue());
        this.command.setInvalid(problem.isPresent());
        if (problem.isPresent() && this.currentSession()) {
            LocalTeleportCommand.Problem found = problem.get();
            String key = "waypoint.profile.problem." + found.kind().name().toLowerCase(Locale.ROOT);
            Component message = found.kind() == LocalTeleportCommand.ProblemKind.UNKNOWN
                    ? Component.translatable(key, found.text()) : Component.translatable(key);
            if (!this.showingProblem || !message.equals(this.statusMessage)) {
                this.showStatus(message, true);
            }
            this.showingProblem = true;
        } else if (this.showingProblem) {
            this.showStatus(Component.empty(), false);
        }
        this.updateControlStates();
    }

    private String savedCommand() {
        Profile active = this.profiles.active();
        return active == null ? LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND : active.teleportCommand();
    }

    private void loadCommand() {
        Profile active = this.profiles.active();
        if (active != null) {
            this.command.setValue(active.teleportCommand());
        }
    }

    private boolean commandEdited() {
        Profile active = this.profiles.active();
        return active != null && !this.command.getValue().equals(active.teleportCommand());
    }

    private void openCreateDialog() {
        if (!this.currentSession()) {
            return;
        }
        this.name.setValue("");
        this.openDialog(Dialog.CREATE, null, this.newProfileButton);
    }

    private void openRenameDialog(UUID id) {
        Profile profile = this.find(id);
        if (!this.currentSession() || profile == null) {
            return;
        }
        this.name.setValue(profile.name());
        this.openDialog(Dialog.RENAME, id, this.row(id).rename());
    }

    private void openDeleteDialog(UUID id) {
        if (!this.currentSession() || this.find(id) == null) {
            return;
        }
        this.openDialog(Dialog.DELETE, id, this.row(id).delete());
    }

    /** Asks before an unsaved edit is dropped; {@code switchTo} is the profile Use was for, or {@code null} to close. */
    private void openDiscardDialog(AbstractWidget opener, @Nullable UUID switchTo) {
        this.discardSwitchTo = switchTo;
        this.openDialog(Dialog.DISCARD, null, opener);
    }

    private void openDialog(Dialog kind, @Nullable UUID profile, AbstractWidget opener) {
        this.dialog = kind;
        this.dialogProfile = profile;
        this.dialogOpener = opener;
        this.buildDialog();
        if (kind == Dialog.CREATE || kind == Dialog.RENAME) {
            this.requestFocus(this.name, kind == Dialog.RENAME ? this::selectWholeName : null);
        } else {
            this.requestFocus(this.dialogWidget.getCancelButton());
        }
    }

    private void selectWholeName() {
        this.name.setCursorPosition(this.name.getValue().length());
        this.name.setHighlightPos(0);
    }

    /** Closes the open dialog without an answer and returns focus to the control that opened it. */
    private void closeDialog() {
        AbstractWidget opener = this.dialogOpener;
        this.endDialog();
        this.requestFocus(opener != null && opener.active && this.shows(opener) ? opener : this.doneButton);
    }

    private void endDialog() {
        this.dialog = null;
        this.dialogWidget = null;
        this.dialogLines = List.of();
        this.dialogProfile = null;
        this.dialogOpener = null;
        this.discardSwitchTo = null;
        this.nameConflict = null;
        this.name.setInvalid(false);
        if (this.initialized) {
            this.register();
        }
        this.updateControlStates();
    }

    private boolean shows(AbstractWidget widget) {
        List<AbstractWidget> widgets = new ArrayList<>();
        this.settings.visitWidgets(widgets::add);
        return widgets.contains(widget);
    }

    /** Another profile already named as typed, for the New and Rename dialogs. */
    private @Nullable Profile currentConflict() {
        if (this.dialog != Dialog.CREATE && this.dialog != Dialog.RENAME) {
            return null;
        }
        return ProfileNames.conflict(this.profiles.profiles(), this.name.getValue(),
                this.dialog == Dialog.RENAME ? this.dialogProfile : null);
    }

    private void onNameEdited() {
        if (this.dialog != Dialog.CREATE && this.dialog != Dialog.RENAME) {
            return;
        }
        if (!Objects.equals(this.currentConflict(), this.nameConflict)) {
            this.buildDialog();
        } else {
            this.updateControlStates();
        }
    }

    /** Builds the open dialog for its current text: on opening, on a resize and when the name error changes. */
    private void buildDialog() {
        this.nameConflict = this.currentConflict();
        this.name.setInvalid(this.nameConflict != null);
        Profile profile = this.find(this.dialogProfile);
        String profileName = profile == null ? "" : profile.name();
        Component title;
        Component confirmLabel;
        Runnable confirm;
        List<DialogLine> lines = new ArrayList<>();
        switch (Objects.requireNonNull(this.dialog)) {
            case CREATE -> {
                title = Component.translatable("waypoint.profile.new");
                lines.add(new DialogLine(Component.translatable("waypoint.profile.new.body"), TEXT_MUTED));
                // Creating switches to the new profile, so it's the confirmation for dropping the edit.
                if (this.commandEdited()) {
                    lines.add(new DialogLine(Component.translatable("waypoint.profile.discard.note"), TEXT_MUTED));
                }
                confirmLabel = Component.translatable("waypoint.profile.create_confirm");
                confirm = this::confirmCreate;
            }
            case RENAME -> {
                title = Component.translatable("waypoint.profile.rename.title");
                lines.add(new DialogLine(Component.translatable("waypoint.profile.rename.body", profileName), TEXT_PRIMARY));
                confirmLabel = Component.translatable("waypoint.profile.rename_action");
                confirm = this::confirmRename;
            }
            case DELETE -> {
                title = Component.translatable("waypoint.profile.delete");
                lines.add(new DialogLine(Component.translatable("waypoint.profile.delete.body", profileName), TEXT_PRIMARY));
                Profile active = this.profiles.active();
                if (active != null && active.id().equals(this.dialogProfile)) {
                    lines.add(new DialogLine(Component.translatable("waypoint.profile.delete.active_note"), TEXT_MUTED));
                }
                lines.add(new DialogLine(Component.translatable("waypoint.profile.delete.other_collections"), TEXT_MUTED));
                confirmLabel = Component.translatable("waypoint.profile.delete_confirm_action");
                confirm = this::confirmDelete;
            }
            case DISCARD -> {
                title = Component.translatable("waypoint.profile.discard.title");
                Profile active = this.profiles.active();
                lines.add(new DialogLine(Component.translatable("waypoint.profile.discard.body",
                        active == null ? "" : active.name()), TEXT_PRIMARY));
                confirmLabel = Component.translatable("waypoint.profile.discard.confirm");
                confirm = this::confirmDiscard;
            }
            default -> throw new IllegalStateException("No dialog for " + this.dialog);
        }
        int textWidth = Math.max(80, Math.min(DIALOG_TEXT_WIDTH, this.width - 48));
        WidgetStack content = new WidgetStack(0, 0, DIALOG_LINE_GAP, true, false);
        List<Component> texts = new ArrayList<>();
        for (DialogLine line : lines) {
            this.addDialogLine(content, line, textWidth, texts);
        }
        if (this.dialog == Dialog.CREATE || this.dialog == Dialog.RENAME) {
            this.name.setWidth(textWidth);
            content.addClickable(this.name);
            if (this.nameConflict != null) {
                this.addDialogLine(content, new DialogLine(Component.translatable("waypoint.profile.name.taken",
                        this.nameConflict.name()), DANGER), textWidth, texts);
            }
        }
        this.dialogLines = List.copyOf(texts);
        this.dialogWidget = new ConfirmationDialog(0, 0, title, content, confirmLabel, confirm, this::closeDialog, this.font);
        this.dialogWidget.setPosition(centered(this.width, this.dialogWidget.getWidth()), centered(this.height, this.dialogWidget.getHeight()));
        if (this.initialized) {
            this.register();
        }
        this.updateControlStates();
    }

    private void addDialogLine(WidgetStack content, DialogLine line, int textWidth, List<Component> texts) {
        ScalableText text = new ScalableText(0, 0, line.text(), 1.0F, line.color(), textWidth, this.font);
        if (texts.isEmpty()) {
            content.addChild(text, 0);
        } else {
            content.addChild(text);
        }
        texts.add(line.text());
    }

    private boolean confirmAllowed() {
        return switch (Objects.requireNonNull(this.dialog)) {
            case CREATE -> ProfileNames.clean(this.name.getValue()) != null && this.nameConflict == null;
            case RENAME -> {
                String cleaned = ProfileNames.clean(this.name.getValue());
                Profile profile = this.find(this.dialogProfile);
                yield cleaned != null && this.nameConflict == null && profile != null && !cleaned.equals(profile.name());
            }
            case DELETE, DISCARD -> true;
        };
    }

    private void confirmCreate() {
        String cleaned = ProfileNames.clean(this.name.getValue());
        if (cleaned == null || this.currentConflict() != null) {
            return;
        }
        if (this.perform(() -> this.profiles.create(cleaned))) {
            Profile created = this.profiles.active();
            this.endDialog();
            this.loadCommand();
            this.showResult(Component.translatable("waypoint.profile.status.created", created.name()));
            this.requestFocus(this.row(created.id()).rename());
        }
    }

    private void confirmRename() {
        UUID id = this.dialogProfile;
        Profile before = this.find(id);
        String cleaned = ProfileNames.clean(this.name.getValue());
        if (before == null || cleaned == null || cleaned.equals(before.name()) || this.currentConflict() != null) {
            return;
        }
        if (this.perform(() -> this.profiles.rename(id, cleaned))) {
            AbstractWidget opener = this.dialogOpener;
            this.endDialog();
            this.showResult(Component.translatable("waypoint.profile.status.renamed", before.name(), cleaned));
            this.requestFocus(opener != null ? opener : this.doneButton);
        }
    }

    private void confirmDelete() {
        UUID id = this.dialogProfile;
        Profile deleted = this.find(id);
        if (deleted == null) {
            return;
        }
        int index = this.profiles.profiles().indexOf(deleted);
        Profile activeBefore = this.profiles.active();
        boolean wasActive = activeBefore != null && activeBefore.id().equals(id);
        boolean done = this.perform(() -> this.profiles.delete(id));
        // A delete whose files couldn't be removed has still left storage: its dialog closes on the failure status.
        if (!done && (!this.currentSession() || this.find(id) != null)) {
            return;
        }
        this.endDialog();
        Profile active = this.profiles.active();
        if (wasActive) {
            this.loadCommand();
        }
        if (done) {
            this.showResult(wasActive
                    ? Component.translatable("waypoint.profile.status.deleted_switched", deleted.name(), active.name())
                    : Component.translatable("waypoint.profile.status.deleted", deleted.name()));
        }
        List<Profile> list = this.profiles.profiles();
        if (index < list.size()) {
            Profile next = list.get(index);
            ProfileRow row = this.row(next.id());
            this.requestFocus(next.id().equals(active.id()) ? row.rename() : row.use());
        } else {
            this.requestFocus(this.newProfileButton);
        }
    }

    /** Closes the screen, or switches profiles; a failed switch keeps the dialog and the edit. */
    private void confirmDiscard() {
        UUID target = this.discardSwitchTo;
        if (target == null) {
            this.close.run();
        } else {
            this.switchTo(target);
        }
    }

    /** Shows an operation's result; a template problem in the command keeps the footer while it lasts. */
    private void showResult(Component message) {
        this.showStatus(message, false);
        this.onCommandEdited();
    }

    private void showObsolete() {
        this.showStatus(Component.translatable("waypoint.profile.status.obsolete"), true);
    }

    private void showStatus(Component message, boolean error) {
        this.statusMessage = message;
        this.statusError = error;
        this.showingProblem = false;
        this.status.setText(message);
        this.status.setColor(error ? DANGER : SUCCESS);
        // A wrapped status can change the footer's height, so the layout runs again.
        if (this.initialized) {
            this.layoutContent();
        }
    }

    /** Sets every control's {@code active} flag from the session and the open dialog. */
    private void updateControlStates() {
        boolean usable = this.currentSession();
        boolean editing = usable && this.dialog == null;
        this.settings.active = this.dialog == null;
        for (ProfileRow row : this.rows.values()) {
            row.use().active = editing;
            row.rename().active = editing;
            row.delete().active = editing;
        }
        this.newProfileButton.active = editing;
        this.resetButton.active = editing;
        enable(this.command, editing);
        this.saveButton.active = editing && this.commandEdited() && LocalTeleportCommand.problem(this.command.getValue()).isEmpty();
        this.doneButton.active = this.dialog == null;
        if (this.dialogWidget != null) {
            this.dialogWidget.getCancelButton().active = true;
            this.dialogWidget.getConfirmButton().active = usable && this.confirmAllowed();
        }
        enable(this.name, usable && (this.dialog == Dialog.CREATE || this.dialog == Dialog.RENAME));
        this.settings.refreshWidgetVisibility();
    }

    /** Vanilla restarts text input whenever a focused field's editable flag is set, so it's set only on a change. */
    private static void enable(TranslucentTextField field, boolean enabled) {
        if (field.active != enabled) {
            field.active = enabled;
            field.setEditable(enabled);
        }
    }

    private void requestFocus(GuiEventListener target) {
        this.requestFocus(target, null);
    }

    /**
     * Focuses {@code target} now, and again after the current click: vanilla focuses the clicked
     * widget after its callback runs, which would undo the change. {@code focused} runs both times,
     * for state that losing focus resets, such as a field's selection. A hidden row widget is
     * scrolled into view once the screen is laid out.
     */
    private void requestFocus(GuiEventListener target, @Nullable Runnable focused) {
        this.setFocused(target);
        if (this.initialized) {
            ClientConfigScreen.keepFocusVisible(this, this.settings);
        }
        if (focused != null && this.getFocused() == target) {
            focused.run();
        }
        this.pendingFocus = this.getFocused();
        this.pendingFocusAction = focused;
    }

    /** Runs after vanilla has focused the clicked widget; package-private so tests can replay a click. */
    void applyPendingFocus() {
        if (this.pendingFocus != null) {
            ClientConfigScreen.handOverFocus(this, this.pendingFocus);
            if (this.pendingFocusAction != null) {
                this.pendingFocusAction.run();
            }
        }
        this.clearPendingFocus();
    }

    /** Enter and Space don't move focus after a button's callback, so a key press only drops the request. */
    private void clearPendingFocus() {
        this.pendingFocus = null;
        this.pendingFocusAction = null;
    }

    @Override
    protected void init() {
        super.init();
        this.start();
    }

    /** Lays the screen out and registers its widgets; {@link #init} runs it after the base screen's key setup. */
    void start() {
        this.initialized = true;
        this.layoutContent();
        if (this.dialog != null) {
            this.buildDialog();
        }
        this.register();
        this.updateControlStates();
        if (this.dialog != null) {
            this.setFocused(this.dialog == Dialog.CREATE || this.dialog == Dialog.RENAME ? this.name : this.dialogWidget.getCancelButton());
        }
    }

    /** Sizes and places the title, the panel, the footer and an open dialog for the window. */
    void layoutContent() {
        int maximumWidth = Math.max(80, Math.min(420, this.width - 20));
        this.command.setWidth(MIN_COMMAND_WIDTH);
        this.settings.relayout();
        int panelWidth = Math.min(maximumWidth, this.settings.getPreferredWidth() + SettingsListWidget.PANEL_PADDING * 2);
        this.settings.setVisualWidth(panelWidth);
        // The command takes the whole row.
        this.command.setWidth(Math.max(40, this.settings.getRowWidth()));
        this.settings.relayout();
        this.heading.setWidth(panelWidth);
        this.status.setMaxWidth(Math.max(1, panelWidth - this.doneButton.getVisualWidth() - STATUS_GAP));
        this.footerHeight = Math.max(this.doneButton.getVisualHeight(), this.status.getHeight());
        this.layout = ProfileLayout.calculate(this.width, this.height, panelWidth, this.heading.getHeight(),
                this.settings.getContentHeight() + SettingsListWidget.PANEL_PADDING * 2, this.footerHeight);
        this.heading.setPosition(this.layout.x(), this.layout.titleY());
        this.settings.setVisualHeight(this.layout.height());
        this.settings.setPosition(this.layout.x() + SettingsListWidget.PANEL_PADDING, this.layout.y() + SettingsListWidget.PANEL_PADDING);
        this.doneButton.setPosition(this.layout.x() + this.layout.width() - this.doneButton.getVisualWidth(),
                this.layout.footerY() + centered(this.footerHeight, this.doneButton.getVisualHeight()));
        this.status.setPosition(this.layout.x(), this.layout.footerY() + centered(this.footerHeight, this.status.getHeight()));
        if (this.dialogWidget != null) {
            this.dialogWidget.setPosition(centered(this.width, this.dialogWidget.getWidth()), centered(this.height, this.dialogWidget.getHeight()));
        }
        if (this.initialized) {
            ClientConfigScreen.keepFocusVisible(this, this.settings);
        }
    }

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Under an open dialog, nothing reacts to the mouse or shows a tooltip.
        int contentMouseX = this.dialogWidget != null ? NO_MOUSE : mouseX;
        int contentMouseY = this.dialogWidget != null ? NO_MOUSE : mouseY;
        this.heading.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.settings.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.status.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.doneButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        if (this.dialogWidget != null) {
            nextLayer(context);
            this.dialogWidget.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
            previousLayer(context);
        }
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        this.applyPendingFocus();
        return handled;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        this.applyPendingFocus();
        return handled;
    }
    *///?}

    //? if <=1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return this.scrollSettingsList(mouseX, mouseY, 0, verticalAmount) || super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.scrollSettingsList(mouseX, mouseY, horizontalAmount, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    /** Gives the wheel to the list first; a widget the wheel hides loses focus. */
    private boolean scrollSettingsList(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.settings.isMouseOver(mouseX, mouseY)
                || !this.settings.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return false;
        }
        if (this.getFocused() instanceof AbstractWidget widget && !widget.visible) {
            this.setFocused(null);
        }
        return true;
    }

    /**
     * Escape, Enter in a dialog's name field and Tab are handled here first: the base screen releases
     * movement keys before vanilla sees a key, and vanilla closes the screen on Escape.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.acceptMovementKeys(!(this.getFocused() instanceof EditBox));
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (this.dialog != null) {
                this.closeDialog();
            } else if (!this.dismissFocusedInput()) {
                this.onClose();
            }
            this.clearPendingFocus();
            return true;
        }
        boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
        if (enter && this.dialogWidget != null && this.getFocused() == this.name) {
            if (this.dialogWidget.getConfirmButton().active) {
                this.dialogWidget.getConfirmButton().onClick(0, 0);
            }
            this.clearPendingFocus();
            return true;
        }
        if (enter && this.dialogWidget == null && this.getFocused() == this.command) {
            if (this.saveButton.active) {
                this.saveCommand();
            }
            this.clearPendingFocus();
            return true;
        }
        if (keyCode == InputConstants.KEY_TAB) {
            this.settings.revealTabTarget(this, !ClientConfigScreen.isShiftDown(keyCode, scanCode, modifiers));
        }
        GuiEventListener focusedBefore = this.getFocused();
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        GuiEventListener focused = this.getFocused();
        if (focused != null && focused != focusedBefore) {
            this.settings.reveal(focused);
        }
        this.clearPendingFocus();
        return handled;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.currentSession() && !this.statusMessage.equals(Component.translatable("waypoint.profile.status.obsolete"))) {
            this.showObsolete();
        }
        this.updateControlStates();
    }

    @Override
    int getContentWidth() {
        return this.layout == null ? 0 : this.layout.width();
    }

    @Override
    int getContentHeight() {
        return this.layout == null ? 0 : this.layout.footerY() + this.footerHeight - this.layout.titleY();
    }

    /** Done and Escape end here; an unsaved command edit is never dropped without asking. */
    @Override
    public void onClose() {
        if (this.currentSession() && this.commandEdited()) {
            this.openDiscardDialog(this.doneButton, null);
        } else {
            this.close.run();
        }
    }

    /** An open dialog keeps its focus through a resize; {@link #init} focuses it. */
    @Override
    protected boolean hasOpenModal() {
        return this.dialogWidget != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Names typed on this screen; storage accepts any non-blank name, as automatic profiles need. */
    static final class ProfileNames {
        static final int MAX_LENGTH = 128;

        private ProfileNames() {
        }

        /** The stripped name, or {@code null} when it's blank or too long. */
        static @Nullable String clean(String input) {
            String name = input.strip();
            return name.isEmpty() || name.length() > MAX_LENGTH ? null : name;
        }

        /** A profile other than {@code except} with this name, ignoring case and surrounding spaces. */
        static @Nullable Profile conflict(List<Profile> profiles, String name, @Nullable UUID except) {
            String cleaned = clean(name);
            if (cleaned == null) {
                return null;
            }
            String key = cleaned.toLowerCase(Locale.ROOT);
            for (Profile profile : profiles) {
                if (!profile.id().equals(except) && profile.name().strip().toLowerCase(Locale.ROOT).equals(key)) {
                    return profile;
                }
            }
            return null;
        }
    }

    record ProfileLayout(int x, int y, int width, int height, int titleY, int footerY) {
        static ProfileLayout calculate(int screenWidth, int screenHeight, int preferredWidth, int titleHeight,
                                       int preferredPanelHeight, int footerHeight) {
            int width = Math.min(preferredWidth, Math.max(80, screenWidth - 20));
            int availableHeight = screenHeight - 20 - titleHeight - footerHeight - 12;
            int height = Math.min(preferredPanelHeight, Math.max(33, availableHeight));
            int contentHeight = titleHeight + 6 + height + 6 + footerHeight;
            int titleY = Math.max(10, (screenHeight - contentHeight) / 2);
            int panelY = titleY + titleHeight + 6;
            return new ProfileLayout((screenWidth - width) / 2, panelY, width, height, titleY, panelY + height + 6);
        }
    }
}
