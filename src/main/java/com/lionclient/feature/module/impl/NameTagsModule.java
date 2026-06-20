package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Renders enlarged nametags with optional health display, visible through walls.
 * Cancels vanilla nametag rendering to avoid duplicates.
 */
public final class NameTagsModule extends Module {
    private final DecimalSetting scale = new DecimalSetting("Scale", 0.5D, 4.0D, 0.1D, 1.8D);
    private final BooleanSetting showHealth = new BooleanSetting("Show Health", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Through Walls", true);

    private boolean forgeRegistered;

    public NameTagsModule() {
        super("NameTags", "Renders bigger nametags with health, visible through walls.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(scale);
        addSetting(showHealth);
        addSetting(throughWalls);
    }

    @Override
    protected void onEnable() {
        registerForge();
    }

    @Override
    protected void onDisable() {
        unregisterForge();
    }

    /** Cancel vanilla nametag rendering for players. */
    @SubscribeEvent
    public void onRenderLivingSpecials(RenderLivingEvent.Specials.Pre event) {
        if (event.entity instanceof EntityPlayer && event.entity != Minecraft.getMinecraft().thePlayer) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }

        float partialTicks = event.partialTicks;
        double viewerX = minecraft.getRenderManager().viewerPosX;
        double viewerY = minecraft.getRenderManager().viewerPosY;
        double viewerZ = minecraft.getRenderManager().viewerPosZ;

        for (Object obj : minecraft.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) obj;
            if (player == minecraft.thePlayer || player.isDead) {
                continue;
            }
            if (player.isInvisible()) {
                continue;
            }
            if (AntiBotModule.shouldIgnore(player)) {
                continue;
            }

            double x = interpolate(player.lastTickPosX, player.posX, partialTicks) - viewerX;
            double y = interpolate(player.lastTickPosY, player.posY, partialTicks) - viewerY + player.height + 0.3D;
            double z = interpolate(player.lastTickPosZ, player.posZ, partialTicks) - viewerZ;

            renderTag(minecraft, player, x, y, z);
        }
    }

    private void renderTag(Minecraft minecraft, EntityPlayer player, double x, double y, double z) {
        FontRenderer font = minecraft.fontRendererObj;
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        // Dynamic scaling: scale strictly proportional to distance so the tag
        // occupies a constant amount of SCREEN space at any range (apparent size
        // ~ worldScale / distance, so worldScale must grow linearly with distance).
        // The previous Math.max(1.0, ...) floor pinned the world size below 8m,
        // which made the tag shrink on screen as you backed away up close.
        float baseScale = (float) (scale.getValue() * 0.026F);
        float dynamicScale = baseScale * (Math.max(distance, 1.0F) / 8.0F);

        String name = player.getDisplayName().getFormattedText();
        String text = name;
        if (showHealth.isEnabled()) {
            int health = Math.round(player.getHealth());
            int maxHealth = Math.round(player.getMaxHealth());
            String healthColor = health > maxHealth * 0.5F ? "\u00a7a" : health > maxHealth * 0.25F ? "\u00a7e" : "\u00a7c";
            text = name + " " + healthColor + health + "\u00a7f\u2764";
        }

        int textWidth = font.getStringWidth(text);
        int halfWidth = textWidth / 2;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(minecraft.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-dynamicScale, -dynamicScale, dynamicScale);

        if (throughWalls.isEnabled()) {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // Background
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(-halfWidth - 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        renderer.pos(-halfWidth - 2, font.FONT_HEIGHT + 1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        renderer.pos(halfWidth + 2, font.FONT_HEIGHT + 1, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        renderer.pos(halfWidth + 2, -2, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        // Text
        font.drawStringWithShadow(text, -halfWidth, 0, 0xFFFFFFFF);

        if (throughWalls.isEnabled()) {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private double interpolate(double prev, double current, float partialTicks) {
        return prev + (current - prev) * partialTicks;
    }

    private void registerForge() {
        if (forgeRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        forgeRegistered = true;
    }

    private void unregisterForge() {
        if (!forgeRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        forgeRegistered = false;
    }
}
