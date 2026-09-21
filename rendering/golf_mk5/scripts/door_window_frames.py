"""Create door-mounted frames along the source glazing's actual boundary."""
import bpy
import bmesh
from door_geometry import components, parent_at_rest


def glazing_boundaries():
    obj = bpy.data.objects["Object_25"]
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bm.transform(obj.matrix_world)
    outlines = {}
    for group in components(bm):
        if min(v.co.x for v in group) < 0.5:
            continue
        low = min(v.co.y for v in group)
        high = max(v.co.y for v in group)
        label = "front" if low > -0.85 and high < 0.33 else "rear" if low > 0.24 and high < 1.33 else None
        if label is None:
            continue
        edges = {edge for vertex in group for edge in vertex.link_edges if edge.is_boundary}
        paths = []
        while edges:
            edge = edges.pop()
            start, current = edge.verts
            path = [start.co.copy(), current.co.copy()]
            while current != start:
                following = next((candidate for candidate in current.link_edges if candidate in edges), None)
                if following is None:
                    break
                edges.remove(following)
                current = following.other_vert(current)
                path.append(current.co.copy())
            paths.append(path)
        outlines[label] = paths
    bm.free()
    return outlines


def add_frames(paths, hinge):
    for index, path in enumerate(paths):
        if len(path) < 3:
            continue
        curve = bpy.data.curves.new(hinge.name + " window frame", "CURVE")
        curve.dimensions = "3D"
        curve.resolution_u = 1
        curve.bevel_depth = 0.013
        curve.bevel_resolution = 3
        spline = curve.splines.new("POLY")
        spline.points.add(len(path) - 1)
        for point, coordinate in zip(spline.points, path):
            point.co = (*coordinate, 1)
        obj = bpy.data.objects.new(hinge.name + f" window frame {index}", curve)
        bpy.context.scene.collection.objects.link(obj)
        curve.materials.append(bpy.data.materials["GolfV-main_body"])
        parent_at_rest(obj, hinge)
