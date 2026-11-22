"""Renders the app icon as an object: a matte competition kettlebell with the mark's ring machined into its face.

    uv run --with pillow tools/render_icon_3d.py            # every output, 1024 px
    uv run --with pillow tools/render_icon_3d.py --draft    # quick and grainy, to look at a change
    uv run --with pillow tools/render_icon_3d.py --accent   # Work's arc alone in its colour, the others steel

It needs Blender (4.2 or later; `BLENDER=/path/to/blender` if it isn't in /Applications or on the path). The
file is two programs. Run as above it starts Blender on itself, which models the scene and renders three
layers on transparency into build/icon3d/, then it composes them with Pillow into iOS's light, dark and
tinted icons and the foreground of Android's adaptive icon (the launcher's; its background, its monochrome
layer and the splash screen's mark stay `tools/generate_icons.py`'s).

The ring's geometry is the mark's, from `tools/generate_icons.py` (the same arcs, hand and hub, on the bell's
face in place of the 108 canvas), and its colours are `PhaseColors`' in `core/core/src/ui/Theme.kt`.
"""

import math
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAYERS = ROOT / "build/icon3d"

# The mark, as tools/generate_icons.py has it: units of the 108 canvas, degrees clockwise from 12 o'clock.
MARK_RING_RADIUS = 24
MARK_RING_STROKE = 7
MARK_ARCS = [  # (start, end, colour, name)
    (14, 64, "F58A3D", "prep"),  # PhaseColors.prep
    (93, 227, "F0583A", "work"),  # PhaseColors.work
    (256, 346, "34C785", "rest"),  # PhaseColors.rest
]
MARK_HAND_LENGTH = 15
MARK_HAND_STROKE = 4.5
MARK_HUB_RADIUS = 4.5
MARK_PIN_RADIUS = 1.7

CHALK = "F7F3E9"  # PhaseColors.onGround: the hand and hub
CHARCOAL = "26292A"  # The bell's powder coat
DARK_CHARCOAL = "41474A"  # The same coat on the dark icon, lifted to stand off iOS's dark ground
STEEL = "7E8583"  # With --accent, the two arcs that aren't Work's: bead-blasted steel
LIGHT_GROUND = "FAF6EE"  # The light theme's ground, the light icon's backdrop
LIGHT_GROUND_EDGE = "EFE9DC"  # The light theme's raised, where the backdrop falls off

# The bell, in units of its body's radius. A competition bell: 210 mm across, 280 tall, a 35 mm handle.
BASE_Z = -0.84  # Where the flat it stands on cuts the sphere
FACE_Y = -0.66  # Where the flat face cuts it; the camera looks along +Y
HANDLE_RADIUS = 0.165
HANDLE_HALF_WIDTH = 0.74  # To the horns' centre lines
HANDLE_TOP = 1.62  # The bar's centre line
HANDLE_CORNER = 0.24
RING_RADIUS = 0.565  # The arcs' centre line on the face
RING_PROUD = 0.06  # How far the arcs stand off the face
MICRO_BEVEL = 0.012

SCALE = RING_RADIUS / MARK_RING_RADIUS  # Mark units to bell units

# How much of the frame's height the bell takes in a rendered layer, and where each output puts it.
LAYER_FILL = 0.70
IOS_FILL = 0.70
ANDROID_SAFE_CIRCLE = 66 / 108  # The adaptive icon's safe zone, a circle across the 108 dp canvas
ANDROID_DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


# Blender ---------------------------------------------------------------------------------------


def srgb_to_linear(hex_color):
    def channel(v):
        v /= 255
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4

    return tuple(channel(int(hex_color[i : i + 2], 16)) for i in (0, 2, 4)) + (1.0,)


def on_face(angle, radius):
    """A point of the face's plane, x to the viewer's right and y up."""
    a = math.radians(angle)
    return radius * math.sin(a), radius * math.cos(a)


def arc_outline(start, end, radius, width, step=1.0):
    """The outline of a round-capped stroke along an arc, as the mark's arcs are drawn."""
    half = width / 2
    count = max(2, round((end - start) / step))
    angles = [start + (end - start) * i / count for i in range(count + 1)]
    points = [on_face(a, radius + half) for a in angles]

    def cap(angle, sign):
        cx, cy = on_face(angle, radius)
        a = math.radians(angle)
        out, along = (math.sin(a), math.cos(a)), (math.cos(a), -math.sin(a))
        return [
            (cx + half * sign * (math.cos(p) * out[0] + math.sin(p) * along[0]),
             cy + half * sign * (math.cos(p) * out[1] + math.sin(p) * along[1]))
            for p in (math.pi * i / 24 for i in range(1, 24))
        ]

    points += cap(end, 1)
    points += [on_face(a, radius - half) for a in reversed(angles)]
    points += cap(start, -1)
    return points


