# Command feedback validation

Date: 2026-10-01. Build: the `cli-improved` branch at `216e84f5`.

## Builds

| Target | Result |
| --- | --- |
| mods 1.20.1-fabric, 1.21.9-fabric, 26.3-fabric | Compiled |
| mods 1.20.2-neoforge, 1.21.11-neoforge | Compiled |
| mods 1.20.1-forge, 26.2-forge | Compiled (the first build of both targets) |
| paper 1.21, 1.21.11, 26.2 | Compiled |
| `:common:test`, `:mods:26.1.2-fabric:test` | 719 and 508 tests passed |

## Live click-through

The checks ran on the project's local test servers: Fabric 26.1.2 through `:mods:26.1.2-fabric:runServer`
and Paper 26.2 through `:paper:26.2-paper:runServer`, with commands typed into each server console.
Minecraft Console Client was the test player.

No player check could run:

- The Fabric dev server disconnects vanilla-protocol clients: "This server requires Fabric Loader and
  Fabric API installed on your client" (the dev runtime carries Xaero's Minimap and World Map, which
  add registry entries). No modded test client was available.
- The Paper test server runs in online mode and the test players are offline accounts. Its
  authentication was left as it is, and no personal account was used.

| Check | Paper 26.2 | Fabric 26.1.2 | Console |
| --- | --- | --- | --- |
| Menu and help | Not run (no player could join) | Not run (needs a modded client) | Passed on both: `/wp` and `/wp help` print the help index, `/wp help list` its usages with indented explanations and examples |
| Creating | Not run | Not run | Passed on both: list and waypoint results, the quick add with a dimension, the existing-list error |
| Lists | Not run | Not run | Passed on both: Tree, Flat, search, single list sorted by name, `limit 1 view flat page 2` with its `… 1 more waypoint: <command>` line, the dimension list, all dimensions and their search; rows show coordinates and dimensions their IDs |
| Details and edits | Not run | Not run | Passed on both: waypoint and list details without buttons, the colour and facing pickers as accepted values, `✘ Nothing changed.` for an identical colour |
| Results and errors | Not run | Not run | Passed on both: `✔ Removed … Restore with /wp restore <token>`, the restore, missing list and waypoint errors, the non-empty list error, `/wp tp` without a target printing its help topic |
| Navigation | Not run | Not run | `✘ Only players can do that.` on both |
| Upload and download | Not run | Not run | Upload: `✘ Only players can do that.`; download: `✘ The waypoints couldn't be delivered to your client.` (download keeps no player check) |
| Remote | Not run | Not run | `Remote servers  0 servers connected` and `No remote servers are connected yet.` on both |
| Plain text under `/execute` | Not run | Not run | `execute as` an armor stand `run wp list` and `run wp details …` print plain text to the console on both; `execute as` a player needs a joined player and did not run |

Each run's test data was removed afterwards; the Paper plugin data matches its backup byte for byte,
and the Fabric waypoint file was put back from its backup after the mod re-saved it with default
fields. The Fabric run added the test players to `usercache.json`; the operator entry it created was
removed again.

### Fixed during the check

- **Paper failed to start** (`159fd9c0`). Paper's help map evaluates command requirements with a source
  that has no level; the `/wp help <topic>` requirement built a full `Viewer`, which reads the source's
  location. Requirements now use the source's permissions only, and
  `CommandFeedbackTest.requirementChecksNeverReadTheSourcesLevel` covers it.
- **Every screen with a link failed on Paper** (`216e84f5`): `NoSuchFieldError` for
  `ClickEvent$Action.RUN_COMMAND`. Paper bundles a newer Adventure than the 4.16 API that `common`
  compiles against. `Click` now calls `ClickEvent.runCommand` and `suggestCommand`, and
  `AdventureCompatibilityTest` fails if a production class refers to `ClickEvent$Action` again.

The unit tests had not caught either failure: they run against Adventure 4.16 and never evaluated
requirements for a source without a level.

## Console colours

Date: 2026-10-02. Build: the `cli-improved` branch at `5aa2468a` with plain text keeping the colours of
chat (spec 15, rule 7). `:common:test` (723 tests) and `:mods:26.1.2-fabric:test` (508 tests) passed.

Paper 26.2 through `:paper:26.2-paper:runServer`, with commands fed to the console and `TERM=xterm-256color`.
Paper's console serializer, kyori ansi 1.1.1, takes its colour level from `TERM` or an attached terminal,
and its code prints no colour when it finds neither, as with a console fed through Gradle and no `TERM`.
`/wp`, `/wp help add`, `/wp list minecraft:overworld`, `/wp list dimensions` and
`set color` without a value printed every piece with an ANSI colour: aqua topic names and commands,
yellow `<arguments>`, gray `[optional parts]`, white then gray explanations, gray row coordinates, gray
names after `No waypoints yet:` and gray accepted values. `logs/latest.log` holds no escape codes, and
the plugin data matched its backup byte for byte.

The help colours of spec 12 were checked the same way afterwards, with `:common:test` (727 tests) and
`:mods:26.1.2-fabric:test` (508 tests) passing. `/wp help`, `/wp help add` and `/wp help list` printed
aqua keywords inside gray brackets and every argument in the colour of its type, in usage lines, at
the start of notes and in examples: `<dimension>`, `<id>` and `minecraft:overworld` green, `<position>`
and `~ ~ ~` light purple, `<yaw>` and `<number>` gold, `<topic>`, `<color>`, `<global>`, `<mode>` and
the example's `name` dark aqua, and text such as `<list>` and `"Home Bases"` yellow.

