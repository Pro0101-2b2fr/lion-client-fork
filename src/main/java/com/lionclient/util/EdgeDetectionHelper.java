package com.lionclient.util;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Utility helper containing shared functions for block edge detection and movement offsets.
 */
public final class EdgeDetectionHelper {

    private EdgeDetectionHelper() {
    }

    /**
     * Checks if there is a solid block at the given coordinates.
     */
    public static boolean hasSupport(World world, double x, double y, double z) {
        BlockPos samplePos = new BlockPos(
            MathHelper.floor_double(x),
            MathHelper.floor_double(y),
            MathHelper.floor_double(z)
        );
        return world.getBlockState(samplePos).getBlock().getMaterial() != Material.air;
    }

    /**
     * Determines if the player is currently standing on the edge of a block
     * (i.e. at least one corner of the bounding box is unsupported).
     */
    public static boolean isStandingOnEdge(EntityPlayerSP player) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        World world = Minecraft.getMinecraft().theWorld;
        if (world == null) {
            return false;
        }
        double sampleY = box.minY - 0.08D;
        double insetX = Math.min(0.28D, (box.maxX - box.minX) * 0.5D - 0.02D);
        double insetZ = Math.min(0.28D, (box.maxZ - box.minZ) * 0.5D - 0.02D);

        boolean corner1 = hasSupport(world, player.posX + insetX, sampleY, player.posZ + insetZ);
        boolean corner2 = hasSupport(world, player.posX + insetX, sampleY, player.posZ - insetZ);
        boolean corner3 = hasSupport(world, player.posX - insetX, sampleY, player.posZ + insetZ);
        boolean corner4 = hasSupport(world, player.posX - insetX, sampleY, player.posZ - insetZ);
        return !(corner1 && corner2 && corner3 && corner4);
    }

    /**
     * Computes horizontal movement offset depending on player inputs and motion.
     */
    public static double[] getMovementOffset(EntityPlayerSP player) {
        if (player.movementInput == null) {
            return new double[]{0.0D, 0.0D};
        }
        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        float magnitude = MathHelper.sqrt_float(forward * forward + strafe * strafe);
        if (magnitude < 0.001F) {
            return new double[]{0.0D, 0.0D};
        }

        forward /= magnitude;
        strafe /= magnitude;

        double yawRadians = Math.toRadians(player.rotationYaw);
        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);
        double motionX = strafe * cos - forward * sin;
        double motionZ = forward * cos + strafe * sin;
        double horizontalMotion = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        double projection = Math.max(0.24D, Math.min(0.34D, horizontalMotion + 0.08D));
        return new double[]{motionX * projection, motionZ * projection};
    }

    /**
     * Resets the keybind state of the sneak key.
     */
    public static void releaseSneak(int sneakKey) {
        KeyBinding.setKeyBindState(sneakKey, false);
    }
}
