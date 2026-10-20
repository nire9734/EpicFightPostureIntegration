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
attack posture damage = 2.1 * P_base * Impact efficiency
                        * (F(u_after) - F(u_before))
                        * posture_damage_multiplier
```

The new default attack coefficient is **2.1**, with `posture_damage_multiplier = 1.0`. At Impact 1.8, direct attacks alone cannot completely deplete posture before a target dies if there is no other posture pressure (assuming equal base/max posture and no posture regeneration). Parries provide additional pressure.

The health-finisher curve `0.25 + 0.75*(1 - currentHealth/maxHealth)^2` applies to direct attacks against **all** mobs. Integrating it over the hit's actual HP-loss interval prevents high-damage RPG weapons from losing posture efficacy and makes hits approximately split-independent at equal Impact efficiency. An Epic Fight source with **zero Impact** remains excluded; eligible non-EF direct player damage receives a bounded derived Impact.

Parries intentionally work differently. They refer to **innate EHP** from base attribute values (unmodified max health, armor and toughness), not the mob's level/affix-scaled EHP:

```text
parry base fraction = (parry_base_min_percent
                     + (parry_base_max_percent - parry_base_min_percent)
                     * (20 / max(20, innate EHP))^0.7) / 100
parry damage = maximum posture * parry base fraction
             * innate-EHP health-finisher modifier
```

The maximum/minimum **base** parry endpoints are **18% at innate EHP 20 or below**, and **6% as innate EHP approaches infinity** (before finisher scaling). At innate EHP 20 the new value requires six parries instead of the previous nine, assuming no regeneration. The EHP exponent remains internal at 0.7. The parry finisher modifier is disabled at innate EHP ≤40, blended over 40–200, and fully active at 200+. It therefore further reduces parry damage against healthy, innately durable bosses. These endpoints describe the **base** fraction: a full-health boss may receive less than the configured high-EHP percentage after its finisher penalty. A mod that rewrites attribute base values instead of adding modifiers may also affect innate-EHP classification.

`maximum_posture_multiplier` and `posture_multipliers` still affect how much attack damage is needed to break posture; parries are intentionally a **percentage of maximum posture** and therefore their relative efficacy is unchanged by that multiplier. Execution damage remains Combat Evolution's responsibility.

After every **successful** posture break, the same per-mob counter increases the required posture damage for both attacks and parries:
```text
required damage multiplier = min(1 + completed_breaks * posture_break_resistance_step,
                                 posture_break_resistance_max)
