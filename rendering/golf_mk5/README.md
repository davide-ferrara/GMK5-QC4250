# Golf Mk5 render workspace

## Door-state images used by the launcher

The articulated working scene is `golf_mk5_hatches_proof.blend`, separate from
the original `golf_mk5_studio_black.blend`. Export all 32 complete orthographic
images (1024×1024 with transparency) from this directory:

```sh
blender -b golf_mk5_hatches_proof.blend --python scripts/render_door_states.py -- --tailgate
bash scripts/encode_door_states.sh --tailgate
```

PNG masters go to `output/studio_black/door-states-v2/`; lossless WebP images
go to the launcher's `drawable-nodpi` resources. Bits 1, 2, 4 and 8 represent
driver front, passenger front, driver rear and passenger rear. An angle of
zero closes each door; open doors use 58 degrees. Bit 16 adds the tailgate at
85 degrees. The front hood stays closed. Export does not save the scene.

The launcher selects the complete image from the four verified CAN door states
and optional tailgate state. The tailgate CAN decoder is deliberately unknown
until vehicle testing; Info can preview all 32 combinations in the meantime.
It uses the perspective view when all doors are closed, the top view when any
door is open, and speed while moving. Info has a session-only visual override
for all combinations, including the closed top view. Unknown CAN door state
uses the normal perspective view. Returning to Auto reads the current CAN state.
Both views have a lights overlay controlled by CAN or the lights preview in
Info. The top overlay uses the same orthographic camera and 1024×1024 canvas
as every door state. Compose applies the same Fit scaling and centering to
the base image, lamp mask and bloom. Regenerate the top overlay with:

```sh
blender -b golf_mk5_hatches_proof.blend --python scripts/render_lights_overlay.py -- --top
blender -b golf_mk5_hatches_proof.blend --python scripts/render_lights_overlay.py -- --top --tailgate-open
cp output/studio_black/door-states-v2/lights/golf_top_lights_overlay.png ../../launcher-app/src/main/res/drawable-nodpi/
cp output/studio_black/door-states-v2/lights/golf_top_tailgate_lights_overlay.png ../../launcher-app/src/main/res/drawable-nodpi/
```

Manual check: toggle each door and the tailgate in Info, try all-open/all-closed,
return to Auto, and verify movement still replaces the car with speed. Confirm
that opening a door interrupts the intro and closing it does not replay it.
Test the tailgate alone and combined with side doors, with lights on/off. The
rear lamp mask and bloom must follow the tailgate. Auto must leave the tailgate
unknown until its CAN mapping is confirmed (see `docs/CANBUS.md`).

This directory contains the isolated Blender workspace for the launcher car
animation.

- `source/`: downloaded glTF model, textures, and original license.
- `scripts/`: repeatable Blender render scripts.
- `output/`: rendered images and video files.
- `golf_mk5_preview.blend`: generated preview scene.

## Launcher video

The tracked lossless VP9 master is
`source/golf_mk5_transparent_7s_lossless.webm`. The app embeds a smaller CRF 32
copy at `launcher-app/src/main/assets/golf_mk5_transparent_7s_crf32.webm` (1024×600,
30 fps, with alpha). Rebuild that copy from the repository root with:

```sh
ffmpeg -y -c:v libvpx-vp9 -i rendering/golf_mk5/source/golf_mk5_transparent_7s_lossless.webm \
  -an -c:v libvpx-vp9 -pix_fmt yuva420p -lossless 0 -crf 32 -b:v 0 \
  -auto-alt-ref 0 -row-mt 1 -deadline good -cpu-used 4 \
  launcher-app/src/main/assets/golf_mk5_transparent_7s_crf32.webm
```

Run the first preview with:

```sh
blender -b --factory-startup --python scripts/render_preview.py
```

The source model is “Volkswagen golf 5 2.0 TDI” by bimboit34, licensed under
CC BY 4.0. The complete attribution text is preserved in `source/license.txt`.

