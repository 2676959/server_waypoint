# Server Waypoint architecture

Updated against the repository sources on 2026-09-11. This is a whole-project overview; feature contracts and release evidence remain under [features](../features/).

## Modules and runtime ownership

| Module | Responsibility | Dependencies and runtime |
| --- | --- | --- |
| `common` | Waypoint core, configuration, network codecs, upload coordination, cross-server models, TCP transport and backend services | Java 17 shared library; embedded by adapters, not a standalone process |
| `mods` | Fabric, Forge and NeoForge adapters, server commands, client cache, GUI, renderer and map integration | Depends on `common`; Stonecutter selects loader/version APIs |
| `paper` | Paper/Folia server adapter, commands, scheduling and destination arrival | Depends on `common`; independent of `mods`, with no client GUI |
| `proxy-common` | Proxy contracts, catalog aggregation and handoff coordination | Depends on `common`; contains no platform-specific proxy API types |
| `velocity` | Velocity lifecycle, registered-server mapping, player routing and coordinator startup | Depends on `proxy-common`; packages both shared libraries; Java 17 source syntax with a Java 25 runtime target |

The [Gradle settings](../../settings.gradle.kts) define the module and version matrix. Shared code is loaded inside its consuming process. It is not another network service. A deployment may contain multiple backends, each with its own authoritative local waypoint state.

## Data and control paths

- **Local waypoints:** backend adapters use `WaypointServerCore` for authoritative state and GSON persistence. Modded clients maintain their local cache, GUI, renderer and Xaero/VoxelMap integrations. Chunked upload/download belongs to this client/backend path.
- **Remote discovery:** each enabled dedicated backend publishes PUBLIC catalogs to the Velocity coordinator over TCP. The coordinator aggregates and distributes catalogs to separate backend replicas. Authorized client requests receive session-only remote catalog snapshots.
- **Remote GUI:** `RemoteClientCatalogs` supplies read-only discovery and teleport selection through the manager's remote panel. The sidebar switches between local and remote views; remote data remains separate from local waypoint storage, persisted cache, renderer and map-mod files.
- **Remote teleport:** source validation and destination preparation precede source readiness confirmation and the Velocity switch. Destination arrival claims the reservation, rechecks live permissions and resolves current local coordinates before teleporting. A successful switch alone is not a successful teleport.
- **Execution ownership:** transport lifecycle workers own blocking startup and cleanup. Minecraft actions return to the owning server; Paper/Folia player callbacks use the player/entity owner. Integrated servers do not start cross-server runtime services.

Client/backend payload **protocol 1** and cross-server TCP **application protocol v1** are separate contracts. Cross-server support is disabled by default and uses pinned `NOISE_KK` credentials when enabled with the default transport. Explicit `PLAINTEXT` mode is unauthenticated and restricted to literal loopback addresses. The reserved handoff plugin-message channel is not an authorization path.

## Source and contract references

- [Payload protocol version](../../common/src/main/java/_959/server_waypoint/ProtocolVersion.java) and [TCP application version](../../common/src/main/java/_959/server_waypoint/crossserver/CrossServerProtocol.java).
- [Backend runtime](../../common/src/main/java/_959/server_waypoint/crossserver/handoff/BackendRuntime.java) and [Velocity runtime](../../velocity/src/main/java/_959/server_waypoint/velocity/VelocityRuntime.java).
- [Remote client state](../../mods/src/main/java/_959/server_waypoint/common/client/RemoteClientCatalogs.java).
- [Runtime and handoff contract](../features/cross-server/specs/cross-server-velocity-runtime.md), [client synchronization](../features/cross-server/specs/cross-server-client-sync.md) and [remote GUI](../features/cross-server/specs/cross-server-gui.md).
- [Administrator guide](../features/cross-server/cross-server-admin.md) and [release validation](../features/cross-server/validation/cross-server-release-readiness.md). This overview does not replace release gates.
- [Upload transport records](../features/upload/) and [implementation tips](../tips/).

## Diagram and regeneration

The repository includes the [architecture input](server-waypoint.architecture.json) and
[generated diagram](server-waypoint-architecture.html), with theme switching and image/SVG export.
The diagram records the 2026-09-11 architecture snapshot; consult the source references above for
current protocol versions and feature behavior.

With Node.js and the archify skill installed, set `ARCHIFY_ROOT` to that skill directory and run from the repository root:

```sh
node "$ARCHIFY_ROOT/bin/archify.mjs" render architecture docs/architecture/server-waypoint.architecture.json docs/architecture/server-waypoint-architecture.html
node "$ARCHIFY_ROOT/bin/archify.mjs" validate architecture docs/architecture/server-waypoint.architecture.json --json
node "$ARCHIFY_ROOT/bin/archify.mjs" check docs/architecture/server-waypoint-architecture.html
```

Archify needs its `ajv` dependency for JSON-schema validation; renderer layout checks still run without it. Update the JSON first, regenerate the HTML, and check both after structural changes. Keep feature-specific designs under `docs/features/<feature>/specs/`.
