# Implementation Plan: AimAssist Improvements

## Overview

This plan enhances the existing `AimAssistModule` and makes two small, backward-compatible
additions to the shared `RotationState` helper. Implementation language is **Java 8**
(MC 1.8.9 Forge), matching the existing code; the design specifies Java directly, so no
language selection is required.

The work proceeds bottom-up: first the additive `RotationState` API the module will depend
on, then the destructive cleanup of `AimAssistModule` (removing SERVER-mode and dead code),
then the new helper methods, and finally the orchestration rewrite (`onClientTick`,
`applyPlayerRotations`, enable/disable hygiene). Each step builds on the previous and ends
with the `onClientTick` rewrite wiring everything together. The final step verifies the
build on the Java 8 toolchain and confirms all removed identifiers are gone.

Property-based testing is intentionally excluded (see design "Testing Strategy"): there is
no test source set, Requirement 9.1 forbids new dependencies (a PBT library + JUnit would
both be new), and the behaviors are deeply coupled to a live Minecraft runtime. Verification
is therefore `compileJava` plus the in-game validation notes captured under each task.

## Tasks

- [x] 1. Add backward-compatible per-axis API to `RotationState`
  - [x] 1.1 Add per-axis speed fields and the `setSpeed(yaw, pitch)` overload
    - In `src/main/java/com/lionclient/combat/RotationState.java`, add private fields `yawSpeed` and `pitchSpeed`, initialized to the scalar `speed` in the constructor
    - Update the existing single-arg `setSpeed(float speed)` to also set `yawSpeed = pitchSpeed = clamped speed` so current callers (KillAura, AntiFireball, Clutch) keep byte-for-byte identical behavior
    - Add the new `setSpeed(float yawSpeed, float pitchSpeed)` overload that clamps each axis to `[1.0, 30.0]` and sets `speed = max(yawSpeed, pitchSpeed)` for shared terms
    - _Requirements: 2.1, 2.3, 2.5_

  - [x] 1.2 Derive per-axis decay and acceleration limit in `step(deltaTimeSeconds)`
    - Compute `kYaw`/`kPitch` and `decayYaw`/`decayPitch` from `yawSpeed`/`pitchSpeed` instead of the single scalar; apply `decayYaw` to the yaw step and `decayPitch` to the pitch step
    - Compute per-axis acceleration bounds `maxAccelYaw`/`maxAccelPitch` from `yawSpeed`/`pitchSpeed` and apply each to its own axis
    - Remove the now-dead local `stepSize`
    - Confirm that when `yawSpeed == pitchSpeed == speed` the math collapses to the current single-value output (no behavior change for existing callers)
    - In-game/manual note: existing KillAura/Clutch feel must be unchanged after this edit
    - _Requirements: 1.3, 2.1, 2.2_

  - [x] 1.3 Add `updateTarget(yaw, pitch)` that retargets without resetting interpolation
    - Add `public void updateTarget(float targetYaw, float targetPitch)` that updates `targetYaw`/`targetPitch`/`lastTargetYaw`/`lastTargetPitch` and increments `sameTargetTicks`, but does NOT reset `current*`, `lastStep*`, or `transitionTicks`
    - This keeps the acceleration limiter continuous across ticks against a moving target; `setTarget(...)` remains for the one-time per-engagement anchor
    - _Requirements: 1.3, 2.4_

- [x] 2. Strip SERVER-mode and dead code from `AimAssistModule`, then add new state fields
  - [x] 2.1 Remove SERVER-mode and dead/contradictory code
    - In `src/main/java/com/lionclient/feature/module/impl/AimAssistModule.java`, delete the `RotationMode` enum, the `rotationMode` "Mode" `EnumSetting` (and its `addSetting(rotationMode)` call), the `applyServerRotations(...)` method, the `serverTargetYaw`/`serverTargetPitch` fields, and every SERVER branch / `ClientRotationHelper` server-rotation and `KillAuraRotationUtils.serverRotations[]` write in `onClientTick`/`onDisable`
    - Delete the `getSpeedMultiplier()` method, the `wasOvershooting` field, the `syncPrevRotationSmooth(...)` and `syncPrevRotation(...)` methods, and the `appliedYaw`/`appliedPitch` locals in the rotation-apply block
    - Delete superseded fields now owned by `RotationState`: `smoothYaw`/`smoothPitch`, `lastYawDelta`/`lastPitchDelta`, `lockTicks`, `playerCurrentYaw`/`playerCurrentPitch`
    - Remove any imports left unused by the removals; leave the file compiling-shaped even if downstream methods are rewritten in later tasks (temporary minimal stubs are acceptable until task 4)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 2.2 Add the new state fields
    - Add `private float speedMultiplier = 1.0F;`
    - Add the per-engagement aim-point cache: `aimOffsetTarget` (EntityLivingBase), `aimOffNormX/Y/Z` (double), `aimOffsetValid` (boolean)
    - Add `private boolean aimStateInitialized = false;` (replaces the inverse-sense `isFirstLockFrame`)
    - Add world-change tracking: `private int lastDimensionId = Integer.MIN_VALUE;` and `private net.minecraft.world.World lastWorld = null;`
    - Add the `AIM_POINT_RANDOMIZATION` constant (e.g. `0.15`) used at use-time by `resolveAimRotations`
    - _Requirements: 3.1, 5.1, 8.3_

- [x] 3. Add the new per-tick helper methods to `AimAssistModule`
  - [x] 3.1 Implement `resolveAimRotations(mc, entity)` (hitbox aim-point variation)
    - On target change (`entity != aimOffsetTarget`), reset `aimOffsetTarget` and `aimOffsetValid = false` so the offset recomputes for the new target
    - Build the border-expanded bounding box; compute `halfX/halfY/halfZ`; if any half-extent `<= 0` (degenerate box), return the eye-center rotation fallback
    - When `!aimOffsetValid`, draw one `KillAuraRotationUtils.getAimPoint(entity, hMultipoint, vMultipoint, AIM_POINT_RANDOMIZATION, rng)`; if null, fall back to eye center; otherwise convert to the normalized offset (`aimOffNormX/Y/Z` in `[-1, 1]`) and mark valid (zero offset resolves to eye center)
    - Reconstruct the absolute aim point from the cached normalized offset each tick, clamped inside the box, then return `KillAuraRotationUtils.getRotationsToPoint(px, py, pz, rotationYaw, rotationPitch)`
    - Keep Target Prediction behavior available for the aim-point base as today; reuse a small eye-center helper for the fallback paths
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 3.2 Implement `updateSpeedMultiplier()` (smooth speed drift)
    - If speed randomization is disabled, set `speedMultiplier = 1.0F` and return
    - Otherwise drift incrementally: `speedMultiplier += (rng.nextFloat() - 0.5F) * 0.1F` (step bounded to `[-0.05, +0.05]`)
    - Read `Speed Rand Min %` / `Speed Rand Max %` at use-time, divide by 100, and clamp `speedMultiplier` into `[min, max]` (clamps back into range when settings change at runtime)
    - _Requirements: 3.1, 3.2, 3.3, 3.5_

  - [x] 3.3 Implement `syncRotationStateConfig()` (push settings into RotationState at use-time)
    - Each call reads `horizontalSpeed`/`verticalSpeed` at use-time and pushes `rotationState.setSpeed((float)(horizontalSpeed.getValue() * speedMultiplier), (float)(verticalSpeed.getValue() * speedMultiplier))`
    - Push noise via `rotationState.setRandomizationPercent(noise.isEnabled() ? noisePercent.getValue() : 0f)`
    - Fold `Smoothing`/`Smooth Factor %` into the effective speed handed to `setSpeed` (smoothing off ⇒ high effective speed so the cursor converges almost immediately, still through `RotationState` — no alternate non-helper path)
    - Must not reconstruct or restart the helper
    - _Requirements: 2.2, 2.3, 2.5, 7.5_

