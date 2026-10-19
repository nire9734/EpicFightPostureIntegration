# Epic Fight Posture Integration

Epic Fight Posture Integration (EFPI) adds a server-authoritative posture system to compatible Epic Fight mobs and connects posture breaks to Combat Evolution executions.

EFPI preserves the source mob's normal AI and ordinary rendering. Automatic compatibility uses lightweight Epic Fight patches only when needed for posture-break and execution-related states, and existing Epic Fight/third-party patches take priority.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+ within 47.x
- Epic Fight 20.14.17 through compatible 20.14.x releases
- Combat Evolution 2.2.8 through compatible 2.3.x releases

GeckoLib, Lionfish/Citadel-style libraries, Iron's Spells 'n Spellbooks, and L_Ender's Cataclysm are optional compatibility targets, not hard dependencies unless another installed mod requires them.

The main 3.0.0 development combination uses Forge 47.4.10, Epic Fight 20.14.17, Combat Evolution 2.3.1, and GeckoLib 4.8.4 when Gecko compatibility is needed.

## Features

- Server-authoritative posture, posture breaks, and Combat Evolution execution integration.
- Automatic compatibility for supported vanilla `HumanoidModel`, extended humanoid ModelPart/GeckoLib/Lionfish layouts, and vanilla-style `SpiderModel` entities.
- Target-proportioned humanoid retargeting for posture/execution states while preserving normal mob behavior.
- Health + Posture HUD.
- EHP-based maximum posture, HP-loss-normalized finisher pressure, innate-EHP parries, Impact-weighted direct attacks, and configurable regeneration.
- Per-entity maximum-posture overrides, entity blacklist, and mod-level automatic-compatibility allow/deny filtering.
- Dedicated-server renderer discovery with generated cache and multiplayer policy synchronization.

## Posture balance

Maximum posture is based on effective health (EHP), using maximum health and armor/toughness:

```text
maximum posture = 3.2 * (EHP / 20)^0.7
```

`maximum_posture_multiplier` is then applied. A per-entity `posture_multipliers` entry replaces that global multiplier for the specified entity. Maximum posture has an internal minimum of 1.0.

In 3.0.1, EFPI observes direct player attacks at Forge 1.20.1's **`LOWEST` priority**, then determines actual damage from the target's **observed HP loss after the event completes** (at the next damage event or entity tick). This avoids relying on listener registration order when Soulrend/KubeJS applies another `LOWEST` damage cap after EFPI's listener. Attacks that do not actually reduce HP cause no posture damage. As with other delayed health-delta approaches, exceptional healing or non-event HP changes between observation points may affect attribution.

Attack posture follows the **fraction of target maximum HP removed**, rather than assuming a fixed damage scale. Let `u = 1 - currentHealth / maxHealth` and `F(u) = 0.25*u + 0.25*u^3`. `P_base = 3.2*(EHP/20)^0.7` is maximum posture *before* global or per-entity multipliers:

```text
Impact efficiency = clamp(0.7 + 0.3 * sqrt(Impact / 3), 0.7, 1.5)
attack posture damage = 2.7 * P_base * Impact efficiency
                        * (F(u_after) - F(u_before))
                        * posture_damage_multiplier
```

The health-finisher curve `0.25 + 0.75*(1 - currentHealth/maxHealth)^2` applies to direct attacks against **all** mobs. Integrating it over the hit's actual HP-loss interval prevents high-damage RPG weapons from losing posture efficacy and makes hits approximately split-independent at equal Impact efficiency. An Epic Fight source with **zero Impact** remains excluded; eligible non-EF direct player damage receives a bounded derived Impact.

Parries intentionally work differently. They refer to **innate EHP** from base attribute values (unmodified max health, armor and toughness), not the mob's level/affix-scaled EHP:

```text
parry fraction = (parry_percent / 100) * (1 + (20 / innate EHP)^0.7)
parry damage = maximum posture * parry fraction
             * innate-EHP health-finisher modifier
             * per-entity parry multiplier
```

The parry finisher modifier is disabled at innate EHP ≤40, blended over 40–200, and fully active at 200+. This keeps even highly leveled zombies parry-vulnerable while naturally durable bosses are parry-resistant. If another mod rewrites *base* attributes instead of using modifiers, EFPI may need a `parry_multipliers` override for that mob type.

`maximum_posture_multiplier` and `posture_multipliers` still affect how much attack damage is needed to break posture; parries are intentionally a **percentage of maximum posture** and therefore their relative efficacy is unchanged by that multiplier. Execution damage remains Combat Evolution's responsibility.

Ordinary regeneration starts after the configured delay and restores the configured percentage of maximum posture per second. Posture-break recovery is a separate state.

## Server configuration

Server policy is stored at:

```text
config/epicfight_posture_integration/server.toml
```

3.0.1 defaults:

