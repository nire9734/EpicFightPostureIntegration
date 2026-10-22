# NeoForge 1.21.1 port — pre-release validation

This is the unreleased NeoForge 1.21.1 edition of Epic Fight Posture
Integration. Its intended feature baseline is the **unreleased Forge 1.20.1
3.0.2**, not the public Forge 3.0.1 release. The code-parity pass is complete;
**build checks and in-game release validation must pass before publishing**.

On the `dev/multiloader` integration branch, this project builds alongside
Forge 1.20.1 and compiles shared code directly from `../common/src/main/java`.
See [the development and branch policy](../DEVELOPMENT.md). The independent
`port/1.21.1-neoforge` branch is retained as the original migration history.
Neither NeoForge nor Forge 3.0.2 has been released; `main` remains Forge 3.0.1.

The stages below are a chronological development log. Statements such as
"not yet implemented" or "do not publish" in *earlier stage descriptions*
describe those old builds, **not the current implementation**. See the final
release checklist for the remaining work.

## Current implementation and release gate

- Combat calculations, posture regeneration, repeated-break resistance, item
  interruption, assassination/execution transitions, state sync, sound volume,
  client settings screen and server-policy synchronization have corresponding
  Forge 3.0.2 behavior.
- The Forge and NeoForge builds share the same posture math, runtime state,
  and entity-blacklist/per-entity-multiplier parsing. NeoForge also caches
  parsed overrides on config load/reload, including whitespace and validation
  rules, and excludes EFPI-owned mobs when automatic compatibility is disabled
  without suppressing third-party Epic Fight patches.
- Vanilla, ModelPart, GeckoLib, Lionfish, and SpiderModel automatic compatibility
  use verified model preparation and fail-closed ownership checks. This is a
  **code implementation claim, not proof that every third-party Mob renders
  correctly**.
- Minecraft/loader-specific damage event timing remains different: NeoForge
  uses completed damage events; Forge observes final HP loss after the event.
  Both use actual committed HP reduction as the pressure input.

Before release: **both CI build/test jobs must pass**, followed by the
in-game checklist at the bottom of this document on the exact release mod set.

## Target versions

- Minecraft 1.21.1, Java 21 and NeoForge 21.1.264
- Epic Fight 21.17.3.1 (CurseForge 8175609)
- Combat Evolution 2.3.2 (CurseForge 9171333)

## Initial server adapter (historical milestone)

- Compiles the shared `PostureBalance`, `PostureDeathSafety`, rig/skeleton
  logic and corresponding JUnit tests from a single physical source tree.
  Both loaders now use the shared `PostureRuntimeState` class for damage,
  regeneration, breaks, execution state and recovery.
- Reads live Minecraft 1.21.1 mob attributes and registers a NeoForge
  COMMON config with Forge 3.0.2's 13 key names/defaults/ranges.
- Uses `LivingDamageEvent.Post` for actual HP lost from direct player attacks,
  then applies the shared bounded-Impact posture damage math.
- Uses `EntityTickEvent.Post` for regen delay, 2%-per-second recovery,
  maximum-posture refresh and guard-break animation polling.
- Ordinary regeneration waits `regeneration.delay_seconds` (15 seconds by
  default) after the **last actual Posture loss** from a direct attack or parry.
  A later Posture-reducing parry restarts the delay even without HP damage;
  HP damage with no Posture loss does not restart it. Guard-break recovery is separate.
- Only supports already Epic Fight-patched mobs with a distinct NEUTRALIZE
  animation. A failed stun does **not** increment repeated-break resistance.
- Hooks `TakeDamageEvent.Income.setParried(true)` to the shared parry scaling
  using mob-type default attributes rather than level-scaled instance stats.
- Hooks Combat Evolution execution-start events, preserves active executions,
  and enters posture recovery after execution ends.
- Excludes Combat Evolution-owned CEHumanoidPatch entities and configured
  entity blacklists from this first adapter.

**These integration paths compile against the target mod JARs but have not
been verified in a live client or dedicated server.**

## Initial milestone gaps (historical; several now implemented)

