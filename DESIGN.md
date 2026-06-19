# ToxicSurface — Design & Technical Spec

> Status: **Design phase** (no implementation code yet). This document is the
> agreed spec to build against. Last updated 2026-06-19.

A NeoForge mod that turns the surface of the world toxic after a configurable
in-game time, forcing players into sealed bases, air filtration, hazmat gear,
and machines to reclaim the world. Designed to slot into a Create /
Create: Aeronautics modpack.

---

## 1. Target stack

| Thing | Choice | Why |
|---|---|---|
| MC version | **1.21.1** | Common ground for Create 6.x and Create: Aeronautics' current builds |
| Loader | **NeoForge** | Where Create: Aeronautics is actively maintained (1.3.0-era, mid-2026) |
| Mappings | Mojmap (official) | NeoForge standard on 1.21.1 |
| Build | Gradle + ModDevGradle/NeoGradle | Standard NeoForge toolchain |
| Floating-island worldgen | **Sky Archipelago** (NeoForge 1.21.1) | Purpose-built for Create: Aeronautics airship gameplay; ideal pack companion |

**API note:** 1.21.1 is a very different world from the common 1.20.1 tutorials.
Item state is **Data Components** (1.20.5+), not NBT. Armor materials are
data-driven records. Fluids, capabilities, and networking use NeoForge's newer
systems. We build against current NeoForge 1.21.1 docs.

---

## 2. The two hard problems

### 2a. Enclosure detection ("is this air sealed from the toxic atmosphere?")
Canonical prior art: **Galacticraft's oxygen sealer**.

- **Per-entity bounded flood-fill** from the entity's head block through passable
  air. Reaches "open atmosphere" within a block budget → **exposed/toxic**.
  Closes off inside a pocket smaller than the budget → **sealed/safe**.
- **Cache mandatory**: result keyed by air-pocket, invalidated when a block in
  the pocket's bounding box changes. Effect checks run throttled (~every 10
  ticks), not every tick.
- **Budget tradeoff**: a sealed base larger than the budget is misread as
  exposed. Mitigation: connected-component cache, flood-fill a pocket once and
  reuse. **Highest correctness risk in the mod — prototype first.**

### 2b. Water → sludge conversion at world scale
Real sludge fluid, so we convert actual blocks — but never billions at once.

- Each chunk stores a **toxified marker** (chunk attachment / SavedData) recording
  the **sludge depth already applied** to it. After the toxicity start time, when
  a chunk loads within simulation distance, a **throttled queued pass**
  (N blocks/tick globally) converts the appropriate water band into sludge and
  updates the marker. A chunk is only re-queued when escalation has **deepened**
  the band beyond what's already applied — so each pass only does incremental work
  and no block is converted twice.
- Cleansers run the reverse pass within radius.

#### Open water / oceans — **surface-anchored, escalation-deepening band**
Converting whole oceans top-to-bottom is the worst case for both performance
(deep ocean chunks hold thousands of water blocks each) and gameplay (entire
seas gone). Instead of "all water below Y," convert a **band that hugs the water
surface** and **deepens over time** — it never sweeps up from the world floor:

- **Anchor to the water surface, not the toxic line.** For each water column in a
  toxified chunk, find the local **water surface Y** (top water block via a
  water-aware heightmap). Convert downward from that surface by the *current*
  sludge depth. This guarantees the sludge always sits where players interact with
  it (the ocean top), regardless of how high the toxic ceiling has climbed into
  the air above.
- **Only convert columns inside the toxic zone:** a column is eligible when its
  water surface `Y ≤ currentToxicY`. High lakes above the line wait until the line
  reaches them.
- **Depth scales *proportionally* with escalation** (not 1:1 with the Y rise):

  ```
  toxicProgress    = clamp01((currentToxicY - toxicStartY) / (escalationMaxY - toxicStartY))
  currentSludgeDepth = round( sludgeDepthMin + toxicProgress * (sludgeDepthMax - sludgeDepthMin) )
  ```

  At the start the ocean has a **thin skin** (`sludgeDepthMin`, default 4); as the
  toxic ceiling climbs toward `escalationMaxY`, the skin **thickens toward**
  `sludgeDepthMax` (default 24). The whole ocean only fully converts if
  `sludgeDepthMax` is set deep enough to reach the floor **and** the line has
  reached its max — by default, deep oceans keep clean water below.
