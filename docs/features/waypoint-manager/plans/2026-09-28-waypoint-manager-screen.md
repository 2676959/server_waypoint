# Waypoint Manager Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the waypoint manager screen as described in
[the screen design](../specs/2026-09-28-waypoint-manager-screen-design.md): view states and
lifecycle, sidebar slot release, remote availability, empty states, translations and documentation.

**Architecture:** `WaypointManagerScreen` keeps ownership of layout, widget registration and
rendering. Each new rule is a small pure static function next to the existing
`calculateLayoutGeometry`, with a JUnit test in the mods test source set. Existing widgets get
narrow extensions: a badge hook in `IconListWidget`, state helpers in `ServerListWidget`, a footer
and empty reasons in `RemoteWaypointPanel`, and empty reasons in `WaypointListWidget`.

**Tech Stack:** Java 17, Minecraft client GUI through Fabric/Forge/NeoForge with Stonecutter,
JUnit 5, Gradle Kotlin DSL.

## Global Constraints

- Java 17 language features only. Indent with four spaces, never tabs.
- The active Stonecutter target is `26.1.2-fabric`. Source under `mods/src/main/java` is written
  in that target's form: `GuiGraphicsExtractor`, `extractRenderState` after a
  `//$ render_method_swap` line, `extractWidgetRenderState` after a `//$ render_widget_method_swap`
  line, and `Identifier` after a `//$ resource_location_type_swap` line where it is a type. Keep
  `//~` replacement tokens above the `package` line.
- Keep every Stonecutter branch working from 1.20.1 to 26.3. Version differences live in
  `DrawContextHelper`. Don't add a swap or replacement; if one becomes necessary, update the
  inventory in `AGENTS.md` in the same change.
- Run Gradle tasks by version project (for example `:mods:1.20.1-fabric:compileJava`). Never switch
  the active Stonecutter project.
- No backward-compatibility code. Removed translation keys need no migration.
- **Do not commit.** AGENTS.md forbids commits unless the user asks. Each task ends with a review
  checkpoint instead of a commit.
- Update `docs/tips/gui/local-guide.md` in the same task as any GUI API change (AGENTS.md).
- Translation changes apply to all six locales: `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk`, `zh_tw`.
  The non-English strings in this plan are machine drafts.
- `tools/cross-server-gui-test/RemoteGuiProbe.java` reads these members by reflection, so their
  names must not change:
  - `WaypointManagerScreen`: `serverScopeToggle`, `remotePanel`, `showingRemote`,
    `addWaypointButton`, `serverListWidget`, static `dimensionListWidget`, `groupModeToggle`,
    `sortOrderToggle`, `sortingModeDropdown`, `searchField`, and `iconItems` inside the dropdown.
  - `RemoteWaypointPanel`: `tree`, `selected`, `grouped`, `reversed`, `sortMode`,
    `teleportButton`, and the `teleport()` method.
- Work on branch `waypoint-manager-improved`, which includes the waypoint-icon commit `4bc23c14`.
  Every exact-match "Replace" anchor in this plan was checked against that commit; anchors that
  earlier tasks create are checked by those tasks. If an anchor doesn't match, stop and report
  instead of improvising. Make only the edits listed here, and leave any other working-tree changes
  alone.

## File Map

