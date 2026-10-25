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
  diagnostics, not yet a save/restart terrain correctness assertion.

## Executed functional checks

`/Volumes/ssd/sw-performance-m1-navigation-small-targets/` and
`/Volumes/ssd/sw-performance-m1-navigation-medium-targets/` execute all 31 nonempty
subsets of the five discovered methods for one and ten real vanilla-protocol
Azalea players. Each actor has every requested method and its expected native/
client effects; unselected methods have no active item/display effect. The map
cursor is independently expected at `(-96, -96)` for target `(320, -61, 320)` in
the `(512, 512)` region at scale 2. The named `all` command is a separate pending
command-leaf requirement; enabling all five through individual method commands
does not claim that leaf was executed.

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
Death/respawn, world changes, active-session reconnect/restart restoration,
deletion, map terrain persistence/eviction/multiple viewers and the remaining
native protections still require evidence. Main CLI adapters remain blocked
until their complete contracts are implemented.
