# Private/local waypoint final acceptance

Date: 2026-10-31. Source: `feature/private-waypoint`, `ea91edcb`, plus the uncommitted Task 8
documentation/evidence. **Implementation Tasks 1–7 and the planned Task 8 validation are complete.**
No additional production change was needed. No commit, push, publication or compatibility shim was made.
This supersedes the open acceptance items in the [first platform pass](2026-10-31-platform-validation.md).

## Scope and evidence

All tests used isolated offline accounts and disposable loopback servers under
`/Volumes/ssd/sw-private-final-20261031`. Existing user servers, accounts and launcher profiles were
not changed. Native clients used a fresh production JAR and Fabric API; the graphical vanilla
client ran `net.minecraft.client.main.Main` with an empty mods directory, bypassing Fabric's main
entry point. The external observer/controller transforms no production bytecode.

[Summary](execution/final-acceptance/summary.json),
[artifact hashes](execution/final-acceptance/artifact-hashes.json),
[executed driver sources and logs](execution/final-acceptance/README.md).
The preceding 2,996-test run and six representative compilation targets remain applicable:
production sources and Stonecutter targets were unchanged throughout this continuation.
Fresh production assembly also succeeded for Paper 1.21.11/26.2, Fabric 26.1.2 and Fabric 1.21.3.

## Two modded identities and a graphical vanilla client

Paper 26.1.2 build 63 with JDK 25, current 1.21.11–26.1.2 plugin; clients WPAlice, WPBob and
WPVanilla. The full game clients joined independently, not through an in-process command fixture.
[Driver](execution/final-acceptance/acceptance.py), [results](execution/final-acceptance/acceptance.log).
Each role directory retains action commands, PASS result files, chat text, raw click-command values,
server-driven suggestions, cache snapshots and game logs.

- All three players created their own `Homes / Base`. Alice additionally created `OnlyAlice / AliceSecret`.
  Bob's menus, server suggestion responses and Private client cache excluded Alice's identifiers/data.
  Alice's private edit changed neither Bob's chat announcements nor any of his collection snapshots.
- The actual vanilla client's menu/help/details/pagination component click events all retained
  `/wp private`. Property edit actions were captured in those components. Private command/Resume
  prefix cases are additionally covered by the common scope/feedback suite.
- Bob's attempt to use a token removed by WPVanilla failed without consuming it. WPVanilla restored
  it successfully; reusing it failed. A separate live name-conflict restore preserved the first token
  until the conflicting waypoint was removed, after which that original token succeeded.
- Vanilla Private Actionbar navigation survived disconnect/reconnect with the correct target and
  Private links. Ordinary private teleport was denied. A narrow `private.tp` attachment enabled it;
  the graphical client's actual position matched its own target, not another collection's coordinates.
- Live `private.list` revocation followed by a real update request set Alice's Private state to DENIED
  and cleared only Private presentation. Shared and Local snapshots remained byte-for-byte equal.
  A regrant and request recovered the collection.

## Native manager, copies, forms and marker identity

The modded client's actual collection access, screens, query/widgets and optimized renderer ran
against the live dedicated server. These are native game-thread event/controller checks.

- Combined kept duplicate Base selections distinct. Grouped/flat search and name sorting ran in
  each view; Add's captured destination matched every single-source view and Local in Combined.
- Private Edit retained its captured identity after changing the manager setting to Local; Shared
  and Local stayed unchanged. Profile changes made an open copy form obsolete without losing its draft.
- Local-to-Shared and Local-to-Private copies preserved identifier, display name, initials, negative
  coordinates, color, yaw, global visibility, keywords, description and icon. Cancel created no list;
  name conflict preserved both entries. Permission and quota denial created no destination list and
  preserved the source. Saved Private JSON independently excludes `DeniedCopies` and `QuotaCopies`.
- Real renderer references retained three source-owned Base instances, including coincident
  Local/Private coordinates. Hiding Private left Shared/Local visible. Shared resync preserved them;
  a Private tombstone removed only Private. [World frame](execution/final-acceptance/visible-collection-markers.png)
  confirms actual world-marker drawing; coincident labels can naturally occupy the same pixels.
- Collapsing the Shared list left same-named Private/Local expansion states unchanged. A populated
  test Remote catalog stayed out of Combined, and entering the actual Remote browser hid Add, Copy,
  profile and collection controls. This live UI test injects a documented catalog fixture; it is not
  a claim about new proxy catalog transport.
- Private Bossbar navigation followed by Shared Actionbar navigation removed the old bossbar and
  retained Shared action links. [Follow-up log](execution/final-acceptance/denial-resume.log).

## Folia and Fabric dedicated backends

The same ownership/CRUD/navigation/default-teleport-denial and authorized-teleport checks were
repeated with ScriptAlice/ScriptBob identities on Folia 26.2 and Fabric 1.21.4 (the supported
1.21.3–1.21.4 production target). Both final runs passed. Actual arrival was `(12.5, 80, -29.5)`;
Folia position was read through the player's entity scheduler, Fabric through entity data.

