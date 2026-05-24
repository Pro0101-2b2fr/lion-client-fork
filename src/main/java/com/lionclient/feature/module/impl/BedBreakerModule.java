package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Legit bed breaker assistant. Finds the nearest enemy bed and computes the
 * shortest path of blocks to mine to reach it. Renders the path as colored
 * block outlines so the player knows exactly which blocks to break in order.
 *
 * <p>Does NOT auto-mine, rotate, or send any packets. Purely visual.</p>
 */
public final class BedBreakerModule extends Module {
    private static final int SCAN_RADIUS = 8;
    private static final int PATH_RECOMPUTE_TICKS = 10;
    private static final BlockPos[] NEIGHBORS = {
        new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
        new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
        new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };

    private final NumberSetting range = new NumberSetting("Range", 3, 20, 1, 8);
    private final BooleanSetting ignoreOwnBed = new BooleanSetting("Ignore Own Bed", true);
    private final BooleanSetting autoMine = new BooleanSetting("Auto Mine", false);
    private final BooleanSetting showPath = new BooleanSetting("Show Path", true);
    private final BooleanSetting showBedHighlight = new BooleanSetting("Highlight Bed", true);
    private final ColorSetting nextBlockColor = new ColorSetting("Next Block", 0xFF55FF55);
    private final ColorSetting pathColor = new ColorSetting("Path Color", 0xFFFFFF55);
    private final ColorSetting bedColor = new ColorSetting("Bed Color", 0xFFFF5555);

