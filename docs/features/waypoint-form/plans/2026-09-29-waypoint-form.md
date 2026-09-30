# Waypoint Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the waypoint add and edit screens with the compact, fixed form the design describes: one aligned label column, live checks with footer feedback, keywords and description fields, an Add flow that waits for the waypoint to appear, Tab/Enter/tooltip behavior, and text in all six locales.

**Architecture:** A pure layout helper (`WaypointFormLayout`) turns measured sizes into column widths, the row gap and every position; pure helpers hold the checks (`WaypointFormCheck`), the edit patch (`WaypointFormPatch`), the pending-add timer (`PendingAdd`) and the initials rule (`WaypointFormInitials`). `AbstractWaypointPropertiesScreen` is rewritten to build the form from those helpers, once, in `init()`; `WaypointAddScreen` and `WaypointEditScreen` supply what differs. A few small widget additions (placeholder, invalid outline, icon button, pointer tooltip) support it.

**Tech Stack:** Java 17, Minecraft GUI classes across Stonecutter targets 1.20.1–26.3 (Fabric, Forge, NeoForge), JUnit 5, GSON, Gradle Kotlin DSL (no build changes).

**Spec:** [`docs/features/waypoint-form/specs/2026-09-29-waypoint-form-design.md`](../specs/2026-09-29-waypoint-form-design.md). The plan argues from it, so read both. Where they differ, the spec wins; the "Decisions where the spec was silent or inexact" section at the end lists the few places this plan chose.

## Global Constraints

- Java 17 compatible code, 4-space indentation, never tabs; Kotlin DSL for Gradle scripts (none change); GSON for JSON. (`AGENTS.md`)
- No build script changes are expected. (spec, Constraints)
- Every Stonecutter target from 1.20.1 to 26.3 must keep working. Version differences stay in `DrawContextHelper` and the existing branches. No new swap or replacement is planned; if one becomes necessary, update the inventory in `AGENTS.md` in the same change. (spec, Constraints)
- No backward-compatibility code; removed translation keys need no migration. (`AGENTS.md`, spec)
- No test or probe reads these screens' members, so their fields may be renamed. (spec)
- A helper used by one project stays in that project: everything here is `mods` code except `FormattedTextHelper.parseKeywords`, which `/wp add` and the form share. (`AGENTS.md`)
- Update `docs/tips/gui/local-guide.md` in the same change as every API added or changed under `mods/src/main/java/_959/server_waypoint/common/client/gui`. (`AGENTS.md`)
- Do not commit. Every task ends with a checkpoint instead. (`AGENTS.md`: commit only when asked)
- Stonecutter: keep inactive branches, keep `//~` tokens before the first code line, keep `//$ render_method_swap` attached to the method name, and check marker balance after editing. (`AGENTS.md`)
- Text is sentence case with no trailing colons, in all six locales: `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk`, `zh_tw`. Non-English strings are machine drafts that need a native speaker's review before release. Each locale keeps its existing terms. (spec, section 7)
- Sizes: 10-pixel screen margin, 6-pixel gaps around the panel, 7 pixels of padding inside a 1-pixel outline, rows 9 pixels apart (down to 5 only when the group is too tall), a 240-pixel control column when the screen allows. It must fit 320×240. (spec, section 1)

## Review Focus

The spec describes what the form must do; these are inputs a person will hit that it doesn't spell out. Each has a test in the task that owns the code.

1. **A saved empty display-name override** (the marker deliberately shows no name). The Edit title must still name the waypoint, the field shows the "Empty: the marker shows no name" placeholder, and saving an untouched form must not clear the override. Pinned by `WaypointFormPatchTest` in Task 4.
2. **A very long list display name, dimension or waypoint name in the header.** The title and subtitle stay on one line inside the panel and end with "…". Pinned by `WaypointFormTextTest` in Task 11.
3. **Names that differ only by case or by a trailing space.** The server compares names exactly, so they are different waypoints and Add must not block on them. Pinned by `WaypointFormCheckTest` in Task 5.
4. **Synced data changing under an open form**, for example someone else adds the same name while you type. The next tick's checks must see it. Pinned by `WaypointFormCheckTest` in Task 5.
5. **Enter while a suggestion list is open** must take the suggestion, not send the form. Pinned by `SuggestingTextInputTest` in Task 9.

## Environment and conventions

Read [`AGENTS.md`](../../../../AGENTS.md) first. Read [`docs/tips/gui/local-guide.md`](../../../tips/gui/local-guide.md) before Tasks 8–11, which change GUI APIs and render entry points. The skills `stonecutter-gradle-version-tasks`, `stonecutter-versioned-comments` and `reference-minecraft-source-code` help with the version work.

- **Checkout.** Work in `/Volumes/ssd/fabric_mods_repo/server_waypoint` on branch `waypoint-manager-improved` with commit `b8c60744` (the spec) in its history. This branch has the unmerged work the plan builds on: `SettingsListWidget`, `ClientConfigScreen.statusAboveButtons`, `TranslucentButton.fitted`, the `IconButton` tint and the redesigned manager. If your worktree was branched from `master`, stop and ask.
- **Git.** Call it as `/usr/bin/git` from the repository root, without `-C` and without globs. Never commit.
- **Gradle.** The Bash tool doesn't source `~/.zshrc`, so every Gradle command sets the SSD home first, in the same command. Targets for 26.x need the JDK 25 path. Use literal paths, not shell variables (the `rtk` hook shifts their arguments). All test commands below use this shape:

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormInitialsTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

- **Active Stonecutter target** is `26.1.2-fabric` (`mods/stonecutter.gradle.kts`). Source files in `mods/src` are written in that target's form, with the other versions in comment branches.
- **Disk.** The SSD is nearly full and the user's floor is 1.5 GB free. Run `df -h /System/Volumes/Data /Volumes/ssd` before building a target that was never built, and say exactly which targets you compiled. `--offline` works for Fabric targets and fails for NeoForge and Forge (`netty 4.1.+`).
- **Marker check.** Save this script as `check_stonecutter.py` in your scratchpad directory. It verifies that the Stonecutter markers in the given Java files are balanced. Below, `SCRATCH` stands for that directory's literal path.

