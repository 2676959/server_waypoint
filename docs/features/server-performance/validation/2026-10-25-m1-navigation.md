# M1 original Paper navigation observations

Task 11 is in progress. These are functional observations of the unchanged published
4.0.0 Paper JAR, not timed performance acceptance or completion of the protection,
terrain, lifecycle or platform matrix.

## Inputs and observation boundaries

- Published artifact SHA-256: `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57`.
- Paper 1.21.11 build 111 and the Java runtime from `config/runtimes.json`.
- Each root records probe and bot binary SHA-256 hashes, native hello receipts,
  server logs, normal client packet summaries and action acknowledgements.
- Read-only snapshots inspect the installed release's sessions, handlers, compass
  lodestones, map views and actual renderer cursor/caption records. They do not
  reconstruct those effects from the session target. No current core classes are
  included in the probe archive.
- Network-only text displays are observed through both their native handler map
  and normal add/metadata/remove packets. Bukkit world-entity counts alone would
  miss them. Bossbars and actionbars also have independent packet observations.
- Client histories are bounded to 64 entries per text channel, 16 active bossbars/
  text displays and eight maps per peer, with explicit history/map eviction and
  text-clipping counters. Invalid map rectangles are rejected before mutation.
  Map pixels are reconstructed from real color patches; counts and CRC32 are
  diagnostics in the method checkpoint. The later terrain checkpoint below adds
  exact native save/restart assertions.

## Executed functional checks

`/Volumes/ssd/sw-performance-m1-navigation-small-targets/` and
`/Volumes/ssd/sw-performance-m1-navigation-medium-targets/` execute all 31 nonempty
subsets of the five discovered methods for one and ten real vanilla-protocol
Azalea players. Each actor has every requested method and its expected native/
client effects; unselected methods have no active item/display effect. The map
cursor is independently expected at `(-96, -96)` for target `(320, -61, 320)` in
the `(512, 512)` region at scale 2. Those runs enabled all five through individual
method commands; the later terrain checkpoint additionally executes the named
`all` command and verifies its outcome.

Both runs also verify real movement, retargeting to a different map region,
waypoint display-name/color/position/identifier edits, refreshed compass/map/live
display text, and native `/wp tp` arrival. Target B starts at `(-320, -61, -320)`;
the edited point is `(-300, -61, -300)`, with independent map cursor expectation
`(106, 106)` in region `(-512, -512)`. Native target RGB values are checked;
plain-text packet observations do not establish rendered color appearance.

The further `/Volumes/ssd/sw-performance-m1-navigation-medium-isolation/` run
repeats all 31 selections for ten actors, seeds each with 64 stone and three
diamonds, and verifies exact ordinary-item identity/count conservation through
selection, movement, retargeting, edits and teleport. One actor retargets to B
while the other nine remain on A; all ten native targets, item destinations,
map cursor/captions and live client displays match their independent owner
expectations. This run also retains ten post-disconnect tick counters and exits
1. Its `inputs.json` records controller/helper source hashes separately from
the compiled binary hashes.

Stopping navigation clears sessions, tagged items, bossbars, text-display state
and renderer targets. Native owner tick tasks remain while players are online.
Over a subsequent interval of at least five actual server ticks, the client
receives no new navigation actionbar updates. The last actionbar text is allowed
to fade normally: the implementation deliberately avoids sending an empty bar
that could erase another plugin's content.

## Retained original-release failure

Both target runs exit 1 because post-disconnect cleanup retains navigation tick
counters: **1 / 10 entries**, respectively, after at least ten additional native
ticks with no players online. Sessions, owner tasks, bossbars, text-display maps,
tagged online items, target index and map renderer targets are zero. Owner map
cache entries intentionally remain at 1 / 10 and are not counted as this defect.

The published source explains the observation: `tickPlayer` maintains a counter
even without an active session; both `removePlayer` overloads return early when
there is no session, before `removeSession` can clear that counter. This proves
retained state in this lifecycle sequence. It does not measure a long-running
heap-growth slope or establish production incident attribution. No repair was
made; M1 retains original-release failures for the later repair milestone.

## Retained harness attempts

- `navigation-snapshot-red`: missing native snapshot operation, before its
  implementation.
- `navigation-small-green`: command acknowledgement unavailable because this
  helper had not initialized native command-tree instrumentation.
- `navigation-small-green-2`: all 31 combinations and the originally declared
  cleanup counters pass. This earlier declaration omitted tick counters; it does
  not override the fuller cleanup failure in subsequent roots.

