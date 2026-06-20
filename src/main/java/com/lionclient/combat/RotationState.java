package com.lionclient.combat;

import java.util.Random;
import net.minecraft.util.MathHelper;

/**
 * Per-module rotation state — each module (KillAura, Clutch, etc.) gets its own instance.
 * This prevents modules from overwriting each other's smoothing state.
 *
 * Key design:
 * - Frame-rate independent: pass deltaTime to step() for consistent speed at any FPS
 * - Tracks rotation progress from start to target
 * - Supports both "aiming toward target" and "returning to original" transitions
 * - Uses exponential decay easing (mathematically correct for frame independence)
 * - Per-axis independent speeds (yaw and pitch don't move identically)
 * - Acceleration-limited to prevent instant direction changes
 * - Micro-drift after sustained tracking (hand tremor simulation)
 */
public final class RotationState {

    // --- Persistent state ---
    private float startYaw, startPitch;       // Where we began the transition
    private float targetYaw, targetPitch;     // Where we're going
    private float currentYaw, currentPitch;   // Current interpolated position
    private float lastStepYaw, lastStepPitch; // Last per-tick delta (for accel limiting)
    private float lastTargetYaw = Float.NaN, lastTargetPitch = Float.NaN;
    private int sameTargetTicks = 0;
    private int transitionTicks = 0;
    private boolean returningToOriginal = false;
    private long lastStepNanos = 0;           // For automatic deltaTime calculation

    // --- Configuration ---
    private float speed;                // 1-30: rotation speed multiplier
    private float yawSpeed;             // 1-30: per-axis yaw convergence speed
    private float pitchSpeed;           // 1-30: per-axis pitch convergence speed
    private float randomizationPercent; // 0-30: per-axis randomization
    private final Random rng;

    // Reference FPS for normalization — at 60 FPS, factor = 1.0
    private static final float REFERENCE_FPS = 60.0F;

    // Reusable result buffer to avoid allocation on every step()
    private final float[] stepResult = new float[2];

    public RotationState(float speed, float randomizationPercent, Random rng) {
        this.speed = MathHelper.clamp_float(speed, 1.0F, 30.0F);
        this.yawSpeed = this.speed;
        this.pitchSpeed = this.speed;
        this.randomizationPercent = MathHelper.clamp_float(randomizationPercent, 0.0F, 30.0F);
        this.rng = rng;
    }

    /** Update the rotation speed at runtime so the in-game slider takes effect. */
    public void setSpeed(float speed) {
        this.speed = MathHelper.clamp_float(speed, 1.0F, 30.0F);
        this.yawSpeed = this.speed;
        this.pitchSpeed = this.speed;
    }

    /** New: independent yaw/pitch convergence speeds for callers with split speed settings. */
    public void setSpeed(float yawSpeed, float pitchSpeed) {
        this.yawSpeed = MathHelper.clamp_float(yawSpeed, 1.0F, 30.0F);
        this.pitchSpeed = MathHelper.clamp_float(pitchSpeed, 1.0F, 30.0F);
        this.speed = Math.max(this.yawSpeed, this.pitchSpeed); // used only for shared terms
    }

    /** Update the randomization percent at runtime so the in-game slider takes effect. */
    public void setRandomizationPercent(float randomizationPercent) {
        this.randomizationPercent = MathHelper.clamp_float(randomizationPercent, 0.0F, 30.0F);
    }

    /**
     * Begin a transition from the current position toward a target.
     * If already mid-transition, smoothly redirects (no snap).
     */
    public void setTarget(float targetYaw, float targetPitch, float fromYaw, float fromPitch) {
        boolean sameTarget = !Float.isNaN(lastTargetYaw)
            && Math.abs(targetYaw - lastTargetYaw) < 1.0F
            && Math.abs(targetPitch - lastTargetPitch) < 1.0F;

        if (sameTarget) {
            sameTargetTicks++;
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            return;
        }

        this.startYaw = fromYaw;
        this.startPitch = fromPitch;
        this.currentYaw = fromYaw;
        this.currentPitch = fromPitch;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;
        this.sameTargetTicks = 0;
        this.transitionTicks = 0;
        this.returningToOriginal = false;
        this.lastStepYaw = 0;
        this.lastStepPitch = 0;
        this.lastStepNanos = 0;
    }

