package _959.server_waypoint.core.network.upload;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationMethodHandler;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadCoordinatorTest {
    @TempDir
    private Path tempDir;

    @Test
    void oneGlobalLeaseRejectsOtherClientsUntilReleased() {
        UploadCoordinator<String> coordinator = coordinator(server());
        UploadCoordinator.BeginResult first = coordinator.begin(
                "first",
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );

        UploadCoordinator.BeginResult second = coordinator.begin(
                "second",
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );

        assertEquals(UploadCoordinator.BeginStatus.STARTED, first.status());
        assertEquals(UploadCoordinator.BeginStatus.BUSY, second.status());
        assertTrue(!coordinator.tryBeginEditRequest());
        assertTrue(coordinator.acceptsUploadChunk("first", first.request().requestId()));
        assertTrue(!coordinator.acceptsUploadChunk("second", first.request().requestId()));

        coordinator.onDisconnect("first");
        UploadCoordinator.BeginResult afterRelease = coordinator.begin(
                "second",
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );
        assertEquals(UploadCoordinator.BeginStatus.STARTED, afterRelease.status());
        coordinator.onDisconnect("second");
    }

    @Test
    void pairingReturnsBusyWhileAnEditIsAlreadyAdmitted() {
        UploadCoordinator<String> coordinator = coordinator(server());
        assertTrue(coordinator.tryBeginEditRequest());

        UploadCoordinator.BeginResult blocked = coordinator.begin(
                "player",
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );
        assertEquals(UploadCoordinator.BeginStatus.BUSY, blocked.status());

        coordinator.finishEditRequest();
        UploadCoordinator.BeginResult started = coordinator.begin(
                "player",
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );
        assertEquals(UploadCoordinator.BeginStatus.STARTED, started.status());
        coordinator.onDisconnect("player");
    }

    @Test
    void leaseIsReservedWhileRevisionCaptureIsBlocked() throws Exception {
        CountDownLatch captureEntered = new CountDownLatch(1);
        CountDownLatch releaseCapture = new CountDownLatch(1);
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
            @Override
            public DimensionRevision captureDimensionRevision(String dimensionName) {
                captureEntered.countDown();
                await(releaseCapture);
                return super.captureDimensionRevision(dimensionName);
            }
        };
        UploadCoordinator<String> coordinator = coordinator(server);
        AtomicReference<UploadCoordinator.BeginResult> result = new AtomicReference<>();
        Thread beginThread = new Thread(() -> result.set(begin(coordinator, "first")));

        beginThread.start();
        assertTrue(captureEntered.await(5, TimeUnit.SECONDS));
        assertEquals(UploadCoordinator.BeginStatus.BUSY, begin(coordinator, "second").status());

        releaseCapture.countDown();
        beginThread.join(5_000L);
        assertEquals(UploadCoordinator.BeginStatus.STARTED, result.get().status());
        coordinator.onDisconnect("first");
    }

    @Test
    void revisionCaptureFailureReleasesExactReservation() {
        java.util.concurrent.atomic.AtomicBoolean failCapture =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
            @Override
            public DimensionRevision captureDimensionRevision(String dimensionName) {
                if (failCapture.getAndSet(false)) {
                    throw new IllegalStateException("capture failed");
                }
                return super.captureDimensionRevision(dimensionName);
            }
        };
        UploadCoordinator<String> coordinator = coordinator(server);

        assertThrows(IllegalStateException.class, () -> begin(coordinator, "first"));
        assertEquals(UploadCoordinator.BeginStatus.STARTED, begin(coordinator, "second").status());
        coordinator.onDisconnect("second");
    }

    @Test
    void cancellationRequiresExactPlayerAndRequestAndCannotCancelReplacement() {
        UploadCoordinator<String> coordinator = coordinator(server());
        UploadCoordinator.BeginResult first = begin(coordinator, "first");

        assertFalse(coordinator.cancel("second", first.request().requestId(), "wrong player"));
        assertFalse(coordinator.cancel("first", UUID.randomUUID(), "wrong request"));
        assertTrue(coordinator.acceptsUploadChunk("first", first.request().requestId()));
        assertTrue(coordinator.cancel("first", first.request().requestId(), "matching cancellation"));

        UploadCoordinator.BeginResult replacement = begin(coordinator, "second");
        assertEquals(UploadCoordinator.BeginStatus.STARTED, replacement.status());
        assertFalse(coordinator.cancel("first", first.request().requestId(), "stale cancellation"));
        assertTrue(coordinator.acceptsUploadChunk("second", replacement.request().requestId()));
        coordinator.onDisconnect("second");
    }

    @Test
    void expiryAndDisconnectCannotInterruptApplyingUpload() throws Exception {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        CountDownLatch applying = new CountDownLatch(1);
        CountDownLatch releaseApplication = new CountDownLatch(1);
        Predicate<String> permissionChecker = player -> {
            applying.countDown();
            await(releaseApplication);
            return true;
        };
        UploadCoordinator<String> coordinator = coordinator(
                server(),
                navigationService(),
                clock,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                permissionChecker
        );
        UploadCoordinator.BeginResult request = begin(coordinator, "first");
        Thread uploadThread = new Thread(() -> coordinator.onUpload(
                "first",
                WaypointData.upload(request.request().requestId(), UploadStatus.XAERO_NOT_READY, List.of())
        ));

        uploadThread.start();
        assertTrue(applying.await(5, TimeUnit.SECONDS));
        clock.advance(Duration.ofSeconds(31));
        coordinator.tick();
        coordinator.onDisconnect("first");
        assertEquals(UploadCoordinator.BeginStatus.BUSY, begin(coordinator, "second").status());

        releaseApplication.countDown();
        uploadThread.join(5_000L);
        assertEquals(UploadCoordinator.BeginStatus.STARTED, begin(coordinator, "second").status());
        coordinator.onDisconnect("second");
    }

    @Test
    void disconnectOnlyAffectsMatchingPlayer() {
        UploadCoordinator<String> coordinator = coordinator(server());
        UploadCoordinator.BeginResult request = begin(coordinator, "first");

        coordinator.onDisconnect("second");

        assertTrue(coordinator.acceptsUploadChunk("first", request.request().requestId()));
        coordinator.onDisconnect("first");
    }

    @Test
    void tickExpiresReceivingRequestAndReleasesLease() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        UploadCoordinator<String> coordinator = coordinator(
                server(),
                navigationService(),
                clock,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                player -> true
        );
        UploadCoordinator.BeginResult expired = begin(coordinator, "first");

        clock.advance(Duration.ofSeconds(30));
        assertEquals(Optional.of(expired.request().requestId()), coordinator.tick());

        assertFalse(coordinator.acceptsUploadChunk("first", expired.request().requestId()));
        assertEquals(UploadCoordinator.BeginStatus.STARTED, begin(coordinator, "second").status());
        coordinator.onDisconnect("second");
    }

    @Test
    void cooldownBlocksOnlyTerminatingPlayerAndExpires() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        UploadCoordinator<String> coordinator = coordinator(
                server(),
                navigationService(),
                clock,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                player -> true
        );
        UploadCoordinator.BeginResult first = begin(coordinator, "first");
        coordinator.onDisconnect("first");

        UploadCoordinator.BeginResult cooldown = begin(coordinator, "first");
        assertEquals(UploadCoordinator.BeginStatus.COOLDOWN, cooldown.status());
        assertEquals(Duration.ofSeconds(5), cooldown.cooldownRemaining());
        UploadCoordinator.BeginResult otherPlayer = begin(coordinator, "second");
        assertEquals(UploadCoordinator.BeginStatus.STARTED, otherPlayer.status());
        coordinator.onDisconnect("second");

        clock.advance(Duration.ofSeconds(5));
        coordinator.tick();
        assertEquals(UploadCoordinator.BeginStatus.STARTED, begin(coordinator, "first").status());
        coordinator.onDisconnect("first");
    }

    @Test
    void sessionResetClearsActiveRequestAndCooldowns() {
        UploadCoordinator<String> coordinator = coordinator(server());
        UploadCoordinator.BeginResult request = begin(coordinator, "first");
        coordinator.onDisconnect("first");
        assertEquals(UploadCoordinator.BeginStatus.COOLDOWN, begin(coordinator, "first").status());

        coordinator.resetSession();

        assertFalse(coordinator.acceptsUploadChunk("first", request.request().requestId()));
        assertEquals(UploadCoordinator.BeginStatus.STARTED, begin(coordinator, "first").status());
        coordinator.resetSession();
    }

    @Test
    void coordinatorDoesNotOwnAnExpiryExecutor() {
        assertTrue(Arrays.stream(UploadCoordinator.class.getDeclaredFields())
                .noneMatch(field -> ScheduledExecutorService.class.isAssignableFrom(field.getType())));
    }

    @Test
    void deleteMissingRequiresLocalConflictPolicyOnTheServer() {
        UploadCoordinator<String> coordinator = coordinator(server());

        assertThrows(IllegalArgumentException.class, () -> coordinator.begin(
                "player",
                UploadScope.DIMENSION,
                UploadConflictPolicy.SERVER,
                true,
                List.of("minecraft:overworld"),
                null,
                null
        ));
    }

    @Test
    void sameXaeroPropertiesIgnoreServerOnlyMetadata() {
        SimpleWaypoint server = new SimpleWaypoint(
                "home", "Main Home", "H", new WaypointPos(10, 64, -20), 0x55AAFF, 90, false,
                List.of("base", "safe"), "The main storage base"
        );
        SimpleWaypoint uploaded = new SimpleWaypoint(
                "home", "H", new WaypointPos(10, 64, -20), 0x55AAFF, 90, false
        );

        assertTrue(UploadCoordinator.hasSameXaeroProperties(server, uploaded));
    }

    @Test
    void mergeXaeroPropertiesPreservesServerOnlyMetadata() {
        SimpleWaypoint server = new SimpleWaypoint(
                "home", "Main Home", "H", new WaypointPos(10, 64, -20), 0x55AAFF, 90, false,
                List.of("base", "safe"), "The main storage base"
        );
        SimpleWaypoint uploaded = new SimpleWaypoint(
                "home", "MH", new WaypointPos(42, 70, 7), 0xFFAA00, -45, true
        );

        SimpleWaypoint merged = UploadCoordinator.mergeXaeroProperties(server, uploaded);

        assertEquals(uploaded.name(), merged.name());
        assertEquals(uploaded.initials(), merged.initials());
        assertEquals(uploaded.pos(), merged.pos());
        assertEquals(uploaded.rgb(), merged.rgb());
        assertEquals(uploaded.yaw(), merged.yaw());
        assertEquals(uploaded.global(), merged.global());
        assertEquals(server.displayName(), merged.displayName());
        assertEquals(server.keywords(), merged.keywords());
        assertEquals(server.description(), merged.description());
    }

    @Test
    void newWaypointAcceptsEmptyIdentifiersAndDropsServerOnlyClientFields() {
        WaypointServerCore server = server();
        UploadCoordinator<String> coordinator = coordinator(server);
        UploadRequestBuffer request = coordinator.begin(
                "player",
                UploadScope.WAYPOINT,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                "",
                ""
        ).request();
        SimpleWaypoint clientWaypoint = new SimpleWaypoint(
                "",
                "Injected display name",
                "",
                new WaypointPos(1, 64, 2),
                0x123456,
                90,
                false,
                List.of("injected"),
                "Injected description"
        );

        coordinator.onUpload(
                "player",
                uploadData(request, "minecraft:overworld", "", List.of(clientWaypoint))
        );

        WaypointFileManager fileManager = server.getWaypointFileManager("minecraft:overworld");
        assertNotNull(fileManager);
        WaypointList list = fileManager.getWaypointListByName("");
        assertNotNull(list);
        SimpleWaypoint stored = list.getWaypointByName("");
        assertNotNull(stored);
        assertEquals("", stored.displayName());
        assertEquals(List.of(), stored.keywords());
        assertEquals("", stored.description());
    }

    @Test
    void invalidClientColorIsNotAdded() {
        WaypointServerCore server = server();
        UploadCoordinator<String> coordinator = coordinator(server);
        UploadRequestBuffer request = coordinator.begin(
                "player",
                UploadScope.LIST,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                "list",
                null
        ).request();

        coordinator.onUpload(
                "player",
                uploadData(request, "minecraft:overworld", "list", List.of(new SimpleWaypoint(
                        "bad",
                        "B",
                        new WaypointPos(0, 64, 0),
                        0x1000000,
                        0,
                        false
                )))
        );

        WaypointFileManager fileManager = server.getWaypointFileManager("minecraft:overworld");
        assertNotNull(fileManager);
        assertNull(fileManager.getWaypointListByName("list"));
    }

    @Test
    void disconnectDiscardsPendingUpload() {
        WaypointServerCore server = server();
        UploadCoordinator<String> coordinator = coordinator(server);
        UploadRequestBuffer request = coordinator.begin(
                "player",
                UploadScope.LIST,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                "list",
                null
        ).request();

        coordinator.onDisconnect("player");
        coordinator.onUpload(
                "player",
                uploadData(request, "minecraft:overworld", "list", List.of(waypoint("ignored", 0)))
        );

        assertNull(server.getWaypointFileManager("minecraft:overworld"));
    }

    @Test
    void destructiveUploadRejectsDimensionChangedAfterRequestBegan() {
        WaypointServerCore server = server();
        server.addWaypoint(
                "minecraft:overworld",
                "list",
                waypoint("base", 0),
                ignored -> {
                }
        );
        UploadCoordinator<String> coordinator = coordinator(server);
        UploadRequestBuffer request = coordinator.begin(
                "player",
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                true,
                List.of("minecraft:overworld"),
                null,
                null
        ).request();
        server.addWaypoint(
                "minecraft:overworld",
                "list",
                waypoint("late", 1),
                ignored -> {
                }
        );

        coordinator.onUpload(
                "player",
                uploadData(request, "minecraft:overworld", "list", List.of(waypoint("base", 0)))
        );

        WaypointList list = server.getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("list");
        assertNotNull(list);
        assertNotNull(list.getWaypointByName("base"));
        assertNotNull(list.getWaypointByName("late"));
    }

    @Test
    void forceLocalReplacementRefreshesActiveNavigationTarget() {
        WaypointServerCore server = server();
        SimpleWaypoint original = waypoint("target", 0);
        server.addWaypoint(
                "minecraft:overworld",
                "list",
                original,
                ignored -> {
                }
        );
        NavigationService<String> navigationService = activeNavigationService();
        WaypointList originalList = server.getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("list");
        navigationService.navigate(
                "player",
                new NavigationTarget("minecraft:overworld", originalList, original)
        );
        UploadCoordinator<String> coordinator = coordinator(server, navigationService);
        UploadRequestBuffer request = coordinator.begin(
                "player",
                UploadScope.WAYPOINT,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                "list",
                "target"
        ).request();

        coordinator.onUpload(
                "player",
                uploadData(request, "minecraft:overworld", "list", List.of(waypoint("target", 25)))
        );

        NavigationTarget refreshed = navigationService.findSession(playerUuid())
                .orElseThrow()
                .target();
        assertEquals(new WaypointPos(25, 64, 0), refreshed.position());
    }

    private WaypointServerCore server() {
        return new WaypointServerCore(this.tempDir) {
        };
    }

    private static SimpleWaypoint waypoint(String name, int x) {
        return new SimpleWaypoint(name, name, new WaypointPos(x, 64, 0), 0x00FF00, 0, false);
    }

    private static WaypointData uploadData(
            UploadRequestBuffer request,
            String dimensionName,
            String listName,
            List<SimpleWaypoint> waypoints
    ) {
        return WaypointData.upload(
                request.requestId(),
                UploadStatus.SUCCESS,
                List.of(new DimensionWaypointData(
                        dimensionName,
                        List.of(new WaypointList(listName, WaypointList.SERVER_N, waypoints))
                ))
        );
    }

    private static UploadCoordinator<String> coordinator(WaypointServerCore server) {
        return coordinator(server, navigationService());
    }

    private static UploadCoordinator<String> coordinator(
            WaypointServerCore server,
            NavigationService<String> navigationService
    ) {
        return coordinator(
                server,
                navigationService,
                Clock.systemUTC(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                player -> true
        );
    }

    private static UploadCoordinator<String> coordinator(
            WaypointServerCore server,
            NavigationService<String> navigationService,
            Clock clock,
            Duration requestTimeout,
            Duration cooldown,
            Predicate<String> permissionChecker
    ) {
        return new UploadCoordinator<>(
                server,
                (player, message) -> {
                },
                packet -> {
                },
                permissionChecker,
                player -> true,
                navigationService,
                player -> UUID.nameUUIDFromBytes(player.getBytes(StandardCharsets.UTF_8)),
                clock,
                requestTimeout,
                cooldown
        );
    }

    private static UploadCoordinator.BeginResult begin(
            UploadCoordinator<String> coordinator,
            String player
    ) {
        return coordinator.begin(
                player,
                UploadScope.DIMENSION,
                UploadConflictPolicy.LOCAL,
                false,
                List.of("minecraft:overworld"),
                null,
                null
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }
    }

    private static NavigationService<String> navigationService() {
        return new NavigationService<>(new NavigationPlatform<>() {
            @Override
            public UUID playerUuid(String player) {
                return UploadCoordinatorTest.playerUuid();
            }

            @Override
            public void executePlayer(UUID playerUuid, Consumer<String> action) {
            }

            @Override
            public NavigationSnapshot snapshot(String player, NavigationTarget target) {
                return NavigationSnapshot.wrongDimension();
            }
        }, List.of());
    }

    private static NavigationService<String> activeNavigationService() {
        NavigationPlatform<String> platform = new NavigationPlatform<>() {
            @Override
            public UUID playerUuid(String player) {
                return UploadCoordinatorTest.playerUuid();
            }

            @Override
            public void executePlayer(UUID playerUuid, Consumer<String> action) {
                action.accept("player");
            }

            @Override
            public NavigationSnapshot snapshot(String player, NavigationTarget target) {
                return NavigationSnapshot.wrongDimension();
            }
        };
        NavigationMethodHandler<String> handler = new NavigationMethodHandler<>() {
            @Override
            public NavigationMethod method() {
                return NavigationMethod.ACTIONBAR;
            }

            @Override
            public NavigationResult enable(
                    String player,
                    NavigationSession session,
                    NavigationSnapshot snapshot
            ) {
                return NavigationResult.success();
            }

            @Override
            public void update(
                    String player,
                    NavigationSession session,
                    NavigationSnapshot snapshot
            ) {
            }

            @Override
            public void disable(String player, NavigationSession session) {
            }
        };
        return new NavigationService<>(
                platform,
                List.of(handler),
                Set.of(NavigationMethod.ACTIONBAR)
        );
    }

    private static UUID playerUuid() {
        return UUID.nameUUIDFromBytes("player".getBytes(StandardCharsets.UTF_8));
    }
}
