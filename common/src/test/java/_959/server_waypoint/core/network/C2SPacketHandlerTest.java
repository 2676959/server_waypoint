package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.WaypointEditResult;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.buffer.MessageBuffer;
import _959.server_waypoint.core.network.buffer.WaypointEditRequestBuffer;
import _959.server_waypoint.core.network.buffer.WaypointEditResultBuffer;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class C2SPacketHandlerTest {
    @TempDir
    private Path tempDir;

    @Test
    void editPermissionDenialDoesNotResolveOrMutateTheTarget() {
        TestSender sender = new TestSender();
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
        };
        C2SPacketHandler<String, String, String> handler = new C2SPacketHandler<>(
                sender,
                server,
                new TestPermissionManager(false),
                navigationService()
        );

        handler.onWaypointEditRequest("player", request());

        assertEquals(EditResultStatus.PERMISSION_DENIED, sender.lastResult().status());
        assertNull(server.getWaypointFileManager("minecraft:overworld"));
    }

    @Test
    void malformedRequestFailureReturnsStructuredStatus() {
        TestSender sender = new TestSender();
        WaypointServerCore server = new WaypointServerCore(this.tempDir) {
            @Override
            public WaypointEditResult updateWaypoint(
                    EditTarget target,
                    Integer expectedSyncNum,
                    WaypointPatch patch,
                    Consumer<WaypointEditResult> resultAction
            ) {
                throw new IllegalArgumentException("malformed target");
            }
        };
        C2SPacketHandler<String, String, String> handler = new C2SPacketHandler<>(
                sender,
                server,
                new TestPermissionManager(true),
                navigationService()
        );

        handler.onWaypointEditRequest("player", request());

        assertEquals(EditResultStatus.MALFORMED_REQUEST, sender.lastResult().status());
        assertEquals(17L, sender.lastResult().requestId());
    }

    private static WaypointEditRequestBuffer request() {
        return new WaypointEditRequestBuffer(
                17L,
                "minecraft:overworld",
                "list",
                "waypoint",
                1,
                WaypointPatch.empty()
        );
    }

    private static NavigationService<String> navigationService() {
        return new NavigationService<>(new NavigationPlatform<>() {
            @Override
            public UUID playerUuid(String player) {
                return UUID.nameUUIDFromBytes(player.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    private static final class TestPermissionManager extends PermissionManager<String, String, String> {
        private final boolean allowed;

        private TestPermissionManager(boolean allowed) {
            super(new TestPermissionKeys());
            this.allowed = allowed;
        }

        @Override
        public boolean hasPermission(String source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
            return this.allowed;
        }

        @Override
        public boolean checkPlayerPermission(String player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
            return this.allowed;
        }
    }

    private static final class TestPermissionKeys extends PermissionKeys<String> {
        @Override
        protected PermissionKey createAddPermissionKey() {
            return new PermissionKey("add");
        }

        @Override
        protected PermissionKey createEditPermissionKey() {
            return new PermissionKey("edit");
        }

        @Override
        protected PermissionKey createRemovePermissionKey() {
            return new PermissionKey("remove");
        }

        @Override
        protected PermissionKey createNavigatePermissionKey() {
            return new PermissionKey("navigate");
        }

        @Override
        protected PermissionKey createTpPermissionKey() {
            return new PermissionKey("tp");
        }

        @Override
        protected PermissionKey createReloadPermissionKey() {
            return new PermissionKey("reload");
        }
    }

    private static final class TestSender implements PlatformMessageSender<String, String> {
        private final List<MessageBuffer> packets = new ArrayList<>();

        private WaypointEditResultBuffer lastResult() {
            return (WaypointEditResultBuffer) this.packets.get(this.packets.size() - 1);
        }

        @Override
        public void sendMessage(String source, Component component) {
        }

        @Override
        public void sendPlayerMessage(String player, Component component) {
        }

        @Override
        public void sendError(String source, Component component) {
        }

        @Override
        public void sendPacket(String source, MessageBuffer packet) {
            this.packets.add(packet);
        }

        @Override
        public void sendPlayerPacket(String player, MessageBuffer packet) {
            this.packets.add(packet);
        }

        @Override
        public Iterable<? extends String> getBroadcastPlayers(String source) {
            return List.of(source);
        }

        @Override
        public Component getSenderName(String source) {
            return Component.text(source);
        }
    }
}
