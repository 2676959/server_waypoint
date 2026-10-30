# Local Profile List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the profile picker in `LocalWaypointProfilesScreen` with a list of every Local
profile and finish the screen as the approved spec describes.

**Architecture:** Two read-only additions to profile storage (`place`, `activeReason`) and one
template scanner shared by `expand` and the screen (`LocalTeleportCommand.tokens`/`problem`) feed
a rewritten screen built from `SettingsListWidget` rows whose controls are cached per profile
UUID. Three small widget hooks (a text formatter, an icon color, a confirm-button getter) and a
screen-owned `TeleportCommandStyle` color the command. Dialogs reuse `ConfirmationDialog`.

**Tech Stack:** Java 17 source level, Minecraft client GUI through Stonecutter (Fabric, Forge,
NeoForge), JUnit 5, Gradle Kotlin DSL.

**Spec:** [Local profile list design](../specs/2026-10-30-local-profile-list-design.md)

## Global Constraints

- Java 17-compatible code, 4-space indentation, no tabs. No backward-compatibility code; the
  profile storage format doesn't change.
- **No commits** (AGENTS.md). Other sessions may share this checkout: run `git status --short` and
  `git branch --show-current` (expect `feature/private-waypoint`) before the first edit and before
  reporting; leave files you didn't change alone.
- Before Task 3, read `docs/tips/gui/local-guide.md`: "Settings lists", "Confirmation dialogs",
  "Widget render entry points" and "Stonecutter and version compatibility". A task that adds or
  changes an API under `mods/src/main/java/_959/server_waypoint/common/client/gui` documents it
  there in the same task.
- Stonecutter: write predicates as `>=1.21.9`; keep `//? if`, `//?}`, `/*` and `*///?}` markers
  balanced; keep the existing `//$ render_method_swap` line split for render calls.
- Client lang files (`mods/src/main/resources/assets/server_waypoint/lang/*.json`) use Minecraft
  `%s` arguments. A task that adds, changes or retires a key does it in all six locales (`en_us`,
  `es_es`, `he_il`, `zh_cn`, `zh_hk`, `zh_tw`) and keeps argument counts equal to English.
  Glossary from the existing files: profile = es "perfil", he "פרופיל", zh_cn "配置", zh_hk/zh_tw
  "設定檔"; Local = es "Locales", he "מקומיים", zh_cn "本地", zh_hk/zh_tw "本機"; teleport
  command = es "comando de teletransporte", he "פקודת שיגור", zh_cn "传送命令", zh_hk/zh_tw "傳送指令".
  Placeholders such as `{x}` stay literal in every locale.
- Placeholder colors (RGB): `{x}` `0xFF7B72`, `{y}` `0x7EE787`, `{z}` `0x79B8FF`, `{yaw}`
  `0xFFC66D`, `{dimension}` `0xD2A8FF`. Mistakes: theme `DANGER` plus strikethrough. Never bold.
