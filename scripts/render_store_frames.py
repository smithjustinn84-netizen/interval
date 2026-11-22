#!/usr/bin/env python3
"""Headless Blender script to render storefront device mockups from Interval screenshots.

Builds clean, flat-edge modern smartphone and landscape tablet frames with concentric
chamfered titanium/graphite bezels, exact screenshot-matched aspect ratios, color-accurate
Emission screen shaders (Strength = 1.0, sRGB), and subtle low-roughness architectural
glass overlays.

Usage (headless Blender CLI):
    /Applications/Blender.app/Contents/MacOS/Blender --background \\
        --python scripts/render_store_frames.py

Optional arguments (after `--`):
    --only hero_timer dual_today_workout companion_tablet_phone
    --formats portrait landscape
    --samples 96
    --screenshots-dir docs/screenshots
    --output-dir docs/assets/store
    --save-blend              # Also save .blend files alongside renders for inspection
"""

from __future__ import annotations

import argparse
import math
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import bmesh
import bpy
from mathutils import Euler, Matrix, Vector


# ---------------------------------------------------------------------------
# Configuration & Design Constants (derived from core/core/src/ui/Theme.kt)
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SCREENSHOTS_DIR = REPO_ROOT / "docs" / "screenshots"
DEFAULT_OUTPUT_DIR = REPO_ROOT / "docs" / "assets" / "store"

FORMATS: dict[str, tuple[int, int]] = {
    "portrait": (1290, 2796),
    "landscape": (1920, 1080),
}


@dataclass(frozen=True)
class DeviceSpec:
    """Physical hardware dimensions (in meters) for a modern flat-edge device."""

    name: str
    screen_width: float  # Screen height is derived from screenshot aspect ratio
    bezel_width: float  # Black OLED border between active display and metal lip
    rail_width: float  # Visible titanium lip width around the glass on the front
    thickness: float  # Overall body depth
    screen_corner_radius: float  # Inner screen corner radius (outer radii are concentric)
    chamfer: float  # 45-degree edge chamfer on front and back titanium rails
    glass_offset: float  # Thin-sheet cover glass standoff in front of screen plane
    is_tablet: bool = False


PHONE_SPEC = DeviceSpec(
    name="Phone",
    screen_width=0.0715,
    bezel_width=0.0020,
    rail_width=0.0014,
    thickness=0.0078,
    screen_corner_radius=0.0034,  # Subtle modern curve; clears all UI edge text cleanly
    chamfer=0.00085,
    glass_offset=0.00032,
    is_tablet=False,
)

# Slightly more compact 6.1" companion scale when paired next to an 11" tablet
COMPANION_PHONE_SPEC = DeviceSpec(
    name="CompanionPhone",
    screen_width=0.0635,
    bezel_width=0.0018,
    rail_width=0.0013,
    thickness=0.0076,
    screen_corner_radius=0.0030,
    chamfer=0.00080,
    glass_offset=0.00030,
    is_tablet=False,
)

TABLET_SPEC = DeviceSpec(
    name="Tablet",
    screen_width=0.2380,
    bezel_width=0.0078,
    rail_width=0.0017,
    thickness=0.0062,
    screen_corner_radius=0.0044,
    chamfer=0.00085,
    glass_offset=0.00034,
    is_tablet=True,
)


@dataclass(frozen=True)
class DevicePlacement:
    """Placement of a single device inside a composition."""

    key: str
    screenshot: str
    spec: DeviceSpec
    location: tuple[float, float, float]
    rotation_deg: tuple[float, float, float]  # (pitch_x, roll_y, yaw_z)


@dataclass(frozen=True)
class CompositionSpec:
    """Defines a storefront render composition with format-specific staging."""

    name: str
    description: str
    placements_by_format: dict[str, list[DevicePlacement]]
    margin_by_format: dict[str, tuple[float, float]]  # (margin_x, margin_y) fraction


