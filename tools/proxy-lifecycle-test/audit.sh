#!/usr/bin/env bash
set -euo pipefail

if (($# != 1)); then
    printf 'Usage: audit.sh <environment-root>\n' >&2
    exit 2
fi

ENVIRONMENT_ROOT="$1"
if [[ ! -f "$ENVIRONMENT_ROOT/.server-waypoint-proxy-lifecycle-test" ]]; then
    printf 'Not a prepared proxy lifecycle environment: %s\n' "$ENVIRONMENT_ROOT" >&2
    exit 2
fi
# shellcheck disable=SC1090
source "$ENVIRONMENT_ROOT/environment.env"

accepted_log() {
    local scenario="$1"
    local accepted
    accepted="$(grep -El 'CommandTest was successful' \
        "$ENVIRONMENT_ROOT/logs/client/$scenario"-*.log 2>/dev/null | sort | tail -n 1)"
    if [[ -z "$accepted" ]]; then
        printf 'No successful client log was recorded for %s.\n' "$scenario" >&2
        exit 1
    fi
    printf '%s\n' "$accepted"
}

CLIENT_LOGS=(
    "$(accepted_log proxy-transfer)"
    "$(accepted_log direct-reconnect)"
    "$(accepted_log direct-restart)"
)

required_log_line() {
    local pattern="$1"
    if ! grep -Eh "$pattern" "${CLIENT_LOGS[@]}" >/dev/null; then
        printf 'Missing required client evidence: %s\n' "$pattern" >&2
        exit 1
    fi
}

required_log_line 'event=transfer_complete result=PASS scenario=proxy-transfer from=A to=B'
required_log_line 'event=transfer_complete result=PASS scenario=proxy-transfer from=B to=A'
required_log_line 'event=marker_created result=PASS scenario=proxy-transfer memory=true disk=false'
required_log_line 'event=assert_a result=PASS scenario=proxy-transfer'
required_log_line 'event=assert_b result=PASS scenario=proxy-transfer'
required_log_line 'event=transfer_complete result=PASS scenario=direct-reconnect from=A to=A'
required_log_line 'event=baseline_complete result=PASS scenario=direct-restart role=A'
required_log_line 'event=fixture_download result=PASS scenario=direct-restart role=A'

if grep -Ehi \
        'UnsupportedOperationException|NullPointerException|ownership violation|not on the region thread|unexpected disconnect|event=assertion_failure|CommandTest failed' \
        "${CLIENT_LOGS[@]}" "$ENVIRONMENT_ROOT/logs"/backend-*/*.log "$ENVIRONMENT_ROOT/logs/proxy"/*.log >/dev/null; then
    printf 'A forbidden lifecycle/runtime error appears in the retained logs.\n' >&2
    exit 1
fi

if jar tf "$ENVIRONMENT_ROOT/artifacts/server_waypoint-3.1.0-fabric-mc1.21.11.jar" \
        | grep -E 'proxyLifecycleTest|ProxyLifecycleTest|SW_LIFECYCLE' >/dev/null; then
    printf 'Development lifecycle controls entered the production Fabric JAR.\n' >&2
    exit 1
fi
if jar tf "$ENVIRONMENT_ROOT/artifacts/ServerWaypoint-3.1.0-paper-1.21.11.jar" \
        | grep -E 'proxyLifecycleTest|ProxyLifecycleTest|SW_LIFECYCLE' >/dev/null; then
    printf 'Development lifecycle controls entered the production Paper JAR.\n' >&2
    exit 1
fi
if find "$REPOSITORY_ROOT/builds" -maxdepth 1 -type f -name '*proxy-lifecycle-test*' | grep . >/dev/null; then
    printf 'The development lifecycle control entered the release artifact directory.\n' >&2
    exit 1
fi

RELEASE_ARTIFACT_COUNT=0
while IFS= read -r release_artifact; do
    RELEASE_ARTIFACT_COUNT=$((RELEASE_ARTIFACT_COUNT + 1))
    if jar tf "$release_artifact" \
            | grep -Ei 'proxyLifecycleTest|ProxyLifecycleTest|server_waypoint-proxy-lifecycle-test|foliaLiveTestProbe|FoliaRegionLoadPlugin|headlessmc|junit' \
            >/dev/null; then
        printf 'A development or test artifact entered %s.\n' "$release_artifact" >&2
        exit 1
    fi
done < <(find "$REPOSITORY_ROOT/builds" -maxdepth 1 -type f \
    -name 'server_waypoint-3.1.0-*.jar' | sort)
if ((RELEASE_ARTIFACT_COUNT != 38)); then
    printf 'Expected 38 release artifacts, found %d.\n' "$RELEASE_ARTIFACT_COUNT" >&2
    exit 1
fi

CURRENT_4B_FIRST="$(find /private/tmp/server-waypoint-folia-4b-0da0865 -type f -print0 \
    | sort -z | xargs -0 shasum -a 256 | shasum -a 256 | awk '{print $1}')"
CURRENT_4B_SECOND="$(find /private/tmp/server-waypoint-folia-4b-rerun-0da0865 -type f -print0 \
    | sort -z | xargs -0 shasum -a 256 | shasum -a 256 | awk '{print $1}')"
FROZEN_4B_FIRST="$(sed -n '1s/[[:space:]].*//p' "$BASELINE_FREEZE/evidence/retained-4b-tree.sha256")"
FROZEN_4B_SECOND="$(sed -n '2s/[[:space:]].*//p' "$BASELINE_FREEZE/evidence/retained-4b-tree.sha256")"
if [[ "$CURRENT_4B_FIRST" != "$FROZEN_4B_FIRST" || "$CURRENT_4B_SECOND" != "$FROZEN_4B_SECOND" ]]; then
    printf 'A retained 4B evidence root changed during Step 5.\n' >&2
    exit 1
fi

{
    printf 'audited_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'result=PASS\n'
    printf 'retained_4b_first_sha256=%s\n' "$CURRENT_4B_FIRST"
    printf 'retained_4b_second_sha256=%s\n' "$CURRENT_4B_SECOND"
    printf 'production_test_tool_exclusion=PASS\n'
    printf 'release_artifact_count=%d\n' "$RELEASE_ARTIFACT_COUNT"
    printf 'release_artifact_content_audit=PASS\n'
    printf 'required_client_assertions=PASS\n'
    printf 'forbidden_log_patterns=ABSENT\n'
    printf 'accepted_client_logs_begin\n'
    printf '%s\n' "${CLIENT_LOGS[@]}"
    printf 'accepted_client_logs_end\n'
    printf 'discarded_client_diagnostics_begin\n'
    find "$ENVIRONMENT_ROOT/logs/client" -type f -name '*.log' \
        ! -path "${CLIENT_LOGS[0]}" ! -path "${CLIENT_LOGS[1]}" ! -path "${CLIENT_LOGS[2]}" \
        -print | sort
    printf 'discarded_client_diagnostics_end\n'
} > "$ENVIRONMENT_ROOT/evidence/audit.txt"

printf 'Proxy lifecycle evidence audit passed: %s/evidence/audit.txt\n' "$ENVIRONMENT_ROOT"