- Renderer-specific parity and advanced HUD polish
- Automatic compatibility registration for unsupported vanilla/modded mobs
- GeckoLib, Lionfish, humanoid retargeting and secondary model rendering
- Generic fallback for missing NEUTRALIZE animations
- In-game validation of parry, execution events and model interactions
- Full parity testing against the Forge 1.20.1 release

## Development build

This is an independent ModDevGradle 2.0.148 build within the multiloader
development tree; `../forge-1.20.1/` independently builds Forge 1.20.1
with Gradle 8.8.

```sh
cd neoforge-1.21.1
gradle clean build
```

Use Java 21 and Gradle 9.2.1. The CI workflow runs the same build and
shared JUnit tests. During local development, `localRuntime` provides
the required Epic Fight and Combat Evolution JARs to NeoForge run tasks
without bundling them inside EFPI's artifact.

The NeoForge config paths now match Forge 1.20.1:

- `config/epicfight_posture_integration/server.toml` (global common/server policy)
- `config/epicfight_posture_integration/client.toml` (client sound volume)

The previous NeoForge preview files (`epicfight_posture_integration-common.toml`
and `epicfight_posture_integration-client.toml`) are not migrated because this
port has not been released. The new locations are generated independently.

Renderer discovery is now prepared during the initial client resource load,
even before the first server policy arrives. Runtime usage remains gated by
the authoritative server policy; first singleplayer world entry must not
require F3+T or a second world entry to activate supported mobs.

**Historical note:** the original incomplete-port warning applied to this
earlier milestone. The current release gate is the checklist below.


## First client synchronization and overhead HUD

The server now uses a versioned NeoForge `CustomPacketPayload` for
entity-ID + UUID posture snapshots (current/max/broken/present).
Clients receive immediate attack/parry/guard-break/execution state changes,
periodic changes (at most once per four ticks), initial tracking snapshots,
and removal messages. IDs are checked alongside UUIDs to prevent stale
entity IDs displaying another mob's posture.

The client HUD Mixin uses Combat Evolution's health-bar texture for EFPI
posture targets, matching the Forge edition's health/posture layout. CE-owned
mob HUDs remain rendered by CE itself. For all mobs, including supported
bosses, overhead bars appear only when HP/posture/CE stamina is damaged or a hit was
recently received; targeting or status effects alone do not reveal the bars.
Epic Fight's `NONE` visibility setting remains authoritative. Bosses only
opt into the HUD if CE or EFPI can safely render their bar.

Client-side posture cache is cleared when the player disconnects or switches worlds.

## Automatic compatibility: conservative initial implementation

NeoForge uses Epic Fight 21.17's `EpicFightEventHooks.Registry.ENTITY_PATCH`
registry, **not** a NeoForge mod-bus `EntityPatchRegistryEvent`.
EFPI registers last (priority -10000) and skips mob types with a registered
Epic Fight provider or a datapack override. If the provider-map ownership
check is unavailable, EFPI fails closed and registers nothing.

For this development milestone the automatic biped candidate set is
**Minecraft Bogged** and **Giant**, both absent from Epic Fight 21.17's
built-in provider list. General discovery for modded renderers and model
retargeting is still pending. The lightweight provider does not replace AI,
normal hit reactions or the original renderer. Automatic compatibility can
be disabled with `compatibility.automatic_compatibility=false`, and is also
subject to EFPI's entity blacklist. Vanilla targets intentionally bypass the
mod allow/deny lists, just as in the Forge implementation.

**Caution:** this is compile-tested scaffolding. Animation rendering for
these mobs and operation on a real NeoForge dedicated server still need
validation. The current debug JAR remains INCOMPLETE.

## Missing NEUTRALIZE fallback for existing datapack humanoids

The 1.21.1 server adapter can now add Epic Fight's canonical biped guard-break
animation to an **existing** `CustomHumanoidMobPatch` when its datapack
provider lacks `StunType.NEUTRALIZE`. It preserves a provided NEUTRALIZE,
requires an actual humanoid armature plus an existing SHORT or LONG reaction,
and ignores explicitly blacklisted mobs. Unlike inserting a new entity patch,
this changes only the missing animation entry in the current authoritative
provider map. Reflection and table mutations fail closed with a warning.

