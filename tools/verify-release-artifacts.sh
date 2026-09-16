#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIRECTORY/.." && pwd)"
ARTIFACT_DIRECTORY="${1:-$REPOSITORY_ROOT/builds}"
EXPECTED_TOTAL=38
EXPECTED_FABRIC=12
EXPECTED_FORGE=12
EXPECTED_NEOFORGE=11
EXPECTED_PAPER=3

if [[ ! -d "$ARTIFACT_DIRECTORY" ]]; then
    printf 'Release artifact directory does not exist: %s\n' "$ARTIFACT_DIRECTORY" >&2
    exit 1
fi

MOD_VERSION="$(sed -n 's/^mod_version=//p' "$REPOSITORY_ROOT/gradle.properties")"
if [[ -z "$MOD_VERSION" ]]; then
    printf 'Could not determine mod_version from gradle.properties.\n' >&2
    exit 1
fi

RELEASE_ARTIFACTS=()
while IFS= read -r -d '' artifact; do
    RELEASE_ARTIFACTS+=("$artifact")
done < <(find "$ARTIFACT_DIRECTORY" -type f -name '*.jar' -print0 | sort -z)

if ((${#RELEASE_ARTIFACTS[@]} != EXPECTED_TOTAL)); then
    printf 'Expected %d release JARs, found %d.\n' \
        "$EXPECTED_TOTAL" "${#RELEASE_ARTIFACTS[@]}" >&2
    printf '%s\n' "${RELEASE_ARTIFACTS[@]}" >&2
    exit 1
fi

FABRIC_COUNT=0
FORGE_COUNT=0
NEOFORGE_COUNT=0
PAPER_COUNT=0
ARTIFACT_NAMES=()

for artifact in "${RELEASE_ARTIFACTS[@]}"; do
    artifact_name="$(basename -- "$artifact")"
    ARTIFACT_NAMES+=("$artifact_name")

    case "$artifact_name" in
        server_waypoint-"$MOD_VERSION"-fabric-mc*.jar)
            FABRIC_COUNT=$((FABRIC_COUNT + 1))
            ;;
        server_waypoint-"$MOD_VERSION"-forge-mc*.jar)
            FORGE_COUNT=$((FORGE_COUNT + 1))
            ;;
        server_waypoint-"$MOD_VERSION"-neoforge-mc*.jar)
            NEOFORGE_COUNT=$((NEOFORGE_COUNT + 1))
            ;;
        server_waypoint-"$MOD_VERSION"-paper-mc*.jar)
            PAPER_COUNT=$((PAPER_COUNT + 1))
            ;;
        *)
            printf 'Unexpected release artifact name: %s\n' "$artifact_name" >&2
            exit 1
            ;;
    esac

    artifact_entries="$(jar tf "$artifact")"
    if grep -Eiq \
            'proxyLifecycleTest|ProxyLifecycleTest|server_waypoint-proxy-lifecycle-test|foliaLiveTestProbe|FoliaRegionLoadPlugin|headlessmc|(^|/)org/junit|(^|/)junit/' \
            <<< "$artifact_entries"; then
        printf 'Development or test content entered release artifact: %s\n' "$artifact" >&2
        exit 1
    fi
done

DUPLICATE_NAMES="$(printf '%s\n' "${ARTIFACT_NAMES[@]}" | sort | uniq -d)"
if [[ -n "$DUPLICATE_NAMES" ]]; then
    printf 'Duplicate release artifact names:\n%s\n' "$DUPLICATE_NAMES" >&2
    exit 1
fi

if ((FABRIC_COUNT != EXPECTED_FABRIC
        || FORGE_COUNT != EXPECTED_FORGE
        || NEOFORGE_COUNT != EXPECTED_NEOFORGE
        || PAPER_COUNT != EXPECTED_PAPER)); then
    printf 'Unexpected loader distribution: Fabric=%d Forge=%d NeoForge=%d Paper=%d.\n' \
        "$FABRIC_COUNT" "$FORGE_COUNT" "$NEOFORGE_COUNT" "$PAPER_COUNT" >&2
    exit 1
fi

printf 'Verified %d release JARs for Server Waypoint %s: Fabric=%d Forge=%d NeoForge=%d Paper=%d.\n' \
    "${#RELEASE_ARTIFACTS[@]}" "$MOD_VERSION" \
    "$FABRIC_COUNT" "$FORGE_COUNT" "$NEOFORGE_COUNT" "$PAPER_COUNT"
