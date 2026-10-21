# Epic Fight Posture Integration

Epic Fight Posture Integration (EFPI) adds a server-authoritative posture system to compatible Epic Fight mobs and connects posture breaks to Combat Evolution executions.

EFPI preserves the source mob's normal AI and ordinary rendering. Automatic compatibility uses lightweight Epic Fight patches only when needed for posture-break and execution-related states, and existing Epic Fight/third-party patches take priority.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10+ within 47.x
- Epic Fight 20.14.17 through compatible 20.14.x releases
- Combat Evolution 2.2.8 through compatible 2.3.x releases

GeckoLib, Lionfish/Citadel-style libraries, Iron's Spells 'n Spellbooks, and L_Ender's Cataclysm are optional compatibility targets, not hard dependencies unless another installed mod requires them.

A tested combination uses Forge 47.4.10, Epic Fight 20.14.17, Combat Evolution 2.3.1, and GeckoLib 4.8.4 when Gecko compatibility is needed.

## Features

- Server-authoritative posture, posture breaks, and Combat Evolution execution integration.
- Automatic compatibility for supported vanilla `HumanoidModel`, extended humanoid ModelPart/GeckoLib/Lionfish layouts, and vanilla-style `SpiderModel` entities.
- Target-proportioned humanoid retargeting for posture/execution states while preserving normal mob behavior.
- Health + Posture HUD.
- EHP-based maximum posture, linear observed-HP-loss attacks, level-stable innate-EHP parries, Impact-weighted direct attacks, and configurable regeneration.
- Per-entity maximum-posture overrides, entity blacklist, and mod-level automatic-compatibility allow/deny filtering.
- Dedicated-server renderer discovery with generated cache and multiplayer policy synchronization.

## Posture calculations (3.0.1)

Posture is a separate, server-authoritative resource. Normal attacks and successful parries lower it; reaching zero attempts Epic Fight's `NEUTRALIZE` guard-break animation, which Combat Evolution can use for executions. Damage, posture regeneration and execution damage are separate systems.

### 1. Effective health and maximum posture

EFPI estimates a target's **effective health (EHP)** from maximum HP, armor and armor toughness, using an **8-damage reference hit**. It is not simply the target's HP. With `clamp(value, low, high)` restricting a value to the given interval:

```text
armor_points = clamp(armor - 8 / max(0.01, 2 + toughness / 4),
                     armor * 0.2, 20)
received_fraction = clamp(1 - armor_points / 25, 0.2, 1.0)
EHP = maximum_health / received_fraction

P_base = 3.2 * max(0.01, EHP / 20)^0.7
P_max = clamp(max(1.0, P_base * M_max), 0.1, 1,000,000)
```

`M_max` is `posture.maximum_posture_multiplier` (default `1.0`), **replaced** by a matching entry in `entities.posture_multipliers` rather than multiplied twice. The formulas assume valid positive maximum HP; the implementation also guards against invalid/non-finite attributes and applies a minimum and safety cap.

For example, a 20-HP target with zero armor has `EHP = 20` and `P_max = 3.2` at default settings; a 40-HP target with zero armor has about `5.20` maximum posture. Increased armor/toughness raises EHP and therefore posture. Maximum posture uses **current total attributes**, including applicable attribute modifiers, and is refreshed as they change. Changes preserve the remaining posture percentage outside locked guard-break/execution states.

### 2. Direct attacks: actual HP loss, with no HP finisher

Direct hits use a `LOWEST` damage-event observation: posture damage uses the **real HP reduction** after damage processing, not the early event amount. Canceled/capped hits that do not reduce HP deal no attack posture damage. Let `H_max` be maximum HP, `H_before` be HP before the hit, `L` be observed positive HP loss, and `R` the repeat-break resistance:

```text
HP_loss_fraction = clamp(H_before / H_max, 0, 1)
                   - clamp((H_before - L) / H_max, 0, 1)
Impact_scale = clamp(0.7 + 0.3*sqrt(Impact / 3), 0.7, 1.5)
raw_attack_posture = P_base * 0.54 * Impact_scale
                     * HP_loss_fraction * posture_damage_multiplier
attack_posture_damage = raw_attack_posture / R
```

Nonpositive or invalid Impact generates zero direct-hit posture. Epic Fight attacks retain their actual Impact; other eligible direct player hits retain the existing bounded Impact estimate. Indirect/environmental hits remain excluded. `posture.posture_damage_multiplier` defaults to `1.0` and scales the internal **0.54** attack coefficient.

At Impact 3, maximum-posture multiplier 1.0 and zero regeneration, reducing an entire HP bar generates only **about 54% maximum posture**, before resistance. Equal fractions of HP loss always yield equal posture pressure regardless of remaining HP. For example, with `P_base = P_max = 3.2`, an Impact-3 hit removing 10 of 20 HP generates `0.864` posture; a second equal hit generates another `0.864`, with no late-HP finisher bonus. Increasing maximum posture by a global/per-entity multiplier still lowers attacks' relative effectiveness.

Unrelated healing between the event and the observed HP change can influence damage attribution.