The animation must still be accepted by Epic Fight's animator at runtime;
a rejection triggers recovery rather than a counted break.


## Experimental special-state rendering for Bogged

The first renderer-backed lightweight compatibility target is
`minecraft:bogged`. On the client, Epic Fight's
`ADD_PATCHED_ENTITY` hook checks the current source renderer is a
`HumanoidModel`, then asks Epic Fight's own vanilla transformer to produce
a runtime skinned mesh. Only successful model preparation enables the EFPI
renderer. It draws only while the server reports guard break/execution.
Ordinary rendering stays owned by the original game renderer.

The Giant's renderer has custom scale and is **not** replaced at this stage.
Likewise, modded humanoid/GeckoLib/Lionfish renderers are not yet registered.
Unknown or failed model preparations fail closed to normal rendering.
The initial Bogged renderer does not yet mirror armor/third-party layers
during special states, so its visual parity must be tested before release.


## Bogged assassination special-render fix

Combat Evolution can start assassination from behind **before any attack or
posture damage**. Earlier builds only entered EXECUTING if a posture state
already existed. As a result, a never-hit Bogged never sent the client its
execution snapshot, and the special renderer stayed inactive.

The server now creates a posture state upon an accepted execution-start event
for eligible mobs, marks it EXECUTING and immediately synchronizes it. A
short five-tick activation grace prevents the tick listener from prematurely
reverting to normal rendering before Combat Evolution's queued ExecutionTask
starts. Once execution ends, the usual 60-tick recovery applies.

The target should now animate during *assassination from behind*, as well as
during guard-break executions. Whether the player starts the assassination at
all remains controlled by Combat Evolution's weapon, skill, distance, angle
and entity eligibility checks. Validate both animations in-game.


## Giant special-animation rendering (experimental)

Giant now uses EFPI's guard-break/assassination skinned animation renderer
when its source renderer is the vanilla `GiantMobRenderer` and mesh baking
succeeds. The original renderer's `scale` method is invoked again after
Epic Fight's armature transform, so the Giant's built-in size should be
preserved with no guessed or fixed scale values. If renderer ownership or
model verification fails, EFPI keeps the source rendering. Bogged retains
its existing behavior. This needs in-game size/position and animation tests.


## Stage 9 — trusted dedicated-server renderer discovery

On a **dedicated NeoForge server**, EFPI now asks a level-2 operator client
for the most recent verified vanilla HumanoidModel renderer scan. Each
request carries a one-time nonce; only the operator's matching answer is
accepted. The report includes a SHA-256 client mod-set fingerprint, bounded
entity ID lists, inspected models and successfully prepared humanoid meshes.

The conflict-aware server store is derived from Forge 3.0.2's implementation.
It stores the report under
`config/epicfight_posture_integration/cache/neoforge-renderer-discovery.json`
and reconciles multiple environments: if one client sees a renderer as
unsupported while another claims it is humanoid, the target is excluded.
Reports from other clients never directly modify Epic Fight patches.

**Restart the dedicated server** after an accepted report to promote pending
vanilla HumanoidModel targets. EFPI then registers only the approved types
during the normal Epic Fight patch registry event, after checking existing
owners, user config and datapack patches. A mod-set fingerprint change clears
stale approvals. This first handshake does not permit spider/Gecko/Lionfish
models and does not make general clients authoritative.

## Experimental optional renderer backends

This branch now contains preliminary GeckoLib 4.9.3 and Lionfish API 3.1
model adapters, and a full SpiderModel special-state rendering path.
Dependencies are **compile-only** and are not required unless a mob that
uses the corresponding library is installed. The main registrar loads the
optional backends only when the matching mod is present.

- GeckoLib: static baked `GeoEntityRenderer` bind model -> shared semantic
  humanoid rig -> skin weights -> Epic Fight runtime skinned mesh.
- Lionfish: `AdvancedEntityModel` capture -> the same portable rig analyzer
  and weighted mesh baking.
