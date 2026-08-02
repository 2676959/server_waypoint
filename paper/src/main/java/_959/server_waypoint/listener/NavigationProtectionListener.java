package _959.server_waypoint.listener;

import _959.server_waypoint.PaperScheduler;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.PaperItemNavigationHandler;
import _959.server_waypoint.navigation.PaperNavigationItemManager;
import _959.server_waypoint.navigation.PaperNavigationPlatform;
import _959.server_waypoint.server.WaypointServerPlugin;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import io.papermc.paper.event.player.CartographyItemEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public final class NavigationProtectionListener implements Listener {
    private static final int DENIAL_MESSAGE_INTERVAL_TICKS = 20;

    private final WaypointServerPlugin waypointServer;
    private final NavigationService<Player> navigationService;
    private final PaperNavigationPlatform navigationPlatform;
    private final PaperNavigationItemManager itemManager;
    private final PaperScheduler scheduler;
    private final Map<NavigationMethod, PaperItemNavigationHandler> itemHandlers;
    private final Set<UUID> reconciling = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> lastDenialMessageTick = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> navigationTickTasks = new ConcurrentHashMap<>();

    public NavigationProtectionListener(
            JavaPlugin plugin,
            WaypointServerPlugin waypointServer,
            NavigationService<Player> navigationService,
            PaperNavigationPlatform navigationPlatform,
            PaperNavigationItemManager itemManager,
            List<? extends PaperItemNavigationHandler> itemHandlers
    ) {
        this.waypointServer = waypointServer;
        this.navigationService = navigationService;
        this.navigationPlatform = navigationPlatform;
        this.itemManager = itemManager;
        this.scheduler = new PaperScheduler(plugin);
        this.itemHandlers = new EnumMap<>(NavigationMethod.class);
        for (PaperItemNavigationHandler handler : itemHandlers) {
            this.itemHandlers.put(handler.method(), handler);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbarItem = this.hotbarItem(event, player);
        boolean currentNavigation = this.itemManager.containsNavigationItem(current);
        boolean cursorNavigation = this.itemManager.containsNavigationItem(cursor);
        boolean hotbarNavigation = this.itemManager.containsNavigationItem(hotbarItem);
        if (!currentNavigation && !cursorNavigation && !hotbarNavigation) {
            return;
        }

        boolean allowedSlot = isAllowedDirectSlot(event.getClickedInventory(), event.getSlot(), player);
        InventoryAction action = event.getAction();
        boolean directPlayerQuickMove = currentNavigation
                && action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && allowedSlot
                && event.getView().getType() == InventoryType.CRAFTING;
        boolean denied = event.getClickedInventory() == null
                || (currentNavigation && !allowedSlot)
                || (cursorNavigation && !allowedSlot)
                || (hotbarNavigation && !allowedSlot)
                || (currentNavigation
                        && action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        && !directPlayerQuickMove)
                || action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT
                || action == InventoryAction.CLONE_STACK
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.UNKNOWN
                || (cursorNavigation && this.itemManager.isPortableStorage(current))
                || (currentNavigation && this.itemManager.isPortableStorage(cursor));
        if (denied) {
            event.setCancelled(true);
            this.sendMovementDenied(player);
        }
        this.reconcileNextTick(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !this.itemManager.containsNavigationItem(event.getOldCursor())) {
            return;
        }
        InventoryView view = event.getView();
        boolean allowed = true;
        for (int rawSlot : event.getRawSlots()) {
            Inventory inventory = view.getInventory(rawSlot);
            int convertedSlot = view.convertSlot(rawSlot);
            if (!isAllowedDirectSlot(inventory, convertedSlot, player)) {
                allowed = false;
                break;
            }
        }
        if (!allowed) {
            event.setCancelled(true);
            this.sendMovementDenied(player);
        }
        this.reconcileNextTick(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player
                && this.itemManager.removeFromInvalidInventory(event.getView().getTopInventory())) {
            this.sendDeduplicated(player);
            this.reconcileNextTick(player, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        boolean changed = this.itemManager.removeFromInvalidInventory(event.getView().getTopInventory());
        changed |= this.itemManager.sanitizeCursor(player);
        if (changed) {
            this.sendDeduplicated(player);
        }
        this.reconcile(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        boolean containsNavigationItem = false;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (this.itemManager.containsNavigationItem(item)) {
                containsNavigationItem = true;
                break;
            }
        }
        if (containsNavigationItem) {
            event.getInventory().setResult(null);
            if (event.getView().getPlayer() instanceof Player player) {
                this.sendMovementDenied(player);
                this.reconcileNextTick(player, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareResultEvent event) {
        if (event.getView().getType() != InventoryType.CARTOGRAPHY) {
            return;
        }
        for (ItemStack item : event.getView().getTopInventory().getContents()) {
            if (this.itemManager.containsNavigationItem(item)) {
                event.setResult(null);
                if (event.getView().getPlayer() instanceof Player player) {
                    this.sendMovementDenied(player);
                    this.reconcileNextTick(player, true);
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCartographyItem(CartographyItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        for (ItemStack item : event.getInventory().getContents()) {
            if (this.itemManager.containsNavigationItem(item)) {
                event.setCancelled(true);
                this.sendMovementDenied(player);
                this.reconcileNextTick(player, true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (this.itemManager.containsNavigationItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            this.sendMovementDenied(event.getPlayer());
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (!this.itemManager.containsNavigationItem(item)) {
            return;
        }
        if (this.itemManager.isNavigationItem(item)) {
            event.setCancelled(true);
        } else if (this.itemManager.removeNestedNavigationItems(item)) {
            event.getEntity().setItemStack(item);
        }
        this.reconcileActivePlayers();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!this.itemManager.containsNavigationItem(item)) {
            return;
        }
        if (this.itemManager.isNavigationItem(item)) {
            event.setCancelled(true);
            event.getItem().remove();
            if (event.getEntity() instanceof Player player) {
                this.sendMovementDenied(player);
                this.reconcileNextTick(player, true);
            }
        } else if (this.itemManager.removeNestedNavigationItems(item)) {
            event.getItem().setItemStack(item);
        }
        this.reconcileActivePlayers();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (!this.itemManager.containsNavigationItem(item)) {
            return;
        }
        if (this.itemManager.isNavigationItem(item)) {
            event.setCancelled(true);
            event.getItem().remove();
        } else if (this.itemManager.removeNestedNavigationItems(item)) {
            event.getItem().setItemStack(item);
        }
        this.reconcileActivePlayers();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (this.itemManager.containsNavigationItem(event.getItem())) {
            event.setCancelled(true);
            Inventory source = event.getSource();
            this.scheduler.runNextTick(source, () -> {
                this.itemManager.removeFromInvalidInventory(source);
                this.reconcileActivePlayers();
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtectedBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Material clickedType = event.getClickedBlock().getType();
        boolean protectedInteraction = clickedType == Material.DECORATED_POT
                && this.itemManager.containsNavigationItem(event.getItem());
        protectedInteraction |= clickedType == Material.LODESTONE
                && this.itemManager.isMethod(event.getItem(), NavigationMethod.COMPASS);
        if (!protectedInteraction) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        this.sendMovementDenied(event.getPlayer());
        this.reconcileNextTick(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        ItemStack heldItem = event.getPlayer().getInventory().getItem(event.getHand());
        if (event.getRightClicked() instanceof ItemFrame
                && this.itemManager.containsNavigationItem(heldItem)) {
            event.setCancelled(true);
            this.sendMovementDenied(event.getPlayer());
            this.reconcileNextTick(event.getPlayer(), true);
            return;
        }
        if (event.getRightClicked() instanceof Allay allay) {
            ItemStack allayItem = allay.getEquipment().getItemInMainHand();
            if (!this.itemManager.containsNavigationItem(heldItem)
                    && !this.itemManager.containsNavigationItem(allayItem)) {
                return;
            }
            event.setCancelled(true);
            if (this.itemManager.containsNavigationItem(allayItem)) {
                this.sanitizeEquipmentItem(allay, EquipmentSlot.HAND, allayItem);
            }
            this.sendMovementDenied(event.getPlayer());
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ItemStack playerItem = event.getPlayerItem();
        ItemStack armorStandItem = event.getArmorStandItem();
        if (!this.itemManager.containsNavigationItem(playerItem)
                && !this.itemManager.containsNavigationItem(armorStandItem)) {
            return;
        }
        event.setCancelled(true);
        if (this.itemManager.containsNavigationItem(armorStandItem)) {
            this.sanitizeEquipmentItem(event.getRightClicked(), event.getSlot(), armorStandItem);
        }
        this.sendMovementDenied(event.getPlayer());
        this.reconcileNextTick(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        ItemStack item = event.getItemStack();
        if (!this.itemManager.containsNavigationItem(item)) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() != PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) {
            if (this.itemManager.isNavigationItem(item)) {
                event.getItemFrame().setItem(new ItemStack(Material.AIR), false);
            } else {
                ItemStack sanitizedItem = item.clone();
                this.itemManager.removeNestedNavigationItems(sanitizedItem);
                event.getItemFrame().setItem(sanitizedItem, false);
            }
            this.reconcileActivePlayers();
        }
        this.sendMovementDenied(event.getPlayer());
        this.reconcileNextTick(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (this.itemManager.containsNavigationItem(event.getMainHandItem())
                || this.itemManager.containsNavigationItem(event.getOffHandItem())) {
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        if (this.itemManager.containsNavigationItem(event.getOldItemStack())
                || this.itemManager.containsNavigationItem(event.getNewItemStack())) {
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        for (int index = drops.size() - 1; index >= 0; index--) {
            ItemStack item = drops.get(index);
            if (!this.itemManager.containsNavigationItem(item)) {
                continue;
            }
            if (this.itemManager.isNavigationItem(item)) {
                drops.remove(index);
            } else if (this.itemManager.removeNestedNavigationItems(item)) {
                drops.set(index, item);
            }
        }
        Player player = event.getPlayer();
        this.itemManager.sanitizeCursor(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        this.reconcileNextTick(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        this.restorePlayer(event.getPlayer());
    }

    public void restorePlayer(Player player) {
        this.navigationPlatform.registerPlayer(player);
        this.navigationService.restorePersistedSession(player, this.waypointServer);
        this.startNavigationTick(player);
        this.reconcileNextTick(player, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ScheduledTask tickTask = this.navigationTickTasks.remove(player.getUniqueId());
        if (tickTask != null) {
            tickTask.cancel();
        }
        this.navigationService.removePlayer(player);
        this.itemManager.purgeAll(player);
        this.lastDenialMessageTick.remove(player.getUniqueId());
        this.reconciling.remove(player.getUniqueId());
        this.navigationPlatform.unregisterPlayer(player.getUniqueId());
    }

    private ItemStack hotbarItem(InventoryClickEvent event, Player player) {
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && hotbarButton < 9) {
            return player.getInventory().getItem(hotbarButton);
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            return player.getInventory().getItemInOffHand();
        }
        return null;
    }

    private void reconcileNextTick(Player player, boolean restoreMissing) {
        this.scheduler.runNextTick(player, () -> {
            if (player.isOnline()) {
                this.reconcile(player, restoreMissing);
            }
        });
    }

    private void reconcile(Player player, boolean restoreMissing) {
        UUID playerUuid = player.getUniqueId();
        if (!this.reconciling.add(playerUuid)) {
            return;
        }
        try {
            Optional<NavigationSession> session = this.navigationService.findSession(playerUuid);
            PaperNavigationItemManager.ValidationResult validation =
                    this.itemManager.validateDirectInventory(player, session.orElse(null));
            boolean changed = validation.changed();
            changed |= this.itemManager.removeFromInvalidInventory(
                    player.getOpenInventory().getTopInventory()
            );
            if (changed) {
                this.sendDeduplicated(player);
            }
            if (!restoreMissing || session.isEmpty() || validation.missingMethods().isEmpty()) {
                return;
            }

            int requiredSlots = validation.missingMethods().size();
            int availableSlots = this.itemManager.emptyStorageSlots(player);
            if (requiredSlots > availableSlots) {
                player.sendMessage(translatable(
                        "waypoint.navigation.inventory.insufficient",
                        text(requiredSlots),
                        text(availableSlots)
                ));
                return;
            }

            List<PaperItemNavigationHandler> restoredHandlers = new ArrayList<>();
            for (NavigationMethod method : validation.missingMethods()) {
                PaperItemNavigationHandler handler = this.itemHandlers.get(method);
                if (handler == null || !handler.restore(player, session.orElseThrow())) {
                    for (PaperItemNavigationHandler restored : restoredHandlers) {
                        this.itemManager.removeMethodItems(player, restored.method());
                    }
                    return;
                }
                restoredHandlers.add(handler);
            }
            if (!restoredHandlers.isEmpty()) {
                player.sendMessage(translatable("waypoint.navigation.item.restored"));
            }
        } finally {
            this.reconciling.remove(playerUuid);
        }
    }

    private void sanitizeEquipmentItem(
            LivingEntity entity,
            EquipmentSlot slot,
            ItemStack item
    ) {
        ItemStack sanitizedItem = null;
        if (!this.itemManager.isNavigationItem(item)) {
            sanitizedItem = item.clone();
            this.itemManager.removeNestedNavigationItems(sanitizedItem);
        }
        entity.getEquipment().setItem(slot, sanitizedItem, true);
        this.reconcileActivePlayers();
    }

    private void reconcileActivePlayers() {
        for (UUID playerUuid : this.navigationPlatform.registeredPlayerUuids()) {
            if (this.navigationService.findSession(playerUuid).isEmpty()) {
                continue;
            }
            this.navigationPlatform.executePlayer(
                    playerUuid,
                    player -> this.reconcileNextTick(player, true)
            );
        }
    }

    private void sendMovementDenied(Player player) {
        int currentTick = player.getTicksLived();
        Integer previousTick = this.lastDenialMessageTick.get(player.getUniqueId());
        if (previousTick != null && currentTick - previousTick < DENIAL_MESSAGE_INTERVAL_TICKS) {
            return;
        }
        this.lastDenialMessageTick.put(player.getUniqueId(), currentTick);
        player.sendMessage(translatable("waypoint.navigation.item.movement_denied"));
    }

    private void sendDeduplicated(Player player) {
        player.sendMessage(translatable("waypoint.navigation.item.deduplicated"));
    }

    private void startNavigationTick(Player player) {
        UUID playerUuid = player.getUniqueId();
        ScheduledTask previousTask = this.navigationTickTasks.remove(playerUuid);
        if (previousTask != null) {
            previousTask.cancel();
        }
        ScheduledTask task = this.scheduler.runAtFixedRate(
                player,
                ignored -> this.navigationService.tickPlayer(player),
                () -> {
                    this.navigationTickTasks.remove(playerUuid);
                    this.navigationService.removePlayer(playerUuid);
                    this.navigationPlatform.unregisterPlayer(playerUuid);
                },
                1L,
                1L
        );
        if (task != null) {
            this.navigationTickTasks.put(playerUuid, task);
        }
    }

    private static boolean isAllowedDirectSlot(
            Inventory clickedInventory,
            int slot,
            Player player
    ) {
        PlayerInventory inventory = player.getInventory();
        return clickedInventory == inventory
                && ((slot >= 0 && slot < 36) || slot == 40);
    }
}