```python
#!/usr/bin/env python3
"""Checks that the Stonecutter conditional markers in the given Java files are balanced:
every `//? if ... {` has its `//?}`, `else`/`elif` lines sit inside one, and every inline
`/*? ... {*/` has its `/*?}*/`. Usage: check_stonecutter.py FILE..."""
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

- **Checkpoint.** Every task ends with the same two commands, then a look at the file list. Nothing is committed.

```bash
/usr/bin/git diff --check
```

```bash
/usr/bin/git status --short
```

## File structure

| File | Responsibility |
| --- | --- |
| `common/.../text/FormattedTextHelper.java` (modify) | Gains `parseKeywords`, moved from `CoreWaypointCommand`, so `/wp add` and the form split keywords the same way |
| `mods/.../gui/screens/WaypointFormInitials.java` (new) | The rule for when the initials follow the name |
| `mods/.../gui/screens/PendingAdd.java` (new) | The dimension, list, name and 5-second deadline of an Add that has been sent |
| `mods/.../gui/screens/WaypointFormPatch.java` (new) | Builds the edit patch from the saved waypoint and the form's values; whether it changes anything |
| `mods/.../gui/screens/WaypointFormCheck.java` (new) | The checks, in order, returning the first problem as enums and strings |
| `mods/.../gui/screens/WaypointFormLayout.java` (new) | Column widths, row gap, positions, footer and status placement |
| `mods/.../gui/render/DrawContextHelper.java`, `.../widgets/SettingsListWidget.java` (modify) | One helper that schedules a tooltip at the pointer, used by the settings list and the form |
| `mods/.../gui/widgets/SuggestingTextInput.java`, `TranslucentTextField.java`, `ComboBoxWidget.java`, `ColorHexCodeField.java` (modify) | Themed placeholder, danger outline, list state and Enter's use of the highlighted suggestion |
| `mods/.../gui/widgets/WaypointIconPicker.java`, `render/WidgetTextures.java`, `textures/gui/clear.png` (modify, new) | The 13×13 remove-icon button and the dropdown's placeholder |
| `mods/.../gui/screens/AbstractWaypointPropertiesScreen.java`, `WaypointAddScreen.java`, `WaypointEditScreen.java` (rewrite) | The form itself |
| `mods/src/main/resources/assets/server_waypoint/lang/*.json` (modify) | Six locales |
| `docs/tips/gui/local-guide.md`, `README.md`, `README_zh.md`, `docs/features/waypoint-form/*` (modify, new) | Documentation and the validation record |

---

### Task 1: Keyword parsing in `FormattedTextHelper`

`/wp add` splits its keywords argument on commas, trims each keyword and drops empty ones, in a private method of `CoreWaypointCommand`. The form must split the same way, so the method moves to the shared helper.

**Files:**
- Modify: `common/src/main/java/_959/server_waypoint/text/FormattedTextHelper.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Test: `common/src/test/java/_959/server_waypoint/text/FormattedTextHelperTest.java`

**Interfaces:**
- Produces: `public static List<String> FormattedTextHelper.parseKeywords(String rawKeywords)`, an unmodifiable list; empty for null, empty or blank input.

- [ ] **Step 1: Write the failing tests**

In `common/src/test/java/_959/server_waypoint/text/FormattedTextHelperTest.java`, replace:

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

with:

```java
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

In `common/src/test/java/_959/server_waypoint/text/FormattedTextHelperTest.java`, replace:

```java
        assertFalse(FormattedTextHelper.isValidInput("{not valid json}"));
    }
}
```

with:

```java
        assertFalse(FormattedTextHelper.isValidInput("{not valid json}"));
    }

    @Test
    void parseKeywordsSplitsOnCommasAndTrimsEachKeyword() {
        assertEquals(List.of("home", "base", "farm"), FormattedTextHelper.parseKeywords("home,  base ,farm"));
    }

    @Test
    void parseKeywordsDropsEmptyEntries() {
        assertEquals(List.of("home", "base"), FormattedTextHelper.parseKeywords(",home,, ,base,"));
    }

    @Test
    void parseKeywordsReturnsNoKeywordsForAnEmptyBlankOrMissingString() {
        assertEquals(List.of(), FormattedTextHelper.parseKeywords(""));
        assertEquals(List.of(), FormattedTextHelper.parseKeywords("   "));
        assertEquals(List.of(), FormattedTextHelper.parseKeywords(null));
    }

    @Test
    void parseKeywordsKeepsTheCaseAndInnerSpacesOfEachKeyword() {
        assertEquals(List.of("Home Base", "NETHER"), FormattedTextHelper.parseKeywords(" Home Base , NETHER"));
    }

    @Test
    void parseKeywordsReturnsAListThatCannotBeChanged() {
        List<String> keywords = FormattedTextHelper.parseKeywords("a, b");

        assertThrows(UnsupportedOperationException.class, () -> keywords.add("c"));
    }
}
```

- [ ] **Step 2: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test --tests '_959.server_waypoint.text.FormattedTextHelperTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: method parseKeywords(String)`.

- [ ] **Step 3: Add the method**

In `common/src/main/java/_959/server_waypoint/text/FormattedTextHelper.java`, replace:

```java
import java.util.HashSet;
```

with:

```java
import java.util.ArrayList;
import java.util.HashSet;
```

In `common/src/main/java/_959/server_waypoint/text/FormattedTextHelper.java`, replace:

```java
    private static boolean looksLikeJson(String rawText) {
```

with:

```java
    /**
     * Splits comma-separated keywords, trimming each and dropping empty entries, the way {@code /wp add}
     * reads its keywords argument.
     */
    public static List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.trim().isEmpty()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String keyword : rawKeywords.split(",", -1)) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return List.copyOf(keywords);
    }

    private static boolean looksLikeJson(String rawText) {
```

- [ ] **Step 4: Make `CoreWaypointCommand` use it**

`CoreWaypointCommand` already has `import static _959.server_waypoint.text.FormattedTextHelper.*;`, so its three `parseKeywords(...)` calls resolve to the shared method once the private one is gone.

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
    private static List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.trim().isEmpty()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String keyword : rawKeywords.split(",", -1)) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return List.copyOf(keywords);
    }

    private static WaypointPatch patchWithString(
```

with:

```java
    private static WaypointPatch patchWithString(
```

In `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`, replace:

```java
import java.util.ArrayList;
import java.util.List;
```

with:

```java
import java.util.List;
```

- [ ] **Step 5: Run the common tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS, including `FormattedTextHelperTest` and the command tests.

- [ ] **Step 6: Checkpoint** (see Environment and conventions).

---

### Task 2: The initials rule

The initials follow the name while they still equal what `WaypointInitials.getDefaultInitials` makes of the previous name. Once the player types other initials, changing the name leaves them alone. On Edit they follow only if the saved initials were the default for the saved name.

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormInitials.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormInitialsTest.java`

**Interfaces:**
- Produces: `static String WaypointFormInitials.defaultFor(String name)` (the default initials of a name that may hold formatted text) and `static String afterNameChange(String previousName, String newName, String initials)` (what the initials become after the name changed).

- [ ] **Step 1: Write the failing test**

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormInitialsTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointFormInitialsTest {
    @Test
    void theDefaultComesFromTheNamesPlainText() {
        assertEquals("HB", WaypointFormInitials.defaultFor("Home Base"));
        assertEquals("HB", WaypointFormInitials.defaultFor("{\"text\":\"Home Base\",\"color\":\"red\"}"));
        assertEquals("", WaypointFormInitials.defaultFor(""));
    }

    @Test
    void initialsFollowTheNameWhileTheyEqualTheDefaultForThePreviousName() {
        // Add starts with no name and no initials, which is the default for an empty name.
        String initials = "";
        initials = WaypointFormInitials.afterNameChange("", "Home", initials);
        assertEquals(WaypointFormInitials.defaultFor("Home"), initials);
        initials = WaypointFormInitials.afterNameChange("Home", "Home Base", initials);
        assertEquals("HB", initials);
        initials = WaypointFormInitials.afterNameChange("Home Base", "Camp Site", initials);
        assertEquals("CS", initials);
    }

    @Test
    void initialsTheUserTypedStayWhenTheNameChanges() {
        assertEquals("X", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "X"));
        assertEquals("HBX", WaypointFormInitials.afterNameChange("Home Base", "Home Bases", "HBX"));
    }

    @Test
    void emptiedInitialsStayEmptyOnceTheNameHasAnotherDefault() {
        // Deleting the initials is typing other initials: "" is not the default for "Home Base".
        assertEquals("", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", ""));
    }

    @Test
    void initialsTypedBackToTheDefaultFollowTheNameAgain() {
        assertEquals("CS", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "HB"));
    }

    @Test
    void onEditTheInitialsFollowOnlyIfTheSavedOnesAreTheDefaultForTheSavedName() {
        // Saved as the default for "Home Base": editing the name updates them.
        assertEquals("CS", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "HB"));
        // Saved as something else: they stay.
        assertEquals("ZZ", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "ZZ"));
    }

    @Test
    void formattedNamesFollowThroughTheirPlainText() {
        String json = "{\"text\":\"Home Base\"}";

        assertEquals("CS", WaypointFormInitials.afterNameChange(json, "Camp Site", "HB"));
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormInitialsTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class WaypointFormInitials`.

- [ ] **Step 3: Write the implementation**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormInitials.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.WaypointInitials.getDefaultInitials;

/**
 * When the form's initials follow the name: as long as they still equal what
 * {@code WaypointInitials.getDefaultInitials} makes of the previous name. Once the player types
 * something else they stay, and on Edit they follow only if the saved ones were the default.
 */
final class WaypointFormInitials {
    private WaypointFormInitials() {
    }

    /** The initials made from a name, which may hold formatted text. */
    static String defaultFor(String name) {
        return getDefaultInitials(plainText(name));
    }

    /** The initials to show after the name changed from {@code previousName} to {@code newName}. */
    static String afterNameChange(String previousName, String newName, String initials) {
        return initials.equals(defaultFor(previousName)) ? defaultFor(newName) : initials;
    }
}
```

- [ ] **Step 4: Run the test**

Same command as Step 2. Expected: PASS, 7 tests.

- [ ] **Step 5: Checkpoint.**

---

### Task 3: The pending Add

`/wp add` has no reply, so after sending it the form watches the synced data for the waypoint. `PendingAdd` remembers what was sent and when to give up. It uses a nanosecond clock passed in by the caller, like `EditResponseDeadline`, so it can be tested without a wall clock.

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/PendingAdd.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/PendingAddTest.java`

**Interfaces:**
- Produces: `PendingAdd` with `TIMEOUT_NANOS` (5 s), `boolean pending()`, `String dimension()`, `String list()`, `String name()` (only while pending), `void begin(String dimension, String list, String name, long nowNanos)` (throws if one is pending), `boolean expire(long nowNanos)` (true only for the call that ends the wait) and `void clear()`.

- [ ] **Step 1: Write the failing test**

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/PendingAddTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAddTest {
    @Test
    void nothingIsPendingUntilAnAddBegins() {
        PendingAdd add = new PendingAdd();

        assertFalse(add.pending());
        assertFalse(add.expire(Long.MAX_VALUE));
        assertThrows(IllegalStateException.class, add::name);
    }

    @Test
    void itRemembersWhatIsBeingAdded() {
        PendingAdd add = new PendingAdd();

        add.begin("minecraft:overworld", "Base", "Home", 100);

        assertTrue(add.pending());
        assertEquals("minecraft:overworld", add.dimension());
        assertEquals("Base", add.list());
        assertEquals("Home", add.name());
    }

    @Test
    void itExpiresAtFiveSecondsAndNotBefore() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 100);

        assertEquals(5_000_000_000L, PendingAdd.TIMEOUT_NANOS);
        assertFalse(add.expire(100 + PendingAdd.TIMEOUT_NANOS - 1));
        assertTrue(add.pending());
        assertTrue(add.expire(100 + PendingAdd.TIMEOUT_NANOS));
        assertFalse(add.pending());
    }

    @Test
    void itExpiresOnlyOnce() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);

        assertTrue(add.expire(PendingAdd.TIMEOUT_NANOS));
        assertFalse(add.expire(PendingAdd.TIMEOUT_NANOS * 2));
    }

    @Test
    void theClockMayWrapAround() {
        PendingAdd add = new PendingAdd();
        long start = Long.MAX_VALUE - 1_000;
        add.begin("minecraft:overworld", "Base", "Home", start);

        assertFalse(add.expire(Long.MAX_VALUE));
        assertTrue(add.expire(start + PendingAdd.TIMEOUT_NANOS));
    }

    @Test
    void nothingIsPendingAfterClearing() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);

        add.clear();

        assertFalse(add.pending());
        assertFalse(add.expire(PendingAdd.TIMEOUT_NANOS * 10));
        assertThrows(IllegalStateException.class, add::list);
    }

    @Test
    void anotherAddCannotBeginWhileOneIsPending() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);

        assertThrows(IllegalStateException.class, () -> add.begin("minecraft:the_nether", "Base", "Fort", 1));
    }

    @Test
    void anAddCanBeginAgainAfterItEnds() {
        PendingAdd add = new PendingAdd();
        add.begin("minecraft:overworld", "Base", "Home", 0);
        add.clear();

        add.begin("minecraft:the_nether", "Base", "Fort", 10);

        assertEquals("Fort", add.name());
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.PendingAddTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class PendingAdd`.

- [ ] **Step 3: Write the implementation**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/PendingAdd.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the waypoint an Add form has asked the server for, without a wall clock. There is no
 * correlated reply to /wp add, so the screen watches for the waypoint to appear in the synced data
 * and gives up after {@link #TIMEOUT_NANOS}.
 */
final class PendingAdd {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    private @Nullable String dimension;
    private @Nullable String list;
    private @Nullable String name;
    private long deadlineNanos;

    boolean pending() {
        return this.name != null;
    }

    /** The dimension of the pending waypoint; only while {@link #pending()}. */
    String dimension() {
        return this.required(this.dimension);
    }

    /** The list of the pending waypoint; only while {@link #pending()}. */
    String list() {
        return this.required(this.list);
    }

    /** The name of the pending waypoint; only while {@link #pending()}. */
    String name() {
        return this.required(this.name);
    }

    void begin(String dimension, String list, String name, long nowNanos) {
        if (this.pending()) {
            throw new IllegalStateException("A waypoint is already being added");
        }
        this.dimension = dimension;
        this.list = list;
        this.name = name;
        this.deadlineNanos = nowNanos + TIMEOUT_NANOS;
    }

    /** Ends the wait once its deadline has come; true only for the call that ends it. */
    boolean expire(long nowNanos) {
        if (!this.pending() || nowNanos - this.deadlineNanos < 0) {
            return false;
        }
        this.clear();
        return true;
    }

    void clear() {
        this.dimension = null;
        this.list = null;
        this.name = null;
        this.deadlineNanos = 0;
    }

    private String required(@Nullable String value) {
        if (value == null) {
            throw new IllegalStateException("No waypoint is being added");
        }
        return value;
    }
}
```

- [ ] **Step 4: Run the test**

Same command as Step 2. Expected: PASS, 8 tests.

- [ ] **Step 5: Checkpoint.**

---

### Task 4: The edit patch

The patch holds only the fields that differ from the saved waypoint. The Display name field holds only the override: empty over no override changes nothing, empty over a saved override clears it, and an empty saved override stays put unless the player types text. Keywords compare as parsed lists and the description as text.

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormPatch.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormPatchTest.java`

**Interfaces:**
- Produces: `WaypointFormPatch.Saved` (record of the saved values; `Saved.of(SimpleWaypoint)`; `titleName()`), `WaypointFormPatch.Values` (record of the form's values), `static WaypointPatch build(Saved, Values)`, `static PatchField<String> displayName(@Nullable String savedOverride, String field)` and `static boolean changesAnything(WaypointPatch)`.
- Consumes: `WaypointPatch` and `PatchField` from `common`.

- [ ] **Step 1: Write the failing test** (this pins Review Focus item 1)

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormPatchTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointFormPatch.Saved;
import _959.server_waypoint.common.client.gui.screens.WaypointFormPatch.Values;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormPatchTest {
    private static final WaypointPos HERE = new WaypointPos(120, 64, -35);
    private static final NamespacedId DIAMOND = new NamespacedId("minecraft", "diamond");

    @Test
    void anUntouchedFormChangesNothing() {
        WaypointPatch patch = WaypointFormPatch.build(saved(null, null), same());

        assertFalse(WaypointFormPatch.changesAnything(patch));
    }

    @Test
    void noOverrideAndAnEmptyFieldLeavesTheDisplayNameUnchanged() {
        assertEquals(PatchField.unchanged(), WaypointFormPatch.displayName(null, ""));
    }

    @Test
    void noOverrideAndTextSetsTheOverride() {
        assertEquals(PatchField.set("Home Base"), WaypointFormPatch.displayName(null, "Home Base"));
    }

    @Test
    void anOverrideKeptAsItIsLeavesTheDisplayNameUnchanged() {
        assertEquals(PatchField.unchanged(), WaypointFormPatch.displayName("Home Base", "Home Base"));
    }

    @Test
    void anOverrideEmptiedInTheFieldClearsIt() {
        assertEquals(PatchField.clear(), WaypointFormPatch.displayName("Home Base", ""));
    }

    @Test
    void anOverrideReplacedByOtherTextSetsTheNewText() {
        assertEquals(PatchField.set("Camp"), WaypointFormPatch.displayName("Home Base", "Camp"));
    }

    @Test
    void anEmptyOverrideLeftEmptyLeavesTheDisplayNameUnchanged() {
        assertEquals(PatchField.unchanged(), WaypointFormPatch.displayName("", ""));
    }

    @Test
    void anEmptyOverrideGivenTextSetsTheText() {
        assertEquals(PatchField.set("Camp"), WaypointFormPatch.displayName("", "Camp"));
    }

    @Test
    void theDisplayNameRowsReachThePatch() {
        Values typed = new Values("Home", "Home Base", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", null);

        assertEquals(PatchField.set("Home Base"), WaypointFormPatch.build(saved(null, null), typed).displayName());
        assertEquals(PatchField.clear(), WaypointFormPatch.build(saved("Home Base", null), same()).displayName());
    }

    @Test
    void aChangedFieldIsSetAndTheRestStayUnchanged() {
        Values moved = new Values("Home", "", "H", new WaypointPos(1, 64, -35), 0xFFAA00, 0, true,
                List.of("home", "base"), "Beds upstairs", null);

        WaypointPatch patch = WaypointFormPatch.build(saved(null, null), moved);

        assertEquals(PatchField.set(new WaypointPos(1, 64, -35)), patch.position());
        assertTrue(patch.identifier().isUnchanged());
        assertTrue(patch.displayName().isUnchanged());
        assertTrue(patch.initials().isUnchanged());
        assertTrue(patch.color().isUnchanged());
        assertTrue(patch.yaw().isUnchanged());
        assertTrue(patch.visibility().isUnchanged());
        assertTrue(patch.keywords().isUnchanged());
        assertTrue(patch.description().isUnchanged());
        assertTrue(patch.icon().isUnchanged());
        assertTrue(WaypointFormPatch.changesAnything(patch));
    }

    @Test
    void everyOrdinaryFieldIsSetWhenItChanges() {
        Values changed = new Values("Camp", "", "C", HERE, 0x112233, 90, false, List.of(), "Beds", DIAMOND);

        WaypointPatch patch = WaypointFormPatch.build(saved(null, null), changed);

        assertEquals(PatchField.set("Camp"), patch.identifier());
        assertEquals(PatchField.set("C"), patch.initials());
        assertEquals(PatchField.set(0x112233), patch.color());
        assertEquals(PatchField.set(90), patch.yaw());
        assertEquals(PatchField.set(false), patch.visibility());
        assertEquals(PatchField.set("Beds"), patch.description());
        assertEquals(PatchField.set(DIAMOND), patch.icon());
    }

    @Test
    void theColorComparesWithoutItsAlpha() {
        Values opaque = new Values("Home", "", "H", HERE, 0xFFFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", null);

        assertFalse(WaypointFormPatch.changesAnything(WaypointFormPatch.build(saved(null, null), opaque)));
    }

    @Test
    void keywordsAreSetOnlyWhenTheParsedListsDiffer() {
        Saved saved = saved(null, null);

        assertTrue(WaypointFormPatch.build(saved, withKeywords(List.of("home", "base"))).keywords().isUnchanged());
        assertEquals(PatchField.set(List.of("base", "home")),
                WaypointFormPatch.build(saved, withKeywords(List.of("base", "home"))).keywords());
        assertEquals(PatchField.set(List.of()), WaypointFormPatch.build(saved, withKeywords(List.of())).keywords());
    }

    @Test
    void theDescriptionComparesAsText() {
        Saved saved = saved(null, null);

        assertTrue(WaypointFormPatch.build(saved, withDescription("Beds upstairs")).description().isUnchanged());
        assertEquals(PatchField.set(""), WaypointFormPatch.build(saved, withDescription("")).description());
    }

    @Test
    void anIconIsSetClearedOrLeftAlone() {
        Saved withIcon = saved(null, DIAMOND);

        assertTrue(WaypointFormPatch.build(withIcon, withIcon(DIAMOND)).icon().isUnchanged());
        assertEquals(PatchField.clear(), WaypointFormPatch.build(withIcon, withIcon(null)).icon());
        assertEquals(PatchField.set(DIAMOND), WaypointFormPatch.build(saved(null, null), withIcon(DIAMOND)).icon());
    }

    @Test
    void savedReadsTheOverrideRatherThanTheDisplayName() {
        SimpleWaypoint plain = new SimpleWaypoint("Home", "Home", "H", HERE, 0xFFAA00, 0, true, List.of("home"), "Beds");
        SimpleWaypoint override = new SimpleWaypoint("home", "Home Base", "H", HERE, 0xFFAA00, 0, true, List.of("home"), "Beds");
        SimpleWaypoint empty = new SimpleWaypoint("home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home"), "Beds");

        assertNull(Saved.of(plain).displayNameOverride());
        assertEquals("Home Base", Saved.of(override).displayNameOverride());
        assertEquals("", Saved.of(empty).displayNameOverride());
        assertEquals(List.of("home"), Saved.of(plain).keywords());
        assertEquals("Beds", Saved.of(plain).description());
    }

    @Test
    void theTitleShowsTheOverrideOrElseTheName() {
        assertEquals("Home", saved(null, null).titleName());
        assertEquals("Home Base", saved("Home Base", null).titleName());
        // An empty override makes the marker show no name, but the title still names the waypoint.
        assertEquals("Home", saved("", null).titleName());
    }

    private static Saved saved(String override, NamespacedId icon) {
        return new Saved("Home", override, "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", icon);
    }

    /** The form as it opens on the waypoint from {@code saved(null, null)}: nothing edited. */
    private static Values same() {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", null);
    }

    private static Values withKeywords(List<String> keywords) {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, keywords, "Beds upstairs", null);
    }

    private static Values withDescription(String description) {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), description, null);
    }

    private static Values withIcon(NamespacedId icon) {
        return new Values("Home", "", "H", HERE, 0xFFAA00, 0, true, List.of("home", "base"), "Beds upstairs", icon);
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormPatchTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class WaypointFormPatch`.

- [ ] **Step 3: Write the implementation**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormPatch.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the patch an Edit form sends: a field is set or cleared only when the form's value differs
 * from the saved waypoint. It holds no Minecraft classes, so it can be unit tested.
 */
final class WaypointFormPatch {
    private WaypointFormPatch() {
    }

    /**
     * The waypoint as saved. {@code displayNameOverride} is null when it has no override, and empty
     * when its marker deliberately shows no name.
     */
    record Saved(
            String name,
            @Nullable String displayNameOverride,
            String initials,
            WaypointPos position,
            int rgb,
            int yaw,
            boolean global,
            List<String> keywords,
            String description,
            @Nullable NamespacedId icon
    ) {
        /** What the Edit title shows: the override, or the name when there is none or it is empty. */
        String titleName() {
            return this.displayNameOverride == null || this.displayNameOverride.isEmpty() ? this.name : this.displayNameOverride;
        }

        static Saved of(SimpleWaypoint waypoint) {
            return new Saved(
                    waypoint.name(),
                    waypoint.displayNameOverride(),
                    waypoint.initials(),
                    waypoint.pos(),
                    waypoint.rgb(),
                    waypoint.yaw(),
                    waypoint.global(),
                    waypoint.keywords(),
                    waypoint.description(),
                    waypoint.icon()
            );
        }
    }

    /** What the form holds. {@code displayName} is the Display name field, where empty means no override. */
    record Values(
            String name,
            String displayName,
            String initials,
            WaypointPos position,
            int rgb,
            int yaw,
            boolean global,
            List<String> keywords,
            String description,
            @Nullable NamespacedId icon
    ) {
    }

    static WaypointPatch build(Saved saved, Values values) {
        return new WaypointPatch(
                changed(saved.name(), values.name()),
                displayName(saved.displayNameOverride(), values.displayName()),
                changed(saved.initials(), values.initials()),
                changed(saved.position(), values.position()),
                changed(saved.rgb() & 0xFFFFFF, values.rgb() & 0xFFFFFF),
                changed(saved.yaw(), values.yaw()),
                changed(saved.global(), values.global()),
                changed(saved.keywords(), values.keywords()),
                changed(saved.description(), values.description()),
                icon(saved.icon(), values.icon())
        );
    }

    /**
     * The display-name part. The field holds only the override, so an empty field over a saved override
     * clears it, and over no override or an empty one changes nothing.
     */
    static PatchField<String> displayName(@Nullable String savedOverride, String field) {
        if (savedOverride == null) {
            return field.isEmpty() ? PatchField.unchanged() : PatchField.set(field);
        }
        if (field.equals(savedOverride)) {
            return PatchField.unchanged();
        }
        return field.isEmpty() ? PatchField.clear() : PatchField.set(field);
    }

    /** Whether the patch sets or clears any field. */
    static boolean changesAnything(WaypointPatch patch) {
        return !patch.identifier().isUnchanged()
                || !patch.displayName().isUnchanged()
                || !patch.initials().isUnchanged()
                || !patch.position().isUnchanged()
                || !patch.color().isUnchanged()
                || !patch.yaw().isUnchanged()
                || !patch.visibility().isUnchanged()
                || !patch.keywords().isUnchanged()
                || !patch.description().isUnchanged()
                || !patch.icon().isUnchanged();
    }

    private static PatchField<NamespacedId> icon(@Nullable NamespacedId saved, @Nullable NamespacedId selected) {
        if (Objects.equals(saved, selected)) {
            return PatchField.unchanged();
        }
        return selected == null ? PatchField.clear() : PatchField.set(selected);
    }

    private static <T> PatchField<T> changed(T saved, T value) {
        return Objects.equals(saved, value) ? PatchField.unchanged() : PatchField.set(value);
    }
}
```

- [ ] **Step 4: Run the test**

Same command as Step 2. Expected: PASS, 17 tests.

- [ ] **Step 5: Checkpoint.**

---

### Task 5: The checks

The form runs these checks in this order on every edit and every tick and shows the first problem. A hint or an error blocks Add and Save; a note doesn't and shows only when nothing else does. The checks read the client's synced waypoint data through a small `Lookup` interface, so the tests don't need a game.

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormCheck.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormCheckTest.java`

**Interfaces:**
- Consumes: `FormattedTextHelper.parseKeywords`, `isValidInput`, `hasDuplicateKeywords`, `MAX_KEYWORDS`, `MAX_KEYWORD_LENGTH` (Task 1 and existing).
- Produces: `WaypointFormCheck` with `MAX_KEYWORDS_TEXT_LENGTH` (2110), enums `Kind {HINT, ERROR, NOTE}`, `Field {NONE, NAME, DISPLAY_NAME, KEYWORDS, DESCRIPTION}` and `Message` (each with `translationKey()`), record `Input(boolean add, String dimension, String list, String name, String displayName, String keywords, String description, @Nullable String savedName)`, interface `Lookup` (`listExists`, `hasWaypoint`, `listDisplayName`), record `Problem(Message message, Kind kind, Field field, List<String> arguments)` with `blocks()`, and `static @Nullable Problem firstProblem(Input, Lookup)`.

- [ ] **Step 1: Write the failing test** (this pins Review Focus items 3 and 4)

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormCheckTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Field;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Input;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Kind;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Lookup;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Message;
import _959.server_waypoint.common.client.gui.screens.WaypointFormCheck.Problem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormCheckTest {
    private static final String OVERWORLD = "minecraft:overworld";
    // The Base list, shown as "Base list", holds Home and Farm.
    private static final Lookup DATA = new Lookup() {
        @Override
        public boolean listExists(String dimension, String list) {
            return dimension.equals(OVERWORLD) && list.equals("Base");
        }

        @Override
        public boolean hasWaypoint(String dimension, String list, String name) {
            return listExists(dimension, list) && Set.of("Home", "Farm").contains(name);
        }

        @Override
        public String listDisplayName(String dimension, String list) {
            return Map.of("Base", "Base list").getOrDefault(list, list);
        }
    };

    @Test
    void aValidAddHasNoProblem() {
        assertNull(WaypointFormCheck.firstProblem(add("Base", "Camp"), DATA));
    }

    @Test
    void anEmptyDimensionAsksForOneBeforeAnythingElse() {
        Problem problem = WaypointFormCheck.firstProblem(
                new Input(true, "", "", "", "", "", "", null), DATA);

        assertProblem(problem, Message.CHOOSE_DIMENSION, Kind.HINT, Field.NONE, List.of());
        assertTrue(problem.blocks());
    }

    @Test
    void anEmptyListAsksForOneBeforeTheName() {
        Problem problem = WaypointFormCheck.firstProblem(add("", ""), DATA);

        assertProblem(problem, Message.ENTER_LIST, Kind.HINT, Field.NONE, List.of());
    }

    @Test
    void anEmptyNameAsksForOneOnBothScreens() {
        assertProblem(WaypointFormCheck.firstProblem(add("Base", ""), DATA),
                Message.ENTER_NAME, Kind.HINT, Field.NONE, List.of());
        assertProblem(WaypointFormCheck.firstProblem(edit("", "", "", "Home"), DATA),
                Message.ENTER_NAME, Kind.HINT, Field.NONE, List.of());
    }

    @Test
    void aNameTheListAlreadyHasIsAnErrorOnTheNameFieldNamingTheListByItsDisplayName() {
        Problem problem = WaypointFormCheck.firstProblem(add("Base", "Home"), DATA);

        assertProblem(problem, Message.NAME_TAKEN, Kind.ERROR, Field.NAME, List.of("Base list", "Home"));
        assertTrue(problem.blocks());
    }

    @Test
    void namesAreComparedExactlyAndCaseSensitively() {
        assertNull(WaypointFormCheck.firstProblem(add("Base", "home"), DATA));
        assertNull(WaypointFormCheck.firstProblem(add("Base", "Home "), DATA));
    }

    @Test
    void editAcceptsTheWaypointsOwnSavedNameButNotAnotherWaypointsName() {
        assertNull(WaypointFormCheck.firstProblem(edit("Home", "", "", "Home"), DATA));
        assertProblem(WaypointFormCheck.firstProblem(edit("Farm", "", "", "Home"), DATA),
                Message.NAME_TAKEN, Kind.ERROR, Field.NAME, List.of("Base list", "Farm"));
    }

    @Test
    void aDisplayNameThatIsNotValidFormattedTextIsAnErrorOnEditOnly() {
        Problem problem = WaypointFormCheck.firstProblem(edit("Home", "{not valid json}", "", "Home"), DATA);

        assertProblem(problem, Message.INVALID_DISPLAY_NAME, Kind.ERROR, Field.DISPLAY_NAME, List.of());
        assertNull(WaypointFormCheck.firstProblem(edit("Home", "{\"text\":\"Home\"}", "", "Home"), DATA));
        assertNull(WaypointFormCheck.firstProblem(new Input(true, OVERWORLD, "Base", "Camp", "{not valid json}", "", "", null), DATA));
    }

    @Test
    void aKeywordThatAppearsTwiceIgnoringCaseIsAnErrorNamingIt() {
        Problem problem = WaypointFormCheck.firstProblem(addWith("home, base, HOME"), DATA);

        assertProblem(problem, Message.DUPLICATE_KEYWORD, Kind.ERROR, Field.KEYWORDS, List.of("HOME"));
    }

    @Test
    void emptyEntriesAreNotDuplicates() {
        assertNull(WaypointFormCheck.firstProblem(addWith("home,, ,base,"), DATA));
    }

    @Test
    void moreThan32KeywordsIsAnError() {
        assertNull(WaypointFormCheck.firstProblem(addWith(keywords(32)), DATA));
        assertProblem(WaypointFormCheck.firstProblem(addWith(keywords(33)), DATA),
                Message.TOO_MANY_KEYWORDS, Kind.ERROR, Field.KEYWORDS, List.of("32"));
    }

    @Test
    void aKeywordLongerThan64CharactersIsAnError() {
        assertNull(WaypointFormCheck.firstProblem(addWith("a".repeat(64)), DATA));
        assertProblem(WaypointFormCheck.firstProblem(addWith("a".repeat(65)), DATA),
                Message.KEYWORD_TOO_LONG, Kind.ERROR, Field.KEYWORDS, List.of("64"));
    }

    @Test
    void theKeywordsFieldTakes32KeywordsOf64CharactersWithTheirSeparators() {
        assertEquals(2110, WaypointFormCheck.MAX_KEYWORDS_TEXT_LENGTH);
        String longest = String.join(", ", java.util.Collections.nCopies(32, "a".repeat(64)));
        assertEquals(WaypointFormCheck.MAX_KEYWORDS_TEXT_LENGTH, longest.length());
    }

    @Test
    void aDescriptionThatIsNotValidFormattedTextIsAnErrorOnTheDescriptionField() {
        Problem problem = WaypointFormCheck.firstProblem(
                new Input(true, OVERWORLD, "Base", "Camp", "", "", "[broken", null), DATA);

        assertProblem(problem, Message.INVALID_DESCRIPTION, Kind.ERROR, Field.DESCRIPTION, List.of());
        assertNull(WaypointFormCheck.firstProblem(
                new Input(true, OVERWORLD, "Base", "Camp", "", "", "Beds upstairs", null), DATA));
    }

    @Test
    void aListThatDoesNotExistYetIsANoteThatDoesNotBlock() {
        Problem problem = WaypointFormCheck.firstProblem(add("Outposts", "Camp"), DATA);

        assertProblem(problem, Message.NEW_LIST, Kind.NOTE, Field.NONE, List.of("Outposts"));
        assertFalse(problem.blocks());
    }

    @Test
    void theNoteShowsOnlyWhenNothingElseDoes() {
        Problem problem = WaypointFormCheck.firstProblem(add("Outposts", ""), DATA);

        assertProblem(problem, Message.ENTER_NAME, Kind.HINT, Field.NONE, List.of());
        Problem keywords = WaypointFormCheck.firstProblem(new Input(true, OVERWORLD, "Outposts", "Camp", "", "a, A", "", null), DATA);
        assertEquals(Message.DUPLICATE_KEYWORD, keywords.message());
    }

    @Test
    void editNeverNotesANewList() {
        assertNull(WaypointFormCheck.firstProblem(edit("Camp", "", "", "Camp"), DATA));
    }

    @Test
    void theFirstProblemInTheListedOrderWins() {
        // Everything wrong at once: the dimension is named first.
        Input everything = new Input(true, "", "", "", "{bad", "a, a", "[bad", null);
        assertEquals(Message.CHOOSE_DIMENSION, WaypointFormCheck.firstProblem(everything, DATA).message());
        assertEquals(Message.ENTER_LIST, WaypointFormCheck.firstProblem(
                new Input(true, OVERWORLD, "", "", "", "", "", null), DATA).message());
        // A taken name comes before a bad display name, keywords and description.
        assertEquals(Message.NAME_TAKEN, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Farm", "{bad", "a, a", "[bad", "Home"), DATA).message());
        assertEquals(Message.INVALID_DISPLAY_NAME, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "{bad", "a, a", "[bad", "Home"), DATA).message());
        assertEquals(Message.DUPLICATE_KEYWORD, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", "a, a, " + keywords(40), "[bad", "Home"), DATA).message());
        assertEquals(Message.TOO_MANY_KEYWORDS, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", keywords(40) + ", " + "b".repeat(70), "[bad", "Home"), DATA).message());
        assertEquals(Message.KEYWORD_TOO_LONG, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", "b".repeat(70), "[bad", "Home"), DATA).message());
        assertEquals(Message.INVALID_DESCRIPTION, WaypointFormCheck.firstProblem(
                new Input(false, OVERWORLD, "Base", "Home", "", "", "[bad", "Home"), DATA).message());
    }

    @Test
    void aChangeInTheSyncedDataChangesTheAnswer() {
        Set<String> names = new java.util.HashSet<>();
        Lookup changing = new Lookup() {
            @Override
            public boolean listExists(String dimension, String list) {
                return true;
            }

            @Override
            public boolean hasWaypoint(String dimension, String list, String name) {
                return names.contains(name);
            }

            @Override
            public String listDisplayName(String dimension, String list) {
                return list;
            }
        };
        Input camp = add("Base", "Camp");

        assertNull(WaypointFormCheck.firstProblem(camp, changing));
        names.add("Camp");
        assertEquals(Message.NAME_TAKEN, WaypointFormCheck.firstProblem(camp, changing).message());
        names.clear();
        assertNull(WaypointFormCheck.firstProblem(camp, changing));
    }

    @Test
    void everyMessageHasATranslationKey() {
        for (Message message : Message.values()) {
            assertNotNull(message.translationKey());
            assertTrue(message.translationKey().startsWith("waypoint."), message.name());
        }
        assertEquals("waypoint.edit.error.invalid_display_text", Message.INVALID_DISPLAY_NAME.translationKey());
    }

    private static Input add(String list, String name) {
        return new Input(true, OVERWORLD, list, name, "", "", "", null);
    }

    private static Input addWith(String keywords) {
        return new Input(true, OVERWORLD, "Base", "Camp", "", keywords, "", null);
    }

    private static Input edit(String name, String displayName, String keywords, String savedName) {
        return new Input(false, OVERWORLD, "Base", name, displayName, keywords, "", savedName);
    }

    private static String keywords(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(i == 0 ? "" : ", ").append("k").append(i);
        }
        return text.toString();
    }

    private static void assertProblem(Problem problem, Message message, Kind kind, Field field, List<String> arguments) {
        assertNotNull(problem);
        assertEquals(message, problem.message());
        assertEquals(kind, problem.kind());
        assertEquals(field, problem.field());
        assertEquals(arguments, problem.arguments());
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormCheckTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class WaypointFormCheck`.

- [ ] **Step 3: Write the implementation**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormCheck.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.text.FormattedTextHelper;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.text.FormattedTextHelper.MAX_KEYWORDS;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_KEYWORD_LENGTH;

/**
 * What the waypoint form checks before it sends anything, with the server's rules. It reports the
 * first problem in the order the messages are listed; the screens turn it into a translated message
 * and highlight its field. It holds no Minecraft classes, so it can be unit tested.
 */
final class WaypointFormCheck {
    /** The keywords field's length: 32 keywords of 64 characters, with a comma and a space between them. */
    static final int MAX_KEYWORDS_TEXT_LENGTH = MAX_KEYWORDS * MAX_KEYWORD_LENGTH + (MAX_KEYWORDS - 1) * 2;

    private WaypointFormCheck() {
    }

    /** How a problem shows: a hint or an error stops the form from being sent, a note doesn't. */
    enum Kind {
        HINT,
        ERROR,
        NOTE
    }

    /** The field an error points at, drawn with a danger outline. */
    enum Field {
        NONE,
        NAME,
        DISPLAY_NAME,
        KEYWORDS,
        DESCRIPTION
    }

    /** The translation each problem is shown with, in the order the checks run. */
    enum Message {
        CHOOSE_DIMENSION("waypoint.form.status.choose_dimension"),
        ENTER_LIST("waypoint.form.status.enter_list"),
        ENTER_NAME("waypoint.form.status.enter_name"),
        NAME_TAKEN("waypoint.form.status.name_taken"),
        INVALID_DISPLAY_NAME("waypoint.edit.error.invalid_display_text"),
        DUPLICATE_KEYWORD("waypoint.form.status.duplicate_keyword"),
        TOO_MANY_KEYWORDS("waypoint.form.status.too_many_keywords"),
        KEYWORD_TOO_LONG("waypoint.form.status.keyword_too_long"),
        INVALID_DESCRIPTION("waypoint.form.status.invalid_description"),
        NEW_LIST("waypoint.form.status.new_list");

        private final String translationKey;

        Message(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return this.translationKey;
        }
    }

    /**
     * The values in the form. {@code displayName} matters only on Edit. {@code savedName} is the name
     * the edited waypoint has now, which it may keep; it is null on Add.
     */
    record Input(
            boolean add,
            String dimension,
            String list,
            String name,
            String displayName,
            String keywords,
            String description,
            @Nullable String savedName
    ) {
    }

    /** The waypoint data a check reads, from the client's synced or integrated-server files. */
    interface Lookup {
        boolean listExists(String dimension, String list);

        /** Whether the list has a waypoint with exactly this name: the server's rule, so case counts. */
        boolean hasWaypoint(String dimension, String list, String name);

        /** The list's display name, or {@code list} itself when it has none or doesn't exist. */
        String listDisplayName(String dimension, String list);
    }

    /** One problem: its message, how it shows, the field to highlight and the message's arguments. */
    record Problem(Message message, Kind kind, Field field, List<String> arguments) {
        boolean blocks() {
            return this.kind != Kind.NOTE;
        }
    }

    /**
     * The first hint or error, or else the note, or null when the form can be sent. Checks run on every
     * edit and every tick, because the synced data can change while the form is open.
     */
    static @Nullable Problem firstProblem(Input input, Lookup data) {
        if (input.add()) {
            if (input.dimension().isEmpty()) {
                return hint(Message.CHOOSE_DIMENSION);
            }
            if (input.list().isEmpty()) {
                return hint(Message.ENTER_LIST);
            }
        }
        if (input.name().isEmpty()) {
            return hint(Message.ENTER_NAME);
        }
        if (!input.name().equals(input.savedName()) && data.hasWaypoint(input.dimension(), input.list(), input.name())) {
            return error(Message.NAME_TAKEN, Field.NAME,
                    data.listDisplayName(input.dimension(), input.list()), input.name());
        }
        if (!input.add() && !FormattedTextHelper.isValidInput(input.displayName())) {
            return error(Message.INVALID_DISPLAY_NAME, Field.DISPLAY_NAME);
        }
        List<String> keywords = FormattedTextHelper.parseKeywords(input.keywords());
        if (FormattedTextHelper.hasDuplicateKeywords(keywords)) {
            return error(Message.DUPLICATE_KEYWORD, Field.KEYWORDS, firstRepeated(keywords));
        }
        if (keywords.size() > MAX_KEYWORDS) {
            return error(Message.TOO_MANY_KEYWORDS, Field.KEYWORDS, Integer.toString(MAX_KEYWORDS));
        }
        for (String keyword : keywords) {
            if (keyword.length() > MAX_KEYWORD_LENGTH) {
                return error(Message.KEYWORD_TOO_LONG, Field.KEYWORDS, Integer.toString(MAX_KEYWORD_LENGTH));
            }
        }
        if (!FormattedTextHelper.isValidInput(input.description())) {
            return error(Message.INVALID_DESCRIPTION, Field.DESCRIPTION);
        }
        if (input.add() && !data.listExists(input.dimension(), input.list())) {
            return new Problem(Message.NEW_LIST, Kind.NOTE, Field.NONE, List.of(input.list()));
        }
        return null;
    }

    /** The first keyword that repeats an earlier one, ignoring case, as {@code hasDuplicateKeywords} does. */
    private static String firstRepeated(List<String> keywords) {
        Set<String> seen = new HashSet<>();
        for (String keyword : keywords) {
            if (!seen.add(keyword.toLowerCase(Locale.ROOT))) {
                return keyword;
            }
        }
        return "";
    }

    private static Problem hint(Message message) {
        return new Problem(message, Kind.HINT, Field.NONE, List.of());
    }

    private static Problem error(Message message, Field field, String... arguments) {
        return new Problem(message, Kind.ERROR, field, List.of(arguments));
    }
}
```

- [ ] **Step 4: Run the test**

Same command as Step 2. Expected: PASS, 20 tests.

- [ ] **Step 5: Checkpoint.**

---

### Task 6: The layout

`WaypointFormLayout` holds every number in the spec's sizes and heights tables. The screen measures its text and widgets, asks the layout where things go, and places its widgets from the answers; the layout never sees a font or a widget. It reuses `ClientConfigScreen.statusAboveButtons` for the footer rule, as the design says.

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormLayout.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormLayoutTest.java`

**Interfaces:**
- Consumes: `ClientConfigScreen.statusAboveButtons(int panelWidth, int buttonsWidth)` (existing, package-private).
- Produces: constants (`SCREEN_MARGIN`, `SECTION_GAP`, `TITLE_SUBTITLE_GAP`, `PANEL_INSET`, `MAX_CONTROL_WIDTH`, `LABEL_PADDING`, `MAX_ROW_GAP`, `MIN_ROW_GAP`, `DIVIDER_HEIGHT`, `FIELD_GAP`, `INLINE_GAP`, `AXIS_GAP`, `CONTROL_GAP`, `MIN_COORDINATE_WIDTH`, `MAX_COORDINATE_WIDTH`, `SMALL_FIELD_WIDTH`, `TOGGLE_WIDTH`, `FOOTER_BUTTON_GAP`, `STATUS_GAP`, `STATUS_LINE_GAP`); records `Columns(panelWidth, labelWidth, controlWidth)` with `labelTextWidth()`, `Item(height, isDivider)` with `Item.row(int)` and `Item.divider()`, and `Arrangement(rowGap, groupTop, groupHeight, panelX, panelY, panelWidth, panelHeight, labelX, controlX, itemTops, statusAbove, statusY, buttonsY, buttonsHeight)`; and static methods `columns`, `minimumControlWidth`, `coordinateFieldWidth`, `stretchedWidth`, `iconDropdownWidth`, `buttonsWidth`, `statusWidth`, `arrange`, `rowGap` and `contentHeight`.

- [ ] **Step 1: Write the failing test**

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormLayoutTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Arrangement;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Columns;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Item;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The widths are those of English at the default GUI scale: the widest label is 55 pixels, an axis
 * letter 6, "Yaw" 18, "Visibility" 53, and the Color row's swatch and hex field take 60 together.
 */
class WaypointFormLayoutTest {
    private static final int WIDEST_LABEL = 55;
    private static final int AXIS = 6;
    private static final int YAW_LABEL = 18;
    private static final int COLOR_GROUP = 60;
    private static final int VISIBILITY_LABEL = 53;
    private static final int MINIMUM_CONTROL = WaypointFormLayout.minimumControlWidth(AXIS, YAW_LABEL, COLOR_GROUP, VISIBILITY_LABEL);
    private static final int BUTTON_HEIGHT = 13;
    private static final int TITLE_HEIGHT = 11;
    private static final int TITLE_AND_SUBTITLE_HEIGHT = 22;
    // Add: Dimension, List, a divider, Name, Icon, Color, Position, a divider, Keywords, Description.
    private static final List<Item> ADD_ITEMS = List.of(
            Item.row(11), Item.row(11), Item.divider(), Item.row(11), Item.row(13),
            Item.row(11), Item.row(11), Item.divider(), Item.row(11), Item.row(11));
    // Edit: Name, Display name, Icon, Color, Position, a divider, Keywords, Description.
    private static final List<Item> EDIT_ITEMS = List.of(
            Item.row(11), Item.row(11), Item.row(13), Item.row(11), Item.row(11),
            Item.divider(), Item.row(11), Item.row(11));

    @Test
    void theLabelColumnIsTheWidestLabelPlusTenPixels() {
        Columns columns = WaypointFormLayout.columns(480, WIDEST_LABEL, MINIMUM_CONTROL);

        assertEquals(65, columns.labelWidth());
        assertEquals(55, columns.labelTextWidth());
    }

    @Test
    void theControlColumnTakes240PixelsWhenTheScreenAllows() {
        Columns columns = WaypointFormLayout.columns(480, WIDEST_LABEL, MINIMUM_CONTROL);

        assertEquals(240, columns.controlWidth());
        // The label column, the control column and 16 pixels of outline and padding.
        assertEquals(65 + 240 + 16, columns.panelWidth());
    }

    @Test
    void theControlColumnShrinksToFitA320PixelScreen() {
        Columns columns = WaypointFormLayout.columns(320, WIDEST_LABEL, MINIMUM_CONTROL);

        assertEquals(300, columns.panelWidth());
        assertEquals(65, columns.labelWidth());
        assertEquals(219, columns.controlWidth());
    }

    @Test
    void theLabelColumnNarrowsWhenThePositionRowNeedsTheRoom() {
        Columns columns = WaypointFormLayout.columns(320, 120, MINIMUM_CONTROL);

        assertEquals(MINIMUM_CONTROL, columns.controlWidth());
        assertEquals(300, columns.panelWidth());
        assertEquals(284 - MINIMUM_CONTROL, columns.labelWidth());
        // The widest label is 120 pixels, so it wraps inside the narrower column.
        assertTrue(columns.labelTextWidth() < 120);
    }

    @Test
    void thePanelNeverExceedsTheScreenMinusItsMargins() {
        for (int width = 320; width <= 640; width += 10) {
            for (int label : new int[] {40, 55, 90, 140}) {
                Columns columns = WaypointFormLayout.columns(width, label, MINIMUM_CONTROL);
                assertTrue(columns.panelWidth() <= width - 20, width + "/" + label);
                assertTrue(columns.controlWidth() >= MINIMUM_CONTROL, width + "/" + label);
            }
        }
    }

    @Test
    void theMinimumControlWidthIsThePositionRowWithThirtyPixelFields() {
        // Three letters, gaps and 30-pixel fields, three field gaps, then "Yaw", 5 pixels and its 26-pixel field.
        assertEquals(3 * (6 + 4 + 30) + 3 * 10 + 18 + 5 + 26, MINIMUM_CONTROL);
        assertEquals(199, MINIMUM_CONTROL);
    }

    @Test
    void aLongVisibilityLabelMakesTheColorRowTheMinimum() {
        int minimum = WaypointFormLayout.minimumControlWidth(AXIS, YAW_LABEL, COLOR_GROUP, 90);

        assertEquals(COLOR_GROUP + 10 + 90 + 5 + 48, minimum);
        assertTrue(minimum > MINIMUM_CONTROL);
    }

    @Test
    void theCoordinateFieldsShareTheRoomBetween30And48Pixels() {
        assertEquals(43, WaypointFormLayout.coordinateFieldWidth(240, AXIS, YAW_LABEL));
        assertEquals(36, WaypointFormLayout.coordinateFieldWidth(219, AXIS, YAW_LABEL));
        assertEquals(30, WaypointFormLayout.coordinateFieldWidth(MINIMUM_CONTROL, AXIS, YAW_LABEL));
        assertEquals(48, WaypointFormLayout.coordinateFieldWidth(400, AXIS, YAW_LABEL));
        assertEquals(30, WaypointFormLayout.coordinateFieldWidth(150, AXIS, YAW_LABEL));
    }

    @Test
    void theGapBeforeYawIsAtLeastTenPixelsAtEveryControlWidth() {
        for (int control = MINIMUM_CONTROL; control <= 400; control++) {
            int field = WaypointFormLayout.coordinateFieldWidth(control, AXIS, YAW_LABEL);
            int rowWithoutTheGap = 3 * (AXIS + 4 + field) + 2 * 10 + YAW_LABEL + 5 + 26;
            assertTrue(control - rowWithoutTheGap >= 10, "control " + control);
        }
    }

    @Test
    void aStretchedFieldStopsTenPixelsBeforeTheGroupAtTheRight() {
        // Name: the "Initials" label (41 pixels), 5 pixels and a 26-pixel field.
        assertEquals(240 - (41 + 5 + 26) - 10, WaypointFormLayout.stretchedWidth(240, 41 + 5 + 26));
        assertEquals(0, WaypointFormLayout.stretchedWidth(50, 100));
    }

    @Test
    void theIconDropdownFillsTheRowBetweenThePreviewAndTheRemoveButton() {
        assertEquals(240 - 11 - 13 - 8, WaypointFormLayout.iconDropdownWidth(240, 11, 13));
    }

    @Test
    void theButtonsAreSixPixelsApart() {
        assertEquals(0, WaypointFormLayout.buttonsWidth());
        assertEquals(52, WaypointFormLayout.buttonsWidth(52));
        assertEquals(52 + 6 + 52 + 6 + 60, WaypointFormLayout.buttonsWidth(52, 52, 60));
    }

    @Test
    void theRowGapIs9ForAddAndEditAtBothSizes() {
        assertEquals(9, addAt(480, 270, 9).rowGap());
        assertEquals(9, addAt(320, 240, 9).rowGap());
        assertEquals(9, editAt(480, 270, 9).rowGap());
        assertEquals(9, editAt(320, 240, 9).rowGap());
    }

    @Test
    void theGroupAt320By240IsAsTallAsTheDesignSays() {
        Arrangement add = addAt(320, 240, 9);
        // Header 11 + gap 6 + panel 181 + gap 6 + footer 13.
        assertEquals(181, add.panelHeight());
        assertEquals(11 + 6 + 181 + 6 + 13, add.groupHeight());
        Arrangement edit = editAt(320, 240, 9);
        assertEquals(155, edit.panelHeight());
        assertEquals(22 + 6 + 155 + 6 + 13, edit.groupHeight());
    }

    @Test
    void theGroupIsCenteredOnTheScreen() {
        Arrangement add = addAt(320, 240, 9);

        assertEquals((240 - 217) >> 1, add.groupTop());
        assertEquals(add.groupTop() + 11 + 6, add.panelY());
        assertEquals(10, add.panelX());
        assertEquals(add.panelX() + 8, add.labelX());
        assertEquals(add.labelX() + 65, add.controlX());
        assertEquals((480 - 321) >> 1, addAt(480, 270, 9).panelX());
    }

    @Test
    void rowsAreSpacedByTheGapAndDividersByTheGapMinusTwoOnEachSide() {
        Arrangement add = addAt(320, 240, 9);
        List<Integer> tops = add.itemTops();

        int first = add.panelY() + 8;
        assertEquals(first, tops.get(0));
        assertEquals(first + 11 + 9, tops.get(1));
        // List (11), 7 pixels, the divider (1), 7 pixels, then Name.
        assertEquals(tops.get(1) + 11 + 7, tops.get(2));
        assertEquals(tops.get(2) + 1 + 7, tops.get(3));
        assertEquals(tops.get(3) + 11 + 9, tops.get(4));
        // The last row ends 8 pixels above the panel's bottom edge.
        assertEquals(add.panelY() + add.panelHeight() - 8, tops.get(9) + 11);
    }

    @Test
    void aWrappedStatusLowersTheGap() {
        // Two lines beside the buttons leave Add at 320x240 one pixel too tall at a gap of 9.
        assertEquals(8, addAt(320, 240, 18).rowGap());
        assertEquals(7, addAt(320, 240, 27).rowGap());
        assertEquals(9, editAt(320, 240, 18).rowGap());
    }

    @Test
    void theGapNeverDropsBelowFive() {
        Arrangement add = addAt(320, 100, 27);

        assertEquals(5, add.rowGap());
    }

    @Test
    void aGroupThatCannotFitStartsAtTheTopMargin() {
        Arrangement add = addAt(320, 150, 9);

        assertEquals(5, add.rowGap());
        assertEquals(10, add.groupTop());
    }

    @Test
    void theFooterIsAsTallAsTheStatusWhenItIsTallerThanTheButtons() {
        Arrangement add = addAt(320, 240, 27);

        assertFalse(add.statusAbove());
        // The status is centered on the footer, which is 27 pixels tall; the buttons fill it.
        int footerY = add.panelY() + add.panelHeight() + 6;
        assertEquals(footerY, add.statusY());
        assertEquals(footerY, add.buttonsY());
        assertEquals(27, add.buttonsHeight());
    }

    @Test
    void theStatusIsCenteredBesideShorterButtons() {
        Arrangement add = addAt(320, 240, 9);

        int footerY = add.panelY() + add.panelHeight() + 6;
        assertEquals(footerY + ((13 - 9) >> 1), add.statusY());
        assertEquals(footerY, add.buttonsY());
        assertEquals(13, add.buttonsHeight());
    }

    @Test
    void theStatusTakesItsOwnLineAboveButtonsThatLeaveItTooLittleRoom() {
        Columns columns = WaypointFormLayout.columns(320, WIDEST_LABEL, MINIMUM_CONTROL);
        // 300 - 270 - 8 leaves 22 pixels beside them, less than the 100 the status needs.
        int buttons = 270;
        assertEquals(300, WaypointFormLayout.statusWidth(columns.panelWidth(), buttons));
        Arrangement arrangement = WaypointFormLayout.arrange(320, 240, columns, TITLE_HEIGHT, ADD_ITEMS, buttons, BUTTON_HEIGHT, 18);

        assertTrue(arrangement.statusAbove());
        int footerHeight = 18 + 4 + 13;
        int footerY = arrangement.panelY() + arrangement.panelHeight() + 6;
        assertEquals(footerY, arrangement.statusY());
        assertEquals(footerY + footerHeight - 13, arrangement.buttonsY());
        assertEquals(13, arrangement.buttonsHeight());
        assertEquals(TITLE_HEIGHT + 6 + arrangement.panelHeight() + 6 + footerHeight, arrangement.groupHeight());
    }

    @Test
    void theStatusWrapsToTheRoomBesideTheButtonsWhenThereIsEnough() {
        assertEquals(300 - 165 - 8, WaypointFormLayout.statusWidth(300, 165));
        assertEquals(300, WaypointFormLayout.statusWidth(300, 270));
    }

    @Test
    void anEmptyStatusAddsNothingToTheFooter() {
        Arrangement withoutStatus = addAt(320, 240, 0);

        assertEquals(217, withoutStatus.groupHeight());
        assertEquals(13, withoutStatus.buttonsHeight());
    }

    private static Arrangement addAt(int width, int height, int statusHeight) {
        Columns columns = WaypointFormLayout.columns(width, WIDEST_LABEL, MINIMUM_CONTROL);
        return WaypointFormLayout.arrange(width, height, columns, TITLE_HEIGHT, ADD_ITEMS,
                WaypointFormLayout.buttonsWidth(52, 52), BUTTON_HEIGHT, statusHeight);
    }

    private static Arrangement editAt(int width, int height, int statusHeight) {
        Columns columns = WaypointFormLayout.columns(width, WIDEST_LABEL, MINIMUM_CONTROL);
        return WaypointFormLayout.arrange(width, height, columns, TITLE_AND_SUBTITLE_HEIGHT, EDIT_ITEMS,
                WaypointFormLayout.buttonsWidth(52, 52, 52), BUTTON_HEIGHT, statusHeight);
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormLayoutTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: class WaypointFormLayout`.

- [ ] **Step 3: Write the implementation**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointFormLayout.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the waypoint add and edit forms, worked out from measured sizes: the widths of the
 * columns, the gap between rows, and where the header, the rows and the footer go. It knows nothing
 * about widgets or fonts, so the screens measure their text, ask it where things go and place their
 * widgets from the answers.
 */
final class WaypointFormLayout {
    static final int SCREEN_MARGIN = 10;
    /** Between the header and the panel, and between the panel and the footer. */
    static final int SECTION_GAP = 6;
    static final int TITLE_SUBTITLE_GAP = 2;
    /** The panel's 1-pixel outline plus 7 pixels of padding, on each side. */
    static final int PANEL_INSET = 8;
    static final int MAX_CONTROL_WIDTH = 240;
    /** Between the widest label and the control column. */
    static final int LABEL_PADDING = 10;
    static final int MAX_ROW_GAP = 9;
    static final int MIN_ROW_GAP = 5;
    static final int DIVIDER_HEIGHT = 1;
    /** Between the fields of one row. */
    static final int FIELD_GAP = 10;
    /** From an inline label to its field. */
    static final int INLINE_GAP = 5;
    /** From an axis letter to its field. */
    static final int AXIS_GAP = 4;
    /** From the swatch to the hex field, and between the icon preview, dropdown and remove button. */
    static final int CONTROL_GAP = 4;
    static final int MIN_COORDINATE_WIDTH = 30;
    static final int MAX_COORDINATE_WIDTH = 48;
    /** The Initials and Yaw fields. */
    static final int SMALL_FIELD_WIDTH = 26;
    static final int TOGGLE_WIDTH = 48;
    static final int FOOTER_BUTTON_GAP = 6;
    /** Between the status message and the buttons beside it. */
    static final int STATUS_GAP = 8;
    /** Between the status message and the buttons below it. */
    static final int STATUS_LINE_GAP = 4;

    private WaypointFormLayout() {
    }

    /** The widths of the panel and its two columns. */
    record Columns(int panelWidth, int labelWidth, int controlWidth) {
        /** The width a label can take before it wraps onto another line. */
        int labelTextWidth() {
            return Math.max(1, this.labelWidth - LABEL_PADDING);
        }
    }

    /** A row or a divider of the panel. */
    record Item(int height, boolean isDivider) {
        static Item row(int height) {
            return new Item(height, false);
        }

        static Item divider() {
            return new Item(DIVIDER_HEIGHT, true);
        }
    }

    /** Where the parts go, with y positions on the screen. */
    record Arrangement(
            int rowGap,
            int groupTop,
            int groupHeight,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int labelX,
            int controlX,
            List<Integer> itemTops,
            boolean statusAbove,
            int statusY,
            int buttonsY,
            int buttonsHeight
    ) {
    }

    /**
     * The panel and column widths. The control column takes 240 pixels when the screen allows, then
     * shrinks, but never below {@code minimumControlWidth}; after that the label column narrows and
     * its labels wrap.
     */
    static Columns columns(int screenWidth, int widestLabelWidth, int minimumControlWidth) {
        int available = Math.max(0, screenWidth - 2 * SCREEN_MARGIN - 2 * PANEL_INSET);
        int label = widestLabelWidth + LABEL_PADDING;
        int control = MAX_CONTROL_WIDTH;
        if (label + control > available) {
            control = Math.min(available, Math.max(minimumControlWidth, available - label));
            label = Math.min(label, available - control);
        }
        return new Columns(label + control + 2 * PANEL_INSET, label, control);
    }

    /**
     * The control column's smallest width: the Position row with the smallest coordinate fields and
     * at least a field gap before Yaw, or the Color row, whichever needs more.
     */
    static int minimumControlWidth(int axisLetterWidth, int yawLabelWidth, int colorGroupWidth, int visibilityLabelWidth) {
        int position = 3 * (axisLetterWidth + AXIS_GAP + MIN_COORDINATE_WIDTH) + 3 * FIELD_GAP
                + yawLabelWidth + INLINE_GAP + SMALL_FIELD_WIDTH;
        int color = colorGroupWidth + FIELD_GAP + visibilityLabelWidth + INLINE_GAP + TOGGLE_WIDTH;
        return Math.max(position, color);
    }

    /**
     * The width of each coordinate field: an equal share of the Position row's free space, between
     * 30 and 48 pixels. The gap before Yaw takes what's left.
     */
    static int coordinateFieldWidth(int controlWidth, int axisLetterWidth, int yawLabelWidth) {
        int fixed = 3 * (axisLetterWidth + AXIS_GAP) + 3 * FIELD_GAP + yawLabelWidth + INLINE_GAP + SMALL_FIELD_WIDTH;
        return Math.max(MIN_COORDINATE_WIDTH, Math.min(MAX_COORDINATE_WIDTH, Math.floorDiv(controlWidth - fixed, 3)));
    }

    /** The width of a field that fills its row up to a field gap before a group of fixed width. */
    static int stretchedWidth(int controlWidth, int rightGroupWidth) {
        return Math.max(0, controlWidth - rightGroupWidth - FIELD_GAP);
    }

    /** The icon dropdown's width: the rest of its row after the preview and the remove button. */
    static int iconDropdownWidth(int controlWidth, int previewWidth, int removeButtonWidth) {
        return Math.max(0, controlWidth - previewWidth - removeButtonWidth - 2 * CONTROL_GAP);
    }

    /** The width of the footer's buttons together, given each button's outer width. */
    static int buttonsWidth(int... buttonWidths) {
        int width = 0;
        for (int buttonWidth : buttonWidths) {
            width += buttonWidth;
        }
        return width + FOOTER_BUTTON_GAP * Math.max(0, buttonWidths.length - 1);
    }

    /** The width the status message wraps to: beside the buttons, or a full line above them. */
    static int statusWidth(int panelWidth, int buttonsWidth) {
        return ClientConfigScreen.statusAboveButtons(panelWidth, buttonsWidth)
                ? panelWidth
                : panelWidth - buttonsWidth - STATUS_GAP;
    }

    /**
     * Places the header, panel and footer as one group centered on the screen. The gap between rows is
     * the largest from 9 down to 5 pixels at which the group fits inside the screen margins; if none
     * does, the group starts at the top margin.
     *
     * @param headerHeight the title, plus the subtitle and the gap under the title when there is one
     * @param items the panel's rows and dividers, top to bottom, with each row's height
     * @param buttonsWidth the footer's buttons together, see {@link #buttonsWidth}
     * @param buttonHeight the footer buttons' outer height
     * @param statusHeight the height of the status message wrapped to {@link #statusWidth}, 0 for none
     */
    static Arrangement arrange(
            int screenWidth,
            int screenHeight,
            Columns columns,
            int headerHeight,
            List<Item> items,
            int buttonsWidth,
            int buttonHeight,
            int statusHeight
    ) {
        boolean statusAbove = ClientConfigScreen.statusAboveButtons(columns.panelWidth(), buttonsWidth);
        int footerHeight = statusAbove && statusHeight > 0
                ? statusHeight + STATUS_LINE_GAP + buttonHeight
                : Math.max(buttonHeight, statusHeight);
        int fixedHeight = headerHeight + 2 * SECTION_GAP + 2 * PANEL_INSET + footerHeight;
        int rowGap = rowGap(items, screenHeight - 2 * SCREEN_MARGIN - fixedHeight);
        int panelHeight = 2 * PANEL_INSET + contentHeight(items, rowGap);
        int groupHeight = headerHeight + SECTION_GAP + panelHeight + SECTION_GAP + footerHeight;
        int groupTop = Math.max(SCREEN_MARGIN, (screenHeight - groupHeight) >> 1);
        int panelX = (screenWidth - columns.panelWidth()) >> 1;
        int panelY = groupTop + headerHeight + SECTION_GAP;

        List<Integer> itemTops = new ArrayList<>(items.size());
        int y = panelY + PANEL_INSET;
        for (int i = 0; i < items.size(); i++) {
            itemTops.add(y);
            y += items.get(i).height();
            if (i + 1 < items.size()) {
                y += gapBetween(items.get(i), items.get(i + 1), rowGap);
            }
        }

        int footerY = panelY + panelHeight + SECTION_GAP;
        int buttonsHeight = statusAbove ? buttonHeight : footerHeight;
        int statusY = statusAbove ? footerY : footerY + ((footerHeight - statusHeight) >> 1);
        return new Arrangement(
                rowGap,
                groupTop,
                groupHeight,
                panelX,
                panelY,
                columns.panelWidth(),
                panelHeight,
                panelX + PANEL_INSET,
                panelX + PANEL_INSET + columns.labelWidth(),
                List.copyOf(itemTops),
                statusAbove,
                statusY,
                footerY + footerHeight - buttonsHeight,
                buttonsHeight
        );
    }

    /** The largest row gap from 9 down to 5 at which the items fit {@code availableHeight}, else 5. */
    static int rowGap(List<Item> items, int availableHeight) {
        for (int gap = MAX_ROW_GAP; gap > MIN_ROW_GAP; gap--) {
            if (contentHeight(items, gap) <= availableHeight) {
                return gap;
            }
        }
        return MIN_ROW_GAP;
    }

    /** The items' heights and the gaps between them. */
    static int contentHeight(List<Item> items, int rowGap) {
        int height = 0;
        for (int i = 0; i < items.size(); i++) {
            height += items.get(i).height();
            if (i + 1 < items.size()) {
                height += gapBetween(items.get(i), items.get(i + 1), rowGap);
            }
        }
        return height;
    }

    /** Around a divider the gap is the row gap minus 2, above it and below it. */
    private static int gapBetween(Item above, Item below, int rowGap) {
        return above.isDivider() || below.isDivider() ? Math.max(0, rowGap - 2) : rowGap;
    }
}
```

- [ ] **Step 4: Run the test**

Same command as Step 2. Expected: PASS, 24 tests. The spec's numbers appear in them: a group of 217 (Add) and 202 (Edit) pixels at 320×240, a gap of 9 that drops to 8 and 7 as the status wraps, and never below 5.

- [ ] **Step 5: Checkpoint.**

---
### Task 7: Translations in six locales

The form uses new `waypoint.form.*` keys, a `waypoint.edit.screen.location` subtitle and `waypoint.save.button`; it retires the old `Label: ` keys, the display-name toggle's keys, `waypoint.update.button` and the two info keys; and the 14 `waypoint.edit.error.*` messages, which showed only in `en_us` and `zh_cn`, now appear in the footer, so the other four locales get them (with the new `upload_busy`). `waypoint.add.screen.title` and `waypoint.icon.clear` keep their keys with new text.

The files are edited line by line, not through a JSON library, because `en_us` and `zh_cn` group their keys with blank lines that a round trip would lose. The strings below are the review artifact: `en_us` and `zh_cn` come from the spec, and the other four are machine drafts that keep each locale's existing terms (`waypoint` in Spanish, 路徑點 and 清單 in Traditional Chinese, `נקודת ציון` in Hebrew, and so on). `zh_tw` keeps its file's existing spelling 顔色 in the Color strings, for consistency within the file; a reviewer may normalize the whole file.

**Files:**
- Modify: `mods/src/main/resources/assets/server_waypoint/lang/{en_us,es_es,he_il,zh_cn,zh_hk,zh_tw}.json`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormTranslationTest.java`

**Interfaces:**
- Consumes: `WaypointFormCheck.Message.translationKey()` (Task 5) and `EditResultStatus` from `common`.
- Produces: every translation key the screens in Task 11 use.

- [ ] **Step 1: Write the failing test**

The test checks that every key the screens use exists in all six locales with the same arguments as `en_us`, that every check message and every edit-result status has a translation, that the retired keys are gone, and that labels have no trailing colon.

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormTranslationTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.core.edit.EditResultStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointFormTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    // As Minecraft reads a translation: %% is a literal percent sign, %s takes the next argument and
    // %<n>$s takes argument n.
    private static final Pattern FORMAT_SPECIFIER = Pattern.compile("%(?:(\\d+)\\$)?([s%])");
    private static final List<String> LABEL_KEYS = List.of(
            "waypoint.form.dimension",
            "waypoint.form.list",
            "waypoint.form.name",
            "waypoint.form.initials",
            "waypoint.form.display_name",
            "waypoint.form.color",
            "waypoint.form.visibility",
            "waypoint.form.position",
            "waypoint.form.yaw",
            "waypoint.form.keywords",
            "waypoint.form.description",
            "waypoint.form.optional",
            "waypoint.form.no_icon",
            "waypoint.form.empty_display_name"
    );
    private static final List<String> TOOLTIP_KEYS = List.of(
            "waypoint.form.dimension.tooltip",
            "waypoint.form.list.tooltip",
            "waypoint.form.name.tooltip",
            "waypoint.form.initials.tooltip",
            "waypoint.form.display_name.tooltip",
            "waypoint.form.icon.tooltip",
            "waypoint.form.color.tooltip",
            "waypoint.form.visibility.tooltip",
            "waypoint.form.position.tooltip",
            "waypoint.form.yaw.tooltip",
            "waypoint.form.keywords.tooltip",
            "waypoint.form.description.tooltip"
    );
    private static final List<String> STATUS_KEYS = List.of(
            "waypoint.form.status.adding",
            "waypoint.form.status.saving",
            "waypoint.form.status.add_timeout",
            "waypoint.form.status.send_failed"
    );
    // The keys the screens use that other screens define too.
    private static final List<String> SHARED_KEYS = List.of(
            "waypoint.add.screen.title",
            "waypoint.edit.screen.title",
            "waypoint.edit.screen.location",
            "waypoint.edit.screen.previous_color.hover",
            "waypoint.edit.screen.current_color.hover",
            "waypoint.add.button",
            "waypoint.save.button",
            "waypoint.reset.button",
            "waypoint.global",
            "waypoint.local",
            "waypoint.icon.label",
            "waypoint.icon.clear",
            "server_waypoint.cancel.button"
    );
    private static final List<String> RETIRED_KEYS = List.of(
            "waypoint.edit.screen.name.entry",
            "waypoint.edit.screen.identifier.entry",
            "waypoint.edit.screen.display_name.entry",
            "waypoint.edit.screen.initials.entry",
            "waypoint.edit.screen.color",
            "waypoint.edit.screen.coords_yaw",
            "waypoint.edit.screen.visibility",
            "waypoint.display_name.clear.button",
            "waypoint.display_name.keep.button",
            "waypoint.update.button",
            "waypoint.dimension.info",
            "waypoint.list_name.info"
    );

    @Test
    void allSixLocalesDefineTheFormKeysWithTheArgumentsOfEnglish() throws Exception {
        List<String> keys = new ArrayList<>();
        keys.addAll(LABEL_KEYS);
        keys.addAll(TOOLTIP_KEYS);
        keys.addAll(STATUS_KEYS);
        keys.addAll(SHARED_KEYS);
        assertSameKeysAndArguments(keys);
    }

    @Test
    void everyCheckMessageIsTranslatedInEveryLocale() throws Exception {
        List<String> keys = new ArrayList<>();
        for (WaypointFormCheck.Message message : WaypointFormCheck.Message.values()) {
            keys.add(message.translationKey());
        }
        assertSameKeysAndArguments(keys);
    }

    @Test
    void everyEditResultHasAMessageInEveryLocale() throws Exception {
        List<String> keys = new ArrayList<>();
        for (EditResultStatus status : EditResultStatus.values()) {
            if (status != EditResultStatus.SUCCESS) {
                keys.add("waypoint.edit.error." + status.name().toLowerCase(Locale.ROOT));
            }
        }
        keys.add("waypoint.edit.error.response_timeout");
        assertTrue(keys.contains("waypoint.edit.error.upload_busy"));
        assertSameKeysAndArguments(keys);
    }

    @Test
    void theMessagesTakeTheArgumentsTheScreensPass() throws Exception {
        JsonObject english = read("en_us");

        assertEquals(List.of(1, 2), arguments(english.get("waypoint.form.status.name_taken").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.duplicate_keyword").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.too_many_keywords").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.keyword_too_long").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.status.new_list").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.form.visibility.tooltip").getAsString()));
        assertEquals(List.of(1, 2), arguments(english.get("waypoint.edit.screen.location").getAsString()));
        assertEquals(List.of(1), arguments(english.get("waypoint.edit.screen.title").getAsString()));
    }

    @Test
    void retiredKeysAreRemovedFromEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : RETIRED_KEYS) {
                assertFalse(translated.has(key), locale + ": " + key);
            }
        }
    }

    @Test
    void labelsAreWordsWithoutTrailingColons() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : LABEL_KEYS) {
                String label = translated.get(key).getAsString();
                assertFalse(label.trim().endsWith(":") || label.trim().endsWith("："), locale + ": " + key);
            }
        }
    }

    private void assertSameKeysAndArguments(List<String> keys) throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : keys) {
                assertTrue(english.has(key), "en_us: " + key);
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(arguments(english.get(key).getAsString()),
                        arguments(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    private JsonObject read(String locale) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/server_waypoint/lang/" + locale + ".json");
        assertNotNull(stream, locale);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /**
     * The positions of the arguments a translation shows, sorted, so {@code %s} and {@code %1$s}
     * compare equal. A literal {@code %%} shows none.
     */
    private static List<Integer> arguments(String value) {
        List<Integer> arguments = new ArrayList<>();
        int next = 1;
        Matcher matcher = FORMAT_SPECIFIER.matcher(value);
        while (matcher.find()) {
            if (matcher.group(2).equals("%")) {
                continue;
            }
            arguments.add(matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : next++);
        }
        arguments.sort(null);
        return arguments;
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormTranslationTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL, 6 of 6 tests, on missing keys such as `en_us: waypoint.form.dimension`.

- [ ] **Step 3: Save the script that applies the strings**

Save this as `apply_translations.py` in your scratchpad directory. It reads the language directory from its first argument, retires the old keys, replaces the text of the kept keys, renames `waypoint.update.button` to `waypoint.save.button` in place, adds the new keys after the swatch hover texts and adds the edit errors the four minor locales lack. It refuses to write a file with a duplicate key or invalid JSON.

```python
#!/usr/bin/env python3
"""Applies the waypoint form's translation changes to the six language files, line by line so the
files keep their layout: retires the old form keys, adds the new ones and the edit errors that only
en_us and zh_cn had. Usage: apply_translations.py <lang directory>."""
import json
import re
import sys
from pathlib import Path

LOCALES = ["en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw"]

RETIRED = [
    "waypoint.edit.screen.name.entry",
    "waypoint.edit.screen.identifier.entry",
    "waypoint.edit.screen.display_name.entry",
    "waypoint.edit.screen.initials.entry",
    "waypoint.edit.screen.color",
    "waypoint.edit.screen.coords_yaw",
    "waypoint.edit.screen.visibility",
    "waypoint.display_name.clear.button",
    "waypoint.display_name.keep.button",
    "waypoint.dimension.info",
    "waypoint.list_name.info",
]

# Kept keys with new text.
REPLACED = {
    "waypoint.add.screen.title": {
        "en_us": "Add waypoint",
        "es_es": "Añadir waypoint",
        "he_il": "הוסף נקודת ציון",
        "zh_cn": "添加路径点",
        "zh_hk": "新增路徑點",
        "zh_tw": "新增路徑點",
    },
    "waypoint.icon.clear": {
        "en_us": "Remove icon",
        "es_es": "Quitar icono",
        "he_il": "הסר סמל",
        "zh_cn": "移除图标",
        "zh_hk": "移除圖示",
        "zh_tw": "移除圖示",
    },
}

# "waypoint.update.button" becomes "waypoint.save.button" in the same place.
SAVE = {
    "en_us": "Save",
    "es_es": "Guardar",
    "he_il": "שמור",
    "zh_cn": "保存",
    "zh_hk": "儲存",
    "zh_tw": "儲存",
}

LOCATION = {
    "en_us": "In %1$s · %2$s",
    "es_es": "En %1$s · %2$s",
    "he_il": "ב-%1$s · %2$s",
    "zh_cn": "位于 %1$s · %2$s",
    "zh_hk": "位於 %1$s · %2$s",
    "zh_tw": "位於 %1$s · %2$s",
}

# The new form keys, in the order they are added: label, then per-locale text.
FORM = [
    ("waypoint.form.dimension", {
        "en_us": "Dimension", "es_es": "Dimensión", "he_il": "מימד",
        "zh_cn": "维度", "zh_hk": "維度", "zh_tw": "維度"}),
    ("waypoint.form.list", {
        "en_us": "List", "es_es": "Lista", "he_il": "רשימה",
        "zh_cn": "列表", "zh_hk": "清單", "zh_tw": "清單"}),
    ("waypoint.form.name", {
        "en_us": "Name", "es_es": "Nombre", "he_il": "שם",
        "zh_cn": "名称", "zh_hk": "名稱", "zh_tw": "名稱"}),
    ("waypoint.form.initials", {
        "en_us": "Initials", "es_es": "Iniciales", "he_il": "ראשי תיבות",
        "zh_cn": "缩写", "zh_hk": "縮寫", "zh_tw": "縮寫"}),
    ("waypoint.form.display_name", {
        "en_us": "Display name", "es_es": "Nombre para mostrar", "he_il": "שם תצוגה",
        "zh_cn": "显示名称", "zh_hk": "顯示名稱", "zh_tw": "顯示名稱"}),
    ("waypoint.form.color", {
        "en_us": "Color", "es_es": "Color", "he_il": "צבע",
        "zh_cn": "颜色", "zh_hk": "顏色", "zh_tw": "顔色"}),
    ("waypoint.form.visibility", {
        "en_us": "Visibility", "es_es": "Visibilidad", "he_il": "תצוגה",
        "zh_cn": "可见范围", "zh_hk": "可見範圍", "zh_tw": "可見範圍"}),
    ("waypoint.form.position", {
        "en_us": "Position", "es_es": "Posición", "he_il": "מיקום",
        "zh_cn": "位置", "zh_hk": "位置", "zh_tw": "位置"}),
    ("waypoint.form.yaw", {
        "en_us": "Yaw", "es_es": "Yaw", "he_il": "זווית",
        "zh_cn": "偏航角", "zh_hk": "偏航角", "zh_tw": "偏航角"}),
    ("waypoint.form.keywords", {
        "en_us": "Keywords", "es_es": "Palabras clave", "he_il": "מילות מפתח",
        "zh_cn": "关键词", "zh_hk": "關鍵字", "zh_tw": "關鍵字"}),
    ("waypoint.form.description", {
        "en_us": "Description", "es_es": "Descripción", "he_il": "תיאור",
        "zh_cn": "描述", "zh_hk": "描述", "zh_tw": "描述"}),
    ("waypoint.form.optional", {
        "en_us": "Optional", "es_es": "Opcional", "he_il": "אופציונלי",
        "zh_cn": "可选", "zh_hk": "可選", "zh_tw": "可選"}),
    ("waypoint.form.no_icon", {
        "en_us": "None — shows the initials", "es_es": "Ninguno — muestra las iniciales",
        "he_il": "ללא — מציג את ראשי התיבות",
        "zh_cn": "无（显示缩写）", "zh_hk": "無（顯示縮寫）", "zh_tw": "無（顯示縮寫）"}),
    ("waypoint.form.empty_display_name", {
        "en_us": "Empty: the marker shows no name",
        "es_es": "Vacío: el marcador no muestra ningún nombre",
        "he_il": "ריק: הסמן לא מציג שם",
        "zh_cn": "空：标记不显示名称", "zh_hk": "空：標記不顯示名稱", "zh_tw": "空：標記不顯示名稱"}),
    ("waypoint.form.dimension.tooltip", {
        "en_us": "The dimension the waypoint is saved in.",
        "es_es": "La dimensión en la que se guarda el waypoint.",
        "he_il": "המימד שבו נשמרת נקודת הציון.",
        "zh_cn": "路径点所在的维度。", "zh_hk": "路徑點所在的維度。", "zh_tw": "路徑點所在的維度。"}),
    ("waypoint.form.list.tooltip", {
        "en_us": "The list to add it to. A new name creates the list.",
        "es_es": "La lista a la que se añade. Un nombre nuevo crea la lista.",
        "he_il": "הרשימה שאליה יש להוסיף. שם חדש יוצר את הרשימה.",
        "zh_cn": "要添加到的列表。输入新名称会创建该列表。",
        "zh_hk": "要新增到的清單。輸入新名稱會建立該清單。",
        "zh_tw": "要新增到的清單。輸入新名稱會建立該清單。"}),
    ("waypoint.form.name.tooltip", {
        "en_us": "Identifies the waypoint in its list and in commands. Must be unique in the list.",
        "es_es": "Identifica el waypoint en su lista y en los comandos. Debe ser único en la lista.",
        "he_il": "מזהה את נקודת הציון ברשימה שלה ובפקודות. חייב להיות ייחודי ברשימה.",
        "zh_cn": "在列表和命令中用于识别路径点，在列表中必须唯一。",
        "zh_hk": "在清單和指令中用於識別路徑點，在清單中必須唯一。",
        "zh_tw": "在清單和指令中用於識別路徑點，在清單中必須唯一。"}),
    ("waypoint.form.initials.tooltip", {
        "en_us": "Shown on the marker when it has no icon. Filled in from the name until you change them.",
        "es_es": "Se muestran en el marcador cuando no tiene icono. Se rellenan a partir del nombre hasta que las cambies.",
        "he_il": "מוצגים על הסמן כשאין לו סמל. מתמלאים מהשם עד שתשנה אותם.",
        "zh_cn": "没有图标时显示在标记上。在你修改之前会根据名称自动填写。",
        "zh_hk": "沒有圖示時顯示在標記上。在你修改之前會根據名稱自動填寫。",
        "zh_tw": "沒有圖示時顯示在標記上。在你修改之前會根據名稱自動填寫。"}),
    ("waypoint.form.display_name.tooltip", {
        "en_us": "Formatted text shown instead of the name. Leave empty to show the name.",
        "es_es": "Texto con formato que se muestra en lugar del nombre. Déjalo vacío para mostrar el nombre.",
        "he_il": "טקסט מעוצב שמוצג במקום השם. השאר ריק כדי להציג את השם.",
        "zh_cn": "代替名称显示的格式化文本。留空则显示名称。",
        "zh_hk": "代替名稱顯示的格式化文字。留空則顯示名稱。",
        "zh_tw": "代替名稱顯示的格式化文字。留空則顯示名稱。"}),
    ("waypoint.form.icon.tooltip", {
        "en_us": "An item or VoxelMap icon for the marker. Remove it to show the initials.",
        "es_es": "Un icono de objeto o de VoxelMap para el marcador. Quítalo para mostrar las iniciales.",
        "he_il": "סמל פריט או סמל VoxelMap לסמן. הסר אותו כדי להציג את ראשי התיבות.",
        "zh_cn": "标记使用的物品或 VoxelMap 图标。移除后显示缩写。",
        "zh_hk": "標記使用的物品或 VoxelMap 圖示。移除後顯示縮寫。",
        "zh_tw": "標記使用的物品或 VoxelMap 圖示。移除後顯示縮寫。"}),
    ("waypoint.form.color.tooltip", {
        "en_us": "The marker's color. Click the swatch to pick one.",
        "es_es": "El color del marcador. Haz clic en la muestra para elegir uno.",
        "he_il": "צבע הסמן. לחץ על הריבוע כדי לבחור צבע.",
        "zh_cn": "标记的颜色。点击色块进行选择。",
        "zh_hk": "標記的顏色。點擊色塊進行選擇。",
        "zh_tw": "標記的顔色。點擊色塊進行選擇。"}),
    ("waypoint.form.visibility.tooltip", {
        "en_us": "Global waypoints are always drawn. Local ones only within your local waypoint range (%s chunks).",
        "es_es": "Los waypoints globales se dibujan siempre. Los locales solo dentro de tu alcance de waypoints locales (%s chunks).",
        "he_il": "נקודות ציון גלובליות מצוירות תמיד. מקומיות רק בטווח נקודות הציון המקומיות שלך (%s צ'אנקים).",
        "zh_cn": "全局路径点始终绘制。局部路径点只在你的局部路径点范围（%s 区块）内绘制。",
        "zh_hk": "全域路徑點始終繪製。區域路徑點只在你的區域路徑點範圍（%s 區塊）內繪製。",
        "zh_tw": "全域路徑點始終繪製。局部路徑點只在你的局部路徑點範圍（%s 區塊）內繪製。"}),
    ("waypoint.form.position.tooltip", {
        "en_us": "Block coordinates. ~ is relative to you. ^ is relative to where you look (right, up, forward).",
        "es_es": "Coordenadas de bloque. ~ es relativo a ti. ^ es relativo a hacia dónde miras (derecha, arriba, adelante).",
        "he_il": "קואורדינטות בלוק. ~ ביחס אליך. ^ ביחס למקום שבו אתה מסתכל (ימין, למעלה, קדימה).",
        "zh_cn": "方块坐标。~ 相对于你的位置，^ 相对于你的视线方向（右、上、前）。",
        "zh_hk": "方塊座標。~ 相對於你的位置，^ 相對於你的視線方向（右、上、前）。",
        "zh_tw": "方塊坐標。~ 相對於你的位置，^ 相對於你的視線方向（右、上、前）。"}),
    ("waypoint.form.yaw.tooltip", {
        "en_us": "The direction you face after teleporting, in degrees.",
        "es_es": "La dirección hacia la que miras tras teletransportarte, en grados.",
        "he_il": "הכיוון שבו אתה פונה לאחר טלפורט, במעלות.",
        "zh_cn": "传送后面朝的方向，单位为度。",
        "zh_hk": "傳送後面朝的方向，單位為度。",
        "zh_tw": "傳送後面朝的方向，單位為度。"}),
    ("waypoint.form.keywords.tooltip", {
        "en_us": "Comma-separated words that searches also match.",
        "es_es": "Palabras separadas por comas que las búsquedas también reconocen.",
        "he_il": "מילים מופרדות בפסיקים שגם החיפוש מתאים להן.",
        "zh_cn": "用逗号分隔的词语，搜索时也会匹配。",
        "zh_hk": "用逗號分隔的詞語，搜尋時也會匹配。",
        "zh_tw": "用逗號分隔的詞語，搜尋時也會匹配。"}),
    ("waypoint.form.description.tooltip", {
        "en_us": "Formatted text shown in the waypoint's details.",
        "es_es": "Texto con formato que se muestra en los detalles del waypoint.",
        "he_il": "טקסט מעוצב שמוצג בפרטי נקודת הציון.",
        "zh_cn": "显示在路径点详情中的格式化文本。",
        "zh_hk": "顯示在路徑點詳情中的格式化文字。",
        "zh_tw": "顯示在路徑點詳情中的格式化文字。"}),
    ("waypoint.form.status.choose_dimension", {
        "en_us": "Choose a dimension.", "es_es": "Elige una dimensión.", "he_il": "בחר מימד.",
        "zh_cn": "请选择维度。", "zh_hk": "請選擇維度。", "zh_tw": "請選擇維度。"}),
    ("waypoint.form.status.enter_list", {
        "en_us": "Enter a list name.", "es_es": "Introduce un nombre de lista.", "he_il": "הזן שם רשימה.",
        "zh_cn": "请输入列表名称。", "zh_hk": "請輸入清單名稱。", "zh_tw": "請輸入清單名稱。"}),
    ("waypoint.form.status.enter_name", {
        "en_us": "Enter a name.", "es_es": "Introduce un nombre.", "he_il": "הזן שם.",
        "zh_cn": "请输入名称。", "zh_hk": "請輸入名稱。", "zh_tw": "請輸入名稱。"}),
    ("waypoint.form.status.name_taken", {
        "en_us": "%1$s already has a waypoint named \"%2$s\".",
        "es_es": "%1$s ya tiene un waypoint llamado \"%2$s\".",
        "he_il": "ב-%1$s כבר יש נקודת ציון בשם \"%2$s\".",
        "zh_cn": "%1$s 中已有名为“%2$s”的路径点。",
        "zh_hk": "%1$s 中已有名為「%2$s」的路徑點。",
        "zh_tw": "%1$s 中已有名為「%2$s」的路徑點。"}),
    ("waypoint.form.status.duplicate_keyword", {
        "en_us": "\"%s\" appears twice in the keywords.",
        "es_es": "\"%s\" aparece dos veces en las palabras clave.",
        "he_il": "\"%s\" מופיע פעמיים במילות המפתח.",
        "zh_cn": "关键词“%s”重复。", "zh_hk": "關鍵字「%s」重複。", "zh_tw": "關鍵字「%s」重複。"}),
    ("waypoint.form.status.too_many_keywords", {
        "en_us": "At most %s keywords.", "es_es": "Como máximo %s palabras clave.",
        "he_il": "מקסימום %s מילות מפתח.",
        "zh_cn": "最多 %s 个关键词。", "zh_hk": "最多 %s 個關鍵字。", "zh_tw": "最多 %s 個關鍵字。"}),
    ("waypoint.form.status.keyword_too_long", {
        "en_us": "A keyword can have at most %s characters.",
        "es_es": "Una palabra clave puede tener como máximo %s caracteres.",
        "he_il": "מילת מפתח יכולה להכיל לכל היותר %s תווים.",
        "zh_cn": "每个关键词最多 %s 个字符。", "zh_hk": "每個關鍵字最多 %s 個字元。", "zh_tw": "每個關鍵字最多 %s 個字元。"}),
    ("waypoint.form.status.invalid_description", {
        "en_us": "The description is not valid formatted text.",
        "es_es": "La descripción no es un texto con formato válido.",
        "he_il": "התיאור אינו טקסט מעוצב חוקי.",
        "zh_cn": "描述不是有效的格式化文本。", "zh_hk": "描述不是有效的格式化文字。", "zh_tw": "描述不是有效的格式化文字。"}),
    ("waypoint.form.status.new_list", {
        "en_us": "Adding creates the list \"%s\".",
        "es_es": "Al añadir se creará la lista \"%s\".",
        "he_il": "ההוספה תיצור את הרשימה \"%s\".",
        "zh_cn": "添加时将创建列表“%s”。", "zh_hk": "新增時將建立清單「%s」。", "zh_tw": "新增時將建立清單「%s」。"}),
    ("waypoint.form.status.adding", {
        "en_us": "Adding…", "es_es": "Añadiendo…", "he_il": "מוסיף…",
        "zh_cn": "正在添加…", "zh_hk": "正在新增…", "zh_tw": "正在新增…"}),
    ("waypoint.form.status.saving", {
        "en_us": "Saving…", "es_es": "Guardando…", "he_il": "שומר…",
        "zh_cn": "正在保存…", "zh_hk": "正在儲存…", "zh_tw": "正在儲存…"}),
    ("waypoint.form.status.add_timeout", {
        "en_us": "The server didn't add the waypoint. Check the chat.",
        "es_es": "El servidor no ha añadido el waypoint. Revisa el chat.",
        "he_il": "השרת לא הוסיף את נקודת הציון. בדוק את הצ'אט.",
        "zh_cn": "服务器未添加该路径点。请查看聊天栏。",
        "zh_hk": "伺服器未新增該路徑點。請查看聊天欄。",
        "zh_tw": "伺服器未新增該路徑點。請查看聊天欄。"}),
    ("waypoint.form.status.send_failed", {
        "en_us": "Couldn't reach the server.", "es_es": "No se ha podido conectar con el servidor.",
        "he_il": "לא ניתן להתחבר לשרת.",
        "zh_cn": "无法连接到服务器。", "zh_hk": "無法連線到伺服器。", "zh_tw": "無法連線到伺服器。"}),
]

# The edit errors, in the order of en_us. en_us and zh_cn already have all but upload_busy.
ERRORS = [
    ("waypoint.edit.error.stale_revision", {
        "es_es": "Este waypoint ha cambiado en el servidor. Vuelve a abrirlo e inténtalo de nuevo.",
        "he_il": "נקודת הציון הזו השתנתה בשרת. פתח אותה מחדש ונסה שוב.",
        "zh_hk": "此路徑點已在伺服器上變更，請重新開啟後再試。",
        "zh_tw": "此路徑點已在伺服器上變更，請重新開啟後再試。"}),
    ("waypoint.edit.error.dimension_not_found", {
        "es_es": "La dimensión ya no existe.", "he_il": "המימד כבר לא קיים.",
        "zh_hk": "該維度已不存在。", "zh_tw": "該維度已不存在。"}),
    ("waypoint.edit.error.list_not_found", {
        "es_es": "La lista ya no existe.", "he_il": "הרשימה כבר לא קיימת.",
        "zh_hk": "該清單已不存在。", "zh_tw": "該清單已不存在。"}),
    ("waypoint.edit.error.waypoint_not_found", {
        "es_es": "El waypoint ya no existe.", "he_il": "נקודת הציון כבר לא קיימת.",
        "zh_hk": "該路徑點已不存在。", "zh_tw": "該路徑點已不存在。"}),
    ("waypoint.edit.error.identifier_collision", {
        "es_es": "Ese identificador ya está en uso.", "he_il": "המזהה הזה כבר בשימוש.",
        "zh_hk": "該識別碼已被佔用。", "zh_tw": "該識別碼已被佔用。"}),
    ("waypoint.edit.error.invalid_display_text", {
        "es_es": "El nombre para mostrar no es un texto con formato válido.",
        "he_il": "שם התצוגה אינו טקסט מעוצב חוקי.",
        "zh_hk": "顯示名稱不是有效的格式化文字。", "zh_tw": "顯示名稱不是有效的格式化文字。"}),
    ("waypoint.edit.error.invalid_value", {
        "es_es": "Uno de los valores editados no es válido.", "he_il": "אחד מהערכים שנערכו אינו חוקי.",
        "zh_hk": "某個編輯值無效。", "zh_tw": "某個編輯值無效。"}),
    ("waypoint.edit.error.identical", {
        "es_es": "No ha cambiado ningún campo.", "he_il": "אף שדה לא השתנה.",
        "zh_hk": "沒有欄位發生變更。", "zh_tw": "沒有欄位發生變更。"}),
    ("waypoint.edit.error.permission_denied", {
        "es_es": "No tienes permiso para editar.", "he_il": "אין לך הרשאת עריכה.",
        "zh_hk": "你沒有編輯權限。", "zh_tw": "你沒有編輯權限。"}),
    ("waypoint.edit.error.malformed_request", {
        "es_es": "El servidor ha rechazado esta solicitud de edición.",
        "he_il": "השרת דחה את בקשת העריכה הזו.",
        "zh_hk": "伺服器拒絕了此編輯請求。", "zh_tw": "伺服器拒絕了此編輯請求。"}),
    ("waypoint.edit.error.duplicate_keyword", {
        "es_es": "Un waypoint no puede contener palabras clave duplicadas.",
        "he_il": "נקודת ציון אינה יכולה לכלול מילות מפתח כפולות.",
        "zh_hk": "一個路徑點不能包含重複的關鍵字。", "zh_tw": "一個路徑點不能包含重複的關鍵字。"}),
    ("waypoint.edit.error.encoding_failed", {
        "es_es": "La actualización es demasiado grande para sincronizarla.",
        "he_il": "העדכון גדול מדי לסנכרון.",
        "zh_hk": "更新內容過大，無法同步。", "zh_tw": "更新內容過大，無法同步。"}),
    ("waypoint.edit.error.upload_busy", {
        "en_us": "The server is busy with a waypoint upload. Try again in a moment.",
        "es_es": "El servidor está ocupado con una subida de waypoints. Inténtalo de nuevo en un momento.",
        "he_il": "השרת עסוק בהעלאת נקודות ציון. נסה שוב בעוד רגע.",
        "zh_cn": "服务器正在处理路径点上传，请稍后再试。",
        "zh_hk": "伺服器正在處理路徑點上傳，請稍後再試。",
        "zh_tw": "伺服器正在處理路徑點上傳，請稍後再試。"}),
    ("waypoint.edit.error.response_timeout", {
        "es_es": "El servidor no ha confirmado la edición a tiempo. Comprueba el waypoint antes de volver a intentarlo.",
        "he_il": "השרת לא אישר את העריכה בזמן. בדוק את נקודת הציון לפני שתנסה שוב.",
        "zh_hk": "伺服器未能及時確認編輯，請檢查路徑點後再重試。",
        "zh_tw": "伺服器未能及時確認編輯，請檢查路徑點後再重試。"}),
]

KEY_LINE = re.compile(r'^  "([^"]+)": ')


def line(key, text):
    return '  %s: %s,\n' % (json.dumps(key), json.dumps(text, ensure_ascii=False))


def index_of(lines, key):
    for i, text in enumerate(lines):
        match = KEY_LINE.match(text)
        if match and match.group(1) == key:
            return i
    raise SystemExit('missing key %s' % key)


def has(lines, key):
    return any((m := KEY_LINE.match(text)) and m.group(1) == key for text in lines)


def apply(locale, directory):
    path = directory / (locale + '.json')
    lines = path.read_text(encoding='utf-8').splitlines(keepends=True)

    # Retire the old keys wherever they exist.
    lines = [text for text in lines
             if not ((m := KEY_LINE.match(text)) and m.group(1) in RETIRED)]

    # New text for kept keys, in place.
    for key, texts in REPLACED.items():
        i = index_of(lines, key)
        lines[i] = line(key, texts[locale])

    # Update -> Save, in place.
    i = index_of(lines, 'waypoint.update.button')
    lines[i] = line('waypoint.save.button', SAVE[locale])

    # The subtitle key follows the Edit title.
    i = index_of(lines, 'waypoint.edit.screen.title')
    lines.insert(i + 1, line('waypoint.edit.screen.location', LOCATION[locale]))

    # The form keys go after the swatch hover texts; the four locales without the edit errors get them too.
    block = [line(key, texts[locale]) for key, texts in FORM]
    if not has(lines, 'waypoint.edit.error.stale_revision'):
        block += [line(key, texts[locale]) for key, texts in ERRORS]
    else:
        i = index_of(lines, 'waypoint.edit.error.encoding_failed')
        lines.insert(i + 1, line('waypoint.edit.error.upload_busy', dict(ERRORS)['waypoint.edit.error.upload_busy'][locale]))
    i = index_of(lines, 'waypoint.edit.screen.current_color.hover')
    lines[i + 1:i + 1] = block

    text = ''.join(lines)
    seen = set()

    def no_duplicates(pairs):
        for key, _ in pairs:
            if key in seen:
                raise SystemExit('%s: duplicate key %s' % (locale, key))
            seen.add(key)
        return dict(pairs)

    json.loads(text, object_pairs_hook=no_duplicates)
    path.write_text(text, encoding='utf-8')


if __name__ == '__main__':
    directory = Path(sys.argv[1])
    for locale in LOCALES:
        apply(locale, directory)
    print('updated', ', '.join(LOCALES))
```

- [ ] **Step 4: Apply it**

```bash
python3 SCRATCH/apply_translations.py mods/src/main/resources/assets/server_waypoint/lang
```

Expected: `updated en_us, es_es, he_il, zh_cn, zh_hk, zh_tw`. Key counts go from 215/116/116/215/116/116 to 245/163/163/245/163/163.

- [ ] **Step 5: Run the test**

Same command as Step 2. Expected: PASS, 6 tests. Also run `ClientConfigTranslationTest`, `RemoteGuiTranslationTest` and `WidgetThemeTranslationTest` to confirm the untouched keys are intact:

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '*TranslationTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

- [ ] **Step 6: Look at the diff**

```bash
/usr/bin/git diff --stat -- mods/src/main/resources
```

Expected: only the six language files. In `en_us` and `zh_cn` the blank lines between groups are still there.

- [ ] **Step 7: Checkpoint.**

---

### Task 8: One helper for a tooltip at the pointer

The form shows a field's tooltip at the pointer, as the settings rows do. `SettingsListWidget` carries the version branch inline (`setTooltipForNextFrame` from 1.21.6, the screen's `setTooltipForNextRenderPass` before). It moves into `DrawContextHelper`, where the guide says version differences belong, and the settings list switches to it. The helper is named `scheduleTooltipAtPointer` because `SettingsListWidget` already has a private `scheduleTooltip`, which would shadow a static import of the same name.

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Produces: `public static void DrawContextHelper.scheduleTooltipAtPointer(GuiGraphicsExtractor context, List<FormattedCharSequence> lines, int mouseX, int mouseY)`.

- [ ] **Step 1: Add the helper**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java`, replace:

```java
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
```

with:

```java
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Consumer;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java`, replace:

```java
    public static void previousItemOverlayLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -200.0F);
        *///?}
    }
```

with:

```java
    public static void previousItemOverlayLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -200.0F);
        *///?}
    }

    /**
     * Schedules a tooltip at the pointer for the current frame, rather than at a widget's bounds. It is
     * {@code setTooltipForNextFrame} from 1.21.6 and the screen's {@code setTooltipForNextRenderPass}
     * before.
     */
    public static void scheduleTooltipAtPointer(GuiGraphicsExtractor context, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        //? if >= 1.21.6 {
        context.setTooltipForNextFrame(lines, mouseX, mouseY);
        //?} else {
        /*net.minecraft.client.gui.screens.Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen != null) {
            screen.setTooltipForNextRenderPass(lines);
        }
        *///?}
    }
```

- [ ] **Step 2: Switch the settings list to it**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java`, replace:

```java
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
```

with:

```java
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scheduleTooltipAtPointer;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java`, replace:

```java
        Minecraft client = Minecraft.getInstance();
        var lines = Tooltip.create(text).toCharSequence(client);
        // Anchor the row's tooltip to the cursor, not the whole scrollable list.
        //? if >=1.21.6 {
        context.setTooltipForNextFrame(lines, mouseX, mouseY);
        //?} else {
        /*if (client.screen != null) client.screen.setTooltipForNextRenderPass(lines);
        *///?}
```

with:

```java
        var lines = Tooltip.create(text).toCharSequence(Minecraft.getInstance());
        // Anchor the row's tooltip to the cursor, not the whole scrollable list.
        scheduleTooltipAtPointer(context, lines, mouseX, mouseY);
```

- [ ] **Step 3: Update the GUI guide**

In the "Tooltip position for scrollable widgets" section of `docs/tips/gui/local-guide.md`:

In `docs/tips/gui/local-guide.md`, replace:

```markdown
- Pass the screen-space `mouseX` and `mouseY` to `GuiGraphicsExtractor.setTooltipForNextFrame(...)`
  on Minecraft 1.21.6 and newer. Do not pass coordinates after a render translation or the item's
  local position. On older versions, use the screen's `setTooltipForNextRenderPass(...)`.
```

with:

```markdown
- Pass the screen-space `mouseX` and `mouseY` to `DrawContextHelper.scheduleTooltipAtPointer(...)`,
  which calls `GuiGraphicsExtractor.setTooltipForNextFrame(...)` on Minecraft 1.21.6 and newer and the
  screen's `setTooltipForNextRenderPass(...)` before. Do not pass coordinates after a render
  translation or the item's local position.
```

In `docs/tips/gui/local-guide.md`, replace:

```markdown
  whole-widget tooltip when moving to per-item scheduling. See `IconListWidget` and
  `RemoteWaypointPanel.BrowserTree` for the two version branches and render ownership.
```

with:

```markdown
  whole-widget tooltip when moving to per-item scheduling. `SettingsListWidget` and the waypoint form
  schedule through the helper; `IconListWidget` and `RemoteWaypointPanel.BrowserTree` still carry the
  two version branches inline and show render ownership.
```

- [ ] **Step 4: Compile and run the settings list tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.widgets.SettingsListWidget*' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 5: Compile the oldest target**

The `< 1.21.6` branch of the new helper is inactive in the active target, so only another target compiles it.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:1.20.1-fabric:compileJava --offline -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Check the markers**

```bash
python3 SCRATCH/check_stonecutter.py mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SettingsListWidget.java
```

Expected: `balanced: 2 file(s)`.

- [ ] **Step 7: Checkpoint.**

---

### Task 9: Placeholder, invalid outline and Enter

The form needs a placeholder that follows the theme (Display name shows the Name field's text until an override is typed), a danger outline for a field a check rejects, and a way for Enter to take the highlighted suggestion while a list is open. All of it goes through vanilla's `EditBox` hint or small opt-in methods, so no other screen changes behavior. `ColorHexCodeField` moves its `RRGGBB` hint onto the new placeholder and drops its Adventure-to-vanilla conversion.

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TranslucentTextField.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ComboBoxWidget.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java`
- Modify: `docs/tips/gui/local-guide.md`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInputTest.java`

**Interfaces:**
- Produces: `SuggestingTextInput.setPlaceholder(Supplier<Component>)`, `boolean acceptHighlightedSuggestion()`, `boolean isSuggestionListOpen()`; `ComboBoxWidget.setPlaceholder(Supplier<Component>)`, `boolean isSuggestionListOpen()`; `TranslucentTextField.setInvalid(boolean)`.

- [ ] **Step 1: Write the failing tests** (this pins Review Focus item 5)

`SuggestingTextInputTest` builds a real field with `TestFont` that reports being focused. Add these tests before its `fieldSuggesting` helper:

In `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInputTest.java`, replace:

```java
    /**
     * A field showing these suggestions. From 26.1 a real text field asks the game client to start
```

with:

```java
    @Test
    void acceptingTakesTheHighlightedSuggestionAndClosesTheList() {
        TranslucentTextField field = fieldSuggesting("alpha", "beta");
        // Down highlights the second suggestion.
        assertTrue(field.keyPressed(InputConstants.KEY_DOWN, 0, 0));

        assertTrue(field.acceptHighlightedSuggestion());

        assertEquals("beta", field.getValue());
        assertFalse(field.isSuggestionListOpen());
    }

    @Test
    void acceptingTakesTheFirstSuggestionWhenNoneWasMoved() {
        TranslucentTextField field = fieldSuggesting("alpha", "beta");

        assertTrue(field.acceptHighlightedSuggestion());

        assertEquals("alpha", field.getValue());
    }

    @Test
    void acceptingDoesNothingWhileNoListIsShowing() {
        TranslucentTextField field = fieldSuggesting();

        assertFalse(field.acceptHighlightedSuggestion());

        assertEquals("", field.getValue());
    }

    @Test
    void theListIsOpenOnlyWhileThereAreSuggestions() {
        assertTrue(fieldSuggesting("alpha").isSuggestionListOpen());
        assertFalse(fieldSuggesting().isSuggestionListOpen());
    }

    /**
     * A field showing these suggestions. From 26.1 a real text field asks the game client to start
```

- [ ] **Step 2: Run them and see them fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.widgets.SuggestingTextInputTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: method acceptHighlightedSuggestion()`.

- [ ] **Step 3: Add the placeholder, `acceptHighlightedSuggestion` and `isSuggestionListOpen` to `SuggestingTextInput`**

The placeholder goes through `EditBox.setHint`, which vanilla shows while the field is empty and unfocused. From 26.1 vanilla wraps a hint that has no style of its own in a fixed gray, so the input gives the text the theme's color, and re-applies it only when the text or the color changes.

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`, replace:

```java
import java.util.Locale;
import java.util.Set;
```

with:

```java
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`, replace:

```java
import net.minecraft.network.chat.Component;

/** Surface-free editable input
```

with:

```java
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Surface-free editable input
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`, replace:

```java
    private boolean suggestionsDismissed;

    public SuggestingTextInput(int x, int y, int width, Component text, Font textRenderer) {
```

with:

```java
    private boolean suggestionsDismissed;
    private @Nullable Supplier<Component> placeholder;
    private @Nullable Component shownPlaceholder;
    private int shownPlaceholderColor;

    public SuggestingTextInput(int x, int y, int width, Component text, Font textRenderer) {
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`, replace:

```java
    public void setSuggestionsProvider(Supplier<List<String>> suggestionsProvider) {
```

with:

```java
    /**
     * Shows themed text while the field is empty and unfocused, in {@code TEXT_PLACEHOLDER}, or
     * {@code TEXT_DISABLED} while inactive. It goes through vanilla's hint, which gives text without a
     * color of its own a fixed gray. The supplier is read every frame, so the text can follow another field.
     */
    public void setPlaceholder(Supplier<Component> placeholder) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
        this.shownPlaceholder = null;
        this.updatePlaceholder();
    }

    public void setSuggestionsProvider(Supplier<List<String>> suggestionsProvider) {
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`, replace:

```java
    public boolean mouseClickedSuggestion(double mouseX, double mouseY) {
        return this.handleSuggestionMouseClicked(mouseX, mouseY);
    }
```

with:

```java
    public boolean mouseClickedSuggestion(double mouseX, double mouseY) {
        return this.handleSuggestionMouseClicked(mouseX, mouseY);
    }

    /**
     * Takes the highlighted suggestion, as clicking it does, for a screen that gives Enter this meaning
     * while a list is open; false when no list is showing.
     */
    public boolean acceptHighlightedSuggestion() {
        if (!this.isSuggestionListVisible()) {
            return false;
        }
        this.useSuggestion(this.getValue());
        this.tabCycles = false;
        this.suggestionsDismissed = false;
        this.updateSuggestions();
        return true;
    }

    /** Whether the suggestion list is showing: the field is focused and has suggestions. */
    public boolean isSuggestionListOpen() {
        return this.isSuggestionListVisible();
    }
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`, replace:

```java
    protected void updateThemeTextColors() {
        this.setTextColor(WidgetThemeState.text(this.active));
        this.setTextColorUneditable(getColor(TEXT_DISABLED));
    }
```

with:

```java
    protected void updateThemeTextColors() {
        this.setTextColor(WidgetThemeState.text(this.active));
        this.setTextColorUneditable(getColor(TEXT_DISABLED));
        this.updatePlaceholder();
    }

    private void updatePlaceholder() {
        if (this.placeholder == null) {
            return;
        }
        Component text = this.placeholder.get();
        int color = getColor(this.active ? TEXT_PLACEHOLDER : TEXT_DISABLED) & 0x00FFFFFF;
        if (color == this.shownPlaceholderColor && text.equals(this.shownPlaceholder)) {
            return;
        }
        this.shownPlaceholder = text;
        this.shownPlaceholderColor = color;
        this.setHint(text.copy().withStyle(style -> style.withColor(color)));
    }
```

- [ ] **Step 4: Add the danger outline to `TranslucentTextField`**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TranslucentTextField.java`, replace:

```java
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
```

with:

```java
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TranslucentTextField.java`, replace:

```java
public class TranslucentTextField extends SuggestingTextInput {
    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
```

with:

```java
public class TranslucentTextField extends SuggestingTextInput {
    private boolean invalid;

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TranslucentTextField.java`, replace:

```java
    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
```

with:

```java
    /** Draws the outline in the danger color instead of the border color, for a value the form rejects. */
    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TranslucentTextField.java`, replace:

```java
        int bdColor = WidgetThemeState.border(this.active, isFocused(), isHovered());
        renderOutline(context, x, y, this.width, this.backgroundHeight, bdColor);
```

with:

```java
        int bdColor = this.invalid ? getColor(DANGER) : WidgetThemeState.border(this.active, isFocused(), isHovered());
        renderOutline(context, x, y, this.width, this.backgroundHeight, bdColor);
```

- [ ] **Step 5: Pass the placeholder and the list state through `ComboBoxWidget`**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ComboBoxWidget.java`, replace:

```java
    public boolean closeSuggestionsIfOpen() {
        return this.input.closeSuggestionsIfOpen();
    }
```

with:

```java
    /** Shows themed text while the field is empty and unfocused; see {@link SuggestingTextInput#setPlaceholder}. */
    public void setPlaceholder(Supplier<Component> placeholder) {
        this.input.setPlaceholder(placeholder);
    }

    public boolean closeSuggestionsIfOpen() {
        return this.input.closeSuggestionsIfOpen();
    }

    /** Whether the suggestion list of the text input is showing; the choice list is {@link #isExpanded()}. */
    public boolean isSuggestionListOpen() {
        return this.input.isSuggestionListOpen();
    }
```

- [ ] **Step 6: Move `ColorHexCodeField`'s hint onto the placeholder**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java`, replace:

```java
import _959.server_waypoint.common.client.gui.api.Colorable;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PLACEHOLDER;
import static _959.server_waypoint.common.network.ModMessageSender.toVanillaText;
import static _959.server_waypoint.util.ColorUtils.hexCodeToRgb;
```

with:

```java
import _959.server_waypoint.common.client.gui.api.Colorable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.util.ColorUtils.hexCodeToRgb;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java`, replace:

```java
    private final Font textRenderer;
    private int hintColor;
    private boolean hintColorInitialized;

    public ColorHexCodeField(int x, int y, net.minecraft.network.chat.Component text, Font textRenderer) {
        super(x, y, 39, text, textRenderer);
        this.textRenderer = textRenderer;
        this.setMaxLength(6);
        this.updateThemeHint();
    }
```

with:

```java
    private final Font textRenderer;

    public ColorHexCodeField(int x, int y, Component text, Font textRenderer) {
        super(x, y, 39, text, textRenderer);
        this.textRenderer = textRenderer;
        this.setMaxLength(6);
        this.setPlaceholder(() -> Component.literal("RRGGBB"));
    }
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java`, replace:

```java
        this.updateThemeTextColors();
        this.updateThemeHint();
        this.isHovered
```

with:

```java
        this.updateThemeTextColors();
        this.isHovered
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java`, replace:

```java
    private void updateThemeHint() {
        int color = WidgetThemeManager.getColor(this.active ? TEXT_PLACEHOLDER : TEXT_DISABLED);
        if (this.hintColorInitialized && this.hintColor == color) {
            return;
        }
        this.hintColor = color;
        this.hintColorInitialized = true;
        this.setHint(toVanillaText(Component.text("RRGGBB").color(TextColor.color(color & 0x00FFFFFF))));
    }

    @Override
    public int getColor() {
```

with:

```java
    @Override
    public int getColor() {
```

- [ ] **Step 7: Update the GUI guide**

In the `SuggestingTextInput` paragraph, after the sentence that ends "preserve its separate-rendering contract.":

In `docs/tips/gui/local-guide.md`, replace:

```markdown
another popup when the full menu is closed; preserve its separate-rendering contract.
```

with:

```markdown
another popup when the full menu is closed; preserve its separate-rendering contract.

`setPlaceholder(Supplier<Component>)` shows themed text while the field is empty and unfocused, in
`TEXT_PLACEHOLDER` (`TEXT_DISABLED` while inactive). It goes through vanilla's `EditBox` hint, which
gives text without a color of its own a fixed gray, so the input styles the text with the theme's
color each frame. The supplier is read every frame too, so the text can follow another field, as the
waypoint form's Display name follows Name. `ComboBoxWidget.setPlaceholder` passes it to its input,
and `ColorHexCodeField` uses it for `RRGGBB`. `TranslucentTextField.setInvalid(true)` draws the
outline in `DANGER` instead of the border color until it is cleared, for a value a form rejects.
`isSuggestionListOpen()`, also on `ComboBoxWidget` for its input's list, reports whether a list is
showing. `acceptHighlightedSuggestion()` takes the highlighted suggestion the way clicking it does
and reports whether a list was showing. Text fields don't handle Enter themselves; a screen that
gives Enter this meaning while a list is open, as the waypoint form does, calls it before using
Enter for anything else.
```

- [ ] **Step 8: Run the widget tests**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.widgets.*' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS. `SuggestingTextInputTest` now has 18 tests.

- [ ] **Step 9: Check the markers**

```bash
python3 SCRATCH/check_stonecutter.py mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/TranslucentTextField.java mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ColorHexCodeField.java mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ComboBoxWidget.java
```

Expected: `balanced: 4 file(s)`.

- [ ] **Step 10: Checkpoint.**

---

### Task 10: The remove-icon button

The icon row's Clear text button becomes a 13×13 `IconButton` with a new 9×9 texture, inactive while no icon is selected (which tints it with `TEXT_DISABLED`, like the settings screen's reset icons), with a vanilla "Remove icon" tooltip. The dropdown gets the placeholder "None — shows the initials". The texture uses the same light gray as `reset.png`, so the tint shows.

**Files:**
- Create: `mods/src/main/resources/assets/server_waypoint/textures/gui/clear.png`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WidgetTextures.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: `ComboBoxWidget.setPlaceholder` (Task 9), `IconButton`.
- Produces: `WidgetTextures.CLEAR_ICON`; `WaypointIconPicker.clearButton()` now returns `IconButton`, and its `active` follows whether an icon is selected.

- [ ] **Step 1: Generate the texture**

Run this from the repository root. It writes a 9×9 RGBA PNG with a one-pixel ✕ in `#D9D9D9`, the color `reset.png` uses.

```bash
python3 - <<'EOF'
import struct, zlib

W = H = 9
LIGHT = (217, 217, 217, 255)
CLEAR = (0, 0, 0, 0)
CROSS = {(i, i) for i in range(1, 8)} | {(8 - i, i) for i in range(1, 8)}
rows = b''.join(
    b'\x00' + b''.join(bytes(LIGHT if (x, y) in CROSS else CLEAR) for x in range(W))
    for y in range(H)
)


def chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)


png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(rows, 9))
       + chunk(b'IEND', b''))
open('mods/src/main/resources/assets/server_waypoint/textures/gui/clear.png', 'wb').write(png)
EOF
```

- [ ] **Step 2: Check it**

```bash
file mods/src/main/resources/assets/server_waypoint/textures/gui/clear.png
```

Expected: `PNG image data, 9 x 9, 8-bit/color RGBA, non-interlaced`.

- [ ] **Step 3: Register the texture**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WidgetTextures.java`, replace:

```java
    RESET_ICON = modId("textures/gui/reset.png");
```

with:

```java
    RESET_ICON = modId("textures/gui/reset.png");

    public static final
    //$ resource_location_type_swap
    Identifier
    CLEAR_ICON = modId("textures/gui/clear.png");
```

- [ ] **Step 4: Change the icon picker**

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`, replace:

```java
import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
```

with:

```java
import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`, replace:

```java
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
```

with:

```java
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`, replace:

```java
public final class WaypointIconPicker {
    private final ComboBoxWidget menu;
    private final TranslucentButton clearButton;
```

with:

```java
public final class WaypointIconPicker {
    private static final int CLEAR_BUTTON_SIZE = 13;

    private final ComboBoxWidget menu;
    private final IconButton clearButton;
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`, replace:

```java
        this.menu.setRenderPopupSeparately(true);
        this.clearButton = new TranslucentButton(0, 0, 42, 11,
                Component.translatable("waypoint.icon.clear"), () -> select(null, true));
    }
```

with:

```java
        this.menu.setRenderPopupSeparately(true);
        this.menu.setPlaceholder(() -> Component.translatable("waypoint.form.no_icon"));
        Component clearLabel = Component.translatable("waypoint.icon.clear");
        this.clearButton = new IconButton(0, 0, CLEAR_BUTTON_SIZE, CLEAR_BUTTON_SIZE, clearLabel,
                WidgetTextures.CLEAR_ICON, () -> select(null, true));
        this.clearButton.setTooltip(Tooltip.create(clearLabel));
        this.clearButton.active = false;
    }
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`, replace:

```java
    private void select(@Nullable NamespacedId id, boolean notify) {
        this.selectedIcon = id;
        this.menu.setValue(id == null ? "" : id.toString());
```

with:

```java
    private void select(@Nullable NamespacedId id, boolean notify) {
        this.selectedIcon = id;
        // There is nothing to remove while no icon is selected.
        this.clearButton.active = id != null;
        this.menu.setValue(id == null ? "" : id.toString());
```

In `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`, replace:

```java
    public TranslucentButton clearButton() {
```

with:

```java
    public IconButton clearButton() {
```

- [ ] **Step 5: Update the GUI guide**

In "Waypoint icon picker and renderer":

In `docs/tips/gui/local-guide.md`, replace:

```markdown
`WaypointIconPicker` owns a searchable `ComboBoxWidget`, a clear button, and the selected nullable `NamespacedId`. Add and edit screens register the menu and button once for input, render the menu's popup after the main form, and read `getSelectedIcon()` when submitting.
```

with:

```markdown
`WaypointIconPicker` owns a searchable `ComboBoxWidget` whose placeholder reads "None — shows the initials", a 13×13 `IconButton` that removes the icon, and the selected nullable `NamespacedId`. The button draws `WidgetTextures.CLEAR_ICON`, is inactive while no icon is selected, which tints its icon with `TEXT_DISABLED`, and has vanilla's "Remove icon" tooltip. Add and edit screens register the menu and button once for input, render the menu's popup after the main form, and read `getSelectedIcon()` when submitting.
```

In `docs/tips/gui/local-guide.md`, replace:

```markdown
In the waypoint form, the icon row's first label has no leading padding. Reserve the combobox height plus the preview-to-field gap before the combobox, so the preview starts directly after the label like other form controls.
```

with:

```markdown
In the waypoint form, the icon row is the 11-pixel preview, 4 pixels, the stretched dropdown, 4 pixels and the remove button, starting at the control column.
```

- [ ] **Step 6: Compile and run the tests**

The current screens still compile: they treat `clearButton()` as a widget, and an `IconButton` is one.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: PASS.

- [ ] **Step 7: Checkpoint.**

---
### Task 11: The form screens

The three screens are rewritten together, because the base's abstract methods change and both subclasses implement them. Everything the earlier tasks built is used here.

**How the pieces fit** (read this before the code):

- **Build once.** The constructor creates the shared widgets and their callbacks and stores the saved values; it calls no method a subclass overrides. `init()` calls the subclass methods (`leadingRows()`, `subtitle()`, `hasDisplayNameRow()`, `footerButtons()`), builds the label, tab-order, dropdown, hover-area and suggestion lists once, registers the widgets in tab order, then lays out. A `formReady` flag keeps responders that fire during construction inert.
- **Layout.** `layoutForm()` measures the text and widgets, asks `WaypointFormLayout` for the columns and the arrangement, then places every widget with `placeInRow`/`placeOutline`, which position a widget by its outline whatever its anchor (the text fields anchor their text, the combobox its text, the buttons their content). It runs in `init()`, when the footer message changes (a wrapped message changes the footer's height) and on resize through `repositionElements()`, which keeps the widgets, so values, focus, the message and a pending request survive; it never runs every frame.
- **State.** `refreshControlStates()` runs on every edit and every tick. It runs `WaypointFormCheck`, sets every control's `active` flag from the color picker, a pending request, the check result and (Edit) whether the form differs from the saved waypoint, sets the danger outlines and picks the footer message: pending text, else a server result or timeout, else the check's problem. Editing any field clears a server result or timeout.
- **Submit.** Add, or Enter in a text field, runs `submitForm()`: refresh, and if the primary button is active, `submit()`. Add sends `/wp add` and records a `PendingAdd`; each tick it closes when the waypoint appears, or unlocks with a message after 5 seconds. Edit builds its patch and sends the request as before, beginning the deadline before it sends so a synchronous reply matches.
- **Input.** Enter in a text field first takes a highlighted suggestion, else submits if Add or Save is active. Dropdowns are routed before other controls, all through one loop. The base overrides `pickInitialFocus()` (1.20.5+), because vanilla would move focus to the first Tab stop after a keyboard press; Add focuses List when it's empty, otherwise Name, and Edit focuses nothing.
- **Drawing.** Order: panel, header, labels and dividers, row widgets, status, icon preview, suggestions, dropdown popups, the 500 ms field tooltip, then the color picker.

**Files:**
- Modify (rewrite): `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/AbstractWaypointPropertiesScreen.java`
- Modify (rewrite): `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointAddScreen.java`
- Modify (rewrite): `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointEditScreen.java`
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/TestFont.java`
- Modify: `docs/tips/gui/local-guide.md`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormTextTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–10. From existing code: `ScalableText`, `WidgetPack`, `SeparatorWidget`, `VisualPositioning` (public getters), `MovementAllowedScreen` (`pickInitialFocus`, `centered`, `acceptMovementKeys`), `TranslucentButton.fitted`, `ComboBoxWidget.layoutPopup`, `WaypointClientMod`.
- Produces: the constructors `WaypointAddScreen(Screen, String, String[, WaypointPos])` and `WaypointEditScreen(Screen, String, String, [String,] SimpleWaypoint)`, unchanged, so the manager, the list widget and the Xaero's World Map mixins compile untouched; `WaypointEditScreen.handleResult(WaypointEditResultMessage)`, unchanged, for `WaypointClientMod`; and the base's `static Component cutToWidth(Font, Component, int)`.

- [ ] **Step 1: Write the failing test** (this pins Review Focus item 2)

`TestFont` makes every character 6 pixels wide but doesn't implement `substrByWidth`, so add it:

In `mods/src/test/java/_959/server_waypoint/common/client/gui/TestFont.java`, replace:

```java
    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
```

with:

```java
    /** The whole characters that fit, from the start, without their styles. */
    @Override
    public FormattedText substrByWidth(FormattedText text, int maxWidth) {
        String characters = text.getString();
        int fitting = Math.min(characters.length(), Math.max(0, maxWidth) / CHARACTER_WIDTH);
        return FormattedText.of(characters.substring(0, fitting));
    }

    @Override
    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
```

`mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointFormTextTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The header text is one line as wide as the panel: text that doesn't fit is cut and ends with an
 * ellipsis. {@link TestFont} makes every character 6 pixels wide.
 */
class WaypointFormTextTest {
    private static final TestFont FONT = new TestFont();

    @Test
    void textThatFitsIsLeftAlone() {
        Component text = Component.literal("Home");

        assertSame(text, AbstractWaypointPropertiesScreen.cutToWidth(FONT, text, 24));
    }

    @Test
    void textThatDoesNotFitIsCutAndEndsWithAnEllipsis() {
        Component cut = AbstractWaypointPropertiesScreen.cutToWidth(FONT, Component.literal("Home Base"), 6 * 6);

        // Six characters fit: five of the text and the ellipsis.
        assertEquals("Home …", cut.getString());
        assertEquals(6 * 6, FONT.width(cut));
    }

    @Test
    void aWidthTooSmallForAnyTextLeavesOnlyTheEllipsis() {
        Component cut = AbstractWaypointPropertiesScreen.cutToWidth(FONT, Component.literal("Home Base"), 3);

        assertEquals("…", cut.getString());
    }
}
```

- [ ] **Step 2: Run it and see it fail**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test --tests '_959.server_waypoint.common.client.gui.screens.WaypointFormTextTest' -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: FAIL to compile, `cannot find symbol: method cutToWidth`.

- [ ] **Step 3: Replace `AbstractWaypointPropertiesScreen.java`**

Replace the whole file with this. It keeps the old file's coordinate parsing, suggestions and looked-at-block code, and its Stonecutter branches for mouse events and reach.

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/AbstractWaypointPropertiesScreen.java`:

```java
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.VisualPositioning;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;
import _959.server_waypoint.common.client.gui.render.WaypointRowRenderer;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Arrangement;
import _959.server_waypoint.common.client.gui.screens.WaypointFormLayout.Columns;
import _959.server_waypoint.common.client.gui.widgets.ColorHexCodeField;
import _959.server_waypoint.common.client.gui.widgets.ColorSquareButton;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.CoordinateField;
import _959.server_waypoint.common.client.gui.widgets.IntegerField;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SeparatorWidget;
import _959.server_waypoint.common.client.gui.widgets.SuggestingTextInput;
import _959.server_waypoint.common.client.gui.widgets.SwatchWidget;
import _959.server_waypoint.common.client.gui.widgets.ToggleButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.gui.widgets.WaypointIconPicker;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.util.CoordinateInputParser;
import _959.server_waypoint.common.util.CoordinateSuggestions;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.scheduleTooltipAtPointer;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_DESCRIPTION_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_NAME_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.ColorUtils.BLUE;
import static _959.server_waypoint.util.ColorUtils.GREEN;
import static _959.server_waypoint.util.ColorUtils.RED;
import static _959.server_waypoint.util.ColorUtils.randomColor;
import static _959.server_waypoint.util.WaypointInitials.getInitialsCandidatesFromName;

/**
 * The waypoint add and edit forms: one compact, fixed panel with a label column and a control column,
 * laid out by {@link WaypointFormLayout}. This base owns the shared fields, their checks, the layout
 * and the drawing; a subclass supplies what differs: the rows above Name, the footer buttons and what
 * Add or Save does. {@code init()} builds the form after both constructors have finished, so the
 * constructors call no method a subclass overrides.
 */
public abstract class AbstractWaypointPropertiesScreen extends MovementAllowedScreen {
    private static final float TITLE_SCALE = 1.2F;
    private static final long TOOLTIP_DELAY_NANOS = 500_000_000L;
    private static final int PREVIEW_SIZE = 11;
    private static final int MIN_STATUS_WIDTH = 100;
    private static final String AXIS_LETTERS = "XYZRUF";
    private static final Component ELLIPSIS = Component.literal("…");
    private static final WaypointFormCheck.Lookup CLIENT_DATA = new ClientData();

    protected final Screen previousScreen;
    protected final String dimensionName;
    protected final String listName;
    /** The waypoint being edited as saved; null on Add. */
    protected final @Nullable WaypointFormPatch.Saved saved;
    protected final TranslucentTextField nameEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.name"), font);
    protected final TranslucentTextField displayNameEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.display_name"), font);
    protected final TranslucentTextField initialsEditBox = new TranslucentTextField(0, 0, WaypointFormLayout.SMALL_FIELD_WIDTH, Component.translatable("waypoint.form.initials"), font);
    protected final ColorHexCodeField colorEditBox = new ColorHexCodeField(0, 0, Component.translatable("waypoint.form.color"), font);
    protected final ColorSquareButton colorPickerButton = new ColorSquareButton(0, 0, 9, this::openSwatch);
    protected final CoordinateField xEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("X"), font);
    protected final CoordinateField yEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("Y"), font);
    protected final CoordinateField zEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("Z"), font);
    protected final IntegerField yawEditBox = new IntegerField(0, 0, WaypointFormLayout.SMALL_FIELD_WIDTH, Component.translatable("waypoint.form.yaw"), font);
    protected final TranslucentTextField keywordsEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.keywords"), font);
    protected final TranslucentTextField descriptionEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.form.description"), font);
    protected final ToggleButton globalToggle = new ToggleButton(
            0,
            0,
            WaypointFormLayout.TOGGLE_WIDTH,
            11,
            Component.translatable("waypoint.local"),
            Component.translatable("waypoint.global"),
            WidgetThemeVariable.CONTROL_BACKGROUND,
            WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND,
            state -> this.onFormEdited()
    );
    protected final SwatchWidget swatchWidget = new SwatchWidget(0, 0, font, color -> {
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.closeSwatch();
        this.onFormEdited();
    });
    protected final TranslucentButton cancelButton = TranslucentButton.fitted(
            Component.translatable("server_waypoint.cancel.button"), this::onClose);
    protected final WaypointIconPicker iconPicker;
    protected WaypointPos coordinateDefaultPos;

    private final ScalableText titleText;
    private final ScalableText statusText;
    private final ScalableText xLabel = new ScalableText(0, 0, Component.nullToEmpty("X"), RED, font);
    private final ScalableText yLabel = new ScalableText(0, 0, Component.nullToEmpty("Y"), GREEN, font);
    private final ScalableText zLabel = new ScalableText(0, 0, Component.nullToEmpty("Z"), BLUE, font);
    private final Map<FormField, ScalableText> labels = new EnumMap<>(FormField.class);
    private final Map<FormField, List<LayoutElement>> hoverParts = new EnumMap<>(FormField.class);
    private final SpacerElement iconPreview = new SpacerElement(PREVIEW_SIZE, PREVIEW_SIZE);
    private final boolean emptyDisplayNameOverride;
    private @Nullable ScalableText subtitleText;
    private List<LeadingRow> leading = List.of();
    private List<TranslucentButton> buttons = List.of();
    private List<AbstractWidget> tabOrder = List.of();
    private List<AbstractWidget> fieldControls = List.of();
    private List<ComboBoxWidget> dropdowns = List.of();
    private List<TranslucentTextField> suggestionFields = List.of();
    private List<Renderable> staticParts = List.of();
    private @Nullable Arrangement arrangement;
    private boolean built;
    private boolean formReady;
    private boolean focusChosen;
    private boolean hasStatus;
    private @Nullable Component shownStatus;
    private @Nullable Component resultMessage;
    private WaypointFormCheck.Field resultField = WaypointFormCheck.Field.NONE;
    private @Nullable FormField hoveredField;
    private long hoveredSince;
    private String lastName;
    private boolean enforcingCoordinateMode;

    /** The rows above Name, in order: Add's Dimension and List. */
    protected abstract List<LeadingRow> leadingRows();

    /** The muted line under the title, or null for none. */
    protected abstract @Nullable Component subtitle();

    /** Whether the panel has a Display name row below Name. */
    protected abstract boolean hasDisplayNameRow();

    /** The footer's buttons, left to right. */
    protected abstract List<TranslucentButton> footerButtons();

    /** The footer's Add or Save button: it is active only when the form can be sent. */
    protected abstract TranslucentButton primaryButton();

    /** The values the checks run on. */
    protected abstract WaypointFormCheck.Input checkInput();

    /** Sends the form; called only when the checks pass and the primary button is active. */
    protected abstract void submit();

    /** The message to show while a request is pending, or null when none is; it locks the form. */
    protected abstract @Nullable Component pendingMessage();

    /**
     * Sets the footer buttons' {@code active} flags. {@code locked} covers the color picker and a
     * pending request; {@code canSubmit} adds the checks and, on Edit, whether anything changed.
     */
    protected abstract void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed);

    /** Whether the form differs from what it started with; Edit's Save and Reset need this. */
    protected boolean hasChanges() {
        return true;
    }

    /** The control to focus when the screen opens, or null for none. */
    protected @Nullable GuiEventListener initialFocus() {
        return null;
    }

    /** Runs every tick, before the checks: a subclass watches its pending request here. */
    protected void onTick() {
    }

    public AbstractWaypointPropertiesScreen(Screen previousScreen, Component title, String dimensionName, String listName, @Nullable SimpleWaypoint waypoint) {
        super(title);
        this.previousScreen = previousScreen;
        this.dimensionName = dimensionName;
        this.listName = listName;
        this.saved = waypoint == null ? null : WaypointFormPatch.Saved.of(waypoint);
        this.emptyDisplayNameOverride = waypoint != null && "".equals(waypoint.displayNameOverride());
        this.iconPicker = new WaypointIconPicker(174, ignored -> this.onFormEdited());
        this.titleText = new ScalableText(0, 0, title, TITLE_SCALE, TEXT_PRIMARY, font);
        this.statusText = new ScalableText(0, 0, Component.empty(), 1.0F, TEXT_MUTED, MIN_STATUS_WIDTH, font);
        this.swatchWidget.visible = false;

        int rgb;
        String initialName = "";
        if (waypoint == null) {
            rgb = 0xFF000000 | randomColor();
            this.coordinateDefaultPos = new WaypointPos(0, 0, 0);
            this.xEditBox.setValue("0");
            this.yEditBox.setValue("0");
            this.zEditBox.setValue("0");
            this.yawEditBox.setValue("0");
            this.globalToggle.setState(true);
            this.iconPicker.setSelectedIcon(null);
        } else {
            WaypointFormPatch.Saved values = Objects.requireNonNull(this.saved);
            initialName = values.name();
            rgb = 0xFF000000 | values.rgb();
            this.coordinateDefaultPos = values.position();
            this.nameEditBox.setValue(values.name());
            this.displayNameEditBox.setValue(values.displayNameOverride() == null ? "" : values.displayNameOverride());
            this.initialsEditBox.setValue(values.initials());
            this.keywordsEditBox.setValue(String.join(", ", values.keywords()));
            this.descriptionEditBox.setValue(values.description());
            this.xEditBox.setValue(Integer.toString(values.position().x()));
            this.xEditBox.setDefaultValue(values.position().x());
            this.yEditBox.setValue(Integer.toString(values.position().y()));
            this.yEditBox.setDefaultValue(values.position().y());
            this.zEditBox.setValue(Integer.toString(values.position().z()));
            this.zEditBox.setDefaultValue(values.position().z());
            this.yawEditBox.setValue(Integer.toString(values.yaw()));
            this.yawEditBox.setDefaultValue(values.yaw());
            this.globalToggle.setState(values.global());
            this.iconPicker.setSelectedIcon(values.icon());
        }
        this.lastName = initialName;
        this.colorEditBox.setColor(rgb);
        this.colorPickerButton.setColor(rgb);
        this.swatchWidget.setColor(rgb);
        this.swatchWidget.setPreviousColor(rgb);

        this.nameEditBox.setMaxLength(65_535);
        this.displayNameEditBox.setMaxLength(MAX_NAME_LENGTH);
        this.keywordsEditBox.setMaxLength(WaypointFormCheck.MAX_KEYWORDS_TEXT_LENGTH);
        this.descriptionEditBox.setMaxLength(MAX_DESCRIPTION_LENGTH);
        this.yawEditBox.setMaxLength(4);
        this.displayNameEditBox.setPlaceholder(() -> this.emptyDisplayNameOverride
                ? Component.translatable("waypoint.form.empty_display_name")
                : Component.literal(this.nameEditBox.getValue()));
        this.keywordsEditBox.setPlaceholder(() -> Component.translatable("waypoint.form.optional"));
        this.descriptionEditBox.setPlaceholder(() -> Component.translatable("waypoint.form.optional"));
        this.initialsEditBox.setSuggestionsProvider(this::getWaypointInitialsSuggestions);
        this.configureResponders();
        this.configureCoordinateModeEnforcement();
        this.configureCoordinateSuggestions();
    }

    private void configureResponders() {
        this.nameEditBox.setResponder(name -> {
            String initials = WaypointFormInitials.afterNameChange(this.lastName, name, this.initialsEditBox.getValue());
            this.lastName = name;
            if (!initials.equals(this.initialsEditBox.getValue())) {
                this.initialsEditBox.setValue(initials);
            }
            this.onFormEdited();
        });
        this.colorEditBox.setResponder(text -> {
            this.colorPickerButton.setColor(this.colorEditBox.getColor());
            this.onFormEdited();
        });
        for (EditBox field : List.of(this.displayNameEditBox, this.initialsEditBox, this.yawEditBox,
                this.keywordsEditBox, this.descriptionEditBox)) {
            field.setResponder(text -> this.onFormEdited());
        }
    }

    // ------------------------------------------------------------------ building

    /** One labelled row above Name whose single control fills the control column. */
    protected record LeadingRow(FormField field, AbstractWidget control, IntConsumer setWidth) {
    }

    /** The form's fields, each with the tooltip shown when the pointer rests on its label or controls. */
    enum FormField {
        DIMENSION("waypoint.form.dimension", "waypoint.form.dimension.tooltip"),
        LIST("waypoint.form.list", "waypoint.form.list.tooltip"),
        NAME("waypoint.form.name", "waypoint.form.name.tooltip"),
        INITIALS("waypoint.form.initials", "waypoint.form.initials.tooltip"),
        DISPLAY_NAME("waypoint.form.display_name", "waypoint.form.display_name.tooltip"),
        ICON("waypoint.icon.label", "waypoint.form.icon.tooltip"),
        COLOR("waypoint.form.color", "waypoint.form.color.tooltip"),
        VISIBILITY("waypoint.form.visibility", "waypoint.form.visibility.tooltip"),
        POSITION("waypoint.form.position", "waypoint.form.position.tooltip"),
        YAW("waypoint.form.yaw", "waypoint.form.yaw.tooltip"),
        KEYWORDS("waypoint.form.keywords", "waypoint.form.keywords.tooltip"),
        DESCRIPTION("waypoint.form.description", "waypoint.form.description.tooltip");

        private final String labelKey;
        private final String tooltipKey;

        FormField(String labelKey, String tooltipKey) {
            this.labelKey = labelKey;
            this.tooltipKey = tooltipKey;
        }

        String labelKey() {
            return this.labelKey;
        }

        String tooltipKey() {
            return this.tooltipKey;
        }
    }

    /** Builds what depends on the subclass, once both constructors have finished. */
    private void buildForm() {
        this.leading = List.copyOf(this.leadingRows());
        this.buttons = List.copyOf(this.footerButtons());
        boolean displayName = this.hasDisplayNameRow();
        @Nullable Component subtitle = this.subtitle();
        if (subtitle != null) {
            this.subtitleText = new ScalableText(0, 0, subtitle, TEXT_MUTED, font);
        }
        for (FormField field : FormField.values()) {
            this.labels.put(field, new ScalableText(0, 0, Component.translatable(field.labelKey()), TEXT_PRIMARY, font));
        }

        List<AbstractWidget> controls = new ArrayList<>();
        List<ComboBoxWidget> dropdownList = new ArrayList<>();
        List<TranslucentTextField> textFields = new ArrayList<>();
        for (LeadingRow row : this.leading) {
            controls.add(row.control());
            if (row.control() instanceof ComboBoxWidget combo) {
                dropdownList.add(combo);
            } else if (row.control() instanceof TranslucentTextField field) {
                textFields.add(field);
            }
            this.hoverParts.put(row.field(), List.of(this.labels.get(row.field()), row.control()));
        }
        controls.add(this.nameEditBox);
        controls.add(this.initialsEditBox);
        if (displayName) {
            controls.add(this.displayNameEditBox);
        }
        controls.add(this.iconPicker.menu());
        controls.add(this.iconPicker.clearButton());
        controls.add(this.colorPickerButton);
        controls.add(this.colorEditBox);
        controls.add(this.globalToggle);
        controls.add(this.xEditBox);
        controls.add(this.yEditBox);
        controls.add(this.zEditBox);
        controls.add(this.yawEditBox);
        controls.add(this.keywordsEditBox);
        controls.add(this.descriptionEditBox);
        dropdownList.add(this.iconPicker.menu());
        textFields.addAll(List.of(this.nameEditBox, this.initialsEditBox, this.xEditBox, this.yEditBox,
                this.zEditBox, this.yawEditBox));
        if (displayName) {
            textFields.add(this.displayNameEditBox);
        }
        this.fieldControls = List.copyOf(controls);
        this.dropdowns = List.copyOf(dropdownList);
        this.suggestionFields = List.copyOf(textFields);
        List<AbstractWidget> order = new ArrayList<>(controls);
        order.addAll(this.buttons);
        this.tabOrder = List.copyOf(order);

        this.hoverParts.put(FormField.NAME, List.of(this.labels.get(FormField.NAME), this.nameEditBox));
        this.hoverParts.put(FormField.INITIALS, List.of(this.labels.get(FormField.INITIALS), this.initialsEditBox));
        if (displayName) {
            this.hoverParts.put(FormField.DISPLAY_NAME, List.of(this.labels.get(FormField.DISPLAY_NAME), this.displayNameEditBox));
        }
        // The remove button has its own tooltip, so it isn't part of the Icon field's area.
        this.hoverParts.put(FormField.ICON, List.of(this.labels.get(FormField.ICON), this.iconPreview, this.iconPicker.menu()));
        this.hoverParts.put(FormField.COLOR, List.of(this.labels.get(FormField.COLOR), this.colorPickerButton, this.colorEditBox));
        this.hoverParts.put(FormField.VISIBILITY, List.of(this.labels.get(FormField.VISIBILITY), this.globalToggle));
        this.hoverParts.put(FormField.POSITION, List.of(this.labels.get(FormField.POSITION), this.xLabel, this.xEditBox,
                this.yLabel, this.yEditBox, this.zLabel, this.zEditBox));
        this.hoverParts.put(FormField.YAW, List.of(this.labels.get(FormField.YAW), this.yawEditBox));
        this.hoverParts.put(FormField.KEYWORDS, List.of(this.labels.get(FormField.KEYWORDS), this.keywordsEditBox));
        this.hoverParts.put(FormField.DESCRIPTION, List.of(this.labels.get(FormField.DESCRIPTION), this.descriptionEditBox));
        this.built = true;
    }

    @Override
    protected void init() {
        super.init();
        if (!this.built) {
            this.buildForm();
        }
        for (AbstractWidget widget : this.tabOrder) {
            this.addRenderableWidget(widget);
        }
        this.addRenderableWidget(this.swatchWidget);
        this.formReady = true;
        this.refreshControlStates();
        this.layoutForm();
        if (!this.focusChosen) {
            this.focusChosen = true;
            GuiEventListener focus = this.initialFocus();
            if (focus != null) {
                this.setInitialFocus(focus);
            }
        }
    }

    //? if >= 1.20.5 {
    /** The form chooses its own first focus in {@code init}; vanilla's would take the first Tab stop after a keyboard press. */
    @Override
    void pickInitialFocus() {
    }
    //?}

    /** A resize keeps the widgets, so their values, focus, status and pending request stay. */
    @Override
    protected void repositionElements() {
        this.layoutForm();
    }

    @Override
    int getContentWidth() {
        return this.arrangement == null ? 0 : this.arrangement.panelWidth();
    }

    @Override
    int getContentHeight() {
        return this.arrangement == null ? 0 : this.arrangement.groupHeight();
    }

    // ------------------------------------------------------------------ layout

    /** A row or divider that knows how tall it is and how to place its parts once the layout says where. */
    private record PanelItem(WaypointFormLayout.Item size, RowPlacement placement) {
    }

    private interface RowPlacement {
        void place(Arrangement layout, int top, int height);
    }

    private void layoutForm() {
        if (!this.built) {
            return;
        }
        int axisLetter = this.axisLetterWidth();
        ScalableText initialsLabel = this.labels.get(FormField.INITIALS);
        ScalableText visibilityLabel = this.labels.get(FormField.VISIBILITY);
        ScalableText yawLabel = this.labels.get(FormField.YAW);
        int colorGroup = VisualPositioning.getVisualWidth(this.colorPickerButton)
                + WaypointFormLayout.CONTROL_GAP + VisualPositioning.getVisualWidth(this.colorEditBox);
        List<FormField> leftFields = this.leftColumnFields();
        int widest = 0;
        for (FormField field : leftFields) {
            widest = Math.max(widest, font.width(Component.translatable(field.labelKey())));
        }
        Columns columns = WaypointFormLayout.columns(this.width, widest, WaypointFormLayout.minimumControlWidth(
                axisLetter, yawLabel.getWidth(), colorGroup, visibilityLabel.getWidth()));
        for (FormField field : leftFields) {
            this.labels.get(field).setMaxWidth(columns.labelTextWidth());
        }

        List<PanelItem> items = new ArrayList<>();
        for (LeadingRow row : this.leading) {
            items.add(this.stretchedRow(row.field(), row.control(), row.setWidth(), columns));
        }
        if (!this.leading.isEmpty()) {
            items.add(this.dividerItem());
        }
        items.add(this.nameRow(columns, initialsLabel));
        if (this.hasDisplayNameRow()) {
            items.add(this.stretchedRow(FormField.DISPLAY_NAME, this.displayNameEditBox, this.displayNameEditBox::setWidth, columns));
        }
        items.add(this.iconRow(columns));
        items.add(this.colorRow(columns, visibilityLabel));
        items.add(this.positionRow(columns, axisLetter, yawLabel));
        items.add(this.dividerItem());
        items.add(this.stretchedRow(FormField.KEYWORDS, this.keywordsEditBox, this.keywordsEditBox::setWidth, columns));
        items.add(this.stretchedRow(FormField.DESCRIPTION, this.descriptionEditBox, this.descriptionEditBox::setWidth, columns));

        this.titleText.setText(cutToWidth(font, this.getTitle(), (int) (columns.panelWidth() / TITLE_SCALE)));
        int headerHeight = this.titleText.getHeight();
        if (this.subtitleText != null) {
            this.subtitleText.setText(cutToWidth(font, this.subtitle(), columns.panelWidth()));
            headerHeight += WaypointFormLayout.TITLE_SUBTITLE_GAP + this.subtitleText.getHeight();
        }
        int[] buttonWidths = new int[this.buttons.size()];
        int buttonHeight = 0;
        for (int i = 0; i < buttonWidths.length; i++) {
            buttonWidths[i] = VisualPositioning.getVisualWidth(this.buttons.get(i));
            buttonHeight = Math.max(buttonHeight, VisualPositioning.getVisualHeight(this.buttons.get(i)));
        }
        int buttonsWidth = WaypointFormLayout.buttonsWidth(buttonWidths);
        this.statusText.setMaxWidth(WaypointFormLayout.statusWidth(columns.panelWidth(), buttonsWidth));
        int statusHeight = this.hasStatus ? this.statusText.getHeight() : 0;

        List<WaypointFormLayout.Item> sizes = new ArrayList<>();
        for (PanelItem item : items) {
            sizes.add(item.size());
        }
        Arrangement layout = WaypointFormLayout.arrange(this.width, this.height, columns, headerHeight, sizes,
                buttonsWidth, buttonHeight, statusHeight);
        this.arrangement = layout;

        List<Renderable> parts = new ArrayList<>();
        parts.add(this.titleText);
        this.titleText.setPosition(layout.panelX(), layout.groupTop());
        if (this.subtitleText != null) {
            this.subtitleText.setPosition(layout.panelX(),
                    layout.groupTop() + this.titleText.getHeight() + WaypointFormLayout.TITLE_SUBTITLE_GAP);
            parts.add(this.subtitleText);
        }
        for (int i = 0; i < items.size(); i++) {
            items.get(i).placement().place(layout, layout.itemTops().get(i), sizes.get(i).height());
        }
        for (FormField field : leftFields) {
            parts.add(this.labels.get(field));
        }
        parts.add(initialsLabel);
        parts.add(visibilityLabel);
        parts.add(yawLabel);
        parts.add(this.xLabel);
        parts.add(this.yLabel);
        parts.add(this.zLabel);
        int dividerWidth = columns.labelWidth() + columns.controlWidth();
        for (int i = 0; i < items.size(); i++) {
            if (sizes.get(i).isDivider()) {
                parts.add(new SeparatorWidget(layout.labelX(), layout.itemTops().get(i), dividerWidth,
                        WaypointFormLayout.DIVIDER_HEIGHT, BORDER));
            }
        }
        this.staticParts = List.copyOf(parts);

        WidgetPack footer = new WidgetPack(layout.panelX(), layout.buttonsY(), layout.panelWidth(),
                layout.buttonsHeight(), Orientation.HORIZONTAL);
        footer.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        for (int i = this.buttons.size() - 1; i >= 0; i--) {
            footer.addChild(this.buttons.get(i), Direction.REVERSE);
            if (i > 0) {
                footer.addChild(SpacerElement.width(WaypointFormLayout.FOOTER_BUTTON_GAP), Direction.REVERSE);
            }
        }
        this.statusText.setPosition(layout.panelX(), layout.statusY());
        this.swatchWidget.setPosition(
                layout.panelX() + centered(layout.panelWidth(), this.swatchWidget.getWidth()),
                layout.panelY() + centered(layout.panelHeight(), this.swatchWidget.getHeight())
        );
    }

    /** The fields whose labels are in the label column, top to bottom. */
    private List<FormField> leftColumnFields() {
        List<FormField> fields = new ArrayList<>();
        for (LeadingRow row : this.leading) {
            fields.add(row.field());
        }
        fields.add(FormField.NAME);
        if (this.hasDisplayNameRow()) {
            fields.add(FormField.DISPLAY_NAME);
        }
        fields.add(FormField.ICON);
        fields.add(FormField.COLOR);
        fields.add(FormField.POSITION);
        fields.add(FormField.KEYWORDS);
        fields.add(FormField.DESCRIPTION);
        return fields;
    }

    private int axisLetterWidth() {
        int width = 0;
        for (char letter : AXIS_LETTERS.toCharArray()) {
            width = Math.max(width, font.width(String.valueOf(letter)));
        }
        return width;
    }

    private PanelItem stretchedRow(FormField field, AbstractWidget control, IntConsumer setWidth, Columns columns) {
        setWidth.accept(columns.controlWidth());
        ScalableText label = this.labels.get(field);
        int height = Math.max(label.getHeight(), VisualPositioning.getVisualHeight(control));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(control, layout.controlX(), top, rowHeight);
        });
    }

    private PanelItem dividerItem() {
        return new PanelItem(WaypointFormLayout.Item.divider(), (layout, top, height) -> {
        });
    }

    private PanelItem nameRow(Columns columns, ScalableText initialsLabel) {
        int rightGroup = initialsLabel.getWidth() + WaypointFormLayout.INLINE_GAP + WaypointFormLayout.SMALL_FIELD_WIDTH;
        this.nameEditBox.setWidth(WaypointFormLayout.stretchedWidth(columns.controlWidth(), rightGroup));
        this.initialsEditBox.setWidth(WaypointFormLayout.SMALL_FIELD_WIDTH);
        ScalableText label = this.labels.get(FormField.NAME);
        int height = Math.max(label.getHeight(), VisualPositioning.getVisualHeight(this.nameEditBox));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(this.nameEditBox, layout.controlX(), top, rowHeight);
            int initialsX = layout.controlX() + columns.controlWidth() - WaypointFormLayout.SMALL_FIELD_WIDTH;
            placeInRow(this.initialsEditBox, initialsX, top, rowHeight);
            placeInRow(initialsLabel, initialsX - WaypointFormLayout.INLINE_GAP - initialsLabel.getWidth(), top, rowHeight);
        });
    }

    private PanelItem iconRow(Columns columns) {
        ComboBoxWidget menu = this.iconPicker.menu();
        AbstractWidget clear = this.iconPicker.clearButton();
        int clearWidth = VisualPositioning.getVisualWidth(clear);
        menu.setWidth(WaypointFormLayout.iconDropdownWidth(columns.controlWidth(), PREVIEW_SIZE, clearWidth));
        ScalableText label = this.labels.get(FormField.ICON);
        int height = Math.max(label.getHeight(), Math.max(PREVIEW_SIZE,
                Math.max(VisualPositioning.getVisualHeight(menu), VisualPositioning.getVisualHeight(clear))));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(this.iconPreview, layout.controlX(), top, rowHeight);
            placeInRow(menu, layout.controlX() + PREVIEW_SIZE + WaypointFormLayout.CONTROL_GAP, top, rowHeight);
            placeInRow(clear, layout.controlX() + columns.controlWidth() - clearWidth, top, rowHeight);
        });
    }

    private PanelItem colorRow(Columns columns, ScalableText visibilityLabel) {
        ScalableText label = this.labels.get(FormField.COLOR);
        int height = Math.max(label.getHeight(), Math.max(VisualPositioning.getVisualHeight(this.colorPickerButton),
                Math.max(VisualPositioning.getVisualHeight(this.colorEditBox), VisualPositioning.getVisualHeight(this.globalToggle))));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            placeInRow(this.colorPickerButton, layout.controlX(), top, rowHeight);
            placeInRow(this.colorEditBox, layout.controlX() + VisualPositioning.getVisualWidth(this.colorPickerButton)
                    + WaypointFormLayout.CONTROL_GAP, top, rowHeight);
            int toggleX = layout.controlX() + columns.controlWidth() - VisualPositioning.getVisualWidth(this.globalToggle);
            placeInRow(this.globalToggle, toggleX, top, rowHeight);
            placeInRow(visibilityLabel, toggleX - WaypointFormLayout.INLINE_GAP - visibilityLabel.getWidth(), top, rowHeight);
        });
    }

    private PanelItem positionRow(Columns columns, int axisLetter, ScalableText yawLabel) {
        int fieldWidth = WaypointFormLayout.coordinateFieldWidth(columns.controlWidth(), axisLetter, yawLabel.getWidth());
        this.xEditBox.setWidth(fieldWidth);
        this.yEditBox.setWidth(fieldWidth);
        this.zEditBox.setWidth(fieldWidth);
        this.yawEditBox.setWidth(WaypointFormLayout.SMALL_FIELD_WIDTH);
        ScalableText label = this.labels.get(FormField.POSITION);
        int height = Math.max(label.getHeight(), VisualPositioning.getVisualHeight(this.xEditBox));
        return new PanelItem(WaypointFormLayout.Item.row(height), (layout, top, rowHeight) -> {
            placeInRow(label, layout.labelX(), top, rowHeight);
            ScalableText[] letters = {this.xLabel, this.yLabel, this.zLabel};
            CoordinateField[] fields = {this.xEditBox, this.yEditBox, this.zEditBox};
            int x = layout.controlX();
            for (int i = 0; i < letters.length; i++) {
                placeInRow(letters[i], x, top, rowHeight);
                x += axisLetter + WaypointFormLayout.AXIS_GAP;
                placeInRow(fields[i], x, top, rowHeight);
                x += fieldWidth + WaypointFormLayout.FIELD_GAP;
            }
            int yawX = layout.controlX() + columns.controlWidth() - WaypointFormLayout.SMALL_FIELD_WIDTH;
            placeInRow(this.yawEditBox, yawX, top, rowHeight);
            placeInRow(yawLabel, yawX - WaypointFormLayout.INLINE_GAP - yawLabel.getWidth(), top, rowHeight);
        });
    }

    /** Moves an element so the top-left corner of its outline is at ({@code x}, {@code y}). */
    private static void placeOutline(LayoutElement element, int x, int y) {
        element.setPosition(x, y);
        // Padded widgets anchor their content, not their outline; move them by the difference.
        element.setPosition(x + (x - VisualPositioning.getVisualX(element)), y + (y - VisualPositioning.getVisualY(element)));
    }

    /** Places an element at {@code x}, centered vertically on a row. */
    private static void placeInRow(LayoutElement element, int x, int rowTop, int rowHeight) {
        placeOutline(element, x, rowTop + ((rowHeight - VisualPositioning.getVisualHeight(element)) >> 1));
    }

    /** {@code text} cut to fit {@code width}, ending in an ellipsis when it doesn't fit. */
    static Component cutToWidth(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        FormattedText head = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        MutableComponent cut = Component.empty();
        head.visit((style, contents) -> {
            cut.append(Component.literal(contents).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return cut.append(ELLIPSIS);
    }

    // ------------------------------------------------------------------ state

    /**
     * Sets every control's {@code active} flag and the status message from the current state: the color
     * picker, a pending request, the checks and whether Edit's form differs from the saved waypoint.
     */
    protected final void refreshControlStates() {
        if (!this.formReady) {
            return;
        }
        boolean modal = this.swatchWidget.visible;
        Component pending = this.pendingMessage();
        boolean locked = modal || pending != null;
        WaypointFormCheck.Problem problem = WaypointFormCheck.firstProblem(this.checkInput(), CLIENT_DATA);
        boolean changed = this.hasChanges();
        boolean canSubmit = !locked && !(problem != null && problem.blocks()) && changed;

        for (AbstractWidget control : this.fieldControls) {
            control.active = !locked;
        }
        this.iconPicker.clearButton().active = !locked && this.iconPicker.getSelectedIcon() != null;
        this.refreshButtons(modal, locked, canSubmit, changed);

        WaypointFormCheck.Field errorField = problem != null && problem.kind() == WaypointFormCheck.Kind.ERROR
                ? problem.field() : WaypointFormCheck.Field.NONE;
        this.nameEditBox.setInvalid(this.marks(WaypointFormCheck.Field.NAME, errorField));
        this.displayNameEditBox.setInvalid(this.marks(WaypointFormCheck.Field.DISPLAY_NAME, errorField));
        this.keywordsEditBox.setInvalid(this.marks(WaypointFormCheck.Field.KEYWORDS, errorField));
        this.descriptionEditBox.setInvalid(this.marks(WaypointFormCheck.Field.DESCRIPTION, errorField));

        if (pending != null) {
            this.showStatus(pending, TEXT_MUTED);
        } else if (this.resultMessage != null) {
            this.showStatus(this.resultMessage, DANGER);
        } else if (problem != null) {
            this.showStatus(this.messageFor(problem), problem.kind() == WaypointFormCheck.Kind.ERROR ? DANGER : TEXT_MUTED);
        } else {
            this.showStatus(null, TEXT_MUTED);
        }
    }

    private boolean marks(WaypointFormCheck.Field field, WaypointFormCheck.Field errorField) {
        return field == errorField || field == this.resultField;
    }

    /** Whether the client's waypoint data has a waypoint with exactly this name in that list. */
    protected static boolean hasWaypoint(String dimension, String list, String name) {
        return CLIENT_DATA.hasWaypoint(dimension, list, name);
    }

    /** Shows a message from the server or a failed send in the footer, marking a field when it names one. */
    protected final void showResult(Component message, WaypointFormCheck.Field field) {
        this.resultMessage = message;
        this.resultField = field;
        this.refreshControlStates();
    }

    /** Editing any field removes a message from a server result or a timeout; the checks decide what shows next. */
    protected final void onFormEdited() {
        if (!this.formReady) {
            return;
        }
        this.resultMessage = null;
        this.resultField = WaypointFormCheck.Field.NONE;
        this.refreshControlStates();
    }

    /** Runs the checks again and, when nothing blocks, hands over to the screen's submit action. */
    protected final void submitForm() {
        this.refreshControlStates();
        if (!this.primaryButton().active) {
            return;
        }
        this.submit();
        this.refreshControlStates();
    }

    private Component messageFor(WaypointFormCheck.Problem problem) {
        Object[] arguments = problem.arguments().toArray();
        if (problem.message() == WaypointFormCheck.Message.NAME_TAKEN) {
            arguments[0] = parseFormattedText((String) arguments[0]);
        }
        return Component.translatable(problem.message().translationKey(), arguments);
    }

    /** Changes the footer message; the layout runs again because a wrapped message changes the footer's height. */
    private void showStatus(@Nullable Component message, WidgetThemeVariable color) {
        this.statusText.setColor(color);
        if (Objects.equals(message, this.shownStatus)) {
            return;
        }
        this.shownStatus = message;
        this.hasStatus = message != null;
        if (message != null) {
            this.statusText.setText(message);
        }
        this.layoutForm();
    }

    @Override
    public void tick() {
        super.tick();
        this.onTick();
        this.refreshControlStates();
    }

    // ------------------------------------------------------------------ the color picker

    private void openSwatch() {
        this.swatchWidget.visible = true;
        this.swatchWidget.setColor(this.colorPickerButton.getColor());
        this.refreshControlStates();
        this.setFocused(this.swatchWidget);
    }

    protected void closeSwatch() {
        this.swatchWidget.visible = false;
        this.refreshControlStates();
        this.setFocused(this.colorPickerButton);
    }

    // ------------------------------------------------------------------ coordinates

    protected WaypointPos resolveCoordinateFields() {
        PlayerCoordinates playerCoordinates = getPlayerCoordinates();
        return CoordinateInputParser.resolve(
                this.xEditBox.getValue(),
                this.yEditBox.getValue(),
                this.zEditBox.getValue(),
                playerCoordinates.pos(),
                this.coordinateDefaultPos,
                playerCoordinates.pitch(),
                playerCoordinates.yaw()
        );
    }

    protected List<String> getWaypointInitialsSuggestions() {
        return getInitialsCandidatesFromName(plainText(this.nameEditBox.getValue()));
    }

    private void configureCoordinateModeEnforcement() {
        this.xEditBox.setValueChangedCallback(this::coordinateEdited);
        this.yEditBox.setValueChangedCallback(this::coordinateEdited);
        this.zEditBox.setValueChangedCallback(this::coordinateEdited);
    }

    private void coordinateEdited(CoordinateField editedField) {
        this.enforceCoordinateMode(editedField);
        this.onFormEdited();
    }

    private void configureCoordinateSuggestions() {
        this.xEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.X));
        this.yEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.Y));
        this.zEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.Z));
        this.yawEditBox.setSuggestionsProvider(this::getYawSuggestions);
    }

    private List<String> getCoordinateSuggestions(CoordinateSuggestions.Axis axis) {
        return CoordinateSuggestions.forAxis(axis, getLookedAtBlockPos());
    }

    private List<String> getYawSuggestions() {
        return CoordinateSuggestions.forYaw(getPlayerCoordinates().yaw());
    }

    private void enforceCoordinateMode(CoordinateField editedField) {
        if (this.enforcingCoordinateMode) {
            return;
        }
        this.enforcingCoordinateMode = true;
        try {
            if (isLocalCoordinateField(editedField)) {
                setLocalIfNeeded(this.xEditBox);
                setLocalIfNeeded(this.yEditBox);
                setLocalIfNeeded(this.zEditBox);
                setCoordinateLabelsLocal(true);
            } else if (hasLocalCoordinateField()) {
                setDefaultAbsoluteIfLocal(this.xEditBox);
                setDefaultAbsoluteIfLocal(this.yEditBox);
                setDefaultAbsoluteIfLocal(this.zEditBox);
                setCoordinateLabelsLocal(false);
            } else {
                setCoordinateLabelsLocal(false);
            }
        } finally {
            this.enforcingCoordinateMode = false;
        }
    }

    private void setCoordinateLabelsLocal(boolean local) {
        this.xLabel.setText(local ? "R" : "X");
        this.yLabel.setText(local ? "U" : "Y");
        this.zLabel.setText(local ? "F" : "Z");
    }

    private boolean hasLocalCoordinateField() {
        return isLocalCoordinateField(this.xEditBox) || isLocalCoordinateField(this.yEditBox) || isLocalCoordinateField(this.zEditBox);
    }

    private boolean isLocalCoordinateField(CoordinateField field) {
        return CoordinateInputParser.isLocalCoordinateExpression(field.getValue());
    }

    private void setLocalIfNeeded(CoordinateField field) {
        if (!isLocalCoordinateField(field)) {
            field.setValue("^");
        }
    }

    private void setDefaultAbsoluteIfLocal(CoordinateField field) {
        if (isLocalCoordinateField(field)) {
            field.setValue(Integer.toString(getDefaultCoordinate(field)));
        }
    }

    private int getDefaultCoordinate(CoordinateField field) {
        if (field == this.xEditBox) {
            return this.coordinateDefaultPos.x();
        }
        if (field == this.yEditBox) {
            return this.coordinateDefaultPos.y();
        }
        return this.coordinateDefaultPos.z();
    }

    private PlayerCoordinates getPlayerCoordinates() {
        Minecraft minecraftClient = Minecraft.getInstance();
        Entity entity = minecraftClient.player != null ? minecraftClient.player : minecraftClient.getCameraEntity();
        if (entity == null) {
            return new PlayerCoordinates(this.coordinateDefaultPos, 0.0F, 0.0F);
        }
        BlockPos blockPos = entity.blockPosition();
        return new PlayerCoordinates(
                new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                entity.getXRot(),
                entity.getYRot()
        );
    }

    private @Nullable WaypointPos getLookedAtBlockPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }

        Vec3 start = mc.player.getEyePosition(1.0F);
        double reach;
        //? if >= 1.20.5 {
        reach = mc.player.blockInteractionRange();
        //?} else {
        /*reach = mc.gameMode == null ? 4.5D : mc.gameMode.getPickRange();
        *///?}
        Vec3 end = start.add(mc.player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos blockPos = hit.getBlockPos();
        if (mc.level.getBlockState(blockPos).isAir()) {
            return null;
        }
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    // ------------------------------------------------------------------ input

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (this.clickDropdown(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button())) {
            return true;
        }
        if (this.mouseClickedTextFieldSuggestion(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClicked);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.clickDropdown(mouseX, mouseY, button)) {
            return true;
        }
        if (this.mouseClickedTextFieldSuggestion(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    //? if >= 1.21.9 {
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.releaseDropdown(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }
    //?} else {
    /*@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.releaseDropdown(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    *///?}

    //? if <= 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (this.scrollDropdown(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollDropdown(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = this.getFocused();
        this.acceptMovementKeys(!(focused instanceof EditBox) && !(focused instanceof ComboBoxWidget));
        if (keyCode == InputConstants.KEY_ESCAPE && this.swatchWidget.visible) {
            this.closeSwatch();
            return true;
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && focused instanceof EditBox field && this.enterInTextField(field)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Enter in a text field picks the highlighted suggestion while a list is open, and otherwise sends
     * the form if Add or Save is active. Returns false when Enter isn't used, so the field sees it.
     */
    private boolean enterInTextField(EditBox field) {
        if (field instanceof SuggestingTextInput input && input.acceptHighlightedSuggestion()) {
            return true;
        }
        if (!this.primaryButton().active) {
            return false;
        }
        this.submitForm();
        return true;
    }

    private boolean clickDropdown(double mouseX, double mouseY, int button) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isMouseOver(mouseX, mouseY) && dropdown.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(dropdown);
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    this.setDragging(true);
                }
                return true;
            }
            dropdown.closeMenuIfOutside(mouseX, mouseY);
        }
        return false;
    }

    private boolean releaseDropdown(double mouseX, double mouseY, int button) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.mouseReleased(mouseX, mouseY, button)) {
                this.setDragging(false);
                return true;
            }
        }
        return false;
    }

    private boolean scrollDropdown(double mouseX, double mouseY, double verticalAmount) {
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isExpanded() && dropdown.mouseScrolled(mouseX, mouseY, 0, verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    private boolean mouseClickedTextFieldSuggestion(double mouseX, double mouseY) {
        GuiEventListener focused = this.getFocused();
        return focused instanceof TranslucentTextField textField && textField.mouseClickedSuggestion(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Arrangement layout = this.arrangement;
        if (layout == null) {
            return;
        }
        context.fill(layout.panelX(), layout.panelY(), layout.panelX() + layout.panelWidth(),
                layout.panelY() + layout.panelHeight(), getColor(PANEL_BACKGROUND));
        renderOutline(context, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), getColor(BORDER));
        for (Renderable part : this.staticParts) {
            part.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, delta);
        }
        for (AbstractWidget widget : this.tabOrder) {
            widget.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, delta);
        }
        if (this.hasStatus) {
            this.statusText.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, delta);
        }
        this.drawIconPreview(context);
        nextLayer(context);
        for (TranslucentTextField field : this.suggestionFields) {
            field.renderSuggestions(context, mouseX, mouseY);
        }
        previousLayer(context);
        this.iconPicker.menu().layoutPopup(this.height, 8);
        for (ComboBoxWidget dropdown : this.dropdowns) {
            dropdown.renderPopup(context, mouseX, mouseY, delta);
        }
        this.renderFieldTooltip(context, mouseX, mouseY);
        nextLayer(context);
        this.swatchWidget.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
        previousLayer(context);
    }

    private void drawIconPreview(GuiGraphicsExtractor context) {
        var resolvedIcon = this.iconPicker.preview();
        int previewX = this.iconPreview.getX();
        int previewY = this.iconPreview.getY();
        if (resolvedIcon.kind() == WaypointIconRenderer.Kind.INITIALS) {
            WaypointRowRenderer.initials(context, font, this.initialsEditBox.getValue(), previewX,
                    previewY + (PREVIEW_SIZE - font.lineHeight) / 2,
                    this.colorPickerButton.getColor(), WidgetThemeManager.getColor(TEXT_PRIMARY));
        } else {
            WaypointIconRenderer.drawForWaypoint(context, resolvedIcon, previewX, previewY, PREVIEW_SIZE,
                    this.colorPickerButton.getColor());
        }
    }

    /** After the pointer rests on a field's label or controls for 500 ms, shows that field's tooltip at the pointer. */
    private void renderFieldTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        FormField field = this.tooltipBlocked() ? null : this.fieldAt(mouseX, mouseY);
        long now = System.nanoTime();
        if (field != this.hoveredField) {
            this.hoveredField = field;
            this.hoveredSince = now;
            return;
        }
        if (field == null || now - this.hoveredSince < TOOLTIP_DELAY_NANOS) {
            return;
        }
        scheduleTooltipAtPointer(context, Tooltip.create(this.tooltipText(field)).toCharSequence(Minecraft.getInstance()),
                mouseX, mouseY);
    }

    /** No field tooltip shows while a popup or the color picker is open, or while a request is pending. */
    private boolean tooltipBlocked() {
        if (this.swatchWidget.visible || this.pendingMessage() != null) {
            return true;
        }
        for (ComboBoxWidget dropdown : this.dropdowns) {
            if (dropdown.isExpanded() || dropdown.isSuggestionListOpen()) {
                return true;
            }
        }
        for (TranslucentTextField field : this.suggestionFields) {
            if (field.isSuggestionListOpen()) {
                return true;
            }
        }
        return false;
    }

    private @Nullable FormField fieldAt(int mouseX, int mouseY) {
        for (Map.Entry<FormField, List<LayoutElement>> entry : this.hoverParts.entrySet()) {
            for (LayoutElement part : entry.getValue()) {
                int left = VisualPositioning.getVisualX(part);
                int top = VisualPositioning.getVisualY(part);
                if (mouseX >= left && mouseX < left + VisualPositioning.getVisualWidth(part)
                        && mouseY >= top && mouseY < top + VisualPositioning.getVisualHeight(part)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Component tooltipText(FormField field) {
        if (field == FormField.VISIBILITY) {
            return Component.translatable(field.tooltipKey(), WaypointClientMod.getClientConfig().getViewDistance());
        }
        return Component.translatable(field.tooltipKey());
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, this.previousScreen);
    }

    private record PlayerCoordinates(WaypointPos pos, float pitch, float yaw) {
    }

    /** The client's waypoint data for the checks: the synced files, or the integrated server's in singleplayer. */
    private static final class ClientData implements WaypointFormCheck.Lookup {
        @Override
        public boolean listExists(String dimension, String list) {
            return list(dimension, list) != null;
        }

        @Override
        public boolean hasWaypoint(String dimension, String list, String name) {
            WaypointList found = list(dimension, list);
            return found != null && found.getWaypointByName(name) != null;
        }

        @Override
        public String listDisplayName(String dimension, String list) {
            WaypointList found = list(dimension, list);
            return found == null ? list : found.displayName();
        }

        private static @Nullable WaypointList list(String dimension, String list) {
            WaypointFileManager manager = WaypointClientMod.getInstance().getWaypointFileManager(dimension);
            return manager == null ? null : manager.getWaypointListByName(list);
        }
    }
}
```

- [ ] **Step 4: Replace `WaypointAddScreen.java`**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointAddScreen.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.getAvailableDimensionNames;
import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.mergeDimensionNames;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_NAME_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;
import static _959.server_waypoint.util.StringCommandBuilder.addCmd;

/**
 * Adds a waypoint with {@code /wp add}. There is no reply to that command, so after sending it the
 * form locks and waits for the waypoint to show up in the synced data, then closes.
 */
public class WaypointAddScreen extends AbstractWaypointPropertiesScreen {
    private final ComboBoxWidget dimensionField;
    private final TranslucentTextField listNameField;
    private final TranslucentButton addButton;
    private final PendingAdd pendingAdd = new PendingAdd();

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName) {
        this(previousScreen, dimensionName, listName, null);
    }

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName, WaypointPos defaultPos) {
        super(previousScreen, Component.translatable("waypoint.add.screen.title"), dimensionName, listName, null);
        List<String> dimensions = mergeDimensionNames(
                WaypointClientMod.getAllAvailableDimensionNames(),
                List.of(dimensionName)
        );
        this.dimensionField = new ComboBoxWidget(0, 0, 155, Component.translatable("waypoint.form.dimension"), this.font,
                dimensions, dimensionName, value -> this.onFormEdited());
        this.dimensionField.setRenderPopupSeparately(true);
        this.listNameField = new TranslucentTextField(0, 0, 90, Component.translatable("waypoint.form.list"), this.font);
        this.listNameField.setMaxLength(MAX_NAME_LENGTH);
        this.listNameField.setValue(listName);
        this.listNameField.setResponder(value -> this.onFormEdited());
        this.addButton = TranslucentButton.fitted(Component.translatable("waypoint.add.button"), this::submitForm);
        this.configureSuggestions();
        this.setDefaultPos(defaultPos == null ? this.getCurrentDefaultPos() : defaultPos);
        this.refreshDimensionChoices();
    }

    private WaypointPos getCurrentDefaultPos() {
        Minecraft minecraftClient = Minecraft.getInstance();
        //? if >= 1.21.11 {
        BlockPos defaultPos = MinecraftClientHelper.getMainCamera(minecraftClient).blockPosition();
        //?} else {
        /*BlockPos defaultPos = minecraftClient.gameRenderer.getMainCamera().getBlockPosition();
        *///?}
        if (minecraftClient.getCameraEntity() != null) {
            defaultPos = minecraftClient.getCameraEntity().blockPosition();
        }
        return new WaypointPos(defaultPos.getX(), defaultPos.getY(), defaultPos.getZ());
    }

    private void setDefaultPos(WaypointPos defaultPos) {
        int x = defaultPos.x();
        int y = defaultPos.y();
        int z = defaultPos.z();
        this.coordinateDefaultPos = defaultPos;
        this.xEditBox.setDefaultValue(x);
        this.yEditBox.setDefaultValue(y);
        this.zEditBox.setDefaultValue(z);
        this.xEditBox.setValue(Integer.toString(x));
        this.yEditBox.setValue(Integer.toString(y));
        this.zEditBox.setValue(Integer.toString(z));
    }

    private void refreshDimensionChoices() {
        getAvailableDimensionNames().thenAccept(dimensions -> this.dimensionField.setValues(
                mergeDimensionNames(dimensions, List.of(this.dimensionName))
        ));
    }

    private void configureSuggestions() {
        this.listNameField.setSuggestionsProvider(() -> WaypointClientMod.getAllWaypointListNames(this.dimensionField.getValue()));
        this.nameEditBox.setSuggestionsProvider(() -> WaypointClientMod.getAllWaypointNames(this.dimensionField.getValue(), this.listNameField.getValue()));
    }

    @Override
    protected List<LeadingRow> leadingRows() {
        return List.of(
                new LeadingRow(FormField.DIMENSION, this.dimensionField, this.dimensionField::setWidth),
                new LeadingRow(FormField.LIST, this.listNameField, this.listNameField::setWidth)
        );
    }

    @Override
    protected @Nullable Component subtitle() {
        return null;
    }

    @Override
    protected boolean hasDisplayNameRow() {
        return false;
    }

    @Override
    protected List<TranslucentButton> footerButtons() {
        return List.of(this.cancelButton, this.addButton);
    }

    @Override
    protected TranslucentButton primaryButton() {
        return this.addButton;
    }

    /** The first empty field the player must fill: the list when it's empty, otherwise the name. */
    @Override
    protected @Nullable GuiEventListener initialFocus() {
        return this.listNameField.getValue().isEmpty() ? this.listNameField : this.nameEditBox;
    }

    @Override
    protected WaypointFormCheck.Input checkInput() {
        return new WaypointFormCheck.Input(
                true,
                this.dimensionField.getValue(),
                this.listNameField.getValue(),
                this.nameEditBox.getValue(),
                "",
                this.keywordsEditBox.getValue(),
                this.descriptionEditBox.getValue(),
                null
        );
    }

    @Override
    protected void submit() {
        String dimension = this.dimensionField.getValue();
        String list = this.listNameField.getValue();
        String name = this.nameEditBox.getValue();
        if (!sendCommand(addCmd(dimension, list, this.toWaypoint(), false))) {
            this.showResult(Component.translatable("waypoint.form.status.send_failed"), WaypointFormCheck.Field.NONE);
            return;
        }
        this.pendingAdd.begin(dimension, list, name, System.nanoTime());
    }

    private SimpleWaypoint toWaypoint() {
        return new SimpleWaypoint(
                this.nameEditBox.getValue(),
                this.nameEditBox.getValue(),
                this.initialsEditBox.getValue(),
                this.resolveCoordinateFields(),
                this.colorPickerButton.getColor() & 0xFFFFFF,
                this.yawEditBox.getIntValue(),
                this.globalToggle.getState(),
                parseKeywords(this.keywordsEditBox.getValue()),
                this.descriptionEditBox.getValue(),
                this.iconPicker.getSelectedIcon()
        );
    }

    @Override
    protected @Nullable Component pendingMessage() {
        return this.pendingAdd.pending() ? Component.translatable("waypoint.form.status.adding") : null;
    }

    @Override
    protected void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed) {
        this.cancelButton.active = !modal;
        this.addButton.active = canSubmit;
    }

    /** Closes once the waypoint shows up in the synced data, and unlocks with a message after 5 seconds. */
    @Override
    protected void onTick() {
        if (!this.pendingAdd.pending()) {
            return;
        }
        if (hasWaypoint(this.pendingAdd.dimension(), this.pendingAdd.list(), this.pendingAdd.name())) {
            this.pendingAdd.clear();
            this.onClose();
        } else if (this.pendingAdd.expire(System.nanoTime())) {
            this.showResult(Component.translatable("waypoint.form.status.add_timeout"), WaypointFormCheck.Field.NONE);
        }
    }
}
```

- [ ] **Step 5: Replace `WaypointEditScreen.java`**

`mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointEditScreen.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.util.ColorHelper;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.message.WaypointEditRequestMessage;
import _959.server_waypoint.core.network.message.WaypointEditResultMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static _959.server_waypoint.common.util.TextHelper.parseFormattedText;
import static _959.server_waypoint.text.FormattedTextHelper.parseKeywords;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;

/**
 * Edits a waypoint with one atomic request that carries only the fields that changed, and keeps the
 * entered values until a matching server result accepts the edit.
 */
public class WaypointEditScreen extends AbstractWaypointPropertiesScreen {
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();

    private final String listDisplayName;
    private final int expectedListRevision;
    private final TranslucentButton saveButton;
    private final TranslucentButton resetButton;
    private final EditResponseDeadline responseDeadline = new EditResponseDeadline();

    public WaypointEditScreen(
            Screen previousScreen,
            String dimensionName,
            String listName,
            SimpleWaypoint waypoint
    ) {
        this(previousScreen, dimensionName, listName, listName, waypoint);
    }

    public WaypointEditScreen(
            Screen previousScreen,
            String dimensionName,
            String listName,
            String listDisplayName,
            SimpleWaypoint waypoint
    ) {
        super(
                previousScreen,
                Component.translatable("waypoint.edit.screen.title",
                        parseFormattedText(WaypointFormPatch.Saved.of(waypoint).titleName())),
                dimensionName,
                listName,
                waypoint
        );
        this.listDisplayName = listDisplayName;
        WaypointFileManager fileManager = WaypointClientMod.getInstance()
                .getWaypointFileManager(dimensionName);
        WaypointList waypointList = fileManager == null
                ? null
                : fileManager.getWaypointListByName(listName);
        this.expectedListRevision = waypointList == null ? 0 : waypointList.getSyncNum();
        this.saveButton = TranslucentButton.fitted(Component.translatable("waypoint.save.button"), this::submitForm);
        this.resetButton = TranslucentButton.fitted(Component.translatable("waypoint.reset.button"), this::resetProperties);
        this.nameEditBox.setSuggestionsProvider(
                () -> WaypointClientMod.getAllWaypointNames(this.dimensionName, this.listName)
        );
    }

    public static void handleResult(WaypointEditResultMessage result) {
        //? if >=26.2 {
        /*Screen screen = Minecraft.getInstance().gui.screen();
        *///?} else {
        Screen screen = Minecraft.getInstance().screen;
        //?}
        if (screen instanceof WaypointEditScreen editScreen) {
            editScreen.acceptResult(result);
        }
    }

    @Override
    protected List<LeadingRow> leadingRows() {
        return List.of();
    }

    /** "In <list> · <dimension>", with the dimension in the color the details panel uses. */
    @Override
    protected @Nullable Component subtitle() {
        int dimensionColor = ColorHelper.scaleRgb(
                0xFF000000 | getDimensionColor(this.dimensionName).value(),
                0.8F
        ) & 0xFFFFFF;
        return Component.translatable(
                "waypoint.edit.screen.location",
                parseFormattedText(this.listDisplayName),
                Component.literal(this.dimensionName).withStyle(style -> style.withColor(dimensionColor))
        );
    }

    @Override
    protected boolean hasDisplayNameRow() {
        return true;
    }

    @Override
    protected List<TranslucentButton> footerButtons() {
        return List.of(this.resetButton, this.cancelButton, this.saveButton);
    }

    @Override
    protected TranslucentButton primaryButton() {
        return this.saveButton;
    }

    @Override
    protected WaypointFormCheck.Input checkInput() {
        return new WaypointFormCheck.Input(
                false,
                this.dimensionName,
                this.listName,
                this.nameEditBox.getValue(),
                this.displayNameEditBox.getValue(),
                this.keywordsEditBox.getValue(),
                this.descriptionEditBox.getValue(),
                this.savedWaypoint().name()
        );
    }

    /** Whether the form would change the waypoint: its patch has a field to set or clear. */
    @Override
    protected boolean hasChanges() {
        return WaypointFormPatch.changesAnything(this.buildPatch());
    }

    private WaypointFormPatch.Saved savedWaypoint() {
        return Objects.requireNonNull(this.saved);
    }

    private WaypointPatch buildPatch() {
        return WaypointFormPatch.build(this.savedWaypoint(), new WaypointFormPatch.Values(
                this.nameEditBox.getValue(),
                this.displayNameEditBox.getValue(),
                this.initialsEditBox.getValue(),
                this.resolveCoordinateFields(),
                this.colorPickerButton.getColor() & 0xFFFFFF,
                this.yawEditBox.getIntValue(),
                this.globalToggle.getState(),
                parseKeywords(this.keywordsEditBox.getValue()),
                this.descriptionEditBox.getValue(),
                this.iconPicker.getSelectedIcon()
        ));
    }

    @Override
    protected void submit() {
        WaypointPatch patch = this.buildPatch();
        long requestId = NEXT_REQUEST_ID.incrementAndGet();
        this.responseDeadline.begin(requestId, System.nanoTime());
        boolean sent = WaypointClientMod.getInstance().sendChunkedMessageToServer(new WaypointEditRequestMessage(
                requestId,
                this.dimensionName,
                this.listName,
                this.savedWaypoint().name(),
                this.expectedListRevision,
                patch
        ));
        if (!sent) {
            this.responseDeadline.clear();
            this.showResult(Component.translatable("waypoint.form.status.send_failed"), WaypointFormCheck.Field.NONE);
        }
    }

    @Override
    protected @Nullable Component pendingMessage() {
        return this.responseDeadline.pending() ? Component.translatable("waypoint.form.status.saving") : null;
    }

    @Override
    protected void refreshButtons(boolean modal, boolean locked, boolean canSubmit, boolean changed) {
        this.cancelButton.active = !modal;
        this.saveButton.active = canSubmit;
        this.resetButton.active = !locked && changed;
    }

    @Override
    protected void onTick() {
        if (this.responseDeadline.expire(System.nanoTime())) {
            this.showResult(Component.translatable("waypoint.edit.error.response_timeout"), WaypointFormCheck.Field.NONE);
        }
    }

    private void acceptResult(WaypointEditResultMessage result) {
        if (!this.responseDeadline.clearIfMatches(result.requestId())) {
            return;
        }
        if (result.status() == EditResultStatus.SUCCESS) {
            this.onClose();
            return;
        }
        Component error = Component.translatable(
                "waypoint.edit.error." + result.status().name().toLowerCase(Locale.ROOT)
        );
        this.showResult(error, fieldOf(result.status()));
    }

    /** The field a rejected edit points at. */
    private static WaypointFormCheck.Field fieldOf(EditResultStatus status) {
        return switch (status) {
            case IDENTIFIER_COLLISION -> WaypointFormCheck.Field.NAME;
            case INVALID_DISPLAY_TEXT -> WaypointFormCheck.Field.DISPLAY_NAME;
            case DUPLICATE_KEYWORD -> WaypointFormCheck.Field.KEYWORDS;
            default -> WaypointFormCheck.Field.NONE;
        };
    }

    /** Puts every field back to the saved waypoint and clears the status and the field highlights. */
    private void resetProperties() {
        WaypointFormPatch.Saved values = this.savedWaypoint();
        this.nameEditBox.setValue(values.name());
        this.displayNameEditBox.setValue(values.displayNameOverride() == null ? "" : values.displayNameOverride());
        this.initialsEditBox.setValue(values.initials());
        int color = 0xFF000000 | values.rgb();
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.xEditBox.setValue(Integer.toString(values.position().x()));
        this.yEditBox.setValue(Integer.toString(values.position().y()));
        this.zEditBox.setValue(Integer.toString(values.position().z()));
        this.yawEditBox.setValue(Integer.toString(values.yaw()));
        this.globalToggle.setState(values.global());
        this.keywordsEditBox.setValue(String.join(", ", values.keywords()));
        this.descriptionEditBox.setValue(values.description());
        this.iconPicker.setSelectedIcon(values.icon());
        this.onFormEdited();
    }
}
```

- [ ] **Step 6: Compile the active target and run the whole mods suite**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.1.2-fabric:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL with no test failures. If a call site outside these files broke, it is a call to something the rewrite removed (`showDisplayNameField`, `getTitleRowClickableWidgets`, `getButtonRowClickableWidgets`, `renderTitleRowOverlays`, `onSwatchClosed`, `resetProperties` as public); fix that call site, don't restore the method.

- [ ] **Step 7: Compile the oldest target now, not at the end**

Version drift in the new drawing, focus and mouse code shows here first.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:1.20.1-fabric:compileJava --offline -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL. If it fails on an API, use the `reference-minecraft-source-code` skill to check it against `minecraft_source_code/1.20.1` and fix it behind a small Stonecutter predicate, keeping the active branch as it is.

- [ ] **Step 8: Check the markers**

```bash
python3 SCRATCH/check_stonecutter.py mods/src/main/java/_959/server_waypoint/common/client/gui/screens/AbstractWaypointPropertiesScreen.java mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointAddScreen.java mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointEditScreen.java
```

Expected: `balanced: 3 file(s)`.

- [ ] **Step 9: Update the GUI guide**

Four edits: the dropdown popups paragraph, the "Choosing a base screen" bullet, the add and edit screens bullet, and the testing list.

In `docs/tips/gui/local-guide.md`, replace:

```markdown
popup choices, including on newer render strata APIs. `AbstractWaypointPropertiesScreen` exposes
`renderTitleRowOverlays(...)` after suggestions and before the swatch for this purpose.
`WaypointAddScreen` uses that hook for its dimension combobox, populated from the same complete
integrated-server or remote-suggestion dimension catalog as `WaypointManagerScreen`, with the
supplied starting dimension retained.
```

with:

```markdown
popup choices, including on newer render strata APIs. `AbstractWaypointPropertiesScreen` draws the
popup of every dropdown it holds, the icon picker's and Add's dimension combobox, after the rows,
the suggestions and the field tooltip. The dimension combobox is populated from the same complete
integrated-server or remote-suggestion dimension catalog as `WaypointManagerScreen`, with the
supplied starting dimension retained.
```

In `docs/tips/gui/local-guide.md`, replace:

```markdown
Shared fields, coordinate rules, suggestions, color selection, layout, and overlay behavior belong in this base; subclasses provide the title row, action row, and operation-specific action. Pass `showDisplayNameField = true` only for edit flows that expose the identifier and formatted display-name override separately. Override `onSwatchClosed()` when modal restoration must reapply operation-specific disabled states after the base screen re-enables its controls.
```

with:

```markdown
Shared fields, coordinate rules, suggestions, color selection, checks, layout, and overlay behavior belong in this base. A subclass supplies what differs through methods the base calls from `init()`, after both constructors have finished, so the constructors call none of them: `leadingRows()` (Add's Dimension and List rows above Name), `subtitle()`, `hasDisplayNameRow()`, `footerButtons()` and `primaryButton()`, `checkInput()`, `submit()`, `pendingMessage()` and `refreshButtons(...)`, and optionally `hasChanges()`, `initialFocus()` and `onTick()`. `refreshControlStates()` sets every control's `active` flag and the footer message from the color picker, a pending request, the check result and whether Edit's form differs from the saved waypoint, so no screen keeps a list of controls to disable.
```

In `docs/tips/gui/local-guide.md`, replace:

```markdown
- `AbstractWaypointPropertiesScreen`, `WaypointAddScreen`, and `WaypointEditScreen` demonstrate shared form behavior, `WidgetStack` rows, suggestion fields, and subclass extension points. `WaypointEditScreen` captures the list revision, tracks an explicit display-name clear state, submits one atomic edit payload, and keeps entered values until a matching server result accepts the edit. Client transport reset and handshake paths must call `WaypointEditScreen.handleTransportReset()` so a lost correlated result cannot leave the update action disabled. The add screen treats its name field only as the exact identifier and creates no display-name override.
```

with:

```markdown
- `AbstractWaypointPropertiesScreen`, `WaypointAddScreen`, and `WaypointEditScreen` demonstrate a compact, fixed form. `WaypointFormLayout` is pure and unit-tested: from measured sizes it works out the label and control columns, the gap between rows (9 pixels, down to 5 when the screen is short), the dividers and the footer, and the screen places its widgets from the answers with `placeOutline` and `placeInRow`, which position any widget by its outline whatever its anchor. Layout runs in `init()`, when the footer message changes because a wrapped message changes the footer's height, and on resize through `repositionElements()`, which keeps the widgets, so values, focus, the message and a pending request survive; it never runs every frame. `WaypointFormCheck` runs on every edit and tick and reports the first problem: a hint or an error blocks Add and Save and the footer says why, an error's field gets a `DANGER` outline through `setInvalid`, and a note doesn't block. `WaypointAddScreen` sends `/wp add`, locks the form with a `PendingAdd`, closes when the waypoint appears in the synced data and unlocks with a message after 5 seconds. `WaypointEditScreen` captures the list revision, builds one atomic patch with `WaypointFormPatch`, keeps entered values until a matching server result accepts the edit, and unlocks with that result's message otherwise. Its Display name field holds only the override, and an empty field over a saved override clears it. The add screen treats its name field only as the exact identifier and creates no display-name override. Resting the pointer on a field's label or controls for 500 ms shows that field's tooltip at the pointer through `DrawContextHelper.scheduleTooltipAtPointer`, but not over the remove-icon button, which has its own, nor while a popup, the color picker or a pending request is open.
```

In `docs/tips/gui/local-guide.md`, replace:

```markdown
- Pure label or presentation calculations.
```

with:

```markdown
- Pure label or presentation calculations.
- A form's geometry, checks, patch and pending-request rules as helpers without Minecraft text
  classes: `WaypointFormLayout`, `WaypointFormCheck`, `WaypointFormPatch`, `WaypointFormInitials` and
  `PendingAdd` have their own tests, and `WaypointFormTranslationTest` checks that every key the
  form uses exists in all six locales with the arguments of English.
```

- [ ] **Step 10: Checkpoint.**

---

### Task 12: Documentation and the validation record

**Files:**
- Modify: `README.md`, `README_zh.md`
- Modify: `docs/features/waypoint-form/README.md` (it already lists the plan)
- Modify: `docs/features/waypoint-form/specs/2026-09-29-waypoint-form-design.md` (its status line)
- Create: `docs/features/waypoint-form/validation/2026-09-29-waypoint-form-validation.md`
- Delete: `docs/features/waypoint-form/validation/.gitkeep` (the folder now has a document; `plans/.gitkeep` went when the plan was written)

- [ ] **Step 1: Add the usage bullet to both READMEs**

In `README.md`, replace:

```markdown
- In the waypoint manager screen, press `C` to open the client settings. The config button in Mod Menu (Fabric) or the Mods screen (NeoForge and Forge) opens them too.
```

with:

```markdown
- In the waypoint manager screen, press `C` to open the client settings. The config button in Mod Menu (Fabric) or the Mods screen (NeoForge and Forge) opens them too.
- The add and edit waypoint screens also set keywords and a description.
```

In `README_zh.md`, replace:

```markdown
- 在路径点管理界面按下 `C` 可打开客户端设置。也可以通过 Mod Menu（Fabric）或模组列表（NeoForge 和 Forge）中的配置按钮打开。
```

with:

```markdown
- 在路径点管理界面按下 `C` 可打开客户端设置。也可以通过 Mod Menu（Fabric）或模组列表（NeoForge 和 Forge）中的配置按钮打开。
- 添加和编辑路径点界面也可以设置关键词和描述。
```

- [ ] **Step 2: Index the validation record, and update the spec's status**

In `docs/features/waypoint-form/README.md`, replace:

```markdown
- [Implementation plan](plans/2026-09-29-waypoint-form.md)
```

with:

```markdown
- [Implementation plan](plans/2026-09-29-waypoint-form.md)
- [Validation record](validation/2026-09-29-waypoint-form-validation.md)
```

In `docs/features/waypoint-form/specs/2026-09-29-waypoint-form-design.md`, replace:

```markdown
Status: design agreed on 2026-09-29. Not implemented yet.
```

with:

```markdown
Status: design agreed on 2026-09-29. Implemented; see the [validation record](../validation/2026-09-29-waypoint-form-validation.md).
```

- [ ] **Step 3: Create the validation record**

Create `docs/features/waypoint-form/validation/2026-09-29-waypoint-form-validation.md`. Task 13 records the results; the in-game pass is manual.

```markdown
# Waypoint form validation

How the [form design](../specs/2026-09-29-waypoint-form-design.md) was checked, following its Validation section. The [implementation plan](../plans/2026-09-29-waypoint-form.md) says how it was built.

## Automated checks

Every Gradle command sets `GRADLE_USER_HOME=/Volumes/ssd/gradle_home` and, for 26.x targets, `-Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`. Record the date and the result of each row, and note any target that was not run.

| Check | Command | Date | Result |
| --- | --- | --- | --- |
| Common unit tests | `./gradlew :common:test` | | |
| Mods unit tests, active target | `./gradlew :mods:26.1.2-fabric:test` | | |
| Compile 1.20.1 Fabric | `./gradlew :mods:1.20.1-fabric:compileJava --offline` | | |
| Compile 1.20.1 Forge | `./gradlew :mods:1.20.1-forge:compileJava` | | |
| Compile 1.21.6 Fabric, the first version with `setTooltipForNextFrame` | `./gradlew :mods:1.21.6-fabric:compileJava --offline` | | |
| Compile 26.3 Fabric | `./gradlew :mods:26.3-fabric:compileJava --offline` | | |
| Compile 26.3 NeoForge | `./gradlew :mods:26.3-neoforge:compileJava` | | |
| No whitespace errors | `git diff --check` | | |
| Stonecutter markers balanced in every touched Java file | marker check from the plan | | |

## In game

A manual pass on 26.1.2 Fabric. Compiling can't prove these. Tick a box only after seeing it.

### Sizes

- [ ] Add at 480×270, 378×245 and 320×240 with a one-line status.
- [ ] Edit at the same three sizes.
- [ ] A status that wraps, such as Spanish at 320×240: the gap between rows shrinks, the footer grows and nothing crosses the screen edge.
- [ ] A long list display name, waypoint name and dimension in the Edit header: one line each, cut with "…".

### Add

- [ ] From the manager's + button: the dimension is the selected one, List is empty and focused, Name is empty.
- [ ] From a list row: List is filled in and Name is focused.
- [ ] From Xaero's World Map: the position comes from the map.
- [ ] Add is inactive, with a hint in the footer, until the dimension, list and name are filled in. An existing name shows an error and a danger outline on Name.
- [ ] Add closes when the waypoint appears, in singleplayer and on a dedicated server.
- [ ] When the server refuses, for example without permission, "Adding…" locks the form and after 5 seconds it unlocks with "The server didn't add the waypoint. Check the chat."
- [ ] A new list name shows the note "Adding creates the list …" and doesn't block.
- [ ] The keywords and the description reach the waypoint, as the manager's details show.

### Edit

- [ ] Save with each field changed.
- [ ] Save and Reset are inactive until something changes. Reset restores every field and clears the message and the highlights.
- [ ] A name collision highlights Name, an invalid display name highlights Display name and duplicate keywords highlight Keywords.
- [ ] A permission error shows in the footer.
- [ ] A waypoint with an empty display-name override: the title names the waypoint, Display name shows "Empty: the marker shows no name" and saving untouched changes nothing.
- [ ] The subtitle shows "In <list> · <dimension>" with the list's display name, never blank.

### Keyboard, focus and tooltips

- [ ] Tab order follows the screen on both screens and ends with the footer buttons.
- [ ] Enter in a text field sends the form when Add or Save is active. While a suggestion list is open it takes the highlighted suggestion instead.
- [ ] Escape closes a popup, then leaves the field, then closes the screen. With the color picker open it closes the picker and focus returns to the color button.
- [ ] First focus: List, or Name once List is filled, on Add; nothing on Edit, where the movement keys move the player.
- [ ] Resizing the window keeps the values, the focus, the message and a pending Add or Save, including during "Adding…" and while a field has focus.
- [ ] With the color picker open every other control is inactive.
- [ ] Tooltips appear after about 500 ms on a label and on its controls, with their own text for Initials, Visibility and Yaw. None shows over the remove-icon button, which has its own "Remove icon" tooltip, while a popup or the picker is open, or while a request is pending.
- [ ] Initials follow the name until you type other initials. On Edit they follow only when the saved initials were the default.

### Themes

- [ ] Translucent Dark, Modern Dark and High Contrast: text, placeholders, the danger outline and the footer message are readable.

## Notes

Record anything that differed from the plan here.
```

- [ ] **Step 4: Remove the placeholders**

```bash
rm docs/features/waypoint-form/validation/.gitkeep
```

- [ ] **Step 5: Checkpoint.**

---

### Task 13: Verify against the spec's Validation section

**Files:**
- Modify: `docs/features/waypoint-form/validation/2026-09-29-waypoint-form-validation.md` (record the results)

- [ ] **Step 1: Run every unit test in the two projects**

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :common:test :mods:26.1.2-fabric:test -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL. Before this change `:mods:26.1.2-fabric:test` ran 410 tests; with the new ones it should report about 96 more.

- [ ] **Step 2: Check the disk, then compile the other targets the spec lists**

```bash
df -h /System/Volumes/Data /Volumes/ssd
```

Keep at least 1.5 GB free. Then compile each of these, one command each. `1.20.1-fabric` was compiled in Task 11; run it again, because later edits touched more files. Drop `--offline` for the Forge and NeoForge targets.

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:1.20.1-fabric:compileJava --offline -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:1.20.1-forge:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:1.21.6-fabric:compileJava --offline -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.3-fabric:compileJava --offline -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

```bash
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home && ./gradlew :mods:26.3-neoforge:compileJava -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

Expected: BUILD SUCCESSFUL each. `1.21.6-fabric` is the first version that uses `setTooltipForNextFrame`, so it checks the helper's other branch. Say exactly which targets ran; do not claim the ones that didn't.

- [ ] **Step 3: Whitespace and markers**

```bash
/usr/bin/git diff --check
```

```bash
python3 SCRATCH/check_stonecutter.py $(/usr/bin/git ls-files -m -o --exclude-standard -- '*.java')
```

Expected: no output from the first; `balanced: N file(s)` from the second.

- [ ] **Step 4: Record the automated results**

Record the commands you ran, the date and the results in the "Automated checks" table of the validation record, and list the targets that ran.

- [ ] **Step 5: Hand over the in-game pass**

The in-game checklist in the validation record needs a person or the `test-minecraft-mods-headlessmc` skill: compiling can't prove layout, focus, tooltips or the Add flow. Tell the user which items are still unchecked. Do not mark them done.

- [ ] **Step 6: Checkpoint.**

---

## Decisions where the spec was silent or inexact

These are choices the plan makes. Change any of them before Task 11 if you disagree.

1. **Enter and suggestion lists.** The spec says that while a suggestion list is open Enter "picks the highlighted entry instead, as today". Text fields don't handle Enter today; only the dropdowns do, and the suggestion lists answer Up, Down, Tab and Escape. So the plan adds an opt-in `SuggestingTextInput.acceptHighlightedSuggestion()` (Task 9) and the form calls it before using Enter to submit. No other screen changes. The alternative is to leave Enter alone while a list is open, so it does nothing there.
2. **Add now follows the hex field.** Before, only the swatch colored an added waypoint: the hex field's responder that copies its color to the swatch button existed on Edit only. Both screens have it now.
3. **The placeholder "Optional"** sits on Keywords and Description, both optional and empty by default on Add. The spec gives the string but not the fields.
4. **Hover areas.** A field's area is its label and its controls, including the icon preview; the remove-icon button is left out, so its own tooltip shows. The gaps between the fields of one row belong to no field.
5. **Two small accessors** the spec doesn't list: `SuggestingTextInput.isSuggestionListOpen()` and `ComboBoxWidget.isSuggestionListOpen()`, used to keep the field tooltip from showing over an open suggestion list.
6. **Resize keeps everything** by overriding `repositionElements()` to lay out again instead of rebuilding the widgets, which is how vanilla loses focus and values on a rebuild.
7. **Yaw is compared as typed**, as before. A value outside -180..180 differs from the saved, normalized one, so Save enables; the server normalizes it.
8. **Commits.** The plan ends every task with a checkpoint, not a commit, because `AGENTS.md` says to commit only when asked.
