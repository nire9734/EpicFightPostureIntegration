# Epic Fight Posture Integration

Epic Fight Posture Integration adds a server-authoritative posture system to Epic Fight entities and connects posture breaks to Combat Evolution executions.

The mod is designed to preserve the original mob implementation. It does **not** replace normal AI, targeting, pathfinding, attacks, or ordinary rendering for automatically supported mobs. Automatic compatibility uses lightweight Epic Fight patches only where necessary for posture-break and execution-related behavior.

## Features

- Adds posture to compatible Epic Fight entities
- Uses Epic Fight Impact values for posture damage
- Applies posture damage to attackers after successful parries
- Supports posture breaks and Combat Evolution executions
- Preserves existing mob AI and normal rendering
- Automatically supports compatible Minecraft `HumanoidModel` mobs
- Conservatively analyzes supported Minecraft `HierarchicalModel` / `ModelPart`, GeckoLib, Lionfish/Citadel-style, and AzureLib humanoid model layouts
- Automatically supports compatible unpatched Minecraft `SpiderModel` mobs
- Uses target-proportioned Epic Fight BIPED armatures for accepted generic humanoid models
- Converts supported humanoid source geometry to Epic Fight skinned meshes for special posture states
- Preserves supported GeckoLib armor, equipment, attachment, and selected post-render paths during special states
- Replays supported Minecraft/Lionfish renderer layers and secondary geometry during special states
- Supports structural shoulder-carrier layouts such as `body -> shoulder/clavicle -> arm`
- Handles supported hybrid GeckoLib renderers that delegate to ordinary Minecraft humanoid renderers at runtime
- Respects Epic Fight patches supplied by Epic Fight or other compatibility mods instead of replacing them
- Supports `CustomExecuteEntity` implementations from Combat Evolution
- Displays health and posture together in the combat HUD
- Uses server-authoritative multiplayer posture state and server policy synchronization
- Includes posture scaling, finisher-curve, regeneration, blacklist, and automatic-compatibility server settings
- Includes a client-side Combat Evolution execution sound volume setting

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer within the 47.x series
- Epic Fight 20.14.17 or another compatible 20.14.x release
- Combat Evolution 2.2.8 through the compatible 2.3.x series

Epic Fight and Combat Evolution are required on both the client and server.

GeckoLib is **optional**. It is only required when a loaded mob or renderer uses GeckoLib and EFPI's Gecko compatibility path is needed. Lionfish API and AzureLib integrations are likewise optional framework integrations and are only used when those libraries are present.

Iron's Spells 'n Spellbooks and L_Ender's Cataclysm are **optional compatibility targets**, not required EFPI dependencies.

The main tested development combination for 2.1.0 is:

- Forge 47.4.10
- Epic Fight 20.14.17
- Combat Evolution 2.3.1
- GeckoLib 4.8.4 when Gecko compatibility is used

## How Posture Works

Each eligible entity receives a separate posture value. Maximum posture is based on effective health (EHP), using maximum health and, when enabled, armor and armor toughness. The default reference is 3.2 posture at 20 EHP, with sublinear EHP scaling:

```text
maximum posture = 3.2 * (EHP / 20)^0.7
```

The configured global multiplier and minimum posture are then applied.

Direct Epic Fight attacks use the attack's real Impact value. Their posture damage is additionally scaled by final dealt damage so player damage scaling and target EHP scaling remain comparable:

```text
attack posture scale = (dealt damage / 8)^0.7
```

The default Impact-derived posture multiplier is `0.55`. Eligible non-Epic-Fight direct player attacks use a bounded damage-derived fallback Impact before the same dealt-damage scaling is applied.

Successful Epic Fight-style parries damage the attacker's posture. By default the raw parry value is 8% of the target's maximum posture, with a minimum raw value of 0.5.

Both direct-hit and parry posture damage then use the health/EHP finisher modifier. For a sufficiently high-EHP target, the default health curve is:

