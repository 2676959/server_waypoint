# Command sender logging validation — 2026-10-03

The logging context retains both the original sender and the executor. Regression tests first failed
because sender fields were absent, then passed after the context and platform sender lookup were added.

Regression coverage checks:

- A player sender with a different executor, including each player's UUID.
- Console-issued commands that execute as a player.
- Identity snapshots retained after delayed save and teleport completion, even if the source fixture changes.
- Upload request and completion entries retain the same initiating sender identity.

Verification command:

```sh
./gradlew :common:test :mods:26.1.2-fabric:compileJava :mods:1.20.1-fabric:compileJava :mods:26.3-neoforge:compileJava :mods:26.2-forge:compileJava :paper:26.2-paper:compileJava --continue --console=plain
```

Result: **776 common tests passed**, all five platform compile targets passed. Existing Forge
mapping/deprecation warnings and the NeoForge Xaero deprecation warning remain. `git diff --check`
passed.

Local Minecraft sources confirm that `CommandSourceStack.withEntity` preserves its `source` while
replacing the executor and stack name. Sender lookup reuses the existing versioned receiving-player
resolution, covering both player command sources from 1.21.2 onward and older player objects.

No live Minecraft/Paper `/execute as` command was run. GUI and destination-arrival actions identify
the acting player as their sender; original source-server command provenance is logged at the source
and is not added to the cross-server wire protocol by this change. Changes are uncommitted.
