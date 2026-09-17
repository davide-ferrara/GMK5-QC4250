#!/usr/bin/env bash
set -euo pipefail
render_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
for ((frame=1; frame<=210; frame++)); do
  printf -v frame_path '%s/output/studio_black/frames/golf_%04d.png' "$render_root" "$frame"
  if [[ ! -s "$frame_path" ]]; then
    echo "Missing frame: $frame_path. Complete the render before encoding." >&2
    exit 1
  fi
done
ffmpeg -hide_banner -nostdin -n \
  -framerate 30 -start_number 1 \
  -i "$render_root/output/studio_black/frames/golf_%04d.png" \
  -frames:v 210 -an -c:v libx264 -preset slow -crf 18 \
  -profile:v high -level:v 3.1 -pix_fmt yuv420p \
  -movflags +faststart \
  "$render_root/output/studio_black/golf_mk5_black_7s.mp4"
