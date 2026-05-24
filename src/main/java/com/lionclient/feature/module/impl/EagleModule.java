package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class EagleModule extends Module {
    private final BooleanSetting blockOnly = new BooleanSetting("Block Only", true);
    private final BooleanSetting pitchCheck = new BooleanSetting("Pitch Check", false);
    private final NumberSetting sneakDelay = new NumberSetting("Sneak Delay", 0, 250, 5, 0);

    private long sneakReleaseTime;

    public EagleModule() {
        super("Eagle", "Auto-sneaks while bridging blocks.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(blockOnly);
        addSetting(pitchCheck);
        addSetting(sneakDelay);
    }

    @Override
    public void onClientTick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null || minecraft.gameSettings == null) {
            return;
        }

        int sneakKey = minecraft.gameSettings.keyBindSneak.getKeyCode();
        if (minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            releaseSneak(sneakKey);
            return;
        }

        if (blockOnly.isEnabled() && !isHoldingBlock(player)) {
            releaseSneak(sneakKey);
            return;
        }

        if (shouldSneakAtEdge(player)) {
            KeyBinding.setKeyBindState(sneakKey, true);
            if (Mouse.isButtonDown(1)) {
                sneakReleaseTime = System.currentTimeMillis() + sneakDelay.getValue();
            }
            return;
        }

        if (System.currentTimeMillis() < sneakReleaseTime) {
            KeyBinding.setKeyBindState(sneakKey, true);
            return;
        }

        releaseSneak(sneakKey);
    }

    @Override
    protected void onDisable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings != null) {
            releaseSneak(minecraft.gameSettings.keyBindSneak.getKeyCode());
        }
    }

    private boolean shouldSneakAtEdge(EntityPlayerSP player) {
        if (!player.onGround || player.isCollidedHorizontally || player.movementInput == null) {
            return false;
        }
        if (player.movementInput.jump) {
            // Player is trying to jump, never glue them down with sneak.
            return false;
        }

        if (pitchCheck.isEnabled()) {
            return shouldSneakWithPitchCheck(player);
        }

        return shouldSneakSafewalk(player);
    }

    private boolean shouldSneakWithPitchCheck(EntityPlayerSP player) {
        if (player.movementInput.moveForward >= 0.0F) {
            return false;
        }

        double[] movement = getMovementOffset(player);
        if (Math.abs(movement[0]) < 1.0E-4D && Math.abs(movement[1]) < 1.0E-4D) {
            return false;
        }

        return isEdgeUnsafe(player, movement[0], movement[1]);
    }

    private boolean shouldSneakSafewalk(EntityPlayerSP player) {
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);

        // Use actual velocity direction if moving, otherwise use input direction.
        double projectedX;
        double projectedZ;
        if (speed > 0.01D) {
            // Shorter projection to avoid triggering too early.
            double projection = Math.max(0.20D, Math.min(0.30D, speed + 0.06D));
            projectedX = (motionX / speed) * projection;
            projectedZ = (motionZ / speed) * projection;
        } else {
            float forward = player.movementInput != null ? player.movementInput.moveForward : 0.0F;
            float strafe = player.movementInput != null ? player.movementInput.moveStrafe : 0.0F;
            if (Math.abs(forward) < 0.001F && Math.abs(strafe) < 0.001F) {
                return isStandingOnEdge(player);
            }
            double yawRadians = Math.toRadians(player.rotationYaw);
            double sin = Math.sin(yawRadians);
            double cos = Math.cos(yawRadians);
            double dirX = strafe * cos - forward * sin;
            double dirZ = forward * cos + strafe * sin;
            double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len < 0.001D) {
                return isStandingOnEdge(player);
            }
            projectedX = (dirX / len) * 0.28D;
            projectedZ = (dirZ / len) * 0.28D;
        }

        if (Math.abs(projectedX) < 1.0E-3D && Math.abs(projectedZ) < 1.0E-3D) {
            return isStandingOnEdge(player);
        }

        return isEdgeUnsafe(player, projectedX, projectedZ);
    }

    private boolean isStandingOnEdge(EntityPlayerSP player) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        World world = Minecraft.getMinecraft().theWorld;
        double sampleY = box.minY - 0.08D;
        double insetX = Math.min(0.28D, (box.maxX - box.minX) * 0.5D - 0.02D);
        double insetZ = Math.min(0.28D, (box.maxZ - box.minZ) * 0.5D - 0.02D);

        boolean corner1 = hasSupport(world, player.posX + insetX, sampleY, player.posZ + insetZ);
        boolean corner2 = hasSupport(world, player.posX + insetX, sampleY, player.posZ - insetZ);
        boolean corner3 = hasSupport(world, player.posX - insetX, sampleY, player.posZ + insetZ);
        boolean corner4 = hasSupport(world, player.posX - insetX, sampleY, player.posZ - insetZ);
        return !(corner1 && corner2 && corner3 && corner4);
    }

    private boolean isEdgeUnsafe(EntityPlayerSP player, double offsetX, double offsetZ) {
        World world = Minecraft.getMinecraft().theWorld;
        AxisAlignedBB box = player.getEntityBoundingBox();
        AxisAlignedBB projectedBox = box.offset(offsetX, 0.0D, offsetZ);
        double sampleY = projectedBox.minY - 0.08D;

        double insetX = Math.min(0.28D, (projectedBox.maxX - projectedBox.minX) * 0.5D - 0.02D);
        double insetZ = Math.min(0.28D, (projectedBox.maxZ - projectedBox.minZ) * 0.5D - 0.02D);
        double centerX = (projectedBox.minX + projectedBox.maxX) * 0.5D;
        double centerZ = (projectedBox.minZ + projectedBox.maxZ) * 0.5D;

        boolean center = hasSupport(world, centerX, sampleY, centerZ);
        // If center still has support, only sneak if ALL corners are gone
        // (about to fall off completely). This prevents premature sneak in
        // diagonal where 1-2 corners overshoot but the player is still safe.
        if (center) {
            boolean corner1 = hasSupport(world, centerX + insetX, sampleY, centerZ + insetZ);
            boolean corner2 = hasSupport(world, centerX + insetX, sampleY, centerZ - insetZ);
            boolean corner3 = hasSupport(world, centerX - insetX, sampleY, centerZ + insetZ);
            boolean corner4 = hasSupport(world, centerX - insetX, sampleY, centerZ - insetZ);
            int unsupported = 0;
            if (!corner1) unsupported++;
            if (!corner2) unsupported++;
            if (!corner3) unsupported++;
            if (!corner4) unsupported++;
            // Only trigger when 3+ corners are unsupported (truly at the edge).
            return unsupported >= 3;
        }
        return true;
    }

    private double[] getMovementOffset(EntityPlayerSP player) {
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

    private boolean hasSupport(World world, double x, double y, double z) {
        BlockPos samplePos = new BlockPos(
            MathHelper.floor_double(x),
            MathHelper.floor_double(y),
            MathHelper.floor_double(z)
        );
        return world.getBlockState(samplePos).getBlock().getMaterial() != Material.air;
    }

    private boolean isHoldingBlock(EntityPlayerSP player) {
        ItemStack held = player.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    private void releaseSneak(int sneakKey) {
        sneakReleaseTime = 0L;
        KeyBinding.setKeyBindState(sneakKey, false);
    }
}