Python contract tests also reject stale bossbar/actionbar targets, changed
ordinary-item counts, escaping protected items and missing resource evidence.
The protection contracts are not native execution of the 22 protection hooks.
Deletion, terrain across dimensions, cache eviction, shared-map viewers and the
remaining native protections still require evidence. A later lifecycle checkpoint
below adds actual deaths, respawns and world changes.
Main CLI adapters remain blocked until their complete contracts are implemented.

## Terrain and active-session restart checkpoint

`/Volumes/ssd/sw-performance-m1-terrain-small/` passes same-dimension cold/warm
region switching and save/restart with one real owner and the map method.
`/Volumes/ssd/sw-performance-m1-terrain-medium-all/` passes the equivalent ten-owner
sequence and restores **all five methods** after restart. These are standalone
functional checks, not timed performance acceptance.

The native probe copies every actual `MapItemSavedData.colors` byte on the owner
thread. A warm region must contain nonzero terrain and stabilize across repeated
native observations; the client must have received every pixel and reconstruct
the matching CRC32. Switching to never-explored B while viewers remain in A must
produce exactly 16,384 zero bytes on both server and client. The outgoing terrain
file must have independently expected region metadata and exactly the bytes
observed before the switch. Returning to A restores those exact bytes. Exploring
B and switching back produces a separate exact saved B reference.

After an active-session quit and native save/stop, the same server reopens and
the same offline UUIDs reconnect. Session restoration occurs on join before a new
navigation command: native targets, method sets, owner map IDs, item counts,
compass destinations, map cursors and corresponding client live displays match.
All A pixels remain byte-for-byte exact; switching to B restores its saved bytes
and clears/replaces the client map appropriately. The ten-owner run retains 30
terrain snapshots (including the initial blank region), hashes every saved file,
and records ten passing owner assertions at every terrain/restoration step.

Both active-session disconnects in the ten-owner run clear sessions, tasks,
bossbars, network-display maps, renderer targets, target indices and tick counters
to zero after ten further native ticks, while retaining ten legitimate owner-map
cache entries. This passing lifecycle does not erase the prior stop-then-quit
failure: the early return applies when the session has already been removed.
The `all` command leaf is now positively exercised before quit, with actual
all-method state and client effects. No rendered-color or visual-layout claim is
made from plaintext packet observations.

RED evidence remains under `/Volumes/ssd/sw-performance-m1-terrain-red/`: native
pixel observation was absent before the probe extension. New contract tests
reject changed region metadata, changed pixel bytes, incomplete client pixels,
a stale checksum, changed owner/map identity, lost/duplicated method items and
mismatched native/client text-display identity. Current verification: 108 Python
tests and the Paper probe build pass; the 17 Rust tests are unchanged and green.

## Actual death, respawn and world transitions

`/Volumes/ssd/sw-performance-m1-lifecycle-small-final/` and
`/Volumes/ssd/sw-performance-m1-lifecycle-medium-2/` pass with one and ten real
actors. The probe is explicitly armed to observe normal native lifecycle events;
it does not synthesize death/respawn/world-change events. Vanilla `kill` triggers
death, and Azalea's normal automatic-respawn plugin sends the respawn request.
All five navigation methods are active throughout.

For ten actors, native receipts show ten deaths, ten respawns and twenty world
transitions, with no dropped observations. Death drops preserve the independently
seeded ordinary stone/diamond identities and counts (640 stone and 30 diamonds in
aggregate), while direct tagged navigation items reach neither the death drops
nor world item entities. Ordinary items may legitimately remain as drops or have
been picked up after respawn; their combined counts are checked before the world
travel phase. No nested carrier or inventory-cursor death variant is claimed.

After respawn, each owner has exactly one compass and one map, its original map
ID and target, and matching native/client bossbar, actionbar and text-display
state. Actual Nether travel and Overworld return retain those method targets and
item counts and replace the packet-only text display as its world changes.
These observations concern packets/native state, not visual layout or rendering.

The landing fixture synchronously loads and force-loads four native Nether
chunks before placement. It reads back all 625 bedrock floor blocks, 288 wall
blocks and 2,645 clear interior blocks, then uses distinct player positions inside
the enclosure. The prepared-state receipt must pass before teleporting an actor.

Retained earlier attempts:

- `lifecycle-red`: native lifecycle observation operation was absent.
- `lifecycle-small`: the packet observer incorrectly cleared text displays on
  every respawn and the helper incorrectly required a new display ID. Pinned
  Minecraft 1.21.11 `ClientPacketListener.handleRespawn` retains the same level
  for same-dimension respawn, and the native player entity ID is reused. A Rust
  regression now distinguishes same-world respawn from actual world replacement.
  No original-release text-display defect is inferred from that observer failure.
