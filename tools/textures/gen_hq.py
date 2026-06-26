#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
High-quality second-pass art for ToxicSurface (DESIGN.md §3 / §5b Phase-8 art pass).

Unlike the first-pass generators (gen_items.py / gen_machines.py / gen_armor.py / gen_sludge.py,
kept for reference; their output archived under tools/textures/archive_v1/), this uses numpy +
Pillow for: tileable fractal value noise (2D and 3D for seamless animation), multi-step shaded
colour ramps with Bayer ordered dithering (smooth gradients that stay crunchy at 16px), bevels
with corner ambient occlusion, and soft emissive glows. Everything stays native-resolution pixel
art (no supersampling blur). Run from the repo root: `python3 tools/textures/gen_hq.py`.

Writes to the live asset paths, overwriting the first-pass PNGs (originals preserved in archive_v1/).
"""

import os
import zlib

import numpy as np
from PIL import Image


def seed_of(name):
    """Stable per-name seed. (Python's built-in hash() is salted by PYTHONHASHSEED and would make
    generation non-deterministic across runs.)"""
    return zlib.crc32(name.encode()) % 9999

ASSETS = "src/main/resources/assets/toxicsurface/textures"
ITEM = ASSETS + "/item"
BLOCK = ASSETS + "/block"
ARMOR = ASSETS + "/models/armor"
MISC = ASSETS + "/misc"

FRAMES = 16

# ----------------------------------------------------------------------------- noise
def _fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)  # quintic smoothstep


def vnoise2(h, w, period, seed):
    """Periodic 2D value noise in [0,1), wrapping every `period` lattice cells (tileable when
    period divides the texture size)."""
    rng = np.random.default_rng(seed)
    lat = rng.random((period, period))
    yy = np.arange(h) * period / h
    xx = np.arange(w) * period / w
    y0 = np.floor(yy).astype(int)
    x0 = np.floor(xx).astype(int)
    fy = _fade(yy - y0)[:, None]
    fx = _fade(xx - x0)[None, :]
    y0m, y1m = y0 % period, (y0 + 1) % period
    x0m, x1m = x0 % period, (x0 + 1) % period
    v00 = lat[np.ix_(y0m, x0m)]
    v01 = lat[np.ix_(y0m, x1m)]
    v10 = lat[np.ix_(y1m, x0m)]
    v11 = lat[np.ix_(y1m, x1m)]
    top = v00 * (1 - fx) + v01 * fx
    bot = v10 * (1 - fx) + v11 * fx
    return top * (1 - fy) + bot * fy


def fractal2(h, w, periods, seed):
    """Sum of octaves at the given (tileable) periods, amplitude halving each octave; normalised."""
    out = np.zeros((h, w))
    amp, tot = 1.0, 0.0
    for i, p in enumerate(periods):
        out += amp * vnoise2(h, w, p, seed + i * 17)
        tot += amp
        amp *= 0.5
    return out / tot


def vnoise3(h, w, f, period, fperiod, seed):
    """Periodic 3D value noise [frames,h,w] wrapping in x, y AND time → seamless animation loops."""
    rng = np.random.default_rng(seed)
    lat = rng.random((fperiod, period, period))
    yy = np.arange(h) * period / h
    xx = np.arange(w) * period / w
    tt = np.arange(f) * fperiod / f
    y0 = np.floor(yy).astype(int)
    x0 = np.floor(xx).astype(int)
    t0 = np.floor(tt).astype(int)
    fy = _fade(yy - y0)[None, :, None]
    fx = _fade(xx - x0)[None, None, :]
    ft = _fade(tt - t0)[:, None, None]
    y0m, y1m = y0 % period, (y0 + 1) % period
    x0m, x1m = x0 % period, (x0 + 1) % period
    t0m, t1m = t0 % fperiod, (t0 + 1) % fperiod

    def slab(tm):
        v00 = lat[np.ix_([tm], y0m, x0m)][0]
        v01 = lat[np.ix_([tm], y0m, x1m)][0]
        v10 = lat[np.ix_([tm], y1m, x0m)][0]
        v11 = lat[np.ix_([tm], y1m, x1m)][0]
        top = v00 * (1 - fx[0]) + v01 * fx[0]
        bot = v10 * (1 - fx[0]) + v11 * fx[0]
        return top * (1 - fy[0]) + bot * fy[0]

    s0 = np.stack([slab(t) for t in t0m])
    s1 = np.stack([slab(t) for t in t1m])
    return s0 * (1 - ft) + s1 * ft


# ----------------------------------------------------------------------------- colour
_BAYER = np.array(
    [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=float
) / 16.0 - 0.5 / 16.0


def bayer(h, w):
    return np.tile(_BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w]


def ramp(colors, t, dither=None):
    """Map t in [0,1] (HxW) through a list of RGB stops with optional ordered dithering between
    adjacent stops, returning HxWx3 uint8. Keeps gradients smooth yet crunchy."""
    colors = np.array(colors, dtype=float)
    n = len(colors) - 1
    tt = np.clip(t, 0, 1) * n
    if dither is not None:
        tt = tt + dither
    tt = np.clip(tt, 0, n)
    lo = np.floor(tt).astype(int)
    lo = np.clip(lo, 0, n - 1)
    out = colors[lo]  # nearest-low stop; dithered fraction pushes pixels across the boundary
    return out.astype(np.uint8)


def lerp(a, b, t):
    a = np.array(a, float)
    b = np.array(b, float)
    return a + (b - a) * t


def save(path, rgb, alpha):
    """rgb: HxWx3 float/uint8; alpha: HxW float[0..1] or uint8."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rgb = np.clip(np.asarray(rgb), 0, 255).astype(np.uint8)
    a = np.asarray(alpha)
    if a.dtype != np.uint8:
        a = np.clip(a * 255, 0, 255).astype(np.uint8)
    img = np.dstack([rgb, a])
    Image.fromarray(img, "RGBA").save(path)


