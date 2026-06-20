package com.lionclient.feature.module.impl;

import com.lionclient.combat.ClientRotationHelper;
import com.lionclient.combat.RotationState;
import com.lionclient.event.ClientRotationEvent;
import com.lionclient.event.EventBus;
import com.lionclient.event.IEventListener;
import com.lionclient.event.PrePlayerInputEvent;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Clutch — rewritten. Saves you from a fall by placing a block beneath your feet.
 *
 * <p>Design goals (lessons from the old bridge-pathfinder version + the anti-cheat
 * flags it produced):</p>
 * <ul>
 *   <li>No pathfinding. Each tick it simply finds the block position under the
 *       player and a solid face to click, then places one block there.</li>
 *   <li>No extra packets. Rotations ride the normal movement packet through the
 *       silent-rotation pipeline (or the visible rotation), never a separate C03.</li>
 *   <li>Smooth, configurable rotation via {@link RotationState}.</li>
 * </ul>
 */
public final class ClutchModule extends Module {
    private static final ClutchModule INSTANCE = new ClutchModule();
    private static final EnumFacing[] CLICK_ORDER = {
        EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final RotationState rotationState = new RotationState(15.0F, 4.0F, new Random());

    private final EnumSetting<Trigger> trigger = new EnumSetting<Trigger>("Trigger", Trigger.values(), Trigger.FALL_DAMAGE);
    private final DecimalSetting rotationSpeed = new DecimalSetting("Rotation Speed", 1.0D, 30.0D, 0.5D, 22.0D);
    private final BooleanSetting silentAim = new BooleanSetting("Silent Aim", true);
    private final BooleanSetting rotateBack = new BooleanSetting("Rotate Back", true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    private final BooleanSetting returnToSlot = new BooleanSetting("Return To Slot", true);
    private final NumberSetting maxBlocks = new NumberSetting("Max Blocks", 1, 64, 1, 16);
    private final NumberSetting searchDepth = new NumberSetting("Search Depth", 1, 5, 1, 3);

    // State
    private boolean clutching;
    private boolean returningToCamera;
    private boolean rotating;
    private int blocksPlaced;
    private int savedSlot = -1;
    private float savedCamYaw;
    private float savedCamPitch;
    private float[] activeAim;
    private int aimHeldTicks;

    private final IEventListener<ClientRotationEvent> rotationListener = this::onClientRotation;

    private ClutchModule() {
        super("Clutch", "Places a block beneath you to save you from a fall.", Category.PLAYER, Keyboard.KEY_NONE);
        rotateBack.setVisibility(() -> !silentAim.isEnabled());
        addSetting(trigger);
        addSetting(rotationSpeed);
        addSetting(silentAim);
        addSetting(rotateBack);
        addSetting(autoSwitch);
        addSetting(returnToSlot);
        addSetting(maxBlocks);
        addSetting(searchDepth);
    }

    public static ClutchModule getInstance() {
        return INSTANCE;
    }

    @Override
    protected void onEnable() {
        resetState();
        EventBus.getInstance().register(ClientRotationEvent.class, rotationListener);
    }

    @Override
    protected void onDisable() {
        EventBus.getInstance().unregister(ClientRotationEvent.class, rotationListener);
        restoreSlot();
        resetState();
        rotationState.reset();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !ready()) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;

        // Landed → finish up.
        if (player.onGround) {
            if (clutching) {
                finishClutch(player);
            }
            updateReturnRotation(player);
            return;
        }

        // Only clutch while actually falling.
        if (player.motionY >= 0.0D || !triggerMet(player) || findBlockSlot(player) == -1) {
            if (clutching && !returningToCamera) {
                // Lost the conditions mid-air; stop trying but keep state minimal.
                clutching = false;
            }
            return;
        }

        if (!clutching) {
            beginClutch(player);
        }
        if (blocksPlaced >= maxBlocks.getValue()) {
            return;
        }

        rotationState.setSpeed((float) rotationSpeed.getValue());
        tryPlace(player);
    }

    // ── Clutch lifecycle ──────────────────────────────────────────────────

    private void beginClutch(EntityPlayerSP player) {
        clutching = true;
        returningToCamera = false;
        rotating = false;
        blocksPlaced = 0;
        aimHeldTicks = 0;
        activeAim = null;
        savedCamYaw = player.rotationYaw;
        savedCamPitch = player.rotationPitch;
        savedSlot = returnToSlot.isEnabled() ? player.inventory.currentItem : -1;
    }

    private void finishClutch(EntityPlayerSP player) {
        clutching = false;
        rotating = false;
        blocksPlaced = 0;
        activeAim = null;
        restoreSlot();
        if (!silentAim.isEnabled() && rotateBack.isEnabled()) {
            returningToCamera = true;
            rotationState.setTarget(savedCamYaw, savedCamPitch, player.rotationYaw, player.rotationPitch);
        } else {
            rotationState.reset();
        }
    }

    private void updateReturnRotation(EntityPlayerSP player) {
        if (!returningToCamera) {
            return;
        }
        float[] r = rotationState.step();
        player.rotationYaw = r[0];
        player.rotationPitch = r[1];
        if (rotationState.hasReachedTarget(1.5F)) {
            returningToCamera = false;
            rotationState.reset();
        }
    }

    // ── Placement ─────────────────────────────────────────────────────────

    private void tryPlace(EntityPlayerSP player) {
        Placement place = findPlacement(player);
        if (place == null) {
            activeAim = null;
            aimHeldTicks = 0;
            return;
        }

        float[] aim = aimAt(player, place.hitVec);
        activeAim = aim;
        float fromYaw = rotating ? rotationState.getCurrentYaw() : player.rotationYaw;
        float fromPitch = rotating ? rotationState.getCurrentPitch() : player.rotationPitch;
        rotationState.setTarget(aim[0], aim[1], fromYaw, fromPitch);
        rotating = true;
        float[] stepped = rotationState.step();

        if (!silentAim.isEnabled()) {
            player.rotationYaw = stepped[0];
            player.rotationPitch = stepped[1];
        }

        // Wait until we're actually aimed at the face before placing.
        if (!rotationState.hasReachedTarget(3.5F)) {
            aimHeldTicks = 0;
            return;
        }
        if (++aimHeldTicks < 2) {
            return;
        }

        if (!ensureHoldingBlock(player)) {
            return;
        }

        // Confirm the ray (at our aim) actually hits the intended face.
        MovingObjectPosition hit = rayTraceAt(player, stepped[0], stepped[1]);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || !place.clicked.equals(hit.getBlockPos()) || hit.sideHit != place.side) {
            return;
        }

        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return;
        }

