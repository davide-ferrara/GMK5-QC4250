"""Refine the studio model, also usable on an existing scene without rebuilding it.

blender -b golf_mk5_studio_black.blend --python scripts/refine_surfaces.py
"""
import math
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector


SURFACES = {"GolfV-main_body", "GolfV-alu", "GolfV-glass", "GolfV-glass_blac"}
REVISION = "surfaces-v1"


def refine_surfaces():
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH" or not obj.data.materials:
            continue
        material = obj.data.materials[0].name
        if material not in SURFACES or obj.get("surface_revision") == REVISION:
            continue
        mesh = obj.data
        bm = bmesh.new()
        bm.from_mesh(mesh)
        before = len(bm.faces)
        # Reconstruct quad strips before subdivision: subdividing exported
        # triangles directly retains diagonal pinching in specular highlights.
        bmesh.ops.join_triangles(
            bm, faces=[f for f in bm.faces if len(f.verts) == 3],
            angle_face_threshold=math.radians(40),
            angle_shape_threshold=math.radians(60),
            cmp_seam=False, cmp_sharp=True, cmp_uvs=False, cmp_materials=True,
        )
        bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        if material == "GolfV-main_body":
            # Relax irregular source sampling inside panels, leaving panel gaps
            # and styling folds fixed. This addresses geometry, not just normals.
            interior = [v for v in bm.verts if v.link_edges and all(
                e.is_manifold and e.calc_face_angle(0) < math.radians(25)
                for e in v.link_edges)]
            for _ in range(3):
                bmesh.ops.smooth_vert(bm, verts=interior, factor=0.2,
                                      use_axis_x=True, use_axis_y=True, use_axis_z=True)
            bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
        crease = bm.edges.layers.float.get("crease_edge") or bm.edges.layers.float.new("crease_edge")
        for face in bm.faces:
            face.smooth = True
        for edge in bm.edges:
            # Open panel perimeters must stay in place beside lamps, seals and
            # adjoining panels. Retain intentional folds, not triangle diagonals.
            hard = not edge.is_manifold or edge.calc_face_angle(0) > math.radians(55)
            edge[crease] = 1.0 if hard else 0.0
            edge.smooth = not hard
        bm.to_mesh(mesh)
        bm.free()
        mesh.update()
        modifier = obj.modifiers.new("Continuous reflection surface", "SUBSURF")
        modifier.subdivision_type = "CATMULL_CLARK"
        modifier.levels = 2
        modifier.render_levels = 2
        modifier.show_only_control_edges = True
        # Refine the optical surface before generating glass thickness.
        obj.modifiers.move(len(obj.modifiers) - 1, 0)
        obj["surface_revision"] = REVISION
        print(f"Refined {obj.name} ({material}): {before} -> {len(mesh.polygons)} control faces")

    finishes = {
        "GolfV-alu": (0.82, 0.27, (0.65, 0.68, 0.72, 1)),
        "GolfV-alu_logo": (0.85, 0.23, None),
        "GolfV-wheel-insi": (0.8, 0.30, None),
        "GolfV-wheel-disc": (0.9, 0.32, None),
        "GolfV-plate": (0.0, 0.32, None),
    }
    for name, (metallic, roughness, color) in finishes.items():
        material = bpy.data.materials.get(name)
        if material is None or material.node_tree is None:
            continue
        shader = next(n for n in material.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
        values = {"Metallic": metallic, "Roughness": roughness,
                  "Coat Weight": 0.25, "Coat Roughness": 0.18}
        if color is not None:
            values["Base Color"] = color
        for name, value in values.items():
            socket = shader.inputs[name]
            for link in list(socket.links):
                material.node_tree.links.remove(link)
            socket.default_value = value
    plate = bpy.data.materials.get("GolfV-plate")
    if plate is not None and plate.node_tree is not None:
        nodes = plate.node_tree.nodes
        links = plate.node_tree.links
        shader = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
        texture = nodes.get("Blank EU plate") or nodes.new("ShaderNodeTexImage")
        texture.name = "Blank EU plate"
        path = Path(__file__).resolve().parents[1] / "source/textures/GolfV-plate_blank-eu_baseColor.png"
        texture.image = bpy.data.images.load(str(path), check_existing=True)
        texture.image.pack()
        uv = nodes.get("Plate UV") or nodes.new("ShaderNodeTexCoord")
        uv.name = "Plate UV"
        wrap = nodes.get("Plate UV repeat") or nodes.new("ShaderNodeVectorMath")
        wrap.name = "Plate UV repeat"
        wrap.operation = "FRACTION"
        crop = nodes.get("Plate image framing") or nodes.new("ShaderNodeVectorMath")
        crop.name = "Plate image framing"
        crop.operation = "MULTIPLY_ADD"
        # Exclude the generated image's white top/bottom margins in UV space.
        # The source plate islands also run vertically inverted.
        crop.inputs[1].default_value = (1, -0.772, 1)
        crop.inputs[2].default_value = (0, 0.886, 0)
        # Imported plate UVs occupy V=1..2; wrap before applying the crop.
        links.new(uv.outputs["UV"], wrap.inputs[0])
        links.new(wrap.outputs["Vector"], crop.inputs[0])
        links.new(crop.outputs["Vector"], texture.inputs["Vector"])
        links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    # The source texture includes the red lens and its internal details. Keep
    # that color map and give the outer polycarbonate a clear dielectric coat.
    for material_name in ("GolfV-rear_red", "GolfV-3stop"):
        material = bpy.data.materials.get(material_name)
        if material is None or material.node_tree is None:
            continue
        shader = next(n for n in material.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
        for name, value in {"Metallic": 0.0, "Roughness": 0.18,
                            "IOR": 1.49, "Coat Weight": 1.0,
                            "Coat Roughness": 0.065, "Coat IOR": 1.49}.items():
            socket = shader.inputs[name]
            for link in list(socket.links):
                material.node_tree.links.remove(link)
            socket.default_value = value
    # Silver needs broad, low studio reflections on the vertical wheel faces;
    # the overhead softboxes alone leave metal reflecting the black world.
    for side in (-1, 1):
        name = f"Silver wheel softbox {side:+d}"
        light = bpy.data.objects.get(name)
        if light is None:
            data = bpy.data.lights.new(name, "AREA")
            light = bpy.data.objects.new(name, data)
            bpy.context.collection.objects.link(light)
        light.location = (side * 4, -0.7, 1.6)
        light.rotation_euler = (Vector((0, 0, 0.45)) - light.location).to_track_quat("-Z", "Y").to_euler()
        light.data.energy = 125
        light.data.color = (1, 1, 1)
        light.data.shape = "RECTANGLE"
        light.data.size = 5
        light.data.size_y = 2.5
    bpy.context.scene["surface_revision"] = REVISION
    # Keep resumable rendering from silently reusing frames of the old mesh.
    bpy.context.scene.render.filepath = f"//output/studio_black/{REVISION}/frames/golf_"


if __name__ == "__main__":
    if not bpy.data.filepath:
        raise RuntimeError("Open the existing studio blend before refining it")
    refine_surfaces()
    bpy.ops.file.pack_all()
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
