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

    def test_death_preserves_ordinary_drops_without_protected_world_items(self):
        from scenarios.navigation import death_protection
        ordinary={"stone-identity":64,"diamond-identity":3}
        drops={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        world={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        self.assertEqual(Status.PASS,death_protection(ordinary,drops,world).status)
        self.assertEqual(Status.BLOCKED,death_protection(ordinary,{},world).status)
        self.assertEqual(Status.FAIL,death_protection(ordinary,{**drops,"protectedItems":1},world).status)
        self.assertEqual(Status.FAIL,death_protection(ordinary,drops,{**world,"ordinary":{"stone-identity":64}}).status)

    def test_physical_protection_requires_matching_native_receipts(self):
        from scenarios.navigation import physical_receipts
        required=[{"event":"drop","uuid":"owner-1","cancelled":True}]
        receipt={"event":"drop","uuid":"owner-1","cancelled":True}
        self.assertEqual(Status.PASS,physical_receipts([receipt],required).status)
        self.assertEqual(Status.BLOCKED,physical_receipts([{**receipt,"uuid":"owner-2"}],required).status)
        self.assertEqual(Status.FAIL,physical_receipts([{**receipt,"cancelled":False}],required).status)
        self.assertEqual(Status.FAIL,physical_receipts([receipt,receipt],required).status)

    def test_ordinary_drop_is_present_as_the_actual_correlated_world_entity(self):
        from scenarios.navigation import physical_drop_spawned
        requested=[{"uuid":"owner-1","material":"minecraft:diamond","amount":3}]
        events=[{"event":"drop","uuid":"owner-1","cancelled":False,"itemEntityUuid":"item-1","material":"minecraft:diamond","amount":3},
                {"event":"item-spawn","uuid":"item-1","cancelled":False}]
        entity={"uuid":"item-1","material":"minecraft:diamond","amount":3}
        self.assertEqual(Status.PASS,physical_drop_spawned(events,[entity],requested).status)
        self.assertEqual(Status.BLOCKED,physical_drop_spawned(events,[],requested).status)
        self.assertEqual(Status.FAIL,physical_drop_spawned(events,[{**entity,"amount":1}],requested).status)
        self.assertEqual(Status.FAIL,physical_drop_spawned([events[0],{**events[1],"cancelled":True}],[entity],requested).status)

    def test_nested_escape_oracle_is_independent_of_release_depth_limit(self):
        from scenarios.navigation import nested_item_outcome
        graph={"origin":"independent-bukkit-item-graph","complete":True,"protectedUnits":1,"ordinaryLeafUnits":9,"sha256":"before"}
        expected={**graph,"protectedUnits":0,"sha256":"clean"}
        sample={"before":graph,"expectedAfter":expected,"after":expected,"worldPresent":True,"spawnCancelled":False,"roundTripValid":True,"installedReleaseContainsBefore":False}
        recipe={"expectedWorldPresent":True,"expectedBeforeProtected":1,"expectedOrdinaryLeafUnits":9}
        self.assertEqual(Status.PASS,nested_item_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,nested_item_outcome(recipe,{**sample,"after":{**expected,"protectedUnits":1}}).status)
        self.assertEqual(Status.BLOCKED,nested_item_outcome(recipe,{**sample,"after":{**expected,"complete":False}}).status)

    def test_nested_sanitization_preserves_exact_declared_carrier_metadata(self):
        from scenarios.navigation import nested_item_outcome
        graph={"origin":"independent-bukkit-item-graph","complete":True,"protectedUnits":0,"ordinaryLeafUnits":67,"sha256":"carrier-with-ordinary-metadata"}
        recipe={"expectedWorldPresent":True,"expectedBeforeProtected":0,"expectedOrdinaryLeafUnits":67}
        sample={"before":graph,"expectedAfter":graph,"after":graph,"worldPresent":True,"spawnCancelled":False,"roundTripValid":True}
        self.assertEqual(Status.PASS,nested_item_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,nested_item_outcome(recipe,{**sample,"worldPresent":False}).status)
        self.assertEqual(Status.FAIL,nested_item_outcome(recipe,{**sample,"after":{**graph,"sha256":"lost-name-or-child"}}).status)

    def test_cancelled_direct_spawn_may_leave_only_the_removed_object_tagged(self):
        from scenarios.navigation import nested_item_outcome
        graph={"origin":"independent-bukkit-item-graph","complete":True,"protectedUnits":1,"ordinaryLeafUnits":0,"sha256":"tagged"}
        expected={**graph,"protectedUnits":0,"sha256":"empty"}
        recipe={"expectedWorldPresent":False,"expectedBeforeProtected":1,"expectedOrdinaryLeafUnits":0}
        sample={"before":graph,"expectedAfter":expected,"after":graph,"worldPresent":False,"spawnCancelled":True,"roundTripValid":True}
        self.assertEqual(Status.PASS,nested_item_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,nested_item_outcome(recipe,{**sample,"worldPresent":True}).status)

    def test_other_container_open_removes_protected_and_preserves_ordinary(self):
        from scenarios.navigation import container_cleanup
        ordinary={"stone-identity":16,"diamond-identity":3}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        after={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        recipe={"trigger":"open","inventoryType":"CHEST","expectedBeforeProtected":1,"expectedBeforeNested":0,"expectedBeforeCursorProtected":0,"expectedAfterProtected":0,"expectedAfterNested":0,"ordinary":ordinary}
        clean={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"open","before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":clean}]}
        self.assertEqual(Status.PASS,container_cleanup(recipe,sample).status)
        self.assertEqual(Status.FAIL,container_cleanup(recipe,{**sample,"after":{**after,"protectedItems":1}}).status)
        self.assertEqual(Status.FAIL,container_cleanup(recipe,{**sample,"after":{**after,"ordinary":{"stone-identity":16}}}).status)
        self.assertEqual(Status.BLOCKED,container_cleanup(recipe,{**sample,"receipts":[]}).status)

    def test_dirty_close_cleans_top_inventory_and_cursor(self):
        from scenarios.navigation import container_cleanup
        ordinary={"stone-identity":16}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":1,"inventoryType":"CHEST"}
        after={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        recipe={"trigger":"close","inventoryType":"CHEST","expectedBeforeProtected":1,"expectedBeforeNested":0,"expectedBeforeCursorProtected":1,"expectedAfterProtected":0,"expectedAfterNested":0,"expectedAfterCursorProtected":0,"ordinary":ordinary}
        top={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        cursor={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"close","before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[{"event":"inventory-close","uuid":"owner-1","type":"CHEST","topItems":top,"cursorItems":cursor}]}
        self.assertEqual(Status.PASS,container_cleanup(recipe,sample).status)
        self.assertEqual(Status.FAIL,container_cleanup(recipe,{**sample,"after":{**after,"cursorProtectedItems":1}}).status)
        self.assertEqual(Status.BLOCKED,container_cleanup(recipe,{**sample,"before":{"ordinary":ordinary}}).status)

    def test_nested_other_container_escape_is_independent_of_top_level_only_cleanup(self):
        from scenarios.navigation import container_cleanup
        ordinary={"diamond-identity":3}
        before={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":1,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        after={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        recipe={"trigger":"open","inventoryType":"CHEST","expectedBeforeProtected":0,"expectedBeforeNested":1,"expectedBeforeCursorProtected":0,"expectedAfterProtected":0,"expectedAfterNested":0,"ordinary":ordinary}
        clean={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"open","before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":clean}]}
        self.assertEqual(Status.PASS,container_cleanup(recipe,sample).status)
        self.assertEqual(Status.FAIL,container_cleanup(recipe,{**sample,"after":{**after,"nestedProtectedCarriers":1}}).status)

    def test_other_container_open_cases_hold_until_state_is_checked(self):
        from scenarios.navigation import container_cases
        cases=container_cases()
        self.assertTrue(all(case["hold"] for case in cases if case["trigger"]=="open"))
        cursor=next(case for case in cases if case.get("inject")=="cursor")
        self.assertEqual(1,cursor["expectedBeforeCursorProtected"])
        self.assertEqual(0,cursor["expectedBeforeProtected"])

    def test_other_container_open_receipt_contents_are_checked_not_just_presence(self):
        from scenarios.navigation import container_cleanup
        ordinary={"stone-identity":16,"diamond-identity":3}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        after={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        recipe={"trigger":"open","inventoryType":"CHEST","expectedBeforeProtected":1,"expectedBeforeNested":0,"expectedBeforeCursorProtected":0,"expectedAfterProtected":0,"expectedAfterNested":0,"ordinary":ordinary}
        world={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}
        dirty={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0}
        clean={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"open","before":before,"after":after,"world":world,"receipts":[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":dirty}]}
        self.assertEqual(Status.FAIL,container_cleanup(recipe,sample).status)
        sample["receipts"]=[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":clean}]
        self.assertEqual(Status.PASS,container_cleanup(recipe,sample).status)

    def test_other_container_requires_the_intended_dirty_fixture_before_cleanup(self):
        from scenarios.navigation import container_cleanup
        ordinary={"stone-identity":16}
        clean={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        recipe={"trigger":"close","inventoryType":"CHEST","expectedBeforeProtected":0,"expectedBeforeNested":0,"expectedBeforeCursorProtected":1,"expectedAfterProtected":0,"expectedAfterNested":0,"expectedAfterCursorProtected":0,"ordinary":ordinary}
        top={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        cursor={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"close","before":clean,"after":clean,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[{"event":"inventory-close","uuid":"owner-1","type":"CHEST","topItems":top,"cursorItems":cursor}]}
        self.assertEqual(Status.FAIL,container_cleanup(recipe,sample).status)
        self.assertEqual(Status.PASS,container_cleanup(recipe,{**sample,"before":{**clean,"cursorProtectedItems":1}}).status)

    def test_other_container_rejects_protected_items_that_escape_into_the_world(self):
        from scenarios.navigation import container_cleanup
        ordinary={"stone-identity":16,"diamond-identity":3}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        after={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        recipe={"trigger":"open","inventoryType":"CHEST","expectedBeforeProtected":1,"expectedBeforeNested":0,"expectedBeforeCursorProtected":0,"expectedAfterProtected":0,"expectedAfterNested":0,"ordinary":ordinary}
        clean={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"open","before":before,"after":after,"world":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0},"receipts":[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":clean}]}
        self.assertEqual(Status.FAIL,container_cleanup(recipe,sample).status)
        sample["world"]={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":1}
        self.assertEqual(Status.FAIL,container_cleanup(recipe,sample).status)
        sample["world"]={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}
        self.assertEqual(Status.PASS,container_cleanup(recipe,sample).status)

    def test_nested_container_conservation_uses_independent_expected_carrier(self):
        from scenarios.navigation import container_cleanup,container_recipe
        expected_ordinary={"sanitized-shulker":1}
        graph={"origin":"independent-bukkit-item-graph","complete":True,"protectedUnits":0,"ordinaryLeafUnits":67,"sha256":"clean-carrier"}
        case={"trigger":"open","expectedBeforeProtected":0,"expectedBeforeNested":1,"expectedBeforeCursorProtected":0,"expectedAfterProtected":0,"expectedAfterNested":0}
        recipe=container_recipe(case,{"top":{"ordinary":{}},"expectedTop":{"ordinary":expected_ordinary}})
        self.assertEqual(expected_ordinary,recipe["ordinary"])
        before={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":1,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        deleted={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        world={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}
        sample={"trigger":"open","before":before,"after":deleted,"world":world,"graphs":[],"expectedGraphs":[graph],
                "receipts":[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}}]}
        self.assertEqual(Status.FAIL,container_cleanup(recipe,sample).status)
        clean_after={"ordinary":expected_ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        clean_top={"ordinary":expected_ordinary,"protectedItems":0,"nestedProtectedCarriers":0}
        clean={"trigger":"open","before":before,"after":clean_after,"world":world,"graphs":[graph],"expectedGraphs":[graph],
               "receipts":[{"event":"inventory-open","uuid":"owner-1","type":"CHEST","topItems":clean_top}]}
        self.assertEqual(Status.PASS,container_cleanup(recipe,clean).status)
        self.assertEqual(Status.FAIL,container_cleanup(recipe,{**clean,"graphs":[{**graph,"sha256":"lost-name-or-child"}]}).status)

    def test_drag_cases_cover_allowed_denied_and_ordinary(self):
        from scenarios.navigation import drag_cases
        labels={case["label"]:case for case in drag_cases()}
        self.assertEqual({"protected-own-storage","protected-crafting-grid-denied","protected-chest-denied","protected-mixed-denied","ordinary-chest-control"},set(labels))
        self.assertFalse(labels["protected-own-storage"]["cancelled"])
        self.assertEqual("CRAFTING",labels["protected-own-storage"]["inventoryType"])
        self.assertEqual((9,10),labels["protected-own-storage"]["slots"])
        self.assertEqual(16,labels["protected-own-storage"]["expectedBeforeCursorProtected"])
        self.assertTrue(labels["protected-crafting-grid-denied"]["cancelled"])
        self.assertEqual((1,2),labels["protected-crafting-grid-denied"]["slots"])
        self.assertEqual(16,labels["protected-crafting-grid-denied"]["expectedBeforeCursorProtected"])
        self.assertTrue(labels["protected-chest-denied"]["cancelled"])
        self.assertEqual("CHEST",labels["protected-chest-denied"]["inventoryType"])
        self.assertEqual((3,4),labels["protected-chest-denied"]["slots"])
        self.assertEqual(16,labels["protected-chest-denied"]["expectedBeforeCursorProtected"])
        self.assertTrue(labels["protected-mixed-denied"]["cancelled"])
        self.assertEqual((0,27),labels["protected-mixed-denied"]["slots"])
        self.assertFalse(labels["ordinary-chest-control"]["cancelled"])
        self.assertEqual((0,1),labels["ordinary-chest-control"]["slots"])
        self.assertEqual(0,labels["ordinary-chest-control"]["expectedBeforeCursorProtected"])

    def test_player_raw_slot_matches_open_view(self):
        from scenarios.navigation import player_raw_slot
        self.assertEqual(36,player_raw_slot(0,"CRAFTING"))
        self.assertEqual(9,player_raw_slot(9,"CRAFTING"))
        self.assertEqual(45,player_raw_slot(40,"CRAFTING"))
        self.assertEqual(54,player_raw_slot(0,"CHEST"))
        self.assertEqual(27,player_raw_slot(9,"CHEST"))
        self.assertEqual(37,player_raw_slot(0,"WORKBENCH"))
        self.assertEqual(10,player_raw_slot(9,"WORKBENCH"))
        self.assertEqual(36,player_raw_slot(35,"WORKBENCH"))
        self.assertEqual(30,player_raw_slot(0,"CARTOGRAPHY"))
        self.assertEqual(3,player_raw_slot(9,"CARTOGRAPHY"))
        self.assertEqual(29,player_raw_slot(35,"CARTOGRAPHY"))
        with self.assertRaises(ValueError):
            player_raw_slot(40,"CHEST")
        with self.assertRaises(ValueError):
            player_raw_slot(40,"WORKBENCH")
        with self.assertRaises(ValueError):
            player_raw_slot(0,"FURNACE")

    def test_protected_drag_into_own_storage_is_allowed(self):
        from scenarios.navigation import drag_outcome
        ordinary={"stone-identity":64,"diamond-identity":3}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":16,"inventoryType":"CRAFTING"}
        after={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CRAFTING","protectedInInvalidLocations":0}
        recipe={"label":"protected-own-storage","inventoryType":"CRAFTING","slots":(9,10),"cancelled":False,
                "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
                "expectedAfterInvalidProtected":0,"ordinary":ordinary}
        receipt={"event":"inventory-drag","uuid":"owner-1","type":"CRAFTING","cancelled":False,"rawSlots":[9,10],
                 "items":{"ordinary":{},"protectedItems":16,"nestedProtectedCarriers":0}}
        sample={"before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[receipt]}
        self.assertEqual(Status.PASS,drag_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,drag_outcome(recipe,{**sample,"receipts":[{**receipt,"cancelled":True}]}).status)

    def test_protected_drag_into_chest_or_crafting_grid_is_cancelled(self):
        from scenarios.navigation import drag_outcome
        ordinary={"stone-identity":16,"diamond-identity":3}
        recipe={"label":"protected-chest-denied","inventoryType":"CHEST","slots":(0,1),"cancelled":True,
                "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
                "expectedAfterInvalidProtected":0,"ordinary":ordinary}
        before={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":16,"inventoryType":"CHEST"}
        after={"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST","protectedInInvalidLocations":0}
        receipt={"event":"inventory-drag","uuid":"owner-1","type":"CHEST","cancelled":True,"rawSlots":[0,1],
                 "items":{"ordinary":{},"protectedItems":16,"nestedProtectedCarriers":0},"topItems":{"ordinary":ordinary,"protectedItems":0,"nestedProtectedCarriers":0}}
        sample={"before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[receipt]}
        self.assertEqual(Status.PASS,drag_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,drag_outcome(recipe,{**sample,"after":{**after,"protectedInInvalidLocations":1}}).status)
        self.assertEqual(Status.FAIL,drag_outcome(recipe,{**sample,"receipts":[{**receipt,"topItems":{"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0}}]}).status)

    def test_ordinary_drag_is_not_cancelled(self):
        from scenarios.navigation import drag_outcome
        transferred={"stone-identity":64}
        recipe={"label":"ordinary-chest-control","inventoryType":"CHEST","slots":(0,1),"cancelled":False,
                "expectedBeforeCursorProtected":0,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
                "expectedAfterInvalidProtected":0,"ordinary":transferred}
        before={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST"}
        after={"ordinary":transferred,"protectedItems":0,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CHEST","protectedInInvalidLocations":0}
        receipt={"event":"inventory-drag","uuid":"owner-1","type":"CHEST","cancelled":False,"rawSlots":[0,1],
                 "items":{"ordinary":transferred,"protectedItems":0,"nestedProtectedCarriers":0},
                 "topItems":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}}
        sample={"before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[receipt]}
        self.assertEqual(Status.PASS,drag_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,drag_outcome(recipe,{**sample,"receipts":[{**receipt,"cancelled":True}]}).status)
        self.assertEqual(Status.FAIL,drag_outcome(recipe,{**sample,"after":{**after,"ordinary":{}}}).status)

    def test_drag_requires_matching_native_receipt_and_intended_cursor(self):
        from scenarios.navigation import drag_outcome
        ordinary={"stone-identity":64}
        recipe={"label":"protected-own-storage","inventoryType":"CRAFTING","slots":(9,10),"cancelled":False,
                "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
                "expectedAfterInvalidProtected":0,"ordinary":ordinary}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CRAFTING"}
        after={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CRAFTING","protectedInInvalidLocations":0}
        receipt={"event":"inventory-drag","uuid":"owner-1","type":"CRAFTING","cancelled":False,"rawSlots":[9,10],
                 "items":{"ordinary":{},"protectedItems":16,"nestedProtectedCarriers":0}}
        sample={"before":before,"after":after,"world":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0},"receipts":[receipt]}
        self.assertEqual(Status.FAIL,drag_outcome(recipe,sample).status)
        sample["before"]={**before,"cursorProtectedItems":16}
        sample["receipts"]=[]
        self.assertEqual(Status.BLOCKED,drag_outcome(recipe,sample).status)
        sample["receipts"]=[{**receipt,"rawSlots":[10]}]
        self.assertEqual(Status.FAIL,drag_outcome(recipe,sample).status)

    def test_drag_rejects_protected_items_that_escape_into_the_world(self):
        from scenarios.navigation import drag_outcome
        ordinary={"stone-identity":64}
        recipe={"label":"protected-own-storage","inventoryType":"CRAFTING","slots":(9,10),"cancelled":False,
                "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
                "expectedAfterInvalidProtected":0,"ordinary":ordinary}
        before={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":16,"inventoryType":"CRAFTING"}
        after={"ordinary":ordinary,"protectedItems":1,"nestedProtectedCarriers":0,"cursorProtectedItems":0,"inventoryType":"CRAFTING","protectedInInvalidLocations":0}
        receipt={"event":"inventory-drag","uuid":"owner-1","type":"CRAFTING","cancelled":False,"rawSlots":[9,10],
                 "items":{"ordinary":{},"protectedItems":16,"nestedProtectedCarriers":0}}
        sample={"before":before,"after":after,"world":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0},"receipts":[receipt]}
        self.assertEqual(Status.FAIL,drag_outcome(recipe,sample).status)
        sample["world"]={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":1}
        self.assertEqual(Status.FAIL,drag_outcome(recipe,sample).status)
        sample["world"]={"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}
        self.assertEqual(Status.PASS,drag_outcome(recipe,sample).status)

    def test_station_cases_cover_craft_and_cartography(self):
        from scenarios.navigation import station_cases
        labels={case["label"]:case for case in station_cases()}
        self.assertEqual({"protected-workbench-map-denied","ordinary-workbench-map","protected-cartography-scale-denied","ordinary-cartography-scale"},set(labels))
        self.assertEqual("WORKBENCH",labels["protected-workbench-map-denied"]["inventoryType"])
        self.assertTrue(labels["protected-workbench-map-denied"]["protected"])
        self.assertEqual("minecraft:map",labels["protected-workbench-map-denied"]["recipe"])
        self.assertFalse(labels["ordinary-workbench-map"]["protected"])
        self.assertEqual("CARTOGRAPHY",labels["protected-cartography-scale-denied"]["inventoryType"])
        self.assertTrue(labels["protected-cartography-scale-denied"]["protected"])
        self.assertEqual("minecraft:filled_map",labels["ordinary-cartography-scale"]["resultMaterial"])
        self.assertFalse(labels["ordinary-cartography-scale"]["protected"])

    def test_protected_workbench_recipe_result_is_cleared(self):
        from scenarios.navigation import station_outcome
        recipe=self.station_recipe("protected-workbench-map-denied")
        counts={"paper":8,"plainCompass":0,"emptyMaps":0,"plainFilledMaps":0,"sessionCompass":1,"sessionMap":1}
        receipt={"event":"prepare-craft","uuid":"owner-1","type":"WORKBENCH","recipe":"minecraft:map",
                 "matrix":{"ordinary":{"minecraft:paper:paper":8},"protectedItems":1,"nestedProtectedCarriers":0},
                 "result":{"material":"minecraft:air","amount":0,"protected":False}}
        denied={"event":"inventory-click","uuid":"owner-1","type":"WORKBENCH","cancelled":True,"rawSlot":5,
                "items":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0}}
        sample={"before":counts,"after":dict(counts),"world":self.clean_world(),"receipts":[denied,receipt]}
        self.assertEqual(Status.BLOCKED,station_outcome(recipe,{"before":counts,"after":dict(counts),"world":self.clean_world(),"receipts":[receipt]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[{**denied,"cancelled":False},receipt]}).status)
        self.assertEqual(Status.PASS,station_outcome(recipe,sample).status)
        produced={**receipt,"result":{"material":"minecraft:map","amount":1,"protected":False}}
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[denied,produced]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"after":{**counts,"emptyMaps":1}}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"after":{**counts,"sessionCompass":0}}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[denied,receipt,{"event":"craft-item","type":"WORKBENCH","cancelled":False,"rawSlot":0,"recipe":"minecraft:map"}]}).status)

    def test_ordinary_workbench_result_can_be_taken(self):
        from scenarios.navigation import station_outcome
        recipe=self.station_recipe("ordinary-workbench-map")
        before={"paper":8,"plainCompass":1,"emptyMaps":0,"plainFilledMaps":0,"sessionCompass":1,"sessionMap":1}
        after={"paper":0,"plainCompass":0,"emptyMaps":1,"plainFilledMaps":0,"sessionCompass":1,"sessionMap":1}
        prepare={"event":"prepare-craft","uuid":"owner-1","type":"WORKBENCH","recipe":"minecraft:map",
                 "matrix":{"ordinary":{"minecraft:paper:paper":8,"minecraft:compass:plain":1},"protectedItems":0,"nestedProtectedCarriers":0},
                 "result":{"material":"minecraft:map","amount":1,"protected":False}}
        take={"event":"craft-item","uuid":"owner-1","type":"WORKBENCH","cancelled":False,"rawSlot":0,"recipe":"minecraft:map"}
        sample={"before":before,"after":after,"world":self.clean_world(),"receipts":[prepare,take]}
        self.assertEqual(Status.PASS,station_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[{**prepare,"result":{"material":"minecraft:air","amount":0,"protected":False}},take]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[prepare,{**take,"cancelled":True}]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"after":before}).status)

    def test_protected_cartography_result_is_cleared_and_take_does_not_succeed(self):
        from scenarios.navigation import station_outcome
        recipe=self.station_recipe("protected-cartography-scale-denied")
        counts={"paper":1,"plainCompass":0,"emptyMaps":0,"plainFilledMaps":0,"sessionCompass":1,"sessionMap":1}
        receipt={"event":"prepare-result","uuid":"owner-1","type":"CARTOGRAPHY",
                 "top":{"ordinary":{"minecraft:paper:paper":1},"protectedItems":1,"nestedProtectedCarriers":0},
                 "result":{"material":"minecraft:air","amount":0,"protected":False}}
        denied={"event":"inventory-click","uuid":"owner-1","type":"CARTOGRAPHY","cancelled":True,"rawSlot":0,
                "items":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0}}
        sample={"before":counts,"after":dict(counts),"world":self.clean_world(),"receipts":[denied,receipt]}
        self.assertEqual(Status.BLOCKED,station_outcome(recipe,{"before":counts,"after":dict(counts),"world":self.clean_world(),"receipts":[receipt]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[{**denied,"cancelled":False},receipt]}).status)
        self.assertEqual(Status.PASS,station_outcome(recipe,sample).status)
        produced={**receipt,"result":{"material":"minecraft:filled_map","amount":1,"protected":True}}
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[denied,produced]}).status)
        taken={"event":"cartography-item","uuid":"owner-1","type":"CARTOGRAPHY","cancelled":False,"rawSlot":2}
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[denied,receipt,taken]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"after":{**counts,"paper":0}}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"world":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0}}).status)
        map_only={**receipt,"top":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0}}
        self.assertEqual(Status.PASS,station_outcome(recipe,{**sample,"receipts":[denied,map_only,receipt]}).status)

    def test_ordinary_cartography_take_is_not_cancelled(self):
        from scenarios.navigation import station_outcome
        recipe=self.station_recipe("ordinary-cartography-scale")
        before={"paper":1,"plainCompass":0,"emptyMaps":0,"plainFilledMaps":1,"sessionCompass":1,"sessionMap":1}
        after={"paper":0,"plainCompass":0,"emptyMaps":0,"plainFilledMaps":1,"sessionCompass":1,"sessionMap":1}
        prepare={"event":"prepare-result","uuid":"owner-1","type":"CARTOGRAPHY",
                 "top":{"ordinary":{"minecraft:paper:paper":1,"minecraft:filled_map:plain":1},"protectedItems":0,"nestedProtectedCarriers":0},
                 "result":{"material":"minecraft:filled_map","amount":1,"protected":False}}
        take={"event":"cartography-item","uuid":"owner-1","type":"CARTOGRAPHY","cancelled":False,"rawSlot":2}
        sample={"before":before,"after":after,"world":self.clean_world(),"receipts":[prepare,take]}
        self.assertEqual(Status.PASS,station_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[{**prepare,"result":{"material":"minecraft:air","amount":0,"protected":False}},take]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"receipts":[prepare,{**take,"cancelled":True}]}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"after":{**after,"sessionMap":0}}).status)
        self.assertEqual(Status.FAIL,station_outcome(recipe,{**sample,"after":{**after,"plainFilledMaps":2}}).status)

    def test_station_requires_matching_native_receipt_and_counts(self):
        from scenarios.navigation import station_outcome
        recipe=self.station_recipe("protected-workbench-map-denied")
        counts={"paper":8,"plainCompass":0,"emptyMaps":0,"plainFilledMaps":0,"sessionCompass":1,"sessionMap":1}
        sample={"before":counts,"after":dict(counts),"world":self.clean_world(),"receipts":[]}
        self.assertEqual(Status.BLOCKED,station_outcome(recipe,sample).status)
        self.assertEqual(Status.BLOCKED,station_outcome(recipe,{"before":counts,"after":dict(counts),"receipts":[]}).status)
        partial={"event":"prepare-craft","type":"WORKBENCH","recipe":"minecraft:map","matrix":{"protectedItems":1},"result":{"material":"minecraft:air"}}
        self.assertEqual(Status.BLOCKED,station_outcome(recipe,{**sample,"receipts":[partial]}).status)

    def test_entity_cases_cover_pickup_frame_and_equipment(self):
        from scenarios.navigation import entity_cases
        labels={case["label"]:case for case in entity_cases()}
        self.assertEqual({
            "entity-pickup-protected","entity-pickup-ordinary","hopper-pickup-protected","hopper-pickup-ordinary",
            "item-frame-place-denied","item-frame-ordinary","item-frame-remove-protected",
            "armor-stand-place-denied","armor-stand-sanitize","armor-stand-ordinary",
            "allay-held-denied","allay-sanitize"},set(labels))
        self.assertEqual("entity-pickup",labels["entity-pickup-protected"]["event"])
        self.assertTrue(labels["entity-pickup-protected"]["cancelled"])
        self.assertFalse(labels["entity-pickup-ordinary"]["cancelled"])
        self.assertEqual("PLACE",labels["item-frame-place-denied"]["action"])
        self.assertEqual("ROTATE",labels["item-frame-remove-protected"]["action"])
        self.assertTrue(labels["armor-stand-sanitize"]["injected"])
        self.assertTrue(labels["allay-sanitize"]["injected"])
        self.assertFalse(labels["armor-stand-ordinary"]["cancelled"])

    def test_protected_pickup_removes_the_world_item_and_keeps_the_session(self):
        from scenarios.navigation import entity_outcome
        recipe=self.entity_recipe("entity-pickup-protected")
        ordinary={"minecraft:stone:stone":64}
        receipt={"event":"entity-pickup","cancelled":True,"items":{"protectedItems":1,"ordinary":{}}}
        sample=self.entity_sample(ordinary,ordinary,{},receipt)
        self.assertEqual(Status.PASS,entity_outcome(recipe,sample).status)
        self.assertEqual(Status.FAIL,entity_outcome(recipe,{**sample,"receipts":[{**receipt,"cancelled":False}]}).status)
        self.assertEqual(Status.FAIL,entity_outcome(recipe,{**sample,"after":{**sample["after"],"sessionCompass":0}}).status)
        self.assertEqual(Status.FAIL,entity_outcome(recipe,{**sample,"world":{"protectedItems":1,"nestedProtectedCarriers":0}}).status)
        self.assertEqual(Status.FAIL,entity_outcome(recipe,{**sample,"carrier":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0}}).status)
        self.assertEqual(Status.BLOCKED,entity_outcome(recipe,{**sample,"receipts":[]}).status)
        self.assertEqual(Status.FAIL,entity_outcome(recipe,{**sample,"playerOrdinary":{"minecraft:stone:stone":63}}).status)

    def test_ordinary_pickup_reaches_the_player_and_hopper_pickup_does_not(self):
        from scenarios.navigation import entity_outcome
        stone={"minecraft:stone:stone":64}
        gained={"minecraft:stone:stone":64,"minecraft:emerald:gem":1}
        player=self.entity_recipe("entity-pickup-ordinary")
        player={**player,"playerOrdinary":gained,"carrierOrdinary":{}}
        hopper=self.entity_recipe("hopper-pickup-ordinary")
        hopper={**hopper,"playerOrdinary":stone,"carrierOrdinary":{"minecraft:emerald:gem":1}}
        receipt={"event":"entity-pickup","cancelled":False,"items":{"protectedItems":0,"ordinary":{"minecraft:emerald:gem":1}}}
        self.assertEqual(Status.PASS,entity_outcome(player,self.entity_sample(stone,gained,{},receipt)).status)
        self.assertEqual(Status.FAIL,entity_outcome(player,self.entity_sample(stone,stone,{},receipt)).status)
        hopper_receipt={**receipt,"event":"inventory-pickup"}
        self.assertEqual(Status.PASS,entity_outcome(hopper,self.entity_sample(stone,stone,{"minecraft:emerald:gem":1},hopper_receipt)).status)
        self.assertEqual(Status.FAIL,entity_outcome(hopper,self.entity_sample(gained,gained,{"minecraft:emerald:gem":1},hopper_receipt)).status)
        self.assertEqual(Status.FAIL,entity_outcome(hopper,{**self.entity_sample(stone,stone,{"minecraft:emerald:gem":1},hopper_receipt),"carrier":{"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}}).status)

    def test_frame_and_equipment_keep_protected_items_off_the_carrier(self):
        from scenarios.navigation import entity_outcome
        ordinary={"minecraft:stone:stone":64}
        placed={"minecraft:stone:stone":63}
        denied=self.entity_recipe("item-frame-place-denied")
        denied={**denied,"playerOrdinary":ordinary,"carrierOrdinary":{}}
        receipt={"event":"item-frame","cancelled":True,"action":"PLACE","items":{"protectedItems":1,"ordinary":{}}}
        sample=self.entity_sample(ordinary,ordinary,{},receipt)
        self.assertEqual(Status.PASS,entity_outcome(denied,sample).status)
        self.assertEqual(Status.FAIL,entity_outcome(denied,{**sample,"receipts":[{**receipt,"action":"REMOVE"}]}).status)
        self.assertEqual(Status.FAIL,entity_outcome(denied,{**sample,"receipts":[{**receipt,"items":{"protectedItems":0,"ordinary":ordinary}}]}).status)
        removed=self.entity_recipe("item-frame-remove-protected")
        removed={**removed,"playerOrdinary":ordinary,"carrierOrdinary":{}}
        injected={**receipt,"action":"ROTATE","injected":{"protectedItems":1}}
        self.assertEqual(Status.BLOCKED,entity_outcome(removed,self.entity_sample(ordinary,ordinary,{},{**receipt,"action":"ROTATE"})).status)
        self.assertEqual(Status.PASS,entity_outcome(removed,self.entity_sample(ordinary,ordinary,{},injected)).status)
        stand=self.entity_recipe("armor-stand-ordinary")
        stand={**stand,"playerOrdinary":placed,"carrierOrdinary":{"minecraft:stone:stone":1}}
        stand_receipt={"event":"armor-stand","cancelled":False,"items":{"protectedItems":0,"ordinary":{"minecraft:stone:stone":1}}}
        self.assertEqual(Status.PASS,entity_outcome(stand,self.entity_sample(ordinary,placed,{"minecraft:stone:stone":1},stand_receipt)).status)
        self.assertEqual(Status.FAIL,entity_outcome(stand,self.entity_sample(ordinary,placed,{"minecraft:stone:stone":1},{**stand_receipt,"cancelled":True})).status)
        sanitize=self.entity_recipe("allay-sanitize")
        sanitize={**sanitize,"playerOrdinary":ordinary,"carrierOrdinary":{}}
        allay={"event":"allay-interact","cancelled":True,"items":{"protectedItems":0,"ordinary":{}},"injected":{"protectedItems":1}}
        self.assertEqual(Status.PASS,entity_outcome(sanitize,self.entity_sample(ordinary,ordinary,{},allay)).status)
        self.assertEqual(Status.FAIL,entity_outcome(sanitize,{**self.entity_sample(ordinary,ordinary,{},allay),"carrier":{"ordinary":{},"protectedItems":1,"nestedProtectedCarriers":0}}).status)

    def test_deleted_waypoint_stops_only_its_sessions(self):
        from scenarios.navigation import deleted_navigation
        removed={"dimension":"minecraft:overworld","list":"protection_fixture","waypoint":"target"}
        other={"dimension":"minecraft:overworld","list":"protection_fixture","waypoint":"other"}
        ordinary={"minecraft:stone:stone":64}
        affected={"before":{"target":removed,"ordinary":ordinary,"protectedItems":2,"persisted":True},"after":{"target":None,"ordinary":ordinary,"protectedItems":0,"persisted":False}}
        untouched={"before":{"target":other,"ordinary":ordinary,"protectedItems":2,"persisted":True},"after":{"target":other,"ordinary":ordinary,"protectedItems":2,"persisted":True}}
        self.assertEqual(Status.PASS,deleted_navigation(removed,[affected,untouched]).status)
        self.assertEqual(Status.FAIL,deleted_navigation(removed,[{**affected,"after":{**affected["after"],"target":removed}}]).status)
        self.assertEqual(Status.FAIL,deleted_navigation(removed,[{**affected,"after":{**affected["after"],"protectedItems":1}}]).status)
        self.assertEqual(Status.FAIL,deleted_navigation(removed,[{**affected,"after":{**affected["after"],"persisted":True}}]).status)
        self.assertEqual(Status.FAIL,deleted_navigation(removed,[{**affected,"before":{**affected["before"],"persisted":False}}]).status)
        self.assertEqual(Status.FAIL,deleted_navigation(removed,[{**affected,"after":{**affected["after"],"ordinary":{}}},untouched]).status)
        self.assertEqual(Status.FAIL,deleted_navigation(removed,[affected,{**untouched,"after":{**untouched["after"],"target":None,"persisted":False,"protectedItems":0}}]).status)
        self.assertEqual(Status.BLOCKED,deleted_navigation(removed,[{"before":affected["before"],"after":{"target":None,"ordinary":ordinary,"protectedItems":0}}]).status)
        self.assertEqual(Status.BLOCKED,deleted_navigation(removed,[untouched]).status)

    def test_dimension_switch_keeps_the_owner_map_and_separate_viewers(self):
        import base64
        import zlib
        from scenarios.navigation import switched_dimension,separate_map_viewers
        origin={"dimension":"minecraft:overworld","centerX":512,"centerZ":512,"scale":2}
        destination={"dimension":"minecraft:the_nether","centerX":0,"centerZ":0,"scale":2}
        colors=bytes(16384)
        native={**destination,"colorsBase64":base64.b64encode(colors).decode()}
        client={"scale":2,"knownPixels":16384,"colorsCrc32":zlib.crc32(colors)}
        self.assertEqual(Status.PASS,switched_dimension(origin,destination,7,7,colors,native,client).status)
        self.assertEqual(Status.FAIL,switched_dimension(origin,origin,7,7,colors,native,client).status)
        self.assertEqual(Status.FAIL,switched_dimension(origin,destination,7,8,colors,native,client).status)
        self.assertEqual(Status.FAIL,switched_dimension(origin,destination,7,7,bytes([1])*16384,native,client).status)
        viewers=({"uuid":"a","mapId":7,"dimension":"minecraft:overworld"},{"uuid":"b","mapId":8,"dimension":"minecraft:the_nether"})
        self.assertEqual(Status.PASS,separate_map_viewers(viewers).status)
        self.assertEqual(Status.FAIL,separate_map_viewers((viewers[0],{**viewers[1],"mapId":7})).status)
        self.assertEqual(Status.BLOCKED,separate_map_viewers((viewers[0],)).status)
        self.assertEqual(Status.BLOCKED,separate_map_viewers((viewers[0],{**viewers[1],"dimension":None})).status)

    @staticmethod
    def entity_recipe(label):
        from scenarios.navigation import entity_cases
        recipe=next(case for case in entity_cases() if case["label"]==label)
        return {**recipe,"playerOrdinary":{"minecraft:stone:stone":64},"carrierOrdinary":{}}

    def entity_sample(self,before,after,carrier,receipt):
        return {"before":{"sessionCompass":1,"sessionMap":1},"after":{"sessionCompass":1,"sessionMap":1},
                "world":self.clean_world(),"carrier":{"ordinary":carrier,"protectedItems":0,"nestedProtectedCarriers":0},
                "playerOrdinary":after,"receipts":[receipt],"startedOrdinary":before}

    @staticmethod
    def station_recipe(label):
        from scenarios.navigation import station_cases
        return next(case for case in station_cases() if case["label"]==label)

    @staticmethod
    def clean_world():
        return {"ordinary":{},"protectedItems":0,"nestedProtectedCarriers":0}