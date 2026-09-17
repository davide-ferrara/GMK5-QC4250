from pathlib import Path
import math

import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "source" / "scene.gltf"
OUTPUT = ROOT / "output"


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for datablocks in (bpy.data.materials, bpy.data.cameras, bpy.data.lights):
        for datablock in list(datablocks):
            if datablock.users == 0:
                datablocks.remove(datablock)


def look_at(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def principled(material):
    return next(
        (node for node in material.node_tree.nodes if node.type == "BSDF_PRINCIPLED"),
        None,
    )


def set_input(shader, name, value):
    socket = shader.inputs.get(name)
    if socket is None:
        return
    for link in list(socket.links):
        shader.id_data.links.remove(link)
    socket.default_value = value


def make_material(name, color, metallic=0.0, roughness=0.45):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    shader = principled(material)
    set_input(shader, "Base Color", (*color, 1.0))
    set_input(shader, "Metallic", metallic)
    set_input(shader, "Roughness", roughness)
    return material


def model_bounds(objects):
    points = [
        obj.matrix_world @ Vector(corner)
        for obj in objects
        if obj.type == "MESH"
        for corner in obj.bound_box
    ]
    low = Vector(min(point[i] for point in points) for i in range(3))
    high = Vector(max(point[i] for point in points) for i in range(3))
    return low, high


def import_and_normalize_car():
    before = set(bpy.context.scene.objects)
    bpy.ops.import_scene.gltf(filepath=str(SOURCE))
    imported = [obj for obj in bpy.context.scene.objects if obj not in before]
    roots = [obj for obj in imported if obj.parent is None]

    low, high = model_bounds(imported)
    scale = 4.20 / (high.y - low.y)
    center = (low + high) * 0.5

    turntable = bpy.data.objects.new("Golf_turntable", None)
    bpy.context.collection.objects.link(turntable)
    for root in roots:
        matrix = root.matrix_world.copy()
        root.parent = turntable
        root.matrix_world = matrix

    turntable.scale = (scale, scale, scale)
    turntable.location = (-scale * center.x, -scale * center.y, -scale * low.z)

    body = bpy.data.materials.get("GolfV-main_body")
    if body and body.use_nodes:
        shader = principled(body)
        set_input(shader, "Base Color", (0.56, 0.59, 0.62, 1.0))
        set_input(shader, "Metallic", 0.72)
        set_input(shader, "Roughness", 0.22)
        set_input(shader, "Coat Weight", 0.38)
        set_input(shader, "Coat Roughness", 0.12)

    return turntable


def add_studio_floor():
    bpy.ops.mesh.primitive_plane_add(size=30.0, location=(0.0, 0.0, 0.0))
    floor = bpy.context.object
    floor.name = "Studio floor"
    floor.data.materials.append(
        make_material("Studio charcoal", (0.025, 0.032, 0.042), roughness=0.3)
    )


def add_area(name, location, energy, size, color, target=(0.0, 0.0, 0.8)):
    data = bpy.data.lights.new(name, "AREA")
    data.energy = energy
    data.shape = "DISK"
    data.size = size
    data.color = color
    obj = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(obj)
    obj.location = location
    look_at(obj, target)
    return obj


def build_scene():
    clear_scene()
    car = import_and_normalize_car()
    car.rotation_euler.z = math.radians(-4.0)
    add_studio_floor()

    add_area("Key", (-4.0, -3.5, 6.0), 950, 5.0, (0.78, 0.88, 1.0))
    add_area("Fill", (4.5, -1.5, 3.3), 620, 4.0, (0.45, 0.62, 1.0))
    add_area("Roof strip", (0.0, 1.5, 6.0), 850, 4.5, (1.0, 0.96, 0.9))
    add_area("Rear rim", (-3.5, 4.0, 3.0), 900, 3.0, (0.2, 0.48, 1.0))

    camera_data = bpy.data.cameras.new("Hero camera")
    camera = bpy.data.objects.new("Hero camera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera.location = (6.2, -8.0, 2.15)
    camera_data.lens = 62
    camera_data.sensor_width = 36
    look_at(camera, (0.0, 0.0, 0.83))
    bpy.context.scene.camera = camera

    world = bpy.data.worlds.new("Studio world")
    world.use_nodes = True
    background = world.node_tree.nodes.get("Background")
    background.inputs["Color"].default_value = (0.008, 0.012, 0.02, 1.0)
    background.inputs["Strength"].default_value = 0.16
    bpy.context.scene.world = world

    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 24
    scene.cycles.use_denoising = True
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 600
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = False
    scene.render.filepath = str(OUTPUT / "golf_preview.png")
    scene.render.image_settings.color_depth = "8"

    scene.view_settings.look = "AgX - Medium High Contrast"
    scene.render.resolution_percentage = 100

    OUTPUT.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "golf_mk5_preview.blend"))
    bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    build_scene()
