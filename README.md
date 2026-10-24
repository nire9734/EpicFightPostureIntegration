# Epic Fight Posture Integration

Epic Fight Posture Integration (EFPI) adds a server-authoritative Posture system to compatible Epic Fight mobs, with guard breaks and Combat Evolution executions.

## Supported editions (3.2.0 development branch)

| | Forge | NeoForge |
| --- | --- | --- |
| Minecraft | 1.20.1 | 1.21.1 |
| Loader | Forge 47.4.10+ (47.x) | NeoForge 21.1.219+ (21.1.x) |
| Epic Fight | 20.14.17–20.14.x | 21.17.3.1–21.17.x |
| Combat Evolution | 2.2.8–2.3.x | 2.3.2–2.3.x |
| Java for building | 17 | 21 |
| File | `epicfight-posture-integration-forge-1.20.1-3.2.0.jar` | `epicfight-posture-integration-neoforge-1.21.1-3.2.0.jar` |

Install **only the JAR matching your Minecraft version and loader**. Install EFPI and its required mods on both client and server. GeckoLib and Lionfish API are optional compatibility libraries, not EFPI hard dependencies.

## Features

- Posture damage from direct attacks and successful parries, guard breaks, and Combat Evolution executions.
- Combined health/Posture HUD and synchronized multiplayer state.
- Automatic compatibility for supported vanilla humanoids, ModelPart, GeckoLib, Lionfish and SpiderModel mobs. Existing Epic Fight patches take priority; unsupported models are skipped safely.
- EHP-based maximum Posture, delayed regeneration, and resistance to repeated guard breaks.
- Independent attack/parry damage settings, per-mob overrides, blacklists and mod allow/deny lists.

Normal attacks apply Posture damage based on **actual HP lost as a fraction of maximum HP**, adjusted by Impact. Parries instead use the mob type's original health and armor to determine a percentage of its current maximum Posture, so level-scaled mobs do not automatically take the same large parry percentage as weak mobs. Maximum Posture is EHP-based and no longer has a configurable multiplier.

## Configuration

Server settings are generated at `config/epicfight_posture_integration/server.toml`.

```toml
[posture]
attack_posture_damage_multiplier = 1.0
parry_posture_damage_multiplier = 1.0
parry_base_max_percent = 22.0
parry_base_min_percent = 10.0
posture_break_resistance_step = 0.5
posture_break_resistance_max = 2.0

[regeneration]
delay_seconds = 15.0
percent_per_second = 2.0

[entities]
attack_posture_damage_multipliers = []
parry_posture_damage_multipliers = []
entity_blacklist = []

[compatibility]
automatic_compatibility = true
mod_allowlist = []
mod_denylist = []
```

- Attack and parry multipliers are independent. Per-entity entries such as `["cataclysm:ignis=0.5"]` **replace** their corresponding global multiplier.
- Regeneration starts after the last actual Posture reduction, including a parry. Guard-break recovery is separate.
- An empty allowlist permits all mods; the denylist takes priority. These lists affect automatic compatibility, not existing Epic Fight patches.
- Client HUD visibility and execution sound volume are configured in `config/epicfight_posture_integration/client.toml` or the in-game config screen.

**Upgrading from 3.0.2:** Back up custom settings and regenerate `server.toml` to use the new attack/parry keys. The removed `maximum_posture_multiplier` and old per-entity multipliers are not automatically migrated. See [CHANGELOG.md](CHANGELOG.md).

## Compatibility and diagnostics

Automatic support depends on the mob's model, animations and other mods. A Posture bar does not guarantee that every third-party execution animation will succeed. For dedicated-server renderer discovery, EFPI maintains a generated cache under `config/epicfight_posture_integration/cache/`. Compare Forge/NeoForge renderer coverage reports and test guard breaks, executions, equipment and model layers in game before releasing a new modpack.

**Dedicated servers (3.2.0 development):** After an OP client submits a trusted renderer-discovery report, EFPI immediately registers approved compatibility for new spawns **and** patches compatible mobs already loaded in the world. No server restart, chunk reload, or entity replacement is required. Mobs whose Epic Fight patches belong to other mods are left untouched. Results and failures are recorded in the server log without sending in-game chat messages. This behavior is under testing and is not part of the released 3.1.0 version.

## Building

Both editions compile shared source files directly from `common/src/main/java`. Loader APIs, event hooks and specific render preparation remain in their respective project directories.

```sh
# Forge: Java 17, Gradle wrapper (Gradle 8.8)
cd forge-1.20.1
./gradlew clean build

# NeoForge: Java 21, pinned Gradle 9.2.1 bootstrap
cd ../neoforge-1.21.1
./gradlew clean build
```

Both build scripts download and verify their pinned Gradle distributions on first use; no system Gradle installation is required. On Windows, run `gradlew.bat clean build` inside the corresponding loader directory. Both builds run their associated tests. JARs are generated in each edition's `build/libs/` directory. Active development for the next release takes place on `dev/3.2.0`; the released 3.1.0 source remains on `main`.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
