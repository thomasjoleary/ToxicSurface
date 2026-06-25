#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Composites the toxic-sludge bucket icon from the *vanilla* bucket textures so it matches Minecraft's
bucket exactly, just with sludge in it (DESIGN.md §3). Takes the empty bucket and the water bucket,
keeps the empty metal pixels, and recolours the water's cavity to toxic olive-green by luminance
(so the fluid keeps the vanilla shading/highlights).

Usage: python3 tools/textures/gen_bucket.py <bucket.png> <water_bucket.png>
(extract both from the client jar's assets/minecraft/textures/item/). Dependency-free.
"""

import os
import struct
import sys
import zlib

OUT = "src/main/resources/assets/toxicsurface/textures/item/sludge_bucket.png"


def read_png(path):
    """Returns (w, h, pixels[y][x] = (r,g,b,a)). Handles RGBA8 (type 6) and indexed 4-bit (type 3)."""
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    w = h = bitdepth = ctype = 0
    idat = bytearray()
    plte = None
    trns = None
    i = 8
    while i < len(data):
        ln = struct.unpack(">I", data[i : i + 4])[0]
        typ = data[i + 4 : i + 8]
        chunk = data[i + 8 : i + 8 + ln]
        if typ == b"IHDR":
            w, h, bitdepth, ctype = struct.unpack(">IIBB", chunk[:10])
        elif typ == b"PLTE":
            plte = chunk
        elif typ == b"tRNS":
            trns = chunk
        elif typ == b"IDAT":
            idat += chunk
        elif typ == b"IEND":
            break
        i += 12 + ln

    raw = zlib.decompress(bytes(idat))
    if ctype == 6 and bitdepth == 8:
        bpp, stride = 4, w * 4
    elif ctype == 3 and bitdepth == 4:
        bpp, stride = 1, (w + 1) // 2
    else:
        raise ValueError(f"unsupported PNG type {ctype}/{bitdepth} in {path}")

    # Defilter scanlines.
    out = bytearray()
    prev = bytearray(stride)
    pos = 0
    for _ in range(h):
        ft = raw[pos]
        pos += 1
        line = bytearray(raw[pos : pos + stride])
        pos += stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if ft == 1:
                line[x] = (line[x] + a) & 0xFF
            elif ft == 2:
                line[x] = (line[x] + b) & 0xFF
            elif ft == 3:
                line[x] = (line[x] + (a + b) // 2) & 0xFF
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 0xFF
        out += line
        prev = line

    pixels = [[(0, 0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        if ctype == 6:
            row = out[y * stride : (y + 1) * stride]
            for x in range(w):
                r, g, b, a = row[x * 4 : x * 4 + 4]
                pixels[y][x] = (r, g, b, a)
        else:  # indexed 4-bit
            row = out[y * stride : (y + 1) * stride]
            for x in range(w):
                idx = (row[x // 2] >> 4) if x % 2 == 0 else (row[x // 2] & 0x0F)
                r, g, b = plte[idx * 3 : idx * 3 + 3]
                a = trns[idx] if (trns is not None and idx < len(trns)) else 255
                pixels[y][x] = (r, g, b, a)
    return w, h, pixels


def write_png(path, w, h, pixels):
    def chunk(typ, d):
        return struct.pack(">I", len(d)) + typ + d + struct.pack(">I", zlib.crc32(typ + d) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw += bytes(pixels[y][x])
    blob = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(blob)


def main(bucket_path, water_path):
    w, h, empty = read_png(bucket_path)
    _, _, water = read_png(water_path)
    out = [[empty[y][x] for x in range(w)] for y in range(h)]
    for y in range(h):
        for x in range(w):
            er, eg, eb, ea = empty[y][x]
            wr, wg, wb, wa = water[y][x]
            # Fluid pixel: the water bucket paints blue-dominant water over the bucket interior.
            if wa >= 128 and wb >= wr + 6 and wb >= wg:
                lum = 0.30 * wr + 0.59 * wg + 0.11 * wb
                out[y][x] = (int(lum * 0.50), int(lum * 0.74), int(lum * 0.28), 255)
    write_png(OUT, w, h, out)
    print("wrote", OUT)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
