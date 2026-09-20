#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
sdk_dir="$repo_root/.android-sdk"
android_jar="$sdk_dir/platforms/android-37.0/android.jar"
d8="$sdk_dir/build-tools/36.0.0/d8"
source_dir="$repo_root/tools/can-observer/src"
output_dir="$repo_root/tools/can-observer/build"
classes_dir="$output_dir/classes"
class_jar="$output_dir/can-observer-classes.jar"

if [[ ! -f "$android_jar" ]]; then
    echo "Missing $android_jar" >&2
    exit 1
fi

mkdir -p "$classes_dir"
find "$classes_dir" -type f -delete

javac --release 8 -Xlint:-options -cp "$android_jar" -d "$classes_dir" \
    "$source_dir/com/golfv/canobserver/CanObserver.java"
jar --create --file "$class_jar" -C "$classes_dir" .
"$d8" --min-api 30 --output "$output_dir/can-observer.jar" "$class_jar"

echo "$output_dir/can-observer.jar"