COMPOSITIONS: dict[str, CompositionSpec] = {
    "hero_timer": CompositionSpec(
        name="hero_timer",
        description="Single hero angled phone featuring 07_timer.png",
        placements_by_format={
            "portrait": [
                DevicePlacement(
                    key="HeroPhone",
                    screenshot="07_timer.png",
                    spec=PHONE_SPEC,
                    location=(0.0, 0.0, 0.0),
                    rotation_deg=(-4.5, 0.0, -16.0),
                ),
            ],
            "landscape": [
                DevicePlacement(
                    key="HeroPhone",
                    screenshot="07_timer.png",
                    spec=PHONE_SPEC,
                    location=(0.0, 0.0, 0.0),
                    rotation_deg=(-4.5, 0.0, -18.0),
                ),
            ],
        },
        margin_by_format={
            "portrait": (0.10, 0.10),
            "landscape": (0.12, 0.085),
        },
    ),
    "dual_today_workout": CompositionSpec(
        name="dual_today_workout",
        description="Dual overlapping phones featuring 01_today.png and 09_workout.png",
        placements_by_format={
            # Foreground left: 01_today.png (full weekly streak & Start card unobstructed)
            # Rear right: 09_workout.png (active Work 0:16 ring & controls cleanly framed)
            "portrait": [
                DevicePlacement(
                    key="TodayPhone",
                    screenshot="01_today.png",
                    spec=PHONE_SPEC,
                    location=(-0.034, -0.016, -0.024),
                    rotation_deg=(-3.5, 0.0, 11.0),
                ),
                DevicePlacement(
                    key="WorkoutPhone",
                    screenshot="09_workout.png",
                    spec=PHONE_SPEC,
                    location=(0.038, 0.020, 0.026),
                    rotation_deg=(-3.5, 0.0, -9.0),
                ),
            ],
            "landscape": [
                DevicePlacement(
                    key="TodayPhone",
                    screenshot="01_today.png",
                    spec=PHONE_SPEC,
                    location=(-0.037, -0.014, -0.008),
                    rotation_deg=(-3.5, 0.0, 10.5),
                ),
                DevicePlacement(
                    key="WorkoutPhone",
                    screenshot="09_workout.png",
                    spec=PHONE_SPEC,
                    location=(0.040, 0.018, 0.010),
                    rotation_deg=(-3.5, 0.0, -9.5),
                ),
            ],
        },
        margin_by_format={
            "portrait": (0.060, 0.11),
            "landscape": (0.12, 0.085),
        },
    ),
    "companion_tablet_phone": CompositionSpec(
        name="companion_tablet_phone",
        description="Tablet + phone companion featuring 16_tablet_today.png and 14_library.png",
        placements_by_format={
            # Portrait cascades the phone lower-right so both screens stay large & legible
            "portrait": [
                DevicePlacement(
                    key="TodayTablet",
                    screenshot="16_tablet_today.png",
                    spec=TABLET_SPEC,
                    location=(-0.014, 0.030, 0.056),
                    rotation_deg=(-4.0, 0.0, 14.0),
                ),
                DevicePlacement(
                    key="LibraryPhone",
                    screenshot="14_library.png",
                    spec=COMPANION_PHONE_SPEC,
                    location=(0.062, -0.026, -0.046),
                    rotation_deg=(-3.5, 0.0, -11.0),
                ),
            ],
            # Landscape pairs the tablet behind-left with the phone anchored foreground-right
            "landscape": [
                DevicePlacement(
                    key="TodayTablet",
                    screenshot="16_tablet_today.png",
                    spec=TABLET_SPEC,
                    location=(-0.030, 0.024, 0.008),
                    rotation_deg=(-4.0, 0.0, 10.0),
                ),
                DevicePlacement(
                    key="LibraryPhone",
                    screenshot="14_library.png",
                    spec=COMPANION_PHONE_SPEC,
                    location=(0.112, -0.024, -0.014),
                    rotation_deg=(-3.5, 0.0, -12.0),
                ),
            ],
        },
        margin_by_format={
            "portrait": (0.055, 0.12),
            "landscape": (0.075, 0.095),
        },
    ),
}


# ---------------------------------------------------------------------------
# Geometry Utilities
# ---------------------------------------------------------------------------


def rounded_rect_points(
    width: float,
    height: float,
    radius: float,
    corner_segments: int = 24,
) -> list[tuple[float, float]]:
    """Return 2D CCW perimeter points (x, z) of a centered rounded rectangle."""
    hw = width * 0.5
    hh = height * 0.5
    r = max(0.0, min(radius, hw - 1e-6, hh - 1e-6))

    # Corner centers and start angles (CCW in X-Z plane)
    corners = [
        (hw - r, -hh + r, -math.pi * 0.5, 0.0),  # Bottom-right
        (hw - r, hh - r, 0.0, math.pi * 0.5),  # Top-right
        (-hw + r, hh - r, math.pi * 0.5, math.pi),  # Top-left
        (-hw + r, -hh + r, math.pi, math.pi * 1.5),  # Bottom-left
    ]

    pts: list[tuple[float, float]] = []
    for cx, cz, a0, a1 in corners:
        for i in range(corner_segments + 1):
            t = i / corner_segments
            ang = a0 + (a1 - a0) * t
            pts.append((cx + r * math.cos(ang), cz + r * math.sin(ang)))
    return pts


def ensure_normal_direction(bm: bmesh.types.BMesh, target_dir: Vector) -> None:
    """Flip faces whose normal points opposite to target_dir."""
    bm.normal_update()
    for face in bm.faces:
        if face.normal.dot(target_dir) < 0.0:
            face.normal_flip()


def build_flat_rounded_plate(
    name: str,
    width: float,
    height: float,
    radius: float,
    y_pos: float,
    material: bpy.types.Material,
    with_screen_uvs: bool = False,
    corner_segments: int = 24,
) -> bpy.types.Object:
    """Create a flat rounded-rectangle plate facing -Y with exact analytical UVs."""
    bm = bmesh.new()
    pts = rounded_rect_points(width, height, radius, corner_segments)

    # Use a center vertex fan so triangulation is perfectly symmetric and planar
    center = bm.verts.new((0.0, y_pos, 0.0))
    ring = [bm.verts.new((x, y_pos, z)) for (x, z) in pts]

    n = len(ring)
    for i in range(n):
        v0 = ring[i]
        v1 = ring[(i + 1) % n]
        # Looking from -Y toward +Y: CCW in (X, Z) gives normal (0, -1, 0)
        bm.faces.new((center, v0, v1))

    ensure_normal_direction(bm, Vector((0.0, -1.0, 0.0)))

    if with_screen_uvs:
        uv_layer = bm.loops.layers.uv.new("UVMap")
        inv_w = 1.0 / width
        inv_h = 1.0 / height
        for face in bm.faces:
            for loop in face.loops:
                co = loop.vert.co
                u = (co.x + width * 0.5) * inv_w
                v = (co.z + height * 0.5) * inv_h
                loop[uv_layer].uv = (u, v)

    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    mesh.materials.append(material)

    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    return obj