def hand_outline():
    """The hand and the hub as one keyhole, so no two solids share a face."""
    hub, half, tip = MARK_HUB_RADIUS * SCALE, MARK_HAND_STROKE / 2 * SCALE, MARK_HAND_LENGTH * SCALE
    meet = math.asin(half / hub)  # Where the hand's side leaves the hub's rim, from 12 o'clock
    points = [(half, hub * math.cos(meet)), (half, tip)]
    points += [(half * math.cos(p), tip + half * math.sin(p)) for p in (math.pi * i / 24 for i in range(1, 24))]
    points += [(-half, tip), (-half, hub * math.cos(meet))]
    sweep = 2 * math.pi - 2 * meet  # Round the hub the long way, anticlockwise from the left
    steps = 120
    for i in range(1, steps):
        a = -meet - sweep * i / steps
        points.append((hub * math.sin(a), hub * math.cos(a)))
    return points


def circle_outline(radius, steps=96):
    return [(radius * math.cos(2 * math.pi * i / steps), radius * math.sin(2 * math.pi * i / steps))
            for i in range(steps)]


def render_layers(out_dir, size, samples, arcs):
    import bmesh
    import bpy
    from mathutils import Vector

    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    collection = scene.collection

    # Materials ---------------------------------------------------------------------------------

    def material(name, hex_color, roughness, metallic=0.0, grain=0.0):
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        nodes, links = mat.node_tree.nodes, mat.node_tree.links
        bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
        bsdf.inputs["Base Color"].default_value = srgb_to_linear(hex_color)
        bsdf.inputs["Roughness"].default_value = roughness
        bsdf.inputs["Metallic"].default_value = metallic
        if grain:  # A powder coat's orange peel: too fine to see as texture, enough to break a highlight up
            noise = nodes.new("ShaderNodeTexNoise")
            noise.inputs["Scale"].default_value = 260
            noise.inputs["Detail"].default_value = 2
            bump = nodes.new("ShaderNodeBump")
            bump.inputs["Strength"].default_value = grain
            bump.inputs["Distance"].default_value = 0.004
            links.new(noise.outputs["Fac"], bump.inputs["Height"])
            links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
        return mat

    def set_color(mat, hex_color, roughness=None, metallic=None):
        bsdf = next(n for n in mat.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
        bsdf.inputs["Base Color"].default_value = srgb_to_linear(hex_color)
        if roughness is not None:
            bsdf.inputs["Roughness"].default_value = roughness
        if metallic is not None:
            bsdf.inputs["Metallic"].default_value = metallic

    coat = material("PowderCoat", CHARCOAL, 0.5, grain=0.12)
    chalk = material("Chalk", CHALK, 0.5)
    arc_materials = {name: material(f"Arc.{name}", color, 0.48) for _, _, color, name in MARK_ARCS}

    # The bell ----------------------------------------------------------------------------------

    def add(name, data, mat):
        obj = bpy.data.objects.new(name, data)
        collection.objects.link(obj)
        data.materials.append(mat)
        return obj

    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=192, v_segments=96, radius=1.0)

    def flat(point, normal, bevel):
        """Cuts the sphere flat and rounds the edge the cut leaves."""
        cut = bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], plane_co=point,
                                     plane_no=normal, clear_outer=True)
        edges = [e for e in cut["geom_cut"] if isinstance(e, bmesh.types.BMEdge)]
        bmesh.ops.holes_fill(bm, edges=edges)
        bmesh.ops.bevel(bm, geom=edges, offset=bevel, offset_type="OFFSET", segments=8, profile=0.5,
                        affect="EDGES", clamp_overlap=True)

    flat((0, 0, BASE_Z), (0, 0, -1), 0.035)
    flat((0, FACE_Y, 0), (0, -1, 0), 0.028)
    for face in bm.faces:
        face.smooth = True
    body_mesh = bpy.data.meshes.new("Body")
    bm.to_mesh(body_mesh)
    bm.free()
    body = add("Body", body_mesh, coat)
    weighted = body.modifiers.new("Normals", "WEIGHTED_NORMAL")  # So each flat shades flat up to its bevel
    weighted.keep_sharp = True
    weighted.weight = 100

    # The handle: two horns up from the shoulders and a bar across, one tube, thicker where it meets the body.
    path = []
    foot, shoulder = 0.42, HANDLE_TOP - HANDLE_CORNER
    for i in range(41):
        z = foot + (shoulder - foot) * i / 40
        path.append((-HANDLE_HALF_WIDTH + 0.05 * (1 - i / 40), z))  # The horns lean in a touch at the foot
    for i in range(1, 32):
        a = math.pi / 2 * i / 32
        path.append((-HANDLE_HALF_WIDTH + HANDLE_CORNER * (1 - math.cos(a)), shoulder + HANDLE_CORNER * math.sin(a)))
    left = path
    path = left + [(0.0, HANDLE_TOP)] + [(-x, z) for x, z in reversed(left)]
    handle_curve = bpy.data.curves.new("Handle", "CURVE")
    handle_curve.dimensions = "3D"
    handle_curve.bevel_depth = HANDLE_RADIUS
    handle_curve.bevel_resolution = 24
    handle_curve.use_fill_caps = True
    spline = handle_curve.splines.new("POLY")
    spline.points.add(len(path) - 1)
    for p, (x, z) in zip(spline.points, path):
        p.co = (x, 0.0, z, 1.0)
        flare = max(0.0, (1.1 - z) / (1.1 - foot))  # 1 at the foot, 0 from just over the body's top
        p.radius = 1.0 + 0.2 * flare * flare
    spline.use_smooth = True
    add("Handle", handle_curve, coat)

    # The ring, on the face ---------------------------------------------------------------------

    def plate(name, outlines, mat, proud):
        """A plate cut to [outlines] (the first its edge, the rest holes), standing [proud] off the face."""
        sink = 0.03
        curve = bpy.data.curves.new(name, "CURVE")
        curve.dimensions = "2D"
        curve.fill_mode = "BOTH"
        curve.bevel_depth = MICRO_BEVEL
        curve.bevel_resolution = 4
        curve.offset = -MICRO_BEVEL  # Keeps the bevel inside the outline, so the plate is the mark's size
        curve.extrude = (proud + sink) / 2 - MICRO_BEVEL
        for outline in outlines:
            s = curve.splines.new("POLY")
            s.points.add(len(outline) - 1)
            for p, (x, y) in zip(s.points, outline):
                p.co = (x, y, 0.0, 1.0)
            s.use_cyclic_u = True
            s.use_smooth = True
        obj = add(name, curve, mat)
        obj.rotation_euler = (math.pi / 2, 0, 0)  # The curve's plane to the face's: its z toward the camera
        obj.location = (0, FACE_Y + (sink - proud) / 2, 0)
        return obj

    stroke = MARK_RING_STROKE * SCALE
    for start, end, _, name in MARK_ARCS:
        plate(f"Arc.{name}", [arc_outline(start, end, RING_RADIUS, stroke)], arc_materials[name], RING_PROUD)
    plate("Hand", [hand_outline(), circle_outline(MARK_PIN_RADIUS * SCALE)], chalk, RING_PROUD)

    # The floor, which only takes the shadow.
    floor_mesh = bpy.data.meshes.new("Floor")
    span = 30
    floor_mesh.from_pydata([(-span, -span, BASE_Z), (span, -span, BASE_Z), (span, span, BASE_Z),
                            (-span, span, BASE_Z)], [], [(0, 1, 2, 3)])
    floor = add("Floor", floor_mesh, material("Floor", "FFFFFF", 1.0))
    floor.is_shadow_catcher = True

    # Camera ------------------------------------------------------------------------------------

    camera_data = bpy.data.cameras.new("Camera")
    camera_data.type = "ORTHO"
    camera = bpy.data.objects.new("Camera", camera_data)
    collection.objects.link(camera)
    scene.camera = camera
    azimuth, elevation = math.radians(17), math.radians(9)
    toward = Vector((math.sin(azimuth) * math.cos(elevation), -math.cos(azimuth) * math.cos(elevation),
                     math.sin(elevation)))
    camera.rotation_euler = (-toward).to_track_quat("-Z", "Y").to_euler()
    bpy.context.view_layer.update()
    right, up = camera.matrix_world.col[0].xyz, camera.matrix_world.col[1].xyz

    # Frame the bell by what the camera sees of it: its bounds across and up the frame.
    depsgraph = bpy.context.evaluated_depsgraph_get()
    across, upward = [], []
    for obj in collection.objects:
        if obj.type not in ("MESH", "CURVE") or obj == floor:
            continue
        evaluated = obj.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh()
        for v in mesh.vertices:
            world = evaluated.matrix_world @ v.co
            across.append(world.dot(right))
            upward.append(world.dot(up))
        evaluated.to_mesh_clear()
    middle = right * (min(across) + max(across)) / 2 + up * (min(upward) + max(upward)) / 2
    camera.location = middle + toward * 20
    camera_data.ortho_scale = (max(upward) - min(upward)) / LAYER_FILL
    camera_data.clip_end = 100
    bpy.context.view_layer.update()

    # Lights: a soft key, a fill and a rim, placed by where the camera stands -------------------

    def light(name, across_up_toward, size, watts):
        data = bpy.data.lights.new(name, "AREA")
        data.shape = "DISK"
        data.size = size
        data.energy = watts
        obj = bpy.data.objects.new(name, data)
        collection.objects.link(obj)
        a, u, t = across_up_toward
        obj.location = middle + right * a + up * u + toward * t
        obj.rotation_euler = (middle - obj.location).to_track_quat("-Z", "Y").to_euler()
        return data

    key = light("Key", (-4.0, 4.5, 6.0), 6.0, 460)
    fill = light("Fill", (5.0, 0.5, 5.0), 7.0, 130)
    rim = light("Rim", (2.5, 5.0, -4.5), 3.5, 420)
    kicker = light("Kicker", (-5.0, 1.0, -3.5), 3.0, 0)  # Only after dark: the left edge off a dark ground
    rim.use_shadow = kicker.use_shadow = False  # The floor takes the key's shadow alone

    world = bpy.data.worlds.new("World")
    world.use_nodes = True
    background = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
    background.inputs["Color"].default_value = (1, 1, 1, 1)
    scene.world = world

    # Render ------------------------------------------------------------------------------------

    scene.render.engine = "CYCLES"
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    try:  # The GPU where there is one; Cycles falls back to the CPU otherwise
        preferences = bpy.context.preferences.addons["cycles"].preferences
        preferences.compute_device_type = "METAL"
        preferences.get_devices()
        for device in preferences.devices:
            device.use = True
        scene.cycles.device = "GPU"
    except Exception as error:  # noqa: BLE001
        print("rendering on the CPU:", error)
    scene.render.resolution_x = scene.render.resolution_y = size
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "16"
    scene.view_settings.view_transform = "Standard"  # So a brand colour lit square on comes out as itself
    scene.view_settings.look = "None"

    def render(name):
        scene.render.filepath = str(Path(out_dir) / f"{name}.png")
        bpy.ops.render.render(write_still=True)

    def paint(arc_colors, others, ink, body):
        for _, _, color, name in MARK_ARCS:
            if arcs == "phases" or name == "work":
                set_color(arc_materials[name], arc_colors.get(name, color), 0.48, 0.0)
            else:
                set_color(arc_materials[name], *others)
        set_color(chalk, ink)
        set_color(coat, body)

    # Light: on the chalk backdrop, with its shadow on the floor.
    background.inputs["Strength"].default_value = 0.22
    paint({}, (STEEL, 0.42, 1.0), CHALK, CHARCOAL)
    render("light")

    # Dark: no backdrop of its own (iOS puts its dark one behind), so the edges are lit to stand off it.
    floor.hide_render = True
    background.inputs["Strength"].default_value = 0.2
    key.energy, fill.energy, rim.energy, kicker.energy = 460, 200, 1500, 1100
    paint({}, ("A4ABA8", 0.5, 0.0), CHALK, DARK_CHARCOAL)
    render("dark")

    # Tinted: iOS colours it by luminance, so each part is a grey level of its own, and the bell no charcoal.
    tinted = {"prep": "C8C8C8", "work": "FFFFFF", "rest": "9A9A9A"}
    paint(tinted, ("9A9A9A", 0.5, 0.0), "FFFFFF", "3E3E3E")
    render("tinted")


