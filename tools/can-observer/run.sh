#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
adb="$repo_root/.android-sdk/platform-tools/adb"
serial="${1:-}"
local_jar="$repo_root/tools/can-observer/build/can-observer.jar"
remote_jar="/data/local/tmp/golf-can-observer.jar"

if [[ -z "$serial" ]]; then
    echo "Usage: $0 DEVICE_SERIAL" >&2
    exit 2
fi

"$repo_root/tools/can-observer/build.sh" >/dev/null
"$adb" -s "$serial" push "$local_jar" "$remote_jar" >/dev/null
exec "$adb" -s "$serial" shell \
    "CLASSPATH=$remote_jar app_process /system/bin com.golfv.canobserver.CanObserver"
