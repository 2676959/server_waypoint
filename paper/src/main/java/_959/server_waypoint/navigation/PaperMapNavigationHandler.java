package _959.server_waypoint.navigation;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperMapNavigationHandler implements
        NavigationMethodHandler<Player>,
        PaperItemNavigationHandler {
    private final PaperNavigationItemManager itemManager;
    private final PaperNavigationMapCache mapCache;
    private final Map<UUID, PaperNavigationMapCache.Lease> leases = new ConcurrentHashMap<>();

    public PaperMapNavigationHandler(
            PaperNavigationItemManager itemManager,
            PaperNavigationMapCache mapCache
    ) {
        this.itemManager = itemManager;
        this.mapCache = mapCache;
    }

    @Override
    public NavigationMethod method() {
        return NavigationMethod.MAP;
    }

    @Override
    public NavigationResult enable(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        InstallResult result = this.installForTarget(player, session.target());
        if (result == InstallResult.TARGET_UNAVAILABLE) {
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }
        if (result == InstallResult.INSUFFICIENT_INVENTORY) {
            return NavigationResult.insufficientInventory(1, this.itemManager.emptyStorageSlots(player));
        }
        return NavigationResult.success();
    }

    @Override
    public void update(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        InstallResult result = this.installForTarget(player, session.target());
        if (result == InstallResult.TARGET_UNAVAILABLE) {
            throw new IllegalStateException("Navigation map target is unavailable");
        }
        if (result == InstallResult.INSUFFICIENT_INVENTORY) {
            throw new IllegalStateException("Navigation map could not be reissued after preflight");
        }
    }

    @Override
    public void disable(Player player, NavigationSession session) {
        try {
            this.itemManager.removeMethodItems(player, this.method());
        } finally {
            this.release(player.getUniqueId());
        }
    }

    @Override
    public void cleanupPlayer(UUID playerUuid, NavigationSession session) {
        this.release(playerUuid);
    }

    @Override
    public boolean restore(Player player, NavigationSession session) {
        return this.installForTarget(player, session.target()) == InstallResult.SUCCESS;
    }

    private InstallResult installForTarget(Player player, NavigationTarget target) {
        UUID playerUuid = player.getUniqueId();
        PaperNavigationMapCache.Lease currentLease = this.leases.get(playerUuid);
        if (currentLease != null) {
            if (!currentLease.updateTarget(target)) {
                return InstallResult.TARGET_UNAVAILABLE;
            }
            ItemStack item = this.createItem(player, target, currentLease);
            if (!this.itemManager.install(player, this.method(), item)) {
                return InstallResult.INSUFFICIENT_INVENTORY;
            }
            return InstallResult.SUCCESS;
        }

        PaperNavigationMapCache.Lease newLease = this.mapCache.acquire(player, target);
        if (newLease == null) {
            return InstallResult.TARGET_UNAVAILABLE;
        }
        ItemStack item = this.createItem(player, target, newLease);
        if (!this.itemManager.install(player, this.method(), item)) {
            newLease.close();
            return InstallResult.INSUFFICIENT_INVENTORY;
        }

        this.leases.put(playerUuid, newLease);
        return InstallResult.SUCCESS;
    }

    private ItemStack createItem(
            Player player,
            NavigationTarget target,
            PaperNavigationMapCache.Lease lease
    ) {
        ItemStack item = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(lease.view());
        meta.displayName(NavigationDisplayText.buildItemName(target));
        meta.lore(NavigationDisplayText.buildItemLore(target));
        item.setItemMeta(meta);
        return this.itemManager.tag(item);
    }

    private void release(UUID playerUuid) {
        PaperNavigationMapCache.Lease lease = this.leases.remove(playerUuid);
        if (lease != null) {
            lease.close();
        }
    }

    private enum InstallResult {
        SUCCESS,
        TARGET_UNAVAILABLE,
        INSUFFICIENT_INVENTORY
    }
}
