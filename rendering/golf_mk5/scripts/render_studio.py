"""Rebuild imported normals and render a seven-second, seamless studio loop.

blender -b --factory-startup --python scripts/render_studio.py -- --preview
blender -b golf_mk5_studio_black.blend --python scripts/render_gpu.py
"""
from pathlib import Path
import argparse
import math
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
import render_preview
namespace = vars(render_preview)
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "studio_black"
look_at = namespace["look_at"]
set_input = namespace["set_input"]
principled = namespace["principled"]


def repair_normals():
    """Weld export splits before rebuilding angle-limited surface normals."""
    for obj in list(bpy.context.scene.objects):
        if obj.type != "MESH":
            continue
        mesh = obj.data
        bpy.context.view_layer.objects.active = obj
        if mesh.has_custom_normals:
            bpy.ops.mesh.customdata_custom_splitnormals_clear()
        bm = bmesh.new()
        bm.from_mesh(mesh)
        # Local model dimensions vary; this threshold only joins coincident vertices.
        extent = max((max(v.co[i] for v in bm.verts) - min(v.co[i] for v in bm.verts)) for i in range(3))
        bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=max(extent * 0.000001, 0.0000001))
        bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        for face in bm.faces:
            face.smooth = True
        for edge in bm.edges:
            edge.smooth = edge.is_manifold and edge.calc_face_angle(0) < math.radians(55)
        bm.to_mesh(mesh)
        bm.free()
        mesh.update()
        material = mesh.materials[0].name if mesh.materials else ""
        if material == "GolfV-tire":
            modifier = obj.modifiers.new("Round tire silhouette", "SUBSURF")
            modifier.levels = 1
            modifier.render_levels = 2


def finish_materials():
    for mat in bpy.data.materials:
        if not mat.use_nodes:
            continue
        shader = principled(mat)
        if shader is None:
            continue
        name = mat.name
        if name == "GolfV-main_body":
            set_input(shader, "Base Color", (0.36, 0.39, 0.43, 1))
            set_input(shader, "Metallic", 0.8)
            set_input(shader, "Roughness", 0.27)
            set_input(shader, "Coat Weight", 0.45)
            set_input(shader, "Coat Roughness", 0.19)
        elif name == "GolfV-glass":
            set_input(shader, "Base Color", (0.48, 0.56, 0.59, 1))
            set_input(shader, "Metallic", 0.0)
            set_input(shader, "Roughness", 0.055)
            set_input(shader, "Transmission Weight", 1.0)
            set_input(shader, "Alpha", 1.0)
            set_input(shader, "IOR", 1.52)
        elif name == "GolfV-glass_blac":
            set_input(shader, "Base Color", (0.008, 0.011, 0.013, 1))
            set_input(shader, "Metallic", 0.0)
            set_input(shader, "Roughness", 0.08)
            set_input(shader, "Transmission Weight", 0.0)
            set_input(shader, "Alpha", 1.0)
            set_input(shader, "Coat Weight", 0.8)
        elif name in {"GolfV-light_glas", "GolfV-light_lens"}:
            set_input(shader, "Base Color", (0.97, 0.985, 1.0, 1))
            set_input(shader, "Metallic", 0.0)
            set_input(shader, "Roughness", 0.025)
            set_input(shader, "Transmission Weight", 1.0)
            set_input(shader, "Alpha", 1.0)
            set_input(shader, "IOR", 1.49)
        elif name == "GolfV-light_mirr":
            set_input(shader, "Base Color", (0.82, 0.85, 0.89, 1))
            set_input(shader, "Metallic", 1.0)
            set_input(shader, "Roughness", 0.10)
        elif name in {"GolfV-inside", "GolfV-dark_plast", "GolfV-rubber", "GolfV-black"}:
            set_input(shader, "Base Color", (0.018, 0.021, 0.024, 1))
            set_input(shader, "Roughness", 0.55)
            set_input(shader, "Metallic", 0)
        elif name in {"GolfV-tire", "GolfV-tire-rel"}:
            set_input(shader, "Base Color", (0.013, 0.015, 0.017, 1))
            set_input(shader, "Roughness", 0.64)
            set_input(shader, "Metallic", 0)
        elif name in {"GolfV-alu", "GolfV-chrome", "GolfV-light_mirr"}:
            set_input(shader, "Metallic", 0.88)
            set_input(shader, "Roughness", 0.24)


def components(bm):
    remaining = set(bm.verts)
    while remaining:
        stack = [remaining.pop()]
        group = set(stack)
        while stack:
            vertex = stack.pop()
            neighbors = {e.other_vert(vertex) for e in vertex.link_edges} & remaining
            remaining.difference_update(neighbors)
            group.update(neighbors)
            stack.extend(neighbors)
        yield group


