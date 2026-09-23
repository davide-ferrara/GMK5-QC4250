"""Render the static lamp overlay without modifying the production scene.

blender -b golf_mk5_studio_black.blend --python scripts/render_lights_overlay.py
The camera is locked to frame 210, matching the end of transparent-v1.
"""
from pathlib import Path
import math
import sys

import bpy


ROOT = Path(__file__).resolve().parents[1]
scene = bpy.context.scene
top_view = "--top" in sys.argv
if top_view and (scene.camera is None or scene.camera.data.type != "ORTHO"):
    raise RuntimeError("Use golf_mk5_all_doors.blend for the top-view overlay")
scene.frame_set(210)
tailgate = bpy.data.objects.get("Tailgate hinge") if top_view else None
tailgate_open = "--tailgate-open" in sys.argv
if tailgate_open and tailgate is None:
    raise RuntimeError("The tailgate overlay requires golf_mk5_hatches_proof.blend --top")
if tailgate:
    scene.camera.data.ortho_scale = 5.8
    scene.camera.location.y = 0
    tailgate.rotation_euler.x = math.radians(85) if tailgate_open else 0
    bpy.data.objects["Hood hinge"].rotation_euler.x = 0
    bpy.context.view_layer.update()
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 32
scene.cycles.use_denoising = False
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "Standard"
scene.view_settings.look = "None"
scene.view_settings.exposure = 0
scene.view_settings.gamma = 1

# Keep the car as an occluder. Headlamp covers transmit the isolated light;
# bodywork and lamp housings cut holes in the overlay instead of being drawn.
for material in bpy.data.materials:
    material.use_nodes = True
    nodes = material.node_tree.nodes
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    if material.name == "GolfV-light_glas":
        shader = nodes.new("ShaderNodeEmission")
        shader.inputs["Color"].default_value = (1.0, 0.91, 0.72, 1.0)
    elif material.name == "GolfV-rear_red":
        shader = nodes.new("ShaderNodeEmission")
        shader.inputs["Color"].default_value = (0.8, 0.015, 0.008, 1.0)
    else:
        shader = nodes.new("ShaderNodeHoldout")
    material.node_tree.links.new(shader.outputs[0], output.inputs["Surface"])

destination = ROOT / "output" / "studio_black" / ("door-states-v2" if tailgate else "door-states-v1" if top_view else "transparent-v1") / "lights"
destination.mkdir(parents=True, exist_ok=True)
scene.render.filepath = str(destination / ("golf_top_tailgate_lights_overlay.png" if tailgate_open else "golf_top_lights_overlay.png" if top_view else "golf_lights_overlay.png"))
bpy.ops.render.render(write_still=True)