- SpiderModel: existing Epic Fight SPIDER mesh/armature and
  `SPIDER_NEUTRALIZED` animation; no registered Epic Fight mob is overwritten.

For Gecko/Lionfish the model must pass conservative skeleton quality checks,
and the source mod's normal renderer is preserved outside break/execution
states. Extra armor, per-bone attachments, third-party layers and complete
spider execution-type replacement are **not yet implemented**.

Dedicated servers can receive three disjoint renderer classes through the
operator handshake (payload protocol 2), not just vanilla HumanoidModel.
A successful scan is only *pending* until the server restarts, and conflicts
between reports fail closed. Existing patched Mob types always take priority.

**CI verifies compilation and pure unit tests, not in-game GeckoLib,
Lionfish or SpiderModel animations. Do not publish this JAR as a release.**


## Lionfish parity follow-up (NeoForge 1.21.1)

Forge 3.0.2 trusts the shared Lionfish skeleton resolver and skin mesh
baker. The NeoForge port had incorrectly added a second
`overallConfidence >= 0.75` admission gate, which excluded valid
low-confidence meshes that the Forge version supported. The extra gate
is removed while the existing semantic/mesh validation remains mandatory.

The renderer scanner also no longer excludes all `MobCategory.MISC` types
before model inspection: some modded Mob implementations can use that
category. The runtime provider still creates a patch only for actual
`Mob` instances and respects existing patch ownership.

Optional backend invocation failures caused by a specific model no longer
disable the entire backend. Per-entity rejections and confidence are
available at DEBUG log level to identify any remaining incompatibilities.
GeckoLib's working mesh acceptance rule remains unchanged.


## Forge 3.0.2 automatic compatibility parity — stage 1

- GeckoLib's NeoForge-only `confidence >= 0.75` filter has been removed.
  As in Forge 3.0.2, `analyseAndBake()` must report a supported
  skeleton and a nonnull mesh; no second backend-specific cutoff applies.
- The shared GeckoLib and Lionfish semantic rig/mesh implementations remain
  intact. No known-working animations, scale logic or model conversion were changed.
- If both optional backends prepare the same entity, the entity is rejected
  rather than choosing the first one. This matches Forge
  `HumanoidBackendRegistry.scan` and avoids nondeterministic ownership.
- Added pure unit tests for zero/one/two backend candidates.


## Forge ↔ NeoForge per-entity compatibility comparison

The NeoForge client writes an ID-sorted diagnostic inventory after its
Epic Fight renderer registration scan:

`config/epicfight_posture_integration/cache/neoforge-renderer-coverage.tsv`

Each row is `entity_id<TAB>result`, e.g. `verified:geckolib`,
`verified:lionfish`, `excluded:existing_patch`, or
`rejected:backend_or_registration`. This file **never grants server-side
compatibility** and contains no account or player identifiers.

Enable DEBUG logging for EFPI on Forge 3.0.2, launch the same modded content
as closely as the two different Minecraft versions permit, then run:

```shell
python tools/compare_compatibility_reports.py --forge-log forge-debug.log \
  --neoforge-tsv neoforge-renderer-coverage.tsv --output missing-candidates.tsv
```

This comparison lists **candidates to investigate**, not a verified
Mob-support percentage: Forge's `accepted` line records a successful
model bake, not necessarily a renderer/patch registration, and different
mod versions can register different entity IDs.

Stage-1 parity also removes the GeckoLib-only 0.75 quality cutoff and
rejects ambiguous multiple-backend candidates, as Forge 3.0.2 does.


## Stage 12 — HierarchicalModel / ModelPart admission parity

Forge 3.0.2 also supports non-`HumanoidModel` modded mobs whose source
renderers expose `HierarchicalModel` and `ModelPart` body geometry.
The NeoForge scanner now includes the actual Forge
`ModelPartHumanoidMeshAdapter`, `ModelPartSourceRigAdapter`, and the
related semantic geometry selection/attachment-plan classes.

