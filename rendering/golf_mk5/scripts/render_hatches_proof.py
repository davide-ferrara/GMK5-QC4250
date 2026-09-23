"""Hood/tailgate visual proof; never changes the production scenes or CAN logic.

blender -b golf_mk5_all_doors.blend --python scripts/render_hatches_proof.py
Hinge locations and concealed compartment geometry are visual approximations.
"""
from pathlib import Path
import math
import sys
import bpy
import bmesh
from mathutils import Matrix, Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from door_geometry import components, parent_at_rest

scene = bpy.context.scene
for obj in scene.objects:
    if "door - hinge axis" in obj.name:
        obj.rotation_euler.z = 0
bpy.context.view_layer.update()

def pivot(name, position):
    obj = bpy.data.objects.new(name, None)
    scene.collection.objects.link(obj)
    obj.location = position
    obj.empty_display_type = "ARROWS"
    obj.empty_display_size = 0.15
    obj["note"] = "Estimated hinge position; rotation X = 0 closes the panel."
    bpy.context.view_layer.update()
    return obj

hood = pivot("Hood hinge", (0, -0.90, 1.01))
tailgate = pivot("Tailgate hinge", (0, 1.49, 1.44))

def destination(name, lo, hi):
    if name == "Object_19":
        if hi.y < -0.85 and lo.z > 0.70:
            return hood
        if lo.y > 1.70 and lo.z > 0.65:
            return tailgate
    if name == "Object_8" and lo.y > 1.45 and lo.z > 1.30:
        return tailgate
    if name in {"Object_25", "Object_26"} and lo.y > 1.5:
        return tailgate
    if name == "Object_37" and lo.y > 1.9:
        return tailgate
    if name == "Object_39" and lo.y > 1.5:
        return tailgate
    if name == "Object_40" and lo.y > 1.8 and max(abs(lo.x), abs(hi.x)) < 0.67:
        return tailgate
    if name == "Object_34" and lo.y > 1.9 and lo.z > 1.08:
        return tailgate
    return None

for name in ("Object_8", "Object_19", "Object_25", "Object_26", "Object_34", "Object_37", "Object_39", "Object_40"):
    bpy.context.view_layer.update()
    obj = bpy.data.objects[name]
    graph = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(graph), depsgraph=graph)
    mesh.transform(obj.matrix_world)
    bm = bmesh.new()
    bm.from_mesh(mesh)
    tag = bm.faces.layers.int.new("hatch_part")
    for group in components(bm):
        lo = Vector(min(v.co[i] for v in group) for i in range(3))
        hi = Vector(max(v.co[i] for v in group) for i in range(3))
        target = destination(name, lo, hi)
        if target:
            for vertex in group:
                for face in vertex.link_faces:
                    face[tag] = 1 if target == hood else 2
    for number, target in ((1, hood), (2, tailgate)):
        selected = [face for face in bm.faces if face[tag] == number]
        if not selected:
            continue
        part = bm.copy()
        part_tag = part.faces.layers.int["hatch_part"]
        bmesh.ops.delete(part, geom=[face for face in part.faces if face[part_tag] != number], context="FACES")
        part_mesh = bpy.data.meshes.new(name + " " + target.name)
        part.to_mesh(part_mesh)
        part.free()
        for material in mesh.materials:
            part_mesh.materials.append(material)
        panel = bpy.data.objects.new(part_mesh.name, part_mesh)
        scene.collection.objects.link(panel)
        parent_at_rest(panel, target)
        print("SEPARATED", panel.name, len(selected), "faces", flush=True)
        bmesh.ops.delete(bm, geom=selected, context="FACES")
    bm.to_mesh(mesh)
    bm.free()
    obj.parent = None
    obj.matrix_world = Matrix.Identity(4)
    obj.modifiers.clear()
    obj.data = mesh

def material(name, color):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    shader = mat.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*color, 1)
    shader.inputs["Roughness"].default_value = 0.85
    return mat

dark = material("Compartment dark lining", (0.012, 0.012, 0.011))
engine = material("Engine matte cover", (0.022, 0.024, 0.026))

