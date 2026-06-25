#!/usr/bin/env python3
# SPDX-License-Identifier: LGPL-3.0-or-later
"""
Generates the shared empty GameTest arena template (DESIGN.md §10): a 16x8x16 structure with a
single polished-andesite floor at y=0 and air above. Every GameTest loads this one template via
`@GameTest(template = "empty")` and builds its own scenario with the helper, so we never have to
author per-test structure NBTs by hand.

Dependency-free: writes a gzip-compressed Minecraft structure NBT by hand. Run from the repo root:
`python3 tools/gametest/gen_empty_structure.py`.
"""

import gzip
import os
import struct

OUT = "src/main/resources/data/toxicsurface/structure/empty.nbt"
SIZE = (16, 8, 16)
FLOOR = "minecraft:polished_andesite"
DATA_VERSION = 3955  # Minecraft 1.21.1


# --- minimal NBT writers (tag id, then payload; named tags prefix id + name) ---
def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _int(v):
    return struct.pack(">i", v)


def named(tag_id, name, payload):
    return bytes([tag_id]) + _str(name) + payload


def compound(*named_tags):
    return b"".join(named_tags) + b"\x00"  # TAG_End terminator


def list_tag(elem_id, payloads):
    return bytes([elem_id]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def int_list(*vals):
    return list_tag(3, [_int(v) for v in vals])  # ListTag<IntTag>


def main():
    w, h, d = SIZE
    palette = list_tag(10, [compound(named(8, "Name", _str(FLOOR)))])  # index 0 = floor block
    blocks = list_tag(
        10,
        [
            compound(named(9, "pos", int_list(x, 0, z)), named(3, "state", _int(0)))
            for x in range(w)
            for z in range(d)
        ],
    )
    root = named(
        10,
        "",
        compound(
            named(3, "DataVersion", _int(DATA_VERSION)),
            named(9, "size", int_list(w, h, d)),
            named(9, "palette", palette),
            named(9, "blocks", blocks),
            named(9, "entities", list_tag(0, [])),  # empty
        ),
    )
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(root)
    print("wrote", OUT, f"({w}x{h}x{d}, {w*d} floor blocks)")


if __name__ == "__main__":
    main()
