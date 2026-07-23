package _959.server_waypoint.navigation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.jetbrains.annotations.Nullable;

public final class PaperCompassNavigationHandler implements
        NavigationMethodHandler<Player>,
        PaperItemNavigationHandler {
    private final PaperNavigationPlatform platform;
    private final PaperNavigationItemManager itemManager;

    public PaperCompassNavigationHandler(
            PaperNavigationPlatform platform,
            PaperNavigationItemManager itemManager
    ) {
        this.platform = platform;
        this.itemManager = itemManager;
    }

    @Override
    public NavigationMethod method() {
        return NavigationMethod.COMPASS;
    }

    @Override
    public NavigationResult enable(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        ItemStack item = this.createItem(player, session.target());
        if (item == null) {
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }
        if (!this.itemManager.install(player, this.method(), item)) {
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
        ItemStack item = this.createItem(player, session.target());
        if (item == null) {
            throw new IllegalStateException("Navigation compass target is unavailable");
        }
        if (!this.itemManager.install(player, this.method(), item)) {
            throw new IllegalStateException("Navigation compass could not be reissued after preflight");
        }
    }

    @Override
    public void disable(Player player, NavigationSession session) {
        this.itemManager.removeMethodItems(player, this.method());
    }

    @Override
    public boolean restore(Player player, NavigationSession session) {
        ItemStack item = this.createItem(player, session.target());
        return item != null && this.itemManager.install(player, this.method(), item);
    }

    private @Nullable ItemStack createItem(Player player, NavigationTarget target) {
        World world = this.platform.resolveWorld(target.dimensionName());
        if (world == null) {
            return null;
        }
        ItemStack compass = new ItemStack(Material.COMPASS, 1);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestone(new Location(
                world,
                target.position().x(),
                target.position().y(),
                target.position().z()
        ));
        meta.setLodestoneTracked(false);
        meta.displayName(Component.text(
                target.waypointName(),
                TextColor.color(target.rgb())
        ));
        compass.setItemMeta(meta);
        return this.itemManager.tag(compass);
    }
}