actual posture damage = normal attack or parry posture damage / required damage multiplier
```
The default resistance step is **1.0** and the cap is **3.0**: the first break needs 1x, the second 2x, and the third and later 3x cumulative posture damage. A break is counted only if Epic Fight successfully applies the break stun. This resistance does **not** increase the maximum posture value or its ordinary regeneration rate; it instead reduces incoming posture damage. New breaks are blocked during the existing break/recovery state, preventing immediate re-breaks while the posture bar refills. The counter is kept in memory only and is reset when the entity leaves the level (including chunk unload); no NBT persistence, extra per-tick calculation, or network protocol change is required.

Ordinary regeneration starts after the configured delay and restores the configured percentage of maximum posture per second. Posture-break recovery is a separate state.

### Guard-break and execution eligibility (3.0.1)

A posture HUD does **not** imply that an arbitrary entity can be executed just
because its model resembles a humanoid. EFPI requires a distinct
`StunType.NEUTRALIZE` animation and an Epic Fight humanoid armature (or a
Combat Evolution `CustomExecuteEntity` contract). On the client, the HUD is
shown only when Combat Evolution considers the target compatible with the
local player's execution capability.

On reaching zero posture, EFPI now accepts only an actually active
`NEUTRALIZE` animation with Epic Fight's stunned state. It no longer treats
a `LONG` hit reaction as an executable guard break. If the animation cannot start or ends almost immediately, EFPI returns to
posture recovery **without removing the HUD or blacklisting the entity**.
Such a failure alone does not establish that the mob is permanently
incompatible: temporary effects and third-party animations may interrupt it.
The server logs a diagnostic warning on failed or immediately terminated
breaks (entity ID/UUID, patch class, expected and current animations,
and `isStunned`). These diagnostics can distinguish the failure's stage
without changing ordinary combat behavior. A confirmed guard break ending
normally also enters recovery.

A purely static scan cannot guarantee a future execution: other mods may
cancel stuns, replace animations, apply temporary immunity, or alter execution
rules at runtime. The presence of a bar does not prove a future break will succeed. EFPI does
not infer permanent incompatibility from a single failed attempt. When a
specific modpack has targets confirmed to be unsupported, operators can
manually add their IDs to `entities.entity_blacklist`, without hardcoding
mod-specific behavior into EFPI.

### Soul's Weapons diagnostic branch

The development branch `dev/soulsweapons-compat-diagnostics` contains temporary,
read-only `EFPI souls-diagnostic/scan`, `/server`, `/client` and `/break`
logging for `soulsweapons:remnant`, `soulsweapons:dark_sorcerer` and
`soulsweapons:forlorn`. Scan diagnostics are emitted even if no mobs spawn.
Server/client diagnostics appear the first time each type spawns and reaches
20 ticks, even if no posture bar is shown. Break diagnostics appear when a
neutralize animation is accepted. This logging does not try to stun mobs in
advance or blacklist failed instances.

A behavioral correction also aligns the broken-state check with Combat
Evolution's execution predicate: only the actual `NEUTRALIZE` animation
accessor identity is required, not a redundant `isStunned()` flag that can
disagree during transitions. Unsupported targets still cannot be guaranteed
executable without an in-game attempt; captured diagnostics will identify
the actual patch/renderer ownership and filter stage for the three mobs.

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
# Impact gives a bounded bonus; 1.0 uses the internal 2.1 attack coefficient.
# Default: 1.0 | Range: 0.0-100.0
posture_damage_multiplier = 1.0

# Maximum base parry Posture damage (% of maximum) for innate EHP <= 20.
# Innate EHP below 20 is clamped to 20 for parry scaling.
# Default: 18.0 | Range: 0.0-100.0
parry_base_max_percent = 18.0

# Minimum base parry Posture damage (%) as innate EHP approaches infinity.
# This is the asymptote at 0% target HP, before the finisher modifier.
# Default: 6.0 | Range: 0.0-100.0
parry_base_min_percent = 6.0

# Extra required posture-damage multiplier per successful break.
# 1.0 = first break 1x, second 2x, third and later 3x.
# Default: 1.0 | Range: 0.0-100.0
posture_break_resistance_step = 1.0

# Maximum required posture-damage multiplier.
# Affects both normal attacks and parries.
# Default: 3.0 | Range: 1.0-100.0
posture_break_resistance_max = 3.0


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

`posture_multipliers` replaces `maximum_posture_multiplier` for the specified entity rather than multiplying it again.

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

3.0.1 retains the 3.0.0 config path and compatibility/network format. The obsolete `posture.parry_percent`, interim `posture.parry_low_ehp_percent` / `posture.parry_high_ehp_percent`, and `entities.parry_multipliers` keys are no longer recognized. On loading an old TOML, Forge config correction adds `posture.parry_base_max_percent = 18.0` and `posture.parry_base_min_percent = 6.0` at their defaults. **Customized values of old parry keys are not migrated**; unknown keys may be removed when Forge rewrites the TOML. Do not delete the whole file: unrelated settings remain in place. The attack coefficient is now 2.1 (previous 3.0.1 development builds: 2.7, then 2.2), while `posture_damage_multiplier` still defaults to 1.0. If `posture_damage_multiplier` was manually set to 0.8 to approximate a previous balance, reset it to `1.0` after upgrading. Two new optional per-entity-break-resistance keys default to `1.0` and `3.0`; unlike the previous builds, second and later breaks now require progressively more posture damage. Other customized attack multipliers remain effective and may need retuning. No renderer-discovery cache or network protocol reset is required.

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
