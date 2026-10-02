# Waypoint Icons Design

## Intent

Let a player choose an icon for each waypoint from the current Minecraft item registry or VoxelMap's built-in waypoint images. Show that choice in Server Waypoint's waypoint list and world markers. Preserve VoxelMap's native image when possible and use its ordinary waypoint image for every other icon.

## Identifier and storage

The optional `icon` property is a `NamespacedId` record in `common` with validated `namespace` and `path` components using Minecraft `Identifier`/`ResourceLocation` syntax. `null` means no icon and preserves the current initials marker. The reusable record parses a canonical, lowercase `namespace:path` string once, and `toString()` writes that form. Waypoint-specific length and JSON fallback rules stay in `WaypointIconPolicy`, outside the record. Item icons use the item's registry ID, such as `minecraft:diamond` or `examplemod:gem`. VoxelMap built-ins use `voxelmap:<suffix>`, where `<suffix>` is the suffix VoxelMap stores in `Waypoint.imageSuffix`; for example, `voxelmap:star` maps to `star`. Reserve the `voxelmap` namespace for these images. The plain default VoxelMap image is `voxelmap:waypoint` in the picker and maps to the empty image suffix.

The command layer uses a generic icon argument type. Fabric, Forge, and NeoForge provide Minecraft's native identifier argument and registry suggestions; Paper provides its native namespaced-key argument and item suggestions. Each platform also suggests the supported `voxelmap:` IDs. Command IDs use the native unquoted identifier syntax and are converted to `NamespacedId` before storage.

The server validates newly selected icons from add/edit commands and client edit packets against its own item registry, accepting registered non-air items and the fixed VoxelMap image catalog. The `voxelmap` namespace is reserved for that catalog. Invalid selections are rejected before mutation; commands report an invalid icon and edit packets return `INVALID_VALUE`. Uploads accept only recognized VoxelMap icons and discard other uploaded icon IDs. The waypoint's GSON adapter writes the record as one string and network codecs send that same string; both parse it into the record once on input. Stored and remote IDs are not checked against the receiving server's registry. A missing item or unavailable VoxelMap image stays stored as selected and uses the current initials presentation on that client. Existing waypoint JSON without `icon` loads as `null`; an invalid JSON icon is logged and treated as unselected so the rest of that waypoint file remains readable. This is ordinary optional-field loading, not a legacy protocol branch. All network participants must run matching builds after the wire-format change.

## Selection and display

Add one icon control to the shared add/edit waypoint properties screens. A picker offers a search box, a clear choice, loaded item registry entries, and the known VoxelMap built-ins. It shows the current selection even when the client's registry lacks that icon. The picker should not require VoxelMap to be installed; render packaged VoxelMap images only if their resources are present, otherwise label them and preview the initials fallback. Selecting or clearing an icon updates the edit patch; Reset restores the original icon. The add screen must send the icon in its add command, and both command and edit APIs must accept it.

Use a single client-side resolver for icon availability and drawing. Render selected icons in local waypoint rows, remote waypoint rows/details when remote catalog data carries the icon, and world markers. Keep existing color, label, distance, selection, hover, and occlusion behavior. Preserve the current initials renderer for no icon or a missing icon. The world renderer must cache icon references with its existing per-waypoint render state, clear them on removal/reload, and avoid resolving registry or resource data from its worker thread.

## VoxelMap and uploads

On VoxelMap upload, convert `Waypoint.imageSuffix` to the corresponding `voxelmap:` ID, including empty suffix to `voxelmap:waypoint`. Reject an unknown/unsafe suffix as no icon rather than accepting an arbitrary image path. On Server Waypoint to VoxelMap sync, only a recognized VoxelMap icon supplies `imageSuffix`; an item icon, no icon, or an unavailable VoxelMap icon supplies the empty suffix, which is VoxelMap's default waypoint image. This fallback changes presentation in VoxelMap but never erases the stored Server Waypoint ID.

Xaero uploads have no equivalent icon mapping and use no icon. Reupload/update merge rules preserve an existing Server Waypoint icon unless a valid VoxelMap upload explicitly supplies a native icon. Existing unrelated properties and list revisions behave as before.

## Scope and constraints

- Java 17, GSON JSON, four-space indentation, Kotlin DSL Gradle.
- `common` owns the reusable `NamespacedId` record in `util`, the waypoint-specific icon policy, waypoint state, patch, codecs, commands, upload merge, and cross-server catalog fields. `mods` converts the record to version-specific `Identifier`/`ResourceLocation` using its existing `ResourceLocationHelper` and owns registry lookup, picker, rendering, and VoxelMap API integration. Paper may construct `NamespacedKey` from the record's validated components when it has a real icon consumer; the record itself has no Paper or Minecraft dependency.
- Maintain every supported Stonecutter branch from `settings.gradle.kts`; use versioned predicates for API differences and update `docs/tips/gui/local-guide.md` for the new GUI contract.
- No backward-compatible wire encoding is added; matched client, backend, and coordinator builds are required. The protocol constants remain 1 at the user's direction despite the changed payloads.
- The plan and spec are documentation only. Implementation, live visual verification, commits, and release are separate steps.

## Acceptance evidence

Focused common tests cover ID validation, JSON, copying, patch set/clear, codec round trips, command add/edit, upload merge, and remote catalog serialization. Focused mods tests cover picker catalog/filtering and VoxelMap suffix mapping. Representative oldest and newest Fabric, Forge, NeoForge, and Paper targets compile; manual game checks confirm item and VoxelMap icon display, missing-icon fallback, VoxelMap export, and screen layout.
