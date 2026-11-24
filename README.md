# Epic Fight Posture Integration

Adds a server-authoritative Posture system to Epic Fight mobs: attacks and parries reduce Posture, an empty bar causes a guard break, and Combat Evolution can then execute the mob.

This README is for developers. Player documentation lives on the distribution page; release notes are in [CHANGELOG.md](CHANGELOG.md).

## Editions

| | Forge | NeoForge |
| --- | --- | --- |
| Minecraft | 1.20.1 | 1.21.1 |
| Loader | Forge 47.4.10+ (47.x) | NeoForge 21.1.219+ (21.1.x) |
| Epic Fight | 20.14.17–20.14.x, 20.15.1–20.15.x | 21.17.3.1–21.17.x |
| Combat Evolution | 2.2.8–2.3.x | 2.3.2–2.3.x |
| Optional | GeckoLib 4.8.4, Lionfish API 3.0 | GeckoLib 4.9.3, Lionfish API 3.1 |
| JDK | 17 | 21 |
| Project | `forge-1.20.1/` | `neoforge-1.21.1/` |

Epic Fight 20.15.0 is excluded on Forge because it lacks a required API. The compile-time dependency pins are in each project's `gradle.properties` and `build.gradle`.

## Repository layout

| Path | Contents |
| --- | --- |
| `common/src/main/java` | Loader-independent code: Posture math and state, config policy, renderer discovery, rig analysis and skinning |
| `common/src/test` | Tests for the shared code, plus rig fixtures in `resources/rig-fixtures/` |
| `forge-1.20.1/` | Forge adapter: events, networking, mixins, renderers, config |
| `neoforge-1.21.1/` | NeoForge adapter, same responsibilities |
| `tools/` | Python script that compares Forge and NeoForge renderer reports (tested in CI) |
| `.github/workflows/` | CI: both builds with tests, plus a Windows Gradle bootstrap check |

**Shared sources.** Both projects compile `common/` directly; there is no separate common artifact.

- Forge adds all of `common/src/main/java` and `common/src/test/java`.
- NeoForge lists each shared file with `include` in `neoforge-1.21.1/build.gradle`. **When you add a common file or test, add it there too**, or NeoForge will not compile or run it.

Keep game-independent logic in `common/` so both loaders behave the same and it can be unit-tested. Loader-specific API calls stay in the adapters.

## Building and testing

```sh
cd forge-1.20.1 && ./gradlew clean build      # Java 17, Gradle 8.8 wrapper
cd neoforge-1.21.1 && ./gradlew clean build   # Java 21, Gradle 9.2.1 bootstrap
```

The wrappers download and verify their pinned Gradle versions; on Windows use `gradlew.bat`. `build` runs the tests, and JARs are written to each project's `build/libs/`.

CI (`.github/workflows/build-neoforge-port.yml`) runs on pull requests, on pushes to `main`, and on pushes to release branches named after a version (for example `3.3.0`).

## Architecture

### Posture (server)

| Piece | Location |
| --- | --- |
| Flow: damage, guard breaks, executions, recovery, tick and sync rules | `PostureEngine` |
| Per-entity state `NORMAL → BROKEN → EXECUTING → RECOVERING` | `PostureRuntimeState` |
| Loader operations the engine needs (Epic Fight, Combat Evolution, config, sync) | `PostureHost` (+ `PostureSettings`) |
| Balance math | `PostureBalance` |
| Attribute inputs and maximum Posture | `PostureInputs` |
| Fixed constants | `PostureTuning` |
| Defense policy (normal guard, projectile multiplier) | `GuardPosturePolicy` |
| Loader adapters: event wiring, damage detection, `PostureHost` implementation | `PostureManager` (Forge), `NeoForgePostureServerEvents` (NeoForge) |