| File | Responsibility | Tasks |
| --- | --- | --- |
| `mods/src/main/resources/assets/server_waypoint/lang/{en_us,es_es,he_il,zh_cn,zh_hk,zh_tw}.json` | Manager translations | 1, 7 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteGuiTranslationTest.java` | Translation coverage | 1, 7 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java` | View states, lifecycle, sidebar layout, wiring | 2, 3, 5, 6 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreenViewStateTest.java` (new) | View-state resolver | 2 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreenSidebarLayoutTest.java` (new) | Sidebar layout | 3 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreenLayoutTest.java` | Drop the removed geometry method | 3 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java` | Above-items overlay layer | 4 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/IconListWidget.java` | Badge hook and pass | 4 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ServerListWidget.java` | Server state colors, badges, tooltip | 4 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/ServerListWidgetStateTest.java` (new) | State mappings | 4 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteBrowserModel.java` | Remote empty reasons, rail dimensions | 5 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteBrowserModelTest.java` | Model tests | 5 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java` | Session binding, empty message, footer, details layout, teleport tooltip | 2, 5, 6 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanelLayoutTest.java` (new) | List-area split | 6 |
| `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointListWidget.java` | Local empty reasons | 7 |
| `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/WaypointListWidgetEmptyStateTest.java` (new) | Local empty reasons | 7 |
| `docs/tips/gui/local-guide.md` | GUI API documentation | 2–7 |
| `docs/features/cross-server/specs/cross-server-gui.md` | Step 18 spec | 8 |
| `tools/cross-server-gui-test/RemoteGuiProbe.java`, `tools/cross-server-gui-test/README.md` | Native probe checks | 9 |
| `docs/features/waypoint-manager/validation/`, `docs/features/waypoint-manager/README.md` | Validation record | 9 |

---

### Task 1: Manager translation keys

**Files:**
- Modify: `mods/src/main/resources/assets/server_waypoint/lang/en_us.json`, `es_es.json`, `he_il.json`, `zh_cn.json`, `zh_hk.json`, `zh_tw.json`
- Test: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteGuiTranslationTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: translation keys used by later tasks: `server_waypoint.manager.title`,
  `server_waypoint.manager.loading`, `waypoint.remote.gui.server_status` (two `%s`),
  `waypoint.empty.no_matches` (one `%s`), `waypoint.empty.all`, `waypoint.empty.dimension`,
  `waypoint.remote.empty.unauthorized`, `waypoint.remote.empty.server_unavailable`,
  `waypoint.remote.empty.server`, `waypoint.remote.empty.dimension`. `waypoint.remote.gui.local`
  reads "Local waypoints"; `waypoint.remote.gui.selector` is gone.

- [ ] **Step 1: Write the failing test**

Replace the whole content of `RemoteGuiTranslationTest.java` with:

```java
package _959.server_waypoint.common.client.gui.screens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoteGuiTranslationTest {
    private static final List<String> LOCALES = List.of("en_us", "es_es", "he_il", "zh_cn", "zh_hk", "zh_tw");
    private static final List<String> MANAGER_KEYS = List.of(
            "server_waypoint.manager.title",
            "server_waypoint.manager.loading",
            "waypoint.remote.gui.server_status",
            "waypoint.empty.no_matches",
            "waypoint.empty.all",
            "waypoint.empty.dimension",
            "waypoint.remote.empty.unauthorized",
            "waypoint.remote.empty.server_unavailable",
            "waypoint.remote.empty.server",
            "waypoint.remote.empty.dimension"
    );
    private static final List<String> RETIRED_KEYS = List.of("waypoint.remote.gui.selector");

    @Test void allSixLocalesCoverRemoteGuiKeysAndPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : english.keySet()) {
                if (!key.startsWith("waypoint.remote.")) continue;
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    @Test void allSixLocalesDefineTheManagerKeysWithMatchingPlaceholders() throws Exception {
        JsonObject english = read("en_us");
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : MANAGER_KEYS) {
                assertTrue(translated.has(key), locale + ": " + key);
                assertEquals(placeholders(english.get(key).getAsString()),
                        placeholders(translated.get(key).getAsString()), locale + ": " + key);
            }
        }
    }

    @Test void retiredManagerKeysAreRemovedFromEveryLocale() throws Exception {
        for (String locale : LOCALES) {
            JsonObject translated = read(locale);
            for (String key : RETIRED_KEYS) {
                assertFalse(translated.has(key), locale + ": " + key);
            }
        }
    }

    @Test void localToggleTooltipNamesTheView() throws Exception {
        assertEquals("Local waypoints", read("en_us").get("waypoint.remote.gui.local").getAsString());
    }

    private JsonObject read(String locale) throws Exception {
        var stream = getClass().getResourceAsStream("/assets/server_waypoint/lang/" + locale + ".json");
        assertNotNull(stream);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private long placeholders(String value) {
        return java.util.regex.Pattern.compile("%s").matcher(value).results().count();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.RemoteGuiTranslationTest" --console=plain`

Expected: `BUILD FAILED` with three failing tests:
`allSixLocalesDefineTheManagerKeysWithMatchingPlaceholders` (`en_us: server_waypoint.manager.title`),
`retiredManagerKeysAreRemovedFromEveryLocale` (`en_us: waypoint.remote.gui.selector`) and
`localToggleTooltipNamesTheView` (`expected: <Local waypoints> but was: <Server: Local>`).

- [ ] **Step 3: Update `en_us.json`**

Make five edits. Each "Replace" block is an exact existing line or pair of lines.

Replace:
```json
  "waypoint.empty_mark": "<Empty>",
```
With:
```json
  "waypoint.empty_mark": "<Empty>",
  "waypoint.empty.no_matches": "No waypoints match \"%s\".",
  "waypoint.empty.all": "No waypoints yet. Use the + button to add one.",
  "waypoint.empty.dimension": "No waypoints in this dimension yet. Use the + button to add one.",
```

Replace:
```json
  "server_waypoint.incompatible_protocol_version": "Incompatible serverside mod version",
```
With:
```json
  "server_waypoint.incompatible_protocol_version": "Incompatible serverside mod version",
  "server_waypoint.manager.title": "Waypoint Manager",
  "server_waypoint.manager.loading": "Synchronizing waypoints…",
```

Replace:
```json
  "waypoint.remote.gui.selector": "Server: Local / Remote…",
  "waypoint.remote.gui.local": "Server: Local",
```
With:
```json
  "waypoint.remote.gui.local": "Local waypoints",
```

Replace:
```json
  "waypoint.remote.gui.status": "Status: %s",
```
With:
```json
  "waypoint.remote.gui.status": "Status: %s",
  "waypoint.remote.gui.server_status": "%s · %s",
```

Replace:
```json
  "waypoint.remote.no_servers": "No remote servers are cached. Catalog synchronization may be unavailable.",
```
With:
```json
  "waypoint.remote.no_servers": "No remote servers are cached. Catalog synchronization may be unavailable.",
  "waypoint.remote.empty.unauthorized": "You don't have permission to view remote waypoints.",
  "waypoint.remote.empty.server_unavailable": "This server's waypoints are unavailable right now.",
  "waypoint.remote.empty.server": "This server has no waypoints.",
  "waypoint.remote.empty.dimension": "This dimension has no waypoints on this server.",
```

- [ ] **Step 4: Update `es_es.json`**

Replace:
```json
  "waypoint.empty_mark": "<Vacío>",
```
With:
```json
  "waypoint.empty_mark": "<Vacío>",
  "waypoint.empty.no_matches": "Ningún punto de ruta coincide con \"%s\".",
  "waypoint.empty.all": "Aún no hay puntos de ruta. Usa el botón + para añadir uno.",
  "waypoint.empty.dimension": "Aún no hay puntos de ruta en esta dimensión. Usa el botón + para añadir uno.",
```

Replace:
```json
  "server_waypoint.incompatible_protocol_version": "Versión del mod del servidor incompatible",
```
With:
```json
  "server_waypoint.incompatible_protocol_version": "Versión del mod del servidor incompatible",
  "server_waypoint.manager.title": "Gestor de puntos de ruta",
  "server_waypoint.manager.loading": "Sincronizando puntos de ruta…",
```

Replace:
```json
  "waypoint.remote.gui.selector": "Servidor: Local / Remoto…",
  "waypoint.remote.gui.local": "Servidor: Local",
```
With:
```json
  "waypoint.remote.gui.local": "Puntos de ruta locales",
```

Replace:
```json
  "waypoint.remote.gui.status": "Estado: %s",
```
With:
```json
  "waypoint.remote.gui.status": "Estado: %s",
  "waypoint.remote.gui.server_status": "%s · %s",
```

Replace:
```json
  "waypoint.remote.no_servers": "No hay servidores remotos en caché. La sincronización puede no estar disponible.",
```
With:
```json
  "waypoint.remote.no_servers": "No hay servidores remotos en caché. La sincronización puede no estar disponible.",
  "waypoint.remote.empty.unauthorized": "No tienes permiso para ver puntos de ruta remotos.",
  "waypoint.remote.empty.server_unavailable": "Los puntos de ruta de este servidor no están disponibles ahora mismo.",
  "waypoint.remote.empty.server": "Este servidor no tiene puntos de ruta.",
  "waypoint.remote.empty.dimension": "Esta dimensión no tiene puntos de ruta en este servidor.",
```

- [ ] **Step 5: Update `he_il.json`**

Replace:
```json
  "waypoint.empty_mark": "<ריק>",
```
With:
```json
  "waypoint.empty_mark": "<ריק>",
  "waypoint.empty.no_matches": "אין נקודות ציון שתואמות ל-\"%s\".",
  "waypoint.empty.all": "אין עדיין נקודות ציון. השתמשו בכפתור + כדי להוסיף אחת.",
  "waypoint.empty.dimension": "אין עדיין נקודות ציון בממד הזה. השתמשו בכפתור + כדי להוסיף אחת.",
```

Replace:
```json
  "server_waypoint.incompatible_protocol_version": "גרסת המוד בצד השרת אינה תואמת",
```
With:
```json
  "server_waypoint.incompatible_protocol_version": "גרסת המוד בצד השרת אינה תואמת",
  "server_waypoint.manager.title": "מנהל נקודות ציון",
  "server_waypoint.manager.loading": "מסנכרן נקודות ציון…",
```

Replace:
```json
  "waypoint.remote.gui.selector": "שרת: מקומי / מרוחק…",
  "waypoint.remote.gui.local": "שרת: מקומי",
```
With:
```json
  "waypoint.remote.gui.local": "נקודות ציון מקומיות",
```

Replace:
```json
  "waypoint.remote.gui.status": "מצב: %s",
```
With:
```json
  "waypoint.remote.gui.status": "מצב: %s",
  "waypoint.remote.gui.server_status": "%s · %s",
```

Replace:
```json
  "waypoint.remote.no_servers": "אין שרתים מרוחקים במטמון. ייתכן שסנכרון הקטלוג אינו זמין.",
```
With:
```json
  "waypoint.remote.no_servers": "אין שרתים מרוחקים במטמון. ייתכן שסנכרון הקטלוג אינו זמין.",
  "waypoint.remote.empty.unauthorized": "אין לך הרשאה לצפות בנקודות ציון מרוחקות.",
  "waypoint.remote.empty.server_unavailable": "נקודות הציון של השרת הזה אינן זמינות כרגע.",
  "waypoint.remote.empty.server": "לשרת הזה אין נקודות ציון.",
  "waypoint.remote.empty.dimension": "לממד הזה אין נקודות ציון בשרת הזה.",
```

- [ ] **Step 6: Update `zh_cn.json`**

Replace:
```json
  "waypoint.empty_mark": "<空>",
```
With:
```json
  "waypoint.empty_mark": "<空>",
  "waypoint.empty.no_matches": "没有与“%s”匹配的路径点。",
  "waypoint.empty.all": "还没有路径点。使用 + 按钮添加一个。",
  "waypoint.empty.dimension": "此维度还没有路径点。使用 + 按钮添加一个。",
```

Replace:
```json
  "server_waypoint.incompatible_protocol_version": "不兼容的服务端版本",
```
With:
```json
  "server_waypoint.incompatible_protocol_version": "不兼容的服务端版本",
  "server_waypoint.manager.title": "路径点管理器",
  "server_waypoint.manager.loading": "正在同步路径点…",
```

Replace:
```json
  "waypoint.remote.gui.selector": "服务器：本地 / 远程…",
  "waypoint.remote.gui.local": "服务器：本地",
```
With:
```json
  "waypoint.remote.gui.local": "本地路径点",
```

Replace:
```json
  "waypoint.remote.gui.status": "状态：%s",
```
With:
```json
  "waypoint.remote.gui.status": "状态：%s",
  "waypoint.remote.gui.server_status": "%s · %s",
```

Replace:
```json
  "waypoint.remote.no_servers": "尚未缓存远程服务器，目录同步可能不可用。",
```
With:
```json
  "waypoint.remote.no_servers": "尚未缓存远程服务器，目录同步可能不可用。",
  "waypoint.remote.empty.unauthorized": "你没有查看远程路径点的权限。",
  "waypoint.remote.empty.server_unavailable": "此服务器的路径点暂时不可用。",
  "waypoint.remote.empty.server": "此服务器没有路径点。",
  "waypoint.remote.empty.dimension": "此服务器的该维度没有路径点。",
```

- [ ] **Step 7: Update `zh_hk.json`**

Replace:
```json
  "waypoint.empty_mark": "<空>",
```
With:
```json
  "waypoint.empty_mark": "<空>",
  "waypoint.empty.no_matches": "沒有符合「%s」的路徑點。",
  "waypoint.empty.all": "尚未有路徑點。使用 + 按鈕新增一個。",
  "waypoint.empty.dimension": "此維度尚未有路徑點。使用 + 按鈕新增一個。",
```

Replace:
```json
  "server_waypoint.incompatible_protocol_version": "伺服器嘅MOD版本不相容",
```
With:
```json
  "server_waypoint.incompatible_protocol_version": "伺服器嘅MOD版本不相容",
  "server_waypoint.manager.title": "路徑點管理器",
  "server_waypoint.manager.loading": "正在同步路徑點…",
```

Replace:
```json
  "waypoint.remote.gui.selector": "伺服器：本地 / 遠端…",
  "waypoint.remote.gui.local": "伺服器：本地",
```
With:
```json
  "waypoint.remote.gui.local": "本地路徑點",
```

Replace:
```json
  "waypoint.remote.gui.status": "狀態：%s",
```
With:
```json
  "waypoint.remote.gui.status": "狀態：%s",
  "waypoint.remote.gui.server_status": "%s · %s",
```

Replace:
```json
  "waypoint.remote.no_servers": "尚未快取遠端伺服器，目錄同步可能無法使用。",
```
With:
```json
  "waypoint.remote.no_servers": "尚未快取遠端伺服器，目錄同步可能無法使用。",
  "waypoint.remote.empty.unauthorized": "你沒有檢視遠端路徑點的權限。",
  "waypoint.remote.empty.server_unavailable": "此伺服器的路徑點目前無法使用。",
  "waypoint.remote.empty.server": "此伺服器沒有路徑點。",
  "waypoint.remote.empty.dimension": "此伺服器的這個維度沒有路徑點。",
```

- [ ] **Step 8: Update `zh_tw.json`**

Replace:
```json
  "waypoint.empty_mark": "<空>",
```
With:
```json
  "waypoint.empty_mark": "<空>",
  "waypoint.empty.no_matches": "沒有符合「%s」的路徑點。",
  "waypoint.empty.all": "尚未有路徑點。使用 + 按鈕新增一個。",
  "waypoint.empty.dimension": "此維度尚未有路徑點。使用 + 按鈕新增一個。",
```

Replace:
```json
  "server_waypoint.incompatible_protocol_version": "不相容的伺服器端版本",
```
With:
```json
  "server_waypoint.incompatible_protocol_version": "不相容的伺服器端版本",
  "server_waypoint.manager.title": "路徑點管理器",
  "server_waypoint.manager.loading": "正在同步路徑點…",
```

Replace:
```json
  "waypoint.remote.gui.selector": "伺服器：本地 / 遠端…",
  "waypoint.remote.gui.local": "伺服器：本地",
```
With:
```json
  "waypoint.remote.gui.local": "本地路徑點",
```

Replace:
```json
  "waypoint.remote.gui.status": "狀態：%s",
```
With:
```json
  "waypoint.remote.gui.status": "狀態：%s",
  "waypoint.remote.gui.server_status": "%s · %s",
```

Replace:
```json
  "waypoint.remote.no_servers": "尚未快取遠端伺服器，目錄同步可能無法使用。",
```
With:
```json
  "waypoint.remote.no_servers": "尚未快取遠端伺服器，目錄同步可能無法使用。",
  "waypoint.remote.empty.unauthorized": "你沒有檢視遠端路徑點的權限。",
  "waypoint.remote.empty.server_unavailable": "此伺服器的路徑點目前無法使用。",
  "waypoint.remote.empty.server": "此伺服器沒有路徑點。",
  "waypoint.remote.empty.dimension": "此伺服器的這個維度沒有路徑點。",
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.RemoteGuiTranslationTest" --console=plain`

Expected: `BUILD SUCCESSFUL`. The test parses all six files, so a JSON syntax error also fails here.

- [ ] **Step 10: Review checkpoint (no commit)**

Run: `git diff --check -- mods/src/main/resources mods/src/test` → no output.
Run: `git status --short` → the six language files and the test are modified; nothing else new.

---

### Task 2: View states and lifecycle

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java`
- Modify: `docs/tips/gui/local-guide.md`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreenViewStateTest.java`

**Interfaces:**
- Consumes: `server_waypoint.manager.title`, `server_waypoint.manager.loading` (Task 1).
- Produces:
  - `WaypointManagerScreen.ManagerViewState` (package-private enum: `LOADING`, `UNSUPPORTED`,
    `INCOMPATIBLE`, `READY`) with `@Nullable String messageKey()`.
  - `static ManagerViewState resolveViewState(boolean integratedServer, WaypointClientMod.ClientNetworkState networkState)`.
  - `RemoteWaypointPanel`: `long session()`, `boolean isSessionCurrent()`, `boolean bindSession()`,
    and `boolean tick()` (returns `false` after closing the screen).
  - `WaypointManagerScreen` field `private final IconMenuItem distanceSortItem`;
    `IconDropdownMenu.addIconItem(...)` now returns the `IconMenuItem`.

- [ ] **Step 1: Write the failing test**

Create `WaypointManagerScreenViewStateTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaypointManagerScreenViewStateTest {
    @Test
    void integratedServerIsReadyInEveryNetworkState() {
        for (ClientNetworkState networkState : ClientNetworkState.values()) {
            assertEquals(
                    WaypointManagerScreen.ManagerViewState.READY,
                    WaypointManagerScreen.resolveViewState(true, networkState),
                    networkState.name()
            );
        }
    }

    @Test
    void dedicatedServerLoadsUntilSynchronizationFinishes() {
        assertEquals(WaypointManagerScreen.ManagerViewState.LOADING,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.NOT_READY));
        assertEquals(WaypointManagerScreen.ManagerViewState.LOADING,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.HANDSHAKE_FINISHED));
        assertEquals(WaypointManagerScreen.ManagerViewState.READY,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.SYNC_FINISHED));
    }

    @Test
    void unsupportedAndIncompatibleServersHaveTheirOwnStates() {
        assertEquals(WaypointManagerScreen.ManagerViewState.UNSUPPORTED,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.NO_SERVERSIDE_SUPPORT));
        assertEquals(WaypointManagerScreen.ManagerViewState.INCOMPATIBLE,
                WaypointManagerScreen.resolveViewState(false, ClientNetworkState.INCOMPATIBLE_PROTOCOL));
    }

    @Test
    void onlyNonReadyStatesShowAMessage() {
        assertEquals("server_waypoint.manager.loading",
                WaypointManagerScreen.ManagerViewState.LOADING.messageKey());
        assertEquals("server_waypoint.no_serverside_support",
                WaypointManagerScreen.ManagerViewState.UNSUPPORTED.messageKey());
        assertEquals("server_waypoint.incompatible_protocol_version",
                WaypointManagerScreen.ManagerViewState.INCOMPATIBLE.messageKey());
        assertNull(WaypointManagerScreen.ManagerViewState.READY.messageKey());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.WaypointManagerScreenViewStateTest" --console=plain`

Expected: `BUILD FAILED` during `compileTestJava` with `cannot find symbol` for `ManagerViewState`.

- [ ] **Step 3: Add the enum and resolver**

In `WaypointManagerScreen.java`, insert this block immediately before the line
`    static ManagerLayoutGeometry calculateLayoutGeometry(int screenWidth, int screenHeight) {`:

```java
    /** What the manager shows, chosen from the client connection state. */
    enum ManagerViewState {
        LOADING("server_waypoint.manager.loading"),
        UNSUPPORTED("server_waypoint.no_serverside_support"),
        INCOMPATIBLE("server_waypoint.incompatible_protocol_version"),
        READY(null);

        private final @Nullable String messageKey;

        ManagerViewState(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /** The centered message of a non-ready state, or {@code null} for the full manager. */
        @Nullable String messageKey() {
            return this.messageKey;
        }
    }

    /**
     * Chooses the manager content. An integrated server shares the server's waypoint model and is
     * always ready; a dedicated server is ready once waypoint synchronization finishes.
     */
    static ManagerViewState resolveViewState(
            boolean integratedServer,
            WaypointClientMod.ClientNetworkState networkState
    ) {
        if (integratedServer) {
            return ManagerViewState.READY;
        }
        return switch (networkState) {
            case SYNC_FINISHED -> ManagerViewState.READY;
            case NOT_READY, HANDSHAKE_FINISHED -> ManagerViewState.LOADING;
            case NO_SERVERSIDE_SUPPORT -> ManagerViewState.UNSUPPORTED;
            case INCOMPATIBLE_PROTOCOL -> ManagerViewState.INCOMPATIBLE;
        };
    }

```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.WaypointManagerScreenViewStateTest" --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Add session binding to `RemoteWaypointPanel`**

In `RemoteWaypointPanel.java`, replace:
```java
    private final long session;
```
With:
```java
    private long session;
```

Replace the `tick()` method:
```java
    void tick() {
        if (session != catalogs.session()) {
            MinecraftClientHelper.setScreen(null);
            return;
        }
        if (displayed != catalogs.snapshot() || displayedState != catalogs.state()) rebuild();
    }
```
With:
```java
    /** Refreshes changed catalog data; returns {@code false} after closing the screen for a new session. */
    boolean tick() {
        if (!isSessionCurrent()) {
            MinecraftClientHelper.setScreen(null);
            return false;
        }
        if (displayed != catalogs.snapshot() || displayedState != catalogs.state()) rebuild();
        return true;
    }

    /** The catalog session this panel's selection and teleport guard belong to. */
    long session() { return session; }

    boolean isSessionCurrent() { return session == catalogs.session(); }

    /** Binds a changed catalog session, clearing the old session's selection; returns whether it changed. */
    boolean bindSession() {
        long current = catalogs.session();
        if (current == session) return false;
        session = current;
        selected = null;
        rebuild();
        return true;
    }
```

- [ ] **Step 6: Update the screen's imports, title, fields and constructor**

In `WaypointManagerScreen.java`, replace:
```java
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.INCOMPATIBLE_PROTOCOL;
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.NO_SERVERSIDE_SUPPORT;
import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.WaypointClientMod.getNetworkState;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
```
With:
```java
import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.WaypointClientMod.getNetworkState;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
```

Replace:
```java
    private final WaypointClientMod waypointClientMod;
    private boolean hasInitialized = false;
```
With:
```java
    private final WaypointClientMod waypointClientMod;
    private final ScalableText statusMessage;
    private final IconMenuItem distanceSortItem;
    private ManagerViewState builtState = ManagerViewState.LOADING;
    private boolean hasInitialized = false;
```

Replace:
```java
        super(Component.nullToEmpty("Server Waypoints"));
        this.parentScreen = parentScreen;
        this.waypointClientMod = waypointClientMod;
        this.remotePanel = new RemoteWaypointPanel(waypointClientMod, this.font);
```
With:
```java
        super(Component.translatable("server_waypoint.manager.title"));
        this.parentScreen = parentScreen;
        this.waypointClientMod = waypointClientMod;
        this.remotePanel = new RemoteWaypointPanel(waypointClientMod, this.font);
        this.statusMessage = new ScalableText(0, 0, Component.empty(), WidgetThemeVariable.TEXT_PRIMARY, this.font);
```

Replace:
```java
        sortingModeDropdown.addIconItem(
                Component.translatable("waypoint.sort.distance"),
                WidgetTextures.SORT_DISTANCE_ICON,
                () -> toggleSortMode(WaypointSorting.SortMode.DISTANCE)
        );
```
With:
```java
        this.distanceSortItem = sortingModeDropdown.addIconItem(
                Component.translatable("waypoint.sort.distance"),
                WidgetTextures.SORT_DISTANCE_ICON,
                () -> toggleSortMode(WaypointSorting.SortMode.DISTANCE)
        );
```

In the nested `IconDropdownMenu` class, replace:
```java
        private void addIconItem(
                Component message,
                //$ resource_location_type_swap
                Identifier
                icon,
                Runnable callback
        ) {
            IconMenuItem menuItem = this.addMenuItem(new IconMenuItem(message, icon, callback));
            this.iconItems.add(menuItem);
        }
```
With:
```java
        private IconMenuItem addIconItem(
                Component message,
                //$ resource_location_type_swap
                Identifier
                icon,
                Runnable callback
        ) {
            IconMenuItem menuItem = this.addMenuItem(new IconMenuItem(message, icon, callback));
            this.iconItems.add(menuItem);
            return menuItem;
        }
```

In `setShowingRemote`, replace:
```java
        IconMenuItem distanceSortItem = sortingModeDropdown.iconItems.get(2);
        distanceSortItem.visible = distanceSortItem.active = !remote;
```
With:
```java
        this.distanceSortItem.visible = this.distanceSortItem.active = !remote;
```

- [ ] **Step 7: Build only what the view state needs in `init()`**

Replace the start of `init()`:
```java
    @Override
    protected void init() {
        isRendering = true;
        activeScreen = this;
        super.init();
        String currentDimension = WaypointClientMod.getCurrentDimensionName();
        if (WaypointServerMod.runsWithClient()) {
            WaypointServerMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
        } else {
            if (WaypointClientMod.getNetworkState() == WaypointClientMod.ClientNetworkState.SYNC_FINISHED) {
                WaypointClientMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
            } else {
                return;
            }
        }
        updateWidgetDimension();
```
With:
```java
    @Override
    protected void init() {
        super.init();
        this.builtState = resolveViewState(WaypointServerMod.runsWithClient(), getNetworkState());
        if (this.builtState != ManagerViewState.READY) {
            // rebuildWidgets() doesn't call removed(), so a ready screen that rebuilds as loading
            // must stop receiving refreshes here.
            this.stopReceivingRefreshes();
            this.resetToLocalView();
            this.layoutStatusMessage();
            return;
        }
        isRendering = true;
        activeScreen = this;
        if (this.remotePanel.bindSession()) {
            // A new catalog session belongs to another connection; request its dimensions again.
            this.requestedAvailableDimensions = false;
            this.availableDimensionNames = List.of();
        }
        String currentDimension = getCurrentDimensionName();
        if (WaypointServerMod.runsWithClient()) {
            WaypointServerMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
        } else {
            WaypointClientMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
        }
        updateWidgetDimension();
```

Insert these three methods immediately after the end of `init()` (after its closing `}` and before
`    @Override\n    public void tick() {`):

```java
    /** Ends this manager's static refresh registration (see {@link #canUpdateWidgets()}). */
    private void stopReceivingRefreshes() {
        isRendering = false;
        if (activeScreen == this) {
            activeScreen = null;
        }
    }

    private void layoutStatusMessage() {
        Component message = Component.translatable(Objects.requireNonNull(this.builtState.messageKey()));
        int availableWidth = Math.max(1, this.width - SCREEN_MARGIN * 2);
        this.statusMessage.setText(message);
        this.statusMessage.setMaxWidth(Math.min(this.font.width(message), availableWidth));
        this.statusMessage.setPosition(
                centered(this.width, this.statusMessage.getWidth()),
                centered(this.height, this.statusMessage.getHeight())
        );
    }

    /** Leaving the ready state drops the remote view, so the next ready build starts local. */
    private void resetToLocalView() {
        if (!this.showingRemote) {
            return;
        }
        this.showingRemote = false;
        this.serverScopeToggle.setState(false);
        this.distanceSortItem.visible = this.distanceSortItem.active = true;
        List<String> localDimensions = this.getDisplayedDimensionNames();
        dimensionListWidget.updateDimensionNames(localDimensions);
        dimensionListWidget.setDimensionName(resolveSelectedDimension(
                this.localDimension,
                getCurrentDimensionName(),
                localDimensions
        ));
    }

```

- [ ] **Step 8: Switch states in `tick()`, unregister in `removed()`, and guard list keys**

Replace:
```java
    @Override
    public void tick() {
        super.tick();
        if (showingRemote) {
            remotePanel.tick();
            refreshRemoteSelectors(false);
        } else waypointListWidget.refreshDistanceSortIfPlayerMoved();
    }
```
With:
```java
    @Override
    public void tick() {
        super.tick();
        if (this.builtState == ManagerViewState.READY && this.showingRemote && !this.remotePanel.tick()) {
            // Step 18: a catalog session change in the remote view closed the manager.
            return;
        }
        ManagerViewState state = resolveViewState(WaypointServerMod.runsWithClient(), getNetworkState());
        boolean staleLocalSession = state == ManagerViewState.READY
                && !this.showingRemote
                && !this.remotePanel.isSessionCurrent();
        if (state != this.builtState || staleLocalSession) {
            this.rebuildWidgets();
            return;
        }
        if (state != ManagerViewState.READY) {
            return;
        }
        if (this.showingRemote) {
            refreshRemoteSelectors(false);
        } else {
            waypointListWidget.refreshDistanceSortIfPlayerMoved();
        }
    }

    @Override
    public void removed() {
        super.removed();
        // Child screens and every close path end this manager's static refresh registration.
        this.stopReceivingRefreshes();
    }
```

In `keyPressed`, replace:
```java
        return !showingRemote && waypointListWidget.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
```
With:
```java
        return this.builtState == ManagerViewState.READY
                && !showingRemote
                && waypointListWidget.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
```

- [ ] **Step 9: Render the status message and keep dimension replies across child screens**

In `renderScreenContents`, replace:
```java
        WaypointClientMod.ClientNetworkState networkState = getNetworkState();
        if (networkState == NO_SERVERSIDE_SUPPORT) {
            Component info = Component.translatable("server_waypoint.no_serverside_support");
            int infoWidth = font.width(info);
            drawText(context, this.font, info, centered(this.width, infoWidth), this.height / 2,
                    WidgetThemeManager.getColor(WidgetThemeVariable.TEXT_PRIMARY));
            return;
        } else if (networkState == INCOMPATIBLE_PROTOCOL) {
            Component info = Component.translatable("server_waypoint.incompatible_protocol_version");
            int infoWidth = font.width(info);
            drawText(context, this.font, info, centered(this.width, infoWidth), this.height / 2,
                    WidgetThemeManager.getColor(WidgetThemeVariable.TEXT_PRIMARY));
            return;
        }
```
With:
```java
        if (this.builtState != ManagerViewState.READY) {
            this.statusMessage.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, delta);
            return;
        }
```

Replace `requestAvailableDimensionNames()`:
```java
    private void requestAvailableDimensionNames() {
        if (this.requestedAvailableDimensions) {
            return;
        }
        this.requestedAvailableDimensions = true;
        ClientDimensionCatalog.getAvailableDimensionNames().thenAccept(dimensionNames -> {
            if (activeScreen != this) {
                return;
            }
            this.availableDimensionNames = dimensionNames;
            updateDimensionWidgetSelection();
            layoutSidebar();
        });
    }
```
With:
```java
    private void requestAvailableDimensionNames() {
        if (this.requestedAvailableDimensions) {
            return;
        }
        this.requestedAvailableDimensions = true;
        long requestSession = this.remotePanel.session();
        ClientDimensionCatalog.getAvailableDimensionNames().thenAccept(dimensionNames -> {
            // A reply to an earlier session lists the previous server's dimensions.
            if (this.remotePanel.session() != requestSession) {
                return;
            }
            // Keep the reply while a child screen is open; the next init() reads it.
            this.availableDimensionNames = dimensionNames;
            if (activeScreen != this) {
                return;
            }
            updateDimensionWidgetSelection();
            layoutSidebar();
        });
    }
```

- [ ] **Step 10: Document the view states in the GUI guide**

In `docs/tips/gui/local-guide.md`, replace this exact text (the end of the bullet that begins
"- In all-dimensions mode, the waypoint-list scroll position"):
```markdown
so connecting to another server or opening another local save also starts at the top with every dimension expanded.
```
With:
```markdown
so connecting to another server or opening another local save also starts at the top with every dimension expanded.
- `WaypointManagerScreen.resolveViewState(integratedServer, networkState)` picks `LOADING` (`NOT_READY` or `HANDSHAKE_FINISHED`), `UNSUPPORTED`, `INCOMPATIBLE` or `READY` (`SYNC_FINISHED`, or any state in an integrated world). A non-ready build registers no widgets and shows one centered `ScalableText` message. `tick()` calls vanilla `rebuildWidgets()` when the resolved state changes, so a manager opened during sync builds itself in place when sync finishes. In the remote view a catalog session change still closes the screen; in the local view it rebuilds the screen, and the ready build rebinds `RemoteWaypointPanel` to the new session, clearing its selection and the requested dimension catalog. `removed()` clears the static `isRendering`/`activeScreen` registration, so a manager closed by teleport or `setScreen(null)` stops receiving refresh calls; returning from a child screen re-runs `init()`, which re-queries the list. Because the client reports `NO_SERVERSIDE_SUPPORT` until a dedicated server's handshake arrives, a manager open in that window briefly shows the unsupported message.
```

- [ ] **Step 11: Run the mods tests and an old-version compile**

Run: `./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:compileJava --console=plain`

Expected: `BUILD SUCCESSFUL`. The 1.20.1 compile checks the Stonecutter render paths of the
changed screen.

- [ ] **Step 12: Review checkpoint (no commit)**

Run: `git diff --check` → no output. Review the diff of `WaypointManagerScreen.java` and
`RemoteWaypointPanel.java`: no other methods changed.

---

### Task 3: Sidebar layout and slot release

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java`
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreenLayoutTest.java`
- Modify: `docs/tips/gui/local-guide.md`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreenSidebarLayoutTest.java`

**Interfaces:**
- Consumes: `resolveViewState` and the Task 2 `tick()`.
- Produces:
  - `static SidebarLayout calculateSidebarLayout(int contentY, int contentHeight, int visibleControlCount, int dimensionPreferredHeight, int serverPreferredHeight, boolean showingRemote)`.
  - Package-private `record SidebarLayout(boolean controlsVisible, int controlsY, int dimensionY, int dimensionHeight, int serverY, int serverHeight)`
    with `dimensionVisible()`, `serverVisible()`, `controlY(int index)`, `railSeparatorY()` and
    `controlSeparatorY()`.
  - `ManagerLayoutGeometry.dimensionListHeight()` and `CONTROL_COLUMN_HEIGHT` are removed.

- [ ] **Step 1: Write the failing test**

Create `WaypointManagerScreenSidebarLayoutTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerScreenSidebarLayoutTest {
    // The content rectangle of an 854x480 viewport.
    private static final int CONTENT_Y = 43;
    private static final int CONTENT_HEIGHT = 394;

    @Test
    void sixControlsLeaveTheRailTheSpaceAboveThem() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 6, 400, 16, false);

        assertTrue(layout.controlsVisible());
        assertEquals(321, layout.controlsY());
        assertEquals(CONTENT_Y, layout.dimensionY());
        assertEquals(272, layout.dimensionHeight());
        assertFalse(layout.serverVisible());
    }

    @Test
    void aHiddenControlReleasesItsSlotToTheRail() {
        WaypointManagerScreen.SidebarLayout six =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 6, 400, 16, false);
        WaypointManagerScreen.SidebarLayout five =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 400, 16, false);

        assertEquals(six.controlsY() + 20, five.controlsY());
        assertEquals(six.dimensionHeight() + 20, five.dimensionHeight());
    }

    @Test
    void visibleControlsStackToTheContentBottom() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 52, 16, false);

        assertEquals(341, layout.controlY(0));
        assertEquals(361, layout.controlY(1));
        assertEquals(CONTENT_Y + CONTENT_HEIGHT, layout.controlY(4) + 16);
    }

    @Test
    void localRailStopsAtItsPreferredHeight() {
        assertEquals(52, WaypointManagerScreen.calculateSidebarLayout(
                CONTENT_Y, CONTENT_HEIGHT, 6, 52, 16, false).dimensionHeight());
    }

    @Test
    void remoteRailsGrowFromOppositeEndsWithSeparatorsMidGap() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 52, 100, true);

        assertEquals(52, layout.dimensionHeight());
        assertEquals(100, layout.serverHeight());
        assertEquals(235, layout.serverY());
        assertEquals(165, layout.railSeparatorY());
        assertEquals(338, layout.controlSeparatorY());
    }

    @Test
    void constrainedRemoteRailsShareTheSpaceEqually() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(CONTENT_Y, CONTENT_HEIGHT, 5, 400, 400, true);

        assertEquals(143, layout.dimensionHeight());
        assertEquals(143, layout.serverHeight());
    }

    @Test
    void shortContentHidesTheControlsAndBothRails() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(10, 90, 5, 52, 52, true);

        assertFalse(layout.controlsVisible());
        assertFalse(layout.dimensionVisible());
        assertFalse(layout.serverVisible());
    }

    @Test
    void aRailBelowOneIconHides() {
        WaypointManagerScreen.SidebarLayout layout =
                WaypointManagerScreen.calculateSidebarLayout(0, 110, 5, 52, 16, false);

        assertTrue(layout.controlsVisible());
        assertFalse(layout.dimensionVisible());
    }
}
```

The numbers come from the rules: 6 controls are `6 × 16 + 5 × 4 = 116` pixels, so they start at
`43 + 394 − 116 = 321`, and the rail gets `321 − 6 − 43 = 272`. With 5 controls (96 pixels) the
column starts at 341 and the rail gets 292.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.WaypointManagerScreenSidebarLayoutTest" --console=plain`

