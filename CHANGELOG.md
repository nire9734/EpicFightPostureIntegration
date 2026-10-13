# Changelog

## 2.0.0

Major automatic renderer compatibility update based on the public 1.1.0 release.

### Added

- Added automatic compatibility for living entities rendered with Minecraft `HumanoidModel`.
- Added optional automatic compatibility for supported GeckoLib 4.x humanoid entity models.
- Added automatic compatibility for unpatched entities rendered with Minecraft `SpiderModel`.
- Added lightweight Epic Fight BIPED and SPIDER patches that preserve the source mob's normal AI, attacks, movement, targeting, and ordinary renderer.
- Added semantic GeckoLib humanoid bone detection instead of per-entity hardcoding.
- Added runtime Gecko model conversion to Epic Fight `SkinnedMesh` for posture-break and execution-related special rendering.
- Added guarded limb splitting for suitable single-bone Gecko arms and legs so Epic Fight elbow and knee joints can animate them.
- Added Gecko attachment bridging for supported per-bone item, armor, equipment, decoration, and selected post-render attachment paths during special states.
- Added spider-specific neutralized and execution-victim animation support for standard Epic Fight spider armatures.
- Added dedicated-server renderer discovery using trusted operator-client reports.
- Added persistent client and dedicated-server discovery caches with environment fingerprints so stale renderer classifications are discarded after relevant mod/version changes.
- Added separate renderer classifications for vanilla humanoids, GeckoLib humanoids, and vanilla `SpiderModel` targets, including conflict handling when client environments disagree.
- Added server configuration switches for all three automatic compatibility systems:
  - `compatibility.enable_vanilla_humanoid_compatibility`
  - `compatibility.enable_geckolib_humanoid_compatibility`
  - `compatibility.enable_vanilla_spider_compatibility`
- Added explicit discovery request/upload packets for dedicated-server compatibility discovery.

### Changed

- Existing Epic Fight or third-party entity patches now take priority over EFPI automatic compatibility patches.
- Epic Fight special rendering is used only while EFPI requires a posture-related special state; normal rendering remains owned by the source mod.
- Automatic compatibility is intentionally conservative: unsupported, ambiguous, highly custom, or conflicting renderer/model layouts are skipped rather than guessed.
- Network packet directions are explicitly restricted to their intended side.
- Network protocol version increased from `1` in 1.1.0 to `2` because 2.0.0 adds renderer-discovery packets and changes the client/server packet contract. Client and server should use the same EFPI version.
- The new 2.0.0 renderer-discovery cache format starts at schema version `1`.
- Updated build metadata and documentation for the new compatibility architecture.
- No posture balance changes from 1.1.0.

## 1.1.0

- Added a client-side Combat Evolution execution sound volume setting.
- Added an in-game config screen accessible from `Mods -> Epic Fight Posture Integration -> Config`.
- Execution sound volume can be adjusted from 0% to 100% independently for each player.
- Default execution sound volume multiplier is 70% of Combat Evolution's original volume.
- Updated Combat Evolution compatibility through the 2.3.x series and built against 2.3.1.
- No posture balance changes.

## 1.0.0

Initial public release.

- Added posture to compatible Epic Fight entities.
- Integrated posture breaks with Combat Evolution executions.
- Added posture damage from Epic Fight Impact and successful parries.
- Added support for compatible humanoid patches and `CustomExecuteEntity` implementations.
- Added automatic support for compatible modded entities.
- Added Health + Posture HUD integration.
- Added server-authoritative multiplayer synchronization.
- Added server configuration for posture scaling, minimum posture, armor scaling, and entity blacklist.
- Added shield-use interruption during posture break and execution start.
- Prevented duplicate posture handling for entities already managed by Combat Evolution.
- Optimized posture state creation, attribute recalculation, Epic Fight patch lookups, and network synchronization.
