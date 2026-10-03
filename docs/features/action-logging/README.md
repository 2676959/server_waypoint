# Player action logging

Server consoles receive INFO messages in the `server_waypoint.actions` category. Every entry includes
`action`, `player`, `player_id` (the player UUID), and `outcome`. Commands without a player use
`player_id=non-player` and the command sender name.

Coverage includes waypoint and list creation, editing and removal; restore; local teleport;
cross-server teleport requests and destination arrivals; uploads; navigation changes; downloads;
chat waypoint sharing; and reload completion. GUI edits use the same logging format as commands.
Read-only browsing and routine synchronization do not produce action entries.

Mutation success is logged after the save task finishes. `save_failed` means the change occurred in
memory but persistence failed. Teleport success comes from the platform result, including Paper's
asynchronous completion. Existing cross-server coordinator diagnostics retain handoff details and
UUIDs even when the player has left the source server before feedback can run.

Uploads produce a request entry and a summary containing the request ID, source, scope, conflict
policy, dimensions and affected counts. A later dimension failure produces `partial` when earlier
changes committed. Cancellation, expiration, export failures and rejected upload permissions are
identified separately. Uploads do not emit an entry for each waypoint.

Names and other values are bounded and sanitized to keep each entry on one console line.

- [Validation](validation/2026-10-03-action-logging.md)
- [Plans](plans/)
- [Specs](specs/)
