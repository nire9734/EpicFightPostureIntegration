# Epic Fight Posture Integration 2.1.0

Version 2.1.0 focuses on safer, more general automatic humanoid compatibility, keeping framework-owned secondary rendering aligned with Epic Fight's special-state pose, and a substantial posture-balance pass.

Automatic compatibility remains conservative. EFPI does not claim universal humanoid support: models or render paths that cannot be classified and prepared safely are skipped instead of being forced through the BIPED path.

## Highlights

- Improved automatic humanoid compatibility across supported source-model backends.
- Iron's Spells 'n Spellbooks is now an officially tested compatibility target for supported humanoid mobs.
- L_Ender's Cataclysm is now an officially tested compatibility target for supported humanoid mobs.
- Improved GeckoLib armor rendering, including bind-space handling, cache correctness, and visibility/model-variant handling.
- Improved attachment and secondary-part retargeting so supported body, layer, and post-render paths share consistent semantic ownership.
- Added a shared secondary-deformation policy that classifies real geometry samples as rigid or deformable from the same canonical humanoid skin weights used by the body.
- Lionfish limb decorations, overlays, sleeves, and other supported geometry can now stay rigid to one body segment when appropriate or deform across Root/Chest, Arm/Hand, and Thigh/Leg transitions when their actual geometry spans those transitions.
- Safely analyzable layer-owned Lionfish secondary models can now be skinned against the primary body's humanoid descriptor, keeping their elbow, knee, and spine transition positions aligned with the body instead of deriving a separate clothing skeleton.
- Improved runtime humanoid armature refresh behavior when renderer/model discovery is repeated.
- Improved hybrid GeckoLib/vanilla renderer compatibility for entities that delegate to ordinary Minecraft humanoid renderers at runtime.
- Added structural shoulder-carrier support for compatible `body -> shoulder/clavicle -> arm` hierarchies.
- Fixed L_Ender's Cataclysm Deepling-family arm/leg decoration deformation so distal decorations can follow the forearm or lower leg while longer surfaces continue to bend across the inferred elbow or knee.
- Full-parent-model Lionfish secondary passes can reuse the already-prepared skinned body mesh, preserving the same one-bone limb deformation as the body.
- Gecko post-render attachment anchoring uses the shared semantic CHEST binding instead of relying on a literal source-bone name.
- Runtime target-proportioned armature generation fails closed if it cannot be prepared safely instead of falling back to a mismatched canonical BIPED armature.
- Existing Epic Fight or third-party entity patches continue to take priority over EFPI automatic compatibility.
- Direct attack posture now scales from both Epic Fight Impact and final dealt damage, using a default `(damage / 8)^0.7` dealt-damage factor.
- Default parry posture damage is now 8% of the target's maximum posture, with the existing 0.5 raw minimum retained.
- High-EHP targets use a configurable health-based finisher curve that suppresses posture damage at high HP and approaches full posture damage near zero HP.
- The finisher curve is EHP-aware: it is disabled at 40 EHP or below, smoothly blended between 40 and 200 EHP, and fully active at 200 EHP or above.
- Ordinary posture regeneration now waits 10 seconds and then restores a constant 2% of maximum posture per second, independent of target health.
- Successful parries are detected directly when Epic Fight's attack event is marked parried, avoiding same-priority listener ordering races with Combat Evolution and other addons.

## Posture defaults

The 2.1.0 server defaults are tuned around the following values:

```text
maximum posture reference: 3.2 at 20 EHP
maximum posture EHP exponent: 0.7
Impact posture multiplier: 0.55
direct attack damage reference: 8.0
direct attack damage exponent: 0.7
parry posture percent: 8.0
full-health posture multiplier at full finisher strength: 0.25
zero-health posture multiplier: 1.0
health curve exponent: 2.0
EHP finisher blend: 40 -> 200
regen delay: 10 seconds
regen rate: 2% maximum posture per second
```

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
- Secondary geometry is only converted to deformable rendering when EFPI can obtain and classify its geometry safely. Opaque custom callbacks, item/effect layers, and other render contracts without safely extractable geometry retain their existing rigid or source-compatible fallback behavior.
- GeckoLib `GeoArmorRenderer` armor keeps its existing per-vertex skinned path. Arbitrary `GeoRenderLayer` callbacks are not treated as generic meshes when their rendered geometry cannot be determined safely.
- Existing Epic Fight patches supplied by Epic Fight or another compatibility addon take priority and are not replaced by EFPI.
- The prepared custom/model-library compatibility bucket now uses explicit `extended humanoid` terminology instead of retaining GeckoLib-specific legacy names.
- Network protocol version is `3`; older protocol-2 builds are intentionally rejected.
- Dedicated-server renderer discovery uses cache schema version `2`; older cache schemas are discarded rather than migrated.
- 2.1.0 does not retain config/cache migration shims for older releases or intermediate development builds. Regenerate the server config and renderer cache when upgrading if necessary.

For configuration, dedicated-server renderer discovery, multiplayer behavior, and known limitations, see the project README.
