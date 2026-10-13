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
- Automatically detects supported GeckoLib humanoid models
- Automatically supports compatible unpatched Minecraft `SpiderModel` mobs
- Uses Epic Fight BIPED or SPIDER animation for special posture states
- Converts supported GeckoLib humanoid geometry to an Epic Fight skinned mesh
- Preserves supported GeckoLib per-bone equipment and attachment rendering during special states
- Respects Epic Fight patches supplied by Epic Fight or other compatibility mods instead of replacing them
- Supports `CustomExecuteEntity` implementations from Combat Evolution
- Displays health and posture together in the combat HUD
- Uses server-authoritative multiplayer posture state and server policy synchronization
- Includes posture scaling, blacklist, and automatic-compatibility server settings
- Includes a client-side Combat Evolution execution sound volume setting

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer within the 47.x series
- Epic Fight 20.14.17 or another compatible 20.14.x release
- Combat Evolution 2.2.8 through the compatible 2.3.x series

Epic Fight and Combat Evolution are required on both the client and server.

GeckoLib is **optional**. It is only required when using GeckoLib mobs that need EFPI's automatic Gecko humanoid compatibility.

The main tested development combination for 2.0.0 is:

- Forge 47.4.10
- Epic Fight 20.14.17
- Combat Evolution 2.3.1
- GeckoLib 4.8.4 when Gecko compatibility is used

## How Posture Works

Each eligible entity receives a separate posture value. Maximum posture is calculated primarily from maximum health and, when enabled, armor and armor toughness.

Epic Fight attacks deal posture damage based on Impact. Successful parries also damage the attacker's posture, allowing defensive play to create posture breaks without first reducing health.

When posture reaches zero, EFPI attempts to apply the entity's neutralize/guard-break animation. During execution, posture remains locked at zero. If the entity is not executed, it enters recovery and returns to normal behavior.

Entities already managed by Combat Evolution's own native posture implementation are left to Combat Evolution to avoid duplicate posture systems.

## Automatic Renderer Compatibility

Version 2.0.0 expands automatic compatibility beyond the behavior available in 1.1.0. EFPI can discover supported renderer/model layouts and register lightweight Epic Fight patches without replacing the source mob's normal implementation.

Automatic renderer compatibility is experimental. Detection is intentionally conservative: a target that cannot be classified and prepared safely is skipped instead of being guessed.

Three automatic compatibility paths are handled independently.

### Minecraft HumanoidModel

EFPI inspects registered entity renderers and detects living entities whose renderer model is a Minecraft `HumanoidModel`.

For compatible targets, EFPI registers a lightweight BIPED Epic Fight patch. The original renderer remains active during normal gameplay. Epic Fight's patched renderer is used only when EFPI needs special posture-state rendering.

The source `HumanoidModel` is converted through Epic Fight's own vanilla humanoid model transformer. If conversion fails, automatic compatibility is not installed for that target.

### GeckoLib Humanoids

When GeckoLib is installed, EFPI also inspects `GeoEntityRenderer` models.

A Gecko model is accepted only when its bone names, hierarchy, topology, left/right structure, and major-limb geometry can be classified as a supported humanoid layout. Detection is semantic rather than tied to one specific mod or entity name.

For accepted models EFPI can:

- register the same lightweight BIPED patch used for automatic humanoid compatibility;
- convert supported Geo model geometry into an Epic Fight `SkinnedMesh`;
- split suitable one-bone arms and legs so Epic Fight elbow and knee joints can animate them;
- replay supported Gecko per-bone item, armor, equipment, and attachment layers using the Epic Fight joint pose; and
- bridge certain post-render attachment paths that read Gecko bone transforms directly.

Normal GeckoLib animation and rendering remain untouched outside EFPI's special posture states.

Not every GeckoLib model is guaranteed to be compatible. Non-humanoid, ambiguous, highly custom, dynamically selected, or otherwise unsupported models fail closed instead of being forced through the BIPED path.

### Minecraft SpiderModel

EFPI can also detect unpatched living entities rendered with Minecraft `SpiderModel`.

For compatible targets, EFPI registers a lightweight SPIDER Epic Fight patch while leaving the source mob's normal AI, movement, attacks, targeting, and ordinary renderer unchanged. Epic Fight rendering is used only for posture-related special states.

Supported spider targets use Epic Fight's spider neutralized animation and EFPI's spider execution-victim animation when the entity uses a compatible Epic Fight `SpiderArmature`.

This path is intended for standard Minecraft-style spider renderer/model layouts. Highly custom spider renderers or unrelated arthropod models are not assumed to be compatible.

### Existing Epic Fight Patches Take Priority

EFPI does not overwrite a patch already supplied by Epic Fight, another compatibility addon, or another earlier patch-registry subscriber.

Entities with an existing Epic Fight patch are excluded from EFPI automatic renderer discovery entirely. The automatic compatibility switches described below apply only to lightweight patches owned by EFPI; they do not disable explicit Epic Fight compatibility supplied by another mod.

## Confirmed Compatibility

The automatic compatibility system has been tested with:

- vanilla-style and modded entities using Minecraft `HumanoidModel`;
- Iron's Spells 'n Spellbooks humanoid GeckoLib mobs, including special-state body animation, equipment/attachment rendering, and its humanoid cape path;
- additional GeckoLib humanoid models used during development; and
- Minecraft-style `SpiderModel` entities using the standard Epic Fight spider armature path.

