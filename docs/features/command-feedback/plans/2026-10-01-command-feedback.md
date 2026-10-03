# /wp Command Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild every chat message of `/wp` as the design describes: a chat kit with one visual language, one builder per screen, clickable menus and pickers, line-budget paging, remote browsing that works like local lists, and plain-text output for the console, RCON and command blocks.

**Architecture:** A chat kit in `common/.../text/chat/` holds the pieces every message is made of (`Click`, `Tooltip`, `Chat`, `ChatLines`, `Viewer`, `DimensionStyle`, `ChatFont`, the list controls and paging). Screen builders in `common/.../text/feedback/` take a `Viewer` (or a `DimensionStyle`, which carries one) and plain data and return a `Component`; they hold no command state. Remote screens reuse the local paging and controls on lists converted from catalog snapshots. `CoreWaypointCommand` and `RemoteWaypointCommand` keep parsing and actions, build the `Viewer` once per command source and call the builders. The platform senders add the trailing newline for players and stop colouring errors.

**Tech Stack:** Java 17, Adventure API 4.16 (`net.kyori.adventure.text`), Brigadier 1.0.18, JUnit 5, GSON, Gradle Kotlin DSL (no build changes), Stonecutter for the `mods` and `paper` targets.

**Spec:** [`docs/features/command-feedback/specs/2026-10-01-command-feedback-design.md`](../specs/2026-10-01-command-feedback-design.md). The plan argues from it, so read both. Where they differ, the spec wins, except for the places listed in "Decisions where the spec was silent or inexact" at the end, which the plan had to choose.

> **Amended 2026-10-02.** On every platform, `/execute as <player>` no longer follows the receiver. The feedback is the player's view, and the commander gets a copy under a `Viewed as <player>` line (spec 15). Review Focus 3, the receiver rule in Task 6 (and the `isPlainTextReceiver` Javadoc it quotes), the `adventure-text.md` excerpt in Task 20 and step 9 of the Task 21 click-through below describe the original rule.

## Global Constraints

- Java 17 compatible code, 4-space indentation, never tabs; Kotlin DSL for Gradle scripts (none change); GSON for JSON. (`AGENTS.md`)
- Translations use `MessageFormat`: zero-based placeholders such as `{0}`, never `%s`; literal apostrophes are written `''`; placeholder indices match across locales. (`AGENTS.md`, spec 18)
- No backward-compatibility code and no aliases for removed commands. (`AGENTS.md`, spec 16)
- Chat is 320 GUI px wide; an open chat shows 20 lines. In English with typical names, every line is at most 320 px and every message at most 19 lines plus its trailing blank line. (spec 1)
- The client refuses click commands longer than 256 characters: such an action is rendered as plain text without a click. (spec 1)
- Only these glyphs are used: `✔ ✘ ● ⏷ ↑ ↓ ‹ › « » … · ■ █ ✎ × − + °`. `⋯ ▸ ▾ ✓ ✗` and `▼` are not used. (spec 2.2)
- No bold. Titles are gold at regular weight. Separators are ` · ` in dark gray, always a component of their own, never inside a translation. (spec 2.3, 18)
- Colours follow the table in spec 2.1. Informational text is never dark gray.
- Every message to a player ends with exactly one newline, added at the send boundary; builders never end with `\n`; plain-text viewers get no trailing newline. (spec 2.3)
- Controls the viewer can't use (permission or client mod) are left out. The disabled controls are Distance sorting outside the player's dimension (spec 6.4) and `[Remove]` on a non-empty list (spec 8.2).
- Mod-only controls are `Open GUI`, `Download` and `Upload`. A player has the mod when the handshake has completed or they host the singleplayer world. (spec 2.3)
- Destructive actions (remove, clear, reload, `Mirror`) suggest their command; their tooltip ends with `Press Enter to confirm`. (spec 2.3)
- Every new or changed key ships in all six locales: `en_us`, `zh_cn`, `es_es`, `he_il`, `zh_hk`, `zh_tw`. New keys use the `wp.` prefix. Non-English strings are machine drafts that need a native speaker's review before release; each locale keeps its existing terms (`路径点`, `路徑點`, `punto de ruta`, `נקודת ציון`). Keys that lose their last use are removed. (spec 18)
- Builders follow `docs/tips/adventure-text.md`: every line is built from a neutral parent, so click and hover events never spill onto neighbouring text. (spec 17)
- A helper used by one project stays in that project. Everything new here is in `common`, which `mods` and `paper` both use. (`AGENTS.md`)
- Stonecutter: write `mods` and `paper` code in the active target's form, keep inactive branches, and check marker balance after editing. (`AGENTS.md`)
- Commit after every task. The person asked for step-by-step commits, which overrides the `AGENTS.md` default of not committing.

## Review Focus

The spec says what each message must look like; these are the inputs a person will meet that it doesn't spell out. Each has a test in the task that owns the code.

1. **Names that need quoting**: a list called `search`, `Farm "North"`, names with spaces. Every generated command must quote them so the click reaches the same list. Pinned by `ListTargetTest` in Task 5 and `ListScreenTest` in Task 9.
2. **Identifiers long enough to push a command past 256 characters.** The control turns into plain text without a click, the rest of the message still renders, and nothing throws. Pinned by `ChatTest` in Task 2 and `DetailsScreenTest` in Task 12.
3. **A plain-text receiver running as a player**, for example `/execute as Alex run wp list` from the console. The output follows the receiver: plain text, no trailing newline. Pinned by `CommandFeedbackTest` in Task 8.
4. **JSON-formatted display names and descriptions.** They render with their own style without leaking it onto the text after them, and plain-text viewers get their flattened text. Pinned by `WaypointRefsTest` in Task 7.
5. **The empty identifier** (a list or waypoint whose identifier is `""`). Commands carry `""`, and the screens still show a readable name. Pinned by `ListScreenTest` in Task 9 and `DetailsScreenTest` in Task 12.

## Environment and conventions

Read [`AGENTS.md`](../../../../AGENTS.md) and [`docs/tips/adventure-text.md`](../../../tips/adventure-text.md) first. The skills `stonecutter-gradle-version-tasks`, `stonecutter-versioned-comments` and `reference-minecraft-source-code` help with the few `mods` changes; `test-minecraft-server-commands` helps with the live check in Task 21.

- **Checkout.** Work in `/Volumes/ssd/fabric_mods_repo/server_waypoint` on branch `cli-improved`, which has the spec commit `cdb09649` in its history.
- **Git.** Call it as `/usr/bin/git` from the repository root, without `-C` and without globs.
- **Gradle.** The Bash tool doesn't source `~/.zshrc`, so every Gradle command sets the SSD Gradle home in the same command, and every command passes the JDK 25 path (Gradle configures the 26.x targets even for `:common` tasks). Use literal paths, not shell variables (the `rtk` hook shifts their arguments). All commands below use this shape:

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.ChatTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

- **Platform compile checks.** Tasks that touch `mods` or `paper` sources also compile the active targets:

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

- **Active Stonecutter target** is `26.1.2-fabric` (`mods/stonecutter.gradle.kts`). Source files in `mods/src` are written in that target's form, with the other versions in comment branches.
- **Disk.** The system disk is nearly full. Run `df -h /System/Volumes/Data /Volumes/ssd` before building a target that was never built. `--offline` works for Fabric targets and fails for NeoForge and Forge (`netty 4.1.+`).
- **Scratch files.** `SCRATCH` in the commands below stands for your scratchpad directory; write it as a literal path, since the `rtk` hook shifts the arguments of shell variables.
- **Marker check.** Save this script as `check_stonecutter.py` in your scratchpad directory and run it on every `mods` or `paper` Java file you edit:

```python
#!/usr/bin/env python3
"""Checks that the Stonecutter conditional markers in the given Java files are balanced."""
import re
import sys

OPEN = re.compile(r'^\s*(?:\*/)?//\? (?:if|elif) .*\{\s*$')
MIDDLE = re.compile(r'^\s*(?:\*/)?//\?\} (?:else|elif).*\{\s*$')
CLOSE = re.compile(r'^\s*(?:\*/)?//\?\}\s*$')
INLINE_OPEN = re.compile(r'/\*\? (?:if|elif) [^*]*\{\*/')
INLINE_CLOSE = re.compile(r'/\*\?\}\*/')
STAR_CLOSE_MIDDLE = re.compile(r'\*///\?\} (?:else|elif).*\{\s*$')
STAR_CLOSE = re.compile(r'\*///\?\}\s*$')


def check(path):
    problems = []
    depth = 0
    inline = 0
    for number, line in enumerate(open(path, encoding='utf-8'), 1):
        if MIDDLE.match(line) or STAR_CLOSE_MIDDLE.search(line):
            if depth < 1:
                problems.append('%s:%d: else/elif outside a conditional' % (path, number))
        elif CLOSE.match(line) or STAR_CLOSE.search(line):
            depth -= 1
            if depth < 0:
                problems.append('%s:%d: closing marker without an opening one' % (path, number))
                depth = 0
        elif OPEN.match(line):
            depth += 1
        inline += len(INLINE_OPEN.findall(line)) - len(INLINE_CLOSE.findall(line))
    if depth != 0:
        problems.append('%s: %d unclosed //? if block(s)' % (path, depth))
    if inline != 0:
        problems.append('%s: unbalanced inline /*? ... */ markers (%+d)' % (path, inline))
    return problems


if __name__ == '__main__':
    failures = []
    for file in sys.argv[1:]:
        failures += check(file)
    print('\n'.join(failures) if failures else 'balanced: %d file(s)' % len(sys.argv[1:]))
    sys.exit(1 if failures else 0)
```

- **Translations.** Every task that adds `wp.` keys adds them to all six files in `common/src/main/resources/lang/`, as a new group at the end of the object: a blank line, then the entries, with a comma added to the entry before them. `TranslationFilesTest` (Task 1) fails when a `wp.` key is missing from a locale, so run it with every task. Tasks that retire keys use this script, saved as `remove_keys.py` in your scratchpad directory and run from the repository root (`python3 SCRATCH/remove_keys.py waypoint.help. waypoint.menu.`); an argument ending in `.` removes every key with that prefix, any other argument one exact key:

```python
#!/usr/bin/env python3
"""Removes translation keys from the six locale files, keeping their layout. Run from the repository root."""
import json
import re
import sys

LOCALES = ['en_us', 'zh_cn', 'es_es', 'he_il', 'zh_hk', 'zh_tw']
patterns = sys.argv[1:]


def matches(key):
    return any(key.startswith(p) if p.endswith('.') else key == p for p in patterns)


for locale in LOCALES:
    path = 'common/src/main/resources/lang/%s.json' % locale
    kept = []
    for line in open(path, encoding='utf-8').read().split('\n'):
        entry = re.match(r'\s*"([^"]+)"\s*:', line)
        if entry and matches(entry.group(1)):
            continue
        if line.strip() == '' and kept and kept[-1].strip() in ('', '{'):
            continue
        kept.append(line)
    end = max(index for index, line in enumerate(kept) if line.strip() == '}')
    while kept[end - 1].strip() == '':
        del kept[end - 1]
        end -= 1
    kept[end - 1] = kept[end - 1].rstrip().rstrip(',')
    text = '\n'.join(kept)
    json.loads(text)
    open(path, 'w', encoding='utf-8').write(text)
    print(locale, 'ok')
```
- **English in tests.** Builder tests assert the rendered English text, through `ChatAssert.render` (Task 2), which renders with the real `en_us.json`.
- **Commit.** Every task ends with `/usr/bin/git diff --check`, then `/usr/bin/git add` of the files it lists and a commit with the message it gives.

## File structure

`common/...` is `common/src/main/java/_959/server_waypoint/`; tests mirror it under `common/src/test/java/`.

| File | Responsibility |
| --- | --- |
| `common/.../text/chat/Click.java` (new) | A run or suggest action with the 256-character guard |
| `common/.../text/chat/Tooltip.java` (new) | White title, gray lines with split-out separators, aqua hints |
| `common/.../text/chat/Chat.java` (new) | Text pieces: links, controls, buttons, separators, joining, `✔`/`✘` lines, counts |
| `common/.../text/chat/ChatLines.java` (new) | Collects a message line by line, without a trailing newline |
| `common/.../text/chat/Viewer.java` (new) | Permissions, has-the-mod, plain text, dimension, position and yaw of whoever reads a message |
| `common/.../text/chat/ChatFont.java` (new) | Advances of the vanilla bitmap font, the glyph set, and text width |
| `common/.../text/chat/DimensionKind.java`, `DimensionStyle.java` (new) | Dimension names, colours, converted coordinates, order and tooltips |
| `common/.../text/chat/ListView.java`, `ListQuery.java`, `ListTarget.java` (new) | The list options and the commands that carry them |
| `common/.../text/chat/Paging.java`, `ListControls.java` (new) | Pages by line budget or size; pager, `… N more`, view, sort and search rows |
| `common/.../text/feedback/WaypointRefs.java` (new) | `[AB] Name`, waypoint and teleport tooltips, distances, list labels |
| `common/.../text/feedback/PlacedWaypoint.java` (new) | A waypoint with the dimension and list it belongs to |
| `common/.../text/feedback/MenuScreen.java`, `HelpScreen.java`, `HelpTopics.java` (new) | The menu, the help index and help topics |
| `common/.../text/feedback/ListScreen.java`, `ListActions.java` (new) | Tree, Lists, Flat, single list, search and empty states; the actions under a list |
| `common/.../text/feedback/DimensionScreens.java` (new) | The dimension list, all dimensions and their search |
| `common/.../text/feedback/PickerScreens.java` (new) | Colour, facing and add pickers |
| `common/.../text/feedback/DetailsScreen.java` (new) | Waypoint details, list details and edit results |
| `common/.../text/feedback/Results.java`, `Broadcasts.java`, `Errors.java`, `SharingPrompt.java` (new) | `✔` results, broadcasts to other players, `✘` errors, the Xaero sharing prompt |
| `common/.../text/feedback/NavigationScreens.java` (new) | The navigation panel, its results and the text display panel |
| `common/.../text/feedback/UploadScreens.java` (new) | The upload panel and upload results |
| `common/.../text/feedback/RemoteRefs.java`, `RemoteScreens.java` (new) | Literal remote labels; remote browsing, details and teleport feedback |
| `common/.../command/ListCommandOptions.java` (rewrite) | The list option grammar, with `view lists` and a fixed option order |
| `common/.../command/CoreWaypointCommand.java` (modify) | Builds the `Viewer`, adds the new commands, calls the builders |
| `common/.../command/RemoteWaypointCommand.java` (rewrite) | The new remote grammar and the remote builders |
| `common/.../crossserver/catalog/RemoteCatalogQuery.java` (rewrite) | Structured, unpaged remote data for the builders |
| `common/.../core/waypoint/WaypointQueryEngine.java` (modify) | A query over every list of every dimension, shared with remote search |
| `common/.../core/waypoint/WaypointListDisplayModel.java` (modify) | Flat view with Default sort keeps the saved order |
| `common/.../core/network/PlatformMessageSender.java`, `ChatMessageHandler.java`, `upload/UploadCoordinator.java` (modify) | Plain-text receivers, per-recipient broadcasts, the sharing prompt and upload results |
| `common/.../crossserver/handoff/DestinationHandoffService.java`, `BackendRuntime.java` (modify) | The `✔ Arrived at …` line at the destination |
| `common/.../util/StringCommandBuilder.java` (modify) | Edit, download and upload commands; the old list commands go |
| `common/.../core/waypoint/WaypointModificationType.java`, `navigation/NavigationDisplayText.java` (modify) | Drop `toTranslatable`; colour the navigation dimension through `DimensionStyle` |
| `common/.../text/TextButtonBuilder.java`, `WaypointTextHelper.java`, `WaypointDetailsTextBuilder.java`, `command/WaypointCommandHelp.java`, `command/WaypointListPage.java` (delete) | Replaced by the kit and the builders |
| `mods/.../network/ModMessageSender.java`, `server/command/WaypointCommand.java`, `server/handoff/ModCrossServerRuntime.java` (modify) | Receiver detection, dimension types, the arrival line |
| `mods/.../client/gui/...` (five files), `client/integrations/XaerosMinimapWaypointHelper.java` (modify) | Dimension colours from `DimensionStyle`; the client's own `[AB] Name` |
| `paper/.../network/PaperMessageSender.java`, `server/command/WaypointCommand.java`, `handoff/PaperCrossServerRuntime.java` (modify) | The same for Paper |
| `common/src/main/resources/lang/*.json` (modify) | Six locales |
| `common/src/test/.../translation/TranslationFilesTest.java` (new) | Patterns parse, placeholders match, every `wp.` key in every locale |
| `common/src/test/.../text/chat/ChatAssert.java`, `text/feedback/Fixtures.java`, `command/CommandHarness.java` (new) | Rendering, width and click helpers; sample data; a command dispatcher with recording senders |
| `common/src/test/.../command/CommandFeedbackTest.java`, `text/feedback/ScreenAuditTest.java` (new) | Commands end to end; every screen fits chat and stays plain for the console |
| `README.md`, `README_zh.md`, `docs/tips/adventure-text.md`, `docs/features/cross-server/*`, `docs/features/command-feedback/*` (modify, new) | Documentation and the validation record |

---

### Task 1: Translation file checks

Every later task adds `wp.` keys to six files. A test that reads all six files catches printf placeholders, unescaped apostrophes, placeholder mismatches and keys missing from a locale. It also fixes the three broken entries the spec names (spec 18).

**Files:**
- Create: `common/src/test/java/_959/server_waypoint/translation/TranslationFilesTest.java`
- Modify: `common/src/main/resources/lang/en_us.json`, `common/src/main/resources/lang/zh_cn.json`

**Interfaces:**
- Produces: `TranslationFilesTest.LOCALES` (the six locale codes) and `static Map<String, String> TranslationFilesTest.load(String locale)`, which later tests reuse.

- [ ] **Step 1: Write the failing test**

Create `common/src/test/java/_959/server_waypoint/translation/TranslationFilesTest.java`:

```java
package _959.server_waypoint.translation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TranslationFilesTest {
    public static final List<String> LOCALES = List.of("en_us", "zh_cn", "es_es", "he_il", "zh_hk", "zh_tw");
    private static final Pattern PRINTF = Pattern.compile("%(\\d+\\$)?[sdf]");
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    public static Map<String, String> load(String locale) {
        String path = "lang/" + locale + ".json";
        try (InputStream input = TranslationFilesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            JsonObject object = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return values;
        } catch (IOException exception) {
            throw new AssertionError(path, exception);
        }
    }

    @Test
    void everyLocaleOnlyHasKeysThatEnglishHas() {
        Map<String, String> english = load("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (String key : load(locale).keySet()) {
                if (!english.containsKey(key)) {
                    problems.add(locale + ": " + key);
                }
            }
        }
        assertTrue(problems.isEmpty(), "Keys missing from en_us:\n" + String.join("\n", problems));
    }

    @Test
    void valuesAreMessageFormatPatterns() {
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> entry : load(locale).entrySet()) {
                String where = locale + ": " + entry.getKey() + " = " + entry.getValue();
                if (PRINTF.matcher(entry.getValue()).find()) {
                    problems.add("printf placeholder in " + where);
                }
                if (LONE_APOSTROPHE.matcher(entry.getValue()).find()) {
                    problems.add("unescaped apostrophe in " + where);
                }
                try {
                    new MessageFormat(entry.getValue());
                } catch (IllegalArgumentException invalid) {
                    problems.add("invalid pattern in " + where);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void placeholdersMatchEnglish() {
        Map<String, String> english = load("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> entry : load(locale).entrySet()) {
                String expected = english.get(entry.getKey());
                if (expected != null && !placeholders(expected).equals(placeholders(entry.getValue()))) {
                    problems.add(locale + ": " + entry.getKey());
                }
            }
        }
        assertTrue(problems.isEmpty(), "Placeholders differ from en_us:\n" + String.join("\n", problems));
    }

    @Test
    void everyWpKeyIsInAllSixLocales() {
        Map<String, String> english = load("en_us");
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            Set<String> keys = load(locale).keySet();
            for (String key : english.keySet()) {
                if (key.startsWith("wp.") && !keys.contains(key)) {
                    problems.add(locale + ": " + key);
                }
            }
        }
        assertTrue(problems.isEmpty(), "wp. keys missing from a locale:\n" + String.join("\n", problems));
    }

    private static Set<String> placeholders(String value) {
        Set<String> indices = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            indices.add(matcher.group(1));
        }
        return indices;
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.translation.TranslationFilesTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL in `valuesAreMessageFormatPatterns`, listing `waypoint.upload.cooldown` in `en_us` and `zh_cn` (printf placeholder) and `waypoint.help.upload` and `waypoint.help.edit.example.icon` in `en_us` (unescaped apostrophe).

- [ ] **Step 3: Fix the three entries**

In `common/src/main/resources/lang/en_us.json`, replace:

```json
  "waypoint.upload.cooldown": "Wait %s seconds before starting another waypoint upload",
```

with:

```json
  "waypoint.upload.cooldown": "Wait {0} seconds before starting another waypoint upload",
```

replace:

```json
  "waypoint.help.upload": "Import Xaero's Minimap or VoxelMap waypoints from your client",
```

with:

```json
  "waypoint.help.upload": "Import Xaero''s Minimap or VoxelMap waypoints from your client",
```

and replace:

```json
  "waypoint.help.edit.example.icon": "Set Main Home's icon to a diamond",
```

with:

```json
  "waypoint.help.edit.example.icon": "Set Main Home''s icon to a diamond",
```

In `common/src/main/resources/lang/zh_cn.json`, replace:

```json
  "waypoint.upload.cooldown": "请等待 %s 秒后再开始新的路径点上传",
```

with:

```json
  "waypoint.upload.cooldown": "请等待 {0} 秒后再开始新的路径点上传",
```

- [ ] **Step 4: Run the test again**

Run the Step 2 command. Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/test/java/_959/server_waypoint/translation/TranslationFilesTest.java common/src/main/resources/lang/en_us.json common/src/main/resources/lang/zh_cn.json
```

```bash
/usr/bin/git commit -m "Check translation patterns and fix three broken entries"
```

---

### Task 2: Chat kit pieces

The pieces every message is made of (spec 2.3, 3, 17): clicks with the 256-character guard, tooltips, links, controls, `[buttons]`, separators, `✔` and `✘` lines, counts, the line collector and the `Viewer`. It also adds `ChatAssert`, the test helper that renders a message in English and finds the style, click and tooltip of a visible piece of text.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/chat/Click.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/Tooltip.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/Chat.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/ChatLines.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/Viewer.java`
- Create: `common/src/test/java/_959/server_waypoint/text/chat/ChatAssert.java`
- Test: `common/src/test/java/_959/server_waypoint/text/chat/ChatTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Produces:
  - `record Click(ClickEvent.Action action, String command)`: `static Click run(String)`, `static Click suggest(String)`, `boolean fits()`, `@Nullable ClickEvent event()` (null over 256 characters), `MAX_COMMAND_LENGTH = 256`.
  - `final class Tooltip`: `static Tooltip of(Component title)`, `static Tooltip of(String key, ComponentLike... arguments)`, `Tooltip line(Component... pieces)`, `Tooltip line(String key, ComponentLike... arguments)`, `Tooltip hint(String key)`, `Tooltip withoutHints()`, `List<Component> textLines()` (title and lines, no hints), `Component build()`.
  - `final class Chat`: constants `SEPARATOR`, `CHECK = "✔"`, `CROSS = "✘"`, `ELLIPSIS = "…"`, `PICKER = "⏷"`, `DOT = "●"`, `CRUMB` (dark gray ` › `); `concat(@Nullable Component...)`, `concat(List)`, `join(@Nullable Component...)`, `join(List)`, `spaced(List)`, `colored(Component, TextColor)`, `link(Viewer, Component label, TextColor, Click, @Nullable Tooltip)`, `@Nullable control(...)` (same parameters), `@Nullable button(...)` (same parameters), `@Nullable disabledButton(Viewer, Component label, Tooltip)`, `hover(Viewer, Component, @Nullable Tooltip)`, `ok(Component)`, `ok(Component, List<? extends @Nullable Component> actions)`, `error(Component)`, `error(Component, @Nullable Component recovery)`, `count(String key, int count)`.
  - `final class ChatLines`: `add(@Nullable Component line)`, `line(@Nullable Component... pieces)`, `addAll(ChatLines)`, `size()`, `isEmpty()`, `build()`.
  - `record Viewer(Set<Permission> permissions, boolean hasMod, boolean plainText, @Nullable String dimension, @Nullable WaypointPos position, float yaw)` with `enum Permission { ADD, EDIT, REMOVE, TP, NAVIGATE, RELOAD, UPLOAD, UPLOAD_DELETE, REMOTE_LIST, REMOTE_TP }`, `static Set<Permission> everything()`, `boolean can(Permission)`, `boolean isIn(String dimension)`.
  - Test helper `ChatAssert`: `render(Component)`, `lines(Component)`, `runs(Component)`, `find(Component, String text)`, `colorOf(Component, String)`, `clickOf(Component, String)`, `tooltipOf(Component, String)`, `runCommands(Component)`, `suggestions(Component)`.
- Keys: `wp.count.{waypoint,list,dimension,server,match}.{one,other}`, `wp.hint.{details,open,reverse,page,type_name,type_coordinates,type_search,confirm,fill,run}`.

- [ ] **Step 1: Add the keys in all six locales**

Add this group at the end of `common/src/main/resources/lang/en_us.json`, after the last entry and a blank line (add a comma to the entry before it):

```json
  "wp.count.waypoint.one": "{0} waypoint",
  "wp.count.waypoint.other": "{0} waypoints",
  "wp.count.list.one": "{0} list",
  "wp.count.list.other": "{0} lists",
  "wp.count.dimension.one": "{0} dimension",
  "wp.count.dimension.other": "{0} dimensions",
  "wp.count.server.one": "{0} server",
  "wp.count.server.other": "{0} servers",
  "wp.count.match.one": "{0} match",
  "wp.count.match.other": "{0} matches",
  "wp.hint.details": "Click for details",
  "wp.hint.open": "Click to open",
  "wp.hint.reverse": "Click to reverse",
  "wp.hint.page": "Click to type a page",
  "wp.hint.type_name": "Type its name, then press Enter",
  "wp.hint.type_coordinates": "Type its coordinates and name, then press Enter",
  "wp.hint.type_search": "Type what to look for, then press Enter",
  "wp.hint.confirm": "Press Enter to confirm",
  "wp.hint.fill": "Click to fill it in",
  "wp.hint.run": "Click to run it"
```

`zh_cn.json`:

```json
  "wp.count.waypoint.one": "{0} 个路径点",
  "wp.count.waypoint.other": "{0} 个路径点",
  "wp.count.list.one": "{0} 个列表",
  "wp.count.list.other": "{0} 个列表",
  "wp.count.dimension.one": "{0} 个维度",
  "wp.count.dimension.other": "{0} 个维度",
  "wp.count.server.one": "{0} 个服务器",
  "wp.count.server.other": "{0} 个服务器",
  "wp.count.match.one": "{0} 个结果",
  "wp.count.match.other": "{0} 个结果",
  "wp.hint.details": "点击查看详情",
  "wp.hint.open": "点击打开",
  "wp.hint.reverse": "点击反转顺序",
  "wp.hint.page": "点击输入页码",
  "wp.hint.type_name": "输入名称，然后按回车",
  "wp.hint.type_coordinates": "输入坐标和名称，然后按回车",
  "wp.hint.type_search": "输入要查找的内容，然后按回车",
  "wp.hint.confirm": "按回车确认",
  "wp.hint.fill": "点击填入命令",
  "wp.hint.run": "点击运行"
```

`zh_hk.json` and `zh_tw.json` (the same text in both):

```json
  "wp.count.waypoint.one": "{0} 個路徑點",
  "wp.count.waypoint.other": "{0} 個路徑點",
  "wp.count.list.one": "{0} 個列表",
  "wp.count.list.other": "{0} 個列表",
  "wp.count.dimension.one": "{0} 個維度",
  "wp.count.dimension.other": "{0} 個維度",
  "wp.count.server.one": "{0} 個伺服器",
  "wp.count.server.other": "{0} 個伺服器",
  "wp.count.match.one": "{0} 個結果",
  "wp.count.match.other": "{0} 個結果",
  "wp.hint.details": "點擊查看詳情",
  "wp.hint.open": "點擊開啟",
  "wp.hint.reverse": "點擊反轉順序",
  "wp.hint.page": "點擊輸入頁碼",
  "wp.hint.type_name": "輸入名稱，然後按 Enter",
  "wp.hint.type_coordinates": "輸入座標和名稱，然後按 Enter",
  "wp.hint.type_search": "輸入要搜尋的內容，然後按 Enter",
  "wp.hint.confirm": "按 Enter 確認",
  "wp.hint.fill": "點擊填入指令",
  "wp.hint.run": "點擊執行"
```

`es_es.json`:

```json
  "wp.count.waypoint.one": "{0} punto de ruta",
  "wp.count.waypoint.other": "{0} puntos de ruta",
  "wp.count.list.one": "{0} lista",
  "wp.count.list.other": "{0} listas",
  "wp.count.dimension.one": "{0} dimensión",
  "wp.count.dimension.other": "{0} dimensiones",
  "wp.count.server.one": "{0} servidor",
  "wp.count.server.other": "{0} servidores",
  "wp.count.match.one": "{0} coincidencia",
  "wp.count.match.other": "{0} coincidencias",
  "wp.hint.details": "Haz clic para ver los detalles",
  "wp.hint.open": "Haz clic para abrir",
  "wp.hint.reverse": "Haz clic para invertir el orden",
  "wp.hint.page": "Haz clic para escribir una página",
  "wp.hint.type_name": "Escribe su nombre y pulsa Intro",
  "wp.hint.type_coordinates": "Escribe sus coordenadas y su nombre y pulsa Intro",
  "wp.hint.type_search": "Escribe lo que buscas y pulsa Intro",
  "wp.hint.confirm": "Pulsa Intro para confirmar",
  "wp.hint.fill": "Haz clic para escribirlo",
  "wp.hint.run": "Haz clic para ejecutarlo"
```

`he_il.json`:

```json
  "wp.count.waypoint.one": "{0} נקודת ציון",
  "wp.count.waypoint.other": "{0} נקודות ציון",
  "wp.count.list.one": "{0} רשימה",
  "wp.count.list.other": "{0} רשימות",
  "wp.count.dimension.one": "{0} ממד",
  "wp.count.dimension.other": "{0} ממדים",
  "wp.count.server.one": "{0} שרת",
  "wp.count.server.other": "{0} שרתים",
  "wp.count.match.one": "{0} תוצאה",
  "wp.count.match.other": "{0} תוצאות",
  "wp.hint.details": "לחצו לפרטים",
  "wp.hint.open": "לחצו לפתיחה",
  "wp.hint.reverse": "לחצו להיפוך הסדר",
  "wp.hint.page": "לחצו להקלדת מספר עמוד",
  "wp.hint.type_name": "הקלידו שם ולחצו Enter",
  "wp.hint.type_coordinates": "הקלידו קואורדינטות ושם ולחצו Enter",
  "wp.hint.type_search": "הקלידו מה לחפש ולחצו Enter",
  "wp.hint.confirm": "לחצו Enter לאישור",
  "wp.hint.fill": "לחצו למילוי",
  "wp.hint.run": "לחצו להרצה"
```

- [ ] **Step 2: Write `ChatAssert`**

Create `common/src/test/java/_959/server_waypoint/text/chat/ChatAssert.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.translation.AdventureTranslator;
import _959.server_waypoint.translation.LanguageFilesManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Renders messages in English and finds the style of the pieces a player sees. */
public final class ChatAssert {
    private static boolean installed;

    private ChatAssert() {
    }

    public record Run(String text, Style style) {
    }

    private static synchronized void install() {
        if (!installed) {
            new LanguageFilesManager(Path.of("build", "chat-assert"));
            GlobalTranslator.translator().addSource(new AdventureTranslator());
            installed = true;
        }
    }

    /** The visible pieces in order, with the style each one ends up with. */
    public static List<Run> runs(Component component) {
        install();
        List<Run> runs = new ArrayList<>();
        collect(GlobalTranslator.render(component, Locale.US), Style.empty(), runs);
        return runs;
    }

    private static void collect(Component component, Style inherited, List<Run> runs) {
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            runs.add(new Run(text.content(), style));
        } else if (component instanceof TranslatableComponent translatable) {
            runs.add(new Run("<missing " + translatable.key() + ">", style));
        }
        for (Component child : component.children()) {
            collect(child, style, runs);
        }
    }

    public static String render(Component component) {
        return runs(component).stream().map(Run::text).collect(Collectors.joining());
    }

    public static List<String> lines(Component component) {
        return Arrays.asList(render(component).split("\n", -1));
    }

    /**
     * The first place where this text starts at the beginning of a piece; it may run on into the
     * following pieces, since a translation with arguments renders as several pieces. The result
     * carries the style of the piece it starts in.
     */
    public static Run find(Component component, String text) {
        List<Run> runs = runs(component);
        String all = runs.stream().map(Run::text).collect(Collectors.joining());
        int offset = 0;
        for (Run run : runs) {
            if (all.startsWith(text, offset)) {
                return new Run(text, run.style());
            }
            offset += run.text().length();
        }
        throw new AssertionError("No piece starts with \"" + text + "\" in "
                + runs.stream().map(run -> "\"" + run.text() + "\"").collect(Collectors.joining(", ")));
    }

    public static @Nullable TextColor colorOf(Component component, String text) {
        return find(component, text).style().color();
    }

    public static @Nullable String clickOf(Component component, String text) {
        ClickEvent click = find(component, text).style().clickEvent();
        return click == null ? null : click.value();
    }

    public static @Nullable String tooltipOf(Component component, String text) {
        HoverEvent<?> hover = find(component, text).style().hoverEvent();
        return hover == null ? null : render((Component) hover.value());
    }

    public static List<String> runCommands(Component component) {
        return clicks(component, ClickEvent.Action.RUN_COMMAND);
    }

    public static List<String> suggestions(Component component) {
        return clicks(component, ClickEvent.Action.SUGGEST_COMMAND);
    }

    /** Clicks in order; the message is rendered first so clicks inside translation arguments count too. */
    private static List<String> clicks(Component component, ClickEvent.Action action) {
        install();
        List<String> commands = new ArrayList<>();
        collectClicks(GlobalTranslator.render(component, Locale.US), action, commands);
        return commands;
    }

    private static void collectClicks(Component component, ClickEvent.Action action, List<String> commands) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == action) {
            commands.add(click.value());
        }
        for (Component child : component.children()) {
            collectClicks(child, action, commands);
        }
    }
}
```

- [ ] **Step 3: Write the failing tests**

Create `common/src/test/java/_959/server_waypoint/text/chat/ChatTest.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTest {
    static final Viewer PLAYER = new Viewer(Viewer.everything(), true, false,
            "minecraft:overworld", new WaypointPos(100, 64, -20), 37F);
    static final Viewer CONSOLE = new Viewer(Viewer.everything(), false, true,
            "minecraft:overworld", new WaypointPos(0, 64, 0), 0F);

    @Test
    void separatorsAreDarkGrayPiecesOfTheirOwnAndNullPiecesAreLeftOut() {
        Component joined = Chat.join(text("Lists"), null, text("Tree"));

        assertEquals("Lists · Tree", render(joined));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(joined, " · "));
    }

    @Test
    void linksCarryTheirClickAndTooltipForPlayers() {
        Component link = Chat.link(PLAYER, text("Help"), NamedTextColor.GRAY, Click.run("/wp help"),
                Tooltip.of(text("Open help")).hint("wp.hint.open"));

        assertEquals("/wp help", clickOf(link, "Help"));
        assertEquals("Open help\nClick to open", tooltipOf(link, "Help"));
        assertEquals(NamedTextColor.GRAY, colorOf(link, "Help"));
    }

    @Test
    void plainTextViewersKeepLinkLabelsButNotControlsOrButtons() {
        Component line = Chat.join(
                Chat.link(CONSOLE, text("Farms"), NamedTextColor.WHITE, Click.run("/wp list"), null),
                Chat.control(CONSOLE, text("All"), NamedTextColor.AQUA, Click.run("/wp list all"), null),
                Chat.button(CONSOLE, text("Back"), NamedTextColor.GRAY, Click.run("/wp"), null));

        assertEquals("Farms", render(line));
        assertTrue(runCommands(line).isEmpty());
    }

    @Test
    void commandsOver256CharactersLoseTheirClickAndTheirClickHint() {
        String command = "/wp list " + "x".repeat(300);
        Component link = Chat.link(PLAYER, text("Farms"), NamedTextColor.WHITE, Click.run(command),
                Tooltip.of(text("Farms")).hint("wp.hint.open"));

        assertNull(clickOf(link, "Farms"));
        assertEquals("Farms", tooltipOf(link, "Farms"));
        assertFalse(Click.run(command).fits());
        assertNull(Click.suggest(command).event());
    }

    @Test
    void buttonsAreBracketedAndUndecorated() {
        Component button = Chat.button(PLAYER, text("Back"), NamedTextColor.GRAY, Click.run("/wp"), null);

        assertEquals("[Back]", render(button));
        assertEquals("/wp", clickOf(button, "Back"));
    }

    @Test
    void resultsAndErrorsStartWithTheirGlyphInTheirColour() {
        Component ok = Chat.ok(text("Saved"), List.of(text("Undo")));
        Component error = Chat.error(text("Missing."), text("Browse"));

        assertEquals("✔ Saved   Undo", render(ok));
        assertEquals(NamedTextColor.GREEN, colorOf(ok, "✔ "));
        assertEquals("✘ Missing. Browse", render(error));
        assertEquals(NamedTextColor.RED, colorOf(error, "Missing."));
        assertEquals("✔ Saved", render(Chat.ok(text("Saved"), java.util.Arrays.<Component>asList(null, null))));
    }

    @Test
    void countsUseTheSingularOnlyForOne() {
        assertEquals("1 waypoint", render(Chat.count("wp.count.waypoint", 1)));
        assertEquals("0 waypoints", render(Chat.count("wp.count.waypoint", 0)));
        assertEquals("12 waypoints", render(Chat.count("wp.count.waypoint", 12)));
    }

    @Test
    void linesAreJoinedWithoutATrailingNewline() {
        ChatLines message = new ChatLines().add(text("one")).add(null).line(text("two "), null, text("three"));

        assertEquals(List.of("one", "two three"), lines(message.build()));
        assertEquals(2, message.size());
    }

    @Test
    void tooltipsHaveAWhiteTitleGrayLinesAndAquaHints() {
        Component tooltip = Tooltip.of(text("Farms")).line(text("7 waypoints"), text("Overworld"))
                .hint("wp.hint.open").build();

        assertEquals("Farms\n7 waypoints · Overworld\nClick to open", render(tooltip));
        assertEquals(NamedTextColor.WHITE, colorOf(tooltip, "Farms"));
        assertEquals(NamedTextColor.GRAY, colorOf(tooltip, "7 waypoints"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(tooltip, " · "));
        assertEquals(NamedTextColor.AQUA, colorOf(tooltip, "Click to open"));
    }

    @Test
    void viewersKnowTheirPermissionsAndDimension() {
        Viewer member = new Viewer(Set.of(Viewer.Permission.NAVIGATE), false, false, "minecraft:the_nether", null, 0F);

        assertTrue(member.can(Viewer.Permission.NAVIGATE));
        assertFalse(member.can(Viewer.Permission.TP));
        assertTrue(member.isIn("minecraft:the_nether"));
        assertFalse(member.isIn("minecraft:overworld"));
    }
}
```

- [ ] **Step 4: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.ChatTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class Viewer`.

- [ ] **Step 5: Write `Click`**

Create `common/src/main/java/_959/server_waypoint/text/chat/Click.java`:

```java
package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * What clicking a piece of chat does. The client refuses commands longer than 256 characters, so
 * such a click is dropped and its text stays plain.
 */
public record Click(ClickEvent.Action action, String command) {
    public static final int MAX_COMMAND_LENGTH = 256;

    public Click {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(command, "command");
    }

    public static Click run(String command) {
        return new Click(ClickEvent.Action.RUN_COMMAND, command);
    }

    public static Click suggest(String command) {
        return new Click(ClickEvent.Action.SUGGEST_COMMAND, command);
    }

    public boolean fits() {
        return this.command.length() <= MAX_COMMAND_LENGTH;
    }

    public @Nullable ClickEvent event() {
        return this.fits() ? ClickEvent.clickEvent(this.action, this.command) : null;
    }
}
```

- [ ] **Step 6: Write `Tooltip`**

Create `common/src/main/java/_959/server_waypoint/text/chat/Tooltip.java`:

```java
package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A hover text. The first line is white and names the object or the action, later lines are gray,
 * and click hints and typing instructions are aqua and come last. The pieces of a line are joined
 * with dark gray separators and keep their own colours.
 */
public final class Tooltip {
    private final Component title;
    private final List<Component> lines;
    private final List<Component> hints;

    private Tooltip(Component title, List<Component> lines, List<Component> hints) {
        this.title = title;
        this.lines = List.copyOf(lines);
        this.hints = List.copyOf(hints);
    }

    public static Tooltip of(Component title) {
        return new Tooltip(Objects.requireNonNull(title, "title"), List.of(), List.of());
    }

    public static Tooltip of(String key, ComponentLike... arguments) {
        return of(Component.translatable(key, arguments));
    }

    public Tooltip line(Component... pieces) {
        List<Component> lines = new ArrayList<>(this.lines);
        lines.add(Chat.join(pieces));
        return new Tooltip(this.title, lines, this.hints);
    }

    public Tooltip line(String key, ComponentLike... arguments) {
        return this.line(Component.translatable(key, arguments));
    }

    public Tooltip hint(String key) {
        List<Component> hints = new ArrayList<>(this.hints);
        hints.add(Component.translatable(key));
        return new Tooltip(this.title, this.lines, hints);
    }

    /** The same tooltip without its hints, for a control whose click was dropped. */
    public Tooltip withoutHints() {
        return new Tooltip(this.title, this.lines, List.of());
    }

    /** The title and the lines, for plain-text viewers who read documentation as indented lines. */
    public List<Component> textLines() {
        List<Component> text = new ArrayList<>();
        text.add(this.title);
        text.addAll(this.lines);
        return text;
    }

    public Component build() {
        Component tooltip = Component.empty().append(Chat.colored(this.title, NamedTextColor.WHITE));
        for (Component line : this.lines) {
            tooltip = tooltip.appendNewline().append(Chat.colored(line, NamedTextColor.GRAY));
        }
        for (Component hint : this.hints) {
            tooltip = tooltip.appendNewline().append(Chat.colored(hint, NamedTextColor.AQUA));
        }
        return tooltip;
    }
}
```

- [ ] **Step 7: Write `Chat`**

Create `common/src/main/java/_959/server_waypoint/text/chat/Chat.java`:

```java
package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The pieces chat messages are made of. Every piece is its own component under a neutral parent,
 * so a click or tooltip never spills onto the text next to it. Pieces that only work by clicking
 * return null for plain-text viewers; joining and concatenating leave null pieces out.
 */
public final class Chat {
    public static final Component SEPARATOR = Component.text(" · ", NamedTextColor.DARK_GRAY);
    public static final Component CRUMB = Component.text(" › ", NamedTextColor.DARK_GRAY);
    public static final String CHECK = "✔";
    public static final String CROSS = "✘";
    public static final String ELLIPSIS = "…";
    public static final String PICKER = "⏷";
    public static final String DOT = "●";

    private Chat() {
    }

    public static Component concat(@Nullable Component... pieces) {
        return concat(Arrays.asList(pieces));
    }

    public static Component concat(List<? extends @Nullable Component> pieces) {
        Component result = Component.empty();
        for (Component piece : pieces) {
            if (piece != null) {
                result = result.append(piece);
            }
        }
        return result;
    }

    public static Component join(@Nullable Component... pieces) {
        return join(Arrays.asList(pieces));
    }

    /** The pieces with a dark gray separator between each two. */
    public static Component join(List<? extends @Nullable Component> pieces) {
        return joinWith(pieces, SEPARATOR);
    }

    /** The pieces one space apart, the way [buttons] stand. */
    public static Component spaced(List<? extends @Nullable Component> pieces) {
        return joinWith(pieces, Component.space());
    }

    private static Component joinWith(List<? extends @Nullable Component> pieces, Component between) {
        Component result = Component.empty();
        boolean first = true;
        for (Component piece : pieces) {
            if (piece == null) {
                continue;
            }
            if (!first) {
                result = result.append(between);
            }
            result = result.append(piece);
            first = false;
        }
        return result;
    }

    /** True when every piece was left out. */
    public static boolean isEmpty(List<? extends @Nullable Component> pieces) {
        return pieces.stream().allMatch(piece -> piece == null);
    }

    public static Component colored(Component content, TextColor color) {
        return Component.empty().color(color).append(content);
    }

    /**
     * A piece of text that names something; clicking it opens it. Plain-text viewers keep the
     * label without the click or the tooltip.
     */
    public static Component link(Viewer viewer, Component label, TextColor color, Click click,
                                 @Nullable Tooltip tooltip) {
        Component piece = colored(label, color);
        if (viewer.plainText()) {
            return piece;
        }
        ClickEvent event = click.event();
        if (event != null) {
            piece = piece.clickEvent(event);
        }
        Tooltip shown = tooltip == null ? null : event == null ? tooltip.withoutHints() : tooltip;
        return shown == null ? piece : piece.hoverEvent(HoverEvent.showText(shown.build()));
    }

    /** A link that only works by clicking, such as All or Search. Plain-text viewers don't get it. */
    public static @Nullable Component control(Viewer viewer, Component label, TextColor color, Click click,
                                              @Nullable Tooltip tooltip) {
        return viewer.plainText() ? null : link(viewer, label, color, click, tooltip);
    }

    /** A [button]. Plain-text viewers don't get it. */
    public static @Nullable Component button(Viewer viewer, Component label, TextColor color, Click click,
                                             @Nullable Tooltip tooltip) {
        return control(viewer, concat(Component.text("["), label, Component.text("]")), color, click, tooltip);
    }

    /** A dark gray [button] that does nothing, with a tooltip saying why. */
    public static @Nullable Component disabledButton(Viewer viewer, Component label, Tooltip tooltip) {
        if (viewer.plainText()) {
            return null;
        }
        return hover(viewer, colored(concat(Component.text("["), label, Component.text("]")),
                NamedTextColor.DARK_GRAY), tooltip);
    }

    /** Text with a tooltip and no click. Plain-text viewers get the text alone. */
    public static Component hover(Viewer viewer, Component content, @Nullable Tooltip tooltip) {
        if (viewer.plainText() || tooltip == null) {
            return content;
        }
        return Component.empty().append(content).hoverEvent(HoverEvent.showText(tooltip.build()));
    }

    /** A green result line starting with ✔. */
    public static Component ok(Component message) {
        return colored(concat(Component.text(CHECK + " "), message), NamedTextColor.GREEN);
    }

    /** A result line followed, three spaces later, by its actions. */
    public static Component ok(Component message, List<? extends @Nullable Component> actions) {
        if (isEmpty(actions)) {
            return ok(message);
        }
        return concat(ok(message), Component.text("   "), join(actions));
    }

    /** A red error line starting with ✘. */
    public static Component error(Component message) {
        return colored(concat(Component.text(CROSS + " "), message), NamedTextColor.RED);
    }

    /** An error line followed by the link that helps recover from it. */
    public static Component error(Component message, @Nullable Component recovery) {
        return recovery == null ? error(message) : concat(error(message), Component.space(), recovery);
    }

    /** "1 waypoint" or "12 waypoints": the key's .one or .other form with the number as {0}. */
    public static Component count(String key, int count) {
        return Component.translatable(key + (count == 1 ? ".one" : ".other"), Component.text(count));
    }
}
```

- [ ] **Step 8: Write `ChatLines` and `Viewer`**

Create `common/src/main/java/_959/server_waypoint/text/chat/ChatLines.java`:

```java
package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Collects a message line by line. The built message never ends with a newline. */
public final class ChatLines {
    private final List<Component> lines = new ArrayList<>();

    public ChatLines add(@Nullable Component line) {
        if (line != null) {
            this.lines.add(line);
        }
        return this;
    }

    /** Adds one line made of these pieces, leaving null pieces out. */
    public ChatLines line(@Nullable Component... pieces) {
        return this.add(Chat.concat(pieces));
    }

    public ChatLines addAll(ChatLines other) {
        this.lines.addAll(other.lines);
        return this;
    }

    public int size() {
        return this.lines.size();
    }

    public boolean isEmpty() {
        return this.lines.isEmpty();
    }

    public Component build() {
        Component message = Component.empty();
        for (int index = 0; index < this.lines.size(); index++) {
            if (index > 0) {
                message = message.appendNewline();
            }
            message = message.append(this.lines.get(index));
        }
        return message;
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/chat/Viewer.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Whoever reads a message, built once per command source: what they may do, whether their client
 * has the mod, whether they read plain text (the console, RCON and command blocks), and where
 * they stand.
 */
public record Viewer(
        Set<Permission> permissions,
        boolean hasMod,
        boolean plainText,
        @Nullable String dimension,
        @Nullable WaypointPos position,
        float yaw
) {
    public enum Permission {
        ADD,
        EDIT,
        REMOVE,
        TP,
        NAVIGATE,
        RELOAD,
        UPLOAD,
        UPLOAD_DELETE,
        REMOTE_LIST,
        REMOTE_TP
    }

    public Viewer {
        permissions = permissions.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public static Set<Permission> everything() {
        return EnumSet.allOf(Permission.class);
    }

    public boolean can(Permission permission) {
        return this.permissions.contains(permission);
    }

    /** Whether the viewer stands in this dimension. */
    public boolean isIn(String dimension) {
        return dimension.equals(this.dimension);
    }
}
```

- [ ] **Step 9: Run the tests**

Run the Step 4 command, then the translation test:

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.*' --tests '_959.server_waypoint.translation.TranslationFilesTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/chat common/src/test/java/_959/server_waypoint/text/chat common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add the chat kit pieces and an English rendering test helper"
```

---

### Task 3: Font advances and the layout check

Spec 1 and 21 measure every line against 320 px and every message against 20 lines. `ChatFont` holds the advances of the vanilla bitmap font, taken from the 26.3 client (the 1.20.2 font is identical): the 95 printable ASCII characters and the glyphs of spec 2.2. Help uses it to break long usages (Task 8); `ChatAssert.assertFitsChat` uses it to check every screen.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/chat/ChatFont.java`
- Modify: `common/src/test/java/_959/server_waypoint/text/chat/ChatAssert.java`
- Test: `common/src/test/java/_959/server_waypoint/text/chat/ChatFontTest.java`

**Interfaces:**
- Consumes: `ChatAssert.lines`, `ChatAssert.runs`, `ChatAssert.render` (Task 2).
- Produces: `ChatFont.CHAT_WIDTH = 320`, `ChatFont.CHAT_LINES = 20`, `static int advance(int codePoint)`, `static int width(String text)`, `static boolean isVanillaGlyph(int codePoint)`; test helper `ChatAssert.assertFitsChat(Component message)`.

- [ ] **Step 1: Write the failing tests**

Create `common/src/test/java/_959/server_waypoint/text/chat/ChatFontTest.java`:

```java
package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFontTest {
    @Test
    void asciiAdvancesIncludeTheOnePixelGap() {
        assertEquals(4, ChatFont.advance(' '));
        assertEquals(4, ChatFont.advance('['));
        assertEquals(4, ChatFont.advance(']'));
        assertEquals(6, ChatFont.advance('a'));
        assertEquals(6, ChatFont.advance('W'));
        assertEquals(2, ChatFont.advance('i'));
        assertEquals(3, ChatFont.advance('l'));
        assertEquals(4, ChatFont.advance('t'));
        assertEquals(7, ChatFont.advance('~'));
    }

    @Test
    void glyphsHaveTheAdvancesTheSpecLists() {
        Map<Character, Integer> glyphs = Map.ofEntries(
                Map.entry('✔', 7), Map.entry('✘', 7), Map.entry('●', 5), Map.entry('⏷', 6),
                Map.entry('↑', 6), Map.entry('↓', 6), Map.entry('‹', 4), Map.entry('›', 4),
                Map.entry('«', 7), Map.entry('»', 7), Map.entry('…', 8), Map.entry('·', 2),
                Map.entry('■', 6), Map.entry('█', 9), Map.entry('✎', 8), Map.entry('×', 6),
                Map.entry('−', 6), Map.entry('+', 6), Map.entry('°', 5));
        glyphs.forEach((glyph, advance) -> {
            assertEquals(advance, ChatFont.advance(glyph), String.valueOf(glyph));
            assertTrue(ChatFont.isVanillaGlyph(glyph), String.valueOf(glyph));
        });
    }

    @Test
    void glyphsThatFallBackToUnifontAreNotVanilla() {
        for (char glyph : "⋯▸▾✓✗▼".toCharArray()) {
            assertFalse(ChatFont.isVanillaGlyph(glyph), String.valueOf(glyph));
            assertEquals(9, ChatFont.advance(glyph));
        }
        assertFalse(ChatFont.isVanillaGlyph('\n'));
    }

    @Test
    void widthsAddUpTheAdvances() {
        assertEquals(185, ChatFont.width("Sort Default · Name · Distance · Color"));
        assertEquals(212, ChatFont.width("Server Waypoint   Open GUI · Help · Reload"));
    }

    @Test
    void theLayoutCheckRejectsWideTallBoldAndForeignText() {
        assertDoesNotThrow(() -> ChatAssert.assertFitsChat(text("Farms · 7")));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("x".repeat(60))));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("line\n".repeat(19) + "line")));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("Farms").decorate(TextDecoration.BOLD)));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(text("Farms ⋯")));
        assertThrows(AssertionError.class, () -> ChatAssert.assertFitsChat(Component.text("Farms").appendNewline()));
    }
}
```

- [ ] **Step 2: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.ChatFontTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable ChatFont`.

- [ ] **Step 3: Write `ChatFont`**

Create `common/src/main/java/_959/server_waypoint/text/chat/ChatFont.java`:

```java
package _959.server_waypoint.text.chat;

import java.util.Map;

/**
 * Advances of the vanilla bitmap font in GUI pixels, including the 1 px gap, as the 26.3 client
 * draws them (the 1.20.2 font is the same). Only ASCII and the glyphs the chat kit uses are known;
 * anything else falls back to Unifont and is assumed to be 9 px wide.
 */
public final class ChatFont {
    public static final int CHAT_WIDTH = 320;
    public static final int CHAT_LINES = 20;
    public static final int UNKNOWN_ADVANCE = 9;
    /** Advances of ' ' through '~'. */
    private static final String ASCII_ADVANCES =
            "42466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247";
    private static final Map<Integer, Integer> GLYPHS = Map.ofEntries(
            Map.entry((int) '✔', 7), Map.entry((int) '✘', 7), Map.entry((int) '●', 5),
            Map.entry((int) '⏷', 6), Map.entry((int) '↑', 6), Map.entry((int) '↓', 6),
            Map.entry((int) '‹', 4), Map.entry((int) '›', 4), Map.entry((int) '«', 7),
            Map.entry((int) '»', 7), Map.entry((int) '…', 8), Map.entry((int) '·', 2),
            Map.entry((int) '■', 6), Map.entry((int) '█', 9), Map.entry((int) '✎', 8),
            Map.entry((int) '×', 6), Map.entry((int) '−', 6), Map.entry((int) '°', 5));

    private ChatFont() {
    }

    public static boolean isVanillaGlyph(int codePoint) {
        return codePoint >= ' ' && codePoint <= '~' || GLYPHS.containsKey(codePoint);
    }

    public static int advance(int codePoint) {
        if (codePoint >= ' ' && codePoint <= '~') {
            return ASCII_ADVANCES.charAt(codePoint - ' ') - '0';
        }
        return GLYPHS.getOrDefault(codePoint, UNKNOWN_ADVANCE);
    }

    public static int width(String text) {
        return text.codePoints().map(ChatFont::advance).sum();
    }
}
```

- [ ] **Step 4: Add the layout check to `ChatAssert`**

In `common/src/test/java/_959/server_waypoint/text/chat/ChatAssert.java`, replace:

```java
import net.kyori.adventure.text.format.TextColor;
```

with:

```java
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
```

and replace:

```java
    public static List<String> runCommands(Component component) {
```

with:

```java
    /**
     * The chat limits of spec 1: no line over 320 px, at most 19 lines so the trailing blank line
     * still fits the 20-line window, no bold, only vanilla glyphs and no trailing newline.
     */
    public static void assertFitsChat(Component message) {
        List<String> problems = new ArrayList<>();
        String text = render(message);
        if (text.endsWith("\n")) {
            problems.add("ends with a newline");
        }
        List<String> lines = Arrays.asList(text.split("\n", -1));
        for (String line : lines) {
            int width = ChatFont.width(line);
            if (width > ChatFont.CHAT_WIDTH) {
                problems.add(width + " px: " + line);
            }
            line.codePoints().filter(codePoint -> !ChatFont.isVanillaGlyph(codePoint)).forEach(codePoint ->
                    problems.add("not in the vanilla font: " + new String(Character.toChars(codePoint)) + " in " + line));
        }
        if (lines.size() + 1 > ChatFont.CHAT_LINES) {
            problems.add((lines.size() + 1) + " lines with the trailing blank line");
        }
        if (runs(message).stream().anyMatch(run -> run.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)) {
            problems.add("bold text");
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(String.join("\n", problems) + "\n--- message ---\n" + text);
        }
    }

    public static List<String> runCommands(Component component) {
```

- [ ] **Step 5: Run the tests**

Run the Step 2 command. Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/chat/ChatFont.java common/src/test/java/_959/server_waypoint/text/chat/ChatAssert.java common/src/test/java/_959/server_waypoint/text/chat/ChatFontTest.java
```

```bash
/usr/bin/git commit -m "Measure chat text with the vanilla font advances"
```

---

### Task 4: Dimension names, colours and order

Spec 4: vanilla dimensions use translated names, others their ID path in title case; colours and converted coordinates follow the dimension type (or the ID when the type is unknown); the viewer's dimension comes first, then Overworld, Nether and End, then the rest A–Z; unloaded dimensions are gray. `DimensionStyle.colorOf` and `displayName` also serve places without a viewer: the GUI and navigation displays.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/chat/DimensionKind.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/DimensionStyle.java`
- Test: `common/src/test/java/_959/server_waypoint/text/chat/DimensionStyleTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: `Viewer`, `Tooltip`, `Chat` (Task 2).
- Produces:
  - `enum DimensionKind { OVERWORLD, NETHER, END, MODDED }` with `NamedTextColor color()` and `static DimensionKind of(String id)` (a dimension type ID, or a dimension ID when the type is unknown).
  - `final class DimensionStyle`: `static DimensionStyle local(Viewer, Map<String, String> loadedTypes)` (dimension ID to dimension type ID), `static DimensionStyle remote(Viewer)`, `Viewer viewer()`, `boolean isUnloaded(String id)`, `DimensionKind kind(String id)`, `TextColor color(String id)`, `Component name(String id)`, `Comparator<String> order()`, `@Nullable Component pairedCoordinates(String id, WaypointPos)`, `Tooltip tooltip(String id, @Nullable Component counts, @Nullable String hint)`, `@Nullable Component hereMark(String id)`; statics `NamedTextColor colorOf(String id)`, `Component displayName(String id)`, `String titleCase(String id)`, `String coordinates(WaypointPos)`, `Component hereLine()`, `Component counts(int waypoints, int lists)`.
- Keys: `wp.dimension.{overworld,nether,end,here,not_loaded,no_lists}`, `wp.dimension.type.{overworld,nether,end,modded}`, `wp.coordinates.{nether,overworld}`, `wp.in`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.dimension.overworld": "Overworld",
  "wp.dimension.nether": "Nether",
  "wp.dimension.end": "End",
  "wp.dimension.here": "You are here",
  "wp.dimension.not_loaded": "Not loaded",
  "wp.dimension.no_lists": "No lists yet",
  "wp.dimension.type.overworld": "Overworld type",
  "wp.dimension.type.nether": "Nether type",
  "wp.dimension.type.end": "End type",
  "wp.dimension.type.modded": "Modded type {0}",
  "wp.coordinates.nether": "Nether {0}",
  "wp.coordinates.overworld": "Overworld {0}",
  "wp.in": "{0} in {1}"
```

`zh_cn.json`:

```json
  "wp.dimension.overworld": "主世界",
  "wp.dimension.nether": "下界",
  "wp.dimension.end": "末地",
  "wp.dimension.here": "你在这里",
  "wp.dimension.not_loaded": "未加载",
  "wp.dimension.no_lists": "还没有列表",
  "wp.dimension.type.overworld": "主世界类型",
  "wp.dimension.type.nether": "下界类型",
  "wp.dimension.type.end": "末地类型",
  "wp.dimension.type.modded": "模组类型 {0}",
  "wp.coordinates.nether": "下界 {0}",
  "wp.coordinates.overworld": "主世界 {0}",
  "wp.in": "{1}中的{0}"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.dimension.overworld": "主世界",
  "wp.dimension.nether": "地獄",
  "wp.dimension.end": "終界",
  "wp.dimension.here": "你在這裡",
  "wp.dimension.not_loaded": "未載入",
  "wp.dimension.no_lists": "還沒有列表",
  "wp.dimension.type.overworld": "主世界類型",
  "wp.dimension.type.nether": "地獄類型",
  "wp.dimension.type.end": "終界類型",
  "wp.dimension.type.modded": "模組類型 {0}",
  "wp.coordinates.nether": "地獄 {0}",
  "wp.coordinates.overworld": "主世界 {0}",
  "wp.in": "{1}中的{0}"
```

`es_es.json`:

```json
  "wp.dimension.overworld": "Mundo principal",
  "wp.dimension.nether": "Nether",
  "wp.dimension.end": "End",
  "wp.dimension.here": "Estás aquí",
  "wp.dimension.not_loaded": "Sin cargar",
  "wp.dimension.no_lists": "Aún no hay listas",
  "wp.dimension.type.overworld": "Tipo mundo principal",
  "wp.dimension.type.nether": "Tipo Nether",
  "wp.dimension.type.end": "Tipo End",
  "wp.dimension.type.modded": "Tipo de mod {0}",
  "wp.coordinates.nether": "Nether {0}",
  "wp.coordinates.overworld": "Mundo principal {0}",
  "wp.in": "{0} en {1}"
```

`he_il.json`:

```json
  "wp.dimension.overworld": "העולם העליון",
  "wp.dimension.nether": "הנתר",
  "wp.dimension.end": "הקצה",
  "wp.dimension.here": "אתם כאן",
  "wp.dimension.not_loaded": "לא נטען",
  "wp.dimension.no_lists": "אין רשימות עדיין",
  "wp.dimension.type.overworld": "סוג העולם העליון",
  "wp.dimension.type.nether": "סוג הנתר",
  "wp.dimension.type.end": "סוג הקצה",
  "wp.dimension.type.modded": "סוג ממוד {0}",
  "wp.coordinates.nether": "הנתר {0}",
  "wp.coordinates.overworld": "העולם העליון {0}",
  "wp.in": "{0} ב-{1}"
```

- [ ] **Step 2: Write the failing tests**

Create `common/src/test/java/_959/server_waypoint/text/chat/DimensionStyleTest.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionStyleTest {
    private static final Map<String, String> LOADED = Map.of(
            "minecraft:overworld", "minecraft:overworld",
            "minecraft:the_nether", "minecraft:the_nether",
            "minecraft:the_end", "minecraft:the_end",
            "world_caves:caves", "minecraft:the_nether",
            "twilightforest:twilight_forest", "twilightforest:twilight_forest_type");

    private static DimensionStyle style(String here) {
        return DimensionStyle.local(new Viewer(Viewer.everything(), true, false, here,
                new WaypointPos(0, 64, 0), 0F), LOADED);
    }

    @Test
    void vanillaDimensionsAreTranslatedAndOthersUseTheirPathInTitleCase() {
        DimensionStyle style = style("minecraft:overworld");

        assertEquals("Overworld", render(style.name("minecraft:overworld")));
        assertEquals("Nether", render(style.name("minecraft:the_nether")));
        assertEquals("End", render(style.name("minecraft:the_end")));
        assertEquals("Twilight Forest", render(style.name("twilightforest:twilight_forest")));
        assertEquals("The Aether", DimensionStyle.titleCase("aether:the_aether"));
        assertEquals("A B C", DimensionStyle.titleCase("mod:a/b-c"));
        assertEquals("Plain", DimensionStyle.titleCase("plain"));
    }

    @Test
    void coloursFollowTheTypeAndFallBackToTheId() {
        DimensionStyle local = style("minecraft:overworld");
        DimensionStyle remote = DimensionStyle.remote(local.viewer());

        assertEquals(NamedTextColor.RED, local.color("world_caves:caves"));
        assertEquals(NamedTextColor.YELLOW, local.color("twilightforest:twilight_forest"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, local.color("minecraft:the_end"));
        assertEquals(NamedTextColor.RED, remote.color("minecraft:the_nether"));
        assertEquals(NamedTextColor.YELLOW, remote.color("world_caves:caves"));
        assertNull(remote.hereMark("minecraft:overworld"));
        assertFalse(remote.viewer().isIn("minecraft:overworld"));
        assertEquals(NamedTextColor.GREEN, DimensionStyle.colorOf("minecraft:overworld"));
        assertEquals(NamedTextColor.RED, colorOf(local.name("world_caves:caves"), "Caves"));
    }

    @Test
    void unloadedDimensionsAreGrayAndSaySoInTheirTooltip() {
        DimensionStyle style = style("minecraft:overworld");

        assertTrue(style.isUnloaded("ad_astra:mars"));
        assertFalse(DimensionStyle.remote(style.viewer()).isUnloaded("ad_astra:mars"));
        assertEquals(NamedTextColor.GRAY, style.color("ad_astra:mars"));
        assertEquals("Mars\nad_astra:mars\nNot loaded\nClick to open",
                render(style.tooltip("ad_astra:mars", null, "wp.hint.open").build()));
    }

    @Test
    void theViewersDimensionComesFirstThenVanillaThenTheRestByName() {
        List<String> ids = new ArrayList<>(List.of("zeta:zone", "minecraft:the_end", "aether:the_aether",
                "minecraft:overworld", "twilightforest:twilight_forest", "minecraft:the_nether"));

        ids.sort(style("twilightforest:twilight_forest").order());

        assertEquals(List.of("twilightforest:twilight_forest", "minecraft:overworld", "minecraft:the_nether",
                "minecraft:the_end", "aether:the_aether", "zeta:zone"), ids);
    }

    @Test
    void pairedCoordinatesFollowTheType() {
        DimensionStyle style = style("minecraft:overworld");

        assertEquals("Nether -1, 64, -2", render(style.pairedCoordinates("minecraft:overworld", new WaypointPos(-1, 64, -9))));
        assertEquals("Overworld 80, 64, -16", render(style.pairedCoordinates("world_caves:caves", new WaypointPos(10, 64, -2))));
        assertNull(style.pairedCoordinates("minecraft:the_end", new WaypointPos(0, 64, 0)));
        assertEquals(NamedTextColor.RED, colorOf(style.pairedCoordinates("minecraft:overworld", new WaypointPos(8, 1, 8)), "Nether "));
    }

    @Test
    void tooltipsNameTheDimensionItsIdTypeCountsAndWhereYouAre() {
        DimensionStyle style = style("minecraft:overworld");

        assertEquals("Overworld\nminecraft:overworld\nOverworld type\n12 waypoints in 3 lists\n● You are here\nClick to open",
                render(style.tooltip("minecraft:overworld", DimensionStyle.counts(12, 3), "wp.hint.open").build()));
        assertEquals("Twilight Forest\ntwilightforest:twilight_forest\nModded type twilightforest:twilight_forest_type\nNo lists yet",
                render(style.tooltip("twilightforest:twilight_forest", DimensionStyle.counts(0, 0), null).build()));
    }

    @Test
    void plainTextViewersReadTheIdAndGetNoHereMark() {
        Viewer console = new Viewer(Viewer.everything(), false, true, "minecraft:overworld", null, 0F);
        DimensionStyle style = DimensionStyle.local(console, LOADED);

        assertEquals("Overworld (minecraft:overworld)", render(style.name("minecraft:overworld")));
        assertNull(style.hereMark("minecraft:overworld"));
        assertEquals("●", render(style("minecraft:overworld").hereMark("minecraft:overworld")));
        assertNull(style("minecraft:overworld").hereMark("minecraft:the_end"));
        assertEquals(NamedTextColor.GOLD, colorOf(style("minecraft:overworld").hereMark("minecraft:overworld"), "●"));
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.DimensionStyleTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class DimensionStyle`.

- [ ] **Step 4: Write `DimensionKind`**

Create `common/src/main/java/_959/server_waypoint/text/chat/DimensionKind.java`:

```java
package _959.server_waypoint.text.chat;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

/** The family of a dimension type, which decides a dimension's colour and its converted coordinates. */
public enum DimensionKind {
    OVERWORLD("minecraft:overworld", NamedTextColor.GREEN),
    NETHER("minecraft:the_nether", NamedTextColor.RED),
    END("minecraft:the_end", NamedTextColor.LIGHT_PURPLE),
    MODDED(null, NamedTextColor.YELLOW);

    private final @Nullable String vanillaId;
    private final NamedTextColor color;

    DimensionKind(@Nullable String vanillaId, NamedTextColor color) {
        this.vanillaId = vanillaId;
        this.color = color;
    }

    public NamedTextColor color() {
        return this.color;
    }

    /** The kind of a dimension type ID, or of a dimension ID when its type is unknown. */
    public static DimensionKind of(String id) {
        for (DimensionKind kind : values()) {
            if (id.equals(kind.vanillaId)) {
                return kind;
            }
        }
        return MODDED;
    }
}
```

- [ ] **Step 5: Write `DimensionStyle`**

Create `common/src/main/java/_959/server_waypoint/text/chat/DimensionStyle.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.BlockPosConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * How dimensions look to one viewer: names, colours, converted coordinates, order and tooltips.
 * A local style knows which dimensions are loaded and their dimension type IDs; a remote style
 * only knows dimension IDs, so colours follow the ID.
 */
public final class DimensionStyle {
    private static final List<String> VANILLA = List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    private final Viewer viewer;
    private final @Nullable Map<String, String> loadedTypes;

    private DimensionStyle(Viewer viewer, @Nullable Map<String, String> loadedTypes) {
        this.viewer = viewer;
        this.loadedTypes = loadedTypes;
    }

    /** Dimensions of this server; loadedTypes maps each loaded dimension to its dimension type ID. */
    public static DimensionStyle local(Viewer viewer, Map<String, String> loadedTypes) {
        return new DimensionStyle(viewer, Map.copyOf(loadedTypes));
    }

    /**
     * Dimensions of another server's catalog. The viewer stands on this server, so in none of
     * them: no dimension comes first or carries the gold ●, and no distance is known.
     */
    public static DimensionStyle remote(Viewer viewer) {
        return new DimensionStyle(new Viewer(viewer.permissions(), viewer.hasMod(), viewer.plainText(), null, null,
                viewer.yaw()), null);
    }

    public Viewer viewer() {
        return this.viewer;
    }

    /** A local dimension with waypoint files but no loaded level or world. */
    public boolean isUnloaded(String id) {
        return this.loadedTypes != null && !this.loadedTypes.containsKey(id);
    }

    public DimensionKind kind(String id) {
        String type = this.loadedTypes == null ? null : this.loadedTypes.get(id);
        return DimensionKind.of(type == null ? id : type);
    }

    public TextColor color(String id) {
        return this.isUnloaded(id) ? NamedTextColor.GRAY : this.kind(id).color();
    }

    /** The colour of a dimension by its ID alone, for places without a viewer such as the GUI. */
    public static NamedTextColor colorOf(String id) {
        return DimensionKind.of(id).color();
    }

    /** Overworld, Nether and End, or the ID's path in title case, without colour. */
    public static Component displayName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> translatable("wp.dimension.overworld");
            case "minecraft:the_nether" -> translatable("wp.dimension.nether");
            case "minecraft:the_end" -> translatable("wp.dimension.end");
            default -> text(titleCase(id));
        };
    }

    /** twilightforest:twilight_forest becomes Twilight Forest: the path split on _, - and /. */
    public static String titleCase(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.substring(id.indexOf(':') + 1).split("[_\\-/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            int first = word.codePointAt(0);
            name.appendCodePoint(Character.toUpperCase(first)).append(word.substring(Character.charCount(first)));
        }
        return name.length() == 0 ? id : name.toString();
    }

    /** The name in its colour. Plain-text viewers read "Display (id)". */
    public Component name(String id) {
        Component name = displayName(id);
        if (this.viewer.plainText()) {
            name = Chat.concat(name, text(" (" + id + ")"));
        }
        return Chat.colored(name, this.color(id));
    }

    /** The viewer's dimension, then Overworld, Nether and End, then the rest A–Z by display name. */
    public Comparator<String> order() {
        return Comparator.comparingInt(this::rank)
                .thenComparing(DimensionStyle::titleCase, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.naturalOrder());
    }

    private int rank(String id) {
        if (this.viewer.isIn(id)) {
            return -1;
        }
        int vanilla = VANILLA.indexOf(id);
        return vanilla < 0 ? VANILLA.size() : vanilla;
    }

    /** Nether coordinates for Overworld types in red, Overworld ones for Nether types in green. */
    public @Nullable Component pairedCoordinates(String id, WaypointPos pos) {
        return switch (this.kind(id)) {
            case OVERWORLD -> translatable("wp.coordinates.nether", NamedTextColor.RED,
                    text(coordinates(BlockPosConverter.overWorldToNether(pos))));
            case NETHER -> translatable("wp.coordinates.overworld", NamedTextColor.GREEN,
                    text(coordinates(BlockPosConverter.netherToOverWorld(pos))));
            default -> null;
        };
    }

    public static String coordinates(WaypointPos pos) {
        return pos.x() + ", " + pos.y() + ", " + pos.z();
    }

    /** "12 waypoints in 3 lists", or "No lists yet". */
    public static Component counts(int waypoints, int lists) {
        if (lists == 0) {
            return translatable("wp.dimension.no_lists");
        }
        return translatable("wp.in", Chat.count("wp.count.waypoint", waypoints), Chat.count("wp.count.list", lists));
    }

    /** Name, ID, type or "Not loaded", counts, "● You are here", and a click hint. */
    public Tooltip tooltip(String id, @Nullable Component counts, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(Chat.colored(displayName(id), this.color(id))).line(text(id));
        if (this.loadedTypes != null) {
            String type = this.loadedTypes.get(id);
            tooltip = type == null ? tooltip.line("wp.dimension.not_loaded") : tooltip.line(typeLine(type));
        }
        if (counts != null) {
            tooltip = tooltip.line(counts);
        }
        if (this.viewer.isIn(id)) {
            tooltip = tooltip.line(hereLine());
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    private static Component typeLine(String type) {
        return switch (DimensionKind.of(type)) {
            case OVERWORLD -> translatable("wp.dimension.type.overworld");
            case NETHER -> translatable("wp.dimension.type.nether");
            case END -> translatable("wp.dimension.type.end");
            case MODDED -> translatable("wp.dimension.type.modded", text(type));
        };
    }

    /** The gold "● You are here". */
    public static Component hereLine() {
        return Chat.colored(Chat.concat(text(Chat.DOT + " "), translatable("wp.dimension.here")), NamedTextColor.GOLD);
    }

    /** The gold ● after the viewer's own dimension, or null for any other dimension and in plain text. */
    public @Nullable Component hereMark(String id) {
        if (!this.viewer.isIn(id) || this.viewer.plainText()) {
            return null;
        }
        return Chat.hover(this.viewer, text(Chat.DOT, NamedTextColor.GOLD), Tooltip.of(hereLine()));
    }
}
```

- [ ] **Step 6: Run the tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.*' --tests '_959.server_waypoint.translation.TranslationFilesTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/chat common/src/test/java/_959/server_waypoint/text/chat common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Name, colour and order dimensions by their type"
```

---

### Task 5: List options, paging and list controls

Spec 6.3 and 6.4 describe controls that local and remote lists share: the pager `« ‹ 2/5 › »`, the `… N more` line, the view row, the sort row and the search lines. They all build commands from the options of the screen they sit on, so this task adds the options (`ListQuery`, `ListView`), the command they render to (`ListTarget`), the paging helpers and the controls. Commands carry options in one fixed order, `search`, `sort`/`order`, `limit`, `view`, `page`, so the pager can suggest a command that ends in `page ` (Task 9 makes the grammar accept that order).

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/chat/ListView.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/ListQuery.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/ListTarget.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/Paging.java`
- Create: `common/src/main/java/_959/server_waypoint/text/chat/ListControls.java`
- Test: `common/src/test/java/_959/server_waypoint/text/chat/ListTargetTest.java`, `PagingTest.java`, `ListControlsTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: `Chat`, `Click`, `Tooltip`, `Viewer` (Task 2); `WaypointSorting.SortMode`; `StringCommandBuilder.escapeArgument`, `escapeListName`.
- Produces:
  - `enum ListView { DEFAULT, LISTS, TREE, FLAT }` with `String id()` (`""`, `lists`, `tree`, `flat`).
  - `record ListQuery(String search, SortMode sort, boolean descending, ListView view, int page, @Nullable Integer limit)`: `DEFAULT`, `searching()`, `pageLimit(int configured)`, `withSearch(String)`, `withSort(SortMode)`, `reversed()`, `withView(ListView)`, `withPage(int)`. Every `with…` except `withPage` returns to page 1.
  - `record ListTarget(String base)`: `dimension(String)`, `list(String dimension, String list)`, `allDimensions()`, `dimensions()`, `remote(@Nullable String server, @Nullable String dimension, @Nullable String list)`, `String command(ListQuery)`, `String pagePrompt(ListQuery)`, `String searchPrompt()`.
  - `final class Paging`: `static <T> List<List<T>> byLines(List<T>, ToIntFunction<T>, int budget)`, `static <T> List<List<T>> bySize(List<T>, int size)`, `static <T> int after(List<List<T>> pages, int page)`.
  - `final class ListControls`: `@Nullable pager(Viewer, ListTarget, ListQuery, int pages, Component detail)`, `@Nullable more(Viewer, String unit, int count, String command)` (unit is `list`, `waypoint`, `dimension` or `server`), `@Nullable sortRow(Viewer, ListTarget, ListQuery, List<SortMode> modes, String defaultTooltipKey, @Nullable Component distanceBlocked)`, `@Nullable viewRow(Viewer, ListTarget, ListQuery, ListView current, List<ListView> views, List<? extends @Nullable Component> extras)`, `@Nullable search(Viewer, ListTarget, Component tooltipTitle)`, `searchLine(Viewer, String search, int matches, String clearCommand)`, `noMatches(Viewer, String search, String clearCommand)`, `pageNotFound(Viewer, ListTarget, ListQuery, int pages)`, `pageDetail(int pageLimit, Component total)`, `controls(ChatLines lines, @Nullable Component pager, @Nullable Component... rows)` (adds the control rows that exist and closes the last one with the pager).
- Keys: `wp.page.*`, `wp.more.*`, `wp.plain.continue`, `wp.sort*`, `wp.view.*`, `wp.search*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.page.of": "Page {0} of {1}",
  "wp.page.size": "{0} per page",
  "wp.page.first": "First page",
  "wp.page.previous": "Previous page",
  "wp.page.next": "Next page",
  "wp.page.last": "Last page",
  "wp.page.go": "Go to page {0}",
  "wp.page.missing": "Page {0} does not exist; the last page is {1}.",
  "wp.more.list.one": "{0} more list",
  "wp.more.list.other": "{0} more lists",
  "wp.more.waypoint.one": "{0} more waypoint",
  "wp.more.waypoint.other": "{0} more waypoints",
  "wp.more.dimension.one": "{0} more dimension",
  "wp.more.dimension.other": "{0} more dimensions",
  "wp.more.server.one": "{0} more server",
  "wp.more.server.other": "{0} more servers",
  "wp.more.after": "{0} after this page",
  "wp.plain.continue": "{0}: {1}",
  "wp.sort": "Sort",
  "wp.sort.default": "Default",
  "wp.sort.name": "Name",
  "wp.sort.distance": "Distance",
  "wp.sort.color": "Color",
  "wp.sort.default.saved": "Saved order",
  "wp.sort.default.published": "Published order",
  "wp.sort.name.tooltip": "Sort by name",
  "wp.sort.distance.tooltip": "Sort by distance from you",
  "wp.sort.color.tooltip": "Sort by colour",
  "wp.sort.name.ascending": "A to Z",
  "wp.sort.name.descending": "Z to A",
  "wp.sort.distance.ascending": "Nearest first",
  "wp.sort.distance.descending": "Farthest first",
  "wp.sort.color.ascending": "By hue",
  "wp.sort.color.descending": "Hue reversed",
  "wp.sort.distance.unavailable": "Distance needs you in {0}",
  "wp.view.lists": "Lists",
  "wp.view.tree": "Tree",
  "wp.view.flat": "Flat",
  "wp.view.lists.tooltip": "One line per list",
  "wp.view.tree.tooltip": "Lists with their first waypoints",
  "wp.view.flat.tooltip": "One row per waypoint",
  "wp.search": "Search",
  "wp.search.results": "Search \"{0}\"",
  "wp.search.clear": "Clear",
  "wp.search.clear_search": "Clear search",
  "wp.search.clear.tooltip": "Show everything again",
  "wp.search.none": "Nothing matches \"{0}\"."
```

`zh_cn.json`:

```json
  "wp.page.of": "第 {0} 页，共 {1} 页",
  "wp.page.size": "每页 {0} 条",
  "wp.page.first": "第一页",
  "wp.page.previous": "上一页",
  "wp.page.next": "下一页",
  "wp.page.last": "最后一页",
  "wp.page.go": "前往第 {0} 页",
  "wp.page.missing": "第 {0} 页不存在；最后一页是第 {1} 页。",
  "wp.more.list.one": "还有 {0} 个列表",
  "wp.more.list.other": "还有 {0} 个列表",
  "wp.more.waypoint.one": "还有 {0} 个路径点",
  "wp.more.waypoint.other": "还有 {0} 个路径点",
  "wp.more.dimension.one": "还有 {0} 个维度",
  "wp.more.dimension.other": "还有 {0} 个维度",
  "wp.more.server.one": "还有 {0} 个服务器",
  "wp.more.server.other": "还有 {0} 个服务器",
  "wp.more.after": "此页之后还有{0}",
  "wp.plain.continue": "{0}：{1}",
  "wp.sort": "排序",
  "wp.sort.default": "默认",
  "wp.sort.name": "名称",
  "wp.sort.distance": "距离",
  "wp.sort.color": "颜色",
  "wp.sort.default.saved": "保存的顺序",
  "wp.sort.default.published": "发布的顺序",
  "wp.sort.name.tooltip": "按名称排序",
  "wp.sort.distance.tooltip": "按与你的距离排序",
  "wp.sort.color.tooltip": "按颜色排序",
  "wp.sort.name.ascending": "A 到 Z",
  "wp.sort.name.descending": "Z 到 A",
  "wp.sort.distance.ascending": "由近到远",
  "wp.sort.distance.descending": "由远到近",
  "wp.sort.color.ascending": "按色相",
  "wp.sort.color.descending": "色相反序",
  "wp.sort.distance.unavailable": "按距离排序需要你在{0}",
  "wp.view.lists": "列表",
  "wp.view.tree": "树状",
  "wp.view.flat": "平铺",
  "wp.view.lists.tooltip": "每个列表一行",
  "wp.view.tree.tooltip": "列表及其前几个路径点",
  "wp.view.flat.tooltip": "每个路径点一行",
  "wp.search": "搜索",
  "wp.search.results": "搜索“{0}”",
  "wp.search.clear": "清除",
  "wp.search.clear_search": "清除搜索",
  "wp.search.clear.tooltip": "重新显示全部",
  "wp.search.none": "没有与“{0}”匹配的内容。"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.page.of": "第 {0} 頁，共 {1} 頁",
  "wp.page.size": "每頁 {0} 項",
  "wp.page.first": "第一頁",
  "wp.page.previous": "上一頁",
  "wp.page.next": "下一頁",
  "wp.page.last": "最後一頁",
  "wp.page.go": "前往第 {0} 頁",
  "wp.page.missing": "第 {0} 頁不存在；最後一頁是第 {1} 頁。",
  "wp.more.list.one": "還有 {0} 個列表",
  "wp.more.list.other": "還有 {0} 個列表",
  "wp.more.waypoint.one": "還有 {0} 個路徑點",
  "wp.more.waypoint.other": "還有 {0} 個路徑點",
  "wp.more.dimension.one": "還有 {0} 個維度",
  "wp.more.dimension.other": "還有 {0} 個維度",
  "wp.more.server.one": "還有 {0} 個伺服器",
  "wp.more.server.other": "還有 {0} 個伺服器",
  "wp.more.after": "此頁之後還有{0}",
  "wp.plain.continue": "{0}：{1}",
  "wp.sort": "排序",
  "wp.sort.default": "預設",
  "wp.sort.name": "名稱",
  "wp.sort.distance": "距離",
  "wp.sort.color": "顏色",
  "wp.sort.default.saved": "儲存的順序",
  "wp.sort.default.published": "發佈的順序",
  "wp.sort.name.tooltip": "按名稱排序",
  "wp.sort.distance.tooltip": "按與你的距離排序",
  "wp.sort.color.tooltip": "按顏色排序",
  "wp.sort.name.ascending": "A 到 Z",
  "wp.sort.name.descending": "Z 到 A",
  "wp.sort.distance.ascending": "由近到遠",
  "wp.sort.distance.descending": "由遠到近",
  "wp.sort.color.ascending": "按色相",
  "wp.sort.color.descending": "色相反序",
  "wp.sort.distance.unavailable": "按距離排序需要你在{0}",
  "wp.view.lists": "列表",
  "wp.view.tree": "樹狀",
  "wp.view.flat": "平鋪",
  "wp.view.lists.tooltip": "每個列表一行",
  "wp.view.tree.tooltip": "列表及其前幾個路徑點",
  "wp.view.flat.tooltip": "每個路徑點一行",
  "wp.search": "搜尋",
  "wp.search.results": "搜尋「{0}」",
  "wp.search.clear": "清除",
  "wp.search.clear_search": "清除搜尋",
  "wp.search.clear.tooltip": "重新顯示全部",
  "wp.search.none": "沒有與「{0}」相符的內容。"
```

`es_es.json`:

```json
  "wp.page.of": "Página {0} de {1}",
  "wp.page.size": "{0} por página",
  "wp.page.first": "Primera página",
  "wp.page.previous": "Página anterior",
  "wp.page.next": "Página siguiente",
  "wp.page.last": "Última página",
  "wp.page.go": "Ir a la página {0}",
  "wp.page.missing": "La página {0} no existe; la última es la {1}.",
  "wp.more.list.one": "{0} lista más",
  "wp.more.list.other": "{0} listas más",
  "wp.more.waypoint.one": "{0} punto de ruta más",
  "wp.more.waypoint.other": "{0} puntos de ruta más",
  "wp.more.dimension.one": "{0} dimensión más",
  "wp.more.dimension.other": "{0} dimensiones más",
  "wp.more.server.one": "{0} servidor más",
  "wp.more.server.other": "{0} servidores más",
  "wp.more.after": "{0} después de esta página",
  "wp.plain.continue": "{0}: {1}",
  "wp.sort": "Orden",
  "wp.sort.default": "Predeterminado",
  "wp.sort.name": "Nombre",
  "wp.sort.distance": "Distancia",
  "wp.sort.color": "Color",
  "wp.sort.default.saved": "Orden guardado",
  "wp.sort.default.published": "Orden publicado",
  "wp.sort.name.tooltip": "Ordenar por nombre",
  "wp.sort.distance.tooltip": "Ordenar por distancia a ti",
  "wp.sort.color.tooltip": "Ordenar por color",
  "wp.sort.name.ascending": "De la A a la Z",
  "wp.sort.name.descending": "De la Z a la A",
  "wp.sort.distance.ascending": "Más cercanos primero",
  "wp.sort.distance.descending": "Más lejanos primero",
  "wp.sort.color.ascending": "Por tono",
  "wp.sort.color.descending": "Tono invertido",
  "wp.sort.distance.unavailable": "La distancia requiere que estés en {0}",
  "wp.view.lists": "Listas",
  "wp.view.tree": "Árbol",
  "wp.view.flat": "Plana",
  "wp.view.lists.tooltip": "Una línea por lista",
  "wp.view.tree.tooltip": "Listas con sus primeros puntos de ruta",
  "wp.view.flat.tooltip": "Una fila por punto de ruta",
  "wp.search": "Buscar",
  "wp.search.results": "Búsqueda «{0}»",
  "wp.search.clear": "Borrar",
  "wp.search.clear_search": "Borrar búsqueda",
  "wp.search.clear.tooltip": "Mostrar todo de nuevo",
  "wp.search.none": "Nada coincide con «{0}»."
```

`he_il.json`:

```json
  "wp.page.of": "עמוד {0} מתוך {1}",
  "wp.page.size": "{0} בעמוד",
  "wp.page.first": "העמוד הראשון",
  "wp.page.previous": "העמוד הקודם",
  "wp.page.next": "העמוד הבא",
  "wp.page.last": "העמוד האחרון",
  "wp.page.go": "מעבר לעמוד {0}",
  "wp.page.missing": "עמוד {0} לא קיים; העמוד האחרון הוא {1}.",
  "wp.more.list.one": "עוד {0} רשימה",
  "wp.more.list.other": "עוד {0} רשימות",
  "wp.more.waypoint.one": "עוד {0} נקודת ציון",
  "wp.more.waypoint.other": "עוד {0} נקודות ציון",
  "wp.more.dimension.one": "עוד {0} ממד",
  "wp.more.dimension.other": "עוד {0} ממדים",
  "wp.more.server.one": "עוד {0} שרת",
  "wp.more.server.other": "עוד {0} שרתים",
  "wp.more.after": "{0} אחרי העמוד הזה",
  "wp.plain.continue": "{0}: {1}",
  "wp.sort": "מיון",
  "wp.sort.default": "ברירת מחדל",
  "wp.sort.name": "שם",
  "wp.sort.distance": "מרחק",
  "wp.sort.color": "צבע",
  "wp.sort.default.saved": "הסדר השמור",
  "wp.sort.default.published": "הסדר שפורסם",
  "wp.sort.name.tooltip": "מיון לפי שם",
  "wp.sort.distance.tooltip": "מיון לפי המרחק ממך",
  "wp.sort.color.tooltip": "מיון לפי צבע",
  "wp.sort.name.ascending": "מא׳ עד ת׳",
  "wp.sort.name.descending": "מת׳ עד א׳",
  "wp.sort.distance.ascending": "הקרובים קודם",
  "wp.sort.distance.descending": "הרחוקים קודם",
  "wp.sort.color.ascending": "לפי גוון",
  "wp.sort.color.descending": "גוון הפוך",
  "wp.sort.distance.unavailable": "מיון לפי מרחק דורש שתהיו ב-{0}",
  "wp.view.lists": "רשימות",
  "wp.view.tree": "עץ",
  "wp.view.flat": "שטוח",
  "wp.view.lists.tooltip": "שורה אחת לכל רשימה",
  "wp.view.tree.tooltip": "רשימות עם נקודות הציון הראשונות שלהן",
  "wp.view.flat.tooltip": "שורה אחת לכל נקודת ציון",
  "wp.search": "חיפוש",
  "wp.search.results": "חיפוש \"{0}\"",
  "wp.search.clear": "ניקוי",
  "wp.search.clear_search": "ניקוי החיפוש",
  "wp.search.clear.tooltip": "הצגת הכול מחדש",
  "wp.search.none": "אין התאמות ל-\"{0}\"."
```

- [ ] **Step 2: Write the failing tests**

Create `common/src/test/java/_959/server_waypoint/text/chat/ListTargetTest.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTargetTest {
    @Test
    void commandsCarryOptionsInTheGrammarOrderAndLeaveDefaultsOut() {
        ListTarget target = ListTarget.dimension("minecraft:overworld");

        assertEquals("/wp list minecraft:overworld", target.command(ListQuery.DEFAULT));
        assertEquals("/wp list minecraft:overworld search \"iron farm\" sort name order descending limit 20 view tree page 2",
                target.command(new ListQuery("iron farm", SortMode.NAME, true, ListView.TREE, 2, 20)));
        assertEquals("/wp list minecraft:overworld view flat page ", target.pagePrompt(ListQuery.DEFAULT.withView(ListView.FLAT).withPage(3)));
        assertEquals("/wp list minecraft:overworld search ", target.searchPrompt());
    }

    @Test
    void namesThatNeedQuotingAreQuoted() {
        assertEquals("/wp list minecraft:overworld \"search\"", ListTarget.list("minecraft:overworld", "search").command(ListQuery.DEFAULT));
        assertEquals("/wp list minecraft:overworld \"Farm \\\"North\\\"\"",
                ListTarget.list("minecraft:overworld", "Farm \"North\"").command(ListQuery.DEFAULT));
        assertEquals("/wp list minecraft:overworld \"\"", ListTarget.list("minecraft:overworld", "").command(ListQuery.DEFAULT));
        assertEquals("/wp remote list survival \"minecraft:overworld\" Farms",
                ListTarget.remote("survival", "minecraft:overworld", "Farms").command(ListQuery.DEFAULT));
        assertEquals("/wp remote list", ListTarget.remote(null, null, null).command(ListQuery.DEFAULT));
    }

    @Test
    void changingAnOptionReturnsToTheFirstPageAndDefaultSortHasNoDirection() {
        ListQuery query = new ListQuery("", SortMode.NAME, true, ListView.FLAT, 4, null);

        assertEquals(1, query.withSort(SortMode.COLOR).page());
        assertFalse(query.withSort(SortMode.COLOR).descending());
        assertFalse(query.reversed().descending());
        assertEquals(1, query.withView(ListView.TREE).page());
        assertEquals(4, query.withPage(4).page());
        assertFalse(new ListQuery("", SortMode.DEFAULT, true, ListView.DEFAULT, 1, null).descending());
        assertEquals(10, query.pageLimit(10));
        assertThrows(IllegalArgumentException.class, () -> query.withPage(0));
    }
}
```

Create `common/src/test/java/_959/server_waypoint/text/chat/PagingTest.java`:

```java
package _959.server_waypoint.text.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingTest {
    @Test
    void pagesHoldWholeItemsWithinTheBudget() {
        List<List<Integer>> pages = Paging.byLines(List.of(4, 6, 5, 6, 2), size -> size, 15);

        assertEquals(List.of(List.of(4, 6, 5), List.of(6, 2)), pages);
        assertEquals(2, Paging.after(pages, 1));
        assertEquals(0, Paging.after(pages, 2));
    }

    @Test
    void anItemLargerThanTheBudgetGetsAPageOfItsOwn() {
        assertEquals(List.of(List.of(20), List.of(3)), Paging.byLines(List.of(20, 3), size -> size, 15));
    }

    @Test
    void nothingStillMakesOneEmptyPage() {
        assertEquals(List.of(List.of()), Paging.bySize(List.of(), 10));
        assertEquals(List.of(List.of(1, 2), List.of(3)), Paging.bySize(List.of(1, 2, 3), 2));
    }
}
```

Create `common/src/test/java/_959/server_waypoint/text/chat/ListControlsTest.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.chat.ChatTest.CONSOLE;
import static _959.server_waypoint.text.chat.ChatTest.PLAYER;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListControlsTest {
    private static final ListTarget OVERWORLD = ListTarget.dimension("minecraft:overworld");
    private static final List<SortMode> LOCAL_SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.DISTANCE, SortMode.COLOR);

    @Test
    void thePagerHasFirstAndLastArrowsFromThreePagesOn() {
        ListQuery query = ListQuery.DEFAULT.withView(ListView.FLAT).withPage(2);
        Component pager = ListControls.pager(PLAYER, OVERWORLD, query, 5,
                ListControls.pageDetail(10, Chat.count("wp.count.waypoint", 42)));

        assertEquals("    « ‹ 2/5 › »", render(pager));
        assertEquals("/wp list minecraft:overworld view flat", clickOf(pager, "«"));
        assertEquals("/wp list minecraft:overworld view flat", clickOf(pager, "‹"));
        assertEquals("/wp list minecraft:overworld view flat page 3", clickOf(pager, "›"));
        assertEquals("/wp list minecraft:overworld view flat page 5", clickOf(pager, "»"));
        assertEquals("/wp list minecraft:overworld view flat page ", clickOf(pager, " 2/5 "));
        assertEquals("Page 2 of 5\n10 per page · 42 waypoints\nClick to type a page", tooltipOf(pager, " 2/5 "));
        assertEquals(NamedTextColor.GRAY, colorOf(pager, " 2/5 "));
    }

    @Test
    void arrowsThatLeadNowhereAreDarkGray() {
        Component pager = ListControls.pager(PLAYER, OVERWORLD, ListQuery.DEFAULT, 2, text("detail"));

        assertEquals("    ‹ 1/2 ›", render(pager));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(pager, "‹"));
        assertNull(clickOf(pager, "‹"));
        assertNull(ListControls.pager(PLAYER, OVERWORLD, ListQuery.DEFAULT, 1, text("detail")));
        assertNull(ListControls.pager(CONSOLE, OVERWORLD, ListQuery.DEFAULT, 3, text("detail")));
    }

    @Test
    void moreLinesOpenTheNextPageOrPrintItsCommand() {
        Component more = ListControls.more(PLAYER, "list", 3, "/wp list minecraft:overworld page 2");

        assertEquals("… 3 more lists", render(more));
        assertEquals("/wp list minecraft:overworld page 2", clickOf(more, "3 more lists"));
        assertEquals("Next page\n3 lists after this page", tooltipOf(more, "3 more lists"));
        assertEquals(NamedTextColor.AQUA, colorOf(more, "3 more lists"));
        assertEquals("… 1 more waypoint: /wp list minecraft:overworld page 2",
                render(ListControls.more(CONSOLE, "waypoint", 1, "/wp list minecraft:overworld page 2")));
        assertNull(ListControls.more(PLAYER, "list", 0, "/wp list"));
    }

    @Test
    void theSelectedSortIsGoldCarriesItsDirectionAndReversesOnClick() {
        Component row = ListControls.sortRow(PLAYER, OVERWORLD, ListQuery.DEFAULT.withSort(SortMode.NAME),
                LOCAL_SORTS, "wp.sort.default.saved", null);

        assertEquals("Sort Default · Name ↑ · Distance · Color", render(row));
        assertEquals(NamedTextColor.GOLD, colorOf(row, "Name"));
        assertEquals("/wp list minecraft:overworld sort name order descending", clickOf(row, "Name"));
        assertEquals("Name · A to Z\nClick to reverse", tooltipOf(row, "Name"));
        assertEquals("/wp list minecraft:overworld", clickOf(row, "Default"));
        assertEquals("/wp list minecraft:overworld sort distance", clickOf(row, "Distance"));
        assertEquals(NamedTextColor.AQUA, colorOf(row, "Color"));
    }

    @Test
    void defaultSortIsGoldWithoutClickAndDistanceCanBeBlocked() {
        Component row = ListControls.sortRow(PLAYER, OVERWORLD, ListQuery.DEFAULT, LOCAL_SORTS,
                "wp.sort.default.saved", text("Distance needs you in Nether"));

        assertEquals(NamedTextColor.GOLD, colorOf(row, "Default"));
        assertNull(clickOf(row, "Default"));
        assertEquals("Saved order", tooltipOf(row, "Default"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(row, "Distance"));
        assertNull(clickOf(row, "Distance"));
        assertEquals("Distance needs you in Nether", tooltipOf(row, "Distance"));
        assertEquals("Sort Default · Name ↓ · Color", render(ListControls.sortRow(PLAYER, OVERWORLD,
                ListQuery.DEFAULT.withSort(SortMode.NAME).reversed(),
                List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.COLOR), "wp.sort.default.published", null)));
        assertNull(ListControls.sortRow(CONSOLE, OVERWORLD, ListQuery.DEFAULT, LOCAL_SORTS, "wp.sort.default.saved", null));
    }

    @Test
    void theViewRowMarksTheCurrentViewAndListsDropsTheSearch() {
        ListQuery query = ListQuery.DEFAULT.withSearch("farm");
        Component row = ListControls.viewRow(PLAYER, OVERWORLD, query, ListView.TREE,
                List.of(ListView.LISTS, ListView.TREE, ListView.FLAT),
                List.of(ListControls.search(PLAYER, OVERWORLD, text("Search Overworld"))));

        assertEquals("Lists · Tree · Flat · Search", render(row));
        assertEquals(NamedTextColor.GOLD, colorOf(row, "Tree"));
        assertNull(clickOf(row, "Tree"));
        assertEquals("/wp list minecraft:overworld view lists", clickOf(row, "Lists"));
        assertEquals("/wp list minecraft:overworld search farm view flat", clickOf(row, "Flat"));
        assertEquals("/wp list minecraft:overworld search ", clickOf(row, "Search"));
        assertEquals("Search Overworld\nType what to look for, then press Enter", tooltipOf(row, "Search"));
    }

    @Test
    void searchLinesShowTheQueryTheMatchesAndHowToClearIt() {
        Component results = ListControls.searchLine(PLAYER, "farm", 3, "/wp list minecraft:overworld");
        Component none = ListControls.noMatches(PLAYER, "farm", "/wp list minecraft:overworld");

        assertEquals("Search \"farm\" · 3 matches · Clear", render(results));
        assertEquals(NamedTextColor.WHITE, colorOf(results, "farm"));
        assertEquals("/wp list minecraft:overworld", clickOf(results, "Clear"));
        assertEquals("Nothing matches \"farm\". Clear search", render(none));
        assertEquals("Nothing matches \"farm\".", render(ListControls.noMatches(CONSOLE, "farm", "/wp list")));
    }

    @Test
    void thePagerClosesTheLastControlRow() {
        ChatLines lines = new ChatLines();
        ListControls.controls(lines, text("    ‹ 1/2 ›"), text("Lists · Tree"), null, text("Sort Default"));
        ChatLines alone = new ChatLines();
        ListControls.controls(alone, text("    ‹ 1/2 ›"), null, null);

        assertEquals(List.of("Lists · Tree", "Sort Default    ‹ 1/2 ›"), ChatAssert.lines(lines.build()));
        assertEquals(List.of("    ‹ 1/2 ›"), ChatAssert.lines(alone.build()));
    }

    @Test
    void aMissingPageNamesTheLastOneAndLinksToIt() {
        Component error = ListControls.pageNotFound(PLAYER, OVERWORLD, ListQuery.DEFAULT.withPage(5), 2);

        assertEquals("✘ Page 5 does not exist; the last page is 2. Last page", render(error));
        assertEquals("/wp list minecraft:overworld page 2", clickOf(error, "Last page"));
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.ListTargetTest' --tests '_959.server_waypoint.text.chat.PagingTest' --tests '_959.server_waypoint.text.chat.ListControlsTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class ListTarget`.

- [ ] **Step 4: Write `ListView` and `ListQuery`**

Create `common/src/main/java/_959/server_waypoint/text/chat/ListView.java`:

```java
package _959.server_waypoint.text.chat;

/** How a dimension's lists are shown. DEFAULT lets the screen choose. */
public enum ListView {
    DEFAULT(""),
    LISTS("lists"),
    TREE("tree"),
    FLAT("flat");

    private final String id;

    ListView(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/chat/ListQuery.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The options of a list command. A null limit stands for the server's configured page limit.
 * Every change except the page itself returns to the first page.
 */
public record ListQuery(
        String search,
        SortMode sort,
        boolean descending,
        ListView view,
        int page,
        @Nullable Integer limit
) {
    public static final ListQuery DEFAULT = new ListQuery("", SortMode.DEFAULT, false, ListView.DEFAULT, 1, null);

    public ListQuery {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(view, "view");
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        descending = descending && sort != SortMode.DEFAULT;
    }

    public boolean searching() {
        return !this.search.isBlank();
    }

    public int pageLimit(int configured) {
        return this.limit == null ? configured : this.limit;
    }

    public ListQuery withSearch(String search) {
        return new ListQuery(search, this.sort, this.descending, this.view, 1, this.limit);
    }

    public ListQuery withSort(SortMode sort) {
        return new ListQuery(this.search, sort, false, this.view, 1, this.limit);
    }

    public ListQuery reversed() {
        return new ListQuery(this.search, this.sort, !this.descending, this.view, 1, this.limit);
    }

    public ListQuery withView(ListView view) {
        return new ListQuery(this.search, this.sort, this.descending, view, 1, this.limit);
    }

    public ListQuery withPage(int page) {
        return new ListQuery(this.search, this.sort, this.descending, this.view, page, this.limit);
    }
}
```

- [ ] **Step 5: Write `ListTarget` and `Paging`**

Create `common/src/main/java/_959/server_waypoint/text/chat/ListTarget.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.util.StringCommandBuilder.escapeArgument;
import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;

/**
 * The command a list screen points at, without options. Options follow in the order the grammar
 * accepts: search, sort and order, limit, view, then page, so a command can end in "page ".
 */
public record ListTarget(String base) {
    public static ListTarget dimension(String dimension) {
        return new ListTarget("/wp list " + dimension);
    }

    public static ListTarget list(String dimension, String list) {
        return new ListTarget("/wp list " + dimension + " " + escapeListName(list));
    }

    public static ListTarget allDimensions() {
        return new ListTarget("/wp list all");
    }

    public static ListTarget dimensions() {
        return new ListTarget("/wp list dimensions");
    }

    /** /wp remote list with as many of server, dimension and list as are given, in that order. */
    public static ListTarget remote(@Nullable String server, @Nullable String dimension, @Nullable String list) {
        StringBuilder base = new StringBuilder("/wp remote list");
        for (String identity : new String[]{server, dimension, list}) {
            if (identity == null) {
                break;
            }
            base.append(' ').append(escapeListName(identity));
        }
        return new ListTarget(base.toString());
    }

    public String command(ListQuery query) {
        StringBuilder command = new StringBuilder(this.base);
        if (query.searching()) {
            command.append(" search ").append(escapeArgument(query.search()));
        }
        if (query.sort() != SortMode.DEFAULT) {
            command.append(" sort ").append(query.sort().name().toLowerCase(Locale.ROOT));
            if (query.descending()) {
                command.append(" order descending");
            }
        }
        if (query.limit() != null) {
            command.append(" limit ").append(query.limit());
        }
        if (query.view() != ListView.DEFAULT) {
            command.append(" view ").append(query.view().id());
        }
        if (query.page() > 1) {
            command.append(" page ").append(query.page());
        }
        return command.toString();
    }

    /** The command up to "page ", for the player to type a page number. */
    public String pagePrompt(ListQuery query) {
        return this.command(query.withPage(1)) + " page ";
    }

    /** The target followed by "search ", for the player to type what to look for. */
    public String searchPrompt() {
        return this.base + " search ";
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/chat/Paging.java`:

```java
package _959.server_waypoint.text.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Splits rows into pages. There is always at least one page, which may be empty. */
public final class Paging {
    private Paging() {
    }

    /** Pages of whole items within a line budget; an item larger than the budget gets a page of its own. */
    public static <T> List<List<T>> byLines(List<T> items, ToIntFunction<T> lines, int budget) {
        List<List<T>> pages = new ArrayList<>();
        List<T> page = new ArrayList<>();
        int used = 0;
        for (T item : items) {
            int size = lines.applyAsInt(item);
            if (!page.isEmpty() && used + size > budget) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
            }
            page.add(item);
            used += size;
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

    public static <T> List<List<T>> bySize(List<T> items, int size) {
        return byLines(items, item -> 1, size);
    }

    /** How many items the pages after this one hold; pages count from 1. */
    public static <T> int after(List<List<T>> pages, int page) {
        int count = 0;
        for (int index = page; index < pages.size(); index++) {
            count += pages.get(index).size();
        }
        return count;
    }
}
```

- [ ] **Step 6: Write `ListControls`**

Create `common/src/main/java/_959/server_waypoint/text/chat/ListControls.java`:

```java
package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Controls that local and remote list screens share (spec 6.3, 6.4, 6.6). */
public final class ListControls {
    private ListControls() {
    }

    /**
     * The pager that closes the last control line: « ‹ 2/5 › ». « and » appear only with three or
     * more pages, and arrows that lead nowhere are dark gray. Null for one page and for plain text.
     */
    public static @Nullable Component pager(Viewer viewer, ListTarget target, ListQuery query, int pages,
                                            Component detail) {
        if (pages <= 1 || viewer.plainText()) {
            return null;
        }
        int page = query.page();
        List<Component> pieces = new ArrayList<>();
        pieces.add(text("    "));
        if (pages >= 3) {
            pieces.add(arrow(viewer, "«", target, query, 1, page > 1, "wp.page.first"));
            pieces.add(space());
        }
        pieces.add(arrow(viewer, "‹", target, query, page - 1, page > 1, "wp.page.previous"));
        pieces.add(Chat.link(viewer, text(" " + page + "/" + pages + " "), GRAY, Click.suggest(target.pagePrompt(query)),
                Tooltip.of("wp.page.of", text(page), text(pages)).line(detail).hint("wp.hint.page")));
        pieces.add(arrow(viewer, "›", target, query, page + 1, page < pages, "wp.page.next"));
        if (pages >= 3) {
            pieces.add(space());
            pieces.add(arrow(viewer, "»", target, query, pages, page < pages, "wp.page.last"));
        }
        return Chat.concat(pieces);
    }

    private static Component arrow(Viewer viewer, String glyph, ListTarget target, ListQuery query, int page,
                                   boolean enabled, String key) {
        if (!enabled) {
            return text(glyph, DARK_GRAY);
        }
        return Chat.link(viewer, text(glyph), AQUA, Click.run(target.command(query.withPage(page))), Tooltip.of(key));
    }

    /** "10 per page · 42 waypoints", the second line of the page number's tooltip. */
    public static Component pageDetail(int pageLimit, Component total) {
        return Chat.join(translatable("wp.page.size", text(pageLimit)), total);
    }

    /** "… 3 more lists" opening the next page. Plain-text viewers read the command after a colon. */
    public static @Nullable Component more(Viewer viewer, String unit, int count, String command) {
        if (count <= 0) {
            return null;
        }
        Component label = Chat.concat(text(Chat.ELLIPSIS + " "), Chat.count("wp.more." + unit, count));
        if (viewer.plainText()) {
            return translatable("wp.plain.continue", label, text(command));
        }
        return Chat.link(viewer, label, AQUA, Click.run(command), Tooltip.of("wp.page.next")
                .line(translatable("wp.more.after", Chat.count("wp.count." + unit, count))));
    }

    /**
     * Sort Default · Name ↑ · Distance · Color. The selected mode is gold; apart from Default it
     * carries its direction and reverses on click. distanceBlocked, when given, disables Distance
     * and becomes its tooltip.
     */
    public static @Nullable Component sortRow(Viewer viewer, ListTarget target, ListQuery query, List<SortMode> modes,
                                              String defaultTooltipKey, @Nullable Component distanceBlocked) {
        if (viewer.plainText()) {
            return null;
        }
        List<Component> items = new ArrayList<>();
        for (SortMode mode : modes) {
            items.add(sortItem(viewer, target, query, mode, defaultTooltipKey, distanceBlocked));
        }
        return Chat.concat(translatable("wp.sort", GRAY), space(), Chat.join(items));
    }

    private static Component sortItem(Viewer viewer, ListTarget target, ListQuery query, SortMode mode,
                                      String defaultTooltipKey, @Nullable Component distanceBlocked) {
        String key = "wp.sort." + mode.name().toLowerCase(Locale.ROOT);
        Component label = translatable(key);
        if (query.sort() == mode && mode == SortMode.DEFAULT) {
            return Chat.hover(viewer, Chat.colored(label, GOLD), Tooltip.of(defaultTooltipKey));
        }
        if (query.sort() == mode) {
            Component direction = translatable(key + (query.descending() ? ".descending" : ".ascending"));
            return Chat.link(viewer, Chat.concat(label, text(query.descending() ? " ↓" : " ↑")), GOLD,
                    Click.run(target.command(query.reversed())),
                    Tooltip.of(Chat.join(label, direction)).hint("wp.hint.reverse"));
        }
        if (mode == SortMode.DISTANCE && distanceBlocked != null) {
            return Chat.hover(viewer, Chat.colored(label, DARK_GRAY), Tooltip.of(distanceBlocked));
        }
        return Chat.link(viewer, label, AQUA, Click.run(target.command(query.withSort(mode))),
                Tooltip.of(key + ".tooltip"));
    }

    /** Lists · Tree · Flat, then the extras. The current view is gold; switching to Lists drops the search. */
    public static @Nullable Component viewRow(Viewer viewer, ListTarget target, ListQuery query, ListView current,
                                              List<ListView> views, List<? extends @Nullable Component> extras) {
        if (viewer.plainText()) {
            return null;
        }
        List<Component> items = new ArrayList<>();
        for (ListView view : views) {
            Component label = translatable("wp.view." + view.id());
            if (view == current) {
                items.add(Chat.colored(label, GOLD));
                continue;
            }
            ListQuery next = view == ListView.LISTS ? query.withSearch("").withView(view) : query.withView(view);
            items.add(Chat.link(viewer, label, AQUA, Click.run(target.command(next)),
                    Tooltip.of("wp.view." + view.id() + ".tooltip")));
        }
        items.addAll(extras);
        return Chat.join(items);
    }

    /** The aqua Search link, which suggests "<target> search ". */
    public static @Nullable Component search(Viewer viewer, ListTarget target, Component tooltipTitle) {
        return Chat.control(viewer, translatable("wp.search"), AQUA, Click.suggest(target.searchPrompt()),
                Tooltip.of(tooltipTitle).hint("wp.hint.type_search"));
    }

    /** Search "farm" · 3 matches · Clear */
    public static Component searchLine(Viewer viewer, String search, int matches, String clearCommand) {
        return Chat.join(
                translatable("wp.search.results", GRAY, text(search, WHITE)),
                Chat.colored(Chat.count("wp.count.match", matches), GRAY),
                Chat.control(viewer, translatable("wp.search.clear"), AQUA, Click.run(clearCommand),
                        Tooltip.of("wp.search.clear.tooltip")));
    }

    /** Nothing matches "farm". Clear search */
    public static Component noMatches(Viewer viewer, String search, String clearCommand) {
        Component clear = Chat.control(viewer, translatable("wp.search.clear_search"), AQUA, Click.run(clearCommand),
                Tooltip.of("wp.search.clear.tooltip"));
        return Chat.concat(translatable("wp.search.none", GRAY, text(search)), clear == null ? null : space(), clear);
    }

    /** ✘ Page 5 does not exist; the last page is 2. Last page */
    public static Component pageNotFound(Viewer viewer, ListTarget target, ListQuery query, int pages) {
        return Chat.error(translatable("wp.page.missing", text(query.page()), text(pages)),
                Chat.control(viewer, translatable("wp.page.last"), AQUA,
                        Click.run(target.command(query.withPage(pages))), Tooltip.of("wp.page.go", text(pages))));
    }

    /** Adds the control rows that exist; the pager closes the last of them (spec 6.3). */
    public static void controls(ChatLines lines, @Nullable Component pager, @Nullable Component... rows) {
        List<Component> present = new ArrayList<>();
        for (Component row : rows) {
            if (row != null) {
                present.add(row);
            }
        }
        if (present.isEmpty()) {
            lines.add(pager);
            return;
        }
        for (int index = 0; index < present.size(); index++) {
            boolean last = index == present.size() - 1;
            lines.add(last ? Chat.concat(present.get(index), pager) : present.get(index));
        }
    }
}
```

- [ ] **Step 7: Run the tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.chat.*' --tests '_959.server_waypoint.translation.TranslationFilesTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/chat common/src/test/java/_959/server_waypoint/text/chat common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add list options, paging and the shared list controls"
```

---

### Task 6: Receivers, the trailing newline and the viewer

The platforms decide two things the builders can't: whether a message goes to a plain-text receiver, and the dimension types (spec 15, 17). Plain text follows the receiver, not the executing entity, so `/execute as Alex run wp list` from the console prints plain text. Players' messages get their one trailing newline at the send boundary (spec 2.3). `CoreWaypointCommand` builds a `Viewer` once per command source. This task also adds `CommandHarness`, a reusable `/wp` test platform for the screen tasks that follow.

`sendError` keeps its red for now; Task 13 removes it, once every error starts with a red `✘`.

**Files:**
- Modify: `common/src/main/java/_959/server_waypoint/core/network/PlatformMessageSender.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/server/command/WaypointCommand.java`
- Modify: `paper/src/main/java/_959/server_waypoint/network/PaperMessageSender.java`
- Modify: `paper/src/main/java/_959/server_waypoint/server/command/WaypointCommand.java`
- Modify (test senders and commands): `common/src/test/java/_959/server_waypoint/core/network/PlatformMessageSenderTransportTest.java`, `common/src/test/java/_959/server_waypoint/core/network/C2SPacketHandlerTest.java`, `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`, `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandNavigationTest.java`
- Create: `common/src/test/java/_959/server_waypoint/command/CommandHarness.java`
- Test: `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`

**Interfaces:**
- Consumes: `Viewer`, `DimensionStyle` (Tasks 2, 4).
- Produces:
  - `PlatformMessageSender.isPlainTextReceiver(S source)` (abstract) and `static Component PlatformMessageSender.forPlayer(Component message)`.
  - `CoreWaypointCommand`: `protected abstract Map<String, String> getDimensionTypes(S source)` (dimension ID to dimension type ID), `protected final Viewer viewer(S source)`, `protected final DimensionStyle dimensions(S source, Viewer viewer)`.
  - `RemoteWaypointCommand.canTeleport(S source)`.
  - Test harness `CommandHarness` (package `_959.server_waypoint.command`): `record Source(String name, String dimension, WaypointPos position, float yaw, boolean player, boolean plainText, Set<String> permissions)` with `withPermissions(String...)`, `in(String dimension)`, `readByConsole()`; `static Source player()`, `static Source console()`; fields `server`, `sender`, `dimensionTypes`, `command`, `dispatcher`, `teleports`; `Component run(Source, String command)`, `void fails(Source, String command)`, `addList(String dimension, String list, SimpleWaypoint...)`, `static SimpleWaypoint waypoint(String name, String initials, int rgb, int x, int y, int z)`. `Sender` keeps `received` (everything sent to the source, in order), `errors`, `toPlayers`, `online` and `handshake`.

- [ ] **Step 1: Write the harness**

Create `common/src/test/java/_959/server_waypoint/command/CommandHarness.java`:

```java
package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.ChunkedMessageDelivery;
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.SinglePacketMessage;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.util.NamespacedId;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A /wp command on a test platform. A player source is also its own player object; a plain-text
 * source reads its feedback like the console. Permissions are plain strings such as "add".
 */
final class CommandHarness {
    static final Set<String> EVERY_PERMISSION = Set.of("add", "edit", "remove", "tp", "navigate", "reload",
            "upload", "upload.delete", "remote.list", "remote.tp");

    record Source(String name, String dimension, WaypointPos position, float yaw, boolean player,
                  boolean plainText, Set<String> permissions) {
        Source withPermissions(String... permissions) {
            return new Source(this.name, this.dimension, this.position, this.yaw, this.player, this.plainText,
                    Set.of(permissions));
        }

        Source in(String dimension) {
            return new Source(this.name, dimension, this.position, this.yaw, this.player, this.plainText,
                    this.permissions);
        }

        /** The same player run from the console with /execute as: the console reads the feedback. */
        Source readByConsole() {
            return new Source(this.name, this.dimension, this.position, this.yaw, this.player, true,
                    this.permissions);
        }
    }

    record Teleport(Source player, String dimension, WaypointPos position, int yaw) {
    }

    static Source player() {
        return new Source("Alex", "minecraft:overworld", new WaypointPos(100, 64, -20), 37F, true, false,
                EVERY_PERMISSION);
    }

    static Source console() {
        return new Source("Server", "minecraft:overworld", new WaypointPos(0, 64, 0), 0F, false, true,
                EVERY_PERMISSION);
    }

    final WaypointServerCore server;
    final Sender sender = new Sender();
    final Map<String, String> dimensionTypes = new LinkedHashMap<>();
    final List<Teleport> teleports = new ArrayList<>();
    final TestCommand command;
    final CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();

    CommandHarness(Path directory) {
        this.server = new WaypointServerCore(directory) {
            @Override
            protected boolean isRegisteredIconItem(NamespacedId icon) {
                return icon.toString().equals("minecraft:diamond");
            }
        };
        this.dimensionTypes.put("minecraft:overworld", "minecraft:overworld");
        this.dimensionTypes.put("minecraft:the_nether", "minecraft:the_nether");
        this.dimensionTypes.put("minecraft:the_end", "minecraft:the_end");
        this.command = new TestCommand(this);
        this.command.register(this.dispatcher);
    }

    /** Runs the command and returns the last message it sent back to the source. */
    Component run(Source source, String command) {
        int before = this.sender.received.size();
        try {
            this.dispatcher.execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new AssertionError(command, exception);
        }
        if (this.sender.received.size() == before) {
            throw new AssertionError("No feedback for " + command);
        }
        return this.sender.received.get(this.sender.received.size() - 1);
    }

    /** The command does not parse for this source. */
    void fails(Source source, String command) {
        assertThrows(CommandSyntaxException.class, () -> this.dispatcher.execute(command, source), command);
    }

    void addList(String dimension, String list, SimpleWaypoint... waypoints) {
        this.server.putWaypointList(dimension, new WaypointList(list, 1, List.of(waypoints)));
    }

    static SimpleWaypoint waypoint(String name, String initials, int rgb, int x, int y, int z) {
        return new SimpleWaypoint(name, initials, new WaypointPos(x, y, z), rgb, 0, true);
    }

    static final class Sender implements PlatformMessageSender<Source, Object> {
        final List<Component> received = new ArrayList<>();
        final List<Component> errors = new ArrayList<>();
        final List<Map.Entry<Object, Component>> toPlayers = new ArrayList<>();
        final List<Object> online = new ArrayList<>();
        boolean handshake = true;

        @Override
        public void sendMessage(Source source, Component component) {
            this.received.add(component);
        }

        @Override
        public void sendPlayerMessage(Object player, Component component) {
            this.toPlayers.add(Map.entry(player, component));
        }

        @Override
        public void sendError(Source source, Component component) {
            this.received.add(component);
            this.errors.add(component);
        }

        @Override
        public boolean isPlainTextReceiver(Source source) {
            return source.plainText();
        }

        @Override
        public void sendPacket(Source source, SinglePacketMessage message) {
        }

        @Override
        public void sendPlayerPacket(Object player, SinglePacketMessage message) {
        }

        @Override
        public void broadcastPacket(SinglePacketMessage message) {
        }

        @Override
        public ChunkedMessageDelivery sendChunkedMessage(Source source, ChunkedMessage message) {
            return ChunkedMessageDelivery.rejected(ChunkedMessageSendResult.UNSUPPORTED);
        }

        @Override
        public Iterable<?> getBroadcastPlayers(Source source) {
            return this.online;
        }

        @Override
        public Component getSenderName(Source source) {
            return Component.text(source.name());
        }

        @Override
        public boolean canSendChunkedMessage(Object player) {
            return this.handshake;
        }
    }

    static final class TestCommand extends CoreWaypointCommand<Source, String, Object, String, String, NamespacedId> {
        private final CommandHarness harness;

        private TestCommand(CommandHarness harness) {
            super(harness.server, harness.sender, permissions(), navigation(),
                    new UploadCoordinator<>(harness.server, (player, message) -> {
                    }, packet -> {
                    }, player -> true, player -> true, navigation(), player -> new UUID(0L, 0L)),
                    () -> word(), CommandHarness::position, () -> reader -> NamespacedId.parse(readWord(reader)));
            this.harness = harness;
        }

        private static ArgumentType<String> word() {
            return CommandHarness::readWord;
        }

        @Override
        protected String toDimensionName(String dimensionArgument) {
            return dimensionArgument;
        }

        @Override
        protected NamespacedId toIconId(NamespacedId iconArgument) {
            return iconArgument;
        }

        @Override
        protected CompletableFuture<Suggestions> suggestIconIds(CommandContext<Source> context, SuggestionsBuilder builder) {
            return builder.buildFuture();
        }

        @Override
        protected WaypointPos toWaypointPos(Source source, String position) {
            String[] parts = position.split(" ");
            return new WaypointPos(coordinate(parts[0], source.position().x()),
                    coordinate(parts[1], source.position().y()), coordinate(parts[2], source.position().z()));
        }

        private static int coordinate(String part, int origin) {
            if (part.startsWith("~")) {
                return origin + (part.length() == 1 ? 0 : Integer.parseInt(part.substring(1)));
            }
            return Integer.parseInt(part);
        }

        @Override
        protected boolean isDimensionValid(Source source, String dimensionArgument) {
            return this.harness.dimensionTypes.containsKey(dimensionArgument);
        }

        @Override
        protected void executeByServer(Source source, Runnable task) {
            task.run();
        }

        @Override
        protected String getSourceDimension(Source source) {
            return source.dimension();
        }

        @Override
        protected WaypointPos getSourcePosition(Source source) {
            return source.position();
        }

        @Override
        protected float getSourceYaw(Source source) {
            return source.yaw();
        }

        @Override
        protected Object getPlayer(Source source) {
            return source.player() ? source : null;
        }

        @Override
        protected boolean isServerConsoleWithHighestPermission(Source source) {
            return !source.player();
        }

        @Override
        protected String getPlayerName(Object player) {
            return ((Source) player).name();
        }

        @Override
        protected void teleportPlayer(Source source, Object player, String dimension, WaypointPos pos, int yaw) {
            this.harness.teleports.add(new Teleport((Source) player, dimension, pos, yaw));
        }

        @Override
        protected Message getMessageFromComponent(Component component) {
            return component::toString;
        }

        @Override
        protected List<String> getAvailableDimensionNames(Source source) {
            return List.copyOf(this.harness.dimensionTypes.keySet());
        }

        @Override
        protected Map<String, String> getDimensionTypes(Source source) {
            return Map.copyOf(this.harness.dimensionTypes);
        }
    }

    /** Three coordinates such as "~ ~ ~" or "1 64 -2", like a block position argument. */
    private static ArgumentType<String> position() {
        return reader -> {
            int start = reader.getCursor();
            for (int part = 0; part < 3; part++) {
                if (part > 0) {
                    reader.expect(' ');
                }
                if (!readWord(reader).matches("~(-?\\d+)?|-?\\d+")) {
                    throw new SimpleCommandExceptionType(() -> "Expected a coordinate").createWithContext(reader);
                }
            }
            return reader.getString().substring(start, reader.getCursor());
        };
    }

    private static String readWord(com.mojang.brigadier.StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    private static PermissionManager<Source, String, Object> permissions() {
        PermissionKeys<String> keys = new PermissionKeys<>() {
            @Override
            protected PermissionKey createAddPermissionKey() {
                return new PermissionKey("add");
            }

            @Override
            protected PermissionKey createEditPermissionKey() {
                return new PermissionKey("edit");
            }

            @Override
            protected PermissionKey createRemovePermissionKey() {
                return new PermissionKey("remove");
            }

            @Override
            protected PermissionKey createNavigatePermissionKey() {
                return new PermissionKey("navigate");
            }

            @Override
            protected PermissionKey createTpPermissionKey() {
                return new PermissionKey("tp");
            }

            @Override
            protected PermissionKey createReloadPermissionKey() {
                return new PermissionKey("reload");
            }

            @Override
            protected PermissionKey createUploadPermissionKey() {
                return new PermissionKey("upload");
            }

            @Override
            protected PermissionKey createUploadDeletePermissionKey() {
                return new PermissionKey("upload.delete");
            }

            @Override
            protected PermissionKey createRemoteListPermissionKey() {
                return new PermissionKey("remote.list");
            }

            @Override
            protected PermissionKey createRemoteTpPermissionKey() {
                return new PermissionKey("remote.tp");
            }
        };
        return new PermissionManager<>(keys) {
            @Override
            public boolean hasPermission(Source source, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
                return source.permissions().contains(key.getKey());
            }

            @Override
            public boolean checkPlayerPermission(Object player, PermissionKeys<String>.PermissionKey key, int defaultLevel) {
                return ((Source) player).permissions().contains(key.getKey());
            }
        };
    }

    private static NavigationService<Object> navigation() {
        return new NavigationService<>(new NavigationPlatform<>() {
            @Override
            public UUID playerUuid(Object player) {
                return new UUID(0L, 0L);
            }

            @Override
            public void executePlayer(UUID playerUuid, Consumer<Object> action) {
            }

            @Override
            public NavigationSnapshot snapshot(Object player, NavigationTarget target) {
                return NavigationSnapshot.wrongDimension();
            }
        }, List.of());
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`:

```java
package _959.server_waypoint.command;

import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Viewer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.render;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /wp feedback through the command: the viewer, the receiver and every screen's wiring. */
class CommandFeedbackTest {
    @TempDir
    Path directory;
    private Config originalConfig;
    CommandHarness harness;

    @BeforeEach
    void setUp() {
        this.originalConfig = WaypointServerCore.CONFIG;
        WaypointServerCore.CONFIG = new Config();
        this.harness = new CommandHarness(this.directory);
    }

    @AfterEach
    void tearDown() {
        WaypointServerCore.CONFIG = this.originalConfig;
    }

    @Test
    void theViewerFollowsPermissionsTheModTheReceiverAndThePosition() {
        Viewer viewer = this.harness.command.viewer(CommandHarness.player().withPermissions("add", "tp"));

        assertEquals(Set.of(Viewer.Permission.ADD, Viewer.Permission.TP), viewer.permissions());
        assertTrue(viewer.hasMod());
        assertFalse(viewer.plainText());
        assertEquals("minecraft:overworld", viewer.dimension());
        assertEquals(new WaypointPos(100, 64, -20), viewer.position());
        assertEquals(37F, viewer.yaw());
        assertTrue(this.harness.command.viewer(CommandHarness.player()).can(Viewer.Permission.REMOTE_TP));

        Viewer console = this.harness.command.viewer(CommandHarness.console());
        assertTrue(console.plainText());
        assertFalse(console.hasMod());
        assertFalse(console.can(Viewer.Permission.REMOTE_TP));

        assertTrue(this.harness.command.viewer(CommandHarness.player().readByConsole()).plainText());
        this.harness.sender.handshake = false;
        assertFalse(this.harness.command.viewer(CommandHarness.player()).hasMod());
    }

    @Test
    void playersMessagesEndWithOneNewline() {
        assertEquals("Farms\n", render(PlatformMessageSender.forPlayer(text("Farms"))));
    }
}
```

- [ ] **Step 3: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.command.CommandFeedbackTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `method does not override or implement a method from a supertype` for `isPlainTextReceiver` and `getDimensionTypes`.

- [ ] **Step 4: Add the receiver check and the newline helper**

In `common/src/main/java/_959/server_waypoint/core/network/PlatformMessageSender.java`, replace:

```java
public interface PlatformMessageSender<S, P> {
    void sendMessage(S source, Component component);
```

with:

```java
public interface PlatformMessageSender<S, P> {
    /**
     * Every message to a player ends with one newline, which the game draws as a blank line before
     * the next message. Platforms add it when they send; builders never end with a newline.
     */
    static Component forPlayer(Component message) {
        return Component.empty().append(message).appendNewline();
    }

    /**
     * Whether this source's output goes to a plain-text receiver such as the console, RCON or a
     * command block. It follows the receiver, not the executing entity: /execute as a player from
     * the console still prints plain text.
     */
    boolean isPlainTextReceiver(S source);

    void sendMessage(S source, Component component);
```

- [ ] **Step 5: Build the viewer in `CoreWaypointCommand`**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.TextButtonBuilder;
```

with:

```java
import _959.server_waypoint.text.TextButtonBuilder;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
```

replace:

```java
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
```

with:

```java
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
```

replace:

```java
    protected abstract List<String> getAvailableDimensionNames(S source);
```

with:

```java
    protected abstract List<String> getAvailableDimensionNames(S source);
    /** Each loaded dimension and its dimension type ID, such as minecraft:the_nether. */
    protected abstract Map<String, String> getDimensionTypes(S source);
```

and replace:

```java
    private boolean hasUploadDeletePermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.uploadDelete(), CONFIG.CommandPermission().uploadDelete());
    }
```

with:

```java
    private boolean hasUploadDeletePermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.uploadDelete(), CONFIG.CommandPermission().uploadDelete());
    }

    /** Whoever reads this source's feedback, built once per command source (spec 17). */
    protected final Viewer viewer(S source) {
        Set<Viewer.Permission> permissions = EnumSet.noneOf(Viewer.Permission.class);
        if (hasAddPermission(source)) permissions.add(Viewer.Permission.ADD);
        if (hasEditPermission(source)) permissions.add(Viewer.Permission.EDIT);
        if (hasRemovePermission(source)) permissions.add(Viewer.Permission.REMOVE);
        if (hasTpPermission(source)) permissions.add(Viewer.Permission.TP);
        if (hasNavigatePermission(source)) permissions.add(Viewer.Permission.NAVIGATE);
        if (hasReloadPermission(source)) permissions.add(Viewer.Permission.RELOAD);
        if (hasUploadPermission(source)) permissions.add(Viewer.Permission.UPLOAD);
        if (hasUploadDeletePermission(source)) permissions.add(Viewer.Permission.UPLOAD_DELETE);
        if (this.remoteCommand.canList(source)) permissions.add(Viewer.Permission.REMOTE_LIST);
        if (this.remoteCommand.canTeleport(source)) permissions.add(Viewer.Permission.REMOTE_TP);
        P player = getPlayer(source);
        boolean hasMod = player != null
                && (this.sender.canSendChunkedMessage(player) || usesLocalUpload(source, player));
        return new Viewer(permissions, hasMod, this.sender.isPlainTextReceiver(source),
                toDimensionName(getSourceDimension(source)), getSourcePosition(source), getSourceYaw(source));
    }

    protected final DimensionStyle dimensions(S source, Viewer viewer) {
        return DimensionStyle.local(viewer, getDimensionTypes(source));
    }
```

In `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`, replace:

```java
    boolean canList(S source) { return canList.test(source); }
```

with:

```java
    boolean canList(S source) { return canList.test(source); }
    boolean canTeleport(S source) { return canTeleport.test(source); }
```

- [ ] **Step 6: Update the existing test senders and commands**

In `common/src/test/java/_959/server_waypoint/core/network/PlatformMessageSenderTransportTest.java`, replace:

```java
        @Override
        public Iterable<? extends String> getBroadcastPlayers(String source) {
            return List.of(source);
        }
```

with:

```java
        @Override
        public boolean isPlainTextReceiver(String source) {
            return false;
        }

        @Override
        public Iterable<? extends String> getBroadcastPlayers(String source) {
            return List.of(source);
        }
```

In `common/src/test/java/_959/server_waypoint/core/network/C2SPacketHandlerTest.java`, replace:

```java
        @Override
        public void sendError(String source, Component component) {
        }

        @Override
        public void sendPacket(String source, SinglePacketMessage message) {
            this.packets.add(message);
        }
```

with:

```java
        @Override
        public void sendError(String source, Component component) {
        }

        @Override
        public boolean isPlainTextReceiver(String source) {
            return false;
        }

        @Override
        public void sendPacket(String source, SinglePacketMessage message) {
            this.packets.add(message);
        }
```

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandNavigationTest.java`, replace:

```java
        @Override
        public Iterable<? extends TestPlayer> getBroadcastPlayers(TestSource source) {
            return List.of();
        }
```

with:

```java
        @Override
        public boolean isPlainTextReceiver(TestSource source) {
            return false;
        }

        @Override
        public Iterable<? extends TestPlayer> getBroadcastPlayers(TestSource source) {
            return List.of();
        }
```

and replace:

```java
        @Override
        protected List<String> getAvailableDimensionNames(TestSource source) {
            return List.of("overworld");
        }
    }
```

with:

```java
        @Override
        protected List<String> getAvailableDimensionNames(TestSource source) {
            return List.of("overworld");
        }

        @Override
        protected java.util.Map<String, String> getDimensionTypes(TestSource source) {
            return java.util.Map.of("overworld", "minecraft:overworld");
        }
    }
```

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`, replace:

```java
        @Override
        public Iterable<?> getBroadcastPlayers(TestSource source) {
            return List.of();
        }
```

with:

```java
        @Override
        public boolean isPlainTextReceiver(TestSource source) {
            return false;
        }

        @Override
        public Iterable<?> getBroadcastPlayers(TestSource source) {
            return List.of();
        }
```

and replace:

```java
        @Override
        protected List<String> getAvailableDimensionNames(TestSource source) {
            return List.of("overworld");
        }
```

with:

```java
        @Override
        protected List<String> getAvailableDimensionNames(TestSource source) {
            return List.of("overworld");
        }

        @Override
        protected java.util.Map<String, String> getDimensionTypes(TestSource source) {
            return java.util.Map.of("overworld", "minecraft:overworld");
        }
```

- [ ] **Step 7: Run the common tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS, including `CommandFeedbackTest`.

- [ ] **Step 8: The mods sender and command**

In `mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java`, replace:

```java
import _959.server_waypoint.common.server.WaypointServerMod;
```

with:

```java
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.mixin.CommandSourceStackAccessor;
```

replace:

```java
    private net.minecraft.network.chat.Component getTranslatedText(CommandSourceStack source, Component component) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return getTranslatedText(player, component);
        } else {
            return toVanillaText(GlobalTranslator.render(component, Locale.getDefault()));
        }
    }
```

with:

```java
    /** Renders for the receiver: a player gets their language and the trailing newline. */
    private net.minecraft.network.chat.Component getTranslatedText(CommandSourceStack source, Component component) {
        if (((CommandSourceStackAccessor) source).serverWaypoint$getSource() instanceof ServerPlayer player) {
            return getTranslatedText(player, PlatformMessageSender.forPlayer(component));
        }
        return toVanillaText(GlobalTranslator.render(component, Locale.getDefault()));
    }

    @Override
    public boolean isPlainTextReceiver(CommandSourceStack source) {
        return !(((CommandSourceStackAccessor) source).serverWaypoint$getSource() instanceof ServerPlayer);
    }
```

and replace:

```java
    @Override
    public void sendPlayerMessage(ServerPlayer player, Component component) {
        player.sendSystemMessage(getTranslatedText(player, component));
    }
```

with:

```java
    @Override
    public void sendPlayerMessage(ServerPlayer player, Component component) {
        player.sendSystemMessage(getTranslatedText(player, PlatformMessageSender.forPlayer(component)));
    }
```

In `mods/src/main/java/_959/server_waypoint/common/server/command/WaypointCommand.java`, replace:

```java
//? if >= 1.21.2
import java.util.Collections;
import java.util.List;
```

with:

```java
//? if >= 1.21.2
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
```

and replace:

```java
    @Override
    protected List<String> getAvailableDimensionNames(CommandSourceStack source) {
```

with:

```java
    @Override
    protected Map<String, String> getDimensionTypes(CommandSourceStack source) {
        Map<String, String> types = new LinkedHashMap<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            //? if >= 1.21.11 {
            String dimension = level.dimension().identifier().toString();
            String type = level.dimensionTypeRegistration().unwrapKey().map(key -> key.identifier().toString()).orElse("");
            //?} else {
            /*String dimension = level.dimension().location().toString();
            String type = level.dimensionTypeRegistration().unwrapKey().map(key -> key.location().toString()).orElse("");
            *///?}
            types.put(dimension, type);
        }
        return types;
    }

    @Override
    protected List<String> getAvailableDimensionNames(CommandSourceStack source) {
```

The `//? if >= 1.21.2` line scope above the imports covers only `import java.util.Collections;`; the new imports sit after it and are unconditional.

- [ ] **Step 9: The Paper sender and command**

In `paper/src/main/java/_959/server_waypoint/network/PaperMessageSender.java`, replace:

```java
    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        CommandSender sender = source.getSender();
        this.scheduler.execute(sender, () -> sender.sendMessage(component));
    }

    @Override
    public void sendPlayerMessage(Player player, Component component) {
        this.scheduler.execute(player, () -> player.sendMessage(component));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        CommandSender sender = source.getSender();
        this.scheduler.execute(sender, () -> sender.sendMessage(component));
    }
```

with:

```java
    @Override
    public boolean isPlainTextReceiver(CommandSourceStack source) {
        return !(source.getSender() instanceof Player);
    }

    @Override
    public void sendMessage(CommandSourceStack source, Component component) {
        CommandSender sender = source.getSender();
        Component message = this.isPlainTextReceiver(source) ? component : PlatformMessageSender.forPlayer(component);
        this.scheduler.execute(sender, () -> sender.sendMessage(message));
    }

    @Override
    public void sendPlayerMessage(Player player, Component component) {
        Component message = PlatformMessageSender.forPlayer(component);
        this.scheduler.execute(player, () -> player.sendMessage(message));
    }

    @Override
    public void sendError(CommandSourceStack source, Component component) {
        this.sendMessage(source, component);
    }
```

In `paper/src/main/java/_959/server_waypoint/server/command/WaypointCommand.java`, replace:

```java
import java.util.List;
import java.util.Arrays;
```

with:

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
```

and replace:

```java
    @Override
    protected List<String> getAvailableDimensionNames(CommandSourceStack source) {
```

with:

```java
    @Override
    protected Map<String, String> getDimensionTypes(CommandSourceStack source) {
        Map<String, String> types = new LinkedHashMap<>();
        for (World world : source.getSender().getServer().getWorlds()) {
            types.put(world.getKey().asString(), switch (world.getEnvironment()) {
                case NORMAL -> "minecraft:overworld";
                case NETHER -> "minecraft:the_nether";
                case THE_END -> "minecraft:the_end";
                default -> "custom";
            });
        }
        return types;
    }

    @Override
    protected List<String> getAvailableDimensionNames(CommandSourceStack source) {
```

- [ ] **Step 10: Check the markers and compile the platforms**

```bash
python3 SCRATCH/check_stonecutter.py mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java mods/src/main/java/_959/server_waypoint/common/server/command/WaypointCommand.java paper/src/main/java/_959/server_waypoint/network/PaperMessageSender.java paper/src/main/java/_959/server_waypoint/server/command/WaypointCommand.java
```

Expected: `balanced: 4 file(s)`.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/core/network/PlatformMessageSender.java common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java mods/src/main/java/_959/server_waypoint/common/server/command/WaypointCommand.java paper/src/main/java/_959/server_waypoint/network/PaperMessageSender.java paper/src/main/java/_959/server_waypoint/server/command/WaypointCommand.java common/src/test/java/_959/server_waypoint/core/network/PlatformMessageSenderTransportTest.java common/src/test/java/_959/server_waypoint/core/network/C2SPacketHandlerTest.java common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandNavigationTest.java common/src/test/java/_959/server_waypoint/command/CommandHarness.java common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java
```

```bash
/usr/bin/git commit -m "Follow the receiver for plain text and end players' messages with a newline"
```

---

### Task 7: Waypoint references and tooltips

Every local screen shows waypoints as `[AB] Name` (spec 2.3): bracketed initials in the waypoint colour that teleport, and a white name that opens details, each with the tooltip of spec 3. Lists show as names with a `Farms · 7 waypoints` tooltip. Plain-text viewers read `Display (identifier)` when the two differ (spec 15). `Fixtures` holds the sample data of the spec, which every screen test and the layout check reuse.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/WaypointRefs.java`
- Create: `common/src/test/java/_959/server_waypoint/text/feedback/Fixtures.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/WaypointRefsTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: `Chat`, `Click`, `Tooltip`, `Viewer`, `DimensionStyle` (Tasks 2, 4); `FormattedTextHelper.parse`, `plainText`; `StringCommandBuilder.tpCmd`, `detailsWaypointCmd`.
- Produces (`final class WaypointRefs`):
  - `Component label(String display, String identifier)`: the parsed display name, or the identifier in quotes when the display text is blank.
  - `Component label(Viewer, String display, String identifier)`: adds ` (identifier)` for plain-text viewers when they differ.
  - `Component reference(DimensionStyle, String dimension, WaypointList list, SimpleWaypoint waypoint)`: `[AB] Name`, the name opening details.
  - `Component title(DimensionStyle, String dimension, WaypointList, SimpleWaypoint, TextColor nameColor)`: `[AB] Name` whose name only shows the tooltip.
  - `Component initials(DimensionStyle, String dimension, WaypointList, SimpleWaypoint)`.
  - `Tooltip waypointTooltip(DimensionStyle, String dimension, SimpleWaypoint, @Nullable String hint)`, `Tooltip teleportTooltip(DimensionStyle, String dimension, SimpleWaypoint)`.
  - `Component where(DimensionStyle, String dimension, WaypointPos)`: `25 m away` or `In Nether`.
  - `@Nullable Component distance(Viewer, String dimension, WaypointPos)`: `25 m`, `1.5 km`, or null outside the viewer's dimension.
  - `@Nullable Component rowDetail(Viewer, String dimension, WaypointPos)`: the gray distance for players, the coordinates for plain-text viewers.
  - `Tooltip listTooltip(WaypointList, @Nullable String hint)`, `Component listLink(DimensionStyle, WaypointList, TextColor, Click, @Nullable String hint)`.
- Test fixtures (`Fixtures`, package `_959.server_waypoint.text.feedback`): `OVERWORLD`, `NETHER`, `END`, `TWILIGHT`, `LOADED`; `player()`, `member()`, `console()`, `in(Viewer, String dimension)`; `dims(Viewer)`; `waypoint(...)`; `homeBases()`, `farms()`, `exploration()`, `overworldLists()`.
- Keys: `wp.teleport.to`, `wp.distance.away`, `wp.distance.metres`, `wp.distance.kilometres`, `wp.where.in`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.teleport.to": "Teleport to {0}",
  "wp.distance.away": "{0} away",
  "wp.distance.metres": "{0} m",
  "wp.distance.kilometres": "{0} km",
  "wp.where.in": "In {0}"
```

`zh_cn.json`:

```json
  "wp.teleport.to": "传送到{0}",
  "wp.distance.away": "距离 {0}",
  "wp.distance.metres": "{0} 米",
  "wp.distance.kilometres": "{0} 公里",
  "wp.where.in": "位于{0}"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.teleport.to": "傳送到{0}",
  "wp.distance.away": "距離 {0}",
  "wp.distance.metres": "{0} 米",
  "wp.distance.kilometres": "{0} 公里",
  "wp.where.in": "位於{0}"
```

`es_es.json`:

```json
  "wp.teleport.to": "Teletransportarse a {0}",
  "wp.distance.away": "A {0}",
  "wp.distance.metres": "{0} m",
  "wp.distance.kilometres": "{0} km",
  "wp.where.in": "En {0}"
```

`he_il.json`:

```json
  "wp.teleport.to": "שיגור אל {0}",
  "wp.distance.away": "במרחק {0}",
  "wp.distance.metres": "{0} מ׳",
  "wp.distance.kilometres": "{0} ק״מ",
  "wp.where.in": "ב-{0}"
```

- [ ] **Step 2: Write the fixtures**

Create `common/src/test/java/_959/server_waypoint/text/feedback/Fixtures.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The sample server of the spec: a player at 100, 64, -20 in the Overworld, facing 37°. */
final class Fixtures {
    static final String OVERWORLD = "minecraft:overworld";
    static final String NETHER = "minecraft:the_nether";
    static final String END = "minecraft:the_end";
    static final String TWILIGHT = "twilightforest:twilight_forest";
    static final Map<String, String> LOADED = Map.of(OVERWORLD, OVERWORLD, NETHER, NETHER, END, END,
            TWILIGHT, "twilightforest:twilight_forest_type");

    private Fixtures() {
    }

    static Viewer player() {
        return new Viewer(Viewer.everything(), true, false, OVERWORLD, new WaypointPos(100, 64, -20), 37F);
    }

    /** A player who may browse and navigate, on a vanilla client. */
    static Viewer member() {
        return new Viewer(Set.of(Viewer.Permission.NAVIGATE), false, false, OVERWORLD, new WaypointPos(100, 64, -20), 37F);
    }

    static Viewer console() {
        return new Viewer(Viewer.everything(), false, true, OVERWORLD, new WaypointPos(0, 64, 0), 0F);
    }

    static Viewer in(Viewer viewer, String dimension) {
        return new Viewer(viewer.permissions(), viewer.hasMod(), viewer.plainText(), dimension, viewer.position(), viewer.yaw());
    }

    static DimensionStyle dims(Viewer viewer) {
        return DimensionStyle.local(viewer, LOADED);
    }

    static SimpleWaypoint waypoint(String name, String initials, int rgb, int x, int y, int z) {
        return new SimpleWaypoint(name, initials, new WaypointPos(x, y, z), rgb, 0, true);
    }

    static WaypointList homeBases() {
        return new WaypointList("Home Bases", 1, List.of(
                new SimpleWaypoint("Main Home", "Main Home", "MH", new WaypointPos(120, 64, -35), 0xFFAA00, 0, true,
                        List.of("home", "base"), "Where the beds are"),
                waypoint("Gem Mine", "GM", 0x55FFFF, -210, 12, 480),
                waypoint("Spawn Village", "SV", 0x55FF55, 0, 70, 0)));
    }

    static WaypointList farms() {
        return new WaypointList("Farms", 1, List.of(
                waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150),
                waypoint("Wheat Fields", "WF", 0xFFFF55, 80, 66, 40),
                waypoint("Cane Farm", "CF", 0x00AA00, 150, 63, -80),
                new SimpleWaypoint("Mob Grinder", "Mob Grinder", "MG", new WaypointPos(-40, 30, -300), 0xFF5555, 0, true,
                        List.of(), "Bring a sword"),
                waypoint("Villager Hall", "VH", 0xAA00AA, 60, 64, 90),
                waypoint("Slime Farm", "SF", 0x55FF55, 410, 20, -260),
                waypoint("Gold Farm", "GF", 0xFFAA00, 520, 120, 610)));
    }

    static WaypointList exploration() {
        return new WaypointList("Exploration", 1, List.of(
                waypoint("Ocean Monument", "OM", 0x5555FF, 1200, 40, -760),
                waypoint("Desert Temple", "DT", 0xFFFF55, -900, 68, 300),
                waypoint("Stronghold", "SH", 0xAA00AA, 2100, 20, -1500),
                waypoint("Woodland Mansion", "WM", 0xAA0000, -3400, 70, -2200)));
    }

    static List<WaypointList> overworldLists() {
        return List.of(homeBases(), farms(), exploration());
    }
}
```

- [ ] **Step 3: Write the failing tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/WaypointRefsTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.find;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointRefsTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final SimpleWaypoint MAIN_HOME = HOME.getWaypointByName("Main Home");

    @Test
    void theInitialsTeleportAndTheNameOpensDetails() {
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals("[MH] Main Home", render(reference));
        assertEquals(TextColor.color(0xFFAA00), colorOf(reference, "[MH]"));
        assertEquals("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(reference, "[MH]"));
        assertEquals(NamedTextColor.WHITE, colorOf(reference, "Main Home"));
        assertEquals("/wp details waypoint minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(reference, "Main Home"));
    }

    @Test
    void tooltipsShowTheDescriptionCoordinatesNetherCoordinatesAndDistance() {
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\n25 m away\nClick for details",
                tooltipOf(reference, "Main Home"));
        assertEquals("Teleport to Main Home\n120, 64, -35\n25 m away", tooltipOf(reference, "[MH]"));
    }

    @Test
    void elsewhereTheTooltipSaysWhichDimensionAndNetherWaypointsShowOverworldCoordinates() {
        DimensionStyle inNether = Fixtures.dims(Fixtures.in(Fixtures.player(), NETHER));
        Component tooltip = WaypointRefs.waypointTooltip(inNether, OVERWORLD, MAIN_HOME, null).build();
        SimpleWaypoint hub = Fixtures.waypoint("Hub", "NH", 0xFF5555, 12, 64, -4);

        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\nIn Overworld", render(tooltip));
        assertEquals(NamedTextColor.GREEN, colorOf(tooltip, "Overworld"));
        assertEquals("Hub\n12, 64, -4\nOverworld 96, 64, -32\n89 m away",
                render(WaypointRefs.waypointTooltip(inNether, NETHER, hub, null).build()));
    }

    @Test
    void withoutTeleportPermissionTheInitialsAreColouredTextWithTheWaypointTooltip() {
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.member()), OVERWORLD, HOME, MAIN_HOME);

        assertNull(clickOf(reference, "[MH]"));
        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\n25 m away", tooltipOf(reference, "[MH]"));
    }

    @Test
    void distancesUseMetresBelowAKilometre() {
        assertEquals("999 m", render(WaypointRefs.distance(Fixtures.player(), OVERWORLD, new WaypointPos(100, 64, 979))));
        assertEquals("1.5 km", render(WaypointRefs.distance(Fixtures.player(), OVERWORLD, new WaypointPos(1600, 64, -20))));
        assertNull(WaypointRefs.distance(Fixtures.player(), NETHER, new WaypointPos(100, 64, -20)));
        assertEquals(NamedTextColor.GRAY, colorOf(WaypointRefs.rowDetail(Fixtures.player(), OVERWORLD, MAIN_HOME.pos()), "25 m"));
        assertEquals("120, 64, -35", render(WaypointRefs.rowDetail(Fixtures.console(), OVERWORLD, MAIN_HOME.pos())));
    }

    @Test
    void formattedDisplayNamesKeepTheirStyleWithoutLeakingIt() {
        SimpleWaypoint gold = new SimpleWaypoint("Gold", "{\"text\":\"Gold\",\"color\":\"gold\",\"italic\":true}", "GF",
                new WaypointPos(0, 64, 0), 0xFFAA00, 0, true, List.of(), "");
        Component line = Chat.concat(WaypointRefs.reference(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, gold), text(" · next"));

        assertEquals("[GF] Gold · next", render(line));
        assertEquals(NamedTextColor.GOLD, colorOf(line, "Gold"));
        assertNotEquals(TextDecoration.State.TRUE, find(line, " · next").style().decoration(TextDecoration.ITALIC));
        assertEquals("[GF] Gold", render(WaypointRefs.reference(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, gold)));
    }

    @Test
    void plainTextViewersReadTheIdentifierWhenTheDisplayNameDiffers() {
        SimpleWaypoint renamed = new SimpleWaypoint("main_home", "Home", "MH", new WaypointPos(0, 64, 0), 0, 0, true, List.of(), "");
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, renamed);

        assertEquals("[MH] Home (main_home)", render(reference));
        assertTrue(runCommands(reference).isEmpty());
    }

    @Test
    void blankNamesShowTheirIdentifierInQuotes() {
        assertEquals("\"\"", render(WaypointRefs.label("", "")));
        assertEquals("\"Gate\"", render(WaypointRefs.label("", "Gate")));
    }

    @Test
    void listsShowTheirSizeAndTheirIdentifierWhenRenamed() {
        WaypointList renamed = new WaypointList("farms", "Farms", 1, Fixtures.farms().simpleWaypoints());
        Component link = WaypointRefs.listLink(Fixtures.dims(Fixtures.player()), renamed, NamedTextColor.WHITE,
                Click.run("/wp list minecraft:overworld farms"), "wp.hint.open");

        assertEquals("Farms", render(link));
        assertEquals("Farms · 7 waypoints\nfarms\nClick to open", tooltipOf(link, "Farms"));
        assertEquals("Farms · 7 waypoints\nClick to open", render(WaypointRefs.listTooltip(Fixtures.farms(), "wp.hint.open").build()));
        assertEquals("Farms (farms)", render(WaypointRefs.listLink(Fixtures.dims(Fixtures.console()), renamed,
                NamedTextColor.WHITE, Click.run("/wp"), null)));
    }
}
```

- [ ] **Step 4: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.WaypointRefsTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable WaypointRefs`.

- [ ] **Step 5: Write `WaypointRefs`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/WaypointRefs.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.text.FormattedTextHelper.parse;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/** How local screens refer to waypoints and lists (spec 2.3, 3). */
public final class WaypointRefs {
    private WaypointRefs() {
    }

    /** A display name as players read it; a blank one shows the identifier in quotes. */
    public static Component label(String display, String identifier) {
        return plainText(display).isBlank() ? text("\"" + identifier + "\"") : parse(display);
    }

    /** The label, followed by " (identifier)" for plain-text viewers when the two differ. */
    public static Component label(Viewer viewer, String display, String identifier) {
        Component label = label(display, identifier);
        if (viewer.plainText() && !plainText(display).equals(identifier)) {
            return Chat.concat(label, text(" (" + identifier + ")"));
        }
        return label;
    }

    /** [AB] Name: the initials teleport and the white name opens details. */
    public static Component reference(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.concat(initials(dims, dimension, list, waypoint), text(" "),
                name(dims, dimension, list, waypoint, NamedTextColor.WHITE, true));
    }

    /** [AB] Name whose name shows its tooltip without a click, for titles and pickers. */
    public static Component title(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                  TextColor nameColor) {
        return Chat.concat(initials(dims, dimension, list, waypoint), text(" "),
                name(dims, dimension, list, waypoint, nameColor, false));
    }

    /** [AB] in the waypoint colour: a teleport link with permission, otherwise coloured text with the waypoint tooltip. */
    public static Component initials(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        Component initials = text("[" + waypoint.initials() + "]");
        TextColor color = TextColor.color(waypoint.rgb());
        if (viewer.can(Viewer.Permission.TP)) {
            return Chat.link(viewer, initials, color, Click.run(tpCmd(dimension, list.name(), waypoint.name())),
                    teleportTooltip(dims, dimension, waypoint));
        }
        return Chat.hover(viewer, Chat.colored(initials, color), waypointTooltip(dims, dimension, waypoint, null));
    }

    private static Component name(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                  TextColor color, boolean details) {
        Viewer viewer = dims.viewer();
        Component label = label(viewer, waypoint.displayName(), waypoint.name());
        if (!details) {
            return Chat.hover(viewer, Chat.colored(label, color), waypointTooltip(dims, dimension, waypoint, null));
        }
        return Chat.link(viewer, label, color, Click.run(detailsWaypointCmd(dimension, list.name(), waypoint.name())),
                waypointTooltip(dims, dimension, waypoint, "wp.hint.details"));
    }

    /** Name, description, coordinates, paired coordinates, distance or dimension, and a hint. */
    public static Tooltip waypointTooltip(DimensionStyle dims, String dimension, SimpleWaypoint waypoint,
                                          @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(label(waypoint.displayName(), waypoint.name()));
        if (!plainText(waypoint.description()).isBlank()) {
            tooltip = tooltip.line(parse(waypoint.description()));
        }
        tooltip = tooltip.line(text(DimensionStyle.coordinates(waypoint.pos()), NamedTextColor.WHITE));
        Component paired = dims.pairedCoordinates(dimension, waypoint.pos());
        if (paired != null) {
            tooltip = tooltip.line(paired);
        }
        tooltip = tooltip.line(where(dims, dimension, waypoint.pos()));
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    public static Tooltip teleportTooltip(DimensionStyle dims, String dimension, SimpleWaypoint waypoint) {
        return Tooltip.of(translatable("wp.teleport.to", label(waypoint.displayName(), waypoint.name())))
                .line(text(DimensionStyle.coordinates(waypoint.pos()), NamedTextColor.WHITE))
                .line(where(dims, dimension, waypoint.pos()));
    }

    /** "25 m away" in the viewer's dimension, otherwise "In Nether" with the dimension in its colour. */
    public static Component where(DimensionStyle dims, String dimension, WaypointPos pos) {
        Component distance = distance(dims.viewer(), dimension, pos);
        return distance != null
                ? translatable("wp.distance.away", distance)
                : translatable("wp.where.in", dims.name(dimension));
    }

    /** "25 m" or "1.5 km" from the viewer, or null outside the viewer's dimension. */
    public static @Nullable Component distance(Viewer viewer, String dimension, WaypointPos pos) {
        WaypointPos origin = viewer.position();
        if (origin == null || !viewer.isIn(dimension)) {
            return null;
        }
        double dx = pos.x() - origin.x();
        double dy = pos.y() - origin.y();
        double dz = pos.z() - origin.z();
        long metres = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        if (metres < 1000) {
            return translatable("wp.distance.metres", text(metres));
        }
        return translatable("wp.distance.kilometres", text(String.format(Locale.ROOT, "%.1f", metres / 1000.0)));
    }

    /** What follows a row's name: the gray distance for players, the coordinates in plain text. */
    public static @Nullable Component rowDetail(Viewer viewer, String dimension, WaypointPos pos) {
        if (viewer.plainText()) {
            return text(DimensionStyle.coordinates(pos));
        }
        Component distance = distance(viewer, dimension, pos);
        return distance == null ? null : Chat.colored(distance, NamedTextColor.GRAY);
    }

    /** Farms · 7 waypoints, then the identifier when the display name differs, then the hint. */
    public static Tooltip listTooltip(WaypointList list, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(Chat.join(label(list.displayName(), list.name()),
                Chat.colored(Chat.count("wp.count.waypoint", list.size()), NamedTextColor.GRAY)));
        if (!plainText(list.displayName()).equals(list.name())) {
            tooltip = tooltip.line(text(list.name()));
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    /** A list name that opens something; plain-text viewers read "Display (identifier)" when they differ. */
    public static Component listLink(DimensionStyle dims, WaypointList list, TextColor color, Click click,
                                     @Nullable String hint) {
        Viewer viewer = dims.viewer();
        return Chat.link(viewer, label(viewer, list.displayName(), list.name()), color, click, listTooltip(list, hint));
    }
}
```

- [ ] **Step 6: Run the tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.*' --tests '_959.server_waypoint.translation.TranslationFilesTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/feedback common/src/test/java/_959/server_waypoint/text/feedback common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add waypoint references with their tooltips and distances"
```

---

### Task 8: The menu and help

`/wp` becomes the menu of spec 5, and `/wp help` the index and topics of spec 12. Plain-text viewers get the help index for `/wp`, since the menu is all links (spec 15). Help topic content stays as data (`HelpTopics`); usages are aqua with `<arguments>` yellow and `[optional parts]` gray, break at argument boundaries to fit 320 px, and explain themselves in tooltips, which plain-text viewers read as indented lines. `WaypointCommandHelp` goes away; until Task 16 gives `/wp remote` its server picker, it shows the remote help topic.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/PlacedWaypoint.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/MenuScreen.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/HelpTopics.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/HelpScreen.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`
- Delete: `common/src/main/java/_959/server_waypoint/command/WaypointCommandHelp.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/MenuScreenTest.java`, `HelpScreenTest.java`; `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`
- Modify (tests of the old menu and help): `CoreWaypointCommandListTest.java`, `CoreWaypointCommandNavigationTest.java`, `RemoteWaypointCommandTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit (Tasks 2–5), `WaypointRefs` (Task 7), `CoreWaypointCommand.viewer` and `dimensions` (Task 6), `CommandHarness` (Task 6).
- Produces:
  - `record PlacedWaypoint(String dimension, WaypointList list, SimpleWaypoint waypoint)` with `Component reference(DimensionStyle)`.
  - `MenuScreen.menu(Viewer, DimensionStyle, @Nullable PlacedWaypoint navigation, boolean remoteAvailable)`.
  - `HelpTopics.Topic` (`LIST, ADD, EDIT, REMOVE, TP, NAVIGATE, UPLOAD, DOWNLOAD, REMOTE`) with `id()`, `label()`, `readableBy(Viewer)`; `record Usage(String syntax, String suggestion, Tooltip tooltip)`, `record Example(String command, String descriptionKey)`, `record Content(List<Usage>, List<Example>)`; `static List<Topic> readable(Viewer)`, `static Content content(Topic, boolean textDisplay)`.
  - `HelpScreen.index(Viewer)`, `HelpScreen.topic(Viewer, HelpTopics.Topic, boolean textDisplay)`, `static List<String> HelpScreen.wrap(String text, String indent, String continuation)`.
  - `CoreWaypointCommand`: `executeMenu(S)`, `helpCommandNode()`, `@Nullable PlacedWaypoint navigationTarget(S)`, `PlacedWaypoint place(NavigationTarget)`.
  - `RemoteWaypointCommand` takes a `Function<S, Component> help` as its last constructor argument.
- Keys: `wp.menu.*`, `wp.all`, `wp.all.tooltip`, `wp.search.everywhere`, `wp.new_list`, `wp.new_list.tooltip`, `wp.navigation.stop`, `wp.navigation.stop.tooltip`, `wp.help.*`. Removed: every `waypoint.menu.` and `waypoint.help.` key.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.menu.title": "Server Waypoint",
  "wp.menu.open_gui": "Open GUI",
  "wp.menu.open_gui.tooltip": "Open the waypoint manager",
  "wp.menu.open_gui.detail": "Shown because your client has Server Waypoint",
  "wp.menu.help": "Help",
  "wp.menu.help.tooltip": "Open help",
  "wp.menu.help.detail": "Commands and examples",
  "wp.menu.reload": "Reload",
  "wp.menu.reload.tooltip": "Reload config and translations",
  "wp.menu.browse": "Browse",
  "wp.menu.this_dimension": "This dimension",
  "wp.menu.this_dimension.tooltip": "Waypoints in {0}",
  "wp.menu.remote": "Remote",
  "wp.menu.remote.tooltip": "Waypoints on your other servers",
  "wp.menu.create": "Create",
  "wp.menu.waypoint_here": "Waypoint here",
  "wp.menu.waypoint_here.tooltip": "Add a waypoint where you stand",
  "wp.menu.waypoint_here.detail": "Choose its list next",
  "wp.menu.list": "List",
  "wp.menu.travel": "Travel",
  "wp.menu.navigation": "Navigation",
  "wp.menu.navigation.tooltip": "Your navigation target and methods",
  "wp.menu.navigation_to": "{0} to {1}",
  "wp.menu.transfer": "Transfer",
  "wp.menu.download": "Download",
  "wp.menu.download.tooltip": "Send every waypoint to your map mod",
  "wp.menu.upload": "Upload",
  "wp.menu.upload.tooltip": "Import from Xaero''s Minimap or VoxelMap",
  "wp.menu.upload.detail": "Choose the source next",
  "wp.all": "All",
  "wp.all.tooltip": "Waypoints in every dimension",
  "wp.search.everywhere": "Search every dimension",
  "wp.new_list": "New list",
  "wp.new_list.tooltip": "Create a list in {0}",
  "wp.navigation.stop": "Stop",
  "wp.navigation.stop.tooltip": "Stop navigating",
  "wp.help.title": "Server Waypoint help",
  "wp.help.menu_hint": "Most things are a click away: {0}.",
  "wp.help.open_menu": "open the menu",
  "wp.help.open_menu.tooltip": "The /wp menu",
  "wp.help.plain_hint": "Run /wp help <topic> for usage and examples.",
  "wp.help.commands": "Commands",
  "wp.help.topic.tooltip": "{0} commands",
  "wp.help.topic.detail": "Usage and examples",
  "wp.help.topic.hint": "hover a line for details, click to use it",
  "wp.help.examples": "Examples",
  "wp.help.index": "Help index",
  "wp.help.index.tooltip": "All topics",
  "wp.help.menu": "Menu",
  "wp.help.topic.list": "List",
  "wp.help.topic.add": "Add",
  "wp.help.topic.edit": "Edit",
  "wp.help.topic.remove": "Remove",
  "wp.help.topic.tp": "Teleport",
  "wp.help.topic.navigate": "Navigate",
  "wp.help.topic.upload": "Upload",
  "wp.help.topic.download": "Download",
  "wp.help.topic.remote": "Remote",
  "wp.help.list.browse": "Browse a dimension or one of its lists",
  "wp.help.list.browse.note": "Without a dimension: the one you are in",
  "wp.help.list.all": "Lists in every dimension",
  "wp.help.list.dimensions": "Every dimension on this server",
  "wp.help.list.options": "Options, in this order, after any of the above",
  "wp.help.list.options.mode": "<mode>: default, name, distance or color",
  "wp.help.list.options.direction": "<direction>: ascending or descending",
  "wp.help.list.options.view": "<view>: lists, tree or flat",
  "wp.help.list.options.number": "<number>: pages start at 1; limits go up to 100",
  "wp.help.list.example.search": "Search every dimension for farm",
  "wp.help.list.example.sort": "Home Bases, sorted by name",
  "wp.help.add.picker": "Pick a list for a waypoint where you stand",
  "wp.help.add.list": "Create an empty list",
  "wp.help.add.list.note": "Quote names with spaces",
  "wp.help.add.here": "Add a waypoint in your dimension",
  "wp.help.add.here.note": "Leave out the rest to have initials, colour and facing picked for you",
  "wp.help.add.color.note": "<color>: a colour name, a hex code such as 39C5BB, or random",
  "wp.help.add.global.note": "<global>: true or false",
  "wp.help.add.elsewhere": "Add a waypoint in any dimension",
  "wp.help.add.example.here": "Add Main Home where you stand",
  "wp.help.add.example.list": "Create the Home Bases list",
  "wp.help.edit.waypoint": "Change one property of a waypoint",
  "wp.help.edit.properties": "<property>: display-name, identifier, initials, position, color, yaw, visibility, keywords, description or icon",
  "wp.help.edit.pickers": "Without a value, color and yaw open a picker",
  "wp.help.edit.clear": "Clear display-name, keywords, description or icon",
  "wp.help.edit.list": "Change a list''s identifier or display-name",
  "wp.help.edit.list.clear": "Clear a list''s display name",
  "wp.help.edit.example": "Make Main Home gold",
  "wp.help.remove.waypoint": "Remove a waypoint; you can restore it for a few minutes",
  "wp.help.remove.list": "Remove an empty list",
  "wp.help.remove.restore": "Put back a removed waypoint",
  "wp.help.remove.restore.note": "The token comes with the Restore link",
  "wp.help.remove.example": "Remove Main Home",
  "wp.help.tp.waypoint": "Teleport to a waypoint",
  "wp.help.tp.example": "Teleport to Main Home",
  "wp.help.navigate.panel": "Your navigation target and methods",
  "wp.help.navigate.start": "Navigate to a waypoint",
  "wp.help.navigate.start.note": "Without methods, a new session uses the defaults and a new target keeps yours",
  "wp.help.navigate.methods": "<method>: compass, map, bossbar or actionbar",
  "wp.help.navigate.methods.text_display": "<method>: compass, map, bossbar, actionbar or text_display",
  "wp.help.navigate.use": "Turn a method on",
  "wp.help.navigate.disable": "Turn a method off, or stop navigating",
  "wp.help.navigate.text_display": "Move, turn or resize the floating label",
  "wp.help.navigate.example": "Navigate to Oak Village with the bossbar",
  "wp.help.upload.panel": "Choose a map mod to upload from",
  "wp.help.upload.run": "Upload waypoints from your map mod",
  "wp.help.upload.source": "<source>: xaero or voxelmap",
  "wp.help.upload.force": "force local overwrites conflicts with yours; delete also removes what your map lacks",
  "wp.help.upload.example": "Merge your Xaero''s Minimap waypoints into the server",
  "wp.help.download.run": "Send waypoints to your map mod",
  "wp.help.download.example": "Send every Overworld waypoint",
  "wp.help.remote.servers": "The connected servers",
  "wp.help.remote.list": "Browse a server, dimension or list",
  "wp.help.remote.list.note": "Takes the same options as /wp list, without distance sorting",
  "wp.help.remote.tp": "Teleport to a waypoint on another server",
  "wp.help.remote.example": "Browse the survival server"
```

`zh_cn.json`:

```json
  "wp.menu.title": "Server Waypoint",
  "wp.menu.open_gui": "打开界面",
  "wp.menu.open_gui.tooltip": "打开路径点管理器",
  "wp.menu.open_gui.detail": "因为你的客户端安装了 Server Waypoint 而显示",
  "wp.menu.help": "帮助",
  "wp.menu.help.tooltip": "打开帮助",
  "wp.menu.help.detail": "命令和示例",
  "wp.menu.reload": "重载",
  "wp.menu.reload.tooltip": "重新加载配置和翻译",
  "wp.menu.browse": "浏览",
  "wp.menu.this_dimension": "当前维度",
  "wp.menu.this_dimension.tooltip": "{0}中的路径点",
  "wp.menu.remote": "远程",
  "wp.menu.remote.tooltip": "你的其他服务器上的路径点",
  "wp.menu.create": "创建",
  "wp.menu.waypoint_here": "在此添加路径点",
  "wp.menu.waypoint_here.tooltip": "在你站立的位置添加路径点",
  "wp.menu.waypoint_here.detail": "接下来选择它的列表",
  "wp.menu.list": "列表",
  "wp.menu.travel": "出行",
  "wp.menu.navigation": "导航",
  "wp.menu.navigation.tooltip": "你的导航目标和方式",
  "wp.menu.navigation_to": "{0}至{1}",
  "wp.menu.transfer": "传输",
  "wp.menu.download": "下载",
  "wp.menu.download.tooltip": "把所有路径点发送到你的地图模组",
  "wp.menu.upload": "上传",
  "wp.menu.upload.tooltip": "从 Xaero''s Minimap 或 VoxelMap 导入",
  "wp.menu.upload.detail": "接下来选择来源",
  "wp.all": "全部",
  "wp.all.tooltip": "所有维度中的路径点",
  "wp.search.everywhere": "搜索所有维度",
  "wp.new_list": "新建列表",
  "wp.new_list.tooltip": "在{0}中创建列表",
  "wp.navigation.stop": "停止",
  "wp.navigation.stop.tooltip": "停止导航",
  "wp.help.title": "Server Waypoint 帮助",
  "wp.help.menu_hint": "大多数功能点一下就能用：{0}。",
  "wp.help.open_menu": "打开菜单",
  "wp.help.open_menu.tooltip": "/wp 菜单",
  "wp.help.plain_hint": "运行 /wp help <topic> 查看用法和示例。",
  "wp.help.commands": "命令",
  "wp.help.topic.tooltip": "{0}命令",
  "wp.help.topic.detail": "用法和示例",
  "wp.help.topic.hint": "悬停查看详情，点击使用",
  "wp.help.examples": "示例",
  "wp.help.index": "帮助目录",
  "wp.help.index.tooltip": "所有主题",
  "wp.help.menu": "菜单",
  "wp.help.topic.list": "列出",
  "wp.help.topic.add": "添加",
  "wp.help.topic.edit": "编辑",
  "wp.help.topic.remove": "删除",
  "wp.help.topic.tp": "传送",
  "wp.help.topic.navigate": "导航",
  "wp.help.topic.upload": "上传",
  "wp.help.topic.download": "下载",
  "wp.help.topic.remote": "远程",
  "wp.help.list.browse": "浏览一个维度或其中的一个列表",
  "wp.help.list.browse.note": "不指定维度时：你所在的维度",
  "wp.help.list.all": "所有维度中的列表",
  "wp.help.list.dimensions": "此服务器上的所有维度",
  "wp.help.list.options": "选项，按此顺序写在以上任一命令之后",
  "wp.help.list.options.mode": "<mode>：default、name、distance 或 color",
  "wp.help.list.options.direction": "<direction>：ascending 或 descending",
  "wp.help.list.options.view": "<view>：lists、tree 或 flat",
  "wp.help.list.options.number": "<number>：页码从 1 开始；每页数量最多 100",
  "wp.help.list.example.search": "在所有维度中搜索 farm",
  "wp.help.list.example.sort": "按名称排序的 Home Bases",
  "wp.help.add.picker": "为你所在位置的路径点选择列表",
  "wp.help.add.list": "创建一个空列表",
  "wp.help.add.list.note": "名称包含空格时请加引号",
  "wp.help.add.here": "在你所在的维度添加路径点",
  "wp.help.add.here.note": "省略其余参数时，缩写、颜色和朝向会自动选择",
  "wp.help.add.color.note": "<color>：颜色名、十六进制颜色（如 39C5BB）或 random",
  "wp.help.add.global.note": "<global>：true 或 false",
  "wp.help.add.elsewhere": "在任意维度添加路径点",
  "wp.help.add.example.here": "在你站立的位置添加 Main Home",
  "wp.help.add.example.list": "创建 Home Bases 列表",
  "wp.help.edit.waypoint": "修改路径点的一项属性",
  "wp.help.edit.properties": "<property>：display-name、identifier、initials、position、color、yaw、visibility、keywords、description 或 icon",
  "wp.help.edit.pickers": "不填值时，color 和 yaw 会打开选择器",
  "wp.help.edit.clear": "清除 display-name、keywords、description 或 icon",
  "wp.help.edit.list": "修改列表的 identifier 或 display-name",
  "wp.help.edit.list.clear": "清除列表的显示名称",
  "wp.help.edit.example": "把 Main Home 设为金色",
  "wp.help.remove.waypoint": "删除路径点；几分钟内可以恢复",
  "wp.help.remove.list": "删除空列表",
  "wp.help.remove.restore": "恢复已删除的路径点",
  "wp.help.remove.restore.note": "令牌附在恢复链接中",
  "wp.help.remove.example": "删除 Main Home",
  "wp.help.tp.waypoint": "传送到路径点",
  "wp.help.tp.example": "传送到 Main Home",
  "wp.help.navigate.panel": "你的导航目标和方式",
  "wp.help.navigate.start": "导航到路径点",
  "wp.help.navigate.start.note": "不指定方式时，新导航使用默认方式，更换目标时保留现有方式",
  "wp.help.navigate.methods": "<method>：compass、map、bossbar 或 actionbar",
  "wp.help.navigate.methods.text_display": "<method>：compass、map、bossbar、actionbar 或 text_display",
  "wp.help.navigate.use": "开启一种方式",
  "wp.help.navigate.disable": "关闭一种方式，或停止导航",
  "wp.help.navigate.text_display": "移动、旋转或缩放浮动标签",
  "wp.help.navigate.example": "用 Boss 栏导航到 Oak Village",
  "wp.help.upload.panel": "选择要上传的地图模组",
  "wp.help.upload.run": "从你的地图模组上传路径点",
  "wp.help.upload.source": "<source>：xaero 或 voxelmap",
  "wp.help.upload.force": "force local 用你的数据覆盖冲突；delete 还会删除你的地图中没有的路径点",
  "wp.help.upload.example": "把你的 Xaero''s Minimap 路径点合并到服务器",
  "wp.help.download.run": "把路径点发送到你的地图模组",
  "wp.help.download.example": "发送主世界的所有路径点",
  "wp.help.remote.servers": "已连接的服务器",
  "wp.help.remote.list": "浏览服务器、维度或列表",
  "wp.help.remote.list.note": "选项与 /wp list 相同，但不能按距离排序",
  "wp.help.remote.tp": "传送到另一台服务器上的路径点",
  "wp.help.remote.example": "浏览 survival 服务器"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.menu.title": "Server Waypoint",
  "wp.menu.open_gui": "開啟介面",
  "wp.menu.open_gui.tooltip": "開啟路徑點管理器",
  "wp.menu.open_gui.detail": "因為你的用戶端安裝了 Server Waypoint 而顯示",
  "wp.menu.help": "說明",
  "wp.menu.help.tooltip": "開啟說明",
  "wp.menu.help.detail": "指令和範例",
  "wp.menu.reload": "重新載入",
  "wp.menu.reload.tooltip": "重新載入設定和翻譯",
  "wp.menu.browse": "瀏覽",
  "wp.menu.this_dimension": "目前維度",
  "wp.menu.this_dimension.tooltip": "{0}中的路徑點",
  "wp.menu.remote": "遠端",
  "wp.menu.remote.tooltip": "你的其他伺服器上的路徑點",
  "wp.menu.create": "建立",
  "wp.menu.waypoint_here": "在此新增路徑點",
  "wp.menu.waypoint_here.tooltip": "在你站立的位置新增路徑點",
  "wp.menu.waypoint_here.detail": "接下來選擇它的列表",
  "wp.menu.list": "列表",
  "wp.menu.travel": "出行",
  "wp.menu.navigation": "導航",
  "wp.menu.navigation.tooltip": "你的導航目標和方式",
  "wp.menu.navigation_to": "{0}至{1}",
  "wp.menu.transfer": "傳輸",
  "wp.menu.download": "下載",
  "wp.menu.download.tooltip": "把所有路徑點傳送到你的地圖模組",
  "wp.menu.upload": "上傳",
  "wp.menu.upload.tooltip": "從 Xaero''s Minimap 或 VoxelMap 匯入",
  "wp.menu.upload.detail": "接下來選擇來源",
  "wp.all": "全部",
  "wp.all.tooltip": "所有維度中的路徑點",
  "wp.search.everywhere": "搜尋所有維度",
  "wp.new_list": "新增列表",
  "wp.new_list.tooltip": "在{0}中建立列表",
  "wp.navigation.stop": "停止",
  "wp.navigation.stop.tooltip": "停止導航",
  "wp.help.title": "Server Waypoint 說明",
  "wp.help.menu_hint": "大多數功能點一下就能用：{0}。",
  "wp.help.open_menu": "開啟選單",
  "wp.help.open_menu.tooltip": "/wp 選單",
  "wp.help.plain_hint": "執行 /wp help <topic> 查看用法和範例。",
  "wp.help.commands": "指令",
  "wp.help.topic.tooltip": "{0}指令",
  "wp.help.topic.detail": "用法和範例",
  "wp.help.topic.hint": "懸停查看詳情，點擊使用",
  "wp.help.examples": "範例",
  "wp.help.index": "說明目錄",
  "wp.help.index.tooltip": "所有主題",
  "wp.help.menu": "選單",
  "wp.help.topic.list": "列出",
  "wp.help.topic.add": "新增",
  "wp.help.topic.edit": "編輯",
  "wp.help.topic.remove": "刪除",
  "wp.help.topic.tp": "傳送",
  "wp.help.topic.navigate": "導航",
  "wp.help.topic.upload": "上傳",
  "wp.help.topic.download": "下載",
  "wp.help.topic.remote": "遠端",
  "wp.help.list.browse": "瀏覽一個維度或其中的一個列表",
  "wp.help.list.browse.note": "不指定維度時：你所在的維度",
  "wp.help.list.all": "所有維度中的列表",
  "wp.help.list.dimensions": "此伺服器上的所有維度",
  "wp.help.list.options": "選項，按此順序寫在以上任一指令之後",
  "wp.help.list.options.mode": "<mode>：default、name、distance 或 color",
  "wp.help.list.options.direction": "<direction>：ascending 或 descending",
  "wp.help.list.options.view": "<view>：lists、tree 或 flat",
  "wp.help.list.options.number": "<number>：頁碼從 1 開始；每頁數量最多 100",
  "wp.help.list.example.search": "在所有維度中搜尋 farm",
  "wp.help.list.example.sort": "按名稱排序的 Home Bases",
  "wp.help.add.picker": "為你所在位置的路徑點選擇列表",
  "wp.help.add.list": "建立一個空列表",
  "wp.help.add.list.note": "名稱包含空格時請加引號",
  "wp.help.add.here": "在你所在的維度新增路徑點",
  "wp.help.add.here.note": "省略其餘參數時，縮寫、顏色和朝向會自動選擇",
  "wp.help.add.color.note": "<color>：顏色名稱、十六進位色碼（如 39C5BB）或 random",
  "wp.help.add.global.note": "<global>：true 或 false",
  "wp.help.add.elsewhere": "在任意維度新增路徑點",
  "wp.help.add.example.here": "在你站立的位置新增 Main Home",
  "wp.help.add.example.list": "建立 Home Bases 列表",
  "wp.help.edit.waypoint": "修改路徑點的一項屬性",
  "wp.help.edit.properties": "<property>：display-name、identifier、initials、position、color、yaw、visibility、keywords、description 或 icon",
  "wp.help.edit.pickers": "不填值時，color 和 yaw 會開啟選擇器",
  "wp.help.edit.clear": "清除 display-name、keywords、description 或 icon",
  "wp.help.edit.list": "修改列表的 identifier 或 display-name",
  "wp.help.edit.list.clear": "清除列表的顯示名稱",
  "wp.help.edit.example": "把 Main Home 設為金色",
  "wp.help.remove.waypoint": "刪除路徑點；幾分鐘內可以還原",
  "wp.help.remove.list": "刪除空列表",
  "wp.help.remove.restore": "還原已刪除的路徑點",
  "wp.help.remove.restore.note": "代碼附在還原連結中",
  "wp.help.remove.example": "刪除 Main Home",
  "wp.help.tp.waypoint": "傳送到路徑點",
  "wp.help.tp.example": "傳送到 Main Home",
  "wp.help.navigate.panel": "你的導航目標和方式",
  "wp.help.navigate.start": "導航到路徑點",
  "wp.help.navigate.start.note": "不指定方式時，新導航使用預設方式，更換目標時保留現有方式",
  "wp.help.navigate.methods": "<method>：compass、map、bossbar 或 actionbar",
  "wp.help.navigate.methods.text_display": "<method>：compass、map、bossbar、actionbar 或 text_display",
  "wp.help.navigate.use": "開啟一種方式",
  "wp.help.navigate.disable": "關閉一種方式，或停止導航",
  "wp.help.navigate.text_display": "移動、旋轉或縮放浮動標籤",
  "wp.help.navigate.example": "用 Boss 欄導航到 Oak Village",
  "wp.help.upload.panel": "選擇要上傳的地圖模組",
  "wp.help.upload.run": "從你的地圖模組上傳路徑點",
  "wp.help.upload.source": "<source>：xaero 或 voxelmap",
  "wp.help.upload.force": "force local 用你的資料覆蓋衝突；delete 還會刪除你的地圖中沒有的路徑點",
  "wp.help.upload.example": "把你的 Xaero''s Minimap 路徑點合併到伺服器",
  "wp.help.download.run": "把路徑點傳送到你的地圖模組",
  "wp.help.download.example": "傳送主世界的所有路徑點",
  "wp.help.remote.servers": "已連線的伺服器",
  "wp.help.remote.list": "瀏覽伺服器、維度或列表",
  "wp.help.remote.list.note": "選項與 /wp list 相同，但不能按距離排序",
  "wp.help.remote.tp": "傳送到另一台伺服器上的路徑點",
  "wp.help.remote.example": "瀏覽 survival 伺服器"
```

`es_es.json`:

```json
  "wp.menu.title": "Server Waypoint",
  "wp.menu.open_gui": "Abrir interfaz",
  "wp.menu.open_gui.tooltip": "Abre el gestor de puntos de ruta",
  "wp.menu.open_gui.detail": "Aparece porque tu cliente tiene Server Waypoint",
  "wp.menu.help": "Ayuda",
  "wp.menu.help.tooltip": "Abrir la ayuda",
  "wp.menu.help.detail": "Comandos y ejemplos",
  "wp.menu.reload": "Recargar",
  "wp.menu.reload.tooltip": "Recarga la configuración y las traducciones",
  "wp.menu.browse": "Explorar",
  "wp.menu.this_dimension": "Esta dimensión",
  "wp.menu.this_dimension.tooltip": "Puntos de ruta en {0}",
  "wp.menu.remote": "Remotos",
  "wp.menu.remote.tooltip": "Puntos de ruta de tus otros servidores",
  "wp.menu.create": "Crear",
  "wp.menu.waypoint_here": "Punto aquí",
  "wp.menu.waypoint_here.tooltip": "Añade un punto de ruta donde estás",
  "wp.menu.waypoint_here.detail": "Después eliges su lista",
  "wp.menu.list": "Lista",
  "wp.menu.travel": "Viajar",
  "wp.menu.navigation": "Navegación",
  "wp.menu.navigation.tooltip": "Tu destino y tus métodos de navegación",
  "wp.menu.navigation_to": "{0} a {1}",
  "wp.menu.transfer": "Transferir",
  "wp.menu.download": "Descargar",
  "wp.menu.download.tooltip": "Envía todos los puntos de ruta a tu mod de mapa",
  "wp.menu.upload": "Subir",
  "wp.menu.upload.tooltip": "Importa desde Xaero''s Minimap o VoxelMap",
  "wp.menu.upload.detail": "Después eliges el origen",
  "wp.all": "Todo",
  "wp.all.tooltip": "Puntos de ruta de todas las dimensiones",
  "wp.search.everywhere": "Buscar en todas las dimensiones",
  "wp.new_list": "Nueva lista",
  "wp.new_list.tooltip": "Crea una lista en {0}",
  "wp.navigation.stop": "Detener",
  "wp.navigation.stop.tooltip": "Deja de navegar",
  "wp.help.title": "Ayuda de Server Waypoint",
  "wp.help.menu_hint": "Casi todo está a un clic: {0}.",
  "wp.help.open_menu": "abre el menú",
  "wp.help.open_menu.tooltip": "El menú de /wp",
  "wp.help.plain_hint": "Ejecuta /wp help <topic> para ver el uso y ejemplos.",
  "wp.help.commands": "Comandos",
  "wp.help.topic.tooltip": "Comandos de {0}",
  "wp.help.topic.detail": "Uso y ejemplos",
  "wp.help.topic.hint": "pasa el ratón por una línea para ver detalles, haz clic para usarla",
  "wp.help.examples": "Ejemplos",
  "wp.help.index": "Índice de ayuda",
  "wp.help.index.tooltip": "Todos los temas",
  "wp.help.menu": "Menú",
  "wp.help.topic.list": "Listar",
  "wp.help.topic.add": "Añadir",
  "wp.help.topic.edit": "Editar",
  "wp.help.topic.remove": "Quitar",
  "wp.help.topic.tp": "Teletransporte",
  "wp.help.topic.navigate": "Navegar",
  "wp.help.topic.upload": "Subir",
  "wp.help.topic.download": "Descargar",
  "wp.help.topic.remote": "Remotos",
  "wp.help.list.browse": "Explora una dimensión o una de sus listas",
  "wp.help.list.browse.note": "Sin dimensión: aquella en la que estás",
  "wp.help.list.all": "Listas de todas las dimensiones",
  "wp.help.list.dimensions": "Todas las dimensiones de este servidor",
  "wp.help.list.options": "Opciones, en este orden, tras cualquiera de los anteriores",
  "wp.help.list.options.mode": "<mode>: default, name, distance o color",
  "wp.help.list.options.direction": "<direction>: ascending o descending",
  "wp.help.list.options.view": "<view>: lists, tree o flat",
  "wp.help.list.options.number": "<number>: las páginas empiezan en 1; el límite llega a 100",
  "wp.help.list.example.search": "Busca farm en todas las dimensiones",
  "wp.help.list.example.sort": "Home Bases, ordenada por nombre",
  "wp.help.add.picker": "Elige una lista para un punto de ruta donde estás",
  "wp.help.add.list": "Crea una lista vacía",
  "wp.help.add.list.note": "Pon entre comillas los nombres con espacios",
  "wp.help.add.here": "Añade un punto de ruta en tu dimensión",
  "wp.help.add.here.note": "Si omites el resto, se eligen las iniciales, el color y la orientación por ti",
  "wp.help.add.color.note": "<color>: un nombre de color, un código hexadecimal como 39C5BB o random",
  "wp.help.add.global.note": "<global>: true o false",
  "wp.help.add.elsewhere": "Añade un punto de ruta en cualquier dimensión",
  "wp.help.add.example.here": "Añade Main Home donde estás",
  "wp.help.add.example.list": "Crea la lista Home Bases",
  "wp.help.edit.waypoint": "Cambia una propiedad de un punto de ruta",
  "wp.help.edit.properties": "<property>: display-name, identifier, initials, position, color, yaw, visibility, keywords, description o icon",
  "wp.help.edit.pickers": "Sin valor, color y yaw abren un selector",
  "wp.help.edit.clear": "Borra display-name, keywords, description o icon",
  "wp.help.edit.list": "Cambia el identifier o el display-name de una lista",
  "wp.help.edit.list.clear": "Borra el nombre visible de una lista",
  "wp.help.edit.example": "Pon Main Home en dorado",
  "wp.help.remove.waypoint": "Quita un punto de ruta; puedes restaurarlo durante unos minutos",
  "wp.help.remove.list": "Quita una lista vacía",
  "wp.help.remove.restore": "Recupera un punto de ruta quitado",
  "wp.help.remove.restore.note": "El código viene con el enlace Restaurar",
  "wp.help.remove.example": "Quita Main Home",
  "wp.help.tp.waypoint": "Teletranspórtate a un punto de ruta",
  "wp.help.tp.example": "Teletranspórtate a Main Home",
  "wp.help.navigate.panel": "Tu destino y tus métodos de navegación",
  "wp.help.navigate.start": "Navega hasta un punto de ruta",
  "wp.help.navigate.start.note": "Sin métodos, una sesión nueva usa los predeterminados y un destino nuevo conserva los tuyos",
  "wp.help.navigate.methods": "<method>: compass, map, bossbar o actionbar",
  "wp.help.navigate.methods.text_display": "<method>: compass, map, bossbar, actionbar o text_display",
  "wp.help.navigate.use": "Activa un método",
  "wp.help.navigate.disable": "Desactiva un método o deja de navegar",
  "wp.help.navigate.text_display": "Mueve, gira o cambia el tamaño de la etiqueta flotante",
  "wp.help.navigate.example": "Navega hasta Oak Village con la barra de jefe",
  "wp.help.upload.panel": "Elige el mod de mapa desde el que subir",
  "wp.help.upload.run": "Sube puntos de ruta desde tu mod de mapa",
  "wp.help.upload.source": "<source>: xaero o voxelmap",
  "wp.help.upload.force": "force local sobrescribe los conflictos con los tuyos; delete además quita lo que tu mapa no tiene",
  "wp.help.upload.example": "Combina tus puntos de Xaero''s Minimap con el servidor",
  "wp.help.download.run": "Envía puntos de ruta a tu mod de mapa",
  "wp.help.download.example": "Envía todos los puntos de ruta del mundo principal",
  "wp.help.remote.servers": "Los servidores conectados",
  "wp.help.remote.list": "Explora un servidor, una dimensión o una lista",
  "wp.help.remote.list.note": "Admite las mismas opciones que /wp list, sin ordenar por distancia",
  "wp.help.remote.tp": "Teletranspórtate a un punto de ruta de otro servidor",
  "wp.help.remote.example": "Explora el servidor survival"
```

`he_il.json`:

```json
  "wp.menu.title": "Server Waypoint",
  "wp.menu.open_gui": "פתיחת הממשק",
  "wp.menu.open_gui.tooltip": "פתיחת מנהל נקודות הציון",
  "wp.menu.open_gui.detail": "מוצג כי בלקוח שלכם מותקן Server Waypoint",
  "wp.menu.help": "עזרה",
  "wp.menu.help.tooltip": "פתיחת העזרה",
  "wp.menu.help.detail": "פקודות ודוגמאות",
  "wp.menu.reload": "טעינה מחדש",
  "wp.menu.reload.tooltip": "טעינה מחדש של ההגדרות והתרגומים",
  "wp.menu.browse": "עיון",
  "wp.menu.this_dimension": "הממד הזה",
  "wp.menu.this_dimension.tooltip": "נקודות ציון ב-{0}",
  "wp.menu.remote": "מרוחק",
  "wp.menu.remote.tooltip": "נקודות ציון בשרתים האחרים שלכם",
  "wp.menu.create": "יצירה",
  "wp.menu.waypoint_here": "נקודה כאן",
  "wp.menu.waypoint_here.tooltip": "הוספת נקודת ציון במקום שבו אתם עומדים",
  "wp.menu.waypoint_here.detail": "בשלב הבא בוחרים את הרשימה",
  "wp.menu.list": "רשימה",
  "wp.menu.travel": "מסע",
  "wp.menu.navigation": "ניווט",
  "wp.menu.navigation.tooltip": "יעד הניווט ושיטות הניווט שלכם",
  "wp.menu.navigation_to": "{0} אל {1}",
  "wp.menu.transfer": "העברה",
  "wp.menu.download": "הורדה",
  "wp.menu.download.tooltip": "שליחת כל נקודות הציון למוד המפה שלכם",
  "wp.menu.upload": "העלאה",
  "wp.menu.upload.tooltip": "ייבוא מ-Xaero''s Minimap או מ-VoxelMap",
  "wp.menu.upload.detail": "בשלב הבא בוחרים את המקור",
  "wp.all": "הכול",
  "wp.all.tooltip": "נקודות ציון בכל הממדים",
  "wp.search.everywhere": "חיפוש בכל הממדים",
  "wp.new_list": "רשימה חדשה",
  "wp.new_list.tooltip": "יצירת רשימה ב-{0}",
  "wp.navigation.stop": "עצירה",
  "wp.navigation.stop.tooltip": "הפסקת הניווט",
  "wp.help.title": "עזרה ל-Server Waypoint",
  "wp.help.menu_hint": "רוב הדברים במרחק לחיצה: {0}.",
  "wp.help.open_menu": "פתחו את התפריט",
  "wp.help.open_menu.tooltip": "התפריט של /wp",
  "wp.help.plain_hint": "הריצו /wp help <topic> לשימוש ולדוגמאות.",
  "wp.help.commands": "פקודות",
  "wp.help.topic.tooltip": "פקודות {0}",
  "wp.help.topic.detail": "שימוש ודוגמאות",
  "wp.help.topic.hint": "רחפו מעל שורה לפרטים, לחצו כדי להשתמש בה",
  "wp.help.examples": "דוגמאות",
  "wp.help.index": "מפתח העזרה",
  "wp.help.index.tooltip": "כל הנושאים",
  "wp.help.menu": "תפריט",
  "wp.help.topic.list": "רשימה",
  "wp.help.topic.add": "הוספה",
  "wp.help.topic.edit": "עריכה",
  "wp.help.topic.remove": "הסרה",
  "wp.help.topic.tp": "שיגור",
  "wp.help.topic.navigate": "ניווט",
  "wp.help.topic.upload": "העלאה",
  "wp.help.topic.download": "הורדה",
  "wp.help.topic.remote": "מרוחק",
  "wp.help.list.browse": "עיון בממד או באחת מהרשימות שבו",
  "wp.help.list.browse.note": "בלי ממד: הממד שבו אתם נמצאים",
  "wp.help.list.all": "רשימות בכל הממדים",
  "wp.help.list.dimensions": "כל הממדים בשרת הזה",
  "wp.help.list.options": "אפשרויות, בסדר הזה, אחרי כל אחת מהפקודות שלמעלה",
  "wp.help.list.options.mode": "<mode>: default, name, distance או color",
  "wp.help.list.options.direction": "<direction>: ascending או descending",
  "wp.help.list.options.view": "<view>: lists, tree או flat",
  "wp.help.list.options.number": "<number>: העמודים מתחילים ב-1; המגבלה עד 100",
  "wp.help.list.example.search": "חיפוש farm בכל הממדים",
  "wp.help.list.example.sort": "Home Bases, ממוינת לפי שם",
  "wp.help.add.picker": "בחירת רשימה לנקודת ציון במקום שבו אתם עומדים",
  "wp.help.add.list": "יצירת רשימה ריקה",
  "wp.help.add.list.note": "שמות עם רווחים יש להקיף במירכאות",
  "wp.help.add.here": "הוספת נקודת ציון בממד שלכם",
  "wp.help.add.here.note": "השמיטו את השאר והראשי תיבות, הצבע והכיוון ייבחרו בשבילכם",
  "wp.help.add.color.note": "<color>: שם צבע, קוד הקסדצימלי כמו 39C5BB, או random",
  "wp.help.add.global.note": "<global>: true או false",
  "wp.help.add.elsewhere": "הוספת נקודת ציון בכל ממד",
  "wp.help.add.example.here": "הוספת Main Home במקום שבו אתם עומדים",
  "wp.help.add.example.list": "יצירת הרשימה Home Bases",
  "wp.help.edit.waypoint": "שינוי מאפיין אחד של נקודת ציון",
  "wp.help.edit.properties": "<property>: display-name, identifier, initials, position, color, yaw, visibility, keywords, description או icon",
  "wp.help.edit.pickers": "בלי ערך, color ו-yaw פותחים בורר",
  "wp.help.edit.clear": "ניקוי display-name, keywords, description או icon",
  "wp.help.edit.list": "שינוי ה-identifier או ה-display-name של רשימה",
  "wp.help.edit.list.clear": "ניקוי שם התצוגה של רשימה",
  "wp.help.edit.example": "צביעת Main Home בזהב",
  "wp.help.remove.waypoint": "הסרת נקודת ציון; אפשר לשחזר אותה במשך כמה דקות",
  "wp.help.remove.list": "הסרת רשימה ריקה",
  "wp.help.remove.restore": "החזרת נקודת ציון שהוסרה",
  "wp.help.remove.restore.note": "הקוד מגיע עם הקישור לשחזור",
  "wp.help.remove.example": "הסרת Main Home",
  "wp.help.tp.waypoint": "שיגור אל נקודת ציון",
  "wp.help.tp.example": "שיגור אל Main Home",
  "wp.help.navigate.panel": "יעד הניווט ושיטות הניווט שלכם",
  "wp.help.navigate.start": "ניווט אל נקודת ציון",
  "wp.help.navigate.start.note": "בלי שיטות, ניווט חדש משתמש בברירות המחדל ויעד חדש שומר את השיטות שלכם",
  "wp.help.navigate.methods": "<method>: compass, map, bossbar או actionbar",
  "wp.help.navigate.methods.text_display": "<method>: compass, map, bossbar, actionbar או text_display",
  "wp.help.navigate.use": "הפעלת שיטה",
  "wp.help.navigate.disable": "כיבוי שיטה, או הפסקת הניווט",
  "wp.help.navigate.text_display": "הזזה, סיבוב או שינוי גודל של התווית הצפה",
  "wp.help.navigate.example": "ניווט אל Oak Village עם סרגל הבוס",
  "wp.help.upload.panel": "בחירת מוד המפה שממנו מעלים",
  "wp.help.upload.run": "העלאת נקודות ציון ממוד המפה שלכם",
  "wp.help.upload.source": "<source>: xaero או voxelmap",
  "wp.help.upload.force": "force local דורס התנגשויות עם הנתונים שלכם; delete גם מסיר את מה שאין במפה שלכם",
  "wp.help.upload.example": "מיזוג נקודות הציון שלכם מ-Xaero''s Minimap לשרת",
  "wp.help.download.run": "שליחת נקודות ציון למוד המפה שלכם",
  "wp.help.download.example": "שליחת כל נקודות הציון בעולם העליון",
  "wp.help.remote.servers": "השרתים המחוברים",
  "wp.help.remote.list": "עיון בשרת, בממד או ברשימה",
  "wp.help.remote.list.note": "מקבל את אותן אפשרויות כמו /wp list, בלי מיון לפי מרחק",
  "wp.help.remote.tp": "שיגור אל נקודת ציון בשרת אחר",
  "wp.help.remote.example": "עיון בשרת survival"
```

Then retire the old menu and help keys:

```bash
python3 SCRATCH/remove_keys.py waypoint.menu. waypoint.help.
```

Expected: `ok` for all six locales.

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/MenuScreenTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.ChatAssert;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuScreenTest {
    private static PlacedWaypoint mainHome() {
        var list = Fixtures.homeBases();
        return new PlacedWaypoint(OVERWORLD, list, list.getWaypointByName("Main Home"));
    }

    @Test
    void everythingAPlayerCanReachIsOneClickAway() {
        Component menu = MenuScreen.menu(Fixtures.player(), Fixtures.dims(Fixtures.player()), mainHome(), true);

        assertEquals(List.of(
                "Server Waypoint   Open GUI · Help · Reload",
                "Browse",
                "  This dimension · All · Remote · Search",
                "Create",
                "  Waypoint here · List",
                "Travel",
                "  Navigation to [MH] Main Home · Stop",
                "Transfer",
                "  Download · Upload"), lines(menu));
        assertEquals("/wp_gui", clickOf(menu, "Open GUI"));
        assertEquals("/wp help", clickOf(menu, "Help"));
        assertEquals("/wp reload", clickOf(menu, "Reload"));
        assertEquals("/wp list", clickOf(menu, "This dimension"));
        assertEquals("/wp list all", clickOf(menu, "All"));
        assertEquals("/wp remote", clickOf(menu, "Remote"));
        assertEquals("/wp list all search ", clickOf(menu, "Search"));
        assertEquals("/wp add", clickOf(menu, "Waypoint here"));
        assertEquals("/wp add minecraft:overworld ", clickOf(menu, "List"));
        assertEquals("/wp navigate", clickOf(menu, "Navigation"));
        assertEquals("/wp navigate disable", clickOf(menu, "Stop"));
        assertEquals("/wp download", clickOf(menu, "Download"));
        assertEquals("/wp upload", clickOf(menu, "Upload"));
        ChatAssert.assertFitsChat(menu);
    }

    @Test
    void colorsFollowTheVisualLanguage() {
        Component menu = MenuScreen.menu(Fixtures.player(), Fixtures.dims(Fixtures.player()), mainHome(), true);

        assertEquals(NamedTextColor.GOLD, colorOf(menu, "Server Waypoint"));
        assertEquals(NamedTextColor.AQUA, colorOf(menu, "Open GUI"));
        assertEquals(NamedTextColor.GRAY, colorOf(menu, "Help"));
        assertEquals(NamedTextColor.GRAY, colorOf(menu, "Browse"));
        assertEquals(NamedTextColor.GREEN, colorOf(menu, "Waypoint here"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, colorOf(menu, "Navigation"));
        assertEquals(NamedTextColor.GRAY, colorOf(menu, " to "));
        assertEquals(NamedTextColor.RED, colorOf(menu, "Stop"));
        assertEquals("Reload config and translations\nPress Enter to confirm", ChatAssert.tooltipOf(menu, "Reload"));
        assertEquals("Waypoints in Overworld", ChatAssert.tooltipOf(menu, "This dimension"));
    }

    @Test
    void controlsTheViewerCannotUseAreLeftOut() {
        Component menu = MenuScreen.menu(Fixtures.member(), Fixtures.dims(Fixtures.member()), null, false);

        assertEquals(List.of(
                "Server Waypoint   Help",
                "Browse",
                "  This dimension · All · Search",
                "Travel",
                "  Navigation"), lines(menu));
    }
}
```

Create `common/src/test/java/_959/server_waypoint/text/feedback/HelpScreenTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ChatFont;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpScreenTest {
    @Test
    void theIndexLinksTheMenuAndEveryTopicTheViewerCanUse() {
        Component index = HelpScreen.index(Fixtures.player());

        assertEquals(List.of(
                "Server Waypoint help",
                "Most things are a click away: open the menu.",
                "Commands  List · Add · Edit · Remove · Teleport",
                "  Navigate · Upload · Download · Remote"), lines(index));
        assertEquals("/wp", clickOf(index, "open the menu"));
        assertEquals(List.of("/wp", "/wp help list", "/wp help add", "/wp help edit", "/wp help remove", "/wp help tp",
                "/wp help navigate", "/wp help upload", "/wp help download", "/wp help remote"), runCommands(index));
        assertEquals("Add commands\nUsage and examples", tooltipOf(index, "Add"));
        assertEquals("Commands  List · Navigate · Download", lines(HelpScreen.index(Fixtures.member())).get(2));
    }

    @Test
    void plainTextViewersGetTheTopicIdsToType() {
        assertEquals(List.of(
                "Server Waypoint help",
                "Run /wp help <topic> for usage and examples.",
                "Commands  list · add · edit · remove · tp · navigate · upload · download · remote"),
                lines(HelpScreen.index(Fixtures.console())));
    }

    @Test
    void usagesAreColouredBrokenToFitAndExplainedInTheirTooltips() {
        Component add = HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.ADD, false);
        List<String> lines = lines(add);

        assertEquals("Add  hover a line for details, click to use it", lines.get(0));
        assertEquals("/wp add", lines.get(1));
        assertEquals("/wp add <dimension> <list>", lines.get(2));
        assertTrue(lines.get(4).startsWith("    "), lines.get(4));
        assertEquals("Help index · Menu", lines.get(lines.size() - 1));
        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<dimension>"));
        assertEquals(NamedTextColor.GRAY, colorOf(add, "[<initials>"));
        assertEquals(NamedTextColor.YELLOW, colorOf(add, "<initials>"));
        assertEquals(NamedTextColor.AQUA, colorOf(add, "/wp add <position>"));
        assertTrue(suggestions(add).contains("/wp add ~ ~ ~ "));
        assertEquals("Create an empty list\nQuote names with spaces\nClick to fill it in", tooltipOf(add, "/wp add <dimension> <list>"));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" \"Main Home\"", clickOf(add, "  /wp add ~ ~ ~ \"Home Bases\" \"Main Home\""));
        ChatAssert.assertFitsChat(add);
    }

    @Test
    void everyTopicFitsTheChat() {
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            ChatAssert.assertFitsChat(HelpScreen.topic(Fixtures.player(), topic, true));
        }
    }

    @Test
    void plainTextTopicsPrintTheirTooltipsAsIndentedLines() {
        List<String> lines = lines(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.ADD, false));

        assertEquals("Add", lines.get(0));
        assertTrue(lines.contains("/wp add <dimension> <list>"));
        assertTrue(lines.contains("  Create an empty list"));
        assertTrue(lines.contains("  Quote names with spaces"));
        assertTrue(lines.contains("  /wp add ~ ~ ~ \"Home Bases\" \"Main Home\""));
        assertTrue(lines.contains("    Add Main Home where you stand"));
        assertFalse(render(HelpScreen.topic(Fixtures.console(), HelpTopics.Topic.ADD, false)).contains("Help index"));
    }

    @Test
    void navigateHelpMentionsTheTextDisplayOnlyWhereItIsSupported() {
        assertFalse(render(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, false)).contains("text_display"));
        assertTrue(render(HelpScreen.topic(Fixtures.player(), HelpTopics.Topic.NAVIGATE, true)).contains("/wp navigate config text_display"));
    }

    @Test
    void wrappingBreaksAtSpacesAndIndentsContinuations() {
        List<String> wrapped = HelpScreen.wrap("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]", "", "    ");

        assertTrue(wrapped.size() > 1);
        assertTrue(wrapped.stream().allMatch(line -> ChatFont.width(line) <= ChatFont.CHAT_WIDTH));
        assertTrue(wrapped.get(1).startsWith("    "));
        assertEquals("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]",
                String.join(" ", wrapped.stream().map(String::trim).toList()));
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.MenuScreenTest' --tests '_959.server_waypoint.text.feedback.HelpScreenTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class PlacedWaypoint`.

- [ ] **Step 4: Write `PlacedWaypoint` and `MenuScreen`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/PlacedWaypoint.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;

/** A waypoint together with the dimension and list it belongs to. */
public record PlacedWaypoint(String dimension, WaypointList list, SimpleWaypoint waypoint) {
    public Component reference(DimensionStyle dims) {
        return WaypointRefs.reference(dims, this.dimension, this.list, this.waypoint);
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/feedback/MenuScreen.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

/** The /wp menu (spec 5). Groups the viewer has nothing in are left out. */
public final class MenuScreen {
    private MenuScreen() {
    }

    public static Component menu(Viewer viewer, DimensionStyle dims, @Nullable PlacedWaypoint navigation,
                                 boolean remoteAvailable) {
        String here = Objects.requireNonNullElse(viewer.dimension(), "minecraft:overworld");
        ChatLines lines = new ChatLines();
        List<Component> extras = new ArrayList<>();
        if (viewer.hasMod()) {
            extras.add(Chat.link(viewer, translatable("wp.menu.open_gui"), AQUA, Click.run("/wp_gui"),
                    Tooltip.of("wp.menu.open_gui.tooltip").line("wp.menu.open_gui.detail")));
        }
        extras.add(Chat.link(viewer, translatable("wp.menu.help"), GRAY, Click.run("/wp help"),
                Tooltip.of("wp.menu.help.tooltip").line("wp.menu.help.detail")));
        if (viewer.can(Viewer.Permission.RELOAD)) {
            extras.add(Chat.link(viewer, translatable("wp.menu.reload"), GRAY, Click.suggest("/wp reload"),
                    Tooltip.of("wp.menu.reload.tooltip").hint("wp.hint.confirm")));
        }
        lines.line(translatable("wp.menu.title", GOLD), text("   "), Chat.join(extras));

        group(lines, "wp.menu.browse", Chat.join(
                Chat.link(viewer, translatable("wp.menu.this_dimension"), AQUA, Click.run("/wp list"),
                        Tooltip.of(translatable("wp.menu.this_dimension.tooltip", dims.name(here)))),
                Chat.link(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"), Tooltip.of("wp.all.tooltip")),
                remoteAvailable ? Chat.link(viewer, translatable("wp.menu.remote"), AQUA, Click.run("/wp remote"),
                        Tooltip.of("wp.menu.remote.tooltip")) : null,
                Chat.link(viewer, translatable("wp.search"), AQUA, Click.suggest("/wp list all search "),
                        Tooltip.of("wp.search.everywhere").hint("wp.hint.type_search"))));

        if (viewer.can(Viewer.Permission.ADD)) {
            group(lines, "wp.menu.create", Chat.join(
                    Chat.link(viewer, translatable("wp.menu.waypoint_here"), GREEN, Click.run("/wp add"),
                            Tooltip.of("wp.menu.waypoint_here.tooltip").line("wp.menu.waypoint_here.detail")),
                    Chat.link(viewer, translatable("wp.menu.list"), GREEN, Click.suggest("/wp add " + here + " "),
                            Tooltip.of(translatable("wp.new_list.tooltip", dims.name(here))).hint("wp.hint.type_name"))));
        }

        if (viewer.can(Viewer.Permission.NAVIGATE)) {
            Component navigationLink = Chat.link(viewer, translatable("wp.menu.navigation"), LIGHT_PURPLE,
                    Click.run("/wp navigate"), Tooltip.of("wp.menu.navigation.tooltip"));
            group(lines, "wp.menu.travel", navigation == null ? navigationLink : Chat.join(
                    translatable("wp.menu.navigation_to", GRAY, navigationLink, navigation.reference(dims)),
                    Chat.link(viewer, translatable("wp.navigation.stop"), RED, Click.run("/wp navigate disable"),
                            Tooltip.of("wp.navigation.stop.tooltip"))));
        }

        List<Component> transfer = new ArrayList<>();
        if (viewer.hasMod()) {
            transfer.add(Chat.link(viewer, translatable("wp.menu.download"), AQUA, Click.run("/wp download"),
                    Tooltip.of("wp.menu.download.tooltip")));
            if (viewer.can(Viewer.Permission.UPLOAD)) {
                transfer.add(Chat.link(viewer, translatable("wp.menu.upload"), AQUA, Click.run("/wp upload"),
                        Tooltip.of("wp.menu.upload.tooltip").line("wp.menu.upload.detail")));
            }
        }
        if (!transfer.isEmpty()) {
            group(lines, "wp.menu.transfer", Chat.join(transfer));
        }
        return lines.build();
    }

    private static void group(ChatLines lines, String labelKey, Component items) {
        lines.add(translatable(labelKey, GRAY));
        lines.line(text("  "), items);
    }
}
```

- [ ] **Step 5: Write `HelpTopics` and `HelpScreen`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/HelpTopics.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.translatable;

/** What each help topic says (spec 12). Usage syntax and examples are commands, so they stay untranslated. */
public final class HelpTopics {
    private HelpTopics() {
    }

    public enum Topic {
        LIST, ADD, EDIT, REMOVE, TP, NAVIGATE, UPLOAD, DOWNLOAD, REMOTE;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public Component label() {
            return translatable("wp.help.topic." + this.id());
        }

        /** Whether the viewer may use the commands this topic is about. */
        public boolean readableBy(Viewer viewer) {
            return switch (this) {
                case LIST, DOWNLOAD -> true;
                case ADD -> viewer.can(Viewer.Permission.ADD);
                case EDIT -> viewer.can(Viewer.Permission.EDIT);
                case REMOVE -> viewer.can(Viewer.Permission.REMOVE);
                case TP -> viewer.can(Viewer.Permission.TP);
                case NAVIGATE -> viewer.can(Viewer.Permission.NAVIGATE);
                case UPLOAD -> viewer.can(Viewer.Permission.UPLOAD);
                case REMOTE -> viewer.can(Viewer.Permission.REMOTE_LIST) || viewer.can(Viewer.Permission.REMOTE_TP);
            };
        }
    }

    /** A usage line: its syntax, the command a click suggests, and the tooltip that explains it. */
    public record Usage(String syntax, String suggestion, Tooltip tooltip) {
    }

    public record Example(String command, String descriptionKey) {
    }

    public record Content(List<Usage> usages, List<Example> examples) {
    }

    public static List<Topic> readable(Viewer viewer) {
        return Arrays.stream(Topic.values()).filter(topic -> topic.readableBy(viewer)).toList();
    }

    public static Content content(Topic topic, boolean textDisplay) {
        return switch (topic) {
            case LIST -> new Content(List.of(
                    usage("/wp list [<dimension> [<list>]]", "/wp list ", "wp.help.list.browse", "wp.help.list.browse.note"),
                    usage("/wp list all", "/wp list all", "wp.help.list.all"),
                    usage("/wp list dimensions", "/wp list dimensions", "wp.help.list.dimensions"),
                    usage("[search <text>] [sort <mode> [order <direction>]] [limit <number>] [view <view>] [page <number>]",
                            "/wp list ", "wp.help.list.options", "wp.help.list.options.mode",
                            "wp.help.list.options.direction", "wp.help.list.options.view", "wp.help.list.options.number")),
                    List.of(new Example("/wp list all search farm", "wp.help.list.example.search"),
                            new Example("/wp list minecraft:overworld \"Home Bases\" sort name", "wp.help.list.example.sort")));
            case ADD -> new Content(List.of(
                    usage("/wp add", "/wp add", "wp.help.add.picker"),
                    usage("/wp add <dimension> <list>", "/wp add ", "wp.help.add.list", "wp.help.add.list.note"),
                    usage("/wp add <position> <list> <name> [<initials> <color> <yaw> <global> [<keywords> [<description>]] [icon <id>]]",
                            "/wp add ~ ~ ~ ", "wp.help.add.here", "wp.help.add.here.note", "wp.help.add.color.note",
                            "wp.help.add.global.note"),
                    usage("/wp add <dimension> <list> <position> <name> [<initials> <color> <yaw> <global> [<keywords> [<description>]] [icon <id>]]",
                            "/wp add ", "wp.help.add.elsewhere", "wp.help.add.here.note")),
                    List.of(new Example("/wp add ~ ~ ~ \"Home Bases\" \"Main Home\"", "wp.help.add.example.here"),
                            new Example("/wp add minecraft:overworld \"Home Bases\"", "wp.help.add.example.list")));
            case EDIT -> new Content(List.of(
                    usage("/wp edit waypoint <dimension> <list> <waypoint> set <property> [<value>]", "/wp edit waypoint ",
                            "wp.help.edit.waypoint", "wp.help.edit.properties", "wp.help.edit.pickers"),
                    usage("/wp edit waypoint <dimension> <list> <waypoint> clear <property>", "/wp edit waypoint ",
                            "wp.help.edit.clear"),
                    usage("/wp edit list <dimension> <list> set <property> <value>", "/wp edit list ", "wp.help.edit.list"),
                    usage("/wp edit list <dimension> <list> clear display-name", "/wp edit list ", "wp.help.edit.list.clear")),
                    List.of(new Example("/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" set color gold",
                            "wp.help.edit.example")));
            case REMOVE -> new Content(List.of(
                    usage("/wp remove <dimension> <list> <waypoint>", "/wp remove ", "wp.help.remove.waypoint"),
                    usage("/wp remove <dimension> <list>", "/wp remove ", "wp.help.remove.list"),
                    usage("/wp restore <token>", "/wp restore ", "wp.help.remove.restore", "wp.help.remove.restore.note")),
                    List.of(new Example("/wp remove minecraft:overworld \"Home Bases\" \"Main Home\"", "wp.help.remove.example")));
            case TP -> new Content(List.of(
                    usage("/wp tp <dimension> <list> <waypoint>", "/wp tp ", "wp.help.tp.waypoint")),
                    List.of(new Example("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", "wp.help.tp.example")));
            case NAVIGATE -> {
                List<Usage> usages = new ArrayList<>(List.of(
                        usage("/wp navigate", "/wp navigate", "wp.help.navigate.panel"),
                        usage("/wp navigate <dimension> <list> <waypoint> [default|all|<method>]", "/wp navigate ",
                                "wp.help.navigate.start", "wp.help.navigate.start.note",
                                textDisplay ? "wp.help.navigate.methods.text_display" : "wp.help.navigate.methods"),
                        usage("/wp navigate use <method>", "/wp navigate use ", "wp.help.navigate.use"),
                        usage("/wp navigate disable [<method>]", "/wp navigate disable", "wp.help.navigate.disable")));
                if (textDisplay) {
                    usages.add(usage("/wp navigate config text_display", "/wp navigate config text_display",
                            "wp.help.navigate.text_display"));
                }
                yield new Content(usages, List.of(new Example(
                        "/wp navigate minecraft:overworld Villages \"Oak Village\" bossbar", "wp.help.navigate.example")));
            }
            case UPLOAD -> new Content(List.of(
                    usage("/wp upload", "/wp upload", "wp.help.upload.panel"),
                    usage("/wp upload <source> [force server|local [delete]] [<dimension> [<list> [<waypoint>]]]",
                            "/wp upload ", "wp.help.upload.run", "wp.help.upload.source", "wp.help.upload.force")),
                    List.of(new Example("/wp upload xaero", "wp.help.upload.example")));
            case DOWNLOAD -> new Content(List.of(
                    usage("/wp download [<dimension> [<list> [<waypoint>]]]", "/wp download ", "wp.help.download.run")),
                    List.of(new Example("/wp download minecraft:overworld", "wp.help.download.example")));
            case REMOTE -> new Content(List.of(
                    usage("/wp remote", "/wp remote", "wp.help.remote.servers"),
                    usage("/wp remote list [<server> [<dimension> [<list>]]]", "/wp remote list ", "wp.help.remote.list",
                            "wp.help.remote.list.note"),
                    usage("/wp remote tp <server> <dimension> <list> <waypoint>", "/wp remote tp ", "wp.help.remote.tp")),
                    List.of(new Example("/wp remote list survival", "wp.help.remote.example")));
        };
    }

    private static Usage usage(String syntax, String suggestion, String descriptionKey, String... noteKeys) {
        Tooltip tooltip = Tooltip.of(translatable(descriptionKey));
        for (String note : noteKeys) {
            tooltip = tooltip.line(note);
        }
        return new Usage(syntax, suggestion, tooltip);
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/feedback/HelpScreen.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatFont;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The help index and topics (spec 12). */
public final class HelpScreen {
    private static final int TOPICS_ON_FIRST_LINE = 5;

    private HelpScreen() {
    }

    public static Component index(Viewer viewer) {
        ChatLines lines = new ChatLines().add(translatable("wp.help.title", GOLD));
        List<HelpTopics.Topic> topics = HelpTopics.readable(viewer);
        if (viewer.plainText()) {
            lines.add(translatable("wp.help.plain_hint", GRAY));
            lines.line(translatable("wp.help.commands", GRAY), text("  "),
                    Chat.join(topics.stream().map(topic -> (Component) text(topic.id())).toList()));
            return lines.build();
        }
        lines.add(translatable("wp.help.menu_hint", GRAY, Chat.link(viewer, translatable("wp.help.open_menu"), AQUA,
                Click.run("/wp"), Tooltip.of("wp.help.open_menu.tooltip"))));
        List<Component> links = topics.stream().map(topic -> Chat.link(viewer, topic.label(), AQUA,
                Click.run("/wp help " + topic.id()),
                Tooltip.of("wp.help.topic.tooltip", topic.label()).line("wp.help.topic.detail"))).toList();
        int split = Math.min(TOPICS_ON_FIRST_LINE, links.size());
        lines.line(translatable("wp.help.commands", GRAY), text("  "), Chat.join(links.subList(0, split)));
        if (split < links.size()) {
            lines.line(text("  "), Chat.join(links.subList(split, links.size())));
        }
        return lines.build();
    }

    public static Component topic(Viewer viewer, HelpTopics.Topic topic, boolean textDisplay) {
        HelpTopics.Content content = HelpTopics.content(topic, textDisplay);
        ChatLines lines = new ChatLines();
        Component title = Chat.colored(topic.label(), GOLD);
        lines.add(viewer.plainText() ? title : Chat.concat(title, text("  "), translatable("wp.help.topic.hint", GRAY)));
        for (HelpTopics.Usage usage : content.usages()) {
            if (viewer.plainText()) {
                lines.add(text(usage.syntax()));
                for (Component line : usage.tooltip().textLines()) {
                    lines.line(text("  "), line);
                }
                continue;
            }
            Click click = Click.suggest(usage.suggestion());
            Tooltip tooltip = usage.tooltip().hint("wp.hint.fill");
            int[] depth = {0};
            for (String line : wrap(usage.syntax(), "", "    ")) {
                lines.add(Chat.link(viewer, colorize(line, depth), AQUA, click, tooltip));
            }
        }
        lines.add(translatable("wp.help.examples", GRAY));
        for (HelpTopics.Example example : content.examples()) {
            if (viewer.plainText()) {
                lines.add(text("  " + example.command()));
                lines.line(text("    "), translatable(example.descriptionKey()));
                continue;
            }
            Tooltip tooltip = Tooltip.of(translatable(example.descriptionKey())).hint("wp.hint.fill");
            for (String line : wrap(example.command(), "  ", "      ")) {
                lines.add(Chat.link(viewer, text(line), AQUA, Click.suggest(example.command()), tooltip));
            }
        }
        if (!viewer.plainText()) {
            lines.add(Chat.join(
                    Chat.link(viewer, translatable("wp.help.index"), GRAY, Click.run("/wp help"), Tooltip.of("wp.help.index.tooltip")),
                    Chat.link(viewer, translatable("wp.help.menu"), GRAY, Click.run("/wp"), Tooltip.of("wp.help.open_menu.tooltip"))));
        }
        return lines.build();
    }

    /** Breaks text at spaces so no line passes the chat width; later lines start with the continuation indent. */
    public static List<String> wrap(String text, String indent, String continuation) {
        List<String> lines = new ArrayList<>();
        String line = indent;
        boolean empty = true;
        for (String word : text.split(" ")) {
            String candidate = empty ? line + word : line + " " + word;
            if (!empty && ChatFont.width(candidate) > ChatFont.CHAT_WIDTH) {
                lines.add(line);
                line = continuation + word;
            } else {
                line = candidate;
            }
            empty = false;
        }
        lines.add(line);
        return lines;
    }

    /** Commands aqua, <arguments> yellow, [optional parts] gray; depth carries brackets across lines. */
    private static Component colorize(String line, int[] depth) {
        List<Component> pieces = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        TextColor runColor = null;
        boolean argument = false;
        for (char character : line.toCharArray()) {
            if (character == '<') {
                argument = true;
            }
            TextColor color;
            if (argument) {
                color = YELLOW;
            } else if (character == '[') {
                depth[0]++;
                color = GRAY;
            } else if (character == ']') {
                color = GRAY;
                depth[0]--;
            } else {
                color = depth[0] > 0 ? GRAY : AQUA;
            }
            if (character == '>') {
                argument = false;
            }
            if (runColor != null && !runColor.equals(color)) {
                pieces.add(text(run.toString(), runColor));
                run.setLength(0);
            }
            runColor = color;
            run.append(character);
        }
        if (runColor != null) {
            pieces.add(text(run.toString(), runColor));
        }
        return Chat.concat(pieces);
    }
}
```

- [ ] **Step 6: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 7: Write the failing command tests**

In `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void theMenuAnswersPlayersAndTheHelpIndexAnswersPlainText() {
        assertEquals("Server Waypoint   Open GUI · Help · Reload",
                lines(this.harness.run(CommandHarness.player(), "wp")).get(0));
        assertEquals("Server Waypoint help", lines(this.harness.run(CommandHarness.console(), "wp")).get(0));
        assertEquals("Server Waypoint help",
                lines(this.harness.run(CommandHarness.player().readByConsole(), "wp")).get(0));
    }

    @Test
    void helpTopicsFollowPermissions() {
        CommandHarness.Source member = CommandHarness.player().withPermissions("navigate");

        assertEquals("Commands  List · Navigate · Download", lines(this.harness.run(member, "wp help")).get(2));
        this.harness.fails(member, "wp help add");
        assertEquals("Teleport  hover a line for details, click to use it",
                lines(this.harness.run(CommandHarness.player(), "wp help tp")).get(0));
        assertEquals("Download  hover a line for details, click to use it",
                lines(this.harness.run(member, "wp help download")).get(0));
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

and replace:

```java
import static _959.server_waypoint.text.chat.ChatAssert.render;
```

with:

```java
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
```

- [ ] **Step 8: Wire the menu and help into the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
```

with:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.HelpScreen;
import _959.server_waypoint.text.feedback.HelpTopics;
import _959.server_waypoint.text.feedback.MenuScreen;
import _959.server_waypoint.text.feedback.PlacedWaypoint;
```

replace:

```java
                remotePermissions::canList, remotePermissions::canRequestTeleport,
                (source, selection, feedback) -> remoteTeleport.initiate(source, selection, feedback));
```

with:

```java
                remotePermissions::canList, remotePermissions::canRequestTeleport,
                (source, selection, feedback) -> remoteTeleport.initiate(source, selection, feedback),
                source -> HelpScreen.topic(this.viewer(source), HelpTopics.Topic.REMOTE, false));
```

replace:

```java
        return (LiteralCommandNode<S>) literal(WAYPOINT_COMMAND)
                .executes(context -> {
                    executeMenu((S) context.getSource(), false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal(HELP_COMMAND)
                        .executes(context -> {
                            executeHelp((S) context.getSource());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(literal(ADD_COMMAND)
                                .requires(source -> hasAddPermission((S) source))
                                .executes(context -> {
                                    executeAddHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(literal(EDIT_COMMAND)
                                .requires(source -> hasEditPermission((S) source))
                                .executes(context -> {
                                    executeEditHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(literal(LIST_COMMAND)
                                .executes(context -> {
                                    executeListHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(literal("remote").requires(source -> this.remoteCommand.canUse((S) source))
                                .executes(context -> this.remoteCommand.help((S) context.getSource())))
                        .then(literal(NAVIGATE_COMMAND)
                                .requires(source -> hasNavigatePermission((S) source))
                                .executes(context -> {
                                    executeNavigateHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal(ADD_COMMAND)
```

with:

```java
        return (LiteralCommandNode<S>) literal(WAYPOINT_COMMAND)
                .executes(context -> {
                    executeMenu((S) context.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .then((ArgumentBuilder<Object, ?>) helpCommandNode())
                .then(literal(ADD_COMMAND)
```

and replace the six methods from `private void executeHelp(S source) {` through the end of `executeNavigateHelp`:

```java
    private void executeHelp(S source) {
        executeMenu(source, true);
    }

    private void executeMenu(S source, boolean detailed) {
        this.sender.sendMessage(source, WaypointCommandHelp.menu(
                hasAddPermission(source),
                hasEditPermission(source),
                hasRemovePermission(source),
                hasNavigatePermission(source),
                hasTpPermission(source),
                hasReloadPermission(source),
                hasUploadPermission(source),
                remoteCommand.canUse(source),
                remoteCommand.canList(source),
                detailed
        ));
    }

    private void executeAddHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.addHelp());
    }

    private void executeEditHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.editHelp());
    }

    private void executeListHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.listHelp());
    }

    private void executeNavigateHelp(S source) {
        this.sender.sendMessage(
                source,
                WaypointCommandHelp.navigateHelp(
                        this.isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)
                )
        );
    }
```

with:

```java
    /** The menu for players; the help index for plain-text viewers, since the menu is all links. */
    private void executeMenu(S source) {
        Viewer viewer = viewer(source);
        if (viewer.plainText()) {
            this.sender.sendMessage(source, HelpScreen.index(viewer));
            return;
        }
        boolean remoteAvailable = viewer.can(Viewer.Permission.REMOTE_LIST)
                && !this.waypointServer.remoteCatalogStore().snapshot().isEmpty();
        this.sender.sendMessage(source, MenuScreen.menu(viewer, dimensions(source, viewer),
                navigationTarget(source), remoteAvailable));
    }

    private LiteralArgumentBuilder<S> helpCommandNode() {
        LiteralArgumentBuilder<S> help = literal(HELP_COMMAND);
        help.executes(context -> {
            this.sender.sendMessage(context.getSource(), HelpScreen.index(viewer(context.getSource())));
            return Command.SINGLE_SUCCESS;
        });
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            help.then(LiteralArgumentBuilder.<S>literal(topic.id())
                    .requires(source -> topic.readableBy(viewer(source)))
                    .executes(context -> {
                        this.sender.sendMessage(context.getSource(), HelpScreen.topic(viewer(context.getSource()), topic,
                                this.isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)));
                        return Command.SINGLE_SUCCESS;
                    }));
        }
        return help;
    }

    /** The player's navigation target, if they may navigate and are navigating. */
    private @Nullable PlacedWaypoint navigationTarget(S source) {
        P player = getPlayer(source);
        if (player == null || !hasNavigatePermission(source)) {
            return null;
        }
        NavigationSession session = this.navigationService.status(player).session();
        return session == null ? null : place(session.target());
    }

    /** The live waypoint behind a navigation target, or a stand-in made from the target when it is gone. */
    private PlacedWaypoint place(NavigationTarget target) {
        WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(target.dimensionName());
        WaypointList list = fileManager == null ? null : fileManager.getWaypointListByName(target.listName());
        SimpleWaypoint waypoint = list == null ? null : list.getWaypointByName(target.waypointName());
        if (list == null || waypoint == null) {
            list = new WaypointList(target.listName(), target.listDisplayName(), 0, List.of());
            waypoint = new SimpleWaypoint(target.waypointName(), target.waypointDisplayName(),
                    WaypointInitials.getDefaultInitials(plainText(target.waypointDisplayName())), target.position(),
                    target.rgb(), 0, true, List.of(), target.waypointDescription());
        }
        return new PlacedWaypoint(target.dimensionName(), list, waypoint);
    }
```

In `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`, replace:

```java
    private final RemoteTeleportInitiator<S> teleport;
    private final RemoteCatalogQuery query = new RemoteCatalogQuery();

    RemoteWaypointCommand(Supplier<RemoteCatalogStore> store, BiConsumer<S, Component> send,
                          BiConsumer<S, Component> error, IntSupplier defaultLimit, Predicate<S> canList,
                          Predicate<S> canTeleport, RemoteTeleportInitiator<S> teleport) {
        this.canList = Objects.requireNonNull(canList, "canList");
        this.canTeleport = Objects.requireNonNull(canTeleport, "canTeleport");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
```

with:

```java
    private final RemoteTeleportInitiator<S> teleport;
    private final Function<S, Component> helpScreen;
    private final RemoteCatalogQuery query = new RemoteCatalogQuery();

    RemoteWaypointCommand(Supplier<RemoteCatalogStore> store, BiConsumer<S, Component> send,
                          BiConsumer<S, Component> error, IntSupplier defaultLimit, Predicate<S> canList,
                          Predicate<S> canTeleport, RemoteTeleportInitiator<S> teleport,
                          Function<S, Component> helpScreen) {
        this.canList = Objects.requireNonNull(canList, "canList");
        this.canTeleport = Objects.requireNonNull(canTeleport, "canTeleport");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.helpScreen = Objects.requireNonNull(helpScreen, "helpScreen");
```

and replace:

```java
        send.accept(source, WaypointCommandHelp.remoteMenu(canList.test(source), canTeleport.test(source)));
```

with:

```java
        send.accept(source, helpScreen.apply(source));
```

Delete `common/src/main/java/_959/server_waypoint/command/WaypointCommandHelp.java`:

```bash
/usr/bin/git rm common/src/main/java/_959/server_waypoint/command/WaypointCommandHelp.java
```

- [ ] **Step 9: Retire the old menu and help tests**

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`, delete these test methods, whose behaviour `MenuScreenTest`, `HelpScreenTest` and `CommandFeedbackTest` now cover: `helpLinksDetailedTopicsAndSuggestsCommandPrefixes`, `bareRootShowsACompactInteractiveMenu`, `menuOffersRemoteEntryWithoutRemoteListPermission`, `addHelpShowsAllFormsArgumentsAndExamples`, `editHelpShowsPatchRoutesAndExample`, `listHelpShowsScopesOrderedOptionsAndExamples`, `helpOmitsCommandsTheSourceCannotUse`. Then replace:

```java
        this.dispatcher.execute("wp help remote", this.source);
        assertTrue(translationKeys(lastMessage()).contains("waypoint.help.remote.summary"));
```

with:

```java
        this.dispatcher.execute("wp help remote", this.source);
        assertTrue(plainText(lastMessage()).contains("/wp remote list"));
```

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandNavigationTest.java`, delete `navigateHelpDocumentsEverySyntaxMethodAndClickableExample` and `helpLinksNavigateTopicWithoutRegisteringMethodsSubcommand` (`HelpScreenTest` covers them), and in `unsupportedExperimentalMethodIsNotRegisteredOrDocumented` replace:

```java
        assertFalse(translationKeys(help).contains(
                "waypoint.help.navigate.usage.transformation.translation"
        ));
```

with:

```java
        assertFalse(translationKeys(help).contains("wp.help.navigate.text_display"));
```

In `common/src/test/java/_959/server_waypoint/command/RemoteWaypointCommandTest.java`, replace:

```java
                (source, text) -> errors.add(text), () -> 5, source -> allowed, source -> tpAllowed, handoffs);
```

with:

```java
                (source, text) -> errors.add(text), () -> 5, source -> allowed, source -> tpAllowed, handoffs,
                source -> Component.text("Remote help"));
```

and replace:

```java
        dispatcher.execute("wp remote", "console");
        assertTrue(keys(last()).contains("waypoint.help.remote.summary"));
        assertFalse(text(last()).contains("/wp remote tp"));
        assertTrue(clicks(last()).containsAll(List.of("/wp remote servers", "/wp remote list",
                "/wp remote details ", "/wp help remote")));
```

with:

```java
        dispatcher.execute("wp remote", "console");
        assertEquals("Remote help", text(last()));
```

and, in `teleportPermissionsAreIndependentAndRecheckedAfterParsing`, replace:

```java
        dispatcher.execute("wp remote", "player");
        assertTrue(text(last()).contains("/wp remote tp")); assertFalse(text(last()).contains("/wp remote list"));
        assertTrue(clicks(last()).contains("/wp remote tp "));
        assertFalse(clicks(last()).contains("/wp remote servers"));
```

with (`HelpScreenTest` covers the remote topic's content):

```java
        dispatcher.execute("wp remote", "player");
        assertEquals("Remote help", text(last()));
```

- [ ] **Step 10: Run the common tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS. If the compiler reports unused imports or symbols left by the deleted tests (for example `WaypointCommandHelp`), remove those imports.

- [ ] **Step 11: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/feedback common/src/test/java/_959/server_waypoint/text/feedback common/src/main/java/_959/server_waypoint/command common/src/test/java/_959/server_waypoint/command common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Rebuild the /wp menu and help with clickable topics"
```

---

### Task 9: Local list screens

`/wp list <dimension>` and `/wp list <dimension> <list>` become the screens of spec 6: the header with the dimension switcher, the Tree, Lists and Flat views, single lists, search, the empty states, the view and sort rows and line-budget paging. The grammar gains `view lists` and accepts `page` after `limit` and after `view`, the order `ListTarget` writes. `/wp tp`, `/wp remove`, `/wp edit` and `/wp details` without a target show this dimension's list with a hint (spec 13), and `/wp add <dimension> <list> <position> <name>` works without the full set of properties, since list screens suggest it for other dimensions (spec 6.5). `/wp list all` keeps its old rendering until Task 10.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/ListActions.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/Errors.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/ListScreen.java`
- Rewrite: `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, `RemoteWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointListDisplayModel.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/ListScreenTest.java`; `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`
- Modify (tests): `common/src/test/java/_959/server_waypoint/core/waypoint/WaypointListDisplayModelTest.java`, `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit (Tasks 2–5), `WaypointRefs` (Task 7), `HelpScreen` (Task 8), `WaypointQueryEngine`, `WaypointListDisplayModel`.
- Produces:
  - `ListActions` (public, static): `Click addClick(Viewer, String dimension, WaypointList)`, `Tooltip addTooltip(DimensionStyle, String dimension, WaypointList)`, `@Nullable Component add(DimensionStyle, String dimension, WaypointList)` (`Add here` or `Add waypoint`), `@Nullable Component plus(DimensionStyle, String dimension, WaypointList)` (`  +`), `@Nullable Component newList(DimensionStyle, String dimension)`, `@Nullable Component removeList(DimensionStyle, String dimension, WaypointList)`.
  - `Errors` (public, static): `noDimension(DimensionStyle, String dimension)`, `noList(DimensionStyle, String dimension, String list)`.
  - `ListScreen`: `record Totals(int lists, int waypoints)`; `static Component dimension(DimensionStyle, String dimension, Totals, WaypointQueryEngine.QueryResult, ListQuery, int pageLimit)`; `static Component list(DimensionStyle, String dimension, WaypointList, WaypointQueryEngine.QueryResult, ListQuery, int pageLimit)`; `static Component header(DimensionStyle, String dimension, Totals)`; `static Component row(DimensionStyle, String dimension, WaypointList, SimpleWaypoint, boolean withList)`; package-private `static Component dimensionLink(DimensionStyle, String dimension)` (the breadcrumb's first step); constants `COLLAPSE_ABOVE = 5`, `PREVIEW = 3`, `SORTS`.
  - `ListCommandOptions.Factory.create(SortMode mode, boolean reversed, ListView view)`.
  - `CoreWaypointCommand`: `Component listScreen(S source, D dimension, @Nullable String list, ListQuery query)`, `WaypointQueryEngine.Query engineQuery(S source, ListQuery query)`, `executeTargetHint(S source, String hintKey, HelpTopics.Topic plainTopic)`, `executeQuickAddWaypoint(S, D, B, String list, String name)`.
- Keys: `wp.hint.list_details`, `wp.hint.choose_dimension`, `wp.list.*`, `wp.dimension.no_lists.sentence`, `wp.more.rows`, `wp.open`, `wp.search.in`, `wp.search.list`, `wp.add.*`, `wp.error.no_dimension`, `wp.dimensions.title`, `wp.dimensions.choose`, `wp.error.no_list`, `wp.error.browse_lists`, `wp.hint_line.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.hint.list_details": "Click for list details",
  "wp.hint.choose_dimension": "Click to choose a dimension",
  "wp.list.empty": "No waypoints yet",
  "wp.list.empty.sentence": "No waypoints yet.",
  "wp.list.all_rows": "All {0}, {1} per page",
  "wp.list.every_list_in": "Every list in {0}",
  "wp.list.remove": "Remove list",
  "wp.list.remove.tooltip": "Remove this empty list",
  "wp.dimension.no_lists.sentence": "No lists yet.",
  "wp.more.rows": "{0} more",
  "wp.open": "Open {0}",
  "wp.search.in": "Search waypoints and lists in {0}",
  "wp.search.list": "Search {0}",
  "wp.add.here": "Add here",
  "wp.add.waypoint": "Add waypoint",
  "wp.add.here.tooltip": "Add a waypoint where you stand to {0}",
  "wp.add.elsewhere.tooltip": "Add a waypoint to {0} in {1}",
  "wp.error.no_dimension": "No dimension called {0}.",
  "wp.dimensions.title": "Dimensions",
  "wp.dimensions.choose": "Choose a dimension",
  "wp.error.no_list": "No list called {0} in {1}.",
  "wp.error.browse_lists": "Browse lists",
  "wp.hint_line.tp": "Click a waypoint''s initials to teleport.",
  "wp.hint_line.remove": "Click a waypoint''s name, then Remove.",
  "wp.hint_line.edit": "Click a waypoint''s name to edit it.",
  "wp.hint_line.details": "Click a waypoint''s name for details."
```

`zh_cn.json`:

```json
  "wp.hint.list_details": "点击查看列表详情",
  "wp.hint.choose_dimension": "点击选择维度",
  "wp.list.empty": "还没有路径点",
  "wp.list.empty.sentence": "还没有路径点。",
  "wp.list.all_rows": "全部{0}，每页 {1} 条",
  "wp.list.every_list_in": "{0}中的所有列表",
  "wp.list.remove": "删除列表",
  "wp.list.remove.tooltip": "删除这个空列表",
  "wp.dimension.no_lists.sentence": "还没有列表。",
  "wp.more.rows": "还有 {0} 个",
  "wp.open": "打开{0}",
  "wp.search.in": "在{0}中搜索路径点和列表",
  "wp.search.list": "搜索{0}",
  "wp.add.here": "在此添加",
  "wp.add.waypoint": "添加路径点",
  "wp.add.here.tooltip": "在你站立的位置向{0}添加路径点",
  "wp.add.elsewhere.tooltip": "向{1}中的{0}添加路径点",
  "wp.error.no_dimension": "没有名为 {0} 的维度。",
  "wp.dimensions.title": "维度",
  "wp.dimensions.choose": "选择维度",
  "wp.error.no_list": "{1}中没有名为 {0} 的列表。",
  "wp.error.browse_lists": "浏览列表",
  "wp.hint_line.tp": "点击路径点的缩写即可传送。",
  "wp.hint_line.remove": "点击路径点名称，然后点删除。",
  "wp.hint_line.edit": "点击路径点名称即可编辑。",
  "wp.hint_line.details": "点击路径点名称查看详情。"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.hint.list_details": "點擊查看列表詳情",
  "wp.hint.choose_dimension": "點擊選擇維度",
  "wp.list.empty": "還沒有路徑點",
  "wp.list.empty.sentence": "還沒有路徑點。",
  "wp.list.all_rows": "全部{0}，每頁 {1} 項",
  "wp.list.every_list_in": "{0}中的所有列表",
  "wp.list.remove": "刪除列表",
  "wp.list.remove.tooltip": "刪除這個空列表",
  "wp.dimension.no_lists.sentence": "還沒有列表。",
  "wp.more.rows": "還有 {0} 個",
  "wp.open": "開啟{0}",
  "wp.search.in": "在{0}中搜尋路徑點和列表",
  "wp.search.list": "搜尋{0}",
  "wp.add.here": "在此新增",
  "wp.add.waypoint": "新增路徑點",
  "wp.add.here.tooltip": "在你站立的位置向{0}新增路徑點",
  "wp.add.elsewhere.tooltip": "向{1}中的{0}新增路徑點",
  "wp.error.no_dimension": "沒有名為 {0} 的維度。",
  "wp.dimensions.title": "維度",
  "wp.dimensions.choose": "選擇維度",
  "wp.error.no_list": "{1}中沒有名為 {0} 的列表。",
  "wp.error.browse_lists": "瀏覽列表",
  "wp.hint_line.tp": "點擊路徑點的縮寫即可傳送。",
  "wp.hint_line.remove": "點擊路徑點名稱，然後點刪除。",
  "wp.hint_line.edit": "點擊路徑點名稱即可編輯。",
  "wp.hint_line.details": "點擊路徑點名稱查看詳情。"
```

`es_es.json`:

```json
  "wp.hint.list_details": "Haz clic para ver los detalles de la lista",
  "wp.hint.choose_dimension": "Haz clic para elegir una dimensión",
  "wp.list.empty": "Aún no hay puntos de ruta",
  "wp.list.empty.sentence": "Aún no hay puntos de ruta.",
  "wp.list.all_rows": "Todos: {0}, {1} por página",
  "wp.list.every_list_in": "Todas las listas de {0}",
  "wp.list.remove": "Quitar lista",
  "wp.list.remove.tooltip": "Quita esta lista vacía",
  "wp.dimension.no_lists.sentence": "Aún no hay listas.",
  "wp.more.rows": "{0} más",
  "wp.open": "Abrir {0}",
  "wp.search.in": "Busca puntos de ruta y listas en {0}",
  "wp.search.list": "Buscar en {0}",
  "wp.add.here": "Añadir aquí",
  "wp.add.waypoint": "Añadir punto",
  "wp.add.here.tooltip": "Añade a {0} un punto de ruta donde estás",
  "wp.add.elsewhere.tooltip": "Añade un punto de ruta a {0} en {1}",
  "wp.error.no_dimension": "No hay ninguna dimensión llamada {0}.",
  "wp.dimensions.title": "Dimensiones",
  "wp.dimensions.choose": "Elige una dimensión",
  "wp.error.no_list": "No hay ninguna lista llamada {0} en {1}.",
  "wp.error.browse_lists": "Ver listas",
  "wp.hint_line.tp": "Haz clic en las iniciales de un punto de ruta para teletransportarte.",
  "wp.hint_line.remove": "Haz clic en el nombre de un punto de ruta y luego en Quitar.",
  "wp.hint_line.edit": "Haz clic en el nombre de un punto de ruta para editarlo.",
  "wp.hint_line.details": "Haz clic en el nombre de un punto de ruta para ver sus detalles."
```

`he_il.json`:

```json
  "wp.hint.list_details": "לחצו לפרטי הרשימה",
  "wp.hint.choose_dimension": "לחצו לבחירת ממד",
  "wp.list.empty": "אין נקודות ציון עדיין",
  "wp.list.empty.sentence": "אין נקודות ציון עדיין.",
  "wp.list.all_rows": "כל ה-{0}, {1} בעמוד",
  "wp.list.every_list_in": "כל הרשימות ב-{0}",
  "wp.list.remove": "הסרת הרשימה",
  "wp.list.remove.tooltip": "הסרת הרשימה הריקה הזו",
  "wp.dimension.no_lists.sentence": "אין רשימות עדיין.",
  "wp.more.rows": "עוד {0}",
  "wp.open": "פתיחת {0}",
  "wp.search.in": "חיפוש נקודות ציון ורשימות ב-{0}",
  "wp.search.list": "חיפוש ב-{0}",
  "wp.add.here": "הוספה כאן",
  "wp.add.waypoint": "הוספת נקודה",
  "wp.add.here.tooltip": "הוספת נקודת ציון ל-{0} במקום שבו אתם עומדים",
  "wp.add.elsewhere.tooltip": "הוספת נקודת ציון ל-{0} ב-{1}",
  "wp.error.no_dimension": "אין ממד בשם {0}.",
  "wp.dimensions.title": "ממדים",
  "wp.dimensions.choose": "בחירת ממד",
  "wp.error.no_list": "אין רשימה בשם {0} ב-{1}.",
  "wp.error.browse_lists": "עיון ברשימות",
  "wp.hint_line.tp": "לחצו על ראשי התיבות של נקודת ציון כדי להשתגר.",
  "wp.hint_line.remove": "לחצו על שם של נקודת ציון ואז על הסרה.",
  "wp.hint_line.edit": "לחצו על שם של נקודת ציון כדי לערוך אותה.",
  "wp.hint_line.details": "לחצו על שם של נקודת ציון לפרטים."
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/ListScreenTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListScreenTest {
    @TempDir
    static Path directory;
    private static WaypointQueryEngine engine;
    private static WaypointServerCore server;

    @BeforeAll
    static void load() {
        server = new WaypointServerCore(directory);
        Fixtures.overworldLists().forEach(list -> server.putWaypointList(OVERWORLD, list));
        server.putWaypointList(NETHER, new WaypointList("Storage", 1, List.of()));
        List<SimpleWaypoint> many = new ArrayList<>();
        for (int index = 1; index <= 23; index++) {
            many.add(Fixtures.waypoint("Pillar " + index, "P" + index, 0xFFFFFF, index, 64, 0));
        }
        server.putWaypointList("minecraft:the_end", new WaypointList("Pillars", 1, many));
        engine = new WaypointQueryEngine(server);
    }

    private static WaypointQueryEngine.Query engineQuery(Viewer viewer, ListQuery query) {
        return new WaypointQueryEngine.Query(query.search(), query.sort(), viewer.position(), viewer.dimension(), query.descending());
    }

    static Component overworld(Viewer viewer, ListQuery query) {
        return ListScreen.dimension(Fixtures.dims(viewer), OVERWORLD, new ListScreen.Totals(3, 14),
                engine.queryDimension(OVERWORLD, engineQuery(viewer, query)), query, 10);
    }

    static Component farms(Viewer viewer, ListQuery query) {
        WaypointList farms = server.getWaypointFileManager(OVERWORLD).getWaypointListByName("Farms");
        return ListScreen.list(Fixtures.dims(viewer), OVERWORLD, farms,
                engine.queryList(OVERWORLD, "Farms", engineQuery(viewer, query)), query, 10);
    }

    @Test
    void theTreeShowsEveryListWithItsFirstWaypoints() {
        Component tree = overworld(Fixtures.player(), ListQuery.DEFAULT);

        assertEquals(List.of(
                "Overworld ⏷  3 lists · 14 waypoints · All",
                "Home Bases  +",
                "  [MH] Main Home · 25 m",
                "  [GM] Gem Mine · 591 m",
                "  [SV] Spawn Village · 102 m",
                "Farms  +",
                "  [IF] Iron Farm · 263 m",
                "  [WF] Wheat Fields · 63 m",
                "  [CF] Cane Farm · 78 m",
                "  … 4 more",
                "Exploration  +",
                "  [OM] Ocean Monument · 1.3 km",
                "  [DT] Desert Temple · 1.1 km",
                "  [SH] Stronghold · 2.5 km",
                "  [WM] Woodland Mansion · 4.1 km",
                "Lists · Tree · Flat · Search · New list",
                "Sort Default · Name · Distance · Color"), lines(tree));
        ChatAssert.assertFitsChat(tree);
    }

    @Test
    void theHeaderSwitchesDimensionsAndListHeadingsOpenDetails() {
        Component tree = overworld(Fixtures.player(), ListQuery.DEFAULT);

        assertEquals("/wp list dimensions", clickOf(tree, "Overworld ⏷"));
        assertEquals(NamedTextColor.GREEN, colorOf(tree, "Overworld ⏷"));
        assertEquals("/wp list all", clickOf(tree, "All"));
        assertEquals("/wp details list minecraft:overworld \"Home Bases\"", clickOf(tree, "Home Bases"));
        assertEquals("Home Bases · 3 waypoints\nClick for list details", tooltipOf(tree, "Home Bases"));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(tree, "… 4 more"));
        assertEquals("Open Farms\nAll 7 waypoints, 10 per page", tooltipOf(tree, "… 4 more"));
        assertTrue(suggestions(tree).contains("/wp add ~ ~ ~ \"Home Bases\" "));
        assertEquals("/wp list minecraft:overworld view lists", clickOf(tree, "Lists"));
        assertEquals("/wp list minecraft:overworld sort name view tree", clickOf(tree, "Name"));
    }

    @Test
    void theListsViewHasOneLinePerList() {
        Component lists = overworld(Fixtures.player(), ListQuery.DEFAULT.withView(ListView.LISTS));

        assertEquals(List.of(
                "Overworld ⏷  3 lists · 14 waypoints · All",
                "Home Bases · 3  +",
                "Farms · 7  +",
                "Exploration · 4  +",
                "Lists · Tree · Flat · Search · New list"), lines(lists));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(lists, "Farms"));
        assertEquals(NamedTextColor.GOLD, colorOf(lists, "Lists"));
    }

    @Test
    void flatRowsNameTheirListAndPageByTheLimit() {
        Component flat = overworld(Fixtures.player(), ListQuery.DEFAULT.withView(ListView.FLAT));
        List<String> lines = lines(flat);

        assertEquals("[MH] Main Home · Home Bases · 25 m", lines.get(1));
        assertEquals(10, lines.stream().filter(line -> line.startsWith("[")).count());
        assertEquals("… 4 more waypoints", lines.get(11));
        assertEquals("Sort Default · Name · Distance · Color    ‹ 1/2 ›", lines.get(lines.size() - 1));
        assertEquals("/wp list minecraft:overworld view flat page 2", clickOf(flat, "… 4 more waypoints"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(flat, "Home Bases"));
        assertEquals(NamedTextColor.GRAY, colorOf(flat, "Home Bases"));
    }

    @Test
    void searchesShowTheirMatchesAndHowToClearThem() {
        Component search = overworld(Fixtures.player(), ListQuery.DEFAULT.withSearch("farm"));
        Component nothing = overworld(Fixtures.player(), ListQuery.DEFAULT.withSearch("zzz"));

        assertEquals("Search \"farm\" · 7 matches · Clear", lines(search).get(1));
        assertEquals("Farms  +", lines(search).get(2));
        assertEquals("/wp list minecraft:overworld", clickOf(search, "Clear"));
        assertEquals(List.of("Overworld ⏷  3 lists · 14 waypoints · All", "Nothing matches \"zzz\". Clear search"), lines(nothing));
    }

    @Test
    void distanceSortingNeedsTheViewerInTheDimension() {
        Component elsewhere = overworld(Fixtures.in(Fixtures.player(), NETHER), ListQuery.DEFAULT);

        assertEquals(NamedTextColor.DARK_GRAY, colorOf(elsewhere, "Distance"));
        assertEquals("Distance needs you in Overworld", tooltipOf(elsewhere, "Distance"));
        assertEquals("  [MH] Main Home", lines(elsewhere).get(2));
        assertTrue(suggestions(elsewhere).contains("/wp add minecraft:overworld \"Home Bases\" "));
    }

    @Test
    void aSingleListHasItsBreadcrumbActionsAndSortRow() {
        Component farms = farms(Fixtures.player(), ListQuery.DEFAULT.withSort(SortMode.NAME));

        assertEquals(List.of(
                "Overworld › Farms  7 waypoints · All",
                "[CF] Cane Farm · 78 m",
                "[GF] Gold Farm · 759 m",
                "[IF] Iron Farm · 263 m",
                "[MG] Mob Grinder · 315 m",
                "[SF] Slime Farm · 395 m",
                "[VH] Villager Hall · 117 m",
                "[WF] Wheat Fields · 63 m",
                "Search · Add here",
                "Sort Default · Name ↑ · Distance · Color"), lines(farms));
        assertEquals("/wp list minecraft:overworld", clickOf(farms, "Overworld"));
        assertEquals(NamedTextColor.GOLD, colorOf(farms, "Farms"));
        assertEquals("/wp details list minecraft:overworld Farms", clickOf(farms, "Farms"));
        assertEquals("/wp list minecraft:overworld Farms search ", clickOf(farms, "Search"));
        assertEquals("/wp add ~ ~ ~ Farms ", clickOf(farms, "Add here"));
        assertEquals("/wp list minecraft:overworld Farms sort name order descending", clickOf(farms, "Name"));
    }

    @Test
    void emptyStatesOfferTheNextStep() {
        Viewer player = Fixtures.player();
        WaypointList storage = server.getWaypointFileManager(NETHER).getWaypointListByName("Storage");
        Component emptyList = ListScreen.list(Fixtures.dims(player), NETHER, storage,
                engine.queryList(NETHER, "Storage", engineQuery(player, ListQuery.DEFAULT)), ListQuery.DEFAULT, 10);
        Component noLists = ListScreen.dimension(Fixtures.dims(player), "ad_astra:mars", new ListScreen.Totals(0, 0),
                WaypointQueryEngine.QueryResult.empty(engineQuery(player, ListQuery.DEFAULT)), ListQuery.DEFAULT, 10);
        Component storageTree = ListScreen.dimension(Fixtures.dims(player), NETHER, new ListScreen.Totals(1, 0),
                engine.queryDimension(NETHER, engineQuery(player, ListQuery.DEFAULT)), ListQuery.DEFAULT, 10);

        assertEquals(List.of("Nether › Storage · All", "No waypoints yet. Add waypoint · Remove list"), lines(emptyList));
        assertEquals("/wp remove minecraft:the_nether Storage", clickOf(emptyList, "Remove list"));
        assertEquals("Remove this empty list\nPress Enter to confirm", tooltipOf(emptyList, "Remove list"));
        assertEquals(List.of("Mars ⏷ · All", "No lists yet. New list"), lines(noLists));
        assertEquals(List.of("Nether ⏷  1 list · 0 waypoints · All", "Storage  +", "  No waypoints yet",
                "Lists · Tree · New list"), lines(storageTree));
    }

    @Test
    void pagersCloseTheLastRowAndMissingPagesLinkToTheLastOne() {
        Viewer player = Fixtures.player();
        Component end = ListScreen.list(Fixtures.dims(player), "minecraft:the_end",
                server.getWaypointFileManager("minecraft:the_end").getWaypointListByName("Pillars"),
                engine.queryList("minecraft:the_end", "Pillars", engineQuery(player, ListQuery.DEFAULT.withPage(3))),
                ListQuery.DEFAULT.withPage(3), 10);
        Component missing = farms(player, ListQuery.DEFAULT.withPage(5));

        assertEquals("Sort Default · Name · Distance · Color    « ‹ 3/3 › »", lines(end).get(lines(end).size() - 1));
        assertEquals(List.of("✘ Page 5 does not exist; the last page is 1. Last page"), lines(missing));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(missing, "Last page"));
    }

    @Test
    void plainTextViewersReadCoordinatesAndTheCommandsThatContinue() {
        Component tree = overworld(Fixtures.console(), ListQuery.DEFAULT);
        List<String> lines = lines(tree);

        assertEquals("Overworld (minecraft:overworld)  3 lists · 14 waypoints", lines.get(0));
        assertEquals("Home Bases", lines.get(1));
        assertEquals("  [MH] Main Home · 120, 64, -35", lines.get(2));
        assertEquals("  … 4 more: /wp list minecraft:overworld Farms", lines.get(9));
        assertFalse(String.join("\n", lines).contains("Sort"));
        assertTrue(runCommands(tree).isEmpty());
    }

    @Test
    void namesThatNeedQuotingAndTheEmptyIdentifierStayClickable() {
        Viewer player = Fixtures.player();
        WaypointList search = new WaypointList("search", 1, List.of(Fixtures.waypoint("Gate", "G", 0xFFFFFF, 0, 64, 0)));
        WaypointList empty = new WaypointList("", 1, List.of(Fixtures.waypoint("", "E", 0xFFFFFF, 0, 64, 0)));
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(List.of(
                new WaypointQueryEngine.DimensionResult("test:dim", List.of(
                        new WaypointQueryEngine.ListResult(search, search.simpleWaypoints(), true),
                        new WaypointQueryEngine.ListResult(empty, empty.simpleWaypoints(), true)))),
                engineQuery(player, ListQuery.DEFAULT));
        Component lists = ListScreen.dimension(Fixtures.dims(player), "test:dim", new ListScreen.Totals(2, 2), result,
                ListQuery.DEFAULT.withView(ListView.LISTS), 10);
        Component tree = ListScreen.dimension(Fixtures.dims(player), "test:dim", new ListScreen.Totals(2, 2), result,
                ListQuery.DEFAULT, 10);

        assertEquals("/wp list test:dim \"search\"", clickOf(lists, "search"));
        assertEquals("/wp list test:dim \"\"", clickOf(lists, "\"\""));
        assertEquals("/wp details list test:dim \"\"", clickOf(tree, "\"\""));
        assertTrue(runCommands(tree).contains("/wp details waypoint test:dim \"\" \"\""));
    }
}
```

The last assertion in `theHeaderSwitchesDimensionsAndListHeadingsOpenDetails` checks that switching the sort keeps the resolved view: the Tree's sort links carry `view tree`, so a sorted tree stays a tree when it grows past one page.

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.ListScreenTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable ListScreen`.

- [ ] **Step 4: Let Flat keep the saved order under Default sorting**

The Flat view's sort row offers Default (spec 6.4), so Flat with Default shows rows in the saved order instead of switching to Tree.

In `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointListDisplayModel.java`, replace:

```java
        WaypointSorting.SortMode sortMode = result.query().sortMode();
        boolean groupByLists = sortMode == WaypointSorting.SortMode.DEFAULT || requestedGroupByLists;
        List<DisplayList> lists = createDisplayLists(result, sortMode);
        if (groupByLists) {
```

with:

```java
        WaypointSorting.SortMode sortMode = result.query().sortMode();
        List<DisplayList> lists = createDisplayLists(result, sortMode);
        if (requestedGroupByLists) {
```

In `common/src/test/java/_959/server_waypoint/core/waypoint/WaypointListDisplayModelTest.java`, replace:

```java
    @Test
    void defaultSortForcesGroupedLists() {
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.DEFAULT,
                null,
                listResult(list("zeta", waypoint("b", 0, 0, 0)))
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertTrue(display.groupByLists());
        assertEquals(List.of("zeta"), listNames(display));
    }
```

with:

```java
    @Test
    void flatWithDefaultSortKeepsTheSavedOrder() {
        WaypointQueryEngine.QueryResult result = result(
                WaypointSorting.SortMode.DEFAULT,
                null,
                listResult(list("zeta", waypoint("b", 0, 0, 0), waypoint("a", 0, 0, 0))),
                listResult(list("alpha", waypoint("c", 0, 0, 0)))
        );

        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, false);

        assertFalse(display.groupByLists());
        assertEquals(List.of("b", "a", "c"), display.flatWaypoints().stream()
                .map(row -> row.waypoint().name()).toList());
    }
```

and add `import static org.junit.jupiter.api.Assertions.assertFalse;` after the `assertEquals` import.

- [ ] **Step 5: Write `ListActions` and `Errors`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/ListActions.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.util.StringCommandBuilder.escapeArgument;
import static _959.server_waypoint.util.StringCommandBuilder.removeListCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

/** Actions on lists that several screens offer (spec 6.5, 6.7, 8.2). */
public final class ListActions {
    private ListActions() {
    }

    /** Adding to a list: where the viewer stands in their own dimension, by coordinates elsewhere. */
    public static Click addClick(Viewer viewer, String dimension, WaypointList list) {
        return viewer.isIn(dimension)
                ? Click.suggest("/wp add ~ ~ ~ " + escapeArgument(list.name()) + " ")
                : Click.suggest("/wp add " + dimension + " " + escapeArgument(list.name()) + " ");
    }

    public static Tooltip addTooltip(DimensionStyle dims, String dimension, WaypointList list) {
        Component name = WaypointRefs.label(list.displayName(), list.name());
        return dims.viewer().isIn(dimension)
                ? Tooltip.of("wp.add.here.tooltip", name).hint("wp.hint.type_name")
                : Tooltip.of("wp.add.elsewhere.tooltip", name, dims.name(dimension)).hint("wp.hint.type_coordinates");
    }

    /** Add here in the viewer's dimension, Add waypoint elsewhere; null without the add permission. */
    public static @Nullable Component add(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.ADD)) {
            return null;
        }
        return Chat.control(viewer, translatable(viewer.isIn(dimension) ? "wp.add.here" : "wp.add.waypoint"), GREEN,
                addClick(viewer, dimension, list), addTooltip(dims, dimension, list));
    }

    /** The green + after a list name, two spaces after it. */
    public static @Nullable Component plus(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.ADD) || viewer.plainText()) {
            return null;
        }
        return Chat.concat(text("  "), Chat.control(viewer, text("+"), GREEN, addClick(viewer, dimension, list),
                addTooltip(dims, dimension, list)));
    }

    public static @Nullable Component newList(DimensionStyle dims, String dimension) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.ADD)) {
            return null;
        }
        return Chat.control(viewer, translatable("wp.new_list"), GREEN, Click.suggest("/wp add " + dimension + " "),
                Tooltip.of("wp.new_list.tooltip", dims.name(dimension)).hint("wp.hint.type_name"));
    }

    public static @Nullable Component removeList(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        if (!viewer.can(Viewer.Permission.REMOVE)) {
            return null;
        }
        return Chat.control(viewer, translatable("wp.list.remove"), RED,
                Click.suggest(removeListCmd(dimension, list.name(), true)),
                Tooltip.of("wp.list.remove.tooltip").hint("wp.hint.confirm"));
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/feedback/Errors.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import net.kyori.adventure.text.Component;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;

/** One red ✘ line, plus the link that helps recover where one helps (spec 13). */
public final class Errors {
    private Errors() {
    }

    /** ✘ No dimension called x. Dimensions */
    public static Component noDimension(DimensionStyle dims, String dimension) {
        return Chat.error(translatable("wp.error.no_dimension", text(dimension)),
                Chat.control(dims.viewer(), translatable("wp.dimensions.title"), AQUA, Click.run("/wp list dimensions"),
                        Tooltip.of("wp.dimensions.choose")));
    }

    /** ✘ No list called Farm in Overworld. Browse lists */
    public static Component noList(DimensionStyle dims, String dimension, String list) {
        return Chat.error(translatable("wp.error.no_list", text(list), dims.name(dimension)),
                Chat.control(dims.viewer(), translatable("wp.error.browse_lists"), AQUA, Click.run("/wp list " + dimension),
                        Tooltip.of("wp.list.every_list_in", dims.name(dimension))));
    }
}
```

- [ ] **Step 6: Write `ListScreen`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/ListScreen.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.util.StringCommandBuilder.detailsListCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Local list screens (spec 6): Tree, Lists and Flat views, single lists, search and empty states. */
public final class ListScreen {
    static final int COLLAPSE_ABOVE = 5;
    static final int PREVIEW = 3;
    static final List<SortMode> SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.DISTANCE, SortMode.COLOR);

    /** Every list and waypoint of the dimension, whatever the search: what the header counts. */
    public record Totals(int lists, int waypoints) {
    }

    private ListScreen() {
    }

    /** /wp list <dimension> */
    public static Component dimension(DimensionStyle dims, String dimension, Totals totals,
                                      WaypointQueryEngine.QueryResult result, ListQuery query, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        ChatLines lines = new ChatLines().add(header(dims, dimension, totals));
        if (totals.lists() == 0) {
            Component newList = ListActions.newList(dims, dimension);
            return lines.line(translatable("wp.dimension.no_lists.sentence", GRAY), newList == null ? null : text(" "),
                    newList).build();
        }
        if (query.searching() && result.listCount() == 0) {
            return lines.add(ListControls.noMatches(viewer, query.search(), target.command(query.withSearch("")))).build();
        }
        List<WaypointListDisplayModel.DisplayList> groups = WaypointListDisplayModel.build(result, true).lists();
        ListView view = resolveView(query, groups, totals, pageLimit);
        ListQuery shown = query.withView(view).withPage(query.page());
        if (query.searching()) {
            lines.add(ListControls.searchLine(viewer, query.search(), result.waypointCount(),
                    target.command(query.withSearch(""))));
        }
        return switch (view) {
            case LISTS -> listsView(dims, dimension, totals, groups, shown, pageLimit, lines);
            case FLAT -> flatView(dims, dimension, totals, result, shown, pageLimit, lines);
            default -> treeView(dims, dimension, totals, groups, shown, pageLimit, lines);
        };
    }

    /** /wp list <dimension> <list> */
    public static Component list(DimensionStyle dims, String dimension, WaypointList list,
                                 WaypointQueryEngine.QueryResult result, ListQuery query, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.list(dimension, list.name());
        Component crumb = Chat.concat(dimensionLink(dims, dimension), Chat.CRUMB, WaypointRefs.listLink(dims, list, GOLD,
                Click.run(detailsListCmd(dimension, list.name())), "wp.hint.list_details"));
        Component all = Chat.control(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"),
                Tooltip.of("wp.all.tooltip"));
        ChatLines lines = new ChatLines();
        if (list.isEmpty()) {
            lines.line(crumb, all == null ? null : Chat.SEPARATOR, all);
            List<Component> actions = new ArrayList<>();
            actions.add(ListActions.add(dims, dimension, list));
            actions.add(ListActions.removeList(dims, dimension, list));
            return lines.line(translatable("wp.list.empty.sentence", GRAY), Chat.isEmpty(actions) ? null : text(" "),
                    Chat.join(actions)).build();
        }
        lines.line(crumb, text("  "), Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY),
                all == null ? null : Chat.SEPARATOR, all);
        List<SimpleWaypoint> rows = result.dimensions().isEmpty()
                ? List.of()
                : result.dimensions().get(0).lists().get(0).waypoints();
        if (query.searching()) {
            String clear = target.command(query.withSearch(""));
            if (rows.isEmpty()) {
                return lines.add(ListControls.noMatches(viewer, query.search(), clear)).build();
            }
            lines.add(ListControls.searchLine(viewer, query.search(), rows.size(), clear));
        }
        List<List<SimpleWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        for (SimpleWaypoint waypoint : pages.get(query.page() - 1)) {
            lines.add(row(dims, dimension, list, waypoint, false));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        Component actions = viewer.plainText() ? null : Chat.join(
                ListControls.search(viewer, target, translatable("wp.search.list",
                        WaypointRefs.label(list.displayName(), list.name()))),
                ListActions.add(dims, dimension, list));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                actions, sortRow(dims, dimension, target, query));
        return lines.build();
    }

    /** The dimension in its colour, opening its lists: the first step of a breadcrumb. */
    static Component dimensionLink(DimensionStyle dims, String dimension) {
        Viewer viewer = dims.viewer();
        return viewer.plainText() ? dims.name(dimension)
                : Chat.link(viewer, DimensionStyle.displayName(dimension), dims.color(dimension),
                Click.run("/wp list " + dimension),
                Tooltip.of("wp.list.every_list_in", dims.name(dimension)).hint("wp.hint.open"));
    }

    /** Overworld ⏷  3 lists · 12 waypoints · All */
    public static Component header(DimensionStyle dims, String dimension, Totals totals) {
        Viewer viewer = dims.viewer();
        Component switcher = viewer.plainText() ? dims.name(dimension)
                : Chat.link(viewer, Chat.concat(DimensionStyle.displayName(dimension), text(" " + Chat.PICKER)),
                dims.color(dimension), Click.run("/wp list dimensions"),
                dims.tooltip(dimension, DimensionStyle.counts(totals.waypoints(), totals.lists()), "wp.hint.choose_dimension"));
        Component summary = totals.lists() == 0 ? null : Chat.concat(text("  "), Chat.colored(Chat.join(
                Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints())), GRAY));
        Component all = Chat.control(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"),
                Tooltip.of("wp.all.tooltip"));
        return Chat.concat(switcher, summary, all == null ? null : Chat.SEPARATOR, all);
    }

    /** [AB] Name, then the list in Flat rows, then the distance (or the coordinates in plain text). */
    public static Component row(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                boolean withList) {
        Component detail = WaypointRefs.rowDetail(dims.viewer(), dimension, waypoint.pos());
        return Chat.join(WaypointRefs.reference(dims, dimension, list, waypoint),
                withList ? WaypointRefs.listLink(dims, list, GRAY,
                        Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open") : null,
                detail);
    }

    /** The view to show: Tree when the dimension fits on one page or a search runs, otherwise Lists. Remote lists use it too. */
    static ListView resolveView(ListQuery query, List<WaypointListDisplayModel.DisplayList> groups, Totals totals,
                                        int pageLimit) {
        ListView view = query.view();
        if (view == ListView.FLAT && totals.waypoints() == 0) {
            view = ListView.TREE;
        }
        if (view == ListView.DEFAULT || view == ListView.LISTS && query.searching()) {
            view = query.searching() || treePages(groups, pageLimit).size() <= 1 ? ListView.TREE : ListView.LISTS;
        }
        return view;
    }

    static List<List<WaypointListDisplayModel.DisplayList>> treePages(
            List<WaypointListDisplayModel.DisplayList> groups, int pageLimit) {
        return Paging.byLines(groups, ListScreen::treeLines, pageLimit + 5);
    }

    /** A list's heading plus its rows: 4 when collapsed, at least 1 (spec 6.3). */
    static int treeLines(WaypointListDisplayModel.DisplayList group) {
        int rows = group.waypoints().size();
        return 1 + (rows > COLLAPSE_ABOVE ? PREVIEW + 1 : Math.max(1, rows));
    }

    private static Component treeView(DimensionStyle dims, String dimension, Totals totals,
                                      List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        List<List<WaypointListDisplayModel.DisplayList>> pages = treePages(groups, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.line(WaypointRefs.listLink(dims, list, WHITE, Click.run(detailsListCmd(dimension, list.name())),
                    "wp.hint.list_details"), ListActions.plus(dims, dimension, list));
            List<SimpleWaypoint> rows = group.waypoints();
            if (rows.isEmpty()) {
                lines.line(text("  "), translatable("wp.list.empty", GRAY).decorate(TextDecoration.ITALIC));
            }
            boolean collapsed = rows.size() > COLLAPSE_ABOVE;
            for (SimpleWaypoint waypoint : collapsed ? rows.subList(0, PREVIEW) : rows) {
                lines.line(text("  "), row(dims, dimension, list, waypoint, false));
            }
            if (collapsed) {
                lines.line(text("  "), collapsedMore(dims, dimension, list, rows.size(), shown, pageLimit));
            }
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(), Chat.join(
                        Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints()))),
                viewRow(dims, dimension, totals, shown, ListView.TREE),
                totals.waypoints() > 0 ? sortRow(dims, dimension, target, shown) : null);
        return lines.build();
    }

    private static Component listsView(DimensionStyle dims, String dimension, Totals totals,
                                       List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                       ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        List<List<WaypointListDisplayModel.DisplayList>> pages = Paging.bySize(groups, pageLimit + 5);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.line(WaypointRefs.listLink(dims, list, WHITE,
                            Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open"),
                    Chat.SEPARATOR, text(String.valueOf(list.size()), GRAY), ListActions.plus(dims, dimension, list));
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", totals.lists()))),
                viewRow(dims, dimension, totals, shown, ListView.LISTS));
        return lines.build();
    }

    private static Component flatView(DimensionStyle dims, String dimension, Totals totals,
                                      WaypointQueryEngine.QueryResult result, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        List<WaypointListDisplayModel.DisplayWaypoint> rows = WaypointListDisplayModel.build(result, false).flatWaypoints();
        List<List<WaypointListDisplayModel.DisplayWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayWaypoint row : pages.get(shown.page() - 1)) {
            lines.add(row(dims, dimension, row.sourceList(), row.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                viewRow(dims, dimension, totals, shown, ListView.FLAT), sortRow(dims, dimension, target, shown));
        return lines.build();
    }

    /** "… 4 more" opening the whole list, keeping the search and the sort. */
    private static Component collapsedMore(DimensionStyle dims, String dimension, WaypointList list, int rows,
                                           ListQuery shown, int pageLimit) {
        Viewer viewer = dims.viewer();
        String command = ListTarget.list(dimension, list.name()).command(shown.withView(ListView.DEFAULT));
        Component label = Chat.concat(text(Chat.ELLIPSIS + " "), translatable("wp.more.rows", text(rows - PREVIEW)));
        if (viewer.plainText()) {
            return translatable("wp.plain.continue", label, text(command));
        }
        return Chat.link(viewer, label, AQUA, Click.run(command),
                Tooltip.of("wp.open", WaypointRefs.label(list.displayName(), list.name()))
                        .line(translatable("wp.list.all_rows", Chat.count("wp.count.waypoint", rows), text(pageLimit))));
    }

    /** Lists · Tree · Flat · Search · New list; Flat and Search only when there are waypoints. */
    private static @Nullable Component viewRow(DimensionStyle dims, String dimension, Totals totals, ListQuery shown,
                                               ListView current) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        boolean waypoints = totals.waypoints() > 0;
        List<Component> extras = new ArrayList<>();
        if (waypoints) {
            extras.add(ListControls.search(viewer, target, translatable("wp.search.in", dims.name(dimension))));
        }
        extras.add(ListActions.newList(dims, dimension));
        return ListControls.viewRow(viewer, target, shown, current,
                waypoints ? List.of(ListView.LISTS, ListView.TREE, ListView.FLAT) : List.of(ListView.LISTS, ListView.TREE),
                extras);
    }

    private static @Nullable Component sortRow(DimensionStyle dims, String dimension, ListTarget target, ListQuery shown) {
        Viewer viewer = dims.viewer();
        return ListControls.sortRow(viewer, target, shown, SORTS, "wp.sort.default.saved",
                viewer.isIn(dimension) ? null : translatable("wp.sort.distance.unavailable", dims.name(dimension)));
    }
}
```

- [ ] **Step 7: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 8: Accept `view lists` and a trailing page in the grammar**

Replace the whole of `common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java` with:

```java
package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.text.chat.ListView;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

/**
 * Shared local/remote list grammar; only execution and exact-identity routing differ. Options come
 * in the order search, sort, order, page, limit, view, with search also allowed last and page also
 * allowed after limit and view, which is where the commands the screens build put it.
 */
final class ListCommandOptions<S> {
    @FunctionalInterface interface Factory<S> {
        Command<S> create(WaypointSorting.SortMode mode, boolean reversed, ListView view);
    }
    private static final List<ListView> VIEWS = List.of(ListView.LISTS, ListView.TREE, ListView.FLAT);
    private final Factory<S> factory;
    private final Function<String, Command<S>> reserved;
    ListCommandOptions(Factory<S> factory, Function<String, Command<S>> reserved) {
        this.factory = factory; this.reserved = reserved;
    }
    private Command<S> command(WaypointSorting.SortMode mode, boolean reversed) { return command(mode, reversed, ListView.DEFAULT); }
    private Command<S> command(WaypointSorting.SortMode mode, boolean reversed, ListView view) {
        return factory.create(mode, reversed, view);
    }
    void configure(ArgumentBuilder<S, ?> targetNode) {
        targetNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = listSearchNode();
        LiteralArgumentBuilder<S> sortNode = listSortNode();
        LiteralArgumentBuilder<S> pageNode = listPageNode(WaypointSorting.SortMode.DEFAULT, false);
        LiteralArgumentBuilder<S> limitNode = listLimitNode(WaypointSorting.SortMode.DEFAULT, false);
        LiteralArgumentBuilder<S> viewNode = listViewNode(WaypointSorting.SortMode.DEFAULT, false);
        if (reserved != null) {
            searchNode.executes(reserved.apply(SEARCH_COMMAND));
            sortNode.executes(reserved.apply(SORT_COMMAND));
            pageNode.executes(reserved.apply(PAGE_COMMAND));
            limitNode.executes(reserved.apply(LIMIT_COMMAND));
            viewNode.executes(reserved.apply(VIEW_COMMAND));
        }
        targetNode.then(searchNode);
        targetNode.then(sortNode);
        targetNode.then(pageNode);
        targetNode.then(limitNode);
        targetNode.then(viewNode);
    }

    private LiteralArgumentBuilder<S> listSearchNode() {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(command(WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listSortNode());
        queryNode.then(listPageNode(WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listLimitNode(WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listViewNode(WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> trailingListSearchNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListView view
    ) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(command(sortMode, reversed, view));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    /** "page <n>" as the last option, after limit or view. */
    private LiteralArgumentBuilder<S> trailingPageNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListView view
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command(sortMode, reversed, view));
        pageNode.then(trailingListSearchNode(sortMode, reversed, view));
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listViewNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        LiteralArgumentBuilder<S> viewNode = literal(VIEW_COMMAND);
        for (ListView view : VIEWS) {
            LiteralArgumentBuilder<S> valueNode = literal(view.id());
            valueNode.executes(command(sortMode, reversed, view));
            valueNode.then(trailingListSearchNode(sortMode, reversed, view));
            valueNode.then(trailingPageNode(sortMode, reversed, view));
            viewNode.then(valueNode);
        }
        return viewNode;
    }

    private LiteralArgumentBuilder<S> listSortNode() {
        LiteralArgumentBuilder<S> sortNode = literal(SORT_COMMAND);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            LiteralArgumentBuilder<S> modeNode = literal(sortMode.name().toLowerCase(Locale.ROOT));
            modeNode.executes(command(sortMode, false));
            if (sortMode != WaypointSorting.SortMode.DEFAULT) {
                modeNode.then(listOrderNode(sortMode));
            }
            modeNode.then(trailingListSearchNode(sortMode, false, ListView.DEFAULT));
            modeNode.then(listPageNode(sortMode, false));
            modeNode.then(listLimitNode(sortMode, false));
            modeNode.then(listViewNode(sortMode, false));
            sortNode.then(modeNode);
        }
        return sortNode;
    }

    private LiteralArgumentBuilder<S> listOrderNode(
            WaypointSorting.SortMode sortMode
    ) {
        LiteralArgumentBuilder<S> orderNode = literal(ORDER_COMMAND);
        for (boolean descending : new boolean[]{false, true}) {
            LiteralArgumentBuilder<S> directionNode = literal(descending ? "descending" : "ascending");
            directionNode.executes(command(sortMode, descending));
            directionNode.then(trailingListSearchNode(sortMode, descending, ListView.DEFAULT));
            directionNode.then(listPageNode(sortMode, descending));
            directionNode.then(listLimitNode(sortMode, descending));
            directionNode.then(listViewNode(sortMode, descending));
            orderNode.then(directionNode);
        }
        return orderNode;
    }

    private LiteralArgumentBuilder<S> listPageNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(command(sortMode, reversed));
        pageNode.then(trailingListSearchNode(sortMode, reversed, ListView.DEFAULT));
        pageNode.then(listLimitNode(sortMode, reversed));
        pageNode.then(listViewNode(sortMode, reversed));
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listLimitNode(
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> limitNode = argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT));
        limitNode.executes(command(sortMode, reversed));
        limitNode.then(trailingListSearchNode(sortMode, reversed, ListView.DEFAULT));
        limitNode.then(listViewNode(sortMode, reversed));
        limitNode.then(trailingPageNode(sortMode, reversed, ListView.DEFAULT));
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }
}
```

In `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`, replace:

```java
        new ListCommandOptions<S>((mode, reversed, grouped) -> context -> execute(context, depth, mode, reversed, grouped), null).configure(node);
```

with:

```java
        new ListCommandOptions<S>((mode, reversed, view) -> context -> execute(context, depth, mode, reversed,
                view != _959.server_waypoint.text.chat.ListView.FLAT), null).configure(node);
```

- [ ] **Step 9: Wire the screens into the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
```

with:

```java
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.Errors;
import _959.server_waypoint.text.feedback.ListScreen;
```

Make the four target-less roots show the hint. Replace:

```java
    private LiteralArgumentBuilder<S> detailsCommandNode() {
        LiteralArgumentBuilder<S> root = literal(DETAILS_COMMAND);
```

with:

```java
    private LiteralArgumentBuilder<S> detailsCommandNode() {
        LiteralArgumentBuilder<S> root = literal(DETAILS_COMMAND);
        root.executes(context -> executeTargetHint(context.getSource(), "wp.hint_line.details", HelpTopics.Topic.LIST));
```

replace:

```java
        LiteralArgumentBuilder<S> root = literal(EDIT_COMMAND);
        root.requires(this::hasEditPermission);
```

with:

```java
        LiteralArgumentBuilder<S> root = literal(EDIT_COMMAND);
        root.requires(this::hasEditPermission);
        root.executes(context -> executeTargetHint(context.getSource(), "wp.hint_line.edit", HelpTopics.Topic.EDIT));
```

replace:

```java
                .then(literal(REMOVE_COMMAND)
                        .requires(source -> hasRemovePermission((S) source))
```

with:

```java
                .then(literal(REMOVE_COMMAND)
                        .requires(source -> hasRemovePermission((S) source))
                        .executes(context -> executeTargetHint((S) context.getSource(), "wp.hint_line.remove",
                                HelpTopics.Topic.REMOVE))
```

and replace:

```java
                .then(literal(TP_COMMAND)
                        .requires(source -> hasTpPermission((S) source))
```

with:

```java
                .then(literal(TP_COMMAND)
                        .requires(source -> hasTpPermission((S) source))
                        .executes(context -> executeTargetHint((S) context.getSource(), "wp.hint_line.tp",
                                HelpTopics.Topic.TP))
```

Let `/wp add <dimension> <list> <position> <name>` run on its own. Replace:

```java
                                        .then(argument(POS_ARG, blockPosArgumentProvider.get())
                                                .then(argument(WAYPOINT_NAME_ARG, string())
                                                        .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                                                        .then(argument(INITIALS_ARG, string())
```

with:

```java
                                        .then(argument(POS_ARG, blockPosArgumentProvider.get())
                                                .then(argument(WAYPOINT_NAME_ARG, string())
                                                        .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                                                        .executes(cxt -> {
                                                            CommandContext<S> context = (CommandContext<S>) cxt;
                                                            executeQuickAddWaypoint(
                                                                    context.getSource(),
                                                                    getArgument(context, DIMENSION_ARG),
                                                                    getArgument(context, POS_ARG),
                                                                    getString(context, LIST_NAME_ARG),
                                                                    getString(context, WAYPOINT_NAME_ARG)
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                        .then(argument(INITIALS_ARG, string())
```

and replace:

```java
    private void executeQuickAddWaypoint(S source, B blockPosArgument, String listName, String name) {
        addWaypointDirectly(source, toDimensionName(getSourceDimension(source)), listName, name, WaypointInitials.getDefaultInitials(plainText(name)), toWaypointPos(source, blockPosArgument), Math.round(getSourceYaw(source)), randomColor(), true, List.of(), "", null);
    }
```

with:

```java
    private void executeQuickAddWaypoint(S source, B blockPosArgument, String listName, String name) {
        executeQuickAddWaypoint(source, getSourceDimension(source), blockPosArgument, listName, name);
    }

    /** Adds with the defaults: initials from the name, a random colour and the source's facing. */
    private void executeQuickAddWaypoint(S source, D dimensionArgument, B blockPosArgument, String listName, String name) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
        WaypointPos position = toWaypointPos(source, blockPosArgument);
        if (position == null) {
            sendPosArgumentError(source);
            return;
        }
        addWaypointDirectly(source, dimensionName, listName, name, WaypointInitials.getDefaultInitials(plainText(name)),
                position, Math.round(getSourceYaw(source)), randomColor(), true, List.of(), "", null);
    }
```

Route the list commands through the new screens. Replace:

```java
    private void configureListTarget(ArgumentBuilder<S, ?> targetNode, ListScope scope) {
        new ListCommandOptions<S>((mode, reversed, grouped) -> listCommand(scope, mode, reversed, grouped),
                scope == ListScope.DIMENSION ? this::reservedListCommand : null).configure(targetNode);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        return listCommand(scope, sortMode, reversed, true);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            boolean groupByLists
    ) {
        return context -> {
            WaypointSorting.SortMode resolvedSortMode = !groupByLists
                    && sortMode == WaypointSorting.SortMode.DEFAULT
                    ? WaypointSorting.SortMode.NAME
                    : sortMode;
            executeList(context, scope, resolvedSortMode, reversed, null, groupByLists);
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<S> reservedListCommand(String listName) {
        return context -> {
            executeList(
                    context,
                    ListScope.WAYPOINT_LIST,
                    WaypointSorting.SortMode.DEFAULT,
                    false,
                    listName,
                    true
            );
            return Command.SINGLE_SUCCESS;
        };
    }
```

with:

```java
    private void configureListTarget(ArgumentBuilder<S, ?> targetNode, ListScope scope) {
        new ListCommandOptions<S>((mode, reversed, view) -> listCommand(scope, mode, reversed, view),
                scope == ListScope.DIMENSION ? this::reservedListCommand : null).configure(targetNode);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListView view
    ) {
        return context -> {
            executeList(context, scope, sortMode, reversed, view, null);
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<S> reservedListCommand(String listName) {
        return context -> {
            executeList(context, ListScope.WAYPOINT_LIST, WaypointSorting.SortMode.DEFAULT, false,
                    ListView.DEFAULT, listName);
            return Command.SINGLE_SUCCESS;
        };
    }
```

Replace the old `executeList` and `executeListDimension`:

```java
    private void executeList(
            CommandContext<S> context,
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            String fixedListName,
            boolean groupByLists
    ) {
        S source = context.getSource();
        ListOptions options = new ListOptions(
                getOptionalString(context, SEARCH_QUERY_ARG, ""),
                sortMode,
                reversed,
                getOptionalInteger(context, PAGE_NUMBER_ARG, 1),
                getOptionalInteger(context, PAGE_LIMIT_ARG, CONFIG.defaultPageLimit()),
                groupByLists
        );
        if (scope == ListScope.ALL_DIMENSIONS) {
            WaypointQueryEngine.Query query = createListQuery(source, options);
            sendListQueryResult(
                    source,
                    new ListTarget(true, null, null),
                    options,
                    this.waypointQueryEngine.queryAll(query)
            );
            return;
        }

        D dimensionArgument = scope == ListScope.CURRENT_DIMENSION
                ? getSourceDimension(source)
                : getArgument(context, DIMENSION_ARG);
        String listName = fixedListName != null
                ? fixedListName
                : scope == ListScope.WAYPOINT_LIST ? getString(context, LIST_NAME_ARG) : null;
        executeListDimension(source, dimensionArgument, listName, options);
    }
```

with:

```java
    private void executeList(
            CommandContext<S> context,
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListView view,
            @Nullable String fixedListName
    ) {
        S source = context.getSource();
        ListQuery query = new ListQuery(getOptionalString(context, SEARCH_QUERY_ARG, ""), sortMode, reversed, view,
                getOptionalInteger(context, PAGE_NUMBER_ARG, 1), optionalLimit(context));
        if (scope == ListScope.ALL_DIMENSIONS) {
            ListOptions options = new ListOptions(query.search(), sortMode, query.descending(), query.page(),
                    query.pageLimit(CONFIG.defaultPageLimit()), view != ListView.FLAT);
            sendListQueryResult(
                    source,
                    new ListTarget(true, null, null),
                    options,
                    this.waypointQueryEngine.queryAll(createListQuery(source, options))
            );
            return;
        }
        D dimensionArgument = scope == ListScope.CURRENT_DIMENSION
                ? getSourceDimension(source)
                : getArgument(context, DIMENSION_ARG);
        String listName = fixedListName != null
                ? fixedListName
                : scope == ListScope.WAYPOINT_LIST ? getString(context, LIST_NAME_ARG) : null;
        this.sender.sendMessage(source, listScreen(source, dimensionArgument, listName, query));
    }

    private @Nullable Integer optionalLimit(CommandContext<S> context) {
        try {
            return getInteger(context, PAGE_LIMIT_ARG);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    /** A dimension's or a list's screen, or the error that explains why there is none. */
    private Component listScreen(S source, D dimensionArgument, @Nullable String listName, ListQuery query) {
        Viewer viewer = viewer(source);
        DimensionStyle dims = dimensions(source, viewer);
        String dimension = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            return Errors.noDimension(dims, dimension);
        }
        int pageLimit = query.pageLimit(CONFIG.defaultPageLimit());
        WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimension);
        WaypointQueryEngine.Query engineQuery = engineQuery(source, query);
        if (listName == null) {
            List<WaypointList> lists = fileManager == null ? List.of() : fileManager.getWaypointLists();
            ListScreen.Totals totals = new ListScreen.Totals(lists.size(),
                    lists.stream().mapToInt(WaypointList::size).sum());
            return ListScreen.dimension(dims, dimension, totals,
                    this.waypointQueryEngine.queryDimension(dimension, engineQuery), query, pageLimit);
        }
        WaypointList list = fileManager == null ? null : fileManager.getWaypointListByName(listName);
        if (list == null) {
            return Errors.noList(dims, dimension, listName);
        }
        return ListScreen.list(dims, dimension, list,
                this.waypointQueryEngine.queryList(dimension, listName, engineQuery), query, pageLimit);
    }

    /** The search and sort of a list query, measured from the source's position. */
    private WaypointQueryEngine.Query engineQuery(S source, ListQuery query) {
        return new WaypointQueryEngine.Query(query.search(), query.sort(), getSourcePosition(source),
                toDimensionName(getSourceDimension(source)), query.descending());
    }

    /** /wp tp, remove, edit and details without a target: this dimension's list and what to click in it. */
    private int executeTargetHint(S source, String hintKey, HelpTopics.Topic plainTopic) {
        Viewer viewer = viewer(source);
        if (viewer.plainText()) {
            this.sender.sendMessage(source, HelpScreen.topic(viewer, plainTopic,
                    isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)));
            return Command.SINGLE_SUCCESS;
        }
        this.sender.sendMessage(source, new ChatLines()
                .add(translatable(hintKey, NamedTextColor.GRAY))
                .add(listScreen(source, getSourceDimension(source), null, ListQuery.DEFAULT))
                .build());
        return Command.SINGLE_SUCCESS;
    }
```

Then delete the now unused `executeListDimension` method (it starts with `private void executeListDimension(` and ends before `private void sendListQueryResult(`).

- [ ] **Step 10: Write the failing command tests**

In `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void listCommandsShowTheNewScreensAndTheirLinksRunAgain() {
        this.harness.addList("minecraft:overworld", "Farms",
                CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();

        assertEquals("Overworld ⏷  1 list · 1 waypoint · All", lines(this.harness.run(player, "wp list")).get(0));
        for (String command : List.of("wp list minecraft:overworld view lists", "wp list minecraft:overworld view tree page 1",
                "wp list minecraft:overworld search iron sort name order descending limit 5 view flat page 1",
                "wp list minecraft:overworld Farms limit 5 page 1")) {
            Component screen = this.harness.run(player, command);
            for (String click : runCommands(screen)) {
                if (click.startsWith("/wp list")) {
                    this.harness.run(player, click.substring(1));
                }
            }
        }
        assertEquals("✘ No list called Farm in Overworld. Browse lists",
                lines(this.harness.run(player, "wp list minecraft:overworld Farm")).get(0));
        assertEquals("✘ No dimension called test:missing. Dimensions",
                lines(this.harness.run(player, "wp list test:missing")).get(0));
    }

    @Test
    void reservedAndEmptyListNamesAreQuotedInTheirPageLinks() {
        List<SimpleWaypoint> pillars = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            pillars.add(CommandHarness.waypoint("Pillar " + index, "P", 0xFFFFFF, index, 64, 0));
        }
        this.harness.addList("minecraft:overworld", "search", pillars.toArray(SimpleWaypoint[]::new));
        this.harness.addList("minecraft:overworld", "", pillars.toArray(SimpleWaypoint[]::new));
        CommandHarness.Source player = CommandHarness.player();

        assertTrue(runCommands(this.harness.run(player, "wp list minecraft:overworld \"search\" limit 5"))
                .contains("/wp list minecraft:overworld \"search\" limit 5 page 2"));
        assertTrue(runCommands(this.harness.run(player, "wp list minecraft:overworld \"\""))
                .contains("/wp list minecraft:overworld \"\" page 2"));
        this.harness.run(player, "wp list minecraft:overworld \"search\" limit 5 page 2");
    }

    @Test
    void theConfiguredPageLimitStaysOutOfTheCommands() {
        this.harness.server.loadConfig(new java.io.StringReader("{\"defaultPageLimit\": 4}"));
        List<SimpleWaypoint> pillars = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            pillars.add(CommandHarness.waypoint("Pillar " + index, "P", 0xFFFFFF, index, 64, 0));
        }
        this.harness.addList("minecraft:overworld", "bases", pillars.toArray(SimpleWaypoint[]::new));

        assertTrue(runCommands(this.harness.run(CommandHarness.player(), "wp list minecraft:overworld bases"))
                .contains("/wp list minecraft:overworld bases page 2"));
        assertTrue(runCommands(this.harness.run(CommandHarness.player(), "wp list minecraft:overworld bases limit 5"))
                .contains("/wp list minecraft:overworld bases limit 5 page 2"));
    }

    @Test
    void commandsWithoutATargetShowThisDimensionAndWhatToClick() {
        this.harness.addList("minecraft:overworld", "Farms",
                CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));

        List<String> tp = lines(this.harness.run(CommandHarness.player(), "wp tp"));
        assertEquals("Click a waypoint's initials to teleport.", tp.get(0));
        assertEquals("Overworld ⏷  1 list · 1 waypoint · All", tp.get(1));
        assertEquals("Click a waypoint's name, then Remove.", lines(this.harness.run(CommandHarness.player(), "wp remove")).get(0));
        assertEquals("Click a waypoint's name to edit it.", lines(this.harness.run(CommandHarness.player(), "wp edit")).get(0));
        assertEquals("Click a waypoint's name for details.", lines(this.harness.run(CommandHarness.player(), "wp details")).get(0));
        assertEquals("Remove", lines(this.harness.run(CommandHarness.console(), "wp remove")).get(0));
    }

    @Test
    void quickAddWorksInAnyDimension() {
        this.harness.addList("minecraft:the_nether", "Hub");

        this.harness.run(CommandHarness.player(), "wp add minecraft:the_nether Hub 1 64 2 Portal");

        assertEquals(new WaypointPos(1, 64, 2), this.harness.server.getWaypointFileManager("minecraft:the_nether")
                .getWaypointListByName("Hub").getWaypointByName("Portal").pos());
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

and replace:

```java
import java.nio.file.Path;
import java.util.Set;
```

with:

```java
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
```

and replace:

```java
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
```

with:

```java
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
```

- [ ] **Step 11: Retire the old dimension and list tests**

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`, delete `listFeedbackSuggestsSearchForTheCurrentTarget`, `reservedListNameIsQuotedInSuggestionsAndPageLinks`, `configuredDefaultPageLimitIsUsedUnlessTheCommandOverridesIt` and `sortControlsAreAvailableOnOnePageAndDefaultOrderIsDisabled`; `ListScreenTest` and `CommandFeedbackTest` cover them. Remove imports the deletions leave unused.

- [ ] **Step 12: Run the common tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 13: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/feedback common/src/test/java/_959/server_waypoint/text/feedback common/src/main/java/_959/server_waypoint/command common/src/test/java/_959/server_waypoint/command common/src/main/java/_959/server_waypoint/core/waypoint/WaypointListDisplayModel.java common/src/test/java/_959/server_waypoint/core/waypoint/WaypointListDisplayModelTest.java common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Show dimensions and lists as trees, lists and flat rows with line-budget pages"
```

---

### Task 10: The dimension list and all dimensions

Spec 7: `/wp list dimensions` lists every dimension of the server, the viewer's first and those without waypoints on one shared line; `/wp list all` shows every dimension with lists, a heading and one row per list, paged by blocks with continued headings; `/wp list all search` groups matches under dimension headings. The old list rendering, `WaypointListPage` and its options adapter go away.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/DimensionScreens.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Delete: `common/src/main/java/_959/server_waypoint/command/WaypointListPage.java`, `common/src/test/java/_959/server_waypoint/command/WaypointListPageTest.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/DimensionScreensTest.java`; `CommandFeedbackTest.java`
- Modify (tests): `CoreWaypointCommandListTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `WaypointRefs`, `ListScreen.row` (Task 9), `CoreWaypointCommand.engineQuery` (Task 9).
- Produces:
  - `DimensionScreens.DimensionLists(String dimension, List<WaypointList> lists)` with `int waypoints()`.
  - `static Component dimensionList(DimensionStyle, List<DimensionLists>, int page, int pageLimit)`, `static Component all(DimensionStyle, List<DimensionLists>, ListQuery, int pageLimit)`, `static Component allSearch(DimensionStyle, WaypointQueryEngine.QueryResult, ListQuery, int pageLimit)`.
  - `CoreWaypointCommand`: `List<DimensionScreens.DimensionLists> knownDimensions(S source)` (loaded dimensions plus those with waypoint files), `/wp list dimensions [page <n>]`.
- Keys: `wp.dimensions.on_server`, `wp.dimensions.no_waypoints`, `wp.all.title`, `wp.all.continued`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.dimensions.on_server": "{0} on this server",
  "wp.dimensions.no_waypoints": "No waypoints yet:",
  "wp.all.title": "All dimensions",
  "wp.all.continued": "(continued)"
```

`zh_cn.json`:

```json
  "wp.dimensions.on_server": "此服务器共 {0} 个",
  "wp.dimensions.no_waypoints": "还没有路径点：",
  "wp.all.title": "所有维度",
  "wp.all.continued": "（续）"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.dimensions.on_server": "此伺服器共 {0} 個",
  "wp.dimensions.no_waypoints": "還沒有路徑點：",
  "wp.all.title": "所有維度",
  "wp.all.continued": "（續）"
```

`es_es.json`:

```json
  "wp.dimensions.on_server": "{0} en este servidor",
  "wp.dimensions.no_waypoints": "Aún sin puntos de ruta:",
  "wp.all.title": "Todas las dimensiones",
  "wp.all.continued": "(continuación)"
```

`he_il.json`:

```json
  "wp.dimensions.on_server": "{0} בשרת הזה",
  "wp.dimensions.no_waypoints": "עדיין ללא נקודות ציון:",
  "wp.all.title": "כל הממדים",
  "wp.all.continued": "(המשך)"
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/DimensionScreensTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static _959.server_waypoint.text.feedback.Fixtures.TWILIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionScreensTest {
    @TempDir
    Path directory;

    static List<DimensionScreens.DimensionLists> dimensions() {
        return List.of(
                new DimensionScreens.DimensionLists(OVERWORLD, Fixtures.overworldLists()),
                new DimensionScreens.DimensionLists(NETHER, List.of(new WaypointList("Nether Hub", 1, List.of(
                        Fixtures.waypoint("Hub", "NH", 0xFF5555, 12, 64, -4),
                        Fixtures.waypoint("Fortress", "NF", 0xAA0000, 180, 70, 44),
                        Fixtures.waypoint("Bastion", "BR", 0xFFAA00, -220, 40, 90),
                        Fixtures.waypoint("Blaze Farm", "BF", 0xFFFF55, 175, 72, 60))),
                        new WaypointList("Storage", 1, List.of()))),
                new DimensionScreens.DimensionLists(END, List.of(new WaypointList("End", 1, List.of(
                        Fixtures.waypoint("Main Island", "MI", 0xFF55FF, 0, 60, 0))))),
                new DimensionScreens.DimensionLists(TWILIGHT, List.of(new WaypointList("Bosses", 1, List.of(
                        Fixtures.waypoint("Naga Courtyard", "NC", 0x55FF55, 40, 5, 60),
                        Fixtures.waypoint("Lich Tower", "LT", 0xAAAAAA, 300, 20, -120),
                        Fixtures.waypoint("Hydra Lair", "HL", 0xFF5555, 900, 10, 700))))),
                new DimensionScreens.DimensionLists("aether:the_aether", List.of(new WaypointList("Sky", 1, List.of(
                        Fixtures.waypoint("Bronze Dungeon", "BD", 0xFFAA00, 80, 120, 30),
                        Fixtures.waypoint("Silver Dungeon", "SD", 0xAAAAAA, -300, 140, 210))))),
                new DimensionScreens.DimensionLists("ad_astra:mars", List.of()),
                new DimensionScreens.DimensionLists("undergarden:undergarden", List.of(new WaypointList("Camps", 1, List.of()))));
    }

    /** Everything is loaded except the Aether, which only has waypoint files. */
    static DimensionStyle dims(Viewer viewer) {
        Map<String, String> loaded = new HashMap<>(Fixtures.LOADED);
        loaded.put("ad_astra:mars", "ad_astra:mars");
        loaded.put("undergarden:undergarden", "undergarden:undergarden");
        return DimensionStyle.local(viewer, loaded);
    }

    @Test
    void theDimensionListPutsTheViewersDimensionFirstAndEmptyOnesOnOneLine() {
        Viewer inTwilight = Fixtures.in(Fixtures.player(), TWILIGHT);
        Component list = DimensionScreens.dimensionList(dims(inTwilight), dimensions(), 1, 10);

        assertEquals(List.of(
                "Dimensions  7 on this server",
                "Twilight Forest · 3 ●",
                "Overworld · 14",
                "Nether · 4",
                "End · 1",
                "The Aether · 2",
                "No waypoints yet: Mars · Undergarden",
                "All dimensions · 24"), lines(list));
        assertEquals("/wp list minecraft:overworld", clickOf(list, "Overworld"));
        assertEquals(NamedTextColor.GRAY, colorOf(list, "The Aether"));
        assertTrue(tooltipOf(list, "The Aether").contains("Not loaded"));
        assertEquals("Nether\nminecraft:the_nether\nNether type\n4 waypoints in 2 lists\nClick to open", tooltipOf(list, "Nether"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(list, "No waypoints yet:"));
        assertEquals(NamedTextColor.GRAY, colorOf(list, "Mars"));
        assertEquals("/wp list ad_astra:mars", clickOf(list, "Mars"));
        assertEquals(NamedTextColor.GOLD, colorOf(list, "●"));
        assertEquals("/wp list all", clickOf(list, "All dimensions"));
        ChatAssert.assertFitsChat(list);
    }

    @Test
    void manyDimensionsPageByRows() {
        List<DimensionScreens.DimensionLists> many = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            many.add(new DimensionScreens.DimensionLists("mod:world_" + (char) ('a' + index), List.of(
                    new WaypointList("List", 1, List.of(Fixtures.waypoint("Spot", "S", 0xFFFFFF, 0, 64, 0))))));
        }
        List<String> lines = lines(DimensionScreens.dimensionList(Fixtures.dims(Fixtures.player()), many, 1, 10));

        assertEquals(18, lines.size());
        assertEquals("… 5 more dimensions", lines.get(16));
        assertEquals("All dimensions · 20    ‹ 1/2 ›", lines.get(17));
        assertEquals(List.of("✘ Page 3 does not exist; the last page is 2. Last page"),
                lines(DimensionScreens.dimensionList(Fixtures.dims(Fixtures.player()), many, 3, 10)));
    }

    @Test
    void allDimensionsShowsEveryDimensionWithListsAndOneRowPerList() {
        Component all = DimensionScreens.all(dims(Fixtures.player()), dimensions(), ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "All dimensions ⏷",
                "Overworld · 14 ●",
                "  Home Bases · 3",
                "  Farms · 7",
                "  Exploration · 4",
                "Nether · 4",
                "  Nether Hub · 4",
                "  Storage · 0",
                "End · 1",
                "  End · 1",
                "Twilight Forest · 3",
                "  Bosses · 3",
                "The Aether · 2",
                "  Sky · 2",
                "Undergarden · 0",
                "  Camps · 0",
                "Search"), lines(all));
        assertEquals("/wp list dimensions", clickOf(all, "All dimensions ⏷"));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(all, "Farms"));
        assertEquals("/wp list all search ", clickOf(all, "Search"));
        ChatAssert.assertFitsChat(all);
    }

    @Test
    void aPageThatStartsInsideADimensionRepeatsItsHeading() {
        List<WaypointList> lists = new ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            lists.add(new WaypointList("List " + index, 1, List.of()));
        }
        List<DimensionScreens.DimensionLists> one = List.of(new DimensionScreens.DimensionLists(OVERWORLD, lists));
        Component second = DimensionScreens.all(Fixtures.dims(Fixtures.player()), one, ListQuery.DEFAULT.withPage(2), 2);
        Component first = DimensionScreens.all(Fixtures.dims(Fixtures.player()), one, ListQuery.DEFAULT, 2);

        assertEquals(List.of("All dimensions ⏷", "Overworld (continued) ●", "  List 7 · 0", "  List 8 · 0", "  List 9 · 0",
                "Search    ‹ 2/2 ›"), lines(second));
        assertEquals("… 3 more lists", lines(first).get(8));
        assertEquals("/wp list all page 2", clickOf(first, "… 3 more lists"));
    }

    @Test
    void searchingEveryDimensionGroupsMatchesUnderTheirDimension() {
        WaypointServerCore server = new WaypointServerCore(this.directory);
        Fixtures.overworldLists().forEach(list -> server.putWaypointList(OVERWORLD, list));
        Viewer player = Fixtures.player();
        ListQuery query = ListQuery.DEFAULT.withSearch("farm");
        Component search = DimensionScreens.allSearch(Fixtures.dims(player), new WaypointQueryEngine(server).queryAll(
                new WaypointQueryEngine.Query("farm", query.sort(), player.position(), player.dimension(), false)), query, 10);

        assertEquals(List.of(
                "All dimensions ⏷",
                "Search \"farm\" · 7 matches · Clear",
                "Overworld ●",
                "  [IF] Iron Farm · Farms · 263 m",
                "  [WF] Wheat Fields · Farms · 63 m",
                "  [CF] Cane Farm · Farms · 78 m",
                "  [MG] Mob Grinder · Farms · 315 m",
                "  [VH] Villager Hall · Farms · 117 m",
                "  [SF] Slime Farm · Farms · 395 m",
                "  [GF] Gold Farm · Farms · 759 m"), lines(search));
        assertEquals("/wp list all", clickOf(search, "Clear"));
    }

    @Test
    void plainTextViewersReadIdsAndNoControls() {
        assertEquals(List.of(
                "Dimensions  7 on this server",
                "Overworld (minecraft:overworld) · 14",
                "Nether (minecraft:the_nether) · 4",
                "End (minecraft:the_end) · 1",
                "The Aether (aether:the_aether) · 2",
                "Twilight Forest (twilightforest:twilight_forest) · 3",
                "No waypoints yet: Mars (ad_astra:mars) · Undergarden (undergarden:undergarden)"),
                lines(DimensionScreens.dimensionList(dims(Fixtures.console()), dimensions(), 1, 10)));
        assertEquals("All dimensions", lines(DimensionScreens.all(dims(Fixtures.console()), dimensions(), ListQuery.DEFAULT, 10)).get(0));
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.DimensionScreensTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable DimensionScreens`.

- [ ] **Step 4: Write `DimensionScreens`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/DimensionScreens.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** The dimension list and all dimensions (spec 7). */
public final class DimensionScreens {
    /** A dimension with its lists in saved order. */
    public record DimensionLists(String dimension, List<WaypointList> lists) {
        public int waypoints() {
            return this.lists.stream().mapToInt(WaypointList::size).sum();
        }
    }

    /** A line of the dimension list, and how many dimensions it shows. */
    private record Row(Component line, int dimensions) {
    }

    /** A dimension heading (list is null) or one of its lists, in /wp list all. */
    private record Block(DimensionLists dimension, @Nullable WaypointList list) {
        boolean heading() {
            return this.list == null;
        }
    }

    private record Match(String dimension, WaypointList list, SimpleWaypoint waypoint) {
    }

    private DimensionScreens() {
    }

    /** /wp list dimensions */
    public static Component dimensionList(DimensionStyle dims, List<DimensionLists> dimensions, int page, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimensions();
        ListQuery query = ListQuery.DEFAULT.withPage(page);
        List<Row> rows = new ArrayList<>();
        List<DimensionLists> empty = new ArrayList<>();
        for (DimensionLists dimension : ordered(dims, dimensions)) {
            if (dimension.waypoints() > 0 || viewer.isIn(dimension.dimension())) {
                rows.add(new Row(dimensionRow(dims, dimension), 1));
            } else {
                empty.add(dimension);
            }
        }
        if (!empty.isEmpty()) {
            rows.add(new Row(Chat.concat(translatable("wp.dimensions.no_waypoints", DARK_GRAY), text(" "),
                    Chat.join(empty.stream().map(dimension -> dimensionLink(dims, dimension, GRAY)).toList())), empty.size()));
        }
        List<List<Row>> pages = Paging.bySize(rows, pageLimit + 5);
        if (page > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        ChatLines lines = new ChatLines().line(translatable("wp.dimensions.title", GOLD), text("  "),
                translatable("wp.dimensions.on_server", GRAY, text(dimensions.size())));
        pages.get(page - 1).forEach(row -> lines.add(row.line()));
        int after = pages.subList(page, pages.size()).stream().flatMap(List::stream).mapToInt(Row::dimensions).sum();
        lines.add(ListControls.more(viewer, "dimension", after, target.command(query.withPage(page + 1))));
        if (!viewer.plainText()) {
            int total = dimensions.stream().mapToInt(DimensionLists::waypoints).sum();
            lines.line(Chat.link(viewer, translatable("wp.all.title"), AQUA, Click.run("/wp list all"),
                            Tooltip.of("wp.all.tooltip")), Chat.SEPARATOR, text(String.valueOf(total), GRAY),
                    ListControls.pager(viewer, target, query, pages.size(), ListControls.pageDetail(pageLimit + 5,
                            Chat.count("wp.count.dimension", dimensions.size()))));
        }
        return lines.build();
    }

    /** /wp list all */
    public static Component all(DimensionStyle dims, List<DimensionLists> dimensions, ListQuery query, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.allDimensions();
        List<Block> blocks = new ArrayList<>();
        for (DimensionLists dimension : ordered(dims, dimensions)) {
            if (dimension.lists().isEmpty()) {
                continue;
            }
            blocks.add(new Block(dimension, null));
            dimension.lists().forEach(list -> blocks.add(new Block(dimension, list)));
        }
        ChatLines lines = new ChatLines().add(allTitle(viewer));
        if (blocks.isEmpty()) {
            return lines.add(translatable("wp.dimension.no_lists.sentence", GRAY)).build();
        }
        List<List<Block>> pages = pageBlocks(blocks, pageLimit + 5);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        List<Block> page = pages.get(query.page() - 1);
        if (!page.get(0).heading()) {
            lines.add(heading(dims, page.get(0).dimension(), true));
        }
        for (Block block : page) {
            lines.add(block.heading() ? heading(dims, block.dimension(), false) : listRow(dims, block));
        }
        int listsAfter = (int) pages.subList(query.page(), pages.size()).stream().flatMap(List::stream)
                .filter(block -> !block.heading()).count();
        lines.add(ListControls.more(viewer, "list", listsAfter, target.command(query.withPage(query.page() + 1))));
        int listCount = (int) blocks.stream().filter(block -> !block.heading()).count();
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(), translatable("wp.in",
                        Chat.count("wp.count.list", listCount), Chat.count("wp.count.dimension", blocks.size() - listCount))),
                ListControls.search(viewer, target, translatable("wp.search.everywhere")));
        return lines.build();
    }

    /** /wp list all search <text>: matches under dimension headings, pageLimit rows per page. */
    public static Component allSearch(DimensionStyle dims, WaypointQueryEngine.QueryResult result, ListQuery query,
                                      int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.allDimensions();
        String clear = target.command(query.withSearch(""));
        ChatLines lines = new ChatLines().add(allTitle(viewer));
        List<Match> rows = new ArrayList<>();
        result.dimensions().stream()
                .sorted(Comparator.comparing(WaypointQueryEngine.DimensionResult::dimensionName, dims.order()))
                .forEach(dimension -> dimension.lists().forEach(list -> list.waypoints().forEach(waypoint ->
                        rows.add(new Match(dimension.dimensionName(), list.sourceList(), waypoint)))));
        if (rows.isEmpty()) {
            return lines.add(ListControls.noMatches(viewer, query.search(), clear)).build();
        }
        List<List<Match>> pages = Paging.bySize(rows, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        lines.add(ListControls.searchLine(viewer, query.search(), rows.size(), clear));
        String last = null;
        for (Match match : pages.get(query.page() - 1)) {
            if (!match.dimension().equals(last)) {
                Component mark = dims.hereMark(match.dimension());
                lines.line(dimensionLink(dims, new DimensionLists(match.dimension(), List.of()), dims.color(match.dimension()), false),
                        mark == null ? null : text(" "), mark);
                last = match.dimension();
            }
            lines.line(text("  "), ListScreen.row(dims, match.dimension(), match.list(), match.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                ListControls.pageDetail(pageLimit, Chat.count("wp.count.match", rows.size()))));
        return lines.build();
    }

    /** The viewer's dimension first, then Overworld, Nether and End, then the rest A–Z. */
    private static List<DimensionLists> ordered(DimensionStyle dims, List<DimensionLists> dimensions) {
        return dimensions.stream().sorted(Comparator.comparing(DimensionLists::dimension, dims.order())).toList();
    }

    /** Pages of at most budget blocks; a heading never ends a page, since its first list would leave it. */
    private static List<List<Block>> pageBlocks(List<Block> blocks, int budget) {
        List<List<Block>> pages = new ArrayList<>();
        List<Block> page = new ArrayList<>();
        for (Block block : blocks) {
            boolean full = page.size() >= budget || block.heading() && page.size() >= budget - 1;
            if (full && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
            }
            page.add(block);
        }
        pages.add(page);
        return pages;
    }

    private static Component allTitle(Viewer viewer) {
        if (viewer.plainText()) {
            return translatable("wp.all.title", GOLD);
        }
        return Chat.link(viewer, Chat.concat(translatable("wp.all.title"), text(" " + Chat.PICKER)), GOLD,
                Click.run("/wp list dimensions"), Tooltip.of("wp.dimensions.choose"));
    }

    /** Twilight Forest · 3 ● */
    private static Component dimensionRow(DimensionStyle dims, DimensionLists dimension) {
        int waypoints = dimension.waypoints();
        Component mark = dims.hereMark(dimension.dimension());
        return Chat.concat(dimensionLink(dims, dimension, waypoints > 0 ? dims.color(dimension.dimension()) : GRAY),
                Chat.SEPARATOR, text(String.valueOf(waypoints), waypoints > 0 ? GRAY : DARK_GRAY),
                mark == null ? null : text(" "), mark);
    }

    /** Overworld · 12 ●, or Overworld (continued) ● at the top of a page. */
    private static Component heading(DimensionStyle dims, DimensionLists dimension, boolean continued) {
        Component mark = dims.hereMark(dimension.dimension());
        Component tail = continued
                ? Chat.concat(text(" "), translatable("wp.all.continued", DARK_GRAY))
                : Chat.concat(Chat.SEPARATOR, text(String.valueOf(dimension.waypoints()), GRAY));
        return Chat.concat(dimensionLink(dims, dimension, dims.color(dimension.dimension())), tail,
                mark == null ? null : text(" "), mark);
    }

    private static Component listRow(DimensionStyle dims, Block block) {
        WaypointList list = block.list();
        String dimension = block.dimension().dimension();
        return Chat.concat(text("  "), WaypointRefs.listLink(dims, list, WHITE,
                        Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open"),
                Chat.SEPARATOR, text(String.valueOf(list.size()), GRAY));
    }

    private static Component dimensionLink(DimensionStyle dims, DimensionLists dimension, TextColor color) {
        return dimensionLink(dims, dimension, color, true);
    }

    /** A dimension name opening its lists; plain-text viewers read "Display (id)". */
    private static Component dimensionLink(DimensionStyle dims, DimensionLists dimension, TextColor color, boolean counts) {
        Viewer viewer = dims.viewer();
        String id = dimension.dimension();
        Component label = viewer.plainText() ? dims.name(id) : DimensionStyle.displayName(id);
        return Chat.link(viewer, label, color, Click.run("/wp list " + id), dims.tooltip(id,
                counts ? DimensionStyle.counts(dimension.waypoints(), dimension.lists().size()) : null, "wp.hint.open"));
    }
}
```

- [ ] **Step 5: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 6: Wire the screens into the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.feedback.Errors;
```

with:

```java
import _959.server_waypoint.text.feedback.DimensionScreens;
import _959.server_waypoint.text.feedback.Errors;
```

replace:

```java
    private LiteralArgumentBuilder<S> listCommandNode() {
        LiteralArgumentBuilder<S> listNode = literal(LIST_COMMAND);
        configureListTarget(listNode, ListScope.CURRENT_DIMENSION);
```

with:

```java
    private LiteralArgumentBuilder<S> listCommandNode() {
        LiteralArgumentBuilder<S> listNode = literal(LIST_COMMAND);
        configureListTarget(listNode, ListScope.CURRENT_DIMENSION);

        LiteralArgumentBuilder<S> dimensionsNode = literal("dimensions");
        dimensionsNode.executes(context -> executeDimensionList(context.getSource(), 1));
        dimensionsNode.then(LiteralArgumentBuilder.<S>literal(PAGE_COMMAND)
                .then(RequiredArgumentBuilder.<S, Integer>argument(PAGE_NUMBER_ARG, integer(1))
                        .executes(context -> executeDimensionList(context.getSource(), getInteger(context, PAGE_NUMBER_ARG)))));
        listNode.then(dimensionsNode);
```

replace:

```java
        if (scope == ListScope.ALL_DIMENSIONS) {
            ListOptions options = new ListOptions(query.search(), sortMode, query.descending(), query.page(),
                    query.pageLimit(CONFIG.defaultPageLimit()), view != ListView.FLAT);
            sendListQueryResult(
                    source,
                    new ListTarget(true, null, null),
                    options,
                    this.waypointQueryEngine.queryAll(createListQuery(source, options))
            );
            return;
        }
```

with:

```java
        if (scope == ListScope.ALL_DIMENSIONS) {
            Viewer viewer = viewer(source);
            DimensionStyle dims = dimensions(source, viewer);
            int pageLimit = query.pageLimit(CONFIG.defaultPageLimit());
            this.sender.sendMessage(source, query.searching()
                    ? DimensionScreens.allSearch(dims, this.waypointQueryEngine.queryAll(engineQuery(source, query)),
                    query, pageLimit)
                    : DimensionScreens.all(dims, knownDimensions(source), query, pageLimit));
            return;
        }
```

and replace:

```java
    private @Nullable Integer optionalLimit(CommandContext<S> context) {
```

with:

```java
    private int executeDimensionList(S source, int page) {
        Viewer viewer = viewer(source);
        this.sender.sendMessage(source, DimensionScreens.dimensionList(dimensions(source, viewer),
                knownDimensions(source), page, CONFIG.defaultPageLimit()));
        return Command.SINGLE_SUCCESS;
    }

    /** The loaded dimensions and those with waypoint files, each with its lists. */
    private List<DimensionScreens.DimensionLists> knownDimensions(S source) {
        Set<String> ids = new java.util.LinkedHashSet<>(getDimensionTypes(source).keySet());
        ids.addAll(this.waypointServer.getFileManagerMap().keySet());
        List<DimensionScreens.DimensionLists> dimensions = new java.util.ArrayList<>();
        for (String id : ids) {
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(id);
            dimensions.add(new DimensionScreens.DimensionLists(id,
                    fileManager == null ? List.of() : fileManager.getWaypointLists()));
        }
        return dimensions;
    }

    private @Nullable Integer optionalLimit(CommandContext<S> context) {
```

Then delete the old list rendering: the methods `createListQuery`, `sendListQueryResult` and `getListDisplayText`, and these imports, which nothing uses any more:

```java
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import _959.server_waypoint.util.StringCommandBuilder.ListTarget;
import static _959.server_waypoint.util.StringCommandBuilder.listDimensionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.listWaypointListCmd;
```

Delete `WaypointListPage` and its test:

```bash
/usr/bin/git rm common/src/main/java/_959/server_waypoint/command/WaypointListPage.java common/src/test/java/_959/server_waypoint/command/WaypointListPageTest.java
```

- [ ] **Step 7: Add the command test**

In `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void theDimensionListAndAllDimensionsAnswerTheirCommands() {
        CommandHarness.Source player = CommandHarness.player();

        assertEquals(List.of("Dimensions  3 on this server", "Overworld · 0 ●", "No waypoints yet: Nether · End",
                "All dimensions · 0"), lines(this.harness.run(player, "wp list dimensions")));
        assertEquals("✘ Page 2 does not exist; the last page is 1. Last page",
                lines(this.harness.run(player, "wp list dimensions page 2")).get(0));
        assertEquals(List.of("All dimensions ⏷", "No lists yet."), lines(this.harness.run(player, "wp list all")));
        this.harness.addList("minecraft:the_nether", "Hub", CommandHarness.waypoint("Portal", "P", 0xFF5555, 1, 64, 1));
        assertEquals("Nether · 1", lines(this.harness.run(player, "wp list all")).get(1));
        assertEquals("Search \"portal\" · 1 match · Clear", lines(this.harness.run(player, "wp list all search portal")).get(1));
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

- [ ] **Step 8: Retire the old all-dimensions tests**

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`, delete `searchUsesFilteredRowsAndNextPagePreservesAllOptions`, `viewTogglePreservesListOptionsAndSwitchesTheRenderedShape`, `treePagesShowAllDimensionsAndTitlesSelectTheirScope`, `pagePastTheResultReportsTheLastAvailablePage` and `sortControlsPreserveTheQueryAndResetThePage`, and the helpers and imports only they used (`listSearchSuggestion`, `countOccurrences`, `hoverTextColor` and `textColor` if nothing else calls them).

- [ ] **Step 9: Run the common tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/feedback common/src/test/java/_959/server_waypoint/text/feedback common/src/main/java/_959/server_waypoint/command common/src/test/java/_959/server_waypoint/command common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add the dimension list and rebuild all dimensions with continued headings"
```

---

### Task 11: Pickers

Spec 9 and 16: `set color` and `set yaw` without a value open a colour and a facing picker, `set color random` picks a random colour, and `/wp add` without arguments opens a picker of this dimension's lists (paged when there are more than `L`). Plain-text viewers read the accepted values instead (spec 15). This comes before details (Task 12), whose Color and Yaw buttons open these pickers.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/PickerScreens.java`
- Modify: `common/src/main/java/_959/server_waypoint/text/feedback/Errors.java`
- Modify: `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/PickerScreensTest.java`; `CommandFeedbackTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `WaypointRefs`, `ListActions` (Task 9), `ColorUtils.VANILLA_COLORS`, `VANILLA_COLOR_NAMES`, `VANILLA_COLOR_CODES`, `rgbToHexCode`.
- Produces:
  - `StringCommandBuilder.editWaypointCmd(String dimension, String list, String waypoint, String tail)`: `/wp edit waypoint <dimension> <list> <waypoint> <tail>`.
  - `PickerScreens.color(DimensionStyle, String dimension, WaypointList, SimpleWaypoint)`, `facing(...)` (same parameters), `add(DimensionStyle, String dimension, List<WaypointList>, int page, int pageLimit)`, `static Component yawValue(int yaw)` (`0° (south)`), `static Component colorValue(int rgb)` (`■ #FFAA00`), `static @Nullable Component back(Viewer, String dimension, WaypointList, SimpleWaypoint)` (`Back` to details).
  - `Errors.playerOnly()`.
  - `CoreWaypointCommand`: `executeAddPicker(S source, int page)`, `executePicker(CommandContext<S>, boolean color)`.
- Keys: `wp.action.back`, `wp.picker.*`, `wp.hint.type_hex`, `wp.hint.type_degrees`, `wp.facing.*`, `wp.yaw.facing`, `wp.details.color`, `wp.color.*`, `wp.error.player_only`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.action.back": "Back",
  "wp.details.color": "Color",
  "wp.picker.now": "now {0}",
  "wp.picker.random": "Random",
  "wp.picker.random.tooltip": "Pick a random colour",
  "wp.picker.custom": "Custom",
  "wp.picker.custom.color": "Custom colour",
  "wp.picker.custom.facing": "Custom facing",
  "wp.picker.back.details": "Back to details",
  "wp.picker.back.menu": "Back to the menu",
  "wp.picker.color.values": "Accepted values: {0}, random, or a hex code such as 39C5BB",
  "wp.picker.facing": "Facing",
  "wp.picker.face": "Face {0}",
  "wp.picker.yours": "Yours",
  "wp.picker.yours.tooltip": "The way you are facing now",
  "wp.picker.yaw.values": "Accepted values: a number of degrees, such as 0 (south), 90 (west), 180 (north) or -90 (east)",
  "wp.picker.add": "Add a waypoint",
  "wp.picker.add.at": "at {0}",
  "wp.picker.into": "Into",
  "wp.picker.add.to": "Add it to {0}",
  "wp.hint.type_hex": "Type a hex colour such as 39C5BB, then press Enter",
  "wp.hint.type_degrees": "Type a number of degrees, then press Enter",
  "wp.facing.south": "south",
  "wp.facing.west": "west",
  "wp.facing.north": "north",
  "wp.facing.east": "east",
  "wp.facing.south.title": "South",
  "wp.facing.west.title": "West",
  "wp.facing.north.title": "North",
  "wp.facing.east.title": "East",
  "wp.yaw.facing": "{0} ({1})",
  "wp.color.black": "Black",
  "wp.color.dark_blue": "Dark blue",
  "wp.color.dark_green": "Dark green",
  "wp.color.dark_aqua": "Dark aqua",
  "wp.color.dark_red": "Dark red",
  "wp.color.dark_purple": "Dark purple",
  "wp.color.gold": "Gold",
  "wp.color.gray": "Gray",
  "wp.color.dark_gray": "Dark gray",
  "wp.color.blue": "Blue",
  "wp.color.green": "Green",
  "wp.color.aqua": "Aqua",
  "wp.color.red": "Red",
  "wp.color.light_purple": "Light purple",
  "wp.color.yellow": "Yellow",
  "wp.color.white": "White",
  "wp.error.player_only": "Only players can do that."
```

`zh_cn.json`:

```json
  "wp.action.back": "返回",
  "wp.details.color": "颜色",
  "wp.picker.now": "当前 {0}",
  "wp.picker.random": "随机",
  "wp.picker.random.tooltip": "随机选择颜色",
  "wp.picker.custom": "自定义",
  "wp.picker.custom.color": "自定义颜色",
  "wp.picker.custom.facing": "自定义朝向",
  "wp.picker.back.details": "返回详情",
  "wp.picker.back.menu": "返回菜单",
  "wp.picker.color.values": "可用值：{0}、random，或十六进制颜色（如 39C5BB）",
  "wp.picker.facing": "朝向",
  "wp.picker.face": "朝{0}",
  "wp.picker.yours": "你的朝向",
  "wp.picker.yours.tooltip": "你现在面朝的方向",
  "wp.picker.yaw.values": "可用值：角度，例如 0（南）、90（西）、180（北）或 -90（东）",
  "wp.picker.add": "添加路径点",
  "wp.picker.add.at": "位于 {0}",
  "wp.picker.into": "加入",
  "wp.picker.add.to": "添加到{0}",
  "wp.hint.type_hex": "输入十六进制颜色（如 39C5BB），然后按回车",
  "wp.hint.type_degrees": "输入角度，然后按回车",
  "wp.facing.south": "南",
  "wp.facing.west": "西",
  "wp.facing.north": "北",
  "wp.facing.east": "东",
  "wp.facing.south.title": "南",
  "wp.facing.west.title": "西",
  "wp.facing.north.title": "北",
  "wp.facing.east.title": "东",
  "wp.yaw.facing": "{0}（{1}）",
  "wp.color.black": "黑色",
  "wp.color.dark_blue": "深蓝色",
  "wp.color.dark_green": "深绿色",
  "wp.color.dark_aqua": "湖蓝色",
  "wp.color.dark_red": "深红色",
  "wp.color.dark_purple": "紫色",
  "wp.color.gold": "金色",
  "wp.color.gray": "灰色",
  "wp.color.dark_gray": "深灰色",
  "wp.color.blue": "蓝色",
  "wp.color.green": "绿色",
  "wp.color.aqua": "天蓝色",
  "wp.color.red": "红色",
  "wp.color.light_purple": "粉红色",
  "wp.color.yellow": "黄色",
  "wp.color.white": "白色",
  "wp.error.player_only": "只有玩家可以这样做。"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.action.back": "返回",
  "wp.details.color": "顏色",
  "wp.picker.now": "目前 {0}",
  "wp.picker.random": "隨機",
  "wp.picker.random.tooltip": "隨機選擇顏色",
  "wp.picker.custom": "自訂",
  "wp.picker.custom.color": "自訂顏色",
  "wp.picker.custom.facing": "自訂朝向",
  "wp.picker.back.details": "返回詳情",
  "wp.picker.back.menu": "返回選單",
  "wp.picker.color.values": "可用值：{0}、random，或十六進位色碼（如 39C5BB）",
  "wp.picker.facing": "朝向",
  "wp.picker.face": "朝{0}",
  "wp.picker.yours": "你的朝向",
  "wp.picker.yours.tooltip": "你現在面朝的方向",
  "wp.picker.yaw.values": "可用值：角度，例如 0（南）、90（西）、180（北）或 -90（東）",
  "wp.picker.add": "新增路徑點",
  "wp.picker.add.at": "位於 {0}",
  "wp.picker.into": "加入",
  "wp.picker.add.to": "新增到{0}",
  "wp.hint.type_hex": "輸入十六進位色碼（如 39C5BB），然後按 Enter",
  "wp.hint.type_degrees": "輸入角度，然後按 Enter",
  "wp.facing.south": "南",
  "wp.facing.west": "西",
  "wp.facing.north": "北",
  "wp.facing.east": "東",
  "wp.facing.south.title": "南",
  "wp.facing.west.title": "西",
  "wp.facing.north.title": "北",
  "wp.facing.east.title": "東",
  "wp.yaw.facing": "{0}（{1}）",
  "wp.color.black": "黑色",
  "wp.color.dark_blue": "深藍色",
  "wp.color.dark_green": "深綠色",
  "wp.color.dark_aqua": "湖藍色",
  "wp.color.dark_red": "深紅色",
  "wp.color.dark_purple": "紫色",
  "wp.color.gold": "金色",
  "wp.color.gray": "灰色",
  "wp.color.dark_gray": "深灰色",
  "wp.color.blue": "藍色",
  "wp.color.green": "綠色",
  "wp.color.aqua": "天藍色",
  "wp.color.red": "紅色",
  "wp.color.light_purple": "粉紅色",
  "wp.color.yellow": "黃色",
  "wp.color.white": "白色",
  "wp.error.player_only": "只有玩家可以這樣做。"
```

`es_es.json`:

```json
  "wp.action.back": "Volver",
  "wp.details.color": "Color",
  "wp.picker.now": "ahora {0}",
  "wp.picker.random": "Aleatorio",
  "wp.picker.random.tooltip": "Elige un color al azar",
  "wp.picker.custom": "Personalizado",
  "wp.picker.custom.color": "Color personalizado",
  "wp.picker.custom.facing": "Orientación personalizada",
  "wp.picker.back.details": "Volver a los detalles",
  "wp.picker.back.menu": "Volver al menú",
  "wp.picker.color.values": "Valores admitidos: {0}, random o un código hexadecimal como 39C5BB",
  "wp.picker.facing": "Orientación",
  "wp.picker.face": "Mirar al {0}",
  "wp.picker.yours": "La tuya",
  "wp.picker.yours.tooltip": "Hacia donde miras ahora",
  "wp.picker.yaw.values": "Valores admitidos: un número de grados, como 0 (sur), 90 (oeste), 180 (norte) o -90 (este)",
  "wp.picker.add": "Añadir un punto de ruta",
  "wp.picker.add.at": "en {0}",
  "wp.picker.into": "En",
  "wp.picker.add.to": "Añadirlo a {0}",
  "wp.hint.type_hex": "Escribe un código hexadecimal como 39C5BB y pulsa Intro",
  "wp.hint.type_degrees": "Escribe un número de grados y pulsa Intro",
  "wp.facing.south": "sur",
  "wp.facing.west": "oeste",
  "wp.facing.north": "norte",
  "wp.facing.east": "este",
  "wp.facing.south.title": "Sur",
  "wp.facing.west.title": "Oeste",
  "wp.facing.north.title": "Norte",
  "wp.facing.east.title": "Este",
  "wp.yaw.facing": "{0} ({1})",
  "wp.color.black": "Negro",
  "wp.color.dark_blue": "Azul oscuro",
  "wp.color.dark_green": "Verde oscuro",
  "wp.color.dark_aqua": "Aguamarina oscuro",
  "wp.color.dark_red": "Rojo oscuro",
  "wp.color.dark_purple": "Morado oscuro",
  "wp.color.gold": "Dorado",
  "wp.color.gray": "Gris",
  "wp.color.dark_gray": "Gris oscuro",
  "wp.color.blue": "Azul",
  "wp.color.green": "Verde",
  "wp.color.aqua": "Aguamarina",
  "wp.color.red": "Rojo",
  "wp.color.light_purple": "Morado claro",
  "wp.color.yellow": "Amarillo",
  "wp.color.white": "Blanco",
  "wp.error.player_only": "Solo los jugadores pueden hacer eso."
```

`he_il.json`:

```json
  "wp.action.back": "חזרה",
  "wp.details.color": "צבע",
  "wp.picker.now": "עכשיו {0}",
  "wp.picker.random": "אקראי",
  "wp.picker.random.tooltip": "בחירת צבע אקראי",
  "wp.picker.custom": "מותאם",
  "wp.picker.custom.color": "צבע מותאם",
  "wp.picker.custom.facing": "כיוון מותאם",
  "wp.picker.back.details": "חזרה לפרטים",
  "wp.picker.back.menu": "חזרה לתפריט",
  "wp.picker.color.values": "ערכים אפשריים: {0}, random, או קוד הקסדצימלי כמו 39C5BB",
  "wp.picker.facing": "כיוון",
  "wp.picker.face": "פנייה ל{0}",
  "wp.picker.yours": "שלכם",
  "wp.picker.yours.tooltip": "הכיוון שאליו אתם פונים עכשיו",
  "wp.picker.yaw.values": "ערכים אפשריים: מספר מעלות, כמו 0 (דרום), 90 (מערב), 180 (צפון) או -90 (מזרח)",
  "wp.picker.add": "הוספת נקודת ציון",
  "wp.picker.add.at": "ב-{0}",
  "wp.picker.into": "לתוך",
  "wp.picker.add.to": "הוספה ל-{0}",
  "wp.hint.type_hex": "הקלידו קוד הקסדצימלי כמו 39C5BB ולחצו Enter",
  "wp.hint.type_degrees": "הקלידו מספר מעלות ולחצו Enter",
  "wp.facing.south": "דרום",
  "wp.facing.west": "מערב",
  "wp.facing.north": "צפון",
  "wp.facing.east": "מזרח",
  "wp.facing.south.title": "דרום",
  "wp.facing.west.title": "מערב",
  "wp.facing.north.title": "צפון",
  "wp.facing.east.title": "מזרח",
  "wp.yaw.facing": "{0} ({1})",
  "wp.color.black": "שחור",
  "wp.color.dark_blue": "כחול כהה",
  "wp.color.dark_green": "ירוק כהה",
  "wp.color.dark_aqua": "טורקיז כהה",
  "wp.color.dark_red": "אדום כהה",
  "wp.color.dark_purple": "סגול כהה",
  "wp.color.gold": "זהב",
  "wp.color.gray": "אפור",
  "wp.color.dark_gray": "אפור כהה",
  "wp.color.blue": "כחול",
  "wp.color.green": "ירוק",
  "wp.color.aqua": "טורקיז",
  "wp.color.red": "אדום",
  "wp.color.light_purple": "סגול בהיר",
  "wp.color.yellow": "צהוב",
  "wp.color.white": "לבן",
  "wp.error.player_only": "רק שחקנים יכולים לעשות את זה."
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/PickerScreensTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.ChatAssert;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickerScreensTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final SimpleWaypoint MAIN_HOME = HOME.getWaypointByName("Main Home");
    private static final String EDIT = "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" ";

    @Test
    void theColourPickerOffersTheNamedColoursRandomAndCustom() {
        Component picker = PickerScreens.color(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals(List.of(
                "Color · [MH] Main Home   now ■ #FFAA00",
                "█ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █",
                "Random · Custom… · Back"), lines(picker));
        assertEquals(EDIT + "set color black", clickOf(picker, "█"));
        assertEquals("Black\n#000000", tooltipOf(picker, "█"));
        assertEquals(TextColor.color(0x000000), colorOf(picker, "█"));
        assertEquals(EDIT + "set color random", clickOf(picker, "Random"));
        assertEquals(EDIT + "set color FFAA00", clickOf(picker, "Custom…"));
        assertEquals(NamedTextColor.YELLOW, colorOf(picker, "Custom…"));
        assertEquals("/wp details waypoint minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(picker, "Back"));
        assertTrue(runCommands(picker).contains(EDIT + "set color white"));
        ChatAssert.assertFitsChat(picker);
    }

    @Test
    void theFacingPickerOffersTheFourDirectionsAndYours() {
        Component picker = PickerScreens.facing(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals(List.of(
                "Facing · [MH] Main Home   now 0° (south)",
                "South 0° · West 90° · North 180° · East -90°",
                "Yours 37° · Custom… · Back"), lines(picker));
        assertEquals(EDIT + "set yaw -90", clickOf(picker, "East -90°"));
        assertEquals("Face east", tooltipOf(picker, "East -90°"));
        assertEquals(EDIT + "set yaw 37", clickOf(picker, "Yours 37°"));
        assertEquals(EDIT + "set yaw 0", clickOf(picker, "Custom…"));
    }

    @Test
    void plainTextViewersReadTheAcceptedValues() {
        assertEquals("  Accepted values: black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, "
                        + "dark_gray, blue, green, aqua, red, light_purple, yellow, white, random, or a hex code such as 39C5BB",
                lines(PickerScreens.color(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, MAIN_HOME)).get(1));
        assertEquals("  Accepted values: a number of degrees, such as 0 (south), 90 (west), 180 (north) or -90 (east)",
                lines(PickerScreens.facing(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, MAIN_HOME)).get(1));
    }

    @Test
    void yawValuesNameTheFourDirections() {
        assertEquals("180° (north)", render(PickerScreens.yawValue(180)));
        assertEquals("-180° (north)", render(PickerScreens.yawValue(-180)));
        assertEquals("90° (west)", render(PickerScreens.yawValue(90)));
        assertEquals("37°", render(PickerScreens.yawValue(37)));
    }

    @Test
    void theAddPickerListsThisDimensionsLists() {
        Component picker = PickerScreens.add(Fixtures.dims(Fixtures.player()), OVERWORLD, Fixtures.overworldLists(), 1, 10);

        assertEquals(List.of(
                "Add a waypoint at 100, 64, -20",
                "Into  Home Bases · Farms · Exploration",
                "New list · Back"), lines(picker));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" ", clickOf(picker, "Home Bases"));
        assertEquals("Add it to Home Bases\nType its name, then press Enter", tooltipOf(picker, "Home Bases"));
        assertEquals(NamedTextColor.GREEN, colorOf(picker, "Farms"));
        assertEquals("/wp add minecraft:overworld ", clickOf(picker, "New list"));
        assertEquals("/wp", clickOf(picker, "Back"));
        assertEquals(List.of("Add a waypoint at 100, 64, -20", "No lists yet. New list"),
                lines(PickerScreens.add(Fixtures.dims(Fixtures.player()), OVERWORLD, List.of(), 1, 10)));
    }

    @Test
    void manyListsGetALineEachAndPages() {
        List<WaypointList> lists = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            lists.add(new WaypointList("List " + index, 1, List.of()));
        }
        Component picker = PickerScreens.add(Fixtures.dims(Fixtures.player()), OVERWORLD, lists, 1, 10);
        List<String> lines = lines(picker);

        assertEquals("Into", lines.get(1));
        assertEquals("  List 1", lines.get(2));
        assertEquals("… 15 more lists", lines.get(17));
        assertEquals("New list · Back    ‹ 1/2 ›", lines.get(18));
        assertEquals("/wp add page 2", clickOf(picker, "… 15 more lists"));
        ChatAssert.assertFitsChat(picker);
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.PickerScreensTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable PickerScreens`.

- [ ] **Step 4: Add the edit command helper and the player-only error**

In `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`, replace:

```java
    public static String editWaypointClearCmd(
```

with:

```java
    /** /wp edit waypoint <dimension> <list> <waypoint> followed by the rest of the command. */
    public static String editWaypointCmd(String dimensionName, String listIdentifier, String waypointIdentifier, String tail) {
        return WAYPOINT_COMMAND_WITH_SLASH + " edit waypoint " + dimensionName + ' '
                + escapeArgument(listIdentifier) + ' ' + escapeArgument(waypointIdentifier) + ' ' + tail;
    }

    public static String editWaypointClearCmd(
```

In `common/src/main/java/_959/server_waypoint/text/feedback/Errors.java`, replace:

```java
    /** ✘ No dimension called x. Dimensions */
```

with:

```java
    /** ✘ Only players can do that. */
    public static Component playerOnly() {
        return Chat.error(translatable("wp.error.player_only"));
    }

    /** ✘ No dimension called x. Dimensions */
```

- [ ] **Step 5: Write `PickerScreens`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/PickerScreens.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static _959.server_waypoint.util.ColorUtils.VANILLA_COLORS;
import static _959.server_waypoint.util.ColorUtils.VANILLA_COLOR_CODES;
import static _959.server_waypoint.util.ColorUtils.VANILLA_COLOR_NAMES;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The colour, facing and add pickers (spec 9). */
public final class PickerScreens {
    private static final int[] FACINGS = {0, 90, 180, -90};

    private PickerScreens() {
    }

    /** set color without a value */
    public static Component color(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        String edit = editWaypointCmd(dimension, list.name(), waypoint.name(), "set color");
        ChatLines lines = new ChatLines().add(title("wp.details.color", dims, dimension, list, waypoint,
                colorValue(waypoint.rgb())));
        if (viewer.plainText()) {
            return lines.line(text("  "), translatable("wp.picker.color.values",
                    text(String.join(", ", VANILLA_COLOR_NAMES)))).build();
        }
        List<Component> swatches = new ArrayList<>();
        for (int index = 0; index < VANILLA_COLORS.length; index++) {
            swatches.add(Chat.link(viewer, text("█"), TextColor.color(VANILLA_COLORS[index]),
                    Click.run(edit + " " + VANILLA_COLOR_NAMES[index]),
                    Tooltip.of("wp.color." + VANILLA_COLOR_NAMES[index]).line(text(VANILLA_COLOR_CODES[index]))));
        }
        lines.add(Chat.spaced(swatches));
        lines.add(Chat.join(
                Chat.link(viewer, translatable("wp.picker.random"), AQUA, Click.run(edit + " random"),
                        Tooltip.of("wp.picker.random.tooltip")),
                Chat.link(viewer, Chat.concat(translatable("wp.picker.custom"), text(Chat.ELLIPSIS)), YELLOW,
                        Click.suggest(edit + " " + rgbToHexCode(waypoint.rgb(), false)),
                        Tooltip.of("wp.picker.custom.color").hint("wp.hint.type_hex")),
                back(viewer, dimension, list, waypoint)));
        return lines.build();
    }

    /** set yaw without a value */
    public static Component facing(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        String edit = editWaypointCmd(dimension, list.name(), waypoint.name(), "set yaw");
        ChatLines lines = new ChatLines().add(title("wp.picker.facing", dims, dimension, list, waypoint,
                yawValue(waypoint.yaw())));
        if (viewer.plainText()) {
            return lines.line(text("  "), translatable("wp.picker.yaw.values")).build();
        }
        List<Component> facings = new ArrayList<>();
        for (int yaw : FACINGS) {
            String facing = Objects.requireNonNull(facing(yaw));
            facings.add(Chat.link(viewer, Chat.concat(translatable("wp.facing." + facing + ".title"), text(" " + yaw + "°")),
                    AQUA, Click.run(edit + " " + yaw), Tooltip.of("wp.picker.face", translatable("wp.facing." + facing))));
        }
        lines.add(Chat.join(facings));
        int yours = Math.floorMod(Math.round(viewer.yaw()) + 180, 360) - 180;
        lines.add(Chat.join(
                Chat.link(viewer, Chat.concat(translatable("wp.picker.yours"), text(" " + yours + "°")), GREEN,
                        Click.run(edit + " " + yours), Tooltip.of("wp.picker.yours.tooltip")),
                Chat.link(viewer, Chat.concat(translatable("wp.picker.custom"), text(Chat.ELLIPSIS)), YELLOW,
                        Click.suggest(edit + " " + waypoint.yaw()),
                        Tooltip.of("wp.picker.custom.facing").hint("wp.hint.type_degrees")),
                back(viewer, dimension, list, waypoint)));
        return lines.build();
    }

    /** /wp add without arguments: this dimension's lists to add a waypoint here to. */
    public static Component add(DimensionStyle dims, String dimension, List<WaypointList> lists, int page, int pageLimit) {
        Viewer viewer = dims.viewer();
        WaypointPos here = Objects.requireNonNullElse(viewer.position(), new WaypointPos(0, 0, 0));
        ChatLines lines = new ChatLines().line(translatable("wp.picker.add", GOLD), text(" "),
                translatable("wp.picker.add.at", GRAY, text(DimensionStyle.coordinates(here))));
        Component newList = ListActions.newList(dims, dimension);
        Component back = Chat.control(viewer, translatable("wp.action.back"), GRAY, Click.run("/wp"),
                Tooltip.of("wp.picker.back.menu"));
        if (lists.isEmpty()) {
            return lines.line(translatable("wp.dimension.no_lists.sentence", GRAY), newList == null ? null : text(" "),
                    newList).build();
        }
        List<Component> links = lists.stream().map(list -> Chat.link(viewer,
                WaypointRefs.label(viewer, list.displayName(), list.name()), GREEN, ListActions.addClick(viewer, dimension, list),
                Tooltip.of("wp.picker.add.to", WaypointRefs.label(list.displayName(), list.name())).hint("wp.hint.type_name")))
                .toList();
        if (lists.size() <= pageLimit) {
            lines.line(translatable("wp.picker.into", GRAY), text("  "), Chat.join(links));
            return lines.add(Chat.join(newList, back)).build();
        }
        ListTarget target = new ListTarget("/wp add");
        ListQuery query = ListQuery.DEFAULT.withPage(page);
        List<List<Component>> pages = Paging.bySize(links, pageLimit + 5);
        if (page > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        lines.add(translatable("wp.picker.into", GRAY));
        pages.get(page - 1).forEach(link -> lines.line(text("  "), link));
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, page), target.command(query.withPage(page + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", lists.size()))), Chat.join(newList, back));
        return lines.build();
    }

    /** "0° (south)": the yaw, and the direction when it is one of the four. */
    public static Component yawValue(int yaw) {
        String facing = facing(yaw);
        Component degrees = text(yaw + "°");
        return facing == null ? degrees : translatable("wp.yaw.facing", degrees, translatable("wp.facing." + facing));
    }

    /** "■ #FFAA00": a swatch in the colour and its hex code in white. */
    public static Component colorValue(int rgb) {
        return Chat.concat(text("■", TextColor.color(rgb)), text(" " + rgbToHexCode(rgb, true), WHITE));
    }

    /** Back to the waypoint's details. */
    public static @Nullable Component back(Viewer viewer, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.control(viewer, translatable("wp.action.back"), GRAY,
                Click.run(detailsWaypointCmd(dimension, list.name(), waypoint.name())), Tooltip.of("wp.picker.back.details"));
    }

    private static @Nullable String facing(int yaw) {
        return switch (Math.floorMod(yaw, 360)) {
            case 0 -> "south";
            case 90 -> "west";
            case 180 -> "north";
            case 270 -> "east";
            default -> null;
        };
    }

    /** "Color · [MH] Main Home   now ■ #FFAA00" */
    private static Component title(String titleKey, DimensionStyle dims, String dimension, WaypointList list,
                                   SimpleWaypoint waypoint, Component value) {
        return Chat.concat(translatable(titleKey, GOLD), Chat.SEPARATOR,
                WaypointRefs.title(dims, dimension, list, waypoint, WHITE), text("   "),
                translatable("wp.picker.now", GRAY, value));
    }
}
```

- [ ] **Step 6: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 7: Wire the pickers and random colours into the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.feedback.ListScreen;
```

with:

```java
import _959.server_waypoint.text.feedback.ListScreen;
import _959.server_waypoint.text.feedback.PickerScreens;
```

replace:

```java
        set.then(LiteralArgumentBuilder.<S>literal("color")
                .then(RequiredArgumentBuilder.<S, String>argument(COLOR_ARG, string())
                        .suggests(this.HEX_COLOR_CODE_SUGGESTION)
                        .executes(context -> {
                            String input = getString(context, COLOR_ARG);
                            int color = colorNameOrHexCodeToRgb(input, false);
```

with:

```java
        set.then(LiteralArgumentBuilder.<S>literal("color")
                .executes(context -> executePicker(context, true))
                .then(RequiredArgumentBuilder.<S, String>argument(COLOR_ARG, string())
                        .suggests(this.HEX_COLOR_CODE_SUGGESTION)
                        .executes(context -> {
                            String input = getString(context, COLOR_ARG);
                            int color = RANDOM_COLOR.equals(input) ? randomColor() : colorNameOrHexCodeToRgb(input, false);
```

replace:

```java
        set.then(LiteralArgumentBuilder.<S>literal("yaw")
                .then(RequiredArgumentBuilder.<S, Integer>argument(YAW_ARG, integer()).executes(context -> {
```

with:

```java
        set.then(LiteralArgumentBuilder.<S>literal("yaw")
                .executes(context -> executePicker(context, false))
                .then(RequiredArgumentBuilder.<S, Integer>argument(YAW_ARG, integer()).executes(context -> {
```

replace:

```java
                .then(literal(ADD_COMMAND)
                        .requires(source -> hasAddPermission((S) source))
                        .then(argument(DIMENSION_ARG, this.dimensionArgumentProvider.get())
```

with:

```java
                .then(literal(ADD_COMMAND)
                        .requires(source -> hasAddPermission((S) source))
                        .executes(context -> executeAddPicker((S) context.getSource(), 1))
                        .then(literal(PAGE_COMMAND)
                                .then(argument(PAGE_NUMBER_ARG, integer(1))
                                        .executes(context -> executeAddPicker((S) context.getSource(),
                                                getInteger(context, PAGE_NUMBER_ARG)))))
                        .then(argument(DIMENSION_ARG, this.dimensionArgumentProvider.get())
```

and replace:

```java
    private int executeVisibilityPatch(CommandContext<S> context, boolean global) {
```

with:

```java
    /** set color or set yaw without a value: the colour or facing picker. */
    private int executePicker(CommandContext<S> context, boolean color) {
        S source = context.getSource();
        runWithSelectorTarget(source, getArgument(context, DIMENSION_ARG), getString(context, LIST_NAME_ARG),
                getString(context, WAYPOINT_NAME_ARG), (fileManager, list, waypoint) -> {
                    Viewer viewer = viewer(source);
                    DimensionStyle dims = dimensions(source, viewer);
                    this.sender.sendMessage(source, color
                            ? PickerScreens.color(dims, fileManager.getDimensionName(), list, waypoint)
                            : PickerScreens.facing(dims, fileManager.getDimensionName(), list, waypoint));
                });
        return Command.SINGLE_SUCCESS;
    }

    /** /wp add without arguments: the lists of the player's dimension to add a waypoint where they stand. */
    private int executeAddPicker(S source, int page) {
        if (getPlayer(source) == null) {
            this.sender.sendError(source, Errors.playerOnly());
            return 0;
        }
        Viewer viewer = viewer(source);
        String dimension = toDimensionName(getSourceDimension(source));
        WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimension);
        this.sender.sendMessage(source, PickerScreens.add(dimensions(source, viewer), dimension,
                fileManager == null ? List.of() : fileManager.getWaypointLists(), page, CONFIG.defaultPageLimit()));
        return Command.SINGLE_SUCCESS;
    }

    private int executeVisibilityPatch(CommandContext<S> context, boolean global) {
```

- [ ] **Step 8: Add the command tests**

In `common/src/test/java/_959/server_waypoint/command/CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void colourAndFacingWithoutAValueOpenTheirPickersAndRandomPicksAColour() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();
        String waypoint = "wp edit waypoint minecraft:overworld Farms \"Iron Farm\" ";

        assertEquals("Color · [IF] Iron Farm   now ■ #AAAAAA", lines(this.harness.run(player, waypoint + "set color")).get(0));
        assertEquals("Facing · [IF] Iron Farm   now 0° (south)", lines(this.harness.run(player, waypoint + "set yaw")).get(0));
        this.harness.run(player, waypoint + "set color random");
        this.harness.run(player, waypoint + "set yaw -90");
        assertEquals(-90, this.harness.server.getWaypointFileManager("minecraft:overworld")
                .getWaypointListByName("Farms").getWaypointByName("Iron Farm").yaw());
    }

    @Test
    void addWithoutArgumentsOpensTheAddPickerForPlayersOnly() {
        this.harness.addList("minecraft:overworld", "Farms");

        assertEquals(List.of("Add a waypoint at 100, 64, -20", "Into  Farms", "New list · Back"),
                lines(this.harness.run(CommandHarness.player(), "wp add")));
        assertEquals("✘ Only players can do that.", lines(this.harness.run(CommandHarness.console(), "wp add")).get(0));
        this.harness.run(CommandHarness.player(), "wp add page 1");
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

- [ ] **Step 9: Run the common tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/feedback common/src/test/java/_959/server_waypoint/text/feedback common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java common/src/main/java/_959/server_waypoint/command common/src/test/java/_959/server_waypoint/command common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add colour, facing and add pickers"
```

---

### Task 12: Waypoint and list details

Spec 8: one property per line with a yellow `[✎]` in front and a red `[×]` or green `[Here]` after it, a breadcrumb title and a row of `[Buttons]`. Color and Yaw open the pickers of Task 11, and Visibility runs its toggle. An edit re-sends the panel with `✔ Updated …` on top; the line is derived from the patch, so the edit call sites stay as they are. `WaypointDetailsTextBuilder` goes away. Old translation keys are swept in Task 18.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/DetailsScreen.java`
- Modify: `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Delete: `common/src/main/java/_959/server_waypoint/text/WaypointDetailsTextBuilder.java`, `common/src/test/java/_959/server_waypoint/text/WaypointDetailsTextBuilderTest.java`
- Modify: `mods/src/test/java/_959/server_waypoint/common/network/ModMessageSenderTest.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/DetailsScreenTest.java`; `CommandFeedbackTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `WaypointRefs` (Task 7), `ListActions` and `ListScreen.dimensionLink` (Task 9), `PickerScreens.colorValue`, `yawValue` and `StringCommandBuilder.editWaypointCmd` (Task 11), `WaypointPatch`, `WaypointListPatch`, `PatchField`.
- Produces:
  - `DetailsScreen.waypoint(DimensionStyle, String dimension, WaypointList, SimpleWaypoint, @Nullable Component updated)`, `DetailsScreen.list(DimensionStyle, String dimension, WaypointList, @Nullable Component updated)`.
  - `DetailsScreen.updated(WaypointPatch)` and `updated(WaypointListPatch)`: `Updated the colour`, `Cleared the description` and so on for the first property the patch changes, or null.
  - `StringCommandBuilder.downloadCmd(String dimension, @Nullable String list, @Nullable String waypoint)`.
- Keys: `wp.details.{property,display_name,identifier,initials,icon,position,yaw,visibility,keywords,description,none,here,here.tooltip}`, `wp.visibility.{global,local}`, `wp.edit.*`, `wp.hint.edit`, `wp.clear.*`, `wp.action.{navigate,navigate.tooltip,teleport,download,download.waypoint,download.list,remove,remove.tooltip,remove.blocked,remove.blocked.detail,back.tooltip,open_list,waypoint_here,waypoint}`, `wp.updated.*`, `wp.cleared.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.details.property": "{0}: {1}",
  "wp.details.display_name": "Display name",
  "wp.details.identifier": "Identifier",
  "wp.details.initials": "Initials",
  "wp.details.icon": "Icon",
  "wp.details.position": "Position",
  "wp.details.yaw": "Yaw",
  "wp.details.visibility": "Visibility",
  "wp.details.keywords": "Keywords",
  "wp.details.description": "Description",
  "wp.details.none": "none",
  "wp.details.here": "Here",
  "wp.details.here.tooltip": "Move it to where you stand",
  "wp.visibility.global": "Global",
  "wp.visibility.local": "Local",
  "wp.edit.display_name": "Edit the display name",
  "wp.edit.identifier": "Edit the identifier",
  "wp.edit.initials": "Edit the initials",
  "wp.edit.icon": "Edit the icon",
  "wp.edit.position": "Edit the position",
  "wp.edit.color": "Choose a colour",
  "wp.edit.yaw": "Choose a facing",
  "wp.edit.visibility": "Switch to {0}",
  "wp.edit.keywords": "Edit the keywords",
  "wp.edit.description": "Edit the description",
  "wp.hint.edit": "Change it, then press Enter",
  "wp.clear.display_name": "Clear the display name",
  "wp.clear.icon": "Clear the icon",
  "wp.clear.keywords": "Clear the keywords",
  "wp.clear.description": "Clear the description",
  "wp.action.navigate": "Navigate",
  "wp.action.navigate.tooltip": "Navigate to {0}",
  "wp.action.teleport": "Teleport",
  "wp.action.download": "Download",
  "wp.action.download.waypoint": "Send {0} to your map mod",
  "wp.action.download.list": "Send the waypoints of {0} to your map mod",
  "wp.action.remove": "Remove",
  "wp.action.remove.tooltip": "Remove {0}",
  "wp.action.remove.blocked": "Only empty lists can be removed",
  "wp.action.remove.blocked.detail": "Remove its {0} first",
  "wp.action.back.tooltip": "Back to {0}",
  "wp.action.open_list": "Open list",
  "wp.action.waypoint_here": "Waypoint here",
  "wp.action.waypoint": "Waypoint",
  "wp.updated.identifier": "Updated the identifier",
  "wp.updated.display_name": "Updated the display name",
  "wp.updated.initials": "Updated the initials",
  "wp.updated.position": "Updated the position",
  "wp.updated.color": "Updated the colour",
  "wp.updated.yaw": "Updated the facing",
  "wp.updated.visibility": "Updated the visibility",
  "wp.updated.keywords": "Updated the keywords",
  "wp.updated.description": "Updated the description",
  "wp.updated.icon": "Updated the icon",
  "wp.cleared.display_name": "Cleared the display name",
  "wp.cleared.keywords": "Cleared the keywords",
  "wp.cleared.description": "Cleared the description",
  "wp.cleared.icon": "Cleared the icon"
```

`zh_cn.json`:

```json
  "wp.details.property": "{0}：{1}",
  "wp.details.display_name": "显示名称",
  "wp.details.identifier": "标识符",
  "wp.details.initials": "缩写",
  "wp.details.icon": "图标",
  "wp.details.position": "位置",
  "wp.details.yaw": "偏航角",
  "wp.details.visibility": "可见范围",
  "wp.details.keywords": "关键词",
  "wp.details.description": "描述",
  "wp.details.none": "无",
  "wp.details.here": "此处",
  "wp.details.here.tooltip": "移到你站立的位置",
  "wp.visibility.global": "全局",
  "wp.visibility.local": "本地",
  "wp.edit.display_name": "编辑显示名称",
  "wp.edit.identifier": "编辑标识符",
  "wp.edit.initials": "编辑缩写",
  "wp.edit.icon": "编辑图标",
  "wp.edit.position": "编辑位置",
  "wp.edit.color": "选择颜色",
  "wp.edit.yaw": "选择朝向",
  "wp.edit.visibility": "切换为{0}",
  "wp.edit.keywords": "编辑关键词",
  "wp.edit.description": "编辑描述",
  "wp.hint.edit": "修改后按回车",
  "wp.clear.display_name": "清除显示名称",
  "wp.clear.icon": "清除图标",
  "wp.clear.keywords": "清除关键词",
  "wp.clear.description": "清除描述",
  "wp.action.navigate": "导航",
  "wp.action.navigate.tooltip": "导航到{0}",
  "wp.action.teleport": "传送",
  "wp.action.download": "下载",
  "wp.action.download.waypoint": "将{0}发送到你的地图模组",
  "wp.action.download.list": "将{0}中的路径点发送到你的地图模组",
  "wp.action.remove": "删除",
  "wp.action.remove.tooltip": "删除{0}",
  "wp.action.remove.blocked": "只能删除空列表",
  "wp.action.remove.blocked.detail": "请先删除其中的 {0}",
  "wp.action.back.tooltip": "返回{0}",
  "wp.action.open_list": "打开列表",
  "wp.action.waypoint_here": "在此添加路径点",
  "wp.action.waypoint": "路径点",
  "wp.updated.identifier": "已更新标识符",
  "wp.updated.display_name": "已更新显示名称",
  "wp.updated.initials": "已更新缩写",
  "wp.updated.position": "已更新位置",
  "wp.updated.color": "已更新颜色",
  "wp.updated.yaw": "已更新朝向",
  "wp.updated.visibility": "已更新可见范围",
  "wp.updated.keywords": "已更新关键词",
  "wp.updated.description": "已更新描述",
  "wp.updated.icon": "已更新图标",
  "wp.cleared.display_name": "已清除显示名称",
  "wp.cleared.keywords": "已清除关键词",
  "wp.cleared.description": "已清除描述",
  "wp.cleared.icon": "已清除图标"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.details.property": "{0}：{1}",
  "wp.details.display_name": "顯示名稱",
  "wp.details.identifier": "識別碼",
  "wp.details.initials": "縮寫",
  "wp.details.icon": "圖示",
  "wp.details.position": "位置",
  "wp.details.yaw": "偏航角",
  "wp.details.visibility": "可見範圍",
  "wp.details.keywords": "關鍵字",
  "wp.details.description": "描述",
  "wp.details.none": "無",
  "wp.details.here": "此處",
  "wp.details.here.tooltip": "移到你站立的位置",
  "wp.visibility.global": "全域",
  "wp.visibility.local": "本地",
  "wp.edit.display_name": "編輯顯示名稱",
  "wp.edit.identifier": "編輯識別碼",
  "wp.edit.initials": "編輯縮寫",
  "wp.edit.icon": "編輯圖示",
  "wp.edit.position": "編輯位置",
  "wp.edit.color": "選擇顏色",
  "wp.edit.yaw": "選擇朝向",
  "wp.edit.visibility": "切換為{0}",
  "wp.edit.keywords": "編輯關鍵字",
  "wp.edit.description": "編輯描述",
  "wp.hint.edit": "修改後按 Enter",
  "wp.clear.display_name": "清除顯示名稱",
  "wp.clear.icon": "清除圖示",
  "wp.clear.keywords": "清除關鍵字",
  "wp.clear.description": "清除描述",
  "wp.action.navigate": "導航",
  "wp.action.navigate.tooltip": "導航到{0}",
  "wp.action.teleport": "傳送",
  "wp.action.download": "下載",
  "wp.action.download.waypoint": "將{0}傳送到你的地圖模組",
  "wp.action.download.list": "將{0}中的路徑點傳送到你的地圖模組",
  "wp.action.remove": "刪除",
  "wp.action.remove.tooltip": "刪除{0}",
  "wp.action.remove.blocked": "只能刪除空列表",
  "wp.action.remove.blocked.detail": "請先刪除其中的 {0}",
  "wp.action.back.tooltip": "返回{0}",
  "wp.action.open_list": "開啟列表",
  "wp.action.waypoint_here": "在此新增路徑點",
  "wp.action.waypoint": "路徑點",
  "wp.updated.identifier": "已更新識別碼",
  "wp.updated.display_name": "已更新顯示名稱",
  "wp.updated.initials": "已更新縮寫",
  "wp.updated.position": "已更新位置",
  "wp.updated.color": "已更新顏色",
  "wp.updated.yaw": "已更新朝向",
  "wp.updated.visibility": "已更新可見範圍",
  "wp.updated.keywords": "已更新關鍵字",
  "wp.updated.description": "已更新描述",
  "wp.updated.icon": "已更新圖示",
  "wp.cleared.display_name": "已清除顯示名稱",
  "wp.cleared.keywords": "已清除關鍵字",
  "wp.cleared.description": "已清除描述",
  "wp.cleared.icon": "已清除圖示"
```

`es_es.json`:

```json
  "wp.details.property": "{0}: {1}",
  "wp.details.display_name": "Nombre visible",
  "wp.details.identifier": "Identificador",
  "wp.details.initials": "Iniciales",
  "wp.details.icon": "Icono",
  "wp.details.position": "Posición",
  "wp.details.yaw": "Orientación",
  "wp.details.visibility": "Visibilidad",
  "wp.details.keywords": "Palabras clave",
  "wp.details.description": "Descripción",
  "wp.details.none": "ninguno",
  "wp.details.here": "Aquí",
  "wp.details.here.tooltip": "Muévelo a donde estás",
  "wp.visibility.global": "Global",
  "wp.visibility.local": "Local",
  "wp.edit.display_name": "Editar el nombre visible",
  "wp.edit.identifier": "Editar el identificador",
  "wp.edit.initials": "Editar las iniciales",
  "wp.edit.icon": "Editar el icono",
  "wp.edit.position": "Editar la posición",
  "wp.edit.color": "Elegir un color",
  "wp.edit.yaw": "Elegir una orientación",
  "wp.edit.visibility": "Cambiar a {0}",
  "wp.edit.keywords": "Editar las palabras clave",
  "wp.edit.description": "Editar la descripción",
  "wp.hint.edit": "Cámbialo y pulsa Intro",
  "wp.clear.display_name": "Borrar el nombre visible",
  "wp.clear.icon": "Borrar el icono",
  "wp.clear.keywords": "Borrar las palabras clave",
  "wp.clear.description": "Borrar la descripción",
  "wp.action.navigate": "Navegar",
  "wp.action.navigate.tooltip": "Navegar hasta {0}",
  "wp.action.teleport": "Teletransportar",
  "wp.action.download": "Descargar",
  "wp.action.download.waypoint": "Enviar {0} a tu mod de mapas",
  "wp.action.download.list": "Enviar los puntos de {0} a tu mod de mapas",
  "wp.action.remove": "Quitar",
  "wp.action.remove.tooltip": "Quitar {0}",
  "wp.action.remove.blocked": "Solo se pueden quitar listas vacías",
  "wp.action.remove.blocked.detail": "Quita antes sus {0}",
  "wp.action.back.tooltip": "Volver a {0}",
  "wp.action.open_list": "Abrir lista",
  "wp.action.waypoint_here": "Punto aquí",
  "wp.action.waypoint": "Punto",
  "wp.updated.identifier": "Identificador actualizado",
  "wp.updated.display_name": "Nombre visible actualizado",
  "wp.updated.initials": "Iniciales actualizadas",
  "wp.updated.position": "Posición actualizada",
  "wp.updated.color": "Color actualizado",
  "wp.updated.yaw": "Orientación actualizada",
  "wp.updated.visibility": "Visibilidad actualizada",
  "wp.updated.keywords": "Palabras clave actualizadas",
  "wp.updated.description": "Descripción actualizada",
  "wp.updated.icon": "Icono actualizado",
  "wp.cleared.display_name": "Nombre visible borrado",
  "wp.cleared.keywords": "Palabras clave borradas",
  "wp.cleared.description": "Descripción borrada",
  "wp.cleared.icon": "Icono borrado"
```

`he_il.json`:

```json
  "wp.details.property": "{0}: {1}",
  "wp.details.display_name": "שם תצוגה",
  "wp.details.identifier": "מזהה",
  "wp.details.initials": "ראשי תיבות",
  "wp.details.icon": "סמל",
  "wp.details.position": "מיקום",
  "wp.details.yaw": "כיוון",
  "wp.details.visibility": "נראות",
  "wp.details.keywords": "מילות מפתח",
  "wp.details.description": "תיאור",
  "wp.details.none": "אין",
  "wp.details.here": "כאן",
  "wp.details.here.tooltip": "העברה למקום שבו אתם עומדים",
  "wp.visibility.global": "גלובלית",
  "wp.visibility.local": "מקומית",
  "wp.edit.display_name": "עריכת שם התצוגה",
  "wp.edit.identifier": "עריכת המזהה",
  "wp.edit.initials": "עריכת ראשי התיבות",
  "wp.edit.icon": "עריכת הסמל",
  "wp.edit.position": "עריכת המיקום",
  "wp.edit.color": "בחירת צבע",
  "wp.edit.yaw": "בחירת כיוון",
  "wp.edit.visibility": "מעבר ל{0}",
  "wp.edit.keywords": "עריכת מילות המפתח",
  "wp.edit.description": "עריכת התיאור",
  "wp.hint.edit": "שנו ולחצו Enter",
  "wp.clear.display_name": "ניקוי שם התצוגה",
  "wp.clear.icon": "ניקוי הסמל",
  "wp.clear.keywords": "ניקוי מילות המפתח",
  "wp.clear.description": "ניקוי התיאור",
  "wp.action.navigate": "ניווט",
  "wp.action.navigate.tooltip": "ניווט אל {0}",
  "wp.action.teleport": "שיגור",
  "wp.action.download": "הורדה",
  "wp.action.download.waypoint": "שליחת {0} למוד המפה שלכם",
  "wp.action.download.list": "שליחת הנקודות של {0} למוד המפה שלכם",
  "wp.action.remove": "הסרה",
  "wp.action.remove.tooltip": "הסרת {0}",
  "wp.action.remove.blocked": "אפשר להסיר רק רשימות ריקות",
  "wp.action.remove.blocked.detail": "הסירו קודם את {0} שבה",
  "wp.action.back.tooltip": "חזרה אל {0}",
  "wp.action.open_list": "פתיחת הרשימה",
  "wp.action.waypoint_here": "נקודה כאן",
  "wp.action.waypoint": "נקודה",
  "wp.updated.identifier": "המזהה עודכן",
  "wp.updated.display_name": "שם התצוגה עודכן",
  "wp.updated.initials": "ראשי התיבות עודכנו",
  "wp.updated.position": "המיקום עודכן",
  "wp.updated.color": "הצבע עודכן",
  "wp.updated.yaw": "הכיוון עודכן",
  "wp.updated.visibility": "הנראות עודכנה",
  "wp.updated.keywords": "מילות המפתח עודכנו",
  "wp.updated.description": "התיאור עודכן",
  "wp.updated.icon": "הסמל עודכן",
  "wp.cleared.display_name": "שם התצוגה נוקה",
  "wp.cleared.keywords": "מילות המפתח נוקו",
  "wp.cleared.description": "התיאור נוקה",
  "wp.cleared.icon": "הסמל נוקה"
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/DetailsScreenTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.find;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailsScreenTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final SimpleWaypoint MAIN_HOME = HOME.getWaypointByName("Main Home");
    private static final String EDIT = "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" ";

    private static Component mainHome() {
        return DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME, null);
    }

    @Test
    void waypointDetailsListEveryPropertyWithItsEditButton() {
        assertEquals(List.of(
                "Overworld › Home Bases › [MH] Main Home",
                "[✎] Display name: Main Home",
                "[✎] Identifier: Main Home",
                "[✎] Initials: MH",
                "[✎] Icon: none",
                "[✎] Position: 120, 64, -35 [Here]",
                "[✎] Color: ■ #FFAA00",
                "[✎] Yaw: 0° (south)",
                "[✎] Visibility: Global",
                "[✎] Keywords: home, base [×]",
                "[✎] Description: Where the beds are [×]",
                "[Navigate] [Teleport] [Download] [Remove] [Back]"), lines(mainHome()));
    }

    @Test
    void theBreadcrumbOpensTheDimensionAndTheListAndTheNameIsGold() {
        Component details = mainHome();

        assertEquals("/wp list minecraft:overworld", clickOf(details, "Overworld"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(details, "Home Bases"));
        assertEquals(NamedTextColor.WHITE, colorOf(details, "Home Bases"));
        assertEquals("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(details, "[MH]"));
        assertEquals(NamedTextColor.GOLD, colorOf(details, "Main Home"));
        assertNull(clickOf(details, "Main Home"));
    }

    @Test
    void editButtonsSuggestTheCurrentValueWhileColorYawAndVisibilityRun() {
        Component details = mainHome();

        assertTrue(suggestions(details).containsAll(List.of(
                EDIT + "set display-name \"Main Home\"",
                EDIT + "set identifier \"Main Home\"",
                EDIT + "set initials MH",
                EDIT + "set icon ",
                EDIT + "set position 120 64 -35",
                EDIT + "set keywords \"home, base\"",
                EDIT + "set description \"Where the beds are\"",
                EDIT + "clear keywords",
                EDIT + "clear description",
                "/wp remove minecraft:overworld \"Home Bases\" \"Main Home\"")));
        assertTrue(runCommands(details).containsAll(List.of(
                EDIT + "set position ~ ~ ~",
                EDIT + "set color",
                EDIT + "set yaw",
                EDIT + "set visibility local",
                "/wp navigate minecraft:overworld \"Home Bases\" \"Main Home\"",
                "/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"",
                "/wp download minecraft:overworld \"Home Bases\" \"Main Home\"",
                "/wp list minecraft:overworld \"Home Bases\"")));
        assertEquals(NamedTextColor.YELLOW, colorOf(details, "[✎]"));
        assertEquals(NamedTextColor.RED, colorOf(details, "[×]"));
        assertEquals(NamedTextColor.GREEN, colorOf(details, "[Here]"));
        assertEquals("Clear the keywords\nPress Enter to confirm", tooltipOf(details, "[×]"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, colorOf(details, "[Navigate]"));
        assertEquals(NamedTextColor.AQUA, colorOf(details, "[Download]"));
        assertEquals("Remove Main Home\nPress Enter to confirm", tooltipOf(details, "[Remove]"));
        assertEquals("Back to Home Bases", tooltipOf(details, "[Back]"));
    }

    @Test
    void labelsAreGrayValuesWhiteAndNoneDarkGrayItalics() {
        Component details = mainHome();

        assertEquals(NamedTextColor.GRAY, colorOf(details, "Initials"));
        assertEquals(NamedTextColor.WHITE, colorOf(details, "MH"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(details, "none"));
        assertEquals(TextDecoration.State.TRUE, find(details, "none").style().decoration(TextDecoration.ITALIC));
    }

    @Test
    void withoutEditPermissionTheEditColumnClearAndHereAreHidden() {
        List<String> lines = lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.member()), OVERWORLD, HOME, MAIN_HOME, null));

        assertEquals("Display name: Main Home", lines.get(1));
        assertEquals("Position: 120, 64, -35", lines.get(5));
        assertEquals("Keywords: home, base", lines.get(9));
        assertEquals("[Navigate] [Back]", lines.get(11));
    }

    @Test
    void hereOnlyAppearsInTheWaypointsDimension() {
        List<String> lines = lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.in(Fixtures.player(), NETHER)),
                OVERWORLD, HOME, MAIN_HOME, null));

        assertEquals("[✎] Position: 120, 64, -35", lines.get(5));
    }

    @Test
    void displayNameOverridesAndIconsCanBeCleared() {
        SimpleWaypoint renamed = new SimpleWaypoint("main_home", "Home", "MH", new WaypointPos(0, 64, 0), 0xFFAA00, 90, false,
                List.of(), "", _959.server_waypoint.util.NamespacedId.parse("minecraft:diamond"));
        List<String> lines = lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, renamed, null));

        assertEquals("[✎] Display name: Home [×]", lines.get(1));
        assertEquals("[✎] Identifier: main_home", lines.get(2));
        assertEquals("[✎] Icon: minecraft:diamond [×]", lines.get(4));
        assertEquals("[✎] Yaw: 90° (west)", lines.get(7));
        assertEquals("[✎] Visibility: Local", lines.get(8));
        assertEquals("[✎] Keywords: none", lines.get(9));
    }

    @Test
    void blankIdentifiersShowInQuotes() {
        SimpleWaypoint blank = new SimpleWaypoint("", "E", new WaypointPos(0, 64, 0), 0, 0, true);

        assertEquals("[✎] Identifier: \"\"",
                lines(DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, blank, null)).get(2));
    }

    @Test
    void anEditPutsItsResultOnTop() {
        Component details = DetailsScreen.waypoint(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME,
                DetailsScreen.updated(patch(PatchField.unchanged(), PatchField.set(0x55FF55), PatchField.unchanged())));

        assertEquals("✔ Updated the colour", lines(details).get(0));
        assertEquals(NamedTextColor.GREEN, colorOf(details, "✔ "));
        assertEquals("Overworld › Home Bases › [MH] Main Home", lines(details).get(1));
    }

    @Test
    void theResultNamesTheFirstPropertyThePatchChanges() {
        assertEquals("Updated the facing", render(DetailsScreen.updated(
                patch(PatchField.unchanged(), PatchField.unchanged(), PatchField.set(90)))));
        assertEquals("Cleared the display name", render(DetailsScreen.updated(
                new WaypointListPatch(PatchField.unchanged(), PatchField.clear()))));
        assertEquals("Updated the identifier", render(DetailsScreen.updated(
                new WaypointListPatch(PatchField.set("farms"), PatchField.unchanged()))));
        assertNull(DetailsScreen.updated(WaypointPatch.empty()));
    }

    @Test
    void plainTextDetailsKeepEveryValueWithoutButtons() {
        Component details = DetailsScreen.waypoint(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, MAIN_HOME, null);

        assertEquals(List.of(
                "Overworld (minecraft:overworld) › Home Bases › [MH] Main Home",
                "Display name: Main Home",
                "Identifier: Main Home",
                "Initials: MH",
                "Icon: none",
                "Position: 120, 64, -35",
                "Color: ■ #FFAA00",
                "Yaw: 0° (south)",
                "Visibility: Global",
                "Keywords: home, base",
                "Description: Where the beds are"), lines(details));
        assertTrue(runCommands(details).isEmpty());
        assertTrue(suggestions(details).isEmpty());
    }

    @Test
    void listDetailsShowTheCountAndOnlyEmptyListsCanBeRemoved() {
        Component details = DetailsScreen.list(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, null);

        assertEquals(List.of(
                "Overworld › Home Bases  3 waypoints",
                "[✎] Display name: Home Bases",
                "[✎] Identifier: Home Bases",
                "[Open list] [+ Waypoint here] [Download] [Remove] [Back]"), lines(details));
        assertEquals(NamedTextColor.GOLD, colorOf(details, "Home Bases"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(details, "[Open list]"));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" ", clickOf(details, "[+ Waypoint here]"));
        assertEquals("/wp download minecraft:overworld \"Home Bases\"", clickOf(details, "[Download]"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(details, "[Remove]"));
        assertNull(clickOf(details, "[Remove]"));
        assertEquals("Only empty lists can be removed\nRemove its 3 waypoints first", tooltipOf(details, "[Remove]"));
        assertEquals("/wp list minecraft:overworld", clickOf(details, "[Back]"));
    }

    @Test
    void anEmptyListElsewhereAddsByCoordinatesAndCanBeRemoved() {
        Component details = DetailsScreen.list(Fixtures.dims(Fixtures.player()), NETHER, new WaypointList("Storage", 1, List.of()), null);

        assertEquals("Nether › Storage  0 waypoints", lines(details).get(0));
        assertEquals("[Open list] [+ Waypoint] [Remove] [Back]", lines(details).get(3));
        assertEquals("/wp add minecraft:the_nether Storage ", clickOf(details, "[+ Waypoint]"));
        assertEquals(NamedTextColor.RED, colorOf(details, "[Remove]"));
        assertEquals("/wp remove minecraft:the_nether Storage", clickOf(details, "[Remove]"));
    }

    @Test
    void renamedListsCanClearTheirDisplayNameAndPlainTextShowsTheIdentifier() {
        WaypointList renamed = new WaypointList("farms", "Farms", 1, Fixtures.farms().simpleWaypoints());

        assertEquals("[✎] Display name: Farms [×]",
                lines(DetailsScreen.list(Fixtures.dims(Fixtures.player()), OVERWORLD, renamed, null)).get(1));
        assertEquals(List.of(
                "Overworld (minecraft:overworld) › Farms (farms)  7 waypoints",
                "Display name: Farms",
                "Identifier: farms"), lines(DetailsScreen.list(Fixtures.dims(Fixtures.console()), OVERWORLD, renamed, null)));
    }

    private static WaypointPatch patch(PatchField<WaypointPos> position, PatchField<Integer> color, PatchField<Integer> yaw) {
        return new WaypointPatch(PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), position, color, yaw,
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.DetailsScreenTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable DetailsScreen`.

- [ ] **Step 4: Add the download command builder**

In `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`, replace:

```java
    public static String restoreCmd(String token) {
        return WAYPOINT_COMMAND_WITH_SLASH + " restore " + escapeArgument(token);
    }
```

with:

```java
    public static String restoreCmd(String token) {
        return WAYPOINT_COMMAND_WITH_SLASH + " restore " + escapeArgument(token);
    }

    /** /wp download <dimension> [<list> [<waypoint>]] */
    public static String downloadCmd(String dimensionName, @Nullable String listName, @Nullable String waypointName) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(DOWNLOAD_COMMAND)
                .append(' ').append(dimensionName);
        if (listName != null) {
            command.append(' ').append(escapeArgument(listName));
            if (waypointName != null) {
                command.append(' ').append(escapeArgument(waypointName));
            }
        }
        return command.toString();
    }
```

- [ ] **Step 5: Write `DetailsScreen`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/DetailsScreen.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.FormattedTextHelper.parse;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.StringCommandBuilder.downloadCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editListClearCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editListSetSuggestionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointClearCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointIconSuggestionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointPositionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointSetSuggestionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.navigateCmd;
import static _959.server_waypoint.util.StringCommandBuilder.removeCmd;
import static _959.server_waypoint.util.StringCommandBuilder.removeListCmd;
import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** Waypoint and list details (spec 8): a breadcrumb, one property per line and a row of buttons. */
public final class DetailsScreen {
    private DetailsScreen() {
    }

    /** /wp details waypoint, with the result of an edit on top. */
    public static Component waypoint(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                     @Nullable Component updated) {
        Viewer viewer = dims.viewer();
        String listId = list.name();
        String id = waypoint.name();
        String listCommand = ListTarget.list(dimension, listId).command(ListQuery.DEFAULT);
        Component name = WaypointRefs.label(waypoint.displayName(), id);
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        lines.line(ListScreen.dimensionLink(dims, dimension), Chat.CRUMB,
                WaypointRefs.listLink(dims, list, WHITE, Click.run(listCommand), "wp.hint.open"),
                Chat.CRUMB, WaypointRefs.title(dims, dimension, list, waypoint, GOLD));
        lines.add(property(suggestEdit(viewer, "display_name",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "display-name", waypoint.displayName())),
                "display_name", name,
                waypoint.hasDisplayNameOverride()
                        ? clear(viewer, "display_name", editWaypointClearCmd(dimension, listId, id, "display-name"))
                        : null));
        lines.add(property(suggestEdit(viewer, "identifier",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "identifier", id)),
                "identifier", identifier(id), null));
        lines.add(property(suggestEdit(viewer, "initials",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "initials", waypoint.initials())),
                "initials", text(waypoint.initials()), null));
        lines.add(property(suggestEdit(viewer, "icon", editWaypointIconSuggestionCmd(dimension, listId, id, waypoint.icon())),
                "icon", waypoint.icon() == null ? none() : text(waypoint.icon().toString()),
                waypoint.icon() == null ? null : clear(viewer, "icon", editWaypointClearCmd(dimension, listId, id, "icon"))));
        lines.add(property(suggestEdit(viewer, "position", editWaypointPositionCmd(dimension, listId, id, waypoint)),
                "position", text(DimensionStyle.coordinates(waypoint.pos())),
                viewer.can(Viewer.Permission.EDIT) && viewer.isIn(dimension)
                        ? Chat.button(viewer, translatable("wp.details.here"), GREEN,
                        Click.run(editWaypointCmd(dimension, listId, id, "set position ~ ~ ~")),
                        Tooltip.of("wp.details.here.tooltip"))
                        : null));
        lines.add(property(edit(viewer, Click.run(editWaypointCmd(dimension, listId, id, "set color")),
                Tooltip.of("wp.edit.color")), "color", PickerScreens.colorValue(waypoint.rgb()), null));
        lines.add(property(edit(viewer, Click.run(editWaypointCmd(dimension, listId, id, "set yaw")),
                Tooltip.of("wp.edit.yaw")), "yaw", PickerScreens.yawValue(waypoint.yaw()), null));
        String toggled = waypoint.global() ? "local" : "global";
        lines.add(property(edit(viewer, Click.run(editWaypointCmd(dimension, listId, id, "set visibility " + toggled)),
                        Tooltip.of("wp.edit.visibility", translatable("wp.visibility." + toggled))),
                "visibility", translatable(waypoint.global() ? "wp.visibility.global" : "wp.visibility.local"), null));
        String keywords = String.join(", ", waypoint.keywords());
        lines.add(property(suggestEdit(viewer, "keywords",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "keywords", keywords)),
                "keywords", keywords.isEmpty() ? none() : text(keywords),
                keywords.isEmpty() ? null : clear(viewer, "keywords", editWaypointClearCmd(dimension, listId, id, "keywords"))));
        String description = waypoint.description();
        lines.add(property(suggestEdit(viewer, "description",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "description", description)),
                "description", plainText(description).isBlank() ? none() : parse(description),
                description.isEmpty() ? null
                        : clear(viewer, "description", editWaypointClearCmd(dimension, listId, id, "description"))));
        lines.add(buttons(
                viewer.can(Viewer.Permission.NAVIGATE)
                        ? Chat.button(viewer, translatable("wp.action.navigate"), LIGHT_PURPLE,
                        Click.run(navigateCmd(dimension, listId, id)), Tooltip.of("wp.action.navigate.tooltip", name))
                        : null,
                viewer.can(Viewer.Permission.TP)
                        ? Chat.button(viewer, translatable("wp.action.teleport"), LIGHT_PURPLE,
                        Click.run(tpCmd(dimension, listId, id)), WaypointRefs.teleportTooltip(dims, dimension, waypoint))
                        : null,
                viewer.hasMod()
                        ? Chat.button(viewer, translatable("wp.action.download"), AQUA,
                        Click.run(downloadCmd(dimension, listId, id)), Tooltip.of("wp.action.download.waypoint", name))
                        : null,
                viewer.can(Viewer.Permission.REMOVE)
                        ? Chat.button(viewer, translatable("wp.action.remove"), RED,
                        Click.suggest(removeCmd(dimension, listId, waypoint)),
                        Tooltip.of("wp.action.remove.tooltip", name).hint("wp.hint.confirm"))
                        : null,
                Chat.button(viewer, translatable("wp.action.back"), GRAY, Click.run(listCommand),
                        Tooltip.of("wp.action.back.tooltip", WaypointRefs.label(list.displayName(), listId)))));
        return lines.build();
    }

    /** /wp details list, with the result of an edit on top. */
    public static Component list(DimensionStyle dims, String dimension, WaypointList list, @Nullable Component updated) {
        Viewer viewer = dims.viewer();
        String listId = list.name();
        Component name = WaypointRefs.label(list.displayName(), listId);
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        lines.line(ListScreen.dimensionLink(dims, dimension), Chat.CRUMB,
                Chat.hover(viewer, Chat.colored(WaypointRefs.label(viewer, list.displayName(), listId), GOLD),
                        WaypointRefs.listTooltip(list, null)),
                text("  "), Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY));
        lines.add(property(suggestEdit(viewer, "display_name",
                        editListSetSuggestionCmd(dimension, listId, "display-name", list.displayName())),
                "display_name", name,
                list.hasDisplayNameOverride()
                        ? clear(viewer, "display_name", editListClearCmd(dimension, listId, "display-name"))
                        : null));
        lines.add(property(suggestEdit(viewer, "identifier", editListSetSuggestionCmd(dimension, listId, "identifier", listId)),
                "identifier", identifier(listId), null));
        lines.add(buttons(
                Chat.button(viewer, translatable("wp.action.open_list"), AQUA,
                        Click.run(ListTarget.list(dimension, listId).command(ListQuery.DEFAULT)), Tooltip.of("wp.open", name)),
                viewer.can(Viewer.Permission.ADD)
                        ? Chat.button(viewer, Chat.concat(text("+ "),
                                translatable(viewer.isIn(dimension) ? "wp.action.waypoint_here" : "wp.action.waypoint")),
                        GREEN, ListActions.addClick(viewer, dimension, list), ListActions.addTooltip(dims, dimension, list))
                        : null,
                viewer.hasMod() && !list.isEmpty()
                        ? Chat.button(viewer, translatable("wp.action.download"), AQUA,
                        Click.run(downloadCmd(dimension, listId, null)), Tooltip.of("wp.action.download.list", name))
                        : null,
                removeList(viewer, dimension, list, name),
                Chat.button(viewer, translatable("wp.action.back"), GRAY,
                        Click.run(ListTarget.dimension(dimension).command(ListQuery.DEFAULT)),
                        Tooltip.of("wp.action.back.tooltip", dims.name(dimension)))));
        return lines.build();
    }

    /** "Updated the colour" or "Cleared the description": the first property the patch changes. */
    public static @Nullable Component updated(WaypointPatch patch) {
        Map<String, PatchField<?>> fields = new LinkedHashMap<>();
        fields.put("identifier", patch.identifier());
        fields.put("display_name", patch.displayName());
        fields.put("initials", patch.initials());
        fields.put("position", patch.position());
        fields.put("color", patch.color());
        fields.put("yaw", patch.yaw());
        fields.put("visibility", patch.visibility());
        fields.put("keywords", patch.keywords());
        fields.put("description", patch.description());
        fields.put("icon", patch.icon());
        return updated(fields);
    }

    public static @Nullable Component updated(WaypointListPatch patch) {
        Map<String, PatchField<?>> fields = new LinkedHashMap<>();
        fields.put("identifier", patch.identifier());
        fields.put("display_name", patch.displayName());
        return updated(fields);
    }

    private static @Nullable Component updated(Map<String, PatchField<?>> fields) {
        for (Map.Entry<String, PatchField<?>> field : fields.entrySet()) {
            if (field.getValue().isClear()) {
                return translatable("wp.cleared." + field.getKey());
            }
            if (field.getValue().isSet()) {
                return translatable("wp.updated." + field.getKey());
            }
        }
        return null;
    }

    /** [✎] Label: value, then [×] or [Here]. */
    private static Component property(@Nullable Component edit, String field, Component value, @Nullable Component after) {
        return Chat.concat(edit, edit == null ? null : text(" "),
                translatable("wp.details.property", GRAY, translatable("wp.details." + field), Chat.colored(value, WHITE)),
                after == null ? null : text(" "), after);
    }

    /** The yellow [✎]; hidden without the edit permission. */
    private static @Nullable Component edit(Viewer viewer, Click click, Tooltip tooltip) {
        return viewer.can(Viewer.Permission.EDIT) ? Chat.button(viewer, text("✎"), YELLOW, click, tooltip) : null;
    }

    /** [✎] suggesting "set <property> <current value>". */
    private static @Nullable Component suggestEdit(Viewer viewer, String field, String command) {
        return edit(viewer, Click.suggest(command), Tooltip.of("wp.edit." + field).hint("wp.hint.edit"));
    }

    /** The red [×] suggesting "clear <property>". */
    private static @Nullable Component clear(Viewer viewer, String field, String command) {
        return viewer.can(Viewer.Permission.EDIT)
                ? Chat.button(viewer, text("×"), RED, Click.suggest(command),
                Tooltip.of("wp.clear." + field).hint("wp.hint.confirm"))
                : null;
    }

    /** Red for an empty list; dark gray with the reason otherwise. */
    private static @Nullable Component removeList(Viewer viewer, String dimension, WaypointList list, Component name) {
        if (!viewer.can(Viewer.Permission.REMOVE)) {
            return null;
        }
        if (list.isEmpty()) {
            return Chat.button(viewer, translatable("wp.action.remove"), RED,
                    Click.suggest(removeListCmd(dimension, list.name(), true)),
                    Tooltip.of("wp.action.remove.tooltip", name).hint("wp.hint.confirm"));
        }
        return Chat.disabledButton(viewer, translatable("wp.action.remove"), Tooltip.of("wp.action.remove.blocked")
                .line(translatable("wp.action.remove.blocked.detail", Chat.count("wp.count.waypoint", list.size()))));
    }

    /** The buttons one space apart, or no line when none is left. */
    private static @Nullable Component buttons(@Nullable Component... buttons) {
        List<Component> row = Arrays.asList(buttons);
        return Chat.isEmpty(row) ? null : Chat.spaced(row);
    }

    /** An identifier as typed; a blank one in quotes so it stays visible. */
    private static Component identifier(String identifier) {
        return text(identifier.isBlank() ? "\"" + identifier + "\"" : identifier);
    }

    private static Component none() {
        return translatable("wp.details.none", DARK_GRAY).decorate(TextDecoration.ITALIC);
    }
}
```

- [ ] **Step 6: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 7: Send the details screens from the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.feedback.ListScreen;
```

with:

```java
import _959.server_waypoint.text.feedback.DetailsScreen;
import _959.server_waypoint.text.feedback.ListScreen;
```

In `executeListPatch`, replace:

```java
                        this.sender.sendMessage(
                                source,
                                _959.server_waypoint.text.WaypointDetailsTextBuilder.listDetails(
                                        dimensionName,
                                        after,
                                        hasAddPermission(source),
                                        hasEditPermission(source),
                                        hasRemovePermission(source)
                                )
                        );
```

with:

```java
                        Viewer viewer = viewer(source);
                        this.sender.sendMessage(source, DetailsScreen.list(dimensions(source, viewer), dimensionName,
                                after, DetailsScreen.updated(patch)));
```

In `executeWaypointPatch`, replace:

```java
                        this.sender.sendMessage(
                                source,
                                _959.server_waypoint.text.WaypointDetailsTextBuilder.waypointDetails(
                                        dimensionName,
                                        list,
                                        after,
                                        hasEditPermission(source),
                                        hasRemovePermission(source),
                                        hasTpPermission(source),
                                        hasNavigatePermission(source)
                                )
                        );
```

with:

```java
                        Viewer viewer = viewer(source);
                        this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source, viewer), dimensionName,
                                list, after, DetailsScreen.updated(patch)));
```

Replace the whole of `executeListDetails` and `executeWaypointDetails`:

```java
    private void executeListDetails(S source, D dimensionArgument, String listIdentifier) {
        BiConsumer<WaypointFileManager, WaypointList> action = (fileManager, waypointList) ->
                this.sender.sendMessage(
                        source,
                        _959.server_waypoint.text.WaypointDetailsTextBuilder.listDetails(
                                fileManager.getDimensionName(),
                                waypointList,
                                hasAddPermission(source),
                                hasEditPermission(source),
                                hasRemovePermission(source)
                        )
                );
        runWithSelectorTarget(source, dimensionArgument, listIdentifier, action, action);
    }

    private void executeWaypointDetails(
            S source,
            D dimensionArgument,
            String listIdentifier,
            String waypointIdentifier
    ) {
        runWithSelectorTarget(
                source,
                dimensionArgument,
                listIdentifier,
                waypointIdentifier,
                (fileManager, waypointList, waypoint) -> this.sender.sendMessage(
                        source,
                        _959.server_waypoint.text.WaypointDetailsTextBuilder.waypointDetails(
                                fileManager.getDimensionName(),
                                waypointList,
                                waypoint,
                                hasEditPermission(source),
                                hasRemovePermission(source),
                                hasTpPermission(source),
                                hasNavigatePermission(source)
                        )
                )
        );
    }
```

with:

```java
    private void executeListDetails(S source, D dimensionArgument, String listIdentifier) {
        BiConsumer<WaypointFileManager, WaypointList> action = (fileManager, waypointList) -> {
            Viewer viewer = viewer(source);
            this.sender.sendMessage(source, DetailsScreen.list(dimensions(source, viewer), fileManager.getDimensionName(),
                    waypointList, null));
        };
        runWithSelectorTarget(source, dimensionArgument, listIdentifier, action, action);
    }

    private void executeWaypointDetails(
            S source,
            D dimensionArgument,
            String listIdentifier,
            String waypointIdentifier
    ) {
        runWithSelectorTarget(source, dimensionArgument, listIdentifier, waypointIdentifier,
                (fileManager, waypointList, waypoint) -> {
                    Viewer viewer = viewer(source);
                    this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source, viewer),
                            fileManager.getDimensionName(), waypointList, waypoint, null));
                });
    }
```

In `executeRestore`, replace:

```java
                                    this.sender.sendMessage(
                                            source,
                                            _959.server_waypoint.text.WaypointDetailsTextBuilder.waypointDetails(
                                                    entry.dimensionName(),
                                                    list,
                                                    waypoint,
                                                    hasEditPermission(source),
                                                    hasRemovePermission(source),
                                                    hasTpPermission(source),
                                                    hasNavigatePermission(source)
                                            )
                                    );
```

with (Task 13 turns this into the `✔ Restored …` result):

```java
                                    Viewer viewer = viewer(source);
                                    this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source, viewer),
                                            entry.dimensionName(), list, waypoint, null));
```

- [ ] **Step 8: Delete the old builder and move the mods test to the new screen**

```bash
/usr/bin/git rm common/src/main/java/_959/server_waypoint/text/WaypointDetailsTextBuilder.java common/src/test/java/_959/server_waypoint/text/WaypointDetailsTextBuilderTest.java
```

In `mods/src/test/java/_959/server_waypoint/common/network/ModMessageSenderTest.java`, replace:

```java
import _959.server_waypoint.text.WaypointDetailsTextBuilder;
```

with:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.DetailsScreen;
```

replace:

```java
import java.util.List;
```

with:

```java
import java.util.List;
import java.util.Map;
```

and replace:

```java
        var details = WaypointDetailsTextBuilder.waypointDetails(
                "minecraft:overworld",
                list,
                waypoint,
                true,
                true,
                true,
                true
        );
```

with:

```java
        Viewer viewer = new Viewer(Viewer.everything(), true, false, "minecraft:overworld", new WaypointPos(0, 64, 0), 0F);
        var details = DetailsScreen.waypoint(
                DimensionStyle.local(viewer, Map.of("minecraft:overworld", "minecraft:overworld")),
                "minecraft:overworld",
                list,
                waypoint,
                null
        );
```

- [ ] **Step 9: Add the command tests**

In `CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void detailsAndEditsShowTheDetailsPanelWithTheResultOnTop() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();
        String edit = "wp edit waypoint minecraft:overworld Farms \"Iron Farm\" ";

        assertEquals("Overworld › Farms › [IF] Iron Farm",
                lines(this.harness.run(player, "wp details waypoint minecraft:overworld Farms \"Iron Farm\"")).get(0));
        List<String> edited = lines(this.harness.run(player, edit + "set yaw 90"));
        assertEquals("✔ Updated the facing", edited.get(0));
        assertEquals("[✎] Yaw: 90° (west)", edited.get(8));
        assertEquals("✔ Updated the visibility", lines(this.harness.run(player, edit + "set visibility local")).get(0));
        assertEquals("✔ Updated the position", lines(this.harness.run(player, edit + "set position ~ ~ ~")).get(0));
        assertEquals("Overworld › Farms  1 waypoint",
                lines(this.harness.run(player, "wp details list minecraft:overworld Farms")).get(0));
        this.harness.run(player, "wp edit list minecraft:overworld Farms set display-name \"Farm Row\"");
        assertEquals("✔ Cleared the display name",
                lines(this.harness.run(player, "wp edit list minecraft:overworld Farms clear display-name")).get(0));
    }

    @Test
    void theConsoleReadsDetailsWithoutButtons() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));

        List<String> details = lines(this.harness.run(CommandHarness.console(),
                "wp details waypoint minecraft:overworld Farms \"Iron Farm\""));

        assertEquals("Overworld (minecraft:overworld) › Farms › [IF] Iron Farm", details.get(0));
        assertEquals("Position: 300, 80, 150", details.get(5));
        assertEquals(11, details.size());
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

- [ ] **Step 10: Run the common tests and compile the platforms**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:compileTestJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text common/src/test/java/_959/server_waypoint/text common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java common/src/main/java/_959/server_waypoint/command common/src/test/java/_959/server_waypoint/command mods/src/test/java/_959/server_waypoint/common/network/ModMessageSenderTest.java common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Rebuild waypoint and list details with edit buttons and result lines"
```

---

### Task 13: Results, broadcasts, errors and the sharing prompt

Spec 13 and 2.3: results are one green `✔` line with the actions that follow from them, broadcasts tell the other players who changed what, and errors are one red `✘` line with a recovery link where one helps. Builders colour errors, so `ModMessageSender.sendError` stops adding red (Paper stopped in Task 6) and Paper and the mods look the same. Each broadcast line is built for its reader, so a waypoint's initials only teleport for players who may teleport; the reader's position isn't known to the command layer, so broadcast tooltips name the waypoint's dimension instead of a distance. Plain-text viewers read `Restore with /wp restore <token>` (spec 15). Navigation, upload and download messages follow in Tasks 14 and 15.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/Results.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/Broadcasts.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/SharingPrompt.java`
- Rewrite: `common/src/main/java/_959/server_waypoint/text/feedback/Errors.java`
- Modify: `common/src/main/java/_959/server_waypoint/text/feedback/WaypointRefs.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/network/PlatformMessageSender.java`, `ChatMessageHandler.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/ResultsTest.java`; `CommandFeedbackTest.java`
- Modify (tests): `common/src/test/java/_959/server_waypoint/core/network/C2SPacketHandlerTest.java`, `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `WaypointRefs`, `ListActions` (Task 9), `StringCommandBuilder` (`detailsWaypointCmd`, `detailsListCmd`, `navigateCmd`, `removeCmd`, `restoreCmd`, `addCmd`, `addListCmd`).
- Produces:
  - `WaypointRefs.plain(Viewer, SimpleWaypoint)`: `[AB] Name` without clicks, for removed and shared waypoints.
  - `Results`: `added`, `restored`, `teleported` `(DimensionStyle, String dimension, WaypointList, SimpleWaypoint)`; `removed(..., SimpleWaypoint, String token)`; `createdList`, `removedList` `(DimensionStyle, String dimension, WaypointList)`; `reloaded(List<String> languages)`; `keyGenerated(String publicKey)`.
  - `Broadcasts`: `added`, `updated`, `removed`, `restored` `(DimensionStyle, Component actor, String dimension, WaypointList, SimpleWaypoint)`; `createdList`, `updatedList`, `removedList` `(DimensionStyle, Component actor, String dimension, WaypointList)`.
  - `Errors`: `playerOnly()`, `noDimension`, `noList` (kept), `noLists(DimensionStyle, String dimension)`, `noWaypoint(DimensionStyle, String dimension, String list, String waypoint)`, `listExists(DimensionStyle, String dimension, WaypointList)`, `waypointExists(DimensionStyle, String dimension, WaypointList, SimpleWaypoint)`, `listNotEmpty(DimensionStyle, String dimension, WaypointList)`, `edit(DimensionStyle, EditResultStatus, String dimension, String list, @Nullable String waypoint)`, `of(String key, ComponentLike... arguments)`.
  - `SharingPrompt.found(DimensionStyle, String dimension, SimpleWaypoint, List<WaypointList>)`, `SharingPrompt.unknownDimension(DimensionStyle, String dimension, SimpleWaypoint)`.
  - `CoreWaypointCommand`: `broadcast(S, ChunkedMessage, Function<DimensionStyle, Component>)`, `recipientViewer(P)`, `dimensions(S)`.
  - `PlatformMessageSender` loses `broadcastWaypointModification` and `getModificationMessage`.
- Keys: `wp.result.*`, `wp.action.{details,open,undo,undo.added,undo.removed_list,restore,restore.tooltip}`, `wp.broadcast.*`, `wp.error.*` (below), `wp.sharing.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.result.added": "Added {0} to {1}",
  "wp.result.created_list": "Created the list {0} in {1}",
  "wp.result.removed": "Removed {0} from {1}",
  "wp.result.removed.plain": "Removed {0} from {1}. Restore with {2}",
  "wp.result.removed_list": "Removed the list {0} from {1}",
  "wp.result.restored": "Restored {0} to {1}",
  "wp.result.teleported": "Teleported to {0}",
  "wp.result.reloaded": "Reloaded the configuration and language files",
  "wp.result.languages": "Languages: {0}",
  "wp.result.key_generated": "Generated the cross-server key. Public key: {0}",
  "wp.action.details": "Details",
  "wp.action.open": "Open",
  "wp.action.undo": "Undo",
  "wp.action.undo.added": "Remove {0} again",
  "wp.action.undo.removed_list": "Create {0} again",
  "wp.action.restore": "Restore",
  "wp.action.restore.tooltip": "Put {0} back in {1}",
  "wp.broadcast.added": "{0} added {1} to {2}",
  "wp.broadcast.updated": "{0} updated {1}",
  "wp.broadcast.removed": "{0} removed {1} from {2}",
  "wp.broadcast.restored": "{0} restored {1} to {2}",
  "wp.broadcast.created_list": "{0} created the list {1} in {2}",
  "wp.broadcast.updated_list": "{0} updated the list {1} in {2}",
  "wp.broadcast.removed_list": "{0} removed the list {1} from {2}",
  "wp.error.no_lists": "{0} has no lists yet.",
  "wp.error.no_waypoint": "No waypoint called {0} in {1}.",
  "wp.error.list_exists": "{1} already has a list called {0}.",
  "wp.error.waypoint_exists": "{1} already has a waypoint called {0}.",
  "wp.error.list_not_empty": "Only empty lists can be removed.",
  "wp.error.position": "That position isn''t valid.",
  "wp.error.color": "{0} isn''t a colour name or a hex code such as 39C5BB.",
  "wp.error.formatted_text": "That isn''t valid formatted text.",
  "wp.error.keywords.too_many": "A waypoint can have at most {0} keywords.",
  "wp.error.keywords.duplicate": "A waypoint can''t have the same keyword twice.",
  "wp.error.too_long": "{0} is too long; the limit is {1} characters.",
  "wp.error.icon": "{0} isn''t a known icon.",
  "wp.error.encoding": "That change is too large to send to players.",
  "wp.error.save": "Couldn''t save {0}.",
  "wp.error.key": "Couldn''t generate the cross-server key: {0}",
  "wp.error.permission": "You don''t have permission to do that.",
  "wp.error.reload_details": "Reload details",
  "wp.error.edit.stale_revision": "{0} changed while you were editing.",
  "wp.error.edit.identifier_collision": "That identifier is already in use.",
  "wp.error.edit.invalid_display_text": "That display name isn''t valid formatted text.",
  "wp.error.edit.invalid_value": "That value isn''t valid.",
  "wp.error.edit.identical": "Nothing changed.",
  "wp.error.edit.malformed_request": "That edit request was malformed.",
  "wp.error.edit.upload_busy": "Editing is paused while an upload runs. Try again soon.",
  "wp.error.restore.invalid": "That restore link has expired.",
  "wp.error.restore.list_missing": "Its list no longer exists. The restore link keeps working.",
  "wp.error.restore.collision": "Its name is taken again. The restore link keeps working.",
  "wp.error.sharing.dimension": "This server has no dimension {0}.",
  "wp.sharing.found": "Shared waypoint {0} in {1}",
  "wp.sharing.save_to": "Save it to",
  "wp.sharing.save_to.tooltip": "Save it to {0}"
```

`zh_cn.json`:

```json
  "wp.result.added": "已将{0}添加到{1}",
  "wp.result.created_list": "已在{1}中创建列表{0}",
  "wp.result.removed": "已从{1}中删除{0}",
  "wp.result.removed.plain": "已从{1}中删除{0}。恢复命令：{2}",
  "wp.result.removed_list": "已从{1}中删除列表{0}",
  "wp.result.restored": "已将{0}恢复到{1}",
  "wp.result.teleported": "已传送到{0}",
  "wp.result.reloaded": "已重新加载配置和语言文件",
  "wp.result.languages": "语言：{0}",
  "wp.result.key_generated": "已生成跨服密钥。公钥：{0}",
  "wp.action.details": "详情",
  "wp.action.open": "打开",
  "wp.action.undo": "撤销",
  "wp.action.undo.added": "再次删除{0}",
  "wp.action.undo.removed_list": "重新创建{0}",
  "wp.action.restore": "恢复",
  "wp.action.restore.tooltip": "将{0}放回{1}",
  "wp.broadcast.added": "{0} 将{1}添加到{2}",
  "wp.broadcast.updated": "{0} 更新了{1}",
  "wp.broadcast.removed": "{0} 从{2}中删除了{1}",
  "wp.broadcast.restored": "{0} 将{1}恢复到{2}",
  "wp.broadcast.created_list": "{0} 在{2}中创建了列表{1}",
  "wp.broadcast.updated_list": "{0} 更新了{2}中的列表{1}",
  "wp.broadcast.removed_list": "{0} 从{2}中删除了列表{1}",
  "wp.error.no_lists": "{0}还没有列表。",
  "wp.error.no_waypoint": "{1}中没有名为 {0} 的路径点。",
  "wp.error.list_exists": "{1}中已有名为 {0} 的列表。",
  "wp.error.waypoint_exists": "{1}中已有名为 {0} 的路径点。",
  "wp.error.list_not_empty": "只能删除空列表。",
  "wp.error.position": "这个位置无效。",
  "wp.error.color": "{0} 不是颜色名称，也不是十六进制颜色（如 39C5BB）。",
  "wp.error.formatted_text": "这不是有效的格式化文本。",
  "wp.error.keywords.too_many": "一个路径点最多只能有 {0} 个关键词。",
  "wp.error.keywords.duplicate": "一个路径点不能有重复的关键词。",
  "wp.error.too_long": "{0} 太长了，上限是 {1} 个字符。",
  "wp.error.icon": "{0} 不是已知的图标。",
  "wp.error.encoding": "这项更改太大，无法发送给玩家。",
  "wp.error.save": "无法保存 {0}。",
  "wp.error.key": "无法生成跨服密钥：{0}",
  "wp.error.permission": "你没有权限这样做。",
  "wp.error.reload_details": "重新加载详情",
  "wp.error.edit.stale_revision": "你编辑时{0}已被修改。",
  "wp.error.edit.identifier_collision": "这个标识符已被使用。",
  "wp.error.edit.invalid_display_text": "这个显示名称不是有效的格式化文本。",
  "wp.error.edit.invalid_value": "这个值无效。",
  "wp.error.edit.identical": "没有任何改动。",
  "wp.error.edit.malformed_request": "这个编辑请求格式错误。",
  "wp.error.edit.upload_busy": "上传进行中，编辑已暂停。请稍后再试。",
  "wp.error.restore.invalid": "这个恢复链接已过期。",
  "wp.error.restore.list_missing": "它所在的列表已不存在。恢复链接仍然有效。",
  "wp.error.restore.collision": "它的名称又被占用了。恢复链接仍然有效。",
  "wp.error.sharing.dimension": "本服务器没有维度 {0}。",
  "wp.sharing.found": "分享的路径点{0}，位于{1}",
  "wp.sharing.save_to": "保存到",
  "wp.sharing.save_to.tooltip": "保存到{0}"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.result.added": "已將{0}新增到{1}",
  "wp.result.created_list": "已在{1}中建立列表{0}",
  "wp.result.removed": "已從{1}中刪除{0}",
  "wp.result.removed.plain": "已從{1}中刪除{0}。還原指令：{2}",
  "wp.result.removed_list": "已從{1}中刪除列表{0}",
  "wp.result.restored": "已將{0}還原到{1}",
  "wp.result.teleported": "已傳送到{0}",
  "wp.result.reloaded": "已重新載入設定和語言檔案",
  "wp.result.languages": "語言：{0}",
  "wp.result.key_generated": "已產生跨伺服器金鑰。公鑰：{0}",
  "wp.action.details": "詳情",
  "wp.action.open": "開啟",
  "wp.action.undo": "復原",
  "wp.action.undo.added": "再次刪除{0}",
  "wp.action.undo.removed_list": "重新建立{0}",
  "wp.action.restore": "還原",
  "wp.action.restore.tooltip": "將{0}放回{1}",
  "wp.broadcast.added": "{0} 將{1}新增到{2}",
  "wp.broadcast.updated": "{0} 更新了{1}",
  "wp.broadcast.removed": "{0} 從{2}中刪除了{1}",
  "wp.broadcast.restored": "{0} 將{1}還原到{2}",
  "wp.broadcast.created_list": "{0} 在{2}中建立了列表{1}",
  "wp.broadcast.updated_list": "{0} 更新了{2}中的列表{1}",
  "wp.broadcast.removed_list": "{0} 從{2}中刪除了列表{1}",
  "wp.error.no_lists": "{0}還沒有列表。",
  "wp.error.no_waypoint": "{1}中沒有名為 {0} 的路徑點。",
  "wp.error.list_exists": "{1}中已有名為 {0} 的列表。",
  "wp.error.waypoint_exists": "{1}中已有名為 {0} 的路徑點。",
  "wp.error.list_not_empty": "只能刪除空列表。",
  "wp.error.position": "這個位置無效。",
  "wp.error.color": "{0} 不是顏色名稱，也不是十六進位色碼（如 39C5BB）。",
  "wp.error.formatted_text": "這不是有效的格式化文字。",
  "wp.error.keywords.too_many": "一個路徑點最多只能有 {0} 個關鍵字。",
  "wp.error.keywords.duplicate": "一個路徑點不能有重複的關鍵字。",
  "wp.error.too_long": "{0} 太長了，上限是 {1} 個字元。",
  "wp.error.icon": "{0} 不是已知的圖示。",
  "wp.error.encoding": "這項變更太大，無法傳送給玩家。",
  "wp.error.save": "無法儲存 {0}。",
  "wp.error.key": "無法產生跨伺服器金鑰：{0}",
  "wp.error.permission": "你沒有權限這樣做。",
  "wp.error.reload_details": "重新載入詳情",
  "wp.error.edit.stale_revision": "你編輯時{0}已被修改。",
  "wp.error.edit.identifier_collision": "這個識別碼已被使用。",
  "wp.error.edit.invalid_display_text": "這個顯示名稱不是有效的格式化文字。",
  "wp.error.edit.invalid_value": "這個值無效。",
  "wp.error.edit.identical": "沒有任何變更。",
  "wp.error.edit.malformed_request": "這個編輯請求格式錯誤。",
  "wp.error.edit.upload_busy": "上傳進行中，編輯已暫停。請稍後再試。",
  "wp.error.restore.invalid": "這個還原連結已過期。",
  "wp.error.restore.list_missing": "它所在的列表已不存在。還原連結仍然有效。",
  "wp.error.restore.collision": "它的名稱又被佔用了。還原連結仍然有效。",
  "wp.error.sharing.dimension": "本伺服器沒有維度 {0}。",
  "wp.sharing.found": "分享的路徑點{0}，位於{1}",
  "wp.sharing.save_to": "儲存到",
  "wp.sharing.save_to.tooltip": "儲存到{0}"
```

`es_es.json`:

```json
  "wp.result.added": "Añadido {0} a {1}",
  "wp.result.created_list": "Creada la lista {0} en {1}",
  "wp.result.removed": "Quitado {0} de {1}",
  "wp.result.removed.plain": "Quitado {0} de {1}. Recupéralo con {2}",
  "wp.result.removed_list": "Quitada la lista {0} de {1}",
  "wp.result.restored": "Recuperado {0} en {1}",
  "wp.result.teleported": "Teletransportado a {0}",
  "wp.result.reloaded": "Configuración y archivos de idioma recargados",
  "wp.result.languages": "Idiomas: {0}",
  "wp.result.key_generated": "Clave entre servidores generada. Clave pública: {0}",
  "wp.action.details": "Detalles",
  "wp.action.open": "Abrir",
  "wp.action.undo": "Deshacer",
  "wp.action.undo.added": "Quitar {0} de nuevo",
  "wp.action.undo.removed_list": "Crear {0} de nuevo",
  "wp.action.restore": "Recuperar",
  "wp.action.restore.tooltip": "Devolver {0} a {1}",
  "wp.broadcast.added": "{0} añadió {1} a {2}",
  "wp.broadcast.updated": "{0} actualizó {1}",
  "wp.broadcast.removed": "{0} quitó {1} de {2}",
  "wp.broadcast.restored": "{0} recuperó {1} en {2}",
  "wp.broadcast.created_list": "{0} creó la lista {1} en {2}",
  "wp.broadcast.updated_list": "{0} actualizó la lista {1} en {2}",
  "wp.broadcast.removed_list": "{0} quitó la lista {1} de {2}",
  "wp.error.no_lists": "{0} aún no tiene listas.",
  "wp.error.no_waypoint": "No hay ningún punto llamado {0} en {1}.",
  "wp.error.list_exists": "{1} ya tiene una lista llamada {0}.",
  "wp.error.waypoint_exists": "{1} ya tiene un punto llamado {0}.",
  "wp.error.list_not_empty": "Solo se pueden quitar listas vacías.",
  "wp.error.position": "Esa posición no es válida.",
  "wp.error.color": "{0} no es un nombre de color ni un código hexadecimal como 39C5BB.",
  "wp.error.formatted_text": "Eso no es texto con formato válido.",
  "wp.error.keywords.too_many": "Un punto puede tener como máximo {0} palabras clave.",
  "wp.error.keywords.duplicate": "Un punto no puede tener dos veces la misma palabra clave.",
  "wp.error.too_long": "{0} es demasiado largo; el límite es {1} caracteres.",
  "wp.error.icon": "{0} no es un icono conocido.",
  "wp.error.encoding": "Ese cambio es demasiado grande para enviarlo a los jugadores.",
  "wp.error.save": "No se pudo guardar {0}.",
  "wp.error.key": "No se pudo generar la clave entre servidores: {0}",
  "wp.error.permission": "No tienes permiso para hacer eso.",
  "wp.error.reload_details": "Recargar detalles",
  "wp.error.edit.stale_revision": "{0} cambió mientras lo editabas.",
  "wp.error.edit.identifier_collision": "Ese identificador ya está en uso.",
  "wp.error.edit.invalid_display_text": "Ese nombre visible no es texto con formato válido.",
  "wp.error.edit.invalid_value": "Ese valor no es válido.",
  "wp.error.edit.identical": "No ha cambiado nada.",
  "wp.error.edit.malformed_request": "Esa solicitud de edición está mal formada.",
  "wp.error.edit.upload_busy": "La edición está en pausa mientras se sube algo. Inténtalo pronto.",
  "wp.error.restore.invalid": "Ese enlace de recuperación ha caducado.",
  "wp.error.restore.list_missing": "Su lista ya no existe. El enlace de recuperación sigue funcionando.",
  "wp.error.restore.collision": "Su nombre vuelve a estar ocupado. El enlace de recuperación sigue funcionando.",
  "wp.error.sharing.dimension": "Este servidor no tiene la dimensión {0}.",
  "wp.sharing.found": "Punto compartido {0} en {1}",
  "wp.sharing.save_to": "Guardarlo en",
  "wp.sharing.save_to.tooltip": "Guardarlo en {0}"
```

`he_il.json`:

```json
  "wp.result.added": "{0} נוספה אל {1}",
  "wp.result.created_list": "הרשימה {0} נוצרה ב-{1}",
  "wp.result.removed": "{0} הוסרה מ-{1}",
  "wp.result.removed.plain": "{0} הוסרה מ-{1}. לשחזור: {2}",
  "wp.result.removed_list": "הרשימה {0} הוסרה מ-{1}",
  "wp.result.restored": "{0} שוחזרה אל {1}",
  "wp.result.teleported": "שוגרתם אל {0}",
  "wp.result.reloaded": "ההגדרות וקובצי השפה נטענו מחדש",
  "wp.result.languages": "שפות: {0}",
  "wp.result.key_generated": "מפתח בין-שרתי נוצר. מפתח ציבורי: {0}",
  "wp.action.details": "פרטים",
  "wp.action.open": "פתיחה",
  "wp.action.undo": "ביטול",
  "wp.action.undo.added": "הסרת {0} שוב",
  "wp.action.undo.removed_list": "יצירת {0} מחדש",
  "wp.action.restore": "שחזור",
  "wp.action.restore.tooltip": "החזרת {0} אל {1}",
  "wp.broadcast.added": "{0} הוסיף/ה את {1} אל {2}",
  "wp.broadcast.updated": "{0} עדכן/ה את {1}",
  "wp.broadcast.removed": "{0} הסיר/ה את {1} מ-{2}",
  "wp.broadcast.restored": "{0} שחזר/ה את {1} אל {2}",
  "wp.broadcast.created_list": "{0} יצר/ה את הרשימה {1} ב-{2}",
  "wp.broadcast.updated_list": "{0} עדכן/ה את הרשימה {1} ב-{2}",
  "wp.broadcast.removed_list": "{0} הסיר/ה את הרשימה {1} מ-{2}",
  "wp.error.no_lists": "ב-{0} אין עדיין רשימות.",
  "wp.error.no_waypoint": "אין נקודה בשם {0} ב-{1}.",
  "wp.error.list_exists": "ב-{1} כבר יש רשימה בשם {0}.",
  "wp.error.waypoint_exists": "ב-{1} כבר יש נקודה בשם {0}.",
  "wp.error.list_not_empty": "אפשר להסיר רק רשימות ריקות.",
  "wp.error.position": "המיקום הזה אינו תקין.",
  "wp.error.color": "{0} אינו שם צבע או קוד הקסדצימלי כמו 39C5BB.",
  "wp.error.formatted_text": "זה אינו טקסט מעוצב תקין.",
  "wp.error.keywords.too_many": "לנקודה יכולות להיות לכל היותר {0} מילות מפתח.",
  "wp.error.keywords.duplicate": "לנקודה לא יכולה להיות אותה מילת מפתח פעמיים.",
  "wp.error.too_long": "{0} ארוך מדי; המגבלה היא {1} תווים.",
  "wp.error.icon": "{0} אינו סמל מוכר.",
  "wp.error.encoding": "השינוי גדול מכדי לשלוח אותו לשחקנים.",
  "wp.error.save": "לא ניתן לשמור את {0}.",
  "wp.error.key": "לא ניתן ליצור מפתח בין-שרתי: {0}",
  "wp.error.permission": "אין לכם הרשאה לעשות זאת.",
  "wp.error.reload_details": "טעינת הפרטים מחדש",
  "wp.error.edit.stale_revision": "{0} השתנתה בזמן שערכתם.",
  "wp.error.edit.identifier_collision": "המזהה הזה כבר בשימוש.",
  "wp.error.edit.invalid_display_text": "שם התצוגה הזה אינו טקסט מעוצב תקין.",
  "wp.error.edit.invalid_value": "הערך הזה אינו תקין.",
  "wp.error.edit.identical": "שום דבר לא השתנה.",
  "wp.error.edit.malformed_request": "בקשת העריכה פגומה.",
  "wp.error.edit.upload_busy": "העריכה מושהית בזמן העלאה. נסו שוב בקרוב.",
  "wp.error.restore.invalid": "קישור השחזור פג תוקף.",
  "wp.error.restore.list_missing": "הרשימה שלה כבר לא קיימת. קישור השחזור ממשיך לעבוד.",
  "wp.error.restore.collision": "השם שלה תפוס שוב. קישור השחזור ממשיך לעבוד.",
  "wp.error.sharing.dimension": "בשרת הזה אין ממד {0}.",
  "wp.sharing.found": "נקודה משותפת {0} ב-{1}",
  "wp.sharing.save_to": "שמירה אל",
  "wp.sharing.save_to.tooltip": "שמירה אל {0}"
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/ResultsTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.util.StringCommandBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsTest {
    private static final WaypointList FARMS = Fixtures.farms();
    private static final SimpleWaypoint IRON = FARMS.getWaypointByName("Iron Farm");
    private static final WaypointList STORAGE = new WaypointList("Storage", 1, List.of());

    private static DimensionStyle dims() {
        return Fixtures.dims(Fixtures.player());
    }

    @Test
    void addingAnswersWithDetailsNavigateAndUndo() {
        Component result = Results.added(dims(), OVERWORLD, FARMS, IRON);

        assertEquals("✔ Added [IF] Iron Farm to Farms   Details · Navigate · Undo", render(result));
        assertEquals(NamedTextColor.GREEN, colorOf(result, "✔ Added "));
        assertEquals(NamedTextColor.WHITE, colorOf(result, "Iron Farm"));
        assertEquals("/wp details waypoint minecraft:overworld Farms \"Iron Farm\"", clickOf(result, "Details"));
        assertEquals("/wp navigate minecraft:overworld Farms \"Iron Farm\"", clickOf(result, "Navigate"));
        assertEquals("/wp remove minecraft:overworld Farms \"Iron Farm\"", clickOf(result, "Undo"));
        assertEquals(NamedTextColor.RED, colorOf(result, "Undo"));
        assertEquals("Remove Iron Farm again\nPress Enter to confirm", tooltipOf(result, "Undo"));
        assertEquals("✔ Added [IF] Iron Farm to Farms", render(Results.added(Fixtures.dims(Fixtures.console()), OVERWORLD, FARMS, IRON)));
    }

    @Test
    void creatingAListOffersToAddToItAndToOpenIt() {
        Component here = Results.createdList(dims(), OVERWORLD, STORAGE);
        Component nether = Results.createdList(dims(), NETHER, STORAGE);

        assertEquals("✔ Created the list Storage in Overworld   Add here · Open", render(here));
        assertEquals("/wp add ~ ~ ~ Storage ", clickOf(here, "Add here"));
        assertEquals("/wp list minecraft:overworld Storage", clickOf(here, "Open"));
        assertEquals("✔ Created the list Storage in Nether   Add waypoint · Open", render(nether));
        assertEquals(NamedTextColor.RED, colorOf(nether, "Nether"));
    }

    @Test
    void removingOffersRestoreAndPlainTextPrintsTheCommand() {
        Component result = Results.removed(dims(), OVERWORLD, FARMS, IRON, "r12");

        assertEquals("✔ Removed [IF] Iron Farm from Farms   Restore", render(result));
        assertEquals("/wp restore r12", clickOf(result, "Restore"));
        assertNull(clickOf(result, "[IF]"));
        assertEquals("✔ Removed [IF] Iron Farm from Farms. Restore with /wp restore r12",
                render(Results.removed(Fixtures.dims(Fixtures.console()), OVERWORLD, FARMS, IRON, "r12")));
    }

    @Test
    void removedListsCanBeCreatedAgainAndRestoresAndTeleportsAreOneLine() {
        Component removed = Results.removedList(dims(), NETHER, STORAGE);

        assertEquals("✔ Removed the list Storage from Nether   Undo", render(removed));
        assertEquals("/wp add minecraft:the_nether Storage", clickOf(removed, "Undo"));
        assertEquals(NamedTextColor.GREEN, colorOf(removed, "Undo"));
        assertEquals("✔ Restored [IF] Iron Farm to Farms", render(Results.restored(dims(), OVERWORLD, FARMS, IRON)));
        assertEquals("✔ Teleported to [IF] Iron Farm", render(Results.teleported(dims(), OVERWORLD, FARMS, IRON)));
        assertEquals(List.of("✔ Reloaded the configuration and language files", "Languages: en_us, zh_cn"),
                lines(Results.reloaded(List.of("en_us", "zh_cn"))));
        assertEquals(List.of("✔ Reloaded the configuration and language files"), lines(Results.reloaded(List.of())));
    }

    @Test
    void broadcastsNameThePlayerInWhiteWithGrayVerbsAndClickableReferences() {
        Component added = Broadcasts.added(dims(), text("Steve"), OVERWORLD, FARMS, IRON);

        assertEquals("Steve added [IF] Iron Farm to Farms", render(added));
        assertEquals(NamedTextColor.WHITE, colorOf(added, "Steve"));
        assertEquals(NamedTextColor.GRAY, colorOf(added, " added "));
        assertEquals("/wp tp minecraft:overworld Farms \"Iron Farm\"", clickOf(added, "[IF]"));
        assertEquals("Steve updated [IF] Iron Farm", render(Broadcasts.updated(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals("Steve removed [IF] Iron Farm from Farms",
                render(Broadcasts.removed(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals("Steve restored [IF] Iron Farm to Farms",
                render(Broadcasts.restored(dims(), text("Steve"), OVERWORLD, FARMS, IRON)));
        assertEquals("Steve created the list Farms in Overworld",
                render(Broadcasts.createdList(dims(), text("Steve"), OVERWORLD, FARMS)));
        assertEquals("Steve updated the list Farms in Overworld",
                render(Broadcasts.updatedList(dims(), text("Steve"), OVERWORLD, FARMS)));
        assertEquals("Steve removed the list Storage from Nether",
                render(Broadcasts.removedList(dims(), text("Steve"), NETHER, STORAGE)));
    }

    @Test
    void errorsAreOneRedLineWithARecoveryLink() {
        Component noWaypoint = Errors.noWaypoint(dims(), OVERWORLD, "Farms", "Iron Farn");

        assertEquals("✘ No waypoint called Iron Farn in Farms. Open Farms", render(noWaypoint));
        assertEquals(NamedTextColor.RED, colorOf(noWaypoint, "✘ "));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(noWaypoint, "Open Farms"));
        assertEquals("✘ No waypoint called Iron Farn in Farms.",
                render(Errors.noWaypoint(Fixtures.dims(Fixtures.console()), OVERWORLD, "Farms", "Iron Farn")));
        assertEquals("✘ Overworld has no lists yet. New list", render(Errors.noLists(dims(), OVERWORLD)));
        assertEquals("✘ Overworld already has a list called Farms. Open", render(Errors.listExists(dims(), OVERWORLD, FARMS)));
        assertEquals("✘ Farms already has a waypoint called Iron Farm. Details",
                render(Errors.waypointExists(dims(), OVERWORLD, FARMS, IRON)));
        assertEquals("✘ Only empty lists can be removed. Open Farms", render(Errors.listNotEmpty(dims(), OVERWORLD, FARMS)));
        assertEquals("Farms · 7 waypoints\nClick to open", tooltipOf(Errors.listNotEmpty(dims(), OVERWORLD, FARMS), "Open Farms"));
    }

    @Test
    void editErrorsExplainTheStatus() {
        Component stale = Errors.edit(dims(), EditResultStatus.STALE_REVISION, OVERWORLD, "Farms", "Iron Farm");

        assertEquals("✘ Iron Farm changed while you were editing. Reload details", render(stale));
        assertEquals("/wp details waypoint minecraft:overworld Farms \"Iron Farm\"", clickOf(stale, "Reload details"));
        assertEquals("/wp details list minecraft:overworld Farms",
                clickOf(Errors.edit(dims(), EditResultStatus.STALE_REVISION, OVERWORLD, "Farms", null), "Reload details"));
        assertEquals("✘ Nothing changed.", render(Errors.edit(dims(), EditResultStatus.IDENTICAL, OVERWORLD, "Farms", "Iron Farm")));
        assertEquals("✘ That identifier is already in use.",
                render(Errors.edit(dims(), EditResultStatus.IDENTIFIER_COLLISION, OVERWORLD, "Farms", "Iron Farm")));
        assertEquals("✘ No list called Farms in Overworld. Browse lists",
                render(Errors.edit(dims(), EditResultStatus.LIST_NOT_FOUND, OVERWORLD, "Farms", "Iron Farm")));
    }

    @Test
    void theSharingPromptOffersEveryListOfTheDimension() {
        Component prompt = SharingPrompt.found(dims(), OVERWORLD, IRON, Fixtures.overworldLists());

        assertEquals(List.of("Shared waypoint [IF] Iron Farm in Overworld", "Save it to  Home Bases · Farms · Exploration"),
                lines(prompt));
        assertEquals(StringCommandBuilder.addCmd(OVERWORLD, "Farms", IRON), clickOf(prompt, "Farms"));
        assertEquals("Save it to Farms\nPress Enter to confirm", tooltipOf(prompt, "Farms"));
        assertEquals(List.of("Shared waypoint [IF] Iron Farm in Overworld", "No lists yet. New list"),
                lines(SharingPrompt.found(dims(), OVERWORLD, IRON, List.of())));
        assertEquals("✘ This server has no dimension mars:mars.",
                render(SharingPrompt.unknownDimension(dims(), "mars:mars", IRON)));
        assertTrue(runCommands(prompt).isEmpty());
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.ResultsTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable Results`.

- [ ] **Step 4: Add plain references**

In `common/src/main/java/_959/server_waypoint/text/feedback/WaypointRefs.java`, replace:

```java
    /** [AB] in the waypoint colour: a teleport link with permission, otherwise coloured text with the waypoint tooltip. */
```

with:

```java
    /** [AB] Name without clicks, for a waypoint that is gone or not on this server yet. */
    public static Component plain(Viewer viewer, SimpleWaypoint waypoint) {
        return Chat.concat(Chat.colored(text("[" + waypoint.initials() + "]"), TextColor.color(waypoint.rgb())), text(" "),
                Chat.colored(label(viewer, waypoint.displayName(), waypoint.name()), NamedTextColor.WHITE));
    }

    /** [AB] in the waypoint colour: a teleport link with permission, otherwise coloured text with the waypoint tooltip. */
```

- [ ] **Step 5: Write `Results`, `Broadcasts`, `SharingPrompt` and the errors**

Create `common/src/main/java/_959/server_waypoint/text/feedback/Results.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.List;

import static _959.server_waypoint.util.StringCommandBuilder.addListCmd;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.navigateCmd;
import static _959.server_waypoint.util.StringCommandBuilder.removeCmd;
import static _959.server_waypoint.util.StringCommandBuilder.restoreCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Result lines (spec 13): a green ✔, what happened, then the actions that follow from it. */
public final class Results {
    private Results() {
    }

    /** ✔ Added [PP] Pumpkin Patch to Farms   Details · Navigate · Undo */
    public static Component added(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        Component name = WaypointRefs.label(waypoint.displayName(), waypoint.name());
        return Chat.ok(translatable("wp.result.added", WaypointRefs.reference(dims, dimension, list, waypoint),
                        listLink(dims, dimension, list)),
                Arrays.asList(
                        Chat.control(viewer, translatable("wp.action.details"), AQUA,
                                Click.run(detailsWaypointCmd(dimension, list.name(), waypoint.name())),
                                WaypointRefs.waypointTooltip(dims, dimension, waypoint, "wp.hint.details")),
                        viewer.can(Viewer.Permission.NAVIGATE)
                                ? Chat.control(viewer, translatable("wp.action.navigate"), LIGHT_PURPLE,
                                Click.run(navigateCmd(dimension, list.name(), waypoint.name())),
                                Tooltip.of("wp.action.navigate.tooltip", name))
                                : null,
                        viewer.can(Viewer.Permission.REMOVE)
                                ? Chat.control(viewer, translatable("wp.action.undo"), RED,
                                Click.suggest(removeCmd(dimension, list.name(), waypoint)),
                                Tooltip.of("wp.action.undo.added", name).hint("wp.hint.confirm"))
                                : null));
    }

    /** ✔ Created the list Farms in Overworld   Add here · Open */
    public static Component createdList(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        String open = ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT);
        return Chat.ok(translatable("wp.result.created_list", listLink(dims, dimension, list), dims.name(dimension)),
                Arrays.asList(
                        ListActions.add(dims, dimension, list),
                        Chat.control(viewer, translatable("wp.action.open"), AQUA, Click.run(open),
                                Tooltip.of("wp.open", WaypointRefs.label(list.displayName(), list.name())))));
    }

    /** ✔ Removed [MH] Main Home from Home Bases   Restore */
    public static Component removed(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                    String token) {
        Viewer viewer = dims.viewer();
        Component gone = WaypointRefs.plain(viewer, waypoint);
        String restore = restoreCmd(token);
        if (viewer.plainText()) {
            return Chat.ok(translatable("wp.result.removed.plain", gone, listLink(dims, dimension, list), text(restore)));
        }
        return Chat.ok(translatable("wp.result.removed", gone, listLink(dims, dimension, list)), List.of(
                Chat.link(viewer, translatable("wp.action.restore"), GREEN, Click.run(restore),
                        Tooltip.of("wp.action.restore.tooltip", WaypointRefs.label(waypoint.displayName(), waypoint.name()),
                                WaypointRefs.label(list.displayName(), list.name())))));
    }

    /** ✔ Removed the list Storage from Nether   Undo */
    public static Component removedList(DimensionStyle dims, String dimension, WaypointList list) {
        Viewer viewer = dims.viewer();
        Component name = WaypointRefs.label(viewer, list.displayName(), list.name());
        return Chat.ok(translatable("wp.result.removed_list", Chat.colored(name, WHITE), dims.name(dimension)),
                Arrays.asList(viewer.can(Viewer.Permission.ADD)
                        ? Chat.control(viewer, translatable("wp.action.undo"), GREEN,
                        Click.run(addListCmd(dimension, list.name())),
                        Tooltip.of("wp.action.undo.removed_list", WaypointRefs.label(list.displayName(), list.name())))
                        : null));
    }

    /** ✔ Restored [MH] Main Home to Home Bases */
    public static Component restored(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.ok(translatable("wp.result.restored", WaypointRefs.reference(dims, dimension, list, waypoint),
                listLink(dims, dimension, list)));
    }

    /** ✔ Teleported to [MH] Main Home */
    public static Component teleported(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.ok(translatable("wp.result.teleported", WaypointRefs.reference(dims, dimension, list, waypoint)));
    }

    /** ✔ Reloaded the configuration and language files, then the external languages that were loaded. */
    public static Component reloaded(List<String> languages) {
        return new ChatLines()
                .add(Chat.ok(translatable("wp.result.reloaded")))
                .add(languages.isEmpty() ? null
                        : translatable("wp.result.languages", GRAY, text(String.join(", ", languages))))
                .build();
    }

    public static Component keyGenerated(String publicKey) {
        return Chat.ok(translatable("wp.result.key_generated", text(publicKey)));
    }

    /** The list name in white, opening the list. */
    static Component listLink(DimensionStyle dims, String dimension, WaypointList list) {
        return WaypointRefs.listLink(dims, list, WHITE,
                Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open");
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/feedback/Broadcasts.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;

import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** What other players read when someone changes waypoints (spec 13): a white name, gray verbs, clickable references. */
public final class Broadcasts {
    private Broadcasts() {
    }

    /** Steve added [PP] Pumpkin Patch to Farms */
    public static Component added(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                  SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.added", GRAY, player(actor), WaypointRefs.reference(dims, dimension, list, waypoint),
                Results.listLink(dims, dimension, list));
    }

    /** Steve updated [MH] Main Home */
    public static Component updated(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                    SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.updated", GRAY, player(actor), WaypointRefs.reference(dims, dimension, list, waypoint));
    }

    /** Steve removed [MH] Main Home from Home Bases */
    public static Component removed(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                    SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.removed", GRAY, player(actor), WaypointRefs.plain(dims.viewer(), waypoint),
                Results.listLink(dims, dimension, list));
    }

    /** Steve restored [MH] Main Home to Home Bases */
    public static Component restored(DimensionStyle dims, Component actor, String dimension, WaypointList list,
                                     SimpleWaypoint waypoint) {
        return translatable("wp.broadcast.restored", GRAY, player(actor), WaypointRefs.reference(dims, dimension, list, waypoint),
                Results.listLink(dims, dimension, list));
    }

    /** Steve created the list Farms in Overworld */
    public static Component createdList(DimensionStyle dims, Component actor, String dimension, WaypointList list) {
        return translatable("wp.broadcast.created_list", GRAY, player(actor), Results.listLink(dims, dimension, list),
                dims.name(dimension));
    }

    /** Steve updated the list Farms in Overworld */
    public static Component updatedList(DimensionStyle dims, Component actor, String dimension, WaypointList list) {
        return translatable("wp.broadcast.updated_list", GRAY, player(actor), Results.listLink(dims, dimension, list),
                dims.name(dimension));
    }

    /** Steve removed the list Storage from Nether */
    public static Component removedList(DimensionStyle dims, Component actor, String dimension, WaypointList list) {
        return translatable("wp.broadcast.removed_list", GRAY, player(actor),
                Chat.colored(WaypointRefs.label(dims.viewer(), list.displayName(), list.name()), WHITE), dims.name(dimension));
    }

    private static Component player(Component actor) {
        return Chat.colored(actor, WHITE);
    }
}
```

Create `common/src/main/java/_959/server_waypoint/text/feedback/SharingPrompt.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;

import java.util.List;

import static _959.server_waypoint.util.StringCommandBuilder.addCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;

/** The prompt a player gets after sharing a Xaero's Minimap waypoint in chat (spec 13). */
public final class SharingPrompt {
    private SharingPrompt() {
    }

    /** Shared waypoint [AB] Name in Overworld, then the lists to save it to. */
    public static Component found(DimensionStyle dims, String dimension, SimpleWaypoint waypoint, List<WaypointList> lists) {
        Viewer viewer = dims.viewer();
        ChatLines lines = new ChatLines().add(translatable("wp.sharing.found", GRAY,
                WaypointRefs.plain(viewer, waypoint), dims.name(dimension)));
        if (lists.isEmpty()) {
            Component newList = ListActions.newList(dims, dimension);
            return lines.line(translatable("wp.dimension.no_lists.sentence", GRAY), newList == null ? null : text(" "),
                    newList).build();
        }
        List<Component> links = lists.stream().map(list -> Chat.link(viewer,
                WaypointRefs.label(viewer, list.displayName(), list.name()), GREEN,
                Click.suggest(addCmd(dimension, list.name(), waypoint)),
                Tooltip.of("wp.sharing.save_to.tooltip", WaypointRefs.label(list.displayName(), list.name()))
                        .hint("wp.hint.confirm"))).toList();
        return lines.line(translatable("wp.sharing.save_to", GRAY), text("  "), Chat.join(links)).build();
    }

    /** ✘ This server has no dimension x. The shared waypoint is in the line the player just sent. */
    public static Component unknownDimension(DimensionStyle dims, String dimension, SimpleWaypoint waypoint) {
        return Chat.error(translatable("wp.error.sharing.dimension", text(dimension)));
    }
}
```

Replace the whole of `common/src/main/java/_959/server_waypoint/text/feedback/Errors.java` with:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.util.StringCommandBuilder.detailsListCmd;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;

/** One red ✘ line, plus the link that helps recover where one helps (spec 13). */
public final class Errors {
    private Errors() {
    }

    /** ✘ and a translated sentence, without a recovery link. */
    public static Component of(String key, ComponentLike... arguments) {
        return Chat.error(translatable(key, arguments));
    }

    /** ✘ Only players can do that. */
    public static Component playerOnly() {
        return of("wp.error.player_only");
    }

    /** ✘ No dimension called x. Dimensions */
    public static Component noDimension(DimensionStyle dims, String dimension) {
        return Chat.error(translatable("wp.error.no_dimension", text(dimension)),
                Chat.control(dims.viewer(), translatable("wp.dimensions.title"), AQUA, Click.run("/wp list dimensions"),
                        Tooltip.of("wp.dimensions.choose")));
    }

    /** ✘ No list called Farm in Overworld. Browse lists */
    public static Component noList(DimensionStyle dims, String dimension, String list) {
        return Chat.error(translatable("wp.error.no_list", text(list), dims.name(dimension)),
                Chat.control(dims.viewer(), translatable("wp.error.browse_lists"), AQUA, Click.run("/wp list " + dimension),
                        Tooltip.of("wp.list.every_list_in", dims.name(dimension))));
    }

    /** ✘ Mars has no lists yet. New list */
    public static Component noLists(DimensionStyle dims, String dimension) {
        return Chat.error(translatable("wp.error.no_lists", dims.name(dimension)), ListActions.newList(dims, dimension));
    }

    /** ✘ No waypoint called x in Farms. Open Farms */
    public static Component noWaypoint(DimensionStyle dims, String dimension, String list, String waypoint) {
        return Chat.error(translatable("wp.error.no_waypoint", text(waypoint), text(list)),
                Chat.control(dims.viewer(), translatable("wp.open", text(list)), AQUA,
                        Click.run(ListTarget.list(dimension, list).command(ListQuery.DEFAULT)),
                        Tooltip.of("wp.list.every_list_in", dims.name(dimension))));
    }

    /** ✘ Overworld already has a list called Farms. Open */
    public static Component listExists(DimensionStyle dims, String dimension, WaypointList list) {
        return Chat.error(translatable("wp.error.list_exists", WaypointRefs.label(list.displayName(), list.name()),
                        dims.name(dimension)),
                Chat.control(dims.viewer(), translatable("wp.action.open"), AQUA,
                        Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)),
                        WaypointRefs.listTooltip(list, "wp.hint.open")));
    }

    /** ✘ Farms already has a waypoint called Iron Farm. Details */
    public static Component waypointExists(DimensionStyle dims, String dimension, WaypointList list,
                                           SimpleWaypoint existing) {
        return Chat.error(translatable("wp.error.waypoint_exists",
                        WaypointRefs.label(existing.displayName(), existing.name()),
                        WaypointRefs.label(list.displayName(), list.name())),
                Chat.control(dims.viewer(), translatable("wp.action.details"), AQUA,
                        Click.run(detailsWaypointCmd(dimension, list.name(), existing.name())),
                        WaypointRefs.waypointTooltip(dims, dimension, existing, "wp.hint.details")));
    }

    /** ✘ Only empty lists can be removed. Open Farms */
    public static Component listNotEmpty(DimensionStyle dims, String dimension, WaypointList list) {
        return Chat.error(translatable("wp.error.list_not_empty"),
                Chat.control(dims.viewer(), translatable("wp.open", WaypointRefs.label(list.displayName(), list.name())),
                        AQUA, Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)),
                        WaypointRefs.listTooltip(list, "wp.hint.open")));
    }

    /** Why an edit of a waypoint (or of a list, when waypoint is null) failed. */
    public static Component edit(DimensionStyle dims, EditResultStatus status, String dimension, String list,
                                 @Nullable String waypoint) {
        return switch (status) {
            case DIMENSION_NOT_FOUND -> noDimension(dims, dimension);
            case LIST_NOT_FOUND -> noList(dims, dimension, list);
            case WAYPOINT_NOT_FOUND -> noWaypoint(dims, dimension, list, waypoint == null ? "" : waypoint);
            case STALE_REVISION -> Chat.error(translatable("wp.error.edit.stale_revision", text(waypoint == null ? list : waypoint)),
                    Chat.control(dims.viewer(), translatable("wp.error.reload_details"), AQUA,
                            Click.run(waypoint == null ? detailsListCmd(dimension, list) : detailsWaypointCmd(dimension, list, waypoint)),
                            null));
            case PERMISSION_DENIED -> of("wp.error.permission");
            case DUPLICATE_KEYWORD -> of("wp.error.keywords.duplicate");
            case ENCODING_FAILED -> of("wp.error.encoding");
            default -> of("wp.error.edit." + status.name().toLowerCase(Locale.ROOT));
        };
    }
}
```

- [ ] **Step 6: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 7: Senders stop colouring errors, and broadcasts move to the command**

In `mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java`, replace:

```java
    @Override
    public void sendError(CommandSourceStack source, Component component) {
        source.sendSystemMessage(getTranslatedText(source, component.color(NamedTextColor.RED)));
    }
```

with:

```java
    @Override
    public void sendError(CommandSourceStack source, Component component) {
        this.sendMessage(source, component);
    }
```

and remove the `NamedTextColor` import if nothing else in the file uses it.

In `common/src/main/java/_959/server_waypoint/core/network/PlatformMessageSender.java`, delete the methods `broadcastWaypointModification(S, WaypointModificationMessage)` and `getModificationMessage(Component, WaypointModificationMessage)`, then remove the imports only they used (`WaypointModificationType`, the static imports of `waypointTextNoTp`, `waypointTextWithTp` and `parse`, and `WaypointModificationMessage` if nothing else uses it).

In `common/src/test/java/_959/server_waypoint/core/network/C2SPacketHandlerTest.java`, replace:

```java
        sender.broadcastWaypointModification("source", modification);
```

with:

```java
        sender.broadcastChunkedMessage(sender.getBroadcastPlayers("source"), modification);
```

- [ ] **Step 8: Send results, broadcasts and errors from the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.feedback.DetailsScreen;
```

with:

```java
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.feedback.Broadcasts;
import _959.server_waypoint.text.feedback.DetailsScreen;
import _959.server_waypoint.text.feedback.Results;
```

Replace the key generation feedback:

```java
                                        sender.sendMessage(source, text("Cross-server static key generated. Public key: " + publicFile));
                                        return Command.SINGLE_SUCCESS;
                                    } catch (IOException | IllegalArgumentException exception) {
                                        sender.sendError(source, text("Could not generate cross-server static key: " + exception.getMessage()));
```

with:

```java
                                        sender.sendMessage(source, Results.keyGenerated(publicFile.toString()));
                                        return Command.SINGLE_SUCCESS;
                                    } catch (IOException | IllegalArgumentException exception) {
                                        sender.sendError(source, Errors.of("wp.error.key",
                                                text(String.valueOf(exception.getMessage()))));
```

Replace the three `runWithSelectorTarget` methods and the three error helpers after them (from `private void runWithSelectorTarget(S source, D dimensionArgument, Consumer<@NotNull WaypointFileManager> foundAction) {` through the end of `sendHexColorCodeError`) with:

```java
    private void runWithSelectorTarget(S source, D dimensionArgument, Consumer<@NotNull WaypointFileManager> foundAction) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (isDimensionValid(source, dimensionArgument)) {
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionName);
            if (fileManager == null) {
                this.sender.sendError(source, Errors.noLists(dimensions(source), dimensionName));
            } else {
                foundAction.accept(fileManager);
            }
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void runWithSelectorTarget(S source, D dimensionArgument, String listName, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> foundAction, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> foundEmptyAction) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            WaypointList waypointList = fileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                this.sender.sendError(source, Errors.noList(dimensions(source), fileManager.getDimensionName(), listName));
            } else if (waypointList.isEmpty()) {
                foundEmptyAction.accept(fileManager, waypointList);
            } else {
                foundAction.accept(fileManager, waypointList);
            }
        });
    }

    private void runWithSelectorTarget(S source, D dimensionArgument, String listName, String name, TriConsumer<@NotNull WaypointFileManager, @NotNull WaypointList, @NotNull SimpleWaypoint> action) {
        BiConsumer<WaypointFileManager, WaypointList> missing = (fileManager, waypointList) -> this.sender.sendError(source,
                Errors.noWaypoint(dimensions(source), fileManager.getDimensionName(), listName, name));
        runWithSelectorTarget(source, dimensionArgument, listName, (fileManager, waypointList) -> {
            SimpleWaypoint waypoint = waypointList.getWaypointByName(name);
            if (waypoint == null) {
                missing.accept(fileManager, waypointList);
            } else {
                action.accept(fileManager, waypointList, waypoint);
            }
        }, missing);
    }

    private void sendDimensionError(S source, String dimensionName) {
        this.sender.sendError(source, Errors.noDimension(dimensions(source), dimensionName));
    }

    private void sendPosArgumentError(S source) {
        this.sender.sendError(source, Errors.of("wp.error.position"));
    }

    private void sendHexColorCodeError(S source, String hexColorCode) {
        this.sender.sendError(source, Errors.of("wp.error.color", text(hexColorCode)));
    }

    private DimensionStyle dimensions(S source) {
        return dimensions(source, viewer(source));
    }

    /** A player who reads a broadcast: whether they may teleport is known, where they stand isn't. */
    private Viewer recipientViewer(P player) {
        boolean teleport = this.permissionManager.checkPlayerPermission(player, this.permissionKeys.tp(),
                CONFIG.CommandPermission().tp());
        return new Viewer(teleport ? Set.of(Viewer.Permission.TP) : Set.of(), false, false, null, null, 0F);
    }

    /**
     * Sends a change to every client, and a chat line about it to every other player (spec 13). Each
     * line is built for its reader.
     */
    private void broadcast(S source, ChunkedMessage update, Function<DimensionStyle, Component> line) {
        Iterable<? extends P> recipients = this.sender.getBroadcastPlayers(source);
        P actor = getPlayer(source);
        Map<String, String> types = getDimensionTypes(source);
        for (P player : recipients) {
            if (!player.equals(actor)) {
                this.sender.sendPlayerMessage(player, line.apply(DimensionStyle.local(recipientViewer(player), types)));
            }
        }
        this.sender.broadcastChunkedMessage(recipients, update);
    }
```

In `validateTextInputs` and `validateLength`, replace:

```java
            this.sender.sendError(source, translatable("argument.formatted_text.invalid"));
```

with:

```java
            this.sender.sendError(source, Errors.of("wp.error.formatted_text"));
```

replace:

```java
                this.sender.sendError(
                        source,
                        translatable("argument.keywords.too_many", text(MAX_KEYWORDS))
                );
```

with:

```java
                this.sender.sendError(source, Errors.of("wp.error.keywords.too_many", text(MAX_KEYWORDS)));
```

replace:

```java
                this.sender.sendError(source, translatable("argument.keywords.duplicate"));
```

with:

```java
                this.sender.sendError(source, Errors.of("wp.error.keywords.duplicate"));
```

and replace:

```java
        this.sender.sendError(
                source,
                translatable("argument.text.too_long", text(argument), text(maximum))
        );
```

with:

```java
        this.sender.sendError(source, Errors.of("wp.error.too_long", text(argument), text(maximum)));
```

In `validateIcon`, replace:

```java
            this.sender.sendError(source, translatable("waypoint.icon.invalid", text(value.toString())));
```

with:

```java
            this.sender.sendError(source, Errors.of("wp.error.icon", text(value.toString())));
```

In `executeListPatch`, replace:

```java
                        if (result.status() != EditResultStatus.SUCCESS) {
                            this.sendEditError(source, result.status(), listIdentifier);
                            return;
                        }
```

with:

```java
                        if (result.status() != EditResultStatus.SUCCESS) {
                            this.sendEditError(source, result.status(), dimensionName, listIdentifier, null);
                            return;
                        }
```

and replace:

```java
                        this.sender.broadcastChunkedMessage(
                                this.sender.getBroadcastPlayers(source),
                                update
                        );
                        Viewer viewer = viewer(source);
                        this.sender.sendMessage(source, DetailsScreen.list(dimensions(source, viewer), dimensionName,
                                after, DetailsScreen.updated(patch)));
```

with:

```java
                        Component actor = this.sender.getSenderName(source);
                        this.broadcast(source, update, dims -> Broadcasts.updatedList(dims, actor, dimensionName, after));
                        this.sender.sendMessage(source, DetailsScreen.list(dimensions(source), dimensionName,
                                after, DetailsScreen.updated(patch)));
```

In `executeWaypointPatch`, replace:

```java
                        if (result.status() != EditResultStatus.SUCCESS) {
                            this.sendEditError(source, result.status(), waypointIdentifier);
                            return;
                        }
```

with:

```java
                        if (result.status() != EditResultStatus.SUCCESS) {
                            this.sendEditError(source, result.status(), dimensionName, listIdentifier, waypointIdentifier);
                            return;
                        }
```

and replace:

```java
                        this.sender.broadcastChunkedMessage(
                                this.sender.getBroadcastPlayers(source),
                                update
                        );
                        Viewer viewer = viewer(source);
                        this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source, viewer), dimensionName,
                                list, after, DetailsScreen.updated(patch)));
```

with:

```java
                        Component actor = this.sender.getSenderName(source);
                        this.broadcast(source, update, dims -> Broadcasts.updated(dims, actor, dimensionName, list, after));
                        this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source), dimensionName,
                                list, after, DetailsScreen.updated(patch)));
```

In `reportEncodingFailure`, replace:

```java
        this.sender.sendError(source, translatable("waypoint.network.encoding_failed"));
```

with:

```java
        this.sender.sendError(source, Errors.of("wp.error.encoding"));
```

Replace the whole of `sendEditError`:

```java
    private void sendEditError(S source, EditResultStatus status, String identifier) {
        this.sender.sendError(
                source,
                translatable(
                        "waypoint.edit.error." + status.name().toLowerCase(Locale.ROOT),
                        text(identifier)
                )
        );
    }
```

with:

```java
    private void sendEditError(S source, EditResultStatus status, String dimension, String list, @Nullable String waypoint) {
        this.sender.sendError(source, Errors.edit(dimensions(source), status, dimension, list, waypoint));
    }
```

In `executeRestore`, replace:

```java
                                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(
                                            entry.dimensionName(),
                                            list.name(),
                                            list.displayName(),
                                            waypoint.name(),
                                            waypoint,
                                            WaypointModificationType.ADD,
                                            result.syncNum()
                                    ));
                                    Viewer viewer = viewer(source);
                                    this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source, viewer),
                                            entry.dimensionName(), list, waypoint, null));
                                }
                                case DIMENSION_NOT_FOUND, LIST_NOT_FOUND -> this.sender.sendError(
                                        source,
                                        translatable("waypoint.restore.list_missing")
                                );
                                case IDENTIFIER_COLLISION -> this.sender.sendError(
                                        source,
                                        translatable("waypoint.restore.collision")
                                );
                            }
                        }
                ),
                () -> this.sender.sendError(source, translatable("waypoint.restore.invalid"))
        );
```

with:

```java
                                    Component actor = this.sender.getSenderName(source);
                                    this.broadcast(source, new WaypointModificationMessage(
                                            entry.dimensionName(),
                                            list.name(),
                                            list.displayName(),
                                            waypoint.name(),
                                            waypoint,
                                            WaypointModificationType.ADD,
                                            result.syncNum()
                                    ), dims -> Broadcasts.restored(dims, actor, entry.dimensionName(), list, waypoint));
                                    this.sender.sendMessage(source, Results.restored(dimensions(source),
                                            entry.dimensionName(), list, waypoint));
                                }
                                case DIMENSION_NOT_FOUND, LIST_NOT_FOUND -> this.sender.sendError(source,
                                        Errors.of("wp.error.restore.list_missing"));
                                case IDENTIFIER_COLLISION -> this.sender.sendError(source,
                                        Errors.of("wp.error.restore.collision"));
                            }
                        }
                ),
                () -> this.sender.sendError(source, Errors.of("wp.error.restore.invalid"))
        );
```

In `executeAddWaypointList`, replace:

```java
                    case ADDED -> {
                        this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(dimensionName, listName, listName, null, null, ADD_LIST, SERVER_N));
                        this.sender.sendMessage(source, translatable("waypoint.add.list.success", text(listName), dimensionNameWithColor(dimensionName))
                                .appendSpace().append(detailsButton(_959.server_waypoint.util.StringCommandBuilder.detailsListCmd(dimensionName, listName)))
                                .appendSpace().append(suggestCommandButton(
                                        translatable("button.add.waypoint.label"),
                                        NamedTextColor.GREEN,
                                        "/wp add " + dimensionName + " " + _959.server_waypoint.util.StringCommandBuilder.escapeArgument(listName) + " ",
                                        translatable("button.add.waypoint")
                                )));
                        saveChanges(source, result.fileManager());
                    }
                    case EXISTS -> this.sender.sendError(source, translatable("waypoint.add.list.exists", parse(result.waypointList().displayName())));
```

with:

```java
                    case ADDED -> {
                        WaypointList created = result.waypointList();
                        Component actor = this.sender.getSenderName(source);
                        this.broadcast(source, new WaypointModificationMessage(dimensionName, listName, listName, null, null, ADD_LIST, SERVER_N),
                                dims -> Broadcasts.createdList(dims, actor, dimensionName, created));
                        this.sender.sendMessage(source, Results.createdList(dimensions(source), dimensionName, created));
                        saveChanges(source, result.fileManager());
                    }
                    case EXISTS -> this.sender.sendError(source,
                            Errors.listExists(dimensions(source), dimensionName, result.waypointList()));
```

In `addWaypointDirectly`, replace:

```java
                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(
                            dimensionName,
                            result.waypointList().name(),
                            result.waypointList().displayName(),
                            result.waypointSnapshot().name(),
                            result.waypointSnapshot(),
                            WaypointModificationType.ADD,
                            result.syncNum()
                    ));
                    this.sender.sendMessage(
                            source,
                            translatable("waypoint.add.success",
                                    waypointTextWithTp(result.waypointSnapshot(), dimensionName, result.waypointList().name()),
                                    parse(result.waypointList().displayName())
                            ).appendSpace().append(detailsButton(
                                    _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd(
                                            dimensionName,
                                            result.waypointList().name(),
                                            result.waypointSnapshot().name()
                                    )
                            ))
                    );
                }
                case DUPLICATE -> this.sender.sendMessage(
                        source,
                        translatable(
                                "waypoint.add.exists",
                                waypointTextWithTp(result.waypointSnapshot(), dimensionName, result.waypointList().name()),
                                TextButtonBuilder.replaceButton(dimensionName, result.waypointList().name(), newWaypoint)
                        )
                );
```

with:

```java
                    WaypointList list = result.waypointList();
                    SimpleWaypoint added = result.waypointSnapshot();
                    Component actor = this.sender.getSenderName(source);
                    this.broadcast(source, new WaypointModificationMessage(
                            dimensionName,
                            list.name(),
                            list.displayName(),
                            added.name(),
                            added,
                            WaypointModificationType.ADD,
                            result.syncNum()
                    ), dims -> Broadcasts.added(dims, actor, dimensionName, list, added));
                    this.sender.sendMessage(source, Results.added(dimensions(source), dimensionName, list, added));
                }
                case DUPLICATE -> this.sender.sendError(source, Errors.waypointExists(dimensions(source), dimensionName,
                        result.waypointList(), result.waypointSnapshot()));
```

In `executeRemoveList`, replace:

```java
                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(dimensionName, listName, waypointList.displayName(), null, null, REMOVE_LIST, waypointList.getSyncNum() + 1));
                    this.sender.sendMessage(source, translatable("waypoint.remove.list.success", parse(waypointList.displayName())));
                    saveChanges(source, fileManager);
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                case LIST_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName)));
                case NON_EMPTY -> this.sender.sendError(source, translatable("waypoint.remove.list.nonempty", parse(Objects.requireNonNull(result.waypointList()).displayName())));
```

with:

```java
                    Component actor = this.sender.getSenderName(source);
                    this.broadcast(source, new WaypointModificationMessage(dimensionName, listName, waypointList.displayName(), null, null, REMOVE_LIST, waypointList.getSyncNum() + 1),
                            dims -> Broadcasts.removedList(dims, actor, dimensionName, waypointList));
                    this.sender.sendMessage(source, Results.removedList(dimensions(source), dimensionName, waypointList));
                    saveChanges(source, fileManager);
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, Errors.noLists(dimensions(source), dimensionName));
                case LIST_NOT_FOUND -> this.sender.sendError(source, Errors.noList(dimensions(source), dimensionName, listName));
                case NON_EMPTY -> this.sender.sendError(source, Errors.listNotEmpty(dimensions(source), dimensionName,
                        Objects.requireNonNull(result.waypointList())));
```

In `executeRemoveWaypoint`, replace:

```java
                    this.sender.broadcastWaypointModification(source, buffer);
                    String token = this.restoreRegistry.register(
                            this.restoreOwner(source),
                            dimensionName,
                            listName,
                            waypoint
                    );
                    this.sender.sendMessage(source, translatable(
                            "waypoint.remove.success",
                            waypointTextNoTp(waypoint, dimensionName),
                            restoreTokenButton(token)
                    ));
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                case LIST_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName)));
                case LIST_EMPTY -> this.sender.sendError(source, translatable("waypoint.empty.list", parse(Objects.requireNonNull(result.waypointList()).displayName())));
                case WAYPOINT_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.nonexist.waypoint", text(name)));
```

with:

```java
                    WaypointList list = Objects.requireNonNull(result.waypointList());
                    Component actor = this.sender.getSenderName(source);
                    this.broadcast(source, buffer, dims -> Broadcasts.removed(dims, actor, dimensionName, list, waypoint));
                    String token = this.restoreRegistry.register(
                            this.restoreOwner(source),
                            dimensionName,
                            listName,
                            waypoint
                    );
                    this.sender.sendMessage(source, Results.removed(dimensions(source), dimensionName, list, waypoint, token));
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, Errors.noLists(dimensions(source), dimensionName));
                case LIST_NOT_FOUND -> this.sender.sendError(source, Errors.noList(dimensions(source), dimensionName, listName));
                case LIST_EMPTY, WAYPOINT_NOT_FOUND -> this.sender.sendError(source,
                        Errors.noWaypoint(dimensions(source), dimensionName, listName, name));
```

In `executeTp`, replace:

```java
                    this.sender.sendPlayerMessage(player, translatable("waypoint.tp", text(getPlayerName(player)), waypointTextWithTp(waypoint, fileManager.getDimensionName(), listName)));
```

with:

```java
                    this.sender.sendPlayerMessage(player, Results.teleported(
                            DimensionStyle.local(recipientViewer(player), getDimensionTypes(source)),
                            fileManager.getDimensionName(), waypointList, waypoint));
```

Replace `executeReload` and the error in `saveChanges`:

```java
    private void executeReload(S source) {
        executeByServer(source, () -> {
            this.waypointServer.reload();
            List<String> lang = getExternalLoadedLanguages();
            this.sender.sendMessage(source, translatable("waypoint.loaded.languages",
                    text(lang.size()), text(String.join(", ", lang))));
        });
        this.sender.sendMessage(source, translatable("waypoint.reload"));
    }
```

with:

```java
    private void executeReload(S source) {
        executeByServer(source, () -> {
            this.waypointServer.reload();
            this.sender.sendMessage(source, Results.reloaded(getExternalLoadedLanguages()));
        });
    }
```

and replace:

```java
                this.sender.sendError(source, translatable("waypoint.save.failed", text(fileManager.getDimensionFile().toString())));
```

with:

```java
                this.sender.sendError(source, Errors.of("wp.error.save", text(fileManager.getDimensionFile().toString())));
```

Then check that no result or error of these flows still uses an old key:

```bash
grep -n 'translatable("\(argument\|hex_color_code\|waypoint\.\(empty\|nonexist\|add\|remove\|restore\|tp\|reload\|loaded\|save\|icon\|edit\.error\|network\.encoding\)\)' common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java
```

Expected: no output. The navigation, upload and download keys that remain belong to Tasks 14 and 15.

- [ ] **Step 9: Rebuild the sharing prompt**

In `common/src/main/java/_959/server_waypoint/core/network/ChatMessageHandler.java`, replace:

```java
import net.kyori.adventure.text.Component;

import java.util.Iterator;
import java.util.List;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.text.TextButtonBuilder.addListButton;
import static _959.server_waypoint.text.TextButtonBuilder.addWaypointButton;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static _959.server_waypoint.util.XaerosMapHelper.*;
```

with:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.SharingPrompt;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.util.XaerosMapHelper.*;
```

replace:

```java
                if (waypointFileManager != null) {
                    List<WaypointList> waypointListsOnServer = waypointFileManager.getWaypointLists();
                    if (waypointListsOnServer.isEmpty()) {
                        promptNoWaypointList(player, dimensionName);
                    } else {
                        Component feedback = Component.translatable("waypoint.xaeros.sharing.found",
                                waypointTextNoTp(waypoint, dimensionName),
                                dimensionNameWithColor(dimensionName));
                        Component waypointLists = Component.text("");
                        for (Iterator<WaypointList> iterator = waypointListsOnServer.iterator(); iterator.hasNext();) {
                            WaypointList waypointList = iterator.next();
                            String listName = waypointList.name();
                            Component listItem = addWaypointButton(dimensionName, listName, waypoint)
                                    .append(Component.text(" ").style(DEFAULT_STYLE))
                                    .append(_959.server_waypoint.text.FormattedTextHelper.parse(waypointList.displayName()).style(DEFAULT_STYLE));
                            waypointLists = waypointLists.append(listItem);
                            if (iterator.hasNext()) {
                                waypointLists = waypointLists.appendNewline();
                            }
                        }
                        Component listSelector = Component.translatable("waypoint.sharing.add.to.list", waypointLists);
                        feedback = feedback.appendNewline().append(listSelector);
                        this.sender.sendPlayerMessage(player, feedback);
                    }
                } else if (isDimensionValid(dimensionName)) {
                    LOGGER.info("dimension {} not found, add new dimension", dimensionName);
                    waypointServer.addWaypointFileManager(dimensionName);
                    promptNoWaypointList(player, dimensionName);
                } else {
                    this.sender.sendPlayerMessage(player, Component.translatable("waypoint.xaeros.sharing.invalid.dimension",
                            waypointTextNoTp(waypoint, dimensionName),
                            dimensionNameWithColor(dimensionName)));
                }
            }
        }
    }

    private void promptNoWaypointList(P player, String dimString) {
        Component feedback = Component.translatable("waypoint.xaeros.sharing.no.list", addListButton(dimString,""));
        this.sender.sendPlayerMessage(player, feedback);
    }
}
```

with:

```java
                DimensionStyle dims = DimensionStyle.local(this.viewer(player), Map.of());
                if (waypointFileManager != null) {
                    this.sender.sendPlayerMessage(player, SharingPrompt.found(dims, dimensionName, waypoint,
                            waypointFileManager.getWaypointLists()));
                } else if (isDimensionValid(dimensionName)) {
                    LOGGER.info("dimension {} not found, add new dimension", dimensionName);
                    waypointServer.addWaypointFileManager(dimensionName);
                    this.sender.sendPlayerMessage(player, SharingPrompt.found(dims, dimensionName, waypoint, List.of()));
                } else {
                    this.sender.sendPlayerMessage(player, SharingPrompt.unknownDimension(dims, dimensionName, waypoint));
                }
            }
        }
    }

    /** The sharing player, who may add waypoints; whether they may teleport decides the initials' click. */
    private Viewer viewer(P player) {
        Set<Viewer.Permission> permissions = EnumSet.of(Viewer.Permission.ADD);
        if (this.permissionManager.checkPlayerPermission(player, this.permissionManager.keys.tp(),
                CONFIG.CommandPermission().tp())) {
            permissions.add(Viewer.Permission.TP);
        }
        return new Viewer(permissions, false, false, null, null, 0F);
    }
}
```

- [ ] **Step 10: Move the old key assertions to the new keys**

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`, replace `contains("argument.keywords.duplicate")` with `contains("wp.error.keywords.duplicate")`, `.contains("waypoint.icon.invalid")` with `.contains("wp.error.icon")`, and `contains("argument.text.too_long")` with `contains("wp.error.too_long")`.

- [ ] **Step 11: Add the command tests**

In `CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void addingAnswersWithItsActionsAndTellsOnlyTheOtherPlayers() {
        this.harness.addList("minecraft:overworld", "Farms");
        CommandHarness.Source alex = CommandHarness.player();
        CommandHarness.Source sam = new CommandHarness.Source("Sam", "minecraft:the_nether", new WaypointPos(0, 64, 0), 0F,
                true, false, Set.of());
        this.harness.sender.online.addAll(List.of(alex, sam));

        Component result = this.harness.run(alex, "wp add minecraft:overworld Farms 80 66 40 \"Pumpkin Patch\" PP FFAA00 0 true");

        assertEquals("✔ Added [PP] Pumpkin Patch to Farms   Details · Navigate · Undo", render(result));
        assertEquals(1, this.harness.sender.toPlayers.size());
        assertEquals(sam, this.harness.sender.toPlayers.get(0).getKey());
        Component broadcast = this.harness.sender.toPlayers.get(0).getValue();
        assertEquals("Alex added [PP] Pumpkin Patch to Farms", render(broadcast));
        assertTrue(runCommands(broadcast).stream().noneMatch(command -> command.startsWith("/wp tp")));
    }

    @Test
    void removingOffersRestoreWhichPutsTheWaypointBack() {
        this.harness.addList("minecraft:overworld", "Farms", CommandHarness.waypoint("Iron Farm", "IF", 0xAAAAAA, 300, 80, 150));
        CommandHarness.Source player = CommandHarness.player();

        Component removed = this.harness.run(player, "wp remove minecraft:overworld Farms \"Iron Farm\"");
        String restore = runCommands(removed).stream().filter(command -> command.startsWith("/wp restore ")).findFirst().orElseThrow();

        assertEquals("✔ Removed [IF] Iron Farm from Farms   Restore", render(removed));
        assertEquals("✔ Restored [IF] Iron Farm to Farms", render(this.harness.run(player, restore.substring(1))));
        assertTrue(render(this.harness.run(CommandHarness.console(), "wp remove minecraft:overworld Farms \"Iron Farm\""))
                .startsWith("✔ Removed [IF] Iron Farm from Farms. Restore with /wp restore "));
    }

    @Test
    void errorsAreOneRedLineWithARecoveryLink() {
        this.harness.addList("minecraft:overworld", "Farms");
        CommandHarness.Source player = CommandHarness.player();

        assertEquals("✘ No list called Farm in Overworld. Browse lists",
                render(this.harness.run(player, "wp details list minecraft:overworld Farm")));
        assertEquals("✘ No waypoint called Gate in Farms. Open Farms",
                render(this.harness.run(player, "wp tp minecraft:overworld Farms Gate")));
        assertEquals("✘ Overworld already has a list called Farms. Open",
                render(this.harness.run(player, "wp add minecraft:overworld Farms")));
        assertEquals("✘ Nothing changed.",
                render(this.harness.run(player, "wp edit list minecraft:overworld Farms set identifier Farms")));
        assertEquals(4, this.harness.sender.errors.size());
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

and add these imports if the file lacks them: `import _959.server_waypoint.core.waypoint.WaypointPos;`, `import net.kyori.adventure.text.Component;`, `import java.util.List;`, `import java.util.Set;` and `import static _959.server_waypoint.text.chat.ChatAssert.runCommands;`.

- [ ] **Step 12: Run the tests and compile the platforms**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:compileTestJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 13: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java common/src/test/java mods/src/main/java/_959/server_waypoint/common/network/ModMessageSender.java common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Answer with result lines, tell other players and explain errors with recovery links"
```

---

### Task 14: Navigation and text display panels

Spec 10 and 16: `/wp navigate` without arguments shows the navigation panel: the target, every method as a toggle and the actions. A toggle, a start or a retarget re-sends the panel with its `✔` line on top. `Stop` answers `✔ Stopped navigating   Resume`, where Resume navigates to the same waypoint again with the configured default methods, since one command can't carry an arbitrary method set. `/wp navigate config text_display` is a new panel that nudges the text display's offsets in steps of 0.05 blocks, 5° and 0.05×. `/wp navigate status` shows the panel too.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/NavigationScreens.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/NavigationScreensTest.java`; `CommandFeedbackTest.java`
- Modify (tests): `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandNavigationTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `WaypointRefs`, `PlacedWaypoint` and `CoreWaypointCommand.place(NavigationTarget)` (Task 8), `Errors.of` (Task 13), `NavigationMethod`, `NavigationResult`, `TextDisplayTransformation`.
- Produces (`NavigationScreens`): `panel(DimensionStyle, PlacedWaypoint target, Set<NavigationMethod> enabled, Set<NavigationMethod> supported, @Nullable Component updated)`, `idle(DimensionStyle)`, `stopped(DimensionStyle, @Nullable PlacedWaypoint previous)`, `notNavigating(Viewer)`, `failure(NavigationResult)`, `textDisplay(Viewer, TextDisplayTransformation, @Nullable Component updated)`, `methodName(NavigationMethod)`, constants `MOVE_STEP`, `TURN_STEP`, `SIZE_STEP`.
- Keys: `wp.navigation.*` (below), `wp.hint.turn_on`, `wp.hint.turn_off`, `wp.error.not_navigating`, `wp.error.navigation.*`, `wp.text_display.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.navigation.title": "Navigating to {0}",
  "wp.navigation.none": "Not navigating",
  "wp.navigation.choose": "Open a waypoint and choose Navigate:",
  "wp.navigation.started": "Started navigating",
  "wp.navigation.target_changed": "Changed the target",
  "wp.navigation.selection_replaced": "Changed the target and the methods",
  "wp.navigation.turned_on": "{0} on",
  "wp.navigation.turned_off": "{0} off",
  "wp.navigation.stopped": "Stopped navigating",
  "wp.navigation.resume": "Resume",
  "wp.navigation.resume.tooltip": "Navigate to {0} again",
  "wp.navigation.change_target": "Change target",
  "wp.navigation.change_target.tooltip": "Choose another waypoint",
  "wp.navigation.adjust": "Adjust text display",
  "wp.navigation.adjust.tooltip": "Move, turn or resize the text display",
  "wp.navigation.browse": "Browse waypoints",
  "wp.navigation.method.compass": "Compass",
  "wp.navigation.method.map": "Map",
  "wp.navigation.method.bossbar": "Bossbar",
  "wp.navigation.method.actionbar": "Actionbar",
  "wp.navigation.method.text_display": "Text display",
  "wp.navigation.method.on": "{0} is on",
  "wp.navigation.method.off": "{0} is off",
  "wp.hint.turn_on": "Click to turn it on",
  "wp.hint.turn_off": "Click to turn it off",
  "wp.error.not_navigating": "You aren''t navigating.",
  "wp.error.navigation.inventory": "Navigation items need {0} free slots; you have {1}.",
  "wp.error.navigation.target": "The navigation target no longer exists.",
  "wp.error.navigation.no_methods": "Choose at least one navigation method.",
  "wp.error.navigation.method": "{0} isn''t available right now.",
  "wp.error.navigation.failed": "Navigation isn''t available right now.",
  "wp.text_display.title": "Text display",
  "wp.text_display.subtitle": "offsets from the default placement",
  "wp.text_display.move": "Move",
  "wp.text_display.turn": "Turn",
  "wp.text_display.size": "Size",
  "wp.text_display.exact": "Type exact values",
  "wp.text_display.updated": "Updated the text display",
  "wp.text_display.reset": "Reset the text display",
  "wp.text_display.reset.button": "Reset",
  "wp.text_display.reset.tooltip": "Go back to the default placement",
  "wp.text_display.back.tooltip": "Back to navigation"
```

`zh_cn.json`:

```json
  "wp.navigation.title": "正在导航到{0}",
  "wp.navigation.none": "未在导航",
  "wp.navigation.choose": "打开一个路径点并选择导航：",
  "wp.navigation.started": "已开始导航",
  "wp.navigation.target_changed": "已更换目标",
  "wp.navigation.selection_replaced": "已更换目标和方式",
  "wp.navigation.turned_on": "{0}已开启",
  "wp.navigation.turned_off": "{0}已关闭",
  "wp.navigation.stopped": "已停止导航",
  "wp.navigation.resume": "继续",
  "wp.navigation.resume.tooltip": "再次导航到{0}",
  "wp.navigation.change_target": "更换目标",
  "wp.navigation.change_target.tooltip": "选择另一个路径点",
  "wp.navigation.adjust": "调整文本展示",
  "wp.navigation.adjust.tooltip": "移动、旋转或缩放文本展示",
  "wp.navigation.browse": "浏览路径点",
  "wp.navigation.method.compass": "指南针",
  "wp.navigation.method.map": "地图",
  "wp.navigation.method.bossbar": "Boss 栏",
  "wp.navigation.method.actionbar": "动作栏",
  "wp.navigation.method.text_display": "文本展示",
  "wp.navigation.method.on": "{0}已开启",
  "wp.navigation.method.off": "{0}已关闭",
  "wp.hint.turn_on": "点击开启",
  "wp.hint.turn_off": "点击关闭",
  "wp.error.not_navigating": "你没有在导航。",
  "wp.error.navigation.inventory": "导航物品需要 {0} 个空格，你只有 {1} 个。",
  "wp.error.navigation.target": "导航目标已不存在。",
  "wp.error.navigation.no_methods": "请至少选择一种导航方式。",
  "wp.error.navigation.method": "{0}现在不可用。",
  "wp.error.navigation.failed": "导航现在不可用。",
  "wp.text_display.title": "文本展示",
  "wp.text_display.subtitle": "相对默认位置的偏移",
  "wp.text_display.move": "移动",
  "wp.text_display.turn": "旋转",
  "wp.text_display.size": "大小",
  "wp.text_display.exact": "输入精确数值",
  "wp.text_display.updated": "已更新文本展示",
  "wp.text_display.reset": "已重置文本展示",
  "wp.text_display.reset.button": "重置",
  "wp.text_display.reset.tooltip": "恢复默认位置",
  "wp.text_display.back.tooltip": "返回导航"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.navigation.title": "正在導航到{0}",
  "wp.navigation.none": "未在導航",
  "wp.navigation.choose": "開啟一個路徑點並選擇導航：",
  "wp.navigation.started": "已開始導航",
  "wp.navigation.target_changed": "已更換目標",
  "wp.navigation.selection_replaced": "已更換目標和方式",
  "wp.navigation.turned_on": "{0}已開啟",
  "wp.navigation.turned_off": "{0}已關閉",
  "wp.navigation.stopped": "已停止導航",
  "wp.navigation.resume": "繼續",
  "wp.navigation.resume.tooltip": "再次導航到{0}",
  "wp.navigation.change_target": "更換目標",
  "wp.navigation.change_target.tooltip": "選擇另一個路徑點",
  "wp.navigation.adjust": "調整文字展示",
  "wp.navigation.adjust.tooltip": "移動、旋轉或縮放文字展示",
  "wp.navigation.browse": "瀏覽路徑點",
  "wp.navigation.method.compass": "指南針",
  "wp.navigation.method.map": "地圖",
  "wp.navigation.method.bossbar": "Boss 欄",
  "wp.navigation.method.actionbar": "動作列",
  "wp.navigation.method.text_display": "文字展示",
  "wp.navigation.method.on": "{0}已開啟",
  "wp.navigation.method.off": "{0}已關閉",
  "wp.hint.turn_on": "點擊開啟",
  "wp.hint.turn_off": "點擊關閉",
  "wp.error.not_navigating": "你沒有在導航。",
  "wp.error.navigation.inventory": "導航物品需要 {0} 個空格，你只有 {1} 個。",
  "wp.error.navigation.target": "導航目標已不存在。",
  "wp.error.navigation.no_methods": "請至少選擇一種導航方式。",
  "wp.error.navigation.method": "{0}現在無法使用。",
  "wp.error.navigation.failed": "導航現在無法使用。",
  "wp.text_display.title": "文字展示",
  "wp.text_display.subtitle": "相對預設位置的偏移",
  "wp.text_display.move": "移動",
  "wp.text_display.turn": "旋轉",
  "wp.text_display.size": "大小",
  "wp.text_display.exact": "輸入精確數值",
  "wp.text_display.updated": "已更新文字展示",
  "wp.text_display.reset": "已重設文字展示",
  "wp.text_display.reset.button": "重設",
  "wp.text_display.reset.tooltip": "恢復預設位置",
  "wp.text_display.back.tooltip": "返回導航"
```

`es_es.json`:

```json
  "wp.navigation.title": "Navegando hacia {0}",
  "wp.navigation.none": "Sin navegación",
  "wp.navigation.choose": "Abre un punto y elige Navegar:",
  "wp.navigation.started": "Navegación iniciada",
  "wp.navigation.target_changed": "Destino cambiado",
  "wp.navigation.selection_replaced": "Destino y métodos cambiados",
  "wp.navigation.turned_on": "{0} activado",
  "wp.navigation.turned_off": "{0} desactivado",
  "wp.navigation.stopped": "Navegación detenida",
  "wp.navigation.resume": "Reanudar",
  "wp.navigation.resume.tooltip": "Navegar de nuevo hacia {0}",
  "wp.navigation.change_target": "Cambiar destino",
  "wp.navigation.change_target.tooltip": "Elige otro punto",
  "wp.navigation.adjust": "Ajustar texto flotante",
  "wp.navigation.adjust.tooltip": "Mueve, gira o cambia el tamaño del texto flotante",
  "wp.navigation.browse": "Ver puntos",
  "wp.navigation.method.compass": "Brújula",
  "wp.navigation.method.map": "Mapa",
  "wp.navigation.method.bossbar": "Barra de jefe",
  "wp.navigation.method.actionbar": "Barra de acción",
  "wp.navigation.method.text_display": "Texto flotante",
  "wp.navigation.method.on": "{0} está activado",
  "wp.navigation.method.off": "{0} está desactivado",
  "wp.hint.turn_on": "Haz clic para activarlo",
  "wp.hint.turn_off": "Haz clic para desactivarlo",
  "wp.error.not_navigating": "No estás navegando.",
  "wp.error.navigation.inventory": "Los objetos de navegación necesitan {0} huecos libres; tienes {1}.",
  "wp.error.navigation.target": "El destino de la navegación ya no existe.",
  "wp.error.navigation.no_methods": "Elige al menos un método de navegación.",
  "wp.error.navigation.method": "{0} no está disponible ahora.",
  "wp.error.navigation.failed": "La navegación no está disponible ahora.",
  "wp.text_display.title": "Texto flotante",
  "wp.text_display.subtitle": "desplazamientos desde la posición por defecto",
  "wp.text_display.move": "Mover",
  "wp.text_display.turn": "Girar",
  "wp.text_display.size": "Tamaño",
  "wp.text_display.exact": "Escribe valores exactos",
  "wp.text_display.updated": "Texto flotante actualizado",
  "wp.text_display.reset": "Texto flotante restablecido",
  "wp.text_display.reset.button": "Restablecer",
  "wp.text_display.reset.tooltip": "Vuelve a la posición por defecto",
  "wp.text_display.back.tooltip": "Volver a la navegación"
```

`he_il.json`:

```json
  "wp.navigation.title": "מנווטים אל {0}",
  "wp.navigation.none": "אין ניווט פעיל",
  "wp.navigation.choose": "פתחו נקודה ובחרו ניווט:",
  "wp.navigation.started": "הניווט התחיל",
  "wp.navigation.target_changed": "היעד הוחלף",
  "wp.navigation.selection_replaced": "היעד והשיטות הוחלפו",
  "wp.navigation.turned_on": "{0} מופעל",
  "wp.navigation.turned_off": "{0} כבוי",
  "wp.navigation.stopped": "הניווט הופסק",
  "wp.navigation.resume": "המשך",
  "wp.navigation.resume.tooltip": "ניווט אל {0} שוב",
  "wp.navigation.change_target": "החלפת יעד",
  "wp.navigation.change_target.tooltip": "בחירת נקודה אחרת",
  "wp.navigation.adjust": "כוונון תצוגת הטקסט",
  "wp.navigation.adjust.tooltip": "הזזה, סיבוב או שינוי גודל של תצוגת הטקסט",
  "wp.navigation.browse": "עיון בנקודות",
  "wp.navigation.method.compass": "מצפן",
  "wp.navigation.method.map": "מפה",
  "wp.navigation.method.bossbar": "פס בוס",
  "wp.navigation.method.actionbar": "פס פעולות",
  "wp.navigation.method.text_display": "תצוגת טקסט",
  "wp.navigation.method.on": "{0} מופעל",
  "wp.navigation.method.off": "{0} כבוי",
  "wp.hint.turn_on": "לחצו להפעלה",
  "wp.hint.turn_off": "לחצו לכיבוי",
  "wp.error.not_navigating": "אתם לא מנווטים.",
  "wp.error.navigation.inventory": "פריטי הניווט צריכים {0} משבצות פנויות; יש לכם {1}.",
  "wp.error.navigation.target": "יעד הניווט כבר לא קיים.",
  "wp.error.navigation.no_methods": "בחרו לפחות שיטת ניווט אחת.",
  "wp.error.navigation.method": "{0} אינו זמין כרגע.",
  "wp.error.navigation.failed": "הניווט אינו זמין כרגע.",
  "wp.text_display.title": "תצוגת טקסט",
  "wp.text_display.subtitle": "היסטים מהמיקום ברירת המחדל",
  "wp.text_display.move": "הזזה",
  "wp.text_display.turn": "סיבוב",
  "wp.text_display.size": "גודל",
  "wp.text_display.exact": "הקלדת ערכים מדויקים",
  "wp.text_display.updated": "תצוגת הטקסט עודכנה",
  "wp.text_display.reset": "תצוגת הטקסט אופסה",
  "wp.text_display.reset.button": "איפוס",
  "wp.text_display.reset.tooltip": "חזרה למיקום ברירת המחדל",
  "wp.text_display.back.tooltip": "חזרה לניווט"
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/NavigationScreensTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static net.kyori.adventure.text.Component.translatable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationScreensTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final PlacedWaypoint MAIN_HOME = new PlacedWaypoint(OVERWORLD, HOME, HOME.getWaypointByName("Main Home"));
    private static final Set<NavigationMethod> ALL = EnumSet.allOf(NavigationMethod.class);
    private static final String TRANSFORMATION = "/wp navigate config text_display transformation ";

    @Test
    void thePanelShowsTheTargetEveryMethodAndTheActions() {
        Component panel = NavigationScreens.panel(Fixtures.dims(Fixtures.player()), MAIN_HOME,
                EnumSet.of(NavigationMethod.COMPASS, NavigationMethod.BOSSBAR), ALL, null);

        assertEquals(List.of(
                "Navigating to [MH] Main Home · 25 m",
                "✔ Compass · Map · ✔ Bossbar · Actionbar · Text display",
                "Stop · Change target"), lines(panel));
        assertEquals(NamedTextColor.GREEN, colorOf(panel, "✔ Compass"));
        assertEquals("/wp navigate disable compass", clickOf(panel, "✔ Compass"));
        assertEquals("Compass is on\nClick to turn it off", tooltipOf(panel, "✔ Compass"));
        assertEquals(NamedTextColor.GRAY, colorOf(panel, "Map"));
        assertEquals("/wp navigate use map", clickOf(panel, "Map"));
        assertEquals(NamedTextColor.RED, colorOf(panel, "Stop"));
        assertEquals("/wp navigate disable", clickOf(panel, "Stop"));
        assertEquals("/wp list", clickOf(panel, "Change target"));
    }

    @Test
    void adjustingTheTextDisplayAppearsOnlyWhileItIsOnAndUpdatesGoOnTop() {
        Component panel = NavigationScreens.panel(Fixtures.dims(Fixtures.player()), MAIN_HOME,
                EnumSet.of(NavigationMethod.TEXT_DISPLAY), ALL,
                translatable("wp.navigation.turned_on", NavigationScreens.methodName(NavigationMethod.TEXT_DISPLAY)));

        assertEquals("✔ Text display on", lines(panel).get(0));
        assertEquals("Stop · Change target · Adjust text display", lines(panel).get(3));
        assertEquals(NamedTextColor.YELLOW, colorOf(panel, "Adjust text display"));
        assertEquals("/wp navigate config text_display", clickOf(panel, "Adjust text display"));
    }

    @Test
    void elsewhereThePanelNamesTheTargetsDimensionAndOnlySupportedMethodsShow() {
        Component panel = NavigationScreens.panel(Fixtures.dims(Fixtures.in(Fixtures.player(), NETHER)), MAIN_HOME,
                EnumSet.of(NavigationMethod.ACTIONBAR), EnumSet.of(NavigationMethod.ACTIONBAR, NavigationMethod.BOSSBAR), null);

        assertEquals("Navigating to [MH] Main Home · Overworld", lines(panel).get(0));
        assertEquals("Bossbar · ✔ Actionbar", lines(panel).get(1));
    }

    @Test
    void withoutATargetThePanelSaysWhereToStartAndStoppingOffersResume() {
        assertEquals(List.of("Not navigating", "Open a waypoint and choose Navigate:  This dimension · All"),
                lines(NavigationScreens.idle(Fixtures.dims(Fixtures.player()))));
        assertEquals(List.of("Not navigating"), lines(NavigationScreens.idle(Fixtures.dims(Fixtures.console()))));

        Component stopped = NavigationScreens.stopped(Fixtures.dims(Fixtures.player()), MAIN_HOME);
        assertEquals("✔ Stopped navigating   Resume", render(stopped));
        assertEquals("/wp navigate minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(stopped, "Resume"));
        assertEquals("✔ Stopped navigating", render(NavigationScreens.stopped(Fixtures.dims(Fixtures.player()), null)));
        assertEquals("✘ You aren't navigating. Browse waypoints", render(NavigationScreens.notNavigating(Fixtures.player())));
        assertEquals("/wp list", clickOf(NavigationScreens.notNavigating(Fixtures.player()), "Browse waypoints"));
    }

    @Test
    void failuresAreErrorLines() {
        assertEquals("✘ Navigation items need 2 free slots; you have 1.",
                render(NavigationScreens.failure(NavigationResult.insufficientInventory(2, 1))));
        assertEquals("✘ The navigation target no longer exists.",
                render(NavigationScreens.failure(NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE))));
        assertEquals("✘ Choose at least one navigation method.",
                render(NavigationScreens.failure(NavigationResult.failure(NavigationResult.Code.INVALID_SELECTION))));
        assertEquals("✘ Compass isn't available right now.", render(NavigationScreens.failure(
                NavigationResult.failure(NavigationResult.Code.METHOD_UNAVAILABLE).withMethod(NavigationMethod.COMPASS))));
    }

    @Test
    void theTextDisplayPanelNudgesEachValue() {
        Component panel = NavigationScreens.textDisplay(Fixtures.player(), TextDisplayTransformation.defaultValue(), null);

        assertEquals(List.of(
                "Text display  offsets from the default placement",
                "Move  X [−][+]  Y [−][+]  Z [−][+]  0, 0, 0 [✎]",
                "Turn  X [−][+]  Y [−][+]  Z [−][+]  0°, 0°, 0° [✎]",
                "Size  [−][+]  1× [✎]",
                "[Reset] [Back]"), lines(panel));
        assertEquals(TRANSFORMATION + "translation -0.05 0 0", clickOf(panel, "[−]"));
        assertEquals(TRANSFORMATION + "translation 0.05 0 0", clickOf(panel, "[+]"));
        assertEquals(TRANSFORMATION + "translation 0 0 0", clickOf(panel, "[✎]"));
        assertTrue(suggestions(panel).contains(TRANSFORMATION + "rotation 0 0 0"));
        assertTrue(suggestions(panel).contains(TRANSFORMATION + "scale 1 1 1"));
        assertEquals(TRANSFORMATION + "reset", clickOf(panel, "[Reset]"));
        assertEquals("/wp navigate", clickOf(panel, "[Back]"));
    }

    @Test
    void nudgesStopAtTheLimitsAndUnevenSizesShowEachAxis() {
        TextDisplayTransformation moved = new TextDisplayTransformation(new Vector3f(16F, 0.1F, -0.05F),
                new Vector3f(355F, 0F, -5F), new Vector3f(0.05F, 2F, 1F));
        Component panel = NavigationScreens.textDisplay(Fixtures.player(), moved,
                translatable("wp.text_display.updated"));

        assertEquals(List.of(
                "✔ Updated the text display",
                "Text display  offsets from the default placement",
                "Move  X [−][+]  Y [−][+]  Z [−][+]  16, 0.1, -0.05 [✎]",
                "Turn  X [−][+]  Y [−][+]  Z [−][+]  355°, 0°, -5° [✎]",
                "Size  [−][+]  0.05×, 2×, 1× [✎]",
                "[Reset] [Back]"), lines(panel));
        assertEquals(TRANSFORMATION + "translation 16 0.1 -0.05", clickOf(panel, "[+]"));
        assertTrue(runCommands(panel).containsAll(List.of(
                TRANSFORMATION + "rotation 360 0 -5",
                TRANSFORMATION + "scale 0.05 0.05 0.05",
                TRANSFORMATION + "scale 0.1 0.1 0.1")));
        assertTrue(lines(NavigationScreens.textDisplay(Fixtures.console(), moved, null))
                .contains("Size  0.05×, 2×, 1×"));
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.NavigationScreensTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable NavigationScreens`.

- [ ] **Step 4: Write `NavigationScreens`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/NavigationScreens.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static _959.server_waypoint.util.StringCommandBuilder.navigateCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The navigation panel and the text display panel (spec 10). */
public final class NavigationScreens {
    public static final float MOVE_STEP = 0.05F;
    public static final float TURN_STEP = 5F;
    public static final float SIZE_STEP = 0.05F;
    private static final String TRANSFORMATION = "/wp navigate config text_display transformation ";
    private static final String[] AXES = {"X", "Y", "Z"};

    private NavigationScreens() {
    }

    /** Navigating to [MH] Main Home · 25 m, the methods as toggles, then Stop · Change target. */
    public static Component panel(DimensionStyle dims, PlacedWaypoint target, Set<NavigationMethod> enabled,
                                  Set<NavigationMethod> supported, @Nullable Component updated) {
        Viewer viewer = dims.viewer();
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        Component where = WaypointRefs.rowDetail(viewer, target.dimension(), target.waypoint().pos());
        lines.add(Chat.join(translatable("wp.navigation.title", GOLD, target.reference(dims)),
                where == null ? dims.name(target.dimension()) : where));
        List<Component> methods = new ArrayList<>();
        for (NavigationMethod method : NavigationMethod.values()) {
            if (!supported.contains(method)) {
                continue;
            }
            Component name = methodName(method);
            methods.add(enabled.contains(method)
                    ? Chat.link(viewer, Chat.concat(text(Chat.CHECK + " "), name), GREEN,
                    Click.run("/wp navigate disable " + method.id()),
                    Tooltip.of("wp.navigation.method.on", name).hint("wp.hint.turn_off"))
                    : Chat.link(viewer, name, GRAY, Click.run("/wp navigate use " + method.id()),
                    Tooltip.of("wp.navigation.method.off", name).hint("wp.hint.turn_on")));
        }
        lines.add(Chat.join(methods));
        List<Component> actions = new ArrayList<>();
        actions.add(Chat.control(viewer, translatable("wp.navigation.stop"), RED, Click.run("/wp navigate disable"),
                Tooltip.of("wp.navigation.stop.tooltip")));
        actions.add(Chat.control(viewer, translatable("wp.navigation.change_target"), AQUA, Click.run("/wp list"),
                Tooltip.of("wp.navigation.change_target.tooltip")));
        if (enabled.contains(NavigationMethod.TEXT_DISPLAY)) {
            actions.add(Chat.control(viewer, translatable("wp.navigation.adjust"), YELLOW,
                    Click.run("/wp navigate config text_display"), Tooltip.of("wp.navigation.adjust.tooltip")));
        }
        if (!Chat.isEmpty(actions)) {
            lines.add(Chat.join(actions));
        }
        return lines.build();
    }

    /** Not navigating, then where to start. */
    public static Component idle(DimensionStyle dims) {
        Viewer viewer = dims.viewer();
        ChatLines lines = new ChatLines().add(translatable("wp.navigation.none", GOLD));
        if (viewer.plainText()) {
            return lines.build();
        }
        String here = Objects.requireNonNullElse(viewer.dimension(), "minecraft:overworld");
        return lines.line(translatable("wp.navigation.choose", GRAY), text("  "), Chat.join(
                Chat.link(viewer, translatable("wp.menu.this_dimension"), AQUA, Click.run("/wp list"),
                        Tooltip.of(translatable("wp.menu.this_dimension.tooltip", dims.name(here)))),
                Chat.link(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"), Tooltip.of("wp.all.tooltip"))))
                .build();
    }

    /** ✔ Stopped navigating   Resume */
    public static Component stopped(DimensionStyle dims, @Nullable PlacedWaypoint previous) {
        Viewer viewer = dims.viewer();
        Component resume = previous == null ? null : Chat.control(viewer, translatable("wp.navigation.resume"), LIGHT_PURPLE,
                Click.run(navigateCmd(previous.dimension(), previous.list().name(), previous.waypoint().name())),
                Tooltip.of("wp.navigation.resume.tooltip",
                        WaypointRefs.label(previous.waypoint().displayName(), previous.waypoint().name())));
        return Chat.ok(translatable("wp.navigation.stopped"), java.util.Arrays.asList(resume));
    }

    /** ✘ You aren't navigating. Browse waypoints */
    public static Component notNavigating(Viewer viewer) {
        return Chat.error(translatable("wp.error.not_navigating"), Chat.control(viewer,
                translatable("wp.navigation.browse"), AQUA, Click.run("/wp list"),
                Tooltip.of("wp.navigation.change_target.tooltip")));
    }

    /** The error line for a navigation result that failed. */
    public static Component failure(NavigationResult result) {
        return switch (result.code()) {
            case INSUFFICIENT_INVENTORY -> Errors.of("wp.error.navigation.inventory",
                    text(result.requiredSlots()), text(result.availableSlots()));
            case TARGET_UNAVAILABLE -> Errors.of("wp.error.navigation.target");
            case INVALID_SELECTION -> Errors.of("wp.error.navigation.no_methods");
            default -> result.method() == null
                    ? Errors.of("wp.error.navigation.failed")
                    : Errors.of("wp.error.navigation.method", methodName(result.method()));
        };
    }

    public static Component methodName(NavigationMethod method) {
        return translatable("wp.navigation.method." + method.id());
    }

    /** /wp navigate config text_display: Move, Turn and Size with nudges and their exact values. */
    public static Component textDisplay(Viewer viewer, TextDisplayTransformation transformation,
                                        @Nullable Component updated) {
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        lines.line(translatable("wp.text_display.title", GOLD), text("  "), translatable("wp.text_display.subtitle", GRAY));
        lines.add(vectorRow(viewer, "wp.text_display.move", "translation", transformation.translation(), MOVE_STEP,
                -TextDisplayTransformation.MAX_TRANSLATION, TextDisplayTransformation.MAX_TRANSLATION, ""));
        lines.add(vectorRow(viewer, "wp.text_display.turn", "rotation", transformation.rotation(), TURN_STEP,
                -TextDisplayTransformation.MAX_ROTATION_DEGREES, TextDisplayTransformation.MAX_ROTATION_DEGREES, "°"));
        lines.add(sizeRow(viewer, transformation.scale()));
        List<Component> buttons = java.util.Arrays.asList(
                Chat.button(viewer, translatable("wp.text_display.reset.button"), AQUA, Click.run(TRANSFORMATION + "reset"),
                        Tooltip.of("wp.text_display.reset.tooltip")),
                Chat.button(viewer, translatable("wp.action.back"), GRAY, Click.run("/wp navigate"),
                        Tooltip.of("wp.text_display.back.tooltip")));
        if (!Chat.isEmpty(buttons)) {
            lines.add(Chat.spaced(buttons));
        }
        return lines.build();
    }

    /** Move  X [−][+]  Y [−][+]  Z [−][+]  0, 0, 0 [✎] */
    private static Component vectorRow(Viewer viewer, String labelKey, String component, Vector3f value, float step,
                                       float minimum, float maximum, String unit) {
        float[] values = {value.x(), value.y(), value.z()};
        List<Component> pieces = new ArrayList<>();
        pieces.add(translatable(labelKey, GRAY));
        if (!viewer.plainText()) {
            for (int axis = 0; axis < 3; axis++) {
                pieces.add(text("  " + AXES[axis] + " ", GRAY));
                pieces.add(nudge(viewer, "−", component, values, axis, -step, minimum, maximum, unit));
                pieces.add(nudge(viewer, "+", component, values, axis, step, minimum, maximum, unit));
            }
        }
        String shown = format(values[0]) + unit + ", " + format(values[1]) + unit + ", " + format(values[2]) + unit;
        pieces.add(text("  "));
        pieces.add(text(shown, WHITE));
        pieces.add(exact(viewer, component, values));
        return Chat.concat(pieces);
    }

    /** Size  [−][+]  1× [✎]; a size that differs per axis shows each axis. */
    private static Component sizeRow(Viewer viewer, Vector3f value) {
        float[] values = {value.x(), value.y(), value.z()};
        boolean even = values[0] == values[1] && values[1] == values[2];
        List<Component> pieces = new ArrayList<>();
        pieces.add(translatable("wp.text_display.size", GRAY));
        if (!viewer.plainText()) {
            pieces.add(text("  "));
            for (String sign : List.of("−", "+")) {
                float size = clamp(values[0] + (sign.equals("+") ? SIZE_STEP : -SIZE_STEP), SIZE_STEP,
                        TextDisplayTransformation.MAX_SCALE_MULTIPLIER);
                pieces.add(Chat.button(viewer, text(sign), AQUA,
                        Click.run(TRANSFORMATION + "scale " + format(size) + " " + format(size) + " " + format(size)),
                        Tooltip.of(text(sign + " " + format(SIZE_STEP) + "×"))));
            }
        }
        String shown = even ? format(values[0]) + "×"
                : format(values[0]) + "×, " + format(values[1]) + "×, " + format(values[2]) + "×";
        pieces.add(text("  "));
        pieces.add(text(shown, WHITE));
        pieces.add(exact(viewer, "scale", values));
        return Chat.concat(pieces);
    }

    private static @Nullable Component nudge(Viewer viewer, String sign, String component, float[] values, int axis,
                                             float step, float minimum, float maximum, String unit) {
        float[] nudged = values.clone();
        nudged[axis] = clamp(values[axis] + step, minimum, maximum);
        return Chat.button(viewer, text(sign), AQUA, Click.run(TRANSFORMATION + component + " " + vector(nudged)),
                Tooltip.of(text(AXES[axis] + " " + sign + " " + format(Math.abs(step)) + unit)));
    }

    /** The yellow [✎] suggesting the exact values, after a space. */
    private static @Nullable Component exact(Viewer viewer, String component, float[] values) {
        Component button = Chat.button(viewer, text("✎"), YELLOW, Click.suggest(TRANSFORMATION + component + " " + vector(values)),
                Tooltip.of("wp.text_display.exact").hint("wp.hint.edit"));
        return button == null ? null : Chat.concat(text(" "), button);
    }

    private static String vector(float[] values) {
        return format(values[0]) + " " + format(values[1]) + " " + format(values[2]);
    }

    /** At most two decimals, without trailing zeros: 0, 0.05, -2.5, 355. */
    static String format(float value) {
        return BigDecimal.valueOf(Math.round(value * 100) / 100.0).stripTrailingZeros().toPlainString();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
```

- [ ] **Step 5: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 6: Send the panels from the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.feedback.DetailsScreen;
```

with:

```java
import _959.server_waypoint.text.feedback.DetailsScreen;
import _959.server_waypoint.text.feedback.NavigationScreens;
```

In `navigationCommandNode`, replace:

```java
        LiteralArgumentBuilder<S> navigateNode = literal(NAVIGATE_COMMAND);
        navigateNode.requires(this::hasNavigatePermission);
```

with:

```java
        LiteralArgumentBuilder<S> navigateNode = literal(NAVIGATE_COMMAND);
        navigateNode.requires(this::hasNavigatePermission);
        navigateNode.executes(context -> {
            executeNavigationPanel(context.getSource());
            return Command.SINGLE_SUCCESS;
        });
```

In `textDisplayTransformationCommandNode`, replace:

```java
        LiteralArgumentBuilder<S> textDisplayNode = literal(NavigationMethod.TEXT_DISPLAY.id());
```

with:

```java
        LiteralArgumentBuilder<S> textDisplayNode = literal(NavigationMethod.TEXT_DISPLAY.id());
        textDisplayNode.executes(context -> {
            executeTextDisplayPanel(context.getSource());
            return Command.SINGLE_SUCCESS;
        });
```

Replace `executeNavigateDisable(S source)` and `executeNavigateStatus`:

```java
    private void executeNavigateDisable(S source) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.disableAll(player));
        }
    }
```

with:

```java
    private void executeNavigateDisable(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationSession previous = this.navigationService.status(player).session();
        NavigationResult result = this.navigationService.disableAll(player);
        if (result.code() == NavigationResult.Code.NAVIGATION_DISABLED) {
            this.sender.sendMessage(source, NavigationScreens.stopped(dimensions(source),
                    previous == null ? null : place(previous.target())));
        } else {
            sendNavigationResult(source, result);
        }
    }
```

and:

```java
    private void executeNavigateStatus(S source) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.status(player));
        }
    }
```

with:

```java
    private void executeNavigateStatus(S source) {
        executeNavigationPanel(source);
    }

    /** /wp navigate: the panel while navigating, otherwise where to start. */
    private void executeNavigationPanel(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationResult status = this.navigationService.status(player);
        if (status.session() == null) {
            this.sender.sendMessage(source, NavigationScreens.idle(dimensions(source)));
        } else {
            sendNavigationResult(source, status);
        }
    }

    /** /wp navigate config text_display */
    private void executeTextDisplayPanel(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationSession session = this.navigationService.status(player).session();
        if (session == null) {
            this.sender.sendError(source, NavigationScreens.notNavigating(viewer(source)));
        } else {
            this.sender.sendMessage(source, NavigationScreens.textDisplay(viewer(source),
                    session.textDisplayTransformation(), null));
        }
    }
```

In `executeTextDisplayTransformationReset`, replace:

```java
        NavigationResult result = this.navigationService.resetTextDisplayTransformation(player);
        if (result.code() != NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED) {
            sendNavigationResult(source, result);
            return;
        }
        sendTextDisplayTransformation(
                source,
                "waypoint.navigation.text_display.transformation.reset",
                result.session().textDisplayTransformation()
        );
```

with:

```java
        sendTextDisplayTransformation(source, this.navigationService.resetTextDisplayTransformation(player),
                translatable("wp.text_display.reset"));
```

Replace `sendTextDisplayTransformationUpdate`, `sendTextDisplayTransformation` and `transformationVector`:

```java
    private void sendTextDisplayTransformationUpdate(S source, NavigationResult result) {
        if (result.code() != NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED) {
            sendNavigationResult(source, result);
            return;
        }
        sendTextDisplayTransformation(
                source,
                "waypoint.navigation.text_display.transformation.updated",
                result.session().textDisplayTransformation()
        );
    }

    private void sendTextDisplayTransformation(
            S source,
            String translationKey,
            TextDisplayTransformation transformation
    ) {
        this.sender.sendMessage(
                source,
                translatable(
                        translationKey,
                        transformationVector(transformation.translation()),
                        transformationVector(transformation.rotation()),
                        transformationVector(transformation.scale())
                )
        );
    }

    private static Component transformationVector(Vector3f vector) {
        return text(vector.x() + " " + vector.y() + " " + vector.z());
    }
```

with:

```java
    private void sendTextDisplayTransformationUpdate(S source, NavigationResult result) {
        sendTextDisplayTransformation(source, result, translatable("wp.text_display.updated"));
    }

    /** The text display panel with the change on top, or why the change failed. */
    private void sendTextDisplayTransformation(S source, NavigationResult result, Component updated) {
        if (result.code() != NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED || result.session() == null) {
            sendNavigationResult(source, result);
            return;
        }
        this.sender.sendMessage(source, NavigationScreens.textDisplay(viewer(source),
                result.session().textDisplayTransformation(), updated));
    }
```

In `getNavigationPlayer`, replace:

```java
            this.sender.sendError(source, translatable("waypoint.navigation.player_only"));
```

with:

```java
            this.sender.sendError(source, Errors.playerOnly());
```

Replace the whole of `sendNavigationResult`, `navigationTargetName` and `navigationMethods` (from `private void sendNavigationResult(S source, NavigationResult result) {` through the end of `navigationMethods`) with:

```java
    /** The navigation panel with what changed on top, or the error a failed result calls for (spec 10). */
    private void sendNavigationResult(S source, NavigationResult result) {
        NavigationSession session = result.session();
        if (result.code() == NavigationResult.Code.NO_ACTIVE_SESSION) {
            this.sender.sendError(source, NavigationScreens.notNavigating(viewer(source)));
            return;
        }
        if (!result.successful()) {
            this.sender.sendError(source, NavigationScreens.failure(result));
            return;
        }
        if (session == null) {
            return;
        }
        Component updated = switch (result.code()) {
            case NAVIGATION_STARTED -> translatable("wp.navigation.started");
            case TARGET_CHANGED -> translatable("wp.navigation.target_changed");
            case SELECTION_REPLACED -> translatable("wp.navigation.selection_replaced");
            case METHOD_ENABLED, METHOD_ALREADY_ENABLED -> translatable("wp.navigation.turned_on",
                    NavigationScreens.methodName(Objects.requireNonNull(result.method())));
            case METHOD_DISABLED, METHOD_ALREADY_DISABLED -> translatable("wp.navigation.turned_off",
                    NavigationScreens.methodName(Objects.requireNonNull(result.method())));
            default -> null;
        };
        this.sender.sendMessage(source, NavigationScreens.panel(dimensions(source), place(session.target()),
                session.enabledMethods(), supportedNavigationMethods(), updated));
    }
```

Remove the `org.joml.Vector3f` import only if nothing else in the file uses it (the transformation vector nodes still build `Vector3f` values, so it normally stays).

- [ ] **Step 7: Move the navigation tests to the new messages**

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandNavigationTest.java`, replace the sender's key helper:

```java
        private static String translationKey(Component component) {
            if (component instanceof TranslatableComponent translatableComponent) {
                return translatableComponent.key();
            }
            throw new AssertionError("Expected translatable component but got " + component);
        }
```

with:

```java
        /** The first wp. translation key, depth first: the message's result, title or error line. */
        private static String translationKey(Component component) {
            String key = firstKey(component);
            if (key == null) {
                throw new AssertionError("Expected a wp. translation in " + component);
            }
            return key;
        }

        private static String firstKey(Component component) {
            if (component instanceof TranslatableComponent translatable && translatable.key().startsWith("wp.")) {
                return translatable.key();
            }
            for (Component child : component.children()) {
                String key = firstKey(child);
                if (key != null) {
                    return key;
                }
            }
            return null;
        }
```

Then change the expected keys:

- In `navigationIsPlayerOnlyAndReportsTranslatedFeedback`: `List.of("waypoint.navigation.player_only")` becomes `List.of("wp.error.player_only")`.
- In `useDisableAndStatusLiteralsAreNotParsedAsDimensions`, replace the `errorKeys()` assertion with:

  ```java
        assertEquals(List.of(
                "wp.error.not_navigating",
                "wp.error.not_navigating",
                "wp.error.not_navigating"
        ), this.sender.errorKeys());
        assertEquals(List.of("wp.navigation.none"), this.sender.messageKeys());
  ```

- `"waypoint.navigation.started"` becomes `"wp.navigation.started"`, and `"waypoint.navigation.target_changed"` becomes `"wp.navigation.target_changed"`.
- In `textDisplayTransformationCommandsUpdateStoredComponentsIndependentlyAndReset`: `"waypoint.navigation.text_display.transformation.updated"` becomes `"wp.text_display.updated"` and `"waypoint.navigation.text_display.transformation.reset"` becomes `"wp.text_display.reset"`.
- In `useDisableAndStatusOperateOnTheActiveSession`: `"waypoint.navigation.method_enabled"` becomes `"wp.navigation.turned_on"`, `"waypoint.navigation.status"` becomes `"wp.navigation.title"`, `"waypoint.navigation.method_disabled"` becomes `"wp.navigation.turned_off"`, `"waypoint.navigation.disabled"` becomes `"wp.navigation.stopped"`, and the last assertion becomes `assertEquals("wp.navigation.none", this.sender.lastMessageKey());`.

- [ ] **Step 8: Add the command test**

In `CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void navigateWithoutArgumentsShowsWhereToStart() {
        CommandHarness.Source player = CommandHarness.player();

        assertEquals(List.of("Not navigating", "Open a waypoint and choose Navigate:  This dimension · All"),
                lines(this.harness.run(player, "wp navigate")));
        assertEquals("✘ You aren't navigating. Browse waypoints", render(this.harness.run(player, "wp navigate disable")));
        assertEquals("✘ Only players can do that.", render(this.harness.run(CommandHarness.console(), "wp navigate")));
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

The harness's navigation service has no method handlers, so `wp navigate config text_display` isn't registered there; `NavigationScreensTest` covers that panel and `CoreWaypointCommandNavigationTest` its command.

- [ ] **Step 9: Run the tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java/_959/server_waypoint/text/feedback common/src/test/java/_959/server_waypoint/text/feedback common/src/main/java/_959/server_waypoint/command common/src/test/java/_959/server_waypoint/command common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add the navigation and text display panels"
```

---

### Task 15: Upload panel, upload outcome and download result

Spec 11 and 16: `/wp upload` without arguments shows each map mod with its three modes, so nobody types `force local delete`. The outcome replaces the old count sentence, the legend line and the hard-coded `FORCE LOCAL` button with a `✔` or `✘` line, the non-zero counts (each explained in its tooltip) and links that suggest the follow-up upload. Downloads answer `✔ Sent 12 waypoints to your map mod`. Every upload and download error becomes a `✘` line.

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/UploadScreens.java`
- Modify: `common/src/main/java/_959/server_waypoint/text/feedback/Results.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/network/upload/UploadCoordinator.java`
- Modify: `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`, `common/src/main/java/_959/server_waypoint/text/TextButtonBuilder.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/UploadScreensTest.java`; `CommandFeedbackTest.java`
- Modify (tests): `common/src/test/java/_959/server_waypoint/core/network/upload/UploadCoordinatorTest.java`, `common/src/test/java/_959/server_waypoint/util/StringCommandBuilderTest.java`, `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `Errors.of` (Task 13), `UploadTarget`, `UploadScope`, `UploadConflictPolicy`, `UploadRequestBuffer`.
- Produces:
  - `UploadScreens.panel(Viewer)`, `UploadScreens.requested(boolean mirror)`, `UploadScreens.sourceName(UploadTarget)`, `record UploadScreens.Outcome(UploadTarget source, int added, int replaced, int deleted, int unchanged, int conflicts, int skipped, int staleDimensions, boolean saveFailed, boolean stoppedEarly, @Nullable String preferMine, String retry)`, `UploadScreens.result(Outcome)`.
  - `Results.sent(int waypoints)`.
  - `StringCommandBuilder.uploadCmd(UploadScope, UploadRequestBuffer, UploadConflictPolicy, boolean deleteMissing)`, replacing `uploadLocalCmd`; `TextButtonBuilder.uploadPreferLocalButton` is deleted.
- Keys: `wp.upload.*`, `wp.count.conflict.{one,other}`, `wp.download.sent`, `wp.error.upload.*`, `wp.error.delivery`, `wp.error.download.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.upload.title": "Upload from your map mod",
  "wp.upload.source.xaero": "Xaero''s Minimap",
  "wp.upload.source.voxelmap": "VoxelMap",
  "wp.upload.merge": "Merge",
  "wp.upload.merge.tooltip": "Add your waypoints",
  "wp.upload.merge.detail": "Conflicts keep the server''s version",
  "wp.upload.prefer_mine": "Prefer mine",
  "wp.upload.prefer_mine.tooltip": "Add your waypoints",
  "wp.upload.prefer_mine.detail": "Conflicts take your version",
  "wp.upload.mirror": "Mirror",
  "wp.upload.mirror.tooltip": "Make the server match your map mod",
  "wp.upload.mirror.detail": "Server waypoints your map mod doesn''t have are removed",
  "wp.upload.scope_hint": "Uploads every dimension; add a dimension to limit it.",
  "wp.upload.requested": "Asking your map mod for its waypoints",
  "wp.upload.requested.mirror": "Server waypoints it doesn''t have will be removed.",
  "wp.upload.done": "Uploaded from {0}",
  "wp.upload.partial": "Upload from {0} stopped early",
  "wp.upload.nothing": "Nothing changed",
  "wp.upload.count.added": "{0} added",
  "wp.upload.count.added.tooltip": "New on the server",
  "wp.upload.count.replaced": "{0} replaced",
  "wp.upload.count.replaced.tooltip": "Your version replaced the server''s",
  "wp.upload.count.deleted": "{0} removed",
  "wp.upload.count.deleted.tooltip": "On the server but not in your map mod",
  "wp.upload.count.unchanged": "{0} unchanged",
  "wp.upload.count.unchanged.tooltip": "Already the same on the server",
  "wp.upload.count.conflicts.tooltip": "Same name, different properties",
  "wp.upload.count.skipped": "{0} skipped",
  "wp.upload.count.skipped.tooltip": "Invalid waypoint data",
  "wp.count.conflict.one": "{0} conflict",
  "wp.count.conflict.other": "{0} conflicts",
  "wp.upload.conflicts_kept": "{0} kept the server''s version.",
  "wp.upload.stale": "{0} changed meanwhile; not updated.",
  "wp.upload.try_again": "Try again",
  "wp.upload.try_again.tooltip": "Upload again with the same choices",
  "wp.download.sent": "Sent {0} to your map mod",
  "wp.error.upload.source": "{0} isn''t an upload source. Use xaero or voxelmap.",
  "wp.error.upload.no_mod": "Uploading needs Server Waypoint on your client.",
  "wp.error.upload.request": "That upload request was invalid or incomplete.",
  "wp.error.upload.expired": "The upload request expired.",
  "wp.error.upload.busy": "Another upload is already running.",
  "wp.error.upload.cooldown": "Wait {0} seconds before uploading again.",
  "wp.error.upload.permission": "You no longer have permission to upload waypoints.",
  "wp.error.upload.delete_permission": "You may no longer remove waypoints by uploading.",
  "wp.error.upload.xaero.missing": "Xaero''s Minimap isn''t installed on your client.",
  "wp.error.upload.xaero.not_ready": "Xaero''s Minimap isn''t ready yet. Try again in a moment.",
  "wp.error.upload.voxelmap.missing": "VoxelMap isn''t installed on your client.",
  "wp.error.upload.voxelmap.not_ready": "VoxelMap isn''t ready for those dimensions; visit them first.",
  "wp.error.upload.export": "Your client couldn''t export its map waypoints.",
  "wp.error.upload.apply": "The server couldn''t apply the upload.",
  "wp.error.upload.delivery": "Your client didn''t get the upload request.",
  "wp.error.upload.save": "Some uploaded waypoints couldn''t be saved to disk.",
  "wp.error.delivery": "The waypoints couldn''t be delivered to your client.",
  "wp.error.download.nothing": "There are no waypoints to download.",
  "wp.error.download.empty": "{0} has no waypoints to download."
```

`zh_cn.json`:

```json
  "wp.upload.title": "从你的地图模组上传",
  "wp.upload.source.xaero": "Xaero 的小地图",
  "wp.upload.source.voxelmap": "VoxelMap",
  "wp.upload.merge": "合并",
  "wp.upload.merge.tooltip": "添加你的路径点",
  "wp.upload.merge.detail": "冲突时保留服务器的版本",
  "wp.upload.prefer_mine": "以我为准",
  "wp.upload.prefer_mine.tooltip": "添加你的路径点",
  "wp.upload.prefer_mine.detail": "冲突时采用你的版本",
  "wp.upload.mirror": "镜像",
  "wp.upload.mirror.tooltip": "让服务器与你的地图模组一致",
  "wp.upload.mirror.detail": "你的地图模组中没有的服务器路径点会被删除",
  "wp.upload.scope_hint": "上传所有维度；加上维度可以限定范围。",
  "wp.upload.requested": "正在向你的地图模组请求路径点",
  "wp.upload.requested.mirror": "它没有的服务器路径点将被删除。",
  "wp.upload.done": "已从{0}上传",
  "wp.upload.partial": "从{0}上传时提前中止",
  "wp.upload.nothing": "没有任何改动",
  "wp.upload.count.added": "新增 {0}",
  "wp.upload.count.added.tooltip": "服务器上新增的",
  "wp.upload.count.replaced": "替换 {0}",
  "wp.upload.count.replaced.tooltip": "你的版本替换了服务器的",
  "wp.upload.count.deleted": "删除 {0}",
  "wp.upload.count.deleted.tooltip": "服务器上有但你的地图模组中没有",
  "wp.upload.count.unchanged": "未变 {0}",
  "wp.upload.count.unchanged.tooltip": "服务器上已经相同",
  "wp.upload.count.conflicts.tooltip": "名称相同，属性不同",
  "wp.upload.count.skipped": "跳过 {0}",
  "wp.upload.count.skipped.tooltip": "无效的路径点数据",
  "wp.count.conflict.one": "{0} 个冲突",
  "wp.count.conflict.other": "{0} 个冲突",
  "wp.upload.conflicts_kept": "{0}保留了服务器的版本。",
  "wp.upload.stale": "{0}在此期间发生了变化，未更新。",
  "wp.upload.try_again": "重试",
  "wp.upload.try_again.tooltip": "用相同的选项再次上传",
  "wp.download.sent": "已将{0}发送到你的地图模组",
  "wp.error.upload.source": "{0} 不是上传来源。请使用 xaero 或 voxelmap。",
  "wp.error.upload.no_mod": "上传需要客户端安装 Server Waypoint。",
  "wp.error.upload.request": "这个上传请求无效或不完整。",
  "wp.error.upload.expired": "上传请求已过期。",
  "wp.error.upload.busy": "已有另一个上传正在进行。",
  "wp.error.upload.cooldown": "请等待 {0} 秒后再上传。",
  "wp.error.upload.permission": "你已没有上传路径点的权限。",
  "wp.error.upload.delete_permission": "你已没有通过上传删除路径点的权限。",
  "wp.error.upload.xaero.missing": "你的客户端没有安装 Xaero 的小地图。",
  "wp.error.upload.xaero.not_ready": "Xaero 的小地图还没准备好，请稍后再试。",
  "wp.error.upload.voxelmap.missing": "你的客户端没有安装 VoxelMap。",
  "wp.error.upload.voxelmap.not_ready": "VoxelMap 还没为这些维度准备好；请先去这些维度。",
  "wp.error.upload.export": "你的客户端无法导出地图路径点。",
  "wp.error.upload.apply": "服务器无法应用这次上传。",
  "wp.error.upload.delivery": "你的客户端没有收到上传请求。",
  "wp.error.upload.save": "部分上传的路径点无法保存到磁盘。",
  "wp.error.delivery": "路径点无法发送到你的客户端。",
  "wp.error.download.nothing": "没有可以下载的路径点。",
  "wp.error.download.empty": "{0}没有可以下载的路径点。"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.upload.title": "從你的地圖模組上傳",
  "wp.upload.source.xaero": "Xaero 的小地圖",
  "wp.upload.source.voxelmap": "VoxelMap",
  "wp.upload.merge": "合併",
  "wp.upload.merge.tooltip": "新增你的路徑點",
  "wp.upload.merge.detail": "衝突時保留伺服器的版本",
  "wp.upload.prefer_mine": "以我為準",
  "wp.upload.prefer_mine.tooltip": "新增你的路徑點",
  "wp.upload.prefer_mine.detail": "衝突時採用你的版本",
  "wp.upload.mirror": "鏡像",
  "wp.upload.mirror.tooltip": "讓伺服器與你的地圖模組一致",
  "wp.upload.mirror.detail": "你的地圖模組中沒有的伺服器路徑點會被刪除",
  "wp.upload.scope_hint": "上傳所有維度；加上維度可以限定範圍。",
  "wp.upload.requested": "正在向你的地圖模組請求路徑點",
  "wp.upload.requested.mirror": "它沒有的伺服器路徑點將被刪除。",
  "wp.upload.done": "已從{0}上傳",
  "wp.upload.partial": "從{0}上傳時提前中止",
  "wp.upload.nothing": "沒有任何變更",
  "wp.upload.count.added": "新增 {0}",
  "wp.upload.count.added.tooltip": "伺服器上新增的",
  "wp.upload.count.replaced": "取代 {0}",
  "wp.upload.count.replaced.tooltip": "你的版本取代了伺服器的",
  "wp.upload.count.deleted": "刪除 {0}",
  "wp.upload.count.deleted.tooltip": "伺服器上有但你的地圖模組中沒有",
  "wp.upload.count.unchanged": "未變 {0}",
  "wp.upload.count.unchanged.tooltip": "伺服器上已經相同",
  "wp.upload.count.conflicts.tooltip": "名稱相同，屬性不同",
  "wp.upload.count.skipped": "略過 {0}",
  "wp.upload.count.skipped.tooltip": "無效的路徑點資料",
  "wp.count.conflict.one": "{0} 個衝突",
  "wp.count.conflict.other": "{0} 個衝突",
  "wp.upload.conflicts_kept": "{0}保留了伺服器的版本。",
  "wp.upload.stale": "{0}在此期間發生了變化，未更新。",
  "wp.upload.try_again": "重試",
  "wp.upload.try_again.tooltip": "用相同的選項再次上傳",
  "wp.download.sent": "已將{0}傳送到你的地圖模組",
  "wp.error.upload.source": "{0} 不是上傳來源。請使用 xaero 或 voxelmap。",
  "wp.error.upload.no_mod": "上傳需要客戶端安裝 Server Waypoint。",
  "wp.error.upload.request": "這個上傳請求無效或不完整。",
  "wp.error.upload.expired": "上傳請求已過期。",
  "wp.error.upload.busy": "已有另一個上傳正在進行。",
  "wp.error.upload.cooldown": "請等待 {0} 秒後再上傳。",
  "wp.error.upload.permission": "你已沒有上傳路徑點的權限。",
  "wp.error.upload.delete_permission": "你已沒有透過上傳刪除路徑點的權限。",
  "wp.error.upload.xaero.missing": "你的客戶端沒有安裝 Xaero 的小地圖。",
  "wp.error.upload.xaero.not_ready": "Xaero 的小地圖還沒準備好，請稍後再試。",
  "wp.error.upload.voxelmap.missing": "你的客戶端沒有安裝 VoxelMap。",
  "wp.error.upload.voxelmap.not_ready": "VoxelMap 還沒為這些維度準備好；請先去這些維度。",
  "wp.error.upload.export": "你的客戶端無法匯出地圖路徑點。",
  "wp.error.upload.apply": "伺服器無法套用這次上傳。",
  "wp.error.upload.delivery": "你的客戶端沒有收到上傳請求。",
  "wp.error.upload.save": "部分上傳的路徑點無法儲存到磁碟。",
  "wp.error.delivery": "路徑點無法傳送到你的客戶端。",
  "wp.error.download.nothing": "沒有可以下載的路徑點。",
  "wp.error.download.empty": "{0}沒有可以下載的路徑點。"
```

`es_es.json`:

```json
  "wp.upload.title": "Subir desde tu mod de mapas",
  "wp.upload.source.xaero": "Xaero''s Minimap",
  "wp.upload.source.voxelmap": "VoxelMap",
  "wp.upload.merge": "Combinar",
  "wp.upload.merge.tooltip": "Añade tus puntos",
  "wp.upload.merge.detail": "En los conflictos se mantiene la versión del servidor",
  "wp.upload.prefer_mine": "Preferir los míos",
  "wp.upload.prefer_mine.tooltip": "Añade tus puntos",
  "wp.upload.prefer_mine.detail": "En los conflictos se usa tu versión",
  "wp.upload.mirror": "Reflejar",
  "wp.upload.mirror.tooltip": "Haz que el servidor coincida con tu mod de mapas",
  "wp.upload.mirror.detail": "Se quitan los puntos del servidor que tu mod de mapas no tenga",
  "wp.upload.scope_hint": "Sube todas las dimensiones; añade una dimensión para limitarlo.",
  "wp.upload.requested": "Pidiendo los puntos a tu mod de mapas",
  "wp.upload.requested.mirror": "Se quitarán los puntos del servidor que no tenga.",
  "wp.upload.done": "Subido desde {0}",
  "wp.upload.partial": "La subida desde {0} se detuvo antes de tiempo",
  "wp.upload.nothing": "No ha cambiado nada",
  "wp.upload.count.added": "{0} añadidos",
  "wp.upload.count.added.tooltip": "Nuevos en el servidor",
  "wp.upload.count.replaced": "{0} reemplazados",
  "wp.upload.count.replaced.tooltip": "Tu versión reemplazó la del servidor",
  "wp.upload.count.deleted": "{0} quitados",
  "wp.upload.count.deleted.tooltip": "En el servidor pero no en tu mod de mapas",
  "wp.upload.count.unchanged": "{0} sin cambios",
  "wp.upload.count.unchanged.tooltip": "Ya eran iguales en el servidor",
  "wp.upload.count.conflicts.tooltip": "Mismo nombre, propiedades distintas",
  "wp.upload.count.skipped": "{0} omitidos",
  "wp.upload.count.skipped.tooltip": "Datos de punto no válidos",
  "wp.count.conflict.one": "{0} conflicto",
  "wp.count.conflict.other": "{0} conflictos",
  "wp.upload.conflicts_kept": "{0} mantuvieron la versión del servidor.",
  "wp.upload.stale": "{0} cambiaron mientras tanto; no se actualizaron.",
  "wp.upload.try_again": "Reintentar",
  "wp.upload.try_again.tooltip": "Volver a subir con las mismas opciones",
  "wp.download.sent": "Enviados {0} a tu mod de mapas",
  "wp.error.upload.source": "{0} no es una fuente de subida. Usa xaero o voxelmap.",
  "wp.error.upload.no_mod": "Para subir hace falta Server Waypoint en tu cliente.",
  "wp.error.upload.request": "Esa solicitud de subida no era válida o estaba incompleta.",
  "wp.error.upload.expired": "La solicitud de subida ha caducado.",
  "wp.error.upload.busy": "Ya hay otra subida en curso.",
  "wp.error.upload.cooldown": "Espera {0} segundos antes de volver a subir.",
  "wp.error.upload.permission": "Ya no tienes permiso para subir puntos.",
  "wp.error.upload.delete_permission": "Ya no tienes permiso para quitar puntos al subir.",
  "wp.error.upload.xaero.missing": "Xaero''s Minimap no está instalado en tu cliente.",
  "wp.error.upload.xaero.not_ready": "Xaero''s Minimap aún no está listo. Inténtalo en un momento.",
  "wp.error.upload.voxelmap.missing": "VoxelMap no está instalado en tu cliente.",
  "wp.error.upload.voxelmap.not_ready": "VoxelMap no está listo para esas dimensiones; visítalas primero.",
  "wp.error.upload.export": "Tu cliente no pudo exportar sus puntos del mapa.",
  "wp.error.upload.apply": "El servidor no pudo aplicar la subida.",
  "wp.error.upload.delivery": "Tu cliente no recibió la solicitud de subida.",
  "wp.error.upload.save": "Algunos puntos subidos no se pudieron guardar en el disco.",
  "wp.error.delivery": "No se pudieron entregar los puntos a tu cliente.",
  "wp.error.download.nothing": "No hay puntos que descargar.",
  "wp.error.download.empty": "{0} no tiene puntos que descargar."
```

`he_il.json`:

```json
  "wp.upload.title": "העלאה ממוד המפה שלכם",
  "wp.upload.source.xaero": "Xaero''s Minimap",
  "wp.upload.source.voxelmap": "VoxelMap",
  "wp.upload.merge": "מיזוג",
  "wp.upload.merge.tooltip": "הוספת הנקודות שלכם",
  "wp.upload.merge.detail": "בהתנגשות נשמרת הגרסה של השרת",
  "wp.upload.prefer_mine": "העדפת שלי",
  "wp.upload.prefer_mine.tooltip": "הוספת הנקודות שלכם",
  "wp.upload.prefer_mine.detail": "בהתנגשות נלקחת הגרסה שלכם",
  "wp.upload.mirror": "שיקוף",
  "wp.upload.mirror.tooltip": "התאמת השרת למוד המפה שלכם",
  "wp.upload.mirror.detail": "נקודות בשרת שאין במוד המפה שלכם יוסרו",
  "wp.upload.scope_hint": "מעלה את כל הממדים; הוסיפו ממד כדי להגביל.",
  "wp.upload.requested": "מבקשים את הנקודות ממוד המפה שלכם",
  "wp.upload.requested.mirror": "נקודות בשרת שאין לו יוסרו.",
  "wp.upload.done": "הועלה מ-{0}",
  "wp.upload.partial": "ההעלאה מ-{0} נעצרה מוקדם",
  "wp.upload.nothing": "שום דבר לא השתנה",
  "wp.upload.count.added": "{0} נוספו",
  "wp.upload.count.added.tooltip": "חדשות בשרת",
  "wp.upload.count.replaced": "{0} הוחלפו",
  "wp.upload.count.replaced.tooltip": "הגרסה שלכם החליפה את זו של השרת",
  "wp.upload.count.deleted": "{0} הוסרו",
  "wp.upload.count.deleted.tooltip": "בשרת אבל לא במוד המפה שלכם",
  "wp.upload.count.unchanged": "{0} ללא שינוי",
  "wp.upload.count.unchanged.tooltip": "כבר זהות בשרת",
  "wp.upload.count.conflicts.tooltip": "אותו שם, מאפיינים שונים",
  "wp.upload.count.skipped": "{0} דולגו",
  "wp.upload.count.skipped.tooltip": "נתוני נקודה לא תקינים",
  "wp.count.conflict.one": "התנגשות אחת",
  "wp.count.conflict.other": "{0} התנגשויות",
  "wp.upload.conflicts_kept": "{0} שמרו על הגרסה של השרת.",
  "wp.upload.stale": "{0} השתנו בינתיים; לא עודכנו.",
  "wp.upload.try_again": "ניסיון חוזר",
  "wp.upload.try_again.tooltip": "העלאה שוב עם אותן בחירות",
  "wp.download.sent": "{0} נשלחו למוד המפה שלכם",
  "wp.error.upload.source": "{0} אינו מקור העלאה. השתמשו ב-xaero או voxelmap.",
  "wp.error.upload.no_mod": "העלאה דורשת את Server Waypoint בלקוח שלכם.",
  "wp.error.upload.request": "בקשת ההעלאה לא הייתה תקינה או מלאה.",
  "wp.error.upload.expired": "תוקף בקשת ההעלאה פג.",
  "wp.error.upload.busy": "העלאה אחרת כבר מתבצעת.",
  "wp.error.upload.cooldown": "המתינו {0} שניות לפני העלאה נוספת.",
  "wp.error.upload.permission": "כבר אין לכם הרשאה להעלות נקודות.",
  "wp.error.upload.delete_permission": "כבר אין לכם הרשאה להסיר נקודות בהעלאה.",
  "wp.error.upload.xaero.missing": "Xaero''s Minimap לא מותקן בלקוח שלכם.",
  "wp.error.upload.xaero.not_ready": "Xaero''s Minimap עדיין לא מוכן. נסו שוב בעוד רגע.",
  "wp.error.upload.voxelmap.missing": "VoxelMap לא מותקן בלקוח שלכם.",
  "wp.error.upload.voxelmap.not_ready": "VoxelMap לא מוכן לממדים האלה; בקרו בהם קודם.",
  "wp.error.upload.export": "הלקוח שלכם לא הצליח לייצא את נקודות המפה.",
  "wp.error.upload.apply": "השרת לא הצליח להחיל את ההעלאה.",
  "wp.error.upload.delivery": "הלקוח שלכם לא קיבל את בקשת ההעלאה.",
  "wp.error.upload.save": "חלק מהנקודות שהועלו לא נשמרו בדיסק.",
  "wp.error.delivery": "לא ניתן היה למסור את הנקודות ללקוח שלכם.",
  "wp.error.download.nothing": "אין נקודות להורדה.",
  "wp.error.download.empty": "ב-{0} אין נקודות להורדה."
```

- [ ] **Step 2: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/UploadScreensTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadScreensTest {
    @Test
    void thePanelOffersEveryMapModWithItsThreeModes() {
        Component panel = UploadScreens.panel(Fixtures.player());

        assertEquals(List.of(
                "Upload from your map mod",
                "Xaero's Minimap  Merge · Prefer mine · Mirror",
                "VoxelMap  Merge · Prefer mine · Mirror",
                "Uploads every dimension; add a dimension to limit it."), lines(panel));
        assertEquals("/wp upload xaero", clickOf(panel, "Merge"));
        assertEquals(NamedTextColor.AQUA, colorOf(panel, "Merge"));
        assertEquals("/wp upload xaero force local", clickOf(panel, "Prefer mine"));
        assertEquals(NamedTextColor.YELLOW, colorOf(panel, "Prefer mine"));
        assertEquals("/wp upload xaero force local delete", clickOf(panel, "Mirror"));
        assertEquals(NamedTextColor.RED, colorOf(panel, "Mirror"));
        assertEquals("Make the server match your map mod\nServer waypoints your map mod doesn't have are removed\n"
                + "Press Enter to confirm", tooltipOf(panel, "Mirror"));
    }

    @Test
    void mirrorNeedsTheDeletePermission() {
        Viewer uploader = new Viewer(EnumSet.of(Viewer.Permission.UPLOAD), true, false, Fixtures.OVERWORLD,
                new WaypointPos(0, 64, 0), 0F);

        assertEquals("Xaero's Minimap  Merge · Prefer mine", lines(UploadScreens.panel(uploader)).get(1));
    }

    @Test
    void theOutcomeShowsTheNonZeroCountsAndOffersToPreferMine() {
        Component result = UploadScreens.result(new UploadScreens.Outcome(UploadTarget.XAERO, 3, 0, 0, 9, 2, 0, 0, false,
                false, "/wp upload xaero force local", "/wp upload xaero"));

        assertEquals(List.of(
                "✔ Uploaded from Xaero's Minimap",
                "3 added · 9 unchanged · 2 conflicts",
                "2 conflicts kept the server's version.  Prefer mine"), lines(result));
        assertEquals("/wp upload xaero force local", clickOf(result, "Prefer mine"));
        assertEquals("Same name, different properties", tooltipOf(result, "2 conflicts"));
    }

    @Test
    void aStoppedUploadSaysSoAndOffersToTryAgain() {
        Component result = UploadScreens.result(new UploadScreens.Outcome(UploadTarget.VOXELMAP, 1, 2, 3, 0, 0, 4, 2, true,
                true, null, "/wp upload voxelmap force local delete"));

        assertEquals(List.of(
                "✘ Upload from VoxelMap stopped early",
                "1 added · 2 replaced · 3 removed · 4 skipped",
                "2 dimensions changed meanwhile; not updated. Try again",
                "✘ Some uploaded waypoints couldn't be saved to disk."), lines(result));
        assertEquals("/wp upload voxelmap force local delete", clickOf(result, "Try again"));
        assertEquals(List.of("✔ Uploaded from VoxelMap", "Nothing changed"), lines(UploadScreens.result(
                new UploadScreens.Outcome(UploadTarget.VOXELMAP, 0, 0, 0, 0, 0, 0, 0, false, false, null, "/wp upload voxelmap"))));
    }

    @Test
    void requestsAndDownloadsAreOneLineEach() {
        assertEquals("Asking your map mod for its waypoints…", render(UploadScreens.requested(false)));
        assertEquals(List.of("Asking your map mod for its waypoints…", "Server waypoints it doesn't have will be removed."),
                lines(UploadScreens.requested(true)));
        assertEquals("✔ Sent 12 waypoints to your map mod", render(Results.sent(12)));
        assertEquals("✔ Sent 1 waypoint to your map mod", render(Results.sent(1)));
    }
}
```

- [ ] **Step 3: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.UploadScreensTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable UploadScreens`.

- [ ] **Step 4: Write `UploadScreens` and `Results.sent`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/UploadScreens.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The upload panel and the outcome of an upload (spec 11). */
public final class UploadScreens {
    /** Whoever uploads is a player whose client has the mod. */
    private static final Viewer UPLOADER = new Viewer(Set.of(), true, false, null, null, 0F);

    /** What an upload changed, and the commands that follow up on it. */
    public record Outcome(UploadTarget source, int added, int replaced, int deleted, int unchanged, int conflicts,
                          int skipped, int staleDimensions, boolean saveFailed, boolean stoppedEarly,
                          @Nullable String preferMine, String retry) {
    }

    private UploadScreens() {
    }

    /** /wp upload: every map mod with Merge, Prefer mine and (with the delete permission) Mirror. */
    public static Component panel(Viewer viewer) {
        ChatLines lines = new ChatLines().add(translatable("wp.upload.title", GOLD));
        for (UploadTarget target : UploadTarget.values()) {
            String upload = "/wp upload " + target.name().toLowerCase(Locale.ROOT);
            lines.line(Chat.colored(sourceName(target), WHITE), text("  "), Chat.join(
                    Chat.link(viewer, translatable("wp.upload.merge"), AQUA, Click.run(upload),
                            Tooltip.of("wp.upload.merge.tooltip").line("wp.upload.merge.detail")),
                    Chat.link(viewer, translatable("wp.upload.prefer_mine"), YELLOW, Click.suggest(upload + " force local"),
                            Tooltip.of("wp.upload.prefer_mine.tooltip").line("wp.upload.prefer_mine.detail")
                                    .hint("wp.hint.confirm")),
                    viewer.can(Viewer.Permission.UPLOAD_DELETE)
                            ? Chat.link(viewer, translatable("wp.upload.mirror"), RED,
                            Click.suggest(upload + " force local delete"),
                            Tooltip.of("wp.upload.mirror.tooltip").line("wp.upload.mirror.detail").hint("wp.hint.confirm"))
                            : null));
        }
        return lines.add(translatable("wp.upload.scope_hint", GRAY)).build();
    }

    /** Asking your map mod for its waypoints… and, when mirroring, what will be removed. */
    public static Component requested(boolean mirror) {
        return new ChatLines()
                .add(Chat.colored(Chat.concat(translatable("wp.upload.requested"), text(Chat.ELLIPSIS)), GRAY))
                .add(mirror ? translatable("wp.upload.requested.mirror", GRAY) : null)
                .build();
    }

    /** ✔ Uploaded from Xaero's Minimap, the non-zero counts, then what to do about conflicts and stale dimensions. */
    public static Component result(Outcome outcome) {
        Component source = sourceName(outcome.source());
        ChatLines lines = new ChatLines().add(outcome.stoppedEarly()
                ? Chat.error(translatable("wp.upload.partial", source))
                : Chat.ok(translatable("wp.upload.done", source)));
        List<Component> counts = new ArrayList<>();
        count(counts, outcome.added(), translatable("wp.upload.count.added", text(outcome.added())), "added");
        count(counts, outcome.replaced(), translatable("wp.upload.count.replaced", text(outcome.replaced())), "replaced");
        count(counts, outcome.deleted(), translatable("wp.upload.count.deleted", text(outcome.deleted())), "deleted");
        count(counts, outcome.unchanged(), translatable("wp.upload.count.unchanged", text(outcome.unchanged())), "unchanged");
        count(counts, outcome.conflicts(), Chat.count("wp.count.conflict", outcome.conflicts()), "conflicts");
        count(counts, outcome.skipped(), translatable("wp.upload.count.skipped", text(outcome.skipped())), "skipped");
        lines.add(counts.isEmpty() ? translatable("wp.upload.nothing", GRAY) : Chat.colored(Chat.join(counts), GRAY));
        if (outcome.conflicts() > 0 && outcome.preferMine() != null) {
            lines.line(translatable("wp.upload.conflicts_kept", GRAY, Chat.count("wp.count.conflict", outcome.conflicts())),
                    text("  "), Chat.link(UPLOADER, translatable("wp.upload.prefer_mine"), YELLOW,
                            Click.suggest(outcome.preferMine()),
                            Tooltip.of("wp.upload.prefer_mine.tooltip").line("wp.upload.prefer_mine.detail")
                                    .hint("wp.hint.confirm")));
        }
        if (outcome.staleDimensions() > 0) {
            lines.line(translatable("wp.upload.stale", GRAY, Chat.count("wp.count.dimension", outcome.staleDimensions())),
                    text(" "), Chat.link(UPLOADER, translatable("wp.upload.try_again"), AQUA, Click.suggest(outcome.retry()),
                            Tooltip.of("wp.upload.try_again.tooltip").hint("wp.hint.confirm")));
        }
        if (outcome.saveFailed()) {
            lines.add(Errors.of("wp.error.upload.save"));
        }
        return lines.build();
    }

    public static Component sourceName(UploadTarget target) {
        return translatable("wp.upload.source." + target.name().toLowerCase(Locale.ROOT));
    }

    private static void count(List<Component> counts, int count, Component label, String kind) {
        if (count > 0) {
            counts.add(Chat.hover(UPLOADER, label, Tooltip.of("wp.upload.count." + kind + ".tooltip")));
        }
    }
}
```

In `common/src/main/java/_959/server_waypoint/text/feedback/Results.java`, replace:

```java
    public static Component keyGenerated(String publicKey) {
```

with:

```java
    /** ✔ Sent 12 waypoints to your map mod */
    public static Component sent(int waypoints) {
        return Chat.ok(translatable("wp.download.sent", Chat.count("wp.count.waypoint", waypoints)));
    }

    public static Component keyGenerated(String publicKey) {
```

- [ ] **Step 5: Run the builder tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 6: One upload command builder for every mode**

In `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`, replace the whole of `uploadLocalCmd`:

```java
    public static String uploadLocalCmd(UploadScope scope, UploadRequestBuffer request) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(UPLOAD_COMMAND)
                .append(' ').append(request.target().name().toLowerCase(Locale.ROOT))
                .append(" force local");
        if (scope == UploadScope.WORLD) {
```

through its closing brace with:

```java
    /** /wp upload <source> [force local [delete]] [<dimension> [<list> [<waypoint>]]] for this request's scope. */
    public static String uploadCmd(UploadScope scope, UploadRequestBuffer request, UploadConflictPolicy conflictPolicy,
                                   boolean deleteMissing) {
        StringBuilder command = new StringBuilder(WAYPOINT_COMMAND_WITH_SLASH)
                .append(' ').append(UPLOAD_COMMAND)
                .append(' ').append(request.target().name().toLowerCase(Locale.ROOT));
        if (conflictPolicy == UploadConflictPolicy.LOCAL) {
            command.append(" force local");
            if (deleteMissing) {
                command.append(" delete");
            }
        }
        if (scope == UploadScope.WORLD) {
            return command.toString();
        }
        command.append(' ').append(request.dimensionNames().get(0));
        if (scope == UploadScope.DIMENSION) {
            return command.toString();
        }
        command.append(' ').append(escapeArgument(request.listName()));
        if (scope == UploadScope.LIST) {
            return command.toString();
        }
        return command.append(' ').append(escapeArgument(request.waypointName())).toString();
    }
```

and add `import _959.server_waypoint.core.network.upload.UploadConflictPolicy;` after `import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;`.

In `common/src/main/java/_959/server_waypoint/text/TextButtonBuilder.java`, delete `uploadPreferLocalButton` and the imports only it used (`UploadScope`, `UploadRequestBuffer`, the static `uploadLocalCmd`).

In `common/src/test/java/_959/server_waypoint/util/StringCommandBuilderTest.java`, replace:

```java
        assertEquals(
                "/wp upload voxelmap force local minecraft:overworld \"Local list\"",
                StringCommandBuilder.uploadLocalCmd(UploadScope.LIST, request)
        );
```

with:

```java
        assertEquals(
                "/wp upload voxelmap force local minecraft:overworld \"Local list\"",
                StringCommandBuilder.uploadCmd(UploadScope.LIST, request, UploadConflictPolicy.LOCAL, false)
        );
        assertEquals(
                "/wp upload voxelmap force local delete minecraft:overworld \"Local list\"",
                StringCommandBuilder.uploadCmd(UploadScope.LIST, request, UploadConflictPolicy.LOCAL, true)
        );
        assertEquals("/wp upload voxelmap",
                StringCommandBuilder.uploadCmd(UploadScope.WORLD, request, UploadConflictPolicy.SERVER, false));
```

and add `import _959.server_waypoint.core.network.upload.UploadConflictPolicy;` to its imports.

- [ ] **Step 7: Report uploads with the new messages**

In `common/src/main/java/_959/server_waypoint/core/network/upload/UploadCoordinator.java`, replace:

```java
import _959.server_waypoint.text.TextButtonBuilder;
```

with:

```java
import _959.server_waypoint.text.feedback.Errors;
import _959.server_waypoint.text.feedback.UploadScreens;
import _959.server_waypoint.util.StringCommandBuilder;
```

In `onUpload`, make these replacements (each appears once unless noted):

- `this.playerMessageSender.send(player, translatable("waypoint.upload.request.invalid"));` (three times) becomes `this.playerMessageSender.send(player, Errors.of("wp.error.upload.request"));`
- `translatable("waypoint.upload.request.expired")` becomes `Errors.of("wp.error.upload.expired")`
- `translatable("waypoint.upload.permission.revoked")` becomes `Errors.of("wp.error.upload.permission")`
- `translatable("waypoint.upload.delete.permission.revoked")` becomes `Errors.of("wp.error.upload.delete_permission")`

Replace the status switch:

```java
                this.playerMessageSender.send(player, switch (upload.status()) {
                    case XAERO_NOT_INSTALLED -> translatable("waypoint.upload.xaero.missing");
                    case XAERO_NOT_READY -> translatable("waypoint.upload.xaero.not-ready");
                    case VOXELMAP_NOT_INSTALLED -> translatable("waypoint.upload.voxelmap.missing");
                    case VOXELMAP_NOT_READY -> translatable("waypoint.upload.voxelmap.not-ready");
                    case FAILED -> translatable("waypoint.upload.client.failed");
                    case SUCCESS -> throw new IllegalStateException("Handled above");
                });
```

with:

```java
                this.playerMessageSender.send(player, switch (upload.status()) {
                    case XAERO_NOT_INSTALLED -> Errors.of("wp.error.upload.xaero.missing");
                    case XAERO_NOT_READY -> Errors.of("wp.error.upload.xaero.not_ready");
                    case VOXELMAP_NOT_INSTALLED -> Errors.of("wp.error.upload.voxelmap.missing");
                    case VOXELMAP_NOT_READY -> Errors.of("wp.error.upload.voxelmap.not_ready");
                    case FAILED -> Errors.of("wp.error.upload.export");
                    case SUCCESS -> throw new IllegalStateException("Handled above");
                });
```

replace:

```java
                failure = translatable("waypoint.network.encoding_failed");
            } catch (RuntimeException exception) {
                WaypointServerCore.LOGGER.warn("Failed to apply waypoint upload", exception);
                failure = translatable("waypoint.upload.client.failed");
            }
```

with:

```java
                failure = Errors.of("wp.error.encoding");
            } catch (RuntimeException exception) {
                WaypointServerCore.LOGGER.warn("Failed to apply waypoint upload", exception);
                failure = Errors.of("wp.error.upload.apply");
            }
```

and replace the summary messages:

```java
            this.playerMessageSender.send(player, translatable(
                    failure == null ? "waypoint.upload.complete" : "waypoint.upload.partial",
                    text(summary.added), text(summary.replaced), text(summary.deleted),
                    text(summary.unchanged), text(summary.conflicts), text(summary.skipped)
            ));
            this.playerMessageSender.send(player, translatable("waypoint.upload.legend"));
            if (summary.conflicts > 0 && pending.conflictPolicy == UploadConflictPolicy.SERVER) {
                this.playerMessageSender.send(player, translatable(
                        "waypoint.upload.conflicts.server-kept",
                        text(summary.conflicts),
                        TextButtonBuilder.uploadPreferLocalButton(pending.scope, pending.request)
                ));
            }
            if (summary.staleDimensions > 0) {
                this.playerMessageSender.send(player, translatable(
                        "waypoint.upload.request.stale",
                        text(summary.staleDimensions)
                ));
            }
            if (summary.saveFailed) {
                this.playerMessageSender.send(player, translatable("waypoint.upload.save.failed"));
            }
```

with:

```java
            this.playerMessageSender.send(player, UploadScreens.result(new UploadScreens.Outcome(
                    pending.request.target(), summary.added, summary.replaced, summary.deleted, summary.unchanged,
                    summary.conflicts, summary.skipped, summary.staleDimensions, summary.saveFailed, failure != null,
                    pending.conflictPolicy == UploadConflictPolicy.SERVER
                            ? StringCommandBuilder.uploadCmd(pending.scope, pending.request, UploadConflictPolicy.LOCAL, false)
                            : null,
                    StringCommandBuilder.uploadCmd(pending.scope, pending.request, pending.conflictPolicy,
                            pending.deleteMissing))));
```

Then remove the static imports of `text` and `translatable` if nothing else in the file uses them.

In `common/src/test/java/_959/server_waypoint/core/network/upload/UploadCoordinatorTest.java`, both feedback collectors read the top-level key. Replace each:

```java
                (player, message) -> feedback.add(((net.kyori.adventure.text.TranslatableComponent) message).key()),
```

with:

```java
                (player, message) -> feedback.add(firstKey(message)),
```

add this helper before `private static SimpleWaypoint waypoint(String name, int x) {`:

```java
    /** The first wp. translation key of a message, depth first: its result or error line. */
    private static String firstKey(net.kyori.adventure.text.Component message) {
        if (message instanceof net.kyori.adventure.text.TranslatableComponent translatable
                && translatable.key().startsWith("wp.")) {
            return translatable.key();
        }
        for (net.kyori.adventure.text.Component child : message.children()) {
            String key = firstKey(child);
            if (!key.isEmpty()) {
                return key;
            }
        }
        return "";
    }

```

and change the expected keys: `"waypoint.network.encoding_failed"` becomes `"wp.error.encoding"`, `"waypoint.upload.partial"` becomes `"wp.upload.partial"` (twice), and `"waypoint.upload.complete"` becomes `"wp.upload.done"` (twice).

- [ ] **Step 8: The panel, the upload errors and the download result in the command**

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import _959.server_waypoint.text.feedback.NavigationScreens;
```

with:

```java
import _959.server_waypoint.text.feedback.NavigationScreens;
import _959.server_waypoint.text.feedback.UploadScreens;
```

In `uploadCommandNode`, replace:

```java
        LiteralArgumentBuilder<S> upload = literal(UPLOAD_COMMAND);
        upload.requires(this::hasUploadPermission);
```

with:

```java
        LiteralArgumentBuilder<S> upload = literal(UPLOAD_COMMAND);
        upload.requires(this::hasUploadPermission);
        upload.executes(context -> executeUploadPanel(context.getSource()));
```

and add after `executeUploadAndReturn`:

```java
    /** /wp upload: the map mods and their modes, for a player whose client has the mod. */
    private int executeUploadPanel(S source) {
        if (getPlayer(source) == null) {
            this.sender.sendError(source, Errors.playerOnly());
            return 0;
        }
        Viewer viewer = viewer(source);
        if (!viewer.hasMod()) {
            this.sender.sendError(source, Errors.of("wp.error.upload.no_mod"));
            return 0;
        }
        this.sender.sendMessage(source, UploadScreens.panel(viewer));
        return Command.SINGLE_SUCCESS;
    }
```

In `executeUpload`, replace:

```java
            this.sender.sendError(
                    source,
                    translatable("waypoint.upload.source.invalid", text(uploadSource))
            );
```

with:

```java
            this.sender.sendError(source, Errors.of("wp.error.upload.source", text(uploadSource)));
```

replace `translatable("waypoint.upload.player-only")` with `Errors.playerOnly()`, `translatable("waypoint.upload.client.incompatible")` with `Errors.of("wp.error.upload.no_mod")`, `translatable("waypoint.upload.request.invalid")` with `Errors.of("wp.error.upload.request")` and `translatable("waypoint.upload.busy")` with `Errors.of("wp.error.upload.busy")`; replace:

```java
            this.sender.sendError(source, translatable(
                    "waypoint.upload.cooldown",
                    text(remainingSeconds)
            ));
```

with:

```java
            this.sender.sendError(source, Errors.of("wp.error.upload.cooldown", text(remainingSeconds)));
```

replace:

```java
                this.sender.sendPlayerMessage(
                        player,
                        translatable("waypoint.upload.request.delivery_failed")
                );
```

with:

```java
                this.sender.sendPlayerMessage(player, Errors.of("wp.error.upload.delivery"));
```

and replace:

```java
        this.sender.sendMessage(source, translatable(deleteMissing
                ? "waypoint.upload.requested.force-delete"
                : "waypoint.upload.requested"));
```

with:

```java
        this.sender.sendMessage(source, UploadScreens.requested(deleteMissing));
```

Replace the four `executeDownload` methods and `sendDownload` (from `private void executeDownload(S source) {` through the end of `sendDownload`) with:

```java
    private void executeDownload(S source) {
        WaypointData waypointData = this.waypointServer.toWorldWaypointData();
        if (waypointData == null) {
            this.sender.sendError(source, Errors.of("wp.error.download.nothing"));
            return;
        }
        this.sendDownload(source, waypointData, waypointCount(waypointData));
    }

    private void executeDownload(S source, D dimensionArgument) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            String dimensionName = fileManager.getDimensionName();
            if (fileManager.hasNoWaypoints()) {
                this.sender.sendError(source, Errors.of("wp.error.download.empty", dimensions(source).name(dimensionName)));
                return;
            }
            WaypointData waypointData = WaypointData.dimension(fileManager.toDimensionWaypointData());
            this.sendDownload(source, waypointData, waypointCount(waypointData));
        });
    }

    private void executeDownload(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) -> this.sendDownload(source,
                        WaypointData.waypointList(fileManager.getDimensionName(), waypointList), waypointList.size()),
                (fileManager, waypointList) -> this.sender.sendError(source, Errors.of("wp.error.download.empty",
                        WaypointRefs.label(waypointList.displayName(), waypointList.name()))));
    }

    private void executeDownload(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) ->
                this.sendDownload(source, new WaypointModificationMessage(
                        fileManager.getDimensionName(),
                        listName,
                        waypointList.displayName(),
                        name,
                        waypoint,
                        WaypointModificationType.ADD,
                        waypointList.getSyncNum()
                ), 1));
    }

    private static int waypointCount(WaypointData waypointData) {
        return waypointData.dimensions().stream()
                .flatMap(dimension -> dimension.waypointLists().stream())
                .mapToInt(WaypointList::size)
                .sum();
    }

    /** ✔ Sent 12 waypoints to your map mod, once the client has received every chunk. */
    private void sendDownload(S source, ChunkedMessage message, int waypoints) {
        _959.server_waypoint.core.network.ChunkedMessageDelivery delivery =
                this.sender.sendChunkedMessage(source, message);
        if (!delivery.queued()) {
            this.sender.sendError(source, Errors.of("wp.error.delivery"));
            return;
        }
        delivery.completion().whenComplete((result, exception) -> {
            if (exception == null && result != null && result.delivered()) {
                this.sender.sendMessage(source, Results.sent(waypoints));
            } else {
                this.sender.sendError(source, Errors.of("wp.error.delivery"));
            }
        });
    }
```

and add `import _959.server_waypoint.text.feedback.WaypointRefs;` next to the other `text.feedback` imports.

- [ ] **Step 9: Move the old command tests to the new keys**

In `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandListTest.java`:

- `contains("waypoint.download.all")` becomes `contains("wp.download.sent")`.
- `.contains("waypoint.network.delivery_failed")` becomes `.contains("wp.error.delivery")`.
- `.contains("waypoint.upload.client.incompatible")` becomes `.contains("wp.error.upload.no_mod")`.
- `.contains("waypoint.upload.source.invalid")` becomes `.contains("wp.error.upload.source")`.
- In `uploadRequiresSourceAndSuggestsSupportedTargets`, delete the block that expects `/wp upload` to fail, since it now opens the panel:

  ```java
        assertThrows(
                CommandSyntaxException.class,
                () -> this.dispatcher.execute("wp upload", this.source)
        );
  ```

  and rename the test to `uploadSuggestsSupportedTargetsAndRejectsOthers`.

- [ ] **Step 10: Add the command test**

In `CommandFeedbackTest.java`, replace:

```java
    @Test
    void playersMessagesEndWithOneNewline() {
```

with:

```java
    @Test
    void uploadWithoutArgumentsOpensTheUploadPanelForPlayersWithTheMod() {
        assertEquals("Upload from your map mod", lines(this.harness.run(CommandHarness.player(), "wp upload")).get(0));
        assertEquals("Xaero's Minimap  Merge · Prefer mine",
                lines(this.harness.run(CommandHarness.player().withPermissions("upload"), "wp upload")).get(1));
        assertEquals("✘ Only players can do that.", render(this.harness.run(CommandHarness.console(), "wp upload")));
        this.harness.sender.handshake = false;
        assertEquals("✘ Uploading needs Server Waypoint on your client.",
                render(this.harness.run(CommandHarness.player(), "wp upload")));
    }

    @Test
    void playersMessagesEndWithOneNewline() {
```

- [ ] **Step 11: Run the tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 12: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java common/src/test/java common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Add the upload panel and report uploads and downloads as results"
```

---

### Task 16: Remote browsing

Spec 14 and 16: remote browsing works like local lists with one more level. `/wp remote` becomes the server picker (`/wp remote page <n>` pages it), `/wp remote list` shows all servers, a server, a dimension (with the local Tree, Lists and Flat views) or a list, and `/wp remote details` shows a waypoint read-only. `/wp remote servers` and list details are removed, and remote list options gain `view lists`. A coloured dot shows each server's state. Remote labels stay literal text of at most 256 characters, so a name from another server can never carry its own clicks. `RemoteCatalogQuery` stops paging rows: it captures the cache as servers holding local-style lists, the local query engine searches and sorts them, and the screens page them with the local budgets. Teleport messages stay as they are until Task 17.

**Files:**
- Rewrite: `common/src/main/java/_959/server_waypoint/crossserver/catalog/RemoteCatalogQuery.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/RemoteRefs.java`
- Create: `common/src/main/java/_959/server_waypoint/text/feedback/RemoteScreens.java`
- Rewrite: `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointQueryEngine.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/RemoteScreensTest.java`
- Rewrite (test): `common/src/test/java/_959/server_waypoint/command/RemoteWaypointCommandTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: the kit, `ListScreen.Totals`, `ListScreen.resolveView`, `treePages`, `COLLAPSE_ABOVE`, `PREVIEW` (Task 9), `PickerScreens.colorValue`, `yawValue` (Task 11), `Errors.of` (Task 13), `DimensionStyle.remote` (Task 4), `ListTarget.remote` (Task 5).
- Produces:
  - `RemoteCatalogQuery.Server(RemoteServerId id, String displayName, RemoteCatalogState state, Map<String, List<WaypointList>> dimensions)` with `readable()`, `available()`, `lists(String)`, `list(String, String)`, `listCount()`, `waypointCount()`; `static List<Server> servers(Map<RemoteServerId, CatalogReceiver.View>)`, `static Optional<Server> server(Map<…>, String id)`.
  - `WaypointQueryEngine.queryLists(Map<String, List<WaypointList>> dimensions, Query)`.
  - `RemoteRefs`: `label(String display, String identity)`, `label(Viewer, …)`, `serverName(Viewer, Server)`, `dot(Viewer, Server)`, `detail(Viewer, Server)`, `serverLink(Viewer, Server)`, `serverTooltip(Server, @Nullable String hint)`, `reference`, `title`, `initials`, `row`, `waypointTooltip`, `teleportTooltip`, `listLink`, `listTooltip`, `command(String action, Server, String dimension, WaypointList, SimpleWaypoint)`.
  - `RemoteScreens`: `picker(Viewer, List<Server>, int page, int pageLimit)`, `allServers(Viewer, List<Server>, ListQuery, int pageLimit)`, `server(Viewer, Server, ListQuery, int pageLimit)`, `search(Viewer, @Nullable Server only, List<Server>, ListQuery, int pageLimit)`, `dimension(Viewer, Server, String dimension, ListQuery, int pageLimit)`, `list(Viewer, Server, String dimension, WaypointList, ListQuery, int pageLimit)`, `details(Viewer, Server, String dimension, WaypointList, SimpleWaypoint)`, errors `noServer`, `noDimension`, `noList`, `noWaypoint`, `distanceUnavailable()`.
  - `RemoteWaypointCommand` gains a last constructor argument `Function<S, Viewer> viewer`.
- Keys: `wp.remote.*`, `wp.hint.choose_server`, `wp.error.remote.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.remote.title": "Remote servers",
  "wp.remote.connected": "{0} connected",
  "wp.remote.none": "No remote servers are connected yet.",
  "wp.remote.all": "All servers",
  "wp.remote.all.tooltip": "Every server''s dimensions",
  "wp.remote.choose": "Choose a server",
  "wp.hint.choose_server": "Click to choose a server",
  "wp.remote.servers": "Servers",
  "wp.remote.browse": "Browse",
  "wp.remote.browse.tooltip": "See what this server publishes",
  "wp.remote.unreachable": "{0} can''t be reached right now.",
  "wp.remote.no_access": "You don''t have access to {0}.",
  "wp.remote.nothing": "Nothing published yet.",
  "wp.remote.state.available": "Available",
  "wp.remote.state.stale": "Stale",
  "wp.remote.state.stale.detail": "Teleporting is off until it refreshes",
  "wp.remote.state.unreachable": "Unreachable",
  "wp.remote.state.no_access": "No access",
  "wp.remote.status.stale": "stale",
  "wp.remote.status.unreachable": "unreachable",
  "wp.remote.status.no_access": "no access",
  "wp.remote.status.empty": "nothing published",
  "wp.remote.on": "On {0} in {1}",
  "wp.remote.teleport.switches": "Switches you to the {0} server",
  "wp.remote.teleport.stale": "Off while {0} is stale",
  "wp.remote.search.everywhere": "Search every server",
  "wp.remote.search.server": "Search {0}",
  "wp.error.remote.no_server": "No server called {0}.",
  "wp.error.remote.no_dimension": "{0} has no dimension {1}.",
  "wp.error.remote.no_list": "{0} has no list {1} in {2}.",
  "wp.error.remote.distance": "Remote waypoints can''t be sorted by distance."
```

`zh_cn.json`:

```json
  "wp.remote.title": "远程服务器",
  "wp.remote.connected": "已连接 {0}",
  "wp.remote.none": "还没有连接远程服务器。",
  "wp.remote.all": "所有服务器",
  "wp.remote.all.tooltip": "每个服务器的维度",
  "wp.remote.choose": "选择服务器",
  "wp.hint.choose_server": "点击选择服务器",
  "wp.remote.servers": "服务器",
  "wp.remote.browse": "浏览",
  "wp.remote.browse.tooltip": "查看这个服务器发布的内容",
  "wp.remote.unreachable": "{0}现在无法连接。",
  "wp.remote.no_access": "你没有{0}的访问权限。",
  "wp.remote.nothing": "还没有发布任何内容。",
  "wp.remote.state.available": "可用",
  "wp.remote.state.stale": "已过期",
  "wp.remote.state.stale.detail": "刷新前无法传送",
  "wp.remote.state.unreachable": "无法连接",
  "wp.remote.state.no_access": "无权访问",
  "wp.remote.status.stale": "已过期",
  "wp.remote.status.unreachable": "无法连接",
  "wp.remote.status.no_access": "无权访问",
  "wp.remote.status.empty": "未发布内容",
  "wp.remote.on": "位于{0}的{1}",
  "wp.remote.teleport.switches": "会将你切换到{0}服务器",
  "wp.remote.teleport.stale": "{0}过期期间不可用",
  "wp.remote.search.everywhere": "搜索所有服务器",
  "wp.remote.search.server": "搜索{0}",
  "wp.error.remote.no_server": "没有名为 {0} 的服务器。",
  "wp.error.remote.no_dimension": "{0}没有维度 {1}。",
  "wp.error.remote.no_list": "{0}的{2}中没有列表 {1}。",
  "wp.error.remote.distance": "远程路径点无法按距离排序。"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.remote.title": "遠端伺服器",
  "wp.remote.connected": "已連線 {0}",
  "wp.remote.none": "還沒有連線遠端伺服器。",
  "wp.remote.all": "所有伺服器",
  "wp.remote.all.tooltip": "每個伺服器的維度",
  "wp.remote.choose": "選擇伺服器",
  "wp.hint.choose_server": "點擊選擇伺服器",
  "wp.remote.servers": "伺服器",
  "wp.remote.browse": "瀏覽",
  "wp.remote.browse.tooltip": "查看這個伺服器發佈的內容",
  "wp.remote.unreachable": "{0}現在無法連線。",
  "wp.remote.no_access": "你沒有{0}的存取權限。",
  "wp.remote.nothing": "還沒有發佈任何內容。",
  "wp.remote.state.available": "可用",
  "wp.remote.state.stale": "已過期",
  "wp.remote.state.stale.detail": "重新整理前無法傳送",
  "wp.remote.state.unreachable": "無法連線",
  "wp.remote.state.no_access": "無權存取",
  "wp.remote.status.stale": "已過期",
  "wp.remote.status.unreachable": "無法連線",
  "wp.remote.status.no_access": "無權存取",
  "wp.remote.status.empty": "未發佈內容",
  "wp.remote.on": "位於{0}的{1}",
  "wp.remote.teleport.switches": "會將你切換到{0}伺服器",
  "wp.remote.teleport.stale": "{0}過期期間無法使用",
  "wp.remote.search.everywhere": "搜尋所有伺服器",
  "wp.remote.search.server": "搜尋{0}",
  "wp.error.remote.no_server": "沒有名為 {0} 的伺服器。",
  "wp.error.remote.no_dimension": "{0}沒有維度 {1}。",
  "wp.error.remote.no_list": "{0}的{2}中沒有列表 {1}。",
  "wp.error.remote.distance": "遠端路徑點無法依距離排序。"
```

`es_es.json`:

```json
  "wp.remote.title": "Servidores remotos",
  "wp.remote.connected": "{0} conectados",
  "wp.remote.none": "Aún no hay servidores remotos conectados.",
  "wp.remote.all": "Todos los servidores",
  "wp.remote.all.tooltip": "Las dimensiones de cada servidor",
  "wp.remote.choose": "Elige un servidor",
  "wp.hint.choose_server": "Haz clic para elegir un servidor",
  "wp.remote.servers": "Servidores",
  "wp.remote.browse": "Explorar",
  "wp.remote.browse.tooltip": "Mira lo que publica este servidor",
  "wp.remote.unreachable": "No se puede conectar con {0} ahora mismo.",
  "wp.remote.no_access": "No tienes acceso a {0}.",
  "wp.remote.nothing": "Aún no hay nada publicado.",
  "wp.remote.state.available": "Disponible",
  "wp.remote.state.stale": "Desactualizado",
  "wp.remote.state.stale.detail": "El teletransporte está desactivado hasta que se actualice",
  "wp.remote.state.unreachable": "Inaccesible",
  "wp.remote.state.no_access": "Sin acceso",
  "wp.remote.status.stale": "desactualizado",
  "wp.remote.status.unreachable": "inaccesible",
  "wp.remote.status.no_access": "sin acceso",
  "wp.remote.status.empty": "nada publicado",
  "wp.remote.on": "En {0}, {1}",
  "wp.remote.teleport.switches": "Te lleva al servidor {0}",
  "wp.remote.teleport.stale": "Desactivado mientras {0} esté desactualizado",
  "wp.remote.search.everywhere": "Buscar en todos los servidores",
  "wp.remote.search.server": "Buscar en {0}",
  "wp.error.remote.no_server": "No hay ningún servidor llamado {0}.",
  "wp.error.remote.no_dimension": "{0} no tiene la dimensión {1}.",
  "wp.error.remote.no_list": "{0} no tiene la lista {1} en {2}.",
  "wp.error.remote.distance": "Los puntos remotos no se pueden ordenar por distancia."
```

`he_il.json`:

```json
  "wp.remote.title": "שרתים מרוחקים",
  "wp.remote.connected": "{0} מחוברים",
  "wp.remote.none": "עדיין אין שרתים מרוחקים מחוברים.",
  "wp.remote.all": "כל השרתים",
  "wp.remote.all.tooltip": "הממדים של כל שרת",
  "wp.remote.choose": "בחירת שרת",
  "wp.hint.choose_server": "לחצו לבחירת שרת",
  "wp.remote.servers": "שרתים",
  "wp.remote.browse": "עיון",
  "wp.remote.browse.tooltip": "מה השרת הזה מפרסם",
  "wp.remote.unreachable": "אי אפשר להתחבר ל-{0} כרגע.",
  "wp.remote.no_access": "אין לכם גישה ל-{0}.",
  "wp.remote.nothing": "עדיין לא פורסם דבר.",
  "wp.remote.state.available": "זמין",
  "wp.remote.state.stale": "לא עדכני",
  "wp.remote.state.stale.detail": "השיגור כבוי עד שיתעדכן",
  "wp.remote.state.unreachable": "לא נגיש",
  "wp.remote.state.no_access": "אין גישה",
  "wp.remote.status.stale": "לא עדכני",
  "wp.remote.status.unreachable": "לא נגיש",
  "wp.remote.status.no_access": "אין גישה",
  "wp.remote.status.empty": "לא פורסם דבר",
  "wp.remote.on": "ב-{0}, {1}",
  "wp.remote.teleport.switches": "מעביר אתכם לשרת {0}",
  "wp.remote.teleport.stale": "כבוי כל עוד {0} לא עדכני",
  "wp.remote.search.everywhere": "חיפוש בכל השרתים",
  "wp.remote.search.server": "חיפוש ב-{0}",
  "wp.error.remote.no_server": "אין שרת בשם {0}.",
  "wp.error.remote.no_dimension": "ל-{0} אין ממד {1}.",
  "wp.error.remote.no_list": "ל-{0} אין רשימה {1} ב-{2}.",
  "wp.error.remote.distance": "אי אפשר למיין נקודות מרוחקות לפי מרחק."
```

- [ ] **Step 2: Let the query engine search lists that live elsewhere**

In `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointQueryEngine.java`, replace:

```java
    private ListResult queryList(String dimensionName, WaypointList waypointList, String filter, Query query) {
```

with:

```java
    /** Searches and sorts lists that don't live in this server's files, such as a remote catalog's lists. */
    public static QueryResult queryLists(Map<String, List<WaypointList>> dimensions, Query query) {
        Query resolvedQuery = resolveQuery(query);
        String filter = resolvedQuery.normalizedFilter();
        List<DimensionResult> dimensionResults = new ArrayList<>();
        dimensions.forEach((dimensionName, lists) -> {
            List<ListResult> listResults = new ArrayList<>();
            for (WaypointList waypointList : lists) {
                ListResult listResult = queryList(dimensionName, waypointList, filter, resolvedQuery);
                if (listResult.include()) {
                    listResults.add(listResult);
                }
            }
            if (!listResults.isEmpty()) {
                dimensionResults.add(new DimensionResult(dimensionName, Collections.unmodifiableList(listResults)));
            }
        });
        return new QueryResult(Collections.unmodifiableList(dimensionResults), resolvedQuery);
    }

    private static ListResult queryList(String dimensionName, WaypointList waypointList, String filter, Query query) {
```

- [ ] **Step 3: Capture the remote cache as servers of lists**

Replace the whole of `common/src/main/java/_959/server_waypoint/crossserver/catalog/RemoteCatalogQuery.java` with:

```java
package _959.server_waypoint.crossserver.catalog;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteListSnapshot;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.RemoteWaypointSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One capture of the local remote-catalog cache as servers holding lists in the shape local
 * screens use (spec 14). Pure: it never resolves local game worlds. Catalog snapshots don't keep
 * the published order, so lists and waypoints follow their identifiers.
 */
public final class RemoteCatalogQuery {
    private RemoteCatalogQuery() {
    }

    /** A server as a reader may see it. Unreachable and no-access servers never show their last copy. */
    public record Server(RemoteServerId id, String displayName, RemoteCatalogState state,
                         Map<String, List<WaypointList>> dimensions) {
        public Server {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(state, "state");
            Map<String, List<WaypointList>> copied = new TreeMap<>();
            dimensions.forEach((dimension, lists) -> copied.put(dimension, List.copyOf(lists)));
            dimensions = Collections.unmodifiableMap(copied);
        }

        /** Available and stale servers show their catalog. */
        public boolean readable() {
            return this.state == RemoteCatalogState.AVAILABLE || this.state == RemoteCatalogState.STALE;
        }

        /** Only an available server takes teleports. */
        public boolean available() {
            return this.state == RemoteCatalogState.AVAILABLE;
        }

        public @Nullable List<WaypointList> lists(String dimension) {
            return this.dimensions.get(dimension);
        }

        public @Nullable WaypointList list(String dimension, String list) {
            List<WaypointList> lists = this.lists(dimension);
            return lists == null ? null
                    : lists.stream().filter(candidate -> candidate.name().equals(list)).findFirst().orElse(null);
        }

        public int listCount() {
            return this.dimensions.values().stream().mapToInt(List::size).sum();
        }

        public int waypointCount() {
            return this.dimensions.values().stream().flatMap(List::stream).mapToInt(WaypointList::size).sum();
        }
    }

    /** Every cached server, by identity. */
    public static List<Server> servers(Map<RemoteServerId, CatalogReceiver.View> catalogs) {
        return catalogs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value)))
                .map(entry -> server(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** The cached server with exactly this identity. */
    public static Optional<Server> server(Map<RemoteServerId, CatalogReceiver.View> catalogs, String id) {
        return catalogs.entrySet().stream()
                .filter(entry -> entry.getKey().value().equals(id))
                .findFirst()
                .map(entry -> server(entry.getKey(), entry.getValue()));
    }

    private static Server server(RemoteServerId id, CatalogReceiver.View view) {
        Map<String, List<WaypointList>> dimensions = new TreeMap<>();
        boolean readable = view.state() == RemoteCatalogState.AVAILABLE || view.state() == RemoteCatalogState.STALE;
        if (readable && view.snapshot() != null) {
            view.snapshot().dimensions().forEach((dimension, lists) -> dimensions.put(dimension, lists(lists)));
        }
        return new Server(id, view.displayName(), view.state(), dimensions);
    }

    private static List<WaypointList> lists(Map<String, RemoteListSnapshot> lists) {
        List<WaypointList> converted = new ArrayList<>();
        new TreeMap<>(lists).forEach((name, list) -> {
            List<SimpleWaypoint> waypoints = new ArrayList<>();
            new TreeMap<>(list.waypoints()).forEach((waypoint, value) -> waypoints.add(waypoint(waypoint, value)));
            converted.add(new WaypointList(name, list.displayName(), 0, waypoints));
        });
        return converted;
    }

    private static SimpleWaypoint waypoint(String name, RemoteWaypointSnapshot value) {
        return new SimpleWaypoint(name, value.displayName(), value.initials(), value.position(), value.rgb(),
                value.yaw(), value.global(), value.keywords(), value.description(), value.icon());
    }
}
```

- [ ] **Step 4: Write the failing builder tests**

Create `common/src/test/java/_959/server_waypoint/text/feedback/RemoteScreensTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteScreensTest {
    private static final String OW = "\"minecraft:overworld\"";
    private static final Server SURVIVAL = server("survival", "Survival", RemoteCatalogState.AVAILABLE, Map.of(
            OVERWORLD, Fixtures.overworldLists(),
            NETHER, List.of(new WaypointList("Nether Hub", 1, List.of(
                    Fixtures.waypoint("Hub", "NH", 0xFF5555, 12, 64, -4),
                    Fixtures.waypoint("Fortress", "NF", 0xAA0000, 40, 70, 90),
                    Fixtures.waypoint("Bastion", "NB", 0x555555, -80, 60, 30)))),
            END, List.of(new WaypointList("End", 1, List.of(Fixtures.waypoint("Gateway", "EG", 0xAA00AA, 100, 70, 0))))));
    private static final Server CREATIVE = server("creative-1", "Creative Plots", RemoteCatalogState.STALE, Map.of(
            OVERWORLD, List.of(new WaypointList("Plots", 1, List.of(
                    Fixtures.waypoint("Plot A", "PA", 0x55FF55, 0, 64, 0),
                    Fixtures.waypoint("Plot B", "PB", 0x55FF55, 32, 64, 0),
                    Fixtures.waypoint("Pumpkin Farm", "PF", 0xFFAA00, 64, 64, 0),
                    Fixtures.waypoint("Sky Farm", "SF", 0x55FFFF, 96, 120, 0))))));
    private static final Server HALLOWEEN = server("halloween", "Halloween Event", RemoteCatalogState.UNAUTHORIZED, Map.of());
    private static final Server LOBBY = server("lobby", "Lobby", RemoteCatalogState.UNAVAILABLE, Map.of());
    private static final Server SKYBLOCK = server("skyblock", "Skyblock", RemoteCatalogState.AVAILABLE, Map.of());
    private static final List<Server> SERVERS = List.of(SURVIVAL, LOBBY, CREATIVE, SKYBLOCK, HALLOWEEN);

    private static Server server(String id, String name, RemoteCatalogState state, Map<String, List<WaypointList>> dimensions) {
        return new Server(new RemoteServerId(id), name, state, dimensions);
    }

    @Test
    void thePickerShowsServersWithWaypointsFirstAndTheirStates() {
        Component picker = RemoteScreens.picker(Fixtures.player(), SERVERS, 1, 10);

        assertEquals(List.of(
                "Remote servers  5 servers connected",
                "● Creative Plots · 4",
                "● Survival · 18",
                "● Halloween Event · no access",
                "● Lobby · unreachable",
                "● Skyblock · nothing published",
                "All servers · 22"), lines(picker));
        assertEquals(NamedTextColor.YELLOW, colorOf(picker, "●"));
        assertEquals("Stale\nTeleporting is off until it refreshes", tooltipOf(picker, "●"));
        assertEquals("/wp remote list survival", clickOf(picker, "Survival"));
        assertEquals(NamedTextColor.WHITE, colorOf(picker, "Survival"));
        assertNull(clickOf(picker, "Lobby"));
        assertEquals(NamedTextColor.GRAY, colorOf(picker, "Lobby"));
        assertEquals(NamedTextColor.GRAY, colorOf(picker, "Skyblock"));
        assertEquals("/wp remote list", clickOf(picker, "All servers"));
    }

    @Test
    void plainTextPickersNameEveryServerAndItsState() {
        assertEquals(List.of(
                "Remote servers  5 servers connected",
                "● Creative Plots (creative-1) · 4 · stale",
                "● Survival (survival) · 18",
                "● Halloween Event (halloween) · no access",
                "● Lobby (lobby) · unreachable",
                "● Skyblock (skyblock) · nothing published"), lines(RemoteScreens.picker(Fixtures.console(), SERVERS, 1, 10)));
    }

    @Test
    void thePickerPagesWithTheDimensionListBudget() {
        List<Server> many = IntStream.range(0, 8)
                .mapToObj(index -> server("s" + index, "Server " + index, RemoteCatalogState.AVAILABLE, Map.of())).toList();
        Component first = RemoteScreens.picker(Fixtures.player(), many, 1, 1);

        assertEquals(9, lines(first).size());
        assertEquals("/wp remote page 2", clickOf(first, "… 2 more servers"));
        assertEquals("✘ Page 3 does not exist; the last page is 2. Last page",
                render(RemoteScreens.picker(Fixtures.player(), many, 3, 1)));
    }

    @Test
    void allServersListEachServerWithItsDimensions() {
        Component all = RemoteScreens.allServers(Fixtures.player(), SERVERS, ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "All servers ⏷",
                "Creative Plots ● · 4",
                "  Overworld · 4",
                "Survival ● · 18",
                "  Overworld · 14",
                "  Nether · 3",
                "  End · 1",
                "Halloween Event ● · no access",
                "Lobby ● · unreachable",
                "Skyblock ● · nothing published",
                "Search"), lines(all));
        assertEquals("/wp remote", clickOf(all, "All servers ⏷"));
        assertEquals("/wp remote list survival \"minecraft:the_nether\"", clickOf(all, "Nether"));
        assertEquals(NamedTextColor.RED, colorOf(all, "Nether"));
        assertEquals("/wp remote list search ", suggestions(all).get(0));
    }

    @Test
    void aServerShowsItsDimensionsAndLists() {
        Component server = RemoteScreens.server(Fixtures.player(), SURVIVAL, ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "● Survival ⏷  3 dimensions · 18 waypoints",
                "Overworld · 14",
                "  Home Bases · 3",
                "  Farms · 7",
                "  Exploration · 4",
                "Nether · 3",
                "  Nether Hub · 3",
                "End · 1",
                "  End · 1",
                "Search"), lines(server));
        assertEquals("/wp remote", clickOf(server, "Survival ⏷"));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(server, "Farms"));
        assertEquals("/wp remote list survival search ", suggestions(server).get(0));
    }

    @Test
    void unreadableAndEmptyServersSaySo() {
        assertEquals(List.of("● Lobby ⏷", "Lobby can't be reached right now. Servers"),
                lines(RemoteScreens.server(Fixtures.player(), LOBBY, ListQuery.DEFAULT, 10)));
        assertEquals("You don't have access to Halloween Event. Servers",
                lines(RemoteScreens.server(Fixtures.player(), HALLOWEEN, ListQuery.DEFAULT, 10)).get(1));
        assertEquals(List.of("● Skyblock ⏷", "Nothing published yet."),
                lines(RemoteScreens.server(Fixtures.player(), SKYBLOCK, ListQuery.DEFAULT, 10)));
    }

    @Test
    void aDimensionUsesTheLocalViewsWithRemoteActions() {
        Component tree = RemoteScreens.dimension(Fixtures.player(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints",
                "Home Bases",
                "  [MH] Main Home",
                "  [GM] Gem Mine",
                "  [SV] Spawn Village",
                "Farms",
                "  [IF] Iron Farm",
                "  [WF] Wheat Fields",
                "  [CF] Cane Farm",
                "  … 4 more",
                "Exploration",
                "  [OM] Ocean Monument",
                "  [DT] Desert Temple",
                "  [SH] Stronghold",
                "  [WM] Woodland Mansion",
                "Lists · Tree · Flat · Search",
                "Sort Default · Name · Color"), lines(tree));
        assertEquals("/wp remote list survival", clickOf(tree, "Overworld ⏷"));
        assertEquals("/wp remote list survival " + OW + " \"Home Bases\"", clickOf(tree, "Home Bases"));
        assertEquals("/wp remote tp survival " + OW + " \"Home Bases\" \"Main Home\"", clickOf(tree, "[MH]"));
        assertEquals("/wp remote details survival " + OW + " \"Home Bases\" \"Main Home\"", clickOf(tree, "Main Home"));
        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\nOn Survival in Overworld\nClick for details",
                tooltipOf(tree, "Main Home"));
        assertEquals("Teleport to Main Home\n120, 64, -35\nSwitches you to the Survival server", tooltipOf(tree, "[MH]"));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(tree, "… 4 more"));
    }

    @Test
    void theListsAndFlatViewsShowNoDistancesOrAddActions() {
        assertEquals(List.of(
                "● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints",
                "Home Bases · 3",
                "Farms · 7",
                "Exploration · 4",
                "Lists · Tree · Flat · Search"),
                lines(RemoteScreens.dimension(Fixtures.player(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT.withView(ListView.LISTS), 10)));
        List<String> flat = lines(RemoteScreens.dimension(Fixtures.player(), SURVIVAL, OVERWORLD,
                ListQuery.DEFAULT.withView(ListView.FLAT), 10));
        assertEquals("[MH] Main Home · Home Bases", flat.get(1));
        assertEquals("… 4 more waypoints", flat.get(11));
    }

    @Test
    void staleServersAndReadersWithoutPermissionDontTeleport() {
        Component stale = RemoteScreens.dimension(Fixtures.player(), CREATIVE, OVERWORLD, ListQuery.DEFAULT, 10);
        Component member = RemoteScreens.dimension(Fixtures.member(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT, 10);

        assertNull(clickOf(stale, "[PA]"));
        assertEquals("Off while Creative Plots is stale", tooltipOf(stale, "[PA]"));
        assertNull(clickOf(member, "[MH]"));
        assertTrue(runCommands(member).stream().noneMatch(command -> command.startsWith("/wp remote tp ")));
    }

    @Test
    void aListHasItsRowsSearchAndTheSortRow() {
        Component list = RemoteScreens.list(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "● Survival › Overworld › Farms  7 waypoints",
                "[IF] Iron Farm",
                "[WF] Wheat Fields",
                "[CF] Cane Farm",
                "[MG] Mob Grinder",
                "[VH] Villager Hall",
                "[SF] Slime Farm",
                "[GF] Gold Farm",
                "Search",
                "Sort Default · Name · Color"), lines(list));
        assertEquals("/wp remote list survival", clickOf(list, "Survival"));
        assertEquals("/wp remote list survival " + OW, clickOf(list, "Overworld"));
        assertEquals(NamedTextColor.GOLD, colorOf(list, "Farms"));
        assertEquals("No waypoints yet.", lines(RemoteScreens.list(Fixtures.player(), SURVIVAL, OVERWORLD,
                new WaypointList("Storage", 1, List.of()), ListQuery.DEFAULT, 10)).get(1));
    }

    @Test
    void detailsAreReadOnly() {
        SimpleWaypoint iron = Fixtures.farms().getWaypointByName("Iron Farm");
        Component details = RemoteScreens.details(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), iron);

        assertEquals(List.of(
                "● Survival › Overworld › Farms › [IF] Iron Farm",
                "Position: 300, 80, 150",
                "Color: ■ #AAAAAA",
                "Yaw: 0° (south)",
                "Visibility: Global",
                "Keywords: none",
                "Description: none",
                "[Teleport] [Back]"), lines(details));
        assertEquals("/wp remote tp survival " + OW + " Farms \"Iron Farm\"", clickOf(details, "[Teleport]"));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(details, "[Back]"));
        SimpleWaypoint renamed = new SimpleWaypoint("iron_farm", "Iron Farm", "IF", iron.pos(), iron.rgb(), 0, true,
                List.of(), "", null);
        assertEquals("Identifier: iron_farm",
                lines(RemoteScreens.details(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), renamed)).get(1));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(RemoteScreens.details(Fixtures.player(), CREATIVE, OVERWORLD,
                CREATIVE.lists(OVERWORLD).get(0), CREATIVE.lists(OVERWORLD).get(0).getWaypointByName("Plot A")), "[Teleport]"));
        assertEquals(7, lines(RemoteScreens.details(Fixtures.console(), SURVIVAL, OVERWORLD, Fixtures.farms(), iron)).size());
    }

    @Test
    void searchResultsSitUnderServerAndDimensionHeadings() {
        Component everywhere = RemoteScreens.search(Fixtures.player(), null, SERVERS, ListQuery.DEFAULT.withSearch("farm"), 10);

        assertEquals(List.of(
                "All servers ⏷",
                "Search \"farm\" · 9 matches · Clear",
                "Creative Plots ● › Overworld",
                "  [PF] Pumpkin Farm · Plots",
                "  [SF] Sky Farm · Plots",
                "Survival ● › Overworld",
                "  [IF] Iron Farm · Farms",
                "  [WF] Wheat Fields · Farms",
                "  [CF] Cane Farm · Farms",
                "  [MG] Mob Grinder · Farms",
                "  [VH] Villager Hall · Farms",
                "  [SF] Slime Farm · Farms",
                "  [GF] Gold Farm · Farms"), lines(everywhere));
        assertEquals("/wp remote list", clickOf(everywhere, "Clear"));
        List<String> onServer = lines(RemoteScreens.search(Fixtures.player(), SURVIVAL, List.of(SURVIVAL),
                ListQuery.DEFAULT.withSearch("farm"), 10));
        assertEquals("● Survival ⏷", onServer.get(0));
        assertEquals("Overworld", onServer.get(2));
    }

    @Test
    void errorsNameTheMissingPartAndHowToGoBack() {
        assertEquals("✘ No server called mars. Servers", render(RemoteScreens.noServer(Fixtures.player(), "mars")));
        assertEquals("✘ Survival has no dimension ad_astra:mars. Browse",
                render(RemoteScreens.noDimension(Fixtures.player(), SURVIVAL, "ad_astra:mars")));
        assertEquals("✘ Survival has no list Farm in Overworld. Browse",
                render(RemoteScreens.noList(Fixtures.player(), SURVIVAL, OVERWORLD, "Farm")));
        assertEquals("✘ No waypoint called Gate in Farms. Open Farms",
                render(RemoteScreens.noWaypoint(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), "Gate")));
        assertEquals("✘ Remote waypoints can't be sorted by distance.", render(RemoteScreens.distanceUnavailable()));
    }

    @Test
    void remoteLabelsStayLiteralAndShort() {
        Server odd = server("odd", "{\"text\":\"Odd\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"}}",
                RemoteCatalogState.AVAILABLE, Map.of(OVERWORLD, List.of(new WaypointList("x".repeat(300), 1, List.of()))));
        Component picker = RemoteScreens.picker(Fixtures.player(), List.of(odd), 1, 10);

        assertTrue(render(picker).contains("{\"text\":\"Odd\""));
        assertTrue(runCommands(picker).stream().noneMatch(command -> command.contains("/op")));
        assertTrue(render(RemoteScreens.server(Fixtures.player(), odd, ListQuery.DEFAULT, 10)).contains("x".repeat(256) + "…"));
    }
}
```

- [ ] **Step 5: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.RemoteScreensTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: variable RemoteScreens`.

- [ ] **Step 6: Write `RemoteRefs`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/RemoteRefs.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/**
 * How remote screens show servers, lists and waypoints (spec 14). Labels from another server stay
 * literal text of at most 256 characters, so a remote name can never carry its own clicks.
 */
public final class RemoteRefs {
    static final int MAX_LABEL = 256;

    private RemoteRefs() {
    }

    /** A remote label as literal text; a blank one shows its identity in quotes. */
    public static Component label(String display, String identity) {
        return display.isBlank() ? text("\"" + truncate(identity) + "\"") : text(truncate(display));
    }

    /** The label, followed by " (identity)" for plain-text viewers when the two differ. */
    public static Component label(Viewer viewer, String display, String identity) {
        Component label = label(display, identity);
        return viewer.plainText() && !display.equals(identity)
                ? Chat.concat(label, text(" (" + truncate(identity) + ")"))
                : label;
    }

    static String truncate(String value) {
        return value.length() > MAX_LABEL ? value.substring(0, MAX_LABEL) + Chat.ELLIPSIS : value;
    }

    /** The server's name; plain-text viewers always read "Display (id)". */
    public static Component serverName(Viewer viewer, Server server) {
        Component name = label(server.displayName(), server.id().value());
        return viewer.plainText() ? Chat.concat(name, text(" (" + truncate(server.id().value()) + ")")) : name;
    }

    /** The coloured ●: green available, yellow stale, red unreachable, dark gray no access. */
    public static Component dot(Viewer viewer, Server server) {
        NamedTextColor color = switch (server.state()) {
            case AVAILABLE -> GREEN;
            case STALE -> YELLOW;
            case UNAVAILABLE -> RED;
            case UNAUTHORIZED -> DARK_GRAY;
        };
        Tooltip tooltip = Tooltip.of("wp.remote.state." + switch (server.state()) {
            case AVAILABLE -> "available";
            case STALE -> "stale";
            case UNAVAILABLE -> "unreachable";
            case UNAUTHORIZED -> "no_access";
        });
        if (server.state() == RemoteCatalogState.STALE) {
            tooltip = tooltip.line("wp.remote.state.stale.detail");
        }
        return Chat.hover(viewer, text(Chat.DOT, color), tooltip);
    }

    /** What follows a server's name: its waypoint count, or a status word; plain text adds "stale". */
    public static Component detail(Viewer viewer, Server server) {
        if (!server.readable()) {
            return translatable(server.state() == RemoteCatalogState.UNAUTHORIZED
                    ? "wp.remote.status.no_access" : "wp.remote.status.unreachable", GRAY);
        }
        if (server.waypointCount() == 0) {
            return translatable("wp.remote.status.empty", GRAY);
        }
        return Chat.join(text(String.valueOf(server.waypointCount()), GRAY),
                viewer.plainText() && server.state() == RemoteCatalogState.STALE
                        ? translatable("wp.remote.status.stale", GRAY) : null);
    }

    /** The server's name opening it: white with waypoints, gray when empty, and unclickable when unreadable. */
    public static Component serverLink(Viewer viewer, Server server) {
        Component name = serverName(viewer, server);
        if (!server.readable()) {
            return Chat.hover(viewer, Chat.colored(name, GRAY), serverTooltip(server, null));
        }
        return Chat.link(viewer, name, server.waypointCount() > 0 ? WHITE : GRAY,
                Click.run(ListTarget.remote(server.id().value(), null, null).command(ListQuery.DEFAULT)),
                serverTooltip(server, "wp.hint.open"));
    }

    /** The name, the identity, and what a readable server holds. */
    public static Tooltip serverTooltip(Server server, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(label(server.displayName(), server.id().value()))
                .line(text(truncate(server.id().value())));
        if (server.readable()) {
            tooltip = tooltip.line(translatable("wp.in", Chat.count("wp.count.waypoint", server.waypointCount()),
                    Chat.count("wp.count.dimension", server.dimensions().size())));
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    /** [AB] Name: the initials teleport and the white name opens the read-only details. */
    public static Component reference(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                      SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        return Chat.concat(initials(dims, server, dimension, list, waypoint), text(" "),
                Chat.link(viewer, label(viewer, waypoint.displayName(), waypoint.name()), WHITE,
                        Click.run(command("details", server, dimension, list, waypoint)),
                        waypointTooltip(dims, server, dimension, waypoint, "wp.hint.details")));
    }

    /** [AB] Name whose gold name only shows its tooltip, for the details title. */
    public static Component title(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                  SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        return Chat.concat(initials(dims, server, dimension, list, waypoint), text(" "),
                Chat.hover(viewer, Chat.colored(label(viewer, waypoint.displayName(), waypoint.name()), GOLD),
                        waypointTooltip(dims, server, dimension, waypoint, null)));
    }

    /**
     * [AB] teleports only on an available server and with the remote teleport permission. On a
     * stale server it says it is off; otherwise it shows the waypoint tooltip.
     */
    public static Component initials(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                     SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        Component initials = text("[" + waypoint.initials() + "]");
        TextColor color = TextColor.color(waypoint.rgb());
        boolean permitted = viewer.can(Viewer.Permission.REMOTE_TP);
        if (permitted && server.available()) {
            return Chat.link(viewer, initials, color, Click.run(command("tp", server, dimension, list, waypoint)),
                    teleportTooltip(dims, server, dimension, waypoint));
        }
        Tooltip tooltip = permitted && server.state() == RemoteCatalogState.STALE
                ? Tooltip.of("wp.remote.teleport.stale", label(server.displayName(), server.id().value()))
                : waypointTooltip(dims, server, dimension, waypoint, null);
        return Chat.hover(viewer, Chat.colored(initials, color), tooltip);
    }

    /** [AB] Name, the list in Flat rows and search results, and the coordinates for plain-text viewers. */
    public static Component row(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                SimpleWaypoint waypoint, boolean withList) {
        Viewer viewer = dims.viewer();
        return Chat.join(reference(dims, server, dimension, list, waypoint),
                withList ? listLink(dims, server, dimension, list, GRAY, ListQuery.DEFAULT) : null,
                viewer.plainText() ? text(DimensionStyle.coordinates(waypoint.pos())) : null);
    }

    /** Name, description, coordinates, paired coordinates, "On Survival in Overworld", and a hint. */
    public static Tooltip waypointTooltip(DimensionStyle dims, Server server, String dimension, SimpleWaypoint waypoint,
                                          @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(label(waypoint.displayName(), waypoint.name()));
        if (!waypoint.description().isBlank()) {
            tooltip = tooltip.line(text(truncate(waypoint.description())));
        }
        tooltip = tooltip.line(text(DimensionStyle.coordinates(waypoint.pos()), WHITE));
        Component paired = dims.pairedCoordinates(dimension, waypoint.pos());
        if (paired != null) {
            tooltip = tooltip.line(paired);
        }
        tooltip = tooltip.line(translatable("wp.remote.on", label(server.displayName(), server.id().value()),
                dims.name(dimension)));
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    public static Tooltip teleportTooltip(DimensionStyle dims, Server server, String dimension, SimpleWaypoint waypoint) {
        return Tooltip.of(translatable("wp.teleport.to", label(waypoint.displayName(), waypoint.name())))
                .line(text(DimensionStyle.coordinates(waypoint.pos()), WHITE))
                .line(translatable("wp.remote.teleport.switches", label(server.displayName(), server.id().value())));
    }

    /** A list name opening the list on its server. */
    public static Component listLink(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                     TextColor color, ListQuery query) {
        Viewer viewer = dims.viewer();
        return Chat.link(viewer, label(viewer, list.displayName(), list.name()), color,
                Click.run(ListTarget.remote(server.id().value(), dimension, list.name()).command(query)),
                listTooltip(list, "wp.hint.open"));
    }

    /** Farms · 7 waypoints, then the identity when the display name differs. */
    public static Tooltip listTooltip(WaypointList list, @Nullable String hint) {
        Tooltip tooltip = Tooltip.of(Chat.join(label(list.displayName(), list.name()),
                Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY)));
        if (!list.displayName().equals(list.name())) {
            tooltip = tooltip.line(text(truncate(list.name())));
        }
        return hint == null ? tooltip : tooltip.hint(hint);
    }

    /** /wp remote <action> <server> <dimension> <list> <waypoint>, every identity quoted as needed. */
    public static String command(String action, Server server, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return "/wp remote " + action + " " + escapeListName(server.id().value()) + " " + escapeListName(dimension)
                + " " + escapeListName(list.name()) + " " + escapeListName(waypoint.name());
    }
}
```

- [ ] **Step 7: Write `RemoteScreens`**

Create `common/src/main/java/_959/server_waypoint/text/feedback/RemoteScreens.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Remote browsing (spec 14): the server picker, all servers, a server, a dimension, a list and details. */
public final class RemoteScreens {
    private static final List<SortMode> SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.COLOR);

    /** Servers with waypoints first, then the rest, each group A–Z by name. */
    static final Comparator<Server> ORDER = Comparator
            .comparingInt((Server server) -> server.waypointCount() > 0 ? 0 : 1)
            .thenComparing(Server::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(server -> server.id().value());

    /** A heading (a server or a dimension) or one of its rows, for block paging. */
    private record Line(Server server, @Nullable String dimension, @Nullable WaypointList list, boolean heading) {
    }

    private record Match(Server server, String dimension, WaypointList list, SimpleWaypoint waypoint) {
    }

    private RemoteScreens() {
    }

    /** /wp remote: every server with its state, L + 5 per page, then All servers. */
    public static Component picker(Viewer viewer, List<Server> servers, int page, int pageLimit) {
        ListTarget target = new ListTarget("/wp remote");
        ListQuery query = ListQuery.DEFAULT.withPage(page);
        ChatLines lines = new ChatLines().line(translatable("wp.remote.title", GOLD), text("  "),
                translatable("wp.remote.connected", GRAY, Chat.count("wp.count.server", servers.size())));
        if (servers.isEmpty()) {
            return lines.add(translatable("wp.remote.none", GRAY)).build();
        }
        List<List<Server>> pages = Paging.bySize(servers.stream().sorted(ORDER).toList(), pageLimit + 5);
        if (page > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        for (Server server : pages.get(page - 1)) {
            lines.line(RemoteRefs.dot(viewer, server), text(" "), RemoteRefs.serverLink(viewer, server), Chat.SEPARATOR,
                    RemoteRefs.detail(viewer, server));
        }
        lines.add(ListControls.more(viewer, "server", Paging.after(pages, page), target.command(query.withPage(page + 1))));
        if (!viewer.plainText()) {
            int total = servers.stream().mapToInt(Server::waypointCount).sum();
            lines.line(Chat.link(viewer, translatable("wp.remote.all"), AQUA, Click.run("/wp remote list"),
                            Tooltip.of("wp.remote.all.tooltip")), Chat.SEPARATOR, text(String.valueOf(total), GRAY),
                    ListControls.pager(viewer, target, query, pages.size(),
                            ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.server", servers.size()))));
        }
        return lines.build();
    }

    /** /wp remote list: each server with its dimensions, L + 5 lines per page. */
    public static Component allServers(Viewer viewer, List<Server> servers, ListQuery query, int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = ListTarget.remote(null, null, null);
        List<Line> blocks = new ArrayList<>();
        for (Server server : servers.stream().sorted(ORDER).toList()) {
            blocks.add(new Line(server, null, null, true));
            ordered(dims, server).forEach(dimension -> blocks.add(new Line(server, dimension, null, false)));
        }
        ChatLines lines = new ChatLines().add(allTitle(viewer));
        if (blocks.isEmpty()) {
            return lines.add(translatable("wp.remote.none", GRAY)).build();
        }
        List<List<Line>> pages = pageBlocks(blocks, pageLimit + 5);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        List<Line> page = pages.get(query.page() - 1);
        if (!page.get(0).heading()) {
            lines.add(serverHeading(viewer, page.get(0).server(), true));
        }
        for (Line line : page) {
            lines.add(line.heading() ? serverHeading(viewer, line.server(), false)
                    : dimensionRow(dims, line.server(), Objects.requireNonNull(line.dimension()), "  ", false));
        }
        int dimensionsAfter = rowsAfter(pages, query.page());
        String next = target.command(query.withPage(query.page() + 1));
        lines.add(dimensionsAfter > 0 ? ListControls.more(viewer, "dimension", dimensionsAfter, next)
                : ListControls.more(viewer, "server", headingsAfter(pages, query.page()), next));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.server", servers.size()))),
                ListControls.search(viewer, target, translatable("wp.remote.search.everywhere")));
        return lines.build();
    }

    /** /wp remote list <server>: its dimensions and their lists, L + 5 lines per page. */
    public static Component server(Viewer viewer, Server server, ListQuery query, int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = ListTarget.remote(server.id().value(), null, null);
        ChatLines lines = new ChatLines();
        Component title = serverTitle(viewer, server);
        if (!server.readable()) {
            Component servers = serversLink(viewer);
            return lines.add(title).line(translatable(server.state() == RemoteCatalogState.UNAUTHORIZED
                                    ? "wp.remote.no_access" : "wp.remote.unreachable", GRAY,
                            RemoteRefs.label(server.displayName(), server.id().value())),
                    servers == null ? null : text(" "), servers).build();
        }
        if (server.dimensions().isEmpty()) {
            return lines.add(title).add(translatable("wp.remote.nothing", GRAY)).build();
        }
        lines.line(title, text("  "), Chat.colored(Chat.join(Chat.count("wp.count.dimension", server.dimensions().size()),
                Chat.count("wp.count.waypoint", server.waypointCount())), GRAY));
        List<Line> blocks = new ArrayList<>();
        for (String dimension : ordered(dims, server)) {
            blocks.add(new Line(server, dimension, null, true));
            Objects.requireNonNull(server.lists(dimension)).forEach(list -> blocks.add(new Line(server, dimension, list, false)));
        }
        List<List<Line>> pages = pageBlocks(blocks, pageLimit + 5);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        List<Line> page = pages.get(query.page() - 1);
        if (!page.get(0).heading()) {
            lines.add(dimensionRow(dims, server, Objects.requireNonNull(page.get(0).dimension()), "", true));
        }
        for (Line line : page) {
            String dimension = Objects.requireNonNull(line.dimension());
            lines.add(line.heading() ? dimensionRow(dims, server, dimension, "", false)
                    : Chat.concat(text("  "), RemoteRefs.listLink(dims, server, dimension, Objects.requireNonNull(line.list()),
                            WHITE, ListQuery.DEFAULT), Chat.SEPARATOR, text(String.valueOf(line.list().size()), GRAY)));
        }
        lines.add(ListControls.more(viewer, "list", rowsAfter(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", server.listCount()))),
                ListControls.search(viewer, target, translatable("wp.remote.search.server",
                        RemoteRefs.label(server.displayName(), server.id().value()))));
        return lines.build();
    }

    /**
     * /wp remote list [<server>] search <text>: matches under headings, L rows per page. Across all
     * servers a heading names the server and the dimension; on one server only the dimension.
     */
    public static Component search(Viewer viewer, @Nullable Server only, List<Server> servers, ListQuery query,
                                   int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = only == null ? ListTarget.remote(null, null, null) : ListTarget.remote(only.id().value(), null, null);
        String clear = target.command(query.withSearch(""));
        ChatLines lines = new ChatLines().add(only == null ? allTitle(viewer) : serverTitle(viewer, only));
        List<Match> matches = new ArrayList<>();
        for (Server server : only == null ? servers.stream().sorted(ORDER).toList() : List.of(only)) {
            if (!server.readable()) {
                continue;
            }
            WaypointQueryEngine.queryLists(server.dimensions(), engineQuery(query)).dimensions().stream()
                    .sorted(Comparator.comparing(WaypointQueryEngine.DimensionResult::dimensionName, dims.order()))
                    .forEach(dimension -> dimension.lists().forEach(list -> list.waypoints().forEach(waypoint ->
                            matches.add(new Match(server, dimension.dimensionName(), list.sourceList(), waypoint)))));
        }
        if (matches.isEmpty()) {
            return lines.add(ListControls.noMatches(viewer, query.search(), clear)).build();
        }
        List<List<Match>> pages = Paging.bySize(matches, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        lines.add(ListControls.searchLine(viewer, query.search(), matches.size(), clear));
        String lastServer = null;
        String lastDimension = null;
        for (Match match : pages.get(query.page() - 1)) {
            String server = match.server().id().value();
            if (!server.equals(lastServer) || !match.dimension().equals(lastDimension)) {
                lines.add(only == null
                        ? Chat.concat(RemoteRefs.serverLink(viewer, match.server()), text(" "),
                        RemoteRefs.dot(viewer, match.server()), Chat.CRUMB, dimensionLink(dims, match.server(), match.dimension()))
                        : dimensionLink(dims, match.server(), match.dimension()));
                lastServer = server;
                lastDimension = match.dimension();
            }
            lines.line(text("  "), RemoteRefs.row(dims, match.server(), match.dimension(), match.list(), match.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                ListControls.pageDetail(pageLimit, Chat.count("wp.count.match", matches.size()))));
        return lines.build();
    }

    /** /wp remote list <server> <dimension>: the local views (spec 6.2) with remote actions. */
    public static Component dimension(Viewer viewer, Server server, String dimension, ListQuery query, int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        List<WaypointList> lists = Objects.requireNonNull(server.lists(dimension));
        ListScreen.Totals totals = new ListScreen.Totals(lists.size(), lists.stream().mapToInt(WaypointList::size).sum());
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        ChatLines lines = new ChatLines().add(dimensionHeader(dims, server, dimension, totals));
        if (totals.lists() == 0) {
            return lines.add(translatable("wp.dimension.no_lists.sentence", GRAY)).build();
        }
        WaypointQueryEngine.QueryResult result = WaypointQueryEngine.queryLists(Map.of(dimension, lists), engineQuery(query));
        if (query.searching() && result.listCount() == 0) {
            return lines.add(ListControls.noMatches(viewer, query.search(), target.command(query.withSearch("")))).build();
        }
        List<WaypointListDisplayModel.DisplayList> groups = WaypointListDisplayModel.build(result, true).lists();
        ListView view = ListScreen.resolveView(query, groups, totals, pageLimit);
        ListQuery shown = query.withView(view).withPage(query.page());
        if (query.searching()) {
            lines.add(ListControls.searchLine(viewer, query.search(), result.waypointCount(),
                    target.command(query.withSearch(""))));
        }
        return switch (view) {
            case LISTS -> listsView(dims, server, dimension, totals, groups, shown, pageLimit, lines);
            case FLAT -> flatView(dims, server, dimension, totals, result, shown, pageLimit, lines);
            default -> treeView(dims, server, dimension, totals, groups, shown, pageLimit, lines);
        };
    }

    /** /wp remote list <server> <dimension> <list> */
    public static Component list(Viewer viewer, Server server, String dimension, WaypointList list, ListQuery query,
                                 int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = ListTarget.remote(server.id().value(), dimension, list.name());
        Component crumb = Chat.concat(crumbs(dims, server, dimension), Chat.CRUMB,
                Chat.hover(viewer, Chat.colored(RemoteRefs.label(viewer, list.displayName(), list.name()), GOLD),
                        RemoteRefs.listTooltip(list, null)));
        ChatLines lines = new ChatLines();
        if (list.isEmpty()) {
            return lines.add(crumb).add(translatable("wp.list.empty.sentence", GRAY)).build();
        }
        lines.line(crumb, text("  "), Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY));
        WaypointQueryEngine.QueryResult result = WaypointQueryEngine.queryLists(Map.of(dimension, List.of(list)),
                engineQuery(query));
        List<SimpleWaypoint> rows = result.dimensions().isEmpty()
                ? List.of()
                : result.dimensions().get(0).lists().get(0).waypoints();
        if (query.searching()) {
            String clear = target.command(query.withSearch(""));
            if (rows.isEmpty()) {
                return lines.add(ListControls.noMatches(viewer, query.search(), clear)).build();
            }
            lines.add(ListControls.searchLine(viewer, query.search(), rows.size(), clear));
        }
        List<List<SimpleWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        for (SimpleWaypoint waypoint : pages.get(query.page() - 1)) {
            lines.add(RemoteRefs.row(dims, server, dimension, list, waypoint, false));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                ListControls.search(viewer, target, translatable("wp.search.list",
                        RemoteRefs.label(list.displayName(), list.name()))),
                sortRow(viewer, target, query));
        return lines.build();
    }

    /** /wp remote details: read-only, with Identifier first when the display name differs. */
    public static Component details(Viewer viewer, Server server, String dimension, WaypointList list,
                                    SimpleWaypoint waypoint) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        Viewer reader = dims.viewer();
        ChatLines lines = new ChatLines().line(crumbs(dims, server, dimension), Chat.CRUMB,
                RemoteRefs.listLink(dims, server, dimension, list, WHITE, ListQuery.DEFAULT), Chat.CRUMB,
                RemoteRefs.title(dims, server, dimension, list, waypoint));
        if (!waypoint.displayName().equals(waypoint.name())) {
            lines.add(property("identifier", text(RemoteRefs.truncate(waypoint.name()))));
        }
        lines.add(property("position", text(DimensionStyle.coordinates(waypoint.pos()))));
        lines.add(property("color", PickerScreens.colorValue(waypoint.rgb())));
        lines.add(property("yaw", PickerScreens.yawValue(waypoint.yaw())));
        lines.add(property("visibility", translatable(waypoint.global() ? "wp.visibility.global" : "wp.visibility.local")));
        lines.add(property("keywords", waypoint.keywords().isEmpty() ? none()
                : text(RemoteRefs.truncate(String.join(", ", waypoint.keywords())))));
        lines.add(property("description", waypoint.description().isBlank() ? none()
                : text(RemoteRefs.truncate(waypoint.description()))));
        Component teleport = null;
        if (reader.can(Viewer.Permission.REMOTE_TP)) {
            teleport = server.available()
                    ? Chat.button(reader, translatable("wp.action.teleport"), LIGHT_PURPLE,
                    Click.run(RemoteRefs.command("tp", server, dimension, list, waypoint)),
                    RemoteRefs.teleportTooltip(dims, server, dimension, waypoint))
                    : Chat.disabledButton(reader, translatable("wp.action.teleport"), Tooltip.of("wp.remote.teleport.stale",
                    RemoteRefs.label(server.displayName(), server.id().value())));
        }
        List<Component> buttons = Arrays.asList(teleport, Chat.button(reader, translatable("wp.action.back"), GRAY,
                Click.run(ListTarget.remote(server.id().value(), dimension, list.name()).command(ListQuery.DEFAULT)),
                Tooltip.of("wp.action.back.tooltip", RemoteRefs.label(list.displayName(), list.name()))));
        if (!Chat.isEmpty(buttons)) {
            lines.add(Chat.spaced(buttons));
        }
        return lines.build();
    }

    /** ✘ No server called x. Servers */
    public static Component noServer(Viewer viewer, String id) {
        return Chat.error(translatable("wp.error.remote.no_server", text(RemoteRefs.truncate(id))), serversLink(viewer));
    }

    /** ✘ Survival has no dimension x. Browse */
    public static Component noDimension(Viewer viewer, Server server, String dimension) {
        return Chat.error(translatable("wp.error.remote.no_dimension", RemoteRefs.serverName(viewer, server),
                text(RemoteRefs.truncate(dimension))), browse(viewer, ListTarget.remote(server.id().value(), null, null)));
    }

    /** ✘ Survival has no list x in Overworld. Browse */
    public static Component noList(Viewer viewer, Server server, String dimension, String list) {
        return Chat.error(translatable("wp.error.remote.no_list", RemoteRefs.serverName(viewer, server),
                        text(RemoteRefs.truncate(list)), DimensionStyle.remote(viewer).name(dimension)),
                browse(viewer, ListTarget.remote(server.id().value(), dimension, null)));
    }

    /** ✘ No waypoint called x in Farms. Open Farms */
    public static Component noWaypoint(Viewer viewer, Server server, String dimension, WaypointList list, String waypoint) {
        Component name = RemoteRefs.label(list.displayName(), list.name());
        return Chat.error(translatable("wp.error.no_waypoint", text(RemoteRefs.truncate(waypoint)), name),
                Chat.control(viewer, translatable("wp.open", name), AQUA,
                        Click.run(ListTarget.remote(server.id().value(), dimension, list.name()).command(ListQuery.DEFAULT)),
                        RemoteRefs.listTooltip(list, "wp.hint.open")));
    }

    public static Component distanceUnavailable() {
        return Errors.of("wp.error.remote.distance");
    }

    private static WaypointQueryEngine.Query engineQuery(ListQuery query) {
        return new WaypointQueryEngine.Query(query.search(), query.sort(), null, null, query.descending());
    }

    private static Component treeView(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals,
                                      List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        List<List<WaypointListDisplayModel.DisplayList>> pages = ListScreen.treePages(groups, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.add(RemoteRefs.listLink(dims, server, dimension, list, WHITE, ListQuery.DEFAULT));
            List<SimpleWaypoint> rows = group.waypoints();
            if (rows.isEmpty()) {
                lines.line(text("  "), translatable("wp.list.empty", GRAY).decorate(TextDecoration.ITALIC));
            }
            boolean collapsed = rows.size() > ListScreen.COLLAPSE_ABOVE;
            for (SimpleWaypoint waypoint : collapsed ? rows.subList(0, ListScreen.PREVIEW) : rows) {
                lines.line(text("  "), RemoteRefs.row(dims, server, dimension, list, waypoint, false));
            }
            if (collapsed) {
                lines.line(text("  "), collapsedMore(dims, server, dimension, list, rows.size(), shown, pageLimit));
            }
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(), Chat.join(
                        Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints()))),
                viewRow(dims, server, dimension, totals, shown, ListView.TREE),
                totals.waypoints() > 0 ? sortRow(viewer, target, shown) : null);
        return lines.build();
    }

    private static Component listsView(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals,
                                       List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                       ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        List<List<WaypointListDisplayModel.DisplayList>> pages = Paging.bySize(groups, pageLimit + 5);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.line(RemoteRefs.listLink(dims, server, dimension, list, WHITE, ListQuery.DEFAULT), Chat.SEPARATOR,
                    text(String.valueOf(list.size()), GRAY));
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", totals.lists()))),
                viewRow(dims, server, dimension, totals, shown, ListView.LISTS));
        return lines.build();
    }

    private static Component flatView(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals,
                                      WaypointQueryEngine.QueryResult result, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        List<WaypointListDisplayModel.DisplayWaypoint> rows = WaypointListDisplayModel.build(result, false).flatWaypoints();
        List<List<WaypointListDisplayModel.DisplayWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayWaypoint row : pages.get(shown.page() - 1)) {
            lines.add(RemoteRefs.row(dims, server, dimension, row.sourceList(), row.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                viewRow(dims, server, dimension, totals, shown, ListView.FLAT), sortRow(viewer, target, shown));
        return lines.build();
    }

    /** "… 4 more" opening the whole list on its server, keeping the search and the sort. */
    private static Component collapsedMore(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                           int rows, ListQuery shown, int pageLimit) {
        Viewer viewer = dims.viewer();
        String command = ListTarget.remote(server.id().value(), dimension, list.name()).command(shown.withView(ListView.DEFAULT));
        Component label = Chat.concat(text(Chat.ELLIPSIS + " "), translatable("wp.more.rows", text(rows - ListScreen.PREVIEW)));
        if (viewer.plainText()) {
            return translatable("wp.plain.continue", label, text(command));
        }
        return Chat.link(viewer, label, AQUA, Click.run(command),
                Tooltip.of("wp.open", RemoteRefs.label(list.displayName(), list.name()))
                        .line(translatable("wp.list.all_rows", Chat.count("wp.count.waypoint", rows), text(pageLimit))));
    }

    /** Lists · Tree · Flat · Search; Flat and Search only when there are waypoints. */
    private static @Nullable Component viewRow(DimensionStyle dims, Server server, String dimension,
                                               ListScreen.Totals totals, ListQuery shown, ListView current) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        boolean waypoints = totals.waypoints() > 0;
        return ListControls.viewRow(viewer, target, shown, current,
                waypoints ? List.of(ListView.LISTS, ListView.TREE, ListView.FLAT) : List.of(ListView.LISTS, ListView.TREE),
                waypoints ? Collections.singletonList(ListControls.search(viewer, target,
                        translatable("wp.search.in", dims.name(dimension)))) : List.of());
    }

    private static @Nullable Component sortRow(Viewer viewer, ListTarget target, ListQuery shown) {
        return ListControls.sortRow(viewer, target, shown, SORTS, "wp.sort.default.published", null);
    }

    /** ● Survival ⏷, the name opening the server picker. */
    private static Component serverTitle(Viewer viewer, Server server) {
        Component name = viewer.plainText() ? RemoteRefs.serverName(viewer, server)
                : Chat.link(viewer, Chat.concat(RemoteRefs.serverName(viewer, server), text(" " + Chat.PICKER)), GOLD,
                Click.run("/wp remote"), RemoteRefs.serverTooltip(server, "wp.hint.choose_server"));
        return Chat.concat(RemoteRefs.dot(viewer, server), text(" "), name);
    }

    /** ● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints */
    private static Component dimensionHeader(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals) {
        Viewer viewer = dims.viewer();
        Component dimensionName = viewer.plainText() ? dims.name(dimension)
                : Chat.link(viewer, Chat.concat(DimensionStyle.displayName(dimension), text(" " + Chat.PICKER)),
                dims.color(dimension), Click.run(ListTarget.remote(server.id().value(), null, null).command(ListQuery.DEFAULT)),
                dims.tooltip(dimension, DimensionStyle.counts(totals.waypoints(), totals.lists()), "wp.hint.choose_dimension"));
        Component summary = totals.lists() == 0 ? null : Chat.concat(text("  "), Chat.colored(Chat.join(
                Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints())), GRAY));
        return Chat.concat(serverTitle(viewer, server), Chat.CRUMB, dimensionName, summary);
    }

    /** ● Survival › Overworld: the server opens its view and the dimension its lists. */
    private static Component crumbs(DimensionStyle dims, Server server, String dimension) {
        Viewer viewer = dims.viewer();
        Component serverName = Chat.link(viewer, RemoteRefs.serverName(viewer, server), WHITE,
                Click.run(ListTarget.remote(server.id().value(), null, null).command(ListQuery.DEFAULT)),
                RemoteRefs.serverTooltip(server, "wp.hint.open"));
        return Chat.concat(RemoteRefs.dot(viewer, server), text(" "), serverName, Chat.CRUMB,
                dimensionLink(dims, server, dimension));
    }

    private static Component allTitle(Viewer viewer) {
        if (viewer.plainText()) {
            return translatable("wp.remote.all", GOLD);
        }
        return Chat.link(viewer, Chat.concat(translatable("wp.remote.all"), text(" " + Chat.PICKER)), GOLD,
                Click.run("/wp remote"), Tooltip.of("wp.remote.choose"));
    }

    /** Creative Plots ● · 4, or Survival ● (continued) at the top of a page. */
    private static Component serverHeading(Viewer viewer, Server server, boolean continued) {
        return Chat.concat(RemoteRefs.serverLink(viewer, server), text(" "), RemoteRefs.dot(viewer, server),
                continued ? Chat.concat(text(" "), translatable("wp.all.continued", DARK_GRAY))
                        : Chat.concat(Chat.SEPARATOR, RemoteRefs.detail(viewer, server)));
    }

    /** Overworld · 14, indented under a server in All servers, or Overworld (continued). */
    private static Component dimensionRow(DimensionStyle dims, Server server, String dimension, String indent,
                                          boolean continued) {
        List<WaypointList> lists = Objects.requireNonNull(server.lists(dimension));
        int waypoints = lists.stream().mapToInt(WaypointList::size).sum();
        return Chat.concat(text(indent), dimensionLink(dims, server, dimension), continued
                ? Chat.concat(text(" "), translatable("wp.all.continued", DARK_GRAY))
                : Chat.concat(Chat.SEPARATOR, text(String.valueOf(waypoints), GRAY)));
    }

    /** A dimension name in its colour, opening its lists on that server. */
    private static Component dimensionLink(DimensionStyle dims, Server server, String dimension) {
        Viewer viewer = dims.viewer();
        List<WaypointList> lists = Objects.requireNonNull(server.lists(dimension));
        Component label = viewer.plainText() ? dims.name(dimension) : DimensionStyle.displayName(dimension);
        return Chat.link(viewer, label, dims.color(dimension),
                Click.run(ListTarget.remote(server.id().value(), dimension, null).command(ListQuery.DEFAULT)),
                dims.tooltip(dimension, DimensionStyle.counts(lists.stream().mapToInt(WaypointList::size).sum(),
                        lists.size()), "wp.hint.open"));
    }

    private static List<String> ordered(DimensionStyle dims, Server server) {
        return server.dimensions().keySet().stream().sorted(dims.order()).toList();
    }

    /** Pages of at most budget lines; a heading never ends a page, since its first row would leave it. */
    private static List<List<Line>> pageBlocks(List<Line> blocks, int budget) {
        List<List<Line>> pages = new ArrayList<>();
        List<Line> page = new ArrayList<>();
        for (Line block : blocks) {
            boolean full = page.size() >= budget || block.heading() && page.size() >= budget - 1;
            if (full && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
            }
            page.add(block);
        }
        pages.add(page);
        return pages;
    }

    private static int rowsAfter(List<List<Line>> pages, int page) {
        return (int) pages.subList(page, pages.size()).stream().flatMap(List::stream).filter(line -> !line.heading()).count();
    }

    private static int headingsAfter(List<List<Line>> pages, int page) {
        return (int) pages.subList(page, pages.size()).stream().flatMap(List::stream).filter(Line::heading).count();
    }

    private static @Nullable Component serversLink(Viewer viewer) {
        return Chat.control(viewer, translatable("wp.remote.servers"), AQUA, Click.run("/wp remote"),
                Tooltip.of("wp.remote.choose"));
    }

    private static @Nullable Component browse(Viewer viewer, ListTarget target) {
        return Chat.control(viewer, translatable("wp.remote.browse"), AQUA, Click.run(target.command(ListQuery.DEFAULT)),
                Tooltip.of("wp.remote.browse.tooltip"));
    }

    private static Component property(String field, Component value) {
        return translatable("wp.details.property", GRAY, translatable("wp.details." + field), Chat.colored(value, WHITE));
    }

    private static Component none() {
        return translatable("wp.details.none", DARK_GRAY).decorate(TextDecoration.ITALIC);
    }
}
```

- [ ] **Step 8: Run the builder tests**

Run the Step 5 command. Expected: PASS.

- [ ] **Step 9: The remote command**

Replace the whole of `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java` with:

```java
package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.handoff.RemoteTeleportInitiator;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.RemoteScreens;
import _959.server_waypoint.util.StringCommandBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static net.kyori.adventure.text.Component.*;

/** Vanilla-safe remote commands. Suggestions and target resolution use only the bounded local replica. */
final class RemoteWaypointCommand<S> {
    private static final String SERVER = "remote server", DIMENSION = "remote dimension", LIST = "remote list", WAYPOINT = "remote waypoint";
    private final Supplier<RemoteCatalogStore> store;
    private final BiConsumer<S, Component> send, error;
    private final IntSupplier defaultLimit;
    private final Predicate<S> canList, canTeleport;
    private final RemoteTeleportInitiator<S> teleport;
    private final Function<S, Component> helpScreen;
    private final Function<S, Viewer> viewer;

    RemoteWaypointCommand(Supplier<RemoteCatalogStore> store, BiConsumer<S, Component> send,
                          BiConsumer<S, Component> error, IntSupplier defaultLimit, Predicate<S> canList,
                          Predicate<S> canTeleport, RemoteTeleportInitiator<S> teleport,
                          Function<S, Component> helpScreen, Function<S, Viewer> viewer) {
        this.canList = Objects.requireNonNull(canList, "canList");
        this.canTeleport = Objects.requireNonNull(canTeleport, "canTeleport");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.helpScreen = Objects.requireNonNull(helpScreen, "helpScreen");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.store = store; this.send = send; this.error = error; this.defaultLimit = defaultLimit;
    }
    LiteralArgumentBuilder<S> build() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal("remote").requires(this::canUse)
                .executes(context -> picker(context.getSource(), 1));
        root.then(LiteralArgumentBuilder.<S>literal(PAGE_COMMAND).requires(canList)
                .then(RequiredArgumentBuilder.<S, Integer>argument(PAGE_NUMBER_ARG, integer(1))
                        .executes(context -> picker(context.getSource(), getInteger(context, PAGE_NUMBER_ARG)))));
        LiteralArgumentBuilder<S> lists = LiteralArgumentBuilder.<S>literal("list").requires(canList); configure(lists, 0);
        RequiredArgumentBuilder<S, String> server = argument(SERVER, string()); configure(server, 1);
        server.suggests((context, builder) -> suggest(context, builder, 0));
        RequiredArgumentBuilder<S, String> dimension = argument(DIMENSION, string()); configure(dimension, 2);
        dimension.suggests((context, builder) -> suggest(context, builder, 1));
        RequiredArgumentBuilder<S, String> list = argument(LIST, string()); configure(list, 3);
        list.suggests((context, builder) -> suggest(context, builder, 2));
        root.then(lists.then(server.then(dimension.then(list))));
        RequiredArgumentBuilder<S, String> detailsServer = argument(SERVER, string());
        RequiredArgumentBuilder<S, String> detailsDimension = argument(DIMENSION, string());
        RequiredArgumentBuilder<S, String> detailsList = argument(LIST, string());
        RequiredArgumentBuilder<S, String> detailsWaypoint = argument(WAYPOINT, string());
        detailsServer.suggests((context, builder) -> suggest(context, builder, 0));
        detailsDimension.suggests((context, builder) -> suggest(context, builder, 1));
        detailsList.suggests((context, builder) -> suggest(context, builder, 2));
        detailsWaypoint.suggests((context, builder) -> suggest(context, builder, 3)).executes(this::details);
        root.then(LiteralArgumentBuilder.<S>literal("details").requires(canList)
                .then(detailsServer.then(detailsDimension.then(detailsList.then(detailsWaypoint)))));
        RequiredArgumentBuilder<S, String> tpServer = argument(SERVER, string());
        RequiredArgumentBuilder<S, String> tpDimension = argument(DIMENSION, string());
        RequiredArgumentBuilder<S, String> tpList = argument(LIST, string());
        RequiredArgumentBuilder<S, String> tpWaypoint = argument(WAYPOINT, string());
        tpServer.suggests((context, builder) -> suggest(context, builder, 0, true));
        tpDimension.suggests((context, builder) -> suggest(context, builder, 1, true));
        tpList.suggests((context, builder) -> suggest(context, builder, 2, true));
        tpWaypoint.suggests((context, builder) -> suggest(context, builder, 3, true)).executes(this::teleport);
        return root.then(LiteralArgumentBuilder.<S>literal("tp").requires(canTeleport)
                .then(tpServer.then(tpDimension.then(tpList.then(tpWaypoint)))));
    }
    boolean canList(S source) { return canList.test(source); }
    boolean canTeleport(S source) { return canTeleport.test(source); }
    boolean canUse(S source) { return canList.test(source) || canTeleport.test(source); }
    int help(S source) {
        if (!canUse(source)) return 0;
        send.accept(source, helpScreen.apply(source));
        return Command.SINGLE_SUCCESS;
    }
    /** /wp remote: the server picker; readers who may only teleport get the remote help. */
    private int picker(S source, int page) {
        if (!canList.test(source)) return help(source);
        send.accept(source, RemoteScreens.picker(viewer.apply(source), RemoteCatalogQuery.servers(store.get().snapshot()),
                page, defaultLimit.getAsInt()));
        return Command.SINGLE_SUCCESS;
    }
    private int teleport(CommandContext<S> context) {
        S source = context.getSource();
        if (!canTeleport.test(source)) return fail(source, Result.UNAUTHORIZED);
        var cached = store.get().snapshot();
        var entry = cached.entrySet().stream().filter(value -> value.getKey().value().equals(getString(context, SERVER))).findFirst().orElse(null);
        if (entry == null) return fail(source, Result.UNAVAILABLE);
        var view = entry.getValue();
        if (view.state() == RemoteCatalogState.UNAUTHORIZED) return fail(source, Result.UNAUTHORIZED);
        if (view.state() == RemoteCatalogState.STALE) return fail(source, Result.STALE_CATALOG);
        if (view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) return fail(source, Result.UNAVAILABLE);
        String dimension = getString(context, DIMENSION), listName = getString(context, LIST), waypoint = getString(context, WAYPOINT);
        var list = view.snapshot().dimensions().getOrDefault(dimension, Map.of()).get(listName);
        if (list == null || !list.waypoints().containsKey(waypoint)) return fail(source, Result.NOT_FOUND);
        var selection = new RemoteTeleportInitiator.Selection(new RemoteWaypointKey(entry.getKey(), dimension, listName, waypoint),
                view.snapshot().catalogRevision(), list.listRevision());
        send.accept(source, translatable("waypoint.remote.tp.preparing"));
        teleport.initiate(source, selection, result -> {
            if (result == Result.SUCCESS) send.accept(source, translatable("waypoint.remote.tp.success"));
            else fail(source, result);
        });
        return Command.SINGLE_SUCCESS;
    }
    private int fail(S source, Result result) {
        error.accept(source, translatable("waypoint.remote.tp." + result.name().toLowerCase(Locale.ROOT)));
        return 0;
    }
    private void configure(ArgumentBuilder<S, ?> node, int depth) {
        new ListCommandOptions<S>((mode, reversed, view) -> context -> execute(context, depth, mode, reversed, view), null)
                .configure(node);
    }
    private CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, int depth) {
        return suggest(context, builder, depth, false);
    }
    private CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, int depth, boolean tp) {
        // Brigadier supplies the outer context for redirected commands such as /execute ... run wp.
        context = context.getLastChild();
        if (!(tp ? canTeleport : canList).test(context.getSource())) return Suggestions.empty();
        Map<RemoteServerId, CatalogReceiver.View> cached = store.get().snapshot();
        Collection<String> candidates = List.of();
        if (depth == 0) candidates = cached.entrySet().stream().filter(entry -> entry.getValue().state() != RemoteCatalogState.UNAUTHORIZED)
                .map(entry -> entry.getKey().value()).toList();
        else {
            String id = getString(context, SERVER);
            CatalogReceiver.View view = cached.entrySet().stream().filter(entry -> entry.getKey().value().equals(id)).map(Map.Entry::getValue).findFirst().orElse(null);
            if (view != null && view.snapshot() != null && view.state() != RemoteCatalogState.UNAUTHORIZED && view.state() != RemoteCatalogState.UNAVAILABLE) {
                if (depth == 1) candidates = view.snapshot().dimensions().keySet();
                else {
                    var lists = view.snapshot().dimensions().getOrDefault(getString(context, DIMENSION), Map.of());
                    if (depth == 2) candidates = lists.keySet();
                    else {
                        var selected = lists.get(getString(context, LIST));
                        if (selected != null) candidates = selected.waypoints().keySet();
                    }
                }
            }
        }
        String remaining = builder.getRemaining();
        String prefix;
        try {
            prefix = new com.mojang.brigadier.StringReader(remaining).readString();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException incomplete) {
            prefix = remaining.startsWith("\"") ? remaining.substring(1).replace("\\\"", "\"").replace("\\\\", "\\") : remaining;
        }
        final String match = prefix;
        candidates.stream().sorted().filter(value -> value.startsWith(match)).map(StringCommandBuilder::escapeListName)
                .filter(value -> value.length() <= 256).limit(100).forEach(builder::suggest);
        return builder.buildFuture();
    }
    /** /wp remote list [<server> [<dimension> [<list>]]] with the list options. */
    private int execute(CommandContext<S> context, int depth, WaypointSorting.SortMode mode, boolean reversed, ListView view) {
        S source = context.getSource();
        if (!canList.test(source)) return 0;
        if (mode == WaypointSorting.SortMode.DISTANCE) {
            error.accept(source, RemoteScreens.distanceUnavailable());
            return 0;
        }
        Viewer reader = viewer.apply(source);
        ListQuery query = new ListQuery(optionalString(context, SEARCH_QUERY_ARG), mode, reversed, view,
                optionalInt(context, PAGE_NUMBER_ARG, 1), optionalLimit(context));
        int pageLimit = query.pageLimit(defaultLimit.getAsInt());
        var catalogs = store.get().snapshot();
        if (depth == 0) {
            List<RemoteCatalogQuery.Server> servers = RemoteCatalogQuery.servers(catalogs);
            send.accept(source, query.searching() ? RemoteScreens.search(reader, null, servers, query, pageLimit)
                    : RemoteScreens.allServers(reader, servers, query, pageLimit));
            return Command.SINGLE_SUCCESS;
        }
        String id = getString(context, SERVER);
        RemoteCatalogQuery.Server server = RemoteCatalogQuery.server(catalogs, id).orElse(null);
        if (server == null) {
            error.accept(source, RemoteScreens.noServer(reader, id));
            return 0;
        }
        if (depth == 1 || !server.readable()) {
            send.accept(source, query.searching() && server.readable()
                    ? RemoteScreens.search(reader, server, List.of(server), query, pageLimit)
                    : RemoteScreens.server(reader, server, query, pageLimit));
            return Command.SINGLE_SUCCESS;
        }
        String dimension = getString(context, DIMENSION);
        if (server.lists(dimension) == null) {
            error.accept(source, RemoteScreens.noDimension(reader, server, dimension));
            return 0;
        }
        if (depth == 2) {
            send.accept(source, RemoteScreens.dimension(reader, server, dimension, query, pageLimit));
            return Command.SINGLE_SUCCESS;
        }
        String listName = getString(context, LIST);
        WaypointList list = server.list(dimension, listName);
        if (list == null) {
            error.accept(source, RemoteScreens.noList(reader, server, dimension, listName));
            return 0;
        }
        send.accept(source, RemoteScreens.list(reader, server, dimension, list, query, pageLimit));
        return Command.SINGLE_SUCCESS;
    }
    /** /wp remote details <server> <dimension> <list> <waypoint>: read-only details. */
    private int details(CommandContext<S> context) {
        S source = context.getSource();
        if (!canList.test(source)) return 0;
        Viewer reader = viewer.apply(source);
        String id = getString(context, SERVER), dimension = getString(context, DIMENSION),
                listName = getString(context, LIST), name = getString(context, WAYPOINT);
        RemoteCatalogQuery.Server server = RemoteCatalogQuery.server(store.get().snapshot(), id).orElse(null);
        if (server == null) {
            error.accept(source, RemoteScreens.noServer(reader, id));
            return 0;
        }
        if (!server.readable()) {
            send.accept(source, RemoteScreens.server(reader, server, ListQuery.DEFAULT, defaultLimit.getAsInt()));
            return 0;
        }
        if (server.lists(dimension) == null) {
            error.accept(source, RemoteScreens.noDimension(reader, server, dimension));
            return 0;
        }
        WaypointList list = server.list(dimension, listName);
        if (list == null) {
            error.accept(source, RemoteScreens.noList(reader, server, dimension, listName));
            return 0;
        }
        SimpleWaypoint waypoint = list.getWaypointByName(name);
        if (waypoint == null) {
            error.accept(source, RemoteScreens.noWaypoint(reader, server, dimension, list, name));
            return 0;
        }
        send.accept(source, RemoteScreens.details(reader, server, dimension, list, waypoint));
        return Command.SINGLE_SUCCESS;
    }
    private static <S> int optionalInt(CommandContext<S> context, String name, int fallback) {
        try { return getInteger(context, name); } catch (IllegalArgumentException missing) { return fallback; }
    }
    private static <S> @Nullable Integer optionalLimit(CommandContext<S> context) {
        try { return getInteger(context, PAGE_LIMIT_ARG); } catch (IllegalArgumentException missing) { return null; }
    }
    private static <S> String optionalString(CommandContext<S> context, String name) {
        try { return getString(context, name); } catch (IllegalArgumentException missing) { return ""; }
    }
}
```

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
                source -> HelpScreen.topic(this.viewer(source), HelpTopics.Topic.REMOTE, false));
```

with:

```java
                source -> HelpScreen.topic(this.viewer(source), HelpTopics.Topic.REMOTE, false), this::viewer);
```

- [ ] **Step 10: Rewrite the remote command tests**

Replace the whole of `common/src/test/java/_959/server_waypoint/command/RemoteWaypointCommandTest.java` with:

```java
package _959.server_waypoint.command;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.handoff.*;
import java.util.concurrent.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.protocol.*;
import _959.server_waypoint.crossserver.transport.*;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.util.StringCommandBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class RemoteWaypointCommandTest {
    private static final RemoteServerId A = new RemoteServerId("search"), B = new RemoteServerId("other");
    private static final String DIMENSION = "world \"quoted\"\\zone";
    private boolean allowed = true, tpAllowed;
    private final UUID playerId = UUID.randomUUID();
    private final CompletableFuture<ApplicationMessage> prepareReply = new CompletableFuture<>();
    private ApplicationMessage.PrepareHandoff preparation;
    private int transfers;
    private final SourceHandoffService<String> handoffs = new SourceHandoffService<>(new RemoteServerId("source"), new SourceHandoffService.Platform<>() {
        public boolean ownsThread(String source) { return true; }
        public UUID playerId(String source) { return playerId; }
        public boolean isCurrentPlayer(String source, UUID id) { return playerId.equals(id); }
        public boolean canTeleport(String source) { return tpAllowed; }
        public boolean execute(String source, Runnable task, Runnable retired) { task.run(); return true; }
    }, new SourceHandoffService.Link() {
        public CompletionStage<ApplicationMessage> prepare(UUID id, ApplicationMessage.PrepareHandoff request) {
            preparation = request; return prepareReply;
        }
        public CompletionStage<ApplicationMessage.Result> transfer(UUID id, ApplicationMessage.HandoffBinding binding) {
            transfers++; return CompletableFuture.completedFuture(ApplicationMessage.Result.SUCCESS);
        }
        public void cancel(UUID id, ApplicationMessage.CancelHandoff cancel) { }
    });
    @AfterEach void closeHandoffs() { handoffs.close(); }

    private final AtomicLong time = new AtomicLong();
    private final CatalogIndex index = new CatalogIndex(new CatalogCacheLimits(4, 100000, 400000, 10), time::get);
    private final Object owner = new Object();
    private final List<Component> messages = new ArrayList<>(), errors = new ArrayList<>();
    private final CommandDispatcher<String> dispatcher = new CommandDispatcher<>();

    @BeforeEach void setup() throws Exception {
        var commands = new RemoteWaypointCommand<String>(() -> { assertTrue(allowed || tpAllowed, "Denied readers must not access the catalog"); return new RemoteCatalogStore(index); }, (source, text) -> messages.add(text),
                (source, text) -> errors.add(text), () -> 5, source -> allowed, source -> tpAllowed, handoffs,
                source -> Component.text("Remote help"), this::viewer);
        dispatcher.register(LiteralArgumentBuilder.<String>literal("wp").then(commands.build()));
        Map<String, RemoteWaypointSnapshot> waypoints = new HashMap<>();
        for (int i = 0; i < 12; i++) waypoints.put("base " + i, waypoint("Display " + i, i));
        publish(A, Map.of(DIMENSION, Map.of("search", new RemoteListSnapshot("Public display", new RemoteRevision(1), waypoints),
                "", new RemoteListSnapshot("Empty identity", new RemoteRevision(1), Map.of()))));
        publish(B, Map.of(DIMENSION, Map.of("search", new RemoteListSnapshot("Other display", new RemoteRevision(1),
                Map.of("base 0", waypoint("Other waypoint", 0))))));
    }
    /** "plain" reads plain text like the console; every other source reads chat. */
    private Viewer viewer(String source) {
        Set<Viewer.Permission> permissions = EnumSet.noneOf(Viewer.Permission.class);
        if (allowed) permissions.add(Viewer.Permission.REMOTE_LIST);
        if (tpAllowed) permissions.add(Viewer.Permission.REMOTE_TP);
        return new Viewer(permissions, false, source.equals("plain"), null, null, 0F);
    }
    private static RemoteWaypointSnapshot waypoint(String display, int position) {
        return new RemoteWaypointSnapshot(display, "B", new WaypointPos(position, 64, 0), position % 2 == 0 ? 0xFF0000 : 0x00FF00,
                0, false, List.of("village"), "description", null);
    }
    private void publish(RemoteServerId id, Map<String, Map<String, RemoteListSnapshot>> data) throws Exception {
        index.connected(id, owner, TransportMode.NOISE_KK, ProtocolLimits.DEFAULT);
        RemoteCatalogSnapshot snapshot = new RemoteCatalogSnapshot(id, new RemoteRevision(1), data, Instant.EPOCH);
        UUID request = UUID.randomUUID(); byte[] bytes = new ApplicationCodec(ProtocolLimits.DEFAULT).encodeCatalog(snapshot);
        index.receive(id, owner, received(request, new ApplicationMessage.CatalogMetadata(id, "Server " + id.value(), new RemoteRevision(1), CatalogExportPolicy.PUBLIC, "minecraft:compass"), null));
        index.receive(id, owner, received(request, new ApplicationMessage.CatalogSnapshot(id, new RemoteRevision(1), UUID.randomUUID(), 0,
                bytes.length, new ApplicationMessage.Bytes(bytes)), snapshot));
    }
    private static TcpChannel.Received received(UUID request, ApplicationMessage message, RemoteCatalogSnapshot catalog) {
        return new TcpChannel.Received(new ApplicationEnvelope(0, request, message), catalog);
    }
    private static String quote(String value) { return StringCommandBuilder.escapeListName(value); }
    private String target() { return "wp remote list " + quote(A.value()) + " " + quote(DIMENSION) + " \"search\""; }
    private String tpTarget() { return target().replace("remote list", "remote tp") + " " + quote("base 0"); }
    private String detailsTarget() { return target().replace("remote list", "remote details") + " " + quote("base 0"); }
    private Component last() { return messages.get(messages.size() - 1); }
    private List<String> suggestions(String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, "console")).join().getList().stream().map(value -> value.getText()).toList();
    }
    private static List<Component> components(Component root) {
        List<Component> all = new ArrayList<>(); all.add(root);
        root.children().forEach(child -> all.addAll(components(child))); return all;
    }
    private static String text(Component root) {
        return components(root).stream().filter(TextComponent.class::isInstance).map(TextComponent.class::cast).map(TextComponent::content).reduce("", String::concat);
    }
    private static List<String> keys(Component root) {
        return components(root).stream().filter(TranslatableComponent.class::isInstance).map(TranslatableComponent.class::cast).map(TranslatableComponent::key).toList();
    }
    private static List<String> clicks(Component root) {
        return components(root).stream().map(Component::clickEvent).filter(Objects::nonNull).map(ClickEvent::value).toList();
    }
    @Test void suggestionsUseOnlyLocalCacheAndRoundTripReservedQuotedAndEmptyIdentities() throws Exception {
        assertTrue(suggestions("wp remote list ").contains("\"search\""));
        assertTrue(suggestions("wp remote list \"search\" ").contains(quote(DIMENSION)));
        assertTrue(suggestions("wp remote list \"search\" " + quote(DIMENSION) + " ").containsAll(List.of("\"search\"", "\"\"")));
        assertTrue(suggestions("wp remote list \"se").contains("\"search\""));
        assertEquals(1, dispatcher.execute(target(), "console"));
        assertEquals(1, dispatcher.execute("wp remote list \"search\" " + quote(DIMENSION) + " \"\"", "console"));
        assertTrue(ChatAssert.render(last()).endsWith("No waypoints yet."));
    }
    @Test void redirectedSuggestionsResolveArgumentsAtEveryRemoteDepth() {
        tpAllowed = true;
        var execute = dispatcher.register(LiteralArgumentBuilder.<String>literal("execute"));
        execute.addChild(LiteralArgumentBuilder.<String>literal("as")
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<String, String>argument("target",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                        .fork(execute, context -> List.of("player"))).build());
        execute.addChild(LiteralArgumentBuilder.<String>literal("run").redirect(dispatcher.getRoot()).build());
        for (String prefix : List.of("execute as 7c00 run ", "execute as 7c00 run execute as 7c00 run ")) {
            for (String command : List.of("list", "details", "tp")) {
                String input = "wp remote " + command + " ";
                assertEquals(suggestions(input), suggestions(prefix + input));
                input += quote(A.value()) + " ";
                assertTrue(suggestions(input).contains(quote(DIMENSION)));
                assertEquals(suggestions(input), suggestions(prefix + input));
                input += quote(DIMENSION) + " ";
                assertTrue(suggestions(input).contains("\"search\""));
                assertEquals(suggestions(input), suggestions(prefix + input));
                if (!command.equals("list")) {
                    input += "\"search\" ";
                    assertTrue(suggestions(input).contains(quote("base 0")));
                    assertEquals(suggestions(input), suggestions(prefix + input));
                    assertEquals(suggestions(input + "\"base"), suggestions(prefix + input + "\"base"));
                }
            }
        }
    }
    @Test void generatedLinksKeepTheExactScopeInTheCanonicalOptionOrder() throws Exception {
        assertEquals(1, dispatcher.execute(target() + " search village sort name order descending limit 2", "console"));
        String next = clicks(last()).stream().filter(value -> value.endsWith(" page 2")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search village sort name order descending limit 2 page 2", next);
        assertEquals(1, dispatcher.execute(next.substring(1), "console"));
        assertFalse(ChatAssert.render(last()).contains("Other waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.startsWith("/wp remote list ") || value.startsWith("/wp remote details ")));
        for (String suffix : List.of("search village", "sort color order ascending search village", "page 1 limit 3 view tree search village",
                "sort name page 1 limit 2 view flat", "limit 3 search village", "view flat search village", "view lists")) {
            assertEquals(1, dispatcher.execute(target() + " " + suffix, "console"));
        }
    }
    @Test void listControlsKeepTheScopeAndOfferNoDistanceSorting() throws Exception {
        dispatcher.execute(target() + " search village sort color order descending limit 2 page 2", "console");
        Component output = last();
        assertTrue(ChatAssert.render(output).contains("Sort Default · Name · Color ↓"));
        assertFalse(ChatAssert.render(output).contains("Distance"));
        String search = ChatAssert.suggestions(output).stream().filter(value -> value.endsWith(" search ")).findFirst().orElseThrow();
        assertEquals("/" + target() + " search ", search);
        assertEquals(1, dispatcher.execute(search.substring(1) + "village", "console"));
        for (String command : ChatAssert.runCommands(output)) {
            assertEquals(1, dispatcher.execute(command.substring(1), "console"), command);
        }
    }
    @Test void remoteDimensionsAreNamedAndColouredByTheirIds() throws Exception {
        publish(new RemoteServerId("colored"), Map.of("minecraft:the_nether", Map.of("list",
                new RemoteListSnapshot("list", new RemoteRevision(1), Map.of("point", waypoint("point", 0))))));
        for (String command : List.of("wp remote list colored", "wp remote list colored " + quote("minecraft:the_nether"),
                "wp remote details colored " + quote("minecraft:the_nether") + " list point")) {
            assertEquals(1, dispatcher.execute(command, "console"));
            assertEquals(NamedTextColor.RED, ChatAssert.colorOf(last(), "Nether"), command);
        }
    }
    @Test void initialsTeleportOnlyWhenAvailableAndPermittedWhileNamesOpenDetails() throws Exception {
        tpAllowed = true;
        dispatcher.execute(target(), "player");
        Component output = last();
        assertEquals("/" + tpTarget(), ChatAssert.clickOf(output, "[B]"));
        assertEquals("/" + detailsTarget(), ChatAssert.clickOf(output, "Display 0"));
        assertTrue(ChatAssert.tooltipOf(output, "Display 0").contains("description"));
        assertEquals(1, dispatcher.execute(tpTarget(), "player"));
        assertNotNull(preparation);
        tpAllowed = false;
        dispatcher.execute(target(), "player");
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        tpAllowed = true;
        index.disconnected(A, owner);
        dispatcher.execute(target(), "player");
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        assertEquals("Off while Server search is stale", ChatAssert.tooltipOf(last(), "[B]"));
    }
    @Test void detailsResolveExactCachedIdentitiesAndRespectAvailabilityAndPermissions() throws Exception {
        dispatcher.execute(target(), "console");
        List<String> details = clicks(last()).stream().filter(value -> value.startsWith("/wp remote details ")).toList();
        assertEquals(5, details.size());
        for (String command : details) assertEquals(1, dispatcher.execute(command.substring(1), "console"));
        String rendered = ChatAssert.render(last());
        assertTrue(rendered.contains("Position: ") && rendered.contains("Keywords: village")
                && rendered.contains("Description: description"));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        String waypointDetails = details.get(0).substring(1);
        tpAllowed = true;
        dispatcher.execute(waypointDetails, "player");
        assertTrue(ChatAssert.runCommands(last()).stream().anyMatch(value -> value.startsWith("/wp remote tp ")));
        index.disconnected(A, owner);
        dispatcher.execute(waypointDetails, "player");
        assertEquals(NamedTextColor.DARK_GRAY, ChatAssert.colorOf(last(), "[Teleport]"));
        assertTrue(ChatAssert.runCommands(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ")));
        time.set(11_000_000); index.maintain();
        assertEquals(0, dispatcher.execute(waypointDetails, "player"));
        assertFalse(ChatAssert.render(last()).contains("description"));
        var parsed = dispatcher.parse(waypointDetails, "player");
        allowed = false;
        int count = messages.size();
        assertEquals(0, dispatcher.execute(parsed));
        assertEquals(count, messages.size());
    }
    @Test void longIdentitiesNeverProduceTruncatedOrOversizedActions() throws Exception {
        String longList = "x".repeat(240);
        publish(new RemoteServerId("long"), Map.of(DIMENSION, Map.of(longList,
                new RemoteListSnapshot("Long list", new RemoteRevision(1), Map.of("waypoint", waypoint("Long waypoint", 0))))));
        tpAllowed = true;
        dispatcher.execute("wp remote list long " + quote(DIMENSION) + " " + quote(longList), "player");
        assertTrue(ChatAssert.render(last()).contains("Long waypoint"));
        assertTrue(clicks(last()).stream().allMatch(value -> value.length() <= 256));
        assertTrue(clicks(last()).stream().noneMatch(value -> value.startsWith("/wp remote tp ") || value.startsWith("/wp remote details ")));
    }
    @Test void staleUnreachableAndEmptyServersStayDistinct() throws Exception {
        index.disconnected(A, owner);
        dispatcher.execute(target(), "console");
        assertTrue(ChatAssert.render(last()).contains("Display"));
        assertEquals("Stale\nTeleporting is off until it refreshes", ChatAssert.tooltipOf(last(), "●"));
        time.set(11_000_000); index.maintain();
        dispatcher.execute(target(), "console");
        assertEquals(List.of("● Server search ⏷", "Server search can't be reached right now. Servers"), ChatAssert.lines(last()));
        assertTrue(suggestions("wp remote list \"search\" ").stream().noneMatch(value -> value.equals(quote(DIMENSION))));
        publish(new RemoteServerId("empty"), Map.of());
        dispatcher.execute("wp remote list empty", "console");
        assertEquals(List.of("● Server empty ⏷", "Nothing published yet."), ChatAssert.lines(last()));
        assertEquals("Available", ChatAssert.tooltipOf(last(), "●"));
    }
    @Test void missingScopesAndDistanceAreErrorLines() throws Exception {
        assertEquals(0, dispatcher.execute("wp remote list missing", "console"));
        assertEquals("✘ No server called missing. Servers", ChatAssert.render(errors.get(0)));
        assertEquals(0, dispatcher.execute("wp remote list other missing", "console"));
        assertEquals("✘ Server other has no dimension missing. Browse", ChatAssert.render(errors.get(1)));
        assertEquals(0, dispatcher.execute("wp remote list other " + quote(DIMENSION) + " missing", "console"));
        assertTrue(ChatAssert.render(errors.get(2)).startsWith("✘ Server other has no list missing in "));
        assertEquals(0, dispatcher.execute(target() + " sort distance", "console"));
        assertEquals("✘ Remote waypoints can't be sorted by distance.", ChatAssert.render(errors.get(3)));
        assertEquals(1, dispatcher.execute(target() + " page 2147483647", "console"));
        assertTrue(ChatAssert.render(last()).startsWith("✘ Page 2147483647 does not exist; the last page is 3."));
    }
    @Test void remoteListsSearchAndSortLikeLocalLists() {
        var servers = RemoteCatalogQuery.servers(new RemoteCatalogStore(index).snapshot());
        WaypointQueryEngine.Query fuzzy = new WaypointQueryEngine.Query("vilage", WaypointSorting.SortMode.NAME, null, null, false);
        assertEquals(13, servers.stream().mapToInt(server -> WaypointQueryEngine.queryLists(server.dimensions(), fuzzy).waypointCount()).sum());
        var search = servers.stream().filter(server -> server.id().equals(A)).findFirst().orElseThrow();
        List<String> ascending = WaypointQueryEngine.queryLists(search.dimensions(), fuzzy).dimensions().get(0).lists().stream()
                .flatMap(list -> list.waypoints().stream()).map(SimpleWaypoint::name).toList();
        List<String> descending = WaypointQueryEngine.queryLists(search.dimensions(),
                        new WaypointQueryEngine.Query("vilage", WaypointSorting.SortMode.NAME, null, null, true))
                .dimensions().get(0).lists().stream().flatMap(list -> list.waypoints().stream()).map(SimpleWaypoint::name).toList();
        List<String> reversed = new ArrayList<>(ascending); Collections.reverse(reversed);
        assertEquals(reversed, descending);
        assertThrows(UnsupportedOperationException.class, () -> search.dimensions().clear());
    }
    @Test void thePickerAndItsPagesOfferOnlyReadOnlyActions() throws Exception {
        assertEquals(1, dispatcher.execute("wp remote", "console"));
        assertEquals("Remote servers  2 servers connected", ChatAssert.lines(last()).get(0));
        for (String command : ChatAssert.runCommands(last())) assertEquals(1, dispatcher.execute(command.substring(1), "console"), command);
        assertEquals(1, dispatcher.execute("wp remote page 1", "console"));
        var remote = dispatcher.getRoot().getChild("wp").getChild("remote");
        assertNull(remote.getChild("servers"));
        assertNotNull(remote.getChild("tp"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> dispatcher.execute(
                "wp remote details \"search\" " + quote(DIMENSION) + " \"search\"", "console"));
        allowed = false; tpAllowed = true;
        dispatcher.execute("wp remote", "player");
        assertEquals("Remote help", text(last()));
    }
    @Test void permissionDenialAndRevocationBlockCommandsAndCachedSuggestions() throws Exception {
        var parsed = dispatcher.parse(target(), "console");
        var suggestionParse = dispatcher.parse("wp remote list ", "console");
        allowed = false;
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("wp remote page 1", "console"));
        assertEquals(0, dispatcher.execute(parsed));
        assertTrue(dispatcher.getCompletionSuggestions(suggestionParse).join().getList().stream()
                .noneMatch(suggestion -> Set.of("\"search\"", "other").contains(suggestion.getText())));
        assertTrue(messages.isEmpty());
        assertTrue(errors.isEmpty());
        allowed = true;
        assertEquals(1, dispatcher.execute("wp remote page 1", "console"));
    }
    @Test void teleportCommandReachesFakeTransferOnlyAfterMatchingPreparation() throws Exception {
        tpAllowed = true;
        assertEquals(1, dispatcher.execute(tpTarget(), "player"));
        assertNotNull(preparation); assertEquals(0, transfers);
        assertEquals(new RemoteWaypointKey(A, DIMENSION, "search", "base 0"), preparation.target());
        assertEquals(new RemoteRevision(1), preparation.observedCatalogRevision());
        assertEquals(new RemoteRevision(1), preparation.observedListRevision());
        assertEquals(playerId, preparation.playerId());
        prepareReply.complete(new ApplicationMessage.HandoffPrepared(new ApplicationMessage.HandoffBinding(UUID.randomUUID(), playerId,
                preparation.source(), preparation.target(), preparation.action(), System.currentTimeMillis() + 15000)));
        assertEquals(1, transfers); assertTrue(keys(last()).contains("waypoint.remote.tp.success"));
    }
    @Test void teleportPreparationRejectionReportsExactReasonWithoutTransfer() throws Exception {
        tpAllowed = true; dispatcher.execute(tpTarget(), "player");
        prepareReply.complete(new ApplicationMessage.HandoffRejected(ApplicationMessage.Result.UNSUPPORTED));
        assertEquals(0, transfers); assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.unsupported"));
    }
    @Test void teleportPermissionsAreIndependentAndRecheckedAfterParsing() throws Exception {
        tpAllowed = true; allowed = false;
        var parsed = dispatcher.parse(tpTarget(), "player");
        var suggestionParse = dispatcher.parse("wp remote tp ", "player");
        dispatcher.execute("wp remote", "player");
        assertEquals("Remote help", text(last()));
        tpAllowed = false;
        assertEquals(0, dispatcher.execute(parsed)); assertNull(preparation);
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.unauthorized"));
        assertTrue(dispatcher.getCompletionSuggestions(suggestionParse).join().getList().isEmpty());
    }
    @Test void teleportSuggestionsUseExactCachedWaypointNamesAndHideUnavailableData() throws Exception {
        tpAllowed = true;
        String prefix = target().replace("remote list", "remote tp") + " ";
        assertTrue(suggestions(prefix).contains(quote("base 0")));
        assertTrue(suggestions(prefix + "\"base").contains(quote("base 0")));
        assertFalse(suggestions(prefix).contains(quote("Display 0")));
        index.disconnected(A, owner); time.set(11_000_000); index.maintain();
        assertTrue(suggestions(prefix).isEmpty());
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.unavailable"));
    }
    @Test void missingAndStaleTargetsNeverPrepare() throws Exception {
        tpAllowed = true;
        assertEquals(0, dispatcher.execute(tpTarget().replace("base 0", "Display 0"), "player"));
        assertTrue(keys(errors.get(0)).contains("waypoint.remote.tp.not_found"));
        index.disconnected(A, owner);
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(1)).contains("waypoint.remote.tp.stale_catalog"));
        time.set(11_000_000); index.maintain();
        assertEquals(0, dispatcher.execute(tpTarget(), "player"));
        assertTrue(keys(errors.get(2)).contains("waypoint.remote.tp.unavailable"));
        assertNull(preparation); assertEquals(0, transfers);
    }
    @Test void unauthorizedViewsNeverExposeRetainedCoordinates() {
        var existing = index.views().get(A);
        var hidden = new CatalogReceiver.View(existing.snapshot(), RemoteCatalogState.UNAUTHORIZED, existing.displayName(), existing.mode(), "minecraft:compass");
        var server = RemoteCatalogQuery.servers(Map.of(A, hidden)).get(0);
        assertFalse(server.readable());
        assertTrue(server.dimensions().isEmpty());
        assertEquals(0, server.waypointCount());
    }
}
```

- [ ] **Step 11: Run the tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 12: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java common/src/test/java common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Browse remote servers with a picker, status dots and the local list views"
```

---

### Task 17: Remote teleport messages and the arrival line

Spec 14.4: a remote teleport answers `Switching you to Survival for [IF] Iron Farm…`, and the destination server greets the player with `✔ Arrived at [IF] Iron Farm on survival`, naming itself by its server ID, which is the name it publishes. Every handoff failure becomes one `✘` line with `Try again` where retrying can help, or `Open Farms` when the waypoint is gone. The source no longer reports success itself: the player has left by then and the destination's line confirms the arrival. `DestinationHandoffService.ArrivalResult` gains the target, so the destination can name the waypoint it teleported to.

**Files:**
- Modify: `common/src/main/java/_959/server_waypoint/text/feedback/RemoteRefs.java`, `RemoteScreens.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/crossserver/handoff/DestinationHandoffService.java`, `BackendRuntime.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/server/handoff/ModCrossServerRuntime.java`, `paper/src/main/java/_959/server_waypoint/handoff/PaperCrossServerRuntime.java`
- Test: `RemoteScreensTest.java`
- Modify (tests): `RemoteWaypointCommandTest.java`, `common/src/test/java/_959/server_waypoint/crossserver/handoff/DestinationHandoffServiceTest.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: Task 16's remote builders, `WaypointRefs.plain` (Task 13), `ApplicationMessage.Result`.
- Produces:
  - `RemoteRefs.plain(Viewer, SimpleWaypoint)`.
  - `RemoteScreens.switching(Viewer, Server, String dimension, WaypointList, SimpleWaypoint)`, `teleportFailed(Viewer, @Nullable Server, String serverId, String dimension, String list, String waypoint, Result)`, `arrival(Result, String server, @Nullable String waypointName, @Nullable SimpleWaypoint)`.
  - `DestinationHandoffService.ArrivalResult(Result result, boolean notificationQueued, @Nullable RemoteWaypointKey target)`.
  - `BackendRuntime.arrivalMessage(DestinationHandoffService.ArrivalResult)`.
- Keys: `wp.remote.switching`, `wp.remote.arrived`, `wp.remote.try_again`, `wp.remote.try_again.tooltip`, `wp.remote.tp.*`.

- [ ] **Step 1: Add the keys in all six locales**

Append to `en_us.json`:

```json
  "wp.remote.switching": "Switching you to {0} for {1}",
  "wp.remote.arrived": "Arrived at {0} on {1}",
  "wp.remote.try_again": "Try again",
  "wp.remote.try_again.tooltip": "Ask for the switch again",
  "wp.remote.tp.unavailable": "{0} can''t be reached right now.",
  "wp.remote.tp.unauthorized": "You don''t have permission to switch to {0}.",
  "wp.remote.tp.not_found": "That waypoint is no longer on {0}.",
  "wp.remote.tp.stale_catalog": "{0} is out of date; wait for it to refresh.",
  "wp.remote.tp.busy": "Another switch is already under way.",
  "wp.remote.tp.expired": "The switch to {0} timed out.",
  "wp.remote.tp.replay": "That switch request was already used.",
  "wp.remote.tp.wrong_source": "You''re no longer on the server that asked for the switch.",
  "wp.remote.tp.wrong_destination": "{0} can''t take this switch.",
  "wp.remote.tp.transfer_failed": "The switch to {0} failed.",
  "wp.remote.tp.cancelled": "The switch to {0} was cancelled.",
  "wp.remote.tp.unsupported": "This server can''t switch players to other servers.",
  "wp.remote.tp.invalid_request": "{0} sent a mismatched reply.",
  "wp.remote.tp.internal_error": "The switch to {0} hit an internal error."
```

`zh_cn.json`:

```json
  "wp.remote.switching": "正在把你切换到{0}，前往{1}",
  "wp.remote.arrived": "已到达{1}的{0}",
  "wp.remote.try_again": "重试",
  "wp.remote.try_again.tooltip": "再次请求切换",
  "wp.remote.tp.unavailable": "{0}现在无法连接。",
  "wp.remote.tp.unauthorized": "你没有切换到{0}的权限。",
  "wp.remote.tp.not_found": "那个路径点已不在{0}上。",
  "wp.remote.tp.stale_catalog": "{0}的数据已过期，请等待刷新。",
  "wp.remote.tp.busy": "已有另一个切换正在进行。",
  "wp.remote.tp.expired": "切换到{0}超时了。",
  "wp.remote.tp.replay": "这个切换请求已经用过了。",
  "wp.remote.tp.wrong_source": "你已不在发起切换的服务器上。",
  "wp.remote.tp.wrong_destination": "{0}无法接受这次切换。",
  "wp.remote.tp.transfer_failed": "切换到{0}失败了。",
  "wp.remote.tp.cancelled": "切换到{0}已取消。",
  "wp.remote.tp.unsupported": "这个服务器无法把玩家切换到其他服务器。",
  "wp.remote.tp.invalid_request": "{0}的回复与请求不符。",
  "wp.remote.tp.internal_error": "切换到{0}时发生内部错误。"
```

`zh_hk.json` and `zh_tw.json`:

```json
  "wp.remote.switching": "正在把你切換到{0}，前往{1}",
  "wp.remote.arrived": "已到達{1}的{0}",
  "wp.remote.try_again": "重試",
  "wp.remote.try_again.tooltip": "再次請求切換",
  "wp.remote.tp.unavailable": "{0}現在無法連線。",
  "wp.remote.tp.unauthorized": "你沒有切換到{0}的權限。",
  "wp.remote.tp.not_found": "那個路徑點已不在{0}上。",
  "wp.remote.tp.stale_catalog": "{0}的資料已過期，請等待重新整理。",
  "wp.remote.tp.busy": "已有另一個切換正在進行。",
  "wp.remote.tp.expired": "切換到{0}逾時了。",
  "wp.remote.tp.replay": "這個切換請求已經用過了。",
  "wp.remote.tp.wrong_source": "你已不在發起切換的伺服器上。",
  "wp.remote.tp.wrong_destination": "{0}無法接受這次切換。",
  "wp.remote.tp.transfer_failed": "切換到{0}失敗了。",
  "wp.remote.tp.cancelled": "切換到{0}已取消。",
  "wp.remote.tp.unsupported": "這個伺服器無法把玩家切換到其他伺服器。",
  "wp.remote.tp.invalid_request": "{0}的回覆與請求不符。",
  "wp.remote.tp.internal_error": "切換到{0}時發生內部錯誤。"
```

`es_es.json`:

```json
  "wp.remote.switching": "Te llevamos a {0} para ir a {1}",
  "wp.remote.arrived": "Has llegado a {0} en {1}",
  "wp.remote.try_again": "Reintentar",
  "wp.remote.try_again.tooltip": "Volver a pedir el cambio",
  "wp.remote.tp.unavailable": "No se puede conectar con {0} ahora mismo.",
  "wp.remote.tp.unauthorized": "No tienes permiso para cambiar a {0}.",
  "wp.remote.tp.not_found": "Ese punto ya no está en {0}.",
  "wp.remote.tp.stale_catalog": "{0} está desactualizado; espera a que se actualice.",
  "wp.remote.tp.busy": "Ya hay otro cambio en curso.",
  "wp.remote.tp.expired": "El cambio a {0} ha caducado.",
  "wp.remote.tp.replay": "Esa solicitud de cambio ya se usó.",
  "wp.remote.tp.wrong_source": "Ya no estás en el servidor que pidió el cambio.",
  "wp.remote.tp.wrong_destination": "{0} no puede aceptar este cambio.",
  "wp.remote.tp.transfer_failed": "El cambio a {0} ha fallado.",
  "wp.remote.tp.cancelled": "Se canceló el cambio a {0}.",
  "wp.remote.tp.unsupported": "Este servidor no puede llevar jugadores a otros servidores.",
  "wp.remote.tp.invalid_request": "{0} respondió algo que no coincide con la solicitud.",
  "wp.remote.tp.internal_error": "El cambio a {0} tuvo un error interno."
```

`he_il.json`:

```json
  "wp.remote.switching": "מעבירים אתכם ל-{0} אל {1}",
  "wp.remote.arrived": "הגעתם אל {0} ב-{1}",
  "wp.remote.try_again": "ניסיון חוזר",
  "wp.remote.try_again.tooltip": "בקשת המעבר שוב",
  "wp.remote.tp.unavailable": "אי אפשר להתחבר ל-{0} כרגע.",
  "wp.remote.tp.unauthorized": "אין לכם הרשאה לעבור ל-{0}.",
  "wp.remote.tp.not_found": "הנקודה הזו כבר לא נמצאת ב-{0}.",
  "wp.remote.tp.stale_catalog": "{0} לא עדכני; המתינו שיתעדכן.",
  "wp.remote.tp.busy": "מעבר אחר כבר מתבצע.",
  "wp.remote.tp.expired": "תם הזמן למעבר ל-{0}.",
  "wp.remote.tp.replay": "בקשת המעבר הזו כבר נוצלה.",
  "wp.remote.tp.wrong_source": "אתם כבר לא בשרת שביקש את המעבר.",
  "wp.remote.tp.wrong_destination": "{0} לא יכול לקבל את המעבר הזה.",
  "wp.remote.tp.transfer_failed": "המעבר ל-{0} נכשל.",
  "wp.remote.tp.cancelled": "המעבר ל-{0} בוטל.",
  "wp.remote.tp.unsupported": "השרת הזה לא יכול להעביר שחקנים לשרתים אחרים.",
  "wp.remote.tp.invalid_request": "{0} שלח תשובה שלא תואמת לבקשה.",
  "wp.remote.tp.internal_error": "במעבר ל-{0} אירעה שגיאה פנימית."
```

- [ ] **Step 2: Write the failing builder tests**

In `common/src/test/java/_959/server_waypoint/text/feedback/RemoteScreensTest.java`, add `import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;` to the imports, and replace:

```java
    @Test
    void remoteLabelsStayLiteralAndShort() {
```

with:

```java
    @Test
    void switchingNamesTheServerAndTheWaypoint() {
        SimpleWaypoint iron = Fixtures.farms().getWaypointByName("Iron Farm");

        assertEquals("Switching you to Survival for [IF] Iron Farm…",
                render(RemoteScreens.switching(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), iron)));
    }

    @Test
    void failedSwitchesOfferTryAgainOrTheListWhenTheWaypointIsGone() {
        Component unreachable = RemoteScreens.teleportFailed(Fixtures.player(), SURVIVAL, "survival", OVERWORLD, "Farms",
                "Iron Farm", Result.UNAVAILABLE);
        Component gone = RemoteScreens.teleportFailed(Fixtures.player(), SURVIVAL, "survival", OVERWORLD, "Farms",
                "Iron Farm", Result.NOT_FOUND);

        assertEquals("✘ Survival can't be reached right now. Try again", render(unreachable));
        assertEquals("/wp remote tp survival " + OW + " Farms \"Iron Farm\"", clickOf(unreachable, "Try again"));
        assertEquals("✘ That waypoint is no longer on Survival. Open Farms", render(gone));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(gone, "Open Farms"));
        assertEquals("✘ You don't have permission to switch to survival.", render(RemoteScreens.teleportFailed(
                Fixtures.player(), null, "survival", OVERWORLD, "Farms", "Iron Farm", Result.UNAUTHORIZED)));
    }

    @Test
    void theDestinationSaysWhereThePlayerArrived() {
        SimpleWaypoint iron = Fixtures.farms().getWaypointByName("Iron Farm");

        assertEquals("✔ Arrived at [IF] Iron Farm on survival",
                render(RemoteScreens.arrival(Result.SUCCESS, "survival", "Iron Farm", iron)));
        assertEquals("✔ Arrived at Iron Farm on survival",
                render(RemoteScreens.arrival(Result.SUCCESS, "survival", "Iron Farm", null)));
        assertEquals("✘ The switch to survival timed out.", render(RemoteScreens.arrival(Result.EXPIRED, "survival", null, null)));
    }

    @Test
    void remoteLabelsStayLiteralAndShort() {
```

- [ ] **Step 3: Run them and see them fail**

Run the Task 16 Step 5 command. Expected: FAIL to compile, `cannot find symbol: method switching`.

- [ ] **Step 4: Write the switching, failure and arrival lines**

In `common/src/main/java/_959/server_waypoint/text/feedback/RemoteRefs.java`, replace:

```java
    /** [AB] Name: the initials teleport and the white name opens the read-only details. */
```

with:

```java
    /** [AB] Name without clicks, for a waypoint the player is switching to. */
    public static Component plain(Viewer viewer, SimpleWaypoint waypoint) {
        return Chat.concat(Chat.colored(text("[" + waypoint.initials() + "]"), TextColor.color(waypoint.rgb())), text(" "),
                Chat.colored(label(viewer, waypoint.displayName(), waypoint.name()), WHITE));
    }

    /** [AB] Name: the initials teleport and the white name opens the read-only details. */
```

In `common/src/main/java/_959/server_waypoint/text/feedback/RemoteScreens.java`, replace:

```java
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
```

with:

```java
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
```

replace:

```java
import java.util.List;
import java.util.Map;
import java.util.Objects;
```

with:

```java
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
```

add `import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;` before `import static net.kyori.adventure.text.Component.text;`, replace:

```java
    private static final List<SortMode> SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.COLOR);
```

with:

```java
    private static final List<SortMode> SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.COLOR);
    /** Whoever arrives at this server is a player reading chat. */
    private static final Viewer ARRIVING = new Viewer(Set.of(), true, false, null, null, 0F);
```

and replace:

```java
    public static Component distanceUnavailable() {
        return Errors.of("wp.error.remote.distance");
    }
```

with:

```java
    public static Component distanceUnavailable() {
        return Errors.of("wp.error.remote.distance");
    }

    /** Switching you to Survival for [IF] Iron Farm… */
    public static Component switching(Viewer viewer, Server server, String dimension, WaypointList list,
                                      SimpleWaypoint waypoint) {
        return Chat.colored(Chat.concat(translatable("wp.remote.switching",
                        Chat.colored(RemoteRefs.serverName(viewer, server), WHITE), RemoteRefs.plain(viewer, waypoint)),
                text(Chat.ELLIPSIS)), GRAY);
    }

    /**
     * ✘ Why a switch failed, with Try again where retrying can help, or Open <list> when the
     * waypoint is gone. The server is named by its display name when it is cached, else by its ID.
     */
    public static Component teleportFailed(Viewer viewer, @Nullable Server server, String serverId, String dimension,
                                           String list, String waypoint, Result result) {
        Component name = server == null ? text(RemoteRefs.truncate(serverId)) : RemoteRefs.serverName(viewer, server);
        Component message = translatable("wp.remote.tp." + result.name().toLowerCase(Locale.ROOT), Chat.colored(name, WHITE));
        WaypointList cached = server == null ? null : server.list(dimension, list);
        Component listName = cached == null ? text(RemoteRefs.truncate(list)) : RemoteRefs.label(cached.displayName(), list);
        Component recovery = switch (result) {
            case NOT_FOUND -> Chat.control(viewer, translatable("wp.open", listName), AQUA,
                    Click.run(ListTarget.remote(serverId, dimension, list).command(ListQuery.DEFAULT)),
                    Tooltip.of("wp.remote.browse.tooltip"));
            case UNAUTHORIZED, WRONG_SOURCE, WRONG_DESTINATION, UNSUPPORTED, SUCCESS -> null;
            default -> Chat.control(viewer, translatable("wp.remote.try_again"), AQUA,
                    Click.run("/wp remote tp " + escapeListName(serverId) + " " + escapeListName(dimension) + " "
                            + escapeListName(list) + " " + escapeListName(waypoint)),
                    Tooltip.of("wp.remote.try_again.tooltip"));
        };
        return Chat.error(message, recovery);
    }

    /**
     * At the destination: ✔ Arrived at [IF] Iron Farm on survival, naming this server by its ID,
     * or why the arrival failed. The waypoint is this server's own, when it still exists.
     */
    public static Component arrival(Result result, String server, @Nullable String waypointName,
                                    @Nullable SimpleWaypoint waypoint) {
        Component serverName = Chat.colored(text(RemoteRefs.truncate(server)), WHITE);
        if (result != Result.SUCCESS) {
            return Chat.error(translatable("wp.remote.tp." + result.name().toLowerCase(Locale.ROOT), serverName));
        }
        Component target = waypoint != null ? WaypointRefs.plain(ARRIVING, waypoint)
                : Chat.colored(text(RemoteRefs.truncate(Objects.requireNonNullElse(waypointName, ""))), WHITE);
        return Chat.ok(translatable("wp.remote.arrived", target, serverName));
    }
```

- [ ] **Step 5: Run the builder tests**

Run the Task 16 Step 5 command. Expected: PASS.

- [ ] **Step 6: Report switches from the command**

In `common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java`, replace the whole of `teleport` and `fail`:

```java
    private int teleport(CommandContext<S> context) {
        S source = context.getSource();
        if (!canTeleport.test(source)) return fail(source, Result.UNAUTHORIZED);
```

through:

```java
    private int fail(S source, Result result) {
        error.accept(source, translatable("waypoint.remote.tp." + result.name().toLowerCase(Locale.ROOT)));
        return 0;
    }
```

with:

```java
    private int teleport(CommandContext<S> context) {
        S source = context.getSource();
        String id = getString(context, SERVER), dimension = getString(context, DIMENSION),
                listName = getString(context, LIST), name = getString(context, WAYPOINT);
        Viewer reader = viewer.apply(source);
        if (!canTeleport.test(source)) return fail(source, reader, null, id, dimension, listName, name, Result.UNAUTHORIZED);
        var cached = store.get().snapshot();
        var entry = cached.entrySet().stream().filter(value -> value.getKey().value().equals(id)).findFirst().orElse(null);
        RemoteCatalogQuery.Server server = RemoteCatalogQuery.server(cached, id).orElse(null);
        if (entry == null || server == null) return fail(source, reader, null, id, dimension, listName, name, Result.UNAVAILABLE);
        var view = entry.getValue();
        if (view.state() == RemoteCatalogState.UNAUTHORIZED) return fail(source, reader, server, id, dimension, listName, name, Result.UNAUTHORIZED);
        if (view.state() == RemoteCatalogState.STALE) return fail(source, reader, server, id, dimension, listName, name, Result.STALE_CATALOG);
        if (view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) {
            return fail(source, reader, server, id, dimension, listName, name, Result.UNAVAILABLE);
        }
        var list = view.snapshot().dimensions().getOrDefault(dimension, Map.of()).get(listName);
        WaypointList shown = server.list(dimension, listName);
        SimpleWaypoint target = shown == null ? null : shown.getWaypointByName(name);
        if (list == null || !list.waypoints().containsKey(name) || target == null) {
            return fail(source, reader, server, id, dimension, listName, name, Result.NOT_FOUND);
        }
        var selection = new RemoteTeleportInitiator.Selection(new RemoteWaypointKey(entry.getKey(), dimension, listName, name),
                view.snapshot().catalogRevision(), list.listRevision());
        send.accept(source, RemoteScreens.switching(reader, server, dimension, shown, target));
        teleport.initiate(source, selection, result -> {
            if (result != Result.SUCCESS) fail(source, reader, server, id, dimension, listName, name, result);
        });
        return Command.SINGLE_SUCCESS;
    }
    /** ✘ why the switch failed; the destination's arrival line reports success. */
    private int fail(S source, Viewer reader, @Nullable RemoteCatalogQuery.Server server, String id, String dimension,
                     String list, String waypoint, Result result) {
        error.accept(source, RemoteScreens.teleportFailed(reader, server, id, dimension, list, waypoint, result));
        return 0;
    }
```

- [ ] **Step 7: Carry the target to the arrival line**

In `common/src/main/java/_959/server_waypoint/crossserver/handoff/DestinationHandoffService.java`, replace:

```java
    public record ArrivalResult(Result result, boolean notificationQueued) { }
```

with:

```java
    /** How an arrival ended, and the waypoint it was for when the handoff was known. */
    public record ArrivalResult(Result result, boolean notificationQueued, @org.jetbrains.annotations.Nullable RemoteWaypointKey target) { }
```

replace the three early returns in `arrive`:

```java
            if (closed) return CompletableFuture.completedFuture(new ArrivalResult(Result.UNAVAILABLE, false));
            entry = players.get(authenticatedPlayerId);
            if (entry == null) return CompletableFuture.completedFuture(new ArrivalResult(Result.NOT_FOUND, false));
            if (entry.state != State.PREPARED) return CompletableFuture.completedFuture(new ArrivalResult(Result.REPLAY, false));
```

with:

```java
            if (closed) return CompletableFuture.completedFuture(new ArrivalResult(Result.UNAVAILABLE, false, null));
            entry = players.get(authenticatedPlayerId);
            if (entry == null) return CompletableFuture.completedFuture(new ArrivalResult(Result.NOT_FOUND, false, null));
            if (entry.state != State.PREPARED) {
                return CompletableFuture.completedFuture(new ArrivalResult(Result.REPLAY, false, entry.binding.target()));
            }
```

and replace:

```java
        entry.arrival.complete(new ArrivalResult(result, queued));
```

with:

```java
        entry.arrival.complete(new ArrivalResult(result, queued, entry.binding.target()));
```

Add `import _959.server_waypoint.crossserver.RemoteWaypointKey;` after `import _959.server_waypoint.crossserver.RemoteServerId;`.

In `common/src/test/java/_959/server_waypoint/crossserver/handoff/DestinationHandoffServiceTest.java`, replace `new DestinationHandoffService.ArrivalResult(SUCCESS, true)` with `new DestinationHandoffService.ArrivalResult(SUCCESS, true, binding.target())` and `new DestinationHandoffService.ArrivalResult(SUCCESS, false)` with `new DestinationHandoffService.ArrivalResult(SUCCESS, false, binding.target())`.

In `common/src/main/java/_959/server_waypoint/crossserver/handoff/BackendRuntime.java`, replace:

```java
import _959.server_waypoint.core.WaypointServerCore;
```

with:

```java
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.feedback.RemoteScreens;
import net.kyori.adventure.text.Component;
```

replace:

```java
    private volatile String startupFailureDetails;
```

with:

```java
    private volatile String startupFailureDetails;
    private volatile RemoteServerId localId;
```

replace:

```java
        if (!RuntimeConfiguration.text(config, "catalogExport", "PUBLIC").equals("PUBLIC")) {
```

with:

```java
        localId = id;
        if (!RuntimeConfiguration.text(config, "catalogExport", "PUBLIC").equals("PUBLIC")) {
```

replace:

```java
        if (stopping || current == null) return CompletableFuture.completedFuture(new DestinationHandoffService.ArrivalResult(Result.UNAVAILABLE, false));
        return current.destination().arrive(playerId, player);
    }
```

with:

```java
        if (stopping || current == null) return CompletableFuture.completedFuture(new DestinationHandoffService.ArrivalResult(Result.UNAVAILABLE, false, null));
        return current.destination().arrive(playerId, player);
    }
    /** What the arriving player reads (spec 14.4): the waypoint is this server's own copy, when it still exists. */
    public Component arrivalMessage(DestinationHandoffService.ArrivalResult result) {
        RemoteServerId id = localId;
        RemoteWaypointKey target = result.target();
        SimpleWaypoint waypoint = null;
        if (target != null) {
            WaypointFileManager fileManager = manager.getWaypointFileManager(target.dimensionName());
            WaypointList list = fileManager == null ? null : fileManager.getWaypointListByName(target.listName());
            waypoint = list == null ? null : list.getWaypointByName(target.waypointName());
        }
        return RemoteScreens.arrival(result.result(), id == null ? "" : id.value(),
                target == null ? null : target.waypointName(), waypoint);
    }
```

In `mods/src/main/java/_959/server_waypoint/common/server/handoff/ModCrossServerRuntime.java`, replace:

```java
            destination.execute(player, () -> ModMessageSender.getInstance().sendPlayerMessage(player, translatable(result.result() == Result.SUCCESS
                    ? "waypoint.remote.tp.arrived" : "waypoint.remote.tp." + result.result().name().toLowerCase(java.util.Locale.ROOT))), () -> { });
```

with:

```java
            destination.execute(player, () -> ModMessageSender.getInstance().sendPlayerMessage(player,
                    runtime.arrivalMessage(result)), () -> { });
```

and delete `import static net.kyori.adventure.text.Component.translatable;`.

In `paper/src/main/java/_959/server_waypoint/handoff/PaperCrossServerRuntime.java`, replace:

```java
            destination.execute(player, () -> sender.sendPlayerMessage(player, translatable(result.result() == Result.SUCCESS
                    ? "waypoint.remote.tp.arrived" : "waypoint.remote.tp." + result.result().name().toLowerCase(java.util.Locale.ROOT))), () -> { });
```

with:

```java
            destination.execute(player, () -> sender.sendPlayerMessage(player, runtime.arrivalMessage(result)), () -> { });
```

and delete `import static net.kyori.adventure.text.Component.translatable;`.

- [ ] **Step 8: Move the teleport tests to the new messages**

In `common/src/test/java/_959/server_waypoint/command/RemoteWaypointCommandTest.java`:

- In `teleportCommandReachesFakeTransferOnlyAfterMatchingPreparation`, replace `assertEquals(1, transfers); assertTrue(keys(last()).contains("waypoint.remote.tp.success"));` with:

  ```java
        assertEquals(1, transfers);
        assertEquals("Switching you to Server search for [B] Display 0…", ChatAssert.render(last()));
        assertTrue(errors.isEmpty());
  ```

- Replace the key prefix `"waypoint.remote.tp.` with `"wp.remote.tp.` in the remaining teleport assertions (`unsupported`, `unauthorized`, `unavailable`, `not_found`, `stale_catalog`, `unavailable`).

- [ ] **Step 9: Run the tests and compile the platforms**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/main/java common/src/test/java mods/src/main/java/_959/server_waypoint/common/server/handoff/ModCrossServerRuntime.java paper/src/main/java/_959/server_waypoint/handoff/PaperCrossServerRuntime.java common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Report remote switches, their failures and the arrival on the destination"
```

---

### Task 18: Remove the old text helpers and unused keys

Spec 17 and 18: `TextButtonBuilder`, `WaypointTextHelper` and the old list command builders have no callers left after Tasks 8 to 17, and keys that lose their last use are removed. The mods GUI and the navigation displays still asked `WaypointTextHelper` for dimension colours; they move to `DimensionStyle.colorOf`. The Xaero's Minimap integration builds its own `[AB] Name` for the client's chat, with the client's own translation of the teleport hint, since its messages are rendered on the client.

**Files:**
- Delete: `common/src/main/java/_959/server_waypoint/text/TextButtonBuilder.java`, `common/src/main/java/_959/server_waypoint/text/WaypointTextHelper.java`, `common/src/test/java/_959/server_waypoint/text/TextButtonBuilderTest.java`, `common/src/test/java/_959/server_waypoint/text/WaypointTextHelperTest.java`
- Modify: `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointModificationType.java`
- Modify: `common/src/main/java/_959/server_waypoint/navigation/NavigationDisplayText.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java`, `WaypointEditScreen.java`, `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/DimensionListWidget.java`, `WaypointDetailsWidget.java`, `WaypointListWidget.java`, `mods/src/main/java/_959/server_waypoint/common/client/integrations/XaerosMinimapWaypointHelper.java`
- Modify: the six files in `common/src/main/resources/lang/`

**Interfaces:**
- Consumes: `DimensionStyle.colorOf` (Task 4).
- Produces: nothing new. Removed: `TextButtonBuilder`, `WaypointTextHelper`, `StringCommandBuilder.ListTarget`, `ListOptions`, the `list…Cmd` and `remoteListPageCmd` builders, `editListSetCmd`, `editWaypointSetCmd`, `WaypointModificationType.toTranslatable`, and every translation key nothing uses.

- [ ] **Step 1: Move the remaining dimension colours to `DimensionStyle`**

In each of `RemoteWaypointPanel.java`, `WaypointEditScreen.java`, `DimensionListWidget.java`, `WaypointDetailsWidget.java` and `WaypointListWidget.java` under `mods/src/main/java/_959/server_waypoint/common/client/gui/`, replace:

```java
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;
```

with:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
```

(moving it into the regular import block, sorted with its neighbours) and replace each `getDimensionColor(` call with `DimensionStyle.colorOf(`. Both return a `NamedTextColor`, so the `.value()` calls after them stay.

In `common/src/main/java/_959/server_waypoint/navigation/NavigationDisplayText.java`, replace:

```java
import static _959.server_waypoint.text.WaypointTextHelper.dimensionNameWithColor;
```

with:

```java
import _959.server_waypoint.text.chat.DimensionStyle;
```

(in the regular import block) and replace both `dimensionNameWithColor(target.dimensionName())` calls with `dimensionName(target)`, adding this method after `buildItemLore`:

```java
    /** The dimension ID in its colour; item lore and live displays show the ID as it is. */
    private static Component dimensionName(NavigationTarget target) {
        return text(target.dimensionName()).color(DimensionStyle.colorOf(target.dimensionName()));
    }
```

- [ ] **Step 2: Give the Xaero's Minimap messages their own waypoint text**

In `mods/src/main/java/_959/server_waypoint/common/client/integrations/XaerosMinimapWaypointHelper.java`, replace:

```java
import static _959.server_waypoint.text.WaypointTextHelper.waypointTextWithTp;
```

with:

```java
import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;
```

add these imports to the regular import block:

```java
import _959.server_waypoint.text.FormattedTextHelper;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
```

replace both `waypointTextWithTp(waypoint, dimensionName, listName)` calls with `waypointText(waypoint, dimensionName, listName)`, and add this method after the method that contains them:

```java
    /**
     * [AB] Name for this client's own chat: the initials teleport, with the client's translation
     * of the hint, and the name shows the description and coordinates.
     */
    private static net.kyori.adventure.text.Component waypointText(SimpleWaypoint waypoint, String dimensionName, String listName) {
        net.kyori.adventure.text.Component where = net.kyori.adventure.text.Component.text(DimensionStyle.coordinates(waypoint.pos()));
        if (!waypoint.description().isEmpty()) {
            where = FormattedTextHelper.parse(waypoint.description()).appendNewline().append(where);
        }
        return net.kyori.adventure.text.Component.empty()
                .append(net.kyori.adventure.text.Component.text("[" + waypoint.initials() + "]", TextColor.color(waypoint.rgb()))
                        .clickEvent(ClickEvent.runCommand(tpCmd(dimensionName, listName, waypoint.name())))
                        .hoverEvent(HoverEvent.showText(net.kyori.adventure.text.Component.translatable("button.initials.tp"))))
                .append(net.kyori.adventure.text.Component.space())
                .append(net.kyori.adventure.text.Component.empty().color(NamedTextColor.WHITE)
                        .hoverEvent(HoverEvent.showText(where))
                        .append(FormattedTextHelper.parse(waypoint.displayName())));
    }
```

`button.initials.tp` comes from the mod's client language files (`mods/src/main/resources/assets/server_waypoint/lang/`), which this change does not touch.

- [ ] **Step 3: Delete the old helpers**

```bash
/usr/bin/git rm common/src/main/java/_959/server_waypoint/text/TextButtonBuilder.java common/src/main/java/_959/server_waypoint/text/WaypointTextHelper.java common/src/test/java/_959/server_waypoint/text/TextButtonBuilderTest.java common/src/test/java/_959/server_waypoint/text/WaypointTextHelperTest.java
```

In `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`, delete the records `ListTarget` and `ListOptions`, every method from the first `listPageCmd` through `listOrderCmd` (including `appendListOptions`, `remoteListPageCmd`, `listSearchCmd`, `listViewCmd`, `listDimensionCmd`, `listWaypointListCmd`, `listTargetCmd` and `listSortCmd`), and `editListSetCmd` and `editWaypointSetCmd`. Keep `escapeListName` and `isListOptionLiteral`. Then remove the `WaypointSorting` import, which nothing else uses.

In `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointModificationType.java`, delete `toTranslatable()` and the two imports only it used.

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, delete these imports:

```java
import _959.server_waypoint.text.TextButtonBuilder;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import _959.server_waypoint.util.StringCommandBuilder.ListTarget;
import static _959.server_waypoint.text.TextButtonBuilder.*;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static _959.server_waypoint.util.StringCommandBuilder.listDimensionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.listWaypointListCmd;
```

and check that nothing in `common` still names the deleted classes:

```bash
grep -rn "TextButtonBuilder\|WaypointTextHelper\|StringCommandBuilder.ListOptions\|StringCommandBuilder.ListTarget\|listPageCmd\|remoteListPageCmd\|toTranslatable" common/src mods/src/main paper/src/main --include='*.java'
```

Expected: no output.

- [ ] **Step 4: Compile everything that changed**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:compileTestJava :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL. A failure that names a deleted helper points at a call an earlier task should have replaced; replace it with the builder that task introduced.

- [ ] **Step 5: Remove the keys nothing uses**

Save this script as `find_unused_keys.py` in your scratchpad directory and run it from the repository root. It reads every Java source the server runs (`common`, `paper`, and `mods` without its client GUI, which has its own language files) and prints each `en_us.json` key that no string literal names. A key also counts as used when a literal is a prefix of it that ends in `.` or stops just before a `.`, which covers keys built at run time such as `"wp.details." + field` and `key + ".one"`.

```python
#!/usr/bin/env python3
"""Prints en_us.json keys that no server-side Java string literal names. Run from the repository root."""
import json
import pathlib
import re

sources = [path for base in ('common/src/main/java', 'paper/src/main/java', 'mods/src/main/java')
           for path in pathlib.Path(base).rglob('*.java') if '/common/client/' not in path.as_posix()]
literals = set()
for path in sources:
    literals.update(re.findall(r'"((?:[^"\\]|\\.)*)"', path.read_text(encoding='utf-8')))
prefixes = [literal for literal in literals if '.' in literal]


def used(key):
    if key in literals:
        return True
    return any(key.startswith(prefix) and (prefix.endswith('.') or key[len(prefix)] == '.')
               for prefix in prefixes if len(prefix) < len(key))


keys = json.loads(pathlib.Path('common/src/main/resources/lang/en_us.json').read_text(encoding='utf-8'))
for key in keys:
    if not used(key):
        print(key)
```

```bash
python3 SCRATCH/find_unused_keys.py
```

Read the list before removing anything: it should hold only keys of the old feedback (`waypoint.*`, `button.*`, `argument.*`, `hex_color_code.invalid`, `waypoint_list.*`), never a `wp.` key. A `wp.` key in the list means a builder lost its use or a task added a key it never used; look for the builder before deciding. Then pass the printed keys to `remove_keys.py` (from the conventions above), which removes them from all six files:

```bash
python3 SCRATCH/find_unused_keys.py > SCRATCH/unused_keys.txt
```

```bash
xargs python3 SCRATCH/remove_keys.py < SCRATCH/unused_keys.txt
```

Run the finder again. Expected: no output.

- [ ] **Step 6: Run the tests and compile the targets**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test :mods:26.1.2-fabric:compileTestJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src mods/src/main/java paper/src/main/java
```

```bash
/usr/bin/git commit -m "Remove the old feedback helpers and the translations they used"
```

---

### Task 19: Audit every screen's layout, glyphs and plain text

Spec 1, 15 and 21: in English with the spec's sample data, every chat line fits 320 px, every message fits the 20-line window with its trailing blank line, nothing is bold, every glyph (in chat and in tooltips) is in the vanilla font, and no key is missing. For plain-text viewers no screen carries a click or a tooltip, rows show coordinates and dimensions show their IDs. Earlier tasks check their own screens; this test checks them all in one place, so a later wording change can't push one past the limits unnoticed.

**Files:**
- Test: `common/src/test/java/_959/server_waypoint/text/feedback/ScreenAuditTest.java`

**Interfaces:**
- Consumes: every builder of Tasks 7 to 17, `ChatAssert`, `ChatFont`, `WaypointQueryEngine.queryLists` (Task 16).

- [ ] **Step 1: Write the audit**

Create `common/src/test/java/_959/server_waypoint/text/feedback/ScreenAuditTest.java`:

```java
package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ChatFont;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static _959.server_waypoint.text.feedback.Fixtures.TWILIGHT;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Every screen with the spec's sample data (spec 21): it fits chat, uses vanilla glyphs and reads in plain text. */
class ScreenAuditTest {
    /** Screens only a player with the mod ever receives; they keep their tooltips. */
    private static final Set<String> PLAYER_ONLY = Set.of("upload result", "arrival");

    private static Map<String, Component> screens(Viewer viewer) {
        DimensionStyle dims = Fixtures.dims(viewer);
        WaypointList home = Fixtures.homeBases();
        WaypointList farms = Fixtures.farms();
        SimpleWaypoint mainHome = home.getWaypointByName("Main Home");
        SimpleWaypoint iron = farms.getWaypointByName("Iron Farm");
        WaypointList storage = new WaypointList("Storage", 1, List.of());
        PlacedWaypoint target = new PlacedWaypoint(OVERWORLD, home, mainHome);
        Map<String, List<WaypointList>> overworld = Map.of(OVERWORLD, Fixtures.overworldLists());
        WaypointQueryEngine.Query all = new WaypointQueryEngine.Query("", SortMode.DEFAULT, viewer.position(), viewer.dimension(), false);
        WaypointQueryEngine.Query farm = new WaypointQueryEngine.Query("farm", SortMode.DEFAULT, viewer.position(), viewer.dimension(), false);
        ListScreen.Totals totals = new ListScreen.Totals(3, 14);
        Map<String, Component> screens = new LinkedHashMap<>();
        screens.put("menu", MenuScreen.menu(viewer, dims, target, true));
        screens.put("help", HelpScreen.index(viewer));
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            screens.put("help " + topic.id(), HelpScreen.topic(viewer, topic, true));
        }
        screens.put("tree", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, all),
                ListQuery.DEFAULT, 10));
        screens.put("lists", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, all),
                ListQuery.DEFAULT.withView(ListView.LISTS), 10));
        screens.put("flat", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, all),
                ListQuery.DEFAULT.withView(ListView.FLAT), 10));
        screens.put("search", ListScreen.dimension(dims, OVERWORLD, totals, WaypointQueryEngine.queryLists(overworld, farm),
                ListQuery.DEFAULT.withSearch("farm"), 10));
        screens.put("list", ListScreen.list(dims, OVERWORLD, farms,
                WaypointQueryEngine.queryLists(Map.of(OVERWORLD, List.of(farms)), all), ListQuery.DEFAULT, 10));
        screens.put("empty list", ListScreen.list(dims, NETHER, storage,
                WaypointQueryEngine.queryLists(Map.of(NETHER, List.of(storage)), all), ListQuery.DEFAULT, 10));
        List<DimensionScreens.DimensionLists> dimensions = List.of(
                new DimensionScreens.DimensionLists(OVERWORLD, Fixtures.overworldLists()),
                new DimensionScreens.DimensionLists(NETHER, List.of(storage)),
                new DimensionScreens.DimensionLists(END, List.of()),
                new DimensionScreens.DimensionLists(TWILIGHT, List.of()));
        screens.put("dimensions", DimensionScreens.dimensionList(dims, dimensions, 1, 10));
        screens.put("all", DimensionScreens.all(dims, dimensions, ListQuery.DEFAULT, 10));
        screens.put("all search", DimensionScreens.allSearch(dims, WaypointQueryEngine.queryLists(overworld, farm),
                ListQuery.DEFAULT.withSearch("farm"), 10));
        screens.put("details", DetailsScreen.waypoint(dims, OVERWORLD, home, mainHome, translatable("wp.updated.color")));
        screens.put("list details", DetailsScreen.list(dims, OVERWORLD, home, null));
        screens.put("colour picker", PickerScreens.color(dims, OVERWORLD, home, mainHome));
        screens.put("facing picker", PickerScreens.facing(dims, OVERWORLD, home, mainHome));
        screens.put("add picker", PickerScreens.add(dims, OVERWORLD, Fixtures.overworldLists(), 1, 10));
        screens.put("added", Results.added(dims, OVERWORLD, farms, iron));
        screens.put("created list", Results.createdList(dims, OVERWORLD, storage));
        screens.put("removed", Results.removed(dims, OVERWORLD, home, mainHome, "r12"));
        screens.put("removed list", Results.removedList(dims, NETHER, storage));
        screens.put("restored", Results.restored(dims, OVERWORLD, home, mainHome));
        screens.put("teleported", Results.teleported(dims, OVERWORLD, home, mainHome));
        screens.put("reloaded", Results.reloaded(List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw")));
        screens.put("sent", Results.sent(12));
        screens.put("broadcast", Broadcasts.added(dims, text("Steve"), OVERWORLD, farms, iron));
        screens.put("broadcast list", Broadcasts.removedList(dims, text("Steve"), NETHER, storage));
        screens.put("no list", Errors.noList(dims, OVERWORLD, "Farm"));
        screens.put("no waypoint", Errors.noWaypoint(dims, OVERWORLD, "Farms", "Gate"));
        screens.put("list exists", Errors.listExists(dims, OVERWORLD, farms));
        screens.put("waypoint exists", Errors.waypointExists(dims, OVERWORLD, farms, iron));
        screens.put("list not empty", Errors.listNotEmpty(dims, OVERWORLD, farms));
        for (EditResultStatus status : EditResultStatus.values()) {
            if (status != EditResultStatus.SUCCESS) {
                screens.put("edit " + status, Errors.edit(dims, status, OVERWORLD, "Farms", "Iron Farm"));
            }
        }
        screens.put("sharing", SharingPrompt.found(dims, OVERWORLD, iron, Fixtures.overworldLists()));
        screens.put("sharing dimension", SharingPrompt.unknownDimension(dims, "mars:mars", iron));
        screens.put("navigation", NavigationScreens.panel(dims, target, EnumSet.allOf(NavigationMethod.class),
                EnumSet.allOf(NavigationMethod.class), translatable("wp.navigation.started")));
        screens.put("not navigating", NavigationScreens.idle(dims));
        screens.put("stopped", NavigationScreens.stopped(dims, target));
        screens.put("text display", NavigationScreens.textDisplay(viewer, TextDisplayTransformation.defaultValue(),
                translatable("wp.text_display.updated")));
        screens.put("upload", UploadScreens.panel(viewer));
        screens.put("upload result", UploadScreens.result(new UploadScreens.Outcome(UploadTarget.XAERO, 3, 1, 2, 9, 2, 1,
                2, true, true, "/wp upload xaero force local", "/wp upload xaero")));
        Server survival = new Server(new RemoteServerId("survival"), "Survival", RemoteCatalogState.AVAILABLE,
                Map.of(OVERWORLD, Fixtures.overworldLists()));
        Server creative = new Server(new RemoteServerId("creative-1"), "Creative Plots", RemoteCatalogState.STALE,
                Map.of(OVERWORLD, List.of(farms)));
        Server lobby = new Server(new RemoteServerId("lobby"), "Lobby", RemoteCatalogState.UNAVAILABLE, Map.of());
        List<Server> servers = List.of(survival, creative, lobby);
        screens.put("remote picker", RemoteScreens.picker(viewer, servers, 1, 10));
        screens.put("remote all", RemoteScreens.allServers(viewer, servers, ListQuery.DEFAULT, 10));
        screens.put("remote server", RemoteScreens.server(viewer, survival, ListQuery.DEFAULT, 10));
        screens.put("remote search", RemoteScreens.search(viewer, null, servers, ListQuery.DEFAULT.withSearch("farm"), 10));
        screens.put("remote dimension", RemoteScreens.dimension(viewer, survival, OVERWORLD, ListQuery.DEFAULT, 10));
        screens.put("remote list", RemoteScreens.list(viewer, survival, OVERWORLD, farms, ListQuery.DEFAULT, 10));
        screens.put("remote details", RemoteScreens.details(viewer, survival, OVERWORLD, home, mainHome));
        screens.put("unreachable", RemoteScreens.server(viewer, lobby, ListQuery.DEFAULT, 10));
        screens.put("switching", RemoteScreens.switching(viewer, survival, OVERWORLD, farms, iron));
        for (Result result : Result.values()) {
            if (result != Result.SUCCESS) {
                screens.put("switch " + result, RemoteScreens.teleportFailed(viewer, survival, "survival", OVERWORLD,
                        "Farms", "Iron Farm", result));
            }
        }
        screens.put("arrival", RemoteScreens.arrival(Result.SUCCESS, "survival", "Iron Farm", iron));
        return screens;
    }

    @Test
    void everyScreenFitsChatForPlayers() {
        screens(Fixtures.player()).forEach((name, screen) -> {
            try {
                ChatAssert.assertFitsChat(screen);
            } catch (AssertionError problem) {
                throw new AssertionError(name + ": " + problem.getMessage(), problem);
            }
            assertFalse(ChatAssert.render(screen).contains("<missing"), name + ": " + ChatAssert.render(screen));
        });
    }

    @Test
    void everyTooltipUsesTheVanillaFont() {
        screens(Fixtures.player()).forEach((name, screen) -> {
            for (ChatAssert.Run run : ChatAssert.runs(screen)) {
                HoverEvent<?> hover = run.style().hoverEvent();
                if (hover == null || !(hover.value() instanceof Component tooltip)) {
                    continue;
                }
                String text = ChatAssert.render(tooltip);
                assertFalse(text.contains("<missing"), name + " tooltip: " + text);
                text.codePoints().filter(codePoint -> codePoint != '\n' && !ChatFont.isVanillaGlyph(codePoint)).findFirst()
                        .ifPresent(codePoint -> fail(name + " tooltip uses " + new String(Character.toChars(codePoint))
                                + ": " + text));
            }
        });
    }

    @Test
    void plainTextScreensNeedNoHoverOrClick() {
        Map<String, Component> console = screens(Fixtures.console());
        console.forEach((name, screen) -> {
            if (!PLAYER_ONLY.contains(name)) {
                assertNoInteraction(name, screen);
            }
            assertFalse(ChatAssert.render(screen).contains("<missing"), name + ": " + ChatAssert.render(screen));
        });
        assertTrue(ChatAssert.render(console.get("tree")).startsWith("Overworld (minecraft:overworld)"));
        assertTrue(ChatAssert.render(console.get("tree")).contains("[MH] Main Home · 120, 64, -35"));
        assertTrue(ChatAssert.render(console.get("flat")).contains("[IF] Iron Farm · Farms · 300, 80, 150"));
        assertTrue(ChatAssert.render(console.get("remote dimension")).contains("[MH] Main Home · 120, 64, -35"));
        assertTrue(ChatAssert.render(console.get("remote picker")).contains("● Survival (survival) · 14"));
        assertTrue(ChatAssert.render(console.get("removed")).contains("Restore with /wp restore r12"));
    }

    private static void assertNoInteraction(String name, Component component) {
        if (component.clickEvent() != null || component.hoverEvent() != null) {
            fail(name + " has a " + (component.clickEvent() != null ? "click" : "tooltip")
                    + " for plain-text viewers: " + ChatAssert.render(component));
        }
        component.children().forEach(child -> assertNoInteraction(name, child));
        if (component instanceof TranslatableComponent translatable) {
            translatable.arguments().forEach(argument -> assertNoInteraction(name, argument.asComponent()));
        }
    }
}
```

- [ ] **Step 2: Run it**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.feedback.ScreenAuditTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS. Each failure names the screen and prints the message. A line over 320 px is fixed in its English wording (shorter, never with a narrower layout rule) and the matching builder test is updated with it; a glyph outside the vanilla font is replaced with one from spec 2.2; a missing key is added in all six locales; a click or tooltip for plain-text viewers is moved behind `Chat.link`, `Chat.control` or `Chat.hover`, which drop them. Fix the builder, then run the whole common suite:

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add common/src/test/java/_959/server_waypoint/text/feedback common/src/main/java/_959/server_waypoint/text common/src/main/resources/lang
```

```bash
/usr/bin/git commit -m "Audit every screen's width, glyphs and plain-text output"
```

---

### Task 20: Documentation

Spec 19: the project README and its Chinese translation describe the new commands, the cross-server docs stop showing `/wp remote servers`, `docs/tips/adventure-text.md` gains the chat kit rules, and the feature folder links the spec, the plan and the validation record. Release notes, the original cross-server plan and the step-1 protocol contract (`cross-server-protocol-v1.md`) stay as history.

**Files:**
- Modify: `README.md`, `README_zh.md`
- Modify: `docs/features/cross-server/specs/cross-server-catalog-queries.md`, `docs/features/cross-server/cross-server-admin.md`
- Modify: `docs/tips/adventure-text.md`
- Modify: `docs/features/command-feedback/specs/2026-10-01-command-feedback-design.md`

- [ ] **Step 1: The project README**

In `README.md`, replace the whole `## Commands` section (from `## Commands` through the line `- \`/wp tp\` teleport the executor player to a waypoint`) with:

```markdown
## Commands
Run `/wp` for a menu: most features are a click away, and every screen offers its next steps as links. The console, RCON and command blocks get the same information as plain text, with identifiers and coordinates written out.
- `/wp add` opens a picker of the current dimension's lists to add a waypoint where you stand. Identifiers must be unique within their list.
  - `/wp add <dimension> <list-identifier>` adds a waypoint list.
  - `/wp add <dimension> <list-identifier> <x y z> <name>` adds a waypoint with generated initials and a random colour. The full form continues with initials, colour, yaw, visibility, keywords, description and `icon <namespace:path>`.
- `/wp download [<dimension> [<list-identifier> [<waypoint-identifier>]]]` sends waypoints to your map mod (needs the mod on your client).
- `/wp details list <dimension> <list-identifier>` and `/wp details waypoint <dimension> <list-identifier> <waypoint-identifier>` show every property with buttons to edit it.
- `/wp edit list ...` and `/wp edit waypoint ...` set one property at a time or clear an optional property. Without a value, `set color` opens a colour picker and `set yaw` a facing picker; `set color random` picks a colour. Run `/wp help edit` for the complete grammar.
  - `/wp edit waypoint <dimension> <list> <waypoint> set icon minecraft:diamond` selects an item icon. Use `clear icon` to restore the initials marker. A `voxelmap:` ID such as `voxelmap:star` selects a built-in VoxelMap image. The full add command accepts `icon <namespace:path>` after visibility or its optional keywords and description. Enter icon IDs without quotes; tab completion suggests server item IDs and built-in VoxelMap IDs. `/wp details waypoint` includes an icon row with edit and clear controls.
- `/wp help [<topic>]` explains each command with usages you can click and examples.
- `/wp list` shows the current dimension: its lists with their first waypoints when they fit on one page, otherwise one line per list. Use `all`, a dimension, or a dimension plus list identifier to change the scope.
  - Options follow the order `search <query>`, `sort <default|name|distance|color>` (with `order <ascending|descending>` for the other sorts), `limit <1-100>`, `view <lists|tree|flat>`, `page <number>`. Quote multi-word values and list names that match an option word.
  - Pages hold whole lists: the tree view and the one-line-per-list view hold the page limit plus five lines; flat views and single lists hold the page limit in rows.
  - `/wp list dimensions [page <number>]` lists every dimension with its waypoint count.
- `/wp navigate` shows the navigation panel. `/wp navigate <dimension> <list> <waypoint> [<method>|default|all]` starts navigating, `/wp navigate use|disable <method>` turns one method on or off, `/wp navigate disable` stops, and `/wp navigate config text_display` adjusts the floating text.
- `/wp remote` shows the remote servers and their state; `/wp remote page <number>` pages it.
- `/wp remote list [<server> [<dimension> [<list>]]]` browses cached remote waypoints with the same options as `/wp list`. Quote exact identities, including names matching option words. Distance sorting reports that cross-server distances are unavailable.
  - Results are read-only and work through ordinary server chat. A coloured dot shows each server's state: available, stale, unreachable or no access. `/wp remote details <server> <dimension> <list> <waypoint>` shows one waypoint. Run `/wp help remote` for help.
  - Catalog synchronization starts when cross-server configuration is enabled. See [Velocity runtime setup](docs/features/cross-server/specs/cross-server-velocity-runtime.md) and [remote catalog queries](docs/features/cross-server/specs/cross-server-catalog-queries.md).
- `/wp remote tp <server> <dimension> <list> <waypoint>` requests a teleport using exact cached identities (quote names with spaces). Stale or missing targets fail before preparation; the player stays on the source until destination preparation and fresh permission checks succeed, and the destination confirms the arrival. See [remote teleport initiation](docs/features/cross-server/specs/cross-server-source-teleport.md).
  - Velocity and dedicated backend runtime integration is implemented and disabled by default. Suggestions use only the local cache. Quote dimension identities such as `"minecraft:overworld"`. See [configuration and validation](docs/features/cross-server/specs/cross-server-velocity-runtime.md).
- `/wp reload` reload `config.json` and translation files in `/config/server_waypoint/lang`, feature `sendXaerosWorldId` requires restarting to take effect.
- `/wp remove` removes a waypoint by identifier and answers with a temporary, single-use Restore link.
  - `/wp remove <dimension> <list-identifier>` removes an empty waypoint list.
- `/wp restore <token>` restores a recently removed waypoint while its temporary token remains valid.
- `/wp tp` teleport the executor player to a waypoint
- `/wp upload` shows the upload panel when your client has the mod. `/wp upload <xaero|voxelmap>` imports waypoints from the selected map mod on the executing player’s client. See [Uploading from client map mods](#uploading-from-client-map-mods) for conflict, force, and delete behavior.
```

In the cross-server setup sections, replace `run \`/wp remote servers\`, then \`/wp remote list survival\`` with `run \`/wp remote\`, then \`/wp remote list survival\``, and `Run \`/wp remote servers\` and \`/wp remote list survival\`` with `Run \`/wp remote\` and \`/wp remote list survival\``.

In the permission example, replace `// /wp remote servers and /wp remote list` with `// /wp remote and /wp remote list`.

Under `### Default Page Limit`, replace:

```markdown
  Sets the number of waypoints shown on each `/wp list` page when the command does not include `limit`. Values are constrained to `1-100`, and the default is `10`. This setting takes effect after `/wp reload`.
```

with:

```markdown
  Sets the page limit `L` that `/wp list` and `/wp remote list` use when the command does not include `limit`: flat views and single lists show `L` rows, while the tree view, the one-line-per-list view and the dimension and server lists hold `L + 5` lines. Values are constrained to `1-100`, and the default is `10`. This setting takes effect after `/wp reload`.
```

- [ ] **Step 2: The Chinese README**

In `README_zh.md`, replace the whole `## 命令` section (from `## 命令` through `- \`/wp tp\` 将执行该命令的玩家传送至指定路径点。`) with:

```markdown
## 命令
运行 `/wp` 打开菜单：大多数功能点一下即可使用，每个界面都会以链接给出下一步操作。控制台、RCON 和命令方块会收到相同信息的纯文本版本，其中写明标识符和坐标。
- `/wp add` 打开当前维度的列表选择，在你所在的位置添加路径点。同一列表中的标识符不能重复。
  - `/wp add <维度> <列表标识符>` 添加一个路径点列表。
  - `/wp add <维度> <列表标识符> <x y z> <名称>` 添加一个路径点，缩写自动生成，颜色随机。完整格式还可依次填写缩写、颜色、偏航角、可见范围、关键词、描述和 `icon <namespace:path>`。
- `/wp download [<维度> [<列表标识符> [<路径点标识符>]]]` 将路径点发送到你的地图模组（需客户端安装本模组）。
- `/wp details list <维度> <列表标识符>` 和 `/wp details waypoint <维度> <列表标识符> <路径点标识符>` 显示全部属性，并提供编辑按钮。
- `/wp edit list ...` 和 `/wp edit waypoint ...` 每次设置一个属性，或清除一个可选属性。不填值时，`set color` 打开颜色选择，`set yaw` 打开朝向选择；`set color random` 随机选择颜色。完整命令格式请运行 `/wp help edit`。
  - `/wp edit waypoint <维度> <列表> <路径点> set icon minecraft:diamond` 选用物品图标；使用 `clear icon` 恢复首字母标记。`voxelmap:star` 等 `voxelmap:` ID 可选用 VoxelMap 内置图片。`/wp add` 也接受在可选关键词和描述之后添加 `icon <namespace:path>`。图标 ID 不加引号；图标参数会补全服务器物品 ID 和 VoxelMap 内置图片 ID；`/wp details waypoint` 的图标行提供编辑和清除按钮。
- `/wp help [<主题>]` 说明各个命令，提供可点击的用法和示例。
- `/wp list` 显示当前维度：能在一页内显示时，列出各列表及其前几个路径点，否则每个列表占一行。可使用 `all`、维度，或维度加列表标识符更改范围。
  - 选项顺序为 `search <查询内容>`、`sort <default|name|distance|color>`（非默认排序可加 `order <ascending|descending>`）、`limit <1-100>`、`view <lists|tree|flat>`、`page <页码>`。包含空格的值，以及与选项名称相同的列表名称，需要加引号。
  - 每页只显示完整的列表：树状视图和每列表一行的视图每页最多显示每页数量加 5 行；平铺视图和单个列表每页显示每页数量的行数。
  - `/wp list dimensions [page <页码>]` 列出所有维度及其路径点数量。
- `/wp navigate` 显示导航面板。`/wp navigate <维度> <列表> <路径点> [<方式>|default|all]` 开始导航，`/wp navigate use|disable <方式>` 开启或关闭某种方式，`/wp navigate disable` 停止导航，`/wp navigate config text_display` 调整悬浮文字。
- `/wp remote` 显示远程服务器及其状态；`/wp remote page <页码>` 翻页。
- `/wp remote list [<服务器> [<维度> [<列表>]]]` 浏览缓存中的远程路径点，选项与 `/wp list` 相同。标识符必须精确匹配；包含空格或与选项名称相同的名称需要加引号。跨服务器距离不可用，因此按距离排序时会显示提示。
  - 结果仅供浏览，通过普通服务端聊天显示。彩色圆点表示每个服务器的状态：可用、已过期、无法连接或无权访问。`/wp remote details <服务器> <维度> <列表> <路径点>` 显示单个路径点。运行 `/wp help remote` 查看帮助。
  - 启用跨服务器配置后开始同步目录。参见[Velocity 运行时配置](docs/features/cross-server/specs/cross-server-velocity-runtime.md)和[远程目录查询](docs/features/cross-server/specs/cross-server-catalog-queries.md)。
- `/wp remote tp <服务器> <维度> <列表> <路径点>` 使用缓存中的精确标识符请求跨服务器传送（含空格的名称须加引号）。过期或不存在的目标会在准备阶段前被拒绝；目的地准备完成且重新检查权限通过前，玩家仍留在来源服务器，到达后由目的地服务器确认。参见[远程传送发起流程](docs/features/cross-server/specs/cross-server-source-teleport.md)。
  - Velocity 与专用后端的运行时集成已实现，默认禁用。命令补全仅使用本地缓存。维度标识符也须加引号，例如 `"minecraft:overworld"`。参见[配置与验证](docs/features/cross-server/specs/cross-server-velocity-runtime.md)。
- `/wp reload` 重载 `config.json` 和 `<config-path>/server_waypoint/lang/` 目录下的翻译文件。`sendXaerosWorldId` 特性需要重启服务器才能生效。
- `/wp remove` 按标识符删除路径点，并给出临时且仅可使用一次的恢复链接。
  - `/wp remove <维度> <列表标识符>` 删除一个空的路径点列表。
- `/wp restore <令牌>` 在临时令牌有效期间恢复最近删除的路径点。
- `/wp tp` 将执行该命令的玩家传送至指定路径点。
- `/wp upload` 在客户端安装了本模组时显示上传面板。`/wp upload <xaero|voxelmap>` 从执行玩家客户端上所选的地图模组导入路径点。冲突、强制覆盖和删除行为详见[从客户端地图模组上传](#从客户端地图模组上传)。
```

Replace `运行 \`/wp remote servers\` 和 \`/wp remote list survival\`` with `运行 \`/wp remote\` 和 \`/wp remote list survival\``, and `// /wp remote servers 和 /wp remote list` with `// /wp remote 和 /wp remote list`.

Under `### 默认每页数量 Default Page Limit`, replace:

```markdown
  设置 `/wp list` 命令未指定 `limit` 时每页显示的路径点数量。有效范围为 `1-100`，默认值为 `10`。使用 `/wp reload` 后此设置即可生效。
```

with:

```markdown
  设置 `/wp list` 和 `/wp remote list` 未指定 `limit` 时的每页数量 `L`：平铺视图和单个列表每页显示 `L` 行，树状视图、每列表一行的视图以及维度列表和服务器列表每页最多 `L + 5` 行。有效范围为 `1-100`，默认值为 `10`。使用 `/wp reload` 后此设置即可生效。
```

- [ ] **Step 3: The cross-server docs**

In `docs/features/cross-server/cross-server-admin.md`, replace `` `/wp remote servers`. Each healthy exported backend should become available. `` with `` `/wp remote`. Each healthy exported backend should show a green dot. ``.

In `docs/features/cross-server/specs/cross-server-catalog-queries.md`, insert after the title:

```markdown
> Updated on 2026-10-01 for the [command feedback redesign](../../command-feedback/specs/2026-10-01-command-feedback-design.md):
> `/wp remote` is the server picker, `/wp remote servers` and list details are removed, and remote
> lists use the local views and line budgets. The verification record below describes Step 11 as
> it shipped.
```

replace the paragraph that starts `` `RemoteCatalogQuery` consumes one immutable capture per query `` with:

```markdown
`RemoteCatalogQuery` captures one immutable cache snapshot per command as servers holding lists in
the shape the local screens use; unreachable and no-access servers keep no lists. The capture is a
fresh copy: nothing adds it to `WaypointFilesManagerCore` or writes it to local waypoint files.
Name/keyword filtering and sorting run through the local `WaypointQueryEngine.queryLists`, including
fuzzy matching. There is no destination-world lookup.
```

replace the grammar block:

```text
/wp remote
/wp help remote
/wp remote servers [page <number> [limit <1-100>]]
/wp remote list [<server> [<dimension> [<list>]]] [list options]
```

with:

```text
/wp remote [page <number>]
/wp help remote
/wp remote list [<server> [<dimension> [<list>]]] [list options]
/wp remote details <server> <dimension> <list> <waypoint>
```

replace the options block:

```text
search <query>
sort <default|name|distance|color> [order <ascending|descending>]
page <number>
limit <1-100>
view <tree|flat>
```

with:

```text
search <query>
sort <default|name|distance|color> [order <ascending|descending>]
limit <1-100>
view <lists|tree|flat>
page <number>
```

replace the paragraph that starts `The canonical order is search, sort/order, page, limit, view;` with:

```markdown
The canonical order is search, sort/order, limit, view, page; the earlier order (page before limit
and view) and a trailing search are still accepted. Default sorting has no order modifier.
`sort distance` is parsed but returns a localized error: coordinates from another server are never
compared with the executor's position, even if dimension names match. Name and color sorting work
in every view. Default sorting follows exact identities, because wire catalogs do not preserve a
local file's insertion order.
```

replace the paragraph that starts `Pagination defaults to the configured local page limit.` with:

```markdown
Pagination uses the local line budgets with the configured page limit `L`: the server picker, all
servers, a server's dimensions and lists, the tree view and the one-line-per-list view hold `L + 5`
lines; flat views, lists and search results hold `L` rows. A heading continued from the previous
page repeats with "(continued)". Out-of-range pages return the local page error. Generated links
keep the exact scope, filter, sort/order, page size and view in the canonical order, and quote
option-like identities at every scope level.
```

replace the example:

```text
/wp remote list "survival" "minecraft:overworld" "search" search village sort name page 1 limit 10 view flat
```

with:

```text
/wp remote list survival "minecraft:overworld" "search" search village sort name limit 10 view flat page 2
```

and replace the first two sentences of `## Availability, suggestions and feedback` (`Every displayed server is labeled AVAILABLE, STALE, UNAVAILABLE or UNAUTHORIZED. Stale snapshots remain advisory and visibly marked.`) with:

```markdown
A coloured dot shows every server's state: green available, yellow stale, red unreachable, dark gray
no access. Its tooltip names the state, and plain-text viewers read the state as a word. Stale
snapshots remain advisory, and their teleport links stay off until they refresh.
```

- [ ] **Step 4: The Adventure tips**

Append to `docs/tips/adventure-text.md`:

```markdown
## Chat kit

Command feedback is built from the pieces in `common/src/main/java/_959/server_waypoint/text/chat/`
(see the [command feedback design](../features/command-feedback/specs/2026-10-01-command-feedback-design.md)):

- **Separators.** `Chat.SEPARATOR` (` · ` in dark gray) and `Chat.CRUMB` (` › `) are components of
  their own. Translations never contain separators or glyphs; builders join the pieces with
  `Chat.join` and `Chat.concat`, which also skip pieces that are null.
- **The 256-character guard.** Every click goes through `Click`. A command longer than 256
  characters loses its click, because the client refuses it, keeps its text, and its tooltip loses
  the click hint.
- **Tooltips** come from `Tooltip`: a white first line naming the object or the action, gray detail
  lines, and aqua click hints and typing instructions last.
- **Plain-text viewers.** `Viewer.plainText()` is true when the output goes to the console, RCON or a
  command block, decided by the receiver, not the executing entity. They get no clicks, tooltips or
  controls: `Chat.link` keeps the label, `Chat.control` and `Chat.button` return null, and builders
  write out identifiers and coordinates instead.
- **One trailing newline.** The platform sender adds it for players (`PlatformMessageSender.forPlayer`);
  builders never end a message with a newline.
- **Layout.** `ChatFont` holds the vanilla font advances. `ChatAssert.assertFitsChat` checks a
  message against the 320 px width, the 20-line window, bold text and the vanilla glyphs, and
  `ScreenAuditTest` checks every screen.
```

- [ ] **Step 5: The spec status**

The feature README already links the design and this plan; Task 21 adds the validation record. In `docs/features/command-feedback/specs/2026-10-01-command-feedback-design.md`, replace:

```markdown
Status: design agreed on 2026-10-01. Not implemented yet.
```

with:

```markdown
Status: design agreed on 2026-10-01 and implemented; see the
[implementation plan](../plans/2026-10-01-command-feedback.md) and the
[validation record](../validation/2026-10-01-command-feedback-validation.md).
```

The validation record is written in Task 21; until then the link points at the file Task 21 creates.

- [ ] **Step 6: Check the links and commit**

```bash
grep -rn "remote servers" README.md README_zh.md docs/features/cross-server/cross-server-admin.md docs/features/cross-server/specs/cross-server-catalog-queries.md
```

Expected: only the history note at the top of `cross-server-catalog-queries.md`.

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git add README.md README_zh.md docs/features/cross-server/specs/cross-server-catalog-queries.md docs/features/cross-server/cross-server-admin.md docs/tips/adventure-text.md docs/features/command-feedback/specs/2026-10-01-command-feedback-design.md
```

```bash
/usr/bin/git commit -m "Document the redesigned command feedback"
```

---

### Task 21: Cross-version builds and the live check

Spec 21 ends with a live click-through on Paper and Fabric and the same commands from the console; spec 20 names the risks to check there: the `Open GUI` link (`/wp_gui` is a client command), translations that wrap, and plain-text detection under `/execute`. The `mods` changes touch Stonecutter-sensitive code (the dimension type lookup, the receiver check), so representative targets of every loader and the Paper versions compile first.

**Files:**
- Create: `docs/features/command-feedback/validation/2026-10-01-command-feedback-validation.md`
- Modify: `docs/features/command-feedback/README.md`
- Delete: `docs/features/command-feedback/validation/.gitkeep` (the folder is no longer empty)

- [ ] **Step 1: Compile a target of every loader and range**

Check the disks first; targets that were never built need room on the SSD:

```bash
df -h /System/Volumes/Data /Volumes/ssd
```

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:1.20.1-fabric:compileJava :mods:1.21.9-fabric:compileJava :mods:26.3-fabric:compileJava :mods:1.20.2-neoforge:compileJava :mods:1.21.11-neoforge:compileJava :mods:1.20.1-forge:compileJava :mods:26.2-forge:compileJava :paper:1.21-paper:compileJava :paper:1.21.11-paper:compileJava :paper:26.2-paper:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL. A failure in one range is usually an API that changed between versions; fix it with a Stonecutter predicate (see `AGENTS.md`), run `check_stonecutter.py` on the file, and compile that target again.

- [ ] **Step 2: Run the mods tests on the active target**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 3: The live click-through**

Use the `test-minecraft-server-commands` skill to start a Paper 26.2 server and a Fabric 26.1.2 server with this build, join a test player with operator permissions, and run the checks below on each. For every message the player receives, run each command its links carry (the click-through) and confirm it answers with a screen or an expected `✘` line. Then run the same commands from the server console. Note every result for the record.

1. **Menu and help.** `/wp`, `/wp help`, `/wp help list`. Every link answers; help lines suggest their usage.
2. **Creating.** `/wp add` (the add picker), `/wp add minecraft:overworld "Home Bases"` (✔ result with Add here and Open), a list link in the add picker followed by a name, and `/wp add minecraft:overworld "Home Bases" 120 64 -35 "Main Home" MH FFAA00 0 true`.
3. **Lists.** `/wp list` (Tree), the view row (Lists, Flat), `Search` with `farm`, the sort row (each mode and a reversal), the pager with more than one page (`/wp list minecraft:overworld limit 1`), `/wp list dimensions` and `/wp list all`.
4. **Details and edits.** Open a waypoint from a list, then use each `[✎]`, `Color` (a swatch, Random, Custom…), `Yaw` (a facing, Yours), the Visibility toggle, `[Here]`, a `[×]`, `[Back]`; open list details and its buttons.
5. **Results and errors.** `[Remove]` then Enter, `Restore`, `Undo` after an add, `/wp tp` from initials, `/wp list minecraft:overworld Missing`, `/wp tp minecraft:overworld "Home Bases" Gate`. A second player online sees the broadcasts.
6. **Navigation.** `/wp navigate` (not navigating), start from a waypoint's `[Navigate]`, toggle each method, `Adjust text display` and its nudges, `Stop`, then `Resume`.
7. **Upload and download.** `/wp upload` and `/wp download` from a vanilla client (an `✘` line each); with a modded client if one is available, the panel, a Merge upload and a download.
8. **Remote.** `/wp remote` without a cross-server setup ("No remote servers are connected yet.").
9. **Plain text.** From the console: `/wp` (the help index), `/wp list`, `/wp details waypoint minecraft:overworld "Home Bases" "Main Home"`, `/wp remove …` (the `Restore with` line), and `/execute as <player> run wp list` (plain text, because the console receives it).
10. **Risks.** The `Open GUI` link in the menu with a modded client on Fabric 26.1.2: does `/wp_gui` open the manager? Chat lines in `zh_cn` (`/wp list`, details): note any line that wraps.

- [ ] **Step 4: Write the validation record**

Create `docs/features/command-feedback/validation/2026-10-01-command-feedback-validation.md` with this structure, filled in from Steps 1 to 3:

```markdown
# Command feedback validation

Date: 2026-10-01. Build: the `cli-improved` branch at <commit>.

## Builds

| Target | Result |
| --- | --- |
| mods 1.20.1-fabric, 1.21.9-fabric, 26.3-fabric | <result> |
| mods 1.20.2-neoforge, 1.21.11-neoforge | <result> |
| mods 1.20.1-forge, 26.2-forge | <result> |
| paper 1.21, 1.21.11, 26.2 | <result> |
| `:common:test`, `:mods:26.1.2-fabric:test` | <tests passed> |

## Live click-through

| Check | Paper 26.2 | Fabric 26.1.2 | Console |
| --- | --- | --- | --- |
| Menu and help | | | |
| Creating | | | |
| Lists | | | |
| Details and edits | | | |
| Results and errors | | | |
| Navigation | | | |
| Upload and download | | | |
| Remote | | | |
| Plain text under `/execute` | | | |

## Risks

- `Open GUI` (`/wp_gui`): <what happened>.
- Translations that wrap: <lines>.

## Follow-ups

<anything found that this change does not fix, with where it was seen>
```

Write what was observed; when a check could not run (for example, no modded client for the upload panel or `Open GUI`), say so in its cell instead of leaving it blank.

In `docs/features/command-feedback/README.md`, add after the line `- [Implementation plan](plans/2026-10-01-command-feedback.md)`:

```markdown
- [Validation](validation/2026-10-01-command-feedback-validation.md)
```

- [ ] **Step 5: Commit**

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git rm --quiet docs/features/command-feedback/validation/.gitkeep
```

```bash
/usr/bin/git add docs/features/command-feedback/validation/2026-10-01-command-feedback-validation.md docs/features/command-feedback/README.md
```

```bash
/usr/bin/git commit -m "Record the command feedback validation"
```

Commit any fixes the live check needed in their own commits first, each with the tests that cover them.

---

## Decisions where the spec was silent or inexact

These choices fill gaps in the spec or correct it where a measurement disagreed. Each is pinned by a test in the task named.

1. **`Add here` in the created-list result** (Task 13). The spec's example `✔ Created the list Farms in Overworld   Add a waypoint here · Open` measures 339 px; `Add here` (or `Add waypoint` in another dimension) fits.
2. **The grammar is a superset; screens write one order** (Tasks 5 and 9). Commands accept the old option order (page before limit and view) and a trailing search, and also `limit`/`view` before `page`. Every link a screen builds uses search, sort, order, limit, view, page, so the pager can suggest a command that ends in `page `.
3. **Quick add takes a dimension** (Task 9). `/wp add <dimension> <list> <x y z> <name>` works without the rest of the properties, because list screens suggest it for lists in other dimensions.
4. **Flat view with Default sorting keeps the saved order** (Task 9): lists in their saved order, each list's rows in theirs. Before, flat Default sorted by name.
5. **Keys and plurals** (Task 2). New keys use the `wp.` prefix. `AdventureTranslator` formats arguments as text, so `ChoiceFormat` can't pick a plural; counts use `.one` and `.other` keys.
6. **Remote "published order" is identifier order** (Task 16). Catalog snapshots are unordered maps, so remote lists and waypoints follow their identifiers.
7. **`/wp remote page <n>`** pages the server picker (Task 16), with the dimension list's `L + 5` budget.
8. **Broadcasts** (Task 13) go to every other player, not the actor, and are built for each reader: the initials teleport only for readers who may teleport. Their position isn't known to the command layer, so their tooltips name the waypoint's dimension instead of a distance.
9. **`[Here]`** appears only when the viewer is in the waypoint's dimension (Task 12), since `~ ~ ~` would move the waypoint into another dimension's coordinates.
10. **Plain-text `/wp tp`, `/wp remove`, `/wp edit` and `/wp details` without a target** show that command's help topic (Task 9): the console has no dimension of its own, and the list hint is about clicking.
11. **The plain-text dimension list leaves out `All dimensions · N`** (Task 10), a line that is only a link; `/wp list all` is in the help index.
12. **Budgets** (Tasks 9 and 10). The Tree budget counts list headings and rows only (`L + 5`), not the header, search line or controls. All-dimensions search pages `L` rows, with dimension headings extra, as spec 7 says.
13. **`sendError` stays** (Tasks 6 and 13) as the call for errors, but no platform colours it any more: builders start errors with the red `✘`.
14. **`/wp remote`** shows the picker to readers who may browse; readers who may only teleport get the remote help (Task 16).
15. **Edit result wording** (Task 12): `Updated the colour` and `Updated the facing` for colour and yaw; `Cleared …` for a cleared property.
16. **Resume after Stop** (Task 14) navigates to the same waypoint with the configured default methods, since one command can't carry an arbitrary method set.
17. **Text display nudges** (Task 14) carry absolute values computed when the panel is built. Size nudges set all three axes from the X axis and stop at 0.05×.
18. **The remote source no longer reports success** (Task 17). The player has left by then; the destination's `✔ Arrived at …` line confirms the arrival and names the destination by its server ID, which is the name a backend publishes.
19. **Remote labels stay literal** (Task 16), so remote screens have their own reference helpers (`RemoteRefs`) instead of the local ones, which parse formatted text.
20. **The sharing prompt** (Task 13) suggests the full add command for each list on one line, which wraps when there are many lists. Its error names only the missing dimension, to fit one line.
21. **Download keeps no player check** (Task 15); delivery decides, as before, and the console gets the delivery error.
22. **Messages outside the command feedback stay**: the handshake and update notices in `C2SPacketHandler`, the navigation item and display texts (which keep the dimension ID, now coloured by `DimensionStyle.colorOf`), and the mod's client GUI texts.
23. **The Xaero's Minimap integration** (Task 18) builds its own `[AB] Name` for the client's chat, with the client's own translation of the teleport hint, because the server's `wp.` keys aren't in the client's language files.
24. **History docs stay** (Task 20): release notes, the original cross-server plan and the step-1 contract `cross-server-protocol-v1.md`.
25. **Follow-up uploads are suggested, not run** (Task 15): `Prefer mine` and `Try again` overwrite or may delete waypoints, so they ask for Enter like other destructive actions.
26. **The identifier-collision error** (Task 13) reads `That identifier is already in use.`: the edit result knows the target, not the identifier that collided.
