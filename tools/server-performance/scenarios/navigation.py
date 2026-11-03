"""Native navigation method, target, conservation and cleanup evidence contracts."""
from itertools import combinations
import base64
import binascii
import zlib
from models import Decision,Status

METHODS=frozenset(("compass","map","bossbar","actionbar","text_display"))


def method_selections(methods):
    values=tuple(sorted(methods))
    if not values or len(set(values))!=len(values) or not set(values)<=METHODS:
        raise ValueError("verified distinct supported navigation methods required")
    return tuple(selection for size in range(1,len(values)+1) for selection in combinations(values,size))


def method_coverage(methods,observations):
    required={frozenset(value) for value in method_selections(methods)}
    observed=set()
    for value in observations:
        requested=frozenset(value.get("requested",[]))
        actual=frozenset(value.get("nativeMethods",[]))
        if actual!=requested:
            return Decision(Status.FAIL,("native enabled methods differ from requested selection",))
        effects=value.get("effects",{})
        if not set(methods)<=effects.keys() or any(type(effects[method]) is not bool for method in methods):
            return Decision(Status.BLOCKED,("native/client effect evidence missing",))
        if any(effects[method] != (method in requested) for method in methods):
            return Decision(Status.FAIL,("method item/display effect absent or retained unexpectedly",))
        observed.add(requested)
    missing=required-observed
    return Decision(Status.BLOCKED if missing else Status.PASS,tuple("unexecuted method selection: "+",".join(sorted(value)) for value in sorted(missing,key=lambda value:tuple(sorted(value)))))


def retargeted(expected,state):
    target=state.get("target",{})
    if any(target.get(key)!=value for key,value in expected.items() if key!="expectedMap"):
        return Decision(Status.FAIL,("navigation session did not receive exact edited target",))
    methods=set(state.get("methods",[]))
    if "compass" in methods:
        if state.get("compassTarget")!={"dimension":expected["dimension"],"position":expected["position"]} or expected["displayName"] not in state.get("compassCaption",""):
            return Decision(Status.FAIL,("compass target/name stale after retarget",))
    if "map" in methods:
        map_expected=expected.get("expectedMap")
        if not map_expected:
            return Decision(Status.BLOCKED,("independent map region/cursor expectation missing",))
        actual=state.get("mapState",{})
        if any(actual.get(key)!=value for key,value in map_expected.items()) or expected["displayName"] not in actual.get("caption",""):
            return Decision(Status.FAIL,("map region/cursor/caption stale after retarget",))
    if "text_display" in methods and expected["displayName"] not in state.get("text",""):
        return Decision(Status.FAIL,("text display stale after retarget",))
    for method in ("bossbar","actionbar"):
        if method in methods and expected["displayName"] not in state.get(method,""):
            return Decision(Status.FAIL,(method+" stale after retarget",))
    return Decision(Status.PASS)


def ordinary_conservation(before,after):
    if not isinstance(before,dict) or not isinstance(after,dict):
        return Decision(Status.BLOCKED,("ordinary item identity/count evidence missing",))
    if before!=after:
        return Decision(Status.FAIL,("ordinary item identities/counts changed unexpectedly",))
    return Decision(Status.PASS)


def protected_transfer(sample):
    keys=("protectedInInvalidLocations","ordinaryBefore","ordinaryAfter","expectedOwners","actualOwners")
    if any(key not in sample for key in keys):
        return Decision(Status.BLOCKED,("native protection/ownership evidence missing",))
    if sample["protectedInInvalidLocations"]!=0 or sample["expectedOwners"]!=sample["actualOwners"]:
        return Decision(Status.FAIL,("protected item escaped or owner method counts changed",))
    return ordinary_conservation(sample["ordinaryBefore"],sample["ordinaryAfter"])


def cleanup_counts(expected,actual):
    if not expected or any(type(actual.get(key)) is not int or actual[key]<0 for key in expected):
        return Decision(Status.BLOCKED,("post-quiescence resource evidence missing/invalid",))
    if any(actual[key]!=value for key,value in expected.items()):
        return Decision(Status.FAIL,("post-quiescence resource counts differ from declared retained state",))
    return Decision(Status.PASS)


