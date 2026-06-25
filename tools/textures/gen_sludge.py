#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Procedural generator for the toxic-sludge textures (DESIGN.md §3 Toxic sludge).

Dependency-free (pure stdlib): writes 8-bit RGBA PNGs by hand. The fluid's client
extension multiplies these by an olive tint (0xFF4A5D23), so the textures are authored
as near-neutral *luminance* maps (light = thin/foamy sludge, dark = deep) with a faint
green bias; the tint supplies the toxic-olive hue.

Outputs (run from the repo root: `python3 tools/textures/gen_sludge.py`):
  assets/.../textures/block/sludge_still.png   (+ .mcmeta)   16 x (16*F), animated bubbling
  assets/.../textures/block/sludge_flow.png    (+ .mcmeta)   32 x (32*F), animated downward flow
  assets/.../textures/block/sludge_overlay.png               16 x 16, static
  assets/.../textures/item/sludge_bucket.png                 16 x 16, static

Loops are seamless: noise is sampled on a lattice that wraps in x, y and time, and the
flow pattern scrolls exactly one tile-height over the frame set.
"""

import math
import os
import struct
import zlib

OUT_BLOCK = "src/main/resources/assets/toxicsurface/textures/block"
OUT_ITEM = "src/main/resources/assets/toxicsurface/textures/item"
OUT_MISC = "src/main/resources/assets/toxicsurface/textures/misc"

FRAMES = 16


def write_png(path, w, h, px):
    """px: flat bytearray of w*h*4 RGBA bytes."""

    def chunk(typ, data):
        return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)  # filter type 0 (None)
        raw += px[y * stride : (y + 1) * stride]
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)  # 8-bit, colour type 6 (RGBA)
    blob = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(blob)


def write_mcmeta(path, frametime):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write('{\n    "animation": {\n        "frametime": %d\n    }\n}\n' % frametime)


def _hash(x, y, z, seed):
    n = (x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1013904223) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFFFFFF) / 0xFFFFFFFF


def _smooth(t):
    return t * t * (3 - 2 * t)


def vnoise(x, y, z, px, py, pz, seed=0):
    """Periodic 3D value noise in [0,1), wrapping at lattice periods px/py/pz."""
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    xf, yf, zf = x - xi, y - yi, z - zi
    u, v, w = _smooth(xf), _smooth(yf), _smooth(zf)

    def h(dx, dy, dz):
        return _hash((xi + dx) % px, (yi + dy) % py, (zi + dz) % pz, seed)

    x00 = h(0, 0, 0) + (h(1, 0, 0) - h(0, 0, 0)) * u
    x10 = h(0, 1, 0) + (h(1, 1, 0) - h(0, 1, 0)) * u
    x01 = h(0, 0, 1) + (h(1, 0, 1) - h(0, 0, 1)) * u
    x11 = h(0, 1, 1) + (h(1, 1, 1) - h(0, 1, 1)) * u
    y0 = x00 + (x10 - x00) * v
    y1 = x01 + (x11 - x01) * v
    return y0 + (y1 - y0) * w


def lum_to_rgba(lum, alpha=255):
    """Neutral luminance with a faint green bias; the fluid tint does the colouring."""
    lum = max(0, min(255, lum))
    return (int(lum * 0.95), int(lum), int(lum * 0.78), alpha)


def gen_still():
    size, f = 16, FRAMES
    px = bytearray(size * size * f * 4)
    # A few bubbles that rise and loop (vertical travel = whole-tile multiple).
    bubbles = [(2.0, 3.0, 1), (7.0, 11.0, 2), (11.0, 6.0, 1), (13.0, 14.0, 2), (5.0, 9.0, 1)]
    for fr in range(f):
        tz = fr * 2.0 / f  # time lattice period 2 → slow seamless drift
        for y in range(size):
            for x in range(size):
                n = vnoise(x * 4.0 / size, y * 4.0 / size, tz, 4, 4, 2, seed=1)
                n += 0.5 * vnoise(x * 8.0 / size, y * 8.0 / size, tz, 8, 8, 2, seed=2)
                lum = 168 + (n / 1.5 - 0.5) * 95
                for bx, by, k in bubbles:
                    cy = (by - (fr / f) * size * k) % size
                    d2 = (x - bx) ** 2 + (y - cy) ** 2
                    if d2 < 3.2:
                        lum += (3.2 - d2) * 22  # foamy bright cap
                r, g, b, a = lum_to_rgba(lum)
                i = ((fr * size + y) * size + x) * 4
                px[i], px[i + 1], px[i + 2], px[i + 3] = r, g, b, a
    write_png(os.path.join(OUT_BLOCK, "sludge_still.png"), size, size * f, px)
    write_mcmeta(os.path.join(OUT_BLOCK, "sludge_still.png.mcmeta"), 5)


def gen_flow():
    size, f = 32, FRAMES
    px = bytearray(size * size * f * 4)
    for fr in range(f):
        shift = fr * (size / f)  # scrolls exactly one tile over the frame set → seamless
        tz = fr * 2.0 / f
        for y in range(size):
            for x in range(size):
                # Vertical streaks: vary in x, constant in y (py=1), drifting slowly in time.
                band = vnoise(x * 6.0 / size, 0.0, tz, 6, 1, 2, seed=3)
                # Downward-scrolling ripples for the sense of motion.
                ripple = vnoise(x * 4.0 / size, (y + shift) * 4.0 / size, tz, 4, 4, 2, seed=4)
                lum = 150 + (band - 0.5) * 78 + (ripple - 0.5) * 44
                r, g, b, a = lum_to_rgba(lum)
                i = ((fr * size + y) * size + x) * 4
                px[i], px[i + 1], px[i + 2], px[i + 3] = r, g, b, a
    write_png(os.path.join(OUT_BLOCK, "sludge_flow.png"), size, size * f, px)
    write_mcmeta(os.path.join(OUT_BLOCK, "sludge_flow.png.mcmeta"), 2)


def gen_overlay():
    size = 16
    px = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            n = vnoise(x * 4.0 / size, y * 4.0 / size, 0.0, 4, 4, 1, seed=5)
            lum = 150 + (n - 0.5) * 60
            r, g, b, a = lum_to_rgba(lum)
            i = (y * size + x) * 4
            px[i], px[i + 1], px[i + 2], px[i + 3] = r, g, b, a
    write_png(os.path.join(OUT_BLOCK, "sludge_overlay.png"), size, size, px)


def gen_bucket():
    size = 16
    px = bytearray(size * size * 4)

    def put(x, y, rgba):
        if 0 <= x < size and 0 <= y < size:
            i = (y * size + x) * 4
            px[i], px[i + 1], px[i + 2], px[i + 3] = rgba

    OUTLINE = (40, 42, 44, 255)
    STEEL = (150, 154, 158, 255)
    STEEL_HI = (188, 192, 196, 255)
    STEEL_LO = (104, 108, 112, 255)
    SLUDGE = (74, 93, 35, 255)
    SLUDGE_HI = (120, 142, 60, 255)

    # Bucket body: trapezoid, wider at the top. left/right edges per row.
    body_top, body_bottom = 5, 14
    for y in range(body_top, body_bottom + 1):
        t = (y - body_top) / (body_bottom - body_top)
        left = round(3 + t * 1.5)
        right = round(12 - t * 1.5)
        for x in range(left, right + 1):
            if x == left or x == right or y == body_bottom:
                put(x, y, OUTLINE)
            elif x == left + 1:
                put(x, y, STEEL_HI)
            elif x == right - 1:
                put(x, y, STEEL_LO)
            else:
                put(x, y, STEEL)
    # Rim + sludge surface near the top.
    for x in range(3, 13):
        put(x, body_top, OUTLINE)
    for x in range(4, 12):
        put(x, body_top + 1, SLUDGE_HI if (x % 2 == 0) else SLUDGE)
        put(x, body_top + 2, SLUDGE)
    # Handle arc above the rim.
    for x, y in [(3, 4), (4, 3), (5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3), (12, 4)]:
        put(x, y, OUTLINE)

    write_png(os.path.join(OUT_ITEM, "sludge_bucket.png"), size, size, px)


def gen_underwater():
    """Full-screen submerged overlay (tiled 4x, drawn at ~0.1 alpha): a dark murky green so the
    camera in sludge gets the green 'underwater' tint, the way vanilla water uses a dark-blue one."""
    size = 64
    px = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            n = vnoise(x * 6.0 / size, y * 6.0 / size, 0.0, 6, 6, 1, seed=7)
            n += 0.5 * vnoise(x * 14.0 / size, y * 14.0 / size, 0.0, 14, 14, 1, seed=8)
            n /= 1.5
            r = int(18 + n * 26)
            g = int(34 + n * 44)
            b = int(12 + n * 20)
            i = (y * size + x) * 4
            px[i], px[i + 1], px[i + 2], px[i + 3] = r, g, b, 235
    write_png(os.path.join(OUT_MISC, "sludge_underwater.png"), size, size, px)


if __name__ == "__main__":
    gen_still()
    gen_flow()
    gen_overlay()
    gen_underwater()
    # NB: the sludge bucket icon is composited from the *vanilla* bucket textures by
    # tools/textures/gen_bucket.py (not the old crude drawing here), so it is not generated here.
    print("generated sludge textures")
