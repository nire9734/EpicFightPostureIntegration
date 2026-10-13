# Changelog

## 2.0.0

Major automatic-humanoid compatibility update.

### Added

- Added automatic compatibility for living entities rendered with Minecraft `HumanoidModel`.
- Added optional automatic compatibility for supported GeckoLib 4.x humanoid entity models.
- Added semantic GeckoLib humanoid bone detection instead of per-entity hardcoding.
- Added runtime Gecko model conversion to Epic Fight `SkinnedMesh` for posture-break and execution-related special rendering.
- Added guarded Epic Fight limb splitting for suitable single-bone Gecko arms and legs so BIPED elbow and knee animations can work.
- Added Gecko attachment bridging for supported per-bone item, armor, equipment, and decoration layers during special states.
- Added support for selected Gecko post-render attachment paths that read model bones directly, including the Iron's Spells 'n Spellbooks humanoid cape path.
- Added dedicated-server renderer discovery using trusted operator-client reports.
- Added persistent dedicated-server discovery state with active, pending, report, and conflict tracking.
- Added environment fingerprints to automatic Humanoid discovery caches so stale results are discarded after relevant mod/version changes.
- Added separate discovery classification for Minecraft `HumanoidModel` and GeckoLib humanoid targets.
- Added server configuration switches for the two automatic compatibility systems:
  - `compatibility.enable_vanilla_humanoid_compatibility`
  - `compatibility.enable_geckolib_humanoid_compatibility`
- Both automatic compatibility switches default to `true`.

### Changed

- Automatic compatibility now uses a lightweight `LivingEntityPatch` that preserves the source mob's AI, attacks, movement, and ordinary renderer.
- Epic Fight special rendering is used only while EFPI requires a posture-related special state.
- Existing Epic Fight or third-party compatibility patches take priority over EFPI's automatic lightweight patch.
- Vanilla and Gecko automatic-compatibility targets are now tracked separately throughout client cache, dedicated-server discovery, and runtime registration.
- Client discovery cache `humanoid-detected.json` now uses schema version 2 and stores an environment fingerprint.
- Dedicated-server discovery cache `humanoid-server-discovery.json` now uses schema version 2 and stores vanilla and Gecko targets separately.
- Legacy automatic-Humanoid discovery caches are intentionally ignored and rebuilt instead of being trusted after the schema change.
- Dedicated-server discovery reports now treat disagreement between vanilla-humanoid, Gecko-humanoid, and non-humanoid classifications as a conflict.
- Network protocol version increased from 2 to 3 because the discovery packet format changed.
- Packet directions are now explicitly restricted:
  - posture synchronization: server to client;
  - discovery request: server to client;
  - discovery upload: client to server.
- Client and server should run the same EFPI 2.0.0 version.
- Updated mod metadata and documentation for the new automatic compatibility architecture.
- Simplified GitHub Actions CI to build the project and upload the generated JAR. Removed the Forge development-runtime dedicated-server smoke test that produced unrelated false failures.

### Fixed

- Fixed Gecko special-state base rendering that previously produced missing or transparency-only output.
- Fixed Gecko one-bone limb conversion so supported models can bend correctly at Epic Fight elbow and knee joints.
- Fixed missing Gecko equipment and clothing layers during special rendering.
- Fixed attachment transforms that were offset or spread away from the body by using Epic Fight pose matrices with the correct inverse-bind transform.
- Fixed post-render Gecko attachments that could remain in world/model bind space instead of following the animated body.
- Fixed the Iron's Spells 'n Spellbooks humanoid cape path so it follows the Epic Fight chest pose without manual positional offsets.
- Fixed stale client renderer-discovery data being reused after the loaded mod environment changes.

### Tested Compatibility

Development testing for the new compatibility layer includes:

- vanilla-style and modded Minecraft `HumanoidModel` entities;
- Iron's Spells 'n Spellbooks humanoid GeckoLib mobs, including body animation, equipment/attachments, and its humanoid cape rendering path; and
- additional GeckoLib humanoid models used during development.

Automatic compatibility is intentionally conservative. Unusual renderer hierarchies, dynamic model resources, custom full-model render layers, or non-humanoid Geo models may be rejected or may require specific compatibility work.

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
