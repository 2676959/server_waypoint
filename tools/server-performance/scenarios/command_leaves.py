"""Native read-command templates and actual console feedback transcript evidence."""
import re
from probe_driver import ProbeStream

ARGUMENTS = {"dimension":"minecraft:overworld", "list identifier":"perf_00",
             "waypoint identifier":"wp_00000", "page number":"1", "page limit":"1",
             "search query":"perf", "list sort mode":"name", "list sort order":"ascending",
             "list view":"lists"}


def read_cases(tree):
    if tree.get("origin") != "native-minecraft-command-dispatcher":
        raise ValueError("native registered command tree required")
    result = []
    seen = set()
    for node in tree.get("nodes",[]):
        path = node["path"]
        if not node.get("executable") or not (path == "wp" or path.startswith(("wp list", "wp help", "wp details"))):
            continue
        if path in seen:
            raise ValueError("duplicate native command leaf")
        seen.add(path)
        def argument(match):
            name = match.group(1)
            if name not in ARGUMENTS:
                raise ValueError("unmapped native read argument: "+name)
            return ARGUMENTS[name]
        command = re.sub(r"<([^>]+)>",argument,path)
        result.append({"path":path,"command":command})
    if not result:
        raise ValueError("native read command inventory is empty")
    return result


def console_feedback(transcript, run_id, attempt_id):
    stream = ProbeStream(run_id,attempt_id)
    current = None
    result = []
    completed = set()
    owner_output = False
    for line in transcript.splitlines():
        if "SW_PERF " in line:
            owner_output = False
            event = stream.parse(line)
            if event["type"] == "command.begin":
                data = event["data"]
                identifier = data.get("commandObservationId")
                if current is not None or type(identifier) is not int or identifier in completed or data.get("actor") != "CONSOLE":
                    raise ValueError("invalid/overlapping console feedback observation")
                current = {**data,"feedback":[]}
            elif event["type"] == "command.end":
                data = event["data"]
                if current is None or any(data.get(key) != current.get(key) for key in ("commandObservationId","path","input","actor")) or data.get("serverObserved") is not True:
                    raise ValueError("unmatched native console callback end")
                feedback = current["feedback"]
                result.append({**data,"feedback":feedback,"feedbackObserved":bool(feedback)})
                completed.add(data["commandObservationId"])
                current = None
            continue
        output = re.search(r"\[Server thread/INFO\]: (.*)",line)
        if output:
            owner_output = current is not None
            if owner_output and output.group(1).strip():
                current["feedback"].append(output.group(1))
        elif re.match(r"\[.*(?:/INFO|/WARN|/ERROR| INFO| WARN| ERROR).*\]:", line):
            owner_output = False
        elif owner_output and current is not None and line.strip():
            current["feedback"].append(line)
    if current is not None:
        raise ValueError("console feedback observation did not complete")
    return result


SUGGESTION_ARGUMENTS = {**ARGUMENTS,"position":"12 -61 -16","initials":"P","color":"112233",
    "yaw":"0","global":"true","keywords":"perf","description":"description","source":"xaero",
    "translation x":"0","translation y":"0","translation z":"0",
    "rotation x":"0","rotation y":"0","rotation z":"0","scale x":"1","scale y":"1","scale z":"1"}


def suggestion_cases(tree):
    if tree.get("origin") != "native-minecraft-command-dispatcher":
        raise ValueError("native registered suggestion tree required")
    cases=[]
    for node in tree.get("nodes",[]):
        path=node["path"]
        if not node.get("suggestions") or path.startswith("wp remote "):
            continue
        if type(node.get("providerId")) is not int or node["providerId"] <= 0:
            raise ValueError("native suggestion provider identity missing")
        match=re.fullmatch(r"(.*)<[^>]+>",path)
        if not match:
            raise ValueError("unmapped native suggestion argument path")
        def argument(value):
            name=value.group(1)
            if name not in SUGGESTION_ARGUMENTS:
                raise ValueError("unmapped native suggestion prefix: "+name)
            return SUGGESTION_ARGUMENTS[name]
        cases.append({"path":path,"input":re.sub(r"<([^>]+)>",argument,match.group(1)),"providerId":node["providerId"]})
    return cases


def verify_suggestions(cases,reports):
    from models import Decision,Status
    if not cases or len(cases) != len(reports):
        return Decision(Status.BLOCKED,("missing native suggestion cases/results",))
    missing=[]
    for case,report in zip(cases,reports):
        callbacks=report.get("providerCallbacks",[])
        matching=[value for value in callbacks if value.get("providerId")==case["providerId"] and value.get("input")==case["input"] and value.get("serverObserved") is True]
        if report.get("input") != case["input"] or report.get("droppedProviderCallbacks") != 0 or not matching:
            missing.append("native suggestion provider not observed: "+case["path"])
        if any(value.get("exception") for value in matching):
            return Decision(Status.FAIL,("native suggestion provider threw",))
    return Decision(Status.BLOCKED if missing else Status.PASS,tuple(missing))