A model must still pass the shared Forge-style humanoid skeleton resolver
and skin-mesh bake. It is evaluated alongside GeckoLib/Lionfish candidates;
exactly one backend must succeed. Successful ModelPart entries are marked
`verified:modelpart` in the renderer coverage report. Existing Epic Fight
patches continue to take priority.

**Current limitation:** the static body is animated through the shared
rigged renderer. Forge's dynamic ModelPart attachments, held-item pose
corrections, and third-party render-layer replay remain to be ported.
Inspect ModelPart Mob scale, positioning and attachments before release.


## Stage 13 — rendered attachment/layer parity (development)

This stage ports *live rendering*, not only automatic model admission:

- **ModelPart / HierarchicalModel:** Forge's per-part attachment mesh
  renderer is now called after the retargeted body pass. Each special-state
  frame temporarily updates the original source model to read current
  attachment visibility, then restores the original ModelPart poses, scaling,
  draw flags and entity-model fields in a scoped `finally`. This is
  intended to preserve weapon/equipment switches on bosses.
- **GeckoLib 4.9.x:** the Forge bone layer bridge is adapted to
  Minecraft 1.21.1 and invoked reflectively after the base skinned mesh
  only for confirmed Gecko humanoids. It replays the renderer's
  per-bone `GeoRenderLayer` callbacks using the Epic Fight skin matrices,
  and synchronizes source bone anchors. The optional library is never
  class-loaded on installations without GeckoLib; individual layer
  failures are logged instead of disabling normal rendering.
- **Lionfish:** Forge's primary and secondary weighted layer mesh
  replacement utilities, source-model state snapshots, LRU variant cache
  and render-target capture are now compiled and covered by unit tests.
  The corresponding Lionfish model/render-target hooks are **not active
  yet**: 1.21.1 layer argument/RenderType contracts must be verified
  before intercepting and replacing a real source draw.

**In-game tests required:** Gecko per-bone held items/armor, ModelPart
dynamic weapon visibility, alternate geometry, correct alpha/texture,
the source renderer's next normal frame, and dedicated-server behavior.
Do not treat a successful CI as proof of runtime animation parity.

## Stage 14 — Lionfish layer replay (experimental)

Lionfish `LivingEntityRenderer` extra layers now run in verified special
states. Compatible parent redraws use the primary skinned mesh, deformable
secondary models use a cached weighted mesh, and supported remaining layer
geometry follows per-part Epic Fight poses. The hook converts 1.21.1's ARGB
color, captures source RenderType and restores the source model after replay.
Renderer reloads invalidate the layer caches; classes stay optional.

**Live Lionfish/Cataclysm validation is required.** Successful compilation
alone does not confirm translucent effects, equipment or held-item alignment.

## Stage 15 — Spider/ModelPart parity follow-up

The original Forge procedural SPIDER victim clip is now compiled directly
into the NeoForge build and registered through the NeoForge animation event.
Combat Evolution's target-specific execution contract is attached both to
Epic Fight-owned spider patches and EFPI's lightweight spider patch, without
replacing normal rendering or enabling spider assassination.

ModelPart-backed custom humanoids now replay the source renderer's extra
RenderLayers in the skinned special state, using EF pose transforms for
supported source parts. Dynamic accessories and extra layers share a single
temporary preparation/restoration of the original model.

Both paths still require a real 1.21.1 test world; GeckoLib skinned armor,
addon-specific cape callbacks and cross-mod layer behavior remain unfinished.

## Stage 16 — GeckoLib skinned armor (experimental)

GeckoLib 4.9.3's ItemArmorGeoLayer now has a scoped, optional skinned-armor
replacement during EFPI special-state rendering. It uses Gecko's real
bone/model-part fit result and GeoArmorRenderer visibility hooks to bake
supported armor pieces onto the Epic Fight skeleton, with guarded fallbacks to
normal Gecko rendering when any step is unsupported. Armor cache entries are
bounded and cleared on renderer reload. The source Forge cache-key identity,
visibility and transform invariants are also covered by NeoForge unit tests.

This implementation is not yet validated in-game; mod-specific post-render
cape hooks and custom GeoArmorRenderer layers still need compatibility work.