def remove_sunroof():
    """Remove glazing/seal and reconstruct the opening in the existing roof mesh."""
    for name in ("Object_25", "Object_11"):
        obj = bpy.data.objects[name]
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        for group in list(components(bm)):
            points = [obj.matrix_world @ v.co for v in group]
            if all(abs(p.x) < 0.44 and -0.07 < p.y < 0.40 and p.z > 1.42 for p in points):
                bmesh.ops.delete(bm, geom=list(group), context="VERTS")
        bm.to_mesh(obj.data)
        bm.free()
    obj = bpy.data.objects["Object_8"]
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    roof = next(g for g in components(bm) if len(g) > 300 and all((obj.matrix_world @ v.co).z > 1.37 for v in g))
    normal_matrix = obj.matrix_world.to_3x3().inverted().transposed()
    def basis(p):
        return [1, p.x, p.y, p.x*p.x, p.x*p.y, p.y*p.y,
                p.y**3, p.y**4, p.x*p.x*p.y, p.x*p.x*p.y*p.y]
    samples = []
    for v in roof:
        p = obj.matrix_world @ v.co
        n = (normal_matrix @ v.normal).normalized()
        if n.z > 0.75 and not (abs(p.x) < 0.46 and -0.09 < p.y < 0.43):
            samples.append(p)
    coefficients = np.linalg.lstsq(np.array([basis(p) for p in samples]), np.array([p.z for p in samples]), rcond=None)[0]
    # Replace the complete roof skin so the old recessed aperture cannot leave
    # a rectangular highlight/seam. Match its measured footprint and crown.
    bmesh.ops.delete(bm, geom=list(roof), context="VERTS")
    inverse = obj.matrix_world.inverted()
    grid = []
    for row in range(65):
        y = -0.30 + (1.474 + 0.30) * row / 64
        line = []
        for col in range(49):
            x = 0.555 * (2 * col / 48 - 1)
            p = Vector((x, y, 0))
            p.z = float(np.dot(basis(p), coefficients))
            line.append(bm.verts.new(inverse @ p))
        grid.append(line)
    for row in range(64):
        for col in range(48):
            bm.faces.new((grid[row][col], grid[row][col+1], grid[row+1][col+1], grid[row+1][col]))
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    for f in bm.faces:
        f.smooth = True
    for e in bm.edges:
        if all((obj.matrix_world @ v.co).z > 1.42 for v in e.verts):
            e.smooth = True
    bm.to_mesh(obj.data)
    bm.free()
    obj.data.update()
    print(f"Sunroof removed; rebuilt roof using {len(samples)} surface samples")


def glass_thickness():
    for obj in bpy.data.objects:
        if obj.type != "MESH" or not obj.data.materials:
            continue
        name = obj.data.materials[0].name
        if name not in {"GolfV-glass", "GolfV-light_glas", "GolfV-light_lens"}:
            continue
        mod = obj.modifiers.new("Optical glass thickness", "SOLIDIFY")
        mod.thickness = (0.003 if name == "GolfV-glass" else 0.0015) / obj.matrix_world.to_scale().x
        mod.offset = -1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", action="store_true")
    args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
    namespace["clear_scene"]()
    namespace["import_and_normalize_car"]()
    repair_normals()
    bpy.context.view_layer.update()
    remove_sunroof()
    finish_materials()
    glass_thickness()
    add_area = namespace["add_area"]
    add_area("Large front softbox", (-3, -4, 6), 1050, 5, (1, 0.96, 0.91))
    add_area("Side softbox", (4, 0, 4.5), 800, 5, (0.86, 0.92, 1))
    add_area("Roof softbox", (0, 2, 6), 1100, 4, (1, 1, 1))
    add_area("Edge separation", (-4, 3, 3), 650, 3, (0.65, 0.78, 1))
    for name, location, energy, width, height in [
        ("Windshield reflection strip", (-3, 1.5, 4), 100, 4, 0.6),
        ("Window reflection strip", (4, 4, 3.5), 80, 3.5, 0.35),
        ("Headlamp reflection strip", (-3, -4, 0.9), 80, 2, 0.18),
    ]:
        light = add_area(name, location, energy, width, (1, 1, 1))
        light.data.shape = "RECTANGLE"
        light.data.size_y = height
    camera_data = bpy.data.cameras.new("Studio camera")
    camera = bpy.data.objects.new("Studio camera", camera_data)
    bpy.context.collection.objects.link(camera)
    camera_data.lens = 62
    scene = bpy.context.scene
    scene.camera = camera
    scene.render.fps = 30
    scene.frame_start = 1
    scene.frame_end = 210
    # One complete orbit. Frame 211 matches frame 1 and supplies matching
    # position and velocity across the loop boundary, but is not rendered.
    for frame in range(1, 212):
        phase = 2 * math.pi * (frame - 1) / 210
        angle = math.radians(42) + phase
        radius = 9.7
        camera.location = (radius * math.sin(angle), -radius * math.cos(angle), 2.45)
        look_at(camera, (0, 0, 0.82))
        camera.keyframe_insert(data_path="location", frame=frame)
        camera.keyframe_insert(data_path="rotation_euler", frame=frame)
    world = bpy.data.worlds.new("Charcoal studio")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0, 0, 0, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0
    scene.world = world
    scene.render.engine = "CYCLES"
    scene.cycles.device = "GPU"
    scene.cycles.samples = 128
    scene.cycles.use_denoising = True
    scene.cycles.use_adaptive_sampling = True
    scene.cycles.adaptive_threshold = 0.01
    scene.cycles.max_bounces = 12
    scene.cycles.transmission_bounces = 8
    scene.cycles.diffuse_bounces = 2
    scene.cycles.glossy_bounces = 3
    scene.render.use_persistent_data = True
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 600
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGB"
    scene.view_settings.look = "AgX - Medium High Contrast"
    OUTPUT.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = "//output/studio_black/frames/golf_"
    (OUTPUT / "frames").mkdir(exist_ok=True)
    scene.frame_set(1)
    bpy.ops.file.pack_all()
    bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "golf_mk5_studio_black.blend"))
    if args.preview:
        scene.cycles.device = "CPU"
        scene.cycles.samples = 32
        scene.render.resolution_percentage = 75
        scene.render.filepath = str(OUTPUT / "preview.png")
        bpy.ops.render.render(write_still=True)


main()
