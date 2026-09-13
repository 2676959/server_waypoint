package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.core.network.message.RemoteCatalogMessage;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Disposable, automatically terminating native GUI fixture in a newly created offline world. */
public final class RemoteGuiProbe implements ClientModInitializer {
    private int ticks;
    private int stage = -1;
    private WaypointClientMod client;
    private WaypointManagerScreen local;
    private RemoteWaypointPanel remote;
    private Object manager;
    private Map<Path, String> files;
    private final RemoteServerId id = new RemoteServerId("remote-a");

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++ticks > 1200) { fail(mc, new AssertionError("Native GUI timeout")); return; }
            if (ticks % 10 != 0 || mc.getOverlay() != null) return;
            try { advance(mc); } catch (Throwable error) { fail(mc, error); }
        });
    }

    private void advance(Minecraft mc) throws Exception {
        switch (stage) {
            case -1 -> {
                if (!(mc.screen instanceof TitleScreen)) return;
                var settings = new net.minecraft.world.level.LevelSettings("GUI probe",
                        net.minecraft.world.level.GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT,
                        true, net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("gui-probe-" + System.currentTimeMillis(), settings,
                        new net.minecraft.world.level.levelgen.WorldOptions(1, false, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createFlatWorldDimensions, mc.screen);
            }
            case 0 -> {
                if (mc.level == null || mc.player == null) return;
                client = WaypointClientMod.getInstance();
                field(WaypointClientMod.class, "networkState").set(null, WaypointClientMod.ClientNetworkState.SYNC_FINISHED);
                field(WaypointClientMod.class, "currentDimensionName").set(null, "minecraft:overworld");
                client.changeWaypointFilesDir(mc.gameDirectory.toPath().resolve("probe-local"));
                client.putWaypointList("minecraft:overworld", WaypointList.build("local-marker", 7));
                client.saveAllWaypointFiles();
                manager = client.getWaypointFileManager("minecraft:overworld");
                files = localFiles(mc);
                install(RemoteCatalogState.AVAILABLE);
                var config = WaypointClientMod.getClientConfig();
                config.setWaypointManagerGroupByLists(true);
                config.setWaypointManagerSortMode(WaypointSorting.SortMode.NAME);
                config.setWaypointManagerSortReversed(false);
                local = new WaypointManagerScreen(client);
                mc.setScreen(local);
            }
            case 1 -> {
                check(mc.screen == local, "local manager open");
                click(mc.screen, (AbstractWidget) field(WaypointManagerScreen.class, "serverScopeToggle").get(local));
                check(mc.screen == local, "scope toggle keeps manager screen");
                remote = (RemoteWaypointPanel) field(WaypointManagerScreen.class, "remotePanel").get(local);
                check((boolean) field(WaypointManagerScreen.class, "showingRemote").get(local), "remote scope selected");
                check(!((AbstractWidget) field(WaypointManagerScreen.class, "addWaypointButton").get(local)).active, "local add disabled");
            }
            case 2 -> {
                var tree = (TreeViewWidget<?>) field(RemoteWaypointPanel.class, "tree").get(remote);
                // Server, dimension, list, then first waypoint; dispatch through the real screen input path.
                clickAt(local, tree.getX() + 24, tree.getY() + 3 * 16 + 8);
                check(button().active, "available exact target enables teleport");
                var key = (RemoteWaypointKey) field(RemoteWaypointPanel.class, "selected").get(remote);
                check(key.serverId().equals(id) && key.listName().isEmpty() && key.waypointName().equals("Exact \"Name\""), "exact selection identity");
                click(local, button());
                check(mc.screen instanceof ConfirmScreen, "confirmation is a separate modal screen");
            }
            case 3 -> {
                // The native Cancel button returns to the same remote screen without sending a command.
                var cancel = mc.screen.children().stream().filter(c -> c instanceof AbstractWidget w
                        && w.getMessage().getString().equals("No")).map(c -> (AbstractWidget)c).findFirst().orElseThrow();
                click(mc.screen, cancel);
                check(mc.screen == local, "cancel restores browser");
                local.resize(320, 240);
                check(button().getX() + button().getWidth() <= 320, "small viewport fits actions");
                local.resize(960, 540);
                check(button().active, "resize preserves valid selection");
                var grouping = (AbstractWidget) field(WaypointManagerScreen.class, "groupModeToggle").get(local);
                click(local, grouping);
                check(!(boolean) field(RemoteWaypointPanel.class, "grouped").get(remote), "shared flat control");
                var order = (AbstractWidget) field(WaypointManagerScreen.class, "sortOrderToggle").get(local);
                click(local, order);
                check((boolean) field(RemoteWaypointPanel.class, "reversed").get(remote), "shared sort direction");
                check(button().active, "sorting keeps exact selection");
                var sorting = (AbstractWidget) field(WaypointManagerScreen.class, "sortingModeDropdown").get(local);
                var items = (java.util.List<?>) field(sorting.getClass(), "iconItems").get(sorting);
                check(!((AbstractWidget) items.get(2)).active, "remote distance sort is disabled");
                click(local, sorting);
                click(local, (AbstractWidget) items.get(3));
                check(field(RemoteWaypointPanel.class, "sortMode").get(remote) == WaypointSorting.SortMode.COLOR,
                        "shared color sort menu controls remote panel");
                var scope = (AbstractWidget) field(WaypointManagerScreen.class, "serverScopeToggle").get(local);
                click(local, scope);
                check(mc.screen == local && !button().visible, "local view hides remote actions");
                click(local, scope);
                check(button().active, "remote view restores selection");
                click(local, grouping);
                ((WaypointSearchBarWidget) field(WaypointManagerScreen.class, "searchField").get(local)).setValue("not-found");
                check(!button().active, "filtered-out selection disables teleport");
            }
            case 4 -> {
                ((WaypointSearchBarWidget) field(WaypointManagerScreen.class, "searchField").get(local)).setValue("");
                install(RemoteCatalogState.STALE);
            }
            case 5 -> {
                var tree = (TreeViewWidget<?>) field(RemoteWaypointPanel.class, "tree").get(remote);
                clickAt(local, tree.getX() + 24, tree.getY() + 3 * 16 + 8);
                check(!button().active, "stale target remains read-only");
                install(RemoteCatalogState.AVAILABLE);
            }
            case 6 -> {
                check(button().active, "fresh replacement restores action");
                click(local, button());
                check(mc.screen instanceof ConfirmScreen, "confirmation reopened");
                client.remoteCatalogs().clear();
                var yes = mc.screen.children().stream().filter(c -> c instanceof AbstractWidget w
                        && w.getMessage().getString().equals("Yes")).map(c -> (AbstractWidget)c).findFirst().orElseThrow();
                click(mc.screen, yes);
                check(mc.screen == null, "session reset invalidates confirmation");
                check(manager == client.getWaypointFileManager("minecraft:overworld"), "local manager untouched");
                check(files.equals(localFiles(mc)), "local files unchanged");
                System.out.println("REMOTE_GUI_PROBE PASS: native Fabric 26.1.2 screen input, confirmation, resize, cache transitions and local isolation");
                Files.writeString(mc.gameDirectory.toPath().resolve("remote-gui-result.txt"), "PASS\n");
                mc.stop();
            }
            default -> { }
        }
        stage++;
    }

    private TranslucentButton button() throws Exception {
        return (TranslucentButton) field(RemoteWaypointPanel.class, "teleportButton").get(remote);
    }
    private void install(RemoteCatalogState state) throws Exception {
        // Make the fixture refresh due without replacing the session under test.
        field(client.remoteCatalogs().getClass(), "nextRequest").setLong(client.remoteCatalogs(), 0);
        var request = client.remoteCatalogs().poll();
        var waypoint = new RemoteWaypointSnapshot("Shared label", "R", new WaypointPos(1, 64, 3), 0x12AB34,
                0, true, List.of("keyword"), "Remote read-only description");
        var snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(1), Map.of("minecraft:overworld",
                Map.of("", new RemoteListSnapshot("Shared list", new RemoteRevision(1), Map.of("Exact \"Name\"", waypoint)))), Instant.EPOCH);
        check(client.remoteCatalogs().apply(new RemoteCatalogMessage(request.requestId(), RemoteCatalogState.AVAILABLE,
                Map.of(id, new CatalogReceiver.View(snapshot, state, "Shared server", null)))), "fixture refresh applied");
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void click(Screen screen, AbstractWidget widget) {
        clickAt(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }
    private static void clickAt(Screen screen, double x, double y) {
        check(screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false), "native click handled");
    }
    private static Map<Path, String> localFiles(Minecraft mc) throws Exception {
        Map<Path, String> files = new HashMap<>();
        try (var paths = Files.walk(mc.gameDirectory.toPath().resolve("probe-local"))) {
            for (var path : paths.filter(Files::isRegularFile).toList()) files.put(path, Files.readString(path));
        }
        return files;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void fail(Minecraft mc, Throwable error) {
        System.err.println("REMOTE_GUI_PROBE FAIL stage=" + stage); error.printStackTrace(); mc.stop(); stage = 9999;
    }
}
