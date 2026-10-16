# Private and local waypoint validation checklist

Status: **not run**. Created on 2026-10-16 as part of design documentation.
Implementation has not started. An unchecked item below is a required scenario, not a result.

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

- [ ] Collection keys reject an owner on Shared and a missing owner on Private.
- [ ] Owner stores persist/reload independently and never enter shared snapshots/catalogs.
- [ ] Default quotas are 1,000 waypoints/100 lists; zero is unlimited; negative values fail validation.
- [ ] Competing additions in different dimensions cannot exceed an owner's quota.
- [ ] Rejected automatic list creation leaves neither a list nor a waypoint behind.
- [ ] Reduced quotas retain data and allow metadata edits, renames, and deletions.
- [ ] Restore is owner/collection-bound, expires after ten minutes, and is single-use.
- [ ] Failed quota/name-conflict/wrong-owner restores do not consume the valid owner's token.
- [ ] Navigation edit/rename/remove/persistence distinguishes all duplicate identities.
- [ ] Scope survives all relevant codec round-trips and revision-recovery/tombstone paths.
- [ ] Invalid scopes, Private uploads, and incompatible custom-message requests are rejected.
- [ ] Private snapshot/edit delivery goes only to the authenticated owner.
- [ ] Private read-permission loss clears private presentation without clearing other sources.
- [ ] Late messages and owner eviction cannot apply to a replacement connection/store.
- [ ] Read/save errors do not overwrite unread data or claim durable persistence.

## Local profiles and operations

- [ ] First-use Default, reconnect, rename, and per-profile waypoint persistence work.
- [ ] Host/port, account, and singleplayer-save identities are isolated.
- [ ] Filesystem keys cannot collide through display names or lossy address replacement.
- [ ] Known backend identities select their bound profile.
- [ ] A new identity binds an unused fallback or creates a distinct empty profile.
- [ ] Manual selection wins over a late handshake; bindings survive reconnect.
- [ ] Unsupported backend switches behind one address can be handled through manual selection.
- [ ] Proxy transfer saves the outgoing profile without needing a disconnect event.
- [ ] Profile switch save failure leaves the outgoing profile active and reports the error.
- [ ] Profile creation/rename/deletion and last-profile fallback affect only Local.
- [ ] Local create/edit/remove works without server commands or custom waypoint payloads.
- [ ] Local remains usable on unsupported and protocol-incompatible servers.
- [ ] Changing profile/account/connection invalidates a form destination while preserving its draft.
- [ ] Default/custom teleport expansion handles negatives, yaw, dimension IDs, and non-English locales.
- [ ] Blank commands, invalid tokens, control characters, and excessive command length are rejected.
- [ ] Cross-dimension teleport is unavailable for templates missing the dimension placeholder.
- [ ] Command submission is reported separately from actual arrival.

## Live server-private checks without a client mod

- [ ] Alice creates, lists, views, edits, and removes Private waypoints through commands/chat.
- [ ] Bob cannot discover Alice's entries through menus, suggestions, details, or restoration.
- [ ] Alice's private changes produce no shared player announcement or Bob client update.
- [ ] Alice and Bob can independently use identical private list/waypoint identifiers.
- [ ] Private menus, help, property edits, pagination, and Resume links retain the private prefix.
- [ ] Private navigation uses existing methods and restores the correct target after reconnect.
- [ ] Shared/private retargeting uses one navigation session and cleans up the previous presentation.
- [ ] Default ordinary-player private teleport is denied; an explicit grant enables it.
- [ ] Authorized teleport arrives at the intended dimension/coordinates, not a same-named Shared entry.
- [ ] No-player command execution fails safely; privileged execute-as follows documented semantics.
- [ ] Quotas/reload/restore failures provide clear feedback and preserve valid data.
- [ ] Repeat relevant checks on Paper/Folia and a mod-loader backend.

## Live client manager, copying, and markers

- [ ] Source switching uses an `IconDropdownMenu` with Shared, Private, Local, and Combined.
- [ ] Combined is the initial view; changing it persists the selected view.
- [ ] Combined rows, list headers, and selected details show readable source labels.
- [ ] Equal names stay distinct in grouped/flat search, sorting, selection, and collapsed state.
- [ ] Existing Remote browsing remains separate and its data never enters Combined.
- [ ] Local/Combined work while Shared/Private is loading, unsupported, incompatible, or denied.
- [ ] Single-source creation defaults to its source; Combined creation defaults to Local.
- [ ] Add destination selection and profile management remain clear at constrained GUI sizes.
- [ ] Edit/remove actions retain the selected source after changing the dropdown.
- [ ] Copy across available collections preserves supported metadata and leaves the source intact.
- [ ] Copy cancellation/name conflict/quota/permission denial creates no destination entry.
- [ ] Connection/profile change while a form is open cannot redirect a save.
- [ ] Enabled markers from all current collections render independently of manager view.
- [ ] Equal-name/equal-coordinate markers retain independent visibility and action identity.
- [ ] Shared refresh and private tombstones do not remove Local markers.
- [ ] Verify marker persistence after leaving and rejoining an unmodified server.
- [ ] Test popup hover suppression, scrolling, keyboard focus, narration, Escape, and dialogs.
- [ ] Inspect constrained viewport and long translations for clipping/overlap.
- [ ] Local works with neither Xaero's Minimap nor VoxelMap installed.
- [ ] Existing Shared map integration works and receives no Local/Private mutations.

## Builds, translations, and documentation

- [ ] Common tests pass.
- [ ] Active Fabric tests and an applicable older-version client suite pass.
- [ ] Representative Fabric, Forge, NeoForge, and Paper targets compile.
- [ ] Stonecutter scopes remain balanced and configured active targets remain intact.
- [ ] Applicable server/client locales contain every new key with consistent placeholders.
- [ ] Fluent-language review is recorded separately from key/placeholder coverage.
- [ ] GUI API documentation, server guide, feature indexes, and relative links are current.
- [ ] Whitespace checks pass; unrelated changes are preserved.

## Results

No feature tests, builds, gameplay checks, or translation reviews have been executed for this
feature. Documentation link/whitespace checks performed when authoring these files do not change
that status. Replace this paragraph with dated evidence only after implementation and validation.
