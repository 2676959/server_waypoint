package _959.server_waypoint.navigation;

import _959.server_waypoint.core.collection.WaypointCollectionKey;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import _959.server_waypoint.util.ColorUtils;
import _959.server_waypoint.util.NamespacedId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AbstractWorldMarkerNavigationHandlerTest {
    @Test
    void createsSeparateInitialsBadgeAndDistanceRows() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid);
        handler.enable(player, session, snapshot(player, session));
        assertEquals(2, handler.created.size());
        assertEquals(2, handler.spawned);
        assertEquals(2, handler.dataPackets);
        WorldMarkerPosition center = WorldMarkerPosition.project(player.eye, session.target().position(), session.worldMarkerDistance());
        TestDisplay badge = handler.created.get(0);
        TestDisplay distance = handler.created.get(1);
        assertEquals("badge", badge.part);
        assertEquals("distance", distance.part);
        assertEquals("V", ((TextComponent) badge.text).content());
        assertEquals(TextColor.color(ColorUtils.getSafeTextColor(session.target().rgb())), badge.text.color());
        assertEquals(0x80000000 | session.target().rgb(), badge.background);
        assertEquals("waypoint.navigation.unit.meter", ((TranslatableComponent) distance.text).key());
        assertEquals(center, badge.position);
        assertEquals(center, distance.position);
        assertTrue(badge.positionTicks.isEmpty());
    }

    @Test
    void resizingUpdatesAllRowsOnceWithoutRespawning() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid, new WaypointPos(1, 2, 3),
                new NamespacedId("minecraft", "diamond"));
        NavigationSnapshot snapshot = snapshot(player, session);
        handler.enable(player, session, snapshot);
        assertEquals(List.of(1.0F, 1.0F, 1.0F), handler.created.stream().map(display -> display.size).toList());

        NavigationSession resized = session.withWorldMarkerSize(2.0F);
        handler.update(player, resized, snapshot);
        handler.update(player, resized, snapshot);

        assertEquals(List.of(2.0F, 2.0F, 2.0F), handler.created.stream().map(display -> display.size).toList());
        assertEquals(3, handler.spawned);
        assertEquals(0, handler.removed);
        assertEquals(6, handler.dataPackets);
    }

    @Test
    void changingProjectionDistanceMovesExistingRowsAndKeepsNearbyMarkerAtItsTarget() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NamespacedId icon = NamespacedId.parse("minecraft:diamond");
        NavigationSession distant = session(player.uuid, new WaypointPos(100, 64, 0), icon);
        NavigationSnapshot snapshot = snapshot(player, distant);
        handler.enable(player, distant, snapshot);
        assertEquals(12.0D, handler.created.get(0).position.distanceTo(player.eye), 0.000001D);

        player.tick++;
        handler.update(player, distant.withWorldMarkerDistance(8.5F), snapshot);
        for (TestDisplay display : handler.created) {
            assertEquals(8.5D, display.position.distanceTo(player.eye), 0.000001D);
        }
        assertEquals(3, handler.dataPackets);

        player.tick++;
        NavigationSession nearby = session(player.uuid, new WaypointPos(3, 64, 0), icon)
                .withWorldMarkerDistance(8.5F);
        handler.update(player, nearby, snapshot(player, nearby));
        for (TestDisplay display : handler.created) {
            assertEquals(new WorldMarkerPosition(3.5D, 64.5D, 0.5D), display.position);
        }
        assertEquals(3, handler.spawned);
        assertEquals(0, handler.removed);
    }

    @Test
    void markerUpdatesEveryTickWithoutDuplicateSameTickPackets() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid);
        NavigationSnapshot snapshot = snapshot(player, session);
        handler.enable(player, session, snapshot);
        for (int tick = 1; tick <= 30; tick++) {
            player.tick = tick;
            player.eye = new WorldMarkerPosition(100 + tick * 0.001D, 64, 100);
            handler.update(player, session, snapshot);
            handler.update(player, session, snapshot); // Same-tick callbacks must not advance a cadence.
        }
        List<Long> expectedTicks = java.util.stream.LongStream.rangeClosed(1, 30).boxed().toList();
        assertEquals(expectedTicks, handler.created.get(0).positionTicks);
        assertEquals(2, handler.dataPackets);
        assertEquals(2, handler.textUpdates);
        assertEquals(60, handler.markerStatistics(player.uuid).orElseThrow().moves());
    }

    @Test
    void markerHidesAcrossDimensionsRestoresAndCleansUp() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid);
        NavigationSnapshot valid = snapshot(player, session);
        handler.enable(player, session, NavigationSnapshot.wrongDimension());
        assertEquals(0, handler.spawned);
        handler.update(player, session, valid);
        assertEquals(2, handler.spawned);
        handler.update(player, session, NavigationSnapshot.wrongDimension());
        assertEquals(2, handler.removed);
        player.world = new Object();
        handler.update(player, session, valid);
        assertEquals(4, handler.spawned);
        player.entityId++;
        handler.update(player, session, valid);
        assertEquals(6, handler.spawned);
        assertEquals(4, handler.removed);
        player.eye = new WorldMarkerPosition(1.5, 4, 3.5);
        player.tick++;
        handler.update(player, session, valid);
        assertEquals(4, handler.removed); // No close-range disappearance.
        assertEquals(6, handler.spawned);
        assertTrue(handler.created.stream().allMatch(display -> Double.isFinite(display.position.x())
                && Double.isFinite(display.position.y()) && Double.isFinite(display.position.z())));
        handler.disable(player, session);
        assertEquals(6, handler.removed);
        assertTrue(handler.markerStatistics(player.uuid).isEmpty());
        handler.enable(player, session, valid);
        handler.cleanupPlayer(player.uuid, session);
        assertTrue(handler.markerStatistics(player.uuid).isEmpty());
    }

    @Test
    void localeChangesRefreshTheLabelWithoutSendingEarlyPositionPackets() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid);
        NavigationSnapshot snapshot = snapshot(player, session);
        handler.enable(player, session, snapshot);
        player.locale = "zh_cn";
        handler.update(player, session, snapshot);
        assertEquals(4, handler.textUpdates);
        assertEquals(4, handler.dataPackets);
        assertTrue(handler.created.stream().allMatch(display -> display.positionTicks.isEmpty()));
    }

    @Test
    void predictsSprintingPositionForNetworkRoundTripAndOneTickInterpolation() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        player.eye = new WorldMarkerPosition(0, 64, 0);
        player.latency = 100; // Two 50 ms ticks for the complete client-server-client path.
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid, new WaypointPos(0, 62, 10000));
        handler.enable(player, session, snapshot(player, session));
        for (int tick = 1; tick <= 30; tick++) {
            player.tick = tick;
            player.eye = new WorldMarkerPosition(tick * 0.28D, 64, 0);
            handler.update(player, session, snapshot(player, session));
            // Two ticks of round-trip transport, followed by one tick of display interpolation.
            WorldMarkerPosition futureEye = new WorldMarkerPosition((tick + 3) * 0.28D, 64, 0);
            WorldMarkerPosition expected = WorldMarkerPosition.project(futureEye, session.target().position(), session.worldMarkerDistance());
            WorldMarkerPosition actual = handler.created.get(0).position;
            assertTrue(WorldMarkerPosition.project(player.eye, session.target().position(), session.worldMarkerDistance())
                    .distanceTo(expected) > 0.25D);
            assertEquals(0.0D, actual.distanceTo(expected), 0.000001D);
        }
    }

    @Test
    void predictionStopsWithThePlayerAndResetsAfterTeleportAndWorldChange() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        player.eye = new WorldMarkerPosition(0, 64, 0);
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid, new WaypointPos(0, 62, 10000));
        handler.enable(player, session, snapshot(player, session));
        player.tick = 1;
        player.eye = new WorldMarkerPosition(0.28, 64, 0);
        handler.update(player, session, snapshot(player, session));
        player.tick = 2; // No displacement: don't carry forward the previous sprint velocity.
        handler.update(player, session, snapshot(player, session));
        assertEquals(WorldMarkerPosition.project(player.eye, session.target().position(), session.worldMarkerDistance()), handler.created.get(0).position);
        player.tick = 3;
        player.eye = new WorldMarkerPosition(1000, 64, 0);
        handler.update(player, session, snapshot(player, session));
        assertEquals(WorldMarkerPosition.project(player.eye, session.target().position(), session.worldMarkerDistance()), handler.created.get(0).position);
        player.tick = 4;
        player.eye = new WorldMarkerPosition(1000.28, 64, 0);
        handler.update(player, session, snapshot(player, session));
        player.world = new Object();
        player.tick = 5;
        player.eye = new WorldMarkerPosition(1000.56, 64, 0);
        handler.update(player, session, snapshot(player, session));
        WorldMarkerPosition reset = WorldMarkerPosition.project(player.eye, session.target().position(), session.worldMarkerDistance());
        assertEquals(reset, handler.created.get(2).position);
    }

    @Test
    void predictionIsBoundedForHighLatencyAndDiscardedAfterLongTickGaps() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        player.eye = new WorldMarkerPosition(0, 64, 0);
        player.latency = 20000;
        TestHandler handler = new TestHandler();
        NavigationSession session = session(player.uuid, new WaypointPos(0, 62, 10000));
        handler.enable(player, session, snapshot(player, session));
        player.tick = 1;
        player.eye = new WorldMarkerPosition(0.6, 64, 0);
        handler.update(player, session, snapshot(player, session));
        WorldMarkerPosition bounded = WorldMarkerPosition.project(new WorldMarkerPosition(2.6, 64, 0),
                session.target().position(), session.worldMarkerDistance());
        assertEquals(0.0D, bounded.distanceTo(handler.created.get(0).position), 0.000001D);
        player.tick = 100;
        player.eye = new WorldMarkerPosition(1.2, 64, 0);
        handler.update(player, session, snapshot(player, session));
        assertEquals(WorldMarkerPosition.project(player.eye, session.target().position(), session.worldMarkerDistance()), handler.created.get(0).position);
    }

    @Test
    void predictionDoesNotExtrapolateCrouchHeightOrMoveNearbyDestinationAnchors() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        player.eye = new WorldMarkerPosition(0, 64, 0);
        TestHandler handler = new TestHandler();
        NavigationSession distant = session(player.uuid, new WaypointPos(0, 62, 10000));
        handler.enable(player, distant, snapshot(player, distant));
        player.tick = 1;
        player.eye = new WorldMarkerPosition(0.28, 63.5, 0);
        handler.update(player, distant, snapshot(player, distant));
        WorldMarkerPosition expected = WorldMarkerPosition.project(new WorldMarkerPosition(0.56, 63.5, 0),
                distant.target().position(), distant.worldMarkerDistance());
        assertEquals(0.0D, expected.distanceTo(handler.created.get(0).position), 0.000001D);
        NavigationSession nearby = session(player.uuid);
        player.eye = new WorldMarkerPosition(0, 4, 0);
        handler.enable(player, nearby, snapshot(player, nearby));
        player.tick = 2;
        player.eye = new WorldMarkerPosition(0.28, 4, 0);
        handler.update(player, nearby, snapshot(player, nearby));
        WorldMarkerPosition anchor = new WorldMarkerPosition(1.5, 2.5, 3.5);
        assertEquals(anchor, handler.created.get(2).position);
    }

    @Test
    void itemIconIsCreatedAndRemovedWhenTargetChangesToVoxelMapOrNoIcon() {
        TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession item = session(player.uuid, new WaypointPos(1, 2, 3), NamespacedId.parse("minecraft:diamond"));
        handler.enable(player, item, snapshot(player, item));
        assertEquals(3, handler.spawned);
        assertEquals("item", handler.created.get(2).part);
        assertEquals(1, handler.itemRequests);
        NavigationSession voxel = session(player.uuid, new WaypointPos(1, 2, 3), NamespacedId.parse("voxelmap:house"));
        player.tick++;
        handler.update(player, voxel, snapshot(player, voxel));
        assertEquals(1, handler.removed);
        assertEquals(1, handler.itemRequests); // VoxelMap IDs never reach the item registry resolver.
        NavigationSession restored = session(player.uuid, new WaypointPos(1, 2, 3), NamespacedId.parse("minecraft:apple"));
        player.tick++;
        handler.update(player, restored, snapshot(player, restored));
        assertEquals(4, handler.spawned);
        assertEquals(2, handler.itemRequests);
        player.tick++;
        handler.update(player, session(player.uuid), snapshot(player, session(player.uuid)));
        assertEquals(2, handler.removed);
        handler.disable(player, restored);
        assertEquals(4, handler.removed);
    }

    @Test
    void voxelMapAndUnknownItemsLeaveOnlyTwoTextRows() {
        for (String id : List.of("voxelmap:house", "unknown:missing")) {
            TestPlayer player = new TestPlayer(UUID.randomUUID(), 10, new Object());
            TestHandler handler = new TestHandler();
            NavigationSession session = session(player.uuid, new WaypointPos(1, 2, 3), NamespacedId.parse(id));
            handler.enable(player, session, snapshot(player, session));
            player.tick++;
            handler.update(player, session, snapshot(player, session));
            assertEquals(2, handler.spawned);
            assertEquals(id.startsWith("voxelmap:") ? 0 : 1, handler.itemRequests);
            handler.disable(player, session);
            assertEquals(2, handler.removed);
        }
    }

    private static NavigationSnapshot snapshot(TestPlayer player, NavigationSession session) {
        return NavigationMath.snapshot("minecraft:overworld", player.eye.x(), player.eye.y() - 1.62D,
                player.eye.z(), 0, session.target());
    }

    private static NavigationSession session(UUID playerUuid) {
        return session(playerUuid, new WaypointPos(1, 2, 3));
    }

    private static NavigationSession session(UUID playerUuid, WaypointPos position) {
        return session(playerUuid, position, null);
    }

    private static NavigationSession session(UUID playerUuid, WaypointPos position, NamespacedId icon) {
        return new NavigationSession(
                playerUuid,
                new NavigationTarget(
                        WaypointCollectionKey.shared(),
                        "minecraft:overworld",
                        "towns",
                        "towns",
                        "Village",
                        "Village",
                        "",
                        List.of(),
                        position,
                        0x123456, "V", icon
                ),
                Set.of(NavigationMethod.WORLD_MARKER),
                TextDisplayTransformation.defaultValue(),
                NavigationSession.DEFAULT_WORLD_MARKER_SIZE,
                NavigationSession.DEFAULT_WORLD_MARKER_DISTANCE
        );
    }

    private static final class TestPlayer {
        private final UUID uuid;
        private int entityId;
        private Object world;
        private WorldMarkerPosition eye = new WorldMarkerPosition(0, 64, 0);
        private String locale = "en_us";
        private long tick;
        private int latency;

        private TestPlayer(UUID uuid, int entityId, Object world) {
            this.uuid = uuid;
            this.entityId = entityId;
            this.world = world;
        }
    }

    private static final class TestDisplay {
        private WorldMarkerPosition position;
        private int duration = -1;
        private Component text;
        private String part = "legacy";
        private int background;
        private float size;
        private final java.util.ArrayList<Long> positionTicks = new java.util.ArrayList<>();
    }

    private static final class TestHandler
            extends AbstractWorldMarkerNavigationHandler<TestPlayer, TestDisplay> {
        private int spawned;
        private int removed;
        private int textUpdates;
        private int dataPackets;
        private int itemRequests;
        private final java.util.ArrayList<TestDisplay> created = new java.util.ArrayList<>();

        @Override
        protected WorldMarkerPosition eyePosition(TestPlayer player) {
            return player.eye;
        }

        @Override
        protected int latencyMillis(TestPlayer player) {
            return player.latency;
        }

        @Override
        protected Object textLocale(TestPlayer player) {
            return player.locale;
        }

        @Override
        protected void setPosition(TestDisplay display, WorldMarkerPosition position) {
            display.position = position;
        }

        @Override
        protected void sendPosition(TestPlayer player, TestDisplay display) {
            display.positionTicks.add(player.tick);
        }

        @Override
        protected UUID playerUuid(TestPlayer player) {
            return player.uuid;
        }

        @Override
        protected int playerEntityId(TestPlayer player) {
            return player.entityId;
        }

        @Override
        protected Object worldIdentity(TestPlayer player) {
            return player.world;
        }

        @Override
        protected TestDisplay createDisplay(TestPlayer player) {
            TestDisplay display = new TestDisplay();
            this.created.add(display);
            return display;
        }

        @Override
        protected TestDisplay createItemDisplay(TestPlayer player, NamespacedId icon, float size) {
            this.itemRequests++;
            if (icon.namespace().equals("unknown")) return null;
            TestDisplay display = new TestDisplay();
            display.part = "item";
            this.configureItem(display, size);
            this.created.add(display);
            return display;
        }

        @Override
        protected void configureBadge(TestDisplay display, int rgb, float size) {
            display.part = "badge";
            display.background = 0x80000000 | rgb;
            display.duration = 1;
            display.size = size;
        }

        @Override
        protected void configureDistance(TestDisplay display, float size) {
            display.part = "distance";
            display.duration = 1;
            display.size = size;
        }

        @Override
        protected void configureItem(TestDisplay display, float size) {
            display.size = size;
        }

        @Override
        protected void sendSpawn(TestPlayer player, TestDisplay display) {
            this.spawned++;
        }

        @Override
        protected void sendRemove(TestPlayer player, TestDisplay display) {
            this.removed++;
        }

        @Override
        protected void setText(TestPlayer player, TestDisplay display, Component text) {
            display.text = text;
            this.textUpdates++;
        }

        @Override
        protected long currentTick(TestPlayer player) {
            return player.tick;
        }

        @Override
        protected boolean sendEntityData(TestPlayer player, TestDisplay display) {
            this.dataPackets++;
            return true;
        }
    }
}
