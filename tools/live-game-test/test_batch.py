"""Contracts for unattended selection, diagnostics and failure reporting."""
from contextlib import redirect_stdout
import importlib
import io
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


class BatchTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def module(self, name):
        try:
            return importlib.import_module(name)
        except ModuleNotFoundError:
            self.fail("Missing unattended-run module: " + name)

    def test_client_selection_resolves_runtime_range_and_case(self):
        clients = self.module("clients")
        versions = self.root / "versions"
        (versions / "1.21.1-Fabric").mkdir(parents=True)
        target = self.root / "repo/mods/versions/1.21-fabric"
        target.mkdir(parents=True)
        (target / "gradle.properties").write_text("mcVersionRange=1.21-1.21.1\n")
        row = clients.select_client(self.root / "repo", versions, "1.21.1-fabric")
        self.assertEqual(row["name"], "1.21.1-Fabric")
        self.assertEqual(row["target"], "1.21-fabric")
        self.assertEqual(row["minecraft"], "1.21.1")

    def test_client_selection_rejects_ambiguous_or_non_test_folders(self):
        clients = self.module("clients")
        versions = self.root / "versions"
        versions.mkdir()
        for name in ("1.21.1-Fabric", "Personal Modpack"):
            (versions / name).mkdir()
        for target in ("1.21-fabric", "1.21.2-fabric"):
            path = self.root / "mods/versions" / target
            path.mkdir(parents=True)
            (path / "gradle.properties").write_text("mcVersionRange=1.21-1.21.1\n")
        with self.assertRaisesRegex(ValueError, "unique"):
            clients.select_client(self.root, versions, "1.21.1-fabric")
        for name in ("../1.21-Fabric", "Personal Modpack"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "version.*loader"):
                clients.select_client(self.root, versions, name)

    def test_jdk_discovery_selects_lowest_compatible_and_rejects_old_java(self):
        clients = self.module("clients")
        homes = []
        for major in (17, 21, 26):
            home = self.root / str(major)
            (home / "bin").mkdir(parents=True)
            (home / "bin/java").touch()
            (home / "bin/javac").touch()
            (home / "release").write_text('JAVA_VERSION="' + str(major) + '.0.1"\n')
            homes.append(home)
        self.assertEqual(clients.choose_jdk(homes, 21), homes[1])
        self.assertEqual(clients.choose_jdk(homes, 25), homes[2])
        with self.assertRaisesRegex(ValueError, "JDK 25"):
            clients.choose_jdk(homes[:2], 25)

    def test_client_default_respects_environment_and_uses_home_when_unset(self):
        clients = self.module("clients")
        with patch.dict(os.environ, {"LIVE_GAME_TEST_CLIENTS": str(self.root / "test versions")}):
            self.assertEqual(clients.default_clients(), self.root / "test versions")
        with patch.dict(os.environ, {}, clear=True), patch("clients.Path.home", return_value=self.root):
            self.assertEqual(clients.default_clients(), self.root / ".minecraft/versions")

    def test_launcher_path_respects_cli_over_environment_and_reports_missing_configuration(self):
        clients = self.module("clients")
        configured = self.root / "configured.jar"
        explicit = self.root / "explicit.jar"
        configured.touch()
        explicit.touch()
        with patch.dict(os.environ, {"LIVE_GAME_TEST_HMCL": str(configured)}):
            self.assertTrue(clients.find_hmcl().samefile(configured))
            self.assertTrue(clients.find_hmcl(explicit).samefile(explicit))
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "--hmcl"):
                clients.find_hmcl()

    def test_diagnostics_deduplicate_logs_keep_causes_and_fail_on_runtime_errors(self):
        diagnostics = self.module("diagnostics")
        game = self.root / "game"
        (game / "logs").mkdir(parents=True)
        log = ("[12:00:00] [Render thread/WARN]: Missing optional setting\n"
               "[12:00:01] [Render thread/ERROR]: Handler crashed\n"
               "java.lang.IllegalStateException: bad state\n"
               "\tat example.Handler.run(Handler.java:12)\n"
               "Caused by: java.lang.NullPointerException: absent\n")
        (game / "stdout.log").write_text(log)
        (game / "logs/latest.log").write_text(log)
        result = {"status": "PASS"}
        diagnostics.record_diagnostics(self.root, result)
        self.assertEqual(result["status"], "FAIL")
        self.assertEqual(result["diagnostics"], {"errors": 1, "warnings": 1, "ignored": 0})
        saved = json.loads((self.root / "diagnostics.json").read_text())
        self.assertIn("NullPointerException", saved[1]["message"])
        self.assertEqual(len(saved[1]["sources"]), 2)

    def test_stderr_fatal_errors_and_crash_reports_are_recorded(self):
        diagnostics = self.module("diagnostics")
        game = self.root / "game"
        (game / "crash-reports").mkdir(parents=True)
        (game / "stderr.log").write_text('Exception in thread "main" java.lang.LinkageError: missing\n')
        (game / "crash-reports/crash.txt").write_text("Minecraft crashed\n")
        result = {"status": "FAIL", "failure": "Client exited 1"}
        diagnostics.record_diagnostics(self.root, result)
        self.assertGreaterEqual(result["diagnostics"]["errors"], 1)
        self.assertIn("game/crash-reports/crash.txt", result["crash_reports"])

    def test_only_known_offline_services_are_excluded_from_failure_counts(self):
        diagnostics = self.module("diagnostics")
        game = self.root / "game"
        game.mkdir()
        (game / "stdout.log").write_text(
            "[12:00:00] [Render thread/ERROR]: Failed to fetch Realms feature flags\n"
            "net.minecraft.class_4355: Invalid session id\n"
            "[12:00:01] [Render thread/ERROR]: io exception while checking versions: Online mod data expired! Date: yesterday\n")
        result = {"status": "PASS"}
        diagnostics.record_diagnostics(self.root, result)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["diagnostics"], {"errors": 0, "warnings": 0, "ignored": 2})
        entries = json.loads((self.root / "diagnostics.json").read_text())
        self.assertTrue(all(entry.get("ignore_reason") for entry in entries))
        self.assertIn("Invalid session id", entries[0]["message"])

    def test_missing_clients_all_reported_without_verbose_tracebacks(self):
        batch = self.module("batch")
        args = SimpleNamespace(versions=["1.21-fabric", "1.20.1-forge"],
                               clients=self.root / "versions", repo=self.root / "repo",
                               logs=self.root / "results", jdk=None, hmcl=None, exports=[],
                               suite="editor", jobs=1, heap=1024, startup_timeout=30,
                               step_timeout=30, fabric_api=[], allow_launcher_agent=[])
        args.clients.mkdir()
        args.repo.mkdir()
        capture = io.StringIO()
        with redirect_stdout(capture):
            status = batch.run_batch(args)
        self.assertEqual(status, 1)
        runs = list(args.logs.iterdir())
        self.assertEqual(len(runs), 1)
        summary = json.loads((runs[0] / "summary.json").read_text())
        self.assertEqual([row["status"] for row in summary], ["FAIL", "FAIL"])
        self.assertTrue(all(row["stage"] == "selection" for row in summary))
        self.assertNotIn("Traceback", capture.getvalue())
        self.assertLessEqual(len(capture.getvalue().splitlines()), 5)

    def batch_args(self):
        args = SimpleNamespace(versions=["1.21-fabric", "1.20.1-forge"],
                               clients=self.root / "versions", repo=self.root / "repo",
                               logs=self.root / "results", jdk=self.root / "jdk", hmcl=None,
                               exports=[self.root / "exports"], suite="editor", jobs=2,
                               heap=1024, startup_timeout=30, step_timeout=30)
        args.clients.mkdir()
        args.exports[0].mkdir()
        (args.jdk / "bin").mkdir(parents=True)
        (args.jdk / "release").write_text('JAVA_VERSION="25.0.1"\n')
        (args.jdk / "bin/java").touch()
        (args.jdk / "bin/javac").touch()
        for name in args.versions:
            (args.clients / name).mkdir()
            target = args.repo / "mods/versions" / name
            target.mkdir(parents=True)
            (target / "gradle.properties").write_text("mcVersionRange=" + name.rsplit("-", 1)[0] + "\n")
            (args.exports[0] / (name + ".sh")).write_text("/fake/bin/java --gameDir /old\n")
        return args

    def test_prepare_failure_does_not_prevent_later_runtime_and_each_run_has_new_logs(self):
        batch = self.module("batch")
        args = self.batch_args()

        def prepare_boundary(repo, manifest, output, jdk, selected):
            profiles = json.loads(manifest.read_text())["profiles"]
            output.mkdir()
            (output / "gradle-prepare.log").write_text("Full build log\n")
            if profiles[0]["target"] == "1.21-fabric":
                raise RuntimeError("broken build")
            (output / "prepared.json").write_text(json.dumps({"profiles": profiles}))

        def native_boundary(prepared, row, output, *args, **kwargs):
            home = output / row["name"]
            (home / "runtime.log").write_text("Full runtime log\n")
            result = {"profile": row["name"], "suite": "editor", "status": "PASS", "stage": "complete"}
            (home / "result.json").write_text(json.dumps(result))
            return result

        with patch("batch.prepare", prepare_boundary), patch("batch.run_profile", native_boundary):
            with redirect_stdout(io.StringIO()):
                self.assertEqual(batch.run_batch(args), 1)
                self.assertEqual(batch.run_batch(args), 1)
        runs = list(args.logs.iterdir())
        self.assertEqual(len(runs), 2)
        for run in runs:
            summary = json.loads((run / "summary.json").read_text())
            self.assertEqual({row["profile"]: row["status"] for row in summary},
                             {"1.21-fabric": "FAIL", "1.20.1-forge": "PASS"})
            self.assertEqual((run / "1.20.1-forge/runtime.log").read_text(), "Full runtime log\n")
            self.assertEqual((run / "prepare-1/gradle-prepare.log").read_text(), "Full build log\n")

    def test_interruption_reports_every_requested_profile(self):
        batch = self.module("batch")
        args = self.batch_args()
        with patch("batch.prepare", side_effect=KeyboardInterrupt), redirect_stdout(io.StringIO()):
            self.assertEqual(batch.run_batch(args), 130)
        summary = json.loads(next(args.logs.iterdir()).joinpath("summary.json").read_text())
        self.assertEqual(len(summary), 2)
        self.assertTrue(all(row["stage"] == "interrupted" for row in summary))

    def test_bare_version_expands_all_installed_supported_loaders_once(self):
        clients = self.module("clients")
        versions = self.root / "versions"
        versions.mkdir()
        for name in ("1.21-Fabric", "1.21-Forge", "1.21-NeoForge", "1.21-Fabric[]", "1.21.1-Fabric"):
            (versions / name).mkdir()
        self.assertEqual(clients.expand_versions(versions, ["1.21", "1.21-fabric"]),
                         ["1.21-fabric", "1.21-forge", "1.21-neoforge"])


if __name__ == "__main__":
    unittest.main()
