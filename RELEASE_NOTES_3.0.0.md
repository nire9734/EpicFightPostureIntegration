# Epic Fight Posture Integration 3.0.0

3.0.0 is a major compatibility and posture-balance update.

## Highlights

- Broader automatic humanoid compatibility across supported ModelPart, GeckoLib, and Lionfish/Citadel-style models.
- Target-proportioned humanoid retargeting with improved armor, attachment, layer, and special-state rendering behavior.
- New EHP-aware posture balance: dealt-damage-scaled direct attacks, EHP-scaled parries, shared health/finisher scaling, and constant delayed regeneration.
- New per-entity maximum-posture overrides.
- New mod-level automatic-compatibility `ALLOWLIST` / `DENYLIST` filtering.
- Network protocol `3` and renderer-discovery cache schema `2`.

## Default posture behavior

```text
maximum posture = 3.2 * (EHP / 20)^0.7
attack posture scale = (dealt damage / 8)^0.7
effective parry fraction = base parry fraction * (1 + (20 / EHP)^0.7)
parry baseline = 8%
full-health finisher multiplier = 0.25
zero-health finisher multiplier = 1.0
regen delay = 10 seconds
regen rate = 2% maximum posture per second
```

Direct hits and parries use the same EHP-aware health finisher modifier. The old absolute minimum parry posture damage has been removed.

## New server controls

- `posture.overrides.entity_multipliers`: replaces `global_multiplier` for specified entities and affects maximum posture only.
- `compatibility.mod_filter_mode = "DENYLIST"`: normal automatic compatibility except listed mod IDs.
- `compatibility.mod_filter_mode = "ALLOWLIST"`: automatic compatibility only for listed mod IDs.
- `mod_allowlist` / `mod_denylist`: third-party mod namespace filters for EFPI automatic compatibility.

## Upgrade notes

3.0.0 intentionally does not migrate the 2.0.0 server config or old renderer-discovery cache layouts. Back up values you want to keep, regenerate the config/cache, and reapply desired settings.

Client and server should use the same EFPI version. Protocol-2 builds are rejected by 3.0.0.

Tested compatibility targets include supported humanoid mobs from Iron's Spells 'n Spellbooks and L_Ender's Cataclysm. Support remains conservative and fail-closed for unsupported or ambiguous render/model layouts.
