package com.lionclient.feature.module.impl.bedbreaker;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.BlockPos;

/**
 * Bed scanning and detection for BedBreaker.
 * Finds the nearest enemy bed, determines team ownership via color matching,
 * and locates the second half of a bed block.
 */
public final class BedBreakerScanner {

    /**
     * Scans a cubic region around {@code center} for the nearest bed block
     * that is not the player's own bed (when {@code ignoreOwnBed} is true).
     *
     * @return the BlockPos of the nearest enemy bed, or null if none found.
     */
    public static BlockPos findNearestBed(Minecraft minecraft, BlockPos center, int radius, boolean ignoreOwnBed) {
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
                    if (ignoreOwnBed && isOwnBed(minecraft, pos)) {
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

    /**
     * Determines whether the bed at {@code bedPos} belongs to the player's team
     * by checking surrounding colored blocks (wool, stained glass, carpet, etc.)
     * against the player's team dye color.
     *
     * @return true if the bed appears to be the player's own.
     */
    public static boolean isOwnBed(Minecraft minecraft, BlockPos bedPos) {
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

    /**
     * Checks whether any colored block in a 5×3×5 region around {@code center}
     * matches the given {@code targetColor}.
     */
    public static boolean hasSurroundingColor(Minecraft minecraft, BlockPos center, EnumDyeColor targetColor) {
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

    /**
     * Returns the dye color of a block at the given position, if it is a
     * colorable block (wool, stained hardened clay, stained glass, stained glass pane, carpet).
     * Returns null if the block has no dye color.
     */
    public static EnumDyeColor getBlockDyeColor(Minecraft minecraft, BlockPos pos) {
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

    /**
     * Extracts the player's team dye color from the color code prefix
     * in their display name.
     *
     * @return the EnumDyeColor, or null if no color code is found.
     */
    public static EnumDyeColor getPlayerTeamDyeColor(EntityPlayerSP player) {
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

    /**
     * Maps a Minecraft chat formatting code character to the corresponding EnumDyeColor.
     */
    public static EnumDyeColor chatColorToDye(char code) {
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

    /**
     * Finds the other half of a bed block. Beds occupy two adjacent blocks;
     * this checks north/south/east/west of {@code bedPos} for the partner.
     *
     * @return the BlockPos of the other half, or {@code bedPos} itself if not found.
     */
    public static BlockPos findOtherBedHalf(Minecraft minecraft, BlockPos bedPos) {
        BlockPos[] sides = {bedPos.north(), bedPos.south(), bedPos.east(), bedPos.west()};
        for (BlockPos side : sides) {
            if (minecraft.theWorld.getBlockState(side).getBlock() instanceof BlockBed) {
                return side;
            }
        }
        return bedPos;
    }
}