Compatibility with every third-party renderer, custom Geo layer, or custom spider model cannot be guaranteed.

## Server Configuration

EFPI 2.0.0 stores server policy globally for the Minecraft installation/server rather than separately inside each world.

```text
config/epicfight_posture_integration/server.toml
```

Example:

```toml
[posture]

# Global maximum posture multiplier.
# 1.0 = default
global_multiplier = 1.0

# Minimum maximum posture an eligible mob can have.
minimum_posture = 1.0

# Whether armor and armor toughness affect maximum posture.
use_armor_scaling = true

# Entities excluded from EFPI posture and automatic compatibility behavior.
# Use namespace:entity IDs.
entity_blacklist = []

[compatibility]

# Automatic compatibility for Minecraft HumanoidModel entities.
enable_vanilla_humanoid_compatibility = true

# Automatic compatibility for supported GeckoLib humanoid entities.
enable_geckolib_humanoid_compatibility = true

# Automatic compatibility for unpatched Minecraft SpiderModel entities.
enable_vanilla_spider_compatibility = true
```

All three automatic compatibility options default to `true`.

On a dedicated server, this file is the authoritative configuration. Its relevant values, including the compatibility switches and entity blacklist, are synchronized to clients when they join. A client's local `server.toml` does not override the remote server. In single-player, the same global file is used by the integrated server.

Restart the server after changing an automatic compatibility option so patch ownership is rebuilt consistently.

`entity_blacklist` remains the per-entity override. Blacklisted IDs do not use EFPI posture or active EFPI automatic compatibility behavior.

### Upgrading from 1.1.0

The public 1.1.0 release used Forge's per-world server config location. Version 2.0.0 uses the global file above. If you customized the old per-world `serverconfig/epicfight_posture_integration-server.toml`, copy those values into the new global `server.toml` once after upgrading.

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

Posture state and EFPI server policy are controlled by the server. Clients receive synchronized posture information only for entities they are tracking, plus the server's authoritative EFPI configuration when joining.

Important state changes such as posture damage, posture breaks, execution start, recovery start, and return to normal are synchronized immediately. Continuous recovery uses reduced-frequency synchronization to avoid unnecessary traffic.

EFPI 2.0.0 uses network protocol version `2`. The public 1.1.0 release used protocol version `1`; 2.0.0 adds renderer-discovery and server-settings synchronization packets and is not network-compatible with 1.1.0. Client and server should therefore use the same EFPI version.

## Dedicated Server Renderer Discovery

A dedicated server cannot inspect Minecraft client renderer classes directly. EFPI therefore uses a guarded client-report system for automatic renderer compatibility.

The flow is:

1. An operator-level client joins the dedicated server.
2. The server sends a one-time discovery request with a nonce.
3. The client uses the renderer compatibility snapshot already validated during client startup.
4. The result is sent back to the server.
5. The server validates entity IDs and stores the result as pending discovery data.
6. Restart the dedicated server to promote the pending compatibility targets to active targets before Epic Fight's patch registry runs.

Only players with the required operator permission level can submit discovery reports.

The dedicated-server renderer cache is automatically managed at:

```text
config/epicfight_posture_integration/cache/renderer-discovery.json
```

This JSON file is **not a user configuration file**. EFPI creates and updates it automatically. Manual editing is not supported. It can be deleted while the server is stopped to reset renderer discovery; a trusted operator client will then need to report compatible renderers again.

Client renderer discovery is not persisted to disk. The client validates registered renderers fresh on every launch, prepares compatible special-state meshes, and keeps the verified report snapshot only in memory.

The dedicated-server cache uses an environment fingerprint derived from loaded mod IDs and versions. When the server mod environment changes, stale discovery data is discarded instead of being reused blindly.

The 2.0.0 renderer-discovery cache format starts at schema version `1`.

## Cache and Conflict Handling

Vanilla `HumanoidModel`, GeckoLib humanoid, and vanilla `SpiderModel` targets are stored separately.

On dedicated servers, reports are keyed by client environment fingerprint. If client environments disagree about whether an entity belongs to one of those supported renderer kinds or is unsupported, that entity is placed in the conflict set instead of being automatically enabled.

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

Renderer discovery and runtime mesh preparation happen during renderer-registration/discovery phases rather than every frame. Dedicated-server discovery requests reuse the already validated in-memory client snapshot instead of rebaking Gecko meshes on login.

## Known Limitations

- Automatic compatibility requires a renderer/model structure EFPI can classify safely.
- A visually humanoid mob is not automatically compatible if its renderer or bone hierarchy is unconventional.
- GeckoLib models with dynamic per-entity model resources may fail closed instead of being guessed.
- Highly custom full-model Geo render layers, physics systems, or mod-specific post-render code may require additional compatibility work.
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

The generated JAR is placed in `build/libs/`.

GitHub Actions performs the same project build and uploads the generated JAR as an artifact.

## Reporting Issues

For compatibility issues, include:

- Minecraft version
- Forge version
- Epic Fight version
- Combat Evolution version
- GeckoLib version if applicable
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

This project is not affiliated with or endorsed by the developers of Epic Fight, Combat Evolution, or GeckoLib. Those projects remain separate and are distributed under their respective licenses.

## License

Epic Fight Posture Integration is licensed under the **GNU General Public License v3.0 only (GPL-3.0-only)**.

See the `LICENSE` file for the full license text.
