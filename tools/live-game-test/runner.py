"""Native Minecraft test orchestration. Launch scripts are data, never shell code."""
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time


def write_json(path, value):
    path.write_text(json.dumps(value, indent=4) + "\n")


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def fresh_directory(path):
    path = Path(path).resolve()
    path.mkdir(parents=True, exist_ok=False)
    return path


def select_profiles(rows, names):
    unknown = set(names) - {row["name"] for row in rows}
    if unknown:
        raise ValueError("Unknown profiles: " + ", ".join(sorted(unknown)))
    selected = [row for row in rows if not names or row["name"] in names]
    if not selected:
        raise ValueError("No profiles selected")
    return selected


def read_launch_script(path):
    """Accept the export's literal environment and one Java invocation, without eval."""
    command = None
    env = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        lexer = shlex.shlex(line, posix=True, punctuation_chars=";&|<>")
        lexer.whitespace_split = True
        tokens = list(lexer)
        if any(token in (";", "&&", "||", "|", ">", ">>", "<", "&") for token in tokens):
            raise ValueError("Launch exports must contain a literal Java invocation, without shell chaining")
        if tokens[0] == "export":
            if len(tokens) != 2 or "=" not in tokens[1]:
                raise ValueError("Unsupported environment assignment")
            name, value = tokens[1].split("=", 1)
            if name in {"INST_NAME", "INST_ID", "INST_DIR", "INST_MC_DIR", "INST_JAVA", "INST_FABRIC", "INST_FORGE", "INST_NEOFORGE"}:
                continue
            if name not in {"JAVA_HOME", "LD_LIBRARY_PATH", "DYLD_LIBRARY_PATH", "DYLD_FALLBACK_LIBRARY_PATH"}:
                raise ValueError("Unsupported launch environment variable: " + name)
            env[name] = value
        elif tokens[0] == "cd":
            # The actual cwd is always the new test directory.
            if len(tokens) != 2:
                raise ValueError("Unsupported cd command")
        else:
            if tokens[0] == "exec":
                tokens = tokens[1:]
            if command is not None or not tokens or Path(tokens[0]).name not in {"java", "java.exe"}:
                raise ValueError("Expected exactly one Java launch command")
            command = tokens
    if command is None:
        raise ValueError("No Java launch command in export")
    return command, env


def isolated_launch(command, game, heap, allowed_agents=()):
    forbidden = ("--server", "--port", "--quickPlay", "-agentlib:", "-agentpath:")
    if any(arg.startswith(forbidden) for arg in command):
        raise ValueError("Remote/Quick Play launches and external JVM agents are not supported")
    for arg in command:
        if arg.startswith("-javaagent:"):
            path = Path(arg[len("-javaagent:"):].split("=", 1)[0]).resolve()
            approved = next((entry for entry in allowed_agents if Path(entry["path"]).resolve() == path), None)
            if approved is None or not path.is_file() or sha256(path) != approved["sha256"]:
                raise ValueError("Launcher Java agent is absent from the manifest or its hash changed")
    replacements = {"--gameDir": str(game), "--username": "SwLiveTest", "--accessToken": "0",
                    "--uuid": "673e4b54076e3b81a3ccb55892822159", "--userType": "legacy"}
    args = [command[0]]
    seen = set()
    index = 1
    while index < len(command):
        arg = command[index]
        key = arg.split("=", 1)[0]
        if key in replacements:
            if "=" not in arg:
                if index + 1 >= len(command):
                    raise ValueError("Missing value for " + key)
                index += 1
            if key in seen:
                raise ValueError("Duplicate launch argument: " + key)
            args.extend([key, replacements[key]])
            seen.add(key)
        elif key in {"--xuid", "--clientId"}:
            if "=" not in arg:
                if index + 1 >= len(command):
                    raise ValueError("Missing value for " + key)
                index += 1
        elif not arg.startswith(("-Xmx", "-Xms", "-Djava.awt.headless=", "-Dmixin.debug.")):
            args.append(arg)
        index += 1
    if "--gameDir" not in seen:
        raise ValueError("Export must specify --gameDir")
    for key, value in replacements.items():
        if key not in seen:
            args.extend([key, value])
    args[1:1] = [f"-Xmx{heap}M", "-Xms128M", "-Djava.awt.headless=true",
                 "-Dmixin.debug.export=true", "-Dmixin.debug.verbose=true"]
    return args


