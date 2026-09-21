"""Separate door surfaces in world coordinates, preserving the closed shape."""
import bpy
import bmesh
from mathutils import Matrix


def components(bm):
    remaining = set(bm.verts)
    while remaining:
        stack = [remaining.pop()]
        group = set(stack)
        while stack:
            vertex = stack.pop()
            neighbors = {edge.other_vert(vertex) for edge in vertex.link_edges} & remaining
            remaining.difference_update(neighbors)
            group.update(neighbors)
            stack.extend(neighbors)
        yield group


def parent_at_rest(obj, hinge):
    world = obj.matrix_world.copy()
    obj.parent = hinge
    obj.matrix_parent_inverse = hinge.matrix_world.inverted()
    obj.matrix_basis = world


def separate_door(label, hinge_position, planes, y_min, y_max, include_mirror=False):
    scene = bpy.context.scene
    hinge = bpy.data.objects.new(label + " - hinge axis", None)
    scene.collection.objects.link(hinge)
    hinge.location = hinge_position
    hinge.empty_display_type = "ARROWS"
    hinge.empty_display_size = 0.15
    bpy.context.view_layer.update()
    whole_parts = {"Object_14", "Object_15", "Object_16", "Object_25", "Object_34", "Object_41"}
    # The main side shell, roof rails, pillars and cabin lining are fixed body
    # parts, not door skins. Only detach complete original door components.
    names = ["Object_14", "Object_15", "Object_16", "Object_25", "Object_34"]
    if include_mirror:
        names.append("Object_41")
    total = 0
    for name in names:
        bpy.context.view_layer.update()
        depsgraph = bpy.context.evaluated_depsgraph_get()
        obj = bpy.data.objects[name]
        mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), depsgraph=depsgraph)
        mesh.transform(obj.matrix_world)
        bm = bmesh.new()
        bm.from_mesh(mesh)
        eligible = bm.faces.layers.int.get("driver_side") or bm.faces.layers.int.new("driver_side")
        for face in bm.faces:
            face[eligible] = 0
        for group in components(bm):
            if min(vertex.co.x for vertex in group) <= 0.45:
                continue
            if name in whole_parts and not (
                min(vertex.co.y for vertex in group) > y_min
                and max(vertex.co.y for vertex in group) < y_max
            ):
                continue
            for vertex in group:
                for face in vertex.link_faces:
                    face[eligible] = 1
        for origin, normal in ([] if name in whole_parts else planes):
            bmesh.ops.bisect_plane(bm, geom=list(bm.verts) + list(bm.edges) + list(bm.faces),
                                   dist=0.000001, plane_co=origin, plane_no=normal)

        def selected_face(face, layer):
            return face[layer] and (name in whole_parts or all(
                (face.calc_center_median() - origin).dot(normal) > -0.000001
                for origin, normal in planes))

        selected = [face for face in bm.faces if selected_face(face, eligible)]
        if not selected:
            bm.free()
            bpy.data.meshes.remove(mesh)
            continue
        before, count = len(bm.faces), len(selected)
        door = bm.copy()
        door_eligible = door.faces.layers.int["driver_side"]
        bmesh.ops.delete(door, geom=[face for face in door.faces
            if not selected_face(face, door_eligible)], context="FACES")
        bmesh.ops.delete(bm, geom=selected, context="FACES")
        assert len(bm.faces) + len(door.faces) == before
        assert not any(selected_face(face, eligible) for face in bm.faces)
        bm.to_mesh(mesh)
        bm.free()
        obj.modifiers.clear()
        obj.parent = None
        obj.matrix_world = Matrix.Identity(4)
        obj.data = mesh
        door_mesh = bpy.data.meshes.new(name + " " + label)
        door.to_mesh(door_mesh)
        door.free()
        for material in mesh.materials:
            door_mesh.materials.append(material)
        panel = bpy.data.objects.new(name + " " + label, door_mesh)
        scene.collection.objects.link(panel)
        parent_at_rest(panel, hinge)
        if name in {"Object_14", "Object_15"}:
            thickness = panel.modifiers.new("Door sheet thickness", "SOLIDIFY")
            thickness.thickness = 0.008
            thickness.offset = -1
        total += count
        print("SEPARATED", label, name, count, "faces", flush=True)
    if include_mirror:
        for name in ("Object_55", "Object_56", "Object_57", "Object_58", "Object_59"):
            parent_at_rest(bpy.data.objects[name], hinge)
    assert total > 0
    hinge["description"] = "Rotation Z = 0 closes door. Hinge inferred from source panel edge."
    return hinge
