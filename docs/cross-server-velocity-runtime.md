# Velocity runtime integration (step 16)

Step 16 connects the existing catalog and handoff services to Velocity and the dedicated mod/Paper
backends. The feature remains disabled by default. No client mod is required for remote commands
or server switches. Modded-client catalogs and GUI controls remain Steps 17–18.

## Runtime ownership and handoff flow

`VelocityRuntime` reads configuration and opens the coordinator on a private lifecycle worker.
`VelocityPlayerRouter` resolves exact configured stable IDs to current registered Velocity servers,
gets the UUID from the proxy player, checks optional proxy permission and rechecks the current
source immediately before `createConnectionRequest(...).connect()`. It never uses vanilla transfer,
RCON, or a backend-supplied permission assertion.

`BackendRuntime` starts from Paper plugin startup or the dedicated mod server lifecycle. Integrated
servers do not start this feature. It owns credentials, a durable catalog revision sequence,
publication, the separate remote replica and one `BackendHandoffSession` per admitted connection.
Command initiators point to this lifecycle owner; reconnect replaces and closes the old services.
Paper/Folia callbacks use the player/entity owner. Mod callbacks use the owning Minecraft server.
Join events pass the actual local player's UUID to the destination service. Successful destination
teleports and actionable arrival failures are sent to that player on its owner.

The complete flow is:

1. Source command resolves an available exact cached target and checks the source player's permissions.
2. `PREPARE_HANDOFF` travels over the admitted TCP session. Coordinator admission rechecks the proxy
   UUID/source route, destination mapping, current catalog state, exact target and observed revisions.
3. The destination resolves its own PUBLIC target and reserves a bounded one-time handoff.
4. Coordinator validates and forwards `HANDOFF_PREPARED` with its capped binding to the source.
5. After source-owner identity/permission/deadline checks, the source returns that exact
   `HANDOFF_PREPARED` binding as **readiness confirmation** over the same TCP connection.
6. Coordinator requires the exact source session and PREPARED reservation, rechecks route,
   destination session, catalog revisions and proxy permission, then invokes the real Velocity
   transfer adapter at most once. Failed connections cancel the destination reservation and return
   a stable failure to the source. The source player stays put if connection establishment fails.
7. The destination's join event claims once through the coordinator, resolves current local
   coordinates again, rechecks the live player's permission and performs the owning-thread teleport.
   `COMPLETE_HANDOFF` closes the source's pending operation. A switch by itself is not teleport success.

Readiness confirmation completes the previously unused source-to-coordinator direction of the
existing `HANDOFF_PREPARED` message. The canonical field layout, numeric IDs and application v1
version are unchanged; all components of this unshipped feature must be updated together.
Minecraft `ProtocolVersion` and loader custom-payload registrations are unchanged. The codec and
TCP replay accounting continue to enforce the same bounds and request/type uniqueness.

`CoordinatorHandoffRuntime` serializes dispatch, readiness, transfer initiation and disconnect.
Each session has a fresh UUID and a bounded writer queue (64 messages); socket writes never block
a game/proxy callback. Readiness replay records are bounded by the registry's retained records.
Backend request futures are correlated by request and phase, capped at 64, expire within 15 seconds
and complete on connection closure. A 100 ms maintenance task enforces service deadlines. Proxy
player disconnect invalidates that UUID's pending registry records. Cleanup closes the old channel
owners before a reconnect can reuse the stable server ID.

All control traffic uses TCP. Velocity consumes `server_waypoint:handoff` plugin messages from
**both** client and backend sources without parsing or forwarding them. That reserved channel is
not an authorization carrier. Other plugin channels retain their existing behavior.

## Configuration

Each component creates a disabled `cross-server.json` in its configuration directory. A restart is
required after changing transport, mappings, pins or export configuration. Unknown top-level fields
are rejected; input is capped at 1 MiB. Default transport is `NOISE_KK`. Only `PUBLIC` catalog export
is implemented; it exports the local server's waypoint lists. Private/player-specific export is not
implemented. Cache/transport limits use the existing conservative defaults.

Example coordinator configuration (Velocity `plugins/server_waypoint/cross-server.json`):

```json
{
    "enabled": true,
    "transportMode": "NOISE_KK",
    "listen": "127.0.0.1:25580",
    "protocolVersion": 1,
    "credentialsDirectory": "credentials",
    "proxyPermission": "server_waypoint.command.remote.tp",
    "backends": {
        "survival": {
            "enabled": true,
            "velocityServer": "survival",
            "publicKey": "<canonical Base64 X25519 SPKI public key>"
        }
    }
}
```

