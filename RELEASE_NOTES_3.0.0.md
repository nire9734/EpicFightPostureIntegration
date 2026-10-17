# Epic Fight Posture Integration 3.0.0

3.0.0 is a major compatibility and posture-balance update.

## Highlights

- Broader automatic humanoid compatibility across supported ModelPart, GeckoLib, and Lionfish/Citadel-style models.
- Target-proportioned humanoid retargeting with improved armor, attachment, layer, and special-state rendering behavior.
- New EHP-aware posture balance: dealt-damage-scaled direct attacks, EHP-scaled parries, shared health/finisher scaling, and constant delayed regeneration.
- Compact server config with high-level balance controls, per-entity maximum-posture overrides, entity blacklist, and mod allow/deny filtering.
- Network protocol `3` and renderer-discovery cache schema `2`.

## Default posture behavior

```text
maximum posture = 3.2 * (EHP / 20)^0.7
attack posture scale = (dealt damage / 8)^0.7
effective parry fraction = base parry fraction * (1 + (20 / EHP)^0.7)
parry baseline = 6%
full-health finisher multiplier = 0.25
zero-health finisher multiplier = 1.0
regen delay = 10 seconds
regen rate = 2% maximum posture per second
```

Direct hits and parries use the same EHP-aware health finisher modifier. The old absolute minimum parry posture damage has been removed. Lower-level formula constants are now internal so the public server config exposes only useful high-level tuning.

## Server controls

- `posture.maximum_posture_multiplier`: global maximum-Posture scale.
- `posture.posture_damage_multiplier`: scale around EFPI's established attack-posture balance; `1.0` is the default balance.
- `posture.parry_percent`: baseline parry percentage of maximum Posture.
- `regeneration.delay_seconds` / `regeneration.percent_per_second`: ordinary Posture regeneration.
- `entities.posture_multipliers`: per-entity maximum-Posture override; values from `0.01` through `100.0` are accepted.
- `entities.entity_blacklist`: entities completely excluded from EFPI.
- `compatibility.automatic_compatibility`: when false, EFPI adds no new lightweight patches and only already-patched eligible mobs are supported.
- `compatibility.mod_allowlist`: when non-empty, limits third-party automatic compatibility to listed mod IDs.
- `compatibility.mod_denylist`: excludes listed mod IDs and takes priority over the allowlist.

## Upgrade notes

3.0.0 intentionally does not migrate the 2.0.0 server config, intermediate development config keys, or old renderer-discovery cache layouts. Back up values you want to keep, regenerate the config/cache, and reapply desired settings.

Client and server should use the same EFPI version. Incompatible older protocol builds are rejected by 3.0.0.

Tested compatibility targets include supported humanoid mobs from Iron's Spells 'n Spellbooks and L_Ender's Cataclysm. Support remains conservative and fail-closed for unsupported or ambiguous render/model layouts.