Expected: `BUILD FAILED` during `compileTestJava` with `cannot find symbol` for `SidebarLayout`.

- [ ] **Step 3: Add the sidebar layout function**

In `WaypointManagerScreen.java`, insert this block immediately before
`    static ManagerLayoutGeometry calculateLayoutGeometry(int screenWidth, int screenHeight) {`
(after the Task 2 resolver):

```java
    /**
     * Lays out the sidebar. Visible controls stack upward from the content bottom, so a hidden
     * control releases its slot. The dimension rail grows down from the top; in the remote view the
     * server rail grows up from above the controls, and {@link OpposedExpansionLayout} divides the
     * space. A rail below its one-icon minimum is hidden (zero height).
     */
    static SidebarLayout calculateSidebarLayout(
            int contentY,
            int contentHeight,
            int visibleControlCount,
            int dimensionPreferredHeight,
            int serverPreferredHeight,
            boolean showingRemote
    ) {
        int controlsHeight = visibleControlCount <= 0
                ? 0
                : visibleControlCount * CONTROL_BUTTON_SIZE + (visibleControlCount - 1) * CONTROL_GAP;
        int controlsY = contentY + contentHeight - controlsHeight;
        int available = Math.max(0, controlsY - SECTION_GAP - contentY);
        int dimensionHeight = Math.min(available, dimensionPreferredHeight);
        int serverHeight = 0;
        if (showingRemote) {
            OpposedExpansionLayout.Sizes sizes = OpposedExpansionLayout.allocate(
                    available,
                    MIN_DIMENSION_LIST_HEIGHT,
                    dimensionPreferredHeight,
                    serverPreferredHeight,
                    SECTION_GAP
            );
            dimensionHeight = sizes.top();
            serverHeight = sizes.bottom();
        }
        if (dimensionHeight < MIN_DIMENSION_LIST_HEIGHT) {
            dimensionHeight = 0;
        }
        if (serverHeight < MIN_DIMENSION_LIST_HEIGHT) {
            serverHeight = 0;
        }
        return new SidebarLayout(
                contentHeight >= controlsHeight,
                controlsY,
                contentY,
                dimensionHeight,
                controlsY - SECTION_GAP - serverHeight,
                serverHeight
        );
    }

    record SidebarLayout(
            boolean controlsVisible,
            int controlsY,
            int dimensionY,
            int dimensionHeight,
            int serverY,
            int serverHeight
    ) {
        boolean dimensionVisible() {
            return this.dimensionHeight > 0;
        }

        boolean serverVisible() {
            return this.serverHeight > 0;
        }

        int controlY(int index) {
            return this.controlsY + index * (CONTROL_BUTTON_SIZE + CONTROL_GAP);
        }

        int railSeparatorY() {
            return (this.dimensionY + this.dimensionHeight + this.serverY) / 2;
        }

        int controlSeparatorY() {
            return (this.serverY + this.serverHeight + this.controlsY) / 2;
        }
    }

```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.WaypointManagerScreenSidebarLayoutTest" --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Remove the stale geometry method and its assertions**

