package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.EdgeDetectionHelper;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * LegitScaffold — enhanced scaffold walk with multiple modes.
 *
 * Modes:
 * - SAFEWalk: Only sneaks at block edges. Original behavior. Most legit.
 * - EXPAND: Sneak + auto-place block under you when you reach the edge.
 *   Searches hotbar for blocks and places them.
 * - BRIDGE: Sneak + auto-place blocks while moving forward/backward.
 *   Auto-switches to blocks and places at feet when approaching edge.
 * - TOWER: Sneak + auto-place + jump when space is held (for upward building).
 *
 * Legit behavior principles:
 * - Only places blocks when holding right-click (in EXPAND/BRIDGE/TOWER modes)
 * - Random delay on block placement (not instant)
 * - Edge detection is conservative (won't place if already safe)
 * - Auto-stops when no blocks in hotbar
 */
public final class LegitScaffoldModule extends Module {
    private static final Random RNG = new Random();

    public enum ScaffoldMode {
        /** Only sneaks at edges — original safewalk behavior */
        SAFE_WALK("Safewalk"),
        /** Safewalk + auto-place block under you at edges */
        EXPAND("Expand"),
        /** Safewalk + auto-place blocks while bridging forward/backward */
        BRIDGE("Bridge"),
        /** Bridge mode + tower when jump is held */
        TOWER("Tower");

        final String displayName;
        ScaffoldMode(String displayName) { this.displayName = displayName; }

        @Override
        public String toString() { return displayName; }
    }

    private final EnumSetting<ScaffoldMode> mode = new EnumSetting<>("Mode", ScaffoldMode.values(), ScaffoldMode.SAFE_WALK);
    private final BooleanSetting pitchCheck = new BooleanSetting("Pitch Check", false);
    private final NumberSetting sneakDelay = new NumberSetting("Sneak Delay", 0, 250, 5, 60);
    // Block placement settings (for EXPAND/BRIDGE/TOWER modes)
    private final BooleanSetting placeBlocks = new BooleanSetting("Place Blocks", true);
    private final NumberSetting placeDelayMin = new NumberSetting("Place Delay Min", 50, 500, 25, 100);
    private final NumberSetting placeDelayMax = new NumberSetting("Place Delay Max", 100, 800, 25, 250);
    private final BooleanSetting swingItem = new BooleanSetting("Swing Item", false);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    private final BooleanSetting searchHotbar = new BooleanSetting("Search Hotbar", true);

    private long sneakReleaseTime;
    private long lastPlaceTime = 0;
    private int targetSlot = -1;
    private boolean wasOnGround = false;

    public LegitScaffoldModule() {
        super("LegitScaffold", "Enhanced scaffold walk with block placement.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(pitchCheck);
        addSetting(sneakDelay);
        addSetting(placeBlocks);
        addSetting(placeDelayMin);
        addSetting(placeDelayMax);
        addSetting(swingItem);
        addSetting(autoSwitch);
        addSetting(searchHotbar);

        // Only show placement settings for modes that use them
        java.util.function.BooleanSupplier placementVisible = () ->
            mode.getValue() != ScaffoldMode.SAFE_WALK;
        placeBlocks.setVisibility(placementVisible);
        placeDelayMin.setVisibility(placementVisible);
        placeDelayMax.setVisibility(placementVisible);
        swingItem.setVisibility(placementVisible);
        autoSwitch.setVisibility(placementVisible);
        searchHotbar.setVisibility(placementVisible);
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.gameSettings == null) return;

        int sneakKey = mc.gameSettings.keyBindSneak.getKeyCode();
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            releaseSneak(sneakKey);
            return;
        }

        boolean shouldSneakAtEdge = shouldSneakAtEdge(player);

        // Handle block placement for EXPAND/BRIDGE/TOWER modes
        if (mode.getValue() != ScaffoldMode.SAFE_WALK && placeBlocks.isEnabled()) {
            handleBlockPlacement(mc, player, shouldSneakAtEdge);
        }

        // Handle sneaking
        if (shouldSneakAtEdge) {
            KeyBinding.setKeyBindState(sneakKey, true);
            if (shouldExtendSneakDelay(mc)) {
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) releaseSneak(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    /**
     * Handles block placement logic for EXPAND/BRIDGE/TOWER modes.
     */
    private void handleBlockPlacement(Minecraft mc, EntityPlayerSP player, boolean atEdge) {
        long now = System.currentTimeMillis();
        long placeDelay = placeDelayMin.getValue() + RNG.nextInt(
            Math.max(1, placeDelayMax.getValue() - placeDelayMin.getValue() + 1));

        // Determine if we should place a block
        boolean shouldPlace = false;

        switch (mode.getValue()) {
            case EXPAND:
                // Place when at edge and not already over a block
                if (atEdge && player.onGround) shouldPlace = true;
                break;

            case BRIDGE:
                // Place when moving forward/backward and approaching edge
                if (!player.onGround) break;
                boolean moving = player.movementInput != null &&
                    (Math.abs(player.movementInput.moveForward) > 0.1f ||
                     Math.abs(player.movementInput.moveStrafe) > 0.1f);
                if (moving && atEdge) shouldPlace = true;
                break;

            case TOWER:
                // Bridge behavior + place when jump is held and on ground
                if (player.movementInput != null && player.movementInput.jump && player.onGround) {
                    shouldPlace = true;
                } else if (atEdge) {
                    boolean moving2 = player.movementInput != null &&
                        (Math.abs(player.movementInput.moveForward) > 0.1f ||
                         Math.abs(player.movementInput.moveStrafe) > 0.1f);
                    if (moving2) shouldPlace = true;
                }
                break;

            default:
                break;
        }

        if (!shouldPlace) return;
        if (!Mouse.isButtonDown(1)) return; // Only place when holding right-click
        if (now - lastPlaceTime < placeDelay) return;

        // Check if holding a block
        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock)) {
            // Try to find a block in hotbar
            if (!autoSwitch.isEnabled() || !searchHotbar.isEnabled()) return;
            int slot = findBlockSlot(player);
            if (slot < 0) return;
            targetSlot = slot;
            player.inventory.currentItem = slot;
            return; // Wait until next tick to place (after switch)
        }

        // Place the block
        placeBlockUnder(mc, player);
        lastPlaceTime = now;
    }

    /**
     * Finds a block in the hotbar.
     */
    private int findBlockSlot(EntityPlayerSP player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Places a block under the player using the use item mechanism.
     * Sends rotation packet BEFORE placement to prevent Grim/Vulcan flags.
     */
    private void placeBlockUnder(Minecraft mc, EntityPlayerSP player) {
        // Find the position to place at (one block below)
        double x = player.posX;
        double y = player.posY - 1.0;
        double z = player.posZ;

        // Offset in movement direction for bridge modes
        if (mode.getValue() == ScaffoldMode.BRIDGE || mode.getValue() == ScaffoldMode.TOWER) {
            if (player.movementInput != null) {
                float yawRad = (float) Math.toRadians(player.rotationYaw);
                double forward = player.movementInput.moveForward;
                double strafe = player.movementInput.moveStrafe;
                double dist = 0.5;
                x -= Math.sin(yawRad) * forward * dist + Math.sin(yawRad + Math.PI / 2) * strafe * dist;
                z += Math.cos(yawRad) * forward * dist - Math.cos(yawRad + Math.PI / 2) * strafe * dist;
            }
        }

        BlockPos pos = new BlockPos(x, y, z);
        if (!mc.theWorld.isAirBlock(pos)) return;
        if (!isBlockUnderSafe(mc.theWorld, pos)) return;

        // Find a solid block adjacent to the target to click on
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos adjacent = pos.offset(facing);
            if (!mc.theWorld.isAirBlock(adjacent)) {
                Vec3 hitVec = new Vec3(
                    pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.5,
                    pos.getY() + 0.5 + facing.getFrontOffsetY() * 0.5,
                    pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.5
                );
                MovingObjectPosition ray = new MovingObjectPosition(hitVec, facing.getOpposite(), pos);

                // Aim the sent rotation AT the block face so the server sees a
                // placement consistent with where we're "looking" (Vulcan/Grim flag
                // placements whose rotation doesn't match the clicked block).
                sendPlacementRotation(player, hitVec);

                mc.playerController.onPlayerRightClick(
                    player, mc.theWorld, player.getHeldItem(),
                    adjacent, facing.getOpposite(), hitVec
                );
                if (swingItem.isEnabled()) player.swingItem();
                return;
            }
        }

        // Fallback: if no adjacent solid block, try looking down and using objectMouseOver
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos mopPos = mop.getBlockPos();
            // Only place if the clicked block is at our feet level
            if (Math.abs(mopPos.getY() - pos.getY()) <= 1 && mopPos.distanceSq(pos) <= 2.0) {
                // Aim the sent rotation at the block face before placing.
                sendPlacementRotation(player, mop.hitVec);

                mc.playerController.onPlayerRightClick(
                    player, mc.theWorld, player.getHeldItem(),
                    mopPos, mop.sideHit, mop.hitVec
                );
                if (swingItem.isEnabled()) player.swingItem();
            }
        }
    }

    /**
     * Aims the OUTGOING movement packet at {@code hitVec} via the silent rotation
     * pipeline instead of sending a separate C03 packet. Sending an extra rotation
     * packet per placement doubled the packets and tripped Vulcan Timer + the
     * "Post BlockPlace" badpackets / Grim "Post" checks.
     */
    private void sendPlacementRotation(EntityPlayerSP player, Vec3 hitVec) {
        float[] rot = rotationsToward(player, hitVec);
        com.lionclient.combat.ClientRotationHelper.get().setServerRotations(rot[0], rot[1]);
    }

    /** Yaw/pitch from the player's eyes toward a world point. */
    private float[] rotationsToward(EntityPlayerSP player, Vec3 target) {
        double dx = target.xCoord - player.posX;
        double dy = target.yCoord - (player.posY + player.getEyeHeight());
        double dz = target.zCoord - player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[] { yaw, pitch };
    }

    /**
     * Checks if placing a block under the player is safe (won't suffocate).
     */
    private boolean isBlockUnderSafe(World world, BlockPos pos) {
        // Don't place if it would suffocate the player
        BlockPos headPos = pos.up();
        if (world.isAirBlock(headPos)) return true;
        Material mat = world.getBlockState(headPos).getBlock().getMaterial();
        return mat == Material.air || !mat.isSolid();
    }

    private boolean shouldSneakAtEdge(EntityPlayerSP player) {
        if (!player.onGround || player.isCollidedHorizontally || player.movementInput == null) return false;
        if (player.movementInput.jump) return false;
        if (pitchCheck.isEnabled()) return shouldSneakWithPitchCheck(player);
        return shouldSneakSafewalk(player);
    }

    private boolean shouldSneakWithPitchCheck(EntityPlayerSP player) {
        if (player.movementInput.moveForward >= 0.0F) return false;
        double[] movement = EdgeDetectionHelper.getMovementOffset(player);
        if (Math.abs(movement[0]) < 1.0E-4D && Math.abs(movement[1]) < 1.0E-4D) return false;
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
        AxisAlignedBB box = player.getEntityBoundingBox();
        AxisAlignedBB projectedBox = box.offset(offsetX, 0.0D, offsetZ);
        double sampleY = projectedBox.minY - 0.08D;
        double[] lateral = getLateralOffset(new double[]{offsetX, offsetZ});
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
        if (length < 1.0E-4D) return new double[]{1.0D, 0.0D};
        return new double[]{-movement[1] / length, movement[0] / length};
    }

    private boolean shouldExtendSneakDelay(Minecraft mc) {
        if (!Mouse.isButtonDown(1) || mc.objectMouseOver == null) return false;
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        return heldItem != null && heldItem.getItem() instanceof ItemBlock;
    }

    private void releaseSneak(int sneakKey) {
        sneakReleaseTime = 0L;
        EdgeDetectionHelper.releaseSneak(sneakKey);
    }
}
