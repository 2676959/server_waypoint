# Waypoint Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let players select namespaced item or VoxelMap icons for waypoints and see them in waypoint lists and world markers, with native VoxelMap sync and a default VoxelMap fallback.

**Architecture:** Store one nullable `NamespacedId` record in the UI-neutral waypoint model and carry it through edits, commands, uploads, and the remote catalog. JSON and wire codecs encode it as one canonical string and parse it once on input. The `mods` client converts the record's components to the target Minecraft identifier type and resolves it against the live item registry or VoxelMap images; VoxelMap conversion changes only the external `imageSuffix`.

**Tech Stack:** Java 17, GSON, Brigadier, Netty, Minecraft Fabric/Forge/NeoForge Stonecutter targets, Paper, JUnit, VoxelMap's existing compile dependency.

**Spec:** [Waypoint Icons Design](../specs/2026-09-23-waypoint-icons-design.md)

## Global Constraints

- Use Java 17, four-space indentation, GSON, Kotlin DSL Gradle, and the current Stonecutter matrix in `settings.gradle.kts`.
- Keep client registry and drawing code in `mods`; `common` and Paper must not depend on Minecraft client classes.
- Store `icon` as nullable `NamespacedId(namespace, path)`; `null` retains the initials marker. Serialize it as canonical `namespace:path` and reserve `voxelmap:` for native VoxelMap images.
- Keep `NamespacedId` in `common/util`, independent of waypoint, GSON, Minecraft, and Bukkit policy. Convert with `ResourceLocationHelper.mcId(id.namespace(), id.path())` in `mods`, or `new NamespacedKey(id.namespace(), id.path())` in Paper only when Paper needs an icon key.
- Keep the optional JSON field absent for unselected icons and accept old waypoint JSON with no field.
- Do not add an old/new wire-format branch; bump changed protocols and deploy matching builds.
- Read `docs/tips/gui/local-guide.md` before GUI edits and update it when the new picker/resolver API is added.
- Do not commit unless the user separately requests a commit. At each task boundary, run `rtk git diff --check` and record test results; leave the worktree uncommitted.

## Review Focus

- A syntactically valid item ID that is absent from one client's registry must remain stored and render initials there; Task 5 tests this.
- A malformed or oversized ID arriving through JSON, a command, or a packet must not become a resource path or crash rendering; Tasks 1, 2, and 3 test this.
- A VoxelMap suffix containing a slash, colon, or traversal segment must be ignored on upload and exported as the default image; Task 4 tests this.
- An icon-only edit must increase the list revision, synchronize, and survive a save/reload; Tasks 1 and 2 test this.
- A dense world-marker scene with mixed item, VoxelMap, and initials icons must preserve depth/occlusion and clear cached references on reload; Task 6 tests this.

---

## File map and task boundaries

| Unit | Files | Responsibility |
| --- | --- | --- |
| Identifier/model | `common/.../util/NamespacedId.java`, `core/waypoint/WaypointIconPolicy.java`, `SimpleWaypoint.java`, `WaypointList.java` | Reusable parsed value; waypoint-specific length, JSON fallback, state and mutations |
| Write APIs | `common/.../core/edit/WaypointPatch.java`, `command/CoreWaypointCommand.java`, `util/StringCommandBuilder.java` | Add/edit/clear icon from GUI and commands |
| Transfer | `common/.../core/network/codec/{SimpleWaypointCodec,WaypointPatchCodec}.java`, `ProtocolVersion.java`, `crossserver/{RemoteWaypointSnapshot.java,catalog/CatalogSource.java,protocol/ApplicationCodec.java,CrossServerProtocol.java}` | Local and remote wire data |
| Map integration | `mods/.../client/integrations/VoxelMapWaypointHelper.java`, `common/.../core/network/upload/UploadCoordinator.java` | Native image suffix import/export and merge |
| Picker | `mods/.../client/gui/widgets/WaypointIconPicker.java`, `mods/.../client/gui/screens/{AbstractWaypointPropertiesScreen,WaypointAddScreen,WaypointEditScreen}.java` | Search, selection, clear/reset, submission |
| Presentation | `mods/.../client/gui/render/WaypointIconRenderer.java`, `mods/.../client/gui/widgets/{WaypointListWidget,WaypointDetailsWidget}.java`, `mods/.../client/gui/screens/RemoteWaypointPanel.java`, `mods/.../client/render/OptimizedWaypointRenderer.java` | Registry/resource resolution and display |

