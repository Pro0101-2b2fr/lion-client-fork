package com.lionclient.combat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;

public final class TargetPredictor {
    
    /**
     * Estimates future position based on velocity and distance/latency.
     * @param entity The target entity
     * @param latencyTicks Estimated ticks to compensate for (ping / 50ms)
     * @return Predicted Vec3 position
     */
    public static Vec3 predict(EntityLivingBase entity, int latencyTicks) {
        if (entity == null) return null;
        
        double x = entity.posX + (entity.posX - entity.prevPosX) * latencyTicks;
        double y = entity.posY + (entity.posY - entity.prevPosY) * latencyTicks;
        double z = entity.posZ + (entity.posZ - entity.prevPosZ) * latencyTicks;
        
        return new Vec3(x, y, z);
    }
}