Dark aqua sat too close to the aqua of command words, so choices became dark purple. A later run of
`/wp help navigate` and `/wp help` printed `<method>`, the example's `bossbar` and `<topic>` in ANSI
magenta next to the bright aqua commands; both suites still passed (727 and 508 tests).

Fabric and NeoForge were not run: their console and RCON print `Component.getString()`, so they stay
plain.

## `/execute as` shows the player's view

Date: 2026-10-02. Build: the `cli-improved` branch at `53cf600d` plus the change that implements
spec 15 (`ModMessageSender` and `PaperMessageSender` route by viewer, `Chat.viewedAs`,
`PlatformMessageSender.forCommander` and `viewingSource`).

| Target | Result |
| --- | --- |
| mods, all 39 targets (14 Fabric, 13 NeoForge, 12 Forge) and paper 1.21, 1.21.11, 26.2 | Compiled |
| `:common:test` | 746 tests passed |
| `:mods:26.1.2-fabric:test`, `:mods:1.20.1-fabric:test` | 508 and 505 tests passed |
| `:paper:1.21-paper:test`, `:paper:1.21.11-paper:test`, `:paper:26.2-paper:test` | 25 tests passed on each |

`ChatTest.theViewedAsLineIsGrayItalicsWithThePlayersNameInYellow` pins the line in English and Spanish, and
`PlatformMessageSenderViewTest` the copy the commander gets.
`CommandFeedbackTest.thePermissionsAreThoseOfTheSourceTheFeedbackIsViewedFrom` pins that the viewer's
permissions come from the platform's `viewingSource` while the position stays the source's; it fails
when the hook is bypassed.

`PaperMessageSenderDeliveryTest` pins who gets what on Paper, with stand-ins for the sender, the
executor and the source, in five cases: a player's own command, the console running as a player, a
player running as another player, a command running as an armor stand, and the console alone. It ran
on the Adventure each target bundles (4.17.0 on 1.21, 4.26.1 on 1.21.11 and 5.2.0 on 26.2), so it
also showed that the new `common` code, compiled against Adventure 4.16, runs on all three.

Two things have no test. The sends themselves: the `CommandSourceStackAccessor` mixin behind
`ModMessageSender.sendMessage` is not applied in a plain JUnit run, and Paper's region scheduling
needs a server. And `viewingSource` on both platforms, which needs a real player. These need a live
check, and it did not run. The mods check needs real players, such as a throwaway mod that places
`ServerPlayer`s on the Fabric 26.1.2 dev server, the way the original defect was found: a console
running `execute as ProbeA run wp help` printed nothing while ProbeA got the console layout, and
ProbeA running `execute as ProbeB run wp help` sent ProbeB the layout rendered for ProbeA. The Paper
test server runs in online mode and its authentication was left as it is, so that check needs an
online test account. The expected results now, on both platforms:

| Command | The player it runs as gets | The commander gets |
| --- | --- | --- |
| console: `execute as ProbeA run wp help` | ProbeA's language and buttons, with the trailing newline | `Viewed as ProbeA`, then the same text without the colours and without the trailing newline |
| ProbeA: `execute as ProbeB run wp help` | ProbeB's language and buttons | `Viewed as ProbeB` in ProbeA's language, then ProbeB's view with the trailing newline |
| ProbeA: `wp help` | one message, no `Viewed as` line (ProbeA is both) | none |
| ProbeA: `execute as <armor stand> run wp help` | nothing | ProbeA's own view |
| a datapack function running `wp help` (Fabric, NeoForge and Forge) | nothing | nothing |

## Risks

- `Open GUI` (`/wp_gui`): not checked; it needs a modded client.
- Translations that wrap: not observed live, but measured in all six locales since 2026-10-02.
  `ChatFont` used to count every glyph beyond ASCII and its symbols as 9 px. It now has the advance
  of every glyph in the three sheets of the vanilla bitmap font, taken from the 26.3 client (`ñ` is
  6 px, `í` 3 px, most Hebrew letters 6 px), and only Chinese still counts as 9 px, the advance of a
  full-width Unifont glyph. `ScreenAuditTest.everyScreenFitsChatInEveryLocale` renders the 86 player
  screens in each locale and measures every line. `en_us`, `zh_cn`, `zh_hk`, `zh_tw` and `he_il` fit
  (the widest line, 319 px, is the English `/wp upload` usage), but 14 `es_es` lines pass 320 px, from
  323 px to 385 px (the navigation panel's methods), so chat wraps them. The maintainers accepted
  these wraps on 2026-10-02: the test lists them in `ACCEPTED_WRAPS` and fails on any other line over
  320 px, and on a listed line that fits again. The 9 px estimate had put 26 `he_il` lines over, up to
  423 px. Help topic titles start with their label and `wp.help.topic.hint`; the shorter `es_es` hint
  was needed (the original gives a 430 px title), the shorter `he_il` one was not (the original gives
  240 px). `HelpScreenTest.everyTopicTitleFitsTheChatInEveryLocale` also checks that every title is
  translated.

## Follow-ups

- Run the player click-through: with a modded client on the Fabric dev server, and on Paper with an
  online test account (or a local offline-mode Paper server if the maintainers want one).
- Run the `/execute as` live check above with real players: on the Fabric dev server, and on Paper
  with an online test account.
- Before the `Click` fix, `/wp add` on Paper created the list and the waypoint and then failed to
  build its reply, so the player saw an internal error for a change that was saved. Commands save
  before they build their feedback, so any failure while building a reply still looks like a
  failed command.
