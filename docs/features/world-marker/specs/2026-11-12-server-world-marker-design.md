# Server-side world marker contract

Date: 2026-11-12. Release: 4.0.1. Status: implemented behavior, formalized as a regular navigation method. Validation status is tracked [separately](../validation/2026-11-12-formalization.md); this status does not imply completed visual or platform-matrix acceptance.

## Intent and scope

Let a player on a server without the client mod see an in-world direction marker for their selected waypoint. The marker is private to that player and requires no resource pack. It uses vanilla display entities sent over the player's connection, without inserting entities into the server world or loading the destination chunk.

This contract records the existing implementation. Formalization updates discovery, documentation and release notes; it does not change commands, defaults, session storage, entity layout, motion prediction or diagnostics. No backward-compatibility implementation is added.

The marker is one method in the shared navigation session, with one selected Shared or Private target per player. Private collection access still follows the owner rules. Client-local profiles, remote catalog entries, rendering all saved waypoints, cross-server handoffs and the client mod's marker settings are outside this feature. The independent `text_display` HUD remains available alongside `world_marker`.

## Retained approach

The existing implementation sends absolute entity-position packets for two text rows and an optional item row. This preserves per-owner visibility and a small fixed entity group without passenger attachments. It costs two or three position submissions per active player per tick.

