# Command tree probe (Minecraft 26.3)

This isolated development probe exercises the actual native command-copy and
packet algorithms without starting a server. It is not included in production
source sets or JARs.

Run:

```sh
rtk proxy ./gradlew :mods:26.3-fabric:issue35Probe \
    --init-script tools/server-performance/command-tree/probe.init.gradle.kts \
    --console=plain --max-workers=2
```

The optional `-Pissue35BaselineSource=/absolute/path/ListCommandOptions.java`
compiles an original implementation into a separate probe output directory.

See [issue #35 validation](../../../docs/features/server-performance/validation/2026-10-27-command-tree.md)
for original/candidate results, source hashes, baseline extraction, test failures,
and measurement limits.

## Complete Shared and Private command registration

The full-tree probe uses the actual `CoreWaypointCommand` registration, an empty catalog, and all
navigation methods. It includes Shared, Private, Remote, two redirect aliases, permission filtering,
and native packet encode/decode and redirect reconstruction. Its platform fixture supplies native
Brigadier string arguments for dimension, position, and icon adapters; it does not start a server or
execute gameplay commands.

```sh
rtk proxy ./gradlew :mods:26.3-fabric:privateCommandTreeProbe \
    --init-script tools/server-performance/command-tree/probe.init.gradle.kts \
    --console=plain --max-workers=2
```

This adds an isolated task without changing production dependencies, source sets, or packaging.
See [Private command performance validation](../../../docs/features/private-waypoint/validation/2026-10-28-command-performance.md)
for the before/after measurements, budgets, regression evidence, and proof boundaries.