```text
multiplier = 0.25 + 0.75 * (1 - health fraction)^2
```

That gives approximately x0.25 posture damage at full health, x0.4375 at 50% health, and x1.0 near zero health. The curve is deliberately weakened for lower-EHP mobs: at 40 EHP or below it is disabled and posture damage stays at x1.0; between 40 and 200 EHP its strength is smoothly interpolated; at 200 EHP or above the full curve is applied.

Ordinary posture regeneration starts after 10 seconds without posture damage or a parry and then restores 2% of maximum posture per second. This rate is constant and does not vary with health. Posture-break recovery is a separate state and is not controlled by the ordinary regeneration rate.

When posture reaches zero, EFPI attempts to apply the entity's neutralize/guard-break animation. During execution, posture remains locked at zero. If the entity is not executed, it enters recovery and returns to normal behavior.

Entities already managed by Combat Evolution's own native posture implementation are left to Combat Evolution to avoid duplicate posture systems.

## Automatic Renderer Compatibility

Version 2.0.0 introduced EFPI's renderer-discovery architecture. Version 2.1.0 extends that architecture with a shared humanoid rig/semantic/skeleton pipeline and additional model backends while retaining the same conservative acceptance policy.

Automatic renderer compatibility is intentionally conservative: a target that cannot be classified and prepared safely is skipped instead of being guessed. Existing Epic Fight patches always take priority over EFPI's automatic lightweight patches.

### Shared Humanoid Conversion Pipeline

Supported non-vanilla humanoid backends are normalized through the same general pipeline:

```text
source model
-> SourceRigSnapshot
-> conservative semantic analysis
-> resolved humanoid skeleton
-> target body metrics / inferred joints
-> shared skin-weight resolution
-> Epic Fight runtime armature + skinned mesh
```

This is structural rather than entity-ID based. Major joints are accepted from source names only when hierarchy, topology, left/right structure, and renderable geometry remain consistent with a supported humanoid layout. One-bone limbs can receive inferred elbow/knee joints and continuous per-vertex deformation. Ambiguous layouts fail closed.

### Minecraft HumanoidModel

EFPI inspects registered entity renderers and detects living entities whose renderer model is a Minecraft `HumanoidModel`.

For compatible targets, EFPI registers a lightweight BIPED Epic Fight patch. The original renderer remains active during normal gameplay. Epic Fight's patched renderer is used only when EFPI needs special posture-state rendering.

The source `HumanoidModel` is converted through Epic Fight's vanilla humanoid model transformer. If conversion fails, automatic compatibility is not installed for that target.

### Generic Minecraft ModelPart / HierarchicalModel

EFPI can conservatively inspect supported non-`HumanoidModel` Minecraft hierarchical models and build a humanoid skeleton from their source `ModelPart` hierarchy.

Body geometry is converted to a skinned mesh. Recognized dynamic attachment branches are kept out of the static body mesh and can be replayed separately so source visibility can continue selecting active weapons or attachments.

This does not imply that every custom `ModelPart` renderer is compatible. Models that cannot be classified safely are rejected.

### GeckoLib Humanoids

When GeckoLib is installed, EFPI also inspects supported `GeoEntityRenderer` models.

A Gecko model is accepted only when its source hierarchy, semantic names, topology, left/right structure, and major-limb geometry can be classified as a supported humanoid layout. Detection is not tied to one specific entity ID.

For accepted models EFPI can:

- register the same lightweight BIPED patch used for generic humanoid compatibility;
- convert supported Geo model geometry into an Epic Fight `SkinnedMesh`;
- split suitable one-bone arms and legs so Epic Fight elbow and knee joints can animate them;
- convert supported `GeoArmorRenderer` armor into the same target armature space;
- replay supported per-bone item, equipment, and attachment layers using the Epic Fight joint pose;
- share the same semantic source binding between body, layer, and post-render attachment handling; and
- fall back to a native Minecraft humanoid conversion when a hybrid Gecko renderer delegates to an ordinary `HumanoidModel` renderer at runtime.

