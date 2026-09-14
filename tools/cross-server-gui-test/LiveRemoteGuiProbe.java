package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.crossserver.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.lang.reflect.Field;
import java.nio.file.*;

/** Test-only real-network GUI driver. Never installs catalog data or alters network state. */
public final class LiveRemoteGuiProbe implements ClientModInitializer {
    private int ticks, stage;
    private WaypointClientMod client;
    private WaypointManagerScreen local;
    private RemoteWaypointPanel remote;
    private final RemoteServerId destination = new RemoteServerId("b");
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (stage == 999) return;
            try {
                if (++ticks > 4800) throw new AssertionError("Native network GUI timeout at stage " + stage);
                if (ticks % 10 == 0 && mc.gui.overlay() == null) advance(mc);
            } catch (Throwable failure) {
                System.err.println("LIVE_REMOTE_GUI FAIL stage=" + stage);
                failure.printStackTrace(); stage = 999; mc.stop();
            }
        });
    }
    private void advance(Minecraft mc) throws Exception {
        switch (stage) {
            case 0 -> {
                if (mc.player == null || mc.level == null || WaypointClientMod.getNetworkState()
                        != WaypointClientMod.ClientNetworkState.SYNC_FINISHED) return;
                client = WaypointClientMod.getInstance();
                if (!has("Target")) return;
                check(ProtocolVersion.PROTOCOL_VERSION == 11, "protocol 11 artifact");
                local = new WaypointManagerScreen(client); mc.gui.setScreen(local);
                mark(mc, "SYNC_PROTOCOL_11");
            }
            case 1 -> {
                click(mc.gui.screen(), (AbstractWidget) field(WaypointManagerScreen.class, "serverScopeToggle").get(local));
                check(mc.gui.screen() == local, "scope toggles inside manager");
                remote = (RemoteWaypointPanel) field(WaypointManagerScreen.class, "remotePanel").get(local);
                mark(mc, "WAIT_REMOTE_MUTATION");
            }
            case 2 -> {
                if (!has("Added")) return;
                check(mc.gui.screen() == local, "browser remained open during network change");
                ((WaypointSearchBarWidget) field(WaypointManagerScreen.class, "searchField").get(local)).setValue("Added");
                mark(mc, "NETWORK_CATALOG_CHANGED");
            }
            case 3 -> {
                var tree = (TreeViewWidget<?>) field(RemoteWaypointPanel.class, "tree").get(remote);
                clickAt(local, tree.getX() + 24, tree.getY() + 3 * 20 + 10);
                var selected = (RemoteWaypointKey) field(RemoteWaypointPanel.class, "selected").get(remote);
                check(new RemoteWaypointKey(destination, "minecraft:overworld", "Test", "Added").equals(selected), "exact added target selected");
                var button = (AbstractWidget) field(RemoteWaypointPanel.class, "teleportButton").get(remote);
                check(button.active, "changed target actionable"); click(local, button);
                check(mc.gui.screen() == null, "teleport submitted without confirmation");
                mark(mc, "TELEPORT_SUBMITTED");
            }
            case 4 -> {
                if (mc.player == null || WaypointClientMod.getNetworkState() != WaypointClientMod.ClientNetworkState.SYNC_FINISHED) return;
                if (Math.abs(mc.player.getX() - 45.5) > .01 || Math.abs(mc.player.getY() - 80) > .01
                        || Math.abs(mc.player.getZ() - 30.5) > .01) return;
                check(mc.gui.screen() == null, "manager closed after transfer");
                mark(mc, "PASS real protocol-11 catalog update and immediate proxy transfer x=45.5 y=80 z=30.5");
                stage = 999; mc.stop(); return;
            }
            default -> throw new AssertionError("Unknown stage");
        }
        stage++;
    }
    private boolean has(String name) {
        var view = client.remoteCatalogs().snapshot().get(destination);
        if (view == null || view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) return false;
        var lists = view.snapshot().dimensions().get("minecraft:overworld");
        return lists != null && lists.containsKey("Test") && lists.get("Test").waypoints().containsKey(name);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void click(Screen screen, AbstractWidget widget) {
        clickAt(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }
    private static void clickAt(Screen screen, double x, double y) {
        check(screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false), "native mouse click handled");
    }
    private static void mark(Minecraft mc, String text) throws Exception {
        System.out.println("LIVE_REMOTE_GUI " + text);
        Files.writeString(mc.gameDirectory.toPath().resolve("live-remote-gui-result.txt"), text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
