import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))


class CommandLeavesTest(unittest.TestCase):
    def test_native_read_leaf_templates_preserve_repeated_options(self):
        from scenarios.command_leaves import read_cases
        path = "wp list <dimension> <list identifier> search <search query> sort default page <page number> limit <page limit> view flat search <search query>"
        cases = read_cases({"origin":"native-minecraft-command-dispatcher","nodes":[{"path":path,"executable":True}]})
        self.assertEqual(path,cases[0]["path"])
        self.assertEqual("wp list minecraft:overworld perf_00 search perf sort default page 1 limit 1 view flat search perf",cases[0]["command"])
        with self.assertRaises(ValueError):
            read_cases({"origin":"native-minecraft-command-dispatcher","nodes":[{"path":"wp list <unknown>","executable":True}]})

    def transcript(self, feedback=True):
        def marker(sequence,kind):
            return "[Server thread/INFO]: SW_PERF "+json.dumps({"schemaVersion":1,"runId":"run","attemptId":"attempt","sequence":sequence,"type":kind,"data":{"commandObservationId":1,"path":"wp help","input":"wp help","actor":"CONSOLE","serverObserved":True}})
        lines=[marker(1,"command.begin"),"[Async thread/INFO]: unrelated output"]
        if feedback:
            lines.append("[Server thread/INFO]: Native help output")
        lines.append(marker(2,"command.end"))
        return "\n".join(lines)

    def test_console_feedback_requires_native_owner_thread_output_between_matching_markers(self):
        from scenarios.command_leaves import console_feedback
        values=console_feedback(self.transcript(),"run","attempt")
        self.assertTrue(values[0]["feedbackObserved"])
        self.assertEqual(["Native help output"],values[0]["feedback"])
        values=console_feedback(self.transcript(False),"run","attempt")
        self.assertFalse(values[0]["feedbackObserved"])
        with self.assertRaises(ValueError):
            console_feedback(self.transcript().replace('"commandObservationId": 1','"commandObservationId": 2',1),"run","attempt")

    def test_unclosed_console_observation_cannot_supply_feedback(self):
        from scenarios.command_leaves import console_feedback
        with self.assertRaises(ValueError):
            console_feedback(self.transcript().splitlines()[0],"run","attempt")

    def test_multiline_owner_feedback_is_retained(self):
        from scenarios.command_leaves import console_feedback
        transcript=self.transcript().replace("Native help output\n","Native help output\nUsage: /wp help\n")
        self.assertEqual(["Native help output","Usage: /wp help"],console_feedback(transcript,"run","attempt")[0]["feedback"])

    def test_suggestion_prefix_requires_matching_native_provider_receipt(self):
        from scenarios.command_leaves import suggestion_cases,verify_suggestions
        node={"path":"wp details waypoint <dimension> <list identifier> <waypoint identifier>","suggestions":True,"providerId":7}
        cases=suggestion_cases({"origin":"native-minecraft-command-dispatcher","nodes":[node]})
        self.assertEqual("wp details waypoint minecraft:overworld perf_00 ",cases[0]["input"])
        receipt={"input":cases[0]["input"],"providerCallbacks":[{"providerId":7,"input":cases[0]["input"],"serverObserved":True,"suggestions":["wp_00000"]}],"droppedProviderCallbacks":0}
        self.assertEqual("PASS",verify_suggestions(cases,[receipt]).status.value)
        receipt["providerCallbacks"][0]["providerId"]=8
        self.assertEqual("BLOCKED",verify_suggestions(cases,[receipt]).status.value)
