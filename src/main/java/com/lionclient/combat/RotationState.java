package com.lionclient.combat;

/**
 * Holds the current server-side rotation state. Replaces the raw
 * {@code float[2]} that was previously exposed as a public mutable static
 * field on {@link KillAuraRotationUtils}. This class provides clear semantics
 * and avoids accidental corruption of the array.
 */
public final class RotationState {
    private static final RotationState INSTANCE = new RotationState();

    private float yaw = Float.NaN;
    private float pitch = Float.NaN;

    private RotationState() {
    }

    public static RotationState get() {
        return INSTANCE;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isSet() {
        return !Float.isNaN(yaw) && !Float.isNaN(pitch);
    }

    public void set(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void reset() {
        this.yaw = Float.NaN;
        this.pitch = Float.NaN;
    }

    /**
     * Sets from player rotation if not already set.
     */
    public void initFromPlayerIfNeeded(float playerYaw, float playerPitch) {
        if (Float.isNaN(yaw)) {
            yaw = playerYaw;
            pitch = playerPitch;
        }
    }
}
