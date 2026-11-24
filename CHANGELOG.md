# Changelog

Each release lists **Added**, **Changed**, **Fixed** and **Removed** items. A **Note** marks anything that requires action when updating.

## 3.3.0 (unreleased)

**Note:** the network protocol changed. Update the server and every client together; new config keys are added to existing files automatically.

### Added
- Weapon-based execution range: guard break execution and back assassination ranges now depend on the held weapon's Epic Fight reach, so addon weapons are covered automatically. A sword keeps Combat Evolution's ranges (4 and 2 blocks); shorter and longer weapons scale between 0.75x and 1.5x, and large mobs get extra range. Client prompts use the same ranges.
- `posture.execution_range_scaling_enabled` (default `true`) switches this off to restore Combat Evolution's ranges; `posture.execution_range_multiplier` (default `1.0`, 0.5–2.0) scales both ranges. To disable executions or assassinations, use Combat Evolution's own settings.
- `posture.projectile_defense_posture_multiplier` (default `0.0`) scales Posture damage from parrying or guarding projectiles.
- A chat message after login when the server's mod version differs from the client's (English and Japanese).
- A warning in the log when `parry_base_min_percent` is higher than `parry_base_max_percent`.
- Links to the CurseForge page and the issue tracker in the mod list and on version mismatch screens.

### Changed
- With `guard_posture_damage_enabled`, Impact Guard now damages Posture with every weapon, including weapons that could parry (for example longswords and tachis), because Impact Guard never parries. Plain Guard still excludes parry-capable weapons.
- Parrying or guarding a projectile no longer damages the shooter's Posture by default, so ranged mobs are not guard broken from a distance. Set the new multiplier to `1.0` for the previous behavior.
- Fully recovered mobs that were never guard broken are no longer tracked by the server. Their bar stays full, and guard break resistance is unaffected.
- On NeoForge, a protocol mismatch now names the required mod version (`mod 3.3.0 or later`).
- Operator chat messages begin with `[Epic Fight Posture Integration]` and are shown in the operator's client language (English or Japanese).
- Forge and NeoForge share the Posture math, constants, network fields and the whole Posture flow (damage, guard breaks, executions, recovery) in one engine. Default balance and behavior are unchanged.

### Fixed
- A failure while drawing a guard broken mob (for example a GeckoLib model or a third-party render layer error) no longer crashes the game. That mob type falls back to normal rendering until resources are reloaded, and the error is logged once.
- An error inside a third-party render layer can no longer leave the render matrix stack unbalanced ("Pose stack not empty" crash).
- Errors in the Posture HUD or in server-side Posture hooks (damage, tick, parry, guard, execution start) are logged once and skipped instead of crashing.
- NeoForge now declares the supported GeckoLib range (4.5 to below 5); an incompatible GeckoLib is refused at startup instead of crashing during rendering.
- Operators on modpacks with many entity types could be disconnected by an oversized renderer report; reports are now split into chunks.
- NeoForge: players who start watching a mob during an execution now receive its Posture state.
- A reversed parry range no longer gives weak mobs less parry damage.
- Clients ignore malformed Posture updates.
- An unreadable renderer-discovery cache is kept as `renderer-discovery.json.bak` instead of being overwritten.

### Removed
- Unused legacy fields in the settings sync.

## 3.2.2

### Added
- `posture.guard_posture_damage_enabled` (default `false`) and `guard_posture_damage_multiplier` (default `0.25`): successful normal guards with weapons that cannot parry damage the attacker's Posture.

### Changed
- Compiled against Epic Fight 20.15.2 / Combat Evolution 2.3.6 (Forge) and Epic Fight 21.17.3.1 / Combat Evolution 2.3.3.1 (NeoForge).

## 3.2.1

**Note:** move entries from the experimental `client.toml` key `local_entity_blacklist` to `entities.entity_blacklist` in `server.toml`.

### Changed
- `entities.entity_blacklist` also stops client-side model inspection and patch preparation before server settings arrive. Client and server blacklists are combined.
- `automatic_compatibility = false` fully disables automatic patches on that installation; a server cannot override a client's local `false`.

### Fixed
- Craft of the Wild's Stone Talus could fail to spawn: multipart entities and their parts no longer receive automatic patches.
- An error in one mob's automatic registration or multipart check no longer breaks other registrations.

## 3.2.0

### Added
- Support for Epic Fight 20.15.1–20.15.x on Forge (20.15.0 is excluded).
- One diagnostics report per loader (`forge-diagnostics.tsv` / `neoforge-diagnostics.tsv`).
- Rig regression fixtures shared by both loaders, with optional capture of real models.

### Changed
- Dedicated servers apply new compatibility immediately to new and already-loaded mobs, without a restart.
- An operator's client requests a new renderer scan after a resource reload (e.g. F3+T).
- Reports from multiple operators are kept separately; conflicting results disable the affected mob types.
- Only the reporting operator receives a short chat notice, and only when something changed or failed.
- Unchanged reports skip the live refresh; only newly approved mob types are refreshed.

## 3.1.0

**Note:** custom multipliers are not migrated. Back up and regenerate `server.toml`.

### Added
- NeoForge 1.21.1 edition with Posture, HUD, guard breaks, executions and automatic compatibility.
- Separate global and per-mob multipliers for attack and parry Posture damage.

### Changed
- Forge and NeoForge share Posture logic and model conversion.
- Renderer discovery starts when a connected player is made operator.
- Base attack coefficient raised from 0.54 to 0.75; minimum parry damage raised from 8% to 10% (maximum stays 22%).
- Regeneration delay restarts on any actual Posture loss.
- JAR names include the loader and Minecraft version.

### Removed
- The maximum-Posture multiplier (fixed at 1.0).

Earlier releases are available in the repository's Git tags.