- **Result:** a sludge skin over clean depths that grows downward as the
  apocalypse escalates — bounds per-column cost to a constant, kills surface
  aquatic life, enables **stilted-base-over-a-sludge-sea** play, and lets divers
  punch through to clean (but dark, drowning) water below.
- **Pack/biome lever:** a biome tag **`#toxicsurface:protected_water`** (empty by
  default) lets a pack exempt specific water bodies entirely — e.g. keep certain
  ocean biomes clean as buildable safe frontiers. A config flag also exposes a
  **`FULL` mode** (convert the entire column, true apocalyptic sludge seas) for
  players who want it.

---

## 3. System-by-system design

### World toxicity state (server-authoritative, per-level)
- `SavedData` per affected dimension: `toxicityStartTick` (set when world first
  crosses the configured time) + config snapshot.
- **Default affected dimension: Overworld only.** Other dimensions (Nether, End,
  modded) are **opt-in** via a configurable dimension whitelist — they never turn
  toxic unless explicitly added.

### Escalation mode
- The toxic Y ceiling **rises over time** at a configurable spread speed
  (blocks per in-game day; `0` = static line). Turns the hazard into a creeping
  apocalypse and pushes players upward — strong synergy with airships/sky
  islands as the late-game safe zone.
- **Configurable ceiling (`escalationMaxY`):** the rising line stops at a
  maximum Y. May be set to world height (entire column eventually toxic, airships
  the only refuge), but **defaults to a value below world height** so a high-
  altitude safe band always remains.

### Toxic gas (virtual region + client fog)
- An air block is toxic if `time ≥ start` AND `y ≤ currentToxicY` AND not in a
  sealed pocket AND not inside a cleanser bubble.

#### Toxic air bar (drowning-style mechanic)
Players exposed to toxic gas use a **toxic air bar** modeled on vanilla
drowning, displayed as a row of bubbles (green-tinted) above the hotbar:

1. **Full bar** when protected (mask/suit with active filter, or in sealed area).
2. **Bar drains** when unprotected in toxic gas — same rate as vanilla drowning
   (~15 seconds from full to empty). This gives unmasked players a brief window
   to dash through gas.
3. **When the bar empties** → **toxic damage** (not poison): 2 damage per second,
   **which can and will kill the player**. This is real damage, not the Poison
   effect (which caps at half a heart).
4. **Nausea** applied while the bar is draining (not after — by then you're
   taking damage and need to see to escape).
5. **Bar refills** when the player re-enters clean air or activates a filter.

When a mask or suit filter **runs out mid-exposure**, the bar begins draining
from wherever it was (full, if the filter was active). A brief **HUD flash +
cough sound** plays as a warning that protection just dropped.

This replaces the previous "Poison + Nausea" model entirely. The toxic air bar
is the core survival mechanic.

- **Respiration / water-breathing do NOT affect the toxic air bar.** The gas bar
  is its own gauge; vanilla underwater-breathing enchants and potions have no
  effect on it. (They *do* still help with the separate vanilla drowning bar in
  toxic sludge — see the Toxic sludge section.)
- **Respawn grace:** a player who respawns into toxic gas (no sealed bed area)
  starts with a **full toxic air bar** — i.e. the same ~15s of "held breath."
  That window is the player's chance to dig in or run. There is no extra
  invulnerability beyond the full bar; respawn simply doesn't start mid-drain.

#### Foliage & mob death
- **All exposed foliage dies**: grass, flowers, vines, tree leaves, crops, and
  saplings exposed to toxic gas wither and break over time (throttled block
  decay). Creates a dramatic dead-world look on the surface.
- **All passive mobs die** in toxic gas — including tamed and name-tagged animals
  **and villagers**. No exceptions. Forces fully sealed barns and villages, and is
  unforgiving of mistakes. (Villagers also die to sludge contact like other mobs.)
- **Hostile mobs unaffected** for now (mutant mobs are a future addition).

#### Toxic rain
- Surface weather particle/overlay effect while raining in toxified areas,
  reinforcing the "don't go outside" mood. Accelerates foliage decay rate.

#### Onboarding telegraph (pre-toxicity warnings)
The advancement fires *as* toxicity activates, but players (especially on
multiplayer) deserve warning *before* it's too late. Before `timeToToxicTicks`
elapses, broadcast escalating **title/chat warnings** at configurable intervals
(default: T-3 days, T-1 day, T-1 hour, T-10 min) to players in affected
dimensions — e.g. *"The air grows heavy…"*. Optional ambient cue (distant rumble
/ sky tint) as the moment approaches. All toggleable; can be disabled for a
blind/hardcore experience.

