# ToxicSurface — agent guide

A NeoForge **1.21.1** mod (Java **21**, Mojmap) that turns the world's surface toxic after a
configurable time, forcing sealed bases, air filtration, hazmat gear, and reclamation machines.
Designed to slot into a Create / Create: Aeronautics modpack. **`DESIGN.md` is the source of truth** —
read it (esp. §3 system design, §5b build log for current status, §9 soft-dep contract, §12 dev runs).

## Build & verify

- Requires **JDK 21**. A newer JDK breaks Gradle ("Unsupported class file major version").
- Always run before committing: `./gradlew spotlessApply compileJava test`
  (use `--offline` once deps are cached). `spotlessCheck` is wired into `check` and CI.
- Headless integration: `./gradlew runGameTestServer` (add `-PcreateRuntime=true` for the Create side).
- Play: `./gradlew runClient` (`-PcreateRuntime=true` to load Create; JEI/EMI/Jade need temporary
  `additionalRuntimeClasspath` adds — see DESIGN §12).
- CI runs **both** standalone (no Create) and with-Create jobs; keep both green.

## Architecture conventions (match these)

- **Pure logic + thin adapter:** risky/algorithmic code lives Minecraft-free in `core/**` and is
  unit-tested (`src/test`); a thin NeoForge adapter wires it in. Keep new logic testable this way.
- **Server-authoritative:** all hazard logic is server-side; clients only render via synced payloads
  (`network/`, `client/`). Config is server-synced except `ToxicSurfaceClientConfig` (CLIENT, per-player).
- **Soft dependencies** (Create, JEI, EMI, Jade): the API is `compileOnly` only; integration code
  lives in `compat/<mod>/` and is loaded **only when that mod is present** (Create gated by
  `CreateCompat.isLoaded()` + `CreateContent`; JEI/EMI/Jade via their own `@…Plugin` scanning). It
  must never classload in the standalone jar. Datapack recipes that need a mod are
  `neoforge:conditions` gated. Generator block items are resolved by registry **id** (not class refs)
  in base/compat code so `compat.create` isn't pulled in.
- **Data-driven where players touch it:** weave recipes are a datapack recipe type
  (`toxicsurface:weaving`, shipped under `data/toxicsurface/recipe/weaving/`, JSON shape documented
  on `WeavingRecipe`); both Weaver variants and the JEI/EMI categories read the `RecipeManager`, so
  never reintroduce a hard-coded table. Machine plumbing lives in shared bases
  (`AbstractMachineMenu`, `AbstractFueledMachineBlockEntity`, `WeaverLogic`/`ExhaustScrubber`/
  `SludgeReclaimer` helpers) — extend those rather than copy-pasting between machine variants.
- **Registries** in `registry/` (`Mod*`), registered in `ToxicSurface` ctor. Lang in
  `assets/toxicsurface/lang/en_us.json`; keep it valid JSON.
- **Style:** Palantir Java Format (4-space, 120 col), LGPL SPDX header on every file, comments at the
  surrounding density (explain *why*). `@EventBusSubscriber(bus=…)` deprecation warnings are
  pre-existing repo-wide — match the existing idiom, don't churn them.

## Commit / PR

- Develop on a feature branch cut from `main` (most recent: `claude/minecraft-mod-refactoring-wyydgb`,
  merged); `git push -u origin <branch>` (retry w/ backoff on network errors). Do **not** open a PR or
  push `main` unless asked.
- Commit messages: conventional style (`feat(scope): …`), end with the `Co-Authored-By` + `Claude-Session`
  trailers used by existing commits. Never put the model id in commits/PRs/code.

## Current status & gotchas (handoff)

Phases 1–7 ✅; Phase 8 nearly done (see DESIGN §5b). Everything compiles + unit-tests green (13/13
GameTests, standalone **and** with Create), and the player has verified the big systems in-game:
generators + filter cycle, JEI/EMI/Jade (incl. the weaving/generator-fuel **recipe categories**),
air-bar HUD, toxic rain, commands, weaver work-face, and the **volumetric fog** (screen-space
raymarch; near-field 3D exposure volume mirrors the damage scanner via `RegionOpenness`, so fog
floods breaches/caves/overhangs and skips sealed rooms + water). A structural pass then dedup'd the
machine layer into the shared bases above and made the weave table **datapack-driven**
(`toxicsurface:weaving`; a GameTest proves the JSONs drive the machine end-to-end).

- **Textures/models ✅** — full coverage: every registered block/item has blockstate/model/texture
  (verified by cross-reference), plus armor layers, particles, fluid + atlas entries.
- The `cough` sound is still a placeholder (vanilla drown-hurt) until a real `cough.ogg` (end of list).
- **Known gaps:** enclosure cache invalidates on break/place/explosion but not piston/`/setblock`;
  fog under overhangs can lag a rebuild cycle (~1–2 s) behind — tune `VOL_COLUMNS_PER_FRAME` /
  `VOL_REBUILD_MOVE_THRESHOLD` in `ToxicGasFogRenderer` if it bothers.

Remaining: `cough.ogg`, balance pass, real-pack compat testing.