## Stage 17 — Forge 3.0.2 combat and coverage parity

- The NeoForge damage listener now uses Epic Fight's offender resolution and
  reads Impact from the **actual Epic Fight damage source**, matching Forge
  3.0.2 instead of using the player's current main-hand Impact. Explicit
  zero-Impact EF attacks do not reduce Posture. Eligible ordinary player hits
  retain Forge's bounded damage-derived Impact fallback and excluded damage IDs.
- Eligibility now requires either a real humanoid armature or an explicit
  Combat Evolution `CustomExecuteEntity` implementation, in addition to a
  distinct NEUTRALIZE animation. A patched non-humanoid with an unrelated
  stun clip is no longer accidentally admitted by NeoForge alone.
- Dead entities have their posture state removed unless Combat Evolution is
  actively executing them, matching Forge's death-safety rules.
- `neoforge-renderer-coverage.tsv` now records the *actual* backend selected
  by the successful registration, not an inference from the model superclass.
  This makes per-Mob Forge/NeoForge comparison more reliable.

These changes affect NeoForge only. Forge 3.0.2 remains the reference
implementation. Both target versions still require separate in-game combat
and GeckoLib/Lionfish/ModelPart rendering tests.

## Stage 18 — runtime mesh ownership and outline-layer parity

- Runtime meshes created by the vanilla HumanoidModel, ModelPart, GeckoLib,
  and Lionfish adapters now use the same explicit construction-ownership scope
  as Forge 3.0.2. A narrowly scoped Epic Fight mixin disables compute-shader
  initialization only for EFPI-owned meshes; native Epic Fight and third-party
  meshes retain their normal GPU path. Secondary Lionfish models and Gecko
  armor were already wrapped by this ownership scope.
- Additional Lionfish/ModelPart layer replay also captures
  `OutlineBufferSource.getBuffer` (not just ordinary `BufferSource`), matching
  Forge's handling of glowing/outline passes.
- Source model preparation now follows Forge's mounted-mob body/head yaw
  adjustment before replaying attachments and render layers.

The changes are compile-level parity work, **not** a claim of tested 1.21.1
rendering. Validate a glowing Cataclysm mob, equipped GeckoLib humanoid,
mounted ModelPart mob, and world re-entry before release.

## Stage 19 — guard-break item cancellation and execution sound preference

- Guard breaks from direct attacks and parries now share a transition that
  releases a Mob's actively used item before applying Epic Fight NEUTRALIZE.
  If Epic Fight rejects or throws while applying the stun, the Mob enters
  recovery instead of remaining in a broken state. This follows Forge 3.0.2.
- Combat Evolution execution sounds `skill.execution1` and `skill.execution2`
  now obey a **client-only** volume multiplier, matching Forge's default of
  70%. Change `sound.execution_sound_volume_multiplier` in the NeoForge
  client config file (range 0.0–1.0); this does not change server sounds or
  affect other sound effects.
- The client config screen is accessible from the NeoForge Mods menu, with
  the same slider/reset/done controls and English/Japanese labels as Forge.
  Real-world testing of interrupted item use, both execution sounds,
  the settings screen, and dedicated-server loading is still required.


## Stage 20 — server policy synchronization and combat transition parity

- NeoForge computes attack-based posture pressure from
  `LivingDamageEvent.Post#getNewDamage()`, the actual health removed by
  the completed NeoForge damage sequence. Forge 1.20.1 observes the same
  committed health delta on the following entity tick because its damage
  callback runs before the final health mutation. The logical results match;
  the exact event-to-packet latency differs by API version.
- Item-use interruption on guard break, and immediate packet updates for
  attack/parry, execution entry, guard-break recovery, and completion of the
  60-tick recovery now follow Forge's transition semantics. Routine
  regeneration continues to use a four-tick change-detection interval.
- On multiplayer join, the server sends its current global posture
  multiplier, internal minimum/armor-scaling flags, automatic renderer
  compatibility switches, entity blacklist, and mod allow/deny lists.
  It broadcasts revised values after reloading the common server config.
  The logical client never substitutes its own common config for the
  server's compatibility decisions, and clears the last server policy on
  disconnect. Before receipt, compatibility defaults to disabled.