#### Advancement: "The Air Has Turned"
- When the surface first becomes toxic, all players in affected dimensions
  receive an **advancement** ("The Air Has Turned" or similar). Acts as the
  in-world telegraph at the moment of activation.

#### Client rendering
- **Fog + particle haze** when the camera block is toxic, via NeoForge fog render
  events. Server syncs timer, current Y, config, and nearby cleanser bubbles so
  the client computes fog locally and responsively.

#### Accessibility (client options)
The fog, visor vignette, screen-shake/cough flash, and toxic-rain overlay are
immersive but can hurt readability or trigger motion sickness. Provide
**client-side toggles/sliders** (no gameplay effect, purely visual):
- Fog density / haze intensity slider (down to a thin tint).
- Visor vignette + fog-up effect on/off.
- Reduce HUD flash / screen-shake on filter-expire.
- Toxic-rain overlay opacity.
These are client options, not server config — each player tunes their own view.

### Toxic sludge (real custom fluid)
- Custom flowing fluid (NeoForge `BaseFlowingFluid` + `FluidType`), behaves like
  water (flows, bucket-able, swimmable). **Submersion drowns you via the vanilla
  air supply** (the normal drowning bubble bar, *not* the toxic air bar).
- **Drowning in sludge uses the vanilla breath mechanic**, so **Respiration and
  water-breathing extend it normally.** This matters most in a hazmat suit: the
  suit negates sludge *contact damage* but is **not a rebreather**, so a
  Respiration-enchanted suited player can stay submerged longer but still
  eventually drowns. (The toxic *gas* air bar remains unaffected by these
  enchants — see the toxic air bar section.)
- Entities inside: **2 damage every 0.5s + Poison**.
- **Destroys organic items**: `ItemEntity`s matching food / `#minecraft:logs` /
  `#minecraft:leaves` / a custom `#toxicsurface:organic` tag are consumed in
  sludge.
- Sludge bucket is a real fluid bucket item.
- **Create integration**: exposes the standard fluid-handler capability so sludge
  is **pumpable through Create pipes/pumps** and storable in Create tanks.

### Filters & masks (Data Components)
- **Clean Air Filter** ← **2 wool** (shapeless).
- **Face Mask** ← **clean filter + 2 string**. Component stores
  `{ filterInstalled: bool, remainingTicks }`. Lasts ~2 min (2400 ticks) of
  active protection; **remaining filter time shown as the item durability bar**.
  Counts down only while actually in toxic gas (configurable to always-tick).
- **Worn in the helmet slot** (mutually exclusive with the hazmat helmet — a
  deliberate early-game vs end-game tradeoff).
- When a mask's filter expires while the player is in gas, the **toxic air bar**
  begins draining (see toxic air bar section above). A HUD flash + cough sound
  warns the player that protection just dropped.
- Dirty (used) filter can be **swapped/replaced** in the mask.
- **Washing dirty filters — two paths:**
  1. **Vanilla craft**: dirty filter + water bucket → clean filter **+ sludge
     bucket returned** (custom recipe; the returned bucket's remainder is set to
     a sludge bucket). Reverse: sludge bucket + clean filter → water bucket.
  2. **Create washing**: running a dirty filter through Create's washing
     (encased fan + flowing water / bulk washing) → clean filter. Convenience
     path; no bucket return.

### Hazmat suit (custom armor set)
- Crafted from Hazmat Material + iron; helmet/chest/legs/boots via data-driven
  `ArmorMaterial`.
- **Helmet**: visor overlay — vignette/edge-darkening HUD so you "see through the
  visor." Immersion extras: fog-up effect + muffled breathing audio; cracked
  visor when damaged.
- **Chestpiece**: stores **up to 10 filters** (Data Component), consumed at
  **half the mask's rate** while in toxic gas. Full powered suit → **gas
  immunity** (toxic air bar stays full while filters remain).

### HUD gauge
- While wearing a face mask or hazmat suit, an on-screen **HUD readout** displays:
  - **Remaining filter time** (minutes:seconds) for the active mask or suit filter.
  - **Chest filter count** (e.g. "7/10") when wearing a hazmat chestpiece.
- Positioned near the toxic air bar / armor display. Pairs naturally with the
  hazmat visor overlay (rendered inside the visor frame when suited up).
- **Sludge**: a full hazmat suit **negates all sludge contact damage**, but the
  player **can still drown** in sludge (the suit is not a rebreather).

