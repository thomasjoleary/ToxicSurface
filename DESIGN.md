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
  and no block is converted twice. **Chunks already loaded** when the band first
  appears (activation) or deepens fire no fresh load event, so they are **swept into
  the queue** the moment the current depth rises — otherwise an ocean you're standing
  next to at activation would never get its skin.
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
  reached its max — by default, deep oceans keep clean water below. The pass anchors to the
  **top liquid block** (water or already-placed sludge, so re-passes stay correct) and converts
  clean water plus **insubstantial water-logged blocks** in the band (seagrass, kelp, …) — those
  are destroyed and turned to sludge — while leaving solid water-logged builds alone.
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
  sealed pocket AND not inside a cleanser bubble **AND the cell holds no liquid**.
  Gas is airborne, so a cell filled by water or sludge is never toxic gas: swimmers
  and aquatic plants in **clean water are safe**, and a **sludge** cell is the sludge
  hazard's domain (contact damage / drowning), not the gas's. Passive-mob death and
  foliage decay share this rule — foliage in clean water (waterlogged or sitting on
  water, e.g. lily pads) is spared, while air- or sludge-bound foliage still withers.

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
- **Floats on water (treats it as a solid surface):** `SludgeFluid` makes sludge and water
  mutually impermeable — `canSpreadTo` refuses water-filled targets (sludge never sinks into water,
  which vanilla otherwise allows downward) and `canBeReplacedWith` refuses water as the incoming
  fluid (water never flows in and erases the sludge layer). So the converted surface skin sits on
  top of clean water as a stable sheet (the §2b "skin over clean depths" model); sludge flows
  normally over air/land. (Limitation: MC fluids don't layer, so a single bucket poured into open
  water sits as a stationary blob rather than sheeting across — the conversion is the sheet path.)
- **Submerged vision:** a camera in sludge gets a **murky green** look — `modifyFogColor` tints the
  fog toxic-green, `modifyFogRender` pulls it in to ~6 blocks, and `getRenderOverlayTexture` draws a
  dark-green full-screen overlay (the sludge analogue of vanilla water's dark-blue underwater).
- **No knockback:** the `toxicsurface:toxic` damage type is in `#minecraft:no_knockback`, so toxic
  gas and sludge damage never shove the entity.
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
  **half the mask's rate** while in toxic gas. The chest is what protects you —
  **not the suit itself**: while it holds filter charge the toxic air bar stays
  full, and when the filters run out protection drops just like a mask. The suit's
  edge over a mask is **bigger capacity + half-rate consumption** (far longer
  between swaps, and no fiddly helmet-slot mask swapping), **not** unconditional
  immunity. An empty chest offers no gas protection.
- **Gas protection requires the helmet *and* chest** worn together — the helmet
  seals the breathing path, the chest holds the filters. Wearing just one does
  nothing for gas.
- The chestpiece **crafts empty** and has its own **filter inventory** — sneak +
  right-click it to open a screen with up to 10 filter slots, so clean filters can
  be swapped in and out directly (spent ones come back as used filters to wash).

### HUD gauge
- While wearing a face mask or hazmat suit, an on-screen **HUD readout** displays:
  - **Remaining filter time** (minutes:seconds) for the active mask or suit filter.
  - **Chest filter count** (e.g. "7/10") when wearing a hazmat chestpiece.
- Positioned near the toxic air bar / armor display. Pairs naturally with the
  hazmat visor overlay (rendered inside the visor frame when suited up).
- **Sludge**: a full hazmat suit **negates all sludge contact damage**, but the
  player **can still drown** in sludge (the suit is not a rebreather).

### Weaver (machine block) — textile & filtration fabricator
- Crafted from **6 iron + 2 sticks**. Block entity with a sided `ItemStackHandler`;
  **runs on furnace fuel**, **a redstone signal STOPS it**, **hopper-automatable**.
- **Recipe-driven** (custom `toxicsurface:weaving` recipe type, datapack-extensible — ✅
  implemented) rather than a
  single hard-coded recipe — so the Weaver is the hub for all fiber/filtration gear,
  not just a one-off Hazmat Material press. Accepts a range of fibers via a
  **`#toxicsurface:fiber`** tag (wool, string, kelp, bamboo, dried kelp…), so it
  isn't strictly wool-gated.
- **Core outputs (proposed):**
  1. **Hazmat Material** ← kelp + wool (the suit fabric). *(existing)*
  2. **Clean Air Filter** ← **1 wool OR 2 string** (the Weaver is the bulk/efficient
     filter source; the 2-wool hand recipe stays as a basic fallback). Makes your
     **core consumable** a reason to keep coming back to the machine.
  3. **Carbon Filter** ← filter + charcoal → a **long-life filter** that lasts a
     configurable multiple of a normal filter (a clear filter-tier upgrade).
  4. **Suit/mask repair** ← a damaged hazmat piece + Hazmat Material → repaired
     (cheaper/lossless vs. an anvil).
  5. *(stretch)* **Sealed-glass / canvas** intermediates for the greenhouse and other
     sealing gear (ties into the §7 sealed-greenhouse idea).
- **Mechanical Weaver (Create):** rotation-powered variant — runs on Create
  **stress/RPM** instead of furnace fuel; weave speed scales with supplied RPM.

### Cleanser (machine block)
- Crafted from **4 iron + 2 gold + 2 diamond**. Consumes furnace fuel (**hopper**
  fuel input); **purges gas in a sphere** and **reverts sludge → water** within range.
- **Range is set manually in the Cleanser's menu** (a slider/field up to a configured
  max) — this is the primary control.
