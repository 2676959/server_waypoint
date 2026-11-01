"""Check Task 8 relative links and bundled locale keys/placeholders; no gameplay claims."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[6]
DOCS = [
    "docs/tips/server-guide.md", "docs/tips/gui/local-guide.md", "docs/features/README.md",
    "docs/features/waypoint-manager/README.md", "docs/features/private-waypoint/README.md",
    "docs/features/private-waypoint/plans/2026-10-16-private-waypoint.md",
    "docs/features/private-waypoint/validation/2026-10-16-private-waypoint-checklist.md",
    "docs/features/private-waypoint/validation/2026-10-31-platform-validation.md",
    "docs/features/private-waypoint/validation/2026-10-31-final-acceptance.md",
    "docs/features/private-waypoint/validation/execution/final-acceptance/README.md",
]
issues=[]
links=0
for name in DOCS:
    path=ROOT/name
    for target in re.findall(r"\]\(([^)]+)\)",path.read_text()):
        target=target.split("#",1)[0]
        if not target or "://" in target or target.startswith("mailto:"): continue
        links+=1
        if not (path.parent/target).exists(): issues.append(f"{name}: missing {target}")
print(f"Relative link paths: {links} checked in {len(DOCS)} files")
for folder,pattern in [("common/src/main/resources/lang",r"\{(\d+)(?:[,}])"),
                       ("mods/src/main/resources/assets/server_waypoint/lang",r"%(?:(\d+)\$)?[sdf]")]:
    files=sorted((ROOT/folder).glob("*.json"))
    baseline=json.loads((ROOT/folder/"en_us.json").read_text())
    for path in files:
        values=json.loads(path.read_text())
        required={key for key in baseline if folder.startswith("mods/") or key.startswith("wp.")}
        if not required.issubset(values) or set(values)-set(baseline): issues.append(f"{path.name}: required key set differs in {folder}")
        for key,value in values.items():
            if key in baseline and sorted(re.findall(pattern,value))!=sorted(re.findall(pattern,baseline[key])):
                issues.append(f"{path.name}: placeholders differ for {key}")
    print(f"{folder}: {len(files)} locales, {len(baseline)} English keys; required keys and existing placeholders checked")
for issue in issues: print("FAIL",issue)
assert not issues, f"{len(issues)} issues"
print("PASS (language fluency and visual layout require separate evidence)")