Historical direct-riding and carrier-chain approaches are recorded in the [feature history](../README.md#historical-experiments). Riding followed local player movement but depended on server-updated offsets/orientation; carrier chains required helper entities and left smoothing questions unresolved. Those approaches were removed. Formalization retains the already selected one-tick position updates, one-tick interpolation where available and bounded horizontal motion forecast.

## Commands and permissions

| Command | Behavior |
| --- | --- |
| `/wp navigate <dimension> <list> <waypoint> world_marker` | Select the target with the marker as the requested method. |
| `/wp navigate use world_marker` | Add the marker to the current navigation session. |
| `/wp navigate disable world_marker` | Disable the marker while retaining the selected target and session settings. |
| `/wp navigate disable` | Stop navigation and clear its persisted session. |
| `/wp navigate config world_marker size [<multiplier>]` | Read or set marker size for the selected session. |
| `/wp navigate config world_marker distance [<blocks>]` | Read or set the projection radius for the selected session. |
| `/wp navigate config world_marker` | Report active handler counters and mean handler time, or the inactive message. |

`/wp private navigate` supplies the equivalent commands for Private waypoints. Shared commands require `navigate`; Private commands require `privateNavigate` and access to the collection. Configuration requires a player and a selected navigation session. Size and distance can be configured while `world_marker` is disabled. Setting them does not implicitly enable it. Changes reach an active marker on its next update.

The navigation chat panel exposes a World marker toggle. `all` includes every supported method, including this one. `defaultNavigationMethods` accepts `world_marker`; the built-in default remains `actionbar`.

## Settings and persistence

| Setting | Default | Inclusive range | Effect |
| --- | --- | --- | --- |
| `size` | `1` | `0.25`–`4` | Scale the badge, distance, optional item and local row offsets together. |
| `distance` | `12` blocks | `1`–`32` blocks | Set the eye-to-marker projection radius for distant targets. |

Both values accept fractional numbers. The session model rejects non-finite or out-of-range values. Reading without a value reports the current value; setting `size 1` or `distance 12` restores that setting's default.

Values belong to `NavigationSession`, survive target changes and method toggles, and are encoded with the selected waypoint identity and methods through `NavigationSessionCodec`. Reconnect restoration resolves the current waypoint data and restores these values. They are not independent player preferences: stopping navigation clears the saved session, and a new session starts with the defaults. Existing decoder behavior for absent settings is preserved.

## Presentation and projection

The rows run from top to bottom:

| Row | Content and style | Default local Y offset | Default scale |
| --- | --- | --- | --- |
| Optional item | Registered item icon in GUI display context | `+0.8` blocks | `0.4` |
| Badge | Saved initials; half-opacity waypoint-coloured background; shared contrasting foreground; no shadow | `+0.3` blocks | `1` |
| Distance | White straight-line distance; default text-display background and shadow | `0` | `0.8` |

All rows use CENTER billboarding, full brightness and the same absolute anchor. Text rows are see-through. Item models retain normal depth rendering and can be hidden by blocks. Missing icons, VoxelMap images and unregistered/non-item IDs omit the item row. Switching to such an icon removes an existing item display. An item icon supplements the initials badge rather than replacing it.

The destination anchor is the waypoint block centre `(x + 0.5, y + 0.5, z + 0.5)`. From the predicted observer eye, a farther target projects along the eye-to-target ray to the configured radius. A target within that radius remains at the actual block centre. Rows remain visible at close range without distance-based scaling. Cross-dimension navigation removes the marker until the player returns to the target dimension. Off-screen targets stay off-screen; there is no edge arrow.

Vanilla fonts, text-display padding and client item models affect final appearance. Matching the client mod's row order and colours is a design contract, not proof of pixel-identical rendering. Client markers and this server marker can both be visible when both features are enabled.

## Update and lifecycle contract

1. Enable creates the two text displays and resolves the optional item display for the owner. Entities are packet-only and unmounted.
2. Each subsequent world tick sends one absolute position packet per active part, including while stationary and for sub-centimetre motion. Repeated callbacks in the same tick do not duplicate position packets. Spawn packets supply initial positions.
3. Badge/distance text, locale, colour, icon and size are cached. Metadata is submitted when relevant state changes. Size updates existing rows without respawning them; an icon change can replace the optional item.
4. Dimension mismatch removes all parts and clears motion history. A changed player entity ID or world identity recreates the group. Returning to the target dimension restores the rows.
5. Disable, stop, disconnect and shutdown release display and motion state. A disconnected player can be cleaned up by UUID without retaining its platform player object.

`WorldMarkerMotion` samples successive server-observed eye positions. It forecasts horizontal motion by one interpolation tick plus the measured round trip converted at 20 TPS, with latency clamped to 200 ms. Lead is bounded to five ticks and two blocks. Stationary movement, discontinuities beyond the accepted step, sample gaps longer than three ticks and player/world replacement reset the forecast. Eye height is current rather than extrapolated. Direction changes, latency and jitter can still cause visible corrections.

Other live navigation displays retain their shared five-tick interval. On those ticks they share the marker's navigation snapshot rather than each computing their own.

## Platform and module boundaries

- `common`: `NavigationMethod`, session/settings persistence, command grammar, chat feedback, projection/motion and `AbstractWorldMarkerNavigationHandler` lifecycle. No Minecraft display entity types belong here.
- `mods`: `ModWorldMarkerNavigationHandler`, registered by `ModNavigationRuntime`, implements Minecraft entity creation, registry lookup, display configuration and owner packet delivery for Fabric, Forge and NeoForge.
- `paper`: `PaperWorldMarkerNavigationHandler`, registered by `ServerWaypointPaperMC`, implements its own display configuration and packets. Paper and modded adapters are independent.
- `cross-server`, `proxy-common` and `velocity`: no marker renderer or marker lifecycle ownership.

Versioned adapters follow `settings.gradle.kts` and their Stonecutter predicates. Minecraft 1.20.1 lacks `teleport_duration`; it keeps vanilla interpolation behavior while retaining per-tick position updates. Platform registration establishes intended availability, not completed native testing on every supported target. No active Stonecutter version is changed by this formalization.

## Diagnostics and cost

At 20 TPS the group submits 40 position packets per second without an item, or 60 with one, in addition to spawn/removal and changed metadata. These are submission counts, not socket-delivery or client-rendering measurements.

`/wp navigate config world_marker` reports group updates, aggregate spawns, position/metadata/removal submissions and mean handler time since enable. Timing includes creation, text conversion and packet submission; it excludes rendering, network delivery and the rest of the server tick. Instrumentation uses two `nanoTime` calls per update. Disabled handlers have no active diagnostics. Formalization retains this existing behavior.

## Acceptance and future changes

The [validation record](../validation/2026-11-12-formalization.md) maps the current automated checks and historical native receipts to this contract. Remaining acceptance includes a rendered client check of the final row order/block-centre anchor and configurable values, current native lifecycle/owner-isolation checks, and additional platform/version runtime evidence. Historical packet receipts cannot establish current rendered appearance, full platform support, capacity or server tick-percentile performance.

Future behavior changes should receive a plan under this feature's `plans/` directory and update this contract and its validation evidence together. Avoid silently reintroducing comparison displays, helper carriers, chunk loading, shared entity visibility or coupling to the mounted HUD.
