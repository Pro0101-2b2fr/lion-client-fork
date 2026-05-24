package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Switches to the best matching tool in the hotbar when the player starts
 * mining a block, then optionally swaps back when mining ends. The module
 * never sends slot-change packets manually: it only mutates
 * {@code inventory.currentItem} so that vanilla
 * {@code EntityPlayerSP.onUpdateWalkingPlayer} emits a single
 * {@code C09PacketHeldItemChange} on the next tick. Swaps are rate-limited
 * and only re-evaluated when the targeted block changes, preventing the
 * Vulcan {@code Badpackets/Hotbar} flag.
 */
public final class AutoToolModule extends Module {
    private final BooleanSetting swapBack = new BooleanSetting("Swap Back", true);
    private final BooleanSetting includeSilkTouch = new BooleanSetting("Prefer Silk Touch", false);
    private final NumberSetting swapCooldown = new NumberSetting("Swap Cooldown", 50, 600, 10, 200);

    private int savedSlot = -1;
    private BlockPos lastBlockPos;
    private long nextSwapAllowedAt;

    public AutoToolModule() {
        super("AutoTool", "Automatically switches to the best tool while mining.", Category.PLAYER, Keyboard.KEY_NONE);
        addSetting(swapBack);
        addSetting(includeSilkTouch);
        addSetting(swapCooldown);
    }

    @Override
    protected void onEnable() {
        savedSlot = -1;
        lastBlockPos = null;
        nextSwapAllowedAt = 0L;
    }

    @Override
    protected void onDisable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null && savedSlot != -1 && swapBack.isEnabled()) {
            applySlot(minecraft.thePlayer, savedSlot);
        }
        savedSlot = -1;
        lastBlockPos = null;
        nextSwapAllowedAt = 0L;
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null) {
            return;
        }

        if (!Mouse.isButtonDown(0) || minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            handleStop(player);
            return;
        }

        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.getBlockPos() == null) {
            handleStop(player);
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (pos.equals(lastBlockPos)) {
            // Already evaluated this block: do not re-emit packets.
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextSwapAllowedAt) {
            return;
        }

        IBlockState state = minecraft.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null) {
            handleStop(player);
            return;
        }

        int bestSlot = findBestSlot(player, state, block);
        lastBlockPos = pos;
        if (bestSlot == -1 || bestSlot == player.inventory.currentItem) {
            return;
        }

        if (savedSlot == -1) {
            savedSlot = player.inventory.currentItem;
        }
        applySlot(player, bestSlot);
        nextSwapAllowedAt = now + swapCooldown.getValue();
    }

    private void handleStop(EntityPlayerSP player) {
        if (savedSlot != -1) {
            if (swapBack.isEnabled() && savedSlot != player.inventory.currentItem) {
                applySlot(player, savedSlot);
                nextSwapAllowedAt = System.currentTimeMillis() + swapCooldown.getValue();
            }
            savedSlot = -1;
        }
        lastBlockPos = null;
    }

    private void applySlot(EntityPlayerSP player, int slot) {
        if (slot < 0 || slot > 8) {
            return;
        }
        if (player.inventory.currentItem == slot) {
            return;
        }
        // Only mutate the field. Vanilla EntityPlayerSP.onUpdateWalkingPlayer
        // detects the change and sends a single C09PacketHeldItemChange next
        // tick - sending one ourselves would duplicate the packet.
        player.inventory.currentItem = slot;
    }

    private int findBestSlot(EntityPlayerSP player, IBlockState state, Block block) {
        int bestSlot = -1;
        double bestScore = -1.0D;

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            double score = scoreToolForBlock(stack, state, block);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestScore <= 0.0D ? -1 : bestSlot;
    }

    private double scoreToolForBlock(ItemStack stack, IBlockState state, Block block) {
        Item item = stack.getItem();
        if (item == null) {
            return 0.0D;
        }

        double base = stack.getStrVsBlock(block);
        if (base <= 1.0D) {
            base = matchesByCategory(item, block) ? 2.0D : 1.0D;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null && item instanceof ItemTool) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(32, stack);
            if (efficiency > 0) {
                base += efficiency * efficiency + 1.0D;
            }
        }

        if (includeSilkTouch.isEnabled() && EnchantmentHelper.getEnchantmentLevel(33, stack) > 0) {
            base += 50.0D;
        }
        if (item instanceof ItemSword && block != net.minecraft.init.Blocks.web) {
            base *= 0.5D;
        }
        if (item == Items.shears && block == net.minecraft.init.Blocks.web) {
            base += 5.0D;
        }
        return base;
    }

    private boolean matchesByCategory(Item item, Block block) {
        String name = block.getUnlocalizedName();
        if (item instanceof ItemPickaxe) {
            return name.contains("stone") || name.contains("ore") || name.contains("metal") || name.contains("iron");
        }
        if (item instanceof ItemSpade) {
            return name.contains("dirt") || name.contains("sand") || name.contains("gravel") || name.contains("snow") || name.contains("clay");
        }
        if (item instanceof ItemAxe) {
            return name.contains("wood") || name.contains("log") || name.contains("planks");
        }
        if (item instanceof ItemShears) {
            return name.contains("wool") || name.contains("leaves") || name.contains("vine") || name.contains("web");
        }
        return false;
    }
}
