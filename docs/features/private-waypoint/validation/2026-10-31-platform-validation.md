# Task 8: platform validation and player documentation

Historical first pass. Its open acceptance items were addressed by the
[final acceptance continuation](2026-10-31-final-acceptance.md).

Date: 2026-10-31 (America/Toronto). Source: `feature/private-waypoint`, clean starting
commit `ea91edcb`. This task changes documentation and retained validation artifacts only.
No production code was changed, committed, pushed or published.

Status: automated suites, representative builds, and the live checks listed below have been
executed. **Acceptance remains partial**: unchecked scenarios in the
[checklist](2026-10-16-private-waypoint-checklist.md) remain required. Passing unit tests is
not a substitute for those scenarios.

## Automated tests and compilation

Executed from the repository root; full output is retained in
[task discovery](execution/task-8/task-discovery.log),
[full suites](execution/task-8/full-tests.log), and
[representative compilation](execution/task-8/representative-compile.log).

```sh
rtk proxy ./gradlew projects :mods:26.1.2-fabric:tasks --all --console=plain --max-workers=2
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test :mods:1.20.1-fabric:test :paper:26.2-paper:test --rerun-tasks --console=plain --max-workers=2
rtk proxy ./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.1-forge:compileJava :mods:26.1.2-fabric:compileJava :mods:26.3-neoforge:compileJava :paper:1.21-paper:compileJava :paper:26.2-paper:compileJava :paper:1.21-paper:shadowJar --console=plain --max-workers=2
```

All three commands exited 0. The four suites executed **2,996 tests**, with zero failures,
errors or skips: common 720; Fabric 26.1.2 1,120; Fabric 1.20.1 1,098; Paper 26.2 58.
[Counts](execution/task-8/test-counts.json) were read from the generated JUnit XML.
The full suites include collection keys, owner quotas/lifecycle/concurrency, restore,
commands, codecs, packet delivery, Local profiles, access, query, manager, forms, renderer,
translations and existing map-integration tests. Gradle emitted deprecation/Unsafe warnings.

Both configured Stonecutter active projects stayed unchanged (`26.1.2-fabric` and
`26.2-paper`). Direct target generation/compilation succeeded for every representative
loader/API branch above. No tracked source comments were switched. This is representative
coverage, not an all-version runtime claim.

## Live server-private commands: Paper 1.21

A disposable server at `/Volumes/ssd/sw-private-task8-20261031/paper` bound only to
`127.0.0.1:25638`, using Paper `1.21-130-master@b1b5d4c`, JDK 21.0.11, and the freshly built
`server_waypoint-4.0.1-paper-mc1.21-1.21.8.jar`. It used a new offline-mode world, new config,
and no permission plugin. This did not change an existing server or account configuration.

Two MCC v26.2 build 499 clients joined without Server Waypoint custom channels:
Alice `10920508-d5d8-3eed-93d2-92f193afe7d7` and Bob
`faa5dca3-c3d4-354b-ae1b-dde9e5a14b3b`. These are **vanilla-protocol bots**, not graphical
vanilla clients. MCC was launched in isolated `alice/` and `bob/` directories:

```sh
rtk proxy /opt/homebrew/opt/openjdk@21/bin/java -Xms512M -Xmx1536M -jar server.jar --nogui
rtk proxy /Volumes/ssd/minecraft_console_client/MinecraftClient Alice - 127.0.0.1:25638 --EnableSentry=false --Language=en_us
rtk proxy /Volumes/ssd/minecraft_console_client/MinecraftClient Bob - 127.0.0.1:25638 --EnableSentry=false --Language=en_us
```

In MCC, `/send` forwards the following Minecraft commands. Exact issued commands and actions
are retained in the [server log](execution/task-8/paper-server.log), with normalized
[Alice](execution/task-8/alice-out.log) and [Bob](execution/task-8/bob-out.log) output.

- Alice added `Homes / Base` at `(12, 80, -30)`, listed/viewed it, edited its description,
  started Actionbar navigation, removed it, and added it again. Bob initially saw no lists
  and could not resolve Alice's waypoint through details. Bob then independently added
  `Homes / Base` at `(-44, 80, 22)`. His details continued to show his own coordinates and
  no Alice description. Private add/edit/remove announcements were absent from Bob's chat;
  the deliberately created Shared entry at `(100, 80, 100)` did broadcast to him.
