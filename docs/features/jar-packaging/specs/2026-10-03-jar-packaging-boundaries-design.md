# Jar Packaging Boundaries Design

Status: design agreed on 2026-10-03; implemented on 2026-10-04. The implementation plan lives in
[plans](../plans/). The [validation record](../validation/2026-10-03-jar-packaging-boundaries.md) holds the
evidence and the deviations, including the ForgeGradle development runs that §1.6 did not foresee.

## Intent

Every release jar should carry only the classes and resources its platform uses, and the build
should keep it that way.

Today:

- The Velocity plugin (about 1.06 MB) shades all of `common`: its 228 source files, the six backend
  translation files, the 93 KB `chat-sprites.json` table and `SERVER_WAYPOINT_CREDITS.txt`. The
  proxy uses none of the 560 translation keys: they are all backend `/wp` feedback, and
  `/serverwaypoint status` is plain English. It needs 47 of those source files. Almost everything
  else is reached through one line: `WaypointIconPolicy` logs through `WaypointServerCore.LOGGER`.
- Paper and every mod jar ship `TcpCoordinator` and `CoordinatorTransport`, which only the proxy
  runs.
- Paper ships four client helpers that only the mods use.
- Every target below 1.21.9 ships the chat-sprite table, although sprite objects in chat only exist
  from 1.21.9.
- The root build adds `CREDITS.txt`, renamed `SERVER_WAYPOINT_CREDITS.txt`, to every Jar task of
  every subproject, Velocity included. `TRANSLATOR_CREDITS.md` is repository documentation and is
  not packaged.
- bStats is relocated into the plugin's root namespace, so its classes land inside our own
  packages (`config.MetricsConfig`, `velocity.Metrics`).
- Noise's Maven metadata (`META-INF/maven/org.signal.forks/...`) ships in every jar.
- The release gate (`tools/verify-release-artifacts.sh`) fails: it expects 41 jars, but there are
  42 targets since `1.21.9-paper` was added. It checks jar names and Noise relocation, not content
  boundaries.

Goals:

1. Each jar ships only what its platform needs.
2. A misplaced class or resource fails early: at compile time where possible, otherwise at the
   release gate.
3. Runtime behavior is unchanged.

Success means:

- the Velocity jar contains no `lang/`, `assets/` or credits file and no waypoint core, command,
  configuration, navigation, text or translation classes, and is about a third of its current size;
- Paper and mod jars contain no proxy classes, and Paper contains no mods-only helpers;
- jars below 1.21.9 contain no chat-sprite table;
- the release gate enforces these rules, checks that every internal class reference resolves inside
  each jar, and passes on a full build;
- all existing tests pass, and the trimmed Velocity jar boots and starts its coordinator.

## Scope

In scope: which module owns the cross-server code, the packaging rules for every release jar, the
release gate, and documentation of the new boundary.

Out of scope:

- Trimming inside shaded third-party libraries. Adventure is about 0.5 MB of each mod jar, but the
  mods use it, and class-level trimming would break its ServiceLoader providers.
- Production classes that only tests use (`BackendPairing`, `RemoteCatalogView`,
  `DestinationAuthorization`). They stay where they are; removing them is a separate decision.
- Shadow's `minimize()`. Its reachability analysis misses classes referenced only through
  annotations, such as `@JsonAdapter(NavigationMethodSetJsonAdapter.class)`, and ServiceLoader
  providers.
- Backward-compatibility code. No public API, configuration or file format changes.

## 1. Module layout

```
cross-server <- common       <- paper, mods
             <- proxy-common <- velocity
```

### 1.1 `cross-server` (new)

A Java 17 `java-library` holding what both ends of a cross-server TCP link need: the application
codec, TCP and Noise transport primitives, catalog models and index, pairing credentials, runtime
configuration and the coordinator log. It contains no waypoint core, Minecraft, Brigadier or
Adventure types.

Dependencies: Gson, netty-buffer, SLF4J and the JetBrains annotations as `api`, at the versions
`common` declares today; noise-java as `implementation`, moved from `common` because only
`NoiseRecordCipher` calls it.

Files move unchanged and keep their packages, so no import changes anywhere (Appendix A):

- 41 `crossserver` classes.
- Four value types the codec and catalog models use: `core.waypoint.WaypointPos`,
  `core.waypoint.WaypointIconPolicy`, `util.NamespacedId` and `core.network.DecodingContext`.
- `ModInfo`, together with the `updateModInfo` task that generates it, because the Velocity
  `@Plugin` annotation needs its version constant.
- The eight tests that exercise only these classes.

One code change: `WaypointIconPolicy` declares its own logger with the existing name
`server_waypoint_core` instead of importing `WaypointServerCore.LOGGER`. Log output is unchanged.

### 1.2 `common`

