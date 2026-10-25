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