### Weaver (machine block)
- Crafted from **6 iron + 2 sticks**.
- Block entity with a sided `ItemStackHandler`: inputs **kelp + wool**, outputs
  **Hazmat Material**.
- **Runs on furnace fuel** like a normal furnace. **A redstone signal STOPS it**
  (powered = halted; unpowered = runs when fueled + supplied).
- **Hopper-automatable** (kelp/wool in, material out).

### Cleanser (machine block)
- Crafted from **4 iron + 2 gold + 2 diamond**.
- Consumes furnace fuel; **purges gas in a sphere** and **reverts sludge → water**
  within range.
- Range tiers **8 / 16 / 32 / 64 / 128**, set by **input redstone signal**;
  **hopper** fuel input.
- Fuel cost: base furnace rate at 8 blocks, **exponential** with range
  (cost ∝ (range/8)^k; `k` in config).
- **Create variant (Mechanical Cleanser)**: powered by Create **rotational
  force** (stress/RPM) instead of fuel — range scales with supplied RPM/stress.

### Config (server config — syncs in multiplayer)

Gameplay knobs with **proposed defaults** (all server-config, balance-tunable):

| Key | Default | Notes |
|---|---|---|
| `timeToToxicTicks` | `120000` (~5 in-game days) | When the surface first turns toxic |
| `toxicStartY` | `63` | Initial toxic ceiling (sea level) |
| `escalationSpeedPerDay` | `4` | Blocks/in-game-day the line rises; `0` = static |
| `escalationMaxY` | `200` | Ceiling cap; may be set to world height (`319`) |
| `airBarDrainTicks` | `300` (15 s) | Full→empty when unprotected in gas (vanilla drown rate) |
| `airBarRefillTicks` | `60` (3 s) | Empty→full when back in clean/protected air |
| `toxicDamagePerSecond` | `2.0` | Real damage after the bar empties (kills) |
| `nauseaWhileDraining` | `true` | Nausea applied during drain, not after |
| `sludgeDamage` / `sludgeIntervalTicks` | `2.0` / `10` (0.5 s) | Sludge contact damage + Poison |
| `sludgeDepthMin` | `4` | Starting band depth below the water surface (thin skin) |
| `sludgeDepthMax` | `24` | Band depth when the toxic line reaches `escalationMaxY` |
| `waterConversionMode` | `BANDED` | `BANDED` (depth-capped) or `FULL` (whole column) |
| `maskDurationTicks` | `2400` (2 min) | Active filter time for a face mask |
| `maskTickMode` | `IN_GAS_ONLY` | `IN_GAS_ONLY` or `ALWAYS` |
| `suitFilterCapacity` | `10` | Filters stored in the hazmat chestpiece |
| `suitConsumeRateFactor` | `0.5` | Suit burns filters at half the mask rate |
| `cleanserTiers` | `8,16,32,64,128` | Redstone-selected sphere radii |
| `cleanserFuelExponent` (`k`) | `2.0` | Fuel cost ∝ `(range/8)^k` |
| `affectedDimensions` | `["minecraft:overworld"]` | Opt-in whitelist |
| `foliageDecayBlocksPerTick` | `64` | Global throttle for foliage death pass |
| `toxicRainEnabled` | `true` | Surface toxic-rain overlay |
| `toxicRainDecayMultiplier` | `2.0` | Foliage decay speed-up while raining |

Engine-level performance budgets (not balance knobs) live in **§8**.

---

## 4. Multiplayer model
All hazard logic (damage, effects, conversion, sealing checks) is
**server-authoritative**. Clients only **render** (fog, particles, suit HUD,
toxic rain) and receive synced state via NeoForge payload networking. Config is
server-driven and synced. Baked into the architecture, not bolted on.

---

## 5. Phased roadmap

1. **Foundations** — NeoForge 1.21.1 project, registries, config spec, CI/dev world.
2. **Hazard core (risky stuff first)** — toxicity timer + SavedData + escalation,
   sludge fluid, virtual gas effects, **enclosure flood-fill + cache**, client
   fog. *Prototype enclosure detection before proceeding.*
3. **Lazy world conversion** — chunk toxified-flag + throttled water→sludge queue;
   organic-item destruction; all-passive-mob death; foliage decay; toxic rain;
   "The Air Has Turned" advancement.
4. **Filters & masks** — items, Data Components, durability display, wash/return
   recipes, mask wear, toxic air bar integration, filter-expire warning.
5. **Hazmat suit** — armor set, chest filter storage + consumption, **HUD gauge**,
   helmet visor overlay + immersion, sludge-damage immunity (still drowns).
