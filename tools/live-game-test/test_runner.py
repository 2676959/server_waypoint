import importlib.util
from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile


class RunnerTests(unittest.TestCase):
    def setUp(self):
        path = Path(__file__).with_name("runner.py")
        self.assertTrue(path.exists(), "Reusable live-game runner has not been implemented")
        spec = importlib.util.spec_from_file_location("live_runner", path)
        self.tool = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = self.tool
        spec.loader.exec_module(self.tool)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_launch_script_is_parsed_without_executing_shell(self):
        marker = self.root / "executed"
        script = self.root / "client.sh"
        script.write_text(f"#!/bin/sh\nexport JAVA_HOME='/jdk with spaces'\n/jdk/bin/java '-Dtest=$(touch {marker})' --gameDir '/old game'\n")
        command, env = self.tool.read_launch_script(script)
        self.assertEqual(command[1], f"-Dtest=$(touch {marker})")
        self.assertEqual(env["JAVA_HOME"], "/jdk with spaces")
        self.assertFalse(marker.exists())

    def test_shell_chaining_is_rejected(self):
        script = self.root / "client.sh"
        script.write_text("/jdk/bin/java --gameDir /old ; touch /tmp/unwanted\n")
        with self.assertRaises(ValueError):
            self.tool.read_launch_script(script)

    def test_hmcl_metadata_exports_are_accepted(self):
        script = self.root / "client.sh"
        script.write_text("export INST_NAME='Test client'\nexport INST_MC_DIR='/old game'\n/jdk/bin/java --gameDir /old\n")
        _, env = self.tool.read_launch_script(script)
        self.assertNotIn("INST_MC_DIR", env)

    def test_launch_uses_isolated_world_and_discards_credentials(self):
        args = ["/jdk/bin/java", "-Xmx8G", "-Xms8G", "--gameDir", "/old",
                "--username", "RealUser", "--accessToken=secret", "--uuid", "real-uuid",
                "--userType", "msa", "--xuid", "account-id", "--clientId=client-id"]
        result = self.tool.isolated_launch(args, self.root / "game", 1024)
        self.assertEqual(result[result.index("--gameDir") + 1], str(self.root / "game"))
        self.assertIn("-Xmx1024M", result)
        self.assertIn("-Xms128M", result)
        self.assertIn("-Djava.awt.headless=true", result)
        self.assertEqual(result[result.index("--username") + 1], "SwLiveTest")
        self.assertEqual(result[result.index("--accessToken") + 1], "0")
        self.assertNotIn("secret", " ".join(result))
        self.assertNotIn("RealUser", " ".join(result))
        self.assertNotIn("account-id", result)
        self.assertNotIn("--xuid", result)
        self.assertNotIn("--clientId=client-id", result)

    def test_remote_connection_arguments_are_rejected(self):
        for arg in ["--server", "--quickPlayMultiplayer=example.com", "--quickPlaySingleplayer"]:
            with self.subTest(arg=arg), self.assertRaises(ValueError):
                self.tool.isolated_launch(["/jdk/bin/java", "--gameDir", "/old", arg, "anything"], self.root, 1024)

    def test_user_java_agents_are_rejected(self):
        with self.assertRaises(ValueError):
            self.tool.isolated_launch(["/jdk/bin/java", "-javaagent:/old/probe.jar", "--gameDir", "/old"], self.root, 1024)

    def test_manifest_authorized_launcher_agent_requires_matching_hash(self):
        agent = self.root / "launcher.jar"
        agent.write_bytes(b"launcher helper")
        trusted = [{"path": str(agent), "sha256": self.tool.sha256(agent)}]
        args = ["/jdk/bin/java", "-javaagent:" + str(agent), "--gameDir", "/old"]
        self.assertIn(args[1], self.tool.isolated_launch(args, self.root, 1024, trusted))
        agent.write_bytes(b"changed helper")
        with self.assertRaises(ValueError):
            self.tool.isolated_launch(args, self.root, 1024, trusted)

    def test_duplicate_options_are_written_once(self):
        source = self.root / "options.txt"
        source.write_text("maxFps:120\nmaxFps:60\nrenderDistance:32\n")
        output = self.root / "game/options.txt"
        output.parent.mkdir()
        self.tool.write_options(output, source)
        text = output.read_text()
        self.assertEqual(text.count("maxFps:"), 1)
        self.assertIn("maxFps:20\n", text)
        self.assertIn("pauseOnLostFocus:false\n", text)

    def test_timeout_reaps_only_the_owned_process(self):
        unrelated = subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
        self.addCleanup(lambda: (unrelated.terminate(), unrelated.wait()))
        with self.tool.OwnedProcess([sys.executable, "-c", "import time; time.sleep(60)"], self.root) as process:
            with self.assertRaises(TimeoutError):
                self.tool.wait_result(process, self.root / "missing.result", 0.1)
            owned_pid = process.pid
        self.assertIsNotNone(process.poll())
        self.assertIsNone(unrelated.poll())
        with self.assertRaises(ProcessLookupError):
            os.kill(owned_pid, 0)

    def test_client_exit_before_result_is_a_failure(self):
        with self.tool.OwnedProcess([sys.executable, "-c", "raise SystemExit(7)"], self.root) as process:
            with self.assertRaisesRegex(RuntimeError, "7"):
                self.tool.wait_result(process, self.root / "absent.result", 2)

    def test_cancellation_reaps_owned_client(self):
        cancelled = threading.Event()
        with self.tool.OwnedProcess([sys.executable, "-c", "import time; time.sleep(60)"], self.root,
                                    cancelled=cancelled) as process:
            cancelled.set()
            with self.assertRaises(InterruptedError):
                self.tool.wait_result(process, self.root / "absent.result", 30)
        self.assertIsNotNone(process.poll())

    def test_explicit_probe_failure_is_not_a_pass(self):
        result = self.root / "step.result"
        result.write_text("FAIL\nassertion failed\n")
        with self.tool.OwnedProcess([sys.executable, "-c", "import time; time.sleep(60)"], self.root) as process:
            with self.assertRaisesRegex(RuntimeError, "assertion failed"):
                self.tool.wait_result(process, result, 1)

    def test_preexisting_output_is_not_reused(self):
        self.root.joinpath("earlier-pass.result").write_text("PASS\n")
        with self.assertRaises(FileExistsError):
            self.tool.fresh_directory(self.root)

    def test_unknown_profile_selection_fails(self):
        with self.assertRaises(ValueError):
            self.tool.select_profiles([{"name": "fabric"}], ["typo"])

    def test_changed_production_jar_fails_before_client_launch(self):
        artifact = self.root / "production.jar"
        artifact.write_bytes(b"original")
        expected = self.tool.sha256(artifact)
        artifact.write_bytes(b"changed")
        row = {"name": "test", "target": "1.21-fabric", "minecraft": "1.21",
               "jars": [{"path": str(artifact), "name": artifact.name, "sha256": expected}]}
        with redirect_stdout(io.StringIO()):
            result = self.tool.run_profile(self.root / "prepared", row, self.root)
        self.assertEqual(result["status"], "FAIL")
        self.assertIn("Artifact changed since prepare", result["failure"])
        self.assertNotIn("pid", result)
        recorded = json.loads((self.root / "test/result.json").read_text())
        self.assertEqual(recorded["status"], "FAIL")

    def test_invalid_manifest_profile_names_cannot_escape_output(self):
        import prepare
        for name in ("../outside", "/outside", "a/b"):
            manifest = self.root / "manifest.json"
            manifest.write_text(json.dumps({"profiles": [
                {"name": name, "target": "1.21-fabric", "minecraft": "1.21", "launch_script": "unused"}]}))
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Invalid profile name"):
                prepare.load_manifest(manifest, self.root)

    def test_empty_and_duplicate_manifest_profiles_are_rejected(self):
        import prepare
        for rows in ([], [{"name": "same"}, {"name": "same"}]):
            manifest = self.root / "manifest.json"
            manifest.write_text(json.dumps({"profiles": rows}))
            with self.subTest(rows=rows), self.assertRaisesRegex(ValueError, "uniquely named"):
                prepare.load_manifest(manifest, self.root)

    def test_fabric_api_override_verifies_identity_and_hash(self):
        import prepare
        path = self.root / "api.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({"id": "fabric-api"}))
        row = {"target": "1.21-fabric", "fabric_api": {"path": str(path), "sha256": self.tool.sha256(path)}}
        self.assertEqual(prepare.fabric_api_override(row)["name"], "api.jar")
        path.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "changed"):
            prepare.fabric_api_override(row)

    def test_fabric_api_override_rejects_another_mod(self):
        import prepare
        path = self.root / "other.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({"id": "another-mod"}))
        row = {"target": "1.21-fabric", "fabric_api": {"path": str(path), "sha256": self.tool.sha256(path)}}
        with self.assertRaisesRegex(ValueError, "not a Fabric API"):
            prepare.fabric_api_override(row)

    def test_fabric_api_override_rejects_non_fabric_target(self):
        import prepare
        with self.assertRaisesRegex(ValueError, "Fabric target"):
            prepare.fabric_api_override({"target": "1.21-neoforge", "fabric_api": {}})


if __name__ == "__main__":
    unittest.main()
