"""Mirror the approved driver-side doors and openings into a four-door proof.

blender -b golf_mk5_door_proof.blend --python scripts/render_all_doors.py
"""
from pathlib import Path
import bpy
import bmesh
from mathutils import Matrix

ROOT = Path(__file__).resolve().parents[1]
scene = bpy.context.scene
reflection = Matrix.Diagonal((-1.0, 1.0, 1.0, 1.0))

# Reuse the open side of the affected body meshes. The fixed roof and pillars
# are already symmetric and stay intact. Remove the still-closed other side.
for name in ("Object_14", "Object_15", "Object_16", "Object_25", "Object_34", "Object_41"):
    obj = bpy.data.objects[name]
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), depsgraph=depsgraph)
    mesh.transform(obj.matrix_world)
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.bisect_plane(
        bm, geom=list(bm.verts) + list(bm.edges) + list(bm.faces),
        plane_co=(0, 0, 0), plane_no=(1, 0, 0), dist=0.000001,
        clear_inner=True,
    )
    bm.to_mesh(mesh)
    bm.free()
    obj.parent = None
    obj.matrix_world = Matrix.Identity(4)
    obj.modifiers.clear()
    obj.data = mesh
    mirror = obj.modifiers.new("Opposite side from approved open side", "MIRROR")
    mirror.use_axis[0] = True
    mirror.use_clip = True
    mirror.merge_threshold = 0.00001

# The closed passenger mirror is replaced by the mirrored open assembly.
for name in ("Object_48", "Object_49", "Object_50", "Object_51", "Object_52"):
    obj = bpy.data.objects.get(name)
    if obj:
        bpy.data.objects.remove(obj, do_unlink=True)

bpy.context.view_layer.update()
for position in ("front", "rear"):
    source = bpy.data.objects[f"Driver {position} door - hinge axis"]
    target = bpy.data.objects.new(f"Passenger {position} door - hinge axis", None)
    scene.collection.objects.link(target)
    target.location = (-source.location.x, source.location.y, source.location.z)
    target.rotation_euler.z = -source.rotation_euler.z
    target.empty_display_type = "ARROWS"
    target.empty_display_size = 0.15
    target["description"] = "Mirrored driver door. Rotation Z = 0 closes it."
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for child in list(source.children):
        evaluated = child.evaluated_get(depsgraph)
        mesh = bpy.data.meshes.new_from_object(evaluated, depsgraph=depsgraph)
        mesh.transform(reflection @ child.matrix_world)
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.reverse_faces(bm, faces=list(bm.faces))
        bm.to_mesh(mesh)
        bm.free()
        duplicate = bpy.data.objects.new(child.name.replace("Driver", "Passenger"), mesh)
        scene.collection.objects.link(duplicate)
        duplicate.parent = target
        duplicate.matrix_parent_inverse = target.matrix_world.inverted()
        duplicate.matrix_basis = Matrix.Identity(4)

bpy.context.view_layer.update()
destination = ROOT / "output" / "studio_black" / "all-doors-v1"
destination.mkdir(parents=True, exist_ok=True)
scene.render.filepath = str(destination / "golf_all_doors_top.png")
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "golf_mk5_all_doors.blend"))
bpy.ops.render.render(write_still=True)
