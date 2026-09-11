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
folia-supported: true
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
See [runtime documentation](../../docs/features/cross-server/specs/cross-server-velocity-runtime.md) and its archived evidence.
Offline authentication and forwarding NONE in that run are disposable fixture choices, not deployment
recommendations. The audit uses concurrent UUID counters for Folia event threads. `STEP19_JOIN` and
`STEP19_QUIT` record event ownership; a delayed entity task records `STEP19_OWNER`
with live identity and position, or `STEP19_RETIRED` if the player leaves before it runs.
`STEP16_TELEPORT` also records event ownership. These observations do not instrument
the production adapter itself; correlate them with completion feedback and destination
position before claiming successful arrival. Retained counters are for bounded disposable
runs only and are not a production resource-limit implementation.

For controlled Folia retirement, the test-only `disconnect-next-tick` marker under
`plugins/TeleportAudit/` schedules a kick for **CodexStep19 or the locally configured test UUID only** on the first entity
tick after join. Remove it before recovery checks. The normal audit task is scheduled
20 ticks after join, so a matching `STEP19_RETIRED` confirms its retirement callback.
This does not directly count production adapter callbacks or plugin teleport events.

## Forge 26.2 real client control

`control-probe.gradle.kts` registers an explicit `:mods:26.2-forge:stageNativeClientControl`
task. Its test JAR is never part of release builds. Install it only in a disposable
HeadlessMC game directory alongside the matching production mod. Write a Minecraft
command without its leading slash to `native-command.txt` using an atomic rename.
The probe consumes it on Minecraft's client executor and sends it over the actual
player connection. `STATUS` logs tick count, event-bus state, synchronization state
and position; `STOP` exits the client. `native-control.log` records consumed commands.
Do not use this control mod or its command file in a personal or production installation.

Forge's nonfatal loading-warning screen can disable its event bus while Quick
Play connects. The control probe includes resource-pack metadata to avoid
introducing such a warning. Preserve any loading warnings and require advancing
tick counts, an active event bus, and `SYNC_FINISHED`; a connected player alone
does not prove synchronization. The final validated run kept normal warning display
enabled after correcting the probe metadata.

For NeoForge 26.2, use `neo-control-probe.gradle.kts` and the explicit task
`:mods:26.2-neoforge:stageNeoClientControl`. It stages `NeoClientControl.java`
with NeoForge metadata and uses the same command-file contract. Its status reports
advancing client ticks, screen, synchronization state and position. In both disposable
clients set `onboardAccessibility:false` in `options.txt` while stopped so first-run
onboarding does not block Quick Play. These fixtures require Proxy Compatible Forge
1.3.1 for the tested modern-forwarding setup; preserve its version and hash too.



Select the authenticated test player locally in the disposable plugin's `config.yml`
using `test-player-uuid: "<test-profile-uuid>"`. Keep this file outside the repository.
An absent UUID disables the authenticated arrival actions. The audit reads `plugins/TeleportAudit/arrival-action.txt` at destination join. Its
one-tick entity task is queued before the production MONITOR join handler:
`deny-permission` installs a session-only negative `server_waypoint.command.tp`
attachment; `move-target` executes the real edit command to move `Test/Target` to
`(8197, 81, 30)`; `disconnect` kicks this test player. The marker is retained until
removed so the operator controls retries. These hooks are for disposable fixtures
only. Preserve the action log and subsequent owner/retirement observations, remove
the marker, and verify recovery. Permission attachments disappear with the old
player session; waypoint edits must be restored through the real edit command.


## Authenticated soak and resource observations

Use only the explicitly selected test profile and an isolated MCC configuration/cache.
Do not log authentication output or copy the account cache into evidence. Online Velocity
with modern forwarding establishes player identity; select NOISE_KK independently for
backend/coordinator authentication. Record the exact installed JAR hashes before startup.

The Step-19 soak uses 60 fresh MCC sessions at 30-second intervals for 30 minutes. Each
session waits for both MCC connection success and the actual player join message, then
waits for play readiness before requesting b and a in turn. Pause briefly after each
server switch before sending another command; MCC can drop commands during its protocol
transition. Require a completion response for both transfers and a clean client exit.
A failed join or missing response fails that attempt; preserve its evidence before a rerun.
A TCP connection alone does not constitute a successful reconnect cycle.

Sample each backend and Velocity every cycle with `jcmd GC.heap_info`, process RSS,
numeric file-descriptor count and thread count. Compare `GC.run` followed by heap samples
at the beginning and end, while retaining peak RSS/heap and steady-state thread/descriptor
observations. This disposable run uses 1 GiB backend heaps and a 512 MiB proxy heap;
RSS includes memory outside the Java heap. Thirty minutes with one player is a bounded
soak, not evidence for an overnight or high-player-count deployment.

For a separate socket-pressure check, open 64 stalled TCP handshakes against the isolated
coordinator and observe its accepted connection count. The default limit is 32 including
the two established backend sessions; the handshake deadline is 10 seconds. Require
stalled sockets to close and the accepted count to return to the two legitimate sessions.
To claim transfer under pressure, count successful transfer responses specifically while
the sockets are held. Close every test socket. This checks admission and timeout cleanup;
retained-byte and concurrent-handoff ceilings also require their focused component tests.


The passing macOS arm64 soak set `DOTNET_ReadyToRun=0` in the MCC launcher environment.
Two earlier attempts aborted inside CoreCLR before joining; preserve the sanitized crash
summaries and failed-attempt results. This setting disables precompiled .NET code
([runtime documentation](https://github.com/dotnet/runtime/blob/main/docs/workflow/debugging/coreclr/debugging-runtime.md));
it was a successful fixture adjustment, not proof of a specific upstream defect.
The final run passed 60 reconnect sessions and 120 transfers in 1,801.9 seconds.
See [soak results](../../docs/features/cross-server/validation/cross-server-step19-soak/results.json).


Archived Step-19 evidence replaces the personal profile name with
`AuthenticatedTestPlayer` and uses synthetic UUIDs consistently. These are redaction
labels, not accounts to authenticate. Archived hashes cover the redacted files; raw
source hashes refer to the original private evidence. The historical audit JAR hash
identifies the tested fixture; the checked-in audit now selects its player from local
configuration instead of embedding a personal profile name.
