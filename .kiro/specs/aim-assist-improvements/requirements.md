# Requirements Document

## Introduction

This feature enhances the existing **AimAssist** module of LionClient (a client-side-only
Minecraft 1.8.9 Forge utility mod). It is an enhancement to existing functionality, not a
from-scratch feature.

AimAssist always uses the visible, client-side rotation path: it moves the player's actual
rotation via `MouseGcdHelper.rotateBy`, visibly turning the camera. The previous selectable
rotation "Mode" (the silent server-side path) has been removed; there is now a single
rotation path and no mode setting.

The goals are twofold:

1. **Feel/smoothness** — make assisted rotation feel smooth and continuous to the local
   player by aligning rotation updates with Minecraft's render interpolation, consolidating
   the two ad-hoc smoothing implementations onto the existing `RotationState` easing helper,
   and removing frame-rate inconsistencies and white-noise jitter sources.
2. **Detection realism** — reduce statistical fingerprints that aim-pattern anti-cheat
   checks (e.g. Vulcan/Grim "Aim Constant", "Aim Modulo/Divisor") look for, primarily by
   varying the aim point within the target hitbox instead of always aiming at the exact eye
   center, and by drifting rotation speed smoothly over time rather than re-drawing
   white-noise speed values each tick.

The work also removes dead/contradictory code identified during analysis and must respect
the project's hard constraints (Java 8, MC 1.8.9 Forge, settings read at use-time, full
state reset on toggle, no per-frame allocation in render paths, no new dependencies,
validate with `compileJava`).

This is a fork. Anti-cheat bypass is **not guaranteed**; requirements target measurable
reductions in known fingerprints, not a guarantee of undetectability.

## Glossary

- **AimAssist**: The `AimAssistModule` being enhanced. It nudges the player's aim toward a
  nearby target while the attack button is held.
- **Player_Rotation_Path**: The single code path AimAssist uses to apply rotations. It moves
  the player's actual rotation via `MouseGcdHelper.rotateBy`, visibly turning the camera. The
  rotation-application code block is the one that invokes `MouseGcdHelper.rotateBy` with
  `yawChange` and `pitchChange`. (The previously documented `Server_Rotation_Path` and its
  `SERVER (Silent)` mode have been removed.)
- **Rotation_State**: The existing `com.lionclient.combat.RotationState` helper. It provides
  frame-rate-independent exponential-decay easing, per-axis independent step ratios,
  acceleration limiting, and micro-drift. It exposes runtime setters (`setSpeed`,
  `setRandomizationPercent`) and a reusable result buffer.
- **GCD**: The per-pixel rotation step Minecraft applies to real mouse input,
  `(sensitivity * 0.6 + 0.2)^3 * 1.2`, computed by `MouseGcdHelper.currentGcd()`. A rotation
  delta is **GCD-aligned** when it is an integer multiple of this step. The Player_Rotation_Path
  applies rotations through `MouseGcdHelper.rotateBy`, which is inherently GCD-aligned.
- **Aim_Point**: The 3D point on the target the module rotates toward.
  `KillAuraRotationUtils.getAimPoint(...)` can apply gaussian randomization within the target
  hitbox; AimAssist currently always uses the exact eye center.
- **Render_Interpolation**: Minecraft's per-frame interpolation between `prevRotationYaw/Pitch`
  and `rotationYaw/Pitch` used to render the camera between the 20 Hz client ticks.
- **Acceleration_Limit**: The configured maximum angular acceleration (deg/s²) multiplied by
  the elapsed step time (s), bounding how much a per-step rotation delta may change between
  consecutive steps.
- **Frame-rate independent**: Producing the same total rotation speed and feel regardless of
  rendered frames per second, by scaling per-step movement with elapsed time (deltaTime).
- **Elapsed_Time_Factor**: Time elapsed since the previous step normalized to a 60-steps-per-
  second baseline; the same factor used by the approach and fine-tune tracking phases.
- **Speed_Multiplier**: A per-step factor applied to base rotation speed to introduce natural
  speed variation over time.
- **Settings read at use-time**: Reading each setting's current value on every tick/use rather
  than snapshotting it, so in-game slider changes take effect immediately.

