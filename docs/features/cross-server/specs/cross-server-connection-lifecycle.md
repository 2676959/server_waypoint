# Connection lifecycle and backend registration (step 8)

Step 8 implements operational connection ownership and backend presence. Shared backend/lifecycle
APIs live under `common/.../crossserver/transport`; the coordinator owner is
`proxy-common/.../proxy/transport/CoordinatorAgent`. These classes do not access Minecraft, Bukkit,
Velocity players, commands, waypoint managers or catalog stores. Platform lifecycle wiring remains
later work. The feature remains disabled by default.

## Nonblocking ownership

`BackendAgent` implements `BackendTransport`; `CoordinatorAgent` implements `CoordinatorTransport`.
They share `AsyncTransportLifecycle`, a serial control worker with idempotent asynchronous
`start()`/`stop()` outcomes. Calling start, stop or disconnect performs no socket I/O on the caller.
The coordinator listener factory runs on the control worker, allowing binding and credential-owner
listener construction to happen away from a game thread.

Backend start SUCCESS means that the connection supervisor is running, not that registration has
succeeded. Coordinator start SUCCESS means its listener and bounded workers were created.
DISABLED creates no listener/reader/heartbeat workers and performs no endpoint lookup; disabled KK
needs no credentials. Configuration rejection and bind failure return INVALID_CONFIGURATION or
UNAVAILABLE. Repeated calls share the original outcome. Stop is terminal: restarting requires a
fresh agent; reconnect within a running backend agent is automatic.

Stop marks the agent stopping immediately, closes active and connecting sockets on the control
worker, cancels heartbeat/retry work, joins reader/writer workers and releases owned backend key
copies before completing. The caller can keep running while waiting on the completion stage.
A queued start followed by stop still cleans up anything the start created. A factory or JVM DNS
resolver already running may delay completion; Java hostname lookup is not guaranteed interruptible.
Literal endpoints avoid that resolver dependency. Stop never claims cleanup while I/O workers remain.

Lifecycle/admin operations have a bounded control queue: at most 64 outstanding disconnect commands
and two reserved lifecycle slots. Excess commands complete UNAVAILABLE, while stop retains its
queue slot. Completion callbacks run according to CompletionStage rules; consumers must not block
the owner by waiting synchronously for another queued lifecycle action. Platform consumers should
schedule work through their platform adapter, or read the immutable status snapshots.

## Backend connection supervisor

`BackendAgent` owns a defensive copy of supplied KK key material and coordinator pin, or no keys
in explicit plaintext mode. Its constructor validates local settings without connecting. The
reader worker repeatedly:

1. Connects using exactly the configured transport mode and the existing bounded channel handshake.
2. Sends REGISTER_SERVER with configured stable ID, protocol version 1 and advertised capabilities.
3. Requires a SUCCESS REGISTER_RESULT with matching request UUID and server ID.
4. Publishes registered presence, schedules outbound heartbeats and continuously reads heartbeats.
5. On terminal channel failure, removes presence, closes the session, records metrics and waits
   before another connection attempt with fresh handshake/cipher/sequence state.

Backoff doubles the configured minimum after failures up to the configured maximum. Each wait is
randomized between the minimum and that exponential ceiling. The counter resets only after a
connection has received a heartbeat and remained registered for at least the maximum-backoff
interval, preventing rapidly failing registrations from continuously resetting the retry delay.
Stop wakes a waiting retry immediately. Handshake cancellation closes the actual pending socket.
No failure switches modes or bypasses pairing; revoked or mismatched pins continue failing until
an owner replaces the agent with explicitly updated credentials.

## Coordinator registration and presence

The coordinator accepts channels through an owner-supplied `TcpCoordinator` factory. The factory's
`TcpLimits` must match its worker limits. It may call `PairingCoordinator.listen()` for KK, or create
an explicitly configured loopback plaintext listener. A pairing credential owner can open a fresh
listener after its old agent stops without regenerating its static key or losing public pins.