def build_cover_glass(
    name: str,
    width: float,
    height: float,
    radius: float,
    y_front: float,
    material: bpy.types.Material,
    corner_segments: int = 24,
) -> bpy.types.Object:
    """Create a subtle low-roughness cover glass plane with a micro 2.5D edge polish."""
    bm = bmesh.new()
    edge_drop = 0.00018
    edge_inset = 0.00055

    inner_pts = rounded_rect_points(
        width - 2.0 * edge_inset,
        height - 2.0 * edge_inset,
        max(0.0005, radius - edge_inset),
        corner_segments,
    )
    outer_pts = rounded_rect_points(width, height, radius, corner_segments)

    center = bm.verts.new((0.0, y_front, 0.0))
    inner_ring = [bm.verts.new((x, y_front, z)) for (x, z) in inner_pts]
    outer_ring = [bm.verts.new((x, y_front + edge_drop, z)) for (x, z) in outer_pts]

    n = len(inner_ring)
    for i in range(n):
        i_next = (i + 1) % n
        bm.faces.new((center, inner_ring[i], inner_ring[i_next]))
        f_edge = bm.faces.new((inner_ring[i], outer_ring[i], outer_ring[i_next], inner_ring[i_next]))
        f_edge.smooth = True

    ensure_normal_direction(bm, Vector((0.0, -1.0, 0.0)))

    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    mesh.materials.append(material)

    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    # Architectural thin glass: zero shadow cast onto the Emission screen behind it
    obj.visible_shadow = False
    return obj


def build_chamfered_frame(
    name: str,
    glass_w: float,
    glass_h: float,
    glass_r: float,
    rail_w: float,
    thickness: float,
    chamfer: float,
    mat_body: bpy.types.Material,
    mat_chamfer: bpy.types.Material,
    mat_back: bpy.types.Material,
    corner_segments: int = 24,
) -> bpy.types.Object:
    """Loft a precision flat-edge titanium/graphite chassis with polished micro-chamfers.

    Material slots:
        0: Brushed titanium/graphite flat rails
        1: Brighter polished chamfered bezel rims
        2: Satin anodized dark graphite back plate
    """
    bm = bmesh.new()

    outer_w = glass_w + 2.0 * rail_w
    outer_h = glass_h + 2.0 * rail_w
    outer_r = glass_r + rail_w

    y_front = -thickness * 0.5
    y_back = thickness * 0.5

    # Cross-section profile rings: (inset_from_outer, y_coord)
    # Multi-step chamfers catch crisp specular highlights without shading artifacts.
    c = chamfer
    lip_inset = rail_w + 0.00025  # Slightly tucks behind glass edge for a clean seam
    profile: list[tuple[float, float]] = [
        (lip_inset, y_front + 0.00055),  # 0: Inner glass seat
        (rail_w, y_front + 0.00018),  # 1: Inner front rim step
        (c, y_front),  # 2: Front rim crest
        (c * 0.50, y_front + c * 0.18),  # 3: Front chamfer mid-1
        (c * 0.16, y_front + c * 0.52),  # 4: Front chamfer mid-2
        (0.0, y_front + c),  # 5: Flat outer side wall (front)
        (0.0, y_back - c),  # 6: Flat outer side wall (back)
        (c * 0.16, y_back - c * 0.52),  # 7: Back chamfer mid-1
        (c * 0.50, y_back - c * 0.18),  # 8: Back chamfer mid-2
        (c, y_back),  # 9: Back rim crest
        (rail_w + 0.0012, y_back),  # 10: Back plate transition
    ]

    # Material index per strip between ring[k] and ring[k+1]
    strip_materials = [
        1,  # 0->1 inner seat lip
        1,  # 1->2 front bezel rim
        1,  # 2->3 front chamfer
        1,  # 3->4 front chamfer
        1,  # 4->5 front chamfer
        0,  # 5->6 flat titanium side rail
        1,  # 6->7 back chamfer
        1,  # 7->8 back chamfer
        1,  # 8->9 back chamfer
        2,  # 9->10 back rim
    ]
    strip_smooth = [
        False,
        False,
        True,
        True,
        True,
        True,  # Smooth around the rounded corners; sharp edges split via edge split/crease
        True,
        True,
        True,
        False,
    ]

    rings: list[list[bmesh.types.BMVert]] = []
    for inset, y_val in profile:
        pts = rounded_rect_points(
            outer_w - 2.0 * inset,
            outer_h - 2.0 * inset,
            max(0.0004, outer_r - inset),
            corner_segments,
        )
        rings.append([bm.verts.new((x, y_val, z)) for (x, z) in pts])

    n = len(rings[0])
    for r_idx in range(len(rings) - 1):
        r0 = rings[r_idx]
        r1 = rings[r_idx + 1]
        mat_idx = strip_materials[r_idx]
        is_smooth = strip_smooth[r_idx]
        for i in range(n):
            i_next = (i + 1) % n
            face = bm.faces.new((r0[i], r0[i_next], r1[i_next], r1[i]))
            face.material_index = mat_idx
            face.smooth = is_smooth

    # Cap the back plate
    back_center = bm.verts.new((0.0, y_back, 0.0))
    last_ring = rings[-1]
    for i in range(n):
        i_next = (i + 1) % n
        f_back = bm.faces.new((back_center, last_ring[i_next], last_ring[i]))
        f_back.material_index = 2
        f_back.smooth = False

    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)

    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()

    mesh.materials.append(mat_body)
    mesh.materials.append(mat_chamfer)
    mesh.materials.append(mat_back)

    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)

    # Keep crisp reflections on flat rails while smoothing rounded corners cleanly
    mod_wn = obj.modifiers.new(name="WeightedNormal", type="WEIGHTED_NORMAL")
    mod_wn.keep_sharp = True
    return obj


def build_side_button(
    name: str,
    size_xyz: tuple[float, float, float],
    location: tuple[float, float, float],
    material: bpy.types.Material,
) -> bpy.types.Object:
    """Create a subtle chamfered titanium hardware button along the outer rail."""
    bm = bmesh.new()
    sx, sy, sz = size_xyz
    bmesh.ops.create_cube(bm, size=1.0, matrix=Matrix.Diagonal((sx, sy, sz, 1.0)))

    bevel_w = min(sx, sy, sz) * 0.28
    bmesh.ops.bevel(
        bm,
        geom=list(bm.edges),
        offset=bevel_w,
        segments=3,
        affect="EDGES",
        clamp_overlap=True,
    )
    for face in bm.faces:
        face.smooth = True

    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()
    mesh.materials.append(material)

    obj = bpy.data.objects.new(name, mesh)
    obj.location = location
    bpy.context.collection.objects.link(obj)
    return obj


# ---------------------------------------------------------------------------
# Materials
# ---------------------------------------------------------------------------


def enable_nodes(datablock: bpy.types.ID) -> bpy.types.NodeTree:
    """Return node tree, calling use_nodes=True only on Blender < 5.0 to avoid deprecation warnings."""
    if bpy.app.version < (5, 0, 0):
        datablock.use_nodes = True
    return datablock.node_tree


def create_titanium_material(
    name: str,
    base_color: tuple[float, float, float],
    roughness: float,
    metallic: float = 0.94,
    bump_strength: float = 0.008,
) -> bpy.types.Material:
    """Create a brushed/bead-blasted titanium-graphite metal shader."""
    mat = bpy.data.materials.new(name=name)
    node_tree = enable_nodes(mat)
    nodes = node_tree.nodes
    links = node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Base Color"].default_value = (*base_color, 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    if "Specular IOR Level" in bsdf.inputs:
        bsdf.inputs["Specular IOR Level"].default_value = 0.55
    if "Coat Weight" in bsdf.inputs:
        bsdf.inputs["Coat Weight"].default_value = 0.06
        bsdf.inputs["Coat Roughness"].default_value = 0.14

    if bump_strength > 0.0:
        tex_coord = nodes.new("ShaderNodeTexCoord")
        noise = nodes.new("ShaderNodeTexNoise")
        noise.inputs["Scale"].default_value = 420.0
        noise.inputs["Detail"].default_value = 6.0
        noise.inputs["Roughness"].default_value = 0.55

        bump = nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = bump_strength
        bump.inputs["Distance"].default_value = 0.0002

        links.new(tex_coord.outputs["Object"], noise.inputs["Vector"])
        links.new(noise.outputs["Fac"], bump.inputs["Height"])
        links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])

    links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def create_oled_bezel_material(name: str = "OLED_Bezel_Black") -> bpy.types.Material:
    """Deep neutral jet-black substrate surrounding the active OLED display."""
    mat = bpy.data.materials.new(name=name)
    node_tree = enable_nodes(mat)
    nodes = node_tree.nodes
    links = node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    # Matches the deep charcoal/pine edge tone of the Interval UI seamlessly
    bsdf.inputs["Base Color"].default_value = (0.003, 0.004, 0.005, 1.0)
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 0.14
    if "Specular IOR Level" in bsdf.inputs:
        bsdf.inputs["Specular IOR Level"].default_value = 0.12

    links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    return mat


def create_screen_material(name: str, image: bpy.types.Image) -> bpy.types.Material:
    """Color-accurate Emission screen shader (Strength = 1.0, sRGB)."""
    image.colorspace_settings.name = "sRGB"

    mat = bpy.data.materials.new(name=name)
    node_tree = enable_nodes(mat)
    nodes = node_tree.nodes
    links = node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Strength"].default_value = 1.0

    tex = nodes.new("ShaderNodeTexImage")
    tex.image = image
    tex.interpolation = "Cubic"
    tex.extension = "EXTEND"

    uv = nodes.new("ShaderNodeTexCoord")

    links.new(uv.outputs["UV"], tex.inputs["Vector"])
    links.new(tex.outputs["Color"], emission.inputs["Color"])
    links.new(emission.outputs["Emission"], out.inputs["Surface"])
    return mat


def create_glass_overlay_material(name: str = "CoverGlass_AR") -> bpy.types.Material:
    """Subtle low-roughness anti-reflective glass overlay for slight physical depth.

    Uses a thin-sheet Transparent + Glossy mix driven by Layer Weight so UI text
    behind the glass stays 100% crisp and unwarped with zero refraction distortion.
    """
    mat = bpy.data.materials.new(name=name)
    node_tree = enable_nodes(mat)
    nodes = node_tree.nodes
    links = node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    mix = nodes.new("ShaderNodeMixShader")

    transparent = nodes.new("ShaderNodeBsdfTransparent")
    transparent.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1.0)

    glossy = nodes.new("ShaderNodeBsdfGlossy")
    glossy.inputs["Color"].default_value = (0.85, 0.90, 0.98, 1.0)
    glossy.inputs["Roughness"].default_value = 0.04

    layer_weight = nodes.new("ShaderNodeLayerWeight")
    layer_weight.inputs["Blend"].default_value = 0.16

    # Keep specular reflection subtle so dark Theme.kt UI never washes out
    map_range = nodes.new("ShaderNodeMapRange")
    map_range.clamp = True
    map_range.inputs["From Min"].default_value = 0.0
    map_range.inputs["From Max"].default_value = 1.0
    map_range.inputs["To Min"].default_value = 0.015
    map_range.inputs["To Max"].default_value = 0.055

    links.new(layer_weight.outputs["Facing"], map_range.inputs["Value"])
    links.new(map_range.outputs["Result"], mix.inputs["Fac"])
    links.new(transparent.outputs["BSDF"], mix.inputs[1])
    links.new(glossy.outputs["BSDF"], mix.inputs[2])
    links.new(mix.outputs["Shader"], out.inputs["Surface"])
    return mat


# ---------------------------------------------------------------------------
# Device Assembly
# ---------------------------------------------------------------------------


def create_device_rig(
    placement: DevicePlacement,
    screenshots_dir: Path,
    shared_mats: dict[str, bpy.types.Material],
) -> tuple[bpy.types.Object, list[bpy.types.Object]]:
    """Build a complete device hierarchy sized accurately to its screenshot dimensions."""
    img_path = screenshots_dir / placement.screenshot
    if not img_path.is_file():
        raise FileNotFoundError(f"Screenshot not found: {img_path}")

    image = bpy.data.images.load(str(img_path), check_existing=True)
    img_w, img_h = image.size[0], image.size[1]
    if img_w <= 0 or img_h <= 0:
        raise ValueError(f"Invalid image dimensions for {img_path}: {img_w}x{img_h}")

    spec = placement.spec
    screen_w = spec.screen_width
    screen_h = screen_w * (img_h / img_w)

    glass_w = screen_w + 2.0 * spec.bezel_width
    glass_h = screen_h + 2.0 * spec.bezel_width
    glass_r = spec.screen_corner_radius + spec.bezel_width

    outer_w = glass_w + 2.0 * spec.rail_width
    outer_h = glass_h + 2.0 * spec.rail_width

    root = bpy.data.objects.new(f"{placement.key}_Root", None)
    bpy.context.collection.objects.link(root)

    y_front = -spec.thickness * 0.5
    y_glass = y_front + 0.00010
    y_screen = y_glass + spec.glass_offset
    y_bezel = y_screen + 0.00014

    mat_screen = create_screen_material(f"{placement.key}_ScreenMat", image)

    frame_obj = build_chamfered_frame(
        name=f"{placement.key}_Chassis",
        glass_w=glass_w,
        glass_h=glass_h,
        glass_r=glass_r,
        rail_w=spec.rail_width,
        thickness=spec.thickness,
        chamfer=spec.chamfer,
        mat_body=shared_mats["titanium_body"],
        mat_chamfer=shared_mats["titanium_chamfer"],
        mat_back=shared_mats["titanium_back"],
    )

    bezel_obj = build_flat_rounded_plate(
        name=f"{placement.key}_Bezel",
        width=glass_w,
        height=glass_h,
        radius=glass_r,
        y_pos=y_bezel,
        material=shared_mats["oled_bezel"],
        with_screen_uvs=False,
    )

    screen_obj = build_flat_rounded_plate(
        name=f"{placement.key}_Screen",
        width=screen_w,
        height=screen_h,
        radius=spec.screen_corner_radius,
        y_pos=y_screen,
        material=mat_screen,
        with_screen_uvs=True,
    )

    glass_obj = build_cover_glass(
        name=f"{placement.key}_Glass",
        width=glass_w,
        height=glass_h,
        radius=glass_r,
        y_front=y_glass,
        material=shared_mats["glass_overlay"],
    )

    children: list[bpy.types.Object] = [frame_obj, bezel_obj, screen_obj, glass_obj]

    # Subtle machined hardware buttons (zero branding)
    if not spec.is_tablet:
        btn_protrude = 0.00075
        btn_depth = spec.thickness * 0.34
        # Power button on right (+X) rail
        children.append(
            build_side_button(
                name=f"{placement.key}_PowerBtn",
                size_xyz=(btn_protrude * 2.0, btn_depth, 0.0155),
                location=(outer_w * 0.5, 0.0, outer_h * 0.14),
                material=shared_mats["titanium_chamfer"],
            )
        )
        # Volume Up & Down buttons on left (-X) rail
        children.append(
            build_side_button(
                name=f"{placement.key}_VolUpBtn",
                size_xyz=(btn_protrude * 2.0, btn_depth, 0.0125),
                location=(-outer_w * 0.5, 0.0, outer_h * 0.19),
                material=shared_mats["titanium_chamfer"],
            )
        )
        children.append(
            build_side_button(
                name=f"{placement.key}_VolDownBtn",
                size_xyz=(btn_protrude * 2.0, btn_depth, 0.0125),
                location=(-outer_w * 0.5, 0.0, outer_h * 0.095),
                material=shared_mats["titanium_chamfer"],
            )
        )
    else:
        btn_protrude = 0.00065
        btn_depth = spec.thickness * 0.36
        # Top-left power button & top-right volume buttons on landscape tablet
        children.append(
            build_side_button(
                name=f"{placement.key}_TopPowerBtn",
                size_xyz=(0.015, btn_depth, btn_protrude * 2.0),
                location=(outer_w * 0.34, 0.0, outer_h * 0.5),
                material=shared_mats["titanium_chamfer"],
            )
        )
        children.append(
            build_side_button(
                name=f"{placement.key}_VolBtn1",
                size_xyz=(0.013, btn_depth, btn_protrude * 2.0),
                location=(outer_w * 0.21, 0.0, outer_h * 0.5),
                material=shared_mats["titanium_chamfer"],
            )
        )
        children.append(
            build_side_button(
                name=f"{placement.key}_VolBtn2",
                size_xyz=(0.013, btn_depth, btn_protrude * 2.0),
                location=(outer_w * 0.11, 0.0, outer_h * 0.5),
                material=shared_mats["titanium_chamfer"],
            )
        )

    for child in children:
        child.parent = root

    rx, ry, rz = (math.radians(a) for a in placement.rotation_deg)
    root.location = Vector(placement.location)
    root.rotation_euler = Euler((rx, ry, rz), "XYZ")

    return root, children


# ---------------------------------------------------------------------------
# Studio Lighting, Camera & Render Settings
# ---------------------------------------------------------------------------


def configure_scene(res_x: int, res_y: int, samples: int) -> None:
    """Configure Cycles (Metal GPU if available), Standard sRGB color management, and RGBA output."""
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = samples
    scene.cycles.use_adaptive_sampling = True
    scene.cycles.adaptive_threshold = 0.015
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 6
    scene.cycles.diffuse_bounces = 2
    scene.cycles.glossy_bounces = 4
    scene.cycles.transmission_bounces = 2
    scene.cycles.transparent_max_bounces = 8

    # Prefer Apple Silicon Metal GPU when present; fall back cleanly to CPU
    scene.cycles.device = "CPU"
    try:
        cycles_prefs = bpy.context.preferences.addons["cycles"].preferences
        cycles_prefs.compute_device_type = "METAL"
        cycles_prefs.get_devices()
        gpu_enabled = False
        for dev in cycles_prefs.devices:
            if dev.type == "METAL":
                dev.use = True
                gpu_enabled = True
            else:
                dev.use = False
        if gpu_enabled:
            scene.cycles.device = "GPU"
    except Exception:
        scene.cycles.device = "CPU"

    # Standard view transform keeps sRGB Emission (Strength = 1.0) pixel-accurate to Theme.kt
    scene.display_settings.display_device = "sRGB"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.view_settings.exposure = 0.0
    scene.view_settings.gamma = 1.0

    # Slightly tighter pixel filter for razor-sharp UI typography
    scene.cycles.pixel_filter_type = "BLACKMAN_HARRIS"
    scene.cycles.filter_width = 1.25

    scene.render.resolution_x = res_x
    scene.render.resolution_y = res_y
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.image_settings.compression = 15


def setup_studio_world() -> None:
    """Subtle dark studio gradient environment for metallic rim contrast."""
    scene = bpy.context.scene
    world = bpy.data.worlds.new("StudioWorld")
    scene.world = world
    node_tree = enable_nodes(world)
    nodes = node_tree.nodes
    links = node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputWorld")
    bg = nodes.new("ShaderNodeBackground")
    bg.inputs["Strength"].default_value = 0.35

    tex_coord = nodes.new("ShaderNodeTexCoord")
    sep = nodes.new("ShaderNodeSeparateXYZ")
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (0.03, 0.035, 0.042, 1.0)
    ramp.color_ramp.elements[1].position = 0.85
    ramp.color_ramp.elements[1].color = (0.16, 0.18, 0.21, 1.0)

    links.new(tex_coord.outputs["Generated"], sep.inputs["Vector"])
    links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], bg.inputs["Color"])
    links.new(bg.outputs["Background"], out.inputs["Surface"])


def add_area_light(
    name: str,
    location: tuple[float, float, float],
    target: tuple[float, float, float],
    energy: float,
    color: tuple[float, float, float],
    size_x: float,
    size_y: float,
    spread_deg: float = 95.0,
) -> bpy.types.Object:
    """Create a camera-invisible rectangular studio softbox pointing at target."""
    light_data = bpy.data.lights.new(name=name, type="AREA")
    light_data.shape = "RECTANGLE"
    light_data.size = size_x
    light_data.size_y = size_y
    light_data.energy = energy
    light_data.color = color
    if hasattr(light_data, "spread"):
        light_data.spread = math.radians(spread_deg)

    light_obj = bpy.data.objects.new(name=name, object_data=light_data)
    bpy.context.collection.objects.link(light_obj)
    light_obj.location = Vector(location)

    direction = Vector(target) - light_obj.location
    light_obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()

    # Keep transparent PNG background completely clean while preserving specular reflections
    light_obj.visible_camera = False
    return light_obj


def add_glass_sheen_card(
    name: str,
    location: tuple[float, float, float],
    target: tuple[float, float, float],
    size_x: float,
    size_y: float,
    peak_strength: float = 0.65,
) -> bpy.types.Object:
    """Create a soft, low-radiance gradient reflection card for subtle anti-reflective glass sheen.

    Unlike high-energy area lights, this soft gradient plane produces a gentle, controlled
    studio reflection across the cover glass without ever washing out dark UI screens.
    """
    bm = bmesh.new()
    bmesh.ops.create_grid(
        bm,
        x_segments=1,
        y_segments=1,
        size=0.5,
        matrix=Matrix.Diagonal((size_x, size_y, 1.0, 1.0)),
    )
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh)
    bm.free()

    mat = bpy.data.materials.new(name=f"{name}_Mat")
    node_tree = enable_nodes(mat)
    nodes = node_tree.nodes
    links = node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Strength"].default_value = peak_strength

    tex_coord = nodes.new("ShaderNodeTexCoord")
    sep = nodes.new("ShaderNodeSeparateXYZ")
    ramp = nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = "B_SPLINE"
    e0 = ramp.color_ramp.elements[0]
    e0.position = 0.0
    e0.color = (0.0, 0.0, 0.0, 1.0)
    e1 = ramp.color_ramp.elements[1]
    e1.position = 1.0
    e1.color = (0.0, 0.0, 0.0, 1.0)
    emid = ramp.color_ramp.elements.new(0.42)
    emid.color = (0.92, 0.96, 1.0, 1.0)

    links.new(tex_coord.outputs["Generated"], sep.inputs["Vector"])
    links.new(sep.outputs["Y"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], emission.inputs["Color"])
    links.new(emission.outputs["Emission"], out.inputs["Surface"])
    mesh.materials.append(mat)

    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.location = Vector(location)
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    obj.visible_camera = False
    obj.visible_diffuse = False
    obj.visible_shadow = False
    return obj


