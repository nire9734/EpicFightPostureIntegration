# Epic Fight Posture Integration

Epic Fight Posture Integration (EFPI) adds a server-authoritative posture system to compatible Epic Fight mobs and connects posture breaks to Combat Evolution executions.

EFPI preserves the source mob's normal AI and ordinary rendering. Automatic compatibility uses lightweight Epic Fight patches only when needed for posture-break and execution-related states, and existing Epic Fight/third-party patches take priority.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+ within 47.x
- Epic Fight 20.14.17 through compatible 20.14.x releases
- Combat Evolution 2.2.8 through compatible 2.3.x releases

GeckoLib, Lionfish/Citadel-style libraries, AzureLib, Iron's Spells 'n Spellbooks, and L_Ender's Cataclysm are optional compatibility targets, not hard dependencies unless another installed mod requires them.

The main 3.0.0 development combination uses Forge 47.4.10, Epic Fight 20.14.17, Combat Evolution 2.3.1, and GeckoLib 4.8.4 when Gecko compatibility is needed.

## Features

- Server-authoritative posture, posture breaks, and Combat Evolution execution integration.
- Automatic compatibility for supported vanilla `HumanoidModel`, extended humanoid ModelPart/GeckoLib/Lionfish/AzureLib layouts, and vanilla-style `SpiderModel` entities.
- Target-proportioned humanoid retargeting for posture/execution states while preserving normal mob behavior.
- Health + Posture HUD.
- EHP-based maximum posture, dealt-damage-scaled posture pressure, EHP-scaled parries, health-based finisher pressure, and configurable regeneration.
- Per-entity maximum-posture overrides, entity blacklist, and mod-level automatic-compatibility allow/deny filtering.
- Dedicated-server renderer discovery with generated cache and multiplayer policy synchronization.

## Posture balance

Maximum posture is based on effective health (EHP), using maximum health and, when enabled, armor/toughness:

```text
maximum posture = 3.2 * (EHP / 20)^0.7
```

The configured maximum-posture multiplier and minimum are then applied. An `entity_multipliers` entry replaces `global_multiplier` for that entity and affects maximum posture only.

Direct Epic Fight attacks use real Impact and scale with final dealt damage:

```text
attack posture scale = (dealt damage / 8)^0.7
```

The default Impact-derived posture multiplier is `0.55`. Eligible non-Epic-Fight direct player attacks use a bounded damage-derived fallback Impact before the same dealt-damage scaling.

Successful parries use an EHP-scaled percentage of target maximum posture:

```text
effective parry fraction = base parry fraction * (1 + (20 / EHP)^0.7)
```

With the default `parry_posture_percent = 8.0`, lower-EHP mobs receive a larger effective parry percentage while very high-EHP enemies approach the 8% baseline. No absolute minimum parry damage is used.

Direct hits and parries both use the same health/EHP finisher modifier. At full finisher strength:

```text
multiplier = 0.25 + 0.75 * (1 - health fraction)^2
```

The finisher curve is disabled at 40 EHP or below, smoothly blended from 40 to 200 EHP, and fully active at 200 EHP or above.

Ordinary regeneration starts after 10 seconds without posture damage/parry and restores 2% of maximum posture per second. Posture-break recovery is a separate state.

## Server configuration

Server policy is stored at:

```text
config/epicfight_posture_integration/server.toml
```

3.0.0 defaults:

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
health_scaling_ehp_start = 40.0
health_scaling_ehp_end = 200.0
minimum_posture = 1.0
use_armor_scaling = true
entity_blacklist = []

[posture.regeneration]
delay_seconds = 10.0
percent_per_second = 2.0

[posture.overrides]
entity_multipliers = []

[compatibility]
enable_vanilla_humanoid_compatibility = true
enable_extended_humanoid_compatibility = true
enable_vanilla_spider_compatibility = true
mod_filter_mode = "DENYLIST"
mod_allowlist = []
mod_denylist = []
```

Example per-entity maximum-posture overrides:

```toml
[posture.overrides]
entity_multipliers = [
    "cataclysm:ignis=1.25",
    "minecraft:zombie=0.8"
]
```

These values replace `global_multiplier` for the specified entity rather than multiplying it.

Automatic compatibility filtering applies to third-party mod namespaces only:

- `DENYLIST`: keep normal automatic compatibility except for mod IDs in `mod_denylist`.
- `ALLOWLIST`: only mod IDs in `mod_allowlist` receive EFPI automatic compatibility.
- Vanilla `minecraft` entities remain controlled by the vanilla compatibility switches.
- Explicit Epic Fight patches supplied by other mods are not disabled by this filter.

Restart after changing automatic compatibility policy so patch ownership is rebuilt consistently.

## Multiplayer and dedicated servers

Posture state and compatibility policy are server-authoritative. EFPI 3.0.0 uses network protocol `3`; older protocol-2 builds are intentionally rejected.

Dedicated-server renderer discovery is stored at:

```text
config/epicfight_posture_integration/cache/renderer-discovery.json
```

The cache uses schema version `2`. It is generated state, not a user configuration file. A trusted operator client reports renderer compatibility; when pending discovery data changes, restart the server to promote it to active data.

## Upgrading from 2.0.0

3.0.0 intentionally does not migrate older server config or renderer-discovery cache layouts. Back up any values you want to keep, then regenerate the config/cache and reapply desired settings.

The old GeckoLib-only compatibility terminology is replaced by `enable_extended_humanoid_compatibility` for supported ModelPart, GeckoLib, Lionfish, and AzureLib humanoid backends.

## Client configuration

Client settings are stored at:

```text
config/epicfight_posture_integration/client.toml
```

They can also be changed from `Mods -> Epic Fight Posture Integration -> Config`. Execution Sound Volume is client-side only and defaults to 70%.

## Compatibility notes

Iron's Spells 'n Spellbooks and L_Ender's Cataclysm are tested compatibility targets for supported humanoid mobs. This does not imply universal support for every entity, renderer, armor implementation, custom layer, or framework-specific render path.

Automatic compatibility is deliberately fail-closed: unsupported, ambiguous, conflicting, or unsafe layouts are skipped rather than guessed.
