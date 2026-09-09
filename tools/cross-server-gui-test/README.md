# Step 18 native GUI probe

This explicitly invoked test mod boots Fabric **26.1.2** in a disposable offline flat world.
It opens the real local manager, uses registered mouse input to enter the remote branch, selects
an exact quoted target, cancels confirmation, resizes the browser, filters rows, refreshes stale
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
