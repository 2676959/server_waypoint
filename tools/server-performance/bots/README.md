# Native Azalea driver

First anchor: Azalea 0.15.1+mc1.21.11, crates.io revision
`675e56fd9d9da084b8f923fdab6feda3ebd19568`. Use the committed lockfile and
project-local `nightly-2026-07-15`; no global Rust default change is needed.
DER/PKCS prereleases match that revision's upstream lockfile because later
resolution breaks the pinned RSA prerelease API.

```sh
rtk proxy cargo +nightly-2026-07-15 test --manifest-path tools/server-performance/bots/versions/mc1.21.11/Cargo.toml --locked
rtk proxy cargo +nightly-2026-07-15 build --release --manifest-path tools/server-performance/bots/versions/mc1.21.11/Cargo.toml --locked
```

The controller uses schema-1 JSON lines with run/attempt/sequence identities.
Start declares count (1–50), address and mode. Actions declare unique actionId,
bot name and operation (`walk`, `inventory`, `command`, `disconnect`). `barrier`
and `quiesce` report client-issued accounting; native probe assertions must
independently prove the action's effect. `stop` disconnects and exits. Reconnect
is disabled. Inputs are bounded to 64 queued lines; each line is limited to
64 KiB, and the action ledger limits pending actions to 256. Logs use stderr.

`smoke.py` runs 1/10/50 real players with native UUID/count, movement, inventory,
command and disconnect checks in a new disposable root. Supply the exact release
JAR and optimized binary with `--release`, `--binary`, `--root`. It records CPU/RSS
and client monotonic action latency separately from server tick timing. This is
a connection/action smoke, not a performance gate or a mapping for other versions.

Pinned API reference: [Azalea 0.15.1 documentation](https://docs.rs/azalea/0.15.1+mc1.21.11/azalea/).
