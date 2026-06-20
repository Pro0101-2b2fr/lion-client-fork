package com.lionclient.feature.module.impl.bedbreaker;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Legit bed breaker assistant. Finds the nearest enemy bed and computes the
 * shortest path of blocks to mine to reach it. Renders the path as colored
 * block outlines so the player knows exactly which blocks to break in order.
 *
 * <p>Does NOT auto-mine, rotate, or send any packets. Purely visual
 * unless Auto Mine is enabled.</p>
 */
public final class BedBreakerModule extends Module {
    private static final int PATH_RECOMPUTE_TICKS = 10;

    private final NumberSetting range = new NumberSetting("Range", 3, 20, 1, 8);
    private final BooleanSetting ignoreOwnBed = new BooleanSetting("Ignore Own Bed", true);
    private final BooleanSetting autoMine = new BooleanSetting("Auto Mine", false);
    private final BooleanSetting showPath = new BooleanSetting("Show Path", true);
    private final BooleanSetting showBedHighlight = new BooleanSetting("Highlight Bed", true);
    private final ColorSetting nextBlockColor = new ColorSetting("Next Block", 0xFF55FF55);
    private final ColorSetting pathColor = new ColorSetting("Path Color", 0xFFFFFF55);
    private final ColorSetting bedColor = new ColorSetting("Bed Color", 0xFFFF5555);

    private List<BlockPos> currentPath = new java.util.ArrayList<BlockPos>();
    private BlockPos targetBedFirst;
    private BlockPos targetBedSecond;
    private int recomputeCounter;

    private BlockPos lastToolSwitchBlock;
    private int savedToolSlot = -1;
    private boolean attackHeld;

