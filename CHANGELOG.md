# Changelog

## 3.0.1

### Added
- Per-mob repeat-break resistance, configured by `posture.posture_break_resistance_step = 1.0` and `posture.posture_break_resistance_max = 3.0`. Successive breaks require 1x, 2x, and 3x posture damage (both normal attacks and parries); the counter resets on unload.
- Two configurable parry **base** percentages: `posture.parry_base_max_percent = 18.0` for innate EHP ≤20, and `posture.parry_base_min_percent = 6.0` as innate EHP approaches infinity (before the HP-finisher penalty).

### Changed
- Normal player hits now deplete posture from final actual HP damage as a fraction of maximum HP, using an integrated quadratic health-finisher curve and bounded Impact efficiency.
- Retain EHP^0.7 maximum posture and its global/per-entity multipliers; using unmultipled base posture for attack pressure keeps increased maximum posture meaningful.
- Use *innate/base-attribute EHP* to determine parry resistance and the parry finisher penalty, so leveled low-base-health mobs do not become parry-resistant bosses. Clamp parry-scaling EHP to at least 20, enforcing the low-EHP endpoint even for weaker mobs.
- Apply attack finisher progression to all mobs; parries retain their base-EHP dependent finisher strength.
- Attack posture now uses internal **2.1** HP-normalized pressure (previous 3.0.1 development builds: 2.7, then 2.2; pre-3.0.1 damage-based formula used 0.55). `posture_damage_multiplier = 1.0` remains the default; previously customized settings such as 0.8 should be reset or retuned.
- Remove obsolete `posture.parry_percent`, interim `posture.parry_low_ehp_percent` / `posture.parry_high_ehp_percent`, and `entities.parry_multipliers`. No migration of old parry customizations: existing TOML gains the new default keys via Forge config correction, while unrelated options remain unchanged.
- Updated unit tests, configuration explanations, and version number to 3.0.1.

### Fixed
- Prevent immediate subsequent posture breaks during the existing `RECOVERING` state, when the bar is still refilling.
- Fix Forge 1.20.1 compilation: `EventPriority.MONITOR` does not exist. Listen at `LOWEST` and defer attack posture until actual HP reduction is observed. This prevents false posture damage if a later same-priority KubeJS damage-cap handler nullifies the hit. Added a regression test for capped/cancelled HP loss.


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