def terrain_roundtrip(region,colors,native,client):
    if len(colors)!=16384:
        raise ValueError("independent terrain expectation requires exactly 16384 pixels")
    if "colorsBase64" not in native:
        return Decision(Status.BLOCKED,("native terrain bytes missing",))
    try:
        actual=base64.b64decode(native["colorsBase64"],validate=True)
    except (ValueError,TypeError,binascii.Error):
        return Decision(Status.FAIL,("invalid native terrain byte encoding",))
    if any(native.get(key)!=value for key,value in region.items()) or actual!=colors:
        return Decision(Status.FAIL,("native map region or exact terrain pixels differ",))
    if type(client.get("knownPixels")) is not int or client["knownPixels"]!=16384:
        return Decision(Status.BLOCKED,("complete native client terrain patch coverage missing",))
    if client.get("scale")!=region["scale"] or client.get("colorsCrc32")!=zlib.crc32(colors):
        return Decision(Status.FAIL,("client map scale or reconstructed terrain checksum differs",))
    return Decision(Status.PASS)


def restored_session(expected,native,client):
    methods=set(expected["methods"])
    if native.get("uuid")!=expected["uuid"] or set(native.get("methods",[]))!=methods:
        return Decision(Status.FAIL,("restored owner or method selection differs",))
    items=native.get("items",[])
    for method in ("compass","map"):
        if sum(item.get("amount",0) for item in items if item.get("method")==method)!=(1 if method in methods else 0):
            return Decision(Status.FAIL,("restored method items missing or duplicated",))
    if any(item.get("method") not in {"compass","map"} for item in items):
        return Decision(Status.FAIL,("unrecognized restored protected item",))
    state=dict(native)
    for item in items:
        if item["method"]=="compass":
            state.update(compassTarget=item.get("compassTarget"),compassCaption=item.get("caption",""))
        elif item["method"]=="map":
            state["mapState"]=item.get("mapState",{})
            if state["mapState"].get("id")!=expected["mapId"]:
                return Decision(Status.FAIL,("restored owner map identity differs",))
    history=client.get("actionbar",[])
    state["actionbar"]=history[-1].get("text","") if history else ""
    decision=retargeted(expected["target"],state)
    if decision.status!=Status.PASS:
        return decision
    label=expected["target"]["displayName"]
    bars=client.get("bossbars",{})
    displays=client.get("textDisplays",{})
    if len(bars)!=(1 if "bossbar" in methods else 0) or any(label not in value.get("text","") for value in bars.values()):
        return Decision(Status.FAIL,("restored client bossbar differs",))
    if "text_display" in methods:
        display=displays.get(str(native.get("textDisplayId")),{})
        if len(displays)!=1 or label not in (display.get("text") or ""):
            return Decision(Status.FAIL,("restored native/client text display identity or text differs",))
    elif displays:
        return Decision(Status.FAIL,("unselected client text display retained",))
    return Decision(Status.PASS)


def death_protection(ordinary,drops,world):
    for value in (drops,world):
        if any(key not in value for key in ("ordinary","protectedItems","nestedProtectedCarriers")):
            return Decision(Status.BLOCKED,("native death/drop/world item observations missing",))
        if value["protectedItems"]!=0 or value["nestedProtectedCarriers"]!=0:
            return Decision(Status.FAIL,("protected death item escaped to drops/world",))
        decision=ordinary_conservation(ordinary,value["ordinary"])
        if decision.status!=Status.PASS:
            return decision
    return Decision(Status.PASS)


def physical_receipts(events,required):
    for expected in required:
        selectors={key:value for key,value in expected.items() if key!="cancelled"}
        matches=[event for event in events if all(event.get(key)==value for key,value in selectors.items())]
        if not matches:
            return Decision(Status.BLOCKED,("matching native physical interaction receipt missing",))
        if len(matches)!=1 or matches[0].get("cancelled") is not expected.get("cancelled"):
            return Decision(Status.FAIL,("native physical interaction duplicated or cancellation differs",))
    return Decision(Status.PASS)


