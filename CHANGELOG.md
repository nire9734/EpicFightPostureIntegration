# Changelog

## 3.1.0 (unreleased)

Changes since the `3.0.2` Forge branch:

- **Added NeoForge 1.21.1 support** alongside Forge 1.20.1, including Posture, HUD, guard breaks, executions and automatic mob compatibility.
- **Unified both editions:** shared Posture calculations and state management, plus ModelPart, GeckoLib and Lionfish model conversion and rendering helpers. Loader-specific API adapters remain separate.
- **Improved compatibility:** aligned special-state handling and model/renderer discovery, with per-mob coverage diagnostics and conservative handling of unsupported models.
- **Improved dedicated-server setup:** renderer discovery starts when a connected player is granted OP, without requiring that player to reconnect.
- **Simplified configuration:** fixed the maximum-Posture multiplier at 1.0; added independent global and per-mob attack/parry Posture damage multipliers. Regeneration delay now resets on actual Posture loss from either source.
- **Adjusted Posture balance:** raised the base normal-attack coefficient from 0.54 to 0.75 and the minimum parry Posture percentage from 8% to 10%, keeping the maximum at 22%.
- **Standardized distribution:** both editions use version 3.1.0 and include the loader and Minecraft version in the JAR filename.

Existing custom multiplier settings are **not migrated automatically**. Back up your configuration and regenerate `server.toml` to obtain the new keys and balance defaults.

Earlier public releases are available in the repository's Git tags.
