# Epic Fight Posture Integration 2.1.0

Version 2.1.0 focuses on safer, more general automatic humanoid compatibility and on keeping framework-owned secondary rendering aligned with Epic Fight's special-state pose.

Automatic compatibility remains conservative. EFPI does not claim universal humanoid support: models or render paths that cannot be classified and prepared safely are skipped instead of being forced through the BIPED path.

## Highlights

- Improved automatic humanoid compatibility across supported source-model backends.
- Iron's Spells 'n Spellbooks is now an officially tested compatibility target for supported humanoid mobs.
- L_Ender's Cataclysm is now an officially tested compatibility target for supported humanoid mobs.
- Improved GeckoLib armor rendering, including bind-space handling, cache correctness, and visibility/model-variant handling.
- Improved attachment and secondary-part retargeting so supported body, layer, and post-render paths share consistent semantic ownership.
- Improved runtime humanoid armature refresh behavior when renderer/model discovery is repeated.
- Improved hybrid GeckoLib/vanilla renderer compatibility for entities that delegate to ordinary Minecraft humanoid renderers at runtime.
- Added structural shoulder-carrier support for compatible `body -> shoulder/clavicle -> arm` hierarchies.
- Fixed L_Ender's Cataclysm Deepling-family arm/leg secondary geometry appearing offset from the correctly retargeted limbs during full-model secondary/emissive layer rendering.
- Full-parent-model Lionfish secondary passes can now reuse the already-prepared skinned body mesh, preserving the same per-vertex one-bone limb deformation as the body.
- Gecko post-render attachment anchoring now uses the shared semantic CHEST binding instead of relying on a literal source-bone name.
- Runtime target-proportioned armature generation now fails closed if it cannot be prepared safely instead of falling back to a mismatched canonical BIPED armature.
- Existing Epic Fight or third-party entity patches continue to take priority over EFPI automatic compatibility.

## Tested compatibility targets

The following are officially tested compatibility targets for **supported humanoid mobs** in 2.1.0:

- Iron's Spells 'n Spellbooks
- L_Ender's Cataclysm

This wording is intentionally limited. It does **not** mean that every mob, renderer, armor implementation, or custom layer in either mod is supported.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer within the 47.x series
- Epic Fight 20.14.17 or another compatible 20.14.x release
- Combat Evolution 2.2.8 through the compatible 2.3.x series

The development build uses Epic Fight 20.14.17, Combat Evolution 2.3.1, and Forge 47.4.10. GeckoLib is optional; Gecko compatibility is developed against GeckoLib 4.8.4.

Iron's Spells 'n Spellbooks and L_Ender's Cataclysm are optional compatibility targets and are **not** required dependencies of EFPI.

## Compatibility behavior

- EFPI keeps the source mob's normal AI and ordinary rendering outside posture-related special states.
- Automatic compatibility remains fail-closed for ambiguous, unsupported, conflicting, or unbakeable models.
- Existing Epic Fight patches supplied by Epic Fight or another compatibility addon take priority and are not replaced by EFPI.
- No posture balance changes are included in 2.1.0.

For configuration, dedicated-server renderer discovery, multiplayer behavior, and known limitations, see the project README.