def physical_drop_spawned(events,entities,requested):
    decision=physical_receipts(events,[{"event":"drop","cancelled":False,**value} for value in requested])
    if decision.status!=Status.PASS:
        return decision
    for expected in requested:
        drop=next(event for event in events if event.get("event")=="drop" and all(event.get(key)==value for key,value in expected.items()))
        identity=drop.get("itemEntityUuid")
        spawns=[event for event in events if event.get("event")=="item-spawn" and event.get("uuid")==identity]
        actual=[entity for entity in entities if entity.get("uuid")==identity]
        if not identity or not spawns or not actual:
            return Decision(Status.BLOCKED,("correlated native item spawn/world entity missing",))
        if len(spawns)!=1 or len(actual)!=1 or spawns[0].get("cancelled") is not False:
            return Decision(Status.FAIL,("ordinary native spawn duplicated or cancelled",))
        if any(actual[0].get(key)!=expected[key] for key in ("material","amount")):
            return Decision(Status.FAIL,("actual ordinary world-item identity/count differs",))
    return Decision(Status.PASS)


def nested_item_outcome(recipe,sample):
    graphs=[sample.get(key,{}) for key in ("before","expectedAfter","after")]
    for graph in graphs:
        if graph.get("origin")!="independent-bukkit-item-graph" or graph.get("complete") is not True or not graph.get("sha256"):
            return Decision(Status.BLOCKED,("complete independent item-graph observation missing",))
        if any(type(graph.get(key)) is not int or graph[key]<0 for key in ("protectedUnits","ordinaryLeafUnits")):
            return Decision(Status.BLOCKED,("independent item-graph counts invalid",))
    before,expected,after=graphs
    ordinary=recipe["expectedOrdinaryLeafUnits"]
    if sample.get("roundTripValid") is not True or before["protectedUnits"]!=recipe["expectedBeforeProtected"] or before["ordinaryLeafUnits"]!=ordinary or expected["protectedUnits"]!=0 or expected["ordinaryLeafUnits"]!=ordinary:
        return Decision(Status.BLOCKED,("native nested fixture differs from declared independent recipe",))
    if type(sample.get("worldPresent")) is not bool or type(sample.get("spawnCancelled")) is not bool:
        return Decision(Status.BLOCKED,("native world-presence/spawn receipt missing",))
    visible=recipe["expectedWorldPresent"]
    if sample["worldPresent"]!=visible or sample["spawnCancelled"] is visible:
        return Decision(Status.FAIL,("protected spawn escaped or innocent carrier was removed",))
    if visible and (after["protectedUnits"]!=0 or after["ordinaryLeafUnits"]!=ordinary or after["sha256"]!=expected["sha256"]):
        return Decision(Status.FAIL,("nested protected item escaped or ordinary carrier metadata/contents changed",))
    return Decision(Status.PASS)


def nested_recipes():
    return tuple({"kind":kind,"method":method,"expectedWorldPresent":visible,
                  "expectedBeforeProtected":protected,"expectedOrdinaryLeafUnits":ordinary}
                 for kind,method,visible,protected,ordinary in (
                     ("direct","compass",False,1,0),
                     ("shulker","map",True,1,67),
                     ("bundle-1","compass",True,1,1),
                     ("mixed","map",True,1,69),
                     ("bundle-8","compass",True,1,8),
                     ("bundle-9","map",True,1,9),
                     ("plain-shulker","compass",True,0,67),
                     ("plain-bundle-9","map",True,0,9)))


def container_cases():
    return (
        {"label":"open-direct","kind":"direct","method":"compass","trigger":"open","hold":True,
         "expectedBeforeProtected":1,"expectedBeforeNested":0,"expectedBeforeCursorProtected":0,
         "expectedAfterProtected":0,"expectedAfterNested":0},
        {"label":"open-nested","kind":"nested","method":"map","trigger":"open","hold":True,
         "expectedBeforeProtected":0,"expectedBeforeNested":1,"expectedBeforeCursorProtected":0,
         "expectedAfterProtected":0,"expectedAfterNested":0},
        {"label":"dirty-close-top","kind":"ordinary","method":"compass","trigger":"close","hold":True,
         "inject":"top","expectedBeforeProtected":1,"expectedBeforeNested":0,"expectedBeforeCursorProtected":0,
         "expectedAfterProtected":0,"expectedAfterNested":0},
        {"label":"dirty-close-cursor","kind":"ordinary","method":"compass","trigger":"close","hold":True,
         "inject":"cursor","expectedBeforeProtected":0,"expectedBeforeNested":0,"expectedBeforeCursorProtected":1,
         "expectedAfterProtected":0,"expectedAfterNested":0,"expectedAfterCursorProtected":0},
        {"label":"ordinary-control","kind":"ordinary","method":"compass","trigger":"open","hold":True,
         "expectedBeforeProtected":0,"expectedBeforeNested":0,"expectedBeforeCursorProtected":0,
         "expectedAfterProtected":0,"expectedAfterNested":0},
    )


