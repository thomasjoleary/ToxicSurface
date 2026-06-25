#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Procedural hazmat-suit art (DESIGN.md §3): the worn armour-layer textures the
`toxicsurface:hazmat` ArmorMaterial.Layer points at, plus the four 16x16 inventory
icons. Dependency-free pure-stdlib PNG writer. Run from the repo root:
`python3 tools/textures/gen_armor.py`.

The worn layers are 64x32 humanoid armour sheets filled with a hi-vis hazmat weave
(yellow-green threads, darker panel seams, a lighter reflective band) so the suit reads
as protective gear instead of the missing-texture checkerboard. The whole sheet is opaque;
only the body-part UV faces are ever sampled, so flood-filling is safe and gap-free.
"""

import os
import struct
import zlib

ASSETS = "src/main/resources/assets/toxicsurface"
TEX_ITEM = ASSETS + "/textures/item"
TEX_ARMOR = ASSETS + "/textures/models/armor"
MODEL_ITEM = ASSETS + "/models/item"

T = (0, 0, 0, 0)
WARP = (176, 156, 48, 255)  # hi-vis yellow-green threads
WEFT = (120, 132, 40, 255)
SEAM = (74, 82, 28, 255)  # dark panel seam
TAPE = (212, 222, 176, 255)  # reflective tape
GLASS = (120, 198, 208, 255)  # visor
GLASS_HI = (180, 226, 232, 255)
RUBBER = (60, 64, 50, 255)  # dark rubber trim


def write_png(path, w, h, grid):
    def chunk(typ, data):
        return struct.pack(">I", len(data)) + typ + data + struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(grid[y][x])
    blob = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(blob)


def _hash(x, y, s=0):
    n = (x * 73856093 ^ y * 19349663 ^ s * 83492791) & 0x7FFFFFFF
    return (n % 1000) / 1000.0


def _shade(c, d):
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), 255)


def weave(x, y):
    """Hazmat weave with subtle per-pixel noise; warp/weft checker for a fabric feel."""
    base = WARP if (x + y) % 2 == 0 else WEFT
    return _shade(base, int((_hash(x, y, 2) - 0.5) * 22))


def armor_layer(name, reflective_bands):
    """64x32 humanoid armour sheet flood-filled with hazmat weave + reflective tape bands."""
    w, h = 64, 32
    g = [[weave(x, y) for x in range(w)] for y in range(h)]
    # panel seams every 8px to suggest stitched suit panels.
    for y in range(h):
        for x in range(w):
            if x % 8 == 0 or y % 8 == 0:
                g[y][x] = SEAM
    # horizontal reflective tape bands across the given rows.
    for y0, y1 in reflective_bands:
        for y in range(y0, y1):
            for x in range(w):
                if g[y][x] != SEAM:
                    g[y][x] = TAPE
    write_png(os.path.join(TEX_ARMOR, name + ".png"), w, h, g)


def item_model(name):
    os.makedirs(MODEL_ITEM, exist_ok=True)
    with open(os.path.join(MODEL_ITEM, name + ".json"), "w") as f:
        f.write(
            '{\n    "parent": "minecraft:item/generated",\n    "textures": {\n'
            '        "layer0": "toxicsurface:item/%s"\n    }\n}\n' % name
        )


def blank():
    return [[T for _ in range(16)] for _ in range(16)]


def _fill(g, rows):
    """rows: {y: (x0, x1)} inclusive spans, filled with hazmat weave + a dark outline."""
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            g[y][x] = weave(x, y)


def _outline(g):
    """1px dark rim around every filled (non-transparent) region."""
    src = [row[:] for row in g]
    for y in range(16):
        for x in range(16):
            if src[y][x] != T:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src[ny][nx] != T:
                    g[y][x] = SEAM
                    break


def helmet_tex():
    g = blank()
    rows = {2: (5, 10), 3: (4, 11), 4: (3, 12), 5: (3, 12), 6: (3, 12), 7: (3, 12), 8: (4, 11), 9: (5, 10)}
    _fill(g, rows)
    # visor window across the eyes.
    for x in range(5, 11):
        g[6][x] = GLASS if x % 2 == 0 else GLASS_HI
        g[7][x] = GLASS
    _outline(g)
    write_png(os.path.join(TEX_ITEM, "hazmat_helmet.png"), 16, 16, g)
    item_model("hazmat_helmet")


def chestplate_tex():
    g = blank()
    rows = {
        2: (4, 11),  # collar
        3: (3, 12),
        4: (2, 13),  # shoulders
        5: (2, 13),
        6: (3, 12),
        7: (3, 12),
        8: (3, 12),
        9: (3, 12),
        10: (3, 12),
        11: (4, 11),
    }
    _fill(g, rows)
    # reflective chest band.
    for x in range(3, 13):
        g[7][x] = TAPE
    _outline(g)
    write_png(os.path.join(TEX_ITEM, "hazmat_chestplate.png"), 16, 16, g)
    item_model("hazmat_chestplate")


def leggings_tex():
    g = blank()
    rows = {
        3: (4, 11),  # waist
        4: (4, 11),
        5: (4, 11),
        6: (4, 11),
        7: (4, 7),  # split into two legs
        8: (4, 7),
        9: (4, 7),
        10: (4, 6),
        11: (4, 6),
    }
    _fill(g, rows)
    for y in range(7, 12):  # right leg
        for x in range(9, 12 if y < 10 else 11):
            g[y][x] = weave(x, y)
    # waist belt.
    for x in range(4, 12):
        g[3][x] = TAPE
    _outline(g)
    write_png(os.path.join(TEX_ITEM, "hazmat_leggings.png"), 16, 16, g)
    item_model("hazmat_leggings")


def boots_tex():
    g = blank()
    rows = {
        6: (3, 6), 7: (3, 6),  # left ankle
        8: (3, 6), 9: (2, 8),  # left foot
        10: (2, 8), 11: (2, 8),
    }
    _fill(g, rows)
    for y, (x0, x1) in {6: (9, 12), 7: (9, 12), 8: (9, 12), 9: (9, 13), 10: (9, 13), 11: (9, 13)}.items():
        for x in range(x0, x1 + 1):
            g[y][x] = weave(x, y)
    # dark rubber soles.
    for x in range(2, 14):
        if g[11][x] != T:
            g[11][x] = RUBBER
    _outline(g)
    write_png(os.path.join(TEX_ITEM, "hazmat_boots.png"), 16, 16, g)
    item_model("hazmat_boots")


if __name__ == "__main__":
    # Worn layers: layer_1 = helmet/chest/boots, layer_2 = leggings (vanilla split).
    armor_layer("hazmat_layer_1", reflective_bands=[(20, 22), (26, 28)])
    armor_layer("hazmat_layer_2", reflective_bands=[(28, 30)])
    helmet_tex()
    chestplate_tex()
    leggings_tex()
    boots_tex()
    print("generated hazmat armour textures")
