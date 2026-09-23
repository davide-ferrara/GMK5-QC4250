"""Export complete opening states without saving the input scene.

blender -b golf_mk5_all_doors.blend --python scripts/render_door_states.py
blender -b golf_mk5_hatches_proof.blend --python scripts/render_door_states.py -- --tailgate
Bits: 1 driver front, 2 passenger front, 4 driver rear, 8 passenger rear.
With --tailgate, bit 16 opens the tailgate and 32 states are exported.
"""
from pathlib import Path
import math
import sys
import bpy

ROOT = Path(__file__).resolve().parents[1]
with_tailgate = "--tailgate" in sys.argv
destination = ROOT / "output" / "studio_black" / ("door-states-v2" if with_tailgate else "door-states-v1")
destination.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
if with_tailgate:
    scene.camera.data.ortho_scale = 5.8
    scene.camera.location.y = 0
    bpy.data.objects["Hood hinge"].rotation_euler.x = 0
hinges = [
    (1, bpy.data.objects["Driver front door - hinge axis"], -58),
    (2, bpy.data.objects["Passenger front door - hinge axis"], 58),
    (4, bpy.data.objects["Driver rear door - hinge axis"], -58),
    (8, bpy.data.objects["Passenger rear door - hinge axis"], 58),
]
scene.render.use_persistent_data = True
for mask in range(32 if with_tailgate else 16):
    for bit, hinge, angle in hinges:
        hinge.rotation_euler.z = math.radians(angle) if mask & bit else 0
    if with_tailgate:
        bpy.data.objects["Tailgate hinge"].rotation_euler.x = math.radians(85) if mask & 16 else 0
    bpy.context.view_layer.update()
    scene.render.filepath = str(destination / f"golf_top_{mask:02d}.png")
    print(f"Rendering door state {mask:02d}", flush=True)
    bpy.ops.render.render(write_still=True)
