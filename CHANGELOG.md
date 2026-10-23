# Changelog

## 3.0.2 (unreleased)

Configuration and damage-scaling update relative to **3.0.1**.

- Rewrote generated `server.toml` option descriptions in plain English, with practical examples for multipliers, parry damage, regeneration, repeated guard breaks, mob overrides and compatibility filters.
- Removed EHP, mathematical limits and internal damage coefficients from user-facing configuration comments.
- Ordinary regeneration now explicitly counts from the last **actual Posture reduction**, including parries without HP damage; zero-Posture-damage events no longer extend the delay.
- Removed configurable maximum-posture multipliers; maximum Posture is always calculated with multiplier 1.0.
- Added independent attack/parry Posture damage multipliers, with global and per-entity options. Replaced the old `posture.posture_damage_multiplier` and `entities.posture_multipliers` keys; default balance is unchanged.
- No automatic migration of old multiplier values. Back up custom values and regenerate `server.toml` to see the new keys and descriptions.

## 3.0.1

Posture balance, execution compatibility and stability update relative to the **3.0.0 release**.

### Posture balance

- Reworked ordinary-attack posture damage from the 3.0.0 dealt-damage formula to **actual final HP loss as a fraction of maximum HP**, with a linear internal coefficient of **0.54** when `posture.posture_damage_multiplier=1.0`. Removed the previous current-HP finisher curve from attacks and parries.
- Added a bounded Impact efficiency for direct attacks: `clamp(0.7 + 0.3 * sqrt(Impact / 3), 0.7, 1.5)`. Nonpositive Impact from Epic Fight does not create attack posture damage; eligible non-Epic-Fight direct hits use a bounded fallback.
- Reworked parry scaling around **EntityType registered default maximum HP, armor and toughness** to reduce sensitivity to instance-level changes from mods that scale Mob stats. Fall back to instance base attributes only when registered defaults are unavailable.
- Replaced 3.0.0's `posture.parry_percent` with `posture.parry_base_max_percent=22.0` at innate EHP 20 or below and `posture.parry_base_min_percent=8.0` as innate EHP increases; the fraction is independent of current HP and uses the 0.7 EHP exponent.
- Added resistance to repeated successful posture breaks, shared by attacks and parries. Defaults `posture_break_resistance_step=0.5` and `posture_break_resistance_max=2.0` give 1×, 1.5× and 2× required posture damage across successive breaks, with no hard limit on break count.
- Preserved EHP-based maximum posture and per-entity/global multipliers. Direct attacks use unmultiplied base posture, while parries scale with current maximum posture.
- Increased the **ordinary regeneration delay from 10 to 15 seconds**, keeping the 2%-per-second regeneration rate and separate 3-second post-break recovery. Combat Evolution still controls execution damage.

### Guard breaks and executions

- Require a distinct active Epic Fight `NEUTRALIZE` animation for executable posture breaks rather than accepting a `LONG` hit reaction. Recover from temporarily failed or interrupted stuns without permanently blacklisting the entity.
- Add a generic missing-`NEUTRALIZE` fallback for supported datapack-backed `CustomHumanoidMobPatch` entities with a humanoid armature and existing SHORT/LONG reactions. Preserve original AI, animations and patch ownership; Remnant and Forlorn were verified in game.
- Align guard-break animation recognition with Combat Evolution and restrict the combined Health + Posture HUD to eligible, executable targets.

### Reliability and compatibility

- Prevent new posture breaks during guard-break, execution or recovery states, and avoid starting a guard-break animation from lethal damage. Preserve ongoing execution animations during death processing.
- Improve automatic humanoid discovery, animation retargeting, renderer ownership, secondary model layers and reload stability, while skipping unsupported or ambiguous models safely.
- Reduce unnecessary client instrumentation and development diagnostics; retain warnings for real compatibility failures. Expand automated tests for balance, level-scaling fallback, guard-break behavior and rendering helpers.

### Updating from 3.0.0