```toml
[posture]

# Multiplier for the maximum Posture of all entities.
# Default: 1.0 | Range: 0.1-100.0
maximum_posture_multiplier = 1.0

# Multiplier for attack Posture damage from final HP lost.
# Impact gives a bounded bonus; 1.0 = 3.0.1 default pressure.
# Default: 1.0 | Range: 0.0-100.0
posture_damage_multiplier = 1.0

# Base parry damage as a percentage of maximum Posture.
# Innate EHP controls the parry/finisher penalty.
# Default: 6.0 | Range: 0.0-100.0
parry_percent = 6.0


[regeneration]

# Seconds before Posture regeneration starts.
# Default: 10.0 | Range: 0.0-60.0
delay_seconds = 10.0

# Percentage of maximum Posture regenerated per second.
# Default: 2.0 | Range: 0.0-100.0
percent_per_second = 2.0


[entities]

# Per-entity maximum Posture multipliers.
# Overrides maximum_posture_multiplier for the specified entity.
# Default: [] | Multiplier range: 0.01-100.0
# Example: ["cataclysm:ignis=0.25"]
posture_multipliers = []

# Per-entity parry damage multipliers after innate-EHP scaling.
# Default: [] | Range: 0.0-100.0
# Example: ["cataclysm:ignis=0.25"]
parry_multipliers = []

# Entities completely excluded from EFPI.
# Default: []
# Example: ["minecraft:zombie"]
entity_blacklist = []


[compatibility]

# Automatically add EFPI compatibility to supported mobs that do not already
# have an Epic Fight patch.
# false = only mobs already patched by Epic Fight or another mod are supported.
# Default: true
automatic_compatibility = true

# If non-empty, automatic compatibility is limited to these mod IDs.
# Default: []
# Example: ["cataclysm", "irons_spellbooks"]
mod_allowlist = []

# Mod IDs excluded from automatic compatibility.
# Denylist takes priority over allowlist.
# Default: []
# Example: ["some_mod"]
mod_denylist = []
```

`posture_multipliers` replaces `maximum_posture_multiplier` for the specified entity rather than multiplying it again. `parry_multipliers` affects only parries and uses 0.0 to disable parry posture damage for a particular mob.

Automatic compatibility policy works as follows:

- `automatic_compatibility = false`: EFPI does not add lightweight patches to new mobs. Mobs already patched by Epic Fight or another addon can still use EFPI posture when otherwise eligible.
- An empty `mod_allowlist` imposes no allowlist restriction.
- A non-empty `mod_allowlist` limits third-party automatic compatibility to those mod IDs.
- `mod_denylist` always excludes listed third-party mod IDs and takes priority over the allowlist.
- Vanilla `minecraft` entities are not filtered by the mod allow/deny lists.
- Existing Epic Fight/third-party patches are never removed by these automatic-compatibility filters.

Restart after changing automatic compatibility policy so patch ownership is rebuilt consistently.

## Multiplayer and dedicated servers

Posture state and compatibility policy are server-authoritative. EFPI 3.0.0 uses network protocol `3`; incompatible older protocol builds are rejected.

Dedicated-server renderer discovery is stored at:

```text
config/epicfight_posture_integration/cache/renderer-discovery.json
```

The cache uses schema version `2`. It is generated state, not a user configuration file. A trusted operator client reports renderer compatibility; when pending discovery data changes, restart the server to promote it to active data.

## Upgrading from 3.0.0

3.0.1 retains the 3.0.0 config location and compatibility/network format. Existing `posture_damage_multiplier` values now scale the new HP-normalized attack formula; retune custom non-default values. The new optional `entities.parry_multipliers` defaults to empty. No renderer-discovery cache or network protocol reset is required.

## Upgrading from 2.0.0 / development builds

3.0.0 intentionally does not migrate older server config or renderer-discovery cache layouts. Back up any values you want to keep, delete/regenerate the old config/cache, and reapply desired settings using the final 3.0.0 keys above.

The old GeckoLib-only compatibility terminology is replaced by the shared automatic compatibility pipeline for supported ModelPart, GeckoLib, and Lionfish humanoid backends.

## Client configuration

Client settings are stored at:

```text
config/epicfight_posture_integration/client.toml
```

They can also be changed from `Mods -> Epic Fight Posture Integration -> Config`. Execution Sound Volume is client-side only and defaults to 70%.

## Client rendering performance

EFPI-generated runtime compatibility meshes stay on Epic Fight's CPU skinning path instead of allocating per-mesh compute-shader resources. Ownership is marked explicitly around EFPI mesh-bake operations; native Epic Fight meshes and meshes created by other mods keep their normal compute-shader behavior.

On affected Epic Fight compute-shader + AMD OpenGL configurations, Minecraft can receive a very high volume of the same OpenGL debug callback. EFPI filters only the observed `API / ERROR / id 1005 / HIGH` signature before it reaches Minecraft's debug-message buffer and Log4j. Compute-shader rendering itself remains enabled, and all other OpenGL debug messages remain visible.

## Compatibility notes

Iron's Spells 'n Spellbooks and L_Ender's Cataclysm are tested compatibility targets for supported humanoid mobs. This does not imply universal support for every entity, renderer, armor implementation, custom layer, or framework-specific render path.

Automatic compatibility is deliberately fail-closed: unsupported, ambiguous, conflicting, or unsafe layouts are skipped rather than guessed.
