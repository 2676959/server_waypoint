package _959.server_waypoint.core.network.upload;

import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.UploadChunkBuffer;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadCoordinatorTest {
    @TempDir
    private Path tempDir;

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
        );
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

        coordinator.onUploadChunk(
                "player",
                new UploadChunkBuffer(
                        request.requestId(),
                        0,
                        true,
                        UploadStatus.SUCCESS,
                        List.of(new UploadedWaypointListChunk(
                                "minecraft:overworld",
                                "",
                                List.of(clientWaypoint)
                        ))
                )
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
        );

        coordinator.onUploadChunk(
                "player",
                new UploadChunkBuffer(
                        request.requestId(),
                        0,
                        true,
                        UploadStatus.SUCCESS,
                        List.of(new UploadedWaypointListChunk(
                                "minecraft:overworld",
                                "list",
                                List.of(new SimpleWaypoint(
                                        "bad",
                                        "B",
                                        new WaypointPos(0, 64, 0),
                                        0x1000000,
                                        0,
                                        false
                                ))
                        ))
                )
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
        );

        coordinator.onDisconnect("player");
        coordinator.onUploadChunk(
                "player",
                new UploadChunkBuffer(
                        request.requestId(),
                        0,
                        true,
                        UploadStatus.SUCCESS,
                        List.of(new UploadedWaypointListChunk(
                                "minecraft:overworld",
                                "list",
                                List.of(waypoint("ignored", 0))
                        ))
                )
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
        );
        server.addWaypoint(
                "minecraft:overworld",
                "list",
                waypoint("late", 1),
                ignored -> {
                }
        );

        coordinator.onUploadChunk(
                "player",
                new UploadChunkBuffer(
                        request.requestId(),
                        0,
                        true,
                        UploadStatus.SUCCESS,
                        List.of(new UploadedWaypointListChunk(
                                "minecraft:overworld",
                                "list",
                                List.of(waypoint("base", 0))
                        ))
                )
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
        );

        coordinator.onUploadChunk(
                "player",
                new UploadChunkBuffer(
                        request.requestId(),
                        0,
                        true,
                        UploadStatus.SUCCESS,
                        List.of(new UploadedWaypointListChunk(
                                "minecraft:overworld",
                                "list",
                                List.of(waypoint("target", 25))
                        ))
                )
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

    private static UploadCoordinator<String> coordinator(WaypointServerCore server) {
        return coordinator(server, navigationService());
    }

    private static UploadCoordinator<String> coordinator(
            WaypointServerCore server,
            NavigationService<String> navigationService
    ) {
        return new UploadCoordinator<>(
                server,
                (player, message) -> {
                },
                packet -> {
                },
                player -> true,
                player -> true,
                navigationService
        );
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