6. **Machines** — Weaver (fuel + redstone-stop), then Cleanser (redstone range,
   fuel curve, hopper I/O, sludge reversion).
7. **Create integration** — sludge in Create pipes/tanks, Create washing for
   filters, Mechanical Cleanser variant.
8. **Polish & pack integration** — JEI/EMI recipe support, Jade tooltips, balance
   pass, Create / Aeronautics / Sky Archipelago compat testing.

---

## 6. Open risks
- **Create: Aeronautics maturity** — young, API-churny; pin exact compatible
  Create + Aeronautics + Sable + Sky Archipelago build numbers.
- **Enclosure correctness** for very large bases (budget tuning).
- **Sludge on moving Create/Aeronautics contraptions** — edge-case testing.
- **Create API coupling** — washing recipes and rotational power tie us to Create
  internals; gate behind soft-dependency so the mod still loads standalone.

---

## 7. Future / stretch ideas
- **Mutant mobs** immune to the gas, spawning in toxified surface at night.
- **Geiger / air-quality meter** item that ticks faster near the toxic line.
- **Decontamination/refill station** that recharges suit filters from a tank.
- **Sealed greenhouse** progression for surface farming under glass.

---

## 8. Performance budgets & defaults

Engine-level limits that keep the hazard systems off the main-thread hot path.
These are **server-side tuning constants** (separate from the gameplay config in
§3). Proposed starting values, tuned for a ~4-player server:

| Budget | Default | Rationale |
|---|---|---|
| Exposure/sealing check cadence | every **10 ticks** per entity | 0.5 s granularity; air-bar drain is computed in fractions between checks |
| `enclosureFloodFillBudget` | **4096** air blocks | A 16³ pocket; bases with more connected air than this read as exposed (raise for megabases) |
| Flood-fills per tick (amortized) | **≤ 4** cache *misses*/tick | Spreads recomputation; cache *hits* are free, so steady state is cheap |
| Pocket cache | **256** pockets/dimension, **LRU** | Keyed by connected-component; invalidated when any block in the pocket's AABB changes |
| Water→sludge conversion | **512** blocks/tick global | Throttled queue across all loaded toxified chunks; depth-banding caps per-column work |
| Foliage decay pass | **64** blocks/tick global | Shared throttle; toxic rain multiplies effective rate, not the scan budget |
| State sync | delta-on-change + **100-tick** heartbeat | Timer / current Y / config; cleanser-bubble list synced on add/remove only |

**Worst case sanity check:** when the timer fires and many chunks load at once,
the conversion/foliage queues build a backlog but drain at a fixed rate, so TPS
is protected; the per-chunk "toxified" flag guarantees no chunk is reprocessed.
The flood-fill is the real risk — **prototype and profile it first** (§5, Phase 2)
with the amortization cap and cache in place from day one.

---

## 9. Soft-dependency contract (Create / Aeronautics)

"Loads standalone" must be testable, not aspirational. The mod declares Create &
Create: Aeronautics as **optional** dependencies and degrades cleanly:

| Feature | With Create | Without Create |
|---|---|---|
| Sludge fluid | Pumpable via Create pipes/pumps, storable in Create tanks | Still a real fluid; bucket I/O only |
| Filter washing | Encased-fan / bulk washing path | Vanilla bucket recipe only |
| Cleanser | Mechanical Cleanser (rotational) **+** fuel Cleanser | Fuel Cleanser only |
| Sludge on contraptions | Tested edge case | N/A |

- All Create-touching code lives behind `ModList.get().isLoaded("create")` guards
  and is registered conditionally; no hard class references at load time.
- Sky Archipelago is a **pack companion**, not a code dependency — no compile or
  runtime coupling.
- **CI must run the standalone jar** (no Create on the classpath) to a dev-world
  load + the GameTest suite, so a Create API change can't silently break the
  base mod.

---

## 10. Testing strategy

- **NeoForge GameTests** for the high-risk, logic-heavy pieces — these run
  headless in CI:
  - *Enclosure flood-fill*: sealed box reads safe; box with a 1-block hole reads
    exposed; pocket exactly at budget vs one over; cache invalidation when a wall
    block is removed/placed.
  - *Water→sludge conversion*: depth band converts the right Y range; runs once
    (flag set); idempotent on chunk reload; protected-water biome tag exempts.
  - *Air bar*: drain timing, post-empty damage, refill, filter-expire transition,
    respawn-with-full-bar.
  - *Recipes*: filter wash/return bucket round-trip; Weaver/Cleanser I/O.
