# ToxicSurface — in-game testing checklist

A manual test pass for behavior that's only compile/preview/GameTest-verified, not yet seen in a
real client. Ordered so the **newest / least-verified** code comes first. Most items work in a plain
`./gradlew runClient`; items tagged **[Create]**, **[JEI/EMI]**, **[Jade]** need that runtime loaded
(see DESIGN §12). When something's off, grab a screenshot + the log and note standalone vs
`-PcreateRuntime=true`.

> Automated coverage already green: unit tests, and 12 headless GameTests (enclosure sealed/breached
> against a real level; door/piston seal rules ×4; contraption block-map seal rules ×4; sludge
> float-on-water ×2) via `./gradlew runGameTestServer`.

## Priority 1 — added this session, verify in-game

### Toxic-gas haze — now real world-space geometry (REWRITTEN this session — never seen rendered)
Two earlier passes at this (screen-space depth reconstruction, then a server-throttled ray-march
to keep sealed rooms clear) both shipped real bugs — the ray-march in particular caused visible
flashing, since a single view-direction sample refreshed twice a second can't smoothly track a
turning camera. Replaced entirely with `ToxicGasFieldRenderer`: real translucent boxes placed in
world space (using vanilla's own `RenderType.debugQuads()` — no custom shader, no GLSL, no
per-frame matrix math) over grid columns judged "exposed" (nothing solid reaches the toxic
ceiling's height there, via the existing heightmap — treats a player's roof and a natural mountain
identically). The ordinary GPU depth test then hides it behind real walls/roofs automatically and
instantly, the same way a wall already hides water on the other side — no ray-march, no per-tick
network payload, no flashing by construction. Rebuilds a ~128×128-block grid around the player
every ~1s (or sooner if they move ~12+ blocks), not every frame.

**Known gap:** doesn't know about Cleanser bubbles yet (those aren't bounded by real blocks, so
depth-test occlusion can't carve them out) — standing in one may still show nearby fog geometry.
Flagging as a follow-up, not fixed this pass.

**Second real-client screenshot found a third structural bug, now fixed:** after the interior-walls
fix below made outdoor haze work, sealed rooms started showing fog again. Cause: the wall band's
floor was one constant (`ceilingY - WALL_HEIGHT`) applied to every exposed column alike. A column
only reads "exposed" because nothing reaches the ceiling's *exact* height there — a base whose roof
is lower than the (since-risen) ceiling still passes that check, so the flat floor let the wall
band dip below the roof and straight into the room's own interior air. Nothing occludes that,
since the camera and the misplaced geometry share the same open pocket. Fixed: each column's floor
is now clamped to `max(that column's own solid height, ceilingY - WALL_HEIGHT)`, so the geometry
never sits below a real roof — the roof then correctly occludes it from inside, same as before.

