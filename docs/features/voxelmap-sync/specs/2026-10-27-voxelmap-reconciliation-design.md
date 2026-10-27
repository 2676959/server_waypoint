# VoxelMap reconciliation after synchronization

The selected repair adapts the unchanged-handshake and readiness changes from
minecart551-prog/server_waypoint commit `17de12439018224fd4f2b52baf1e9167224f4e7d`.
The user authorized the recommended adaptation after the fork review.

## Behavior

- An unchanged remote handshake reconciles VoxelMap's managed markers with the
  current Server Waypoint model when VoxelMap auto-sync is enabled.
- Xaero keeps its existing unchanged-handshake behavior. Integrated worlds retain
  the existing guard against automatic full synchronization on entry.
- VoxelMap is ready only when its manager and dimension manager exist and its
  active world is the same non-null client level Minecraft is using.
- Automatic events received before readiness coalesce into one full reconciliation
  from the latest model. Client ticks retry while pending; no marker rebuilding
  happens when idle. Join/leave clears pending work, and disabling auto-sync drops it.
- Explicit downloads and Sync Now retain their existing preference-independent
  behavior and use the stronger readiness predicate.

## Approach

Immediate reconciliation can write into VoxelMap's previous world. A null-only
readiness check cannot detect that lifecycle state. Use a small mods-only
coordinator for deferred automatic work and a client-only accessor for VoxelMap's
private world reference. This avoids timing guesses and changes to map renderers.

## Constraints and validation

Java 17; preserve Stonecutter loader guards; no protocol, localization, reload,
alias, icon, or backward-compatibility changes. Test unchanged reconciliation,
readiness transitions, latest-model coalescing, preferences, integrated entry,
session cancellation, and the accessor target. Automated tests do not prove live
Minecraft world transitions or marker rendering.
