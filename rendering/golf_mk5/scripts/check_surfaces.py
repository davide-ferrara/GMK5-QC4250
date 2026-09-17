"""Render CPU inspection stills without changing the saved production scene.

blender -b golf_mk5_studio_black.blend --python scripts/check_surfaces.py
"""
from pathlib import Path
import bpy

scene = bpy.context.scene
output = Path(bpy.data.filepath).parent / "output" / "studio_black" / "surface_checks"
output.mkdir(parents=True, exist_ok=True)
scene.cycles.device = "CPU"
scene.cycles.samples = 48
scene.render.resolution_percentage = 100
scene.camera.data.lens = 85
for frame in (1, 60, 115, 170):
    scene.frame_set(frame)
    scene.render.filepath = str(output / f"surface_{frame:04d}.png")
    bpy.ops.render.render(write_still=True)
