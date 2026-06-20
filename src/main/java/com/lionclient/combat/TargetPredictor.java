package com.lionclient.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;

/**
 * Target prediction for combat modules.
 *
 * Accounts for:
 * - Network latency (ping compensation)
 * - Entity movement state (sprinting, jumping, knockback)
 * - Server-side position reconciliation
 * - Smooth interpolation between predictions
 */
public final class TargetPredictor {

    private static final double MAX_PREDICTION_DISTANCE = 3.0D;
    private static final double MAX_PREDICTION_TIME = 0.2D;
    // Horizontal air drag per Minecraft tick (~0.91). A player in the air keeps
    // most of its momentum, so we must NOT collapse it to near-zero.
    private static final double AIR_CONTROL_FACTOR = 0.91D;
    private static final double SPRINT_SPEED_MULTIPLIER = 1.3D;

    private TargetPredictor() {}

    /**
     * Predicts entity position at given latency ticks ahead.
     * Works for any Entity (fireballs, players, etc.) using simple linear extrapolation.
     */
    public static Vec3 predict(Entity entity, int latencyTicks) {
        if (entity == null) return null;

        latencyTicks = Math.max(0, Math.min(latencyTicks, 10));

        double motX = entity.motionX;
        double motY = entity.motionY;
        double motZ = entity.motionZ;

        double predictedX = entity.posX + motX * latencyTicks;
        double predictedY = entity.posY + motY * latencyTicks;
        double predictedZ = entity.posZ + motZ * latencyTicks;

        double dx = predictedX - entity.posX;
        double dy = predictedY - entity.posY;
        double dz = predictedZ - entity.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        double maxDistSq = MAX_PREDICTION_DISTANCE * MAX_PREDICTION_DISTANCE;

        if (distSq > maxDistSq) {
            double scale = MAX_PREDICTION_DISTANCE / Math.sqrt(distSq);
            predictedX = entity.posX + dx * scale;
            predictedY = entity.posY + dy * scale;
            predictedZ = entity.posZ + dz * scale;
        }

        return new Vec3(predictedX, predictedY, predictedZ);
    }

    /**
     * Predicts rotations needed to hit predicted position for any Entity.
     */
    public static float[] predictRotations(Entity entity, int latencyTicks, float baseYaw, float basePitch) {
        Vec3 predicted = predict(entity, latencyTicks);
        if (predicted == null) return null;

        return KillAuraRotationUtils.getRotationsToPoint(
                predicted.xCoord, predicted.yCoord, predicted.zCoord, baseYaw, basePitch
        );
    }

    /**
     * Predicts entity position at given latency ticks ahead with living-entity logic.
     * Accounts for sprinting, air control, gravity, knockback.
     */
    public static Vec3 predict(EntityLivingBase entity, int latencyTicks) {
        if (entity == null) return null;

        latencyTicks = Math.max(0, Math.min(latencyTicks, 10));

        double motX = entity.motionX;
        double motY = entity.motionY;
        double motZ = entity.motionZ;

        if (entity.onGround) {
            if (entity.isSprinting()) {
                motX *= SPRINT_SPEED_MULTIPLIER;
                motZ *= SPRINT_SPEED_MULTIPLIER;
            }
        } else {
            motX *= AIR_CONTROL_FACTOR;
            motZ *= AIR_CONTROL_FACTOR;
            motY -= 0.08D * latencyTicks;
        }

        double predictedX = entity.posX + motX * latencyTicks;
        double predictedY = entity.posY + motY * latencyTicks;
        double predictedZ = entity.posZ + motZ * latencyTicks;

        double dx = predictedX - entity.posX;
        double dy = predictedY - entity.posY;
        double dz = predictedZ - entity.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        double maxDistSq = MAX_PREDICTION_DISTANCE * MAX_PREDICTION_DISTANCE;

        if (distSq > maxDistSq) {
            double scale = MAX_PREDICTION_DISTANCE / Math.sqrt(distSq);
            predictedX = entity.posX + dx * scale;
            predictedY = entity.posY + dy * scale;
            predictedZ = entity.posZ + dz * scale;
        }

        return new Vec3(predictedX, predictedY, predictedZ);
    }

    public static Vec3 predictByPing(EntityLivingBase entity, int pingMs) {
        int latencyTicks = (int) Math.round(pingMs / 50.0D);
        return predict(entity, latencyTicks);
    }

    public static float[] predictRotations(EntityLivingBase entity, int latencyTicks, float baseYaw, float basePitch) {
        Vec3 predicted = predict(entity, latencyTicks);
        if (predicted == null) return null;

        return KillAuraRotationUtils.getRotationsToPoint(
                predicted.xCoord, predicted.yCoord, predicted.zCoord, baseYaw, basePitch
        );
    }

    public static int estimateLatencyTicks() {
        return 2;
    }

    public static Vec3 predictWithKnockback(EntityLivingBase entity, int latencyTicks) {
        if (entity == null) return null;

        Vec3 basePrediction = predict(entity, latencyTicks);
        if (basePrediction == null) return null;

        if (entity.hurtTime > 0) {
            double kbFactor = 0.5D * (entity.hurtTime / 10.0D);
            double kbX = -Math.sin(Math.toRadians(entity.rotationYaw)) * kbFactor;
            double kbZ = Math.cos(Math.toRadians(entity.rotationYaw)) * kbFactor;

            return new Vec3(
                    basePrediction.xCoord + kbX * latencyTicks,
                    basePrediction.yCoord,
                    basePrediction.zCoord + kbZ * latencyTicks
            );
        }

        return basePrediction;
    }
}