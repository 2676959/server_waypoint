# Cross-server teleport permission feedback

When destination preparation fails, send the player the destination display name and the actual
`tp` and `remote.tp` results. For example:

`✘ Remote teleport failed: permission check failed in Survival: tp ✘ · remote.tp ✔`

Each status label and glyph is red when denied and green when granted. The separator is dark gray.
The phrase is translated using MessageFormat `{0}` for the server name; permission labels and
status glyphs are literal Adventure components. Both checks run independently, including when
`tp` fails. Paper/LuckPerms preserves explicit node decisions and the existing operator fallback;
mod loaders preserve each node's own configured fallback level.

The destination checks both permissions before transfer and again on the arriving player's owner.
A source permission revocation still gates only `remote.tp` and can report both source node results.
Identity, catalog, provider failure, timeout, and routing errors keep their ordinary result messages.
Unknown results are never shown as a permission grant or denial.

Application protocol 1 adds nullable permission decisions to `HANDOFF_REJECTED`. The proxy accepts
these details only from the admitted destination and forwards them to the correlated source.
All backends and the coordinator need matching builds; retain explicit `protocolVersion` settings
at `1`. Older builds with the same application version do not understand the added permission flags. The Minecraft custom-payload version is independent and unchanged by this feature.

Automated validation covers codec strictness, proxy forwarding, destination denial and arrival
revocation, offline provider decisions, and the command feedback text and colors. Live player
transfer and chat rendering require a Minecraft/Paper/Velocity session.

## Audit logging

The remote teleport action audit and backend source/destination completion logs include a plain
English reason such as `Remote teleport failed: permission check failed in survival: tp ✘ · remote.tp ✔`.
Logs use the stable server ID and preserve existing player UUIDs, original sender identity, request
IDs, and result fields. Only actual node-check failures include permission details; other outcomes
use an empty reason marker. Correlated handoff rejection activity also logs independent `tp` and
`remote.tp` boolean fields.
