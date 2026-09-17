#!/usr/bin/env bash
set -euo pipefail
render_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output_root="$render_root/output/studio_black/transparent-v1"
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
  -frames:v 210 -an -c:v libvpx-vp9 -pix_fmt yuva420p \
  -lossless 1 -auto-alt-ref 0 -row-mt 1 -deadline good -cpu-used 2 \
  "$output_root/golf_mk5_transparent_7s.webm"