def write_options(output, source=None):
    options = {}
    if source:
        options.update(line.split(":", 1) for line in Path(source).read_text().splitlines() if ":" in line)
    options.update(renderDistance="2", simulationDistance="5", maxFps="20",
                   pauseOnLostFocus="false", lang="en_us", onboardAccessibility="false")
    Path(output).write_text("\n".join(key + ":" + value for key, value in options.items()) + "\n")


class OwnedProcess:
    """Own a child until it is reaped; never discover or terminate other JVMs."""
    def __init__(self, command, cwd, env=None, cancelled=None):
        self.command, self.cwd, self.env = command, Path(cwd), env
        self.cancelled = cancelled
        self.process = None

    def __enter__(self):
        self.stdout = (self.cwd / "stdout.log").open("w")
        self.stderr = (self.cwd / "stderr.log").open("w")
        try:
            self.process = subprocess.Popen(self.command, cwd=self.cwd, env=self.env,
                                            stdout=self.stdout, stderr=self.stderr, start_new_session=True)
        except BaseException:
            self.stdout.close()
            self.stderr.close()
            raise
        return self

    @property
    def pid(self):
        return self.process.pid

    def poll(self):
        return self.process.poll()

    def check_cancelled(self):
        if self.cancelled is not None and self.cancelled.is_set():
            raise InterruptedError("Verification interrupted")

    def __exit__(self, *args):
        try:
            if self.poll() is None:
                # This unreaped child still owns its PID/process group.
                try:
                    os.killpg(self.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    self.process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(self.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    self.process.wait(timeout=10)
        finally:
            self.stdout.close()
            self.stderr.close()


def wait_result(process, path, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        process.check_cancelled()
        if process.poll() is not None:
            raise RuntimeError(f"Client exited {process.poll()} before result {path.name}")
        if path.exists():
            state = path.read_text()
            if state.startswith("PASS\n"):
                return state
            if state.startswith("FAIL\n"):
                raise RuntimeError(state)
        time.sleep(0.1)
    raise TimeoutError("Timed out waiting for " + path.name)


def attach(java, tools, process, agent, payload, output):
    process.check_cancelled()
    if process.poll() is not None:
        raise RuntimeError(f"Client exited {process.poll()}; see {process.cwd / 'stdout.log'} and stderr.log")
    with output.open("w") as log:
        result = subprocess.run([java, "-Xmx128M", "-cp", str(tools), "Attach",
                                 str(process.pid), str(agent), payload], stdout=log, stderr=log, timeout=30)
    if result.returncode:
        raise RuntimeError("Java attach failed; see " + str(output))


def run_profile(prepared, row, output, suite="editor", heap=1024, startup_timeout=300, step_timeout=180, cancelled=None):
    import shutil
    from persistence import verify_store
    home = fresh_directory(output / row["name"])
    game = fresh_directory(home / "game")
    mods = fresh_directory(game / "mods")
    result = {"profile": row["name"], "target": row["target"], "minecraft": row["minecraft"],
              "prepared": str(prepared.resolve()),
              "suite": suite, "status": "RUNNING", "steps": [], "heap_mb": heap,
              "stage": "staging", "awt_headless": True, "started_at": time.time(), "jars": row["jars"]}
    process = None
    try:
        for artifact in row["jars"]:
            source = Path(artifact["path"])
            if sha256(source) != artifact["sha256"]:
                raise ValueError("Artifact changed since prepare: " + source.name)
            shutil.copy2(source, mods / source.name)
        command, env = read_launch_script(row["launch_script"])
        args = isolated_launch(command, game, heap, row.get("launcher_agents", []))
        write_options(game / "options.txt")
        tools = prepared / "tools.jar"
        agent = prepared / "agent.jar"
        helper = prepared / "helpers" / row["target"] / "scenarios.jar"
        targets = prepared / "helpers" / row["target"] / "runtime.targets"
        if sha256(helper) != row["helper_sha256"] or sha256(targets) != row["targets_sha256"]:
            raise ValueError("Prepared scenario or target inventory changed")
        result["probe_hashes"] = {"tools": sha256(tools), "agent": sha256(agent),
                                  "scenario": sha256(helper), "targets": sha256(targets)}
        java = args[0]
        result["java"] = java
        environment = os.environ.copy()
        environment.update(env)
        result["stage"] = "startup"
        with OwnedProcess(args, game, environment, cancelled) as process:
            result["pid"] = process.pid
            write_json(home / "result.json", result)
            print("START", row["name"], f"pid={process.pid}", flush=True)
            since = None
            deadline = time.monotonic() + startup_timeout
            count = 0
            while time.monotonic() < deadline:
                count += 1
                screen = home / f"screen-{count}.txt"
                attach(java, tools, process, agent, f"screen|{screen}", home / f"screen-{count}.attach.log")
                if screen.exists():
                    state = screen.read_text()
                    if "title=true" in state and "overlay=null" in state:
                        since = since or time.monotonic()
                        if time.monotonic() - since >= 15:
                            break
                    else:
                        since = None
                time.sleep(5)
            else:
                raise TimeoutError("Title readiness timeout")
            identity = home / "identity.result"
            result["stage"] = "identify"
            attach(java, tools, process, agent, f"identify|{identity}|{helper}", home / "identity.attach.log")
            actual = wait_result(process, identity, step_timeout).split("minecraft=", 1)[1].strip()
            result["actual_minecraft"] = actual
            if actual != row["minecraft"]:
                raise ValueError("Launched Minecraft " + actual + "; expected " + row["minecraft"])
            audit = home / "mixin-audit.result"
            result["stage"] = "audit"
            attach(java, tools, process, agent, f"audit|{audit}|{targets}", home / "mixin-audit.attach.log")
            result["mixin_audit"] = wait_result(process, audit, step_timeout)
            if suite == "editor":
                steps = ["create", "seed", "map", "map-save", "select-nether", "map-nether-save"]
                if row["voxelmap"]:
                    steps += ["voxel-save", "voxel-fallback"]
                steps += ["native-xaero"]
                if row["voxelmap"]:
                    steps += ["voxel-clear"]
                steps += ["map-cancel-add", "map-add-save", "map-stale", "close", "load", "persisted", "close"]
                for index, name in enumerate(steps, 1):
                    key = f"{index:02}-{name}"
                    proof = home / (key + ".result")
                    result["stage"] = key
                    if proof.exists():
                        raise FileExistsError(proof)
                    attach(java, tools, process, agent, f"scenario|{proof}|{name}|{helper}", home / (key + ".attach.log"))
                    state = wait_result(process, proof, max(step_timeout, 240) if name in ("create", "load") else step_timeout)
                    result["steps"].append({"name": name, "result": proof.name, "state": state})
                    write_json(home / "result.json", result)
                    print(row["name"], name, "PASS", flush=True)
                store = game / "saves/live-editor-verification/server_waypoint/waypoints"
                verify_store(store, row["voxelmap"])
                shutil.copytree(store, home / "persisted-waypoints")
                result["persisted_json"] = "PASS"
            result["stage"] = "quit"
            quit_result = home / "quit.result"
            attach(java, tools, process, agent, f"quit|{quit_result}|{helper}", home / "quit.attach.log")
            process.process.wait(timeout=30)
            if process.poll() != 0:
                raise RuntimeError(f"Client shutdown exited {process.poll()}")
            result["status"] = "PASS"
            result["stage"] = "complete"
    except Exception as failure:
        result["status"] = "FAIL"
        result["failure"] = str(failure)
        print("FAIL", row["name"], type(failure).__name__, flush=True)
    finally:
        result["exit_code"] = process.poll() if process else None
        result["finished_at"] = time.time()
        write_json(home / "result.json", result)
    print(result["status"], row["name"], flush=True)
    return result
