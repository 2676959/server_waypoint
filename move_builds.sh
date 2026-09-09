#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$SCRIPT_DIR/builds}"
MOD_VERSION="$(sed -n 's/^mod_version=//p' "$SCRIPT_DIR/gradle.properties")"
[[ -n "$MOD_VERSION" ]] || { echo 'Missing mod_version' >&2; exit 1; }
mkdir -p "$OUTPUT_DIR"

# Collect exact final names. Loader intermediates (including Forge jarjar-input) never qualify.
for properties in "$SCRIPT_DIR"/{mods,paper}/versions/*/gradle.properties; do
    version_dir="$(dirname -- "$properties")"
    target="$(basename -- "$version_dir")"
    case "$target" in 1.21.3-fabric|1.21.3-neoforge) continue ;; esac
    loader="${target##*-}"
    range="$(sed -n 's/^mcVersionRange[[:space:]]*=[[:space:]]*//p' "$properties" | tr -d '\r')"
    [[ -n "$range" ]] || { echo "Missing release range: $properties" >&2; exit 1; }
    artifact="$version_dir/build/libs/server_waypoint-$MOD_VERSION-$loader-mc$range.jar"
    [[ -f "$artifact" ]] || { echo "Missing release artifact: $artifact" >&2; exit 1; }
    cp "$artifact" "$OUTPUT_DIR/"
done

artifact="$SCRIPT_DIR/velocity/build/libs/server_waypoint-$MOD_VERSION-velocity.jar"
[[ -f "$artifact" ]] || { echo "Missing release artifact: $artifact" >&2; exit 1; }
cp "$artifact" "$OUTPUT_DIR/"
printf 'Release artifacts copied to %s\n' "$OUTPUT_DIR"
