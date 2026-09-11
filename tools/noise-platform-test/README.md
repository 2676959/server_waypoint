# Final Noise artifact and platform classloader checks

Development-only tools for cross-server plan step 3. Nothing in this directory is a root Gradle
subproject or release source set. The Java probe uses reflection and needs only Java 17; it does not
compile against the Noise library or add original library classes to a platform classpath.

## Final artifact audit

After the root build, from the repository root:

```sh
python3 tools/noise-platform-test/audit.py \
    --java17-home /path/to/jdk17 \
    --output /new/disposable/audit-directory
```

The output directory must not exist. The audit expects the current complete 41-target matrix:
13 Fabric, 12 Forge, 12 NeoForge, 3 Paper, and 1 Velocity artifact (including the two development-only
mod targets). Update its explicit counts when the supported matrix changes. It selects final JARs,
not Shadow intermediates, Forge thin JARs, or the unshaded Velocity diagnostic JAR.

It checks private Noise classes and their Java 17 bytecode, the exact MIT license, shared transport
contracts, absence of original Noise/test classes, preservation of Paper's common/bStats content,
and Velocity metadata and dependency exclusions. It also checks all classes in common/proxy-common
for Java 17 bytecode and platform API references.

Each final artifact is copied into the fresh evidence directory and loaded in a URLClassLoader
whose parent provides only JDK classes. The probe performs an exact-suite KK handshake, compares
handshake hashes, encrypts/decrypts empty, small, and maximum records in both directions, rejects
replay, and verifies repeated nonce-exhaustion rejection. This is a packaging smoke test; the
published vector and extensive rejection matrix remain in `tools/noise-spike/kk`.

Output includes `artifacts.json` with SHA-256, file size, project path, relocated class count, and
probe result, plus `probe.jar` for the optional live observer.

## Native platform classloader observer

Add this option to a **disposable** server/proxy launch:

```text
-javaagent:/audit-directory/probe.jar=fully.qualified.PluginEntrypoint|/new/evidence-file.txt
```

The observer does not transform bytecode or call a plugin method. It waits for the specified
entrypoint to be loaded, then runs the same dependency smoke test using that exact classloader.
Evidence records the runtime classloader and the Noise class's code source. Tests are limited to
local in-memory crypto: the observer opens no socket, registers no command, and performs no transfer.
It exits after a bounded three-minute wait. Server startup and readiness are separate checks.

`run_live.py` executes a JSON manifest of preprepared disposable environments. Each `cwd` must
contain a `.noise-platform-test` marker. Existing `noise-evidence.txt` files are never reused.
It starts cases sequentially, requires both successful observer evidence and the configured server
readiness pattern, then sends the normal shutdown command and requires exit code 0. On failure it
terminates only the process group it started. Every case retains `console.log` and `result.json`;
a failed case makes the runner fail overall. A crypto probe PASS does not override a startup failure.

```sh
python3 tools/noise-platform-test/run_live.py /disposable/manifest.json
python3 tools/noise-platform-test/run_live.py /disposable/manifest.json --case fabric-1.20.1
```

Manifest structure (paths and exact runtime arguments come from the prepared installation):

```json
{
    "cases": [{
        "name": "platform-version",
        "cwd": "/disposable/platform-version",
        "artifact": "/disposable/platform-version/mods/final-artifact.jar",
        "command": ["/jdk/bin/java", "-Xmx1G", "-javaagent:/audit/probe.jar=plugin.Main|/disposable/platform-version/noise-evidence.txt", "-jar", "server.jar", "nogui"],
        "ready_pattern": "Done ",
        "stop_command": "stop",
        "timeout_seconds": 240
    }]
}
```

Use loopback binds, fresh configs/worlds, and only the necessary platform dependencies. Do not copy
user waypoint/configuration data or point these scripts at a live server directory. A fresh Velocity
configuration should bind to loopback and its stop command is `shutdown`. Forge/NeoForge use the
installer-generated `unix_args.txt` with the appropriate JDK rather than a development run classpath.

The committed [validation record](../../docs/cross-server/cross-server-step3-validation.md) identifies exact
runtime builds, outcomes, baseline comparisons, and retained evidence for this implementation.
