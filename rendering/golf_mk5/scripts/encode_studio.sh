#!/usr/bin/env bash
set -euo pipefail
render_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output_root="$render_root/output/studio_black/surfaces-v1"
for ((frame=1; frame<=210; frame++)); do
  printf -v frame_path '%s/frames/golf_%04d.png' "$output_root" "$frame"
  if [[ ! -s "$frame_path" ]]; then
    echo "Missing frame: $frame_path. Complete the render before encoding." >&2
    exit 1
  fi
done
ffmpeg -hide_banner -nostdin -n \
  -framerate 30 -start_number 1 \
  -i "$output_root/frames/golf_%04d.png" \
  -frames:v 210 -an -c:v libx264 -preset slow -crf 18 \
  -profile:v high -level:v 3.1 -pix_fmt yuv420p \
  -movflags +faststart \
  "$output_root/golf_mk5_black_7s.mp4"
