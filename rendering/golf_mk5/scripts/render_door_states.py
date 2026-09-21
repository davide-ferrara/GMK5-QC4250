"""Export all 16 door states from golf_mk5_all_doors.blend; do not save it.

blender -b golf_mk5_all_doors.blend --python scripts/render_door_states.py
Bits: 1 driver front, 2 passenger front, 4 driver rear, 8 passenger rear.
"""
from pathlib import Path
import math
import bpy

ROOT = Path(__file__).resolve().parents[1]
destination = ROOT / "output" / "studio_black" / "door-states-v1"
destination.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
hinges = [
    (1, bpy.data.objects["Driver front door - hinge axis"], -58),
    (2, bpy.data.objects["Passenger front door - hinge axis"], 58),
    (4, bpy.data.objects["Driver rear door - hinge axis"], -58),
    (8, bpy.data.objects["Passenger rear door - hinge axis"], 58),
]
scene.render.use_persistent_data = True
for mask in range(16):
    for bit, hinge, angle in hinges:
        hinge.rotation_euler.z = math.radians(angle) if mask & bit else 0
    bpy.context.view_layer.update()
    scene.render.filepath = str(destination / f"golf_top_{mask:02d}.png")
    print(f"Rendering door state {mask:02d}/15", flush=True)
    bpy.ops.render.render(write_still=True)