def container_recipe(case,snapshot):
    expected=snapshot.get("expectedTop")
    if not isinstance(expected,dict) or not isinstance(expected.get("ordinary"),dict):
        raise ValueError("independent expected sanitized container missing")
    recipe={"trigger":case["trigger"],"inventoryType":"CHEST",
            "expectedBeforeProtected":case["expectedBeforeProtected"],
            "expectedBeforeNested":case["expectedBeforeNested"],
            "expectedBeforeCursorProtected":case["expectedBeforeCursorProtected"],
            "expectedAfterProtected":case["expectedAfterProtected"],
            "expectedAfterNested":case["expectedAfterNested"],
            "ordinary":dict(expected["ordinary"])}
    if "expectedAfterCursorProtected" in case:
        recipe["expectedAfterCursorProtected"]=case["expectedAfterCursorProtected"]
    return recipe


def container_cleanup(recipe,sample):
    trigger=recipe.get("trigger")
    inventory_type=recipe.get("inventoryType")
    if trigger not in {"open","close"} or not inventory_type:
        return Decision(Status.BLOCKED,("other-container cleanup recipe missing trigger or inventory type",))
    if any(key not in recipe for key in ("expectedBeforeProtected","expectedBeforeNested","expectedBeforeCursorProtected")):
        return Decision(Status.BLOCKED,("other-container cleanup recipe missing intended dirty fixture counts",))
    if sample.get("trigger")!=trigger:
        return Decision(Status.FAIL,("other-container cleanup trigger differs from recipe",))
    before=sample.get("before",{})
    after=sample.get("after",{})
    keys=("ordinary","protectedItems","nestedProtectedCarriers","inventoryType")
    if any(key not in before or key not in after for key in keys) or "cursorProtectedItems" not in before:
        return Decision(Status.BLOCKED,("other-container before/after inventory evidence missing",))
    if before.get("inventoryType")!=inventory_type or after.get("inventoryType")!=inventory_type:
        return Decision(Status.FAIL,("other-container inventory type differs",))
    if (before.get("protectedItems")!=recipe["expectedBeforeProtected"]
            or before.get("nestedProtectedCarriers")!=recipe["expectedBeforeNested"]
            or before.get("cursorProtectedItems")!=recipe["expectedBeforeCursorProtected"]):
        return Decision(Status.FAIL,("other-container did not start from the intended dirty fixture",))
    world=sample.get("world")
    if not isinstance(world,dict) or any(key not in world for key in ("protectedItems","nestedProtectedCarriers")):
        return Decision(Status.BLOCKED,("other-container world item evidence missing",))
    if world.get("protectedItems")!=0 or world.get("nestedProtectedCarriers")!=0:
        return Decision(Status.FAIL,("protected item escaped into the world",))
    event="inventory-open" if trigger=="open" else "inventory-close"
    receipts=[receipt for receipt in sample.get("receipts",[]) if receipt.get("event")==event and receipt.get("type")==inventory_type]
    if not receipts:
        return Decision(Status.BLOCKED,("matching native other-container "+trigger+" receipt missing",))
    if len(receipts)!=1:
        return Decision(Status.FAIL,("native other-container "+trigger+" receipt duplicated",))
    observed=receipts[0].get("topItems")
    if not isinstance(observed,dict) or any(key not in observed for key in ("ordinary","protectedItems","nestedProtectedCarriers")):
        return Decision(Status.BLOCKED,("other-container "+trigger+" receipt top inventory missing",))
    if observed.get("protectedItems")!=recipe.get("expectedAfterProtected") or observed.get("nestedProtectedCarriers")!=recipe.get("expectedAfterNested"):
        return Decision(Status.FAIL,("protected item remained in the other container at "+trigger,))
    decision=ordinary_conservation(recipe.get("ordinary"),observed.get("ordinary"))
    if decision.status!=Status.PASS:
        return decision
    if after.get("protectedItems")!=recipe.get("expectedAfterProtected") or after.get("nestedProtectedCarriers")!=recipe.get("expectedAfterNested"):
        return Decision(Status.FAIL,("protected item remained in the other container after "+trigger,))
    if "expectedAfterCursorProtected" in recipe:
        if "cursorProtectedItems" not in after:
            return Decision(Status.BLOCKED,("other-container cursor evidence missing",))
        if after.get("cursorProtectedItems")!=recipe["expectedAfterCursorProtected"]:
            return Decision(Status.FAIL,("protected cursor survived other-container close",))
        cursor=receipts[0].get("cursorItems")
        if not isinstance(cursor,dict) or "protectedItems" not in cursor:
            return Decision(Status.BLOCKED,("other-container close receipt cursor missing",))
        if cursor.get("protectedItems")!=recipe["expectedAfterCursorProtected"]:
            return Decision(Status.FAIL,("protected cursor survived other-container close",))
    graphs=sample.get("graphs")
    expected_graphs=sample.get("expectedGraphs")
    if graphs is not None or expected_graphs is not None:
        if not isinstance(graphs,list) or not isinstance(expected_graphs,list):
            return Decision(Status.BLOCKED,("independent expected sanitized carrier graphs missing",))
        if len(graphs)!=len(expected_graphs):
            return Decision(Status.FAIL,("nested protected item escaped or ordinary carrier metadata/contents changed",))
        for actual,expected in zip(graphs,expected_graphs):
            for graph in (actual,expected):
                if graph.get("origin")!="independent-bukkit-item-graph" or graph.get("complete") is not True or not graph.get("sha256"):
                    return Decision(Status.BLOCKED,("complete independent item-graph observation missing",))
            if (actual.get("protectedUnits")!=0 or actual.get("ordinaryLeafUnits")!=expected.get("ordinaryLeafUnits")
                    or actual.get("sha256")!=expected.get("sha256")):
                return Decision(Status.FAIL,("nested protected item escaped or ordinary carrier metadata/contents changed",))
    return ordinary_conservation(recipe.get("ordinary"),after.get("ordinary"))


