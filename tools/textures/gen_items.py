#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Procedural generator for ToxicSurface item/block textures (DESIGN.md §3, Phase 8 art pass).

Dependency-free (pure stdlib PNG writer). Produces simple, readable, consistently-themed 16x16
icons + their `item/generated` models, and the toxic-waste block texture. Run from the repo root:
`python3 tools/textures/gen_items.py`. Crude but distinguishable first-pass art an artist can refine.
"""

import os
import struct
import zlib

ASSETS = "src/main/resources/assets/toxicsurface"
TEX_ITEM = ASSETS + "/textures/item"
TEX_BLOCK = ASSETS + "/textures/block"
MODEL_ITEM = ASSETS + "/models/item"

T = (0, 0, 0, 0)  # transparent


def write_png(path, w, h, grid):
    def chunk(typ, data):
        return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            r, g, b, a = grid[y][x]
            raw += bytes((r, g, b, a))
    blob = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(blob)


def blank(c=T):
    return [[c for _ in range(16)] for _ in range(16)]


def item_model(name):
    os.makedirs(MODEL_ITEM, exist_ok=True)
    with open(os.path.join(MODEL_ITEM, name + ".json"), "w") as f:
        f.write(
            '{\n    "parent": "minecraft:item/generated",\n    "textures": {\n'
            '        "layer0": "toxicsurface:item/%s"\n    }\n}\n' % name
        )


def _shade(base, d):
    return (max(0, min(255, base[0] + d)), max(0, min(255, base[1] + d)), max(0, min(255, base[2] + d)), 255)


def _hash(x, y, s=0):
    n = (x * 73856093 ^ y * 19349663 ^ s * 83492791) & 0x7FFFFFFF
    return (n % 1000) / 1000.0


def _rgba(c):
    return c if len(c) == 4 else (c[0], c[1], c[2], 255)


# ---- Filters: a square respirator pad with a mesh grid and a 1px dark rim. ----
def filter_tex(name, fill, rim, mesh_shift, stains=None):
    rim = _rgba(rim)
    g = blank()
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x == 2 or x == 13 or y == 2 or y == 13
            if edge:
                g[y][x] = rim
            else:
                mesh = (x + y) % 3 == 0
                g[y][x] = _shade(fill, mesh_shift if mesh else 0)
    # corner tabs (mounting clips)
    for cx, cy in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        g[cy][cx] = rim
    if stains:
        for (sx, sy, sc) in stains:
            g[sy][sx] = sc
            if sx + 1 < 14:
                g[sy][sx + 1] = sc
    write_png(os.path.join(TEX_ITEM, name + ".png"), 16, 16, g)
    item_model(name)


# ---- Industrial filter: thick metal frame + bolts + mesh center. ----
def industrial_tex(name, center, sheen=None):
    g = blank()
    frame = (122, 126, 130, 255)
    frame_hi = (168, 172, 176, 255)
    frame_lo = (84, 88, 92, 255)
    bolt = (40, 42, 44, 255)
    for y in range(1, 15):
        for x in range(1, 15):
            outer = x <= 2 or x >= 13 or y <= 2 or y >= 13
            if outer:
                if x == 1 or y == 1:
                    g[y][x] = frame_hi
                elif x == 14 or y == 14:
                    g[y][x] = frame_lo
                else:
                    g[y][x] = frame
            else:
                mesh = (x + y) % 2 == 0
                g[y][x] = _shade(center, 14 if mesh else -14)
    for bx, by in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        g[by][bx] = bolt
    if sheen:
        for (x, y) in sheen:
            g[y][x] = (180, 205, 230, 255)
    write_png(os.path.join(TEX_ITEM, name + ".png"), 16, 16, g)
    item_model(name)


def toxic_residue_tex():
    g = blank()
    base = (86, 104, 54)
    # a low mound: rows widen toward the bottom
    spans = {12: (4, 11), 13: (3, 12), 14: (2, 13)}
    for y, (x0, x1) in spans.items():
        for x in range(x0, x1 + 1):
            n = _hash(x, y, 3)
            d = int((n - 0.5) * 60)
            c = _shade((base[0], base[1], base[2], 255), d)
            # toxic flecks
            if _hash(x, y, 9) > 0.82:
                c = (150, 200, 70, 255)
            elif _hash(x, y, 5) < 0.12:
                c = (40, 50, 28, 255)
            g[y][x] = c
    # a couple of granules above the mound
    for x, y in [(6, 11), (9, 11), (7, 10)]:
        g[y][x] = _shade((base[0], base[1], base[2], 255), 10)
    write_png(os.path.join(TEX_ITEM, "toxic_residue.png"), 16, 16, g)
    item_model("toxic_residue")


def hazmat_material_tex():
    g = blank()
    warp = (176, 156, 48, 255)  # hazmat yellow-green threads
    weft = (120, 132, 40, 255)
    dark = (74, 82, 28, 255)
    for y in range(2, 14):
        for x in range(2, 14):
            over = ((x // 1) + (y // 1)) % 2 == 0
            base = warp if over else weft
            if x in (2, 13) or y in (2, 13):
                base = dark
            g[y][x] = base
    write_png(os.path.join(TEX_ITEM, "hazmat_material.png"), 16, 16, g)
    item_model("hazmat_material")


def face_mask_tex():
    g = blank()
    body = (94, 98, 102, 255)
    body_hi = (132, 136, 140, 255)
    body_lo = (60, 63, 66, 255)
    strap = (52, 44, 40, 255)
    canister = (70, 86, 44, 255)  # filter canister, toxic green
    # rounded mask body
    rows = {4: (5, 10), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (4, 11), 10: (5, 10)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            if y == min(rows):
                g[y][x] = body_hi
            elif y == max(rows):
                g[y][x] = body_lo
            else:
                g[y][x] = body
    # straps
    for x in range(0, 4):
        g[5][x] = strap
    for x in range(12, 16):
        g[5][x] = strap
    # round filter canister at the chin
    for x, y in [(7, 11), (8, 11), (6, 12), (7, 12), (8, 12), (9, 12), (7, 13), (8, 13)]:
        g[y][x] = canister
    # eye/visor glints
    g[6][6] = (200, 210, 215, 255)
    g[6][9] = (200, 210, 215, 255)
    write_png(os.path.join(TEX_ITEM, "face_mask.png"), 16, 16, g)
    item_model("face_mask")


def toxic_waste_block_tex():
    # Full tileable cube face: compacted dark green-grey waste with lumps, cracks and toxic flecks.
    g = blank()
    base = (66, 78, 46)
    for y in range(16):
        for x in range(16):
            n = _hash(x, y, 1) * 0.6 + _hash(x // 2, y // 2, 2) * 0.4
            d = int((n - 0.5) * 46)
            c = _shade((base[0], base[1], base[2], 255), d)
            if _hash(x, y, 7) > 0.88:
                c = (150, 196, 74, 255)  # toxic fleck
            elif _hash(x, y, 4) < 0.08:
                c = (34, 42, 26, 255)  # dark crack
            g[y][x] = c
    write_png(os.path.join(TEX_BLOCK, "toxic_waste_block.png"), 16, 16, g)


if __name__ == "__main__":
    # Filters (clean / used / carbon).
    filter_tex("clean_air_filter", (206, 210, 200), (70, 74, 70), -22)
    filter_tex(
        "used_air_filter",
        (150, 140, 104),
        (66, 60, 48),
        -20,
        stains=[(5, 6, (96, 110, 56, 255)), (9, 9, (84, 70, 44, 255)), (7, 10, (96, 110, 56, 255))],
    )
    filter_tex("carbon_air_filter", (58, 60, 64), (28, 28, 30), 18)
    # Industrial filters (clean / dirty / wet).
    industrial_tex("industrial_filter", (198, 204, 196))
    industrial_tex("dirty_industrial_filter", (104, 120, 64))
    industrial_tex("wet_industrial_filter", (150, 168, 176), sheen=[(6, 6), (9, 10), (7, 8)])
    # Other consumables.
    toxic_residue_tex()
    hazmat_material_tex()
    face_mask_tex()
    # Block.
    toxic_waste_block_tex()
    print("generated item/block textures")