Normal GeckoLib animation and rendering remain untouched outside EFPI's special posture states.

Not every GeckoLib model is guaranteed to be compatible. Non-humanoid, ambiguous, highly custom, dynamically selected, or otherwise unsupported models fail closed instead of being forced through the BIPED path.

### Lionfish / Citadel-style Humanoids

EFPI can conservatively inspect supported Lionfish `AdvancedEntityModel` / `AdvancedModelBox` humanoid layouts through the same semantic and skin-weight pipeline.

For full-model secondary layers that redraw the exact same parent model without changing source-part visibility, EFPI 2.1.0 can reuse the prepared skinned body mesh through the layer's own render buffer. This keeps long one-bone arm/leg decorations on the same per-vertex deformation as the body. Layers that use a separate model or change source visibility continue through the existing source-model replay fallback.

This path is intentionally generic and does not special-case Cataclysm entity IDs.

### AzureLib Humanoids

EFPI can conservatively inspect supported AzureLib humanoid source models and convert accepted body geometry through the shared semantic/skeleton/weight pipeline.

AzureLib secondary rendering support is currently less complete than the GeckoLib path. Custom armor, attachment, or render-layer behavior may therefore fail closed or remain unsupported even when the body model itself can be converted safely.

### Minecraft SpiderModel

EFPI can also detect unpatched living entities rendered with Minecraft `SpiderModel`.

For compatible targets, EFPI registers a lightweight SPIDER Epic Fight patch while leaving the source mob's normal AI, movement, attacks, targeting, and ordinary renderer unchanged. Epic Fight rendering is used only for posture-related special states.

Supported spider targets use Epic Fight's spider neutralized animation and EFPI's spider execution-victim animation when the entity uses a compatible Epic Fight `SpiderArmature`.

This path is intended for standard Minecraft-style spider renderer/model layouts. Highly custom spider renderers or unrelated arthropod models are not assumed to be compatible.

### Existing Epic Fight Patches Take Priority

EFPI does not overwrite a patch already supplied by Epic Fight, another compatibility addon, or another earlier patch-registry subscriber.

Entities with an existing Epic Fight patch are excluded from EFPI automatic renderer discovery entirely. Automatic compatibility switches apply only to lightweight patches owned by EFPI; they do not disable explicit Epic Fight compatibility supplied by another mod.

## Confirmed Compatibility

For 2.1.0, **Iron's Spells 'n Spellbooks and L_Ender's Cataclysm are officially tested compatibility targets for supported humanoid mobs**.

This is deliberately narrower than claiming full support for either mod. It does not mean every mob, renderer, armor implementation, or custom layer from those projects is compatible.

The automatic compatibility system has been tested with:

- vanilla-style and modded entities using Minecraft `HumanoidModel`;
- supported humanoid mobs from Iron's Spells 'n Spellbooks, including special-state Gecko body animation, armor/equipment/attachment rendering, hybrid vanilla delegates, and the humanoid cape path;
- supported humanoid mobs from L_Ender's Cataclysm using Lionfish-style models, including Deepling-family full-model secondary layers and structural shoulder-carrier layouts used by compatible humanoids;
- additional supported GeckoLib, ModelPart, and framework-backed humanoid layouts used during development; and
- Minecraft-style `SpiderModel` entities using the standard Epic Fight spider armature path.

Compatibility with every third-party renderer, custom layer, armor implementation, or custom spider model cannot be guaranteed.

## Server Configuration

EFPI stores server policy globally for the Minecraft installation/server at:

```text
config/epicfight_posture_integration/server.toml
```

The 2.1.0 defaults are:

```toml
[posture]
global_multiplier = 1.0
posture_damage_multiplier = 0.55
attack_damage_reference = 8.0
attack_damage_scaling_exponent = 0.7
parry_posture_percent = 8.0
full_health_posture_damage_multiplier = 0.25
zero_health_posture_damage_multiplier = 1.0
health_posture_curve_exponent = 2.0
minimum_posture = 1.0
use_armor_scaling = true
entity_blacklist = []
health_scaling_ehp_start = 40.0
health_scaling_ehp_end = 200.0

[posture.regeneration]
delay_seconds = 10.0
percent_per_second = 2.0

[compatibility]
enable_vanilla_humanoid_compatibility = true
enable_extended_humanoid_compatibility = true
enable_vanilla_spider_compatibility = true
```

`enable_extended_humanoid_compatibility` controls the shared prepared-humanoid bucket used by supported ModelPart, GeckoLib, Lionfish, and AzureLib backends.

On a dedicated server, this file is authoritative. Relevant compatibility policy values and the entity blacklist are synchronized to clients when they join. A client's local `server.toml` does not override the remote server. In single-player, the same global file is used by the integrated server.

Restart the server after changing an automatic compatibility option so patch ownership is rebuilt consistently.

`entity_blacklist` remains the per-entity override. Blacklisted IDs do not use EFPI posture or active EFPI automatic compatibility behavior.

### Upgrading to 2.1.0

2.1.0 deliberately does not retain migration shims for older EFPI configuration or renderer-discovery cache layouts. If upgrading from an older release, back up any settings you want to keep, then delete `config/epicfight_posture_integration/server.toml` and the generated renderer cache before first launch so 2.1.0 can generate the current layout cleanly. Reapply desired settings afterward using the new keys.

The old `enable_geckolib_humanoid_compatibility` switch is replaced by `enable_extended_humanoid_compatibility`.

## Client Configuration

The client configuration is stored at:

```text
config/epicfight_posture_integration/client.toml
```

It can also be changed in-game from:

```text
Mods -> Epic Fight Posture Integration -> Config
```

The **Execution Sound Volume** setting changes only the local player's Combat Evolution execution sound effects. It ranges from 0% to 100% of Combat Evolution's original volume and defaults to 70%.

## Multiplayer

Posture state and EFPI server policy are controlled by the server. Clients receive synchronized posture information only for entities they are tracking, plus the server's authoritative EFPI compatibility policy when joining.

Important state changes such as posture damage, posture breaks, execution start, recovery start, and return to normal are synchronized immediately. Continuous recovery uses reduced-frequency synchronization to avoid unnecessary traffic.

EFPI 2.1.0 uses network protocol version `3`. Older protocol-2 builds are intentionally rejected; client and server should use the same EFPI version.

## Dedicated Server Renderer Discovery

A dedicated server cannot inspect Minecraft client renderer classes directly. EFPI therefore uses a guarded client-report system for automatic renderer compatibility.

The flow is:

1. An operator-level client joins the dedicated server.
2. The server sends a one-time discovery request with a nonce.
3. The client uses the renderer compatibility snapshot already validated during client startup.
4. The result is sent back to the server.
5. The server validates entity IDs and stores the result as pending discovery data.
6. Restart the dedicated server to promote pending compatibility targets to active targets before Epic Fight's patch registry runs.

Only players with the required operator permission level can submit discovery reports.

The dedicated-server renderer cache is automatically managed at:

```text
config/epicfight_posture_integration/cache/renderer-discovery.json
```

This JSON file is **not a user configuration file**. EFPI creates and updates it automatically. Manual editing is not supported. It can be deleted while the server is stopped to reset renderer discovery; a trusted operator client will then need to report compatible renderers again.

Client renderer discovery is not persisted to disk. The client validates registered renderers fresh on every launch, prepares compatible special-state meshes, and keeps the verified report snapshot only in memory.

The dedicated-server cache uses an environment fingerprint derived from loaded mod IDs and versions. When the server mod environment changes, stale discovery data is discarded instead of being reused blindly.

EFPI 2.1.0 uses renderer-discovery cache schema version `2`. Older schema versions are discarded rather than migrated.

## Cache and Conflict Handling

Vanilla `HumanoidModel`, extended/prepared humanoid, and vanilla `SpiderModel` targets are stored separately.

