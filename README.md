# Epic Fight Posture Integration

Epic Fight Posture Integration adds a posture system to Epic Fight entities that are compatible with Combat Evolution's execution system.

The mod does **not** replace or modify the existing Epic Fight AI, targeting, pathfinding, or attack behavior of supported entities. Instead, it adds a separate posture layer that works alongside Epic Fight and Combat Evolution.

Reduce an enemy's posture through attacks and parries, break their posture, and open them up for Combat Evolution executions.

## Features

- Adds posture to compatible Epic Fight entities
- Preserves existing Epic Fight AI and combat behavior
- Uses Epic Fight Impact values to calculate posture damage
- Applies posture damage when enemy attacks are successfully parried
- Supports posture breaks and Combat Evolution executions
- Supports standard humanoid Epic Fight entity patches
- Supports Combat Evolution `CustomExecuteEntity` implementations
- Automatically supports compatible modded entities
- Displays health and posture together in the combat HUD
- Supports multiplayer synchronization with server-authoritative posture state
- Includes configurable posture scaling and an entity blacklist
- Avoids unnecessary per-tick processing through lazy state creation and throttled synchronization

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer within the 47.x series
- Epic Fight 20.14.17 or newer compatible 20.14.x version
- Combat Evolution 2.2.8 or newer compatible 2.2.x version

Epic Fight and Combat Evolution are required on both the client and server.

## How It Works

Each supported entity receives a separate posture value. Maximum posture is calculated primarily from maximum health, armor, and armor toughness.

Epic Fight attacks deal posture damage based on their Impact value. Successful parries also damage the attacker's posture, allowing defensive play to create posture breaks without first reducing the target's health.

When posture reaches zero, the entity enters a broken state. If the entity supports Combat Evolution execution behavior, it can become vulnerable to execution. During execution, posture remains at zero. If the entity is not executed, it transitions into recovery and gradually returns to its normal state.

## Supported Entities

An entity can receive posture when it has a compatible Epic Fight patch and can participate in Combat Evolution's execution system.

The mod recognizes entities whose Epic Fight patch either:

- uses a humanoid armature compatible with Combat Evolution's standard execution system; or
- implements Combat Evolution's `CustomExecuteEntity` support.

This applies to both vanilla and modded entities. There is no vanilla-only restriction.

Entities already managed directly by Combat Evolution's own posture implementation are left to Combat Evolution so that duplicate posture systems, break handling, and HUD behavior are avoided.

## Modded Mob Compatibility

Per-mod compatibility code is normally not required.

If another mod or compatibility addon correctly gives an entity an Epic Fight patch that meets the supported execution conditions, Epic Fight Posture Integration can detect it automatically.

### Confirmed Compatibility

The following combination has been tested successfully:

- Illager Invasion
- Epic Fight - Mod Compat

Other Epic Fight-compatible mob mods may also work automatically. Compatibility ultimately depends on the Epic Fight patch and Combat Evolution execution support provided to the entity.

## Shields and Guard Breaks

When an entity's posture is broken, active item use such as shield blocking is interrupted. The same check is performed again when execution begins.

The shield itself is not removed or damaged by this system. After the break or execution state ends, the entity's normal AI may use the shield again.

## HUD

Supported entities use a combined Health + Posture display. The HUD can appear when the entity has taken health damage, its posture has been reduced, it is posture-broken, it is being executed, or it is recovering from a posture break.

This allows posture damage caused only by parries to remain visible even while the enemy is still at full health.

## Server Configuration

The server configuration is stored per world at:

```text
<world>/serverconfig/epicfight_posture_integration-server.toml
```

Example:

```toml
[posture]

# Global maximum posture multiplier.
# 1.0 = default
# 0.5 = 50%
# 2.0 = 200%
global_multiplier = 1.0

# Minimum maximum posture an eligible entity can have.
minimum_posture = 1.0

# Whether Armor and Armor Toughness affect maximum posture.
use_armor_scaling = true

# Entities that will not use the posture system.
# Use entity IDs in the format "namespace:entity".
entity_blacklist = []
```

`global_multiplier` multiplies the final calculated maximum posture. `minimum_posture` sets the lowest possible maximum posture after scaling. When `use_armor_scaling` is disabled, armor and armor toughness do not contribute to posture scaling.

Both vanilla and modded entity IDs can be added to `entity_blacklist`. Unsupported entities do not need to be blacklisted; they are ignored automatically.

## Multiplayer

Posture is controlled by the server. Clients receive synchronized posture information for entities they are tracking.

Important state changes such as posture damage, parries, posture breaks, execution start, recovery start, and return to normal state are synchronized immediately. Continuous recovery uses reduced-frequency synchronization to avoid unnecessary network traffic.

## Performance

Posture state is created only when needed rather than being initialized for every possible entity in the world.

The implementation also avoids repeated reflective method discovery, unnecessary Epic Fight patch lookups, maximum-posture recalculation every tick, per-tick posture synchronization, and duplicate Combat Evolution posture processing.

## Known Limitations

- Compatibility depends on the Epic Fight patch provided to an entity.
- A visually humanoid mob is not automatically compatible.
- Entities without a valid Epic Fight patch cannot receive posture.
- Execution compatibility ultimately depends on Combat Evolution.
- Some third-party Epic Fight compatibility addons may implement unusual guard, stun, or animation behavior that requires additional testing.
- Compatibility with every Epic Fight addon or mob mod cannot be guaranteed.

## Building From Source

Java 17 is required.

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

The generated JAR is placed in `build/libs/`.

## Reporting Issues

When reporting a compatibility issue, include the Minecraft, Forge, Epic Fight, Combat Evolution, and Epic Fight Posture Integration versions; the affected entity ID; the mod that adds the entity; any Epic Fight compatibility addon used; and relevant logs. For performance problems, a Spark profiler result is also useful.

## Credits

Epic Fight Posture Integration is an unofficial third-party addon.

- **Epic Fight** — Antikythera Studios
- **Combat Evolution** — ShelMarow

This project is not affiliated with or endorsed by the developers of Epic Fight or Combat Evolution. Epic Fight and Combat Evolution remain separate projects and are distributed under their respective licenses.

## License

Epic Fight Posture Integration is licensed under the **GNU General Public License v3.0 only (GPL-3.0-only)**.

See the `LICENSE` file for the full license text.
