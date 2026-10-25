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