- Non-operator Alice's `/wp private tp minecraft:overworld Homes Base` was rejected. After
  the console explicitly ran `op Alice`, it succeeded. `data get entity Alice Pos` returned
  `[12.5d, 80.0d, -29.5d]`; an Overworld selector within one block printed
  `TASK8_PRIVATE_ARRIVAL_OK`. The half-block X/Z offsets are the platform's block-centering
  behavior; the destination was distinct from the Shared waypoint.
- Alice added Nether `Homes / NetherBase` at `(-12, 80, 30)` and teleported there. Entity
  queries returned `[-11.5d, 80.0d, 30.5d]`, `"minecraft:the_nether"`, and the bounded Nether
  selector printed `TASK8_NETHER_ARRIVAL_OK`. These are actual arrival observations.
- Bare console `wp private list` returned `Only players can do that`. Console
  `execute as Alice/Bob run wp private …` selected their respective data and printed
  `Viewed as`. This does not establish all command-block/RCON behavior.
- Reloading a two-waypoint quota rejected the next addition in `RejectedList` across Alice's
  two dimensions. Reducing the quota to one preserved both entries and still permitted a
  description edit. The saved JSON contains no `RejectedList` and no private entry in Shared.
- A controlled server shutdown/restart and MCC reconnect restored Alice's private list.
  Final shutdown used `stop`; both clients quit. Alice later suffocated at the deliberately
  chosen Nether block before the quota-edit check; arrival had already been confirmed.
  Spectator mode and MCC respawn were used only on this disposable fixture. The final edit
  was executed as Alice from console after MCC reported chat disabled following respawn.

Retained disk evidence: [Shared](execution/task-8/paper-waypoints/),
[owner directories](execution/task-8/paper-private-waypoints/). Console logs and MCC
plain text do not prove clickable chat-event payloads, command suggestions or modded-recipient
packet privacy. Those retain their unit-test proof and unchecked live scenarios.

## Live client collections and persistence: Fabric 26.1.2

Fresh production JAR plus Fabric API only; no Xaero or VoxelMap. JDK 25.0.4.1,
Minecraft 26.1.2, isolated offline identity `SwLiveTest`. A scratch copy of
`tools/live-game-test` used the [core probe patch](execution/task-8/core-probe.patch).
The probe is external to the production JAR and uses native game-thread handlers.

Executed commands (working directory is the repository):

```sh
rtk proxy python3 /Volumes/ssd/sw-private-task8-20261031/live-game-test/run.py prepare --repo /Volumes/ssd/fabric_mods_repo/server_waypoint --manifest /Volumes/ssd/sw-private-task8-20261031/manifest.json --jdk /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home --output /Volumes/ssd/sw-private-task8-20261031/prepared
rtk proxy python3 /Volumes/ssd/sw-private-task8-20261031/live-game-test/run.py run --prepared /Volumes/ssd/sw-private-task8-20261031/prepared --output /Volumes/ssd/sw-private-task8-20261031/results --heap 1024
```

All seven scenario assertions passed: create, collections, manager layout, close, load,
persisted, close. [Result and hashes](execution/task-8/core/result.json),
[game log](execution/task-8/core/game.log), [observations](execution/task-8/core/observations.txt).

- Shared/Private additions traveled through commands and synchronization; Local saved directly.
  Combined retained three distinct `minecraft:overworld / Homes / Base` selections.
- Private-to-Local Copy cancellation created no destination list; explicit save created a
  detached copy. Switching profiles invalidated an old captured destination. Selecting the
  original profile and deleting the temporary one retained its data.
- All four views were opened at 427×240. Add, Copy and profile controls opened the expected
  native screens. Placement, tooltips, a long profile name with ellipsis/full tooltip, and
  854×480 placement passed the probe's geometry assertions.
- Closing and reopening the integrated world restored Shared, Private and Local entries.
  [Saved stores](execution/task-8/core/saved-stores/) preserve the three storage roots.

The runner exited **1**, despite passing feature assertions: its strict runtime gate found
`Failed to fetch user properties` and `Couldn't connect to realms` for the offline account.
They are retained in [diagnostics](execution/task-8/core/diagnostics.json). Do not describe
this run as an unconditional runner pass. No feature crash or assertion failure occurred.

