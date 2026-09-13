# Step 18 native GUI probe

This explicitly invoked test mod boots Fabric **26.1.2** in a disposable offline flat world.
It opens the real local manager, uses registered mouse input to toggle the remote panel in place, selects
an exact quoted target, cancels confirmation, resizes the browser, toggles list/flat view and sort
direction through the shared sidebar controls, returns to local and remote views, filters rows, refreshes stale
catalogs and invalidates a confirmation by resetting the cache session. It checks the original
local manager object and saved files remain unchanged. A fixture populates the real remote cache;
this probe does not test TCP, permissions or a real cross-server teleport.

The probe and its Gradle init script are outside production source sets. Normal builds do not
register these tasks. To build the fixture and stage the required runtime mods:

```sh
rtk ./gradlew -I tools/cross-server-gui-test/probe.gradle.kts :mods:26.1.2-fabric:stageRemoteGuiProbe --max-workers=2 --console=plain
```

Create a **new disposable root** outside your real Minecraft directory. Install HeadlessMC 2.10.0
there and pin both `hmc.mcdir=<root>/store` and `hmc.gamedir=<root>/game` on every launcher call.
Run `help` and `help launch` first. Use `fabric 26.1.2` to install the runtime, with
`hmc.java.versions` pointing to an existing Java 25 `bin/java`. The validated loader is 0.19.5.
Copy the production JAR, probe JAR, aggregate Fabric API JAR and Fabric permissions API JAR from
`build/remote-gui-native-mods` into `<root>/game/mods`. Do not copy the individual Fabric API modules
when the aggregate JAR is installed. Put this in `<root>/game/options.txt`:

```text
pauseOnLostFocus:false
onboardAccessibility:false
lang:en_us
```

The HeadlessMC JSON command test is:

```json
{"name":"Step 18 native GUI","steps":[{"type":"CONTAINS","message":"REMOTE_GUI_PROBE PASS:"}],"timeout":120,"totalTimeout":240}
```

From the isolated launcher root, run:

```sh
rtk proxy java -Dhmc.java.versions=<java25-bin> -Dhmc.mcdir=<root>/store -Dhmc.gamedir=<root>/game -Dhmc.offline.username=GuiProbe -Dhmc.assets.dummy=true -Dhmc.test.filename=<root>/test.json -Dhmc.crash.report.watcher=true -jar headlessmc.jar --command 'launch fabric-loader-0.19.5-26.1.2 -lwjgl -offline -quit'
```

The probe creates a fresh timestamped world, writes `game/remote-gui-result.txt`, and stops the client.
Require the fresh PASS marker, a successful HeadlessMC command test and clean process termination.
Preserve the production JAR hash, client log and test result with the run's evidence. The graphics
and audio are stubbed, so OpenAL/missing-sound warnings are expected. Screenshot/visual validation,
real proxy transfers, and the other supported game versions are separate release checks.


## Step 19 real network GUI probe

`LiveRemoteGuiProbe.java` runs against a disposable Paper/Fabric 26.2 pair behind
Velocity. It requires an available backend `b` with `minecraft:overworld` list
`Test` containing `Target` and no `Added` entry. Grant the disposable player the
normal remote browsing and teleport permissions. Build and stage explicitly:

```sh
rtk proxy ./gradlew -I tools/cross-server-gui-test/live-probe.gradle.kts :mods:26.2-fabric:stageLiveRemoteGuiProbe --max-workers=2 --console=plain
```

Install the staged files from `build/live-remote-gui-native-mods` in a fresh
HeadlessMC game directory using the isolation and options above, selecting Fabric
26.2. Connect through Velocity with Quick Play. The validated runtime used Fabric
Loader 0.19.5, Fabric API 0.152.1+26.2 and Java 25. After the probe logs
`LIVE_REMOTE_GUI WAIT_REMOTE_MUTATION`, run this on backend b through its console:

```text
wp add minecraft:overworld Test 45 80 30 Added A gold 0 false
```

Require the ordered markers `SYNC_PROTOCOL_11`, `WAIT_REMOTE_MUTATION`,
`NETWORK_CATALOG_CHANGED`, `CONFIRMATION_OPEN`, `CONFIRMED_BY_CLICK` and
`PASS real protocol-11 catalog update and confirmed proxy transfer`, followed by
clean client termination. The probe opens the initial local manager directly,
then uses native mouse dispatch for remote navigation, target selection and
confirmation. It receives catalog changes over the actual network and checks
fresh synchronization at `(45.5, 80, 30.5)` after transfer. It does not inject cache
or network state. Preserve the fresh `live-remote-gui-result.txt`, client/server
logs and artifact hashes. Headless graphics remain unsuitable for visual approval.
See [recorded evidence](../../docs/features/cross-server/validation/cross-server-step19-live-gui/results.json).
