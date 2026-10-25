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

    def test_saved_terrain_reopens_exactly_and_client_has_full_pixels(self):
        import base64
        import zlib
        from scenarios.navigation import terrain_roundtrip
        region={"dimension":"minecraft:overworld","centerX":512,"centerZ":512,"scale":2}
        colors=bytes([4,8,12,0])*4096
        native={**region,"colorsBase64":base64.b64encode(colors).decode()}
        client={"scale":2,"knownPixels":16384,"colorsCrc32":zlib.crc32(colors)}
        self.assertEqual(Status.PASS,terrain_roundtrip(region,colors,native,client).status)
        self.assertEqual(Status.BLOCKED,terrain_roundtrip(region,colors,region,client).status)
        self.assertEqual(Status.FAIL,terrain_roundtrip(region,colors,{**native,"centerX":0},client).status)
        self.assertEqual(Status.FAIL,terrain_roundtrip(region,colors,{**native,"colorsBase64":base64.b64encode(bytes(16384)).decode()},client).status)
        self.assertEqual(Status.BLOCKED,terrain_roundtrip(region,colors,native,{**client,"knownPixels":512}).status)
        self.assertEqual(Status.FAIL,terrain_roundtrip(region,colors,native,{**client,"colorsCrc32":0}).status)

    def test_active_session_restores_all_methods_with_owner_map_identity(self):
        from copy import deepcopy
        from scenarios.navigation import restored_session
        target={"dimension":"minecraft:overworld","list":"perf","waypoint":"new","displayName":"New","position":[128,64,-16],"rgb":1122867,"expectedMap":{"centerX":0,"centerZ":0,"scale":2,"cursorX":64,"cursorZ":-8}}
        methods=["compass","map","bossbar","actionbar","text_display"]
        expected={"uuid":"owner-1","mapId":7,"methods":methods,"target":target}
        native={"uuid":"owner-1","methods":methods,"target":{key:value for key,value in target.items() if key!="expectedMap"},"text":"New 128m","bossbar":"New 128m","textDisplayId":42,
                "items":[{"method":"compass","amount":1,"caption":"New","compassTarget":{"dimension":"minecraft:overworld","position":[128,64,-16]}},{"method":"map","amount":1,"mapState":{**target["expectedMap"],"id":7,"caption":"New"}}]}
        client={"actionbar":[{"text":"New 128m"}],"bossbars":{"bar":{"text":"New 128m"}},"textDisplays":{"42":{"text":"New 128m"}}}
        self.assertEqual(Status.PASS,restored_session(expected,native,client).status)
        for mutate in (lambda value:value.update(uuid="owner-2"),lambda value:value["items"].pop(0),lambda value:value["items"][1]["mapState"].update(id=8),lambda value:value.update(methods=["map"])):
            value=deepcopy(native)
            mutate(value)
            self.assertEqual(Status.FAIL,restored_session(expected,value,client).status)
        client["textDisplays"]={"99":{"text":"New 128m"}}
        self.assertEqual(Status.FAIL,restored_session(expected,native,client).status)