- [Folia result](execution/final-acceptance/folia-script-3.log) and
  [server evidence](execution/final-acceptance/folia/script-server.log).
- [Fabric result](execution/final-acceptance/platform-scripts-2.log) and
  [server evidence](execution/final-acceptance/fabric/script-server.log).

Failed fixture attempts remain recorded: PTY input was unsuitable for MCC; its text-script wait
uses ticks, so waits were corrected to 20 ticks/second per the
[official MCC usage](https://mccteam.github.io/guide/usage.html). A staging glob initially selected
a development shadow JAR; the final Fabric run explicitly used the remapped production JAR.
Folia's global-console entity query was replaced by the entity-scheduled probe. These were test
setup corrections, not production fixes. No failed attempt is counted as a passing platform run.

## Proxy transfer and late handshake

Two fresh Paper 26.1.2 backends and Velocity 4.2.0, front address `127.0.0.1:25653`.
[Driver](execution/final-acceptance/proxy_acceptance.py),
[results](execution/final-acceptance/proxy-acceptance-2.log).

A real `/server b` transfer, without disconnecting from the proxy, selected a distinct empty Local
profile. The outgoing A data had been saved; a captured A destination was rejected after transfer.
On B, a manual profile selection survived a deliberately late, valid protocol-3 handshake packet
with a new backend identity sent by the test plugin. Returning with `/server a` restored A's original
profile and both Local entries. Unit tests additionally cover late edit/results/chunk-generation rejection.

The proxy's first startup used incomplete forced-host defaults; its corrected disposable configuration
is the run above. The native driver's final wait was held while extra observers used its owned client,
then resumed for cleanup. Test-only access/handshake probes are absent from production artifacts.

## Unsupported server and rendering after reconnect

After the live denial checks, the same disposable Paper server was stopped and restarted with its
Server Waypoint JAR disabled. Two successive joins restored Local data and native marker references;
Shared/Private stayed unavailable. [Result](execution/final-acceptance/unmodified-acceptance.log),
[client assertions](execution/final-acceptance/unmodified-client.log),
[world frame](execution/final-acceptance/visible-local-without-server-mod.png).
The disabled fixture JAR was restored after shutdown. The earlier pass also tested a protocol-2
incompatible backend and singleplayer save/reload. Local requires no map mod.

## Six-locale layout, narration and language review

Resources were actually reloaded for `en_us`, `zh_cn`, `zh_tw`, `zh_hk`, `es_es` and `he_il`.
Thirty final screenshots cover all four views and profile controls at 427×240 GUI coordinates.
Examples: [English Shared](execution/final-acceptance/WPProxy/screenshots/clear-en_us-SHARED.png),
[English Local](execution/final-acceptance/WPProxy/screenshots/clear-en_us-LOCAL.png),
[Hebrew Combined](execution/final-acceptance/WPProxy/screenshots/clear-he_il-COMBINED.png),
[Spanish profiles](execution/final-acceptance/WPProxy/screenshots/clear-es_es-profiles.png).
The first capture set overlapped vanilla's resource-reload fade; final `clear-*` frames wait for it.
Representative frames across every view, CJK, Spanish and RTL profiles were visually inspected.

The source dropdown produced localized narration. Native popup/wheel/Escape and profile-dialog
focus/Enter/cancel/confirm checks passed. A final hover probe rendered the expanded popup and
read the actual scheduled tooltip at a menu item, confirming popup hover ownership in all four views.
[Hover assertions](execution/final-acceptance/hover/03-input-translations.result),
[tooltip observations](execution/final-acceptance/hover/manager-layout-evidence.txt).

Language review was performed by Codex, separately from key/placeholder coverage: reviewed the
new common Private feedback plus client collection/profile/copy terminology, ownership, permission,
quota, deletion and unsaved-command meanings across all six locales. Existing locale conventions
were retained; no wording change was required. This is not native-speaker/community translator sign-off.

## Completion boundaries and cleanup

All original implementation and acceptance tasks now have recorded evidence; no implementation TODO
was discovered. This does not certify every game version, map release, permission plugin, operating
system input stack or screen-reader interaction. Input checks used actual native game event handlers.
CUA inventory did not expose the Java app (`Invalid app: java`), so OS-injected mouse/keyboard behavior
was not certified. Source-dropdown narration was tested; this is not a whole-screen accessibility audit.

Offline-account/Realms/profile-key errors remain in native 26.1.2 game logs and make the stock strict
runner fail its service-error gate despite passing feature assertions. They were not suppressed or
reported as clean runner passes. The earlier stock Fabric 1.21.5 editor and map-isolation runs remain
clean PASS results. No compatibility support for historic protocol-0 wire layouts was introduced.

All owned clients, two proxy backends, proxy, Paper/Folia/Fabric and plugin-free fixture instances were
stopped; final listener/process checks are retained with the evidence. No user server was stopped.