def block(name, location, dimensions, mat, bevel=0.035):
    bpy.ops.mesh.primitive_cube_add(size=1, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = dimensions
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    modifier = obj.modifiers.new("Rounded edges", "BEVEL")
    modifier.width = bevel
    modifier.segments = 3
    return obj

# The source contains no detailed engine or luggage bay: recessed stand-ins
# prevent seeing through the body and keep this proof readable from above.
block("Engine bay floor", (0, -1.46, 0.48), (1.37, 1.09, 0.12), dark)
block("Engine cover - simplified", (0.05, -1.43, 0.68), (0.70, 0.58, 0.26), engine)
block("Battery housing - simplified", (0.49, -1.19, 0.69), (0.27, 0.35, 0.23), dark)
block("Airbox - simplified", (-0.48, -1.50, 0.65), (0.29, 0.49, 0.23), dark)
block("Luggage bay floor", (0, 1.45, 0.57), (1.24, 0.91, 0.14), dark)
for side in (-1, 1):
    block("Luggage bay side lining", (side * 0.61, 1.45, 0.78), (0.10, 0.9, 0.38), dark)
block("Luggage rear sill", (0, 1.94, 0.65), (1.28, 0.09, 0.17), dark)

for target in (hood, tailgate):
    assert target.children, f"No geometry separated for {target.name}"
    for panel in target.children:
        if panel.data.materials and panel.data.materials[0].name == "GolfV-main_body":
            panel.data.materials.append(dark)
            shell = panel.modifiers.new("Panel underside", "SOLIDIFY")
            shell.thickness = 0.018
            shell.offset = -1
            shell.material_offset = 1

hood.rotation_euler.x = 0
tailgate.rotation_euler.x = math.radians(85)

# Softer lens highlights: the previous fully coated red plastic washed out
# under the studio softboxes, especially when tilted with the open tailgate.
lamp_settings = {
    "GolfV-rear_red": {"Roughness": 0.30, "Coat Weight": 0.18,
                       "Coat Roughness": 0.24, "Specular IOR Level": 0.28},
    "GolfV-light_glas": {"Roughness": 0.10, "Specular IOR Level": 0.35},
    "GolfV-light_lens": {"Roughness": 0.12, "Specular IOR Level": 0.35},
    "GolfV-light_mirr": {"Roughness": 0.28, "Base Color": (0.48, 0.50, 0.52, 1)},
}
for name, settings in lamp_settings.items():
    mat = bpy.data.materials.get(name)
    if mat is None or mat.node_tree is None:
        continue
    for node in mat.node_tree.nodes:
        if node.type != "BSDF_PRINCIPLED":
            continue
        for key, value in settings.items():
            socket = node.inputs[key]
            for link in list(socket.links):
                mat.node_tree.links.remove(link)
            socket.default_value = value

# The source lens photograph already contains studio glare. Lower its baked
# brightness without repainting the texture or losing the circular details.
rear_material = bpy.data.materials["GolfV-rear_red"]
for node in list(rear_material.node_tree.nodes):
    if node.type != "BSDF_PRINCIPLED":
        continue
    color = node.inputs["Base Color"]
    if color.links:
        source = color.links[0].from_socket
        tint = rear_material.node_tree.nodes.new("ShaderNodeMixRGB")
        tint.name = "Reduce photographed glare in red lenses"
        tint.blend_type = "MULTIPLY"
        tint.inputs[0].default_value = 1.0
        tint.inputs[2].default_value = (0.18, 0.12, 0.12, 1)
        rear_material.node_tree.links.new(source, tint.inputs[1])
        rear_material.node_tree.links.new(tint.outputs[0], color)
scene.camera.data.ortho_scale = 6.4
scene.camera.location.y = 0.20
scene.cycles.samples = 128
scene.render.use_persistent_data = False
output = ROOT / "output" / "studio_black" / "tailgate-proof-v2"
output.mkdir(parents=True, exist_ok=True)
scene.render.filepath = str(output / "golf_tailgate_top.png")
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "golf_mk5_hatches_proof.blend"))
bpy.ops.render.render(write_still=True)
