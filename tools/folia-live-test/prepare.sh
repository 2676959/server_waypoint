#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIRECTORY/../.." && pwd)"
ENVIRONMENT_ROOT=""
FOLIA_JAR="${SERVER_WAYPOINT_FOLIA_JAR:-}"
MCC_EXECUTABLE="${SERVER_WAYPOINT_MCC_EXECUTABLE:-}"
SERVER_PORT="25611"

usage() {
    printf '%s\n' \
        "Usage: prepare.sh --folia-jar <path> --mcc <path> [--root <new-directory>] [--port <port>]" \
        "" \
        "Creates a new disposable environment. If --root is omitted, mktemp chooses it." \
        "The Folia JAR and MCC executable must already exist; this script never downloads them."
}

while (($# > 0)); do
    case "$1" in
        --folia-jar)
            FOLIA_JAR="$2"
            shift 2
            ;;
        --mcc)
            MCC_EXECUTABLE="$2"
            shift 2
            ;;
        --root)
            ENVIRONMENT_ROOT="$2"
            shift 2
            ;;
        --port)
            SERVER_PORT="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown option: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -z "$FOLIA_JAR" || ! -f "$FOLIA_JAR" ]]; then
    printf 'Provide an existing Folia JAR with --folia-jar.\n' >&2
    exit 2
fi
if [[ -z "$MCC_EXECUTABLE" || ! -f "$MCC_EXECUTABLE" || ! -x "$MCC_EXECUTABLE" ]]; then
    printf 'Provide an existing executable MinecraftClient with --mcc.\n' >&2
    exit 2
fi
if [[ ! "$SERVER_PORT" =~ ^[0-9]+$ ]] || ((SERVER_PORT < 1024 || SERVER_PORT > 65535)); then
    printf 'Port must be an integer between 1024 and 65535.\n' >&2
    exit 2
fi

FOLIA_JAR="$(cd -- "$(dirname -- "$FOLIA_JAR")" && pwd)/$(basename -- "$FOLIA_JAR")"
MCC_EXECUTABLE="$(cd -- "$(dirname -- "$MCC_EXECUTABLE")" && pwd)/$(basename -- "$MCC_EXECUTABLE")"
if [[ -z "$ENVIRONMENT_ROOT" ]]; then
    ENVIRONMENT_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/server-waypoint-folia-4a.XXXXXX")"
