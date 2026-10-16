# Private and local waypoints

Status: design documented on 2026-10-16; implementation has not started.

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
- [Implementation plan](plans/2026-10-16-private-waypoint.md)
- [Validation checklist](validation/2026-10-16-private-waypoint-checklist.md)

## Delivery stages

1. Collection identity, owner-specific server storage, private commands, permissions, and navigation.
2. Owner-only synchronization plus independent local storage, profiles, and teleport commands.
3. Manager views, source labels, creation/copy routing, rendering, and live validation.

The implementation plan records the task dependencies. None of these stages is marked complete,
and the validation checklist is a record of required checks, not evidence that they passed.

## Related guidance

- [Feature documentation index](../README.md)
- [Waypoint manager](../waypoint-manager/README.md)
- [Waypoint forms](../waypoint-form/README.md)
- [GUI development guide](../../tips/gui/local-guide.md)
- [Adventure text guide](../../tips/adventure-text.md)
