# ToxicSurface

> **Status: Design phase** — no implementation code yet. The full spec lives in
> [`DESIGN.md`](DESIGN.md).

A **NeoForge 1.21.1** mod that turns the world's surface **toxic** after a
configurable in-game time, forcing players into sealed bases, air filtration,
hazmat gear, and machines to reclaim the world. Built to slot into a
**Create / Create: Aeronautics / Sky Archipelago** modpack — the rising toxic
ceiling pushes survival upward toward airships and sky islands.

## The pitch

- **Toxic gas** fills the surface below a ceiling that **rises over time**
  (escalation mode). Unprotected players get a drowning-style **toxic air bar**
  (~15s) before real, lethal damage starts.
- **Sealed enclosures protect you** (Galacticraft-style flood-fill detection).
- **Toxic sludge** is a real, pumpable fluid; oceans grow a sludge skin that
  deepens as the apocalypse escalates.
- Progression: **air filters → face masks → hazmat suit → Weaver → Cleanser**,
  with deep **Create** integration (pipes, washing, a rotation-powered
  Mechanical Cleanser).
- **Server-authoritative**; multiplayer-safe by design.

See [`DESIGN.md`](DESIGN.md) for the complete technical spec, config defaults,
performance budgets, roadmap, and decision log.

## Target stack

| Thing | Choice |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge |
| Mappings | Mojmap (official) |
| Build | Gradle + ModDevGradle/NeoGradle |
| Companions | Create, Create: Aeronautics, Sky Archipelago |

## Status & roadmap

Currently a design spec. Implementation follows the phased roadmap in
[`DESIGN.md` §5](DESIGN.md) — Phase 1 is project foundations (registries, config,
CI/dev world), with the risky enclosure flood-fill prototyped early in Phase 2.

## License

Licensed under the **GNU Lesser General Public License v3.0 or later
(LGPL-3.0-or-later)** — see [`LICENSE`](LICENSE). You may include this mod in
modpacks and link against it freely; modifications to the mod itself must be
released under the same license, so an altered build can't be passed off as the
original.

## Author

chooboy
