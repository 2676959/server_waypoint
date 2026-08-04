import json
import math
import sys
from pathlib import Path


# Constants used by the MCDR plugins Locations and LocationMarker.
LOCATION_NAME_KEY = "name"
LOCATION_DESCRIPTION_KEY = "desc"
LOCATION_DIMENSION_KEY = "dim"
LOCATION_POSITION_KEY = "pos"
LOCATION_DIMENSION_ID_MAP = {
    0: "minecraft:overworld",
    -1: "minecraft:the_nether",
    1: "minecraft:the_end",
}

# Constants for waypoint files used by Server Waypoint.
WAYPOINT_LIST_NAME = "locations"
WAYPOINT_LIST_NAME_KEY = "list_name"
WAYPOINT_SYNC_NUMBER_KEY = "n"
WAYPOINTS_KEY = "waypoints"
WAYPOINT_INITIALS_KEY = "initials"
WAYPOINT_NAME_KEY = "name"
WAYPOINT_POSITION_KEY = "pos"
WAYPOINT_COLOR_KEY = "color"
WAYPOINT_YAW_KEY = "yaw"
WAYPOINT_GLOBAL_KEY = "global"
WAYPOINT_KEYWORDS_KEY = "keywords"
WAYPOINT_DESCRIPTION_KEY = "description"

DEFAULT_COLOR = "#39C5BB"


def get_dim_key(dim):
    return LOCATION_DIMENSION_ID_MAP.get(dim, dim)


def check_path(path):
    if path.is_file():
        return True
    print("Invalid path: {}".format(path), file=sys.stderr)
    return False


def get_block_position(position):
    if not isinstance(position, dict):
        raise ValueError("position must be an object")

    coordinates = []
    for axis in ("x", "y", "z"):
        value = position.get(axis)
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError("{} coordinate must be a number".format(axis))
        if not math.isfinite(value):
            raise ValueError("{} coordinate must be finite".format(axis))
        coordinates.append(math.floor(value))
    return coordinates


def convert_to_waypoints(locations):
    if not isinstance(locations, list):
        raise ValueError("the locations file must contain a JSON array")

    waypoints_by_dimension = {}
    names_by_dimension = {}

    for index, location in enumerate(locations):
        if not isinstance(location, dict):
            print(
                "Skipping location {}: entry must be an object".format(index + 1),
                file=sys.stderr,
            )
            continue

        name = location.get(LOCATION_NAME_KEY)
        if not isinstance(name, str) or not name.strip():
            print(
                "Skipping location {}: missing or invalid name".format(index + 1),
                file=sys.stderr,
            )
            continue
        name = name.strip()

        dimension = get_dim_key(location.get(LOCATION_DIMENSION_KEY))
        if not isinstance(dimension, str) or not dimension:
            print(
                "Skipping location {!r}: missing or invalid dimension".format(name),
                file=sys.stderr,
            )
            continue

        try:
            position = get_block_position(location.get(LOCATION_POSITION_KEY))
        except ValueError as error:
            print(
                "Skipping location {!r}: {}".format(name, error),
                file=sys.stderr,
            )
            continue

        dimension_names = names_by_dimension.setdefault(dimension, set())
        if name in dimension_names:
            print(
                "Skipping duplicate location {!r} in dimension {!r}".format(
                    name, dimension
                ),
                file=sys.stderr,
            )
            continue
        dimension_names.add(name)

        description = location.get(LOCATION_DESCRIPTION_KEY, "")
        if description is None:
            description = ""
        elif not isinstance(description, str):
            description = str(description)

        waypoint = {
            WAYPOINT_NAME_KEY: name,
            WAYPOINT_INITIALS_KEY: name[0].upper(),
            WAYPOINT_POSITION_KEY: position,
            WAYPOINT_COLOR_KEY: DEFAULT_COLOR,
            WAYPOINT_YAW_KEY: 0,
            WAYPOINT_GLOBAL_KEY: True,
            WAYPOINT_KEYWORDS_KEY: [],
            WAYPOINT_DESCRIPTION_KEY: description,
        }
        waypoints_by_dimension.setdefault(dimension, []).append(waypoint)

    return {
        dimension: [
            {
                WAYPOINT_LIST_NAME_KEY: WAYPOINT_LIST_NAME,
                WAYPOINT_SYNC_NUMBER_KEY: 1,
                WAYPOINTS_KEY: waypoints,
            }
        ]
        for dimension, waypoints in waypoints_by_dimension.items()
    }


def get_dimension_file_name(dimension):
    return dimension.replace("/", "%").replace(":", "$") + ".json"


def write_waypoint_files(source_path, waypoints_by_dimension):
    output_directory = source_path.parent / "waypoints"
    output_paths = [
        output_directory / get_dimension_file_name(dimension)
        for dimension in waypoints_by_dimension
    ]
    existing_paths = [path for path in output_paths if path.exists()]
    if existing_paths:
        raise OSError(
            "refusing to overwrite existing waypoint file(s): {}".format(
                ", ".join(str(path) for path in existing_paths)
            )
        )

    output_directory.mkdir(parents=True, exist_ok=True)
    for dimension, waypoint_lists in waypoints_by_dimension.items():
        output_path = output_directory / get_dimension_file_name(dimension)
        with output_path.open("x", encoding="utf-8") as output_file:
            json.dump(
                waypoint_lists,
                output_file,
                ensure_ascii=False,
                indent=2,
            )
            output_file.write("\n")

    return output_paths


def convert_file(file_path):
    try:
        with file_path.open("r", encoding="utf-8") as locations_file:
            locations = json.load(locations_file)
        waypoint_files = write_waypoint_files(
            file_path, convert_to_waypoints(locations)
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("Failed to convert {}: {}".format(file_path, error), file=sys.stderr)
        return False

    if waypoint_files:
        for waypoint_file in waypoint_files:
            print("Created {}".format(waypoint_file))
    else:
        print("No valid locations found; no waypoint files were created.")
    return True


def main():
    if len(sys.argv) >= 2:
        file_path = Path(sys.argv[1]).expanduser()
        return 0 if check_path(file_path) and convert_file(file_path) else 1

    while True:
        try:
            entered_path = input("Drop locations.json here: ")
        except (EOFError, KeyboardInterrupt):
            print()
            return 1

        file_path = Path(entered_path.strip().strip('"')).expanduser()
        if check_path(file_path) and convert_file(file_path):
            return 0


if __name__ == "__main__":
    sys.exit(main())
