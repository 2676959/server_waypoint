package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles.Profile;
import _959.server_waypoint.core.waypoint.WaypointList;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The screen on real profile storage. Its profiles are Default ({@code original}) and Survival
 * ({@code survival}, active and picked by the player), on the server "test".
 */
class LocalWaypointProfilesScreenInteractionTest {
    @TempDir Path directory;
    private final Map<Field, Object> previous = new LinkedHashMap<>();
    private WaypointClientMod client;
    private UUID account;
    private UUID original;
    private UUID survival;
    private boolean closed;
    private LocalWaypointProfilesScreen screen;

    @BeforeEach
    void setup() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(minecraft, new TestFont());
        Field renderer = Minecraft.class.getDeclaredField("gameRenderer");
        renderer.setAccessible(true);
        renderer.set(minecraft, unsafe.allocateInstance(GameRenderer.class));
        //? if >=26.1 {
        // From 26.1 a focused text field starts the client's text input.
        Field textInput = Minecraft.class.getDeclaredField("textInputManager");
        textInput.setAccessible(true);
        textInput.set(minecraft, unsafe.allocateInstance(com.mojang.blaze3d.platform.TextInputManager.class));
        //?}
        setStatic(Minecraft.class, "instance", minecraft);
        setStatic(WaypointClientMod.class, "INSTANCE", null);
        setStatic(WaypointClientMod.class, "currentDimensionName", null);
        setStatic(WaypointList.class, "excludeClientOnlyFields", false);
        setStatic(_959.server_waypoint.common.server.LocalWaypointUpload.class, "handler", null);
        var constructor = WaypointClientMod.class.getDeclaredConstructor(Minecraft.class, Path.class, Path.class);
        constructor.setAccessible(true);
        client = constructor.newInstance(minecraft, directory, directory);
        account = UUID.randomUUID();
        client.localProfiles().joinMultiplayer(account, "test");
        original = client.localProfiles().active().id();
        survival = client.localProfiles().create("Survival").id();
        screen = new LocalWaypointProfilesScreen(client, () -> closed = true);
    }

    @AfterEach
    void restore() throws Exception { for (var value : previous.entrySet()) value.getKey().set(null, value.getValue()); }

    @Test
    void theCommandFieldColorsItsPlaceholders() {
        screen.commandField().setValue("{x} ~");
        assertEquals(List.of(0xFF7B72, 0xFF7B72, 0xFF7B72), colors(screen.commandField().formatShown("{x}", 0)));
    }

    @Test
    void aScreenOpenedWithoutALocalProfileCanOnlyClose() throws Exception {
        client.localProfiles().leave();
        var empty = new LocalWaypointProfilesScreen(client, () -> closed = true);
        assertTrue(empty.statusIsError());
        assertFalse(empty.newProfileButton().active);
        empty.doneButton().onClick(0, 0);
        assertTrue(closed);
    }

    @Test
    void aScreenWithoutALocalProfileNamesNoPlaceOrProfile() throws Exception {
        client.localProfiles().leave();
        var empty = new LocalWaypointProfilesScreen(client, () -> { });
        assertEquals(Component.translatable("waypoint.profile.list_section.plain"), empty.listTitle());
        assertEquals(Component.translatable("waypoint.profile.command_section.plain"), empty.commandTitle());
    }

    @Test
    void useSwitchesToThatProfileAndMovesFocusToItsRenameButton() {
        screen.row(original).use().onClick(0, 0);
        assertEquals(original, client.localProfiles().active().id());
        assertStatus("waypoint.profile.status.switched", "Default");
        assertEquals(screen.row(original).rename(), screen.getFocused());
    }

    @Test
    void theListNamesTheServerAndTheActiveRowSaysWhyItIsInUse() throws Exception {
        assertEquals(Component.translatable("waypoint.profile.list_section", "test"), screen.listTitle());
        assertEquals(Component.translatable("waypoint.profile.command_section", "Survival"), screen.commandTitle());
        assertEquals(Component.translatable("waypoint.profile.in_use.manual"), screen.activeStatus());
        client.localProfiles().leave();
        client.localProfiles().joinMultiplayer(account, "test");
        var reopened = new LocalWaypointProfilesScreen(client, () -> { });
        assertEquals(Component.translatable("waypoint.profile.in_use.automatic"), reopened.activeStatus());
    }

    @Test
    void renamingAnotherProfileKeepsTheActiveOneAndAllowsCaseChangesOfItsOwnName() {
        UUID active = client.localProfiles().active().id();
        screen.row(original).rename().onClick(0, 0);
        assertEquals(LocalWaypointProfilesScreen.Dialog.RENAME, screen.openDialog());
        assertEquals(screen.nameField(), screen.getFocused());
        screen.nameField().setValue(" survival ");
        assertFalse(screen.dialogWidget().getConfirmButton().active);
        assertTrue(screen.dialogLines().contains(Component.translatable("waypoint.profile.name.taken", "Survival")));
        screen.nameField().setValue("DEFAULT");
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals("DEFAULT", profile(original).name());
        assertEquals(active, client.localProfiles().active().id());
        assertStatus("waypoint.profile.status.renamed", "Default", "DEFAULT");
        assertNull(screen.openDialog());
        assertEquals(screen.row(original).rename(), screen.getFocused());
    }

    @Test
    void renameKeepsTheWholeNameSelectedAfterTheClickHandsFocusOver() {
        var icon = screen.row(original).rename();
        icon.onClick(0, 0);
        // What vanilla does after a click's callback: it focuses the clicked widget.
        screen.setFocused(icon);
        screen.applyPendingFocus();
        assertEquals(screen.nameField(), screen.getFocused());
        assertEquals("Default", screen.nameField().getHighlighted());
    }

    @Test
    void newProfileNeedsAUniqueNameAndEnterCreatesIt() {
        screen.newProfileButton().onClick(0, 0);
        screen.nameField().setValue("   ");
        assertFalse(screen.dialogWidget().getConfirmButton().active);
        screen.nameField().setValue("Exploration");
        screen.setFocused(screen.nameField());
        assertTrue(screen.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals("Exploration", client.localProfiles().active().name());
        assertStatus("waypoint.profile.status.created", "Exploration");
        assertEquals(screen.row(client.localProfiles().active().id()).rename(), screen.getFocused());
    }

    @Test
    void deletingAnotherProfileDoesNotSwitch() {
        UUID active = client.localProfiles().active().id();
        screen.row(original).delete().onClick(0, 0);
        assertEquals(screen.dialogWidget().getCancelButton(), screen.getFocused());
        assertFalse(screen.dialogLines().contains(Component.translatable("waypoint.profile.delete.active_note")));
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals(active, client.localProfiles().active().id());
        assertEquals(1, client.localProfiles().profiles().size());
        assertStatus("waypoint.profile.status.deleted", "Default");
        assertEquals(screen.row(active).rename(), screen.getFocused());
    }

    @Test
    void deletingTheActiveProfileSaysWhichOneTookOver() {
        screen.row(survival).delete().onClick(0, 0);
        assertTrue(screen.dialogLines().contains(Component.translatable("waypoint.profile.delete.active_note")));
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals(original, client.localProfiles().active().id());
        assertStatus("waypoint.profile.status.deleted_switched", "Survival", "Default");
        // Survival was the last row, so no row took its place.
        assertEquals(screen.newProfileButton(), screen.getFocused());
    }

    @Test
    void aDeleteThatFailsAfterStorageDroppedTheProfileFollowsStorage() throws Exception {
        Path folder = client.localProfiles().directory().resolve("profiles").resolve(original.toString());
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("leftover.json"), "{}");
        var row = screen.row(original);
        row.delete().onClick(0, 0);
        assertTrue(folder.toFile().setWritable(false));
        try {
            screen.dialogWidget().getConfirmButton().onClick(0, 0);
        } finally {
            assertTrue(folder.toFile().setWritable(true));
        }
        assertTrue(client.localProfiles().profiles().stream().noneMatch(profile -> profile.id().equals(original)));
        assertNull(screen.openDialog());
        var widgets = new ArrayList<AbstractWidget>();
        screen.settingsList().visitWidgets(widgets::add);
        assertFalse(widgets.contains(row.use()));
        assertTrue(screen.statusIsError());
        assertStatus("waypoint.local.storage_failed");
        assertEquals(screen.row(survival).rename(), screen.getFocused());
    }

    @Test
    void deletingTheOnlyProfileSwitchesToANewDefault() throws Exception {
        client.localProfiles().delete(original);
        var only = new LocalWaypointProfilesScreen(client, () -> { });
        only.row(survival).delete().onClick(0, 0);
        only.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals("Default", client.localProfiles().active().name());
        assertNotEquals(survival, client.localProfiles().active().id());
        assertEquals(Component.translatable("waypoint.profile.status.deleted_switched", "Survival", "Default"), only.statusMessage());
    }

    @Test
    void duplicateNamesInStorageStaySeparateRows() throws Exception {
        client.localProfiles().rename(original, "Survival");
        var duplicates = new LocalWaypointProfilesScreen(client, () -> { });
        duplicates.row(original).use().onClick(0, 0);
        assertEquals(original, client.localProfiles().active().id());
    }

    @Test
    void saveStoresTheEditedCommandAndSaysSo() {
        assertFalse(screen.saveButton().active);
        screen.commandField().setValue("/tp @s {x} {y} {z}");
        assertTrue(screen.saveButton().active);
        screen.saveButton().onClick(0, 0);
        assertEquals("/tp @s {x} {y} {z}", client.localProfiles().active().teleportCommand());
        assertStatus("waypoint.profile.status.command_saved");
        assertFalse(screen.saveButton().active);
    }

    @Test
    void staleScreensIgnoreTheirButtons() throws Exception {
        client.localProfiles().create("Elsewhere");
        screen.tick();
        assertFalse(screen.row(original).use().active);
        assertTrue(screen.statusIsError());
        screen.row(original).use().onClick(0, 0);
        assertEquals("Elsewhere", client.localProfiles().active().name());
    }

    @Test
    void rowsKeepTheirControlsInsideThePanel() throws Exception {
        // Labels wrap as they do in game.
        installFont(TestFont.wrapping());
        int[][] sizes = {{320, 240}, {480, 270}};
        int[] oneLineHeights = new int[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            var oneLine = new LocalWaypointProfilesScreen(client, () -> { });
            oneLine.width = sizes[i][0];
            oneLine.height = sizes[i][1];
            oneLine.layoutContent();
            oneLineHeights[i] = oneLine.settingsList().getContentHeight();
        }
        client.localProfiles().rename(original, "W".repeat(128));
        for (int i = 0; i < sizes.length; i++) {
            int[] size = sizes[i];
            var sized = new LocalWaypointProfilesScreen(client, () -> { });
            sized.width = size[0];
            sized.height = size[1];
            sized.layoutContent();
            var panel = sized.settingsList();
            assertTrue(panel.getContentHeight() > oneLineHeights[i], "The long name wraps onto more lines");
            int panelRight = panel.getVisualX() + panel.getVisualWidth() - SettingsListWidget.PANEL_PADDING;
            Integer actionsRight = null;
            UUID active = client.localProfiles().active().id();
            for (var profile : client.localProfiles().profiles()) {
                var row = sized.row(profile.id());
                int right = row.delete().getX() + row.delete().getWidth();
                if (actionsRight == null) actionsRight = right;
                assertEquals(actionsRight, right);
                assertTrue(right <= panelRight);
                if (!profile.id().equals(active)) assertTrue(row.use().getX() + row.use().getWidth() <= row.rename().getX());
            }
            assertTrue(sized.doneButton().getY() + sized.doneButton().getHeight() <= size[1]);
            var widgets = new ArrayList<AbstractWidget>();
            panel.visitWidgets(widgets::add);
            assertEquals(widgets.size(), new HashSet<>(widgets).size());
        }
    }

    @Test
    void enterSavesTheCommandAndResetRestoresTheDefault() {
        screen.commandField().setValue("/tp @s {x} {y} {z}");
        screen.setFocused(screen.commandField());
        assertTrue(screen.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals("/tp @s {x} {y} {z}", client.localProfiles().active().teleportCommand());
        assertStatus("waypoint.profile.status.command_saved");
        screen.setFocused(screen.resetButton());
        screen.resetButton().onClick(0, 0);
        assertEquals(screen.commandField(), screen.getFocused());
        assertEquals(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, client.localProfiles().active().teleportCommand());
        assertEquals(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, screen.commandField().getValue());
        assertStatus("waypoint.profile.status.command_reset");
    }

    @Test
    void templateProblemsShowInTheFooterAndKeepSaveOff() {
        screen.commandField().setValue("tp {pitch}");
        assertTrue(screen.statusIsError());
        assertStatus("waypoint.profile.problem.unknown", "{pitch}");
        assertFalse(screen.saveButton().active);
        screen.commandField().setValue("tp {x}");
        assertEquals(Component.empty(), screen.statusMessage());
        assertTrue(screen.saveButton().active);
    }

    @Test
    void useWithAnUnsavedEditAsksBeforeDiscardingIt() {
        String saved = client.localProfiles().active().teleportCommand();
        screen.commandField().setValue("/tp @s {x} 64 {z}");
        screen.row(original).use().onClick(0, 0);
        assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
        screen.dialogWidget().getCancelButton().onClick(0, 0);
        assertNotEquals(original, client.localProfiles().active().id());
        assertEquals("/tp @s {x} 64 {z}", screen.commandField().getValue());
        assertEquals(screen.row(original).use(), screen.getFocused());
        screen.row(original).use().onClick(0, 0);
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals(original, client.localProfiles().active().id());
        assertEquals(client.localProfiles().active().teleportCommand(), screen.commandField().getValue());
        assertEquals(saved, profile(survival).teleportCommand());
    }

    @Test
    void doneAndEscapeAskBeforeDiscardingAndDiscardCloses() {
        screen.commandField().setValue("/tp @s 0 0 0");
        screen.doneButton().onClick(0, 0);
        assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertNull(screen.openDialog());
        assertFalse(closed);
        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertTrue(closed);
    }

    @Test
    void newProfileSaysTheUnsavedEditWillBeDiscarded() {
        screen.commandField().setValue("/tp @s 0 0 0");
        screen.newProfileButton().onClick(0, 0);
        assertTrue(screen.dialogLines().contains(Component.translatable("waypoint.profile.discard.note")));
        screen.nameField().setValue("Fresh");
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, screen.commandField().getValue());
    }

    @Test
    void deletingTheActiveProfileWithAnUnsavedEditDoesNotAsk() {
        screen.commandField().setValue("/tp @s 0 0 0");
        screen.row(client.localProfiles().active().id()).delete().onClick(0, 0);
        assertEquals(LocalWaypointProfilesScreen.Dialog.DELETE, screen.openDialog());
    }

    @Test
    void aSessionEndingUnderADialogLeavesOnlyCancelAndDone() throws Exception {
        screen.commandField().setValue("/tp @s 0 0 0");
        screen.row(original).delete().onClick(0, 0);
        client.localProfiles().leave();
        screen.tick();
        var dialog = screen.dialogWidget();
        assertFalse(dialog.getConfirmButton().active);
        assertTrue(dialog.getCancelButton().active);
        dialog.getConfirmButton().onClick(0, 0);
        dialog.getCancelButton().onClick(0, 0);
        screen.doneButton().onClick(0, 0);
        assertNull(screen.openDialog());
        assertTrue(closed);
    }

    @Test
    void widgetsAreRegisteredInDisplayOrderAndFollowTheRows() {
        screen.width = 480;
        screen.height = 270;
        screen.start();
        var first = screen.row(original);
        var second = screen.row(survival);
        assertEquals(List.of(first.use(), first.rename(), first.delete(), second.rename(), second.delete(),
                screen.newProfileButton(), screen.commandField(), screen.saveButton(), screen.resetButton(),
                screen.settingsList(), screen.doneButton()), List.<GuiEventListener>copyOf(screen.children()));
        first.delete().onClick(0, 0);
        var dialog = screen.dialogWidget();
        int count = screen.children().size();
        assertEquals(List.of(dialog.getCancelButton(), dialog.getConfirmButton()), List.copyOf(screen.children().subList(count - 2, count)));
        dialog.getConfirmButton().onClick(0, 0);
        assertFalse(screen.children().contains(first.use()));
        assertFalse(screen.children().contains(dialog.getConfirmButton()));
        assertEquals(second.rename(), screen.getFocused());
    }

    @Test
    void aStaleScreenClosesWithoutAskingEvenWhileAProfileIsActive() throws Exception {
        screen.commandField().setValue("/tp @s 0 0 0");
        client.localProfiles().create("Elsewhere");
        screen.tick();
        screen.doneButton().onClick(0, 0);
        assertNull(screen.openDialog());
        assertTrue(closed);
    }

    @Test
    void aDiscardWhoseSwitchFailsKeepsTheEditAndTheDialog() throws Exception {
        screen.commandField().setValue("/tp @s 0 0 0");
        screen.row(original).use().onClick(0, 0);
        Path session = client.localProfiles().directory();
        assertTrue(session.toFile().setWritable(false));
        try {
            screen.dialogWidget().getConfirmButton().onClick(0, 0);
        } finally {
            assertTrue(session.toFile().setWritable(true));
        }
        assertEquals(survival, client.localProfiles().active().id());
        assertEquals("/tp @s 0 0 0", screen.commandField().getValue());
        assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
        assertStatus("waypoint.local.storage_failed");
    }

    @Test
    void aTemplateProblemOutlastsAnotherOperationsStatus() {
        screen.commandField().setValue("tp {pitch}");
        screen.row(original).rename().onClick(0, 0);
        screen.nameField().setValue("Builds");
        screen.dialogWidget().getConfirmButton().onClick(0, 0);
        assertEquals("Builds", profile(original).name());
        assertTrue(screen.statusIsError());
        assertStatus("waypoint.profile.problem.unknown", "{pitch}");
    }

    private void installFont(Font font) throws Exception {
        Field field = Minecraft.class.getDeclaredField("font");
        field.setAccessible(true);
        field.set(Minecraft.getInstance(), font);
    }

    private void assertStatus(String key, Object... args) {
        assertEquals(Component.translatable(key, args), screen.statusMessage());
    }

    private Profile profile(UUID id) {
        return client.localProfiles().profiles().stream().filter(profile -> profile.id().equals(id)).findFirst().orElseThrow();
    }

    /** Each character's style color, or -1 for none. */
    private static List<Integer> colors(FormattedCharSequence text) {
        List<Integer> colors = new ArrayList<>();
        text.accept((index, style, codePoint) -> {
            colors.add(style.getColor() == null ? -1 : style.getColor().getValue());
            return true;
        });
        return colors;
    }

    private void setStatic(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        previous.putIfAbsent(field, field.get(null));
        field.set(null, value);
    }
}