def drag_cases():
    return (
        {"label":"protected-own-storage","inventoryType":"CRAFTING","slots":(9,10),"cancelled":False,
         "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
         "expectedAfterInvalidProtected":0},
        {"label":"protected-crafting-grid-denied","inventoryType":"CRAFTING","slots":(1,2),"cancelled":True,
         "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
         "expectedAfterInvalidProtected":0},
        {"label":"protected-chest-denied","inventoryType":"CHEST","slots":(3,4),"cancelled":True,
         "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
         "expectedAfterInvalidProtected":0},
        {"label":"protected-mixed-denied","inventoryType":"CHEST","slots":(0,27),"cancelled":True,
         "expectedBeforeCursorProtected":16,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
         "expectedAfterInvalidProtected":0},
        {"label":"ordinary-chest-control","inventoryType":"CHEST","slots":(0,1),"cancelled":False,
         "expectedBeforeCursorProtected":0,"expectedAfterCursorProtected":0,"expectedAfterTopProtected":0,
         "expectedAfterInvalidProtected":0},
    )


def player_raw_slot(slot,inventory_type):
    if inventory_type=="CRAFTING":
        if slot==40:
            return 45
        if 0<=slot<=8:
            return slot+36
        if 9<=slot<=35:
            return slot
    elif inventory_type=="CHEST":
        if 0<=slot<=8:
            return slot+54
        if 9<=slot<=35:
            return slot+18
    raise ValueError("player slot is not visible in the open inventory view")


