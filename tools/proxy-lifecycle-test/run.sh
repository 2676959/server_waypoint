#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    printf '%s\n' \
        "Usage: run.sh <environment-root> <backend-a|backend-b|proxy|proxy-transfer|direct-reconnect|direct-restart|record|audit>"
}

if (($# != 2)); then
    usage >&2
    exit 2
fi

ENVIRONMENT_ROOT="$1"
ACTION="$2"
if [[ ! -f "$ENVIRONMENT_ROOT/.server-waypoint-proxy-lifecycle-test" \
        || ! -f "$ENVIRONMENT_ROOT/environment.env" ]]; then
    printf 'Not a prepared proxy lifecycle environment: %s\n' "$ENVIRONMENT_ROOT" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "$ENVIRONMENT_ROOT/environment.env"

timestamp() {
    date -u +%Y%m%dT%H%M%SZ
}

run_client() {
    local scenario="$1"
    local port="$2"
    local test_file="$ENVIRONMENT_ROOT/tests/$scenario.json"
    local launch_command
    launch_command="launch fabric-loader-0.18.2-1.21.11 -lwjgl -offline -quit --jvm \"-Xms1G -Xmx3G -DserverWaypointLifecycle.scenario=$scenario -DserverWaypointLifecycle.serverAId=$SERVER_A_ID -DserverWaypointLifecycle.serverBId=$SERVER_B_ID\""
    cd -- "$ENVIRONMENT_ROOT/client/launcher"
    java \
        -Dhmc.jline.enabled=false \
        -Dhmc.test.filename="$test_file" \
        -Dhmc.mcdir="$ENVIRONMENT_ROOT/client/minecraft-store" \
        -Dhmc.gamedir="$ENVIRONMENT_ROOT/client/game" \
        -Dhmc.offline.username=SWLifecycle \
        -Dhmc.gameargs="--quickPlayMultiplayer 127.0.0.1:$port" \
        -Dhmc.crash.report.watcher=true \
        -Dhmc.exit.on.failed.command=true \
        -jar "$ENVIRONMENT_ROOT/artifacts/headlessmc-launcher-2.10.0.jar" \
        --command "$launch_command" \
        2>&1 | tee "$ENVIRONMENT_ROOT/logs/client/$scenario-$(timestamp).log"
}

case "$ACTION" in
    backend-a)
        cd -- "$ENVIRONMENT_ROOT/backends/a"
        java -Xms1G -Xmx2G -jar folia.jar nogui --nojline \
            2>&1 | tee "$ENVIRONMENT_ROOT/logs/backend-a/backend-a-$(timestamp).log"
        ;;
    backend-b)
        cd -- "$ENVIRONMENT_ROOT/backends/b"
        java -Xms1G -Xmx2G -jar folia.jar nogui --nojline \
            2>&1 | tee "$ENVIRONMENT_ROOT/logs/backend-b/backend-b-$(timestamp).log"
        ;;
    proxy)
        cd -- "$ENVIRONMENT_ROOT/proxy"
        "$ENVIRONMENT_ROOT/java25/bin/java" -Xms512M -Xmx1G \
            -jar "$ENVIRONMENT_ROOT/artifacts/velocity-4.1.1-24.jar" \
            2>&1 | tee "$ENVIRONMENT_ROOT/logs/proxy/velocity-$(timestamp).log"
        ;;
    proxy-transfer)
        run_client proxy-transfer "$PROXY_PORT"
        ;;
    direct-reconnect)
        run_client direct-reconnect "$BACKEND_A_PORT"
        ;;
    direct-restart)
        run_client direct-restart "$BACKEND_A_PORT"
        ;;
    record)
        {
            printf 'recorded_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
            printf 'release_candidate_commit=%s\n' "$(git -C "$REPOSITORY_ROOT" rev-parse 99cc415)"
            printf 'head_commit=%s\n' "$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
            printf 'cache_files_begin\n'
            find "$ENVIRONMENT_ROOT/client/game/server_waypoint" -type f -name '*.json' -print -exec shasum -a 256 '{}' \;
            printf 'cache_files_end\n'
            printf 'logs_begin\n'
            find "$ENVIRONMENT_ROOT/logs" -type f -print | sort
            printf 'logs_end\n'
        } >> "$ENVIRONMENT_ROOT/evidence/runtime-records.txt"
        ;;
    audit)
        "$SCRIPT_DIRECTORY/audit.sh" "$ENVIRONMENT_ROOT"
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac
