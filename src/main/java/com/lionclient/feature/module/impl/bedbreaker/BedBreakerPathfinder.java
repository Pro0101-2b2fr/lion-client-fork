package com.lionclient.feature.module.impl.bedbreaker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;

/**
 * Path computation for BedBreaker. Uses 0-1 BFS to find the shortest
 * sequence of solid blocks to break to create a clear path from the
 * player to the target bed.
 *
 * <p>Moving through air costs 0 (free traversal).
 * Moving through a solid block costs 1 (one block to mine).</p>
 */
public final class BedBreakerPathfinder {

    private static final int SCAN_RADIUS = 8;
    private static final BlockPos[] NEIGHBORS = {
        new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
        new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
        new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
    };

    /**
     * Computes the shortest sequence of blocks to break to create a clear path
     * from the player to the bed. Uses 0-1 BFS in a mixed air/solid graph.
     *
     * @return list of BlockPos that need to be broken, in order from nearest to farthest.
     */
    public static List<BlockPos> computeBreakPath(
            Minecraft minecraft, EntityPlayerSP player,
            BlockPos start, BlockPos bedTarget, BlockPos targetBedSecond, int maxRange) {

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
            if (isAdjacentToBed(current, bedTarget, targetBedSecond)) {
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

    /**
     * Checks whether the given position is adjacent (6-directional) to either half of the bed.
     */
    public static boolean isAdjacentToBed(BlockPos pos, BlockPos bedPos, BlockPos targetBedSecond) {
        for (BlockPos neighbor : NEIGHBORS) {
            BlockPos adj = pos.add(neighbor);
            if (adj.equals(bedPos) || adj.equals(targetBedSecond)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the block at the given position is a solid, breakable block
     * (not air, bed, bedrock, barrier, or unbreakable).
     */
    public static boolean isSolidBreakable(Minecraft minecraft, BlockPos pos) {
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

    /**
     * Returns true if the block at the given position is air or a bed block.
     */
    public static boolean isAirOrBed(Minecraft minecraft, BlockPos pos) {
        Block block = minecraft.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.air || block.getMaterial() == Material.air || block instanceof BlockBed;
    }

    /**
     * Returns true if the player is within reach distance (5 blocks) of the given position.
     */
    public static boolean canReachBlock(EntityPlayerSP player, BlockPos pos) {
        double dx = pos.getX() + 0.5D - player.posX;
        double dy = pos.getY() + 0.5D - (player.posY + player.getEyeHeight());
        double dz = pos.getZ() + 0.5D - player.posZ;
        return dx * dx + dy * dy + dz * dz <= 5.0D * 5.0D;
    }
}
