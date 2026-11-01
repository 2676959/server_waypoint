# Task 8 retained evidence

See the [dated validation report](../../2026-10-31-platform-validation.md) for outcomes,
commands, environment, failed attempts and proof boundaries.

- `full-tests.log`, `test-counts.json`: fresh common/current-client/older-client/Paper suites.
- `representative-compile.log`: six representative compilation targets and the Paper test JAR.
- `core/`: integrated collection/copy/profile/layout/persistence probe, saved stores and frames.
- `gui/`: native Spanish manager/profile controls, frames and strict offline-service diagnostics.
- `multiplayer/`: Local on plugin-free/protocol-2 servers, reconnect and saved manual profiles.
- `editor/`: unmodified full Xaero/VoxelMap editor suite, with independent disk assertions.
- `maps/`: live Local/Private exclusion from native map exports alongside Shared entries.
- `paper-server.log`, `alice-out.log`, `bob-out.log`, `paper-*-waypoints/`: dedicated command proof.
- `failed-attempts/`: unsuccessful probes retained separately from final results.
- `server-artifacts.json`: hashes of the current and incompatible server fixtures.

`*-probe.patch` files are diffs against `tools/live-game-test` at source commit `ea91edcb`.
To reproduce a custom probe, copy that tool into a new disposable directory and apply its patch
there with `patch -p1`; use a fresh manifest, preparation and result directory. The multiplayer
controller is the exact executed disposable-server driver, with local evidence paths retained.
Launch exports/manifests are not bundled because installed-client exports can contain personal
authentication arguments. The runner substitutes an offline fixture account before launching;
artifacts here contain no credentials. Use the tool's documented manifest/batch workflow to create
new exports from an installed client. Native probes use an external observer/controller agent,
not a production mod transformer.

Raw game/build logs intentionally retain warnings, failed attempts and offline service errors.
Some final native runner statuses are FAIL even though every feature scenario passed; inspect
`result.json`, `diagnostics.json` and the report together. Do not count native handlers as physical
input or a protocol bot as a graphical vanilla client.
