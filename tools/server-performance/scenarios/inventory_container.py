"""Other-container and inventory-drag session for the timed protection case."""
import json
from pathlib import Path

from models import Decision, Status
from scenarios.navigation import (
    METHODS, container_cases, container_cleanup, container_recipe, drag_cases, drag_outcome,
    player_raw_slot, restored_session,
)

EXPECTED_TARGET = {
    "dimension": "minecraft:overworld", "list": "protection_fixture", "waypoint": "target",
    "displayName": "Protected", "position": [320, -61, 320], "rgb": 0x112233,
    "expectedMap": {"centerX": 512, "centerZ": 512, "scale": 2, "cursorX": -96, "cursorZ": -96},
}


def chest_position(index):
    return {"x": 300 + index * 3, "y": -61, "z": 297}


def removal_decision(removed):
    if not isinstance(removed, dict) or "present" not in removed:
        return Decision(Status.BLOCKED, ("owned other-container cleanup observation missing",))
    if removed.get("present") is True:
        return Decision(Status.FAIL, ("owned other-container cleanup incomplete",))
    if removed.get("present") is not False:
        return Decision(Status.BLOCKED, ("owned other-container cleanup observation missing",))
    return Decision(Status.PASS, ())


def finish_container_cleanup(probe, names):
    decisions = []
    for name in names:
        probe.send("protection.container.remove", {"player": name})
        decisions.append(removal_decision(probe.wait("protection.container.removed")["data"]))
    return tuple(decisions)


def exercise_inventory_container(probe, driver, players, output):
    """Run the passing container and drag fixtures, then leave an empty chest for timing."""
    names = tuple(player["name"] for player in players)
    identities = {player["name"]: player["uuid"] for player in players}
    session = _Session(probe, driver, names, identities)
    decisions = []
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    try:
        session.arm()
        session.prepare_navigation()
        decisions.extend(session.container_cases())
        decisions.extend(session.drag_cases())
        session.place_empty()
        armed = True
    except (OSError, RuntimeError, TimeoutError, ValueError) as error:
        decisions.append(Decision(Status.BLOCKED, (f"inventory container fixture unavailable: {error}",)))
        armed = False
    path = output / "inventory-container.json"
    path.write_text(json.dumps({"schemaVersion": 1, "chestsArmed": armed, "decisions": [decision.status.value for decision in decisions]}, indent=2) + "\n")
    return {
        "decisions": tuple(decisions),
        "assertions": {},
        "paths": {"observation": str(path)},
        "chestsArmed": armed,
    }