- [x] 4. Rewrite the apply path and orchestration, wiring everything together
  - [x] 4.1 Rewrite `applyPlayerRotations(mc)` to consume `rotationState.step(dt)`
    - Drop the `targetYaw/targetPitch` parameters; the target is set on `rotationState` by `onClientTick`
    - Compute `dt = calculateDeltaTime()`, call `rotationState.step(dt)`, and if the result is null or non-finite (NaN/Infinity) on either axis, return without touching camera or `prevRotation` (Req 1.5 / 4.4)
    - Capture `beforeYaw/beforePitch` (pre-delta camera), compute `yawDelta = wrapAngleTo180(out[0] - beforeYaw)` and `pitchDelta = out[1] - beforePitch`, and apply via `MouseGcdHelper.rotateBy(mc, yawDelta, pitchDelta)`
    - Set `prevRotationYaw = beforeYaw` and `prevRotationPitch = beforePitch` (correct render-interpolation handoff; replaces the 70% `syncPrevRotationSmooth` blend)
    - Apply `ClientRotationHelper.get().fixMovementInputs()` when Move Fix is enabled
    - In-game/manual note: cap FPS at 30/60/120/240 with Noise + Speed Randomization + Random Skip Frames off and confirm convergence feel is consistent
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 4.1, 4.2, 4.3, 4.4, 6.1_

  - [x] 4.2 Restructure `onClientTick(...)` to a single dispatch path
    - At the top (after the END-phase / enabled / non-null player+world guards), detect world or dimension change by comparing `mc.theWorld` identity and `mc.thePlayer.dimension` against `lastWorld`/`lastDimensionId`; on change clear `currentTarget`, set `aimStateInitialized = false`, invalidate the aim offset, call `rotationState.reset()`, update the caches, and emit no rotation that tick
    - Preserve all existing gating, click-state, GUI-open guard, target selection (`findTarget`) and validation; on mouse release clear `currentTarget` and set `aimStateInitialized = false`
    - Common prologue once a non-null target exists: `resolveAimRotations` → (return if null) → `updateSpeedMultiplier()` → `syncRotationStateConfig()` → anchor with `rotationState.setTarget(targetYaw, targetPitch, liveYaw, livePitch)` when `!aimStateInitialized` (then set it true), else `rotationState.updateTarget(targetYaw, targetPitch)`
    - Single dispatch to `applyPlayerRotations(mc)`; no SERVER branch remains
    - _Requirements: 1.1, 2.1, 2.4, 4.1, 7.1, 7.2, 7.3, 7.4, 8.3_

  - [x] 4.3 Update `onEnable`/`onDisable` state hygiene for the new fields
    - `onEnable`: reset target/click/lock flags, `aimStateInitialized = false`, `aimOffsetValid = false`, `aimOffsetTarget = null`, `lastStepNanos = 0`, `rotationState.reset()`, cache current world/dimension, and initialize `speedMultiplier` to `1.0` when within `[min%, max%]` else the nearest bound — all before any rotation is emitted
    - `onDisable`: clear `currentTarget`, reset `speedMultiplier`/aim-offset state, and call `rotationState.reset()`; remove the obsolete `ClientRotationHelper` server-rotation clears
    - _Requirements: 3.4, 8.1, 8.2, 8.4_

- [x] 5. Checkpoint — build and confirm removals
  - Ensure all tests pass, ask the user if questions arise.
  - [x] 5.1 Verify the build on Java 8 and confirm removed identifiers are gone
    - Run `JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 ./gradlew compileJava` and confirm zero compilation errors with no new dependencies added to `build.gradle`
    - Grep `AimAssistModule.java` to confirm none of these identifiers remain: `appliedYaw`, `appliedPitch`, `getSpeedMultiplier`, `wasOvershooting`, `syncPrevRotationSmooth`, `syncPrevRotation`, `RotationMode`, `applyServerRotations`, `serverTargetYaw`, `serverTargetPitch`, and the "Mode" setting
    - Confirm no easing/per-axis-ratio/acceleration math remains in `AimAssistModule` outside `rotationState` calls (single easing authority)
    - _Requirements: 6.7, 9.1, 9.2, 9.5_

## Notes

- Implementation language is Java 8; no new third-party dependencies may be added (Req 9.1, 9.5).
- Each task references the specific requirement clauses it satisfies for traceability.
- Property-based tests are intentionally omitted (no test source set, no new dependencies
  permitted, deep Minecraft runtime coupling). Verification is `compileJava` on Java 8 plus
  the in-game validation notes captured inline.
- `RotationState` changes (task 1) are additive and must leave existing callers (KillAura,
  AntiFireball, Clutch) behaviorally identical.
- Tasks 2–5 all edit the same file (`AimAssistModule.java`) and are therefore sequenced into
  separate waves to avoid conflicting edits.
- `onClientTick` (4.2) is the integration point that wires the new helpers and the rewritten
  apply path together; no orphaned code should remain after it.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1"] },
    { "id": 1, "tasks": ["1.2", "2.2"] },
    { "id": 2, "tasks": ["1.3", "3.1"] },
    { "id": 3, "tasks": ["3.2"] },
    { "id": 4, "tasks": ["3.3"] },
    { "id": 5, "tasks": ["4.1"] },
    { "id": 6, "tasks": ["4.2"] },
    { "id": 7, "tasks": ["4.3"] },
    { "id": 8, "tasks": ["5.1"] }
  ]
}
```