def setup_studio_lighting() -> None:
    """Balanced architectural studio lighting to sculpt dark titanium chamfers with zero screen glare.

    High-energy rim strips sit at steep lateral/overhead angles so they carve sharp specular
    highlights along the 45-degree titanium chamfers and hardware buttons without entering the
    direct camera-to-screen mirror reflection cone. A dedicated low-radiance gradient card adds
    a subtle, physical anti-reflective sheen across the front glass overlay.
    """
    # Right-side rim strip -> highlights right chamfer, flat titanium rail, and power button
    add_area_light(
        name="Rim_Strip_Right",
        location=(0.68, -0.06, 0.14),
        target=(0.0, 0.0, 0.0),
        energy=18.0,
        color=(0.94, 0.97, 1.0),
        size_x=0.14,
        size_y=0.78,
        spread_deg=65.0,
    )
    # Left-side rim strip -> traces the left chamfer and volume rockers cleanly
    add_area_light(
        name="Rim_Strip_Left",
        location=(-0.68, -0.04, 0.10),
        target=(0.0, 0.0, 0.0),
        energy=16.0,
        color=(0.95, 0.97, 1.0),
        size_x=0.14,
        size_y=0.76,
        spread_deg=65.0,
    )
    # Overhead top rim -> crisp highlight along top titanium bevel of phone and tablet
    add_area_light(
        name="Top_Strip",
        location=(0.0, -0.08, 0.68),
        target=(0.0, 0.0, 0.0),
        energy=14.0,
        color=(1.0, 0.99, 0.97),
        size_x=0.76,
        size_y=0.12,
        spread_deg=65.0,
    )
    # Lower rim -> subtle graphite sheen along bottom bezel chamfer
    add_area_light(
        name="Bottom_Rim",
        location=(0.0, -0.10, -0.66),
        target=(0.0, 0.0, 0.0),
        energy=8.5,
        color=(0.93, 0.96, 1.0),
        size_x=0.72,
        size_y=0.14,
        spread_deg=70.0,
    )
    # Soft diagonal reflection card for gentle, non-obstructive glass depth
    add_glass_sheen_card(
        name="Glass_Sheen_Card_Left",
        location=(-0.48, -0.78, 0.42),
        target=(0.0, 0.0, 0.02),
        size_x=0.55,
        size_y=0.42,
        peak_strength=0.90,
    )
    add_glass_sheen_card(
        name="Glass_Sheen_Card_Right",
        location=(0.52, -0.78, 0.36),
        target=(0.0, 0.0, 0.02),
        size_x=0.45,
        size_y=0.38,
        peak_strength=0.75,
    )


def world_mesh_points(objects: Iterable[bpy.types.Object]) -> list[Vector]:
    """Collect world-space vertices of all mesh objects for exact camera auto-framing."""
    bpy.context.view_layer.update()
    pts: list[Vector] = []
    for obj in objects:
        if obj.type != "MESH":
            continue
        mw = obj.matrix_world
        # Sample bounding-box + perimeter vertices for exact visual silhouette fit
        for v in obj.data.vertices:
            pts.append(mw @ v.co)
    return pts


def setup_fitted_camera(
    mesh_objects: list[bpy.types.Object],
    res_x: int,
    res_y: int,
    margin_x: float,
    margin_y: float,
    focal_length_mm: float = 85.0,
) -> bpy.types.Object:
    """Create a telephoto studio camera mathematically fitted to the devices' projected silhouette."""
    pts = world_mesh_points(mesh_objects)
    if not pts:
        raise RuntimeError("No mesh vertices found to frame with camera.")

    cam_data = bpy.data.cameras.new("StudioCamera")
    cam_data.lens = focal_length_mm
    cam_data.sensor_fit = "HORIZONTAL"
    cam_data.sensor_width = 36.0
    cam_data.clip_start = 0.01
    cam_data.clip_end = 50.0

    cam_obj = bpy.data.objects.new("StudioCamera", cam_data)
    bpy.context.collection.objects.link(cam_obj)
    bpy.context.scene.camera = cam_obj

    aspect = res_x / res_y
    tan_half_x = (cam_data.sensor_width * 0.5) / focal_length_mm
    tan_half_y = tan_half_x / aspect

    usable_x = 1.0 - 2.0 * margin_x
    usable_y = 1.0 - 2.0 * margin_y

    # Camera looks straight along +Y from (cx, cam_y, cz) with +Z up -> zero vertical keystoning
    # Iteratively refine (cx, cz, cam_y) to center the true perspective projection exactly.
    cx = 0.5 * (min(p.x for p in pts) + max(p.x for p in pts))
    cz = 0.5 * (min(p.z for p in pts) + max(p.z for p in pts))
    cam_y = min(p.y for p in pts) - 0.80

    for _ in range(12):
        # Compute required camera Y so every vertex stays within [-usable_x, +usable_x] and [-usable_y, +usable_y]
        req_y = min(p.y for p in pts) - 0.10
        for p in pts:
            dx = abs(p.x - cx)
            dz = abs(p.z - cz)
            need_dist_x = dx / (usable_x * tan_half_x)
            need_dist_y = dz / (usable_y * tan_half_y)
            need_dist = max(need_dist_x, need_dist_y)
            req_y = min(req_y, p.y - need_dist)
        cam_y = req_y

        # Recenter optical midpoint using perspective-projected NDC bounds
        ndc_x = [(p.x - cx) / ((p.y - cam_y) * tan_half_x) for p in pts]
        ndc_z = [(p.z - cz) / ((p.y - cam_y) * tan_half_y) for p in pts]
        mid_ndc_x = 0.5 * (min(ndc_x) + max(ndc_x))
        mid_ndc_z = 0.5 * (min(ndc_z) + max(ndc_z))
        avg_depth = sum(p.y - cam_y for p in pts) / len(pts)
        cx += mid_ndc_x * avg_depth * tan_half_x * 0.8
        cz += mid_ndc_z * avg_depth * tan_half_y * 0.8

    cam_obj.location = Vector((cx, cam_y, cz))
    cam_obj.rotation_euler = Euler((math.radians(90.0), 0.0, 0.0), "XYZ")
    return cam_obj