## Requirements

### Requirement 1: Smooth rotation aligned with render interpolation

**User Story:** As a player using AimAssist, I want assisted camera movement to look and feel
smooth at any frame rate, so that aiming does not stutter or fight the game's interpolation.

#### Acceptance Criteria

1. WHEN the Player_Rotation_Path updates the player rotation during a client tick, THE AimAssist SHALL set `prevRotationYaw` and `prevRotationPitch` so that the interpolated yaw and pitch advance monotonically across the full 0.0–1.0 Render_Interpolation range, with no rendered frame moving opposite the sign of the tick's net rotation delta.
2. THE AimAssist SHALL NOT move `prevRotationYaw`/`prevRotationPitch` a fixed fraction toward the current rotation as a smoothing substitute (the legacy `syncPrevRotationSmooth` 70% handoff).
3. WHILE a target is locked, THE AimAssist SHALL bound the change between consecutive per-step rotation deltas to the Acceleration_Limit, so that no single rendered step exceeds that bound.
4. WHEN Noise, Speed Randomization, and Random Skip Frames are disabled, THE AimAssist SHALL converge from a 45-degree offset to within the LOCK threshold (3 degrees yaw, 2 degrees pitch) in a total elapsed time that varies by no more than 15 percent across 20, 60, and 240 frames per second.
5. IF a computed rotation value is NaN or Infinity, THEN THE AimAssist SHALL skip the rotation update for that tick and preserve the current and previous rotation values.

### Requirement 2: Consolidate smoothing onto the Rotation_State helper

**User Story:** As a maintainer, I want the rotation path to use the shared Rotation_State
easing helper, so that smoothing logic is defined in one place and the helper is no longer
dead code.

#### Acceptance Criteria

1. WHILE a target is locked, THE AimAssist SHALL produce every assisted rotation update for the Player_Rotation_Path exclusively from the output of a single Rotation_State helper instance, computing its assisted rotation by no other means.
2. THE AimAssist SHALL contain no easing, per-axis ratio, or acceleration-limiting logic outside the Rotation_State helper, such that removing the Rotation_State helper would leave no alternate smoothing code path reachable from the Player_Rotation_Path.
3. WHEN the user changes the horizontal speed, vertical speed, or noise setting in-game, THE AimAssist SHALL, on the next assisted tick (within at most 50 ms), apply the new values by calling the Rotation_State runtime setters with the current setting values before stepping, without reconstructing the helper or restarting the module.
4. WHEN a target is acquired (transition from no locked target to a locked target), THE AimAssist SHALL initialize the Rotation_State start position from the player's live `rotationYaw`/`rotationPitch` so that the helper's initial current position equals the live camera rotation exactly (0-degree difference), and the first emitted rotation differs from the live camera rotation by no more than the helper's per-tick acceleration limit (no instantaneous snap).
5. THE AimAssist SHALL read the horizontal speed, vertical speed, and noise setting values at use-time each tick rather than snapshotting them into final fields or passing them once at helper construction time.

### Requirement 3: Smoothly varying speed randomization

**User Story:** As a player, I want rotation speed variation to drift naturally over time, so
that aiming does not look choppy from per-tick white-noise speed changes.

#### Acceptance Criteria

1. WHERE speed randomization is enabled, THE AimAssist SHALL compute each tick's Speed_Multiplier by incrementally drifting from the previous tick's Speed_Multiplier rather than re-drawing an independent value each tick.
2. WHERE speed randomization is enabled, THE AimAssist SHALL limit the change in Speed_Multiplier between consecutive ticks to at most 0.05 (5 percentage points).
3. WHERE speed randomization is enabled, THE AimAssist SHALL keep the Speed_Multiplier within the inclusive range [Speed Rand Min % / 100, Speed Rand Max % / 100], clamping back into range when the settings change at runtime.
4. WHEN AimAssist is enabled, THE AimAssist SHALL initialize the Speed_Multiplier to a defined starting value within the configured range (1.0 when in range, otherwise the nearest range bound).
5. WHERE speed randomization is disabled, THE AimAssist SHALL apply a Speed_Multiplier of exactly 1.0.

### Requirement 4: Frame-rate-independent lock behavior

