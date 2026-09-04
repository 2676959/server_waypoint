package _959.server_waypoint.live;

import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.common.client.ClientSynchronizationTracker;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.network.buffer.ServerHandshakeBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.live.mixin.ChunkedMessageManagerAccessor;
import _959.server_waypoint.live.mixin.ClientSynchronizationTrackerAccessor;
import _959.server_waypoint.live.mixin.OptimizedWaypointRendererAccessor;
import _959.server_waypoint.live.mixin.WaypointClientModAccessor;
import _959.server_waypoint.live.mixin.WaypointFilesManagerAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

/** Development-only lifecycle assertions for the disposable Velocity topology. */
public final class ProxyLifecycleTestControl implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_proxy_lifecycle_test");
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String A_LIST = "server-a-only";
    private static final String B_LIST = "server-b-only";
    private static final String A_FIXTURE = "a-fixture";
    private static final String B_FIXTURE = "b-fixture";
    private static final String A_MARKER = "pending-a-marker";
    private static final int A_REVISION = 101;
    private static final int B_REVISION = 202;
    private static final int SERVER_A_ID = Integer.getInteger(
            "serverWaypointLifecycle.serverAId",
            41001
    );
    private static final int SERVER_B_ID = Integer.getInteger(
            "serverWaypointLifecycle.serverBId",
            42002
    );
    private static final String SCENARIO = System.getProperty(
            "serverWaypointLifecycle.scenario",
            "unspecified"
    );

    private static TransferAudit transferAudit;
    private static boolean duplicateLeave;
    private static int connectionCount;
    private static int worldSynchronizationCount;
    private static Path aCacheDirectory;
    private static Path bCacheDirectory;
    private static int markedAMapIdentity;
    private static boolean markerCreated;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerCommands(dispatcher));
        LOGGER.info(
                "SW_LIFECYCLE event=control_initialized scenario={} serverAId={} serverBId={}",
                SCENARIO,
                SERVER_A_ID,
                SERVER_B_ID
        );
    }

    @SuppressWarnings("unchecked")
    private static <S> void registerCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder<S>) literal("swlifecycle")
                .then(literal("mark-a").executes(context -> markA()))
                .then(literal("assert-a").executes(context -> assertRole(Role.A)))
                .then(literal("assert-b").executes(context -> assertRole(Role.B)))
                .then(literal("snapshot").executes(context -> snapshot())));
    }

    public static void beforeLeave(WaypointClientMod client) {
        if (transferAudit != null
                && WaypointClientMod.getNetworkState() == WaypointClientMod.ClientNetworkState.NOT_READY) {
            duplicateLeave = true;
            LOGGER.info("SW_LIFECYCLE event=leave phase=before duplicate=true scenario={}", SCENARIO);
            return;
        }
        duplicateLeave = false;
        Role source = roleFor(client.getWaypointFilesDir());
        transferAudit = new TransferAudit(source, identityOfManagerMap(client));
        LOGGER.info(
                "SW_LIFECYCLE event=leave phase=before duplicate=false scenario={} source={} cache={} mapIdentity={} contents={}",
                SCENARIO,
                source,
                path(client.getWaypointFilesDir()),
                transferAudit.oldMapIdentity,
                describeContents(client)
        );
    }

    public static void afterLeave(WaypointClientMod client) {
        if (duplicateLeave) {
            LOGGER.info("SW_LIFECYCLE event=leave phase=after duplicate=true scenario={}", SCENARIO);
            duplicateLeave = false;
            return;
        }
        require(transferAudit != null, "leave completed without an active transfer audit");
        require(
                WaypointClientMod.getNetworkState() == WaypointClientMod.ClientNetworkState.NOT_READY,
                "leave did not reset the client network state"
        );
        assertTransportCleared(client);
        assertSynchronizationCleared(client);
        require(
                OptimizedWaypointRendererAccessor.sw$getTrackedWaypointRefs().isEmpty(),
                "leave retained rendered waypoint references"
        );
        require(
                OptimizedWaypointRendererAccessor.sw$getNextRenderId() == 0,
                "leave did not reset the renderer id generation"
        );
        if (markerCreated
                && transferAudit.source == Role.A
                && samePath(client.getWaypointFilesDir(), aCacheDirectory)) {
            assertCacheFile(aCacheDirectory, Role.A, true);
        }
        transferAudit.leavePassed = true;
        LOGGER.info(
                "SW_LIFECYCLE event=leave phase=after result=PASS scenario={} source={} transportPeers=0 syncUncertain=false rendererTracked=0 rendererQueue={} cacheSha256={}",
                SCENARIO,
                transferAudit.source,
                OptimizedWaypointRendererAccessor.sw$getQueue().size(),
                checksum(cacheFile(client.getWaypointFilesDir()))
        );
    }

    public static void beforeJoin(WaypointClientMod client) {
        if (transferAudit != null) {
            require(transferAudit.leavePassed, "join began before leave cleanup passed");
        }
        LOGGER.info(
                "SW_LIFECYCLE event=join phase=before scenario={} cache={} mapIdentity={} contents={}",
                SCENARIO,
                path(client.getWaypointFilesDir()),
                identityOfManagerMap(client),
                describeContents(client)
        );
    }

    public static void afterJoin(WaypointClientMod client) {
        require(client.getWaypointFilesDir() == null, "remote join retained a cache directory before handshake");
        require(managerMap(client).isEmpty(), "remote join retained waypoint managers before handshake");
        if (transferAudit != null) {
            require(
                    identityOfManagerMap(client) != transferAudit.oldMapIdentity,
                    "remote join retained the old manager generation"
            );
            transferAudit.joinPassed = true;
        }
        require(
                WaypointClientMod.getNetworkState()
                        == WaypointClientMod.ClientNetworkState.NO_SERVERSIDE_SUPPORT,
                "remote join did not enter handshake-waiting state"
        );
        LOGGER.info(
                "SW_LIFECYCLE event=join phase=after result=PASS scenario={} state={} cache=null managers=0 mapIdentity={} oldManagerDetached={}",
                SCENARIO,
                WaypointClientMod.getNetworkState(),
                identityOfManagerMap(client),
                transferAudit == null || identityOfManagerMap(client) != transferAudit.oldMapIdentity
        );
    }

    public static void afterDimensionChange(String dimensionName) {
        if (transferAudit != null && !transferAudit.handshakeStarted) {
            transferAudit.dimensionPassed = true;
        }
        LOGGER.info(
                "SW_LIFECYCLE event=dimension_change phase=after result=PASS scenario={} dimension={} state={} beforeHandshake={}",
                SCENARIO,
                dimensionName,
                WaypointClientMod.getNetworkState(),
                transferAudit != null && !transferAudit.handshakeStarted
        );
    }

    public static void beforeHandshake(WaypointClientMod client, ServerHandshakeBuffer handshake) {
        Role target = roleFor(handshake.serverId());
        require(target != Role.UNKNOWN, "handshake used an unexpected server id " + handshake.serverId());
        require(handshake.version() == ProtocolVersion.PROTOCOL_VERSION, "handshake did not negotiate protocol 9");
        require(client.getWaypointFilesDir() == null, "handshake began with a cache already bound");
        require(managerMap(client).isEmpty(), "handshake began with a waypoint manager already bound");
        if (transferAudit != null) {
            require(transferAudit.leavePassed, "handshake began without leave cleanup");
            require(transferAudit.joinPassed, "handshake began without a clean join reset");
            require(transferAudit.dimensionPassed, "no immediate dimension transition completed before handshake");
            require(
                    identityOfManagerMap(client) != transferAudit.oldMapIdentity,
                    "old manager remained bound while the handshake was pending"
            );
            transferAudit.target = target;
            transferAudit.handshakeStarted = true;
        }
        LOGGER.info(
                "SW_LIFECYCLE event=handshake phase=before result=PASS scenario={} target={} protocol={} serverId={} state={} cache=null managers=0 oldManagerDetached={}",
                SCENARIO,
                target,
                handshake.version(),
                handshake.serverId(),
                WaypointClientMod.getNetworkState(),
                transferAudit == null || identityOfManagerMap(client) != transferAudit.oldMapIdentity
        );
    }

    public static void afterHandshake(WaypointClientMod client, ServerHandshakeBuffer handshake) {
        Role target = roleFor(handshake.serverId());
        Path cacheDirectory = client.getWaypointFilesDir();
        require(target == roleFor(cacheDirectory), "handshake bound the wrong cache directory");
        require(
                WaypointClientMod.getNetworkState()
                        == WaypointClientMod.ClientNetworkState.HANDSHAKE_FINISHED,
                "handshake did not enter synchronization-waiting state"
        );
        rememberCache(target, cacheDirectory);
        if (transferAudit != null) {
            transferAudit.handshakePassed = true;
        }
        LOGGER.info(
                "SW_LIFECYCLE event=handshake phase=after result=PASS scenario={} target={} protocol={} serverId={} state={} cache={} cacheSha256={} contents={}",
                SCENARIO,
                target,
                handshake.version(),
                handshake.serverId(),
                WaypointClientMod.getNetworkState(),
                path(cacheDirectory),
                checksum(cacheFile(cacheDirectory)),
                describeContents(client)
        );
    }

    public static void afterSynchronization(WaypointClientMod client, String source) {
        connectionCount++;
        Role role = roleFor(client.getWaypointFilesDir());
        assertSynchronizedState(client, role);
        if (transferAudit != null) {
            require(transferAudit.target == role, "synchronization completed for the wrong target");
            require(transferAudit.handshakePassed, "synchronization completed before cache binding passed");
            LOGGER.info(
                    "SW_LIFECYCLE event=transfer_complete result=PASS scenario={} from={} to={} connection={} cache={} contents={} cacheSha256={}",
                    SCENARIO,
                    transferAudit.source,
                    role,
                    connectionCount,
                    path(client.getWaypointFilesDir()),
                    describeContents(client),
                    checksum(cacheFile(client.getWaypointFilesDir()))
            );
            transferAudit = null;
        } else {
            LOGGER.info(
                    "SW_LIFECYCLE event=baseline_complete result=PASS scenario={} role={} source={} connection={} protocol={} cache={} contents={} cacheSha256={}",
                    SCENARIO,
                    role,
                    source,
                    connectionCount,
                    ProtocolVersion.PROTOCOL_VERSION,
                    path(client.getWaypointFilesDir()),
                    describeContents(client),
                    checksum(cacheFile(client.getWaypointFilesDir()))
            );
        }
    }

    public static void afterWorldSynchronization(WaypointClientMod client) {
        worldSynchronizationCount++;
        Role role = roleFor(client.getWaypointFilesDir());
        assertSynchronizedState(client, role);
        LOGGER.info(
                "SW_LIFECYCLE event=fixture_download result=PASS scenario={} role={} download={} cache={} contents={} cacheSha256={}",
                SCENARIO,
                role,
                worldSynchronizationCount,
                path(client.getWaypointFilesDir()),
                describeContents(client),
                checksum(cacheFile(client.getWaypointFilesDir()))
        );
    }

    private static int markA() {
        WaypointClientMod client = WaypointClientMod.getInstance();
        require(roleFor(client.getWaypointFilesDir()) == Role.A, "marker can only be created on backend A");
        require(
                WaypointClientMod.getNetworkState() == WaypointClientMod.ClientNetworkState.SYNC_FINISHED,
                "marker requires completed synchronization"
        );
        Path cacheDirectory = client.getWaypointFilesDir();
        assertCacheFile(cacheDirectory, Role.A, false);
        String beforeChecksum = checksum(cacheFile(cacheDirectory));
        Path expectedBCache = cacheDirectory.resolveSibling(String.valueOf(SERVER_B_ID));
        require(!Files.exists(expectedBCache), "backend B cache existed before the first B handshake");
        WaypointFileManager manager = Objects.requireNonNull(client.getWaypointFileManager(OVERWORLD));
        WaypointList list = Objects.requireNonNull(manager.getWaypointListByName(A_LIST));
        require(list.getSyncNum() == A_REVISION, "backend A revision changed before marker creation");
        require(list.getWaypointByName(A_MARKER) == null, "backend A marker already existed");
        client.addWaypointFromRemoteServer(
                OVERWORLD,
                A_LIST,
                A_LIST,
                new SimpleWaypoint(
                        A_MARKER,
                        "PA",
                        new WaypointPos(41, 82, -17),
                        0x33AA55,
                        135,
                        false
                ),
                A_REVISION
        );
        WaypointList updated = Objects.requireNonNull(
                Objects.requireNonNull(client.getWaypointFileManager(OVERWORLD))
                        .getWaypointListByName(A_LIST)
        );
        require(updated.getWaypointByName(A_MARKER) != null, "marker was not added in memory");
        require(
                beforeChecksum.equals(checksum(cacheFile(cacheDirectory))),
                "marker was written before the leave lifecycle"
        );
        aCacheDirectory = cacheDirectory;
        bCacheDirectory = expectedBCache;
        markedAMapIdentity = identityOfManagerMap(client);
        markerCreated = true;
        LOGGER.info(
                "SW_LIFECYCLE event=marker_created result=PASS scenario={} memory=true disk=false revision={} cache={} cacheSha256={} bCacheAbsent=true mapIdentity={}",
                SCENARIO,
                updated.getSyncNum(),
                path(cacheDirectory),
                beforeChecksum,
                markedAMapIdentity
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int assertRole(Role expected) {
        WaypointClientMod client = WaypointClientMod.getInstance();
        assertSynchronizedState(client, expected);
        if (markerCreated
                && expected == Role.A
                && samePath(client.getWaypointFilesDir(), aCacheDirectory)) {
            require(
                    identityOfManagerMap(client) != 0,
                    "backend A did not bind a manager generation"
            );
            require(
                    Objects.requireNonNull(
                            Objects.requireNonNull(client.getWaypointFileManager(OVERWORLD))
                                    .getWaypointListByName(A_LIST)
                    ).getWaypointByName(A_MARKER) != null,
                    "backend A marker was not preserved"
            );
        }
        LOGGER.info(
                "SW_LIFECYCLE event=assert_{} result=PASS scenario={} state={} cache={} mapIdentity={} contents={} cacheSha256={}",
                expected.name().toLowerCase(),
                SCENARIO,
                WaypointClientMod.getNetworkState(),
                path(client.getWaypointFilesDir()),
                identityOfManagerMap(client),
                describeContents(client),
                checksum(cacheFile(client.getWaypointFilesDir()))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int snapshot() {
        WaypointClientMod client = WaypointClientMod.getInstance();
        LOGGER.info(
                "SW_LIFECYCLE event=snapshot scenario={} state={} role={} cache={} mapIdentity={} contents={} cacheSha256={}",
                SCENARIO,
                WaypointClientMod.getNetworkState(),
                roleFor(client.getWaypointFilesDir()),
                path(client.getWaypointFilesDir()),
                identityOfManagerMap(client),
                describeContents(client),
                checksum(cacheFile(client.getWaypointFilesDir()))
        );
        return Command.SINGLE_SUCCESS;
    }

    private static void assertSynchronizedState(WaypointClientMod client, Role expected) {
        require(expected != Role.UNKNOWN, "synchronized cache used an unknown server id");
        require(roleFor(client.getWaypointFilesDir()) == expected, "client bound the wrong server cache");
        require(
                WaypointClientMod.getNetworkState() == WaypointClientMod.ClientNetworkState.SYNC_FINISHED,
                "client did not finish synchronization"
        );
        assertMemoryContents(client, expected);
        boolean requireMarker = markerCreated
                && expected == Role.A
                && samePath(client.getWaypointFilesDir(), aCacheDirectory);
        assertCacheFile(client.getWaypointFilesDir(), expected, requireMarker);
        if (aCacheDirectory != null && Files.exists(cacheFile(aCacheDirectory))) {
            assertCacheFile(aCacheDirectory, Role.A, markerCreated);
        }
        if (bCacheDirectory != null && Files.exists(cacheFile(bCacheDirectory))) {
            assertCacheFile(bCacheDirectory, Role.B, false);
        }
    }

    private static void assertMemoryContents(WaypointClientMod client, Role expected) {
        Map<String, WaypointFileManager> managers = managerMap(client);
        for (Map.Entry<String, WaypointFileManager> entry : managers.entrySet()) {
            if (!OVERWORLD.equals(entry.getKey())) {
                require(entry.getValue().getWaypointLists().isEmpty(), "unexpected non-empty dimension " + entry.getKey());
            }
        }
        WaypointFileManager overworld = managers.get(OVERWORLD);
        require(overworld != null, "missing overworld waypoint manager");
        List<WaypointList> lists = overworld.getWaypointLists();
        require(lists.size() == 1, "unexpected overworld waypoint-list count " + lists.size());
        WaypointList list = lists.get(0);
        String expectedList = expected == Role.A ? A_LIST : B_LIST;
        String expectedFixture = expected == Role.A ? A_FIXTURE : B_FIXTURE;
        int expectedRevision = expected == Role.A ? A_REVISION : B_REVISION;
        require(expectedList.equals(list.name()), "wrong waypoint list " + list.name());
        require(list.getSyncNum() == expectedRevision, "wrong list revision " + list.getSyncNum());
        Set<String> names = waypointNames(list);
        Set<String> expectedNames = new HashSet<>();
        expectedNames.add(expectedFixture);
        if (markerCreated
                && expected == Role.A
                && samePath(client.getWaypointFilesDir(), aCacheDirectory)) {
            expectedNames.add(A_MARKER);
        }
        require(names.equals(expectedNames), "wrong waypoint contents " + names);
        String forbiddenList = expected == Role.A ? B_LIST : A_LIST;
        String forbiddenFixture = expected == Role.A ? B_FIXTURE : A_FIXTURE;
        require(!describeContents(client).contains(forbiddenList), "cross-server list leak " + forbiddenList);
        require(!describeContents(client).contains(forbiddenFixture), "cross-server waypoint leak " + forbiddenFixture);
    }

    private static void assertCacheFile(Path cacheDirectory, Role role, boolean requireMarker) {
        Path file = cacheFile(cacheDirectory);
        require(file != null && Files.isRegularFile(file), "missing cache file for " + role);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray lists = JsonParser.parseReader(reader).getAsJsonArray();
            require(lists.size() == 1, "cache has an unexpected waypoint-list count");
            JsonObject list = lists.get(0).getAsJsonObject();
            String expectedList = role == Role.A ? A_LIST : B_LIST;
            int expectedRevision = role == Role.A ? A_REVISION : B_REVISION;
            require(expectedList.equals(list.get("list_name").getAsString()), "cache contains the wrong list");
            require(list.get("n").getAsInt() == expectedRevision, "cache contains the wrong revision");
            Set<String> names = new HashSet<>();
            for (JsonElement element : list.getAsJsonArray("waypoints")) {
                names.add(element.getAsJsonObject().get("name").getAsString());
            }
            Set<String> expectedNames = new HashSet<>();
            expectedNames.add(role == Role.A ? A_FIXTURE : B_FIXTURE);
            if (role == Role.A && requireMarker) {
                expectedNames.add(A_MARKER);
            }
            require(names.equals(expectedNames), "cache contains the wrong waypoints " + names);
        } catch (IOException exception) {
            throw fail("failed to inspect cache file " + file, exception);
        }
    }

    private static void assertTransportCleared(WaypointClientMod client) {
        WaypointClientModAccessor accessor = (WaypointClientModAccessor) client;
        assertManagerCleared(accessor.sw$getChunkedMessages(), "general");
        assertManagerCleared(accessor.sw$getUploadChunkedMessages(), "upload");
    }

    private static void assertManagerCleared(ChunkedMessageManager<?> manager, String name) {
        ChunkedMessageManagerAccessor accessor = (ChunkedMessageManagerAccessor) (Object) manager;
        require(accessor.sw$getPeers().isEmpty(), name + " transport retained peer state");
        require(accessor.sw$getScheduledPeers().isEmpty(), name + " transport retained scheduled peers");
        require(accessor.sw$getScheduledPeerSet().isEmpty(), name + " transport retained scheduler membership");
        require(accessor.sw$getGloballyRetainedBytes() == 0L, name + " transport retained bytes");
    }

    private static void assertSynchronizationCleared(WaypointClientMod client) {
        ClientSynchronizationTracker tracker = ((WaypointClientModAccessor) client)
                .sw$getSynchronizationTracker();
        require(!tracker.isSynchronizationUncertain(), "synchronization uncertainty survived leave");
        require(
                ((ClientSynchronizationTrackerAccessor) (Object) tracker)
                        .sw$getOutOfSyncLists()
                        .isEmpty(),
                "out-of-sync list state survived leave"
        );
    }

    private static Map<String, WaypointFileManager> managerMap(WaypointClientMod client) {
        return ((WaypointFilesManagerAccessor) client).sw$getFileManagerMap();
    }

    private static int identityOfManagerMap(WaypointClientMod client) {
        return System.identityHashCode(managerMap(client));
    }

    private static void rememberCache(Role role, Path cacheDirectory) {
        if (role == Role.A) {
            if (aCacheDirectory == null || !Files.exists(cacheFile(aCacheDirectory))) {
                aCacheDirectory = cacheDirectory;
            }
        } else if (role == Role.B) {
            bCacheDirectory = cacheDirectory;
        }
    }

    private static Role roleFor(Path cacheDirectory) {
        if (cacheDirectory == null || cacheDirectory.getFileName() == null) {
            return Role.UNKNOWN;
        }
        try {
            return roleFor(Integer.parseInt(cacheDirectory.getFileName().toString()));
        } catch (NumberFormatException ignored) {
            return Role.UNKNOWN;
        }
    }

    private static Role roleFor(int serverId) {
        if (serverId == SERVER_A_ID) {
            return Role.A;
        }
        if (serverId == SERVER_B_ID) {
            return Role.B;
        }
        return Role.UNKNOWN;
    }

    private static Set<String> waypointNames(WaypointList list) {
        Set<String> names = new HashSet<>();
        for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
            names.add(waypoint.name());
        }
        return names;
    }

    private static String describeContents(WaypointClientMod client) {
        List<String> dimensions = new ArrayList<>();
        for (Map.Entry<String, WaypointFileManager> entry : managerMap(client).entrySet()) {
            List<String> lists = new ArrayList<>();
            for (WaypointList list : entry.getValue().getWaypointLists()) {
                Set<String> names = new TreeSet<>(waypointNames(list));
                lists.add(list.name() + "@" + list.getSyncNum() + names);
            }
            Collections.sort(lists);
            dimensions.add(entry.getKey() + "=" + lists);
        }
        Collections.sort(dimensions);
        return dimensions.toString().replace(' ', '_');
    }

    private static Path cacheFile(Path cacheDirectory) {
        return cacheDirectory == null ? null : cacheDirectory.resolve("minecraft$overworld.json");
    }

    private static boolean samePath(Path first, Path second) {
        return first != null && second != null && first.toAbsolutePath().normalize()
                .equals(second.toAbsolutePath().normalize());
    }

    private static String path(Path value) {
        return value == null ? "null" : value.toAbsolutePath().normalize().toString();
    }

    private static String checksum(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return "absent";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw fail("failed to checksum " + file, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw fail(message, null);
        }
    }

    private static IllegalStateException fail(String message, Throwable cause) {
        LOGGER.error("SW_LIFECYCLE event=assertion_failure result=FAIL scenario={} reason={}", SCENARIO, message, cause);
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private enum Role {
        A,
        B,
        UNKNOWN
    }

    private static final class TransferAudit {
        private final Role source;
        private final int oldMapIdentity;
        private Role target = Role.UNKNOWN;
        private boolean leavePassed;
        private boolean dimensionPassed;
        private boolean joinPassed;
        private boolean handshakeStarted;
        private boolean handshakePassed;

        private TransferAudit(Role source, int oldMapIdentity) {
            this.source = source;
            this.oldMapIdentity = oldMapIdentity;
        }
    }
}