def blank(h=16, w=16):
    return np.zeros((h, w, 3), float), np.zeros((h, w), float)


def disc(cy, cx, r, h=16, w=16):
    yy, xx = np.mgrid[0:h, 0:w]
    return ((yy + 0.5 - cy) ** 2 + (xx + 0.5 - cx) ** 2) <= r * r


# ----------------------------------------------------------------------------- sludge anim
def _sludge_color(lum):
    """Luminance map → faint-green-biased neutral RGB (runtime olive tint does the colouring)."""
    lum = np.clip(lum, 0, 1)
    return np.dstack([lum * 232, lum * 244, lum * 196])


def sludge_still():
    h = w = 16
    f = FRAMES
    base = fractal3(h, w, f, periods=(2, 4, 8), fp=2, seed=11)
    warp = vnoise3(h, w, f, 4, 2, seed=21) - 0.5
    # domain-warp for a churning, viscous surface
    sl = base + 0.18 * warp
    lum = 0.62 + (sl - sl.mean()) * 1.15
    # rising bubbles: bright soft caps that loop vertically
    yy, xx = np.mgrid[0:h, 0:w]
    bubbles = [(3, 4, 1), (11, 12, 1), (6, 9, 2), (13, 3, 1), (8, 14, 2)]
    frames = []
    for fr in range(f):
        L = lum[fr].copy()
        for bx, by, k in bubbles:
            cy = (by - (fr / f) * h * k) % h
            d2 = (yy + 0.5 - cy) ** 2 + (xx + 0.5 - bx) ** 2
            L += np.exp(-d2 / 2.2) * 0.42
            L -= np.exp(-d2 / 7.0) * 0.10  # dark halo for depth
        frames.append(np.clip(L, 0.08, 1.12))
    strip = np.concatenate(frames, axis=0)
    save(os.path.join(BLOCK, "sludge_still.png"), _sludge_color(strip), np.ones_like(strip))


def sludge_flow():
    h = w = 32
    f = FRAMES
    streak = vnoise2(h, w, 8, seed=31)  # vertical-ish streaks, constant down the column
    streak = streak.mean(axis=0, keepdims=True).repeat(h, axis=0)
    frames = []
    for fr in range(f):
        shift = fr * (h / f)
        rip = vnoise2(h, w, 8, seed=32)
        rip = np.roll(rip, int(round(shift)), axis=0)
        warp = vnoise3(h, w, f, 4, 2, seed=33)[fr] - 0.5
        L = 0.55 + (streak - 0.5) * 0.5 + (rip - 0.5) * 0.32 + warp * 0.12
        # faint downward drip highlights
        frames.append(np.clip(L, 0.08, 1.1))
    strip = np.concatenate(frames, axis=0)
    save(os.path.join(BLOCK, "sludge_flow.png"), _sludge_color(strip), np.ones_like(strip))


def sludge_overlay():
    h = w = 16
    n = fractal2(h, w, (2, 4, 8), seed=41)
    L = 0.52 + (n - 0.5) * 0.55
    save(os.path.join(BLOCK, "sludge_overlay.png"), _sludge_color(np.clip(L, 0, 1)), np.ones((h, w)))


def fractal3(h, w, f, periods, fp, seed):
    out = np.zeros((f, h, w))
    amp, tot = 1.0, 0.0
    for i, p in enumerate(periods):
        out += amp * vnoise3(h, w, f, p, fp, seed + i * 13)
        tot += amp
        amp *= 0.5
    return out / tot


def sludge_underwater():
    h = w = 64
    n = fractal2(h, w, (4, 8, 16), seed=51)
    n2 = fractal2(h, w, (16, 32), seed=52)
    murk = 0.5 * n + 0.5 * n2
    r = 14 + murk * 26
    g = 30 + murk * 46
    b = 10 + murk * 22
    rgb = np.dstack([r, g, b])
    save(os.path.join(MISC, "sludge_underwater.png"), rgb, np.full((h, w), 235 / 255.0))


