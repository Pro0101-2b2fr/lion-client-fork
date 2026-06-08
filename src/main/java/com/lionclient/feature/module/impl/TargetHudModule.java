package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.FloatSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class TargetHudModule extends Module implements HudElement {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 42;

    private final BooleanSetting showArmor = new BooleanSetting("Show Armor", true);
    private final FloatSetting scale = new FloatSetting("Scale", 0.5F, 2.0F, 0.1F, 1.0F);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, 120);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, 120);

    private EntityLivingBase lastTarget;
    private long lastTargetTime;
    private String cachedHpText = "";
    private String cachedDistText = "";

    public TargetHudModule() {
        super("TargetHUD", "Renders target information on screen during combat.", Category.HUD, Keyboard.KEY_NONE);
        java.util.function.BooleanSupplier hidden = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        addSetting(showArmor);
        addSetting(scale);
        addSetting(hudX);
        addSetting(hudY);
    }

    @Override
    public void onClientTick() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }

        // 1. Try to get target from KillAuraModule
        EntityLivingBase target = null;
        KillAuraModule aura = LionClient.getInstance().getModuleManager().getModule(KillAuraModule.class);
        if (aura != null && aura.isEnabled()) {
            target = aura.getTarget();
        }

        // 2. Try to fallback to pointedEntity if the player is actively hitting/aiming at it
        if (target == null && minecraft.pointedEntity instanceof EntityLivingBase) {
            target = (EntityLivingBase) minecraft.pointedEntity;
        }

        if (target != null && target != minecraft.thePlayer && !target.isDead && !AntiBotModule.shouldIgnore((EntityPlayer) (target instanceof EntityPlayer ? target : null))) {
            lastTarget = target;
            lastTargetTime = System.currentTimeMillis();
            cachedHpText = String.format("%.1f / %.1f HP", target.getHealth(), target.getMaxHealth());
            cachedDistText = String.format("Distance: %.1fm", minecraft.thePlayer.getDistanceToEntity(target));
        }
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.gameSettings.showDebugInfo) {
            return;
        }

        long timeSinceLastTarget = System.currentTimeMillis() - lastTargetTime;
        if (timeSinceLastTarget > 2000L || lastTarget == null) {
            return;
        }

        float alpha = 1.0F;
        if (timeSinceLastTarget > 1700L) {
            alpha = 1.0F - (timeSinceLastTarget - 1700L) / 300.0F;
        }
        alpha = Math.max(0.0F, Math.min(1.0F, alpha));

        ScaledResolution res = event.resolution;
        float s = scale.getValue();
        GL11.glPushMatrix();
        GL11.glScalef(s, s, 1.0F);
        drawTargetHud(lastTarget, alpha, res);
        GL11.glPopMatrix();
    }

    private void drawTargetHud(EntityLivingBase target, float alpha, ScaledResolution res) {
        Minecraft minecraft = Minecraft.getMinecraft();
        float s = scale.getValue();
        int anchorX = Math.round(hudX.getValue() / s);
        int anchorY = Math.round(hudY.getValue() / s);

        int accent = ClickGuiModule.getModernAccentColor();

        // 1. Draw Glassmorphism Card Background
        int bgAlpha = (int) (0x90 * alpha);
        int bgColor = (bgAlpha << 24) | 0x101018;
        int borderAlpha = (int) (0xCC * alpha);
        int borderColor = (borderAlpha << 24) | (accent & 0x00FFFFFF);

        Gui.drawRect(anchorX, anchorY, anchorX + WIDTH, anchorY + HEIGHT, bgColor);
        
        // Draw 1-pixel border
        Gui.drawRect(anchorX, anchorY, anchorX + WIDTH, anchorY + 1, borderColor);
        Gui.drawRect(anchorX, anchorY + HEIGHT - 1, anchorX + WIDTH, anchorY + HEIGHT, borderColor);
        Gui.drawRect(anchorX, anchorY, anchorX + 1, anchorY + HEIGHT, borderColor);
        Gui.drawRect(anchorX + WIDTH - 1, anchorY, anchorX + WIDTH, anchorY + HEIGHT, borderColor);

        // 2. Draw Target Face Avatar
        int avatarX = anchorX + 8;
        int avatarY = anchorY + 9;
        if (target instanceof EntityPlayer) {
            net.minecraft.client.entity.AbstractClientPlayer player = (net.minecraft.client.entity.AbstractClientPlayer) target;
            minecraft.getTextureManager().bindTexture(player.getLocationSkin());
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            
            // Draw base face layer
            Gui.drawScaledCustomSizeModalRect(avatarX, avatarY, 8.0F, 8.0F, 8, 8, 24, 24, 64.0F, 64.0F);
            // Draw hat layer on top
            Gui.drawScaledCustomSizeModalRect(avatarX, avatarY, 40.0F, 8.0F, 8, 8, 24, 24, 64.0F, 64.0F);
        } else {
            // Draw a generic placeholder square if not a player
            Gui.drawRect(avatarX, avatarY, avatarX + 24, avatarY + 24, scaleAlpha(0xFF4A9EFF, alpha));
        }

        // 3. Draw Target Name
        FontRenderer font = minecraft.fontRendererObj;
        String nameText = target.getName();
        if (nameText.length() > 14) {
            nameText = nameText.substring(0, 12) + "...";
        }
        font.drawStringWithShadow(nameText, anchorX + 38, anchorY + 8, scaleAlpha(0xFFFFFFFF, alpha));

        // 4. Draw Health Bar
        int barX = anchorX + 38;
        int barY = anchorY + 20;
        int barWidth = 80;
        int barHeight = 8;

        Gui.drawRect(barX, barY, barX + barWidth, barY + barHeight, scaleAlpha(0x50101010, alpha));
        
        float hpPercent = target.getHealth() / target.getMaxHealth();
        hpPercent = Math.max(0.0F, Math.min(1.0F, hpPercent));
        
        int fillWidth = (int) (barWidth * hpPercent);
        int hpColor = ClickGuiModule.blendColor(0xFF55FF55, 0xFFFF5555, 1.0F - hpPercent);
        Gui.drawRect(barX, barY, barX + fillWidth, barY + barHeight, scaleAlpha(hpColor, alpha));

        // Draw Health Text inside bar
        String hpText = cachedHpText;
        GlStateManager.pushMatrix();
        GlStateManager.translate(barX + (barWidth - font.getStringWidth(hpText) * 0.7F) / 2.0F, barY + 1.5F, 0.0F);
        GlStateManager.scale(0.7F, 0.7F, 1.0F);
        font.drawString(hpText, 0, 0, scaleAlpha(0xFFFFFFFF, alpha));
        GlStateManager.popMatrix();

        // 5. Draw Distance
        String distText = cachedDistText;
        GlStateManager.pushMatrix();
        GlStateManager.translate(anchorX + 38, anchorY + 31, 0.0F);
        GlStateManager.scale(0.8F, 0.8F, 1.0F);
        font.drawString(distText, 0, 0, scaleAlpha(0xFFACB7C2, alpha));
        GlStateManager.popMatrix();

        // 6. Draw Armor Row on Right
        if (showArmor.isEnabled() && target instanceof EntityPlayer) {
            int armorX = anchorX + 124;
            int armorY = anchorY + 11;
            RenderItem renderItem = minecraft.getRenderItem();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

            int slotX = armorX;
            for (int i = 3; i >= 0; i--) {
                ItemStack stack = target.getEquipmentInSlot(i + 1); // 4 = head, 3 = chest, 2 = legs, 1 = feet
                if (stack == null) {
                    slotX += 17;
                    continue;
                }
                
                // Set render item alpha if supported by custom shaders/renderers (or just render standard)
                renderItem.renderItemAndEffectIntoGUI(stack, slotX, armorY);
                renderItem.renderItemOverlayIntoGUI(font, stack, slotX, armorY, null);

                // Draw custom durability bar
                if (stack.getMaxDamage() > 0) {
                    double damageRatio = (double) (stack.getMaxDamage() - stack.getItemDamage()) / stack.getMaxDamage();
                    damageRatio = Math.max(0.0D, Math.min(1.0D, damageRatio));
                    int durabilityWidth = 16;
                    int durabilityHeight = 2;
                    int durabilityX = slotX;
                    int durabilityY = armorY + 17;
                    int durabilityColor = ClickGuiModule.blendColor(0xFF55FF55, 0xFFFF5555, 1.0F - (float) damageRatio);
                    
                    Gui.drawRect(durabilityX, durabilityY, durabilityX + durabilityWidth, durabilityY + durabilityHeight, scaleAlpha(0x60000000, alpha));
                    Gui.drawRect(durabilityX, durabilityY, durabilityX + (int) (durabilityWidth * damageRatio), durabilityY + durabilityHeight, scaleAlpha(durabilityColor, alpha));
                }
                slotX += 17;
            }
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            Gui.drawRect(0, 0, 0, 0, 0); // Restore color state
        }
    }

    @Override
    public void renderHudPreview(ScaledResolution resolution) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            drawTargetHud(minecraft.thePlayer, 1.0F, resolution);
        }
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int scaleAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    public String getHudElementName() {
        return "TargetHUD";
    }

    @Override
    public boolean isHudElementVisible() {
        return isEnabled();
    }

    @Override
    public int getHudX() {
        return hudX.getValue();
    }

    @Override
    public int getHudY() {
        return hudY.getValue();
    }

    @Override
    public int getHudWidth(ScaledResolution resolution) {
        return Math.round(WIDTH * scale.getValue());
    }

    @Override
    public int getHudHeight(ScaledResolution resolution) {
        return Math.round(HEIGHT * scale.getValue());
    }

    @Override
    public boolean isHudRightAligned(ScaledResolution resolution) {
        return false;
    }

    @Override
    public void setHudPosition(int x, int y) {
        hudX.setManualValue(x);
        hudY.setManualValue(y);
    }

    @Override
    public float getHudScale() {
        return scale.getValue();
    }

    @Override
    public void setHudScale(float s) {
        scale.setManualValue(s);
    }
}
