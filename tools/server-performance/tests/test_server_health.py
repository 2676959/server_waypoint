import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from models import Status


class ServerHealthTest(unittest.TestCase):
    def test_watchdog_failure_is_not_overridden_by_completed_actions(self):
        from server_health import assess_log
        text='[22:35:51] [Paper Watchdog Thread/ERROR]: The server has not responded for 10 seconds! Creating thread dump\n[22:36:30] [Server thread/INFO]: all actions completed\n'
        result=assess_log(text)
        self.assertEqual(Status.FAIL,result.status)
        self.assertTrue(any("watchdog" in value for value in result.reasons))

    def test_native_event_exception_fails_stability(self):
        from server_health import assess_log
        self.assertEqual(Status.FAIL,assess_log('[22:35:51] [Server thread/ERROR]: Could not pass event InventoryClickEvent\n').status)
        self.assertEqual(Status.FAIL,assess_log('[22:35:51 SEVERE]: event failed\n').status)

    def test_info_payload_words_are_not_logger_severity(self):
        from server_health import assess_log
        text='[22:35:51] [Server thread/INFO]: SW_PERF {"reason":"ERROR text fixture","exception":null}\n'
        self.assertEqual(Status.PASS,assess_log(text).status)

    def test_missing_native_log_is_blocked(self):
        from server_health import native_log_health
        import tempfile
        with tempfile.TemporaryDirectory() as root:
            self.assertEqual(Status.BLOCKED,native_log_health(Path(root)/"absent.log").status)
