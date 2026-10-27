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
import java.util.function.Consumer;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public final class NavigationProtectionListener implements Listener {
    private static final int DENIAL_MESSAGE_INTERVAL_TICKS = 20;

    private final WaypointServerPlugin waypointServer;
    private final NavigationService<Player> navigationService;
    private final PaperNavigationPlatform navigationPlatform;
    private final PaperNavigationItemManager itemManager;
    private final Consumer<Player> waypointDataDisconnect;
    private final Consumer<Player> playerDisconnect;
    private final PaperScheduler scheduler;
    private final Map<NavigationMethod, PaperItemNavigationHandler> itemHandlers;
    private final Set<UUID> reconciling = ConcurrentHashMap.newKeySet();
    private final PendingPlayerReconciliations pendingReconciliations = new PendingPlayerReconciliations();
    private final Map<UUID, Integer> lastDenialMessageTick = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> navigationTickTasks = new ConcurrentHashMap<>();

    public NavigationProtectionListener(
            JavaPlugin plugin,
            WaypointServerPlugin waypointServer,
            NavigationService<Player> navigationService,
            PaperNavigationPlatform navigationPlatform,
            PaperNavigationItemManager itemManager,
            List<? extends PaperItemNavigationHandler> itemHandlers,
            Consumer<Player> waypointDataDisconnect,
            Consumer<Player> playerDisconnect
    ) {
        this.waypointServer = waypointServer;
        this.navigationService = navigationService;
        this.navigationPlatform = navigationPlatform;
        this.itemManager = itemManager;
        this.waypointDataDisconnect = waypointDataDisconnect;
        this.playerDisconnect = playerDisconnect;
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
        boolean currentNavigation = this.itemManager.isNavigationItem(current);
        boolean cursorNavigation = this.itemManager.isNavigationItem(cursor);
        boolean hotbarNavigation = this.itemManager.isNavigationItem(hotbarItem);
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
                || !this.itemManager.isNavigationItem(event.getOldCursor())) {
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
        this.reconcileNextTick(player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        boolean containsNavigationItem = false;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (this.itemManager.isNavigationItem(item)) {
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
            if (this.itemManager.isNavigationItem(item)) {
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
            if (this.itemManager.isNavigationItem(item)) {
                event.setCancelled(true);
                this.sendMovementDenied(player);
                this.reconcileNextTick(player, true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (this.itemManager.isNavigationItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            this.sendMovementDenied(event.getPlayer());
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!this.itemManager.isNavigationItem(event.getEntity().getItemStack())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!this.itemManager.isNavigationItem(event.getItem().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getItem().remove();
        if (event.getEntity() instanceof Player player) {
            this.sendMovementDenied(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (!this.itemManager.isNavigationItem(event.getItem().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getItem().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProtectedBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Material clickedType = event.getClickedBlock().getType();
        boolean protectedInteraction = clickedType == Material.DECORATED_POT
                && this.itemManager.isNavigationItem(event.getItem());
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
                && this.itemManager.isNavigationItem(heldItem)) {
            event.setCancelled(true);
            this.sendMovementDenied(event.getPlayer());
            this.reconcileNextTick(event.getPlayer(), true);
            return;
        }
        if (event.getRightClicked() instanceof Allay allay) {
            ItemStack allayItem = allay.getEquipment().getItemInMainHand();
            boolean allayNavigation = this.itemManager.isNavigationItem(allayItem);
            if (!this.itemManager.isNavigationItem(heldItem)
                    && !allayNavigation) {
                return;
            }
            event.setCancelled(true);
            if (allayNavigation) {
                this.sanitizeEquipmentItem(allay, EquipmentSlot.HAND);
            }
            this.sendMovementDenied(event.getPlayer());
            if (!allayNavigation) {
                this.reconcileNextTick(event.getPlayer(), true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ItemStack playerItem = event.getPlayerItem();
        ItemStack armorStandItem = event.getArmorStandItem();
        boolean armorStandNavigation = this.itemManager.isNavigationItem(armorStandItem);
        if (!this.itemManager.isNavigationItem(playerItem)
                && !armorStandNavigation) {
            return;
        }
        event.setCancelled(true);
        if (armorStandNavigation) {
            this.sanitizeEquipmentItem(event.getRightClicked(), event.getSlot());
        }
        this.sendMovementDenied(event.getPlayer());
        if (!armorStandNavigation) {
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        ItemStack item = event.getItemStack();
        if (!this.itemManager.isNavigationItem(item)) {
            return;
        }
        event.setCancelled(true);
        boolean placement = event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE;
        if (!placement) {
            event.getItemFrame().setItem(new ItemStack(Material.AIR), false);
        }
        this.sendMovementDenied(event.getPlayer());
        if (placement) {
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (this.itemManager.isNavigationItem(event.getMainHandItem())
                || this.itemManager.isNavigationItem(event.getOffHandItem())) {
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInventorySlotChange(PlayerInventorySlotChangeEvent event) {
        if (this.itemManager.isNavigationItem(event.getOldItemStack())
                || this.itemManager.isNavigationItem(event.getNewItemStack())) {
            this.reconcileNextTick(event.getPlayer(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        for (int index = drops.size() - 1; index >= 0; index--) {
            ItemStack item = drops.get(index);
            if (!this.itemManager.isNavigationItem(item)) {
                continue;
            }
            drops.remove(index);
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
        this.waypointDataDisconnect.accept(event.getPlayer());
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
        this.pendingReconciliations.remove(player.getUniqueId());
        try {
            this.playerDisconnect.accept(player);
            ScheduledTask tickTask = this.navigationTickTasks.remove(player.getUniqueId());
            if (tickTask != null) {
                tickTask.cancel();
            }
            this.navigationService.removePlayer(player);
            this.itemManager.purgeAll(player);
            this.lastDenialMessageTick.remove(player.getUniqueId());
            this.reconciling.remove(player.getUniqueId());
            this.navigationPlatform.unregisterPlayer(player.getUniqueId());
        } finally {
            // Purging items can itself emit inventory events during disconnect.
            this.pendingReconciliations.remove(player.getUniqueId());
        }
    }

    public void shutdown() {
        this.pendingReconciliations.close();
        this.navigationTickTasks.values().forEach(ScheduledTask::cancel);
        this.navigationTickTasks.clear();
        this.lastDenialMessageTick.clear();
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
        this.pendingReconciliations.request(
                player.getUniqueId(),
                restoreMissing,
                (action, retired) -> this.scheduler.runNextTick(player, action, retired) != null,
                restore -> {
                    if (player.isOnline()) {
                        this.reconcile(player, restore);
                    }
                }
        );
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

    private void sanitizeEquipmentItem(LivingEntity entity, EquipmentSlot slot) {
        entity.getEquipment().setItem(slot, null, true);
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