**First real-client screenshot (this session) found a second structural bug and it's now fixed:**
walls were only drawn at the outer silhouette of a contiguous exposed region, to avoid double-
drawing the shared edge between two exposed cells. That meant a big open field showed haze only at
its rim and a thin cap far overhead — completely clear once you were looking into its middle,
exactly "I can see the borders where it's loaded in, but nothing within it." Fixed: every exposed
cell now unconditionally owns its west/north wall (an interior partition when the neighbour is also
exposed, the silhouette edge when it isn't), so looking across open ground now crosses one wall
every `CELL_SIZE` (4) blocks — each wall's alpha was dropped hard (0.55 → 0.14) so that many stacked
crossings accumulate into a gradually thickening haze with distance instead of a flat wall of color.

Also note: if a location shows *no* haze at all (not even at range) and it doesn't look like a
sealed-room case, check `/toxicsurface status` — it might just mean the toxic ceiling Y is below
where you're standing/looking, i.e. you're legitimately above the gas there.

Verified so far: compiles clean, no custom shader risk (uses `RenderType.debugQuads()`, an
already-proven vanilla render type), unit tests + GameTests green. **Still not seen rendered by
me** — please check closely:
- [ ] **Sealed room, no windows**: walking around inside shows *no* haze anywhere, even down a long
      hallway
- [ ] **Open field / outdoors, looking into the middle of it** (the newly-fixed bug): haze should
      now be visible throughout, gradually thickening with distance — not just at the edges
- [ ] **No flashing**: turn the camera around inside/near a room — the haze should never pop or
      flicker, since it's real geometry
- [ ] **Sealed room, looking out a window**: interior stays clear; haze visible outside, thickening
      with distance
- [ ] Standing **above the toxic ceiling**, looking down: reads as thick/near-opaque haze over the
      ground (`TOP_ALPHA` in `ToxicGasFieldRenderer.java` — tune there if still too thin/thick)
- [ ] Standing in a **cleanser bubble**: known gap above — expect this to still look wrong for now
- [ ] Close-up, the haze shouldn't look like a visible grid/maze of walls — if the low-alpha
      partition lines are individually noticeable up close, `WALL_ALPHA` needs to drop further
- [ ] Grid boundary (~64 blocks out) doesn't look jarring; consider whether `GRID_RADIUS_CELLS`
      needs to be bigger
- [ ] Walking/flying around: grid rebuild (every ~1s or ~12 blocks moved) isn't visually jarring —
      look for pop-in at the edges as it recenters
- [ ] `fogIntensity` accessibility slider at 0 disables the effect entirely; scales it in between
- [ ] With Iris/Oculus active: effect is skipped (no z-fight/crash)
- [ ] No FPS cliff — grid rebuild is throttled and cheap (heightmap lookups only), per-frame cost
      is just drawing the cached quad list
- [ ] Old `ToxicFogHandler` (personal screen fog while exposed) still layers correctly on top

### Conditional green rain
- [x] Below the toxic ceiling Y: rain droplets render green (not blue)
- [x] Above the ceiling Y: rain renders normally (blue)
- [x] Green rain visible **through windows** in a sealed room (Y-level check, not player state)
- [x] Toggle rain with `/weather rain` and confirm color swaps when ceiling is active vs not
- [ ] With Iris/Oculus shaders active: vanilla rain is used (no shader z-fight), no crash

### Mechanical Weaver — depot style [Create]
- [x] Items rest on top of the Weaver block (displayed flat, lit from above)
- [x] Two END_ROD weaving sticks animate in crossing over-under pattern (3 stitches per craft)
- [x] Animation speed scales with incoming RPM
- [x] White poof particle fires on each completed output
- [x] Weaver processes kelp+wool and wool+string without GUI (right-click inserts/extracts)
- [x] At very high RPM the animation plays visibly faster (not just a strobe)
- [x] Rod positions look correct from all four horizontal facings (rods always cross above the items)

> **Rod position constants** (adjust if needed): `MechanicalWeaverRenderer.java` —
> `TIP_SPREAD = 0.16`, `TIP_BASE_Y = 0.90`, `TIP_DIP = 0.05`, `ROD_LENGTH = 0.55`

### Generator right-click interaction [Create]
- [x] Right-clicking a Waste Generator with an industrial filter inserts it
- [x] Right-clicking empty-handed extracts the filter (or top-most non-empty stack)
- [x] Same interaction works on the Sludge Generator

### Industrial filter full cycle [Create]
- [x] Fan blowing sludge bulk-contaminates industrial filters → dirty industrial filter
- [x] Washing dirty filters with a fan+water produces 1–2 toxic residue (50 % chance) + wet filter
- [x] Wet filter dries in a furnace → clean industrial filter (ready to re-use)
- [x] Clean industrial filter in scrubber slot suppresses smog; dirty/absent filter allows smog

### Contraption sealing [Create]
- [x] A player sealed inside a moving contraption is protected from toxic gas (confirmed via log)
- [x] A contraption with a gap (open door, extended piston) in its wall does NOT seal the player
- [x] Unsealed room in a contraption does NOT grant protection

### [Jade] machine readouts
- [x] Cleanser: shows live range, active state, RPM on shift-hover
- [x] Waste/Sludge Generator: shows fuel/RPM on shift-hover
- [x] Mechanical Weaver: shows weave-progress %, active state, RPM on shift-hover

## Priority 2 — core mechanics (unit/GameTest-verified only)

### Toxic sludge in-world
- [x] On toxicity onset, **ocean top layer** converts to sludge
- [x] Sludge **floats** on water (doesn't sink/vanish) and spreads across the surface
- [x] Sludge **still & flow** animations read as toxic-green (runtime tint applied)
- [x] Swimming in sludge applies the **murky-green vision overlay**
- [x] Foliage / passive mobs in **clean water** under sludge survive; only sludge contact kills
- [x] Sludge / gas damage applies **no knockback**

### Gas / sealing / cleanser
- [x] Toxic open air damages you; a **sealed room** protects you
- [x] **Pistons** reshaping a wall update the sealed state
- [x] `/setblock` reshaping a wall self-heals within ~10s (TTL)
- [x] **Cleanser** projects a clean bubble; raising its range scrubs the whole radius

### Weaver / Cleanser machines
- [x] Weaver: kelp+wool → hazmat material; wool/string → filter; filter+coal → carbon filter
- [x] Cleanser GUI changes range; HUD/Jade reflects it

## Priority 3 — integrations (need their runtime)

### [Create] generators & machines
- [x] Waste generator burns residue/waste blocks → rotation; waste block = more power/longer burn
- [x] Sludge generator runs off piped-in sludge
- [x] Mechanical Weaver / Cleanser work off rotational power
- [x] Industrial filter **clog → wash → dry** cycle (fan+water → wet; furnace/lava → clean)
- [x] Generators vent smog unless a clean industrial filter is in the scrubber slot

### [JEI/EMI] recipe viewers — *layouts unverified*
- [x] **Weaving** category: inputs → output + time, slots/arrow positioned sanely
- [x] **Toxic Generator Fuel** category: fuel → RPM / SU / burn stats
- [x] Info pages appear for Cleanser, filters, generators, residue/waste block
- [x] Machines show as catalysts/workstations

### [Jade]
- [x] Looking at a machine shows the live readout (range / weave % / generator RPM / fuel)

## Priority 4 — polish & edges
- [ ] Pre-toxicity **telegraph** messages fire at the thresholds (3 days / 1 day / 5 min / 1 min)
- [ ] **Retroactive advancement** grants on login if you missed the toxic onset
- [ ] **Accessibility sliders** (fog / visor / flash / rain intensity) take effect
- [x] Air bar + filter-time gauge correct; filter-expiry red flash fires
- [x] **Cough sound** is still the vanilla-choke placeholder (expected)

## Crafting & visual (earlier session — confirmed)
- [x] Hazmat **helmet / chestplate / leggings / boots** craft (hazmat material + iron)
- [x] **Weaver** and **Cleanser** blocks craft
- [x] **Clean air filter**, **industrial filter**, **face mask** craft
- [x] **Toxic waste block** ↔ **toxic residue** (compact / from-block) craft
- [x] No "recipe failed to load" errors in the startup log
- [x] Weaver / Cleanser blocks render right (distinct top, beveled sides) when placed
- [x] All item icons read clearly in inventory/hotbar (filters, residue, hazmat pieces)
- [x] Hazmat suit renders on the player model with no purple checkerboard
- [x] Helmet shows the rounded viewport + green-tint frame; toggling in config hides it

## Most likely to surface issues
Weaver rod angle from non-south facings; shader gate for green rain (Iris users); wet-filter
drying recipe; contraption seal on assemble/disassemble transitions.
