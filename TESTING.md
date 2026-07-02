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

### Toxic-gas haze — screen-space per-pixel fog (REWRITTEN again; never seen rendered by me)
History: this went through a hard-geometry phase (translucent world-space boxes on a 4-block grid).
It fixed the corridor and window cases, but screenshots showed it couldn't escape three problems
that are *inherent* to view-independent geometry: a blocky/flat "fog sea" top and visible cell
seams, holes stepping over hills, and clear ground when viewed from high up (bounded grid + hard
top). The "dense from above yet soft near the ceiling" look is fundamentally view-dependent, so the
geometry approach was abandoned for a screen-space one.

Current: `ToxicGasFogRenderer` + the `toxic_fog` core shader. After the level draws, a fullscreen
pass reconstructs each surface pixel's world position from the depth buffer and adds green haze
scaled by camera distance — but only where that pixel is genuinely exposed toxic air: at/below the
ceiling, and at the top of its own column (a pixel well below its column's highest solid is under a
roof → sealed → skipped). The "is this column open / under cover" test samples a 256×256 per-column
terrain-top texture (`MOTION_BLOCKING` heightmap, 16-bit height packed into R+G), rebuilt around the
camera every ~1s or when it drifts >16 blocks. Because the test is per-pixel and the height data
updates smoothly, there are no cell seams and no flashing (the flashing before came from a single
throttled view-ray; this replaces it with exact per-pixel height data).

Verified by me: compiles clean; unit tests + GameTests green; a real headless client boot (Xvfb)
reaches the title screen with the shader compiling and linking on the actual GL engine, no errors,
no crash. **Not verified: the actual rendered result, and the in-world texture-upload path** (that
code only runs once a world loads, which the headless title-screen boot doesn't reach) — so the
first live run is where any encoding/orientation/reconstruction bug would surface. Please check:
- [ ] It renders at all in a toxic world (if totally absent, likely a depth-reconstruction or
      height-texture bug — grab a screenshot and the log)
- [ ] **Soft, not blocky**: no flat green top plane, no cell seams, no hard grid
- [ ] **Hills**: no holes looking up or down a slope; haze drapes over terrain
- [ ] **From high up looking down**: reads dense over the ground (long ray through the layer),
      out to the ~128-block map radius; beyond that it fades to nothing (raise `MAP_SIZE` if the
      boundary is too close — cost scales with its square)
- [ ] **Sealed room, no windows**: interior fully clear (per-pixel under-cover test)
- [ ] **Sealed room / base, looking out a window**: interior clear, haze outside thickening with
      distance
- [ ] **No flashing** when turning the camera or moving
- [ ] North/south not mirrored (a Z-flip in the height texture would offset the mask from reality —
      if sealed areas fog and open areas don't, the texture V-orientation is inverted)
- [ ] `fogIntensity` slider at 0 disables it; scales alpha in between
- [ ] With Iris/Oculus active: effect is skipped (no crash/z-fight)
- [ ] No FPS cliff or once-a-second hitch (the 256×256 = 65k heightmap lookups on rebuild)
- [ ] Old `ToxicFogHandler` (personal screen fog while in gas) still layers on top without fighting
- [ ] **Known gap (unchanged):** Cleanser bubbles aren't carved out — the shader has no bubble data
      yet; standing in one may still show haze. Needs cleanser-range sync; follow-up.
- [ ] Colour / density feel right (`FOG_R/G/B`, `FOG_DENSITY`, `FOG_MAX_ALPHA` in
      `ToxicGasFogRenderer.java`; density saturates the haze by ~distance 1/DENSITY blocks)

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
