package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class AbstractTextDisplayNavigationHandlerTest {
    @Test
    void ownsLifecycleAndRecreatesDisplayWhenHostEntityChanges() {
        UUID playerUuid = UUID.randomUUID();
        Object world = new Object();
        TestPlayer player = new TestPlayer(playerUuid, 10, world);
        TestHandler handler = new TestHandler();
        NavigationSession session = session(playerUuid);
        NavigationSnapshot snapshot = NavigationSnapshot.wrongDimension();

        assertEquals(NavigationResult.Code.SUCCESS, handler.enable(player, session, snapshot).code());
        assertEquals(1, handler.created);
        assertEquals(1, handler.spawned);
        assertEquals(1, handler.transformed);
        assertEquals(1, handler.textUpdates);
        assertEquals(1, handler.dataPackets);
        assertEquals(1, handler.passengerPackets);

        player.entityId = 11;
        handler.update(player, session, snapshot);
        assertEquals(2, handler.created);
        assertEquals(2, handler.spawned);
        assertEquals(2, handler.transformed);
        assertEquals(1, handler.removed);
        assertEquals(2, handler.textUpdates);
        assertEquals(2, handler.dataPackets);
        assertEquals(2, handler.passengerPackets);
        assertSame(world, handler.lastDisplay.world);

        handler.disable(player, session);
        assertEquals(2, handler.removed);
    }

    @Test
    void appliesLiveTransformationToTheActiveDisplay() {
        UUID playerUuid = UUID.randomUUID();
        TestPlayer player = new TestPlayer(playerUuid, 10, new Object());
        TestHandler handler = new TestHandler();
        NavigationSession session = session(playerUuid);
        handler.enable(player, session, NavigationSnapshot.wrongDimension());

        handler.applyTransformation(
                player,
                new Vector3f(1.0F, 2.0F, 3.0F),
                new Quaternionf(),
                new Vector3f(0.5F)
        );

        assertEquals(2, handler.transformed);
        assertEquals(2, handler.dataPackets);
    }

    private static NavigationSession session(UUID playerUuid) {
        return new NavigationSession(
                playerUuid,
                new NavigationTarget(
                        "minecraft:overworld",
                        "towns",
                        "Village",
                        new WaypointPos(1, 2, 3),
                        0x123456
                ),
                Set.of(NavigationMethod.TEXT_DISPLAY),
                TextDisplayTransformation.defaultValue()
        );
    }

    private static final class TestPlayer {
        private final UUID uuid;
        private int entityId;
        private Object world;

        private TestPlayer(UUID uuid, int entityId, Object world) {
            this.uuid = uuid;
            this.entityId = entityId;
            this.world = world;
        }
    }

    private record TestDisplay(Object world) {
    }

    private static final class TestHandler
            extends AbstractTextDisplayNavigationHandler<TestPlayer, TestDisplay> {
        private int created;
        private int spawned;
        private int removed;
        private int transformed;
        private int textUpdates;
        private int dataPackets;
        private int passengerPackets;
        private TestDisplay lastDisplay;

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
            this.created++;
            this.lastDisplay = new TestDisplay(player.world);
            return this.lastDisplay;
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
            this.textUpdates++;
        }

        @Override
        protected void setTransformation(
                TestDisplay display,
                Vector3f translation,
                Quaternionf rotation,
                Vector3f scale
        ) {
            this.transformed++;
        }

        @Override
        protected void sendEntityData(TestPlayer player, TestDisplay display) {
            this.dataPackets++;
        }

        @Override
        protected void sendPassengers(TestPlayer player, TestDisplay display) {
            this.passengerPackets++;
        }
    }
}
