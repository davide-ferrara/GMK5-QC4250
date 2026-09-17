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
`output/studio_black/golf_mk5_black_7s.mp4` (H.264, yuv420p, no audio,
fast-start). The shell script refuses to overwrite an existing video.
On Windows, render with Blender as above, then run this FFmpeg command:

```sh
ffmpeg -n -framerate 30 -start_number 1 -i output/studio_black/frames/golf_%04d.png -frames:v 210 -an -c:v libx264 -preset slow -crf 18 -profile:v high -level:v 3.1 -pix_fmt yuv420p -movflags +faststart output/studio_black/golf_mk5_black_7s.mp4
```

Alternatively enable your GPU under Blender Preferences → System → Cycles
Render Devices, then use Render → Render Animation. Keep PNG frames for a
recoverable render and encode afterward. Do not mix frames from the old studio
scene with the black-background version.

To rebuild the scene from the original source (overwrites the generated black
scene), run `blender -b --factory-startup --python scripts/render_studio.py`.
Append `-- --preview` to also render a single CPU proof. The older preview
and studio scenes are retained separately.

Credit the original model author in the application's asset credits and retain
`source/license.txt`; this version modifies geometry, materials and lighting.
