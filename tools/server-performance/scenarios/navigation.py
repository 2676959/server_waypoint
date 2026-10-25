"""Native navigation method, target, conservation and cleanup evidence contracts."""
from itertools import combinations
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
