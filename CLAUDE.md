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
- **Registries** in `registry/` (`Mod*`), registered in `ToxicSurface` ctor. Lang in
  `assets/toxicsurface/lang/en_us.json`; keep it valid JSON.
- **Style:** Palantir Java Format (4-space, 120 col), LGPL SPDX header on every file, comments at the
  surrounding density (explain *why*). `@EventBusSubscriber(bus=…)` deprecation warnings are
  pre-existing repo-wide — match the existing idiom, don't churn them.

## Commit / PR

- Develop on `claude/phase7-create-integration-ez5bsp`; `git push -u origin <branch>` (retry w/ backoff
  on network errors). Do **not** open a PR unless asked.
- Commit messages: conventional style (`feat(scope): …`), end with the `Co-Authored-By` + `Claude-Session`
  trailers used by existing commits. Never put the model id in commits/PRs/code.

## Current status & gotchas (handoff)

Phases 1–7 ✅; Phase 8 in progress (see DESIGN §5b). This session added: the two toxic generators +
industrial-filter cycle, JEI/EMI/Jade integration, air-bar HUD, enclosure-cache wiring, toxic
DamageType, cough+HUD-flash, toxic-rain overlay, cleanser bubble particles, accessibility sliders,
pre-toxicity telegraph + retroactive advancement. All **compile + unit-test green**, but:

- **Not yet run in-game / GameTest in this env** — rendering, particles, the config screen, packets,
  and Create runtime are compile-verified only. Local `runClient` testing is the next step.
- **No textures/models** — everything is missing-texture; this also gates the deferred JEI/EMI
  **recipe categories**. The `cough` sound is a placeholder (vanilla choke) until a real `cough.ogg`.
- **Known gap:** enclosure cache invalidates on break/place/explosion events but not piston/`/setblock`.

Remaining: textures/models, JEI/EMI recipe categories, balance pass, real-pack compat testing.
