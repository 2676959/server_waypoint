import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))


class MutationLeavesTest(unittest.TestCase):
    def tree(self,*paths):
        return {"origin":"native-minecraft-command-dispatcher","nodes":[{"path":path,"executable":True} for path in paths]}

    def test_property_edit_has_independent_fields_and_exact_revision(self):
        from scenarios.mutation_leaves import mutation_cases
        path="wp edit waypoint <dimension> <list identifier> <waypoint identifier> set description <value>"
        case=mutation_cases(self.tree(path))[0]
        self.assertEqual(17,case["before"]["n"])
        self.assertEqual(18,case["after"]["n"])
        self.assertEqual("Changed",case["after"]["waypoints"][0]["description"])
        self.assertEqual("origin",case["after"]["waypoints"][0]["name"])
        self.assertNotEqual(case["before"]["waypoints"][0]["description"],case["after"]["waypoints"][0]["description"])

    def test_clear_and_picker_have_distinct_mutation_expectations(self):
        from scenarios.mutation_leaves import mutation_cases
        clear,picker=mutation_cases(self.tree("wp edit waypoint <dimension> <list identifier> <waypoint identifier> clear display-name","wp edit waypoint <dimension> <list identifier> <waypoint identifier> set color"))
        self.assertNotIn("display_name",clear["after"]["waypoints"][0])
        self.assertEqual(picker["before"],picker["after"])

    def test_quick_add_only_accepts_declared_generated_color(self):
        from scenarios.mutation_leaves import mutation_cases,resolve_generated
        case=mutation_cases(self.tree("wp add <dimension> <list identifier> <position> <waypoint identifier>"))[0]
        observed={**case["after"],"waypoints":[{**case["after"]["waypoints"][0],"color":"#123ABC"}]}
        expected=resolve_generated(case,observed)
        self.assertEqual("#123ABC",expected["waypoints"][0]["color"])
        observed["waypoints"][0]["color"]="invalid"
        with self.assertRaises(ValueError):
            resolve_generated(case,observed)

    def test_restore_fixture_declares_real_remove_prelude_and_two_revisions(self):
        from scenarios.mutation_leaves import mutation_cases
        case=mutation_cases(self.tree("wp restore <token>"))[0]
        self.assertTrue(case["removeBeforeRestore"])
        self.assertEqual(case["before"]["n"]+2,case["after"]["n"])

    def test_list_remove_positive_fixture_is_empty(self):
        from scenarios.mutation_leaves import mutation_cases
        case=mutation_cases(self.tree("wp remove <dimension> <list identifier>"))[0]
        self.assertEqual([],case["before"]["waypoints"])
        self.assertIsNone(case["after"])

    def test_async_reload_feedback_requires_exclusive_native_call_and_unique_delivered_token(self):
        import json
        from scenarios.mutation_leaves import async_reload_feedback
        callback={"path":"wp reload","input":"wp reload","actor":"CONSOLE","commandObservationId":4,"serverObserved":True}
        def event(sequence,kind,data):
            return "[Server thread/INFO]: SW_PERF "+json.dumps({"schemaVersion":1,"runId":"r","attemptId":"a","sequence":sequence,"type":kind,"data":data})
        begin=event(1,"command.begin",callback)
        end=event(2,"command.end",callback)
        transcript=begin+"\n"+end+"\n[Server thread/INFO]: PERF_RELOAD_unique"
        self.assertTrue(async_reload_feedback(transcript,"r","a",callback,"PERF_RELOAD_unique")["feedbackObserved"])
        self.assertIsNone(async_reload_feedback(transcript,"r","a",callback,"wrong-token"))
        other=event(3,"command.begin",{**callback,"input":"wp help","path":"wp help","commandObservationId":5})
        with self.assertRaises(ValueError):
            async_reload_feedback(begin+"\n"+end+"\n"+other+"\n[Server thread/INFO]: PERF_RELOAD_unique","r","a",callback,"PERF_RELOAD_unique")