# Compose ---------------------------------------------------------------------------------------


def find_blender():
    for candidate in (os.environ.get("BLENDER"), shutil.which("blender"),
                      "/Applications/Blender.app/Contents/MacOS/Blender"):
        if candidate and Path(candidate).exists():
            return candidate
    sys.exit("Blender not found: install it, or set BLENDER to its executable.")


def compose():
    from PIL import Image, ImageChops

    def rgb(hex_color):
        return tuple(int(hex_color[i : i + 2], 16) for i in (0, 2, 4))

    def layer(name):
        return Image.open(LAYERS / f"{name}.png").convert("RGBA")

    def placed(img, fill, size=1024):
        """[img] scaled so the bell takes [fill] of a [size] square's height, about its middle."""
        side = round(img.width * fill / LAYER_FILL * size / img.width)
        # Scaled premultiplied, so a transparent pixel's colour never bleeds into an edge.
        scaled = img.convert("RGBa").resize((side, side), Image.LANCZOS).convert("RGBA")
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        offset = (size - side) // 2
        out.alpha_composite(scaled.crop((max(0, -offset), max(0, -offset), side - max(0, -offset),
                                         side - max(0, -offset))), (max(0, offset), max(0, offset)))
        return out

    def backdrop(size=1024):
        """The light theme's ground, a little brighter behind the bell than at the corners."""
        inner, outer = rgb(LIGHT_GROUND), rgb(LIGHT_GROUND_EDGE)
        ramp = Image.radial_gradient("L").resize((size, size), Image.BICUBIC)  # 0 at the centre
        ramp = ramp.point(lambda v: min(255, round(255 * (v / 255) ** 1.6)))
        return Image.composite(Image.new("RGB", (size, size), outer), Image.new("RGB", (size, size), inner),
                               ramp).convert("RGBA")

    def save(path, img):
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path, optimize=True)
        print("wrote", path.relative_to(ROOT))

    icons = ROOT / "apps/iosApp/src/Assets.xcassets/AppIcon.appiconset"
    light = backdrop()
    light.alpha_composite(placed(layer("light"), IOS_FILL))
    save(icons / "app-icon-1024.png", light.convert("RGB"))  # The App Store rejects an icon with an alpha channel

    dark = placed(layer("dark"), IOS_FILL)
    save(icons / "app-icon-1024-dark.png", dark)

    tinted = placed(layer("tinted"), IOS_FILL)
    grey = tinted.convert("L")
    save(icons / "app-icon-1024-tinted.png", Image.merge("RGBA", (grey, grey, grey, tinted.getchannel("A"))))

    # Android's foreground: the dark layer, the whole bell inside the safe circle. Its corners are what reach
    # furthest from the middle, so the circle is fitted to the bell's own bounds.
    bounds = placed(layer("dark"), 1.0 * LAYER_FILL).getchannel("A").getbbox()
    half_diagonal = math.hypot(bounds[2] - bounds[0], bounds[3] - bounds[1]) / 2
    fit = ANDROID_SAFE_CIRCLE * 1024 / 2 / half_diagonal
    foreground = placed(layer("dark"), LAYER_FILL * fit)
    assert not ImageChops.multiply(foreground.getchannel("A"), safe_zone_outside(Image)).getbbox()
    save(ROOT / "tools/icon3d/ic_launcher_foreground_3d.png", foreground)
    # The launcher's copies, the 108 dp canvas at each density. `mipmap-anydpi/ic_launcher.xml` names them; the
    # splash screen keeps the flat mark (`drawable/ic_launcher_foreground.xml`), which the intro opens on.
    for density, pixels in ANDROID_DENSITIES.items():
        scaled = foreground.convert("RGBa").resize((pixels, pixels), Image.LANCZOS).convert("RGBA")
        save(ROOT / f"apps/androidApp/res/mipmap-{density}/ic_launcher_foreground_3d.png", scaled)
    return light, dark, tinted, foreground


def safe_zone_outside(Image, size=1024):
    """White wherever a pixel is outside the adaptive icon's safe circle."""
    from PIL import ImageDraw

    mask = Image.new("L", (size, size), 255)
    r = ANDROID_SAFE_CIRCLE * size / 2
    ImageDraw.Draw(mask).ellipse([size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r], fill=0)
    return mask


def main():
    draft = "--draft" in sys.argv
    arcs = "accent" if "--accent" in sys.argv else "phases"
    if "--compose-only" not in sys.argv:
        LAYERS.mkdir(parents=True, exist_ok=True)
        size, samples = (1024, 32) if draft else (2048, 256)
        subprocess.run([find_blender(), "--background", "--factory-startup", "--python", __file__, "--",
                        str(LAYERS), str(size), str(samples), arcs], check=True)
    compose()


if __name__ == "__main__":
    try:
        import bpy  # noqa: F401: only Blender's Python has it
    except ImportError:
        bpy = None
    if bpy and "--" in sys.argv:
        out, size, samples, arcs = sys.argv[sys.argv.index("--") + 1 :]
        render_layers(out, int(size), int(samples), arcs)
    else:
        main()
