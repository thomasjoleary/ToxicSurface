# ToxicSurface — in-game testing checklist

A manual test pass for behavior that's only compile/preview/GameTest-verified, not yet seen in a
real client. Ordered so the **newest / least-verified** code comes first. Most items work in a plain
`./gradlew runClient`; items tagged **[Create]**, **[JEI/EMI]**, **[Jade]** need that runtime loaded
(see DESIGN §12). When something's off, grab a screenshot + the log and note standalone vs
`-PcreateRuntime=true`.

> Automated coverage already green: unit tests, and 4 headless GameTests (enclosure sealed/breached
> against a real level; sludge float-on-water ×2) via `./gradlew runGameTestServer`.

## Priority 1 — just changed, never seen in a client

### Crafting recipes (fixed this session — verify they load now)
- [x] Hazmat **helmet / chestplate / leggings / boots** craft (hazmat material + iron)
- [x] **Weaver** and **Cleanser** blocks craft
- [x] **Clean air filter**, **industrial filter**, **face mask** craft
- [x] **Toxic waste block** ↔ **toxic residue** (compact / from-block) craft
- [x] No "recipe failed to load" errors in the startup log

### HQ textures & models on real geometry
- [x] Weaver / Cleanser blocks render right (distinct top, beveled sides) when placed
- [x] Sludge **bucket** item looks like a vanilla bucket with speckled green
- [x] All item icons read clearly in inventory/hotbar (filters, residue, hazmat pieces)
- [x] Toxic waste block tiles cleanly in bulk (no obvious seams)

### Hazmat visor overlay
- [x] Helmet shows the rounded viewport + green-tint frame (not the old edge bars)
- [x] **Check your actual aspect ratio** — especially ultrawide (square texture stretches)
- [x] Toggling it off in client config makes it disappear
- [x] Frame thickness feels okay (not too claustrophobic)

### Worn hazmat armor
- [x] Suit renders on the player model (`hazmat_layer_1/2`) with no purple checkerboard

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

## Most likely to surface issues
The JEI/EMI category pixel layouts, the visor on non-16:9, the sludge fluid tint/animation
appearance, and armor-on-player rendering — the code drawn most "blind."
