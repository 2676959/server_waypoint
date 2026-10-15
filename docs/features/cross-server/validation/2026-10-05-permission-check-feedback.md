# Permission feedback validation — 2026-10-05

The baseline feedback regression failed because the old message omitted independent permission
statuses. The final tests assert the English phrase, both status colors, alternate denial
combinations, independent Paper/LuckPerms and mod fallback decisions, source remote.tp-only
admission, destination preflight and arrival revocation, strict wire decoding, and admitted proxy
forwarding. The live TCP fixture validates detailed preflight rejection through both NOISE_KK
and PLAINTEXT; it does not launch Minecraft or Velocity.

Verified command:

```sh
./gradlew :cross-server:test :common:test :proxy-common:test :velocity:test \
    :paper:26.2-paper:test :mods:26.1.2-fabric:compileJava --continue --console=plain
```

After the review coverage addition, `:common:test` passed again: 630 tests. Final reports contain
115 cross-server, 630 common, 162 proxy-common, 15 Velocity, and 31 Paper tests, with no failures
or skips. Fabric 26.1.2 compiled. No active Stonecutter project was switched.

One intermediate proxy suite run hit `CatalogPublicationTest`'s reconnect polling race in
PLAINTEXT: it checks `backend.status().presence()` and then reads a second status before accessing
`generation()`, which can become null between reads. The unchanged test passed on the subsequent
full proxy suite rerun. The final permission runtime assertions passed in both transport modes.

All six locale JSON files parse and use the `{0}` MessageFormat server-name placeholder.
`git diff --check` passed. Read-only review found no actionable issues.

No live Minecraft/Paper/Velocity player session, deployed permission-feedback upgrade, or visual chat check
was performed. Changes remain uncommitted.

## Protocol version rollback

The application protocol version was restored to 1 at the user's request. Permission feedback and
its added rejection flags remain implemented; explicit `protocolVersion` settings stay at `1`.
The protocol, proxy-common, and Velocity suites passed after rollback. The common suite hit an
unrelated `WaypointFilesManagerConcurrencyTest.deferredNestedCallbackFailurePropagatesToOutermostMutationCaller`
assertion once and passed on a full suite rerun without changes to that test or implementation.

## Logging follow-up

Source/destination denial log regressions and the command action audit regression failed against
the old generic logs, then passed after adding the remote teleport context and independent
permission statuses. The command audit test verifies executor UUID and the captured console
sender after a delayed completion. The full `:cross-server:test :common:test :proxy-common:test
:velocity:test` run passed; `:common:test` now has 632 tests. `git diff --check` passed.