    public BedBreakerModule() {
        super("BedBreaker", "Shows the fastest mining path to the nearest bed.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(range);
        addSetting(ignoreOwnBed);
        addSetting(autoMine);
        addSetting(showPath);
        addSetting(showBedHighlight);
        addSetting(nextBlockColor);
        addSetting(pathColor);
        addSetting(bedColor);
    }

    @Override
    protected void onEnable() {
        currentPath.clear();
        targetBedFirst = null;
        targetBedSecond = null;
        recomputeCounter = 0;
    }

    @Override
    protected void onDisable() {
        currentPath.clear();
        targetBedFirst = null;
        targetBedSecond = null;
        releaseAttack(Minecraft.getMinecraft());
        restoreToolSlot();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null) {
            currentPath.clear();
            releaseAttack(minecraft);
            restoreToolSlot();
            return;
        }

        recomputeCounter++;
        if (recomputeCounter >= PATH_RECOMPUTE_TICKS) {
            recomputeCounter = 0;
            recomputePath(minecraft, player);
        }

        // Auto-mine: if the player is looking at the next block in the path, hold attack + switch tool.
        boolean mining = false;
        if (autoMine.isEnabled() && !currentPath.isEmpty() && minecraft.currentScreen == null && minecraft.inGameHasFocus) {
            net.minecraft.util.MovingObjectPosition hit = minecraft.objectMouseOver;
            if (hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK && hit.getBlockPos() != null) {
                BlockPos target = currentPath.get(0);
                boolean isPathBlock = hit.getBlockPos().equals(target);
                boolean isBedBlock = (targetBedFirst != null && hit.getBlockPos().equals(targetBedFirst))
                    || (targetBedSecond != null && hit.getBlockPos().equals(targetBedSecond));
                if (isPathBlock || isBedBlock) {
                    switchToBestTool(minecraft, player, hit.getBlockPos());
                    net.minecraft.client.settings.KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), true);
                    attackHeld = true;
                    mining = true;
                }
            }
        }

        // Release the attack key as soon as we stop actively mining a valid block,
        // otherwise it stays stuck down (continuous left-click).
        if (!mining) {
            releaseAttack(minecraft);
        }
    }

    private void releaseAttack(Minecraft minecraft) {
        if (!attackHeld) {
            return;
        }
        if (minecraft != null && minecraft.gameSettings != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), false);
        }
        attackHeld = false;
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }

        double viewerX = minecraft.getRenderManager().viewerPosX;
        double viewerY = minecraft.getRenderManager().viewerPosY;
        double viewerZ = minecraft.getRenderManager().viewerPosZ;

        BedBreakerRenderer.begin();

        if (showBedHighlight.isEnabled() && targetBedFirst != null) {
            float[] bc = BedBreakerRenderer.toFloatColor(bedColor.getRgb());
            BedBreakerRenderer.drawBlockOutline(targetBedFirst, viewerX, viewerY, viewerZ, bc[0], bc[1], bc[2], 0.85F);
            if (targetBedSecond != null && !targetBedSecond.equals(targetBedFirst)) {
                BedBreakerRenderer.drawBlockOutline(targetBedSecond, viewerX, viewerY, viewerZ, bc[0], bc[1], bc[2], 0.85F);
            }
        }

        if (showPath.isEnabled() && !currentPath.isEmpty()) {
            float[] nc = BedBreakerRenderer.toFloatColor(nextBlockColor.getRgb());
            float[] pc = BedBreakerRenderer.toFloatColor(pathColor.getRgb());
            for (int i = 0; i < currentPath.size(); i++) {
                BlockPos pos = currentPath.get(i);
                if (i == 0) {
                    BedBreakerRenderer.drawBlockOutline(pos, viewerX, viewerY, viewerZ, nc[0], nc[1], nc[2], 0.9F);
                    BedBreakerRenderer.drawBlockFill(pos, viewerX, viewerY, viewerZ, nc[0], nc[1], nc[2], 0.15F);
                } else {
                    BedBreakerRenderer.drawBlockOutline(pos, viewerX, viewerY, viewerZ, pc[0], pc[1], pc[2], 0.7F);
                }
            }
        }

        BedBreakerRenderer.end();
    }

    @Override
    public String getHudInfo() {
        if (currentPath.isEmpty() && targetBedFirst == null) {
            return "";
        }
        return currentPath.size() + " blocks";
    }

    // --- Path recomputation (delegates to scanner + pathfinder) ---

    private void recomputePath(Minecraft minecraft, EntityPlayerSP player) {
        int scanRange = range.getValue();
        BlockPos playerPos = new BlockPos(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ)
        );

        // Find nearest bed.
        BlockPos bedPos = BedBreakerScanner.findNearestBed(minecraft, playerPos, scanRange, ignoreOwnBed.isEnabled());
        if (bedPos == null) {
            currentPath.clear();
            targetBedFirst = null;
            targetBedSecond = null;
            return;
        }

        targetBedFirst = bedPos;
        targetBedSecond = BedBreakerScanner.findOtherBedHalf(minecraft, bedPos);

        // Always compute the path — the 0-1 BFS will return empty if no
        // blocks need to be breaking (bed is already reachable through air).
        currentPath = BedBreakerPathfinder.computeBreakPath(minecraft, player, playerPos, bedPos, targetBedSecond, scanRange);
    }

    // --- Auto-mine tool switching ---

    private void switchToBestTool(Minecraft minecraft, EntityPlayerSP player, BlockPos pos) {
        // Only switch once per target block.
        if (pos.equals(lastToolSwitchBlock)) {
            return;
        }
        lastToolSwitchBlock = pos;

        IBlockState state = minecraft.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == null) {
            return;
        }

        int bestSlot = -1;
        double bestScore = -1.0D;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            double score = stack.getStrVsBlock(block);
            Item item = stack.getItem();
            if (item instanceof ItemTool) {
                int efficiency = EnchantmentHelper.getEnchantmentLevel(32, stack);
                if (efficiency > 0) {
                    score += efficiency * efficiency + 1.0D;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        if (bestSlot == -1 || bestSlot == player.inventory.currentItem) {
            return;
        }
        if (savedToolSlot == -1) {
            savedToolSlot = player.inventory.currentItem;
        }
        player.inventory.currentItem = bestSlot;
    }

    private void restoreToolSlot() {
        if (savedToolSlot == -1) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.inventory.currentItem = savedToolSlot;
        }
        savedToolSlot = -1;
        lastToolSwitchBlock = null;
    }
}
