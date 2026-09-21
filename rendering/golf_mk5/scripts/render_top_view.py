"""Render a transparent, orthographic top view without changing the .blend.

From rendering/golf_mk5:
blender -b golf_mk5_studio_black.blend --python scripts/render_top_view.py
"""
from pathlib import Path
import math

import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[1]
scene = bpy.context.scene
scene.frame_set(210)

# Frame the model independently of the animated hero camera. Leave room on
# both sides for possible open-door overlays, with the nose pointing upward.
car_meshes = [obj for obj in scene.objects if obj.type == "MESH" and obj.name != "Studio floor"]
points = [obj.matrix_world @ Vector(corner) for obj in car_meshes for corner in obj.bound_box]
low = Vector(min(point[axis] for point in points) for axis in range(3))
high = Vector(max(point[axis] for point in points) for axis in range(3))
center = (low + high) * 0.5
turntable = bpy.data.objects.get("Golf_turntable")
heading = turntable.rotation_euler.z if turntable else 0.0

camera_data = bpy.data.cameras.new("Top view camera")
camera = bpy.data.objects.new("Top view camera", camera_data)
scene.collection.objects.link(camera)
camera.location = (center.x, center.y, high.z + 10.0)
camera.rotation_euler = (0.0, 0.0, math.pi + heading)
camera_data.type = "ORTHO"
camera_data.ortho_scale = 5.8
scene.camera = camera

floor = bpy.data.objects.get("Studio floor")
if floor:
    floor.hide_render = True

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
scene.render.image_settings.color_depth = "8"
scene.render.use_border = False

destination = ROOT / "output" / "studio_black" / "top-view-v1"
destination.mkdir(parents=True, exist_ok=True)
scene.render.filepath = str(destination / "golf_top_view.png")
bpy.ops.render.render(write_still=True)
