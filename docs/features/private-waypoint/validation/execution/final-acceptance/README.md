# Final acceptance evidence

See the [final acceptance record](../../2026-10-31-final-acceptance.md) for outcomes and limits.
All sources here are external test observers/controllers or disposable test plugins. They are not
production code and are not part of any release artifact. Local fixture/export paths intentionally
identify the exact executed environment. Do not run these drivers against a personal server.

Executed entry points, from the repository workspace, with outputs under
`/Volumes/ssd/sw-private-final-20261031`:

```sh
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/acceptance.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/platform_scripts.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/folia_script.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/proxy_acceptance.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/extra_acceptance.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/last_acceptance.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/rpc_server.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/denial_acceptance.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/denial_resume.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/world_frame.py
rtk proxy python3 /Volumes/ssd/sw-private-final-20261031/unmodified_acceptance.py
```

The proxy native client was kept alive for the extra observer stages. Final helper JARs were
versioned after compilation, as Java URL connections cache a JAR at a reused path. Native scenario
result names are also unique per final stage. Failed intermediate harness starts remain in logs;
only the final records identified below are counted as successes.

- `acceptance.log`: completed three-client run; WPAlice/WPBob/WPVanilla retain numbered action
  files, chat text/click-command JSON and cache snapshots. WPVanilla is the actual graphical game
  without mod loading, not an MCC simulation.
- `platform-scripts-2.log`: Fabric PASS; its earlier Folia global-console position attempt failed.
  `folia-script-3.log` is Folia's final PASS, corroborated by entity-scheduled position observations.
- `proxy-acceptance-2.log`: successful real transfers and late handshake, followed by cleanup.
- `extra-acceptance-3.log`: successful tombstone and restore-conflict checks.
- `last-acceptance.log`: successful Remote/collapse and final clear locale/narration input checks.
- `WPProxy/extra5-07.result`: copy permission denial passed before a Python fixture-variable error.
  `denial-resume.log` and `WPProxy/extra6-*`: final quota-denial/navigation-retarget assertions.
- `unmodified-acceptance.log`, `unmodified-client.log`: final plugin-free Local/marker reconnect PASS.
- `hover/`: final native tooltip hover assertions all pass; the strict runner preserves its offline
  account/Realms failure status in diagnostics.
- `WPProxy/screenshots/clear-*`: 24 manager-view and six profile frames without resource-reload fade.
  `visible-collection-markers.png` and `visible-local-without-server-mod.png`: native world drawing.
- `folia/`, `fabric/`, `paper/`, `proxy-check-*`: raw backend/proxy logs and saved quota/owner data.
- `artifact-hashes.json`, `summary.json`: exact fixture binary identity and outcome boundaries.

Preparation used the same production paths and installed-client manifest as the first Task 8 run,
with `javac --release 17` against the Gradle-resolved `compile.paths`, JDK 25 for 26.x, and the
repository's existing `Agent`/`Attach` tooling. Native observers call actual game APIs and inspect
actual server-delivered packets/state; no production transformers or fake success responses are used.
Private server quotas, permissions and actor identities remain authoritative in those checks.

Exports and personal account caches are excluded. All account IDs in these game/server logs belong
to disposable offline fixture accounts. No native-speaker or OS-level input certification is implied.
