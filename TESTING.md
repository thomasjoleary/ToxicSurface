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
- [ ] Hazmat **helmet / chestplate / leggings / boots** craft (hazmat material + iron)
- [ ] **Weaver** and **Cleanser** blocks craft
- [ ] **Clean air filter**, **industrial filter**, **face mask** craft
- [ ] **Toxic waste block** ↔ **toxic residue** (compact / from-block) craft
- [ ] No "recipe failed to load" errors in the startup log

### HQ textures & models on real geometry
- [ ] Weaver / Cleanser blocks render right (distinct top, beveled sides) when placed
- [ ] Sludge **bucket** item looks like a vanilla bucket with speckled green
- [ ] All item icons read clearly in inventory/hotbar (filters, residue, hazmat pieces)
- [ ] Toxic waste block tiles cleanly in bulk (no obvious seams)

### Hazmat visor overlay
- [ ] Helmet shows the rounded viewport + green-tint frame (not the old edge bars)
- [ ] **Check your actual aspect ratio** — especially ultrawide (square texture stretches)
- [ ] Toggling it off in client config makes it disappear
- [ ] Frame thickness feels okay (not too claustrophobic)

### Worn hazmat armor
- [ ] Suit renders on the player model (`hazmat_layer_1/2`) with no purple checkerboard

## Priority 2 — core mechanics (unit/GameTest-verified only)

### Toxic sludge in-world
- [ ] On toxicity onset, **ocean top layer** converts to sludge
- [ ] Sludge **floats** on water (doesn't sink/vanish) and spreads across the surface
- [ ] Sludge **still & flow** animations read as toxic-green (runtime tint applied)
- [ ] Swimming in sludge applies the **murky-green vision overlay**
- [ ] Foliage / passive mobs in **clean water** under sludge survive; only sludge contact kills
- [ ] Sludge / gas damage applies **no knockback**

### Gas / sealing / cleanser
- [ ] Toxic open air damages you; a **sealed room** protects you
- [ ] **Pistons** reshaping a wall update the sealed state
- [ ] `/setblock` reshaping a wall self-heals within ~10s (TTL)
- [ ] **Cleanser** projects a clean bubble; raising its range scrubs the whole radius

### Weaver / Cleanser machines
- [ ] Weaver: kelp+wool → hazmat material; wool/string → filter; filter+coal → carbon filter
- [ ] Cleanser GUI changes range; HUD/Jade reflects it

## Priority 3 — integrations (need their runtime)

### [Create] generators & machines
- [ ] Waste generator burns residue/waste blocks → rotation; waste block = more power/longer burn
- [ ] Sludge generator runs off piped-in sludge
- [ ] Mechanical Weaver / Cleanser work off rotational power
- [ ] Industrial filter **clog → wash → dry** cycle (fan+water → wet; furnace/lava → clean)
- [ ] Generators vent smog unless a clean industrial filter is in the scrubber slot

### [JEI/EMI] recipe viewers — *layouts unverified*
- [ ] **Weaving** category: inputs → output + time, slots/arrow positioned sanely
- [ ] **Toxic Generator Fuel** category: fuel → RPM / SU / burn stats
- [ ] Info pages appear for Cleanser, filters, generators, residue/waste block
- [ ] Machines show as catalysts/workstations

### [Jade]
- [ ] Looking at a machine shows the live readout (range / weave % / generator RPM / fuel)

## Priority 4 — polish & edges
- [ ] Pre-toxicity **telegraph** messages fire at the thresholds (3 days / 1 day / 5 min / 1 min)
- [ ] **Retroactive advancement** grants on login if you missed the toxic onset
- [ ] **Accessibility sliders** (fog / visor / flash / rain intensity) take effect
- [ ] Air bar + filter-time gauge correct; filter-expiry red flash fires
- [ ] **Cough sound** is still the vanilla-choke placeholder (expected)

## Most likely to surface issues
The JEI/EMI category pixel layouts, the visor on non-16:9, the sludge fluid tint/animation
appearance, and armor-on-player rendering — the code drawn most "blind."