        // CRITICAL: Force C03 rotation packet BEFORE C08 placement packet
        // This prevents packet-order flags (rotation-before-action checks)
        if (silentAim.isEnabled()) {
            ClientRotationHelper rotHelper = ClientRotationHelper.get();
            rotHelper.setServerRotations(stepped[0], stepped[1]);
            // Apply immediately so the C03 goes out this tick
            rotHelper.updateServerRotations();
        }

        if (mc.playerController.onPlayerRightClick(player, mc.theWorld, held, hit.getBlockPos(), hit.sideHit, hit.hitVec)) {
            player.swingItem();
            blocksPlaced++;
            aimHeldTicks = 0;
        }
    }

    /**
     * Finds the air block beneath the player that has a solid face to click on.
     * Prefers placing on top of the block directly below (you land on it), then
     * the side walls (clutch off a wall).
     */
    private Placement findPlacement(EntityPlayerSP player) {
        World world = mc.theWorld;
        int px = MathHelper.floor_double(player.posX);
        int pz = MathHelper.floor_double(player.posZ);
        int feetY = MathHelper.floor_double(player.posY);
        double reach = reach(player);

        for (int dy = 1; dy <= searchDepth.getValue(); dy++) {
            BlockPos base = new BlockPos(px, feetY - dy, pz);
            if (!isReplaceable(world, base)) {
                continue;
            }
            for (EnumFacing dir : CLICK_ORDER) {
                BlockPos neighbor = base.offset(dir);
                if (!isSolid(world, neighbor)) {
                    continue;
                }
                EnumFacing side = dir.getOpposite();
                Vec3 hitVec = new Vec3(
                    neighbor.getX() + 0.5D + side.getFrontOffsetX() * 0.5D,
                    neighbor.getY() + 0.5D + side.getFrontOffsetY() * 0.5D,
                    neighbor.getZ() + 0.5D + side.getFrontOffsetZ() * 0.5D);
                Vec3 eyes = player.getPositionEyes(1.0F);
                if (eyes.squareDistanceTo(hitVec) > reach * reach) {
                    continue;
                }
                return new Placement(neighbor, side, hitVec);
            }
        }
        return null;
    }

    // ── Rotation event hooks ────────────────────────────────────────────────

    private void onClientRotation(ClientRotationEvent event) {
        if (!isEnabled() || !ready()) {
            return;
        }
        if (silentAim.isEnabled() && clutching && activeAim != null) {
            float currentYaw = rotationState.getCurrentYaw();
            float currentPitch = rotationState.getCurrentPitch();
            if (!Float.isNaN(currentYaw) && !Float.isNaN(currentPitch)) {
                event.yaw = Float.valueOf(currentYaw);
                event.pitch = Float.valueOf(currentPitch);
            }
        }
    }

    // ── Triggers ──────────────────────────────────────────────────────────

    private boolean triggerMet(EntityPlayerSP player) {
        switch (trigger.getValue()) {
            case ALWAYS:
                return true;
            case VOID:
                return !hasGroundBelow(player, 64);
            case FALL_DAMAGE:
            default:
                // Predicted fall damage = (fallDistance + drop to first ground) - 3.
                int drop = dropToGround(player, 32);
                float predicted = player.fallDistance + drop - 3.0F;
                return predicted >= 2.0F; // ~1 heart or more
        }
    }

    private boolean hasGroundBelow(EntityPlayerSP player, int maxDepth) {
        return dropToGround(player, maxDepth) < maxDepth;
    }

    private int dropToGround(EntityPlayerSP player, int maxDepth) {
        World world = mc.theWorld;
        int px = MathHelper.floor_double(player.posX);
        int pz = MathHelper.floor_double(player.posZ);
        int feetY = MathHelper.floor_double(player.posY);
        for (int d = 1; d <= maxDepth; d++) {
            if (isSolid(world, new BlockPos(px, feetY - d, pz))) {
                return d;
            }
        }
        return maxDepth;
    }

    // ── Inventory ───────────────────────────────────────────────────────────

    private boolean ensureHoldingBlock(EntityPlayerSP player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemBlock) {
            return true;
        }
        if (!autoSwitch.isEnabled()) {
            return false;
        }
        int slot = findBlockSlot(player);
        if (slot == -1) {
            return false;
        }
        player.inventory.currentItem = slot;
        return player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemBlock;
    }

    private int findBlockSlot(EntityPlayerSP player) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
                continue;
            }
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block != null && block.isFullCube()) {
                return slot;
            }
        }
        return -1;
    }

    private void restoreSlot() {
        if (savedSlot == -1 || !returnToSlot.isEnabled() || mc.thePlayer == null) {
            savedSlot = -1;
            return;
        }
        mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private float[] aimAt(EntityPlayerSP player, Vec3 target) {
        double dx = target.xCoord - player.posX;
        double dy = target.yCoord - (player.posY + player.getEyeHeight());
        double dz = target.zCoord - player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[] { yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F) };
    }

    private MovingObjectPosition rayTraceAt(EntityPlayerSP player, float yaw, float pitch) {
        float sy = player.rotationYaw;
        float sp = player.rotationPitch;
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        MovingObjectPosition hit = player.rayTrace(reach(player), 1.0F);
        player.rotationYaw = sy;
        player.rotationPitch = sp;
        return hit;
    }

    private double reach(EntityPlayerSP player) {
        return player.capabilities.isCreativeMode ? 5.0D : 4.5D;
    }

    private boolean isReplaceable(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return block.getMaterial() == net.minecraft.block.material.Material.air
            || block.getMaterial().isReplaceable();
    }

    private boolean isSolid(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return block.getMaterial().isSolid() && block.isFullCube();
    }

    private void resetState() {
        clutching = false;
        returningToCamera = false;
        rotating = false;
        blocksPlaced = 0;
        savedSlot = -1;
        activeAim = null;
        aimHeldTicks = 0;
    }

    private boolean ready() {
        return mc.thePlayer != null && mc.theWorld != null && !mc.thePlayer.isDead;
    }

    @Override
    public String getHudInfo() {
        return clutching ? blocksPlaced + " placed" : "";
    }

    public enum Trigger {
        ALWAYS("Always"),
        VOID("Void"),
        FALL_DAMAGE("Fall Damage");

        private final String label;
        Trigger(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final class Placement {
        private final BlockPos clicked;
        private final EnumFacing side;
        private final Vec3 hitVec;

        private Placement(BlockPos clicked, EnumFacing side, Vec3 hitVec) {
            this.clicked = clicked;
            this.side = side;
            this.hitVec = hitVec;
        }
    }
}