### 3. Parry: registered EntityType baseline EHP

Parry uses **the EntityType's registered default maximum-health, armor and armor-toughness values** for innate EHP. This avoids level modifiers **and** per-instance `setBaseValue()` changes. If the registered supplier or individual attribute is unavailable, EFPI falls back to `AttributeInstance.getBaseValue()`, then a safe fallback. If another mod alters the **registered defaults**, that modified registered baseline will still be treated as innate: EFPI cannot recover pre-registration values.

```text
E_innate = EHP(EntityType default max health, armor, toughness)
parry_fraction = [p_min + (p_max - p_min)
                  * (20 / max(20, E_innate))^0.7] / 100

p_max = parry_base_max_percent  (default 22)
p_min = parry_base_min_percent  (default 8)

raw_parry_posture = P_max * parry_fraction
parry_posture_damage = raw_parry_posture / R
```

The parry percentage is **independent of current HP**. It starts at 22% for innate EHP 20 or below and approaches 8% for very large innate EHP; the EHP curve uses an exponent of `0.7`. Unlike direct attacks, parries use actual `P_max`, which continues to scale with live HP/armor and maximum-posture multipliers.

Cataclysm's standard Maledictus default attributes (420 HP, 10 armor, 0 toughness) give innate EHP about **552.63** and parry damage about **9.37%** maximum posture. Provided those EntityType defaults remain the same, a Soulrend Lv.250 Maledictus uses that same percentage. A mod-supplied different EntityType baseline must be validated in game.

### 4. Repeat-break resistance

Each successfully applied guard break increments a counter. Both attacks and parries divide their posture damage by:

```text
R = min(1 + completed_breaks * posture_break_resistance_step,
        posture_break_resistance_max)
```

Defaults: `step=0.5`, `max=2.0`. The first break uses 1x resistance, second 1.5x, third and later 2x; further breaks are possible, not prohibited. Failed or interrupted guard-break animations do not increment the counter. It resets on entity unload and is not persisted to NBT.

With **no regeneration or attack contribution**, the above baseline Maledictus needs approximately **11 parries for the first break**, **17 more for the second**, and **22 more for the third**. These are theoretical counts, not expected actual fight counts: normal attacks assist, while recovery can erase posture pressure if successful parries are spaced too far apart. EFPI does not change CE execution damage.

### 5. Recovery and regeneration

- **Ordinary regeneration:** after `regeneration.delay_seconds` without posture damage (default `15` seconds), regain `regeneration.percent_per_second` of `P_max` per second (default `2%`); implemented as a fixed amount per server tick.
- **After a guard break or execution:** use a distinct **60-tick (3-second) recovery** that fills posture from zero to maximum. No new posture break can occur during the guard-break, execution or recovery states.
- Execution damage and execution animation selection remain Combat Evolution's responsibility.

### Guard-break and execution eligibility (3.0.1)

A posture HUD does **not** imply that an arbitrary entity can be executed just
because its model resembles a humanoid. EFPI requires a distinct
`StunType.NEUTRALIZE` animation and an Epic Fight humanoid armature (or a
Combat Evolution `CustomExecuteEntity` contract). On the client, the HUD is
shown only when Combat Evolution considers the target compatible with the
local player's execution capability.

On reaching zero posture, EFPI accepts only an actually active
`NEUTRALIZE` animation. It does not require a separate `isStunned()` flag
to agree during animation transitions. EFPI no longer treats a `LONG`
hit reaction as an executable guard break. If the animation cannot start or ends almost immediately, EFPI returns to
posture recovery **without removing the HUD or blacklisting the entity**.
Such a failure alone does not establish that the mob is permanently
incompatible: temporary effects and third-party animations may interrupt it.
An ordinary end to a valid guard break starts recovery. A lethal hit does not
start a new guard-break stun after the target has died. Ordinary deaths clear
EFPI's special posture/rendering state, while an already-running Combat
Evolution execution retains its special state until the execution finishes.
Transiently rejected stun attempts are recorded only at DEBUG level, while
actual exceptions and inability to access required compatibility data remain warnings.

A purely static scan cannot guarantee a future execution: other mods may
cancel stuns, replace animations, apply temporary immunity, or alter execution
rules at runtime. The presence of a bar does not prove a future break will succeed. EFPI does
not infer permanent incompatibility from a single failed attempt. When a
specific modpack has targets confirmed to be unsupported, operators can
manually add their IDs to `entities.entity_blacklist`, without hardcoding
mod-specific behavior into EFPI.

### Generic datapack humanoid guard-break compatibility

Epic Fight 1.20.1's datapack-backed `CustomHumanoidMobPatch` may define SHORT
and LONG stun animations without defining `NEUTRALIZE`. Such a mob can still
have a valid humanoid armature and be an eligible Combat Evolution execution
target, yet a missing neutralize animation prevents posture breaks.

EFPI supplies the canonical `BIPED_COMMON_NEUTRALIZED` entry **only** when
all of these conditions hold:

- Existing Epic Fight patch is `CustomHumanoidMobPatch`
- Armature is `HumanoidArmature`
- A SHORT or LONG hit reaction is configured
- No usable NEUTRALIZE animation exists
- The entity type is not in EFPI's entity blacklist

The repair updates the existing Epic Fight provider's stun-animation table,
preserving datapack-provided animations, original mob AI, and renderer
ownership. Its guard-break animation is thereby visible both to Epic Fight
stun application and Combat Evolution execution checks. Remnant and Forlorn
(`soulsweapons`) were verified in game through the complete posture-break
and execution sequence.

This applies structurally, not by specific mob ID, but does **not** guarantee
execution on arbitrary custom mobs. Normal Combat Evolution execution
conditions and renderer compatibility still apply. The fix uses reflective
access to the private Epic Fight `CustomHumanoidMobPatch.provider` field;
check compatibility when upgrading Epic Fight. Successful repairs are logged at DEBUG level per entity type; failures
are logged as `EFPI generic humanoid neutralize fallback REJECTED`.

## Server configuration

Server policy is stored at:

```text
config/epicfight_posture_integration/server.toml
```

3.0.1 defaults (for newly generated configurations):

```toml
[posture]

# Multiplier for the maximum Posture of all entities.
# Default: 1.0 | Range: 0.1-100.0
maximum_posture_multiplier = 1.0

# Multiplier for attack Posture damage from final HP lost.
# Impact gives a bounded bonus; 1.0 uses the internal 0.54 attack coefficient.
# Default: 1.0 | Range: 0.0-100.0
posture_damage_multiplier = 1.0

# Maximum base parry Posture damage (% of maximum) for innate EHP <= 20.
# Innate EHP below 20 is clamped to 20 for parry scaling.
# Default: 22.0 | Range: 0.0-100.0
parry_base_max_percent = 22.0

# Minimum base parry Posture damage (%) as innate EHP approaches infinity.
# The high-EHP asymptote; independent of current HP.
# Default: 8.0 | Range: 0.0-100.0
parry_base_min_percent = 8.0

# Extra required posture-damage multiplier per successful break.
# 0.5 = first break 1x, second 1.5x, third and later 2x.
# Default: 0.5 | Range: 0.0-100.0
posture_break_resistance_step = 0.5

# Maximum required posture-damage multiplier.
# Affects both normal attacks and parries.
# Default: 2.0 | Range: 1.0-100.0
posture_break_resistance_max = 2.0


[regeneration]

# Seconds before Posture regeneration starts.
# Default: 15.0 | Range: 0.0-60.0
delay_seconds = 15.0

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

Posture state and compatibility policy are server-authoritative. EFPI 3.0.1 continues to use network protocol `3` from 3.0.0; incompatible older protocol builds are rejected.

Dedicated-server renderer discovery is stored at:

```text
config/epicfight_posture_integration/cache/renderer-discovery.json
```

The cache uses schema version `2`. It is generated state, not a user configuration file. A trusted operator client reports renderer compatibility; when pending discovery data changes, restart the server to promote it to active data.

## Updating from 3.0.1 to 3.0.2

3.0.2 improves the comments in newly generated `config/epicfight_posture_integration/server.toml` so each setting is easier to understand. **All configuration keys, defaults and gameplay behavior are unchanged.** Replace the 3.0.1 JAR with the 3.0.2 JAR on client and server.

Forge may keep existing configuration comments unchanged when upgrading. To get the clearer comments, back up your `server.toml`, then let Forge regenerate it and reapply any customized settings. Do not delete your configuration without saving those values first.

## Upgrading from 3.0.0 to 3.0.1

3.0.1 updates the **3.0.0 release on `main`**. The release incorporates the posture balance, execution compatibility and rendering fixes described in the [changelog](CHANGELOG.md).

- Replace the 3.0.0 JAR with `epicfight-posture-integration-3.0.1.jar` on both client and server. Do not install two EFPI JARs simultaneously.
- Retain `config/epicfight_posture_integration/server.toml` and the renderer-discovery cache. Multiplayer protocol `3`, cache schema `2`, configuration paths and supported dependency ranges are unchanged.
- 3.0.0's `posture.parry_percent` (default `6.0`) is replaced by `posture.parry_base_max_percent=22.0` and `posture.parry_base_min_percent=8.0`. Custom old parry values are **not automatically migrated**.
- Added repeated-break settings `posture.posture_break_resistance_step=0.5` and `posture.posture_break_resistance_max=2.0`. Existing configurations missing these keys receive the new defaults.
- `posture.posture_damage_multiplier` remains `1.0` by default, but the attack damage formula has changed. Retune customized 3.0.0 attack multipliers rather than assuming the old and new numbers are equivalent.
- The new regeneration delay default is **15 seconds**. Forge preserves an existing explicit `regeneration.delay_seconds=10.0` from 3.0.0, so set it to `15.0` manually (or regenerate the configuration after backing it up) to adopt the new default.
- Existing explicitly saved configuration values are not overwritten merely because defaults change. Back up any customized settings before updating; Forge may remove obsolete unrecognized keys when correcting the file.

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