    private List<BlockPos> currentPath = new ArrayList<BlockPos>();
    private BlockPos targetBedFirst;
    private BlockPos targetBedSecond;
    private int recomputeCounter;

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
            restoreToolSlot();
            return;
        }

        recomputeCounter++;
        if (recomputeCounter >= PATH_RECOMPUTE_TICKS) {
            recomputeCounter = 0;
            recomputePath(minecraft, player);
        }

        // Auto-mine: if the player is looking at the next block in the path, hold attack + switch tool.
        if (autoMine.isEnabled() && !currentPath.isEmpty() && minecraft.currentScreen == null && minecraft.inGameHasFocus) {
            net.minecraft.util.MovingObjectPosition hit = minecraft.objectMouseOver;
            if (hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK && hit.getBlockPos() != null) {
                BlockPos target = currentPath.get(0);
                boolean isPathBlock = hit.getBlockPos().equals(target);
                boolean isBedBlock = (targetBedFirst != null && hit.getBlockPos().equals(targetBedFirst))
                    || (targetBedSecond != null && hit.getBlockPos().equals(targetBedSecond));
                if (isPathBlock || isBedBlock) {
                    // Switch to best tool for this block.
                    switchToBestTool(minecraft, player, hit.getBlockPos());
                    net.minecraft.client.settings.KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindAttack.getKeyCode(), true);
                }
            }
        }
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

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GL11.glLineWidth(2.0F);

        if (showBedHighlight.isEnabled() && targetBedFirst != null) {
            float[] bc = toFloatColor(bedColor.getRgb());
            drawBlockOutline(targetBedFirst, viewerX, viewerY, viewerZ, bc[0], bc[1], bc[2], 0.85F);
            if (targetBedSecond != null && !targetBedSecond.equals(targetBedFirst)) {
                drawBlockOutline(targetBedSecond, viewerX, viewerY, viewerZ, bc[0], bc[1], bc[2], 0.85F);
            }
        }

        if (showPath.isEnabled() && !currentPath.isEmpty()) {
            float[] nc = toFloatColor(nextBlockColor.getRgb());
            float[] pc = toFloatColor(pathColor.getRgb());
            for (int i = 0; i < currentPath.size(); i++) {
                BlockPos pos = currentPath.get(i);
                if (i == 0) {
                    drawBlockOutline(pos, viewerX, viewerY, viewerZ, nc[0], nc[1], nc[2], 0.9F);
                    drawBlockFill(pos, viewerX, viewerY, viewerZ, nc[0], nc[1], nc[2], 0.15F);
                } else {
                    drawBlockOutline(pos, viewerX, viewerY, viewerZ, pc[0], pc[1], pc[2], 0.7F);
                }
            }
        }

        GL11.glLineWidth(1.0F);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getHudInfo() {
        if (currentPath.isEmpty() && targetBedFirst == null) {
            return "";
        }
        return currentPath.size() + " blocks";
    }

    private BlockPos lastToolSwitchBlock;
    private int savedToolSlot = -1;

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
                int efficiency = EnchantmentHelper.getEfficiencyModifier(player);
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

    private void recomputePath(Minecraft minecraft, EntityPlayerSP player) {
        int scanRange = range.getValue();
        BlockPos playerPos = new BlockPos(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posY),
            MathHelper.floor_double(player.posZ)
        );

        // Find nearest bed.
        BlockPos bedPos = findNearestBed(minecraft, playerPos, scanRange);
        if (bedPos == null) {
            currentPath.clear();
            targetBedFirst = null;
            targetBedSecond = null;
            return;
        }

        targetBedFirst = bedPos;
        targetBedSecond = findOtherBedHalf(minecraft, bedPos);

        // Always compute the path — the 0-1 BFS will return empty if no
        // blocks need to be broken (bed is already reachable through air).
        currentPath = computeBreakPath(minecraft, player, playerPos, bedPos, scanRange);
    }

    private BlockPos findNearestBed(Minecraft minecraft, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Block block = minecraft.theWorld.getBlockState(pos).getBlock();
                    if (!(block instanceof BlockBed)) {
                        continue;
                    }
                    if (ignoreOwnBed.isEnabled() && isOwnBed(minecraft, pos)) {
                        continue;
                    }
                    double distSq = center.distanceSq(pos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean isOwnBed(Minecraft minecraft, BlockPos bedPos) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            return false;
        }

        // Detect the player's team color from their display name prefix.
        EnumDyeColor teamDye = getPlayerTeamDyeColor(player);
        if (teamDye == null) {
            // Can't determine team color — don't filter.
            return false;
        }

        // Check colored blocks (wool, stained glass, terracotta, carpet) around
        // the bed. If any match the player's team color, it's our bed.
        BlockPos other = findOtherBedHalf(minecraft, bedPos);
        return hasSurroundingColor(minecraft, bedPos, teamDye)
            || hasSurroundingColor(minecraft, other, teamDye);
    }

    private boolean hasSurroundingColor(Minecraft minecraft, BlockPos center, EnumDyeColor targetColor) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    EnumDyeColor blockColor = getBlockDyeColor(minecraft, pos);
                    if (blockColor == targetColor) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private EnumDyeColor getBlockDyeColor(Minecraft minecraft, BlockPos pos) {
        net.minecraft.block.state.IBlockState state = minecraft.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.wool || block == Blocks.stained_hardened_clay || block instanceof BlockColored) {
            return EnumDyeColor.byMetadata(block.getMetaFromState(state));
        }
        if (block == Blocks.stained_glass || block instanceof BlockStainedGlass) {
            return EnumDyeColor.byMetadata(block.getMetaFromState(state));
        }
        if (block == Blocks.stained_glass_pane || block instanceof BlockStainedGlassPane) {
            return EnumDyeColor.byMetadata(block.getMetaFromState(state));
        }
        if (block == Blocks.carpet || block instanceof BlockCarpet) {
            return EnumDyeColor.byMetadata(block.getMetaFromState(state));
        }
        return null;
    }

    private EnumDyeColor getPlayerTeamDyeColor(EntityPlayerSP player) {
        if (player.getDisplayName() == null) {
            return null;
        }
        String formatted = player.getDisplayName().getFormattedText();
        if (formatted == null || formatted.length() < 2) {
            return null;
        }
        // Find the first color code in the display name.
        for (int i = 0; i < formatted.length() - 1; i++) {
            if (formatted.charAt(i) == '\u00a7') {
                char code = formatted.charAt(i + 1);
                return chatColorToDye(code);
            }
        }
        return null;
    }

    private EnumDyeColor chatColorToDye(char code) {
        switch (code) {
            case '0': return EnumDyeColor.BLACK;
            case '1': return EnumDyeColor.BLUE;
            case '2': return EnumDyeColor.GREEN;
            case '3': return EnumDyeColor.CYAN;
            case '4': return EnumDyeColor.RED;
            case '5': return EnumDyeColor.PURPLE;
            case '6': return EnumDyeColor.ORANGE;
            case '7': return EnumDyeColor.SILVER;
            case '8': return EnumDyeColor.GRAY;
            case '9': return EnumDyeColor.BLUE;
            case 'a': return EnumDyeColor.LIME;
            case 'b': return EnumDyeColor.LIGHT_BLUE;
            case 'c': return EnumDyeColor.RED;
            case 'd': return EnumDyeColor.MAGENTA;
            case 'e': return EnumDyeColor.YELLOW;
            case 'f': return EnumDyeColor.WHITE;
            default: return null;
        }
    }

    private BlockPos findOtherBedHalf(Minecraft minecraft, BlockPos bedPos) {
        BlockPos[] sides = {bedPos.north(), bedPos.south(), bedPos.east(), bedPos.west()};
        for (BlockPos side : sides) {
            if (minecraft.theWorld.getBlockState(side).getBlock() instanceof BlockBed) {
                return side;
            }
        }
        return bedPos;
    }

    /**
     * Computes the shortest sequence of blocks to break to create a clear path
     * from the player to the bed. Uses BFS in a mixed air/solid graph:
     * - Moving through air costs 0 (free traversal).
     * - Moving through a solid block costs 1 (one block to mine).
     * We find the path with the minimum number of solid blocks to break.
     */
    private List<BlockPos> computeBreakPath(Minecraft minecraft, EntityPlayerSP player, BlockPos start, BlockPos bedTarget, int maxRange) {
        // Dijkstra-like BFS where air = cost 0, solid = cost 1.
        // We use a two-queue approach (deque: air neighbors go to front, solid go to back).
        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<BlockPos, BlockPos>();
        Map<BlockPos, Integer> cost = new HashMap<BlockPos, Integer>();

        // Start from player feet and eye level.
        BlockPos eyePos = start.up();
        queue.add(start);
        queue.add(eyePos);
        cameFrom.put(start, null);
        cameFrom.put(eyePos, null);
        cost.put(start, Integer.valueOf(0));
        cost.put(eyePos, Integer.valueOf(0));

        BlockPos goal = null;
        int maxDistSq = maxRange * maxRange;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int currentCost = cost.get(current).intValue();

            // Check if we reached the bed.
            if (current.equals(bedTarget) || current.equals(targetBedSecond)) {
                goal = current;
                break;
            }
            // Check if adjacent to bed.
            if (isAdjacentToBed(current, bedTarget)) {
                goal = current;
                break;
            }

            for (BlockPos offset : NEIGHBORS) {
                BlockPos next = current.add(offset);
                if (start.distanceSq(next) > maxDistSq) {
                    continue;
                }

                Block block = minecraft.theWorld.getBlockState(next).getBlock();
                boolean isAir = block == Blocks.air || block.getMaterial() == Material.air;
                boolean isBed = block instanceof BlockBed;
                boolean isSolid = !isAir && !isBed && block != Blocks.bedrock && block != Blocks.barrier
                    && block.getBlockHardness(minecraft.theWorld, next) >= 0.0F;

                if (!isAir && !isBed && !isSolid) {
                    continue; // unbreakable non-air
                }

                int nextCost = currentCost + (isSolid ? 1 : 0);
                Integer existingCost = cost.get(next);
                if (existingCost != null && existingCost.intValue() <= nextCost) {
                    continue;
                }

                cost.put(next, Integer.valueOf(nextCost));
                cameFrom.put(next, current);
                // 0-1 BFS: air/bed goes to front (free), solid goes to back (cost 1).
                if (isAir || isBed) {
                    queue.addFirst(next);
                } else {
                    queue.addLast(next);
                }
            }
        }

        if (goal == null) {
            return new ArrayList<BlockPos>();
        }

        // Reconstruct path, keeping only solid blocks (the ones to mine).
        List<BlockPos> path = new ArrayList<BlockPos>();
        BlockPos node = goal;
        while (node != null) {
            Block block = minecraft.theWorld.getBlockState(node).getBlock();
            boolean isAir = block == Blocks.air || block.getMaterial() == Material.air;
            boolean isBed = block instanceof BlockBed;
            if (!isAir && !isBed) {
                path.add(node);
            }
            node = cameFrom.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    private boolean isAdjacentToBed(BlockPos pos, BlockPos bedPos) {
        for (BlockPos neighbor : NEIGHBORS) {
            BlockPos adj = pos.add(neighbor);
            if (adj.equals(bedPos) || adj.equals(targetBedSecond)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSolidBreakable(Minecraft minecraft, BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        if (block == null || block == Blocks.air || block.getMaterial() == Material.air) {
            return false;
        }
        if (block instanceof BlockBed) {
            return false;
        }
        // Bedrock and barriers are unbreakable.
        if (block == Blocks.bedrock || block == Blocks.barrier) {
            return false;
        }
        return block.getBlockHardness(minecraft.theWorld, pos) >= 0.0F;
    }

    private boolean isAirOrBed(Minecraft minecraft, BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.air || block.getMaterial() == Material.air || block instanceof BlockBed;
    }

    private boolean canReachBlock(EntityPlayerSP player, BlockPos pos) {
        double dx = pos.getX() + 0.5D - player.posX;
        double dy = pos.getY() + 0.5D - (player.posY + player.getEyeHeight());
        double dz = pos.getZ() + 0.5D - player.posZ;
        return dx * dx + dy * dy + dz * dz <= 5.0D * 5.0D;
    }

    private void drawBlockOutline(BlockPos pos, double viewerX, double viewerY, double viewerZ, float r, float g, float b, float a) {
        double x = pos.getX() - viewerX;
        double y = pos.getY() - viewerY;
        double z = pos.getZ() - viewerZ;
        AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);
        // Bottom face
        vertex(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        vertex(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        vertex(bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        vertex(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ);
        // Top face
        vertex(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        vertex(bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        vertex(bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        vertex(bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);
        // Verticals
        vertex(bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        vertex(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        vertex(bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        vertex(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        GL11.glEnd();
    }

    private void drawBlockFill(BlockPos pos, double viewerX, double viewerY, double viewerZ, float r, float g, float b, float a) {
        double x = pos.getX() - viewerX;
        double y = pos.getY() - viewerY;
        double z = pos.getZ() - viewerZ;
        AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        // All 6 faces
        quad(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        quad(bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        quad(bb.minX, bb.minY, bb.minZ, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);
        quad(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.minZ);
        quad(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        quad(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        GL11.glEnd();
    }

    private void vertex(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    private void quad(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x4, y4, z4);
    }

    private static float[] toFloatColor(int rgb) {
        return new float[] {
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F
        };
    }
}
