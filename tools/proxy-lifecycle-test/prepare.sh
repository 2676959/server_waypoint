#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIRECTORY/../.." && pwd)"
ENVIRONMENT_ROOT=""
FOLIA_JAR=""
VELOCITY_JAR=""
JAVA_25_HOME=""
HMC_LAUNCHER=""
HMC_STORE_TEMPLATE=""
HMC_MOD_TEMPLATE=""
BASELINE_FREEZE=""
PROXY_PORT="25620"
BACKEND_A_PORT="25621"
BACKEND_B_PORT="25622"
SERVER_A_ID="41001"
SERVER_B_ID="42002"

usage() {
    printf '%s\n' \
        "Usage: prepare.sh --root <new-directory> --folia-jar <path> --velocity-jar <path>" \
        "                  --java25-home <path> --hmc-launcher <path>" \
        "                  --hmc-store-template <path> --hmc-mod-template <path>" \
        "                  --baseline-freeze <path>"
}

while (($# > 0)); do
    case "$1" in
        --root) ENVIRONMENT_ROOT="$2"; shift 2 ;;
        --folia-jar) FOLIA_JAR="$2"; shift 2 ;;
        --velocity-jar) VELOCITY_JAR="$2"; shift 2 ;;
        --java25-home) JAVA_25_HOME="$2"; shift 2 ;;
        --hmc-launcher) HMC_LAUNCHER="$2"; shift 2 ;;
        --hmc-store-template) HMC_STORE_TEMPLATE="$2"; shift 2 ;;
        --hmc-mod-template) HMC_MOD_TEMPLATE="$2"; shift 2 ;;
        --baseline-freeze) BASELINE_FREEZE="$2"; shift 2 ;;
        --proxy-port) PROXY_PORT="$2"; shift 2 ;;
        --backend-a-port) BACKEND_A_PORT="$2"; shift 2 ;;
        --backend-b-port) BACKEND_B_PORT="$2"; shift 2 ;;
        --help|-h) usage; exit 0 ;;
        *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
    esac
done

required_file() {
    local value="$1"
    local option="$2"
    if [[ -z "$value" || ! -f "$value" ]]; then
        printf 'Provide an existing file with %s.\n' "$option" >&2
        exit 2
    fi
}

required_directory() {
    local value="$1"
    local option="$2"
    if [[ -z "$value" || ! -d "$value" ]]; then
        printf 'Provide an existing directory with %s.\n' "$option" >&2
        exit 2
    fi
}

if [[ -z "$ENVIRONMENT_ROOT" ]]; then
    printf 'Provide a new disposable path with --root.\n' >&2
    exit 2
