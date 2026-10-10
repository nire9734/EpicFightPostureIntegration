# Changelog

## 1.1.0

- Added a client-side Combat Evolution execution sound volume setting.
- Added an in-game config screen accessible from `Mods -> Epic Fight Posture Integration -> Config`.
- Execution sound volume can be adjusted from 0% to 100% independently for each player.
- Default execution sound volume multiplier is 70% of Combat Evolution's original volume.
- Updated Combat Evolution compatibility through the 2.3.x series and built against 2.3.1.
- No posture balance changes.

## 1.0.0

Initial public release.

- Added posture to compatible Epic Fight entities.
- Integrated posture breaks with Combat Evolution executions.
- Added posture damage from Epic Fight Impact and successful parries.
- Added support for compatible humanoid patches and `CustomExecuteEntity` implementations.
- Added automatic support for compatible modded entities.
- Added Health + Posture HUD integration.
- Added server-authoritative multiplayer synchronization.
- Added server configuration for posture scaling, minimum posture, armor scaling, and entity blacklist.
- Added shield-use interruption during posture break and execution start.
- Prevented duplicate posture handling for entities already managed by Combat Evolution.
- Optimized posture state creation, attribute recalculation, Epic Fight patch lookups, and network synchronization.
