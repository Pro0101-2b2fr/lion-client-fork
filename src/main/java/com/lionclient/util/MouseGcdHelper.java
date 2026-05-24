package com.lionclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

/**
 * Snaps client rotation deltas to the granularity Minecraft applies to real
 * mouse input. Vanilla mouse handling translates raw mouse pixels into a yaw
 * delta of {@code (sensitivity * 0.6F + 0.2F)^3 * 1.2F} per pixel, so any
 * client-driven rotation that is not a multiple of that step value can be
 * detected by anti-cheats that monitor rotation deltas (e.g. Vulcan's
 * "Aim Constant").
 */
public final class MouseGcdHelper {
    private static final float MIN_GCD = 1.0E-6F;

    private MouseGcdHelper() {
    }

    /**
     * @return the per-pixel rotation step matching the player's current
     *         sensitivity, or {@code 0} if the game settings are not yet ready.
     */
    public static float currentGcd() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null) {
            return 0.0F;
        }
        float sensitivity = minecraft.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return sensitivity * sensitivity * sensitivity * 1.2F;
    }

    /**
     * Adds {@code yawDelta} / {@code pitchDelta} to the player rotation but
     * rounded to the nearest GCD multiple. Pitch is clamped to vanilla bounds.
     * No-op if the deltas are smaller than one GCD step.
     */
    public static void rotateBy(Minecraft minecraft, float yawDelta, float pitchDelta) {
        if (minecraft == null || minecraft.thePlayer == null) {
            return;
        }
        float gcd = currentGcd();
        if (gcd <= MIN_GCD) {
            return;
        }

        int yawSteps = Math.round(yawDelta / gcd);
        int pitchSteps = Math.round(pitchDelta / gcd);
        if (yawSteps == 0 && pitchSteps == 0) {
            return;
        }

        minecraft.thePlayer.rotationYaw += yawSteps * gcd;
        minecraft.thePlayer.rotationPitch = MathHelper.clamp_float(
            minecraft.thePlayer.rotationPitch + pitchSteps * gcd, -90.0F, 90.0F
        );
    }

    /**
     * Snaps an absolute rotation target so the resulting delta from the player's
     * current rotation is a GCD multiple. Returns {@code [yaw, pitch]} that the
     * caller can assign back to the entity. Pitch is clamped to vanilla bounds.
     */
    public static float[] snapAbsolute(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        float gcd = currentGcd();
        if (gcd <= MIN_GCD) {
            return new float[] {targetYaw, MathHelper.clamp_float(targetPitch, -90.0F, 90.0F)};
        }

        double yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        double pitchDelta = targetPitch - currentPitch;
        long yawSteps = Math.round(yawDelta / gcd);
        long pitchSteps = Math.round(pitchDelta / gcd);
        return new float[] {
            currentYaw + (float) (yawSteps * gcd),
            MathHelper.clamp_float(currentPitch + (float) (pitchSteps * gcd), -90.0F, 90.0F)
        };
    }
}
