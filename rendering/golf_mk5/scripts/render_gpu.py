"""Run with: blender -b golf_mk5_studio_black.blend --python scripts/render_gpu.py"""
from pathlib import Path
import bpy

preferences = bpy.context.preferences.addons["cycles"].preferences
selected = None
for backend in ("OPTIX", "CUDA", "HIP", "METAL", "ONEAPI"):
    try:
        preferences.compute_device_type = backend
        preferences.refresh_devices()
    except (TypeError, RuntimeError):
        continue
    devices = [d for d in preferences.devices if d.type == backend]
    if devices:
        for device in preferences.devices:
            device.use = device.type == backend
        selected = backend
        print("Rendering with", backend, [d.name for d in devices])
        break
if selected is None:
    raise RuntimeError("No supported Cycles GPU found. Check GPU drivers and Blender Cycles device support.")

scene = bpy.context.scene
scene.cycles.device = "GPU"
Path(bpy.path.abspath(scene.render.filepath)).parent.mkdir(parents=True, exist_ok=True)
scene.render.use_overwrite = False
scene.render.use_placeholder = False
bpy.ops.render.render(animation=True)