- **Manual/dev-world checklist** for the visual + Create-coupled paths (fog,
  visor, pipes, washing, Mechanical Cleanser).
- CI runs both **with and without Create** (see §9).

---

## 11. Project identity & mod metadata

First-mod identity — **set-once and painful to change later**, locked in Phase 1:

- **Mod ID** — `toxicsurface` (lowercase, globally unique; the namespace
  everywhere: `toxicsurface:sludge`, config folder, lang keys).
- **Java package / Maven group** — base package
  `io.github.thomasjoleary.toxicsurface`, Maven `group = io.github.thomasjoleary`.
- **Display name** — `ToxicSurface`. **Author** — `chooboy`.
- **Version** — semantic versioning starting `0.1.0`; jar named
  `toxicsurface-<mcversion>-<modversion>.jar` (e.g. `toxicsurface-1.21.1-0.1.0.jar`).
- **License** — **LGPL-3.0-or-later.** Anyone may include the mod in packs and
  link against it, **but modifications to the mod itself must be published under
  the same license** — so a changed build can't be passed off as the original
  without it being known. (`LICENSE` = GPL-3.0 text, `LICENSE.LESSER` = the LGPL
  additional permissions, per the FSF's standard layout.)
- **`neoforge.mods.toml`** carries: `modId`, `version`, `displayName`,
  `authors = "chooboy"`, `description`, `license = "LGPL-3.0-or-later"`, and
  `[[dependencies]]` (neoforge + minecraft required; create + aeronautics
  optional, see §9).

---

## Decision log
- Target: **1.21.1 / NeoForge**.
- Gas safety model: **sealed enclosures protect you** (Galacticraft-style).
- World representation: **real sludge fluid, virtual gas**.
- **Toxic air bar** (drowning-style): drains when unprotected in gas; when empty,
  **real toxic damage that kills** (not capped-at-half-heart poison). Unmasked
  players get ~15s of exposure before damage starts.
- **All exposed foliage dies** in toxic gas (grass, flowers, vines, leaves, crops).
- **All passive mobs die** in gas — including tamed, named, **and villagers**.
  No exceptions; they also die to sludge.
- **Respawn grace:** respawning into gas starts with a **full** air bar (~15s) —
  enough to dig in — but no extra invulnerability.
- **Respiration / water-breathing do NOT affect the toxic air bar**, but **do**
  extend vanilla drowning in **sludge** (relevant when suited, since the suit
  isn't a rebreather).
- **Open water:** **surface-anchored sludge band** that **deepens proportionally**
  with escalation (depth `4 → 24` as toxic Y climbs to its max), hugging the water
  top rather than sweeping up from the floor. Sludge skin over clean depths;
  `FULL` mode + `#protected_water` biome tag as config levers.
- **Escalation ceiling** (`escalationMaxY`): line stops at a max Y, default below
  world height (can be set to world height).
- **Dimensions:** Overworld toxic by default; others **opt-in** via whitelist.
- Hazmat suit: **negates all sludge damage, but players still drown** in sludge.
- Air filter = **2 wool**; face mask = **clean filter + 2 string**, durability =
  filter time, **helmet slot**.
- **HUD gauge** for remaining filter time + chest filter count.
- **Advancement** ("The Air Has Turned") when toxicity first activates.
- **Filter-expire warning**: HUD flash + cough sound when protection drops.
- Weaver: **furnace fuel**, **redstone signal stops it**.
- **Escalation mode** with configurable spread speed.
- **Toxic rain** accelerates foliage decay.
- **Mechanical Cleanser** (Create rotation); **sludge pumpable** through Create pipes.
- Dirty filters cleanable via **Create washing** (plus the bucket recipe).
- **Visor immersion** included (fog-up, muffled breathing, cracked visor on damage).
- **Pre-toxicity telegraph**: escalating title/chat warnings before activation
  (toggleable).
- **Accessibility**: client-side toggles for fog/visor/flash/rain intensity.
- **Config defaults table** + **engine performance budgets** added (§3, §8).
- **Soft-dependency contract** for Create/Aeronautics; CI tests standalone (§9).
- **GameTest** suite for flood-fill / conversion / air-bar / recipes (§10).
- **Project identity** (§11): mod ID `toxicsurface`, package
  `io.github.thomasjoleary.toxicsurface`, author **chooboy**, license
  **LGPL-3.0-or-later**, version `0.1.0`.