- Profile names: stripped, non-empty, at most 128 characters (the name field's max length), and
  unique among the other profiles ignoring case (`Locale.ROOT`) and surrounding spaces. Storage
  keeps accepting any non-blank name.
- Gradle runs through this wrapper (it sets the SSD Gradle home and the JDK 25 toolchain path).
  Create it once; `--tests` binds to the task written just before it. Check
  `df -h /Volumes/ssd /System/Volumes/Data` first and stop below 2 GiB free.

  ```zsh
  # /private/tmp/claude-501/-Volumes-ssd-fabric-mods-repo-server-waypoint/704b9394-0301-4f72-8e9b-76f7c6b16ab7/scratchpad/gw.zsh
  export GRADLE_USER_HOME=/Volumes/ssd/gradle_home
  cd /Volumes/ssd/fabric_mods_repo/server_waypoint || exit 1
  ./gradlew "$@" -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home --console=plain --max-workers=2
  ```

  Below, `GW` stands for `zsh /private/tmp/claude-501/-Volumes-ssd-fabric-mods-repo-server-waypoint/704b9394-0301-4f72-8e9b-76f7c6b16ab7/scratchpad/gw.zsh`.
- Keep the failing and passing logs of each task under
  `docs/features/private-waypoint/validation/execution/profile-list/` (`task-N-red.log`,
  `task-N-green.log`).

## Review Focus

1. A 128-character profile name with no spaces at a 320×240 GUI: the label wraps and the Use
   button and icons stay inside the panel. Task 5, `rowsKeepTheirControlsInsideThePanel`.
2. Duplicate names that are already in storage (automatic "World …" profiles, older data): rows stay
   separate and act on their own UUID. Task 5, `duplicateNamesInStorageStaySeparateRows`.
3. Deleting the only profile: storage creates "Default" and the status names it. Task 5,
   `deletingTheOnlyProfileSwitchesToANewDefault`.
4. The session ending while a dialog is open: confirm turns off, Cancel and Done keep working and
   Done doesn't ask. Task 6, `aSessionEndingUnderADialogLeavesOnlyCancelAndDone`.
5. A scrolled command field whose visible text starts inside a placeholder: the colors stay
   right. Task 4, `aScrolledFieldColorsThePlaceholderItStartsInside`.

---

### Task 1: Template scanner

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/local/LocalTeleportCommand.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/local/LocalTeleportCommandTest.java`

**Interfaces:**
- Produces, all nested in `LocalTeleportCommand`:
  - `public enum Placeholder { DIMENSION, X, Y, Z, YAW; public String token(); }`: `token()` is
    `"{dimension}"`, `"{x}"`, `"{y}"`, `"{z}"`, `"{yaw}"`.
  - `public record Token(int start, int end, @Nullable Placeholder placeholder, boolean mistake)`:
    a `[start, end)` span of the raw template; plain text has `placeholder == null` and `mistake ==
    false`.
  - `public enum ProblemKind { CONTROL, EMPTY, EXTRA_SLASH, STRAY_CLOSE, UNCLOSED, UNKNOWN }`
  - `public record Problem(ProblemKind kind, String text)`: `text` is the offending span for
    `STRAY_CLOSE`, `UNCLOSED` and `UNKNOWN`, `""` otherwise.
  - `public static List<Token> tokens(String template)`: contiguous spans covering the whole
    string, in order; `""` gives an empty list.
  - `public static Optional<Problem> problem(String template)`

- [x] **Step 1: Write the failing tests** in `LocalTeleportCommandTest`, keeping the two existing ones:

```java
@Test void tokensSplitTheWholeTemplateIntoTextPlaceholdersAndMistakes() {
    assertEquals(List.of(new Token(0, 4, null, false), new Token(4, 7, Placeholder.X, false),
            new Token(7, 10, Placeholder.Y, false), new Token(10, 12, null, false),
            new Token(12, 17, Placeholder.YAW, false)), LocalTeleportCommand.tokens("/tp {x}{y} ~{yaw}"));
    assertEquals(List.of(new Token(0, 3, null, false), new Token(3, 10, null, true),
            new Token(10, 11, null, false), new Token(11, 12, null, true)), LocalTeleportCommand.tokens("tp {pitch} }"));
    assertEquals(List.of(new Token(0, 3, null, false), new Token(3, 7, null, true)), LocalTeleportCommand.tokens("tp {x y"));
    assertEquals(List.of(), LocalTeleportCommand.tokens(""));
}

@Test void problemsFollowTheOrderExpandChecksIn() {
    assertEquals(Optional.empty(), LocalTeleportCommand.problem(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND));
    assertEquals(Optional.of(new Problem(ProblemKind.CONTROL, "")), LocalTeleportCommand.problem("tp {pitch}\n"));
    assertEquals(Optional.of(new Problem(ProblemKind.EMPTY, "")), LocalTeleportCommand.problem(" / "));
    assertEquals(Optional.of(new Problem(ProblemKind.EXTRA_SLASH, "")), LocalTeleportCommand.problem("//tp @s"));
    assertEquals(Optional.of(new Problem(ProblemKind.UNKNOWN, "{pitch}")), LocalTeleportCommand.problem("tp {pitch} }"));
    assertEquals(Optional.of(new Problem(ProblemKind.STRAY_CLOSE, "}")), LocalTeleportCommand.problem("tp } {pitch}"));
    assertEquals(Optional.of(new Problem(ProblemKind.UNCLOSED, "{x y")), LocalTeleportCommand.problem("tp {x y"));
}

@Test void problemAgreesWithExpandOnEveryTemplate() {
    for (String template : new String[]{"", " / ", "tp {unknown}", "tp {x", "tp x}", "tp {{x}}", "tp\nkill @s",
            "tp\rkill @s", "tp\t@s", "tp\u0000@s", "//tp", LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, " /tp @s {x} {y} {z} "}) {
        boolean expands;
        try { LocalTeleportCommand.expand(template, "minecraft:overworld", target); expands = true; }
        catch (IllegalArgumentException exception) { expands = false; }
        assertEquals(expands, LocalTeleportCommand.problem(template).isEmpty(), template);
    }
}
```

- [x] **Step 2: Run and see them fail.** `GW :mods:26.1.2-fabric:test --tests '*LocalTeleportCommandTest'`.
  Expected: compilation fails (`Token`, `Placeholder`, `Problem` missing).
- [x] **Step 3: Implement** the types and both methods with one private scanner that `expand` also
  uses. Its exceptions and results stay the same. `problem` checks in `expand`'s order: any ISO
  control character, then blank after trimming and removing one leading `/`, then a second
  leading `/`, then the first mistake token. Scanning: `}` alone is `STRAY_CLOSE`; `{` with no
  later `}` is `UNCLOSED` to the end; `{name}` with an unknown name is `UNKNOWN`, braces included.
- [x] **Step 4: Run and see them pass.** Same command. Expected: all `LocalTeleportCommandTest` tests pass.

### Task 2: Where profiles belong and why one is active

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/local/LocalWaypointProfiles.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/WaypointClientMod.java:422`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/local/LocalWaypointProfilesTest.java`

**Interfaces:**
- Produces in `LocalWaypointProfiles`:
  - `public enum ActiveReason { FALLBACK, BACKEND, MANUAL }`
  - `public synchronized @Nullable String place()`: the stripped address given to
    `joinMultiplayer`, or the stripped world name given to `joinSingleplayer`; `null` after `leave()`.
  - `public synchronized @Nullable ActiveReason activeReason()`: `MANUAL` while `manual`; else
    `BACKEND` when the backend identity is known and its binding is the active profile; else
    `FALLBACK`. `null` without an active profile.
  - `public synchronized void joinSingleplayer(UUID account, Path worldSave, String worldName)`:
    replaces the two-argument method.
- `WaypointClientMod.onJoinServer` passes `integratedServer.getWorldData().getLevelName()`.

- [x] **Step 1: Write the failing tests**, and change the existing calls in
  `accountHostPortAndSingleplayerSavesHaveIndependentRoots` to pass `"one"` and `"two"`:

```java
@Test void placeIsTheAddressOrWorldNameUntilLeaving() throws Exception {
    var profiles = new LocalWaypointProfiles(root, collections());
    profiles.joinMultiplayer(account, " Example.ORG:25566 ");
    assertEquals("Example.ORG:25566", profiles.place());
    profiles.joinSingleplayer(account, root.resolve("saves/one"), "My World");
    assertEquals("My World", profiles.place());
    profiles.leave();
    assertNull(profiles.place());
    assertNull(profiles.activeReason());
}

