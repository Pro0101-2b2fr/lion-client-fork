package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * AutoBlock — sword blocking for 1.8.9 PvP.
 *
 * Modes:
 * - SMART: Block when a player is in range (with or without KillAura).
 * - TAP_BLOCK: Brief block after taking damage.
 * - PACKET: Pure packet blocking.
 *
 * In 1.8.9, sword blocking is done by holding right-click with a sword.
 * This module simulates that by sending block packets and optionally
 * holding the use item key.
 */
public final class AutoBlockModule extends Module {
    private static final Random RNG = new Random();

    public enum BlockMode {
        SMART("Smart"),
        TAP_BLOCK("Tap Block"),
        PACKET("Packet");

        final String displayName;
        BlockMode(String displayName) { this.displayName = displayName; }

        @Override
        public String toString() { return displayName; }
    }

    private final EnumSetting<BlockMode> blockMode = new EnumSetting<>("Mode", BlockMode.values(), BlockMode.SMART);
    private final DecimalSetting blockRange = new DecimalSetting("Block Range", 2.0, 6.0, 0.5, 3.5);
    private final NumberSetting blockChance = new NumberSetting("Block Chance", 0, 100, 5, 100);
    private final NumberSetting tapDurationMin = new NumberSetting("Tap Duration Min", 50, 500, 10, 100);
    private final NumberSetting tapDurationMax = new NumberSetting("Tap Duration Max", 100, 800, 10, 200);
    private final NumberSetting blockCooldownMs = new NumberSetting("Cooldown", 0, 500, 25, 50);
    private final BooleanSetting swordOnly = new BooleanSetting("Sword Only", true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", false);
    private final BooleanSetting releaseOnDisable = new BooleanSetting("Release On Disable", true);

    private boolean isBlocking = false;
    private long blockUntil = 0L;
    private long lastBlockEnd = 0L;
    private int rightClickTicks = 0;

    public AutoBlockModule() {
        super("AutoBlock", "Auto sword blocking for PvP.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(blockMode);
        addSetting(blockRange);
        addSetting(blockChance);
        addSetting(tapDurationMin);
        addSetting(tapDurationMax);
        addSetting(blockCooldownMs);
        addSetting(swordOnly);
        addSetting(autoSwitch);
        addSetting(releaseOnDisable);

        java.util.function.BooleanSupplier tapVisible = () -> blockMode.getValue() == BlockMode.TAP_BLOCK;
        tapDurationMin.setVisibility(tapVisible);
        tapDurationMax.setVisibility(tapVisible);
    }

    @Override
    protected void onEnable() {
        isBlocking = false;
        blockUntil = 0;
        rightClickTicks = 0;
    }

    @Override
    protected void onDisable() {
        if (isBlocking) {
            releaseBlock(Minecraft.getMinecraft());
        }
        isBlocking = false;
    }

    @Override
    public void onClientTick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            if (isBlocking) releaseBlock(mc);
            isBlocking = false;
            return;
        }

        // Don't interfere with Scaffold or RightClicker actively placing blocks
        if (isPlacingBlocks()) {
            if (isBlocking) releaseBlock(mc);
            isBlocking = false;
            return;
        }

        switch (blockMode.getValue()) {
            case SMART: handleSmartBlock(mc); break;
            case TAP_BLOCK: handleTapBlock(mc); break;
            case PACKET: handlePacketBlock(mc); break;
        }
    }

    /**
     * SMART: Block when any player is in range.
     * Works with or without KillAura.
     */
    private void handleSmartBlock(Minecraft mc) {
        long now = System.currentTimeMillis();
        EntityLivingBase target = findTarget(mc);

        // Release conditions
        if (isBlocking) {
            if (target == null || getDistanceTo(target) > blockRange.getValue() || !canBlock(mc)) {
                releaseBlock(mc);
                isBlocking = false;
                return;
            }
            // Keep blocking — hold right click
            holdRightClick(mc);
            return;
        }

        // Cooldown check
        if (now - lastBlockEnd < blockCooldownMs.getValue()) return;

        // Start blocking
        if (target != null && canBlock(mc)) {
            double dist = getDistanceTo(target);
            if (dist <= blockRange.getValue() && RNG.nextInt(100) < blockChance.getValue()) {
                // Auto-switch to sword if needed
                if (autoSwitch.isEnabled() && !isHoldingSword(mc)) {
                    int slot = findSwordSlot(mc);
                    if (slot >= 0) mc.thePlayer.inventory.currentItem = slot;
                }
                startBlock(mc);
                isBlocking = true;
            }
        }
    }

    /**
     * TAP_BLOCK: Brief block after taking damage.
     */
    private void handleTapBlock(Minecraft mc) {
        long now = System.currentTimeMillis();

        if (isBlocking) {
            if (now >= blockUntil) {
                releaseBlock(mc);
                isBlocking = false;
            } else {
                holdRightClick(mc);
            }
            return;
        }

        if (now - lastBlockEnd < blockCooldownMs.getValue()) return;
        if (!canBlock(mc)) return;
        if (mc.thePlayer.hurtTime <= 0) return;
        if (RNG.nextInt(100) >= blockChance.getValue()) return;

        int min = tapDurationMin.getValue();
        int max = tapDurationMax.getValue();
        int duration = min + (min >= max ? 0 : RNG.nextInt(max - min + 1));

        startBlock(mc);
        isBlocking = true;
        blockUntil = now + duration;
    }

