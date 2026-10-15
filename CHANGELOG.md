# Changelog

## 2.1.0

Generalized humanoid retargeting and special-state rendering reliability update.

### Added

- Added shared framework-neutral humanoid rig, semantic, skeleton, topology, body-metric, and skin-weight processing for supported source-model backends.
- Added conservative automatic humanoid preparation for supported Minecraft `HierarchicalModel`/`ModelPart`, Lionfish/Citadel-style `AdvancedEntityModel`, and AzureLib model layouts in addition to the existing vanilla `HumanoidModel` and GeckoLib paths.
- Added target-proportioned runtime Epic Fight humanoid armatures derived from each accepted source skeleton.
- Added structural shoulder-carrier support for layouts where a shoulder/clavicle node sits directly between the body and upper arm.
- Added officially tested compatibility targets for supported humanoid mobs from Iron's Spells 'n Spellbooks and L_Ender's Cataclysm. This does not imply support for every mob or custom render path in either mod.
- Added shared semantic resolution for Gecko body, attachment, and post-render anchor handling.
- Added full-model Lionfish layer replay through the already-prepared skinned body mesh when the layer redraws the exact same parent model without changing part visibility.

### Changed

- Improved automatic humanoid compatibility while keeping conservative fail-closed acceptance for ambiguous, unsafe, or unbakeable models.
- Improved GeckoLib armor rendering with target-aware bind transforms, visibility/model-state-aware cache keys, source-bone identity separation, and non-sticky transient conversion failures.
- Improved GeckoLib attachment and secondary-part retargeting so body, layer, and post-render paths share the same semantic rig resolution.
- Improved hybrid GeckoLib/vanilla renderer compatibility for runtime delegates such as ordinary `HumanoidModel` renderers.
- Runtime humanoid armatures are now refreshed from the latest prepared skeleton when renderer/model discovery is repeated instead of keeping a stale earlier armature.
- Runtime armature generation now fails closed instead of pairing a target-weighted mesh with the canonical BIPED armature when target armature creation fails.
- Lionfish full-parent-model secondary passes now preserve the same per-vertex one-bone limb deformation as the prepared body mesh instead of reducing the entire source limb to one rigid dominant joint.
- Portable humanoid rig/skeleton code no longer depends on Minecraft `ResourceLocation`; CI now rejects platform/framework imports from those core packages.
- Epic Fight runtime armature construction was moved out of the portable retargeting package into the runtime Epic Fight compatibility layer.
- Existing Epic Fight or third-party entity patches continue to take priority over EFPI automatic compatibility.

### Fixed

- Fixed L_Ender's Cataclysm Deepling-family arm/leg secondary geometry appearing offset from the correctly retargeted limbs during full-model secondary/emissive layer rendering.
- Fixed stale Gecko armor meshes when visibility or prepared model transforms change while the same baked model instance is reused.
- Fixed potential Gecko armor cache collisions between different source bones with the same name.
- Fixed hybrid Gecko fallback renderers being reused across different entity types that happen to share one `HumanoidModel` instance.
- Fixed Gecko post-render attachment anchoring depending on a literal `torso` source-bone name.

### Compatibility / Requirements

- Minecraft 1.20.1
- Forge 47.4.10 through the 47.x series
- Epic Fight 20.14.17 through compatible 20.14.x releases
- Combat Evolution 2.2.8 through compatible 2.3.x releases; development build target remains 2.3.1
- GeckoLib remains optional; the development compatibility target is GeckoLib 4.8.4
- Iron's Spells 'n Spellbooks and L_Ender's Cataclysm remain optional third-party compatibility targets and are not hard dependencies

No posture balance changes are included in 2.1.0.

## 2.0.0

Major automatic renderer compatibility update based on the public 1.1.0 release.

### Added

- Added automatic compatibility for living entities rendered with Minecraft `HumanoidModel`.
- Added optional automatic compatibility for supported GeckoLib 4.x humanoid entity models.
- Added automatic compatibility for unpatched entities rendered with Minecraft `SpiderModel`.
- Added lightweight Epic Fight BIPED and SPIDER patches that preserve the source mob's normal AI, attacks, movement, targeting, and ordinary renderer.
- Added semantic GeckoLib humanoid bone detection instead of per-entity hardcoding.
- Added conservative Gecko humanoid topology validation using hierarchy distance, left/right depth symmetry, independent major branches, and renderable major-limb geometry.
- Added runtime Gecko model conversion to Epic Fight `SkinnedMesh` for posture-break and execution-related special rendering.
- Added guarded limb splitting for suitable single-bone Gecko arms and legs so Epic Fight elbow and knee joints can animate them.
- Added Gecko attachment bridging for supported per-bone item, armor, equipment, decoration, and selected post-render attachment paths during special states.
- Added spider-specific neutralized and execution-victim animation support for standard Epic Fight spider armatures.
- Added dedicated-server renderer discovery using trusted operator-client reports.
- Added persistent dedicated-server discovery state with environment fingerprints and conflict handling.
- Added separate renderer classifications for vanilla humanoids, GeckoLib humanoids, and vanilla `SpiderModel` targets.
- Added server configuration switches for all three automatic compatibility systems:
  - `compatibility.enable_vanilla_humanoid_compatibility`
  - `compatibility.enable_geckolib_humanoid_compatibility`
  - `compatibility.enable_vanilla_spider_compatibility`
- Added explicit discovery request/upload packets for dedicated-server compatibility discovery.
- Added server-settings synchronization so compatibility switches and the entity blacklist remain server-authoritative in multiplayer.

### Changed

- Existing Epic Fight or third-party entity patches now take priority over EFPI automatic compatibility patches and are excluded from automatic renderer discovery.
- Epic Fight special rendering is used only while EFPI requires a posture-related special state; normal rendering remains owned by the source mod.
- Automatic compatibility is intentionally conservative: unsupported, ambiguous, highly custom, conflicting, or unbakeable renderer/model layouts are skipped rather than guessed.
- Gecko and vanilla humanoid automatic compatibility now fail closed if the special-state mesh cannot be prepared safely.
- Dedicated-server discovery requests reuse the client snapshot already validated during renderer setup instead of rebaking Gecko meshes on login.
- EFPI server policy is now stored globally at `config/epicfight_posture_integration/server.toml` instead of Forge's per-world `serverconfig` location.
- The dedicated-server generated renderer cache is stored separately at `config/epicfight_posture_integration/cache/renderer-discovery.json` so it is not confused with editable configuration.
- Client renderer discovery is now validated fresh on each launch and kept in memory only; the previous `humanoid-detected.json` client cache is no longer created or used.
- Old pre-release discovery cache locations are migrated or removed automatically where applicable.
- Network packet directions are explicitly restricted to their intended side.
- Network protocol version increased from `1` in 1.1.0 to `2` because 2.0.0 adds renderer-discovery and server-settings synchronization packets and changes the client/server packet contract. Client and server should use the same EFPI version.
- The new 2.0.0 dedicated renderer-discovery cache format starts at schema version `1`.
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