def hazmat_visor():
    """Full-screen hazmat-helmet visor overlay, blitted stretched over the screen (like the vanilla
    pumpkin overlay): a transparent rounded viewport ringed by tinted green glass that thickens to a
    near-opaque dark rubber frame, with breath condensation pooling at the corners, faint scratches,
    and a soft reflection streak. Authored on a 256x256 square; the engine stretches it to fit."""
    S = 256
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    c = (S - 1) / 2.0
    nx = (xx - c) / (S / 2.0)
    ny = (yy - c) / (S / 2.0)
    # Superellipse "distance" → a rounded-rectangle viewport (0 centre, 1 at edge mids, >1 corners).
    pe = 3.0
    d = (np.abs(nx) ** pe + np.abs(ny) ** pe) ** (1.0 / pe)

    glass = np.clip((d - 0.34) / (0.82 - 0.34), 0, 1) ** 1.7  # tint builds toward the rim
    frame = np.clip((d - 0.80) / (0.97 - 0.80), 0, 1)  # opaque dark frame past the rim
    n = fractal2(S, S, (4, 8, 16, 32), seed=401)

    # Condensation: pale fog gathering near the top/bottom edges and the corners.
    fog_band = np.clip((np.abs(ny) - 0.42) / 0.55, 0, 1)
    corner = np.clip((d - 0.5) / 0.45, 0, 1)
    fog = np.clip(fog_band * 0.55 + corner * 0.5, 0, 1) * np.clip(n * 1.35, 0, 1)

    glass_a = 0.05 + glass * 0.30
    fog_a = fog * 0.22
    alpha = np.maximum(glass_a + fog_a, frame)

    glass_col = np.array([30, 52, 26], float)  # toxic-green glass
    frame_col = np.array([10, 14, 10], float)  # near-black rubber/metal frame
    fog_col = np.array([150, 175, 122], float)  # pale condensation
    col = np.ones((S, S, 1)) * glass_col[None, None, :]
    fa = fog[..., None] * 0.8
    col = col * (1 - fa) + fog_col[None, None, :] * fa
    fr = frame[..., None]
    col = col * (1 - fr) + frame_col[None, None, :] * fr

    # A few hairline scratches across the glass (bright, low-alpha).
    rng = np.random.default_rng(402)
    for _ in range(6):
        x0, y0 = rng.uniform(0.25, 0.75, 2) * S
        ang = rng.uniform(0, np.pi)
        ln = rng.uniform(0.10, 0.30) * S
        x1, y1 = x0 + np.cos(ang) * ln, y0 + np.sin(ang) * ln
        dx, dy = x1 - x0, y1 - y0
        t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / (dx * dx + dy * dy + 1e-6), 0, 1)
        px, py = x0 + t * dx, y0 + t * dy
        dist = np.hypot(xx - px, yy - py)
        s = np.exp(-(dist**2) / 1.4) * (1 - glass * 0.5)  # fade scratches in the clear centre
        alpha = np.clip(alpha + s * 0.16, 0, 1)
        col = col + s[..., None] * np.array([70, 90, 70])

    # Soft diagonal reflection streak across the upper-left glass.
    refl = np.clip(1 - np.abs((nx - ny) + 0.5) / 0.16, 0, 1) * np.clip(0.7 - d, 0, 1)
    col = col + refl[..., None] * np.array([60, 78, 58])
    alpha = np.clip(alpha + refl * 0.08, 0, 1)

    save(os.path.join(MISC, "hazmat_visor.png"), col, alpha)


# ----------------------------------------------------------------------------- shared paint
def pick(colors, t, d=0.0):
    """Scalar ramp lookup with optional dither offset → an RGB tuple."""
    colors = np.array(colors, float)
    n = len(colors) - 1
    tt = np.clip(np.clip(t, 0, 1) * n + d, 0, n)
    lo = int(np.clip(np.floor(tt), 0, n - 1))
    return colors[lo]


def beveled(rgb, a, x0, y0, x1, y1, palette, noise, bd, lift=0.0, ao=0.0):
    """Fill a rectangle with noise-shaded ramp + 1px directional bevel (hi top/left, lo bottom/right)
    and optional corner ambient-occlusion darkening."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rad = max(x1 - x0, y1 - y0) / 2 + 1e-3
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = 0.5 + (noise[y, x] - 0.5) * 0.7 + lift
            if x == x0 or y == y0:
                t += 0.42
            if x == x1 or y == y1:
                t -= 0.4
            if ao:
                r = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5 / rad
                t -= ao * max(0.0, r - 0.55)
            rgb[y, x] = pick(palette, t, bd[y, x])
            a[y, x] = 1.0


def rivet(rgb, a, cx, cy, dark=(34, 36, 38), hi=(150, 154, 158)):
    for (dx, dy) in [(0, 0), (1, 0), (0, 1), (1, 1)]:
        x, y = cx + dx, cy + dy
        if 0 <= x < 16 and 0 <= y < 16:
            rgb[y, x] = dark
            a[y, x] = 1.0
    rgb[cy, cx] = hi  # top-left specular


def rounded(a, inset=0):
    """Knock out the 4 extreme corners so square icons read less boxy."""
    for (x, y) in [(inset, inset), (15 - inset, inset), (inset, 15 - inset), (15 - inset, 15 - inset)]:
        a[y, x] = 0.0


def outline(rgb, a, color=(28, 30, 26)):
    """Dark 1px rim around every opaque region (vanilla-style readability)."""
    src = a.copy()
    for y in range(16):
        for x in range(16):
            if src[y, x] > 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src[ny, nx] > 0:
                    rgb[y, x] = color
                    a[y, x] = 1.0
                    break


# ----------------------------------------------------------------------------- items: filters
def filter_pad(name, media, rim, stains=None):
    rgb, a = blank()
    noise = fractal2(16, 16, (4, 8), seed=seed_of(name))
    bd = bayer(16, 16)
    rim_hi = tuple(min(255, c + 40) for c in rim)
    # mesh media inside a dark frame
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            if edge:
                rgb[y, x] = rim_hi if (x == 2 or y == 2) else rim
                a[y, x] = 1.0
            else:
                mesh = (x + y) % 2 == 0
                t = 0.55 + (noise[y, x] - 0.5) * 0.6 + (0.16 if mesh else -0.16)
                # AO toward the frame
                t -= 0.18 * max(0, (max(abs(x - 7.5), abs(y - 7.5)) - 3) / 3)
                rgb[y, x] = pick(media, t, bd[y, x])
                a[y, x] = 1.0
    if stains:
        sn = fractal2(16, 16, (4, 8), seed=7)
        for (sx, sy, sc, rad) in stains:
            for y in range(3, 13):
                for x in range(3, 13):
                    d = ((x - sx) ** 2 + (y - sy) ** 2) ** 0.5
                    if d < rad and sn[y, x] > 0.4:
                        f = (1 - d / rad) * 0.8
                        rgb[y, x] = lerp(rgb[y, x], sc, f)
    for cx, cy in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        rivet(rgb, a, cx, cy, dark=rim)
    rounded(a)
    outline(rgb, a, color=tuple(max(0, c - 30) for c in rim))
    save(os.path.join(ITEM, name + ".png"), rgb, a)


def industrial_pad(name, media, sheen=False):
    rgb, a = blank()
    noise = fractal2(16, 16, (4, 8, 16), seed=seed_of(name))
    bd = bayer(16, 16)
    steel = [(70, 73, 77), (104, 108, 112), (150, 154, 158), (196, 200, 204)]
    # heavy steel frame
    beveled(rgb, a, 1, 1, 14, 14, steel, noise, bd)
    # recessed pleated media window
    for y in range(4, 12):
        for x in range(4, 12):
            pleat = 0.5 + 0.5 * np.sin((x - 4) * 1.4)  # vertical pleats
            t = 0.35 + pleat * 0.5 + (noise[y, x] - 0.5) * 0.25
            if x in (4, 11) or y in (4, 11):
                t -= 0.5  # recess shadow
            rgb[y, x] = pick(media, t, bd[y, x])
            a[y, x] = 1.0
    if sheen:
        for (x, y) in [(5, 5), (6, 5), (8, 8), (9, 9), (6, 10)]:
            rgb[y, x] = lerp(rgb[y, x], (210, 230, 245), 0.7)
    for cx, cy in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        rivet(rgb, a, cx, cy)
    save(os.path.join(ITEM, name + ".png"), rgb, a)


# ----------------------------------------------------------------------------- items: misc
def face_mask():
    rgb, a = blank()
    noise = fractal2(16, 16, (4, 8), seed=61)
    bd = bayer(16, 16)
    body = [(50, 53, 57), (84, 88, 93), (120, 125, 131), (158, 164, 170)]
    rows = {4: (5, 10), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (4, 11), 10: (5, 10)}
    cy, cx = 7, 7.5
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            r = (((x - cx) / 5) ** 2 + ((y - cy) / 3.4) ** 2) ** 0.5
            t = 0.85 - r * 0.7 + (noise[y, x] - 0.5) * 0.15  # round shaded form, lit upper-left
            rgb[y, x] = pick(body, t, bd[y, x])
            a[y, x] = 1.0
    # visor band
    for x in range(5, 11):
        for y in (6, 7):
            g = pick([(40, 90, 100), (90, 170, 185), (170, 225, 235)], 0.4 + 0.5 * ((x - 5) % 3 == 0), bd[y, x])
            rgb[y, x] = g
            a[y, x] = 1.0
    # filter cartridge at the chin
    cart = [(46, 60, 28), (78, 98, 44), (120, 150, 64)]
    for (x, y, tt) in [(7, 11, .8), (8, 11, .6), (6, 12, .5), (7, 12, .7), (8, 12, .5), (9, 12, .4), (7, 13, .4), (8, 13, .3)]:
        rgb[y, x] = pick(cart, tt, bd[y, x])
        a[y, x] = 1.0
    # straps
    for x in range(0, 4):
        rgb[5][x], a[5][x] = (52, 44, 40), 1.0
    for x in range(12, 16):
        rgb[5][x], a[5][x] = (52, 44, 40), 1.0
    outline(rgb, a, (30, 32, 34))
    save(os.path.join(ITEM, "face_mask.png"), rgb, a)


def toxic_residue():
    rgb, a = blank()
    noise = fractal2(16, 16, (4, 8), seed=71)
    bd = bayer(16, 16)
    pal = [(34, 44, 24), (66, 82, 40), (104, 126, 56), (150, 196, 74)]
    rows = {9: (6, 10), 10: (4, 12), 11: (3, 13), 12: (2, 13), 13: (2, 14), 14: (2, 14)}
    cx = 8
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            mound = 1 - abs(x - cx) / 7 - (14 - y) * 0.03  # lit dome
            t = 0.3 + mound * 0.6 + (noise[y, x] - 0.5) * 0.4
            if noise[y, x] > 0.78:
                rgb[y, x] = (164, 210, 86)  # toxic fleck
            else:
                rgb[y, x] = pick(pal, t, bd[y, x])
            a[y, x] = 1.0
    outline(rgb, a, (26, 32, 18))
    save(os.path.join(ITEM, "toxic_residue.png"), rgb, a)


def hazmat_material():
    rgb, a = blank()
    bd = bayer(16, 16)
    pal = [(150, 108, 18), (205, 158, 28), (240, 196, 46), (252, 222, 92)]
    for y in range(2, 14):
        for x in range(2, 14):
            twill = ((x + y) % 4) / 3.0  # gentle diagonal twill weave (no per-pixel noise)
            t = 0.45 + twill * 0.45
            if x in (2, 13) or y in (2, 13):
                t -= 0.4
            rgb[y, x] = pick(pal, t, bd[y, x])
            a[y, x] = 1.0
    rounded(a)
    outline(rgb, a, HAZ_OUTLINE)
    save(os.path.join(ITEM, "hazmat_material.png"), rgb, a)


# ----------------------------------------------------------------------------- items: armor icons
# Rubber-duck-yellow ramp (dark -> light) for the streamlined hazmat suit, with blue reflective tape.
HAZ = [(150, 108, 18), (205, 158, 28), (240, 196, 46), (252, 222, 92), (255, 240, 150)]
REFLECT = [(96, 150, 205), (152, 202, 238)]  # blue hi-vis reflective tape
HAZ_OUTLINE = (74, 52, 12)  # dark amber rim
HAZ_RUBBER = (54, 48, 40)  # dark rubber trim (boot soles)


def _haz_form(rgb, a, rows, bd, cx, cy, rx, ry):
    """Smooth rounded yellow form (radial shading only — no per-pixel noise, so no mottled dots)."""
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            r = (((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2) ** 0.5
            rgb[y, x] = pick(HAZ, 0.92 - r * 0.62, bd[y, x])
            a[y, x] = 1.0


def hazmat_helmet():
    rgb, a = blank()
    bd = bayer(16, 16)
    rows = {2: (5, 10), 3: (4, 11), 4: (3, 12), 5: (3, 12), 6: (3, 12), 7: (3, 12), 8: (4, 11), 9: (5, 10)}
    _haz_form(rgb, a, rows, bd, 7.5, 5, 5, 4)
    visor = [(28, 64, 78), (60, 130, 152), (130, 205, 226), (190, 240, 250)]
    for x in range(5, 11):
        for y in (6, 7):
            d = abs(x - 7.5) / 3.5
            rgb[y, x] = pick(visor, 0.95 - d * 0.6, bd[y, x])
            a[y, x] = 1.0
    outline(rgb, a, HAZ_OUTLINE)
    save(os.path.join(ITEM, "hazmat_helmet.png"), rgb, a)


def hazmat_chestplate():
    rgb, a = blank()
    bd = bayer(16, 16)
    rows = {2: (4, 11), 3: (3, 12), 4: (2, 13), 5: (2, 13), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (4, 11)}
    _haz_form(rgb, a, rows, bd, 7.5, 6, 6, 5)
    for x in range(3, 13):  # blue reflective chest band
        if a[7, x] > 0:
            rgb[7, x] = pick(REFLECT, 0.5 + ((x % 2) * 0.5), bd[7, x])
    outline(rgb, a, HAZ_OUTLINE)
    save(os.path.join(ITEM, "hazmat_chestplate.png"), rgb, a)


def hazmat_leggings():
    rgb, a = blank()
    bd = bayer(16, 16)
    rows = {3: (4, 11), 4: (4, 11), 5: (4, 11), 6: (4, 11)}
    _haz_form(rgb, a, rows, bd, 7.5, 5, 4.5, 4)
    for y in range(7, 12):
        for x in list(range(4, 7 - (y > 9))) + list(range(9, 12 - (y > 9))):
            r = abs(x - (5 if x < 7 else 10)) / 2
            rgb[y, x] = pick(HAZ, 0.82 - r * 0.22, bd[y, x])
            a[y, x] = 1.0
    for x in range(4, 12):  # blue belt
        if a[3, x] > 0:
            rgb[3, x] = pick(REFLECT, 0.5 + ((x % 2) * 0.5), bd[3, x])
    outline(rgb, a, HAZ_OUTLINE)
    save(os.path.join(ITEM, "hazmat_leggings.png"), rgb, a)


def hazmat_boots():
    rgb, a = blank()
    bd = bayer(16, 16)
    # Two boots split by an empty column at x=8 so the outline draws a dark seam between them.
    left = [(3, 6, 6), (3, 6, 7), (2, 7, 8), (2, 7, 9), (2, 7, 10)]
    right = [(9, 12, 6), (9, 12, 7), (9, 13, 8), (9, 13, 9), (9, 13, 10)]
    for (x0, x1, y) in left + right:
        for x in range(x0, x1 + 1):
            # darken the inner edges (toward the seam) for extra separation.
            inner = (x == x1 and (x0, x1, y) in left) or (x == x0 and (x0, x1, y) in right)
            rgb[y, x] = pick(HAZ, 0.5 if inner else 0.78, bd[y, x])
            a[y, x] = 1.0
    for x in range(2, 14):  # rubber soles
        if a[10, x] > 0:
            rgb[11, x] = HAZ_RUBBER
            a[11, x] = 1.0
    outline(rgb, a, HAZ_OUTLINE)
    save(os.path.join(ITEM, "hazmat_boots.png"), rgb, a)


# ----------------------------------------------------------------------------- machines (block faces)
CASING = [(64, 67, 71), (100, 104, 108), (146, 150, 154), (190, 194, 198)]


def _casing_face(noise, bd, base=CASING):
    rgb, a = np.zeros((16, 16, 3), float), np.ones((16, 16), float)
    beveled(rgb, a, 0, 0, 15, 15, base, noise, bd, ao=0.25)
    return rgb, a


def weaver_side():
    noise = fractal2(16, 16, (4, 8, 16), seed=101)
    bd = bayer(16, 16)
    rgb, a = _casing_face(noise, bd)
    weave = [(70, 80, 28), (120, 134, 44), (170, 156, 52), (200, 186, 70)]
    for y in range(4, 12):
        for x in range(4, 12):
            if x in (4, 11) or y in (4, 11):
                rgb[y, x] = (40, 46, 22)  # recessed frame
            else:
                over = (x + y) % 2 == 0
                t = 0.45 + (0.3 if over else -0.1) + (noise[y, x] - 0.5) * 0.2
                rgb[y, x] = pick(weave, t, bd[y, x])
    save(os.path.join(BLOCK, "weaver_side.png"), rgb, a)


def weaver_top():
    noise = fractal2(16, 16, (4, 8), seed=102)
    bd = bayer(16, 16)
    rgb, a = _casing_face(noise, bd, base=[(60, 63, 67), (96, 100, 104), (140, 144, 148), (182, 186, 190)])
    weave = [(70, 80, 28), (120, 134, 44), (174, 160, 54), (206, 192, 76)]
    for y in range(2, 14):
        for x in range(2, 14):
            if x in (2, 13) or y in (2, 13):
                rgb[y, x] = (44, 50, 24)
            else:
                over = (x + y) % 2 == 0
                rgb[y, x] = pick(weave, 0.45 + (0.3 if over else -0.05) + (noise[y, x] - 0.5) * 0.2, bd[y, x])
    save(os.path.join(BLOCK, "weaver_top.png"), rgb, a)


def cleanser_side():
    noise = fractal2(16, 16, (4, 8, 16), seed=103)
    bd = bayer(16, 16)
    rgb, a = _casing_face(noise, bd)
    glow = [(24, 60, 70), (50, 120, 140), (110, 200, 218), (190, 244, 252)]
    for y in range(5, 11):
        for x in range(5, 11):
            if x in (5, 10) or y in (5, 10):
                rgb[y, x] = (26, 54, 60)
            else:
                d = (((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5) / 2.5
                rgb[y, x] = pick(glow, 1.0 - d * 0.7 + (noise[y, x] - 0.5) * 0.15, bd[y, x])
    gold = [(120, 96, 30), (190, 160, 70), (236, 212, 130)]  # crafted-with-gold trim corner
    for (x, y, tt) in [(2, 2, .9), (3, 2, .6), (2, 3, .6), (4, 2, .4), (2, 4, .4)]:
        rgb[y, x] = pick(gold, tt, bd[y, x])
    save(os.path.join(BLOCK, "cleanser_side.png"), rgb, a)


def cleanser_top():
    noise = fractal2(16, 16, (4, 8), seed=104)
    bd = bayer(16, 16)
    rgb, a = _casing_face(noise, bd, base=[(60, 63, 67), (96, 100, 104), (140, 144, 148), (182, 186, 190)])
    for y in range(3, 13):
        for x in range(3, 13):
            slat = (y % 2 == 0)
            if slat:
                rgb[y, x] = pick([(30, 64, 72), (44, 92, 104)], 0.3 + (noise[y, x] - 0.5) * 0.3, bd[y, x])
            else:
                rgb[y, x] = pick([(90, 170, 184), (150, 214, 226), (196, 240, 248)], 0.5 + (noise[y, x] - 0.5) * 0.3, bd[y, x])
    save(os.path.join(BLOCK, "cleanser_top.png"), rgb, a)


def shaft_socket():
    noise = fractal2(16, 16, (4, 8), seed=105)
    bd = bayer(16, 16)
    rgb, a = _casing_face(noise, bd, base=[(60, 63, 67), (96, 100, 104), (140, 144, 148), (182, 186, 190)])
    ring = disc(8, 8, 4) & ~disc(8, 8, 2.6)
    hub = disc(8, 8, 2.6)
    brass = [(78, 58, 20), (140, 110, 46), (196, 168, 88), (232, 210, 140)]
    for y in range(16):
        for x in range(16):
            if ring[y, x]:
                rgb[y, x] = (44, 46, 50)  # recessed dark ring
            elif hub[y, x]:
                d = ((x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2) ** 0.5 / 2.6
                rgb[y, x] = pick(brass, 1.0 - d * 0.8 - (x > 8) * 0.1 - (y > 8) * 0.1, bd[y, x])
    save(os.path.join(BLOCK, "shaft_socket.png"), rgb, a)


def generator_side(name, ember, grime):
    noise = fractal2(16, 16, (4, 8, 16), seed=seed_of(name))
    bd = bayer(16, 16)
    rgb, a = _casing_face(noise, bd, base=[(58, 54, 52), (88, 84, 80), (124, 118, 112), (158, 150, 142)])
    # firebox: glowing grate with bloom + dark bars
    for y in range(8, 13):
        for x in range(4, 12):
            bar = (x % 2 == 0)
            if bar:
                rgb[y, x] = (34, 26, 22)
            else:
                d = abs(y - 10) / 3
                rgb[y, x] = pick([tuple(int(c * 0.4) for c in ember), ember, tuple(min(255, int(c * 1.25)) for c in ember)],
                                 0.8 - d * 0.5 + (noise[y, x] - 0.5) * 0.2, bd[y, x])
    # ember bloom spill below the grate
    for y in range(13, 15):
        for x in range(4, 12):
            if noise[y, x] > 0.55:
                rgb[y, x] = lerp(rgb[y, x], ember, 0.3)
    # grime weeping
    for (x, y) in [(5, 13), (8, 14), (10, 13), (6, 7), (9, 7)]:
        rgb[y, x] = lerp(rgb[y, x], grime, 0.6)
    save(os.path.join(BLOCK, name + ".png"), rgb, a)


def toxic_waste_block():
    # Tiles seamlessly: the fractal octaves all wrap at periods dividing 16. The old version's visible
    # border line came from a low-frequency blob octave plus a high-contrast dark-crack network that
    # repeated across every block; dropped both and tightened the palette so the repeat is far subtler.
    h = w = 16
    n = fractal2(h, w, (4, 8, 16), seed=111)
    bd = bayer(h, w)
    pal = [(52, 62, 38), (66, 78, 46), (82, 96, 56), (98, 114, 66)]  # tighter range = lower contrast
    spk = np.random.default_rng(113).random((h, w))
    rgb = np.zeros((h, w, 3), float)
    for y in range(h):
        for x in range(w):
            t = 0.28 + n[y, x] * 0.55
            if spk[y, x] > 0.94:
                rgb[y, x] = (140, 182, 80)  # sparse, dimmer toxic fleck
            elif spk[y, x] < 0.04:
                rgb[y, x] = (48, 58, 36)  # occasional subtle dark speck (no connected cracks)
            else:
                rgb[y, x] = pick(pal, t, bd[y, x])
    save(os.path.join(BLOCK, "toxic_waste_block.png"), rgb, np.ones((h, w)))


# ----------------------------------------------------------------------------- armor worn layers
def armor_layer(name, bands, visor=False):
    """Streamlined rubber-duck-yellow worn suit: flat yellow with faint panel seams (no per-pixel
    noise/dots) and blue hi-vis reflective tape bands."""
    h, w = 32, 64
    bd = bayer(h, w)
    pal = [(150, 108, 18), (205, 158, 28), (240, 196, 46), (252, 222, 92)]
    rgb = np.zeros((h, w, 3), float)
    for y in range(h):
        for x in range(w):
            t = 0.62  # flat yellow body
            if x % 8 == 0 or y % 8 == 0:
                t -= 0.4  # subtle panel seam
            rgb[y, x] = pick(pal, t, bd[y, x])
    for (y0, y1) in bands:  # blue reflective tape
        for y in range(y0, y1):
            for x in range(w):
                if not (x % 8 == 0 or y % 8 == 0):
                    rgb[y, x] = pick(REFLECT, 0.45 + ((x % 2) * 0.4), bd[y, x])
    if visor:
        _draw_helmet_visor(rgb, bd)
    save(os.path.join(ARMOR, name + ".png"), rgb, np.ones((h, w)))


def face_mask_worn_tex():
    """Texture for the 3D respirator worn-model (item/face_mask_worn): a rubber mask body with a cyan
    visor band along the top (mapped onto the front face's eye line) and a filter-canister patch
    (bottom-right). Authored so the model's UVs hit forgiving uniform regions."""
    S = 16
    bd = bayer(S, S)
    body = [(44, 70, 40), (70, 100, 56), (98, 132, 78)]  # mask green
    border = (36, 56, 32)  # darker green frame
    visor = [(40, 90, 104), (90, 175, 192), (170, 226, 238)]
    canister = [(72, 102, 58), (100, 134, 76)]
    hole = (32, 44, 28)
    rgb = np.zeros((S, S, 3), float)
    for y in range(S):
        for x in range(S):
            rgb[y, x] = pick(body, 0.5, bd[y, x])
    # FRONT face = texture region (x 0-7, y 0-4): a full green border framing the cyan visor.
    for y in range(0, 5):
        for x in range(0, 8):
            if x in (0, 7) or y in (0, 4):
                rgb[y, x] = border  # frame all four sides
            else:
                d = abs(x - 3.5) / 3.5
                rgb[y, x] = pick(visor, 1.0 - d * 0.55, bd[y, x])
    # CANISTER face = texture region (x 9-14, y 9-14): green base with filter holes in a ring.
    for y in range(9, 15):
        for x in range(9, 15):
            rgb[y, x] = pick(canister, 0.55, bd[y, x])
    cx, cy, ring = 11.5, 11.5, 1.7
    for k in range(8):  # eight holes evenly around the circle + one in the centre
        ang = k * np.pi / 4
        hx, hy = int(round(cx + np.cos(ang) * ring)), int(round(cy + np.sin(ang) * ring))
        if 9 <= hx <= 14 and 9 <= hy <= 14:
            rgb[hy, hx] = hole
    rgb[int(cy), int(cx)] = hole
    save(os.path.join(ITEM, "face_mask_worn.png"), rgb, np.ones((S, S)))


def _draw_helmet_visor(rgb, bd):
    """Paint a gas-mask faceplate onto the head's FRONT face (armor-layer UV x:8-15, y:8-15): a dark
    rubber mask with a glowing cyan visor band across the eyes and a small filter canister at the chin,
    so the worn hazmat helmet reads as a respirator hood over the yellow suit."""
    rubber = [(28, 32, 30), (46, 52, 48), (66, 74, 68)]
    glass = [(26, 60, 70), (60, 130, 150), (120, 200, 218), (190, 244, 252)]
    canister = [(46, 60, 28), (88, 110, 50), (130, 160, 70)]
    for y in range(8, 16):
        for x in range(8, 16):
            if 10 <= y <= 12:  # visor band across the eyes
                d = abs(x - 11.5) / 4.0
                rgb[y, x] = pick(glass, 1.0 - d * 0.7, bd[y, x])
            elif y >= 14 and 10 <= x <= 13:  # filter canister at the chin
                rgb[y, x] = pick(canister, 0.6, bd[y, x])
            else:  # dark rubber mask body framing the face
                rgb[y, x] = pick(rubber, 0.55, bd[y, x])


# ----------------------------------------------------------------------------- bucket (re-tint existing vanilla composite)
def sludge_bucket():
    """The bucket shape is a vanilla composite (gen_bucket.py); here we re-shade only its sludge fill,
    leaving the steel untouched. The fill is a uniform light-green sludge speckled with darker green
    flecks (not a vertical dark gradient, which read as a change to the bucket's shape)."""
    path = os.path.join(ITEM, "sludge_bucket.png")
    if not os.path.exists(path):
        return
    img = np.array(Image.open(path).convert("RGBA"), float)
    h, w, _ = img.shape
    # detect the olive sludge pixels (green-dominant, opaque) vs the grey steel.
    r, g, b, al = img[..., 0], img[..., 1], img[..., 2], img[..., 3]
    fluid = (al > 128) & (g > b + 8) & (g > r - 6) & (g > 40)
    base = [120, 148, 60]
    specks = [[64, 86, 34], [52, 70, 26], [80, 102, 42]]  # dark-green flecks, slight variety
    spk = np.random.default_rng(207).random((h, w))
    ys = np.where(fluid.any(axis=1))[0]
    top = ys.min() if len(ys) else 0
    for y in range(h):
        for x in range(w):
            if not fluid[y, x]:
                continue
            v = spk[y, x]
            # Light-green sludge throughout, sparsely flecked with darker green. The top fluid row
            # gets only a slightly brighter sheen (not a hard band) so it still reads as a surface.
            if v > 0.80:  # sparse dark speck
                img[y, x, :3] = specks[int(v * 1000) % 3]
            elif y == top and v < 0.5:
                img[y, x, :3] = [142, 170, 78]  # faint surface sheen
            else:
                img[y, x, :3] = base
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(path)


if __name__ == "__main__":
    # sludge
    sludge_still()
    sludge_flow()
    sludge_overlay()
    sludge_underwater()
    hazmat_visor()
    # filters
    filter_pad("clean_air_filter", [(150, 156, 150), (196, 202, 196), (226, 232, 226), (246, 250, 246)], (66, 70, 66))
    filter_pad(
        "used_air_filter",
        [(96, 86, 60), (134, 122, 88), (168, 154, 114), (192, 178, 138)],
        (62, 56, 44),
        stains=[(6, 6, (92, 110, 54), 3.5), (10, 9, (78, 64, 42), 3.0), (7, 11, (96, 112, 56), 2.6)],
    )
    filter_pad("carbon_air_filter", [(26, 28, 32), (44, 46, 52), (66, 68, 76), (92, 94, 104)], (20, 22, 24))
    industrial_pad("industrial_filter", [(150, 156, 150), (190, 196, 190), (220, 226, 220), (244, 248, 244)])
    industrial_pad("dirty_industrial_filter", [(58, 70, 34), (88, 106, 50), (118, 138, 66), (150, 170, 84)])
    industrial_pad("wet_industrial_filter", [(80, 110, 128), (120, 152, 170), (160, 190, 206), (200, 224, 236)], sheen=True)
    face_mask()
    face_mask_worn_tex()
    toxic_residue()
    hazmat_material()
    hazmat_helmet()
    hazmat_chestplate()
    hazmat_leggings()
    hazmat_boots()
    # machines
    weaver_side()
    weaver_top()
    cleanser_side()
    cleanser_top()
    shaft_socket()
    generator_side("waste_generator_side", ember=(150, 200, 70), grime=(96, 120, 50))
    generator_side("sludge_generator_side", ember=(206, 150, 60), grime=(110, 96, 44))
    toxic_waste_block()
    # armor worn layers
    armor_layer("hazmat_layer_1", bands=[(20, 22), (26, 28)], visor=True)
    armor_layer("hazmat_layer_2", bands=[(28, 30)])
    # bucket re-shade
    sludge_bucket()
    print("HQ textures written")