- **Redstone input is an optional on-the-fly override:** while powered, the input
  signal strength selects a **range tier** (8 / 16 / 32 / 64 / 128…), so players can
  wire a lever/comparator to switch range instantly (e.g. drop to a tight radius to
  save fuel, spike it when needed). With **no signal it uses the menu value**.
- Fuel cost: base furnace rate at 8 blocks, **exponential** with range
  (cost ∝ (range/8)^k; `k` in config).
- **Mechanical Cleanser (Create):** powered by Create **rotational force**
  (stress/RPM) instead of fuel — range scales with supplied RPM/stress.

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
| `cleanserMaxRange` | `128` | Max range settable in the Cleanser's menu |
| `cleanserTiers` | `8,16,32,64,128` | Range presets the **redstone** input quick-switches between |
| `cleanserFuelExponent` (`k`) | `2.0` | Fuel cost ∝ `(range/8)^k` |
| `carbonFilterDurationMultiplier` | `3.0` | Carbon (long-life) filter lifetime vs. a normal filter |
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
   filters, **Mechanical Cleanser** and **Mechanical Weaver** (rotation-powered)
   variants.
8. **Polish & pack integration** — JEI/EMI recipe support, Jade tooltips, balance
   pass, Create / Aeronautics / Sky Archipelago compat testing.

---

## 5b. Implementation progress (build log)

> Status as of 2026-06-20. Target verified: **NeoForge 21.1.77 / MC 1.21.1 / Java 21**.
> Every NeoForge layer compiles and runs the standalone GameTest in CI; risky logic
> is split into Minecraft-free classes covered by **40 pure unit tests** (all green).
> A recurring pattern: pure algorithm + thin NeoForge adapter, verified per-commit.

**Phase 1 — Foundations ✅ (merged)**
- ModDevGradle build (NeoForge 1.21.1 / Java 21), `neoforge.mods.toml`, registries,
  creative tab, `ToxicSurfaceConfig` server spec mirroring §3/§8.
- Spotless (Palantir + LGPL SPDX headers), GitHub Actions CI (lint + standalone
  build/GameTest), SessionStart hook.

**Phase 2 — Hazard core ✅ (merged)**
- Enclosure flood-fill + connected-component cache (§2a) — *prototyped first*.
- Toxicity timer + escalation + per-dimension `SavedData`; derived ceiling Y.
- Virtual gas predicate + drowning-style air bar (nausea → lethal toxic damage),
  throttled per-player.
- Toxic sludge fluid (`FluidType` + still/flowing), `LiquidBlock`, bucket; contact
  damage + Poison + organic-item destruction; drowning via `canDrown`.
- Client fog driven by a server→client exposure payload.

**Phase 3 — Lazy world conversion ✅ (merged)**
- Budgeted, surface-anchored water→sludge conversion with a per-chunk applied-depth
  attachment (incremental, idempotent, FULL-mode aware).
- Passive-mob death in gas (animals, tamed, named, villagers; hostiles immune).
- Sky-exposed foliage decay (toxic rain accelerates it).
- "The Air Has Turned" advancement on activation.

**Phase 4 — Filters & masks ✅ (CI-green; branch `claude/phase4-filters-masks`)**
- Clean filter (2 wool); face mask (filter + 2 string) worn in the helmet slot via
  `canEquip`, with a `MaskData` filter-time component shown as a durability bar.
- Mask protects from gas and consumes its filter; cough warning on mid-gas expiry.
- Dirty-filter wash loop: refilling a mask ejects a used filter (custom recipe);
  washing (`used + water bucket → clean filter`, **returns a sludge bucket**) and the
  reverse (`clean + sludge bucket → water bucket`, returns a used filter).

**Phase 5 — Hazmat suit ✅ (merged)**
- Data-driven `hazmat` armour material (iron-tier, Hazmat-Material repair); four
  hazmat pieces. Gas protection needs **helmet + chestpiece** worn together.
- Chestpiece stores up to 10 filters in a `minecraft:container` component, swapped
  via a dedicated **filter-inventory screen** (`HazmatChestMenu`/`Screen`); crafted
  empty. Suit burns filters at half the mask rate; HUD gauge + visor overlay.

**Phase 6 — Machines ✅ (CI-green; branch `claude/phase6-machines`)**
- **Weaver** (textile/filtration fabricator): furnace-fuelled, redstone-halt,
  hopper-automatable; hard-coded recipes (kelp + wool → Hazmat Material; 1 wool **or**
  2 string → clean filter; clean filter + charcoal/coal → **carbon filter**). GUI with
  progress arrow + fuel flame.