In `WaypointManagerScreenLayoutTest.java`, replace:
```java
    @Test
    void verticalSpaceGoesToTheDimensionAndWaypointLists() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);

        assertEquals(394, geometry.contentHeight());
        assertEquals(272, geometry.dimensionListHeight());
        assertEquals(379, geometry.waypointListHeight(11));

        WaypointManagerScreen.ManagerLayoutGeometry shorter =
                WaypointManagerScreen.calculateLayoutGeometry(854, 200);
        assertTrue(shorter.dimensionListHeight() < geometry.dimensionListHeight());
        assertTrue(shorter.waypointListHeight(11) < geometry.waypointListHeight(11));
    }
```
With:
```java
    @Test
    void verticalSpaceGoesToTheWaypointList() {
        WaypointManagerScreen.ManagerLayoutGeometry geometry =
                WaypointManagerScreen.calculateLayoutGeometry(854, 480);

        assertEquals(394, geometry.contentHeight());
        assertEquals(379, geometry.waypointListHeight(11));

        WaypointManagerScreen.ManagerLayoutGeometry shorter =
                WaypointManagerScreen.calculateLayoutGeometry(854, 200);
        assertTrue(shorter.waypointListHeight(11) < geometry.waypointListHeight(11));
    }
```

In `WaypointManagerScreen.java`, delete the method from `ManagerLayoutGeometry`:
```java
        int dimensionListHeight() {
            return Math.max(0, this.contentHeight - CONTROL_COLUMN_HEIGHT - SECTION_GAP);
        }

```
and delete the constant:
```java
    private static final int CONTROL_COLUMN_HEIGHT = CONTROL_BUTTON_SIZE * 6 + CONTROL_GAP * 5;
```

- [ ] **Step 6: Replace the fixed-slot control column**

Replace:
```java
    private boolean hasInitialized = false;
    private final WidgetPack controlAnchor;
    private final WidgetPack middleLayout;
```
With:
```java
    private boolean hasInitialized = false;
    private boolean hasRemoteServers;
    private final WidgetPack middleLayout;
```

