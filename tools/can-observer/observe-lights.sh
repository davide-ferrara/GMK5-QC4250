#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
adb="$repo_root/.android-sdk/platform-tools/adb"
serial="${1:-}"

if [[ -z "$serial" ]]; then
    echo "Usage: $0 DEVICE_SERIAL" >&2
    exit 2
fi

echo "LIGHT_OBSERVER_READY action=xy.xygala.lamplet extra=lamplet_state"
exec "$adb" -s "$serial" logcat -v epoch -T 1 \
    XyautoAvmService:I TurnColorTouchView:D '*:S'
