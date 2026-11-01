# Private and local waypoint validation checklist

Status: **planned acceptance completed, 2026-10-31**. Implementation Tasks 1–7 are complete.
See the [Task 8 evidence record](2026-10-31-platform-validation.md) for fresh suites, builds,
native client probes and Paper command checks. Checkmarks in the first two groups denote
automated contract coverage; groups explicitly labelled live require live evidence.
The [final acceptance continuation](2026-10-31-final-acceptance.md) records the completed live
checks and their boundaries: native event input, not OS input certification; Codex linguistic
review, not native-speaker sign-off.

References: [design](../specs/2026-10-16-private-waypoint-design.md) and
[implementation plan](../plans/2026-10-16-private-waypoint.md).

## Evidence requirements

For each executed group record the commit/worktree state, Minecraft version, loader/server,
client mod presence, relevant configuration/permissions, exact command or interaction, observed
result, and log/screenshot location. Record limitations and failures explicitly. Never mark a
scenario passed solely because compilation or an unrelated test suite succeeded.

Use disposable test data with Alice and Bob as distinct UUIDs. Give Shared, Alice Private,
Bob Private, and Alice Local the identifiers `minecraft:overworld / Homes / Base` with different
coordinates and descriptions so unintended access is easy to detect.

## Automated ownership, persistence, and protocol checks

- [x] Collection keys reject an owner on Shared and a missing owner on Private.
- [x] Owner stores persist/reload independently and never enter shared snapshots/catalogs.
- [x] Default quotas are 1,000 waypoints/100 lists; zero is unlimited; negative values fail validation.
- [x] Competing additions in different dimensions cannot exceed an owner's quota.
- [x] Rejected automatic list creation leaves neither a list nor a waypoint behind.
- [x] Reduced quotas retain data and allow metadata edits, renames, and deletions.
- [x] Restore is owner/collection-bound, expires after ten minutes, and is single-use.
- [x] Failed quota/name-conflict/wrong-owner restores do not consume the valid owner's token.
- [x] Navigation edit/rename/remove/persistence distinguishes all duplicate identities.
- [x] Scope survives all relevant codec round-trips and revision-recovery/tombstone paths.
- [x] Invalid scopes, Private uploads, and incompatible custom-message requests are rejected.
- [x] Private snapshot/edit delivery goes only to the authenticated owner.
- [x] Private read-permission loss clears private presentation without clearing other sources.
- [x] Late messages and owner eviction cannot apply to a replacement connection/store.
- [x] Read/save errors do not overwrite unread data or claim durable persistence.

## Local profiles and operations

- [x] First-use Default, reconnect, rename, and per-profile waypoint persistence work.
- [x] Host/port, account, and singleplayer-save identities are isolated.
- [x] Filesystem keys cannot collide through display names or lossy address replacement.
- [x] Known backend identities select their bound profile.
- [x] A new identity binds an unused fallback or creates a distinct empty profile.
- [x] Manual selection wins over a late handshake; bindings survive reconnect.
- [x] Unsupported backend switches behind one address can be handled through manual selection.
- [x] Proxy transfer saves the outgoing profile without needing a disconnect event.
- [x] Profile switch save failure leaves the outgoing profile active and reports the error.
- [x] Profile creation/rename/deletion and last-profile fallback affect only Local.
- [x] Local create/edit/remove works without server commands or custom waypoint payloads.
- [x] Local remains usable on unsupported and protocol-incompatible servers.
- [x] Changing profile/account/connection invalidates a form destination while preserving its draft.
- [x] Default/custom teleport expansion handles negatives, yaw, dimension IDs, and non-English locales.
- [x] Blank commands, invalid tokens, control characters, and excessive command length are rejected.
- [x] Cross-dimension teleport is unavailable for templates missing the dimension placeholder.
- [x] Command submission is reported separately from actual arrival.

## Live server-private checks without a client mod