## Native Spanish controls and constrained layouts

A separate fresh probe applied [GUI patch](execution/task-8/gui-probe.patch), reloaded
actual `es_es` resources, hid the tutorial/toasts/HUD, and used 427×240 GUI coordinates.
Preparation and execution used `gui-tool/run.py`, `gui-prepared-2`, and `gui-results-2` under
the same scratch root with the same flags as the core command above. Logs:
[prepare](execution/task-8/gui-prepare-2.log), [run](execution/task-8/gui-run-2.log),
[assertions](execution/task-8/gui/03-input-translations.result),
[observations](execution/task-8/gui/observations.txt).

- All four collection popups opened; wheel events, Escape closing the popup while keeping
  the manager open, and Tab finding a focus target passed through native handlers.
- New-profile Escape cancellation, focused name input, Enter creation, invalid-template Save
  disabling, valid-template Enter save, and Delete cancel/confirm passed.
- Captured frames include [Combined](execution/task-8/gui/spanish-combined.png) and
  [long profile controls](execution/task-8/gui/spanish-profiles.png). Images were visually
  inspected separately from the handler assertions.

The feature scenarios passed. Overall runner exit **1** again reflects the two offline
account/Realms errors, retained in [diagnostics](execution/task-8/gui/diagnostics.json).
An earlier attempt started before the Private snapshot was ready; it correctly rejected
that unavailable destination. The scratch probe now waits for Private readiness.

## Local on unsupported/incompatible servers and one address

A second disposable Paper 26.1.2 build 63 server bound only to `127.0.0.1:25639`,
using JDK 25. First it had no plugins; after a clean stop it restarted with a different
world and the retained **4.0.0/protocol-2** plugin. The client used today's protocol-3
production JAR. [Server artifact hashes](execution/task-8/server-artifacts.json),
[unsupported log](execution/task-8/unsupported-server.log),
[incompatible log](execution/task-8/incompatible-server.log).

```sh
rtk proxy python3 /Volumes/ssd/sw-private-task8-20261031/multiplayer-tool/run.py prepare --repo /Volumes/ssd/fabric_mods_repo/server_waypoint --manifest /Volumes/ssd/sw-private-task8-20261031/manifest.json --jdk /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home --output /Volumes/ssd/sw-private-task8-20261031/multiplayer-prepared-3
rtk proxy python3 /Volumes/ssd/sw-private-task8-20261031/multiplayer_controller.py
```

The [controller](execution/task-8/multiplayer_controller.py) starts/stops only the disposable
server and drives the native runner; the [probe patch](execution/task-8/multiplayer-probe.patch)
records its exact operations. All six assertions passed: connect unsupported, close, connect
incompatible, close, reconnect incompatible, close. Local remained ready and Combined usable;
Shared/Private remained unavailable. The client reported `NO_SERVERSIDE_SUPPORT` and then
`INCOMPATIBLE_PROTOCOL`, respectively.

The original Local entry survived the server/world restart at the same host/port. A manually
created second profile began empty, saved its own entry, remained selected on reconnect, and
switching back restored the first world's entry. [Observations](execution/task-8/multiplayer/observations.txt),
[result/hashes](execution/task-8/multiplayer/result.json),
[saved Local indexes and profiles](execution/task-8/multiplayer/saved-local/).
This tests manual multiworld handling at one address, not an actual proxy transfer or late handshake.

The strict runner exited **1** only after all feature assertions completed: four non-ignored,
deduplicated offline account/Realms/profile-key errors. The complete diagnostics also retain an
ignored Realms feature-flags error. [Diagnostics](execution/task-8/multiplayer/diagnostics.json).
Both server instances were stopped by the controller.

Earlier attempts are retained under [failed attempts](execution/task-8/failed-attempts/):

- A 3.1.0/protocol-0 plugin used an older wire layout and disconnected the client during custom
  payload decoding. That historic wire format remains unsupported; this task adds no compatibility
  shim. It is not a successful incompatible-backend test.
- The next probe used static fields across separately loaded scenario classloaders. Disk logs showed
  the correct unchanged profile, but the comparison used a reset field. Profile IDs now pass through
  small evidence files between scenarios; production code was not involved.
- The GUI probe initially attempted Private before its snapshot was ready; it now waits for readiness.
- The map-isolation probe initially expected the fixture's later edited name before running that edit.
  The final check uses the seed's actual `map-fixture` name.