`proxyPermission` is optional; omission or an empty string disables this additional proxy check.
The source's two backend teleport permissions and destination local permission remain mandatory.
Mappings must be unique and refer to actual Velocity registered servers. Disabled registry entries
are excluded from admission. Use normal Velocity authenticated player forwarding for deployment;
source and destination UUIDs must match the proxy UUID. The test-only offline configuration in the
verification evidence is confined to disposable loopback servers.

Example backend (`plugins/ServerWaypoint` on Paper; the assigned Server Waypoint config directory
on mod loaders):

```json
{
    "enabled": true,
    "transportMode": "NOISE_KK",
    "serverId": "survival",
    "coordinator": "127.0.0.1:25580",
    "protocolVersion": 1,
    "credentialsDirectory": "credentials",
    "coordinatorPublicKey": "<canonical Base64 X25519 SPKI public key>",
    "catalogExport": "PUBLIC"
}
```

`requiredSuite`, when present, must be `Noise_KK_25519_AESGCM_SHA256`. Private keys live in the
existing `LocalCredentials` store (`credentials/static.key`, binary PKCS#8 with owner-only access
where supported). They are never printed or sent. KK startup writes the local public key to
`cross-server-public-key.txt`; missing pins fail startup. An operator can enable KK once to generate
that key, stop, exchange these public keys through a trusted administrative channel, configure the
pins and restart. No plaintext pairing shortcut is needed. A previously installed backend
`coordinator.pin` can supply the pin; an explicit conflicting configuration is rejected. The
existing authenticated bootstrap APIs remain available, but this step does not add a pairing-code
network carrier or administrative pairing command. Removing an enabled registration/pin and
restarting closes its admission; online administrative revocation UI remains separate.

For explicit same-host plaintext, set `transportMode` to `PLAINTEXT` at both ends and remove all
crypto fields (including registry `publicKey`, `credentialsDirectory`, `coordinatorPublicKey` and
`requiredSuite`). Use literal loopback endpoints such as `127.0.0.1:25580` or `[::1]:25580`.
Hostnames, wildcard and non-loopback addresses are rejected. Existing key files are left untouched
and unused. Plaintext does not authenticate a backend cryptographically and has no automatic KK
fallback. All application/session/player/permission/claim checks still apply.

Use quoted identity arguments, including dimension IDs containing a colon:

```text
/wp remote tp survival "minecraft:overworld" "Public list" "Home"
```

## Verification and limits

On 2026-09-08, common/proxy/Velocity checks passed **523 common, 106 proxy and 6 Velocity tests**,
with no failures/errors/skips. Sixteen new real-socket cases cover both modes with the production
TCP dispatchers, successful one-time arrival, wrong source, proxy denial, destination rejection,
final permission revocation, failed transfer, disconnect and arrival before source readiness. Six actual Velocity API adapter tests
cover current route/UUID, async completion, mapping/permission failures and consumption of spoofed
client/backend plugin messages. Three new configuration tests cover disabled defaults and rejected
fields/versions/size/plaintext endpoints/crypto settings.

Compilation/build commands:

```sh
./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain
./gradlew :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava --max-workers=2 --console=plain
./gradlew :paper:1.21-paper:build :mods:1.20.1-fabric:compileJava :mods:1.21.2-neoforge:compileJava :mods:26.1.2-forge:compileJava --max-workers=2 --console=plain
```

No active Stonecutter version changed. The existing loader branches remain present.

Live tests used Velocity 4.1.1-SNAPSHOT and two Paper 1.21-130 servers, Java 25/21 respectively,
with MCC 26.2 build 499 speaking Minecraft protocol 767. KK succeeded in both directions; a
test-only plugin counted exactly one PLUGIN teleport per successful arrival and console queries
confirmed coordinates. Revoked destination permission denied after arrival without increasing the
count; revoked source permission prevented another switch. Explicit loopback plaintext rejected a
destination login while retaining the player on the source, then successfully retried with exactly
one teleport after the test rejection was removed. All disposable processes stopped cleanly.

[Runtime results and hashes](validation/cross-server-step16/results.json) and adjacent full logs
preserve the evidence and identify the earlier native-run artifacts separately from the final build.
The final source-readiness claim guard passed real-TCP tests in both modes; native sessions were
not repeated for that final guard. The initial test-fixture position format and MCC console-mode setup errors
were corrected before the successful live scenarios; they required no production fixes. Credential
files are not archived. [The test-only audit plugin](../tools/cross-server-live-test/README.md) is not
included in any release artifact.

Spoofed plugin packets, wrong-source injection and disconnect races were checked through
API/real-TCP fixtures, not MCC packet injection. Native mod-loader and Folia cross-server sessions,
modern online forwarding, restart/rotation administration, the full release artifact matrix and
broader soak/security hardening remain release verification work for Step 19. These results establish
the first live Paper/Velocity command-to-arrival path; they do not establish full production readiness.
