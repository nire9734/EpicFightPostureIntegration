# Changelog

## 3.0.1

Posture balance, execution compatibility and stability update relative to the **3.0.0 release**.

### Posture balance

- Reworked direct-hit posture damage to use **final observed HP loss** as a fraction of target maximum HP, instead of 3.0.0's `(dealt damage / 8)^0.7` scaling. Canceled or fully capped damage that removes no HP no longer generates posture pressure.
- Integrated the `0.25 + 0.75 * (missing HP fraction)^2` finisher curve over the damage interval. A single large hit and several small hits removing the same HP fraction produce similar pressure when Impact and other conditions match. The curve now applies to attacks against all eligible mobs.
- Added bounded Impact efficiency for direct hits. Epic Fight attacks use their Impact; eligible non-Epic-Fight player damage gets a bounded estimated Impact. Zero-Impact Epic Fight attacks do not contribute posture damage.
- Reworked parries around the target's **innate/base-attribute EHP**, rather than level/affix-inflated attributes. The old `posture.parry_percent` (default 6%) is replaced by `posture.parry_base_max_percent=18.0` at innate EHP ≤20 and `posture.parry_base_min_percent=6.0` approached at high innate EHP, before the EHP-dependent finisher modifier.
- Added per-target resistance to repeated successful guard breaks, shared by attacks and parries: first break 1× required pressure, second 2×, third and subsequent 3× with default `posture_break_resistance_step=1.0` and `posture_break_resistance_max=3.0`. The counter resets when the mob unloads.
- Kept EHP-based maximum posture `3.2 * (EHP / 20)^0.7`, per-entity/global maximum-posture multipliers, and ordinary delayed regeneration. Attack pressure uses the **unmultiplied** base posture, so increasing maximum posture increases attack difficulty; parry pressure scales with the actual maximum posture.

### Guard breaks and executions

- Require an actual distinct `NEUTRALIZE` animation for server-side posture eligibility and guard breaks; a `LONG` hit reaction is no longer treated as an executable guard break. Failed/briefly interrupted stuns recover without blacklisting the mob.
- Add a **generic** missing-`NEUTRALIZE` fallback to existing Epic Fight datapack-backed `CustomHumanoidMobPatch` instances that have a humanoid armature and SHORT/LONG reaction. Preserve user-defined animations, the original patch, attack AI and renderer. Remnant and Forlorn from Soul's Weapons were verified in-game through HUD, guard break and execution.
- Match Combat Evolution's active guard-break animation check without requiring an additional `isStunned()` value during animation transitions.
- Display the combined Health + Posture HUD only for eligible, server-synchronized posture targets that satisfy Combat Evolution's executor-dependent target support check. Avoid rechecking server-only stun tables on multiplayer clients.

### Reliability

- Prevent new posture breaks during the existing break/execution/recovery sequence.
- Improve automatic humanoid discovery and renderer handling across reloads, ambiguous model layouts, third-party renderers and secondary model layers. Keep conservative fail-closed behavior when safe compatibility cannot be established.
- Reduce repeated client renderer/debug instrumentation and ensure compatibility state is reconciled against validated renderer registrations.
- Remove development-only diagnostic output; retain warnings for actual compatibility failures.

### Updating from 3.0.0

- Keep the existing `server.toml` and renderer-discovery cache. Network protocol `3`, cache schema `2`, configuration paths and supported dependency ranges are unchanged.
- Forge adds the new parry base-percent and repeat-break-resistance settings at their defaults. **Customized 3.0.0 `posture.parry_percent` values are not migrated.** Review the updated formula before retuning.
- Because direct-hit posture damage has a new formula, existing customized `posture.posture_damage_multiplier` values may require adjustment; the default remains `1.0`.

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