## Existing Shared map integration: Fabric 1.21.5

The unmodified native editor suite ran against a freshly prepared production JAR with its
pinned Fabric API, Xaero Minimap/World Map and VoxelMap dependencies:

```sh
rtk proxy python3 tools/live-game-test/run.py prepare --repo /Volumes/ssd/fabric_mods_repo/server_waypoint --manifest /Volumes/ssd/sw-private-task8-20261031/editor-manifest.json --jdk /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home --output /Volumes/ssd/sw-private-task8-20261031/editor-prepared
rtk proxy python3 tools/live-game-test/run.py run --prepared /Volumes/ssd/sw-private-task8-20261031/editor-prepared --output /Volumes/ssd/sw-private-task8-20261031/editor-results --heap 1024
```

**PASS, exit 0**, zero runtime errors. All editor steps, mixin audit, server form round trips,
Xaero/VoxelMap add/edit/cancel/share paths, default-set behavior, world reload and independent
saved-JSON assertions passed. [Result and artifact hashes](execution/task-8/editor/result.json),
[game log](execution/task-8/editor/game.log), [run log](execution/task-8/editor-run.log).
Warnings remain recorded; this is one Fabric runtime, not a loader-wide gameplay certification.

## Live map exclusion of Local and Private

A separate fresh Fabric 1.21.5 native run applied the
[map probe patch](execution/task-8/maps-probe.patch), with the same pinned map dependencies
as the full editor suite. Commands used `map-tool/run.py prepare` with `editor-manifest.json`
and output `map-prepared-3`, then `map-tool/run.py run --prepared .../map-prepared-3
--output .../map-results-3 --heap 1024` under the scratch root. Exact preparation and runner
output: [prepare](execution/task-8/map-prepare-3.log), [run](execution/task-8/map-run-3.log).

**PASS, exit 0**, zero runtime errors. It created a real Shared seed, added Local and Private
`HiddenCollections` entries through production access, awaited Private synchronization, and forced
both installed map integrations to synchronize. Each native upload snapshot contained the Shared
`map-fixture` and contained neither the hidden list nor either hidden waypoint.
[Assertions](execution/task-8/maps/03-collection-isolation.result),
[observations](execution/task-8/maps/observations.txt), [result/hashes](execution/task-8/maps/result.json).
This proves no Local/Private data appeared in these native map stores; event-dispatch isolation also
has unit-test coverage. It does not certify every map release or backend combination.

## Documentation, review and remaining acceptance

The [player guide](../../../tips/server-guide.md#shared-private-and-local-waypoints) now covers
storage, ownership, private command permissions/quotas, profiles, explicit Copy behavior,
teleport tokens/limits/permission decisions, and backups. GUI guidance and feature/manager
indexes link to it. No release is being prepared, so release changelogs were not changed.

Executed `rtk proxy python3 docs/features/private-waypoint/validation/execution/task-8/verify_documentation.py`
and `rtk git diff --check`: both passed (111 relative link paths across eight documents); results are retained in the execution folder. The existing full
translation suites additionally validate server MessageFormat patterns/apostrophes and client
formatting. Fluent-language review is separate from these checks.

Review follows the plan's inline-only rule: no subagents were dispatched. Revisited the feature
commit range and critical ownership/delivery, lifecycle, query, form destination and scene boundaries;
reviewed the Task 8 diff separately. No compatibility handling was added for older wire formats.

Still required: a graphical vanilla client; complete two-modded-player packet privacy and
permission revocation; chat-click/suggestion/restore/navigation reconnect coverage; Folia and
representative mod-loader dedicated-server acceptance; live proxy transfers with late results;
all failure dialogs/narration and physical input; explicit identical-coordinate marker appearance;
and fluent review of every locale. Checkboxes remain open where a scenario requires more than
this record proves. Unit/in-process fixtures and native handler calls do not certify those gaps.

Final fixture cleanup: both owned listening ports, 25638 and 25639, were closed after graceful
shutdown; native runners exited their game processes. Scratch worlds remain only under the
recorded disposable root for investigation. [Aggregate final probe outcomes](execution/task-8/probe-summary.json)
retain the distinction between 41 passing scenario groups and three nonzero offline-service gates.