## Transparent version for GPU rendering

Open `golf_mk5_studio_black.blend` in Blender 5.2.1 or newer. Textures are
packed into the blend file; no external texture paths are needed. The source
mesh has welded vertices and rebuilt smooth normals, refined tires, a rebuilt
closed roof without the sunroof, tinted dielectric window glass, clear headlamp
covers, reflective lamp interiors and rectangular studio reflection lights.
Body, window and alloy surfaces now reconstruct quad strips from the imported
triangles and use two levels of Catmull–Clark subdivision. Gentle relaxation
inside body panels reduces irregular curvature; panel boundaries and pronounced
folds are retained. Silver alloy wheels use a reflective satin finish and broad
low side softboxes. Rear red lenses retain their texture with a glossy clear coat.
Both license plates use `source/textures/GolfV-plate_blank-eu_baseColor.png`:
the registration letters/numbers are removed, retaining the blue European band,
yellow stars and inspection badges. The original source texture is preserved.
Film transparency is enabled with RGBA output, so the PNG frames contain the car
and its antialiased edges with alpha instead of a black world background.
Original detail textures are retained; the glass/paint appearance is controlled
by physical materials.

The animation is a looping 360-degree camera orbit: **7 seconds, 30 fps,
210 frames, 1024×600**, Cycles GPU, 128 samples with adaptive sampling and
denoising. The loop's next frame (211) matches frame 1 and is not exported.
The committed MP4 is the first rendering test; the car model, materials and
lighting still need further refinement before production use.
The saved scene uses production settings; `output/studio_black/preview.png`
is only a reduced-resolution CPU proof.

From this directory, on the GPU machine:

```sh
blender -b golf_mk5_studio_black.blend --python scripts/render_gpu.py
bash scripts/encode_alpha.sh
```

The GPU script selects a supported accelerator (OptiX/CUDA/HIP/Metal/oneAPI),
fails clearly if none is available, and resumes by skipping existing frames.
Encoding requires FFmpeg and creates
`output/studio_black/transparent-v1/golf_mk5_transparent_7s.webm` (VP9 with
alpha). MP4/H.264 does not preserve transparency. The shell script refuses to
overwrite an existing video. On Windows, render with Blender as above, then run:

```sh
ffmpeg -n -framerate 30 -start_number 1 -i output/studio_black/transparent-v1/frames/golf_%04d.png -frames:v 210 -an -c:v libvpx-vp9 -pix_fmt yuva420p -lossless 1 -auto-alt-ref 0 -row-mt 1 -deadline good -cpu-used 2 output/studio_black/transparent-v1/golf_mk5_transparent_7s.webm
```

Alternatively enable your GPU under Blender Preferences → System → Cycles
Render Devices, then use Render → Render Animation. Keep PNG frames for a
recoverable render and encode afterward. The transparent scene writes into
`transparent-v1/`, separate from the earlier black-background render.

To rebuild the scene from the original source (overwrites the generated black
scene), run `blender -b --factory-startup --python scripts/render_studio.py`.
Append `-- --preview` to also render a single CPU proof. The older preview
and studio scenes are retained separately.

To apply the same refinement to an existing black studio scene while retaining
its camera and animation, run:

```sh
blender -b golf_mk5_studio_black.blend --python scripts/refine_surfaces.py
```

Geometry refinement is tagged per object, so rerunning does not subdivide it
again. Material and wheel-light settings are refreshed on each run. A local
pre-edit backup is in `output/studio_black/before_surface_refinement.blend`.
Inspect four closer CPU stills (without changing the saved camera or render
settings) with:

```sh
blender -b golf_mk5_studio_black.blend --python scripts/check_surfaces.py
```

The resulting images are in `output/studio_black/surface_checks/`.

Credit the original model author in the application's asset credits and retain
`source/license.txt`; this version modifies geometry, materials and lighting.
