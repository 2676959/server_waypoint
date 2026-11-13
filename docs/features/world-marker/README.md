# Server-side world marker navigation

Server-only navigation using a private, unmounted marker made from two text displays and an optional item display per player. A vanilla client needs no mod or resource pack. The display is sent only to its owner, never inserted into the server world, and never loads the destination chunk.

`world_marker` is a regular navigation method in 4.0.1. It points toward the selected Shared or Private waypoint. The client mod's independently rendered waypoint markers remain a separate feature.

## Feature documents

- [Feature contract](specs/2026-11-12-server-world-marker-design.md): commands, settings, presentation, lifecycle, ownership and platform boundaries.
- [Formalization validation](validation/2026-11-12-formalization.md): current checks, historical native evidence and remaining acceptance work.
- [Promotion implementation plan](plans/2026-11-12-world-marker-promotion.md): repeatable setting/lifecycle checks, fresh native evidence, platform boundaries and rendered acceptance.
- [Current Paper native acceptance](validation/2026-11-12-promotion-native.md) and [platform/API checks](validation/2026-11-12-platform-checks.md): final layout/settings packet evidence and selected compilation boundaries.

## Use it

```text
/wp navigate minecraft:overworld <list> <waypoint> world_marker
/wp navigate use world_marker
/wp navigate config world_marker
/wp navigate config world_marker size
/wp navigate config world_marker size 1.5
/wp navigate config world_marker distance
/wp navigate config world_marker distance 8
/wp navigate disable world_marker
```

The first command selects a target; `use` adds the marker to an existing session. `/wp private navigate` supports server-private targets too. The navigation panel has a World marker toggle. Built-in defaults remain `actionbar`; `all` includes `world_marker`. Administrators can include `world_marker` in `defaultNavigationMethods`. The separate `text_display` HUD can run alongside it and retains its own riding implementation.

With a target selected, use `size` to see your current multiplier or `size <multiplier>` to set it from `0.25` to `4`. Use `size 1` to restore the default. Your size is saved with the navigation session and follows it when you switch targets or reconnect.

Use `distance` to see your current projection radius or `distance <blocks>` to set it from `1` to `32` blocks. Use `distance 12` to restore the default. Your distance is saved with the navigation session too.

These settings require a selected navigation target, but the marker itself can be disabled while you configure it. They belong to the session: stopping navigation clears the session and its saved settings, so a new session starts with size `1` and distance `12`. Shared commands use the server's `navigate` permission; Private commands use `privateNavigate` and the Private collection's access rules.

The marker follows the client mod's row order, from top to bottom:

1. **Item icon:** a GUI-facing item display using the waypoint's registered item ID. The row is absent for VoxelMap icons, missing icons or unregistered/non-item IDs. Changing to one of these removes an existing item display.
2. **Initials badge:** the waypoint's saved initials, with the client renderer's default half-opacity waypoint-coloured background and shared contrasting text-colour calculation. It has no text shadow.
3. **Distance:** a separate white distance line with the text display's default background and shadow.

All parts use center billboarding, full brightness, `teleport_duration=1` and per-tick position packets. Badge and distance text are see-through. The item uses normal item-model depth rendering. The name/diamond label from the earlier experiment is removed.

The parts share the same projected position and motion forecast. Local display transformations arrange the optional item above the badge and the distance below the badge. At the default size, the badge uses scale 1.0 and a +0.3-block local offset, the distance uses scale 0.8, and the GUI item uses scale 0.4 and a +0.8-block offset. Changing size scales the rows and their offsets together.

The anchor is at the waypoint block centre `(x + 0.5, y + 0.5, z + 0.5)`, matching the client mod. When the waypoint is farther away than your chosen radius, the anchor is projected to that radius along the eye-to-target ray. Within that radius it stays on the waypoint block. Parts remain visible at close range without scaling down. The marker hides in other dimensions and returns in the target dimension. An off-screen target remains off-screen; there is no edge arrow.

## Updates and diagnostics

Each active part receives an absolute entity-position packet every world tick, including while stationary and for movements smaller than one centimetre. Repeated callbacks within a tick do not send additional position packets. Each part's `teleport_duration` is one tick; transformation interpolation is not used for movement. Minecraft 1.20.1 retains vanilla interpolation behavior because it lacks this setting.

