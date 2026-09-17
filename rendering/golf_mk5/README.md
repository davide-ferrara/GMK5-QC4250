# Golf Mk5 render workspace

This directory contains the isolated Blender workspace for the launcher car
animation.

- `source/`: downloaded glTF model, textures, and original license.
- `scripts/`: repeatable Blender render scripts.
- `output/`: rendered images and video files.
- `golf_mk5_preview.blend`: generated preview scene.

Run the first preview with:

```sh
blender -b --factory-startup --python scripts/render_preview.py
```

The source model is “Volkswagen golf 5 2.0 TDI” by bimboit34, licensed under
CC BY 4.0. The complete attribution text is preserved in `source/license.txt`.

## Black background version for GPU rendering

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
The world is pure black with no visible floor. Original detail textures are
retained; the glass/paint appearance is controlled by physical materials.

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
bash scripts/encode_studio.sh
```

The GPU script selects a supported accelerator (OptiX/CUDA/HIP/Metal/oneAPI),
fails clearly if none is available, and resumes by skipping existing frames.
Encoding requires FFmpeg and creates
`output/studio_black/surfaces-v1/golf_mk5_black_7s.mp4` (H.264, yuv420p, no audio,
fast-start). The shell script refuses to overwrite an existing video.
On Windows, render with Blender as above, then run this FFmpeg command:

```sh
ffmpeg -n -framerate 30 -start_number 1 -i output/studio_black/surfaces-v1/frames/golf_%04d.png -frames:v 210 -an -c:v libx264 -preset slow -crf 18 -profile:v high -level:v 3.1 -pix_fmt yuv420p -movflags +faststart output/studio_black/surfaces-v1/golf_mk5_black_7s.mp4
```

Alternatively enable your GPU under Blender Preferences → System → Cycles
Render Devices, then use Render → Render Animation. Keep PNG frames for a
recoverable render and encode afterward. Do not mix frames from the old studio
scene with the black-background version.
The refined scene writes into `surfaces-v1/` so the resume logic cannot reuse
frames from the previous mesh. The earlier MP4 remains the previous rendering.

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