Declares `api(project(":cross-server"))`, so Paper, the mods and its own tests compile unchanged.
It keeps the waypoint core, commands, text, translations, the chat-sprite table and the backend half
of cross-server: `BackendRuntime`, `BackendAgent`, `TcpBackend`, catalog publishing and queries,
handoff services, authorization, `StaticKeyGenerator` and `BackendPairing`. Noise leaves its
dependency list.

### 1.3 `proxy-common`

Depends on `cross-server` through `api` instead of on `common`. `common` becomes a test-only
dependency, because its contract tests drive real backend transports. It gains `TcpCoordinator`
and `CoordinatorTransport`, the only proxy-only classes in `common`, together with
`TcpTransportTest`. They keep the package `crossserver.transport`, because `TcpCoordinator` uses
the package-private `TcpWire` class and `TcpChannel` constructor.

### 1.4 `mods`

Gains the four helpers that only the mods use, placed beside their callers:

| Class | From (`common`) | To (`mods`) |
| --- | --- | --- |
| `XaerosWorldIdBuffer` | `core.network.buffer` | `common.network` |
| `XaerosWorldIdBufferCodec` | `core.network.codec` | `common.network` |
| `WaypointRevisionSequence` | `core.network` | `common.client` |
| `MathUtils` | `util` | `common.client.util` |

`WaypointRevisionSequenceTest` moves to the mods test tree. `MessageChannelID.XAEROS_WORLD_ID_CHANNEL`
stays in `common`, beside the other channel IDs.

### 1.5 `velocity`

Its sources are unchanged. It depends on `proxy-common` and shades `cross-server`, `proxy-common`,
bStats and Noise. `common` is no longer on its classpath, so a reference to backend code is a
compile error.

### 1.6 Split packages

`crossserver.*`, `core.waypoint`, `core.network`, `util` and the root package now span modules.
That is legal on the classpath, and each release jar shades its modules into one jar, so every
runtime package is whole again. Paper already splits `navigation` and the root package with
`common`.

The exception is the Forge and NeoForge development runs. The ModDevGradle (`neoforge`) and
ForgeGradle (`forge`) scripts load `common` as part of the mod module, so `cross-server` must join
that group, or the module system rejects the split packages. NeoGradle (`1.20.2-neoforge`) keeps
`common` on the plain classpath, where `cross-server` joins it, and needs no change.

Renaming packages would avoid the splits, but it would change imports in every module and force
`TcpWire` to become public.

## 2. Packaging rules

1. **Credits.** The root build attaches `SERVER_WAYPOINT_CREDITS.txt` only to Jar tasks in the Paper
   and mod version projects, the jars that ship translations. The Velocity, `common`,
   `proxy-common` and `cross-server` jars carry none.
2. **Shading.** Every route that shades `common` also shades `cross-server`: the Paper shadow filter
   and the shadow filters in the `fabric`, `fabric-unobfuscated`, `neoforge`, `neogradle` and
   `forge` scripts. The `neoforge` and `forge` dev-run mod groups add the `cross-server` main source
   set. Velocity shades `cross-server` in place of `common`.
3. **Chat sprites.** Targets below 1.21.9 exclude `assets/server_waypoint/chat-sprites.json` and
   `_959/server_waypoint/text/chat/VanillaChatSprites.class` from the shadow jar. The `fabric`,
   `neoforge` and `forge` scripts and the Paper script use a `<1.21.9` Stonecutter predicate;
   `neogradle` builds only 1.20.2 and excludes them unconditionally; `fabric-unobfuscated` builds
   only 26.x and needs no rule. The table's only caller, `CommandChatIcons`, sits behind
   `//? if >=1.21.9` on both platforms.
4. **Maven metadata.** `gradle/noise-packaging.gradle.kts`, which every platform applies, excludes
   `META-INF/maven/**` from the shadow jar. The Noise license file stays.
5. **bStats.** Paper and Velocity relocate `org.bstats` to `_959.server_waypoint.internal.bstats`,
   beside `internal.noisekk`.

## 3. Enforcement

`tools/check_release_jar.py <jar> <loader> <minecraft-version>` runs on every release jar from
`verify-release-artifacts.sh`; Velocity passes `-` as its version. It reads class files itself, so
it needs only Python 3 and handles every class file version, including Java 25's. It exits non-zero
with a message naming the jar and the rule:

1. **Internal references.** It parses the constant pool of every class and collects
   `_959/server_waypoint/...` names from class entries and from descriptors, signatures and
   annotations. Every name must exist as a class in the jar; entries under `META-INF/versions/<n>/`
   count, nested jars are ignored. A class file that cannot be parsed is a failure.
2. **Velocity.** No `lang/`, `assets/` or `SERVER_WAYPOINT_CREDITS.txt`; no classes under
   `_959/server_waypoint/command/`, `config/`, `navigation/`, `text/` or `translation/`, and no
   `core/WaypointServerCore`; `velocity-plugin.json` present.