- **Carbon (activated) filter**: `carbonFilterDurationMultiplier`× life; `MaskData`
  tracks max so the bar scales; mask refill + suit are carbon-aware; spent carbon
  degrades to a plain used filter. (Suit repair uses the vanilla anvil — Hazmat
  Material is the armour's repair ingredient.)
- **Cleanser**: furnace-fuelled reclamation block that reverts sludge → water in a
  budgeted sphere **and** keeps breathable air in range (server-authoritative purge
  bubble feeding the gas predicate + client fog). **Range set in its menu**
  (-8/-1/+1/+8 steppers) with a **redstone tier override**; fuel cost ∝ (range/8)^k;
  only runs in an affected, already-toxic dimension.

**Phase 7 — Create integration ✅ (CI-green; branch `claude/phase7-create-integration`)**
- Soft-dependency foundation (DESIGN.md §9): `CreateCompat#isLoaded` gate (no hard
  Create class references at load time), Create declared an **optional** dependency,
  common-setup logs whether the integration is active.
- **Build wiring:** Create **6.0.10-281** (MC 1.21.1, matches the Sky Archipelago pack)
  pinned `compileOnly` (`transitive=false`, never bundled); Ponder added the same way
  because Create's `SmartBlockEntity` implements `ponder.api.VirtualBlockEntity` (needed
  to resolve the `KineticBlockEntity` hierarchy). **NeoForge bumped 21.1.77 → 21.1.234**
  (latest 1.21.1; Create requires `[21.1.219,)` and it's the version a real Create +
  Aeronautics pack runs); the mod's own range stays `[21.1.0,)`.
- **CI now runs both sides of the §9 contract.** Kept the **standalone** job (loads
  without Create) and added **"Build & GameTest (with Create)"** (`-PcreateRuntime=true`):
  the full Create jar jarJar's Flywheel + Ponder + Registrate, so one runtime entry pulls
  the whole mandatory graph; it compiles against the real Create API and boots a
  gameTestServer with Create loaded. Both jobs green.
- **Mechanical Cleanser & Mechanical Weaver** (Create kinetic API): rotation-powered
  siblings of the fuel machines. `DirectionalKineticBlock` + `IBE` / `KineticBlockEntity`,
  consume stress, and scale with supplied RPM instead of fuel (range/weave-speed double
  per speed tier, mirroring gear trains); a redstone signal or over-stressed network halts
  them. Shared logic factored out (`SludgeReclaimer`, `WeaverLogic`) so both variants
  behave identically; the RPM→range curve is a pure, unit-tested function. All Create
  classes live in `compat.create` and register **only** via `CreateContent` behind the
  `isLoaded()` gate — never classloaded in the standalone jar.
  - **Kinetic shaft connections:** each block overrides `hasShaftTowards` (the `KineticBlock`
    default is `false` for every face, so without it nothing connects). Generators *and*
    machines connect on **both ends of the facing axis** (`face.getAxis() == FACING axis`).
  - **Deployer-style Mechanical Weaver (pass 1):** no GUI — a depot-style top face holds the two
    inputs / output, rendered in-world by a Create-gated, client-only `MechanicalWeaverRenderer`
    (registered from `CreateClientContent` behind a `Dist.CLIENT` check). While weaving, two
    crossing "weaving sticks" bob over-under (vanilla sticks as stand-in geometry). Right-click
    inserts/extracts by hand; automation still flows through the item-handler capability. The BE
    syncs a `weaving` flag + inventory to clients via Create's `sendData()`. **Pass 2 pending:**
    real weaving-head model + textures, motion polish.
- **Filter fan-washing:** `create:splashing` `used → clean` filter, **condition-gated** on
  `create`; schema verified against the real Create jar. Sludge pumps/stores through Create
  pipes & tanks automatically (it's a real NeoForge fluid exposing the standard
  `IFluidHandler`) — no code needed.
