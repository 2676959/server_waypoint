# Proxy Lifecycle Release Test

This disposable Minecraft 1.21.11 topology validates the production 3.1.0
Fabric client at release candidate `99cc415` through Velocity and two Folia
backends. The separate lifecycle-control mod is built only by the explicit
`remapProxyLifecycleTestJar` verification task and is excluded from production
and release JARs.

The preparation script requires explicit Folia, Velocity, HeadlessMC, Java 25,
Minecraft-store, mod-template, baseline-freeze, and new output paths. It refuses
to reuse a directory or write inside the repository. The client launcher CWD,
Minecraft store, game directory, server cache tree, backend worlds, and all log
trees are isolated beneath the disposable root.

Start backend A, backend B, and Velocity in separate terminals, then run the
proxy scenario:

```shell
tools/proxy-lifecycle-test/run.sh <root> backend-a
tools/proxy-lifecycle-test/run.sh <root> backend-b
tools/proxy-lifecycle-test/run.sh <root> proxy
tools/proxy-lifecycle-test/run.sh <root> proxy-transfer
```

The JSON command test joins backend A, downloads `server-a-only`, creates a
development-controlled in-memory `pending-a-marker`, switches to B with
`/server backend-b`, then switches back to A. Structured `SW_LIFECYCLE` lines
assert leave cleanup, handshake-waiting state, immediate dimension safety,
distinct manager generations/cache directories, exact list revisions, cache
write ownership, and A/B isolation.

With backend A still running, repeat the focused direct-Folia baseline and
disconnect/reconnect scenario:

```shell
tools/proxy-lifecycle-test/run.sh <root> direct-reconnect
```

Then stop backend A cleanly with `stop`, launch it again from the same prepared
directory, and run the restart scenario:

```shell
tools/proxy-lifecycle-test/run.sh <root> backend-a
tools/proxy-lifecycle-test/run.sh <root> direct-restart
tools/proxy-lifecycle-test/run.sh <root> record
tools/proxy-lifecycle-test/run.sh <root> audit
```

Do not remove the root after a failure. Fix one issue, rerun the failed scenario
and its baseline, and preserve all client, proxy, and backend logs for review.
