#!/usr/bin/env bash
set -euo pipefail
render_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$render_root/output/studio_black/door-states-v1"
last_mask=15
if [[ "${1:-}" == "--tailgate" ]]; then
    source_dir="$render_root/output/studio_black/door-states-v2"
    last_mask=31
fi
target_dir="$render_root/../../launcher-app/src/main/res/drawable-nodpi"
for ((mask=0; mask<=last_mask; mask++)); do
    printf -v asset_name 'golf_top_%02d' "$mask"
    [[ -s "$source_dir/$asset_name.png" ]] || { echo "Missing render: $asset_name" >&2; exit 1; }
done
for ((mask=0; mask<=last_mask; mask++)); do
    printf -v asset_name 'golf_top_%02d' "$mask"
    ffmpeg -y -loglevel error -i "$source_dir/$asset_name.png" \
        -c:v libwebp -lossless 1 -compression_level 6 "$target_dir/$asset_name.webp"
done
