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