**User Story:** As a player, I want the locked-on tracking phase to behave the same at any
frame rate, so that aim feel is consistent across machines.

#### Acceptance Criteria

1. WHILE the player is in the locked tracking phase (target offset within 3 degrees yaw and 2 degrees pitch), THE AimAssist SHALL scale per-step rotation movement by the Elapsed_Time_Factor, the same factor used by the approach and fine-tune phases.
2. THE AimAssist SHALL derive the locked-phase per-step movement from the Elapsed_Time_Factor and SHALL NOT apply a fixed per-tick drift fraction that ignores elapsed time.
3. WHEN Noise, Speed Randomization, and Random Skip Frames are disabled, THE AimAssist SHALL hold the locked-phase total rotation toward the target over a fixed wall-clock interval within 5 percent divergence across 30, 60, 120, and 240 frames per second.
4. IF the elapsed time since the previous step is zero, unavailable, or non-finite, THEN THE AimAssist SHALL clamp it to the same bounds used by the other phases and produce a finite rotation output.

### Requirement 5: Hitbox aim-point variation

**User Story:** As a player, I want AimAssist to aim at varied points within the target hitbox,
so that dead-center eye aim is not a constant fingerprint.

#### Acceptance Criteria

1. WHEN AimAssist computes the rotation to a target, THE AimAssist SHALL derive the Aim_Point using the gaussian hitbox randomization provided by `KillAuraRotationUtils.getAimPoint`.
2. WHEN AimAssist derives the Aim_Point, THE AimAssist SHALL constrain every coordinate of the Aim_Point to lie on or within the target's bounding box, producing no point outside that box.
3. WHERE aim-point randomization resolves to a zero offset, THE AimAssist SHALL set the Aim_Point to the target eye center (the geometric center of the target bounding box at eye height).
4. WHILE consecutive ticks target the same entity, THE AimAssist SHALL constrain the change in Aim_Point between two consecutive ticks so that the resulting yaw delta and pitch delta each remain less than or equal to the configured rotation step (in degrees).
5. IF the targeted entity differs from the entity targeted on the previous tick, THEN THE AimAssist SHALL recompute the Aim_Point for the new target without applying the consecutive-tick stability bound of criterion 4.
6. IF the target bounding box is degenerate (zero or negative width or height) or the computed Aim_Point cannot be constrained inside it, THEN THE AimAssist SHALL fall back to the target eye center and retain the previously valid rotation.

### Requirement 6: Remove dead, contradictory, and server-mode code

**User Story:** As a maintainer, I want unused, contradictory, and now-removed-server-mode code
removed, so that the module's behavior matches its single visible rotation path and is easier
to reason about.

#### Acceptance Criteria

1. THE AimAssist source SHALL NOT contain the `appliedYaw` and `appliedPitch` local variables within the Player_Rotation_Path (the rotation-application block that invokes `MouseGcdHelper.rotateBy` with `yawChange` and `pitchChange`).
2. THE AimAssist source SHALL NOT contain a `getSpeedMultiplier` method, and SHALL NOT contain any remaining call site that references `getSpeedMultiplier`.
3. THE AimAssist source SHALL NOT contain a declaration of the `wasOvershooting` field, and SHALL NOT contain any read or write reference to `wasOvershooting` in any method body (including `onEnable`/`onDisable` reset logic).
4. WHERE the rotation behavior defined by Requirement 1 is the active behavior for the Player_Rotation_Path, THE AimAssist source SHALL NOT contain the `syncPrevRotationSmooth` or `syncPrevRotation` helper method declarations, and SHALL NOT contain any call site that references either helper.
5. THE AimAssist source SHALL NOT contain the `RotationMode` enum declaration, and SHALL NOT contain the "Mode" rotation-mode setting or any reference to either identifier.
6. THE AimAssist source SHALL NOT contain the `applyServerRotations` method, the `serverTargetYaw` and `serverTargetPitch` fields, or any SERVER-mode branch logic within `onClientTick`, such that `onClientTick` applies rotations only through the Player_Rotation_Path.
7. WHEN the AimAssist source is compiled with the Java 8 toolchain after the removals in criteria 1 through 6, THE build SHALL complete with zero compilation errors and zero references to the removed `appliedYaw`, `appliedPitch`, `getSpeedMultiplier`, `wasOvershooting`, `syncPrevRotationSmooth`, `syncPrevRotation`, `RotationMode`, `applyServerRotations`, `serverTargetYaw`, and `serverTargetPitch` identifiers.

