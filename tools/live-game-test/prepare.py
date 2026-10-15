"""Compile fresh probes with Gradle-resolved dependencies; no session cache paths."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from runner import fresh_directory, sha256, write_json
from scenarios import source_for, api_for


TOOL = Path(__file__).resolve().parent


def properties(path):
    return dict(line.strip().split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.lstrip().startswith("#"))


def command(args, log, timeout=300, cwd=None):
    with log.open("w") as stream:
        result = subprocess.run(args, cwd=cwd, stdout=stream, stderr=stream, timeout=timeout)
    if result.returncode:
        raise RuntimeError("Preparation command failed; see " + str(log))


def paths(path):
    values = [Path(value) for value in path.read_text().splitlines() if value]
    if not values or any(not value.exists() for value in values):
        raise ValueError("Missing build input in " + str(path))
    return values


def load_manifest(path, repo):
    data = json.loads(path.read_text())
    rows = data["profiles"]
    if not rows or len({row["name"] for row in rows}) != len(rows):
        raise ValueError("Manifest must contain uniquely named profiles")
    import re
    for row in rows:
        if not re.fullmatch(r"[A-Za-z0-9.-]+", row["name"]):
            raise ValueError("Invalid profile name")
        if not re.fullmatch(r"[0-9.]+-(fabric|forge|neoforge)", row["target"]):
            raise ValueError("Invalid target")
        if not (repo / "mods/versions" / row["target"] / "gradle.properties").is_file():
            raise ValueError("Unknown Stonecutter target: " + row["target"])
        if not re.fullmatch(r"[0-9.]+", row["minecraft"]):
            raise ValueError("Invalid Minecraft version")
        if row.get("suite", "editor") not in {"editor", "core"}:
            raise ValueError("Unknown profile suite")
        script = Path(row["launch_script"])
        if not script.is_absolute():
            script = path.parent / script
        row["launch_script"] = str(script.resolve(strict=True))
        from runner import isolated_launch, read_launch_script
        launch, _ = read_launch_script(script)
        isolated_launch(launch, Path("/isolated/test"), 1024, row.get("launcher_agents", []))
        fabric_api_override(row)
    return rows


def fabric_api_override(row):
    override = row.get("fabric_api")
    if override is None:
        return None
    if not row["target"].endswith("-fabric"):
        raise ValueError("Fabric API override requires a Fabric target")
    path = Path(override["path"]).resolve(strict=True)
    if sha256(path) != override["sha256"]:
        raise ValueError("Fabric API override changed since manifest creation")
    with zipfile.ZipFile(path) as jar:
        if json.loads(jar.read("fabric.mod.json"))["id"] != "fabric-api":
            raise ValueError("Override is not a Fabric API JAR")
    return {"path": str(path), "name": path.name, "sha256": override["sha256"]}


def remap_classpath(classpath):
    """Keep MC, mod output and Xaero hierarchy only; the full CP caused OOM in the spike."""
    selected = []
    for path in classpath:
        if path.is_dir():
            selected.append(path)
        elif path.suffix == ".jar":
            if any(name in path.name for name in ("xaeros-minimap", "xaeros-world-map", "xaerolib")):
                selected.append(path)
            else:
                with zipfile.ZipFile(path) as jar:
                    if "net/minecraft/client/Minecraft.class" in jar.namelist():
                        selected.append(path)
    return selected


def prepare(repo, manifest, output, jdk, selected):
    from runner import select_profiles
    rows = select_profiles(load_manifest(manifest, repo), selected)
    output = fresh_directory(output)
    if not (jdk / "bin/javac").is_file():
        raise ValueError("--jdk must name a JDK, not a Java executable")
    targets = sorted({row["target"] for row in rows})
    for target in targets:
        suites = {row.get("suite", "editor") for row in rows if row["target"] == target}
        if "core" in suites and len(suites) > 1:
            raise ValueError("Prepare core and editor profiles for the same target separately: " + target)
    command([str(repo / "gradlew"), "-I", str(TOOL / "inputs.init.gradle.kts"),
             "-PliveGameOutput=" + str(output), "--console=plain", "--no-configuration-cache",
             *[":mods:" + target + ":liveGameTestInputs" for target in targets]],
            output / "gradle-prepare.log", timeout=1800, cwd=repo)
    java, javac, jar = (str(jdk / "bin" / name) for name in ("java", "javac", "jar"))
    tool_cp = paths(output / "inputs" / targets[0] / "tools.paths")
    classes = fresh_directory(output / "classes")
    command([javac, "--release", "17", "-cp", os.pathsep.join(map(str, tool_cp)), "-d", str(classes),
             *map(str, sorted((TOOL / "java").glob("*.java")))], output / "compile-tools.log")
    command([jar, "cf", str(output / "tools.jar"), "-C", str(classes), "."], output / "jar-tools.log")
    tests = fresh_directory(output / "test-classes")
    test_cp = os.pathsep.join([str(output / "tools.jar"), *map(str, tool_cp)])
    command([javac, "--release", "17", "-cp", test_cp, "-d", str(tests),
             str(TOOL / "java/tests/TargetExtractorTest.java")], output / "compile-extractor-tests.log")
    command([java, "-cp", os.pathsep.join([str(tests), test_cp]), "TargetExtractorTest",
             str(output / "extractor-fixture.jar")], output / "extractor-tests.log")
    agent_manifest = output / "agent.mf"
    agent_manifest.write_text("Manifest-Version: 1.0\nAgent-Class: Agent\n\n")
    command([jar, "cfm", str(output / "agent.jar"), str(agent_manifest), "-C", str(classes),
             "Agent.class", "-C", str(classes), "MixinAudit.class"], output / "jar-agent.log")
    builds = {}
    for target in targets:
        home = fresh_directory(output / "helpers" / target)
        inputs = output / "inputs" / target
        cp = paths(inputs / "compile.paths")
        runtime_mods = paths(inputs / "mods.paths")
        props = properties(repo / "mods/versions" / target / "gradle.properties")
        minecraft, loader = target.rsplit("-", 1)
        voxelmap = "voxelmap_" + loader in props
        production = paths(inputs / "production.paths")[0]
        source = home / "LiveChecks.java"
        core = all(row.get("suite") == "core" for row in rows if row["target"] == target)
        if core:
            runtime_mods = [path for path in runtime_mods if not any(
                mod in path.name for mod in ("xaeros-minimap", "xaeros-world-map", "xaerolib", "voxelmap"))]
        source.write_text(source_for(target, voxelmap, core))
        api = home / "GameApi.java"
        api.write_text(api_for(target, voxelmap))
        helper_classes = fresh_directory(home / "classes")
        command([javac, "-proc:none", "--release", "17", "-cp", os.pathsep.join(map(str, cp)),
                 "-d", str(helper_classes), str(source), str(api)], home / "compile.log")
        named = home / "scenarios-named.jar"
        command([jar, "cf", str(named), "-C", str(helper_classes), "."], home / "jar.log")
        helper = home / "scenarios.jar"
        mapping_input = inputs / "mappings.paths"
        if mapping_input.exists():
            mappings = paths(mapping_input)[0]
            mapping_copy = home / ("mappings" + mappings.suffix)
            shutil.copy2(mappings, mapping_copy)
            remap_cp = home / "remap.cp"
            remap_cp.write_text(os.pathsep.join(map(str, remap_classpath(cp))))
            command([java, "-Xmx1024M", "-cp", os.pathsep.join([str(output / "tools.jar"), *map(str, tool_cp)]),
                     "RemapLive", str(mapping_copy), "intermediary" if loader == "fabric" else "srg",
                     str(named), str(helper), str(remap_cp)], home / "remap.log", timeout=180)
        else:
            shutil.copy2(named, helper)
        command([java, "-Xmx128M", "-cp", os.pathsep.join([str(output / "tools.jar"), *map(str, tool_cp)]),
                 "ListMixinTargets", str(production),
                 *(["--optional-map-mods"] if core else [])], home / "runtime.targets")
        builds[target] = {"voxelmap": voxelmap, "helper_sha256": sha256(helper),
                          "targets_sha256": sha256(home / "runtime.targets"), "jars": [
            {"path": str(path.resolve()), "name": path.name, "sha256": sha256(path)}
            for path in [production, *runtime_mods]]}
        print("PREPARED", target, flush=True)
    for row in rows:
        row.update(builds[row["target"]])
        override = fabric_api_override(row)
        if override is not None:
            row["jars"] = [jar for jar in row["jars"] if not jar["name"].startswith("fabric-api-")] + [override]
    write_json(output / "prepared.json", {"repo": str(repo), "profiles": rows,
                                          "tools_sha256": sha256(output / "tools.jar"),
                                          "agent_sha256": sha256(output / "agent.jar")})
    print("Prepared profiles:", len(rows), "in", output, flush=True)