@Test void activeReasonSaysHowTheActiveProfileWasChosen() throws Exception {
    var profiles = new LocalWaypointProfiles(root, collections());
    profiles.joinMultiplayer(account, "example.org");
    assertEquals(LocalWaypointProfiles.ActiveReason.FALLBACK, profiles.activeReason());
    profiles.onBackendIdentity(10);
    assertEquals(LocalWaypointProfiles.ActiveReason.BACKEND, profiles.activeReason());
    profiles.create("Other");
    assertEquals(LocalWaypointProfiles.ActiveReason.MANUAL, profiles.activeReason());
    profiles.leave();
    profiles.joinMultiplayer(account, "example.org");
    assertEquals(LocalWaypointProfiles.ActiveReason.FALLBACK, profiles.activeReason());
    profiles.onBackendIdentity(10);
    assertEquals(LocalWaypointProfiles.ActiveReason.BACKEND, profiles.activeReason());
    profiles.delete(profiles.active().id());
    assertEquals(LocalWaypointProfiles.ActiveReason.MANUAL, profiles.activeReason());
}
```

- [x] **Step 2: Run and see them fail.** `GW :mods:26.1.2-fabric:test --tests '*LocalWaypointProfilesTest'`.
  Expected: compilation fails (`place`, `ActiveReason` and the three-argument join missing).
- [x] **Step 3: Implement** the field, both methods and the new join argument, and update the
  `WaypointClientMod` call.
- [x] **Step 4: Run and see them pass.** Same command, plus `--tests '*ClientWaypointAccessTest'`
  `--tests '*ClientHandshakeMapSyncTest'`. Expected: all pass.

### Task 3: Widget hooks

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInput.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/IconButton.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ConfirmationDialog.java`
- Modify: `docs/tips/gui/local-guide.md` (the `SuggestingTextInput`, `IconButton` and "Confirmation dialogs" passages)
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/SuggestingTextInputTest.java`,
  `IconButtonTest.java`, new `ConfirmationDialogTest.java` (same package)

**Interfaces:**
- Produces:
  - `SuggestingTextInput.setTextFormatter(BiFunction<String, Integer, FormattedCharSequence> formatter)`:
    stores the function. The first call registers one delegate with vanilla:
    `addFormatter(this::formatShown)` for `>=1.21.9`, `setFormatter(this::formatShown)` below
    (1.21.7's NeoForge sources still have `setFormatter`). Later calls replace the stored function.
  - `public FormattedCharSequence formatShown(String shown, int offset)`: what vanilla draws for
    the shown text. It returns the stored function's result, or
    `FormattedCharSequence.forward(shown, Style.EMPTY)` when none is set, the input isn't
    editable, or it isn't `active`. Public, because screen tests in another package check the wiring.
  - `SuggestingTextInput.setEditable(boolean)` override: records the flag (vanilla's
    `isEditable()` is private), then calls `super`.
  - `IconButton withIconColor(WidgetThemeVariable color)` and package-private `int iconTint()`:
    `getColor(color)` while active and set, else `WidgetThemeState.iconTint(this.active)`. The
    render path uses `iconTint()`.
  - `TranslucentButton ConfirmationDialog.getConfirmButton()`

- [x] **Step 1: Write the failing tests:**

```java
// SuggestingTextInputTest
@Test void aTextFormatterStylesShownTextOnlyWhileTheInputIsEditable() {
    TranslucentTextField field = new TranslucentTextField(10, 20, 60, Component.empty(), new TestFont());
    field.setTextFormatter((shown, offset) -> FormattedCharSequence.forward(shown, Style.EMPTY.withColor(0x123456)));
    assertEquals(List.of(0x123456, 0x123456), colors(field.formatShown("ab", 0)));
    field.setEditable(false);
    assertEquals(List.of(-1, -1), colors(field.formatShown("ab", 0)));
    field.setEditable(true);
    field.active = false;
    assertEquals(List.of(-1, -1), colors(field.formatShown("ab", 0)));
}
// colors(...) collects style.getColor() == null ? -1 : style.getColor().getValue() through FormattedCharSequence.accept.

// IconButtonTest
@Test void anIconColorTintsTheIconOnlyWhileActive() {
    assertEquals(WidgetThemeState.iconTint(true), button(false).iconTint());
    IconButton danger = button(false).withIconColor(WidgetThemeVariable.DANGER);
    assertEquals(WidgetThemeManager.getColor(WidgetThemeVariable.DANGER), danger.iconTint());
    danger.active = false;
    assertEquals(WidgetThemeState.iconTint(false), danger.iconTint());
}

// ConfirmationDialogTest
@Test void theConfirmButtonIsTheFooterButtonBesideCancel() {
    var dialog = new ConfirmationDialog(0, 0, Component.literal("Title"), new WidgetStack(0, 0, 8, true, false),
            Component.literal("Go"), () -> { }, () -> { }, new TestFont());
    List<AbstractWidget> buttons = new ArrayList<>();
    dialog.visitWidgets(buttons::add);
    assertEquals(List.of(dialog.getCancelButton(), dialog.getConfirmButton()), buttons);
    assertEquals(Component.literal("Go"), dialog.getConfirmButton().getMessage());
}
```

- [x] **Step 2: Run and see them fail.** `GW :mods:26.1.2-fabric:test --tests '*SuggestingTextInputTest' --tests '*IconButtonTest' --tests '*ConfirmationDialogTest'`.
  Expected: compilation fails on the missing methods.
- [x] **Step 3: Implement** the three hooks, and document them in the GUI guide passages listed under Files.
- [x] **Step 4: Run and see them pass on both Stonecutter branches.** The same command, then
  `GW :mods:1.20.1-fabric:test --tests '*SuggestingTextInputTest' --tests '*IconButtonTest' --tests '*ConfirmationDialogTest'`
  and `GW :mods:1.21.7-neoforge:compileJava`. Expected: tests pass, compile succeeds.

### Task 4: Placeholder colors

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/TeleportCommandStyle.java`
  (package-private, final, static methods only)
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/TeleportCommandStyleTest.java`

**Interfaces:**
- Consumes: `LocalTeleportCommand.tokens`, `Token`, `Placeholder` (Task 1).
- Produces:
  - `static int color(LocalTeleportCommand.Placeholder placeholder)`: the RGB from Global Constraints.
  - `static FormattedCharSequence format(String value, String shown, int offset)`: `value` is the
    field's whole text and `shown` equals `value.substring(offset, offset + shown.length())`, as
    vanilla passes it. Tokenizes `value`; placeholders get `Style.EMPTY.withColor(color)`,
    mistakes `withColor(getColor(DANGER) & 0xFFFFFF).withStrikethrough(true)`, text `Style.EMPTY`.
  - `static Component legend()`: `Component.translatable("waypoint.profile.placeholders", list)`,
    where `list` is `{dimension} {x} {y} {z} {yaw}`, one space apart, each placeholder in its color.

- [x] **Step 1: Write the failing tests** (`colors` as in Task 3; `styles` collects the `Style` per character):

```java
@Test void placeholdersTakeTheirColorsAndOtherTextKeepsTheFieldColor() {
    String value = "/tp {x} {y} {z} {yaw} {dimension}";
    List<Integer> colors = colors(TeleportCommandStyle.format(value, value, 0));
    assertEquals(-1, colors.get(0));
    assertEquals(0xFF7B72, colors.get(value.indexOf("{x}")));
    assertEquals(-1, colors.get(value.indexOf("{x}") + 3));
    assertEquals(0x7EE787, colors.get(value.indexOf("{y}") + 1));
    assertEquals(0x79B8FF, colors.get(value.indexOf("{z}") + 2));
    assertEquals(0xFFC66D, colors.get(value.indexOf("{yaw}") + 4));
    assertEquals(0xD2A8FF, colors.get(value.indexOf("{dimension}")));
}