- **Sludge reclamation loop** (Create processing, all condition-gated; schemas verified):
  a new **`toxic_residue`** item + **`toxic_waste_block`** (base content) close the
  contamination loop — splashing a used filter yields a clean filter **+ 12% chance** of
  residue; heated `mixing` boils sludge → residue (disposal chain) while `mixing` residue +
  water reconstitutes sludge; `compacting` packs 4 residue → a waste block (plain 4↔1
  crafting too, so it works standalone). A **custom `FanProcessingType`**
  (`sludge_contaminating`, registered into Create's `FAN_PROCESSING_TYPE` registry) makes
  fans blowing through **sludge** re-dirty filters — the fan-driven inverse of the water
  splashing wash. It is **data-driven**: a base `toxicsurface:fan_contaminating` recipe type
  (loads standalone) supplies the transforms, so packs can add more as JSON; the airflow also
  **affects entities like the gas** (nausea + toxic damage, respecting mask/suit). Phase 4's
  vanilla bucket wash is untouched, so the wash paths stay distinct (bucket vs solid residue).
- **Toxic generators** (Create kinetic *sources*; both `compat.create`, gated like the other
  Mechanical machines): a **Waste Generator** that incinerates the §7 contamination items — loose
  `toxic_residue` (basic fuel) and a compacted `toxic_waste_block` (premium: longer burn, double
  RPM + capacity, so **waste blocks produce more power**) — and a **Sludge Generator** that burns
  toxic sludge from an internal tank (Create pumps fill it; it's a real fluid). Both extend Create's
  `GeneratingKineticBlockEntity`, spin a shaft on `FACING`, add stress capacity while burning, and
  halt on a redstone signal; the power tiers are a pure, unit-tested `GeneratorFuel` table. The
  Waste Generator's fuel slot and the Sludge Generator's tank are hopper/pipe-automatable.
  **Burning waste is never free** — while running each generator carries two drawbacks, factored
  into the Create-free `GeneratorEmissions`: (1) a **toxic smog cloud** (`SmogClouds`, the exact
  inverse of a cleanser bubble) poisons the air in a radius — folded into the `GasModel` predicate
  so it drains the air bar and kills passive mobs **even in a dimension/altitude the apocalypse
  hasn't reached**, while sealing or a cleanser still wins; and (2) **pollution** that accumulates on
  the per-dimension `ToxicityState` and reads as extra elapsed time, so heavy waste-burning makes
  the toxic ceiling **rise faster and the world turn toxic sooner**. Both drawbacks are
  config-tunable (`generators.generatorSmogRadius`, `generators.generatorPollutionPerTick`; set
  either to `0` to disable). A third, **opt-out** drawback ties exhaust into the filter economy via
  the shared, Create-free `ExhaustScrubber`: a generator runs **clean** (no smog, no pollution) only
  while a clean **Industrial Filter** sits in its **scrubber slot**. The industrial filter is a
  dedicated, **reusable, generator-only** consumable — crafted from **4 iron + 3 clean filters**,
  **never** accepted by a face mask or hazmat chest — that runs a generator clean for a configurable
  life (`generators.industrialFilterLifeTicks`, default **18000 ticks / 15 min**, ~10–20 min range)
  before **clogging**. Its remaining life lives on the item (the `industrial_filter_life` data
  component, shown as a green durability bar), so the block entities hold no scrubber state; when it
  runs out the scrubber swaps it in place for a **dirty industrial filter** and the generator vents
  raw. Cleaning is a two-step **cycle, not a discard**: a Create fan blowing **water** (`splashing`,
  Create-gated) turns a dirty filter into a **wet** one — releasing captured `toxic_residue` 50% of
  the time as a disposal cost — and **heat** (a vanilla `smelting`/`blasting` recipe, so a plain
  **furnace** *or* a Create fan over **lava** works) dries the wet filter back to a fresh clean one.
  The Waste Generator gains a second (filter) inventory slot; the Sludge Generator gains a one-slot
  filter handler alongside its tank; both are hopper/pipe-automatable (item-type routing keeps
  insertion unambiguous). Loads-standalone contract held: nothing here is classloaded without Create,
  and the filter items, data component, craft + heat-dry recipes, `GasModel`, `ToxicityState`, and
  `ExhaustScrubber` are all base-mod (only the fan-wash recipe is Create-gated; the predicate is
  unit-tested).

**Phase 8 — Polish & pack integration 🚧 (nearly done; branch `claude/phase7-create-integration-ez5bsp`)**
> Everything below is CI-green (spotlessCheck + compileJava + unit tests). The **player has now
> verified the visual/in-game behaviour** locally — the big systems (generators + filters, JEI/EMI
> incl. recipe categories, Jade, air-bar HUD, textures/models, toxic rain, and the volumetric fog with
> its near-field exposure volume) all work in `runClient`. Remaining: a **balance pass**, **real-pack
> compat testing**, and a real **cough.ogg**.

- **JEI integration + hint tooltips** (Phase 8 start): the generator fuels and the industrial-filter
  clog/clean cycle have **no recipe view**, so the mechanics are surfaced two ways. (1) A client
  `ItemTooltipEvent` handler (`HintTooltips`) attaches gray hint lines — what fuels each generator,
  that a filter clogs from use and is generators-only, and the wash→dry cleaning path; JEI and EMI
  both render the item tooltip, so these show in their ingredient panel and the inventory. (2) A
  real `@JeiPlugin` (`compat.jei.ToxicSurfaceJeiPlugin`) registers JEI **ingredient-info pages** (the
  "i" tab) with fuller descriptions for the same items. JEI is a **soft dependency on the same
  contract as Create**: only the JEI common **API** is `compileOnly`, the plugin is loaded by JEI
  only when present (so it never classloads in the standalone jar), it's declared `optional` /
  `CLIENT` in the mods.toml, and the Create-gated generator items are resolved by registry id so the
  plugin never pulls in `compat.create`.
- **JEI/EMI recipe categories** (Phase 8): beyond the info pages, two real categories surface the
  machines' actual processing. (1) **Weaving** — the Weaver's two-input weave table (from
  `WeaverLogic.recipes()`, shared with the Mechanical Weaver) shown as inputs → output with the
  weave time; the Weaver (and Mechanical Weaver, by id) are catalysts/workstations. (2) **Toxic
  Generator Fuel** — each fuel (residue, waste block, sludge bucket) → its generator, with the RPM,
  stress capacity (su) and burn/drain it drives; rows come from the shared JEI/EMI-free
  `compat.MachineFuel`, which resolves the Create-gated generators by **id** (no rows standalone, so
  the category self-hides). The **Cleanser** transforms no items (it's an area scrubber), so it stays
  an info page rather than a forced slot category. JEI uses `IRecipeCategory` + `RecipeType`; EMI
  uses `BasicEmiRecipe` + `EmiRecipeCategory`; both read the same data so they match the machines and
  each other.
- **EMI plugin** (Phase 8): EMI has its own API and ignores JEI plugins, so a native `@EmiEntrypoint`
  plugin (`compat.emi`) registers the same info pages as EMI "info recipes", plus the EMI Weaving and
  Generator-Fuel categories above. The (item, lang-key) list is factored into a JEI/EMI-free
  `compat.HintInfo` so the two viewers stay in lock-step. Same soft-dep contract: EMI `:api` is
  `compileOnly`, declared `optional`/`CLIENT`.
- **Jade tooltips** (Phase 8): in-world machine readouts via a `@WailaPlugin` (`compat.jade`). A
  single `MachineReadoutProvider` serves both Jade halves — server-side it harvests live state from
  any block entity implementing the **Jade-free `JadeReadout`** interface (Cleanser range/active,
  Weaver weave %, both generators' running/RPM/scrubber/fuel-or-sludge) into Jade's per-block NBT;
  client-side it renders the synced primitives as lines. Reading the synced NBT (not casting to a
  BE type) means it shows the Create-gated generators without ever referencing `compat.create`;
  `shouldRequestData` limits server round-trips to our machines. Jade is `compileOnly` via the
  Modrinth maven, declared `optional`/`CLIENT`.
- **Toxic air bar HUD** (Phase 2 carry-over, now done): the drowning-style green bubble row above
  the hotbar. `GasStatePayload` now also syncs the air bar as a 0..1 fraction; `AirBarOverlay`
  renders it from `ClientGasState` using the vanilla air sprites with a green tint, hidden when full
  (mirroring vanilla air) and nudged up when the vanilla air row is also showing (a sludge dive).
- **Enclosure-cache wiring** (Phase 2 carry-over, now done): the unit-tested `EnclosureCache` is now
  live. `EnclosureCacheHandler` holds one cache per dimension, so the sealing flood-fill runs once
  per air pocket instead of once per exposed player every cycle; `GasEffectHandler` queries it
  instead of scanning directly. The cache is LRU-bounded to 256 pockets/dimension (§8) and
  invalidated on the block-change events that make or break a seal (break, place, multi-place,
  fluid-formed blocks, explosions, and **piston** pushes/pulls — the moved column is invalidated
  along the piston's facing); caches clear on level unload. Changes that fire **no event**
  (`/setblock`, `/fill`, `/clone`, worldgen, other mods' direct `setBlock`) are caught by a **TTL**:
  a cached pocket older than 200 ticks (10 s) is re-scanned on next query, so any untracked breach
  self-heals within that window rather than persisting. The TTL lives in the pure `EnclosureCache`
  (the caller passes the tick, keeping it unit-testable).
- **Polish cluster** (carry-overs, now done): (1) a data-driven `toxicsurface:toxic` **DamageType**
  now sources all toxin damage (gas, sludge, mob death, sludge-fan), so deaths read "succumbed to
  the toxic air". (2) **Filter-expire warning** — `ModSounds.COUGH` (a `sounds.json` placeholder
  mapped to the vanilla choke sound until a `cough.ogg` is added) plays on expiry, and a
  `FilterExpiryPayload` triggers a red top/bottom vignette **HUD flash** (`ClientHudEffects` +
  `ScreenEffectsOverlay`). (3) **Toxic-rain overlay** — green wash + falling streaks while it rains
  and the player is in toxic open air; `GasStatePayload` gained an `inToxicArea` flag (protection-
  independent) so a masked player still sees it. Gated by `toxicRainEnabled`.
- **Cleanser purge-bubble visual** (Phase 6 polish, now done): a running Cleanser (both fuel and
  Mechanical) emits a green clean-air dome — an aura over the machine plus a throttled sampling of
  its purge-sphere boundary — via `CleanserVisual`, spawned server-side so it broadcasts with no
  extra networking.
- **Volumetric toxic-gas haze — "fog is visible where the damage is"** (Phase 8, now done; player-
  verified in-game): replaced the old *camera-block-only* NeoForge fog (which showed nothing unless
  your head was in the gas) with a **screen-space raymarched** haze — `ToxicGasFogRenderer` + the
  `toxic_fog` core shader. After the level draws, a fullscreen pass reconstructs each pixel's world
  position from the depth buffer, marches the view ray to the surface, and accumulates the *exposed
  toxic air* it crosses. So gas now reads correctly from **outside** it — through a window, from
  above the layer, across a valley — with real volumetric depth (distant ground fades into haze,
  hills/trees fog uniformly, no flat "fog sea" plane or cell seams). Coverage and march reach **scale
  with render distance** (clamped 8–16 chunks; `MaxDist`/`Steps` shader uniforms) so it fills to about
  as far as you can see. Hard-won gotchas, all fixed: (1) a **depth feedback loop** — sampling the main
  target's depth while it is still attached is undefined GL and left a stable garbage screen region;
  fixed by `copyDepthFrom` into a standalone `TextureTarget`. (2) **Step banding / tearing** on grazing
  boundaries — fixed with interleaved-gradient-noise ray-start dither + smoothstep-soft volume edges.
  (3) **Thin-block halo** — a Create shaft/fence/torch read as a roof and cleared a fog column; the
  roof scan now only accepts a *full collision cube*. (4) **Deep-water far-field** — fluids have no
  collision, so ocean columns read as open to bedrock and fogged; a **fluid surface now seals** the
  column like ground (gas sits *on* water, never in it). *Two dead ends worth not retrying* (see
  TESTING.md): view-independent world-space fog geometry (blocky top, seams, holes on slopes) and a
  2.5D per-column "sky-openness" heightmap flood (can't see an opening hidden under a roof line).
- **Fog exposure matches `GasModel` exactly — near-field 3D volume** (Phase 8, now done): the fog's
  "is this air toxic" test is answered in two tiers so it mirrors the *damage* predicate cell-for-cell
  instead of approximating it. Near the camera, a **96×48×96 exposure volume** (`RegionOpenness`, pure
  + unit-tested in `core/enclosure`) classifies every cell using the **same `LevelPassabilityProbe`
  the server damage scan uses** — a cell is exposed iff its pocket reaches the volume boundary or
  exceeds the enclosure budget, the grid analogue of `EnclosureScanner`'s "grew without closing". So a
  **breached room floods** with fog (the case a roof test fundamentally can't see), unsealed **caves/
  overhangs/doorways** fog, **sealed bases stay clear** (even viewed from outside through a window),
  and **fluid cells stay fog-free** per `GasModel.submerged`. Generator smog is gated by the same
  exposure, so a wall keeps a neighbour's smog out of a sealed room. The scan is **amortized**
  (~768 columns/frame, ~12 frames/rebuild) so no frame pays the whole region, packed into a Y-slice
  atlas texture; beyond the volume the shader falls back to the per-column roof-test heightmap, blended
  over the volume's outer 8 blocks so there is no seam. Tunables in `ToxicGasFogRenderer`:
  `VOL_COLUMNS_PER_FRAME`, `VOL_REBUILD_MOVE_THRESHOLD` (a fresh overhang can lag a rebuild ~1–2 s).
- **Cleanser bubbles carved out + generator smog added in** (Phase 8, now done): the two things that
  are *not* real blocks are synced to the fog shader — `CleanserBubbles.collectNear` /
  `SmogClouds.collectNear` gather nearby spheres server-side (gated by `hasAny`), sent via
  `FogVolumesPayload` and cached in `ClientFogVolumes` as packed `{x,y,z,r}` arrays. The shader fades
  gas *out* inside a cleanser sphere and *in* inside a smog sphere (soft edges), both still respecting
  the exposure gate. Smog renders even with no ambient ceiling (a running generator in a clean area).
- **Shader verified on a real GL context** (Phase 8): the `toxic_fog` vsh/fsh pair now
  **compiles + links on an actual GL 3.2 core profile** (a small LWJGL harness under Xvfb, Mesa) —
  closes the long-standing "never seen the shader accepted by a driver in this env" gap; the rendered
  look itself is player-confirmed.
- **Live-tuning commands + Weaver work-face + Aeronautics dev flag** (Phase 8, now done):
  `/toxicsurface` server commands set/get the toxic values on the fly (ceiling, time-to-toxic, on/off
  with a persisted `suppressed` flag, start Y, rise speed, sludge min/max depth) for testing without a
  config reload. The **Mechanical Weaver** got a designated **work face** (`WORK_FACE` DirectionProperty
  following the shaft axis — top when shafts enter two sides, a side when they go up/down) with a
  distinct texture, and its item handler now **exposes only the output slot** to funnels/hoppers
  (Create-style) instead of ejecting the whole inventory. A `-PaeroRuntime` dev flag loads Create:
  Aeronautics alongside the other soft-dep runtimes (Aeronautics contraptions already seal for free —
  they extend `AbstractContraptionEntity`, which the existing `ContraptionSeal` handles).
- **Accessibility sliders** (Phase 8, now done): a per-player **CLIENT** config
  (`ToxicSurfaceClientConfig`, never synced) — `fogIntensity` (full fog → thin tint), `visorOverlay`
  toggle, `filterFlashIntensity`, `toxicRainOpacity` — wired into the fog handler, visor overlay, and
  screen-effects overlay. Editable in-game via a NeoForge `ConfigurationScreen` registered from the
  Mods list.
- **Pre-toxicity telegraph + retroactive advancement** (Phase 3 carry-over, now done): before
  activation, `ToxicityTicker` fires an escalating title + subtitle + chat warning
  (`ToxicityTelegraph`) the first tick the countdown crosses each configured threshold
  (`telegraphWarningTicks`, default 3 in-game days / 1 in-game day / 5 min / 1 min — day-scale
  warnings shown in in-game days, the final countdown in real-time minutes; `telegraphEnabled` toggle).
  The crossed-stage count is persisted on `ToxicityState`, so it survives restarts and collapses any
  multi-threshold jump (e.g. a pollution spike) into one announcement with the true remaining time.
  The "The Air Has Turned" advancement is now also granted **retroactively** — players who log in or
  change into an already-toxic affected dimension receive it (the award is idempotent).

- **Textures & models** (Phase 8, ✅ complete — full coverage verified by cross-reference; player
  confirmed in-game): started with **toxic sludge** — animated
  `sludge_still`/`sludge_flow` strips + `sludge_overlay`, plus the sludge-bucket item texture/model,
  procedurally generated by `tools/textures/gen_sludge.py` (dependency-free; authored as luminance
  maps so the fluid's olive tint colours them) and stitched onto the block atlas via
  `atlases/blocks.json`. Flow scrolls one tile over its frame set for a seamless loop. Then the
  **consumable items + waste block** via `tools/textures/gen_items.py`: the three air filters
  (clean/used/carbon), three industrial filters (clean/dirty/wet), toxic residue, hazmat material,
  face mask, and the `toxic_waste_block` (texture + cube_all model + blockstate). All
  `item/generated` icons, procedural and first-pass. The **sludge bucket** is composited from the
  *vanilla* bucket + water-bucket textures by `tools/textures/gen_bucket.py` so it matches
  Minecraft's bucket exactly, just with olive sludge in the cavity. Then, in vanilla's
  **outline-and-shade** style (a noisy metal panel + 1px bevel + central motif), the **machines**
  via `tools/textures/gen_machines.py`: the **Weaver/Cleanser** blocks (`cube_bottom_top`, distinct
  top) plus the four Create kinetic machines (**mechanical weaver/cleanser**, **waste/sludge
  generators**) as `cube_column` bodies — a brass **shaft-socket** on the FACING-axis ends, uniform
  body on the sides, blockstates mapping all six FACING values to the pillar-axis rotation; the
  generators get furnace-like firebox sides. The **hazmat suit** via `tools/textures/gen_armor.py`:
  four inventory icons (helmet w/ cyan visor, chestplate, leggings, rubber-soled boots) and the two
  worn armour-layer sheets (`hazmat_layer_1`/`_2`) flood-filled with a hi-vis weave + reflective tape
  so the suit never shows the missing-texture checkerboard. The sludge **LiquidBlock** also got a
  blockstate + particle model (vanilla-water style). **All registered blocks/items now have
  models + textures — no missing-texture cases remain.**
- **Second-pass (HQ) art** (Phase 8): PyPI became reachable (route pip *through* the agent proxy —
  it's in the proxy `noProxy` list, so clear `no_proxy` and set `PIP_CERT=/root/.ccr/ca-bundle.crt`;
  a direct connection 403s at the egress firewall), so **numpy + Pillow** are now available. A single
  `tools/textures/gen_hq.py` regenerates every texture with tileable fractal value noise (2D + 3D for
  seamless animation loops), multi-step shaded colour ramps with **Bayer ordered dithering** (smooth
  gradients that stay crunchy at 16px), bevels with corner ambient occlusion, dark readability
  outlines, and soft emissive glows (generator fireboxes, cleanser gauge). All native-resolution
  pixel art — no supersampling blur. **The first-pass generators (`gen_items.py`/`gen_machines.py`/
  `gen_armor.py`/`gen_sludge.py`) are kept for reference and the original PNGs archived under
  `tools/textures/archive_v1/`.** Still procedural; not yet seen rendered in-game.
- **Hazmat visor overlay** (Phase 8): the helmet's first-person view used to be four flat
  `fill()` bars at the screen edges; it's now a real `textures/misc/hazmat_visor.png` (256x256, a
  transparent rounded viewport ringed by tinted green glass thickening to a near-opaque dark rubber
  frame, with breath condensation, scratches and a reflection streak, generated by `gen_hq.py`).
  `EquipmentHudOverlay` blits it stretched to fill the screen (the vanilla pumpkin-overlay approach),
  gated on `ToxicSurfaceClientConfig.VISOR_OVERLAY_ENABLED`.
- **Headless GameTests** (Phase 8, §10): first `@GameTestHolder` suite (`gametest.ToxicSurfaceGameTests`)
  — the enclosure flood-fill against a *real* level (sealed box vs. a breached one) and the toxic-sludge
  float-on-water rules (doesn't sink into / contaminate / get replaced by water). All four load one
  shared empty arena (`data/toxicsurface/structure/empty.nbt` — note 1.21.1's **singular** `structure/`
  dir — generated by `tools/gametest/gen_empty_structure.py`, a hand-rolled NBT writer) and build their
  scenario via the helper. **Verified green** via `runGameTestServer` once the MC asset CDN was reachable.
  That run also surfaced a real bug: 11 crafting recipes used **bare-string ingredients** (`"H": "id"`),
  which 1.21.1's strict datapack codec rejects — they silently weren't loading. Converted to object form
  (`{"item": …}` / `{"tag": …}`), so the hazmat set, weaver, cleanser, filters, mask and waste block
  are craftable again.

- **Machine-layer dedup + datapack weave recipes** (Phase 8 tail): a structural pass extracted the
  copy-pasted machine plumbing into shared bases — `menu.AbstractMachineMenu` (player-inventory
  layout + shift-click template), `block.AbstractFueledMachineBlockEntity` (fuel/lit bookkeeping,
  item handler, ContainerData + NBT round-trip for Weaver/Cleanser), plus shared
  `WeaverLogic.canOutput/craft`, `ExhaustScrubber.tickExhaust` and `SludgeReclaimer.tickActive`
  helpers for the Create siblings. Then the Weaver's hard-coded table became the **datapack-driven
  `toxicsurface:weaving` recipe type** (§3's "recipe-driven" goal): `block.WeavingRecipe` carries
  two counted ingredients + result + weave time with full JSON/network codecs, the four shipped
  recipes live in `data/toxicsurface/recipe/weaving/`, both Weaver variants look up via the
  `RecipeManager`, and the JEI/EMI categories read the same manager (so pack-added recipes show up
  automatically; EMI now uses the real recipe ids). A new GameTest (`weaverCraftsDatapackRecipe`)
  proves the JSONs parse, register, and drive the machine end-to-end — 13/13 green standalone and
  with Create.

**Carried-forward polish / TODO** (tracked in-code):
Textures/models, the JEI/EMI recipe categories, and the fog/HUD/rain visuals are now
**player-confirmed in-game**. What remains of the whole roadmap: a **balance pass** (filter
lifetimes, generator fuel curves + output, cleanser fuel/range, air-bar drain/refill, damage
cadence, toxicity timings — mostly config-default tuning), **real-pack compat testing** (drop into
an actual Create / Aeronautics / Sky Archipelago pack), and a real **cough.ogg** (still the vanilla
drown-hurt placeholder). No phase 9 — this is the tail of Phase 8.

**Branches:** the Phase 1–8 history lives on `claude/phase7-create-integration-ez5bsp`; a **`main`**
branch was cut at that tip (a true "everything merged", no merge commit) and now also carries the
machine-layer dedup + datapack weave recipes from `claude/minecraft-mod-refactoring-wyydgb`
(fast-forwarded in). Develop new work on feature branches cut from `main`. GitHub's *default* branch
is still the old `claude/minecraft-toxic-mod-io2j93` — flip it to `main` in repo Settings when ready.

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

## 12. Local testing & dev runs

Build/run requires **JDK 21** (NeoForge 1.21.1). A newer JDK breaks Gradle itself
("Unsupported class file major version" during build-script analysis) — point
`JAVA_HOME` / `org.gradle.java.home` at a JDK 21.

- **CI-equivalent checks** (fast, no GUI): `./gradlew spotlessCheck compileJava test`.
- **Headless GameTest** (what CI runs): `./gradlew runGameTestServer`
  (`-PcreateRuntime=true` for the with-Create job).
- **Play it:** `./gradlew runClient`. Standalone loads the base mod only.
  - **Create / JEI / EMI / Jade** are `compileOnly`, so they are **not** loaded by default. The opt-in
    flags below resolve the full mod jars from the mavens (below) and stage them into `run/mods/` via
    the `syncRunMods` task (wired into `runClient`/`runServer`), where FML's `ModsFolderLocator` loads
    them as **real mods**. Putting them on `additionalRuntimeClasspath` does *not* work — that only adds
    their classes as libraries and FML never registers them, so the integrations stay disabled. Staged
    jars are named `tsmanaged-*.jar` and wiped/re-synced each run, so toggling a flag off or swapping
    JEI↔EMI never leaves a stray; mods you drop into `run/mods/` by hand are left alone.
    - **Create** (generators, industrial filter, mechanical machines, sludge fan):
      `-PcreateRuntime=true` (brings Flywheel + Ponder + Registrate via its jar-in-jar).
    - **Recipe viewer + Jade:** `-PviewerRuntime=true` loads **JEI** + Jade by default; add
      `-PuseEmi=true` to load EMI instead (JEI and EMI conflict — one at a time; Jade coexists with
      either). Combine with `-PcreateRuntime=true` so the Generator-Fuel recipe category populates:
      `./gradlew runClient -PcreateRuntime=true -PviewerRuntime=true`.
    - The headless `runGameTestServer` (CI) deliberately stays standalone — the GameTests don't need
      Create loaded — so `syncRunMods` is not wired to it.
- **Fast-forward toxicity:** edit `run/config/toxicsurface-server.toml` →
  `timeToToxicTicks = 2000` (watch the telegraph countdown, then activation) and reload the world.
- **Soft-dep mavens** (now allowlisted): blamejared (JEI), terraformersmc (EMI), Modrinth (Jade,
  redirects to `cdn.modrinth.com`), createmod/tterrag (Create).

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
- **Mechanical Cleanser** *and* **Mechanical Weaver** (Create rotation); **sludge
  pumpable** through Create pipes.
- **Cleanser range**: set **manually in its menu** (primary); **redstone input is an
  optional on-the-fly override** that selects a range tier (for lever/comparator
  quick-switching), not the only control.
- **Weaver = textile/filtration fabricator**: recipe-driven hub for Hazmat Material,
  air filters (incl. a **Carbon long-life filter** tier), and suit repair; accepts a
  **`#toxicsurface:fiber`** tag of inputs.
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
