"""Separate both driver-side doors in the working copy and render from above.

blender -b golf_mk5_studio_black.blend --python scripts/render_door_proof.py
"""
from pathlib import Path
import math
import sys
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from door_geometry import separate_door
from door_window_frames import glazing_boundaries, add_frames

scene = bpy.context.scene
scene.frame_set(210)
window_outlines = glazing_boundaries()

def planes(values):
    return [(Vector(origin), Vector(normal)) for origin, normal in values]

front = separate_door("Driver front door", (0.835, -0.825, 0.72), planes([
    ((0, -0.795, 0), (0, 1, 0)),
    ((0, -0.795, 1.0), (0, 1, -1.25)),
    ((0, 0.254, 0.32), (0, -1, 0.0564)),
    ((0, 0, 0.32), (0, 0, 1)),
    ((0, 0, 1.50), (0, 0, -1)),
]), -0.85, 0.33, include_mirror=True)

rear = separate_door("Driver rear door", (0.835, 0.265, 0.72), planes([
    ((0, 0.254, 0.32), (0, 1, -0.0564)),
    ((0, 1.32, 0), (0, -1, 0)),
    ((0, 1.32, 1.04), (0, -1, -0.53)),
    # The lower panel is a whole original component; leave the fixed sill
    # and wheel arch alone when separating the shared upper frame surfaces.
    ((0, 0, 0.98), (0, 0, 1)),
    ((0, 0, 1.50), (0, 0, -1)),
]), 0.24, 1.33)
add_frames(window_outlines["front"], front)
add_frames(window_outlines["rear"], rear)

# Blender shader colors are scene-linear, whereas the requested hex is sRGB.
def linear(byte):
    value = byte / 255.0
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4

interior_color = tuple(linear(channel) for channel in (0x4b, 0x4a, 0x48)) + (1.0,)
interior = bpy.data.materials["GolfV-inside"]
interior.diffuse_color = interior_color
for node in interior.node_tree.nodes:
    if node.type == "BSDF_PRINCIPLED":
        socket = node.inputs["Base Color"]
        for link in list(socket.links):
            interior.node_tree.links.remove(link)
        socket.default_value = interior_color
        node.inputs["Metallic"].default_value = 0
        node.inputs["Roughness"].default_value = 0.85
interior["base_color_srgb"] = "#4b4a48"
# The exterior studio softboxes overexpose the newly exposed cabin. Blend a
# stable base-color response with its shaded surface for this UI illustration;
# retain some illumination/shape without turning dark upholstery silver.
nodes = interior.node_tree.nodes
links = interior.node_tree.links
output = next(node for node in nodes if node.type == "OUTPUT_MATERIAL" and node.is_active_output)
surface = output.inputs["Surface"].links[0].from_socket
base = nodes.new("ShaderNodeEmission")
base.name = "Interior color under studio lights"
base.inputs["Color"].default_value = interior_color
base.inputs["Strength"].default_value = 1.0
mix = nodes.new("ShaderNodeMixShader")
mix.inputs[0].default_value = 1.0
occlusion = nodes.new("ShaderNodeAmbientOcclusion")
occlusion.name = "Cabin contact shadows"
occlusion.inputs["Color"].default_value = interior_color
occlusion.inputs["Distance"].default_value = 0.45
occlusion.samples = 16
links.new(occlusion.outputs["Color"], base.inputs["Color"])
base.inputs["Strength"].default_value = 0.5
links.new(surface, mix.inputs[1])
links.new(base.outputs[0], mix.inputs[2])
links.new(mix.outputs[0], output.inputs["Surface"])

front.rotation_euler.z = math.radians(-58)
rear.rotation_euler.z = math.radians(-58)
bpy.context.view_layer.update()

camera_data = bpy.data.cameras.new("Driver doors top camera")
camera = bpy.data.objects.new("Driver doors top camera", camera_data)
scene.collection.objects.link(camera)
camera.location = (0, 0, 12)
camera.rotation_euler = (0, 0, math.pi)
camera_data.type = "ORTHO"
camera_data.ortho_scale = 5.8
scene.camera = camera
scene.render.engine = "CYCLES"
scene.cycles.device = "CPU"
scene.cycles.samples = 128
scene.cycles.use_denoising = True
scene.render.resolution_x = 1024
scene.render.resolution_y = 1024
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
destination = ROOT / "output" / "studio_black" / "door-proof-v4"
destination.mkdir(parents=True, exist_ok=True)
scene.render.filepath = str(destination / "golf_driver_doors_top.png")
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "golf_mk5_door_proof.blend"))
bpy.ops.render.render(write_still=True)