@Test void aPlaceholderCutByTheCaretKeepsItsColorOnBothSides() {
    String value = "tp {yaw}";
    assertEquals(List.of(-1, -1, -1, 0xFFC66D, 0xFFC66D), colors(TeleportCommandStyle.format(value, "tp {y", 0)));
    assertEquals(List.of(0xFFC66D, 0xFFC66D, 0xFFC66D), colors(TeleportCommandStyle.format(value, "aw}", 5)));
}

@Test void aScrolledFieldColorsThePlaceholderItStartsInside() {
    String value = "/execute in {dimension} run";
    int offset = value.indexOf("mension");
    List<Integer> colors = colors(TeleportCommandStyle.format(value, value.substring(offset), offset));
    assertEquals(0xD2A8FF, colors.get(0));
    assertEquals(0xD2A8FF, colors.get("mension}".length() - 1));
    assertEquals(-1, colors.get("mension}".length()));
}

@Test void mistakesUseTheDangerColorStruckThrough() {
    String value = "tp {pitch} }";
    List<Style> styles = styles(TeleportCommandStyle.format(value, value, 0));
    int danger = WidgetThemeManager.getColor(WidgetThemeVariable.DANGER) & 0xFFFFFF;
    for (int index : new int[]{3, 9, 11}) {
        assertEquals(danger, styles.get(index).getColor().getValue());
        assertTrue(styles.get(index).isStrikethrough());
    }
    assertFalse(styles.get(0).isStrikethrough());
}