Paths abbreviated as `common/...` and `mods/...` in the table are fully spelled out below. Do not move UI-only helpers into the top-level `common` project. Inspect existing renderer and GUI Stonecutter branches before choosing versioned calls.

### Task 1: Model and persistent icon state

**Files:**
- Create: `common/src/main/java/_959/server_waypoint/util/NamespacedId.java`
- Create: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointIconPolicy.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/waypoint/SimpleWaypoint.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointList.java`
- Test: `common/src/test/java/_959/server_waypoint/util/NamespacedIdTest.java`
- Test: `common/src/test/java/_959/server_waypoint/core/waypoint/SimpleWaypointIconTest.java`

**Interfaces:**
- Produces `record NamespacedId(String namespace, String path)` and `NamespacedId.parse(String): NamespacedId` in `util`. The canonical constructor validates the same namespace/path character sets as Minecraft identifiers and empty components; `parse` requires one colon and `toString()` returns the canonical form. It contains no icon, GSON, Minecraft, or Paper policy.
- Produces `WaypointIconPolicy.validate(@Nullable NamespacedId): @Nullable NamespacedId` with `MAX_LENGTH = 256` and `WaypointIconPolicy.JsonAdapter` for the waypoint field's string JSON representation and invalid-value fallback.
- Produces `SimpleWaypoint.icon(): @Nullable NamespacedId`; the full constructor takes `@Nullable NamespacedId icon` after `description`, while constructors for an unselected icon supply `null` internally. Copy, snapshot, `compareProperties`, and `updateProperties` include it.

- [ ] **Step 1: Write failing model tests.** Add JUnit cases with these concrete assertions:

```java
assertEquals(new NamespacedId("minecraft", "diamond"), NamespacedId.parse("minecraft:diamond"));
assertEquals("examplemod:blue_gem", NamespacedId.parse("examplemod:blue_gem").toString());
assertEquals("other", NamespacedId.parse("example:other").path());
assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("Minecraft:Diamond"));
assertThrows(IllegalArgumentException.class, () -> NamespacedId.parse("voxelmap:bad%icon"));
assertThrows(IllegalArgumentException.class, () -> WaypointIconPolicy.validate(NamespacedId.parse("a:" + "x".repeat(255))));
assertThrows(IllegalArgumentException.class, () -> new NamespacedId("Minecraft", "diamond"));
SimpleWaypoint selected = waypointWithIcon(NamespacedId.parse("voxelmap:star"));
assertEquals(NamespacedId.parse("voxelmap:star"), new SimpleWaypoint(selected).icon());
assertEquals("voxelmap:star", GSON.toJsonTree(selected).getAsJsonObject().get("icon").getAsString());
assertEquals(selected.icon(), GSON.fromJson(GSON.toJson(selected), SimpleWaypoint.class).icon());
JsonObject oldJson = GSON.toJsonTree(waypointWithIcon(null)).getAsJsonObject();
oldJson.remove("icon");
assertNull(GSON.fromJson(oldJson, SimpleWaypoint.class).icon());
oldJson.addProperty("icon", "bad%icon");
assertNull(GSON.fromJson(oldJson, SimpleWaypoint.class).icon());
```

Test an icon-only `WaypointList.applyWaypointPatchByServer` change after Task 2 adds the patch field.

- [ ] **Step 2: Run the focused tests and confirm they fail for the absent API.** Run `rtk proxy ./gradlew :common:test --tests _959.server_waypoint.util.NamespacedIdTest --tests _959.server_waypoint.core.waypoint.SimpleWaypointIconTest`; expect compilation failure for `NamespacedId`/`icon()`.
- [ ] **Step 3: Implement the model.** Use a UI-neutral record based on Minecraft identifier character rules; validate both direct construction and parsing:

```java
public record NamespacedId(String namespace, String path) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public NamespacedId {
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid namespaced ID");
        }
    }

    public static NamespacedId parse(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Invalid namespaced ID");
        }
        return new NamespacedId(value.substring(0, colon), value.substring(colon + 1));
    }

    @Override public String toString() { return namespace + ":" + path; }
}
```

Add `@Expose @JsonAdapter(WaypointIconPolicy.JsonAdapter.class) private @Nullable NamespacedId icon;` to `SimpleWaypoint`; the adapter serializes one string and parses valid strings, logs a malformed JSON value, and returns null for it so the rest of the file loads. Keep the length rule in `WaypointIconPolicy.validate` and call it from waypoint constructors, updates, and patch handling. Copy the record in `State`, copy constructor, and updates. Keep old JSON field absence natural through GSON. Update every model construction/copy site that would otherwise drop a selected icon; do not create a legacy network decoder.

```java
public synchronized @Nullable NamespacedId icon() { return icon; }
// In WaypointIconPolicy:
public static @Nullable NamespacedId validate(@Nullable NamespacedId id) {
    if (id != null && id.toString().length() > MAX_LENGTH) {
        throw new IllegalArgumentException("Waypoint icon ID is too long");
    }
    return id;
}
// In the icon field's GSON adapter:
return new JsonPrimitive(icon.toString()); // serialize a selected icon as one JSON string
// Invalid persisted values become null on deserialize.
try { return WaypointIconPolicy.validate(NamespacedId.parse(json.getAsString())); }
catch (IllegalArgumentException invalid) { LOGGER.warn("Ignoring invalid waypoint icon", invalid); return null; }
```
- [ ] **Step 4: Run `NamespacedIdTest`, `SimpleWaypointIconTest`, `SimpleWaypointExtraInfoTest`, and `WaypointFilesManagerConcurrencyTest`; run `rtk git diff --check`.** Confirm generic parse/format behavior, the icon round trip, and existing snapshot behavior pass.

### Task 2: Command creation and edit patches

**Files:**
- Modify: `common/src/main/java/_959/server_waypoint/core/edit/WaypointPatch.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/waypoint/WaypointList.java`
- Modify: `common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java`
- Modify: `common/src/main/java/_959/server_waypoint/util/StringCommandBuilder.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/network/upload/UploadCoordinator.java`
- Test: `common/src/test/java/_959/server_waypoint/core/WaypointPatchTest.java`
- Test: `common/src/test/java/_959/server_waypoint/command/CoreWaypointCommandIconTest.java`
- Test: `common/src/test/java/_959/server_waypoint/util/StringCommandBuilderTest.java`

**Interfaces:**
- Consumes `NamespacedId.parse`, `WaypointIconPolicy.validate`, and `SimpleWaypoint.icon()`.
- Produces `WaypointPatch.icon(): PatchField<NamespacedId>` as its final record component; `SET` carries an already validated record, `CLEAR` stores `null`, `UNCHANGED` retains the current value.
- Produces `CoreWaypointCommand.patchWithIcon(PatchField<NamespacedId>): WaypointPatch`, which fills every other patch component with `PatchField.unchanged()`.
- Produces `/wp edit waypoint <dimension> <list> <waypoint> set icon <namespace:path>` and `... clear icon`. Extend `/wp add` with an `icon <namespace:path>` tail after existing optional metadata, keeping existing add command shapes accepted as ordinary feature behavior.

- [ ] **Step 1: Write failing tests.** In `WaypointPatchTest`, add a patch that only sets `minecraft:diamond`, assert `UPDATED`, `syncNum + 1`, and no change to unrelated fields; then clear and assert `icon() == null`; then save and reload the file manager and assert the selected icon survives. In `CoreWaypointCommandIconTest`, parse both edit forms, reject `Minecraft:Diamond`, and verify an add command with an icon stores it atomically. In `StringCommandBuilderTest`, assert a selected icon emits the exact `icon minecraft:diamond` tail.
- [ ] **Step 2: Run the focused tests.** Run `rtk proxy ./gradlew :common:test --tests _959.server_waypoint.core.WaypointPatchTest --tests _959.server_waypoint.command.CoreWaypointCommandIconTest --tests _959.server_waypoint.util.StringCommandBuilderTest`; expect failure until the icon field and command nodes exist.
- [ ] **Step 3: Implement patch mutation.** Append the component and resolve it inside `applyWaypointPatchByServer`:

```java
PatchField<NamespacedId> icon

