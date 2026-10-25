import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from models import Status


class NavigationScenariosTest(unittest.TestCase):
    def test_all_supported_methods_and_combinations_executed(self):
        from scenarios.navigation import method_selections,method_coverage
        methods=("compass","map","bossbar","actionbar","text_display")
        selections=method_selections(methods)
        self.assertEqual(31,len(selections))
        self.assertEqual(31,len({frozenset(value) for value in selections}))
        self.assertTrue(all(value and set(value)<=set(methods) for value in selections))
        observations=[{"requested":list(selection),"nativeMethods":list(selection),"effects":{method:method in selection for method in methods}} for selection in selections]
        self.assertEqual(Status.PASS,method_coverage(methods,observations).status)
        self.assertEqual(Status.BLOCKED,method_coverage(methods,observations[:-1]).status)
        observations[0]["nativeMethods"]=[]
        self.assertEqual(Status.FAIL,method_coverage(methods,observations).status)

    def test_retarget_updates_items_and_displays(self):
        from scenarios.navigation import retargeted
        target={"dimension":"minecraft:overworld","list":"perf","waypoint":"new","displayName":"New","position":[128,64,-16],"rgb":1122867,"expectedMap":{"centerX":0,"centerZ":0,"scale":2,"cursorX":64,"cursorZ":-8}}
        state={"methods":["compass","map","text_display"],"target":{key:value for key,value in target.items() if key!="expectedMap"},"compassCaption":"New","compassTarget":{"dimension":"minecraft:overworld","position":[128,64,-16]},"mapState":{**target["expectedMap"],"caption":"New"},"text":"New 128m"}
        self.assertEqual(Status.PASS,retargeted(target,state).status)
        state["compassTarget"]["position"]=[0,64,0]
        self.assertEqual(Status.FAIL,retargeted(target,state).status)

    def test_nested_item_cannot_escape(self):
        from scenarios.navigation import protected_transfer
        sample={"protectedInInvalidLocations":0,"ordinaryBefore":{"stone":64},"ordinaryAfter":{"stone":64},"expectedOwners":{"alice":2},"actualOwners":{"alice":2}}
        self.assertEqual(Status.PASS,protected_transfer(sample).status)
        sample["protectedInInvalidLocations"]=1
        self.assertEqual(Status.FAIL,protected_transfer(sample).status)

    def test_retarget_rejects_stale_live_bossbar_and_actionbar(self):
        from scenarios.navigation import retargeted
        target={"dimension":"minecraft:overworld","list":"perf","waypoint":"new","displayName":"New","position":[128,64,-16],"rgb":1122867}
        state={"methods":["bossbar","actionbar"],"target":target,"bossbar":"Old 64m","actionbar":"New 128m"}
        self.assertEqual(Status.FAIL,retargeted(target,state).status)
        state["bossbar"]="New 128m"
        state["actionbar"]="Old 64m"
        self.assertEqual(Status.FAIL,retargeted(target,state).status)
        state["actionbar"]="New 128m"
        self.assertEqual(Status.PASS,retargeted(target,state).status)

    def test_plain_items_not_deleted(self):
        from scenarios.navigation import ordinary_conservation
        self.assertEqual(Status.PASS,ordinary_conservation({"stone":64,"named-diamond":16},{"stone":64,"named-diamond":16}).status)
        self.assertEqual(Status.FAIL,ordinary_conservation({"stone":64,"named-diamond":16},{"stone":64}).status)

    def test_disconnect_cleanup_returns_to_expected_counts(self):
        from scenarios.navigation import cleanup_counts
        expected={"sessions":0,"tasks":0,"entities":0,"items":0,"mapTargets":0,"mapCacheEntries":1}
        self.assertEqual(Status.PASS,cleanup_counts(expected,dict(expected)).status)
        self.assertEqual(Status.FAIL,cleanup_counts(expected,{**expected,"tasks":1}).status)
        self.assertEqual(Status.BLOCKED,cleanup_counts(expected,{}).status)