- **One engine:** both loaders run the same `PostureEngine`; only damage detection differs (see below). Change Posture behaviour there, and cover it with `PostureEngineTest`, which drives the flow through a fake host.
- **Attacks** add Posture damage from the HP actually lost, scaled by Epic Fight Impact. On Forge the HP loss is measured at the next entity tick; on NeoForge from `LivingDamageEvent.Post`.
- **Parries** and paid **normal guards** are detected by mixins on Epic Fight's `TakeDamageEvent` and `GuardSkill`/`ParryingSkill`. Their damage is a percentage of max Posture, based on the mob type's base EHP.
- **Guard breaks** apply Epic Fight's `NEUTRALIZE` stun; only a confirmed break counts toward guard break resistance.
- **States** are kept per entity in weak maps. A state that has fully regenerated and was never guard broken is dropped after one final sync.

### Execution range

Combat Evolution uses fixed ranges (4.0 blocks to execute, 2.0 to assassinate). Mixins on `ExecutionHandler` (server) and `CEKeyHandler` / `ExecutionHUD` (client) pass them through `ExecutionRange`:

- **Weapon factor:** the held weapon's Epic Fight collider reach (`|center| + longest half extent`, read from `Collider.serialize`) plus an arm reach of 0.75, relative to the one-handed sword. A sword keeps Combat Evolution's ranges exactly; the factor is clamped to 0.75–1.5. Weapons without a readable collider keep the default.
- **Large targets:** Combat Evolution measures to the target's center, so mobs wider than a player get up to 3 blocks extra (`width / 2 - 0.3`).
- **Switch and multiplier:** `execution_range_scaling_enabled` turns the feature on or off (off = Combat Evolution's fixed ranges). `execution_range_multiplier` (0.5–2.0) scales both ranges; below 0.5 bodies collide before an assassination range is reached, and above 2.0 Combat Evolution's teleport to the execution spot gets too long (it does not check line of sight). The effective value is synchronized to clients so prompts match the server; until it arrives, clients use Combat Evolution's ranges.
- **Disabling executions:** use Combat Evolution's own `enabledExecution` / `enabledAssassination` and `executionItemBlacklist`; this mod only changes ranges.
- All injections use `require = 0`: if Combat Evolution changes these calls, its own ranges remain.

### Crash safety

- Every special-state renderer runs through `SpecialRenderGuard`: a failure is logged once, the PoseStack is restored, and that entity type renders normally (`SpecialRenderFailures`) until renderers are rebuilt.
- Event handlers and mixin hooks run through `HookGuard`, which logs each hook's first failure and skips it. Fatal JVM errors are never caught.
- Wrap any new render path or hook the same way: Epic Fight and Minecraft call them without catching exceptions.

### Networking

- **Protocol identifier:** `NetworkProtocol.VERSION` (currently `mod 3.3.0 or later`) is the channel version on both loaders. NeoForge prints it on its mismatch screen; Forge's screen shows mod versions instead. **Change it only when the wire format changes**, to the first release with the new format.
- **Server → client:** Posture state, server settings (blacklist, automatic compatibility, mod filters), server mod version, renderer scan request.
- **Client → server:** renderer scan report chunks, rescan request after a resource reload.
- **Version notice:** the client compares the server's mod version with its own and, if they differ, shows a translated chat line (`epicfight_posture_integration.server_version_mismatch`).

### Automatic compatibility

- **Model discovery:** vanilla `HumanoidModel`, `SpiderModel`, ModelPart, GeckoLib and Lionfish models are inspected on the client. Mobs that pass get a lightweight Epic Fight patch and a special-state renderer. Patches supplied by Epic Fight or other mods always take priority.
- **Dedicated servers:** a server cannot inspect models, so it asks an operator's client for a renderer report.
  - The report is tied to a server-issued nonce, sent in chunks below the 32767-byte serverbound limit, and validated before use (`RendererDiscoveryUpload`, `RendererDiscoveryShared`).
  - Approved results persist in `config/epicfight_posture_integration/cache/renderer-discovery.json` (`RendererDiscoveryStore`).
  - Conflicting reports from different operators disable the affected mob types.
- **Exclusions:** `entities.entity_blacklist`, the mod allow/deny lists, and multipart-entity safety checks (`AutomaticPatchSafety`) run before any patch is attached.

### Diagnostics

| File (in `config/epicfight_posture_integration/cache/`) | Contents |
| --- | --- |
| `forge-diagnostics.tsv` / `neoforge-diagnostics.tsv` | Per entity type: renderer discovery, Posture eligibility and rejection reason, observed guard breaks and executions, installed mod versions. Rewritten each server session. |
| `forge-renderer-coverage.tsv` / `neoforge-renderer-coverage.tsv` | Final renderer registrations on the client. |

Neither file affects gameplay. For Posture outcomes on a dedicated server, read the server's report.

### Rig regression fixtures

Shared rig tests replay the `.rig.tsv` files in `common/src/test/resources/rig-fixtures/`. They cover accepted humanoids and unsafe structures that must stay rejected.

To capture a new model, start the client with `-Defpi.rigFixtureDirectory=<folder>`. Every GeckoLib, Lionfish or ModelPart rig prepared at runtime is then exported there. Review a capture before adding it as a fixture with its expected result.

## Configuration reference

**Server** – `config/epicfight_posture_integration/server.toml`

| Key | Default | Purpose |
| --- | --- | --- |
| `posture.attack_posture_damage_multiplier` | 1.0 | Posture damage from attacks |
| `posture.parry_posture_damage_multiplier` | 1.0 | Posture damage from parries |
| `posture.guard_posture_damage_enabled` | false | Normal guards damage Posture (Guard: only weapons that cannot parry; Impact Guard: all weapons) |
| `posture.guard_posture_damage_multiplier` | 0.25 | Normal guard damage relative to a parry |
| `posture.projectile_defense_posture_multiplier` | 0.0 | Parry/guard damage against projectiles, relative to melee |
| `posture.execution_range_scaling_enabled` | true | Scale execution/assassination range by weapon reach (false = Combat Evolution's ranges) |
| `posture.execution_range_multiplier` | 1.0 | Execution/assassination range multiplier, 0.5–2.0 |
| `posture.parry_base_max_percent` | 22.0 | Parry damage against weak mobs (% of max Posture) |
| `posture.parry_base_min_percent` | 10.0 | Parry damage against strong mobs (% of max Posture) |
| `posture.posture_break_resistance_step` | 0.5 | Extra resistance per guard break |
| `posture.posture_break_resistance_max` | 2.0 | Resistance cap |
| `regeneration.delay_seconds` | 15.0 | Delay before regeneration |
| `regeneration.percent_per_second` | 2.0 | Regeneration rate (% of max Posture) |
| `entities.attack_posture_damage_multipliers` | `[]` | Per-mob attack multipliers, `"modid:mob=x"` |
| `entities.parry_posture_damage_multipliers` | `[]` | Per-mob parry multipliers |
| `entities.entity_blacklist` | `[]` | Mob types excluded from Posture and automatic patches |
| `compatibility.automatic_compatibility` | true | Automatic support for compatible mobs |
| `compatibility.mod_allowlist` | `[]` | Only these mods get automatic support (`[]` = all) |
| `compatibility.mod_denylist` | `[]` | These mods never get automatic support |

The TOML comments come from `PostureConfigDescriptions`, shared by both loaders.

**Client** – `config/epicfight_posture_integration/client.toml`: `hud.mob_health_bar_visibility` (`ALWAYS`, `DAMAGED_ONLY`, `HIDDEN`) and `sound.execution_sound_volume_multiplier`. Both can also be changed from the in-game config screen.

## Releasing

1. Set `mod_version` in both `forge-1.20.1/gradle.properties` and `neoforge-1.21.1/gradle.properties`.
2. If the wire format changed, update `NetworkProtocol.VERSION`.
3. Add the release to `CHANGELOG.md` and update the editions table if dependency ranges changed.
4. Make sure CI is green for both loaders.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