At the end of the main constructor, delete this whole block together with the blank line above it
(keep the constructor's closing `}`):
```java
        WidgetPack controlColumn = new WidgetPack(
                CONTROL_BUTTON_SIZE,
                CONTROL_COLUMN_HEIGHT,
                LayoutFlow.Orientation.VERTICAL
        );
        controlColumn.addChild(serverScopeToggle, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(allDimensionsToggle, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(groupModeToggle, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(sortOrderToggle, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(sortingModeDropdown, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(addWaypointButton, LayoutFlow.Direction.FORWARD);
        this.controlAnchor = new WidgetPack(
                LEFT_PART_WIDTH,
                CONTROL_COLUMN_HEIGHT,
                LayoutFlow.Orientation.HORIZONTAL
        );
        this.controlAnchor.addChild(controlColumn, LayoutFlow.Direction.FORWARD);
```

In `init()`, replace:
```java
        updateWidgetDimension();
        this.middleLayout.setPosition(
```
With:
```java
        this.hasRemoteServers = !this.remotePanel.servers().isEmpty();
        updateWidgetDimension();
        this.middleLayout.setPosition(
```

In `init()`, replace:
```java
        this.controlAnchor.visitWidgets(this::addRenderableWidget);
```
With (same order as the old column, which is also the Tab order):
```java
        this.addRenderableWidget(serverScopeToggle);
        this.addRenderableWidget(allDimensionsToggle);
        this.addRenderableWidget(groupModeToggle);
        this.addRenderableWidget(sortOrderToggle);
        this.addRenderableWidget(sortingModeDropdown);
        this.addRenderableWidget(addWaypointButton);
```

`SpacerElement` stays imported because `middleLayout` still uses it.

- [ ] **Step 7: Lay out the sidebar from the pure function**

Replace the whole `layoutSidebar()` method:
```java
    private void layoutSidebar() {
        int controlsHeight = CONTROL_COLUMN_HEIGHT - (showingRemote ? CONTROL_BUTTON_SIZE + CONTROL_GAP : 0);
        int controlsY = layoutGeometry.contentY() + layoutGeometry.contentHeight() - controlsHeight;
        controlAnchor.setPosition(layoutGeometry.leftX(), controlsY);
        int available = Math.max(0, controlsY - SECTION_GAP - layoutGeometry.contentY());
        int top = Math.min(available, dimensionListWidget.preferredHeight());
        int bottom = 0;
        if (showingRemote) {
            var sizes = OpposedExpansionLayout.allocate(
                    available, MIN_DIMENSION_LIST_HEIGHT, dimensionListWidget.preferredHeight(),
                    serverListWidget.preferredHeight(), SECTION_GAP);
            top = sizes.top();
            bottom = sizes.bottom();
        }
        dimensionListWidget.visible = dimensionListWidget.active = top >= MIN_DIMENSION_LIST_HEIGHT;
        dimensionListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, top));
        dimensionListWidget.setPosition(layoutGeometry.leftX(), layoutGeometry.contentY());
        serverListWidget.visible = serverListWidget.active = showingRemote && bottom >= MIN_DIMENSION_LIST_HEIGHT;
        serverListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, bottom));
        serverListWidget.setPosition(layoutGeometry.leftX(), controlsY - SECTION_GAP - bottom);
        setControlVisibility(layoutGeometry.contentHeight() >= controlsHeight);
        selectorSeparator.setPosition(
                layoutGeometry.leftX(),
                (dimensionListWidget.getY() + dimensionListWidget.getHeight() + serverListWidget.getY()) / 2
        );
        selectorSeparator.setVisible(dimensionListWidget.visible && serverListWidget.visible);
        serverControlSeparator.setPosition(
                layoutGeometry.leftX(),
                (serverListWidget.getY() + serverListWidget.getHeight() + serverScopeToggle.getY()) / 2
        );
        serverControlSeparator.setVisible(serverListWidget.visible && serverScopeToggle.visible);
    }
```
With:
```java
    private void layoutSidebar() {
        List<ShiftableClickableWidget> controls = visibleSidebarControls();
        SidebarLayout sidebar = calculateSidebarLayout(
                layoutGeometry.contentY(),
                layoutGeometry.contentHeight(),
                controls.size(),
                dimensionListWidget.preferredHeight(),
                serverListWidget.preferredHeight(),
                showingRemote
        );
        for (int index = 0; index < controls.size(); index++) {
            controls.get(index).setPosition(layoutGeometry.controlX(), sidebar.controlY(index));
        }
        setControlVisibility(sidebar.controlsVisible());
        dimensionListWidget.visible = dimensionListWidget.active = sidebar.dimensionVisible();
        dimensionListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, sidebar.dimensionHeight()));
        dimensionListWidget.setPosition(layoutGeometry.leftX(), sidebar.dimensionY());
        serverListWidget.visible = serverListWidget.active = sidebar.serverVisible();
        serverListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, sidebar.serverHeight()));
        serverListWidget.setPosition(layoutGeometry.leftX(), sidebar.serverY());
        selectorSeparator.setPosition(layoutGeometry.leftX(), sidebar.railSeparatorY());
        selectorSeparator.setVisible(sidebar.dimensionVisible() && sidebar.serverVisible());
        serverControlSeparator.setPosition(layoutGeometry.leftX(), sidebar.controlSeparatorY());
        serverControlSeparator.setVisible(sidebar.serverVisible() && serverScopeToggle.visible);
    }

    /** Sidebar controls in top-to-bottom order; hidden controls are left out and release their slots. */
    private List<ShiftableClickableWidget> visibleSidebarControls() {
        List<ShiftableClickableWidget> controls = new ArrayList<>(6);
        if (isScopeToggleAvailable()) {
            controls.add(serverScopeToggle);
        }
        controls.add(allDimensionsToggle);
        controls.add(groupModeToggle);
        controls.add(sortOrderToggle);
        controls.add(sortingModeDropdown);
        if (!showingRemote) {
            controls.add(addWaypointButton);
        }
        return controls;
    }

    /** The local/remote toggle appears once remote servers are cached, and stays while viewing them. */
    private boolean isScopeToggleAvailable() {
        return hasRemoteServers || showingRemote;
    }

    private void updateRemoteServerAvailability() {
        boolean available = !this.remotePanel.servers().isEmpty();
        if (available == this.hasRemoteServers) {
            return;
        }
        this.hasRemoteServers = available;
        layoutSidebar();
    }
```

Replace the whole `setControlVisibility(boolean visible)` method:
```java
    private void setControlVisibility(boolean visible) {
        addWaypointButton.visible = visible && !showingRemote;
        addWaypointButton.active = visible && !showingRemote;
        serverScopeToggle.visible = serverScopeToggle.active = visible;
        groupModeToggle.visible = visible;
        groupModeToggle.active = visible;
        sortOrderToggle.visible = visible;
        sortOrderToggle.active = visible
                && waypointListWidget.getSortMode() != WaypointSorting.SortMode.DEFAULT;
        sortingModeDropdown.visible = visible;
        sortingModeDropdown.active = visible;
        allDimensionsToggle.visible = visible;
        allDimensionsToggle.active = visible;
        if (!visible) {
            closeOpenDropdownMenus();
        }
    }
```
With:
```java
    private void setControlVisibility(boolean visible) {
        addWaypointButton.visible = visible && !showingRemote;
        addWaypointButton.active = visible && !showingRemote;
        boolean scopeVisible = visible && isScopeToggleAvailable();
        if (!scopeVisible && getFocused() == serverScopeToggle) {
            setFocused(null);
        }
        serverScopeToggle.visible = serverScopeToggle.active = scopeVisible;
        groupModeToggle.visible = visible;
        groupModeToggle.active = visible;
        sortOrderToggle.visible = visible;
        sortOrderToggle.active = visible
                && waypointListWidget.getSortMode() != WaypointSorting.SortMode.DEFAULT;
        sortingModeDropdown.visible = visible;
        sortingModeDropdown.active = visible;
        allDimensionsToggle.visible = visible;
        allDimensionsToggle.active = visible;
        if (!visible) {
            closeOpenDropdownMenus();
        }
    }
```

In `tick()` (the Task 2 version), replace:
```java
        if (this.showingRemote) {
            refreshRemoteSelectors(false);
        } else {
            waypointListWidget.refreshDistanceSortIfPlayerMoved();
        }
    }
```
With:
```java
        if (this.showingRemote) {
            refreshRemoteSelectors(false);
        } else {
            waypointListWidget.refreshDistanceSortIfPlayerMoved();
        }
        updateRemoteServerAvailability();
    }
```

- [ ] **Step 8: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, make four replacements.

Replace:
```markdown
`ExpandableManager` is layout-only and does not render its children. Register and render the managed widgets separately, as `WaypointManagerScreen` does.
```
With:
```markdown
`ExpandableManager` is layout-only and does not render its children. Register and render the managed widgets separately.
```

Replace:
```markdown
The manager's sidebar `HOME_ICON` / `LAN_SERVERS_ICON` toggle switches its middle list and right
details panel between current-server and remote waypoints without opening another screen.
```
With:
```markdown
The manager's sidebar `HOME_ICON` / `LAN_SERVERS_ICON` toggle switches its middle list and right
details panel between current-server and remote waypoints without opening another screen. The
toggle is shown only while `RemoteWaypointPanel.servers()` is non-empty or the remote view is open,
so singleplayer and servers without cross-server never show it.
```

Replace:
```markdown
Catalog changes, mode switches and resize all recalculate the allocation.
```
With:
```markdown
The pure `WaypointManagerScreen.calculateSidebarLayout` combines this allocation with the number of
visible controls, which stack upward from the content bottom with 4-pixel gaps, so a hidden toggle
or add button releases its slot to the rails. Catalog changes, mode switches, resize and changes in
remote-server availability all recalculate it.
```

Replace:
```markdown
- `WaypointManagerScreen` demonstrates nested `ExpandableManager` layouts, fixed and flexible children, a `TreeViewWidget`, sorting controls, and responsive resizing.
```
With:
```markdown
- `WaypointManagerScreen` demonstrates pure, unit-tested geometry (`calculateLayoutGeometry` and `calculateSidebarLayout`), a `WidgetPack` for the search field and list, `TreeViewWidget` lists, sorting controls, and responsive resizing.
```

- [ ] **Step 9: Run the mods tests and an old-version compile**

Run: `./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:compileJava --console=plain`

Expected: `BUILD SUCCESSFUL`, including `WaypointManagerScreenLayoutTest` and
`WaypointManagerScreenSidebarLayoutTest`.

- [ ] **Step 10: Review checkpoint (no commit)**

Run: `git diff --check` → no output. Confirm with
`grep -n "controlAnchor\|CONTROL_COLUMN_HEIGHT\|dimensionListHeight" mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java`
→ no output.

---

### Task 4: Server availability badges and tooltip

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/IconListWidget.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/ServerListWidget.java`
- Modify: `docs/tips/gui/local-guide.md`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/ServerListWidgetStateTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `DrawContextHelper.nextItemOverlayLayer(GuiGraphicsExtractor)` and
    `DrawContextHelper.previousItemOverlayLayer(GuiGraphicsExtractor)`.
  - `IconListWidget.entryBadgeColor(T entry)`: `protected @Nullable WidgetThemeVariable`, default `null`.
  - `ServerListWidget.stateColor(RemoteCatalogState)`: `public static WidgetThemeVariable`
    (used by Task 6).
  - `ServerListWidget.badgeColor(RemoteCatalogState)`: package-private static, nullable.
  - `ServerListWidget.stateTranslationKey(RemoteCatalogState)`: `public static String` (used by Task 6).

- [ ] **Step 1: Write the failing test**

Create `ServerListWidgetStateTest.java`:

```java
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerListWidgetStateTest {
    @Test
    void stateColorsFollowSeverity() {
        assertEquals(WidgetThemeVariable.TEXT_MUTED, ServerListWidget.stateColor(RemoteCatalogState.AVAILABLE));
        assertEquals(WidgetThemeVariable.WARNING, ServerListWidget.stateColor(RemoteCatalogState.STALE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.stateColor(RemoteCatalogState.UNAVAILABLE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.stateColor(RemoteCatalogState.UNAUTHORIZED));
    }

    @Test
    void onlyDegradedServersGetABadge() {
        assertNull(ServerListWidget.badgeColor(RemoteCatalogState.AVAILABLE));
        assertEquals(WidgetThemeVariable.WARNING, ServerListWidget.badgeColor(RemoteCatalogState.STALE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.badgeColor(RemoteCatalogState.UNAVAILABLE));
        assertEquals(WidgetThemeVariable.DANGER, ServerListWidget.badgeColor(RemoteCatalogState.UNAUTHORIZED));
    }

    @Test
    void stateLabelsUseTheRemoteStateTranslations() {
        assertEquals("waypoint.remote.state.available",
                ServerListWidget.stateTranslationKey(RemoteCatalogState.AVAILABLE));
        assertEquals("waypoint.remote.state.stale",
                ServerListWidget.stateTranslationKey(RemoteCatalogState.STALE));
        assertEquals("waypoint.remote.state.unavailable",
                ServerListWidget.stateTranslationKey(RemoteCatalogState.UNAVAILABLE));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.widgets.ServerListWidgetStateTest" --console=plain`

Expected: `BUILD FAILED` during `compileTestJava` with `cannot find symbol` for `stateColor`.

- [ ] **Step 3: Add the state helpers, tooltip line and badge override to `ServerListWidget`**

Replace:
```java
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.crossserver.RemoteServerId;
```
With:
```java
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
```

Replace:
```java
import net.minecraft.world.item.Items;
import java.util.Map;
```
With:
```java
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;
import java.util.Map;
```

Replace:
```java
/** A bottom-anchored remote server icon rail, keyed by stable server identity. */
```
With:
```java
/** A remote server icon rail above the manager controls, keyed by stable server identity. */
```

Replace:
```java
    public void setServers(Map<RemoteServerId, CatalogReceiver.View> servers) {
```
With:
```java
    /** The theme role that marks a server's availability in its tooltip and the remote list footer. */
    public static WidgetThemeVariable stateColor(RemoteCatalogState state) {
        return switch (state) {
            case AVAILABLE -> WidgetThemeVariable.TEXT_MUTED;
            case STALE -> WidgetThemeVariable.WARNING;
            case UNAVAILABLE, UNAUTHORIZED -> WidgetThemeVariable.DANGER;
        };
    }

    /** Available servers need no badge; degraded servers show their state color. */
    static @Nullable WidgetThemeVariable badgeColor(RemoteCatalogState state) {
        return state == RemoteCatalogState.AVAILABLE ? null : stateColor(state);
    }

    public static String stateTranslationKey(RemoteCatalogState state) {
        return "waypoint.remote.state." + state.name().toLowerCase(Locale.ROOT);
    }

    public void setServers(Map<RemoteServerId, CatalogReceiver.View> servers) {
```

Replace:
```java
    @Override
    protected Component entryLabel(RemoteServerId server) {
        return Component.literal(servers.get(server).displayName() + " [" + server.value() + "]");
    }
```
With:
```java
    @Override
    protected Component entryLabel(RemoteServerId server) {
        CatalogReceiver.View view = servers.get(server);
        int stateRgb = WidgetThemeManager.getColor(stateColor(view.state())) & 0x00FFFFFF;
        // Vanilla tooltip splitting treats the newline as a line break.
        return Component.literal(view.displayName() + " [" + server.value() + "]\n")
                .append(Component.translatable(stateTranslationKey(view.state()))
                        .withStyle(style -> style.withColor(stateRgb)));
    }

    @Override
    protected @Nullable WidgetThemeVariable entryBadgeColor(RemoteServerId server) {
        return badgeColor(servers.get(server).state());
    }
```

- [ ] **Step 4: Add the badge hook to `IconListWidget`**

Replace:
```java
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
```
With:
```java
import _959.server_waypoint.common.client.gui.render.PaddingBackground;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
```

Replace:
```java
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;
```
With:
```java
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.*;
```

Replace:
```java
public abstract class IconListWidget<T> extends ShiftableClickableWidget implements Padding, Expandable {
    private float scrolledPosition;
```
With:
```java
public abstract class IconListWidget<T> extends ShiftableClickableWidget implements Padding, Expandable {
    private static final int BADGE_SIZE = 5;
    private float scrolledPosition;
```

In the widget renderer, replace:
```java
            this.drawIcon(context, this.entries.get(i));
            pop(context);
        }

        pop(context);
        context.disableScissor();
```
With:
```java
            this.drawIcon(context, this.entries.get(i));
            pop(context);
        }
        this.renderBadges(context, viewport);

        pop(context);
        context.disableScissor();
```

Replace:
```java
    protected abstract void drawIcon(GuiGraphicsExtractor context, T entry);
```
With:
```java
    protected abstract void drawIcon(GuiGraphicsExtractor context, T entry);

    /**
     * Returns the theme role of an entry's status badge, or {@code null} for no badge. Badges are
     * drawn in the top-right corner of the icon cell, above the icons.
     */
    protected @Nullable WidgetThemeVariable entryBadgeColor(T entry) {
        return null;
    }

    private void renderBadges(GuiGraphicsExtractor context, IconListLayout.Bounds viewport) {
        boolean overlay = false;
        for (int i = 0; i < this.entries.size(); i++) {
            WidgetThemeVariable badge = this.entryBadgeColor(this.entries.get(i));
            if (badge == null) {
                continue;
            }
            if (!overlay) {
                nextItemOverlayLayer(context);
                overlay = true;
            }
            IconListLayout.Position position = this.iconLayout.iconPosition(i, scrolledPosition, viewport);
            int left = this.iconSize - BADGE_SIZE;
            push(context);
            translate(context, position.x(), position.y());
            context.fill(left, 0, this.iconSize, BADGE_SIZE, getColor(BORDER));
            context.fill(left + 1, 1, this.iconSize - 1, BADGE_SIZE - 1, getColor(badge));
            pop(context);
        }
        if (overlay) {
            previousItemOverlayLayer(context);
        }
    }
```

- [ ] **Step 5: Add the above-items overlay layer to `DrawContextHelper`**

Replace:
```java
    public static void previousLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -1.0F);
        *///?}
    }
```
With:
```java
    public static void previousLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -1.0F);
        *///?}
    }

    /**
     * Moves later drawing above GUI items drawn earlier, such as a badge over an item icon. Newer
     * versions start a new render stratum. Older versions translate past the depth of GUI item
     * models, as vanilla does for item stack counts. Pair each call with
     * {@link #previousItemOverlayLayer}.
     */
    public static void nextItemOverlayLayer(GuiGraphicsExtractor context) {
        //? if >= 1.21.6 {
        context.nextStratum();
        //?} else {
        /*context.pose().translate(0.0F, 0.0F, 200.0F);
        *///?}
    }

    public static void previousItemOverlayLayer(GuiGraphicsExtractor context) {
        //? if < 1.21.6 {
        /*context.pose().translate(0.0F, 0.0F, -200.0F);
        *///?}
    }
```

Check the markers: every `//? if` has a matching `//?}`, and each `/*` opened after `//?} else {`
or `//? if < 1.21.6 {` closes with `*///?}`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.widgets.ServerListWidgetStateTest" --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
reset. Register and manually render each rail once through its high-level wrapper.
```
With:
```markdown
reset. Register and manually render each rail once through its high-level wrapper.
Specializations may also override `entryBadgeColor` to return a theme role. After drawing every
icon, the rail draws a 5×5 dot (a 3×3 fill in that role inside a one-pixel `BORDER` edge) flush
with the top-right corner of each badged icon cell, above the icons through
`DrawContextHelper.nextItemOverlayLayer` and inside the rail's scissor. `ServerListWidget` badges
stale servers with `WARNING` and unavailable ones with `DANGER`; available servers get no badge.
Its hover label adds the state on a second line, colored by `ServerListWidget.stateColor`, which
also colors the remote list footer.
```

Replace:
```markdown
Use `nextLayer`/`previousLayer` around suggestions and overlays when they must appear above normal controls.
```
With:
```markdown
Use `nextLayer`/`previousLayer` around suggestions and overlays when they must appear above normal controls.
Use `nextItemOverlayLayer`/`previousItemOverlayLayer` for marks drawn over GUI item icons, such as the
server rail badges. On 1.21.6 and later both pairs start a new render stratum, but before 1.21.6
`nextLayer` moves drawing up by only 1 in z while vanilla draws GUI item models near z 150;
`nextItemOverlayLayer` translates 200, the depth vanilla uses for item stack counts.
```

- [ ] **Step 8: Run the mods tests and compile both overlay branches**

Run: `./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:compileJava :mods:1.21.6-fabric:compileJava --console=plain`

Expected: `BUILD SUCCESSFUL`. 1.20.1 compiles the z-translation branch; 1.21.6 is the first
version with `nextStratum()`.

- [ ] **Step 9: Review checkpoint (no commit)**

Run: `git diff --check` → no output.

---

### Task 5: Remote empty states and unavailable-server rail

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteBrowserModel.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java`
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteBrowserModelTest.java`
- Modify: `docs/tips/gui/local-guide.md`

**Interfaces:**
- Consumes: the Task 1 remote empty-state keys and `waypoint.empty.no_matches`.
- Produces:
  - `RemoteBrowserModel.EmptyReason` (package-private enum: `UNAUTHORIZED`, `NO_SERVERS`,
    `SERVER_UNAVAILABLE`, `NO_MATCHES`, `SERVER_EMPTY`, `DIMENSION_EMPTY`) with `String translationKey()`.
  - `static EmptyReason emptyReason(RemoteCatalogState catalogState, boolean hasServers, @Nullable CatalogReceiver.View selectedServer, String query, @Nullable String dimensionScope)`.
  - `static List<String> dimensionNames(@Nullable CatalogReceiver.View view)`.
  - `RemoteWaypointPanel.BrowserTree.setEmptyReason(RemoteBrowserModel.EmptyReason)` and the
    private `RemoteWaypointPanel.hasServers()`; `rebuild()` sets the reason (Task 6 keeps this line).

- [ ] **Step 1: Write the failing tests**

In `RemoteBrowserModelTest.java`, add these methods before the class's closing `}`:

```java
    @Test void emptyReasonReportsCatalogFailuresFirst() {
        var available = view(a, RemoteCatalogState.AVAILABLE, 1, "name");
        assertEquals(RemoteBrowserModel.EmptyReason.UNAUTHORIZED, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.UNAUTHORIZED, true, available, "query", "dimension with spaces"));
        assertEquals(RemoteBrowserModel.EmptyReason.NO_SERVERS, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.UNAVAILABLE, false, null, "query", null));
        assertEquals(RemoteBrowserModel.EmptyReason.NO_SERVERS, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, false, null, "", null));
    }

    @Test void emptyReasonReportsAnUnusableSelectedServerBeforeTheSearch() {
        var available = view(a, RemoteCatalogState.AVAILABLE, 1, "name");
        var retained = new CatalogReceiver.View(available.snapshot(), RemoteCatalogState.UNAVAILABLE,
                "retained", null, "minecraft:compass");
        var noSnapshot = new CatalogReceiver.View(null, RemoteCatalogState.STALE, "empty", null, "minecraft:compass");
        for (var server : Arrays.asList(null, retained, noSnapshot)) {
            assertEquals(RemoteBrowserModel.EmptyReason.SERVER_UNAVAILABLE, RemoteBrowserModel.emptyReason(
                    RemoteCatalogState.AVAILABLE, true, server, "query", "dimension with spaces"));
        }
    }

    @Test void emptyReasonSeparatesSearchMissesFromEmptyScopes() {
        var available = view(a, RemoteCatalogState.AVAILABLE, 1, "name");
        var stale = view(a, RemoteCatalogState.STALE, 1, "name");
        assertEquals(RemoteBrowserModel.EmptyReason.NO_MATCHES, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, true, available, "query", "dimension with spaces"));
        assertEquals(RemoteBrowserModel.EmptyReason.DIMENSION_EMPTY, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, true, available, " ", "dimension with spaces"));
        assertEquals(RemoteBrowserModel.EmptyReason.SERVER_EMPTY, RemoteBrowserModel.emptyReason(
                RemoteCatalogState.AVAILABLE, true, stale, "", null));
    }

    @Test void emptyReasonsUseTheirTranslationKeys() {
        assertEquals("waypoint.remote.empty.unauthorized",
                RemoteBrowserModel.EmptyReason.UNAUTHORIZED.translationKey());
        assertEquals("waypoint.remote.no_servers",
                RemoteBrowserModel.EmptyReason.NO_SERVERS.translationKey());
        assertEquals("waypoint.remote.empty.server_unavailable",
                RemoteBrowserModel.EmptyReason.SERVER_UNAVAILABLE.translationKey());
        assertEquals("waypoint.empty.no_matches",
                RemoteBrowserModel.EmptyReason.NO_MATCHES.translationKey());
        assertEquals("waypoint.remote.empty.server",
                RemoteBrowserModel.EmptyReason.SERVER_EMPTY.translationKey());
        assertEquals("waypoint.remote.empty.dimension",
                RemoteBrowserModel.EmptyReason.DIMENSION_EMPTY.translationKey());
    }

    @Test void railDimensionsUseVanillaOrderAndHideUnavailableSnapshots() {
        var snapshot = new RemoteCatalogSnapshot(a, new RemoteRevision(1), Map.of(
                "custom:zeta", Map.of(), "minecraft:the_end", Map.of(),
                "minecraft:overworld", Map.of(), "custom:alpha", Map.of()), Instant.EPOCH);
        var stale = new CatalogReceiver.View(snapshot, RemoteCatalogState.STALE, "a", null, "minecraft:compass");
        var unavailable = new CatalogReceiver.View(snapshot, RemoteCatalogState.UNAVAILABLE, "a", null,
                "minecraft:compass");
        assertEquals(List.of("minecraft:overworld", "minecraft:the_end", "custom:alpha", "custom:zeta"),
                RemoteBrowserModel.dimensionNames(stale));
        assertTrue(RemoteBrowserModel.dimensionNames(unavailable).isEmpty());
        assertTrue(RemoteBrowserModel.dimensionNames(null).isEmpty());
    }