- Keep the existing `server.toml` and renderer-discovery cache. Network protocol `3`, cache schema `2` and configuration paths are unchanged.
- The old `posture.parry_percent` value is **not migrated** to the new 22% / 8% parry settings. Review customized attack posture multipliers because the underlying formula changed.
- New repeated-break settings default to `0.5` and `2.0`. The default regeneration delay is `15.0`, but a saved 3.0.0 value of `10.0` remains until the user edits or regenerates the configuration.

## 3.0.0

Major automatic-compatibility and posture-balance update.

### Added

- Extended humanoid compatibility for supported ModelPart, GeckoLib, and Lionfish/Citadel-style humanoid layouts.
- Target-proportioned humanoid retargeting and improved special-state secondary rendering.
- Per-entity maximum-posture overrides via `entities.posture_multipliers`.
- Compact automatic-compatibility controls with `automatic_compatibility`, `mod_allowlist`, and `mod_denylist`.
- EHP-aware health/finisher posture scaling, constant delayed regeneration, and dealt-damage scaling for direct attacks.

### Changed

- Maximum posture remains EHP-based: `3.2 * (EHP / 20)^0.7` by default.
- `posture.maximum_posture_multiplier` replaces the old global maximum-posture multiplier key.
- `posture.posture_damage_multiplier = 1.0` now represents EFPI's established attack-posture balance; the internal base coefficient remains `0.55`.
- Default parry baseline is 6%, with effective parry percentage increased automatically for lower-EHP targets: `base * (1 + (20 / EHP)^0.7)`.
- Lower-level balance formula constants are internal instead of being exposed as individual server config keys.
- A single `automatic_compatibility` switch replaces separate vanilla/extended/spider automatic-compatibility switches.
- An empty `mod_allowlist` imposes no restriction; a non-empty allowlist limits third-party automatic compatibility, while `mod_denylist` always takes priority.
- Removed the old absolute minimum parry posture damage.
- Direct hits and parries use the same EHP-aware health finisher modifier.
- Ordinary regeneration waits 10 seconds and then restores 2% of maximum posture per second by default.
- Generic prepared-humanoid terminology uses `extended humanoid` instead of GeckoLib-specific legacy naming.
- EFPI runtime-mesh ownership is marked directly in EFPI code instead of using self-targeting Mixins or StackWalker inspection.
- Portable humanoid dependency checks now cover `rig`, `retarget`, and `skeleton` core packages.
- Network protocol is `3`; incompatible older protocol builds are rejected.
- Dedicated-server renderer-discovery cache schema is `2` and older layouts are not migrated.
- 3.0.0 does not retain config/cache migration shims from 2.0.0 or intermediate development builds.

### Fixed

- Improved compatibility with existing Epic Fight/third-party patches, including Guard Villagers x Epic Fight.
- Fixed Illager crossed-arm special-state geometry handling.
- Fixed parry detection ordering by detecting the successful parried transition directly.
- Fixed multiple GeckoLib/Lionfish attachment, armor, layer, cache, and Deepling-family deformation issues.
- GeckoLib startup inspection now fails closed when a model requires a live entity instead of invoking entity-dependent model resolution unsafely.
- Reduced client render overhead from EFPI runtime meshes and repeated OpenGL debug callback logging on affected Epic Fight compute-shader configurations.

### Requirements

- Minecraft 1.20.1
- Forge 47.4.10 through 47.x
- Epic Fight 20.14.17 through compatible 20.14.x
- Combat Evolution 2.2.8 through compatible 2.3.x

## 2.0.0

Major automatic renderer compatibility update.

- Added automatic compatibility for Minecraft `HumanoidModel`, supported GeckoLib humanoids, and vanilla-style `SpiderModel` entities.
- Added lightweight EFPI-owned Epic Fight patches while preserving original mob AI and normal rendering.
- Added dedicated-server renderer discovery, cache, multiplayer policy synchronization, and protocol `2`.
- Added global server config at `config/epicfight_posture_integration/server.toml`.

## 1.1.0

- Added client-side Combat Evolution execution sound volume configuration.
- Added in-game config access.

## 1.0.0

Initial public release.

- Added posture, guard breaks, Health + Posture HUD integration, Combat Evolution execution support, and server-authoritative multiplayer synchronization.
