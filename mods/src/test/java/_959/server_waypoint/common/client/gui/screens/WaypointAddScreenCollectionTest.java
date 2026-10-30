package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.*;
import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.local.LocalWaypointProfiles;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.waypoint.*;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WaypointAddScreenCollectionTest {
    @TempDir Path directory;
    private Field minecraftInstance;
    private Object previousMinecraft;
    private ClientWaypointCollections collections;
    private LocalWaypointProfiles profiles;
    private ClientWaypointAccess access;
    private final List<String> commands = new ArrayList<>();
    private boolean serverAllowed = true;

    @BeforeEach
    void setup() throws Exception {
        //? if fabric {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        //?} else {
        /*Assumptions.abort("Registry-backed forms require the Forge-family loader");
        *///?}
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(minecraft, new TestFont());
        var renderer = (net.minecraft.client.renderer.GameRenderer) unsafe.allocateInstance(net.minecraft.client.renderer.GameRenderer.class);
        Field mainCamera = net.minecraft.client.renderer.GameRenderer.class.getDeclaredField("mainCamera");
        mainCamera.setAccessible(true);
        mainCamera.set(renderer, unsafe.allocateInstance(net.minecraft.client.Camera.class));
        Field rendererField = Minecraft.class.getDeclaredField("gameRenderer");
        rendererField.setAccessible(true);
        rendererField.set(minecraft, renderer);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        previousMinecraft = minecraftInstance.get(null);
        minecraftInstance.set(null, minecraft);
        collections = new ClientWaypointCollections(new WaypointFilesManagerCore(directory.resolve("shared")));
        collections.beginConnection(UUID.randomUUID(), "test");
        collections.bindServer(directory, 1);
        profiles = new LocalWaypointProfiles(directory, collections);
        profiles.joinMultiplayer(UUID.randomUUID(), "test");
        collections.markSnapshotApplied(ClientWaypointSource.SHARED);
        collections.markSnapshotApplied(ClientWaypointSource.PRIVATE);
        access = new ClientWaypointAccess(collections, profiles, new ClientWaypointAccess.Transport() {
            public boolean connected() { return true; }
            public String currentDimension() { return "minecraft:overworld"; }
            public int commandLimit() { return 32767; }
            public List<String> dimensions() { return List.of("minecraft:overworld"); }
            public boolean allowed(ClientWaypointSource source, ClientWaypointAccess.Action action) { return serverAllowed; }
            public boolean command(String command) { commands.add(command); return true; }
            public boolean edit(WaypointEditRequestMessage message) { fail("Add form must not send edit payloads"); return false; }
        });
    }

    @AfterEach
    void restoreMinecraft() throws Exception { if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft); }

    private WaypointAddScreen form() {
        return new WaypointAddScreen(null, access,
                access.capture(ClientWaypointSource.LOCAL, "minecraft:overworld", "Homes", null), new WaypointPos(1, 64, 2), null);
    }

    @Test
    void choosingADestinationRetainsTheDraftAndRejectsUnavailableSources() {
        var form = form();
        form.nameEditBox.setValue("Entered name");
        form.descriptionEditBox.setValue("Entered description");
        assertTrue(form.selectDestination(ClientWaypointSource.PRIVATE));
        assertEquals(ClientWaypointSource.PRIVATE, form.selection.source());
        assertEquals("Entered name", form.nameEditBox.getValue());
        assertEquals("Entered description", form.descriptionEditBox.getValue());
        serverAllowed = false;
        assertFalse(form.destinationAvailable());
        assertFalse(form.selectDestination(ClientWaypointSource.SHARED));
        assertEquals(ClientWaypointSource.PRIVATE, form.selection.source());
        assertTrue(form.selectDestination(ClientWaypointSource.LOCAL));
        assertTrue(form.destinationAvailable());
        assertTrue(commands.isEmpty());
    }

    @Test
    void permissionRevocationStillAllowsAnExplicitLocalDestinationInTheSameGeneration() throws Exception {
        var form = form();
        assertTrue(form.selectDestination(ClientWaypointSource.PRIVATE));
        collections.apply(new _959.server_waypoint.core.network.message.WaypointCollectionStateMessage(
                _959.server_waypoint.core.collection.WaypointScope.PRIVATE, false), collections.generation());
        assertFalse(form.destinationAvailable());
        assertTrue(form.selectDestination(ClientWaypointSource.LOCAL));
        assertTrue(form.destinationAvailable());
    }

    @Test
    void profileChangesKeepEnteredFieldsAndCannotRetargetTheOpenForm() throws Exception {
        var form = form();
        form.nameEditBox.setValue("Unsubmitted draft");
        var captured = form.selection;
        profiles.create("Another world");
        assertFalse(form.destinationAvailable());
        assertFalse(form.selectDestination(ClientWaypointSource.SHARED));
        assertEquals(captured, form.selection);
        assertEquals("Unsubmitted draft", form.nameEditBox.getValue());
        assertTrue(commands.isEmpty());
        assertTrue(collections.files(ClientWaypointSource.LOCAL).getFileManagerMap().isEmpty());
    }
}
