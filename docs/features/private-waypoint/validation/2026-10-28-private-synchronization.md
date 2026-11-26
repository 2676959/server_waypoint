# Task 4: owner-only synchronization validation

Date: 2026-10-28. Base: `c48a4397`, branch `feature/private-waypoint`.
Implementation and automated Task 4 checks complete. Local commit authorized on 2026-10-28.

## Delivered behavior

Protocol 3 carries explicit server scope with no client-supplied owner UUID. Private snapshots,
command deltas, and GUI edit outcomes use the authenticated owner's collection and owner-only
transport. Private read access is rechecked before snapshots, deltas, and queued frame batches;
Paper rechecks on the player's owning region. Observed denial clears private client state through
an unreadable collection-state message. Private upload data and invalid scopes are rejected.

The client has independent server caches, readiness, revisions/recovery, pending edit keys, and
captured selection generations. Private cache paths include account, address digest, and backend
identity. Rebinding invalidates old work. Integrated clients use the authenticated player's normal
Private network path, with detached cache data. Existing Shared map integration remains Shared-only.
Local profile persistence and source-aware manager/rendering remain Tasks 5–7.

## Executed checks

Full suites:

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test :paper:26.2-paper:test \
    :cross-server:test :proxy-common:test --console=plain --max-workers=2
```

| Suite | Tests | Failures/errors/skips |
| --- | ---: | ---: |
| Common | 718 | 0 |
| Fabric 26.1.2 client | 979 | 0 |
| Paper 26.2 | 58 | 0 |
| Cross-server | 115 | 0 |
| Proxy-common | 167 | 0 |

The plan's focused protocol/handler and active-client commands also passed:

```sh
rtk proxy ./gradlew :common:test --tests '*ProtocolVersionTest' --tests '*CodecTest' \
    --tests '*C2SPacketHandlerTest' :mods:26.1.2-fabric:test \
    --tests '*ClientWaypointCollectionsTest' --tests '*ClientSynchronizationTrackerTest' \
    --console=plain --max-workers=2
```

Older client tests and affected platform branches:

```sh
rtk proxy ./gradlew :mods:1.20.1-fabric:test \
    --tests '*ClientWaypointCollectionsTest' --tests '*ClientSynchronizationTrackerTest' \
    --tests '*ClientHandshakeMapSyncTest' \
    :mods:1.20.1-forge:compileJava :mods:26.3-neoforge:compileJava \
    :mods:1.20.2-neoforge:compileJava :mods:1.20.4-neoforge:compileJava \
    :mods:26.1.2-forge:compileJava :paper:1.21-paper:compileJava \
    --console=plain --max-workers=2
```

Passed: 26 focused older-client tests and all listed compilation tasks. Active targets remain
`26.1.2-fabric` and `26.2-paper`; no active-target switch was used.

Coverage includes scope round trips and malformed input, Alice/Bob/Shared isolation, permission
denial and revocation before queued sends, failed private persistence, equal identifiers/revisions,
scoped tombstones/renames/recovery, account/address/backend cache boundaries, late generations,
source-specific pending edits with equal request IDs, integrated dispatch isolation, and unchanged
Shared map-sync behavior. Java 17 common compilation, translation coverage, and chat-width audits pass.

The first broad runs found missing/overlong persistence-error translations. The strict cache test
also needed actual namespaced dimension identifiers, and the expanded Forge check found an ambiguous
network callback overload. Each issue was corrected before the final passing runs. Existing Gradle,
Unsafe fixture, map integration, and loader deprecation warnings remain visible in the logs.

## Evidence and limits

- [Pre-commit suite log](execution/task-4/precommit-suites.log)
- [Full-suite log](execution/task-4/full-suites.log)
- [Focused-suite log](execution/task-4/focused-suites.log)
- [Version-check log](execution/task-4/version-checks.log)
- [Machine-readable counts](execution/task-4/test-counts.json)
- [Retained failing contract checks](execution/task-4/)
- [Execution ledger](execution/progress.md)

Review was performed inline, following the plan's instruction to avoid delegation. All current
waypoint producers and broadcast paths were checked. Cross-server catalog models were unchanged.
The performance exporter remains tied to its separately supplied published artifact and was not
migrated to this build's protocol. The compatibility warning reports the actual required protocol;
a feature release number has not been assigned. New text exists in all six common/client locales;
human language review is unexecuted.

No live server/client session was run for this task. Automated fixtures do not prove actual
multiplayer privacy, vanilla joins, rendering, physical input, or teleport arrival. Those acceptance
checks remain in Task 8 and the feature validation checklist. The user subsequently authorized a
local Task 4 commit; no push or publication is authorized.