NamespacedId newIcon = patch.icon().isClear() ? null
        : patch.icon().isSet() ? WaypointIconPolicy.validate(patch.icon().requiredValue())
        : waypoint.icon();
```

Pass `newIcon` into `compareProperties` and `updateProperties`. Update all existing `new WaypointPatch(...)` call sites and static patch factories to supply `PatchField.unchanged()`; a changed constructor is internal code migration, not wire compatibility.
- [ ] **Step 4: Implement Brigadier nodes and add builder.** Add the two icon edit nodes beside the existing `display-name` nodes. Extend the existing optional add-argument tree with a literal `icon` and one `StringArgumentType.word()` ID under each valid metadata endpoint; pass the parsed value to `addWaypointDirectly`. `StringCommandBuilder.addCmd` must insert empty keyword/description arguments before `icon` when they are omitted, using the existing `escapeArgument` method. Validate before mutation and return localized command feedback for an invalid ID; add translations to all six `mods/src/main/resources/assets/server_waypoint/lang/*.json` files.

```java
NamespacedId icon = NamespacedId.parse(getString(context, VALUE_ARG));
WaypointPatch patch = patchWithIcon(PatchField.set(icon));
// In StringCommandBuilder.addCmd, after positional metadata:
if (waypoint.icon() != null) sb.append(" icon ").append(waypoint.icon());
```
- [ ] **Step 5: Preserve icons through upload merge.** Update the `UploadCoordinator` constructors and merge methods so Xaero imports leave an existing icon untouched, VoxelMap imports can supply a valid native icon, and a newly imported waypoint receives its uploaded icon. Add test cases for an icon-bearing target and icon-free Xaero upload.
- [ ] **Step 6: Rerun focused tests and `rtk git diff --check`.** Verify the command string parses through the same Brigadier tree the server uses.

### Task 3: Client packets and remote catalogs

**Files:**
- Modify: `common/src/main/java/_959/server_waypoint/core/network/codec/SimpleWaypointCodec.java`
- Modify: `common/src/main/java/_959/server_waypoint/core/network/codec/WaypointPatchCodec.java`
- Modify: `common/src/main/java/_959/server_waypoint/ProtocolVersion.java`
- Modify: `common/src/main/java/_959/server_waypoint/crossserver/RemoteWaypointSnapshot.java`
- Modify: `common/src/main/java/_959/server_waypoint/crossserver/catalog/CatalogSource.java`
- Modify: `common/src/main/java/_959/server_waypoint/crossserver/protocol/ApplicationCodec.java`
- Modify: `common/src/main/java/_959/server_waypoint/crossserver/CrossServerProtocol.java`
- Test: `common/src/test/java/_959/server_waypoint/core/network/codec/NetworkCodecTest.java`
- Test: `common/src/test/java/_959/server_waypoint/crossserver/protocol/ApplicationCodecTest.java`
- Test: `common/src/test/java/_959/server_waypoint/crossserver/catalog/CatalogSourceTest.java`

**Interfaces:**
- Consumes nullable icon IDs and patch `SET/CLEAR/UNCHANGED`.
- Produces local and remote snapshots with `@Nullable NamespacedId icon`, encoded as presence boolean plus bounded UTF string; remote catalog fingerprints and byte budgets include the canonical string.

- [ ] **Step 1: Add failing packet tests.** Round-trip a `SimpleWaypoint` with `examplemod:gem`, one with `null`, and patches that set and clear an icon. Assert decoding an invalid/oversized icon fails through the existing packet rejection path rather than reaching rendering.
- [ ] **Step 2: Add failing remote tests.** Round-trip a remote snapshot with `voxelmap:star`; assert `CatalogSource` carries the ID, the metadata fingerprint changes on an icon-only edit, and a remote catalog with many maximum-length IDs respects the configured byte limit.
- [ ] **Step 3: Run the targeted tests.** Run `rtk proxy ./gradlew :common:test --tests _959.server_waypoint.core.network.codec.NetworkCodecTest --tests _959.server_waypoint.crossserver.protocol.ApplicationCodecTest --tests _959.server_waypoint.crossserver.catalog.CatalogSourceTest`; expect failures for the missing field.
- [ ] **Step 4: Update codecs and protocol constants.** Append the optional icon to `SimpleWaypointCodec` and `WaypointPatchCodec` in the same order on encode/decode. Add the field to `RemoteWaypointSnapshot`, `CatalogSource`, and `ApplicationCodec` writer/reader; the remote snapshot constructor calls `WaypointIconPolicy.validate(icon)`. Include icon bytes in all related limits and fingerprints. Increment `ProtocolVersion.PROTOCOL_VERSION` and `CrossServerProtocol.PROTOCOL_VERSION` because payloads change, then update exact-value tests/docs that assert those numbers.

```java
NamespacedId icon = snapshot.icon();
buf.writeBoolean(icon != null);
if (icon != null) UtfStringCodec.encode(buf, icon.toString(), context);
// Decode in the same position:
NamespacedId decodedIcon = byteBuf.readBoolean()
        ? WaypointIconPolicy.validate(NamespacedId.parse(UtfStringCodec.decode(byteBuf, context))) : null;
// Patch codec appends:
PatchFieldCodec.encode(buf, patch.icon(),
        (target, id, nestedContext) -> UtfStringCodec.encode(target, id.toString(), nestedContext), context);
PatchField<NamespacedId> decodedPatchIcon = PatchFieldCodec.decode(buf,
        (target, nestedContext) -> WaypointIconPolicy.validate(
                NamespacedId.parse(UtfStringCodec.decode(target, nestedContext))), context);
```
- [ ] **Step 5: Run targeted tests and `rtk git diff --check`.** Verify no old/new decode branch was added and note that mixed deployed builds are intentionally rejected.

### Task 4: VoxelMap native image conversion

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/integrations/VoxelMapIconIds.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/integrations/VoxelMapWaypointHelper.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/integrations/VoxelMapIconIdsTest.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/integrations/VoxelMapUploadTest.java`

**Interfaces:**
- Produces `VoxelMapIconIds.fromSuffix(String): @Nullable NamespacedId`, `VoxelMapIconIds.toSuffix(@Nullable NamespacedId): String`, and `VoxelMapIconIds.ids(): List<NamespacedId>`.
- The native suffix catalog is the known built-in suffix set observed in the current VoxelMap dependency (`apple`, `axe`, `boat`, `camera`, `carrot`, `chicken`, `cloud`, `diamond`, `fire`, `fish`, `flower`, `gear`, `heart`, `hoe`, `house`, `key`, `like`, `minecart`, `mushroom`, `person`, `pickaxe`, `record`, `science`, `shovel`, `skull`, `skullsmall`, `small`, `star`, `steak`, `temple`, `tree`, `wheat`, `world`) plus `waypoint` for the empty suffix. Confirm the exact resource names from the resolved jar during execution before finalizing the constant.

- [ ] **Step 1: Write conversion tests.** Assert `fromSuffix("star").equals(NamespacedId.parse("voxelmap:star"))`, `fromSuffix("").equals(NamespacedId.parse("voxelmap:waypoint"))`, `toSuffix(NamespacedId.parse("voxelmap:star")).equals("star")`, and `toSuffix(NamespacedId.parse("minecraft:diamond"))`, `toSuffix(null)`, `toSuffix(NamespacedId.parse("voxelmap:missing"))` all equal `""`. Assert `fromSuffix("../star")`, `fromSuffix("foo/bar")`, and `fromSuffix("star:bad")` return `null`.
- [ ] **Step 2: Run `rtk proxy ./gradlew :mods:26.3-fabric:test --tests _959.server_waypoint.common.client.integrations.VoxelMapIconIdsTest`; expect failure for the absent type.**
- [ ] **Step 3: Implement the pure suffix mapper.** Keep it independent of client rendering and return the native VoxelMap fallback for any non-native Server Waypoint icon:

```java
public static String toSuffix(@Nullable NamespacedId icon) {
    if (new NamespacedId("voxelmap", "waypoint").equals(icon)) return "";
    if (icon == null || !ids().contains(icon)) return "";
    return icon.path();
}
```

- [ ] **Step 4: Connect VoxelMap upload and sync.** In `collectUploadDimension`, read `waypoint.imageSuffix` into the new model field when recognized. In `toVoxelMapWaypoint`, replace the hardcoded `""` constructor argument with `VoxelMapIconIds.toSuffix(simpleWaypoint.icon())`. Extend `VoxelMapUploadTest` to assert valid native upload and invalid suffix omission, plus the default export for an item icon.
- [ ] **Step 5: Run both focused mods tests and `rtk git diff --check`.** Compile the VoxelMap integration against its resolved target jar.

### Task 5: Shared picker and form submission

**Files:**
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPicker.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/AbstractWaypointPropertiesScreen.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointAddScreen.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointEditScreen.java`
- Create: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WaypointIconRenderer.java`
- Modify: `docs/tips/gui/local-guide.md`
- Modify: `mods/src/main/resources/assets/server_waypoint/lang/{en_us,es_es,he_il,zh_cn,zh_hk,zh_tw}.json`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/WaypointIconPickerTest.java`

**Interfaces:**
- Consumes `NamespacedId.parse`, `VoxelMapIconIds.ids`, and the current item registry's keys. Convert a selected record in `mods` through `ResourceLocationHelper.mcId(id.namespace(), id.path())`; do not add Minecraft methods to the record.
- Produces `WaypointIconPicker.getSelectedIcon(): @Nullable NamespacedId`, `setSelectedIcon(@Nullable NamespacedId)`, and a callback `Consumer<@Nullable NamespacedId>`; selection survives filtering and an unavailable selected ID remains visible with an initials fallback preview.
- Produces `WaypointIconRenderer.ResolvedIcon(Kind kind, @Nullable NamespacedId id)` with `Kind` values `INITIALS`, `ITEM`, and `VOXELMAP`; `WaypointIconRenderer.resolve(@Nullable NamespacedId): ResolvedIcon` uses live client resources. `WaypointIconRenderer.classify(@Nullable NamespacedId, Predicate<NamespacedId> itemAvailable, Predicate<NamespacedId> voxelMapImageAvailable): Kind` is the pure decision point for tests. `draw(GuiGraphicsExtractor, ResolvedIcon, int x, int y, int size)` and `drawScaled(GuiGraphicsExtractor, ResolvedIcon, float left, float top, float scale)` are used by Task 6; `drawScaled` draws the same 16×16 source at the projected world-marker scale. Keep item lookup and VoxelMap resource lookup on the client thread. Put catalog filtering in `WaypointIconPicker.filter(List<NamespacedId> ids, String query): List<NamespacedId>` for JUnit tests.

- [ ] **Step 1: Add failing catalog/filter tests.** Build a test catalog containing `minecraft:diamond`, `examplemod:gem`, and `voxelmap:star`. Assert `filter(ids, "GEM")` returns only `examplemod:gem` case-insensitively and removes duplicates. Use picker state tests to assert clear selection, stable selected ID after filtering, and a missing `examplemod:removed` entry displayed as selected with fallback preview.
- [ ] **Step 2: Run `rtk proxy ./gradlew :mods:26.3-fabric:test --tests _959.server_waypoint.common.client.gui.widgets.WaypointIconPickerTest`; expect failure for the missing picker.**
- [ ] **Step 3: Implement the catalog and picker.** Reuse existing `ComboBoxWidget`/search field patterns and the GUI guide's input, popup, focus, and overlay rules. Enumerate `BuiltInRegistries.ITEM` or the version-appropriate registry API on the client; filter out air, keep IDs sorted, and add VoxelMap IDs from the fixed catalog. Render a 16×16 item or native image preview and a label; avoid per-frame registry enumeration. Add a clear choice and visible unresolved selection.

```java
public static List<NamespacedId> filter(List<NamespacedId> ids, String query) {
    String needle = query.toLowerCase(Locale.ROOT);
    return ids.stream().distinct().sorted(Comparator.comparing(NamespacedId::toString))
            .filter(id -> id.toString().contains(needle)).toList();
}
```
- [ ] **Step 4: Wire add/edit forms.** Put the picker into the shared property layout, register it for input, and render its popup at the correct layer. `WaypointAddScreen` constructs the new waypoint with `getSelectedIcon()` so `addCmd` emits the icon tail. `WaypointEditScreen` sends `PatchField.clear()` when the selection changes to null, `set(id)` when it changes to a non-null ID, otherwise `unchanged()`; `resetProperties()` restores the original ID. Add six language files' labels, tooltips, and unavailable notice.

```java
private PatchField<NamespacedId> iconPatch() {
    NamespacedId selected = iconPicker.getSelectedIcon();
    if (Objects.equals(originalIcon, selected)) return PatchField.unchanged();
    return selected == null ? PatchField.clear() : PatchField.set(selected);
}
```
- [ ] **Step 5: Document the widget/resolver contract in `docs/tips/gui/local-guide.md`.** State popup ownership, registration, search semantics, fallback preview, and 26.x/older render entry points. Run the picker test, JSON parse check for all six language files, and `rtk git diff --check`.

### Task 6: Local, remote, and world rendering

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WaypointIconRenderer.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/WaypointRowRenderer.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointListWidget.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointDetailsWidget.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/render/OptimizedWaypointRenderer.java`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/render/WaypointIconRendererTest.java`

**Interfaces:**
- Consumes `SimpleWaypoint.icon()` and `RemoteWaypointSnapshot.icon()`.
- `WaypointIconRenderer.resolve` returns either an item, a VoxelMap image, or `INITIALS`. Missing item IDs and missing image resources return `INITIALS` without changing the model.
- Produces `WaypointRowRenderer.icon(GuiGraphicsExtractor context, WaypointIconRenderer.ResolvedIcon icon, int x, int y, int size): int`; it draws with `WaypointIconRenderer.draw` and returns `size` as the badge width.

- [ ] **Step 1: Write resolver tests.** Assert installed `minecraft:diamond` resolves as item, a recognized VoxelMap image resolves as native when its resource exists, null/missing item/missing native image resolve as initials, and a reload invalidates cached image availability.
- [ ] **Step 2: Run `rtk proxy ./gradlew :mods:26.3-fabric:test --tests _959.server_waypoint.common.client.gui.render.WaypointIconRendererTest`; expect failure for unresolved cases.**
- [ ] **Step 3: Update list and detail presentation.** Replace each local and remote initials badge call with one helper that draws the resolved 16×16 icon or the existing initials badge. Preserve row widths, hover, click hit boxes, labels, and color. For remote data, pass `RemoteWaypointSnapshot.icon()` through the existing row/detail models.

```java
WaypointIconRenderer.ResolvedIcon icon = WaypointIconRenderer.resolve(waypoint.icon());
int badgeWidth = icon.kind() == WaypointIconRenderer.Kind.INITIALS
        ? WaypointRowRenderer.initials(context, font, waypoint.initials(), x, y, bg, fg)
        : WaypointRowRenderer.icon(context, icon, x, y, 16);
```
- [ ] **Step 4: Update the world renderer's tracked state.** Inspect `generateBulkData`, queued commands, render ID allocation/removal, and the 26.x `extractRenderState` branch before editing. Snapshot the resolved icon on the client thread with the other per-waypoint fields, use item rendering or VoxelMap texture drawing at the marker's current projected location/scale, and retain `drawTextBoxAt` for `INITIALS`. Clear icon data whenever render IDs or scenes are cleared. Do not read mutable registries from the render worker. Preserve depth culling and optional detail text.

```java
// In the client-thread command preparation, next to initials and colors:
resolvedIcons[index] = WaypointIconRenderer.resolve(waypoint.icon());
// At the existing projected marker draw site:
if (resolvedIcons[waypointIndex].kind() == WaypointIconRenderer.Kind.INITIALS) {
    drawTextBoxAt(context, initials[waypointIndex], left, top, iconScale,
            initialsTextWidth[waypointIndex], bgWidth, background, foreground);
} else {
    WaypointIconRenderer.drawScaled(context, resolvedIcons[waypointIndex], left, top, iconScale);
}
```
- [ ] **Step 5: Verify render contracts.** Run the resolver test and compile `:mods:1.20.1-fabric`, `:mods:1.21.11-neoforge`, `:mods:26.3-fabric`, and `:mods:26.3-neoforge`; inspect Stonecutter marker balance in every touched file and run `rtk git diff --check`. Perform a live client pass with one item icon, one VoxelMap icon, one unselected icon, and one missing item while moving the camera and opening both local and remote lists. Save screenshots under `docs/features/waypoint-icons/validation/` only if useful to review the visuals.

### Task 7: Matrix and behavior audit

**Files:**
- Modify: `README.md`
- Create: `docs/features/waypoint-icons/validation/2026-09-23-results.md`

**Interfaces:** No new code API; this task verifies all surfaces and records proof limits.

- [ ] **Step 1: Update the README's command and upload sections.** Document namespaced item IDs, `voxelmap:` IDs, `/wp edit waypoint ... set icon` and `clear icon`, VoxelMap's default image for item IDs, and matched-build requirements.
- [ ] **Step 2: Run representative checks.** Run `rtk proxy ./gradlew :common:test :paper:1.21-paper:compileJava :paper:26.2-paper:compileJava :mods:1.20.1-fabric:compileJava :mods:1.20.1-forge:compileJava :mods:1.21.11-neoforge:compileJava :mods:26.3-fabric:compileJava :mods:26.3-neoforge:compileJava`; record each result. Run focused VoxelMap tests under the resolved Fabric target and validate all six language JSON files.
- [ ] **Step 3: Audit construction and presentation sites.** Search `rg 'new SimpleWaypoint\(|new WaypointPatch\(|new RemoteWaypointSnapshot\(|waypoint\.initials\(|imageSuffix' common mods paper` and inspect every hit for lost icon state or missed display. Inspect the cross-server catalog byte budget and protocol constants one final time.
- [ ] **Step 4: Write the validation note.** Record command output, target versions, live client/VoxelMap observations, and any unrun proof separately. Run `rtk git diff --check` and `rtk git status --short`; leave changes uncommitted.

## Self-review checklist

- Spec sections map to Tasks 1–6; Task 7 documents supported use and proof.
- Every Review Focus condition has a concrete test or live check in its owning task.
- `icon` means the same nullable canonical string in model, patch, packets, catalog, picker, and VoxelMap mapper.
- The only intentional fallback for non-native VoxelMap sync is the empty `imageSuffix`; the stored ID is retained.
- No code, commit, deployment, or live-result claim is part of this plan-writing step.
