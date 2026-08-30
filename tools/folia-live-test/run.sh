#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    printf '%s\n' \
        "Usage: run.sh <environment-root> <server|alpha|bravo|probe|mcc|verify|record> [probe-mode] [selected-frame]" \
        "Probe modes: valid, partial, bad-checksum, bad-header, saturate, disconnect"
}

if (($# < 2)); then
    usage >&2
    exit 2
fi

ENVIRONMENT_ROOT="$1"
ACTION="$2"
ENVIRONMENT_FILE="$ENVIRONMENT_ROOT/environment.env"
if [[ ! -f "$ENVIRONMENT_FILE" || ! -f "$ENVIRONMENT_ROOT/.server-waypoint-folia-live-test" ]]; then
    printf 'Not a prepared Server Waypoint Folia live-test environment: %s\n' "$ENVIRONMENT_ROOT" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "$ENVIRONMENT_FILE"

timestamp() {
    date -u +%Y%m%dT%H%M%SZ
}

run_compatible_client() {
    local role="$1"
    local username="$2"
    local game_directory="$ENVIRONMENT_ROOT/clients/$role"
    cd -- "$REPOSITORY_ROOT"
    exec ./gradlew --no-daemon --no-parallel --max-workers=2 \
        :mods:1.21.11-fabric:runClient \
        -PfoliaLiveTestGameDir="$game_directory" \
        -PfoliaLiveTestUsername="$username" \
        -PfoliaLiveTestHost="$SERVER_HOST" \
        -PfoliaLiveTestPort="$SERVER_PORT"
}

case "$ACTION" in
    server)
        cd -- "$SERVER_DIRECTORY"
        java -Xms2G -Xmx2G -jar folia.jar nogui \
            2>&1 | tee "$ENVIRONMENT_ROOT/logs/server-$(timestamp).log"
        ;;
    alpha)
        run_compatible_client alpha SWAlpha
        ;;
    bravo)
        run_compatible_client bravo SWBravo
        ;;
    probe)
        PROBE_MODE="${3:-valid}"
        SELECTED_FRAME="${4:-1}"
        cd -- "$REPOSITORY_ROOT"
        exec ./gradlew --no-daemon --no-parallel --max-workers=2 \
            :mods:1.21.11-fabric:runFoliaLiveTestProbe \
            -PfoliaLiveTestGameDir="$ENVIRONMENT_ROOT/clients/probe" \
            -PfoliaLiveTestUsername=SWProbe \
            -PfoliaLiveTestHost="$SERVER_HOST" \
            -PfoliaLiveTestPort="$SERVER_PORT" \
            -PfoliaLiveTestProbeMode="$PROBE_MODE" \
            -PfoliaLiveTestProbeFrame="$SELECTED_FRAME"
        ;;
    mcc)
        cd -- "$ENVIRONMENT_ROOT/clients/mcc"
        exec "$MCC_EXECUTABLE" SWVanilla - "$SERVER_HOST:$SERVER_PORT"
        ;;
    verify)
        cd -- "$REPOSITORY_ROOT"
        exec ./gradlew --no-daemon --no-parallel --max-workers=2 \
            :common:verifyFoliaLiveTestControlFixture \
            -PfoliaLiveTestFixtureDir="$ENVIRONMENT_ROOT/fixtures" \
            -PfoliaLiveTestServerWaypointFile="$SERVER_DIRECTORY/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json"
        ;;
    record)
        {
            printf 'recorded_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
            printf 'repository_commit=%s\n' "$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
            printf 'server_logs=%s\n' "$ENVIRONMENT_ROOT/logs"
            printf 'alpha_log=%s\n' "$ENVIRONMENT_ROOT/clients/alpha/logs/latest.log"
            printf 'bravo_log=%s\n' "$ENVIRONMENT_ROOT/clients/bravo/logs/latest.log"
            printf 'probe_log=%s\n' "$ENVIRONMENT_ROOT/clients/probe/logs/latest.log"
            printf 'server_waypoint_sha256=%s\n' "$(shasum -a 256 "$SERVER_DIRECTORY/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json" | awk '{print $1}')"
        } >> "$ENVIRONMENT_ROOT/evidence/runtime-records.txt"
        printf 'Appended runtime record to %s/evidence/runtime-records.txt\n' "$ENVIRONMENT_ROOT"
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
