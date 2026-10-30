# Private and local waypoints

Status: implementation in progress. Collection-aware navigation and owner storage (plan Tasks 1–2)
were verified on 2026-10-23; private commands (Task 3) and owner-only synchronization with separate
client caches (Task 4), independent Local profiles/persistence (Task 5), and source-aware client
operations/teleport templates (Task 6) on 2026-10-28. Collection manager views, destination/profile
controls, copying and composed markers (Task 7) were verified automatically on 2026-10-29.
Task 8 live acceptance remains pending.

This feature adds two independent waypoint collections alongside existing shared server waypoints:

- **Private:** the server stores each player's own lists, usable through commands and chat without
  a client mod.
- **Local:** the client stores its own lists and renders them on any server, without requiring
  Server Waypoint on the server or another map mod.

The manager uses an `IconDropdownMenu` with **Shared**, **Private**, **Local**, and **Combined**
views. Combined labels each entry's source. Explicit copying creates independent waypoints;
there is no automatic mirroring between collections.

## Documents

- [Design specification](specs/2026-10-16-private-waypoint-design.md)
- [Local profile list design](specs/2026-10-30-local-profile-list-design.md) and its
  [implementation plan](plans/2026-10-30-local-profile-list.md)
- [Implementation plan](plans/2026-10-16-private-waypoint.md)
- [Validation checklist](validation/2026-10-16-private-waypoint-checklist.md)
- [Private command validation](validation/2026-10-28-private-commands.md)
- [Owner-only synchronization validation](validation/2026-10-28-private-synchronization.md)
- [Local profile persistence validation](validation/2026-10-28-local-profiles.md)
- [Client operation routing validation](validation/2026-10-28-client-operations.md)
- [Command performance validation](validation/2026-10-28-command-performance.md)
- [Local profile list validation](validation/2026-10-30-local-profile-list.md)
- [Config-style Local profile rows](validation/2026-10-30-profile-hybrid-rows.md)
- [Local profile layout refinement](validation/2026-10-29-local-profile-layout.md)
- [Collection manager and composed-render validation](validation/2026-10-29-collection-manager.md)
- [Execution evidence and progress](validation/execution/progress.md)

## Delivery stages

1. Collection identity, owner-specific server storage, private commands, permissions, and navigation.
2. Owner-only synchronization plus independent local storage, profiles, and teleport commands.
3. Manager views, source labels, creation/copy routing, rendering, and live validation.

The implementation plan records the task dependencies. Stage 3 implementation is complete;
its live validation and the remaining Task 8 documentation/acceptance work are pending. The
validation checklist records required checks, not evidence that they passed.

## Related guidance

- [Feature documentation index](../README.md)
- [Waypoint manager](../waypoint-manager/README.md)
- [Waypoint forms](../waypoint-form/README.md)
- [GUI development guide](../../tips/gui/local-guide.md)
- [Adventure text guide](../../tips/adventure-text.md)