    /**
     * PACKET: Block when target in range, packet-only.
     */
    private void handlePacketBlock(Minecraft mc) {
        EntityLivingBase target = findTarget(mc);

        if (isBlocking) {
            if (target == null || getDistanceTo(target) > blockRange.getValue() || !canBlock(mc)) {
                releaseBlock(mc);
                isBlocking = false;
            } else {
                holdRightClick(mc);
            }
            return;
        }

        if (target != null && canBlock(mc)) {
            double dist = getDistanceTo(target);
            if (dist <= blockRange.getValue() && RNG.nextInt(100) < blockChance.getValue()) {
                startBlock(mc);
                isBlocking = true;
            }
        }
    }

    // --- Block actions ---

    private void startBlock(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null) return;
        if (blockMode.getValue() == BlockMode.PACKET) {
            // Packet-only block: send the genuine 1.8.9 "use item in air" placement once.
            mc.thePlayer.sendQueue.addToSendQueue(
                new C08PacketPlayerBlockPlacement(
                    new BlockPos(-1, -1, -1), 255,
                    mc.thePlayer.getHeldItem(),
                    0.0F, 0.0F, 0.0F
                )
            );
        } else {
            // Legit block: hold use-item so the client itself sends the real block
            // packet. Sending our own C08 on top of this would duplicate the packet
            // and is a clear AutoBlock signature, so we don't.
            setUseKey(mc, true);
        }
    }

    private void holdRightClick(Minecraft mc) {
        // Keep holding right-click for the visual block animation (key modes only).
        // In PACKET mode we must not touch the key or the client would also block.
        if (blockMode.getValue() != BlockMode.PACKET) {
            setUseKey(mc, true);
        }
        rightClickTicks = 2; // Keep it held
    }

    private void releaseBlock(Minecraft mc) {
        sendReleaseOnly(mc);
        lastBlockEnd = System.currentTimeMillis();
    }

    /**
     * Release the active block without arming the cooldown. Used both for a full
     * release (via {@link #releaseBlock}) and for the block-hit release performed
     * right before an attack.
     */
    private void sendReleaseOnly(Minecraft mc) {
        if (mc.thePlayer == null) return;
        if (blockMode.getValue() == BlockMode.PACKET) {
            mc.thePlayer.sendQueue.addToSendQueue(
                new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN, EnumFacing.DOWN
                )
            );
        } else {
            // Releasing the key makes the client send the genuine C07 itself.
            setUseKey(mc, false);
        }
        rightClickTicks = 0;
    }

    private void setUseKey(Minecraft mc, boolean held) {
        if (mc.gameSettings != null && mc.gameSettings.keyBindUseItem != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), held);
        }
    }

    // --- Target finding ---

    /**
     * Find a target to block against.
     * Priority: KillAura target → nearest player in range.
     */
    private EntityLivingBase findTarget(Minecraft mc) {
        // 1. Try KillAura target first
        KillAuraModule ka = getKillAuraModule();
        if (ka != null && ka.isEnabled()) {
            EntityLivingBase kaTarget = ka.getTarget();
            if (kaTarget != null && getDistanceTo(kaTarget) <= blockRange.getValue()) {
                return kaTarget;
            }
        }

        // 2. Find nearest player in range
        EntityLivingBase nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) obj;
            if (player == mc.thePlayer || player.isDead || player.getHealth() <= 0) continue;
            if (player.isInvisible()) continue;
            if (AntiBotModule.shouldIgnore(player)) continue;

            double dist = mc.thePlayer.getDistanceToEntity(player);
            if (dist <= blockRange.getValue() && dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }

        return nearest;
    }

    // --- Compatibility ---

    private boolean isPlacingBlocks() {
        // Don't block if Scaffold is actively placing
        LegitScaffoldModule scaffold = getScaffoldModule();
        if (scaffold != null && scaffold.isEnabled()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && Mouse.isButtonDown(1)) {
                return true;
            }
        }
        return false;
    }

    private boolean canBlock(Minecraft mc) {
        if (mc.thePlayer.isUsingItem()) return false; // Already using item
        if (swordOnly.isEnabled()) return isHoldingSword(mc);
        return mc.thePlayer.getHeldItem() != null;
    }

    private boolean isHoldingSword(Minecraft mc) {
        if (mc.thePlayer.getHeldItem() == null) return false;
        Item item = mc.thePlayer.getHeldItem().getItem();
        return item instanceof ItemSword || item == Items.stick;
    }

    private int findSwordSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.thePlayer.inventory.getStackInSlot(i) != null) {
                Item item = mc.thePlayer.inventory.getStackInSlot(i).getItem();
                if (item instanceof ItemSword || item == Items.stick) return i;
            }
        }
        return -1;
    }

    private double getDistanceTo(EntityLivingBase entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || entity == null) return Double.MAX_VALUE;
        return mc.thePlayer.getDistanceToEntity(entity);
    }

    private KillAuraModule getKillAuraModule() {
        com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
        if (client != null) return client.getModuleManager().getModule(KillAuraModule.class);
        return null;
    }

    private LegitScaffoldModule getScaffoldModule() {
        com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
        if (client != null) return client.getModuleManager().getModule(LegitScaffoldModule.class);
        return null;
    }

    public boolean isBlocking() { return isBlocking; }

    @Override
    public String getHudInfo() {
        return isBlocking ? "Blocking" : "";
    }
}