- [x] Alice creates, lists, views, edits, and removes Private waypoints through commands/chat.
- [x] Bob cannot discover Alice's entries through menus, suggestions, details, or restoration.
- [x] Alice's private changes produce no shared player announcement or Bob client update.
- [x] Alice and Bob can independently use identical private list/waypoint identifiers.
- [x] Private menus, help, property edits, pagination, and Resume links retain the private prefix.
- [x] Private navigation uses existing methods and restores the correct target after reconnect.
- [x] Shared/private retargeting uses one navigation session and cleans up the previous presentation.
- [x] Default ordinary-player private teleport is denied; an explicit grant enables it.
- [x] Authorized teleport arrives at the intended dimension/coordinates, not a same-named Shared entry.
- [x] No-player command execution fails safely; privileged execute-as follows documented semantics.
- [x] Quotas/reload/restore failures provide clear feedback and preserve valid data.
- [x] Repeat relevant checks on Paper/Folia and a mod-loader backend.

## Live client manager, copying, and markers

- [x] Source switching uses an `IconDropdownMenu` with Shared, Private, Local, and Combined.
- [x] Combined is the initial view; changing it persists the selected view.
- [x] Combined rows, list headers, and selected details show readable source labels.
- [x] Equal names stay distinct in grouped/flat search, sorting, selection, and collapsed state.
- [x] Existing Remote browsing remains separate and its data never enters Combined.
- [x] Local/Combined work while Shared/Private is loading, unsupported, incompatible, or denied.
- [x] Single-source creation defaults to its source; Combined creation defaults to Local.
- [x] Add destination selection and profile management remain clear at constrained GUI sizes.
- [x] Edit/remove actions retain the selected source after changing the dropdown.
- [x] Copy across available collections preserves supported metadata and leaves the source intact.
- [x] Copy cancellation/name conflict/quota/permission denial creates no destination entry.
- [x] Connection/profile change while a form is open cannot redirect a save.
- [x] Enabled markers from all current collections render independently of manager view.
- [x] Equal-name/equal-coordinate markers retain independent visibility and action identity.
- [x] Shared refresh and private tombstones do not remove Local markers.
- [x] Verify marker persistence after leaving and rejoining an unmodified server.
- [x] Test popup hover suppression, scrolling, keyboard focus, narration, Escape, and dialogs.
- [x] Inspect constrained viewport and long translations for clipping/overlap.
- [x] Local works with neither Xaero's Minimap nor VoxelMap installed.
- [x] Existing Shared map integration works and receives no Local/Private mutations.

## Builds, translations, and documentation

- [x] Common tests pass.
- [x] Active Fabric tests and an applicable older-version client suite pass.
- [x] Representative Fabric, Forge, NeoForge, and Paper targets compile.
- [x] Stonecutter scopes remain balanced and configured active targets remain intact.
- [x] Applicable server/client locales contain every new key with consistent placeholders.
- [x] Fluent-language review is recorded separately from key/placeholder coverage.
- [x] GUI API documentation, server guide, feature indexes, and relative links are current.
- [x] Whitespace checks pass; unrelated changes are preserved.

## Results

On 2026-10-31, the fresh four-suite run executed 2,996 tests with no failures/errors/skips.
Representative Fabric/Forge/NeoForge/Paper compilation passed. Native Fabric probes exercised
collection routing, copy cancellation, profile invalidation, integrated-world persistence and
Spanish manager/profile controls at constrained size; the stock Fabric 1.21.5 map editor suite passed.
Two vanilla-protocol MCC bots exercised Paper ownership, permissions, quotas and actual teleport
arrival. These bots are not a graphical vanilla-client test. See the
[dated record](2026-10-31-platform-validation.md) and its exact logs/probe statuses; in particular,
offline account/Realms errors make some native runner exits nonzero despite passing feature assertions.
The remaining live groups were executed in the [final continuation](2026-10-31-final-acceptance.md),
including graphical vanilla, multiplayer revocation/restoration, Folia/Fabric, real proxy transfer,
marker persistence and all six locale layouts. The continuation documents linguistic review and
the remaining certification boundaries rather than implying universal platform or physical-input coverage.