On dedicated servers, reports are keyed by client environment fingerprint. If client environments disagree about whether an entity belongs to one of the supported renderer kinds or is unsupported, that entity is placed in the conflict set instead of being automatically enabled.

The renderer cache is internal generated state, not a place to manually add compatibility targets. Deleting it while the server is stopped is the supported reset mechanism.

## Shields and Guard Breaks

When posture is broken, active item use such as shield blocking is interrupted. The same check is performed when execution begins.

The shield itself is not removed or damaged by EFPI. After the special state ends, the entity's original AI may use it again.

## HUD

Supported entities use a combined Health + Posture display. The HUD can appear when health has been reduced, posture has been reduced, the entity is posture-broken, it is being executed, or it is recovering.

This allows posture damage caused only by parries to remain visible while health is still full.

## Performance

Posture state is created lazily rather than for every living entity.

The implementation also avoids repeated reflective method discovery, unnecessary Epic Fight patch lookups, maximum-posture recalculation every tick, per-tick posture synchronization, and duplicate Combat Evolution posture processing.

Renderer discovery and runtime mesh preparation happen during renderer-registration/discovery phases rather than every frame. Static model analysis and prepared runtime geometry are cached where safe; resource-owned model caches use resource-lifetime-aware keys where applicable.

## Known Limitations

- Automatic compatibility requires a renderer/model structure EFPI can classify safely.
- A visually humanoid mob is not automatically compatible if its renderer or source hierarchy is unconventional.
- Dynamic per-entity model resources or geometry variants may fail closed instead of being guessed.
- Highly custom armor, physics, full-model layers, post-render code, or framework-specific secondary rendering may require additional compatibility work.
- GeckoLib has the most complete framework-specific armor/attachment bridge; AzureLib and some Lionfish/ModelPart secondary rendering contracts remain more limited.
- Automatic spider compatibility targets Minecraft-style `SpiderModel` layouts; highly custom spider/arthropod renderers may require explicit compatibility.
- Execution compatibility ultimately depends on Combat Evolution's execution behavior and a compatible Epic Fight armature.
- Existing third-party Epic Fight patches take priority over EFPI's automatic lightweight patches.
- Dedicated servers require a trusted operator client to discover renderer-based compatibility targets, followed by a restart when the pending set changes.

## Building From Source

Java 17 is required.

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

The generated JAR is placed in `build/libs/`. For version 2.1.0 the release JAR is named `epicfight-posture-integration-2.1.0.jar`.

GitHub Actions performs the same project build and uploads the generated JAR as an artifact.

## Reporting Issues

For compatibility issues, include:

- Minecraft version
- Forge version
- Epic Fight version
- Combat Evolution version
- model framework version (GeckoLib, Lionfish API, AzureLib, if applicable)
- Epic Fight Posture Integration version
- affected entity ID
- mod that adds the entity
- any Epic Fight compatibility addon in use
- relevant logs

For performance issues, a Spark profiler result is also useful.

## Credits

Epic Fight Posture Integration is an unofficial third-party addon.

- **Epic Fight** — Antikythera Studios
- **Combat Evolution** — ShelMarow
- **GeckoLib** — optional compatibility target/API
- **Lionfish API** — optional model compatibility API
- **AzureLib** — optional model compatibility API
- **Iron's Spells 'n Spellbooks** — officially tested compatibility target for supported humanoid mobs
- **L_Ender's Cataclysm** — officially tested compatibility target for supported humanoid mobs

This project is not affiliated with or endorsed by the developers of Epic Fight, Combat Evolution, GeckoLib, Lionfish API, AzureLib, Iron's Spells 'n Spellbooks, or L_Ender's Cataclysm. Those projects remain separate and are distributed under their respective licenses.

## License

Epic Fight Posture Integration is licensed under the **GNU General Public License v3.0 only (GPL-3.0-only)**.

See the `LICENSE` file for the full license text.
