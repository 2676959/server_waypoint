package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Owns the mod-platform service, handlers, and inventory lifecycle. */
public final class ModNavigationRuntime {
    private final ModNavigationItemManager itemManager = new ModNavigationItemManager();
    private final ModNavigationMapCache mapCache = new ModNavigationMapCache();
    private final CompassNavigationHandler compassHandler = new CompassNavigationHandler(this.itemManager);
    private final MapNavigationHandler mapHandler = new MapNavigationHandler(this.itemManager, this.mapCache);
    private final BossbarNavigationHandler bossbarHandler = new BossbarNavigationHandler();
    private final ActionbarNavigationHandler actionbarHandler = new ActionbarNavigationHandler();
    private final ModTextDisplayNavigationHandler textDisplayHandler = new ModTextDisplayNavigationHandler();
    private final ModNavigationPlatform platform = new ModNavigationPlatform(this.itemManager);
    private final NavigationService<ServerPlayer> service = new NavigationService<>(
            this.platform,
            List.of(
                    this.compassHandler,
                    this.mapHandler,
                    this.bossbarHandler,
                    this.actionbarHandler,
                    this.textDisplayHandler
            )
    );

    public NavigationService<ServerPlayer> service() {
        return this.service;
    }

    public void tick() {
        this.service.tick();
    }

    public void onPlayerJoin(ServerPlayer player) {
        WaypointServerMod waypointServer = WaypointServerMod.getInstance();
        if (waypointServer != null) {
            this.service.restorePersistedSession(player, waypointServer);
        }
        if (this.itemManager.validateDirectInventory(player, this.enabledItemMethods(player))) {
            this.sendDeduplicatedMessage(player);
        }
        if (this.service.findSession(player.getUUID()).isEmpty()) {
            this.itemManager.removeAllNavigationItems(player);
        }
    }

    public void onPlayerQuit(ServerPlayer player) {
        this.service.removePlayer(player);
        this.itemManager.removeAllNavigationItems(player);
    }

    public void onPlayerDeath(ServerPlayer player) {
        this.itemManager.removeAllNavigationItems(player);
    }

    public void onPlayerRespawn(ServerPlayer player) {
        this.validateAndRestore(player);
        Optional<NavigationSession> session = this.service.findSession(player.getUUID());
        if (session.isEmpty()) {
            return;
        }
        NavigationSession active = session.get();
        NavigationSnapshot snapshot = this.platform.snapshot(player, active.target());
        if (active.isEnabled(NavigationMethod.BOSSBAR)) {
            this.bossbarHandler.update(player, active, snapshot);
        }
        if (active.isEnabled(NavigationMethod.ACTIONBAR)) {
            this.actionbarHandler.update(player, active, snapshot);
        }
    }

    public void onInventoryClose(ServerPlayer player, AbstractContainerMenu menu) {
        if (ModNavigationItemData.isNavigationItem(menu.getCarried())) {
            menu.setCarried(ItemStack.EMPTY);
        }
        this.validateMenu(player, menu, true);
    }

    public void validateMenu(ServerPlayer player, AbstractContainerMenu menu, boolean restoreItems) {
        boolean changed = false;
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            changed |= ModNavigationItemManager.cleanNestedNavigationItems(stack);
            if (ModNavigationItemData.isNavigationItem(stack)
                    && slot.container != player.getInventory()) {
                slot.set(ItemStack.EMPTY);
                changed = true;
            }
        }

        ItemStack carried = menu.getCarried();
        changed |= ModNavigationItemManager.cleanNestedNavigationItems(carried);
        changed |= this.itemManager.validateDirectInventory(player, this.enabledItemMethods(player));

        if (restoreItems) {
            changed |= this.restoreMissingItems(player);
        }
        if (changed) {
            menu.sendAllDataToRemote();
            this.sendDeduplicatedMessage(player);
        }
    }

    public boolean validateAndRestore(ServerPlayer player) {
        boolean changed = this.itemManager.validateDirectInventory(player, this.enabledItemMethods(player));
        changed |= this.restoreMissingItems(player);
        if (changed) {
            player.containerMenu.sendAllDataToRemote();
        }
        return changed;
    }

    public void shutdown() {
        this.service.shutdown();
        this.mapCache.clear();
    }

    public void sendMovementDenied(ServerPlayer player) {
        ModMessageSender.getInstance().sendPlayerMessage(
                player,
                Component.translatable("waypoint.navigation.item.movement_denied")
        );
    }

    private boolean restoreMissingItems(ServerPlayer player) {
        Optional<NavigationSession> maybeSession = this.service.findSession(player.getUUID());
        if (maybeSession.isEmpty()) {
            return false;
        }
        NavigationSession session = maybeSession.get();
        boolean missingCompass = session.isEnabled(NavigationMethod.COMPASS)
                && !this.itemManager.hasItem(player, NavigationMethod.COMPASS);
        boolean missingMap = session.isEnabled(NavigationMethod.MAP)
                && !this.itemManager.hasItem(player, NavigationMethod.MAP);
        if (!missingCompass && !missingMap) {
            return false;
        }
        NavigationResult capacity = this.itemManager.preflight(player, session, session);
        if (!capacity.successful()) {
            return false;
        }

        ModNavigationItemManager.InventoryState previousState = this.itemManager.captureState(player);
        try {
            if (missingCompass && !this.compassHandler.restore(player, session).successful()) {
                this.itemManager.restoreState(player, previousState);
                return false;
            }
            if (missingMap && !this.mapHandler.restore(player, session).successful()) {
                this.itemManager.restoreState(player, previousState);
                return false;
            }
        } catch (RuntimeException exception) {
            this.itemManager.restoreState(player, previousState);
            throw exception;
        }
        ModMessageSender.getInstance().sendPlayerMessage(
                player,
                Component.translatable("waypoint.navigation.item.restored")
        );
        return true;
    }

    private void sendDeduplicatedMessage(ServerPlayer player) {
        ModMessageSender.getInstance().sendPlayerMessage(
                player,
                Component.translatable("waypoint.navigation.item.deduplicated")
        );
    }

    private Set<NavigationMethod> enabledItemMethods(ServerPlayer player) {
        Optional<NavigationSession> session = this.service.findSession(player.getUUID());
        if (session.isEmpty()) {
            return Set.of();
        }
        EnumSet<NavigationMethod> methods = EnumSet.noneOf(NavigationMethod.class);
        for (NavigationMethod method : session.get().enabledMethods()) {
            if (method.ownsItem()) {
                methods.add(method);
            }
        }
        return methods;
    }
}
