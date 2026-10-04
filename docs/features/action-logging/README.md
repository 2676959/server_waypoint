# Player action logging

Server consoles receive INFO messages in the `server_waypoint.actions` category. Every entry includes
`action`, `player`, `player_id` (the executor's UUID), `sender`, `sender_id`, and `outcome`.
For commands, `sender` identifies the original command source while `player` identifies the executor.
For example, when Steve runs `/execute as Alex run wp ...`, the log includes Alex's name and UUID
in `player`/`player_id` and Steve's name and UUID in `sender`/`sender_id`. A console-issued command
retains the console as its sender even when it executes as a player. Non-player identities use
`non-player` in their UUID field. Direct GUI actions use the player for both identities.

Paper resolves the original sender from `CommandSourceStack.getSender()`. Mod servers resolve the
original `CommandSource`, independently of the executor overwritten by `/execute as`. Console, RCON,
and function sources are named explicitly; custom and command-block sources without an exposed
name are identified by source class and instance. Both identities are captured before asynchronous
work. Upload requests retain the initiating command sender through completion, failure or expiry.

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

- [Initial validation](validation/2026-10-03-action-logging.md)
- [Command sender validation](validation/2026-10-03-command-sender.md)
- [Plans](plans/)
- [Specs](specs/)