Each reader worker accepts/authenticates one channel and requires REGISTER_SERVER as its first
application message. ID and capability set must exactly match the admitted handshake preface;
application version must be 1. A missing, malformed or mismatched registration never becomes
visible presence. Duplicate transport IDs reject without displacing the existing session. The
coordinator sends the correlated successful result before exposing the immutable presence entry.
Step 8 accepted only heartbeats after registration. [Step 9](cross-server-catalog-publication.md)
adds backend catalog publication/receipt; [Step 10](cross-server-catalog-distribution.md)
adds coordinator fan-out and backend replication. Handoff handlers remain absent.

`BackendPresence` contains the stable ID, actual mode, defensive capability set, registration time
and a monotonically increasing generation local to that agent. `authenticated()` is true only
for KK. Administrative `securityStatus()` is `KK_AUTHENTICATED` or `TRUSTED_LOOPBACK`; plaintext is
never labeled cryptographically authenticated. Capabilities remain advertisements, not grants or
proof of implemented support. Generations are not durable identities or authorization tokens.

Status snapshots exclude closed sessions and do no network I/O. Internal removal compares the
exact live-session object so cleanup cannot remove a replacement connection's presence.
`disconnect(id)` is asynchronous and closes current sessions/reservations for that ID; an absent
ID succeeds. It does not revoke registry policy, so a backend can reconnect. Step-7 revocation
still removes its public pin and closes its sessions. Closing/rotating a listener through its
credential owner stops accept loops instead of spinning on a closed socket.

## Heartbeats, limits and metrics

`LifecycleSettings.DEFAULT` is disabled, with 5-second heartbeats, 1-second minimum reconnect
backoff and 30-second maximum backoff. The heartbeat interval must be less than half the channel
operation timeout. Each endpoint continuously reads; the existing absolute receive timeout closes
a silent peer even while outbound heartbeats are succeeding. Heartbeat writes use the channel's
bounded write deadline and close the session on failure.

A backend uses one reader, one heartbeat worker and one control owner. Step 9 adds one optional
publication worker when a CatalogPublisher is supplied. Step 10 adds bounded cache maintenance
tasks and at most four coordinator distribution workers, with one coalescing task per peer. A coordinator uses at most
`TcpLimits.connections()` readers, at most four heartbeat workers and one control owner. Reader
jobs are fixed for the lifecycle; heartbeat jobs are at most one per active peer and canceled jobs
are removed. There is no application outbound queue or executor growth under reconnect storms.
When all readers are occupied, new connections remain within the OS listen backlog until a reader
is available or the backend handshake deadline expires. All step-6 connection, framing, pending,
retained-byte, timeout and session-history limits remain in force; ordinary history exhaustion
causes a fresh reconnect rather than evicting replay history.

Metrics expose saturated long counters for connection attempts, successful registrations,
registered-session disconnects, failures and sent/received heartbeats. Backend attempts count
outbound connection attempts; coordinator attempts count sockets accepted from the listener,
including failed authentication/admission. Snapshots are bounded observations across concurrent
workers, not atomic accounting transactions. No keys, pairing codes, exception payloads or mutable
channel objects appear in administrative status.

## Verification and remaining integration

On 2026-09-07, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 439 common tests and 53 proxy tests with no failures/errors/skips. The 18 new lifecycle cases
cover real sockets in both modes, registration/correlation/capability checks, heartbeats, duplicate
IDs, coordinator restart using the same credential owner, disconnect/reconnect, missing
registration, silent peers, unfinished outbound handshakes, exponential ceilings, disabled KK with
no credentials/DNS, start/stop idempotence, a blocked factory proving nonblocking caller behavior,
bounded admin commands with a reserved stop slot, configuration/bind failures and external listener
closure. Existing pairing/revocation, framing, replay and malformed-message tests also pass.

These tests do not boot Minecraft or wire a Velocity plugin. Catalog publication is now implemented in [step 9](cross-server-catalog-publication.md);
registration of platform lifecycle/commands and the bootstrap network dispatcher remain integration
work. The step-7 bootstrap API is not routed onto the operational KK listener by this change.

## Step-16 integration

The services now have live coordinator/backend lifecycle and Velocity transfer wiring. See
[the runtime contract and current validation](cross-server-velocity-runtime.md). Earlier step-specific
verification above describes its historical boundary; client/GUI and full release hardening remain.