@Test void theLegendListsEveryPlaceholderInItsColor() {
    var contents = (TranslatableContents) TeleportCommandStyle.legend().getContents();
    assertEquals("waypoint.profile.placeholders", contents.getKey());
    Component list = (Component) contents.getArgs()[0];
    assertEquals("{dimension} {x} {y} {z} {yaw}", list.getString());
    List<Integer> colors = new ArrayList<>();
    list.visit((style, text) -> {
        if (text.startsWith("{")) colors.add(style.getColor().getValue());
        return Optional.empty();
    }, Style.EMPTY);
    assertEquals(List.of(0xD2A8FF, 0xFF7B72, 0x7EE787, 0x79B8FF, 0xFFC66D), colors);
}
```

- [x] **Step 2: Run and see them fail.** `GW :mods:26.1.2-fabric:test --tests '*TeleportCommandStyleTest'`. Expected: compilation fails.
- [x] **Step 3: Implement `TeleportCommandStyle`.** Build `format` from `FormattedCharSequence.forward`
  pieces joined with `FormattedCharSequence.composite(List)`, one piece per token overlapping
  `[offset, offset + shown.length())`, cut to that range.
- [x] **Step 4: Run and see them pass**, on `26.1.2-fabric` and `1.20.1-fabric` with the same filter.

### Task 5: Profile list, dialogs and the frame

**Files:**
- Rewrite: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/LocalWaypointProfilesScreen.java`
  (the public constructor keeps its signature, so `WaypointManagerScreen` doesn't change)
- Modify: the six lang files
- Test: rewrite `.../gui/screens/LocalWaypointProfilesScreenInteractionTest.java`; in
  `LocalWaypointProfilesScreenTest.java` replace the `ProfileChoices` tests and keep the
  `ProfileLayout` ones; update `WaypointFormTranslationTest.java`

**Interfaces:**
- Consumes: `place()`, `activeReason()` (Task 2); `setTextFormatter`, `withIconColor`,
  `getConfirmButton` (Task 3); `TeleportCommandStyle.format`, `legend` (Task 4);
  `LocalTeleportCommand.problem` (Task 1, for Save's state only in this task).
- Produces (package-private unless marked), used by Task 6 and the tests:
  - `public LocalWaypointProfilesScreen(Screen parent, WaypointClientMod client)`, which closes with
    `MinecraftClientHelper.setScreen(parent)`, and `LocalWaypointProfilesScreen(WaypointClientMod client, Runnable close)`.
  - `enum Dialog { CREATE, RENAME, DELETE, DISCARD }`
  - `record ProfileRow(TranslucentButton use, IconButton rename, IconButton delete, WidgetStack actions)`
    and `ProfileRow row(UUID id)`
  - `TranslucentButton newProfileButton()`, `TranslucentTextField commandField()`,
    `TranslucentButton saveButton()`, `TranslucentButton doneButton()`, `SettingsListWidget settingsList()`,
    `TranslucentTextField nameField()`
  - `@Nullable Dialog openDialog()`, `@Nullable ConfirmationDialog dialogWidget()`,
    `List<Component> dialogLines()` (the open dialog's text lines, top to bottom)
  - `Component listTitle()`, `Component activeStatus()`, `Component statusMessage()`
    (`Component.empty()` when none), `boolean statusIsError()`
  - `void layoutContent()`
  - `static final class ProfileNames { static @Nullable String clean(String input);
    static @Nullable Profile conflict(List<Profile> profiles, String name, @Nullable UUID except); }`

**Screen decisions** (the spec's Layout and Behavior sections give the rest):
- Entries: `Header(list_section, place)`; one `Row(Component.literal(name), control).action(actions)`
  per profile in `profiles.profiles()` order, where control is the shared `inUse` `ScalableText`
  (`SUCCESS`, row scale) for the active profile and that profile's Use button otherwise. Only the
  active row gets `.tooltip(reason)`. Then `WideRow(newProfileButton)`, `Header(command_section,
  active name)`, `WideRow(command)`, and `Row(TeleportCommandStyle.legend(), saveButton)` with
  `labelColor(TEXT_MUTED)`.
- Row controls are created once per profile and cached by UUID; deleted profiles leave the cache.
  Text buttons are fitted, then scaled to `SettingsListWidget.ROW_TEXT_SCALE` like
  `ClientConfigScreen.scaleRowButton`. Icon buttons are 9×9 with `withIconPadding(1)` and the full
  texture: `EDIT_ICON`, and `REMOVE_ICON` with `withIconColor(DANGER)`. Each pair sits in a
  horizontal `WidgetStack` with a 3-pixel gap. Tooltips name the profile (`use.tooltip`,
  `rename.tooltip`, `delete.tooltip`) and refresh after renames.
- After every operation, `refresh()` rebuilds the entries from the cached controls. Once `init` has
  run, it then clears and re-registers the widgets in display order: list rows, New profile,
  command, Save, Done, the open dialog's buttons, then the name field for New/Rename. It keeps the
  list's scroll position. Focus moves with `requestFocus`/`applyPendingFocus`, copied from
  `ClientConfigScreen`; its package-private static `keepFocusVisible` and `handOverFocus` are reused.
- `perform(Operation)` returns whether it succeeded and keeps today's rules. It checks
  `currentSession()`, recaptures the generation afterwards, calls `client.refreshCurrentCollections()`,
  and shows `waypoint.local.storage_failed` on `IOException` and `status.obsolete` on
  `IllegalArgumentException`/`IllegalStateException`. A failed operation keeps the dialog and its text.
- The command field loads the active profile's command after Use, Create and deleting the active
  profile. Renames and deleting another profile keep its text. The field gets
  `setTextFormatter((shown, offset) -> TeleportCommandStyle.format(command.getValue(), shown, offset))`
  once, in the constructor.
- Focus: opening New or Rename focuses the name field; Delete and Discard focus Cancel. Closing a
  dialog returns focus to the control that opened it while that control is still in the list.
  After a confirmed operation focus goes to:
  - Use: that row's rename button.
  - Create: the new row's rename button.
  - Rename: the opener.
  - Delete: the row now at the deleted row's index, its Use button or, for the active row, its
    rename button; New profile when no row is left at that index.
- In New and Rename, a stripped name that's taken by another profile calls
  `nameField.setInvalid(true)` and adds the `name.taken` line naming that profile. Enter or keypad
  Enter on the focused name field confirms when the confirm button is active.
- Without an active profile at construction, the screen builds no profile rows, shows
  `status.obsolete` as an error and leaves only Done active.
- Dialogs: `ConfirmationDialog` with a vertical `WidgetStack` (gap 8) of `ScalableText` lines
  wrapped to `min(280, width - 48)` and, for New and Rename, the shared name field. The dialog is
  rebuilt whenever the name error appears, changes or disappears. Rename selects the whole name
  with `setCursorPosition(length)` then `setHighlightPos(0)`.
- `keyPressed` handles these keys before calling `super`, which only sees the others. Escape closes
  the open dialog; otherwise `dismissFocusedInput()`; otherwise `onClose()`. Tab calls
  `settings.revealTabTarget(this, !ClientConfigScreen.isShiftDown(...))`.
- Done uses `CommonComponents.GUI_DONE`. `ProfileChoices`, the combobox and its popup handling go away.

**Locale changes** (all six files):
- Add: `list_section` "Profiles for %s"; `in_use.automatic` "In use · automatic"; `in_use.manual`
  "In use · picked by you"; `reason.fallback`, `reason.backend`, `reason.manual` with the spec's
  tooltip texts; `use` "Use"; `use.tooltip` "Switch to %s and show its Local waypoints.";
  `rename.tooltip` "Rename %s"; `delete.tooltip` "Delete %s"; `command_section` "Teleport command ·
  %s"; `placeholders` "Placeholders: %s"; `new.body`, `rename.title`, `rename.body`, `delete.body`,
  `delete.active_note`, `delete.other_collections` and `name.taken` with the spec's dialog texts;
  `status.switched`, `status.created`, `status.renamed` (two arguments), `status.deleted`,
  `status.deleted_switched` (two arguments), `status.command_saved` and `status.obsolete` with the
  spec's statuses. Every key is under `waypoint.profile.`.
- Change: `manage` "Local waypoint profiles", `save_command` "Save", `create_confirm` "Create",
  `delete_confirm_action` "Delete".
- Retire, and append to `RETIRED_KEYS`: `waypoint.profile.` + `select`, `create`, `rename`,
  `delete_confirm`, `invalid`, `switch`, `search`, `search_hint`, `switch_hint`, `already_active`,
  `introduction`, `create_section`, `create_hint`, `current_section`, `editing`, `teleport_section`,
  `teleport_hint`, `tokens`, `command_hint`, `remove_section`, `remove_hint`, `delete_named`; and
  `server_waypoint.done.button`, which also leaves the filter of `collectionAndProfileControlsAreTranslatedInEveryLocale`.
- `LABEL_KEYS` gains `waypoint.profile.name`, `rename.body`, `list_section`, `command_section`,
  `in_use.automatic` and `in_use.manual` (with the prefix). `theMessagesTakeTheArgumentsTheScreensPass`
  asserts `List.of(1, 2)` for `status.renamed` and `status.deleted_switched`, and `List.of(1)` for
  `list_section`, `command_section`, `use.tooltip` and `name.taken`.

- [x] **Step 1: Write the failing tests.** Keep the existing fixture (`setup`, `restore`, `field`,
  `setStatic`) but build the screen with `new LocalWaypointProfilesScreen(client, () -> closed = true)`.
  It joins with a stored `account` field and keeps the `survival` UUID it creates. Its profiles are
  `Default` (`original`) and `Survival` (active, `MANUAL`). Helpers:
  - `assertStatus(key, args...)` compares `screen.statusMessage()` with
    `Component.translatable(key, args)`; names are passed as `String` arguments.
  - `profile(id)` finds a profile in `client.localProfiles().profiles()`.
  - `colors` matches Task 3.

```java
@Test void theCommandFieldColorsItsPlaceholders() {
    screen.commandField().setValue("{x} ~");
    assertEquals(List.of(0xFF7B72, 0xFF7B72, 0xFF7B72), colors(screen.commandField().formatShown("{x}", 0)));
}

@Test void aScreenOpenedWithoutALocalProfileCanOnlyClose() throws Exception {
    client.localProfiles().leave();
    var empty = new LocalWaypointProfilesScreen(client, () -> closed = true);
    assertTrue(empty.statusIsError());
    assertFalse(empty.newProfileButton().active);
    empty.doneButton().onClick(0, 0);
    assertTrue(closed);
}

@Test void useSwitchesToThatProfileAndMovesFocusToItsRenameButton() {
    screen.row(original).use().onClick(0, 0);
    assertEquals(original, client.localProfiles().active().id());
    assertStatus("waypoint.profile.status.switched", "Default");
    assertEquals(screen.row(original).rename(), screen.getFocused());
}

@Test void theListNamesTheServerAndTheActiveRowSaysWhyItIsInUse() throws Exception {
    assertEquals(Component.translatable("waypoint.profile.list_section", "test"), screen.listTitle());
    assertEquals(Component.translatable("waypoint.profile.in_use.manual"), screen.activeStatus());
    client.localProfiles().leave();
    client.localProfiles().joinMultiplayer(account, "test");
    var reopened = new LocalWaypointProfilesScreen(client, () -> { });
    assertEquals(Component.translatable("waypoint.profile.in_use.automatic"), reopened.activeStatus());
}

@Test void renamingAnotherProfileKeepsTheActiveOneAndAllowsCaseChangesOfItsOwnName() {
    UUID active = client.localProfiles().active().id();
    screen.row(original).rename().onClick(0, 0);
    assertEquals(LocalWaypointProfilesScreen.Dialog.RENAME, screen.openDialog());
    assertEquals(screen.nameField(), screen.getFocused());
    screen.nameField().setValue(" survival ");
    assertFalse(screen.dialogWidget().getConfirmButton().active);
    assertTrue(screen.dialogLines().contains(Component.translatable("waypoint.profile.name.taken", "Survival")));
    screen.nameField().setValue("DEFAULT");
    screen.dialogWidget().getConfirmButton().onClick(0, 0);
    assertEquals("DEFAULT", profile(original).name());
    assertEquals(active, client.localProfiles().active().id());
    assertStatus("waypoint.profile.status.renamed", "Default", "DEFAULT");
    assertNull(screen.openDialog());
    assertEquals(screen.row(original).rename(), screen.getFocused());
}

@Test void newProfileNeedsAUniqueNameAndEnterCreatesIt() {
    screen.newProfileButton().onClick(0, 0);
    screen.nameField().setValue("   ");
    assertFalse(screen.dialogWidget().getConfirmButton().active);
    screen.nameField().setValue("Exploration");
    screen.setFocused(screen.nameField());
    assertTrue(screen.keyPressed(InputConstants.KEY_RETURN, 0, 0));
    assertEquals("Exploration", client.localProfiles().active().name());
    assertStatus("waypoint.profile.status.created", "Exploration");
    assertEquals(screen.row(client.localProfiles().active().id()).rename(), screen.getFocused());
}

@Test void deletingAnotherProfileDoesNotSwitch() {
    UUID active = client.localProfiles().active().id();
    screen.row(original).delete().onClick(0, 0);
    assertEquals(screen.dialogWidget().getCancelButton(), screen.getFocused());
    assertFalse(screen.dialogLines().contains(Component.translatable("waypoint.profile.delete.active_note")));
    screen.dialogWidget().getConfirmButton().onClick(0, 0);
    assertEquals(active, client.localProfiles().active().id());
    assertEquals(1, client.localProfiles().profiles().size());
    assertStatus("waypoint.profile.status.deleted", "Default");
    assertEquals(screen.row(active).rename(), screen.getFocused());
}

@Test void deletingTheActiveProfileSaysWhichOneTookOver() {
    UUID survival = client.localProfiles().active().id();
    screen.row(survival).delete().onClick(0, 0);
    assertTrue(screen.dialogLines().contains(Component.translatable("waypoint.profile.delete.active_note")));
    screen.dialogWidget().getConfirmButton().onClick(0, 0);
    assertEquals(original, client.localProfiles().active().id());
    assertStatus("waypoint.profile.status.deleted_switched", "Survival", "Default");
}

@Test void deletingTheOnlyProfileSwitchesToANewDefault() throws Exception {
    client.localProfiles().delete(original);
    var only = new LocalWaypointProfilesScreen(client, () -> { });
    UUID survival = client.localProfiles().active().id();
    only.row(survival).delete().onClick(0, 0);
    only.dialogWidget().getConfirmButton().onClick(0, 0);
    assertEquals("Default", client.localProfiles().active().name());
    assertNotEquals(survival, client.localProfiles().active().id());
    assertEquals(Component.translatable("waypoint.profile.status.deleted_switched", "Survival", "Default"), only.statusMessage());
}

@Test void duplicateNamesInStorageStaySeparateRows() throws Exception {
    client.localProfiles().rename(original, "Survival");
    var duplicates = new LocalWaypointProfilesScreen(client, () -> { });
    duplicates.row(original).use().onClick(0, 0);
    assertEquals(original, client.localProfiles().active().id());
}

@Test void staleScreensIgnoreTheirButtons() throws Exception {
    client.localProfiles().create("Elsewhere");
    screen.tick();
    assertFalse(screen.row(original).use().active);
    assertTrue(screen.statusIsError());
    screen.row(original).use().onClick(0, 0);
    assertEquals("Elsewhere", client.localProfiles().active().name());
}

@Test void rowsKeepTheirControlsInsideThePanel() throws Exception {
    client.localProfiles().rename(original, "W".repeat(128));
    for (int[] size : new int[][]{{320, 240}, {480, 270}}) {
        var sized = new LocalWaypointProfilesScreen(client, () -> { });
        sized.width = size[0];
        sized.height = size[1];
        sized.layoutContent();
        var panel = sized.settingsList();
        int panelRight = panel.getVisualX() + panel.getVisualWidth() - SettingsListWidget.PANEL_PADDING;
        Integer actionsRight = null;
        UUID active = client.localProfiles().active().id();
        for (var profile : client.localProfiles().profiles()) {
            var row = sized.row(profile.id());
            int right = row.delete().getX() + row.delete().getWidth();
            if (actionsRight == null) actionsRight = right;
            assertEquals(actionsRight, right);
            assertTrue(right <= panelRight);
            if (!profile.id().equals(active)) assertTrue(row.use().getX() + row.use().getWidth() <= row.rename().getX());
        }
        assertTrue(sized.doneButton().getY() + sized.doneButton().getHeight() <= size[1]);
        var widgets = new ArrayList<AbstractWidget>();
        panel.visitWidgets(widgets::add);
        assertEquals(widgets.size(), new HashSet<>(widgets).size());
    }
}
```

  In `LocalWaypointProfilesScreenTest`, replace the two `ProfileChoices` tests with:

```java
@Test void namesAreStrippedAndBlankNamesAreNone() {
    assertEquals("Creative", LocalWaypointProfilesScreen.ProfileNames.clean("  Creative "));
    assertNull(LocalWaypointProfilesScreen.ProfileNames.clean("   "));
}

@Test void conflictsIgnoreCaseAndSpacesButNotTheRenamedProfile() {
    var profiles = List.of(FIRST, SECOND);
    assertEquals(FIRST, LocalWaypointProfilesScreen.ProfileNames.conflict(profiles, " survival WORLD ", null));
    assertNull(LocalWaypointProfilesScreen.ProfileNames.conflict(profiles, "SURVIVAL WORLD", FIRST.id()));
    assertEquals(SECOND, LocalWaypointProfilesScreen.ProfileNames.conflict(profiles, "creative", FIRST.id()));
}
```

- [x] **Step 2: Run and see them fail.** `GW :mods:26.1.2-fabric:test --tests '*LocalWaypointProfilesScreen*Test' --tests '*WaypointFormTranslationTest'`.
  Expected: compilation fails on the new constructor and accessors.
- [x] **Step 3: Implement** the screen, the locale changes and the test-list updates above.
- [x] **Step 4: Run and see them pass.** The same command, then the same filters on `1.20.1-fabric`.
  Expected: all pass.

### Task 6: Command editing and unsaved edits

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/LocalWaypointProfilesScreen.java`
- Modify: the six lang files, and `docs/tips/gui/local-guide.md` (replace the
  `LocalWaypointProfilesScreen` paragraphs under "Collection manager and composed markers")
- Test: `LocalWaypointProfilesScreenInteractionTest.java`, `WaypointFormTranslationTest.java`

**Interfaces:**
- Consumes: Task 5's accessors, `LocalTeleportCommand.problem`/`ProblemKind` (Task 1).
- Produces: `IconButton resetButton()`.

**Decisions:**
- The legend row gets `.action(resetButton, () -> !DEFAULT_TELEPORT_COMMAND.equals(<saved command>))`
  and `.tooltip(placeholders.tooltip)`. The reset button is styled like `ClientConfigScreen`'s:
  9×9, `RESET_ICON`, `withoutBackground()`, `withIconPadding(2)`,
  `withIconRegion(9, 9, 46, 43, 64, 64)`, with the tooltip `reset_command` naming the default.
- Every command edit recomputes `problem()`. A problem sets the field invalid and shows
  `"waypoint.profile.problem." + kind.name().toLowerCase(Locale.ROOT)` as an error status, with
  the problem's text as its argument for `UNKNOWN`. Once the problem is fixed, that status clears.
- An edit is unsaved while the field differs from the active profile's saved command.
  `use(id)`, `onClose()` (Done, and Escape through `keyPressed`) open `DISCARD` first. Discard
  restores the saved text, then switches or runs `close`. The New dialog adds the `discard.note`
  line instead. An out-of-date screen closes without asking.
- In `keyPressed`, Enter and keypad Enter on the focused command field save when Save is active.
- Removing the active profile and resetting don't ask.

**Locale changes** (all six files): add `placeholders.tooltip` (the spec's three lines, joined by
`\n`), `reset_command` "Reset to the default: %s", `status.command_reset`, `discard.title`,
`discard.body`, `discard.confirm` "Discard", `discard.note` and one `problem.<kind>` key per
`ProblemKind`, all with the spec's texts and under `waypoint.profile.`. Add a translation test
that enumerates `LocalTeleportCommand.ProblemKind.values()`, like `everyCheckMessageIsTranslatedInEveryLocale`,
and assert `List.of(1)` for `problem.unknown`, `reset_command` and `discard.body` in
`theMessagesTakeTheArgumentsTheScreensPass`.

- [x] **Step 1: Write the failing tests:**

```java
@Test void enterSavesTheCommandAndResetRestoresTheDefault() {
    screen.commandField().setValue("/tp @s {x} {y} {z}");
    screen.setFocused(screen.commandField());
    assertTrue(screen.keyPressed(InputConstants.KEY_RETURN, 0, 0));
    assertEquals("/tp @s {x} {y} {z}", client.localProfiles().active().teleportCommand());
    assertStatus("waypoint.profile.status.command_saved");
    screen.resetButton().onClick(0, 0);
    assertEquals(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, client.localProfiles().active().teleportCommand());
    assertEquals(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, screen.commandField().getValue());
    assertStatus("waypoint.profile.status.command_reset");
}

@Test void templateProblemsShowInTheFooterAndKeepSaveOff() {
    screen.commandField().setValue("tp {pitch}");
    assertTrue(screen.statusIsError());
    assertStatus("waypoint.profile.problem.unknown", "{pitch}");
    assertFalse(screen.saveButton().active);
    screen.commandField().setValue("tp {x}");
    assertEquals(Component.empty(), screen.statusMessage());
    assertTrue(screen.saveButton().active);
}

@Test void useWithAnUnsavedEditAsksBeforeDiscardingIt() {
    String saved = client.localProfiles().active().teleportCommand();
    screen.commandField().setValue("/tp @s {x} 64 {z}");
    screen.row(original).use().onClick(0, 0);
    assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
    screen.dialogWidget().getCancelButton().onClick(0, 0);
    assertNotEquals(original, client.localProfiles().active().id());
    assertEquals("/tp @s {x} 64 {z}", screen.commandField().getValue());
    screen.row(original).use().onClick(0, 0);
    screen.dialogWidget().getConfirmButton().onClick(0, 0);
    assertEquals(original, client.localProfiles().active().id());
    assertEquals(client.localProfiles().active().teleportCommand(), screen.commandField().getValue());
    assertEquals(saved, profile(survival).teleportCommand());
}

@Test void doneAndEscapeAskBeforeDiscardingAndDiscardCloses() {
    screen.commandField().setValue("/tp @s 0 0 0");
    screen.doneButton().onClick(0, 0);
    assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
    assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
    assertNull(screen.openDialog());
    assertFalse(closed);
    assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
    assertEquals(LocalWaypointProfilesScreen.Dialog.DISCARD, screen.openDialog());
    screen.dialogWidget().getConfirmButton().onClick(0, 0);
    assertTrue(closed);
}

@Test void newProfileSaysTheUnsavedEditWillBeDiscarded() {
    screen.commandField().setValue("/tp @s 0 0 0");
    screen.newProfileButton().onClick(0, 0);
    assertTrue(screen.dialogLines().contains(Component.translatable("waypoint.profile.discard.note")));
    screen.nameField().setValue("Fresh");
    screen.dialogWidget().getConfirmButton().onClick(0, 0);
    assertEquals(LocalWaypointProfiles.DEFAULT_TELEPORT_COMMAND, screen.commandField().getValue());
}

@Test void deletingTheActiveProfileWithAnUnsavedEditDoesNotAsk() {
    screen.commandField().setValue("/tp @s 0 0 0");
    screen.row(client.localProfiles().active().id()).delete().onClick(0, 0);
    assertEquals(LocalWaypointProfilesScreen.Dialog.DELETE, screen.openDialog());
}

@Test void aSessionEndingUnderADialogLeavesOnlyCancelAndDone() throws Exception {
    screen.commandField().setValue("/tp @s 0 0 0");
    screen.row(original).delete().onClick(0, 0);
    client.localProfiles().leave();
    screen.tick();
    var dialog = screen.dialogWidget();
    assertFalse(dialog.getConfirmButton().active);
    assertTrue(dialog.getCancelButton().active);
    dialog.getConfirmButton().onClick(0, 0);
    dialog.getCancelButton().onClick(0, 0);
    screen.doneButton().onClick(0, 0);
    assertNull(screen.openDialog());
    assertTrue(closed);
}
```

  The helpers and the `survival` and `closed` fields come from Task 5's fixture.

- [x] **Step 2: Run and see them fail.** `GW :mods:26.1.2-fabric:test --tests '*LocalWaypointProfilesScreen*Test' --tests '*WaypointFormTranslationTest'`.
  Expected: compilation fails (`resetButton`); once that compiles, the new assertions fail.
- [x] **Step 3: Implement** the decisions, the locale keys and the guide update.
- [x] **Step 4: Run and see them pass** on `26.1.2-fabric`, then with the same filters on `1.20.1-fabric`.

### Task 7: Verification and records

**Files:**
- Create: `docs/features/private-waypoint/validation/2026-10-30-local-profile-list.md`
- Modify: `docs/features/private-waypoint/README.md` (link the validation record),
  `docs/features/private-waypoint/validation/execution/progress.md` (one dated section),
  `docs/features/private-waypoint/specs/2026-10-29-local-profile-layout-design.md` and
  `plans/2026-10-29-local-profile-layout.md` (one "Superseded by" line each, linking the new spec and plan)

- [x] **Step 1: Run the full active suite:** `GW :mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test`.
  Expected: BUILD SUCCESSFUL and no failures, errors or skips. Record the test count from
  `mods/versions/26.1.2-fabric/build/test-results/test`.
- [x] **Step 2: Run the older branch:** `GW :mods:1.20.1-fabric:test --tests '*LocalWaypointProfiles*' --tests '*LocalTeleportCommandTest' --tests '*TeleportCommandStyleTest' --tests '*SuggestingTextInputTest' --tests '*IconButtonTest' --tests '*ConfirmationDialogTest' --tests '*WaypointFormTranslationTest' --tests '*ClientWaypointAccessTest'`. Expected: all pass.
- [x] **Step 3: Compile the loader targets:** `GW :mods:1.20.1-forge:compileJava :mods:1.21.7-neoforge:compileJava :mods:26.3-neoforge:compileJava`. Expected: BUILD SUCCESSFUL.
- [x] **Step 4: Check the tree.** Look for trailing whitespace and tabs in touched files, balanced
  Stonecutter markers in `SuggestingTextInput.java`, duplicate keys in the six lang files, and
  that `mods/stonecutter.gradle.kts` still names `26.1.2-fabric` as active. Run `git status --short`
  and name any files this plan didn't touch.
- [x] **Step 5: Write the validation record.** Include the commands, counts and log links. State
  that no live in-game check ran (the user's decision) and that pixel layout, IME and real mouse
  input remain unverified.
