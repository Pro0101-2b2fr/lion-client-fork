package com.lionclient.feature.module.impl.bedbreaker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

/**
 * OpenGL rendering helpers for BedBreaker.
 * Draws block outlines and filled faces for the mining path and bed highlight.
 */
public final class BedBreakerRenderer {

    /**
     * Sets up the GL state for world-space rendering (disable texture/depth, enable blend).
     */
    public static void begin() {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GL11.glLineWidth(2.0F);
    }

    /**
     * Restores GL state after rendering.
     */
    public static void end() {
        GL11.glLineWidth(1.0F);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws a wireframe outline of a single block.
     */
    public static void drawBlockOutline(BlockPos pos, double viewerX, double viewerY, double viewerZ,
                                        float r, float g, float b, float a) {
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

    /**
     * Draws filled faces of a single block (all 6 sides).
     */
    public static void drawBlockFill(BlockPos pos, double viewerX, double viewerY, double viewerZ,
                                     float r, float g, float b, float a) {
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

    /**
     * Emits two vertices forming a line segment.
     */
    public static void vertex(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    /**
     * Emits four vertices forming a quad face.
     */
    public static void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                            double x3, double y3, double z3, double x4, double y4, double z4) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x4, y4, z4);
    }

    /**
     * Converts an RGB int (0xRRGGBB) to a float array [r, g, b] in 0..1 range.
     */
    public static float[] toFloatColor(int rgb) {
        return new float[] {
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F
        };
    }
}
