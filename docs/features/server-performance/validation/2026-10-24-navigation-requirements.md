# Original Paper navigation/protection requirements

This is an execution inventory, not passing runtime evidence. Source: published release
revision `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`,
`paper/src/main/java/_959/server_waypoint/listener/NavigationProtectionListener.java`.

Every event handler is registered at HIGHEST priority. Native probe scenarios must invoke
the installed release handlers and distinguish physical client/server events from bounded
synthetic event dispatch. A passing synthetic handler case does not prove a physical client
interaction. Each requirement below also needs owner-isolation and post-quiescence checks.

| Hook | Native event | Required independent observations | Execution |
| --- | --- | --- | --- |
| `onInventoryClick` | `InventoryClickEvent` | Current/cursor/hotbar, direct allowed slots, shift move, drop/clone/collect, portable storage; ordinary items conserved. | Partial: actual 1/10-owner storage moves allowed, armor/result-slot attempts cancelled, ordinary move control conserved; other click modes/nested storage pending. |
| `onInventoryDrag` | `InventoryDragEvent` | Protected cursor into valid/invalid raw slots; ordinary cursor conserved. | Pending |
| `onInventoryOpen` | `InventoryOpenEvent` | Remove protected/nested items from invalid top inventory; preserve ordinary contents. | Pending |
| `onInventoryClose` | `InventoryCloseEvent` | Top inventory and cursor cleanup; reconcile only the owner. | Partial: actual player-menu close receipts, clean top/cursor and exact owner method items after click cases; other containers pending. |
| `onPrepareCraft` | `PrepareItemCraftEvent` | Protected ingredients clear result; ordinary recipe remains craftable. | Pending |
| `onPrepareResult` | `PrepareResultEvent` | Cartography protected contents clear result; other inventory types and ordinary contents retain results. | Pending |
| `onCartographyItem` | `CartographyItemEvent` | Protected cartography interaction denied; ordinary interaction allowed. | Pending |
| `onDrop` | `PlayerDropItemEvent` | Protected drop denied and restored without duplication; ordinary drop allowed. | Partial: real compass/map drops cancelled, exact owner items retained; ordinary diamond drop uncancelled and present as correlated world entity for 1/10 owners. Nested carriers pending. |
| `onItemSpawn` | `ItemSpawnEvent` | Direct protected item cancelled; nested protected item sanitized; ordinary carrier/item conserved. | Partial: ordinary drop spawn/world-entity identity observed. Protected Q drops are stopped before spawning; direct/nested protected spawn variants remain pending. |
| `onEntityPickup` | `EntityPickupItemEvent` | Direct protected item denied/removed; nested sanitized; ordinary item remains available. | Pending |
| `onInventoryPickup` | `InventoryPickupItemEvent` | Direct protected item denied/removed; nested sanitized; ordinary item transferred. | Pending |
| `onInventoryMove` | `InventoryMoveItemEvent` | Protected move cancelled, source cleaned and active owners reconciled; innocent item conservation. | Partial M0 event/physical-container evidence; player fixture pending |
| `onProtectedBlockInteract` | `PlayerInteractEvent` | Decorated-pot storage denied; compass/lodestone denied; unrelated ordinary interactions allowed. | Pending |
| `onItemFrameInteract` | `PlayerInteractEntityEvent` | Frame placement and allay equipment protected; nested carrier sanitization preserves ordinary equipment. | Pending |
| `onArmorStandManipulate` | `PlayerArmorStandManipulateEvent` | Protected held/equipped items denied and sanitized; ordinary armor/equipment preserved. | Pending |
| `onItemFrameChange` | `PlayerItemFrameChangeEvent` | Place/rotate/remove protected item denied; existing direct/nested item cleaned without deleting innocent carrier. | Pending |
| `onSwapHands` | `PlayerSwapHandItemsEvent` | Protected hand swap reconciled; owner method counts remain exact. | Partial: actual compass swap to offhand/back allowed, exact slots/counts/targets and ordinary conservation for 1/10 owners; other variants pending. |
| `onPlayerInventorySlotChange` | `PlayerInventorySlotChangeEvent` | Old/new protected item reconciled; ordinary slot edits preserved. | Partial: actual protected slot-change receipts in drop/swap/click cases, exact owner methods/items and ordinary controls pass; other injected/nested cases pending. |
| `onDeath` | `PlayerDeathEvent` | Protected drops/cursor removed, nested carrier sanitized; ordinary death drops conserved. | Partial: actual 1/10 deaths conserve seeded ordinary drops, no direct protected items reach drops/world; nested carrier/cursor variants pending. |
| `onRespawn` | `PlayerRespawnEvent` | Missing enabled method items restored exactly once. | Partial: actual 1/10 automatic respawns restore one compass/map per owner and all live effects; additional variants pending. |
| `onJoin` | `PlayerJoinEvent` | Persisted session restored with exact target/methods; one owner tick task; stale protocol state cleared. | Partial: native 1/10 joins and tasks observed; map/all-five-method restart restoration, owner map IDs and live client effects pass. Protocol state has separate Task 9 evidence. |
| `onQuit` | `PlayerQuitEvent` | Player/session/task/platform/protocol cleanup; persisted legitimate restore data retained. | Partial FAIL: stop-then-quit retains 1/10 tick counters. Active-session quit clears counters/resources and preserves legitimate restart restoration. Other lifecycle/protocol assertions pending. |

The five Paper methods are compass, map, bossbar, actionbar and text_display. Verify
single methods, all supported methods, every smaller nonempty combination, retarget/edit/
delete effects, movement/arrival, death/respawn, world changes, disconnect/rejoin and
restart restoration. Native capability discovery must confirm the installed handler set.

Map-cache `release` removes a renderer target but deliberately retains the owner map.
Therefore an inactive session does not imply that every cache must be empty. Compare
against declared retained-cache counts; assert renderer targets, active leases, tasks,
entities and protocol buffers independently. Persisted terrain must survive native save/
reopen with exact region and color data.

Local teleport and navigation command leaves are shared requirements with Task 10.
Upload/edit and remote command leaves also depend on Tasks 12 and 15; their discovery
in the command tree does not satisfy execution coverage.