# ---------------------------------------------------------------------------
# Render Pipeline
# ---------------------------------------------------------------------------


def clear_scene() -> None:
    """Reset the Blender scene cleanly between renders."""
    bpy.ops.wm.read_factory_settings(use_empty=True)


def render_composition(
    comp: CompositionSpec,
    fmt_name: str,
    screenshots_dir: Path,
    output_dir: Path,
    samples: int,
    save_blend: bool,
) -> Path:
    """Build and render one composition in one target resolution format."""
    clear_scene()
    res_x, res_y = FORMATS[fmt_name]
    configure_scene(res_x=res_x, res_y=res_y, samples=samples)
    setup_studio_world()
    setup_studio_lighting()

    # Shared dark titanium/graphite hardware materials
    shared_mats = {
        "titanium_body": create_titanium_material(
            name="Titanium_Graphite_Body",
            base_color=(0.15, 0.165, 0.185),
            roughness=0.24,
            metallic=0.92,
            bump_strength=0.006,
        ),
        "titanium_chamfer": create_titanium_material(
            name="Titanium_Graphite_Chamfer",
            base_color=(0.28, 0.30, 0.33),
            roughness=0.14,
            metallic=0.95,
            bump_strength=0.002,
        ),
        "titanium_back": create_titanium_material(
            name="Titanium_Graphite_Back",
            base_color=(0.09, 0.10, 0.115),
            roughness=0.34,
            metallic=0.84,
            bump_strength=0.009,
        ),
        "oled_bezel": create_oled_bezel_material(),
        "glass_overlay": create_glass_overlay_material(),
    }

    all_meshes: list[bpy.types.Object] = []
    for placement in comp.placements_by_format[fmt_name]:
        _, children = create_device_rig(
            placement=placement,
            screenshots_dir=screenshots_dir,
            shared_mats=shared_mats,
        )
        all_meshes.extend(children)

    margin_x, margin_y = comp.margin_by_format[fmt_name]
    setup_fitted_camera(
        mesh_objects=all_meshes,
        res_x=res_x,
        res_y=res_y,
        margin_x=margin_x,
        margin_y=margin_y,
        focal_length_mm=85.0,
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / f"{comp.name}_{fmt_name}.png"
    bpy.context.scene.render.filepath = str(out_path)

    if save_blend:
        blend_path = output_dir / f"{comp.name}_{fmt_name}.blend"
        bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))

    print(
        f"[render_store_frames] Rendering {comp.name} ({fmt_name} {res_x}x{res_y}, "
        f"device={bpy.context.scene.cycles.device}, samples={samples}) -> {out_path}"
    )
    bpy.ops.render.render(write_still=True)
    return out_path


def parse_args() -> argparse.Namespace:
    argv = sys.argv
    if "--" in argv:
        argv = argv[argv.index("--") + 1 :]
    else:
        argv = []

    parser = argparse.ArgumentParser(
        description="Render Interval storefront device mockups in headless Blender."
    )
    parser.add_argument(
        "--only",
        nargs="+",
        choices=list(COMPOSITIONS.keys()),
        default=list(COMPOSITIONS.keys()),
        help="Compositions to render (default: all).",
    )
    parser.add_argument(
        "--formats",
        nargs="+",
        choices=list(FORMATS.keys()),
        default=list(FORMATS.keys()),
        help="Output aspect ratios to render (default: portrait and landscape).",
    )
    parser.add_argument(
        "--samples",
        type=int,
        default=96,
        help="Cycles render samples per pixel (default: 96).",
    )
    parser.add_argument(
        "--screenshots-dir",
        type=Path,
        default=DEFAULT_SCREENSHOTS_DIR,
        help="Directory containing source app screenshots.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help="Output directory for transparent PNG storefront renders.",
    )
    parser.add_argument(
        "--save-blend",
        action="store_true",
        help="Also save generated .blend files alongside renders.",
    )
    return parser.parse_args(argv)


def main() -> None:
    args = parse_args()
    screenshots_dir = args.screenshots_dir.resolve()
    output_dir = args.output_dir.resolve()

    rendered: list[Path] = []
    for comp_name in args.only:
        comp = COMPOSITIONS[comp_name]
        for fmt_name in args.formats:
            out_path = render_composition(
                comp=comp,
                fmt_name=fmt_name,
                screenshots_dir=screenshots_dir,
                output_dir=output_dir,
                samples=args.samples,
                save_blend=args.save_blend,
            )
            rendered.append(out_path)

    print(f"[render_store_frames] Done! Rendered {len(rendered)} file(s):")
    for p in rendered:
        size_kb = os.path.getsize(p) / 1024.0
        print(f"  - {p} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