### Requirement 7: Preserve existing targeting, gating, and settings behavior

**User Story:** As a player, I want all current AimAssist behavior other than the targeted
improvements to keep working, so that the enhancement does not regress targeting or gating.

#### Acceptance Criteria

1. WHILE AimAssist is enabled and actively assisting, THE AimAssist SHALL select as its target the eligible player whose required look angle is closest to the crosshair (smallest combined yaw/pitch angular distance) among all candidates within the configured FOV (10 to 180 degrees, default 90) and configured Range (2.0 to 8.0 blocks, default 4.5).
2. WHEN evaluating a candidate target, THE AimAssist SHALL exclude the candidate IF any enabled gating check rejects it, where the gating checks are Only On Click, Weapon Only, Check Mode, Team Check, Friend Check, AntiBot, and RayCast Validation, and SHALL also apply the Move Fix, Target Invisibles, and Target Prediction settings according to their current values.
3. WHEN the attack button transitions from pressed to released, THE AimAssist SHALL, on that same client tick, stop assisting and clear the current target so that no residual assisted rotation is applied on subsequent ticks.
4. WHILE a GUI screen is open (the current screen is non-null), THE AimAssist SHALL NOT modify player rotations and SHALL NOT select a target.
5. WHEN AimAssist reads a setting value during a tick, THE AimAssist SHALL read the setting's current value at that time so that an in-game change to any setting takes effect on the next tick without disabling and re-enabling the module.

### Requirement 8: State hygiene on toggle

**User Story:** As a player, I want AimAssist to fully reset when toggled, so that no rotation
or target state leaks across enable/disable or world changes.

#### Acceptance Criteria

1. WHEN AimAssist is enabled, THE AimAssist SHALL reset, before emitting any rotation, all mutable rotation, target, timing, smoothing, perturbation, and Speed_Multiplier state, including resetting the Rotation_State instance.
2. WHEN AimAssist is disabled, THE AimAssist SHALL clear the current target reference and reset the Rotation_State instance, smoothing state, perturbation state, and Speed_Multiplier to their initial values.
3. WHEN the player changes world or dimension while AimAssist is enabled, THE AimAssist SHALL clear the current target and reset the Rotation_State instance so that no stale rotation toward a previous-world target is emitted.
4. WHILE AimAssist is disabled, THE AimAssist SHALL emit no player rotation modifications on any subsequent tick until re-enabled.

### Requirement 9: Build and project constraints

**User Story:** As a maintainer, I want the enhancement to satisfy the project's toolchain and
performance rules, so that it compiles and runs within the existing fork.

#### Acceptance Criteria

1. WHEN `compileJava` is invoked with `JAVA_HOME` pinned to a Java 8 JDK, THE AimAssist SHALL compile to completion with zero compilation errors and add zero new third-party dependency declarations beyond those already present in `build.gradle`.
2. IF `compileJava` is invoked with a JDK other than Java 8, THEN THE AimAssist SHALL NOT be required to compile, and the build failure SHALL be attributable to the JDK version constraint rather than to AimAssist source changes.
3. WHILE executing any render path it touches (specifically `onRenderOverlay` and `onRenderWorld`), THE AimAssist SHALL perform zero heap allocations per frame (no `new` object, array, collection, comparator, or `Vec3` instantiation within the per-frame call), reusing pre-allocated fields or cached buffers instead.
4. WHEN values are needed for per-frame rendering, THE AimAssist SHALL compute and cache those values in a per-tick handler rather than constructing them in the per-frame render call.
5. THE AimAssist SHALL use only the Minecraft 1.8.9 Forge APIs already referenced by the existing module and SHALL NOT modify the pinned versions of Gradle (`4.6`), ForgeGradle (`2.1-SNAPSHOT`), or Forge (`1.8.9-11.15.1.2318-1.8.9`).