Horizontal motion is estimated from successive server-observed eye positions. The observer is predicted ahead by one tick of display interpolation plus the measured client-server-client round trip (converted at 20 TPS), then the waypoint is projected from that predicted eye. The forecast is capped at five ticks and two blocks, using at most 200 ms of measured latency. It resets when movement stops, after large position jumps, after sample gaps longer than three ticks, and when the player entity/world changes. Eye height is kept current rather than extrapolating crouches or jumps. Forecasts remain estimates: abrupt direction changes, jitter and high latency can still produce corrections. This reduces stale-target following delay without changing packet rates or using riding entities.

At 20 TPS, the two text rows submit 40 position packets per second per player; with an item row the total is 60, plus lifecycle and changed label/locale metadata. Other navigation displays retain the shared five-tick update interval and share the marker's snapshot on those ticks.

`/wp navigate config world_marker` reports group updates and aggregate spawns and position/metadata/removal packets across the active parts, and mean handler time since enabling. Timing includes creation, text conversion and packet submission, excludes client rendering, socket delivery and the rest of the tick, and adds two `nanoTime` calls per update. Disable, disconnect and shutdown release all marker parts and their state.

## Historical experiments

These reports retain the experiment terminology and tested layouts of their original revisions. The feature contract above describes the current behavior; the historical reports do not certify later layout, anchor or setting changes.

1. **Entity-position packets:** [initial experiment](validation/2026-11-02-experiment.md), then [independent handlers](validation/2026-11-03-independent-handlers.md). This is the retained approach, now sending every tick instead of every five ticks with a one-centimetre threshold.
2. **Direct riding with display transformation:** [comparison and native evidence](validation/2026-11-03-riding-comparison.md). It followed player motion locally but relied on server-updated offsets/orientation and lacked the position interpolation path. Removed from source.
3. **Carrier chain with native passenger attachments:** [investigation and geometry evidence](validation/2026-11-03-carrier-investigation.md). It could express offsets without the text display's own translation, but required several helper entities and left vertical smoothing and client behavior unresolved. Abandoned; its probe and runner were removed.

[Removal and per-tick verification](validation/2026-11-03-original-per-tick.md) records the preceding single-marker revision. Historical reports describe their tested revisions, not current behavior.

The subsequent [ten-display comparison](validation/2026-11-03-interpolation-comparison.md) tested zero duration against durations matching 1–5-tick position updates. The user selected duration 1 with updates every tick. The grid was temporarily removed; [selected-setting verification](validation/2026-11-03-selected-setting.md) describes that revision. The user then reopened the comparison with only the five matched-duration variants, and subsequently requested shared positioning and normal waypoint text. [Matched-column verification](validation/2026-11-03-matched-column.md) includes this appearance adjustment.

[Motion-prediction verification](validation/2026-11-03-motion-prediction.md) describes the subsequent change to reduce sprint-following delay. The user then removed the other comparison entities; the retained duration-1 marker keeps motion prediction. [Single-marker verification](validation/2026-11-03-single-predicted-marker.md) records that revision. The subsequent [three-part marker verification](validation/2026-11-04-three-part-marker.md) records the current initials/distance/item layout.

## Native smoke runner

```sh
rtk proxy ./gradlew :paper:1.21.11-paper:shadowJar :paper:1.21.11-paper:performanceProbeJar --console=plain
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py \
    --root /absolute/path/to/new-disposable-directory \
    --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar \
    --count 10
```

The candidate-only runner uses the existing pinned local Paper 1.21.11 runtime and Azalea vanilla protocol bots. It checks owner isolation, two text rows and an optional item row per owner, all with duration 1 and one-tick updates, center billboarding, badge colour/contrast, default distance background, GUI item identity and transforms, VoxelMap/no-icon suppression and item removal, absence from the server world, per-display packet cadence while stationary, head turns, walking, a bounded target lead during movement, retarget isolation, simultaneous HUD use, independent disable and cleanup including motion-history state. It retains hashes, snapshots and client label observations in `world-marker.json`, plus logs. It does not render a client, establish a capacity limit or measure server tick percentiles. Its isolated server binds only to localhost and stops after the run.

The promotion checks also exercise minimum/maximum and fractional size/radius settings, stable row IDs during setting changes, near block-centre anchoring, preservation across retargets and reconnects, configuration while disabled, defaults after stopping, and Private owner isolation with fresh client error feedback. A stationary native oracle checks every row against the requested settings and an independent block-centre projection. Protocol observations establish delivery and identity; the runner does not render those rows. See the [execution record](validation/2026-11-12-execution.md) for actual run status.
