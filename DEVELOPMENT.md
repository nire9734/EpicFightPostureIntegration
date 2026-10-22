# Multiloader development and branch policy

`main` contains the published **3.0.1** Forge 1.20.1 release. It is not the
development branch and must not be updated as part of the NeoForge port.

| Branch | Purpose |
| --- | --- |
| `main` | Published Forge 1.20.1 / 3.0.1 release |
| `3.0.2` | Unreleased Forge 1.20.1 development baseline |
| `port/1.21.1-neoforge` | Original standalone NeoForge migration history; keep for reference |
| `dev/multiloader` | Integration branch: both loaders and **one** set of shared sources |

Changes that affect both editions should be developed and tested on
`dev/multiloader`. Changes to `common/` are compiled from **the same physical
Java files** into both distributable JARs. As long as the older standalone
branches remain separate, they **do not** automatically receive changes from
this integration branch. Backport deliberately after testing; do not merge an
unfinished NeoForge adapter or overwrite the 3.0.1 release branch.

## Source tree

- `common/src/main/java`: portable posture math, runtime state, client state
  storage, rig/skeleton inference, retargeting, and the source-shared Epic Fight
  spider animation/armature adapters.
- `common/src/test/java`: corresponding shared unit tests.
- `src/main/java`, `src/main/resources`: Minecraft 1.20.1 Forge-specific code.
- `neoforge-1.21.1/src/main`, `neoforge-1.21.1/src/test`: Minecraft 1.21.1
  NeoForge-specific adapters, mixins, and tests.

**Do not copy shared classes back into loader-specific `src/main/java` trees.**
Both builds import `common/src/main/java` directly. Class packages remain
unchanged, so the compiled mod IDs and external interfaces are unaffected.

Both loaders now use `PostureRuntimeState` for posture-loss timing, break
state, execution state, and recovery. Forge's `PostureManager` and NeoForge's
event listeners remain loader-specific adapters. A change to the shared
state machine or posture math therefore requires one common implementation
change, followed by builds and tests for **both** loaders.

## Build and test

Forge retains ForgeGradle/Gradle 8.8 and **Java 17**:

```sh
./gradlew clean build
```

NeoForge retains ModDevGradle/Gradle 9.2.1 and **Java 21**:

```sh
gradle -p neoforge-1.21.1 clean build
```

Use the appropriate Gradle installation for each command. The GitHub Actions
workflows build both independently on `dev/multiloader`. Neither the
`common/` tree nor the tests are packaged as a separate runtime mod; common
production classes are included in each platform's own JAR, while test
classes are excluded from the production artifact.

Build/test success does not establish GeckoLib/Lionfish/ModelPart/SpiderModel
in-game rendering parity. Compare each backend and entity type with the
renderer coverage reports, and validate both guard breaks and executions
before releasing NeoForge 1.21.1.