- `lifecycle-small-2`: the corrected one-player lifecycle passes with the earlier
  console landing setup; it is superseded for fixture validation by the final root.
- `lifecycle-medium`: death/respawn/conservation pass, but console `fill` ran before
  destination chunks loaded, leaving no pad; subsequent falls invalidate that
  fixture's world-transition case. Raw failures and extra death receipts remain.
- `nether-fixture-red`: verified native landing operation was absent before its
  implementation.

Current verification: 109 Python tests, 18 Rust tests and the Paper probe build
pass. Native/probe archives remain isolated and helper processes are stopped.
No production source or localization changed. Full physical protection, deletion,
cross-dimension terrain, eviction/shared-map viewers and timed CLI integration
remain open, so Task 11 and M1 are still in progress.

## Physical drop, swap and player-menu protection

`/Volumes/ssd/sw-performance-m1-protection-small-final/` and
`/Volumes/ssd/sw-performance-m1-protection-medium/` pass nine normal protocol
interaction cases for one and ten owners. These use normal selected-slot,
player-action and container-click packets; no Bukkit event is synthesized.

1. Compass drop is cancelled and the original owner retains one compass/map.
2. Map drop is cancelled with the same exact item/target invariants.
3. Compass swap to offhand is allowed; the native compass occupies slot 40.
4. Swap back is allowed and restores its original held slot.
5. A protected item may move into its owner's ordinary storage slot 9.
6. An attempt to move it into armor is cancelled.
7. An attempt to move it into the empty player crafting-result slot is cancelled.
8. An ordinary stone storage move remains allowed.
9. An ordinary three-diamond drop remains allowed and becomes an actual world item.

The controller confirms each selected hotbar slot on the native server before
sending a drop/swap. Every case requires matching native actor/slot/cancellation
receipts, all five owner method targets and live effects, exact item counts/map
IDs, no invalid protected locations, and full ordinary identity/count conservation.
The probe samples world items and player inventories within the same owner-thread
command so an intervening pickup cannot appear to lose or duplicate items.
Player-menu close receipts independently show clean top inventory and cursor.
The crafting-result attempt is not a test of an actual protected crafting recipe.

The ten-owner run records 30 drop, 20 swap, 80 click, 40 close and 170 inventory
slot-change receipts, with zero dropped observations. Twelve protected slot edits
are observed per actor. All nine case decisions and all 90 owner assertions pass.
The ten ordinary dropped item UUIDs match uncancelled native spawn receipts and
actual world entities, each containing three diamonds; aggregate native world
diamond count is 30. No protected world item is present.

Spawn event counts alone are insufficient: vanilla `give` can create transient
animation items (`GiveCommand` calls `makeFakeItem`). The positive control therefore
requires the actual drop UUID to appear in the native world snapshot. Conversely,
cancelled protected Q drops do not reach `ItemSpawnEvent`; direct/nested protected
spawn handlers still need their own scenarios.

The bot now accepts explicit player-menu slots 0–45 and validates them without
silently substituting defaults for malformed input. Drop mode must be boolean;
unknown physical actions are rejected. Non-command action timing remains explicitly
client-issued, not an acknowledged performance latency sample. Native RED roots
retain missing actions (`protection-actions-red`) and missing native receipts
(`protection-native-red`). The earlier `protection-small` run also passes the nine
cases before the stronger world-entity/offhand assertions were added.

The action API's original caller was updated to send explicit inventory slots.
`/Volumes/ssd/sw-performance-m1-physical-action-regression-peaceful/` completes native
1/10/50-player identity, movement, inventory, command and disconnect checks, but
its fifty-player phase records three watchdog nonresponse reports and 134 native
ERROR log entries, including stack lines. The thread dumps are in native command
tree serialization (`CommandNode.equals`, `ClientboundCommandsPacket` enumeration/
entry creation). The logger stability decision is **FAIL** despite completed
actions. The number of reports does not establish three distinct stalls. This
controlled stress observation occurred while another functional helper shared
the host and does not attribute the production incident. A subsequent isolated
`/Volumes/ssd/sw-performance-m1-action-health-guard/` run completes all 1/10/50
action checks with a PASS native log-health result. That result does not override
the prior retained FAIL or establish a reproducible production cause.

