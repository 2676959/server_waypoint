#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIRECTORY/.." && pwd)"
ARTIFACT_DIRECTORY="${1:-$REPOSITORY_ROOT/builds}"
EXPECTED_TOTAL=42
EXPECTED_FABRIC=13
EXPECTED_FORGE=12
EXPECTED_NEOFORGE=12
EXPECTED_PAPER=4
EXPECTED_VELOCITY=1

if [[ ! -d "$ARTIFACT_DIRECTORY" ]]; then
    printf 'Release artifact directory does not exist: %s\n' "$ARTIFACT_DIRECTORY" >&2
    exit 1
fi

MOD_VERSION="$(sed -n 's/^mod_version=//p' "$REPOSITORY_ROOT/gradle.properties")"
if [[ -z "$MOD_VERSION" ]]; then
    printf 'Could not determine mod_version from gradle.properties.\n' >&2
    exit 1
fi

# Verify exact target ranges as well as counts, so a renamed/duplicate target cannot fill a gap.
EXPECTED_NAMES=("server_waypoint-$MOD_VERSION-velocity.jar")
# "<jar name>|<loader>|<Stonecutter version>" for the content check. Bash 3.2 has no associative arrays.
EXPECTED_TARGETS=("server_waypoint-$MOD_VERSION-velocity.jar|velocity|-")
for properties in "$REPOSITORY_ROOT"/{mods,paper}/versions/*/gradle.properties; do
    target="$(basename -- "$(dirname -- "$properties")")"
    case "$target" in 1.21.3-fabric|1.21.3-neoforge) continue ;; esac
    loader="${target##*-}"
    range="$(sed -n 's/^mcVersionRange[[:space:]]*=[[:space:]]*//p' "$properties" | tr -d '\r')"
    if [[ -z "$range" ]]; then
        printf 'Missing Minecraft release range: %s\n' "$properties" >&2
        exit 1
    fi
    name="server_waypoint-$MOD_VERSION-$loader-mc$range.jar"
    EXPECTED_NAMES+=("$name")
    EXPECTED_TARGETS+=("$name|$loader|${target%-*}")
done

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
VELOCITY_COUNT=0
ARTIFACT_NAMES=()

for artifact in "${RELEASE_ARTIFACTS[@]}"; do
    artifact_name="$(basename -- "$artifact")"
    ARTIFACT_NAMES+=("$artifact_name")
    if ! printf '%s\n' "${EXPECTED_NAMES[@]}" | grep -Fxq "$artifact_name"; then
        printf 'Artifact does not match a supported target: %s\n' "$artifact_name" >&2
        exit 1
    fi

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
        server_waypoint-"$MOD_VERSION"-velocity.jar)
            VELOCITY_COUNT=$((VELOCITY_COUNT + 1))
            ;;
        *)
            printf 'Unexpected release artifact name: %s\n' "$artifact_name" >&2
            exit 1
            ;;
    esac

    artifact_entries="$(jar tf "$artifact")"
    if grep -Eiq \
            'proxyLifecycleTest|ProxyLifecycleTest|server_waypoint-proxy-lifecycle-test|foliaLiveTestProbe|CrossServerGuiProbe|TeleportAudit|NoisePlatformProbe|FoliaRegionLoadPlugin|headlessmc|(^|/)org/junit|(^|/)junit/' \
            <<< "$artifact_entries"; then
        printf 'Development or test content entered release artifact: %s\n' "$artifact" >&2
        exit 1
    fi
    if grep -q '^com/southernstorm/noise/' <<< "$artifact_entries"; then
        printf 'Unrelocated Noise classes in %s\n' "$artifact" >&2
        exit 1
    fi
    for required in '_959/server_waypoint/internal/noisekk/protocol/HandshakeState.class' 'META-INF/LICENSE-noise-java'; do
        if ! grep -Fxq "$required" <<< "$artifact_entries"; then
            printf 'Missing %s in %s\n' "$required" "$artifact" >&2
            exit 1
        fi
    done

    for record in "${EXPECTED_TARGETS[@]}"; do
        if [[ "${record%%|*}" == "$artifact_name" ]]; then
            IFS='|' read -r _ loader version <<< "$record"
            break
        fi
    done
    if ! python3 "$SCRIPT_DIRECTORY/check_release_jar.py" "$artifact" "$loader" "$version"; then
        printf 'Release content check failed: %s\n' "$artifact" >&2
        exit 1
    fi
done

DUPLICATE_NAMES="$(printf '%s\n' "${ARTIFACT_NAMES[@]}" | sort | uniq -d)"
if [[ -n "$DUPLICATE_NAMES" ]]; then
    printf 'Duplicate release artifact names:\n%s\n' "$DUPLICATE_NAMES" >&2
    exit 1
fi

if [[ "$(printf '%s\n' "${EXPECTED_NAMES[@]}" | sort)" != "$(printf '%s\n' "${ARTIFACT_NAMES[@]}" | sort)" ]]; then
    printf 'Release artifacts do not match the supported target set.\n' >&2
    exit 1
fi

if ((FABRIC_COUNT != EXPECTED_FABRIC
        || FORGE_COUNT != EXPECTED_FORGE
        || NEOFORGE_COUNT != EXPECTED_NEOFORGE
        || PAPER_COUNT != EXPECTED_PAPER
        || VELOCITY_COUNT != EXPECTED_VELOCITY)); then
    printf 'Unexpected loader distribution: Fabric=%d Forge=%d NeoForge=%d Paper=%d Velocity=%d.\n' \
        "$FABRIC_COUNT" "$FORGE_COUNT" "$NEOFORGE_COUNT" "$PAPER_COUNT" "$VELOCITY_COUNT" >&2
    exit 1
fi

printf 'Verified %d release JARs for Server Waypoint %s: Fabric=%d Forge=%d NeoForge=%d Paper=%d Velocity=%d.\n' \
    "${#RELEASE_ARTIFACTS[@]}" "$MOD_VERSION" \
    "$FABRIC_COUNT" "$FORGE_COUNT" "$NEOFORGE_COUNT" "$PAPER_COUNT" "$VELOCITY_COUNT"
