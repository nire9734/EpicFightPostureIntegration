# Changelog

## 3.0.0

Major automatic-compatibility and posture-balance update.

### Added

- Extended humanoid compatibility for supported ModelPart, GeckoLib, Lionfish/Citadel-style, and AzureLib-backed humanoid layouts.
- Target-proportioned humanoid retargeting and improved special-state secondary rendering.
- Per-entity maximum-posture overrides via `posture.overrides.entity_multipliers`.
- Mod-level automatic-compatibility filtering with `DENYLIST` / `ALLOWLIST`, `mod_denylist`, and `mod_allowlist`.
- EHP-aware health/finisher posture scaling, constant delayed regeneration, and dealt-damage scaling for direct attacks.

### Changed

- Maximum posture remains EHP-based: `3.2 * (EHP / 20)^0.7` by default.
- Direct attack posture pressure scales with final dealt damage using `(damage / 8)^0.7` by default.
- Default parry baseline is 8%, with effective parry percentage increased automatically for lower-EHP targets: `base * (1 + (20 / EHP)^0.7)`.
- Removed the old absolute minimum parry posture damage.
- Direct hits and parries use the same EHP-aware health finisher modifier.
- Ordinary regeneration waits 10 seconds and then restores 2% of maximum posture per second.
- Generic prepared-humanoid terminology now uses `extended humanoid` instead of GeckoLib-specific legacy naming.
- Network protocol is now `3`; older protocol-2 builds are rejected.
- Dedicated-server renderer-discovery cache schema is now `2` and older layouts are not migrated.
- 3.0.0 does not retain config/cache migration shims from 2.0.0.

### Fixed

- Improved compatibility with existing Epic Fight/third-party patches, including Guard Villagers x Epic Fight.
- Fixed Illager crossed-arm special-state geometry handling.
- Fixed parry detection ordering by detecting the successful parried transition directly.
- Fixed multiple GeckoLib/Lionfish attachment, armor, layer, cache, and Deepling-family deformation issues.

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