The prior `physical-action-regression` root retains the 50-player failure: a slime
killed one bot, invalidating its inventory fixture. The disposable smoke world
now explicitly uses peaceful difficulty; no release source repair was made.

Current verification: 115 Python tests, 20 Rust tests, Paper probe build and archive
isolation pass. The two physical protection roots have no ERROR/SEVERE entries.
A streaming native-log guard now rejects ERROR/SEVERE logger entries and watchdog
reports independently of successful action checks; missing logs are BLOCKED.
Tests ensure INFO payload words are not mistaken for logger severity. The bot
smoke retains a separate health result and fails its process result when native
stability fails. The physical helper also requires its native log health to pass.
`/Volumes/ssd/sw-performance-m1-protection-health/` completes the nine one-owner
cases with the new health field recorded as PASS.
Applying this stability guard to every prepared case remains Task 16 work.
Full drag, open/other-container, actual crafting/cartography, nested carrier, equipment/frame,
block interaction and pickup variants remain pending, together with remaining
terrain/CLI/timing work. M1 remains in progress.

## Independent nested-carrier spawn checks

`/Volumes/ssd/sw-performance-m1-nested-small/` executes all eight native spawn
recipes with one owner: direct protected compass, protected map in a shulker,
one-layer compass bundle, mixed shulker/two-layer map bundle, depth-eight compass
bundle, depth-nine map bundle, plain shulker and plain depth-nine bundle. Seven
decisions pass and the depth-nine case fails. Native logger health passes. The
earlier `sw-performance-m1-nested-red` root retains the absent fixture operation.

`/Volumes/ssd/sw-performance-m1-nested-medium/` repeats all eight recipes for ten
owners: 70 PASS and ten FAIL decisions, exclusively the depth-nine cases. All 800
per-case owner checks pass with ordinary inventories conserved, no observation
overflow and every owned fixture entity removed after recording its decision.
All cases execute despite earlier failures, native logger health passes, and the
helper exits 1. Archived controller inputs match their recorded hashes. The
one-/ten-owner failures are retained separately; neither is a passing suite run.

These are owned server fixtures created by native `World.dropItem`, with actual
`ItemSpawnEvent` receipts and world entity lookup. They clone an existing live
owner's tagged item; no Bukkit event is synthesized. This does not demonstrate
that a player can construct the carrier through protected inventory interactions.
Each carrier round-trips through the native item codec before spawning. The
depth-nine bundle fits vanilla bundle capacity; nested bundles are supported by
the pinned Minecraft `BundleContents` representation.

The observation oracle reads the public navigation PDC tag and Bukkit bundle/
container metadata independently of the installed detector. Its diagnostic limits
(depth 64, 4,096 nodes) throw on exhaustion rather than classifying unvisited data
as clean. Expected clean carriers are constructed separately from fixed recipes;
they never consume release sanitizer output. Exact serialized root hashes preserve
ordinary child identities/counts and carrier display names, lore, PDC markers and
container metadata. Raw before/after native bytes and full graphs are retained.
Passive lifecycle classification also uses this independent reader, preventing a
release detector defect from hiding an escaped descendant in world observations.

**Original-release depth-nine FAIL:** the native codec accepts the fixture, the
uncancelled spawn remains in the world, and the independent before/after graphs
each contain one protected map plus nine ordinary diamonds. Before/after root
hashes are identical; the separately constructed clean hash differs. The installed
release's `containsNavigationItem` reports false. Its recursive detector returns
false when `depth > 8`, and `onItemSpawn` gates cleanup on that detector. Depth
eight is cleaned successfully. The diagnostic detector result is recorded but is
never used as the correctness oracle. No production repair has been made.

Direct protected spawn cancellation passes; shulker/mixed/depth-one/eight cases
preserve every declared ordinary child and carrier byte after protected removal.
Both plain controls retain their exact metadata/contents. Cases continue after a
failure, and owned fixture entity removal occurs only after the decision is
recorded. Cleanup and later controls cannot override an earlier failure.

Verification: 118 Python tests and the Paper probe build pass; the Rust observer
is unchanged from its 20-test passing checkpoint. The nine-case native physical
regression at `/Volumes/ssd/sw-performance-m1-independent-graph-regression/` passes
with the independent lifecycle reader and PASS native log health. Probe/production
archives have zero overlapping classes, the runtime probe hash matches, and owned
servers/bots are stopped. No production or localization changes were made.

These are functional observations, not timed navigation acceptance. Other
protection hooks, carrier inputs, deletion/terrain boundaries and prepared CLI
integration remain pending; Task 11 and M1 remain in progress.
