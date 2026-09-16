# Folia Upload Transport Live-Test Environment

This runbook prepares resolution 4A only. It does not mark any resolution 4
scenario as passed. Every generated game directory, world, Xaero file, MCC
configuration, fixture, and log stays beneath one new disposable directory
outside the repository.

## Prerequisites

- Java suitable for Minecraft 1.21.11 and the chosen Folia build.
- An exact Minecraft 1.21.11 Folia server JAR.
- An existing executable Minecraft Console Client. The launcher runs it with an
  offline identity and an isolated working directory; it never reads or writes a
  personal MCC configuration.
- Enough memory for one server and two graphical Fabric development clients.

Do not point the preparation script at an existing server, launcher instance,
Minecraft directory, or repository directory. It refuses existing and
repository-owned roots.

## Prepare

```shell
tools/folia-live-test/prepare.sh \
    --folia-jar /absolute/path/to/folia-1.21.11.jar \
    --mcc /absolute/path/to/MinecraftClient
```

The script prints the new environment root. It builds these production targets
directly, without changing either Stonecutter active project:

```shell
./gradlew --no-daemon --no-parallel --max-workers=2 \
    :paper:1.21.11-paper:shadowJar \
    :mods:1.21.11-fabric:build
```

It also explicitly builds the test-only region-load plugin, compiles the
development probe, generates the deterministic fixtures, installs the plugin
and fixture, accepts the EULA, and configures port `25611`, offline identities,
and a disposable world. Test-only source sets are absent from the production
Paper and Fabric JARs.

The initial evidence is in `evidence/environment.txt`,
`evidence/client-roles.tsv`, and `fixtures/fixture-manifest.json`. Before a run,
record the exact Folia build reported by `/version`; the JAR manifest and SHA-256
are already captured.

## Launch and place clients

Use a separate terminal for each long-running process:

```shell
tools/folia-live-test/run.sh <environment-root> server
tools/folia-live-test/run.sh <environment-root> alpha
tools/folia-live-test/run.sh <environment-root> bravo
tools/folia-live-test/run.sh <environment-root> mcc
```

The Fabric launches use `SWAlpha` and `SWBravo`, load the normal 1.21.11 Fabric
development source set with its declared Xaero dependencies, and use separate
game/config/Xaero/log directories. MCC uses `SWVanilla` and does not register a
Server Waypoint transport channel.

After all three clients join, run these commands from the Folia console before
every scenario group:

```text
execute in minecraft:overworld run tp SWAlpha 0 80 0
execute in minecraft:overworld run tp SWBravo 8192 80 8192
execute in minecraft:overworld run tp SWVanilla -8192 80 -8192
version
```

Record the dimension and coordinates from `client-roles.tsv`. Confirm in the
server and client logs that `SWAlpha` and `SWBravo` negotiated protocol 9. The
absence of that handshake for `SWVanilla`, while MCC remains connected, is the
incompatible-client gate.

## Control fixture gate

The server starts with `control` (four exactly specified waypoints, revision 7)
and `large` (4,092 deterministic waypoints, revision 19) in the overworld. The
fixture generator fails if the compressed world snapshot does not require at
least nine 24 KiB transport frames.

On `SWAlpha`:

1. Run `/wp download` and confirm the `control` and `large` sets appear in
   Xaero.
2. Run `/wp upload xaero minecraft:overworld control` without editing the set. The
   normal server-preferred merge must report every control waypoint unchanged.
3. Verify the server file while the server is stopped, or after a clean save and
   shutdown:

```shell
tools/folia-live-test/run.sh <environment-root> verify
```

The verifier uses GSON and the generated manifest to require revision 7 and the
exact four waypoint contents. Repeat download/upload with `SWBravo` to cover the
second isolated Xaero directory.

## Development protocol probe

The probe is a separate Fabric client source set. It registers the production
protocol 9 channel IDs, uses the production `common` codecs, sends at most eight
frames and 192 KiB per client tick, and logs structured `SW_PROBE` events. It
does not load the Server Waypoint production client mod and cannot be packaged
by `build`, `shadowJar`, or `remapJar`.

Start one mode per fresh probe launch:

```shell
tools/folia-live-test/run.sh <environment-root> probe valid
tools/folia-live-test/run.sh <environment-root> probe partial 2
tools/folia-live-test/run.sh <environment-root> probe bad-checksum
tools/folia-live-test/run.sh <environment-root> probe bad-header
tools/folia-live-test/run.sh <environment-root> probe saturate
tools/folia-live-test/run.sh <environment-root> probe disconnect 2
```

- `valid` negotiates version 9, runs `/wp upload xaero`, and sends a valid 4,096
  waypoint uncompressed upload over multiple ticks.
- `partial N` stops after exactly `N` frames and remains connected for timeout
  observation.
- `bad-checksum` sends the complete transfer with a corrupted checksum.
- `bad-header` sends one frame whose sequence equals its chunk count.
- `saturate` issues nine `/wp download` commands in one client tick so the
  per-peer eight-transfer admission boundary deterministically reports busy.
- `disconnect N` sends exactly `N` frames, then closes its connection.

Place `SWProbe` at `(16384, 80, 0)` before a mode if the scenario depends on a
third region. Use a fresh launch after the five-second upload cooldown when a
mode acquired a lease.

## Reversible low-TPS control

The development-only plugin blocks only the scheduler for the target player's
owned region, for a bounded duration. For example, from an operator client:

```text
/swregionload start 30 90 SWAlpha
/swregionload status
/swregionload stop SWAlpha
```

The example applies 90 ms of load per region tick for at most 30 seconds.
`stop` cancels it early, expiry cancels it automatically, disconnect retires the
entity task, and plugin shutdown cancels every remaining task. Compare Folia's
region/tick diagnostics before, during, and after the load; do not proceed until
the target region returns to its baseline while `SWBravo` remains responsive.

## Restart and evidence gate

Stop Folia cleanly with `stop`, restart it with the same server command, and
confirm:

- no lease or incomplete transfer survives;
- only the expected committed waypoint file remains;
- both compatible clients renegotiate protocol 9;
- startup contains no unsupported scheduler or ownership exception.

Append paths and final waypoint checksum to the evidence record:

```shell
tools/folia-live-test/run.sh <environment-root> record
```

Preserve the environment root until resolution 4 execution evidence has been
reviewed. Removal is intentionally manual so the scripts cannot delete a wrong
or user-owned directory.
