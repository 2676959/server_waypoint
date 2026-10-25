"""Declarative per-leaf storage fixtures and an independent operation ledger."""
from copy import deepcopy
import re

GENERATED_COLOR = "@generated-rgb"


def fixture_point():
    return {"name":"origin","display_name":"Before","initials":"B","pos":[4,-61,-4],
            "color":"#123456","yaw":30,"global":True,"keywords":["before"],
            "description":"Before","icon":"minecraft:apple"}


def mutation_cases(tree):
    if tree.get("origin") != "native-minecraft-command-dispatcher":
        raise ValueError("native command tree required")
    cases=[]
    for node in tree.get("nodes",[]):
        path=node["path"]
        parts=path.split()
        if not node.get("executable") or len(parts)<2 or parts[0]!="wp" or parts[1] not in ("add","edit","remove","restore","reload"):
            continue
        family=parts[1]
        name=f"mutation_{len(cases):02}"
        before={"list_name":name,"display_name":"Before list","n":17,"waypoints":[fixture_point()]}
        values={"dimension":"minecraft:overworld","list identifier":name,"position":"12 -61 -16",
                "waypoint identifier":"origin","initials":"P","color":"112233","yaw":"30","global":"true",
                "keywords":"changed","description":"Changed","icon":"minecraft:diamond","page number":"1","token":"{restoreToken}"}
        random_color=False
        remove_before=False
        if family=="add":
            if "<list identifier>" not in path:
                before=after=None
            elif "<waypoint identifier>" not in path:
                before=None
                after={"list_name":name,"n":1,"waypoints":[]}
            else:
                before={"list_name":name,"n":17,"waypoints":[]}
                point={"name":"origin","initials":"P","pos":[12,-61,-16],"color":"#112233","yaw":30,
                       "global":True,"keywords":[],"description":""}
                if "<initials>" not in path:
                    point.update(initials="OR",color=GENERATED_COLOR,yaw=0)
                    random_color=True
                if "<keywords>" in path:
                    point["keywords"]=["changed"]
                if "<description>" in path:
                    point["description"]="Changed"
                if "<icon>" in path:
                    point["icon"]="minecraft:diamond"
                after={**deepcopy(before),"n":18,"waypoints":[point]}
        elif family=="edit":
            if path=="wp edit":
                before=after=None
            else:
                if path.endswith("visibility global"):
                    before["waypoints"][0]["global"]=False
                after=deepcopy(before)
                target=after if path.startswith("wp edit list ") else after["waypoints"][0]
                if " set " in path:
                    suffix=path.split(" set ",1)[1]
                    prop=suffix.split()[0]
                    if suffix in ("color","yaw"):
                        pass  # Picker leaves render controls without mutating.
                    else:
                        if prop=="identifier":
                            value=name+"_renamed" if target is after else "renamed"
                            target["list_name" if target is after else "name"]=value
                            values["value"]=value
                        elif prop in ("display-name","initials","keywords","description","icon"):
                            value={"display-name":"Shown","initials":"Q","keywords":"changed","description":"Changed","icon":"minecraft:diamond"}[prop]
                            values["value"]=value
                            target[prop.replace("-","_")]=[value] if prop=="keywords" else value
                        elif prop=="position":
                            values["position"]="-32 70 48"
                            target["pos"]=[-32,70,48]
                        elif prop=="color":
                            target["color"]="#112233"
                        elif prop=="yaw":
                            values["yaw"]="-45"
                            target["yaw"]=-45
                        elif prop=="visibility":
                            target["global"]=suffix.endswith("global")
                        else:
                            raise ValueError("unmapped native edit property: "+prop)
                        after["n"]+=1
                elif " clear " in path:
                    prop=path.split(" clear ",1)[1]
                    key=prop.replace("-","_")
                    if key in ("display_name","icon"):
                        target.pop(key,None)
                    elif key=="keywords":
                        target[key]=[]
                    elif key=="description":
                        target[key]=""
                    else:
                        raise ValueError("unmapped native clear property: "+prop)
                    after["n"]+=1
                else:
                    raise ValueError("unmapped native edit leaf")
        elif family=="remove":
            if path=="wp remove":
                before=after=None
            elif "<waypoint identifier>" in path:
                after={**deepcopy(before),"n":18,"waypoints":[]}
            else:
                before["waypoints"]=[]
                after=None
        elif family=="restore":
            remove_before=True
            after={**deepcopy(before),"n":19}
        else:
            before=after=None
        def argument(match):
            value=match.group(1)
            if value not in values:
                raise ValueError("unmapped native mutation argument: "+value)
            return values[value]
        command=re.sub(r"<([^>]+)>",argument,path)
        cases.append({"path":path,"command":command,"owner":name,"before":before,"after":after,
                      "randomColor":random_color,"removeBeforeRestore":remove_before})
    if not cases:
        raise ValueError("native mutation inventory empty")
    return cases


def resolve_generated(case,observed):
    expected=deepcopy(case["after"])
    if not case["randomColor"]:
        return expected
    if not observed or len(observed.get("waypoints",[]))!=1:
        raise ValueError("quick-add native result missing")
    color=observed["waypoints"][0].get("color")
    if not isinstance(color,str) or not re.fullmatch(r"#[0-9A-F]{6}",color):
        raise ValueError("quick-add generated color outside declared RGB domain")
    expected["waypoints"][0]["color"]=color
    return expected



def async_reload_feedback(transcript,run_id,attempt_id,callback,token):
    from probe_driver import ProbeStream
    if callback.get("path")!="wp reload" or callback.get("input")!="wp reload" or callback.get("actor")!="CONSOLE" or callback.get("serverObserved") is not True or not token:
        raise ValueError("actual exclusive native reload callback required")
    stream=ProbeStream(run_id,attempt_id)
    begun=ended=False
    response=None
    calls=0
    for line in transcript.splitlines():
        if "SW_PERF " in line:
            event=stream.parse(line)
            data=event.get("data",{})
            if event["type"]=="command.begin":
                if data.get("input")=="wp reload":
                    calls+=1
                    if data.get("commandObservationId")!=callback["commandObservationId"] or begun:
                        raise ValueError("multiple/unmatched reload observations")
                    begun=True
                elif ended and response is None:
                    raise ValueError("another command entered the exclusive reload response window")
            elif event["type"]=="command.end" and data.get("commandObservationId")==callback["commandObservationId"]:
                if not begun or data.get("input")!="wp reload":
                    raise ValueError("unmatched native reload callback end")
                ended=True
            continue
        output=re.search(r"\[Server thread/INFO\]: (.*)",line)
        if ended and output and token in output.group(1):
            response=output.group(1)
    if calls!=1 or response is None:
        return None
    return {**callback,"feedback":[response],"feedbackObserved":True,
            "feedbackObservation":"exclusive-asynchronous-native-console-response"}
