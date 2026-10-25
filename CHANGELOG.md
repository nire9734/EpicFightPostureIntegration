# Changelog

## 3.2.0 (in development)

- **Faster live compatibility updates:** unchanged OP reports no longer repeat server-side registration or scan loaded entities. New approvals refresh only affected mob types; failed registration or patch refresh attempts remain retryable, and registry rebuilds invalidate cached success.
- **Unified diagnostics:** one `forge-diagnostics.tsv` or `neoforge-diagnostics.tsv` report combines entity registry coverage, renderer scans, posture eligibility/preflight reasons, loaded mod versions and observed guard-break/execution outcomes. Untested entities stay explicitly marked as such; CE refusal before the execution-start event remains unobservable. Reports do not enable unsupported mobs.
- **Dedicated-server discovery:** apply newly confirmed, conflict-free renderer compatibility immediately to both new spawns and already-loaded mobs without a server restart on Forge and NeoForge.
- **Safe live registration:** preserve patches owned by Epic Fight addons and disable EFPI-owned compatibility when renderer reports conflict. Existing mobs stay in place without being replaced or despawned.
- **Conditional OP notifications:** only the operator whose renderer report changes compatibility or encounters a problem receives a brief in-game notice. No message is sent for unchanged reports or to other players.
- **Multiple-OP safety:** each operator's renderer observations are retained independently, and conflicting observations disable the affected automatic compatibility until resolved.
- **Resource-pack reloads:** an OP's client automatically requests a fresh authorized renderer scan after its renderer resources are rebuilt (for example, F3+T); no reconnect or dedicated-server restart is needed.
- **Shared discovery pipeline:** Forge and NeoForge now use the same OP nonce/cooldown management and report-validation, filtering, activation, existing-mob refresh coordination, and notification decisions.

## 3.1.0

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