else
    case "$ENVIRONMENT_ROOT" in
        "$REPOSITORY_ROOT"|"$REPOSITORY_ROOT"/*)
            printf 'The live-test root must be outside the repository.\n' >&2
            exit 2
            ;;
    esac
    if [[ -e "$ENVIRONMENT_ROOT" ]]; then
        printf 'Refusing to reuse an existing path: %s\n' "$ENVIRONMENT_ROOT" >&2
        exit 2
    fi
    mkdir -p -- "$ENVIRONMENT_ROOT"
    ENVIRONMENT_ROOT="$(cd -- "$ENVIRONMENT_ROOT" && pwd)"
fi

printf 'Building Minecraft 1.21.11 production artifacts...\n'
(
    cd -- "$REPOSITORY_ROOT"
    ./gradlew --no-daemon --no-parallel --max-workers=2 \
        :paper:1.21.11-paper:shadowJar \
        :mods:1.21.11-fabric:build
)

printf 'Building development-only live-test tools and fixtures...\n'
(
    cd -- "$REPOSITORY_ROOT"
    ./gradlew --no-daemon --no-parallel --max-workers=2 \
        :paper:1.21.11-paper:foliaLiveTestLoadJar \
        :mods:1.21.11-fabric:compileFoliaLiveTestProbeJava \
        :common:generateFoliaLiveTestFixtures \
        -PfoliaLiveTestFixtureDir="$ENVIRONMENT_ROOT/fixtures"
)

SERVER_DIRECTORY="$ENVIRONMENT_ROOT/server"
mkdir -p -- \
    "$SERVER_DIRECTORY/plugins/ServerWaypoint/waypoints" \
    "$ENVIRONMENT_ROOT/artifacts" \
    "$ENVIRONMENT_ROOT/clients/alpha" \
    "$ENVIRONMENT_ROOT/clients/bravo" \
    "$ENVIRONMENT_ROOT/clients/probe" \
    "$ENVIRONMENT_ROOT/clients/mcc" \
    "$ENVIRONMENT_ROOT/evidence" \
    "$ENVIRONMENT_ROOT/logs"

PAPER_PLUGIN="$(find "$REPOSITORY_ROOT/paper/versions/1.21.11-paper/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name 'server-waypoint-folia-live-test-load*.jar' | sort | tail -1)"
FABRIC_CLIENT="$(find "$REPOSITORY_ROOT/mods/versions/1.21.11-fabric/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*dev-shadow*' ! -name '*sources*' | sort | tail -1)"
LOAD_PLUGIN="$(find "$REPOSITORY_ROOT/paper/versions/1.21.11-paper/build/libs" -maxdepth 1 -type f -name 'server-waypoint-folia-live-test-load*.jar' | sort | tail -1)"
if [[ -z "$PAPER_PLUGIN" || -z "$FABRIC_CLIENT" || -z "$LOAD_PLUGIN" ]]; then
    printf 'Could not resolve one or more newly built artifacts.\n' >&2
    exit 1
fi

cp -- "$FOLIA_JAR" "$SERVER_DIRECTORY/folia.jar"
cp -- "$PAPER_PLUGIN" "$SERVER_DIRECTORY/plugins/ServerWaypoint.jar"
cp -- "$LOAD_PLUGIN" "$SERVER_DIRECTORY/plugins/ServerWaypointFoliaLiveTestLoad.jar"
cp -- "$FABRIC_CLIENT" "$ENVIRONMENT_ROOT/artifacts/"
cp -- "$ENVIRONMENT_ROOT/fixtures/minecraft\$overworld.json" \
    "$SERVER_DIRECTORY/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json"
cp -- "$ENVIRONMENT_ROOT/fixtures/ops.json" "$SERVER_DIRECTORY/ops.json"

printf 'eula=true\n' > "$SERVER_DIRECTORY/eula.txt"
{
    printf 'server-port=%s\n' "$SERVER_PORT"
    printf 'server-ip=127.0.0.1\n'
    printf 'online-mode=false\n'
    printf 'enforce-secure-profile=false\n'
    printf 'white-list=false\n'
    printf 'spawn-protection=0\n'
    printf 'level-name=folia-live-test-world\n'
    printf 'motd=Disposable Server Waypoint Folia 4A\n'
    printf 'view-distance=6\n'
    printf 'simulation-distance=4\n'
} > "$SERVER_DIRECTORY/server.properties"

ENVIRONMENT_FILE="$ENVIRONMENT_ROOT/environment.env"
{
    printf 'REPOSITORY_ROOT=%q\n' "$REPOSITORY_ROOT"
    printf 'ENVIRONMENT_ROOT=%q\n' "$ENVIRONMENT_ROOT"
    printf 'SERVER_DIRECTORY=%q\n' "$SERVER_DIRECTORY"
    printf 'SERVER_HOST=%q\n' "127.0.0.1"
    printf 'SERVER_PORT=%q\n' "$SERVER_PORT"
    printf 'MCC_EXECUTABLE=%q\n' "$MCC_EXECUTABLE"
    printf 'PAPER_PLUGIN=%q\n' "$PAPER_PLUGIN"
    printf 'FABRIC_CLIENT=%q\n' "$FABRIC_CLIENT"
    printf 'LOAD_PLUGIN=%q\n' "$LOAD_PLUGIN"
} > "$ENVIRONMENT_FILE"

{
    printf 'prepared_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'repository_commit=%s\n' "$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
    printf 'repository_status_begin\n'
    git -C "$REPOSITORY_ROOT" status --short
    printf 'repository_status_end\n'
    printf 'java_version_begin\n'
    java -version 2>&1
    printf 'java_version_end\n'
    printf 'folia_sha256=%s\n' "$(shasum -a 256 "$SERVER_DIRECTORY/folia.jar" | awk '{print $1}')"
    printf 'paper_plugin_sha256=%s\n' "$(shasum -a 256 "$SERVER_DIRECTORY/plugins/ServerWaypoint.jar" | awk '{print $1}')"
    printf 'fabric_client_sha256=%s\n' "$(shasum -a 256 "$ENVIRONMENT_ROOT/artifacts/$(basename -- "$FABRIC_CLIENT")" | awk '{print $1}')"
    printf 'load_plugin_sha256=%s\n' "$(shasum -a 256 "$SERVER_DIRECTORY/plugins/ServerWaypointFoliaLiveTestLoad.jar" | awk '{print $1}')"
    printf 'fixture_sha256=%s\n' "$(shasum -a 256 "$ENVIRONMENT_ROOT/fixtures/minecraft\$overworld.json" | awk '{print $1}')"
    printf 'folia_manifest_begin\n'
    unzip -p "$SERVER_DIRECTORY/folia.jar" META-INF/MANIFEST.MF 2>/dev/null || true
    printf '\nfolia_manifest_end\n'
} > "$ENVIRONMENT_ROOT/evidence/environment.txt"

{
    printf 'role\tusername\tdimension\tx\ty\tz\tgame_directory\n'
    printf 'compatible-alpha\tSWAlpha\tminecraft:overworld\t0\t80\t0\t%s\n' "$ENVIRONMENT_ROOT/clients/alpha"
    printf 'compatible-bravo\tSWBravo\tminecraft:overworld\t8192\t80\t8192\t%s\n' "$ENVIRONMENT_ROOT/clients/bravo"
    printf 'protocol-probe\tSWProbe\tminecraft:overworld\t16384\t80\t0\t%s\n' "$ENVIRONMENT_ROOT/clients/probe"
    printf 'incompatible-mcc\tSWVanilla\tminecraft:overworld\t-8192\t80\t-8192\t%s\n' "$ENVIRONMENT_ROOT/clients/mcc"
} > "$ENVIRONMENT_ROOT/evidence/client-roles.tsv"

touch "$ENVIRONMENT_ROOT/.server-waypoint-folia-live-test"
printf '\nPrepared disposable environment: %s\n' "$ENVIRONMENT_ROOT"
printf 'Next: %s/run.sh %s server\n' "$SCRIPT_DIRECTORY" "$ENVIRONMENT_ROOT"
printf 'Runbook: %s/RUNBOOK.md\n' "$SCRIPT_DIRECTORY"
