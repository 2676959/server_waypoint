# Disposable cross-server live audit

`TeleportAudit.java` is a test-only Paper plugin, excluded from all Gradle projects and release
artifacts. It logs each non-cancelled `PLUGIN` teleport with player UUID, count and coordinates.
Creating `plugins/TeleportAudit/deny-login` in a disposable backend rejects incoming logins while
leaving its coordinator TCP connection and published catalog alive. Remove that test marker to
restore logins. Never install this plugin or use the rejection marker on a user/production server.

Compile with Java 17 language features against the chosen Paper runtime API/dependencies and
package `TeleportAudit.class` with this `plugin.yml`:

```yaml
name: TeleportAudit
version: 1
main: TeleportAudit
api-version: '1.21'
```

Use two disposable loopback Paper servers, the matching Server Waypoint artifacts, Velocity and
an offline MCC test account. Configure unique backend IDs, matching public pins for KK, or explicit
loopback plaintext on both sides. Grant the test player's backend teleport permissions. Create a
unique local waypoint on each backend and wait for `/wp remote servers` to show availability.

1. Run `/wp remote tp b "minecraft:overworld" Test Target` through the proxy.
2. Require destination `STEP16_TELEPORT ... count=1` and expected coordinates, then confirm actual
   position with `data get entity <test-player> Pos` from that destination's console.
3. Reverse the request and require one event on the other backend.
4. Revoke the test player's destination permission, repeat, and require denial with unchanged count.
5. For failed connection, create the destination rejection marker, request from the source, and
   require failure feedback and source membership with no teleport. Remove the marker and retry.
6. Stop MCC and all three servers gracefully. Preserve logs, runtime versions, artifact SHA-256 and
   assertions without private credentials. Restore test permissions and remove test markers.

The Step-16 run used two Paper 1.21 servers and Velocity 4.1.1 with Minecraft protocol 767.
See [runtime documentation](../../docs/cross-server-velocity-runtime.md) and its archived evidence.
Offline authentication and forwarding NONE in that run are disposable fixture choices, not deployment
recommendations. For native Folia validation, use entity-safe audit storage before enabling concurrent
player tests; this small counter fixture was run only on Paper.
