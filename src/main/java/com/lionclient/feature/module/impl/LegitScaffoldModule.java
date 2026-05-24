package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.util.EdgeDetectionHelper;
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

public final class LegitScaffoldModule extends Module {
    private final BooleanSetting pitchCheck = new BooleanSetting("Pitch Check", false);
    private final NumberSetting sneakDelay = new NumberSetting("Sneak Delay", 0, 250, 5, 60);

    private long sneakReleaseTime;

    public LegitScaffoldModule() {
        super("LegitScaffold", "Sneaks at block edges.", Category.MOVEMENT, Keyboard.KEY_NONE);
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

        boolean shouldSneakAtEdge = shouldSneakAtEdge(player);
        if (shouldSneakAtEdge) {
            KeyBinding.setKeyBindState(sneakKey, true);
            if (shouldExtendSneakDelay(minecraft)) {
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

        double[] movement = EdgeDetectionHelper.getMovementOffset(player);
        if (Math.abs(movement[0]) < 1.0E-4D && Math.abs(movement[1]) < 1.0E-4D) {
            return false;
        }

        return isEdgeUnsafe(player, movement[0], movement[1]);
    }

    private boolean shouldSneakSafewalk(EntityPlayerSP player) {
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        double projectedX = MathHelper.clamp_double(motionX, -0.32D, 0.32D);
        double projectedZ = MathHelper.clamp_double(motionZ, -0.32D, 0.32D);
        if (Math.abs(projectedX) < 1.0E-3D && Math.abs(projectedZ) < 1.0E-3D) {
            projectedX = MathHelper.clamp_double(player.moveStrafing * 0.12D, -0.12D, 0.12D);
            projectedZ = MathHelper.clamp_double(player.moveForward * 0.12D, -0.12D, 0.12D);
        }

        if (Math.abs(projectedX) < 1.0E-3D && Math.abs(projectedZ) < 1.0E-3D) {
            return EdgeDetectionHelper.isStandingOnEdge(player);
        }

        return isEdgeUnsafe(player, projectedX, projectedZ);
    }

    private boolean isEdgeUnsafe(EntityPlayerSP player, double offsetX, double offsetZ) {
        World world = Minecraft.getMinecraft().theWorld;
        double[] movement = new double[] {offsetX, offsetZ};
        AxisAlignedBB box = player.getEntityBoundingBox();
        AxisAlignedBB projectedBox = box.offset(movement[0], 0.0D, movement[1]);
        double sampleY = projectedBox.minY - 0.08D;
        double[] lateral = getLateralOffset(movement);
        double leadX = (projectedBox.minX + projectedBox.maxX) * 0.5D;
        double leadZ = (projectedBox.minZ + projectedBox.maxZ) * 0.5D;
        double sideReach = Math.max(0.20D, (projectedBox.maxX - projectedBox.minX) * 0.48D);
        double sideX = lateral[0] * sideReach;
        double sideZ = lateral[1] * sideReach;

        boolean centerSupported = EdgeDetectionHelper.hasSupport(world, leadX, sampleY, leadZ);
        boolean leftSupported = EdgeDetectionHelper.hasSupport(world, leadX + sideX, sampleY, leadZ + sideZ);
        boolean rightSupported = EdgeDetectionHelper.hasSupport(world, leadX - sideX, sampleY, leadZ - sideZ);

        return !centerSupported || (!leftSupported && !rightSupported);
    }

    private double[] getLateralOffset(double[] movement) {
        double length = Math.sqrt(movement[0] * movement[0] + movement[1] * movement[1]);
        if (length < 1.0E-4D) {
            return new double[] {1.0D, 0.0D};
        }
        return new double[] {-movement[1] / length, movement[0] / length};
    }

    private boolean shouldExtendSneakDelay(Minecraft minecraft) {
        if (!Mouse.isButtonDown(1) || minecraft.objectMouseOver == null) {
            return false;
        }

        ItemStack heldItem = minecraft.thePlayer.getHeldItem();
        return heldItem != null && heldItem.getItem() instanceof ItemBlock;
    }

    private void releaseSneak(int sneakKey) {
        sneakReleaseTime = 0L;
        EdgeDetectionHelper.releaseSneak(sneakKey);
    }
}