def drag_recipe(case,snapshot):
    expected=snapshot.get("expectedTop") if case["inventoryType"]=="CHEST" else snapshot.get("ordinary")
    if case["inventoryType"]=="CHEST":
        if not isinstance(expected,dict) or not isinstance(expected.get("ordinary"),dict):
            raise ValueError("independent expected chest contents missing")
        ordinary=dict(expected["ordinary"])
    else:
        if not isinstance(expected,dict):
            raise ValueError("independent player ordinary identities missing")
        ordinary=dict(expected)
    return {**{key:case[key] for key in ("label","inventoryType","slots","cancelled",
                                         "expectedBeforeCursorProtected","expectedAfterCursorProtected",
                                         "expectedAfterTopProtected","expectedAfterInvalidProtected")},
            "ordinary":ordinary}


def drag_outcome(recipe,sample):
    inventory_type=recipe.get("inventoryType")
    slots=tuple(recipe.get("slots") or ())
    if inventory_type not in {"CRAFTING","CHEST"} or not slots:
        return Decision(Status.BLOCKED,("inventory-drag recipe missing view type or destination slots",))
    before=sample.get("before",{})
    after=sample.get("after",{})
    keys=("ordinary","protectedItems","nestedProtectedCarriers","inventoryType")
    if any(key not in before or key not in after for key in keys) or "cursorProtectedItems" not in before or "cursorProtectedItems" not in after:
        return Decision(Status.BLOCKED,("inventory-drag before/after inventory evidence missing",))
    if before.get("inventoryType")!=inventory_type or after.get("inventoryType")!=inventory_type:
        return Decision(Status.FAIL,("inventory-drag view type differs",))
    if before.get("cursorProtectedItems")!=recipe.get("expectedBeforeCursorProtected"):
        return Decision(Status.FAIL,("inventory-drag did not start from the intended cursor fixture",))
    if after.get("cursorProtectedItems")!=recipe.get("expectedAfterCursorProtected"):
        return Decision(Status.FAIL,("protected cursor survived inventory-drag close",))
    if type(after.get("protectedInInvalidLocations")) is not int:
        return Decision(Status.BLOCKED,("inventory-drag invalid-location evidence missing",))
    if after.get("protectedInInvalidLocations")!=recipe.get("expectedAfterInvalidProtected"):
        return Decision(Status.FAIL,("protected item remained in an invalid drag destination",))
    world=sample.get("world")
    if not isinstance(world,dict) or any(key not in world for key in ("protectedItems","nestedProtectedCarriers")):
        return Decision(Status.BLOCKED,("inventory-drag world item evidence missing",))
    if world.get("protectedItems")!=0 or world.get("nestedProtectedCarriers")!=0:
        return Decision(Status.FAIL,("protected item escaped into the world",))
    receipts=[receipt for receipt in sample.get("receipts",[]) if receipt.get("event")=="inventory-drag" and receipt.get("type")==inventory_type]
    if not receipts:
        return Decision(Status.BLOCKED,("matching native inventory-drag receipt missing",))
    if len(receipts)!=1:
        return Decision(Status.FAIL,("native inventory-drag receipt duplicated",))
    receipt=receipts[0]
    if receipt.get("cancelled") is not recipe.get("cancelled"):
        return Decision(Status.FAIL,("native inventory-drag cancellation differs",))
    if tuple(sorted(receipt.get("rawSlots") or ()))!=tuple(sorted(slots)):
        return Decision(Status.FAIL,("native inventory-drag raw slots differ",))
    items=receipt.get("items")
    if not isinstance(items,dict) or "protectedItems" not in items:
        return Decision(Status.BLOCKED,("inventory-drag receipt cursor item missing",))
    if items.get("protectedItems")!=recipe.get("expectedBeforeCursorProtected"):
        return Decision(Status.FAIL,("inventory-drag old cursor was not the intended item",))
    if inventory_type=="CHEST":
        top=receipt.get("topItems")
        if not isinstance(top,dict) or any(key not in top for key in ("ordinary","protectedItems","nestedProtectedCarriers")):
            return Decision(Status.BLOCKED,("inventory-drag receipt top inventory missing",))
        if top.get("protectedItems")!=recipe.get("expectedAfterTopProtected") or top.get("nestedProtectedCarriers")!=0:
            return Decision(Status.FAIL,("protected item remained in the other container after drag",))
    return ordinary_conservation(recipe.get("ordinary"),after.get("ordinary"))