class _Session:
    def __init__(self, probe, driver, names, identities):
        self.probe = probe
        self.driver = driver
        self.names = names
        self.identities = identities
        self.serial = 0
        self.ids = {}
        self.ordinary_by_owner = {}

    def arm(self):
        self.probe.send("lifecycle.arm")
        self.probe.wait("lifecycle.armed")

    def execute(self, command):
        self.probe.send("commands.execute", {"commands": [command]})
        return self.probe.wait("commands.executed")["data"]

    def snapshot(self):
        self.probe.send("navigation.snapshot")
        return self.probe.wait("navigation.snapshot")["data"]

    def monitor(self):
        self.probe.send("lifecycle.snapshot")
        return self.probe.wait("lifecycle.snapshot")["data"]

    def probe_container(self, name):
        self.probe.send("protection.container.snapshot", {"player": name})
        return self.probe.wait("protection.container.snapshot")["data"]

    def actions(self, values):
        for name in self.names:
            self.serial += 1
            self.driver.send("action", {"actionId": f"container-behavior-{self.serial}", "bot": name, **values[name]})
        return [self.driver.wait("action_result", 30) for _ in self.names]

    def packets(self):
        self.driver.send("barrier")
        value = self.driver.wait("packet_summary", 30)
        rate = self.driver.wait("rate_sample", 30)
        if rate.get("pending") or rate.get("failed"):
            raise ValueError("native protection action barrier incomplete")
        return value["effects"]

    def prepare_navigation(self):
        for index, name in enumerate(self.names):
            position = chest_position(index)
            self.execute(f"tp {name} {position['x']} -61 300")
            self.execute(f"give {name} minecraft:stone 64")
            self.execute(f"give {name} minecraft:diamond 3")
            self.execute(f"execute as {name} run wp navigate minecraft:overworld protection_fixture target all")
        before = self.snapshot()
        if len(before.get("players", [])) != len(self.names):
            raise ValueError("ordinary fixture players missing")
        for player in before["players"]:
            if sum(player.get("ordinary", {}).values()) != 67:
                raise ValueError("ordinary fixture count missing")
            self.ordinary_by_owner[player["name"]] = player["ordinary"]
            self.ids[player["name"]] = next(item["mapState"]["id"] for item in player["items"] if item.get("method") == "map")

    def owners(self, native, client):
        return [
            restored_session(
                {"uuid": self.identities[player["name"]], "mapId": self.ids[player["name"]], "methods": METHODS, "target": EXPECTED_TARGET},
                player, client[player["name"]],
            )
            for player in native["players"]
        ]

    def remove_chests(self):
        decisions = []
        for name in self.names:
            self.probe.send("protection.container.remove", {"player": name})
            decisions.append(removal_decision(self.probe.wait("protection.container.removed")["data"]))
        return decisions

    def container_cases(self):
        decisions = []
        chests = {name: chest_position(index) for index, name in enumerate(self.names)}
        for case in container_cases():
            self.arm()
            start = len(self.monitor().get("events", []))
            for name in self.names:
                self.probe.send("protection.container.place", {"player": name, "kind": case["kind"], "method": case["method"], **chests[name]})
                placed = self.probe.wait("protection.container.placed")["data"]
                if placed.get("inventoryType") != "CHEST":
                    raise ValueError("native other-container fixture is not a chest")
            before = {name: self.probe_container(name) for name in self.names}
            self.actions({name: {"operation": "open-container", "hold": case["hold"], **chests[name]} for name in self.names})
            held = case["hold"]
            if case.get("inject"):
                for name in self.names:
                    self.probe.send("protection.container.dirty", {"player": name, "method": case["method"], "inject": case["inject"]})
                    before[name] = self.probe.wait("protection.container.dirtied")["data"]
                self.actions({name: {"operation": "close-container"} for name in self.names})
                held = False
            after = {name: self.probe_container(name) for name in self.names}
            observed = self.monitor()
            if observed.get("droppedObservations"):
                raise ValueError("native other-container receipts overflowed")
            client = self.packets()
            decisions.extend(self.owners(self.snapshot(), client))
            for name in self.names:
                recipe = container_recipe(case, before[name])
                sample = {
                    "trigger": case["trigger"],
                    "before": {**before[name]["top"], "inventoryType": "CHEST", "cursorProtectedItems": before[name].get("cursor", {}).get("protectedItems", 0)},
                    "after": {**after[name]["top"], "inventoryType": "CHEST", "cursorProtectedItems": after[name].get("cursor", {}).get("protectedItems", 0)},
                    "world": observed["worldItems"],
                    "graphs": after[name]["graphs"],
                    "expectedGraphs": before[name]["expectedGraphs"],
                    "receipts": [event for event in observed["events"][start:] if event.get("uuid") == self.identities[name]],
                }
                decisions.append(container_cleanup(recipe, sample))
            if held:
                self.actions({name: {"operation": "close-container"} for name in self.names})
            decisions.extend(self.remove_chests())
        return decisions

    def drag_cases(self):
        decisions = []
        chests = {name: chest_position(index) for index, name in enumerate(self.names)}
        for case in drag_cases():
            self.arm()
            start = len(self.monitor().get("events", []))
            chest = case["inventoryType"] == "CHEST"
            if chest:
                kind = "empty" if case["label"] == "ordinary-chest-control" else "ordinary"
                for name in self.names:
                    self.probe.send("protection.container.place", {"player": name, "kind": kind, "method": "compass", **chests[name]})
                    placed = self.probe.wait("protection.container.placed")["data"]
                    if placed.get("inventoryType") != "CHEST":
                        raise ValueError("native drag chest fixture is not a chest")
                self.actions({name: {"operation": "open-container", "hold": True, **chests[name]} for name in self.names})
            if case["expectedBeforeCursorProtected"]:
                for name in self.names:
                    self.probe.send("protection.drag.cursor", {"player": name, "amount": case["expectedBeforeCursorProtected"]})
                    cursor = self.probe.wait("protection.drag.cursor-set")["data"]
                    if cursor.get("cursor", {}).get("protectedItems") != case["expectedBeforeCursorProtected"]:
                        raise ValueError("stackable tagged drag cursor missing")
            else:
                observed = self.monitor()
                sources = {name: player_raw_slot(self._ordinary_slot(observed, name, "minecraft:stone"), case["inventoryType"]) for name in self.names}
                self.actions({name: {"operation": "pickup", "from": sources[name]} for name in self.names})
            before_monitor = self.monitor()
            placed = {name: self.probe_container(name) for name in self.names} if chest else {}
            before = {}
            for name in self.names:
                cursor = before_monitor.get("inventories", {}).get(name, {}).get("cursor", {})
                if chest:
                    top = placed[name]["top"]
                    before[name] = {
                        "ordinary": dict(placed[name]["expectedTop"]["ordinary"]), "protectedItems": top.get("protectedItems", 0),
                        "nestedProtectedCarriers": top.get("nestedProtectedCarriers", 0), "cursorProtectedItems": cursor.get("protectedItems", 0),
                        "inventoryType": "CHEST",
                    }
                else:
                    before[name] = {
                        "ordinary": dict(self.ordinary_by_owner[name]), "protectedItems": 1, "nestedProtectedCarriers": 0,
                        "cursorProtectedItems": cursor.get("protectedItems", 0), "inventoryType": "CRAFTING",
                    }
            self.actions({name: {"operation": "drag", "slots": list(case["slots"]), "kind": "left", "hold": True} for name in self.names})
            after_native = self.snapshot()
            after_monitor = self.monitor()
            after_placed = {name: self.probe_container(name) for name in self.names} if chest else {}
            if after_monitor.get("droppedObservations"):
                raise ValueError("native inventory-drag receipts overflowed")
            decisions.extend(self.owners(after_native, self.packets()))
            for name in self.names:
                player = next(value for value in after_native["players"] if value["name"] == name)
                if case["label"] == "ordinary-chest-control":
                    ordinary = {key: value for key, value in self.ordinary_by_owner[name].items() if key.startswith("minecraft:stone:")}
                elif chest:
                    ordinary = dict(before[name]["ordinary"])
                else:
                    ordinary = dict(self.ordinary_by_owner[name])
                recipe = {
                    **{key: case[key] for key in (
                        "label", "inventoryType", "slots", "cancelled", "expectedBeforeCursorProtected",
                        "expectedAfterCursorProtected", "expectedAfterTopProtected", "expectedAfterInvalidProtected")},
                    "ordinary": ordinary,
                }
                top = after_placed[name]["top"] if chest else {}
                after = {
                    "ordinary": top.get("ordinary", player["ordinary"]),
                    "protectedItems": top.get("protectedItems", sum(item["amount"] for item in player["items"])),
                    "nestedProtectedCarriers": top.get("nestedProtectedCarriers", 0),
                    "inventoryType": case["inventoryType"],
                    "cursorProtectedItems": after_monitor.get("inventories", {}).get(name, {}).get("cursor", {}).get("protectedItems", 0),
                    "protectedInInvalidLocations": self._invalid_protected([player], top if chest else None),
                }
                sample = {
                    "before": before[name], "after": after, "world": after_monitor["worldItems"],
                    "receipts": [event for event in after_monitor["events"][start:] if event.get("uuid") == self.identities[name]],
                }
                decisions.append(drag_outcome(recipe, sample))
            self.actions({name: {"operation": "close-container"} for name in self.names})
            if chest:
                decisions.extend(self.remove_chests())
        return decisions

    def place_empty(self):
        for index, name in enumerate(self.names):
            self.probe.send("protection.container.place", {"player": name, "kind": "empty", "method": "compass", **chest_position(index)})
            placed = self.probe.wait("protection.container.placed")["data"]
            if placed.get("inventoryType") != "CHEST":
                raise ValueError("native timing chest is not a chest")

    def _ordinary_slot(self, observed, name, material):
        for entry in observed.get("inventories", {}).get(name, {}).get("slots", []):
            if entry.get("material") == material:
                return entry["slot"]
        raise ValueError("ordinary source slot missing: " + material)

    @staticmethod
    def _invalid_protected(native_players, chest_top=None):
        count = 0
        for player in native_players:
            count += sum(1 for item in player["items"] if item["slot"] >= 36 and item["slot"] != 40)
        if chest_top is not None:
            count += chest_top.get("protectedItems", 0) + chest_top.get("nestedProtectedCarriers", 0)
        return count