3. **Paper and mod jars.** No `_959/server_waypoint/proxy/`, `TcpCoordinator` or
   `CoordinatorTransport`; `lang/en_us.json` and `SERVER_WAYPOINT_CREDITS.txt` present.
4. **Chat sprites.** The table and `VanillaChatSprites` are absent below 1.21.9; the table is
   present on Paper and mod jars from 1.21.9.
5. **Maven metadata.** No `META-INF/maven/` in any jar.

The gate derives each target's loader and Stonecutter version from its `versions/<target>` folder
and passes them to the checker. Its expected counts become 42 jars, four of them Paper.
`tools/test_release_artifacts.py` builds its fixtures from minimal valid class files and gains one
failing case per rule.

## 4. Verification

1. Baseline: build every target at the starting commit, record each release jar's size, and run the
   new checker on those jars. It must pass the internal-reference rule and fail only on rules this
   change introduces.
2. Run `:cross-server:test`, `:common:test`, `:proxy-common:test` and `:velocity:build`.
3. Run a full `./gradlew build`, `./move_builds.sh`, `bash tools/verify-release-artifacts.sh` and
   `python3 tools/test_release_artifacts.py`.
4. Record a before-and-after size table for every release jar.
5. Boot in disposable scratch directories:
   - the local Velocity 4.1.0 with the new jar and cross-server enabled in `NOISE_KK` mode with no
     backends: the coordinator starts, `serverwaypoint status` reports it running, and the log has
     no class-loading errors;
   - `runServer` on one ModDevGradle NeoForge target and one Forge target, to prove the dev-run mod
     groups.

## 5. Documentation

- This feature folder: README, this spec, the plan and the validation record.
- `docs/architecture/README.md`'s module table and `server-waypoint.architecture.json`. The HTML is
  regenerated if the archify tool is installed; otherwise the README notes that it is pending.
- The ownership rows for `TransportLifecycle` and `CoordinatorTransport` in
  `docs/features/cross-server/specs/cross-server-proxy-module-contracts.md`.
- The comment in `gradle/noise-packaging.gradle.kts`: `cross-server` now owns the Noise dependency.
- AGENTS.md "Project Structure" lists `cross-server`, `proxy-common` and `velocity`, with the rule:
  code both backends and the proxy need belongs in `cross-server`, backend-only code in `common`,
  proxy-only code in `proxy-common`.

## Appendix A. Files that move

Main sources, from `common/src/main/java/_959/server_waypoint/` to
`cross-server/src/main/java/_959/server_waypoint/`:

| Package | Classes |
| --- | --- |
| `crossserver` | `CatalogExportPolicy`, `CrossServerProtocol`, `RemoteCatalogSnapshot`, `RemoteCatalogState`, `RemoteListSnapshot`, `RemoteRevision`, `RemoteServerId`, `RemoteWaypointKey`, `RemoteWaypointSnapshot`, `RuntimeConfiguration`, `ServerIcon` |
| `crossserver.catalog` | `CatalogCacheLimits`, `CatalogDelta`, `CatalogIndex`, `CatalogReceiver` |
| `crossserver.handoff` | `TeleportCoordinatorLog` |
| `crossserver.pairing` | `CanonicalKey`, `CredentialFiles`, `LocalCredentials`, `PairingCode`, `PairingWire` |
| `crossserver.protocol` | `ApplicationCodec`, `ApplicationEnvelope`, `ApplicationMessage`, `ProtocolLimits` |
| `crossserver.transport` | `AsyncTransportLifecycle`, `BackendPresence`, `ConnectionMetrics`, `LifecycleSettings`, `NoiseKeys`, `NoiseRecordCipher`, `OperationalSession`, `QueuedChannelSender`, `TcpChannel`, `TcpEndpoint`, `TcpLimits`, `TcpSessionState`, `TcpWire`, `TransportLifecycle`, `TransportMode`, `TransportResult` |
| `core.waypoint` | `WaypointPos`, `WaypointIconPolicy` |
| `core.network` | `DecodingContext` |
| `util` | `NamespacedId` |
| root | `ModInfo` |

Tests, from `common/src/test/java/_959/server_waypoint/` to
`cross-server/src/test/java/_959/server_waypoint/`: `crossserver/catalog/CatalogDeltaTest`,
`crossserver/pairing/CredentialTest`, `crossserver/protocol/ApplicationCodecTest`,
`crossserver/transport/TcpSessionStateTest`, `crossserver/RemoteIdentityTest`,
`crossserver/RuntimeConfigurationTest`, `crossserver/ServerIconTest` and `util/NamespacedIdTest`.

To `proxy-common`: `crossserver/transport/TcpCoordinator` and
`crossserver/transport/CoordinatorTransport` (main), and `crossserver/transport/TcpTransportTest`
(test), all keeping their package.

To `mods`: the four helpers in section 1.4 and `WaypointRevisionSequenceTest`.