```

The test already imports `java.util.*` (for `Arrays`), `java.time.Instant` and
`_959.server_waypoint.crossserver.*`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.RemoteBrowserModelTest" --console=plain`

Expected: `BUILD FAILED` during `compileTestJava` with `cannot find symbol` for `EmptyReason`.

- [ ] **Step 3: Add the resolver and rail helper to `RemoteBrowserModel`**

Replace:
```java
import _959.server_waypoint.util.ColorUtils;
import _959.server_waypoint.util.StringCommandBuilder;

import java.util.*;
```
With:
```java
import _959.server_waypoint.util.ColorUtils;
import _959.server_waypoint.util.StringCommandBuilder;
import _959.server_waypoint.util.VanillaDimensionNames;
import org.jetbrains.annotations.Nullable;

import java.util.*;
```

Replace:
```java
    private static void flatten(List<Node> nodes, List<Node> flat) {
```
With:
```java
    /** Why the scoped remote tree has no rows; the panel shows the matching message. */
    enum EmptyReason {
        UNAUTHORIZED("waypoint.remote.empty.unauthorized"),
        NO_SERVERS("waypoint.remote.no_servers"),
        SERVER_UNAVAILABLE("waypoint.remote.empty.server_unavailable"),
        NO_MATCHES("waypoint.empty.no_matches"),
        SERVER_EMPTY("waypoint.remote.empty.server"),
        DIMENSION_EMPTY("waypoint.remote.empty.dimension");

        private final String translationKey;

        EmptyReason(String translationKey) { this.translationKey = translationKey; }

        String translationKey() { return translationKey; }
    }

    /** Picks the empty-tree message; catalog failures come before the selected server, then the search. */
    static EmptyReason emptyReason(RemoteCatalogState catalogState, boolean hasServers,
                                   @Nullable CatalogReceiver.View selectedServer, String query,
                                   @Nullable String dimensionScope) {
        if (catalogState == RemoteCatalogState.UNAUTHORIZED) return EmptyReason.UNAUTHORIZED;
        if (!hasServers) return EmptyReason.NO_SERVERS;
        if (selectedServer == null || selectedServer.snapshot() == null
                || selectedServer.state() == RemoteCatalogState.UNAVAILABLE) return EmptyReason.SERVER_UNAVAILABLE;
        if (!query.isBlank()) return EmptyReason.NO_MATCHES;
        return dimensionScope == null ? EmptyReason.SERVER_EMPTY : EmptyReason.DIMENSION_EMPTY;
    }

    /** Rail dimensions of a server; like {@link #roots}, hides an unavailable server's retained snapshot. */
    static List<String> dimensionNames(@Nullable CatalogReceiver.View view) {
        if (view == null || view.snapshot() == null || view.state() == RemoteCatalogState.UNAVAILABLE) return List.of();
        return view.snapshot().dimensions().keySet().stream()
                .sorted(VanillaDimensionNames::dimensionNameComparator).toList();
    }

    private static void flatten(List<Node> nodes, List<Node> flat) {
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.RemoteBrowserModelTest" --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Show the reason in the remote tree**

In `RemoteWaypointPanel.java`, replace:
```java
/** Read-only remote panel owned and rendered by the waypoint manager. */
final class RemoteWaypointPanel {
    private final Font font;
```
With:
```java
/** Read-only remote panel owned and rendered by the waypoint manager. */
final class RemoteWaypointPanel {
    private static final int EMPTY_MESSAGE_INSET = 5;
    private final Font font;
```

Replace:
```java
        tree.updateRoots(roots);
        details.setRemoteSelection(selected, selected == null ? null : displayed.get(selected.serverId()));
```
With:
```java
        tree.updateRoots(roots);
        tree.setEmptyReason(RemoteBrowserModel.emptyReason(displayedState, hasServers(),
                serverFilter == null ? null : displayed.get(serverFilter), filter, dimensionFilter));
        details.setRemoteSelection(selected, selected == null ? null : displayed.get(selected.serverId()));
```

Replace:
```java
    private static boolean contains(List<RemoteBrowserModel.Node> nodes, RemoteWaypointKey key) {
```
With:
```java
    private boolean hasServers() {
        return displayed.values().stream().anyMatch(view -> view.state() != RemoteCatalogState.UNAUTHORIZED);
    }

    private static boolean contains(List<RemoteBrowserModel.Node> nodes, RemoteWaypointKey key) {
```

Replace:
```java
        private final ScalableText emptyMessage = new ScalableText(
                2, 2, Component.translatable("waypoint.remote.no_servers"), TEXT_MUTED, font);
        BrowserTree() { super(0, 0, 160, 160, 20, Component.translatable("waypoint.remote.title")); }
```
With:
```java
        // Where the first row's text sits: inset from the left, centered in the first 20-pixel row.
        private final ScalableText emptyMessage = new ScalableText(
                EMPTY_MESSAGE_INSET, MovementAllowedScreen.centered(20, font.lineHeight) + 1,
                Component.translatable("waypoint.remote.no_servers"), TEXT_MUTED, font);
        BrowserTree() { super(0, 0, 160, 160, 20, Component.translatable("waypoint.remote.title")); }
        void setEmptyReason(RemoteBrowserModel.EmptyReason reason) {
            emptyMessage.setText(Component.translatable(reason.translationKey(), filter));
        }
```

In `renderEmpty`, replace:
```java
            int availableWidth = Math.max(1, getContentWidth() - 4);
```
With:
```java
            int availableWidth = Math.max(1, getContentWidth() - EMPTY_MESSAGE_INSET * 2);
```

- [ ] **Step 6: Empty the rail for an unavailable server**

In `WaypointManagerScreen.java`, replace:
```java
        var view = serverListWidget.getSelectedEntry() == null ? null : servers.get(serverListWidget.getSelectedEntry());
        List<String> dimensions = view == null || view.snapshot() == null ? List.of()
                : view.snapshot().dimensions().keySet().stream()
                .sorted(_959.server_waypoint.util.VanillaDimensionNames::dimensionNameComparator).toList();
        dimensionListWidget.updateDimensionNames(dimensions);
```
With:
```java
        var view = serverListWidget.getSelectedEntry() == null ? null : servers.get(serverListWidget.getSelectedEntry());
        dimensionListWidget.updateDimensionNames(RemoteBrowserModel.dimensionNames(view));
```

- [ ] **Step 7: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
server's catalog (including exported empty dimensions); the all-dimensions toggle applies within
that server.
```
With:
```markdown
server's catalog (including exported empty dimensions); `RemoteBrowserModel.dimensionNames` leaves
it empty for an unavailable server, whose retained snapshot the tree also hides. The all-dimensions
toggle applies within that server.
```

Replace:
```markdown
invisible remote selection. Remote scope is owned by the screen instance, not persisted to disk.
```
With:
```markdown
invisible remote selection. Remote scope is owned by the screen instance, not persisted to disk.
An empty remote tree explains itself: `RemoteBrowserModel.emptyReason(catalogState, hasServers,
selectedServer, query, dimensionScope)` returns an `EmptyReason` (unauthorized, no servers, an
unavailable selected server, a search miss, an empty server or an empty dimension, checked in that
order), and the tree renders its translation through a retained muted `ScalableText` at the first
row's text position, wrapped to the content width minus 5 pixels on each side.
```

- [ ] **Step 8: Run the mods tests**

Run: `./gradlew :mods:26.1.2-fabric:test --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Review checkpoint (no commit)**

Run: `git diff --check` → no output.

---

### Task 6: Remote footer, details layout and teleport tooltip

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java`
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java`
- Modify: `docs/tips/gui/local-guide.md`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanelLayoutTest.java`

**Interfaces:**
- Consumes: `ServerListWidget.stateColor` and `ServerListWidget.stateTranslationKey` (Task 4);
  `waypoint.remote.gui.server_status` (Task 1); `hasServers()` and `tree.setEmptyReason` (Task 5);
  `isSessionCurrent()` and `bindSession()` (Task 2).
- Produces:
  - `record RemoteWaypointPanel.ListAreaSplit(int treeHeight, boolean footerVisible)` and
    `static ListAreaSplit splitListArea(int height, int footerHeight, boolean hasServer)`.
  - Constants `FOOTER_GAP = 4` and `TREE_ROW_HEIGHT = 20`.
  - Fields `footer` (`ScalableText`) and `footerVisible` (`boolean`), read by the Task 9 probe.
  - `layout(...)` now receives the full list area below the search field.

- [ ] **Step 1: Write the failing test**

Create `RemoteWaypointPanelLayoutTest.java`:

```java
package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteWaypointPanelLayoutTest {
    @Test
    void footerSitsBelowTheTreeAfterAGap() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(200, 9, true);

        assertTrue(split.footerVisible());
        assertEquals(187, split.treeHeight());
    }

    @Test
    void wrappedFooterTakesMoreHeight() {
        assertEquals(178, RemoteWaypointPanel.splitListArea(200, 18, true).treeHeight());
    }

    @Test
    void footerStaysWhileTheTreeKeepsOneRow() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(33, 9, true);

        assertTrue(split.footerVisible());
        assertEquals(20, split.treeHeight());
    }

    @Test
    void footerHidesWhenTheTreeWouldLoseItsLastRow() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(32, 9, true);

        assertFalse(split.footerVisible());
        assertEquals(32, split.treeHeight());
    }

    @Test
    void footerHidesWithoutASelectedServer() {
        RemoteWaypointPanel.ListAreaSplit split = RemoteWaypointPanel.splitListArea(200, 9, false);

        assertFalse(split.footerVisible());
        assertEquals(200, split.treeHeight());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.RemoteWaypointPanelLayoutTest" --console=plain`

Expected: `BUILD FAILED` during `compileTestJava` with `cannot find symbol` for `ListAreaSplit`.

- [ ] **Step 3: Add the split helper, footer fields and imports**

In `RemoteWaypointPanel.java`, replace:
```java
import net.minecraft.network.chat.Component;

import java.util.*;
```
With:
```java
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
```

Replace:
```java
final class RemoteWaypointPanel {
    private static final int EMPTY_MESSAGE_INSET = 5;
    private final Font font;
```
With:
```java
final class RemoteWaypointPanel {
    static final int FOOTER_GAP = 4;
    static final int TREE_ROW_HEIGHT = 20;
    private static final int EMPTY_MESSAGE_INSET = 5;
    private static final int DETAILS_ACTION_GAP = 4;
    private final Font font;
```

Replace:
```java
    private WaypointSorting.SortMode sortMode = WaypointSorting.SortMode.NAME;
    private String feedback = "waypoint.remote.gui.feedback";
```
With:
```java
    private WaypointSorting.SortMode sortMode = WaypointSorting.SortMode.NAME;
    private final ScalableText footer;
    private boolean footerVisible;
    private boolean footerHasServer;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    // A failed teleport's message; shown until the selection changes.
    private @Nullable String failure;
```

Replace:
```java
        this.teleportButton.setTooltip(Tooltip.create(Component.translatable("waypoint.remote.gui.teleport_hint")));
    }
```
With:
```java
        this.teleportButton.setTooltip(Tooltip.create(Component.translatable("waypoint.remote.gui.teleport_hint")));
        this.footer = new ScalableText(0, 0, Component.empty(), TEXT_MUTED, font);
    }

    record ListAreaSplit(int treeHeight, boolean footerVisible) { }

    /**
     * Splits the list area below the search field between the tree and the status footer. The
     * footer needs a selected server and must leave the tree at least one row.
     */
    static ListAreaSplit splitListArea(int height, int footerHeight, boolean hasServer) {
        int treeHeight = height - FOOTER_GAP - footerHeight;
        if (!hasServer || treeHeight < TREE_ROW_HEIGHT) return new ListAreaSplit(Math.max(1, height), false);
        return new ListAreaSplit(treeHeight, true);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.screens.RemoteWaypointPanelLayoutTest" --console=plain`

Expected: `BUILD SUCCESSFUL`. (The unused `failure` and footer fields compile; the next steps use them.)

- [ ] **Step 5: Split the list area and align the teleport button**

Replace the whole `layout(...)` method:
```java
    void layout(int x, int y, int width, int height, int detailsX, int detailsY, int detailsWidth, int detailsHeight) {
        tree.setX(x);
        tree.setY(y);
        tree.setWidth(width);
        tree.setHeight(height);
        details.setX(detailsX);
        details.setY(detailsY);
        details.setWidth(detailsWidth);
        details.setHeight(Math.max(1, detailsHeight - 24));
        teleportButton.setX(detailsX);
        teleportButton.setY(detailsY + detailsHeight - 20);
        teleportButton.setVisualWidth(detailsWidth);
    }
```
With:
```java
    /** Receives the whole list area below the search field and the details content area. */
    void layout(int x, int y, int width, int height, int detailsX, int detailsY, int detailsWidth, int detailsHeight) {
        listX = x;
        listY = y;
        listWidth = width;
        listHeight = height;
        layoutList();
        // The outline bottom lines up with the content bottom, like every other panel's content.
        int buttonHeight = teleportButton.getVisualHeight();
        details.setX(detailsX);
        details.setY(detailsY);
        details.setWidth(detailsWidth);
        details.setHeight(Math.max(1, detailsHeight - buttonHeight - DETAILS_ACTION_GAP));
        teleportButton.setX(detailsX);
        teleportButton.setY(detailsY + detailsHeight - buttonHeight);
        teleportButton.setVisualWidth(detailsWidth);
    }

    private void layoutList() {
        footer.setMaxWidth(Math.max(1, listWidth));
        var split = splitListArea(listHeight, footer.getHeight(), footerHasServer);
        footerVisible = split.footerVisible();
        tree.setX(listX);
        tree.setY(listY);
        tree.setWidth(listWidth);
        tree.setHeight(split.treeHeight());
        footer.setPosition(listX, listY + listHeight - footer.getHeight());
    }

    private void updateFooter() {
        var view = serverFilter == null ? null : displayed.get(serverFilter);
        footerHasServer = view != null;
        if (view != null) {
            footer.setText(Component.translatable("waypoint.remote.gui.server_status", view.displayName(),
                    Component.translatable(ServerListWidget.stateTranslationKey(view.state()))));
            footer.setColor(ServerListWidget.stateColor(view.state()));
        }
        layoutList();
    }

    private void select(@Nullable RemoteWaypointKey key) {
        if (!Objects.equals(selected, key)) failure = null;
        selected = key;
    }

    private void updateTeleportAction() {
        teleportButton.active = teleportButton.visible && isSessionCurrent()
                && RemoteBrowserModel.prepare(catalogs, selected) != null;
        String tooltip = failure != null ? failure
                : teleportButton.active ? "waypoint.remote.gui.feedback" : "waypoint.remote.gui.teleport_hint";
        teleportButton.setTooltip(Tooltip.create(Component.translatable(tooltip)));
    }
```

- [ ] **Step 6: Route visibility, rebuilds, clicks and teleports through the new helpers**

Replace the whole `setVisible(...)` method:
```java
    void setVisible(boolean visible) {
        tree.visible = tree.active = visible;
        details.visible = details.active = visible;
        teleportButton.visible = visible;
        teleportButton.active = visible && session == catalogs.session()
                && RemoteBrowserModel.prepare(catalogs, selected) != null;
    }
```
With:
```java
    void setVisible(boolean visible) {
        tree.visible = tree.active = visible;
        details.visible = details.active = visible;
        teleportButton.visible = visible;
        updateTeleportAction();
    }
```

Replace the whole `rebuild()` method:
```java
    private void rebuild() {
        displayed = catalogs.snapshot();
        displayedState = catalogs.state();
        teleportButton.setTooltip(Tooltip.create(Component.translatable(feedback)));
        var roots = RemoteBrowserModel.scopedRoots(displayed, serverFilter, dimensionFilter, filter, grouped, sortMode, reversed);
        // Filter/removal must not leave an actionable invisible selection.
        if (selected != null && !contains(roots, selected)) selected = null;
        tree.updateRoots(roots);
        tree.setEmptyReason(RemoteBrowserModel.emptyReason(displayedState, hasServers(),
                serverFilter == null ? null : displayed.get(serverFilter), filter, dimensionFilter));
        details.setRemoteSelection(selected, selected == null ? null : displayed.get(selected.serverId()));
        teleportButton.active = teleportButton.visible && session == catalogs.session() && RemoteBrowserModel.prepare(catalogs, selected) != null;
    }
```
With:
```java
    private void rebuild() {
        displayed = catalogs.snapshot();
        displayedState = catalogs.state();
        var roots = RemoteBrowserModel.scopedRoots(displayed, serverFilter, dimensionFilter, filter, grouped, sortMode, reversed);
        // Filter/removal must not leave an actionable invisible selection.
        if (selected != null && !contains(roots, selected)) select(null);
        tree.updateRoots(roots);
        tree.setEmptyReason(RemoteBrowserModel.emptyReason(displayedState, hasServers(),
                serverFilter == null ? null : displayed.get(serverFilter), filter, dimensionFilter));
        details.setRemoteSelection(selected, selected == null ? null : displayed.get(selected.serverId()));
        updateFooter();
        updateTeleportAction();
    }
```

In `bindSession()` (from Task 2), replace:
```java
        session = current;
        selected = null;
        rebuild();
```
With:
```java
        session = current;
        select(null);
        rebuild();
```

Replace the whole `teleport()` method:
```java
    private void teleport() {
        var request = RemoteBrowserModel.prepare(catalogs, selected);
        if (session != catalogs.session() || request == null || !RemoteBrowserModel.isCurrent(catalogs, request)) {
            feedback = "waypoint.remote.gui.changed";
        } else if (ClientCommandUtils.sendCommand(request.command())) {
            MinecraftClientHelper.setScreen(null);
            return;
        } else {
            feedback = "waypoint.remote.gui.send_failed";
        }
        rebuild();
    }
```
With:
```java
    private void teleport() {
        var request = RemoteBrowserModel.prepare(catalogs, selected);
        if (!isSessionCurrent() || request == null || !RemoteBrowserModel.isCurrent(catalogs, request)) {
            failure = "waypoint.remote.gui.changed";
        } else if (ClientCommandUtils.sendCommand(request.command())) {
            MinecraftClientHelper.setScreen(null);
            return;
        } else {
            failure = "waypoint.remote.gui.send_failed";
        }
        rebuild();
    }
```

In `render(...)`, replace:
```java
        Component status = Component.translatable("waypoint.remote.gui.status", Component.translatable(
                "waypoint.remote.state." + catalogs.state().name().toLowerCase(Locale.ROOT)));
        drawText(context, font, font.plainSubstrByWidth(status.getString(), tree.getWidth()),
                tree.getX(), tree.getY() + tree.getHeight() + 3, getColor(TEXT_MUTED));
        tree.renderHoveredTooltip(context, mouseX, mouseY);

    }
```
With:
```java
        if (footerVisible) {
            footer.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
        }
        tree.renderHoveredTooltip(context, mouseX, mouseY);
    }
```

In `BrowserTree.onEntryClicked(...)`, replace:
```java
            var node = entry.value();
            selected = node.path().key();
            details.setRemoteSelection(selected, displayed.get(node.path().server()));
            teleportButton.active = teleportButton.visible && session == catalogs.session()
                    && RemoteBrowserModel.prepare(catalogs, selected) != null;
            return selected != null;
```
With:
```java
            var node = entry.value();
            select(node.path().key());
            details.setRemoteSelection(selected, displayed.get(node.path().server()));
            updateTeleportAction();
            return selected != null;
```

- [ ] **Step 7: Give the panel the full list height**

In `WaypointManagerScreen.updatePanelVisibility()`, replace:
```java
                layoutGeometry.middlePartWidth(), Math.max(1, layoutGeometry.waypointListHeight(searchField.getVisualHeight()) - 14),
```
With:
```java
                layoutGeometry.middlePartWidth(), Math.max(1, layoutGeometry.waypointListHeight(searchField.getVisualHeight())),
```

- [ ] **Step 8: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
confirmation dialog, after rechecking session, catalog revision, exact identity, and waypoint data.
```
With:
```markdown
confirmation dialog, after rechecking session, catalog revision, exact identity, and waypoint data.
The bottom of its outline lines up with the details content bottom, 4 pixels below the details
viewport. Its tooltip shows `teleport_hint` while disabled, the chat-feedback note while enabled,
and the failure message after a failed attempt until the selection changes. Below the remote tree,
a `ScalableText` footer shows the selected server's display name and state in
`ServerListWidget.stateColor`; `RemoteWaypointPanel.splitListArea` gives the tree the rest of the
list area and hides the footer when no server is selected or the tree would drop below one row.
```

- [ ] **Step 9: Run the mods tests and an old-version compile**

Run: `./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:compileJava --console=plain`

Expected: `BUILD SUCCESSFUL`. Confirm that nothing still reads the removed field:
`grep -n "feedback\b\|- 14" mods/src/main/java/_959/server_waypoint/common/client/gui/screens/RemoteWaypointPanel.java mods/src/main/java/_959/server_waypoint/common/client/gui/screens/WaypointManagerScreen.java`
→ only the `"waypoint.remote.gui.feedback"` string literal in `updateTeleportAction()`.

- [ ] **Step 10: Review checkpoint (no commit)**

Run: `git diff --check` → no output.

---

### Task 7: Local empty states

**Files:**
- Modify: `mods/src/main/java/_959/server_waypoint/common/client/gui/widgets/WaypointListWidget.java`
- Modify: the six language files
- Modify: `mods/src/test/java/_959/server_waypoint/common/client/gui/screens/RemoteGuiTranslationTest.java`
- Modify: `docs/tips/gui/local-guide.md`
- Create: `mods/src/test/java/_959/server_waypoint/common/client/gui/widgets/WaypointListWidgetEmptyStateTest.java`

**Interfaces:**
- Consumes: `waypoint.empty.no_matches`, `waypoint.empty.all`, `waypoint.empty.dimension` (Task 1).
- Produces: `WaypointListWidget.EmptyReason` (package-private enum: `NO_MATCHES`, `NO_WAYPOINTS`,
  `NO_WAYPOINTS_IN_DIMENSION`) with `String translationKey()`, and
  `static EmptyReason resolveEmptyReason(String query, boolean showAllDimensions)`.
  `EMPTY_INFO_TEXT` and the `waypoint.empty_mark` key are removed.

- [ ] **Step 1: Write the failing tests**

Create `WaypointListWidgetEmptyStateTest.java`:

```java
package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointListWidgetEmptyStateTest {
    @Test
    void searchMissWinsInEveryScope() {
        assertEquals(WaypointListWidget.EmptyReason.NO_MATCHES,
                WaypointListWidget.resolveEmptyReason("tower", false));
        assertEquals(WaypointListWidget.EmptyReason.NO_MATCHES,
                WaypointListWidget.resolveEmptyReason("tower", true));
    }

    @Test
    void blankSearchReportsTheScope() {
        assertEquals(WaypointListWidget.EmptyReason.NO_WAYPOINTS_IN_DIMENSION,
                WaypointListWidget.resolveEmptyReason("", false));
        assertEquals(WaypointListWidget.EmptyReason.NO_WAYPOINTS,
                WaypointListWidget.resolveEmptyReason("   ", true));
    }

    @Test
    void reasonsUseTheSharedTranslationKeys() {
        assertEquals("waypoint.empty.no_matches", WaypointListWidget.EmptyReason.NO_MATCHES.translationKey());
        assertEquals("waypoint.empty.all", WaypointListWidget.EmptyReason.NO_WAYPOINTS.translationKey());
        assertEquals("waypoint.empty.dimension",
                WaypointListWidget.EmptyReason.NO_WAYPOINTS_IN_DIMENSION.translationKey());
    }
}
```

In `RemoteGuiTranslationTest.java`, replace:
```java
    private static final List<String> RETIRED_KEYS = List.of("waypoint.remote.gui.selector");
```
With:
```java
    private static final List<String> RETIRED_KEYS = List.of("waypoint.remote.gui.selector", "waypoint.empty_mark");
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.widgets.WaypointListWidgetEmptyStateTest" --tests "_959.server_waypoint.common.client.gui.screens.RemoteGuiTranslationTest" --console=plain`

Expected: `BUILD FAILED` during `compileTestJava` with `cannot find symbol` for `EmptyReason`.
(After Step 3 compiles, `retiredManagerKeysAreRemovedFromEveryLocale` still fails on
`en_us: waypoint.empty_mark` until Step 5.)

- [ ] **Step 3: Add the resolver and the empty message to `WaypointListWidget`**

Replace:
```java
    public static int TELEPORT_KEY = 84;
    public static final Component EMPTY_INFO_TEXT = Component.translatable("waypoint.empty_mark");
    private static final int listIconSize = 16;
```
With:
```java
    public static int TELEPORT_KEY = 84;
    private static final int EMPTY_MESSAGE_INSET = 5;
    private static final int listIconSize = 16;
```

Replace:
```java
    private final int buttonIconHrzOffset;
    private final int btnWidth = 19;
```
With:
```java
    private final int buttonIconHrzOffset;
    private final ScalableText emptyMessage;
    private final int btnWidth = 19;
```

Replace:
```java
        buttonIconHrzOffset = centered(btnWidth, buttonIconSize);
    }
```
With:
```java
        buttonIconHrzOffset = centered(btnWidth, buttonIconSize);
        emptyMessage = new ScalableText(EMPTY_MESSAGE_INSET, textVertOffset, Component.empty(), TEXT_MUTED, textRenderer);
    }
```

Replace:
```java
    static void setDimensionExpanded(String dimensionName, boolean expanded) {
        DIMENSION_EXPANSION_STATES.put(dimensionName, expanded);
    }
```
With:
```java
    static void setDimensionExpanded(String dimensionName, boolean expanded) {
        DIMENSION_EXPANSION_STATES.put(dimensionName, expanded);
    }

    /** Why the list has no rows; {@link #renderEmpty} shows the matching message. */
    enum EmptyReason {
        NO_MATCHES("waypoint.empty.no_matches"),
        NO_WAYPOINTS("waypoint.empty.all"),
        NO_WAYPOINTS_IN_DIMENSION("waypoint.empty.dimension");

        private final String translationKey;

        EmptyReason(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return this.translationKey;
        }
    }

    static EmptyReason resolveEmptyReason(String query, boolean showAllDimensions) {
        if (!query.isBlank()) {
            return EmptyReason.NO_MATCHES;
        }
        return showAllDimensions ? EmptyReason.NO_WAYPOINTS : EmptyReason.NO_WAYPOINTS_IN_DIMENSION;
    }
```

Replace:
```java
        updateRoots(roots);
        reconcileSelection(display);
```
With:
```java
        updateRoots(roots);
        emptyMessage.setText(Component.translatable(
                resolveEmptyReason(this.searchQuery, this.showAllDimensions).translationKey(),
                this.searchQuery
        ));
        reconcileSelection(display);
```

Replace:
```java
    @Override
    protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        drawText(context, textRenderer, EMPTY_INFO_TEXT, 5, textVertOffset, getColor(TEXT_DISABLED), true);
    }
```
With:
```java
    @Override
    protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int availableWidth = Math.max(1, getContentWidth() - EMPTY_MESSAGE_INSET * 2);
        if (emptyMessage.getWidth() != availableWidth) {
            emptyMessage.setMaxWidth(availableWidth);
        }
        emptyMessage.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }
```

`drawText` and `TEXT_DISABLED` stay imported; other rows still use them.

- [ ] **Step 4: Run the widget test to verify it passes**

Run: `./gradlew :mods:26.1.2-fabric:test --tests "_959.server_waypoint.common.client.gui.widgets.WaypointListWidgetEmptyStateTest" --console=plain`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Remove `waypoint.empty_mark` from all six locales**

Delete exactly one line from each file:

| File | Line to delete |
| --- | --- |
| `en_us.json` | `  "waypoint.empty_mark": "<Empty>",` |
| `es_es.json` | `  "waypoint.empty_mark": "<Vacío>",` |
| `he_il.json` | `  "waypoint.empty_mark": "<ריק>",` |
| `zh_cn.json` | `  "waypoint.empty_mark": "<空>",` |
| `zh_hk.json` | `  "waypoint.empty_mark": "<空>",` |
| `zh_tw.json` | `  "waypoint.empty_mark": "<空>",` |

Each line is followed by the `waypoint.empty.no_matches` line added in Task 1, so the JSON stays valid.

- [ ] **Step 6: Update the GUI guide**

In `docs/tips/gui/local-guide.md`, replace:
```markdown
while list metadata remains muted. The dimension rail remains selectable in all-dimensions mode;
its selection is retained for returning to selected-dimension scope.
```
With:
```markdown
while list metadata remains muted. The dimension rail remains selectable in all-dimensions mode;
its selection is retained for returning to selected-dimension scope.
When a query leaves no rows, `WaypointListWidget.resolveEmptyReason(query, showAllDimensions)`
picks a search-miss message, or a no-waypoints message for all dimensions or the selected one with
a hint to use the + button, and the list renders it through a retained muted `ScalableText` at the
first row's text position.
```

- [ ] **Step 7: Run all mods tests**

Run: `./gradlew :mods:26.1.2-fabric:test --console=plain`

Expected: `BUILD SUCCESSFUL`, including `RemoteGuiTranslationTest` with both retired keys.

- [ ] **Step 8: Review checkpoint (no commit)**

Run: `grep -rn "EMPTY_INFO_TEXT\|waypoint.empty_mark" mods/src` → no output.
Run: `git diff --check` → no output.

---

### Task 8: Step 18 spec

**Files:**
- Modify: `docs/features/cross-server/specs/cross-server-gui.md`

**Interfaces:**
- Consumes: the behavior built in Tasks 2–7.
- Produces: an accurate Step 18 description that links the screen design.

- [ ] **Step 1: Replace the outdated selector and tree paragraph**

Replace:
```markdown
Open the waypoint manager and choose **Server: Local / Remote…** above its dimension/list hierarchy.
The remote branch displays server → dimension → list → waypoint rows. Expand/collapse the hierarchy,
search by identifier/list/keyword, and reverse the name ordering. Select a waypoint to see its exact
server identity and read-only metadata. The Local button returns to the existing local manager.
```
With:
```markdown
Open the waypoint manager and use the local/remote toggle in its sidebar. The toggle appears once
the remote catalog holds at least one server. The server rail above the controls selects a server,
the dimension rail shows that server's catalog, and the list shows its list → waypoint rows, or
dimension → list → waypoint rows in all-dimensions mode. Expand/collapse the hierarchy, search by
identifier/list/keyword, and use the shared sort and group controls. Select a waypoint to see its
exact server identity and read-only metadata. The toggle returns to the local view. The
[waypoint manager screen design](../../waypoint-manager/specs/2026-09-28-waypoint-manager-screen-design.md)
describes the whole screen.
```

- [ ] **Step 2: Replace the availability sentence**

Replace:
```markdown
initiate teleport. Server availability is shown beside its label and in the panel status.
```
With:
```markdown
initiate teleport. Server availability appears as a badge and a tooltip line on the server rail, and
in the footer under the list.
```

- [ ] **Step 3: Replace the session sentence**

Replace:
```markdown
- `RemoteClientCatalogs.session()` increments on `clear()` and is independent of remote revisions.
  The screen closes on generation changes, including after a handshake.
```
With:
```markdown
- `RemoteClientCatalogs.session()` increments on `clear()` and is independent of remote revisions.
  In the remote view the screen closes on generation changes, including after a handshake; the
  local view rebuilds the screen and rebinds the panel to the new session instead.
```

- [ ] **Step 4: Review checkpoint (no commit)**

Run: `git diff --check -- docs` → no output. Open the new link target path
`docs/features/waypoint-manager/specs/2026-09-28-waypoint-manager-screen-design.md` to confirm it exists.

---

### Task 9: Probe checks, full verification and validation record

**Files:**
- Modify: `tools/cross-server-gui-test/RemoteGuiProbe.java`
- Modify: `tools/cross-server-gui-test/README.md`
- Create: `docs/features/waypoint-manager/validation/2026-09-28-results.md` (use the date the checks actually run)
- Delete: `docs/features/waypoint-manager/validation/.gitkeep`
- Modify: `docs/features/waypoint-manager/README.md`

**Interfaces:**
- Consumes: the `RemoteWaypointPanel` fields `footer` and `footerVisible` (Task 6), the
  `BrowserTree.emptyMessage` field (Task 5), and the toggle rule (Task 3).
- Produces: native checks and a validation record.

- [ ] **Step 1: Add a text helper to the probe**

In `RemoteGuiProbe.java`, replace:
```java
    private TranslucentButton button() throws Exception {
```
With:
```java
    private static String text(ScalableText label) throws Exception {
        return ((net.minecraft.network.chat.Component) field(ScalableText.class, "text").get(label)).getString();
    }
    private TranslucentButton button() throws Exception {
```

- [ ] **Step 2: Check the search-miss empty message (stage 3)**

Replace:
```java
                ((WaypointSearchBarWidget) field(WaypointManagerScreen.class, "searchField").get(local)).setValue("not-found");
                check(!button().active, "filtered-out selection disables teleport");
            }
```
With:
```java
                ((WaypointSearchBarWidget) field(WaypointManagerScreen.class, "searchField").get(local)).setValue("not-found");
                check(!button().active, "filtered-out selection disables teleport");
                var filteredTree = field(RemoteWaypointPanel.class, "tree").get(remote);
                var emptyMessage = (ScalableText) field(filteredTree.getClass(), "emptyMessage").get(filteredTree);
                check(text(emptyMessage).contains("not-found"), "search miss explains the empty tree");
            }
```

- [ ] **Step 3: Check the stale-server footer (stage 5)**

Replace:
```java
                clickAt(local, tree.getX() + 24, tree.getY() + 20 + 10);
                check(!button().active, "stale target remains read-only");
                install(RemoteCatalogState.AVAILABLE);
```
With:
```java
                clickAt(local, tree.getX() + 24, tree.getY() + 20 + 10);
                check(!button().active, "stale target remains read-only");
                var footer = (ScalableText) field(RemoteWaypointPanel.class, "footer").get(remote);
                check((boolean) field(RemoteWaypointPanel.class, "footerVisible").get(remote)
                        && text(footer).contains("Stale"), "footer shows the selected server's stale state");
                install(RemoteCatalogState.AVAILABLE);
```

- [ ] **Step 4: Check that an empty cache hides the toggle (stage 6)**

Replace:
```java
                check(files.equals(localFiles(mc)), "local files unchanged");
                System.out.println("REMOTE_GUI_PROBE PASS: native Fabric 26.1.2 screen input, immediate teleport, resize, cache transitions and local isolation");
```
With:
```java
                check(files.equals(localFiles(mc)), "local files unchanged");
                var emptyManager = new WaypointManagerScreen(client);
                mc.setScreen(emptyManager);
                check(!((AbstractWidget) field(WaypointManagerScreen.class, "serverScopeToggle").get(emptyManager)).visible,
                        "empty remote cache hides the local/remote toggle");
                mc.setScreen(null);
                System.out.println("REMOTE_GUI_PROBE PASS: native Fabric 26.1.2 screen input, immediate teleport, resize, cache transitions, footer, empty states, toggle availability and local isolation");
```

- [ ] **Step 5: Describe the new checks in the probe README**

In `tools/cross-server-gui-test/README.md`, replace:
```markdown
catalogs and invalidates the teleport action by resetting the cache session. It checks the original
local manager object and saved files remain unchanged.
```
With:
```markdown
catalogs and invalidates the teleport action by resetting the cache session. It checks the original
local manager object and saved files remain unchanged. It also checks that a search miss explains
the empty tree, that the footer reports a stale server, and that an empty remote cache hides the
local/remote toggle.
```

- [ ] **Step 6: Run the full automated verification**

Run: `./gradlew :mods:26.1.2-fabric:test --console=plain`
Expected: `BUILD SUCCESSFUL`. Record the test count from `mods/versions/26.1.2-fabric/build/test-results/test`
(or the Gradle summary) for the validation record.

Run: `./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.1-forge:compileJava :mods:1.21.6-fabric:compileJava :mods:26.3-fabric:compileJava :mods:26.3-neoforge:compileJava --console=plain`
Expected: `BUILD SUCCESSFUL`.

Run: `git diff --check`
Expected: no output.

Run: `grep -n "//?\|\*///?}\|/\*?" mods/src/main/java/_959/server_waypoint/common/client/gui/render/DrawContextHelper.java`
Expected: every `//? if` in the new `nextItemOverlayLayer`/`previousItemOverlayLayer` methods has a
matching `//?}` or `*///?}`.

- [ ] **Step 7: Run the native probe (optional; needs the HeadlessMC setup)**

Run: `./gradlew -I tools/cross-server-gui-test/probe.gradle.kts :mods:26.1.2-fabric:stageRemoteGuiProbe --max-workers=2 --console=plain`
Then follow `tools/cross-server-gui-test/README.md` to launch it in a disposable HeadlessMC root.
Expected: the game prints `REMOTE_GUI_PROBE PASS:` and writes `remote-gui-result.txt` with `PASS`.
If the HeadlessMC setup isn't available, record the probe as not run.

- [ ] **Step 8: In-game checks (manual; compiling can't prove these)**

Check each item and note the Minecraft version and loader used:
- Open the manager while joining a dedicated server with Server Waypoint: it shows the unsupported or
  loading message and then builds the full UI in place when sync finishes.
- Switch servers through a proxy with the local view open: the screen rebuilds (or vanilla's loading
  screen removes it) and afterwards shows the new server's waypoints and dimensions.
- Singleplayer and a server with cross-server disabled: no local/remote toggle. The other controls
  stay in place at the bottom, and the space above them for the dimension rail is 20 pixels taller.
- With stale or unavailable remote servers: amber and red corner dots are drawn above the item icons
  on 1.20.1 and on a 1.21.6-or-later target; the rail tooltip's second line shows the state.
- At a 427×240 scaled viewport (GUI scale 4 on an 1708×960 window), the footer wraps and the tree
  keeps at least one row, or the footer hides.
- The teleport button's outline bottom lines up with the bottom of the local details content.
- Each empty-state message: a search miss in both views, an empty dimension, an empty
  all-dimensions view, and an unavailable remote server.

- [ ] **Step 9: Write the validation record**

Create `docs/features/waypoint-manager/validation/2026-09-28-results.md` (rename it to the actual date).
Fill in every section from the results you observed; do not claim a check you didn't run:

```markdown
# Waypoint manager screen validation

## Automated checks

- `./gradlew :mods:26.1.2-fabric:test --console=plain`: <result and test count>.
- `./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.1-forge:compileJava :mods:1.21.6-fabric:compileJava :mods:26.3-fabric:compileJava :mods:26.3-neoforge:compileJava --console=plain`: <result>.
- `git diff --check`: <result>.

## Native GUI probe

<PASS with the production JAR's SHA-256, or "Not run" with the reason.>

## In-game checks

<One line per item from Task 9 Step 8: version and loader, then passed, failed (with what was seen),
or not run.>

## Known behavior

On a dedicated server the client reports `NO_SERVERSIDE_SUPPORT` until the handshake arrives, so a
manager open in that window briefly shows the unsupported message before loading. This is
documented in the screen design and was left as is.
```

Delete `docs/features/waypoint-manager/validation/.gitkeep`.

In `docs/features/waypoint-manager/README.md`, replace:
```markdown
- [Implementation plan](plans/2026-09-28-waypoint-manager-screen.md)

Validation records don't exist yet; `validation/` holds a `.gitkeep` until they do.
```
With (use the record's actual file name):
```markdown
- [Implementation plan](plans/2026-09-28-waypoint-manager-screen.md)
- [Validation results](validation/2026-09-28-results.md)
```

- [ ] **Step 10: Final review checkpoint (no commit)**

Run: `git status --short`
Expected: only the files listed in this plan's file map, the new test files and the validation
record. Report the results to the user and ask whether to commit; don't commit without that request.