- As with Forge, a client can register candidate renderer providers ahead
  of the login packet, but runtime eligibility is checked against the
  authoritative server policy. This also works with integrated servers:
  the logical server continues to consult its local config.

These changes have CI compilation and shared unit-test coverage only;
multiplayer joining, live config reload, dedicated-server startup, and
specific execution/guard-break animations still need in-game checks.

## Final code-parity pass — settings, eligibility, and state synchronization

- A single source-shared entity-config parser now handles entity blacklist IDs
  and per-entity maximum-posture multipliers on **both** loaders. Entries
  tolerate surrounding whitespace; invalid IDs, NaN/infinite and out-of-range
  multipliers are warned about and ignored. Valid later duplicate overrides
  replace earlier ones. NeoForge caches parsed settings and refreshes them on
  config load/reload, matching Forge's behavior and avoiding per-Mob string
  parsing during combat.
- The server no longer leaves a stale posture HUD entry for an EFPI-owned Mob
  after the operator disables automatic compatibility, blacklists that Mob,
  or excludes its mod with the allow/deny lists. Existing Epic Fight/addon
  patches remain eligible unless explicitly blacklisted.
- No-op hits/parries that do not actually reduce the float-valued posture
  resource skip immediate change packets, matching Forge's state machine.
  Maximum-posture EHP and missing registered-attribute fallbacks are aligned.
- The workflow tests Forge and NeoForge independently, including shared
  parser/state tests and NeoForge policy tests; the workflow result must be
  reviewed before this build is considered release-ready.

## Release checklist (requires actual game clients)

Use matching Epic Fight / Combat Evolution versions for each Minecraft loader.
Do not put the Forge and NeoForge JARs in the same game instance. Start with
clean configs, keep any previous test world backed up, and repeat the same
cases against Forge 3.0.2 for behavior comparison.

1. **Startup + login:** singleplayer first entry, world exit/re-entry,
   resource reload, and a dedicated NeoForge server with a normal client.
   Supported mobs must work on the first entry; no crash or excessive normal
   log spam. Confirm server policy arrives on join.
2. **Combat:** player attacks and parries decrease posture based on actual
   HP/posture reductions, normal regeneration restarts only after posture
   loss, item use stops on guard break, resistance increases across repeated
   successful breaks, and both guard-break executions and rear assassinations
   animate correctly. Repeat on a high-HP/high-armor Mob.
3. **State sync:** verify HUD for a newly spawned and a previously saved Mob,
   with two clients tracking the same Mob, plus chunk unload/reload, death,
   execution ending, disconnect, and rejoin. No stale, swapped, or stuck HUDs.
4. **Configuration:** test `entities.posture_multipliers` with whitespace and
   duplicate entries, `entities.entity_blacklist`, automatic compatibility
   on/off and mod allow/deny lists. Reload the server config while connected:
   changes should apply without requiring client resource reload. Existing
   third-party Epic Fight patches must not be disabled by the automatic filter.
5. **Client features:** confirm the Mods settings screen, execution sound
   volume at 0%, 70% and 100%, and server-side setting precedence in
   multiplayer.
6. **Renderer backends:** Bogged/Giant; vanilla spider; GeckoLib humanoid with
   custom armor and cape; Cataclysm/Lionfish Mob with extra parts and outline;
   extended ModelPart Mob with a complex RenderLayer. Compare alignment,
   scale, held equipment, attachments, glowing passes, and the return to the
   original renderer when combat animation ends.
7. **Dedicated-server discovery:** with the supported mod set, verify trusted
   operator renderer reporting, approval cache creation, restart promotion,
   and conflict fail-closed behavior. Compare the per-backend renderer coverage
   TSVs with `tools/compare_compatibility_reports.py`.

Publish only after both build jobs pass and every applicable live test has an
observed result. Document any third-party Mob deliberately skipped because
its model cannot be transformed safely; skipped unsupported renderers should
continue using their normal renderer without breaking the game.