    /**
     * Retarget against a moving target without resetting interpolation.
     *
     * Unlike {@link #setTarget}, this updates only the target (and same-target
     * tracking) — it does NOT touch {@code currentYaw/currentPitch},
     * {@code lastStepYaw/lastStepPitch}, or {@code transitionTicks}. This keeps the
     * acceleration limiter continuous across ticks against a moving target.
     * {@code setTarget(...)} remains for the one-time per-engagement anchor.
     */
    public void updateTarget(float targetYaw, float targetPitch) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.lastTargetYaw = targetYaw;
        this.lastTargetPitch = targetPitch;
        this.sameTargetTicks++;
    }

    /**
     * Begin a smooth return to the original position (before aim started).
     */
    public void beginReturn(float originalYaw, float originalPitch) {
        if (returningToOriginal) return;

        this.startYaw = currentYaw;
        this.startPitch = currentPitch;
        this.targetYaw = originalYaw;
        this.targetPitch = originalPitch;
        this.returningToOriginal = true;
        this.transitionTicks = 0;
        this.sameTargetTicks = 0;
        this.lastStepYaw = 0;
        this.lastStepPitch = 0;
        this.lastStepNanos = 0;
    }

    /**
     * Advance the rotation by one frame. Returns [yaw, pitch].
     *
     * FRAME-RATE INDEPENDENT: pass deltaTime (seconds since last call).
     * At 60 FPS, deltaTime ≈ 0.0167s. At 240 FPS, deltaTime ≈ 0.0042s.
     * The easing is normalized so the total rotation speed is identical regardless of FPS.
     *
     * @param deltaTimeSeconds Time elapsed since last step() call, in seconds.
     *                         Pass 0 to auto-calculate from System.nanoTime().
     */
    public float[] step(float deltaTimeSeconds) {
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;
        float magnitude = (float) MathHelper.sqrt_double(deltaYaw * deltaYaw + deltaPitch * deltaPitch);

        // Close enough — snap to target to avoid micro-jitter
        if (magnitude < 0.01F) {
            currentYaw = targetYaw;
            currentPitch = targetPitch;
            transitionTicks++;
            lastStepNanos = System.nanoTime();
            stepResult[0] = currentYaw;
            stepResult[1] = clampPitch(currentPitch);
            return stepResult;
        }

        transitionTicks++;

        // Calculate frame-rate independent factor
        // At 60 FPS: deltaTime ≈ 0.0167, factor ≈ 1.0
        // At 240 FPS: deltaTime ≈ 0.0042, factor ≈ 0.25
        // At 20 FPS: deltaTime ≈ 0.05, factor ≈ 3.0
        float deltaTime = deltaTimeSeconds > 0 ? deltaTimeSeconds : autoDeltaTime();
        float fpsFactor = deltaTime * REFERENCE_FPS; // Normalized to 60 FPS

        // Exponential decay easing: 1 - e^(-speed * deltaTime * k)
        // This is mathematically frame-rate independent.
        // k is chosen so that at speed=15, the rotation covers ~95% of the distance in 1 second
        // Per-axis decay constants so yaw and pitch converge at their own speeds.
        // When yawSpeed == pitchSpeed == speed this collapses to the single-value output.
        float kYaw = 0.15F + yawSpeed * 0.01F;   // Decay constant based on yaw speed
        float kPitch = 0.15F + pitchSpeed * 0.01F; // Decay constant based on pitch speed
        float decayYaw = 1.0F - (float) Math.exp(-kYaw * fpsFactor);
        float decayPitch = 1.0F - (float) Math.exp(-kPitch * fpsFactor);

        // Per-axis INDEPENDENT ratios (not derived from a shared stepSize).
        // Using the same ratio for both axes flags "Aim Divisor Y" on Vulacn/Grim
        // because yawStep/pitchStep stays constant across frames.
        float yawRatio = 0.7F + rng.nextFloat() * 0.6F; // 0.7-1.3
        float pitchRatio = 0.5F + rng.nextFloat() * 0.8F; // 0.5-1.3 (independent!)

        if (randomizationPercent > 0.001F) {
            float range = randomizationPercent / 100.0F;
            yawRatio += (rng.nextFloat() - 0.5F) * range * 0.5F;
            pitchRatio += (rng.nextFloat() - 0.5F) * range * 0.5F;
        }

        // Calculate per-axis steps from the delta, each with its own ratio and decay
        float yawStep = deltaYaw * decayYaw * yawRatio;
        float pitchStep = deltaPitch * decayPitch * pitchRatio;

        // Clamp to not overshoot
        if (Math.abs(yawStep) > Math.abs(deltaYaw)) yawStep = deltaYaw;
        if (Math.abs(pitchStep) > Math.abs(deltaPitch)) pitchStep = deltaPitch;

        // Acceleration limiting — frame-rate independent, per-axis
        // Max degrees per second, converted to per-frame.
        // When yawSpeed == pitchSpeed == speed this collapses to the single-value output.
        float maxAccelYaw = (180.0F + yawSpeed * 12.0F) * deltaTime;   // degrees this frame
        float maxAccelPitch = (180.0F + pitchSpeed * 12.0F) * deltaTime; // degrees this frame
        float accelYaw = Math.abs(yawStep - lastStepYaw);
        float accelPitch = Math.abs(pitchStep - lastStepPitch);
        if (accelYaw > maxAccelYaw) {
            yawStep = lastStepYaw + Math.signum(yawStep - lastStepYaw) * maxAccelYaw;
        }
        if (accelPitch > maxAccelPitch) {
            pitchStep = lastStepPitch + Math.signum(pitchStep - lastStepPitch) * maxAccelPitch;
        }

        // Micro-drift after sustained tracking (hand tremor)
        if (sameTargetTicks > 5 && randomizationPercent > 0.001F) {
            float drift = randomizationPercent / 100.0F * 0.3F * fpsFactor;
            yawStep += (rng.nextFloat() - 0.5F) * drift;
            pitchStep += (rng.nextFloat() - 0.5F) * drift * 0.5F;
        }

        lastStepYaw = yawStep;
        lastStepPitch = pitchStep;
        lastStepNanos = System.nanoTime();

        currentYaw += yawStep;
        currentPitch += pitchStep;

        stepResult[0] = currentYaw;
        stepResult[1] = clampPitch(currentPitch);
        return stepResult;
    }

    /**
     * Advance rotation using automatic deltaTime calculation.
     * Less precise than passing deltaTime manually, but convenient.
     */
    public float[] step() {
        return step(0);
    }

    private float autoDeltaTime() {
        long now = System.nanoTime();
        if (lastStepNanos <= 0L) {
            lastStepNanos = now;
            return 1.0F / REFERENCE_FPS; // Assume 60 FPS on first call
        }
        float dt = (now - lastStepNanos) / 1_000_000_000.0F;
        lastStepNanos = now;
        // Clamp to avoid huge jumps after alt-tab or lag spikes
        return MathHelper.clamp_float(dt, 0.001F, 0.1F);
    }

    public boolean hasReachedTarget(float tolerance) {
        float deltaYaw = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - currentYaw));
        float deltaPitch = Math.abs(targetPitch - currentPitch);
        return deltaYaw <= tolerance && deltaPitch <= tolerance;
    }

    public boolean isReturning() { return returningToOriginal; }

    public boolean hasReturned(float tolerance) {
        return returningToOriginal && hasReachedTarget(tolerance);
    }

    public float getCurrentYaw() { return currentYaw; }
    public float getCurrentPitch() { return currentPitch; }
    public float getTargetYaw() { return targetYaw; }
    public float getTargetPitch() { return targetPitch; }
    public int getTransitionTicks() { return transitionTicks; }

    public void reset() {
        currentYaw = 0; currentPitch = 0;
        targetYaw = 0; targetPitch = 0;
        startYaw = 0; startPitch = 0;
        lastStepYaw = 0; lastStepPitch = 0;
        lastTargetYaw = Float.NaN; lastTargetPitch = Float.NaN;
        sameTargetTicks = 0; transitionTicks = 0;
        returningToOriginal = false;
        lastStepNanos = 0;
    }

    private static float clampPitch(float pitch) {
        return MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }
}
