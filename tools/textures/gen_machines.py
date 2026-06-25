#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Procedural machine block textures (Weaver, Cleanser) in vanilla's outline-and-shade style:
a noisy metal panel with a beveled frame (highlight top/left, shadow bottom/right) and a central
motif. Dependency-free. Run from repo root: `python3 tools/textures/gen_machines.py`.
"""

import os
import struct
import zlib

OUT = "src/main/resources/assets/toxicsurface/textures/block"


def write_png(path, grid):
    h = len(grid)
    w = len(grid[0])

    def chunk(typ, d):
        return struct.pack(">I", len(d)) + typ + d + struct.pack(">I", zlib.crc32(typ + d) & 0xFFFFFFFF)

    raw = bytearray()
    for row in grid:
        raw.append(0)
        for px in row:
            raw += bytes(px)
    blob = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(blob)


def _h(x, y, s=0):
    n = (x * 73856093 ^ y * 19349663 ^ s * 83492791) & 0x7FFFFFFF
    return (n % 1000) / 1000.0


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, d):
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d), 255)


METAL = (120, 124, 128)
HI = 34
LO = -30
EDGE_HI = (172, 176, 180, 255)
EDGE_LO = (78, 81, 84, 255)


def metal_panel(base=METAL, noise=16):
    """16x16 noisy metal with a beveled raised-frame look (vanilla machine style)."""
    g = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            d = int((_h(x, y, 1) - 0.5) * noise)
            g[y][x] = shade(base, d)
            # 1px bevel: bright top/left, dark bottom/right.
            if x == 0 or y == 0:
                g[y][x] = EDGE_HI
            elif x == 15 or y == 15:
                g[y][x] = EDGE_LO
            elif x == 1 or y == 1:
                g[y][x] = shade(base, HI)
            elif x == 14 or y == 14:
                g[y][x] = shade(base, LO)
    return g


def inset(g, x0, y0, x1, y1, base):
    """A recessed inner panel (dark border, shaded fill)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (y0, y1):
                g[y][x] = shade(base, -42)
            else:
                g[y][x] = shade(base, int((_h(x, y, 4) - 0.5) * 14))


def weaver_side():
    g = metal_panel()
    panel = (96, 104, 70)
    inset(g, 4, 4, 11, 11, panel)
    # woven threads across the inset
    warp = (182, 162, 54, 255)
    weft = (126, 138, 44, 255)
    for y in range(5, 11):
        for x in range(5, 11):
            g[y][x] = warp if (x + y) % 2 == 0 else weft
    write_png(os.path.join(OUT, "weaver_side.png"), g)


def weaver_top():
    g = metal_panel(base=(112, 116, 120))
    warp = (182, 162, 54, 255)
    weft = (126, 138, 44, 255)
    dark = (78, 86, 30, 255)
    for y in range(2, 14):
        for x in range(2, 14):
            g[y][x] = dark if (x in (2, 13) or y in (2, 13)) else (warp if (x + y) % 2 == 0 else weft)
    write_png(os.path.join(OUT, "weaver_top.png"), g)


def cleanser_side():
    g = metal_panel()
    panel = (66, 120, 132)
    inset(g, 4, 4, 11, 11, panel)
    # cyan filter gauge + gold trim corner (crafted from gold + diamond)
    glow = (120, 198, 208, 255)
    for y in range(6, 10):
        for x in range(6, 10):
            g[y][x] = glow if (x + y) % 2 == 0 else shade(panel, 18)
    gold = (196, 168, 72, 255)
    for x, y in [(2, 2), (3, 2), (2, 3)]:
        g[y][x] = gold
    write_png(os.path.join(OUT, "cleanser_side.png"), g)


def cleanser_top():
    g = metal_panel(base=(112, 116, 120))
    slot = (54, 96, 108, 255)
    grate = (150, 196, 206, 255)
    for y in range(3, 13):
        for x in range(3, 13):
            # vent slats: dark slots with cyan highlights
            g[y][x] = slot if (y % 2 == 0) else grate if (y % 4 == 1) else shade((96, 132, 140), int((_h(x, y, 6) - 0.5) * 12))
    write_png(os.path.join(OUT, "cleanser_top.png"), g)


def shaft_socket():
    """Kinetic connection face: a metal end-plate with a recessed round shaft hub in the centre.
    Used as the `end` (FACING-axis ends) of every Create kinetic machine in this mod."""
    g = metal_panel(base=(112, 116, 120))
    # recessed dark ring with a brassy shaft stub poking through.
    ring = (60, 62, 66, 255)
    shaft = (150, 130, 70, 255)
    shaft_hi = (196, 174, 96, 255)
    hub = [(6, 6), (7, 6), (8, 6), (9, 6), (6, 9), (7, 9), (8, 9), (9, 9), (6, 7), (6, 8), (9, 7), (9, 8)]
    for x, y in hub:
        g[y][x] = ring
    for y in range(7, 9):
        for x in range(7, 9):
            g[y][x] = shaft
    g[7][7] = shaft_hi
    write_png(os.path.join(OUT, "shaft_socket.png"), g)


def _generator_side(name, fire, grime):
    """Furnace-like firebox: dark body, a glowing grille low on the face, toxic grime streaks."""
    g = metal_panel(base=(96, 92, 88))
    # firebox grille (lower third), glowing bars between dark slots.
    for y in range(9, 13):
        for x in range(4, 12):
            g[y][x] = fire if (x % 2 == 0 and y % 2 == 1) else (40, 30, 26, 255)
    # toxic grime weeping down from the grille.
    for x, y in [(5, 13), (8, 13), (10, 13), (6, 8), (9, 8)]:
        g[y][x] = grime
    write_png(os.path.join(OUT, name + ".png"), g)


def waste_generator_side():
    # toxic-residue burner: sickly green flame.
    _generator_side("waste_generator_side", fire=(150, 200, 70, 255), grime=(96, 120, 50, 255))


def sludge_generator_side():
    # sludge burner: oily amber flame.
    _generator_side("sludge_generator_side", fire=(206, 150, 60, 255), grime=(110, 96, 44, 255))


if __name__ == "__main__":
    weaver_side()
    weaver_top()
    cleanser_side()
    cleanser_top()
    shaft_socket()
    waste_generator_side()
    sludge_generator_side()
    print("generated machine textures")
