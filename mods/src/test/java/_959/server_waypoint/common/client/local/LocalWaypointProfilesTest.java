package _959.server_waypoint.common.client.local;

import _959.server_waypoint.common.client.*;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LocalWaypointProfilesTest {
    @TempDir Path root;
    private final UUID account = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ClientWaypointCollections collections() {
        return new ClientWaypointCollections(new WaypointFilesManagerCore(root.resolve("shared")));
    }

    @Test
    void firstJoinCreatesDefaultAndRestoresWaypointsAndCommandWithoutHandshake() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "Example.ORG");
        assertEquals("Default", profiles.active().name());
        assertTrue(collections.ready(ClientWaypointSource.LOCAL));
        assertFalse(collections.ready(ClientWaypointSource.PRIVATE));
        UUID id = profiles.active().id();
        Path directory = profiles.directory();
        add(profiles, collections, "Base");
        profiles.setTeleportCommand("/tp @s {x} {y} {z}");
        profiles.leave();
        assertFalse(collections.ready(ClientWaypointSource.LOCAL));
        var reloaded = new LocalWaypointProfiles(root, collections);
        reloaded.joinMultiplayer(account, "example.org:25565");
        assertEquals(directory, reloaded.directory());
        assertEquals(id, reloaded.active().id());
        assertEquals("/tp @s {x} {y} {z}", reloaded.active().teleportCommand());
        assertNotNull(collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("Homes").getWaypointByName("Base"));
        assertTrue(collections.files(ClientWaypointSource.SHARED).getFileManagerMap().isEmpty());
    }

    @Test
    void accountHostPortAndSingleplayerSavesHaveIndependentRoots() throws Exception {
        var profiles = new LocalWaypointProfiles(root, collections());
        profiles.joinMultiplayer(account, "example.org:25565");
        Path first = profiles.directory();
        profiles.joinMultiplayer(account, "example.org:25566");
        assertNotEquals(first, profiles.directory());
        profiles.joinMultiplayer(account, "example-org:25565");
        assertNotEquals(first, profiles.directory());
        profiles.joinMultiplayer(UUID.randomUUID(), "example.org:25565");
        assertNotEquals(first, profiles.directory());
        profiles.joinSingleplayer(account, root.resolve("saves/one"));
        assertEquals(root.resolve("saves/one/server_waypoint/client-local").resolve(account.toString()), profiles.directory());
        UUID one = profiles.active().id();
        profiles.joinSingleplayer(account, root.resolve("saves/two"));
        assertNotEquals(one, profiles.active().id());
        profiles.joinSingleplayer(account, root.resolve("saves/one"));
        assertEquals(one, profiles.active().id());
    }

    @Test
    void normalizedIpv6AndIdnAddressesReconnectToTheSameProfile() throws Exception {
        var profiles = new LocalWaypointProfiles(root, collections());
        profiles.joinMultiplayer(account, "[2001:DB8::1]");
        UUID ipv6 = profiles.active().id();
        profiles.joinMultiplayer(account, "[2001:db8::1]:25565");
        assertEquals(ipv6, profiles.active().id());
        profiles.joinMultiplayer(account, "BÜCHER.example.");
        UUID idn = profiles.active().id();
        profiles.joinMultiplayer(account, "xn--bcher-kva.example:25565");
        assertEquals(idn, profiles.active().id());
    }

    @Test
    void identitiesBindFallbackOnceThenCreateEmptyProfilesAndRestoreKnownBindings() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID fallback = profiles.active().id();
        add(profiles, collections, "First");
        profiles.onBackendIdentity(10);
        assertEquals(fallback, profiles.active().id());
        profiles.joinMultiplayer(account, "proxy");
        profiles.onBackendIdentity(20);
        UUID second = profiles.active().id();
        assertNotEquals(fallback, second);
        assertTrue(collections.files(ClientWaypointSource.LOCAL).getFileManagerMap().isEmpty());
        profiles.onBackendIdentity(10);
        assertEquals(fallback, profiles.active().id());
        assertNotNull(collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld"));
        profiles.leave();
        profiles.joinMultiplayer(account, "proxy");
        profiles.onBackendIdentity(20);
        assertEquals(second, profiles.active().id());
    }

    @Test
    void manualChoiceWinsLateHandshakeAndPersistsFallbackAndBackendBindings() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID initial = profiles.active().id();
        profiles.onBackendIdentity(10);
        var manual = profiles.create("My world");
        profiles.select(initial);
        profiles.leave();
        profiles.joinMultiplayer(account, "proxy");
        profiles.select(manual.id());
        add(profiles, collections, "Before handshake");
        Path original = collections.files(ClientWaypointSource.LOCAL).getWaypointFilesDir();
        profiles.onBackendIdentity(10);
        assertEquals(manual.id(), profiles.active().id());
        profiles.onBackendIdentity(20);
        assertEquals(manual.id(), profiles.active().id());
        assertEquals(original, collections.files(ClientWaypointSource.LOCAL).getWaypointFilesDir());
        profiles.leave();
        profiles.joinMultiplayer(account, "proxy");
        assertEquals(manual.id(), profiles.active().id());
        profiles.onBackendIdentity(10);
        assertEquals(manual.id(), profiles.active().id());
    }

    @Test
    void createRenameDeleteUseUuidPathsAndRemoveBindingsAndLastDeletionCreatesDefault() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID first = profiles.active().id();
        profiles.onBackendIdentity(10);
        var second = profiles.create("../../outside");
        Path files = collections.files(ClientWaypointSource.LOCAL).getWaypointFilesDir();
        assertEquals(profiles.directory().resolve("profiles").resolve(second.id().toString()), files);
        profiles.rename(second.id(), "Renamed");
        assertEquals("Renamed", profiles.active().name());
        assertEquals(files, collections.files(ClientWaypointSource.LOCAL).getWaypointFilesDir());
        profiles.delete(second.id());
        assertEquals(first, profiles.active().id());
        assertFalse(Files.exists(files));
        profiles.delete(first);
        assertEquals("Default", profiles.active().name());
        assertNotEquals(first, profiles.active().id());
        assertEquals(1, profiles.profiles().size());
        UUID replacement = profiles.active().id();
        profiles.leave();
        profiles.joinMultiplayer(account, "proxy");
        profiles.onBackendIdentity(10);
        assertEquals(replacement, profiles.active().id());
    }

    @Test
    void failedOutgoingSaveKeepsProfileAndRejectsSwitchTransferDeleteAndLeave() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID first = profiles.active().id();
        var second = profiles.create("Second");
        profiles.select(first);
        add(profiles, collections, "Base");
        Path dimension = collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld").getDimensionFile();
        Files.delete(dimension);
        Files.createDirectory(dimension);
        var selection = collections.selection(ClientWaypointSource.LOCAL, "minecraft:overworld", "Homes", "Base");
        assertThrows(IOException.class, () -> profiles.select(second.id()));
        assertThrows(IOException.class, () -> profiles.joinMultiplayer(account, "other"));
        assertThrows(IOException.class, () -> profiles.delete(first));
        assertThrows(IOException.class, profiles::leave);
        assertEquals(first, profiles.active().id());
        assertTrue(collections.valid(selection));
        Files.delete(dimension);
        profiles.select(second.id());
        assertFalse(collections.valid(selection));
    }

    @Test
    void transferWithoutDisconnectFlushesBeforeBindingAndInvalidatesQueuedSelection() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        profiles.onBackendIdentity(1);
        UUID first = profiles.active().id();
        var selection = collections.selection(ClientWaypointSource.LOCAL, "minecraft:overworld", "Homes", null);
        // Existing mutation machinery can be flushed at lifecycle boundaries too.
        collections.files(ClientWaypointSource.LOCAL).addWaypoint("minecraft:overworld", "Homes", waypoint("Outgoing"), ignored -> {});
        profiles.onBackendIdentity(2);
        assertNotEquals(first, profiles.active().id());
        assertThrows(IllegalStateException.class, () -> profiles.mutate(selection, files -> files.addWaypoint(
                "minecraft:overworld", "Homes", waypoint("Late"), ignored -> {})));
        profiles.onBackendIdentity(1);
        assertNotNull(collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("Homes").getWaypointByName("Outgoing"));
    }

    @Test
    void malformedIndexAndWaypointFileFailClosedWithoutOverwritingThem() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        add(profiles, collections, "Saved");
        Path directory = profiles.directory();
        Path file = collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld").getDimensionFile();
        profiles.leave();
        Files.writeString(directory.resolve("index.json"), "{broken");
        assertThrows(IOException.class, () -> profiles.joinMultiplayer(account, "proxy"));
        assertFalse(collections.ready(ClientWaypointSource.LOCAL));
        assertEquals("{broken", Files.readString(directory.resolve("index.json")));
        Files.delete(directory.resolve("index.json"));
        // An absent index beside existing profiles is corruption, not a fresh installation.
        assertThrows(IOException.class, () -> profiles.joinMultiplayer(account, "proxy"));
        assertTrue(Files.exists(file));
    }

    @Test
    void corruptDimensionNeverBecomesAnEmptyWritableProfile() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        add(profiles, collections, "Saved");
        Path file = collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld").getDimensionFile();
        profiles.leave();
        Files.writeString(file, "null");
        assertThrows(IOException.class, () -> profiles.joinMultiplayer(account, "proxy"));
        assertFalse(collections.ready(ClientWaypointSource.LOCAL));
        assertEquals("null", Files.readString(file));
    }

    @Test
    void leaveWaitsForMutationAndDelayedWorkCannotResurrectClosedStore() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        var selection = collections.selection(ClientWaypointSource.LOCAL, "minecraft:overworld", "Homes", null);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var mutation = executor.submit(() -> profiles.mutate(selection, files -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
                return files.addWaypoint("minecraft:overworld", "Homes", waypoint("Saved"), ignored -> {});
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var leave = executor.submit(() -> { profiles.leave(); return null; });
            assertThrows(TimeoutException.class, () -> leave.get(50, TimeUnit.MILLISECONDS));
            release.countDown();
            mutation.get(5, TimeUnit.SECONDS);
            leave.get(5, TimeUnit.SECONDS);
            assertFalse(collections.ready(ClientWaypointSource.LOCAL));
            assertThrows(IllegalStateException.class, () -> profiles.mutate(selection, files -> null));
            profiles.joinMultiplayer(account, "proxy");
            assertNotNull(collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld")
                    .getWaypointListByName("Homes").getWaypointByName("Saved"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void detachedOutgoingStoreCannotBeReactivatedByLateIdentityOrProfileControls() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "old-server");
        UUID old = profiles.active().id();
        collections.beginConnection(UUID.randomUUID(), "new-server");
        assertThrows(IllegalStateException.class, () -> profiles.onBackendIdentity(99));
        assertThrows(IllegalStateException.class, () -> profiles.select(old));
        assertThrows(IllegalStateException.class, () -> profiles.create("Wrong world"));
        assertFalse(collections.ready(ClientWaypointSource.LOCAL));
        profiles.joinMultiplayer(account, "new-server");
        assertNotEquals(old, profiles.active().id());
    }

    @Test
    void failedIndexWriteRetainsSelectionAndMetadata() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID first = profiles.active().id();
        var second = profiles.create("Second");
        profiles.select(first);
        Path index = profiles.directory().resolve("index.json");
        Files.delete(index);
        Files.createDirectory(index);
        assertThrows(IOException.class, () -> profiles.select(second.id()));
        assertThrows(IOException.class, () -> profiles.rename(first, "Lost rename"));
        assertEquals(first, profiles.active().id());
        assertEquals("Default", profiles.active().name());
        assertEquals(2, profiles.profiles().size());
    }

    @Test
    void invalidBindingsAndMissingProfileDirectoriesFailClosed() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID id = profiles.active().id();
        profiles.onBackendIdentity(1);
        Path directory = profiles.directory();
        profiles.leave();
        Path index = directory.resolve("index.json");
        String valid = Files.readString(index);
        String unknown = UUID.randomUUID().toString();
        Files.writeString(index, valid.replace("\"1\": \"" + id + "\"", "\"1\": \"" + unknown + "\""));
        assertThrows(IOException.class, () -> profiles.joinMultiplayer(account, "proxy"));
        Files.writeString(index, valid);
        Files.delete(directory.resolve("profiles").resolve(id.toString()));
        assertThrows(IOException.class, () -> profiles.joinMultiplayer(account, "proxy"));
        assertEquals(valid, Files.readString(index));
    }

    @Test
    void mutationSaveFailureAndBackendSaveFailureNeverClaimDurableSuccess() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        profiles.onBackendIdentity(1);
        UUID first = profiles.active().id();
        add(profiles, collections, "Before");
        Path file = collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld").getDimensionFile();
        Files.delete(file);
        Files.createDirectory(file);
        assertThrows(IOException.class, () -> add(profiles, collections, "Retry me"));
        assertThrows(IOException.class, () -> profiles.onBackendIdentity(2));
        assertEquals(first, profiles.active().id());
        Files.delete(file);
        profiles.onBackendIdentity(2);
        profiles.onBackendIdentity(1);
        assertNotNull(collections.files(ClientWaypointSource.LOCAL).getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("Homes").getWaypointByName("Retry me"));
    }

    @Test
    void deletingAnInactiveProfileDoesNotSuppressAutomaticBackendSelection() throws Exception {
        var collections = collections();
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        UUID initial = profiles.active().id();
        var unused = profiles.create("Unused");
        profiles.select(initial);
        profiles.leave();
        profiles.joinMultiplayer(account, "proxy");
        profiles.onBackendIdentity(1);
        profiles.delete(unused.id());
        profiles.onBackendIdentity(2);
        assertNotEquals(initial, profiles.active().id());
    }

    @Test
    void localLifecycleAndMutationsNeverWriteToSharedOrPrivateFiles() throws Exception {
        var collections = collections();
        collections.beginConnection(account, "proxy");
        collections.bindServer(root, 1);
        long generation = collections.generation();
        collections.files(ClientWaypointSource.SHARED).putWaypointList("minecraft:overworld", WaypointList.build("Homes", 5));
        collections.apply(new _959.server_waypoint.core.network.message.WaypointCollectionStateMessage(
                _959.server_waypoint.core.collection.WaypointScope.PRIVATE, true), generation);
        collections.apply(_959.server_waypoint.core.network.data.WaypointData.world(
                _959.server_waypoint.core.collection.WaypointScope.PRIVATE,
                java.util.List.of(new _959.server_waypoint.core.network.data.DimensionWaypointData("minecraft:overworld",
                        java.util.List.of(WaypointList.build("Homes", 6))))), generation);
        var privateFiles = collections.files(ClientWaypointSource.PRIVATE);
        var profiles = new LocalWaypointProfiles(root, collections);
        profiles.joinMultiplayer(account, "proxy");
        add(profiles, collections, "Local");
        profiles.create("Other");
        profiles.leave();
        assertSame(privateFiles, collections.files(ClientWaypointSource.PRIVATE));
        assertTrue(collections.ready(ClientWaypointSource.PRIVATE));
        for (var source : java.util.List.of(ClientWaypointSource.SHARED, ClientWaypointSource.PRIVATE)) {
            var list = collections.files(source).getWaypointFileManager("minecraft:overworld").getWaypointListByName("Homes");
            assertEquals(source == ClientWaypointSource.SHARED ? 5 : 6, list.getSyncNum());
            assertTrue(list.simpleWaypoints().isEmpty());
        }
    }

    private void add(LocalWaypointProfiles profiles, ClientWaypointCollections collections, String name) throws IOException {
        profiles.mutate(collections.selection(ClientWaypointSource.LOCAL, "minecraft:overworld", "Homes", null),
                files -> files.addWaypoint("minecraft:overworld", "Homes", waypoint(name), ignored -> {}));
    }

    private static SimpleWaypoint waypoint(String name) {
        return new SimpleWaypoint(name, "B", new WaypointPos(1, 64, -2), 0xffffff, 0, false);
    }
}