fi
case "$ENVIRONMENT_ROOT" in
    "$REPOSITORY_ROOT"|"$REPOSITORY_ROOT"/*)
        printf 'The disposable root must be outside the repository.\n' >&2
        exit 2
        ;;
esac
if [[ -e "$ENVIRONMENT_ROOT" ]]; then
    printf 'Refusing to reuse an existing path: %s\n' "$ENVIRONMENT_ROOT" >&2
    exit 2
fi

required_file "$FOLIA_JAR" "--folia-jar"
required_file "$VELOCITY_JAR" "--velocity-jar"
required_file "$HMC_LAUNCHER" "--hmc-launcher"
required_directory "$JAVA_25_HOME" "--java25-home"
required_directory "$HMC_STORE_TEMPLATE" "--hmc-store-template"
required_directory "$HMC_MOD_TEMPLATE" "--hmc-mod-template"
required_directory "$BASELINE_FREEZE" "--baseline-freeze"
required_file "$JAVA_25_HOME/bin/java" "--java25-home"

if [[ "$(git -C "$REPOSITORY_ROOT" rev-parse 99cc415)" != \
        "99cc415e3e74f0fc5a4724553db39179636c8694" ]]; then
    printf 'The required release-candidate commit cannot be resolved.\n' >&2
    exit 1
fi

mkdir -p -- "$ENVIRONMENT_ROOT"
ENVIRONMENT_ROOT="$(cd -- "$ENVIRONMENT_ROOT" && pwd)"

printf 'Building the 1.21.11 production plugin/client and development-only lifecycle control...\n'
(
    cd -- "$REPOSITORY_ROOT"
    ./gradlew --no-daemon --no-parallel --max-workers=2 \
        :paper:1.21.11-paper:shadowJar \
        :mods:1.21.11-fabric:remapJar \
        :mods:1.21.11-fabric:remapProxyLifecycleTestJar
)

PAPER_PLUGIN="$REPOSITORY_ROOT/paper/versions/1.21.11-paper/build/libs/server_waypoint-3.1.0-paper-mc1.21.11-26.1.2.jar"
FABRIC_CLIENT="$REPOSITORY_ROOT/mods/versions/1.21.11-fabric/build/libs/server_waypoint-3.1.0-fabric-mc1.21.11.jar"
LIFECYCLE_CONTROL="$REPOSITORY_ROOT/mods/versions/1.21.11-fabric/build/libs/server-waypoint-proxy-lifecycle-test.jar"
required_file "$PAPER_PLUGIN" "the Paper shadowJar task"
required_file "$FABRIC_CLIENT" "the Fabric remapJar task"
required_file "$LIFECYCLE_CONTROL" "the lifecycle control remap task"

mkdir -p -- \
    "$ENVIRONMENT_ROOT/artifacts" \
    "$ENVIRONMENT_ROOT/backends/a/plugins/ServerWaypoint/waypoints" \
    "$ENVIRONMENT_ROOT/backends/b/plugins/ServerWaypoint/waypoints" \
    "$ENVIRONMENT_ROOT/client/game/mods" \
    "$ENVIRONMENT_ROOT/client/game/server_waypoint" \
    "$ENVIRONMENT_ROOT/client/launcher" \
    "$ENVIRONMENT_ROOT/client/minecraft-store" \
    "$ENVIRONMENT_ROOT/evidence" \
    "$ENVIRONMENT_ROOT/logs/backend-a" \
    "$ENVIRONMENT_ROOT/logs/backend-b" \
    "$ENVIRONMENT_ROOT/logs/client" \
    "$ENVIRONMENT_ROOT/logs/proxy" \
    "$ENVIRONMENT_ROOT/proxy" \
    "$ENVIRONMENT_ROOT/tests"

cp -- "$FOLIA_JAR" "$ENVIRONMENT_ROOT/artifacts/folia-1.21.11.jar"
cp -- "$VELOCITY_JAR" "$ENVIRONMENT_ROOT/artifacts/velocity-4.1.1-24.jar"
cp -- "$HMC_LAUNCHER" "$ENVIRONMENT_ROOT/artifacts/headlessmc-launcher-2.10.0.jar"
cp -- "$PAPER_PLUGIN" "$ENVIRONMENT_ROOT/artifacts/ServerWaypoint-3.1.0-paper-1.21.11.jar"
cp -- "$FABRIC_CLIENT" "$ENVIRONMENT_ROOT/artifacts/server_waypoint-3.1.0-fabric-mc1.21.11.jar"
cp -- "$LIFECYCLE_CONTROL" "$ENVIRONMENT_ROOT/artifacts/server-waypoint-proxy-lifecycle-test.jar"
cp -R -- "$JAVA_25_HOME" "$ENVIRONMENT_ROOT/java25"
cp -R -- "$HMC_STORE_TEMPLATE/." "$ENVIRONMENT_ROOT/client/minecraft-store/"

find "$HMC_MOD_TEMPLATE" -maxdepth 1 -type f -name '*.jar' \
    ! -name 'server_waypoint-*.jar' -exec cp -- '{}' "$ENVIRONMENT_ROOT/client/game/mods/" \;
cp -- "$ENVIRONMENT_ROOT/artifacts/server_waypoint-3.1.0-fabric-mc1.21.11.jar" \
    "$ENVIRONMENT_ROOT/client/game/mods/"
cp -- "$ENVIRONMENT_ROOT/artifacts/server-waypoint-proxy-lifecycle-test.jar" \
    "$ENVIRONMENT_ROOT/client/game/mods/"

for backend in a b; do
    cp -- "$ENVIRONMENT_ROOT/artifacts/folia-1.21.11.jar" \
        "$ENVIRONMENT_ROOT/backends/$backend/folia.jar"
    cp -- "$ENVIRONMENT_ROOT/artifacts/ServerWaypoint-3.1.0-paper-1.21.11.jar" \
        "$ENVIRONMENT_ROOT/backends/$backend/plugins/ServerWaypoint.jar"
    printf 'eula=true\n' > "$ENVIRONMENT_ROOT/backends/$backend/eula.txt"
done

cat > "$ENVIRONMENT_ROOT/backends/a/plugins/ServerWaypoint/config.json" <<EOF
{
  "serverId": $SERVER_A_ID,
  "defaultPageLimit": 10,
  "defaultNavigationMethods": ["actionbar"],
  "CommandPermission": {
    "add": 0, "edit": 0, "remove": 0, "navigate": 0,
    "tp": 2, "reload": 2, "upload": 2, "uploadDelete": 4
  },
  "Features": {
    "addWaypointFromChatSharing": true,
    "sendXaerosWorldId": true,
    "compressChunkedMessages": true
  }
}
EOF

cat > "$ENVIRONMENT_ROOT/backends/b/plugins/ServerWaypoint/config.json" <<EOF
{
  "serverId": $SERVER_B_ID,
  "defaultPageLimit": 10,
  "defaultNavigationMethods": ["actionbar"],
  "CommandPermission": {
    "add": 0, "edit": 0, "remove": 0, "navigate": 0,
    "tp": 2, "reload": 2, "upload": 2, "uploadDelete": 4
  },
  "Features": {
    "addWaypointFromChatSharing": true,
    "sendXaerosWorldId": true,
    "compressChunkedMessages": true
  }
}
EOF

cat > "$ENVIRONMENT_ROOT/backends/a/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json" <<'EOF'
[
  {
    "list_name": "server-a-only",
    "n": 101,
    "waypoints": [
      {
        "name": "a-fixture",
        "initials": "AF",
        "pos": [11, 81, -13],
        "color": "#AA3311",
        "yaw": 45,
        "global": false,
        "keywords": [],
        "description": "backend A only"
      }
    ]
  }
]
EOF

cat > "$ENVIRONMENT_ROOT/backends/b/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json" <<'EOF'
[
  {
    "list_name": "server-b-only",
    "n": 202,
    "waypoints": [
      {
        "name": "b-fixture",
        "initials": "BF",
        "pos": [-22, 91, 27],
        "color": "#1155CC",
        "yaw": -90,
        "global": true,
        "keywords": [],
        "description": "backend B only"
      }
    ]
  }
]
EOF

cat > "$ENVIRONMENT_ROOT/backends/a/server.properties" <<EOF
server-port=$BACKEND_A_PORT
server-ip=127.0.0.1
online-mode=false
enforce-secure-profile=false
white-list=false
spawn-protection=0
level-name=world-a
motd=Server Waypoint lifecycle backend A
view-distance=4
simulation-distance=3
EOF

cat > "$ENVIRONMENT_ROOT/backends/b/server.properties" <<EOF
server-port=$BACKEND_B_PORT
server-ip=127.0.0.1
online-mode=false
enforce-secure-profile=false
white-list=false
spawn-protection=0
level-name=world-b
motd=Server Waypoint lifecycle backend B
view-distance=4
simulation-distance=3
EOF

cat > "$ENVIRONMENT_ROOT/proxy/velocity.toml" <<EOF
config-version = "2.8"
bind = "127.0.0.1:$PROXY_PORT"
motd = "Server Waypoint disposable lifecycle proxy"
show-max-players = 8
online-mode = false
force-key-authentication = false
prevent-client-proxy-connections = false
player-info-forwarding-mode = "NONE"
forwarding-secret-file = "forwarding.secret"
announce-forge = false
kick-existing-players = false
ping-passthrough = "DISABLED"
sample-players-in-ping = false
enable-player-address-logging = true

[packet-limiter]
interval = 7
packets-per-second = -1
bytes-per-second = -1
decompressed-bytes-per-second = 5242880

[servers]
backend-a = "127.0.0.1:$BACKEND_A_PORT"
backend-b = "127.0.0.1:$BACKEND_B_PORT"
try = ["backend-a"]

[forced-hosts]

[advanced]
compression-threshold = 256
compression-level = -1
login-ratelimit = 0
connection-timeout = 5000
read-timeout = 30000
haproxy-protocol = false
tcp-fast-open = false
bungee-plugin-message-channel = true
show-ping-requests = false
failover-on-unexpected-server-disconnect = false
announce-proxy-commands = true
log-command-executions = true
log-player-connections = true
accepts-transfers = false
enable-reuse-port = false
command-rate-limit = 0
forward-commands-if-rate-limited = true
kick-after-rate-limited-commands = 0
tab-complete-rate-limit = 0
kick-after-rate-limited-tab-completes = 0

[query]
enabled = false
port = $PROXY_PORT
map = "Velocity"
show-plugins = false
EOF

cat > "$ENVIRONMENT_ROOT/client/game/options.txt" <<'EOF'
pauseOnLostFocus:false
onboardAccessibility:false
EOF

cat > "$ENVIRONMENT_ROOT/tests/proxy-transfer.json" <<'EOF'
{
  "name": "Server Waypoint A to B to A proxy lifecycle",
  "timeout": 180,
  "totalTimeout": 900,
  "steps": [
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=control_initialized scenario=proxy-transfer"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=baseline_complete result=PASS scenario=proxy-transfer role=A"},
    {"type": "SEND", "message": "/wp download"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=fixture_download result=PASS scenario=proxy-transfer role=A download=1"},
    {"type": "SEND", "message": "/swlifecycle mark-a"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=marker_created result=PASS scenario=proxy-transfer memory=true disk=false revision=101"},
    {"type": "SEND", "message": "/server backend-b"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=transfer_complete result=PASS scenario=proxy-transfer from=A to=B connection=2"},
    {"type": "SEND", "message": "/swlifecycle assert-b"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=assert_b result=PASS scenario=proxy-transfer"},
    {"type": "SEND", "message": "/server backend-a"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=transfer_complete result=PASS scenario=proxy-transfer from=B to=A connection=3"},
    {"type": "SEND", "message": "/swlifecycle assert-a"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=assert_a result=PASS scenario=proxy-transfer"},
    {"type": "SEND", "message": "quit"}
  ]
}
EOF

cat > "$ENVIRONMENT_ROOT/tests/direct-reconnect.json" <<'EOF'
{
  "name": "Server Waypoint direct Folia baseline and reconnect",
  "timeout": 180,
  "totalTimeout": 720,
  "steps": [
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=control_initialized scenario=direct-reconnect"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=baseline_complete result=PASS scenario=direct-reconnect role=A"},
    {"type": "SEND", "message": "/wp download"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=fixture_download result=PASS scenario=direct-reconnect role=A download=1"},
    {"type": "SEND", "message": "disconnect"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=leave phase=after result=PASS scenario=direct-reconnect"},
    {"type": "SEND", "message": "connect 127.0.0.1 25621"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=transfer_complete result=PASS scenario=direct-reconnect from=A to=A connection=2"},
    {"type": "SEND", "message": "/swlifecycle assert-a"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=assert_a result=PASS scenario=direct-reconnect"},
    {"type": "SEND", "message": "quit"}
  ]
}
EOF

cat > "$ENVIRONMENT_ROOT/tests/direct-restart.json" <<'EOF'
{
  "name": "Server Waypoint direct Folia clean restart and cache rebinding",
  "timeout": 180,
  "totalTimeout": 600,
  "steps": [
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=control_initialized scenario=direct-restart"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=baseline_complete result=PASS scenario=direct-restart role=A"},
    {"type": "SEND", "message": "/wp download"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=fixture_download result=PASS scenario=direct-restart role=A download=1"},
    {"type": "SEND", "message": "/swlifecycle assert-a"},
    {"type": "CONTAINS", "message": "SW_LIFECYCLE event=assert_a result=PASS scenario=direct-restart"},
    {"type": "SEND", "message": "quit"}
  ]
}
EOF

ENVIRONMENT_FILE="$ENVIRONMENT_ROOT/environment.env"
{
    printf 'REPOSITORY_ROOT=%q\n' "$REPOSITORY_ROOT"
    printf 'ENVIRONMENT_ROOT=%q\n' "$ENVIRONMENT_ROOT"
    printf 'PROXY_PORT=%q\n' "$PROXY_PORT"
    printf 'BACKEND_A_PORT=%q\n' "$BACKEND_A_PORT"
    printf 'BACKEND_B_PORT=%q\n' "$BACKEND_B_PORT"
    printf 'SERVER_A_ID=%q\n' "$SERVER_A_ID"
    printf 'SERVER_B_ID=%q\n' "$SERVER_B_ID"
    printf 'BASELINE_FREEZE=%q\n' "$BASELINE_FREEZE"
} > "$ENVIRONMENT_FILE"

cp -R -- "$BASELINE_FREEZE/evidence" "$ENVIRONMENT_ROOT/evidence/baseline-freeze"

{
    printf 'prepared_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'release_candidate_commit=%s\n' "$(git -C "$REPOSITORY_ROOT" rev-parse 99cc415)"
    printf 'current_repository_commit=%s\n' "$(git -C "$REPOSITORY_ROOT" rev-parse HEAD)"
    printf 'current_repository_status_begin\n'
    git -C "$REPOSITORY_ROOT" status --short
    printf 'current_repository_status_end\n'
    printf 'java21_begin\n'
    java -version 2>&1
    printf 'java21_end\n'
    printf 'java25_begin\n'
    "$ENVIRONMENT_ROOT/java25/bin/java" -version 2>&1
    printf 'java25_end\n'
    printf 'artifact_checksums_begin\n'
    shasum -a 256 "$ENVIRONMENT_ROOT"/artifacts/*
    printf 'artifact_checksums_end\n'
    printf 'fixture_checksums_begin\n'
    shasum -a 256 \
        "$ENVIRONMENT_ROOT/backends/a/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json" \
        "$ENVIRONMENT_ROOT/backends/b/plugins/ServerWaypoint/waypoints/minecraft\$overworld.json"
    printf 'fixture_checksums_end\n'
    printf 'repository_diff_sha256=%s\n' "$(git -C "$REPOSITORY_ROOT" diff --binary | shasum -a 256 | awk '{print $1}')"
} > "$ENVIRONMENT_ROOT/evidence/environment.txt"

touch "$ENVIRONMENT_ROOT/.server-waypoint-proxy-lifecycle-test"
printf 'Prepared disposable proxy lifecycle environment: %s\n' "$ENVIRONMENT_ROOT"
