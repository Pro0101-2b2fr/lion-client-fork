package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.FloatSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
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

/**
 * TargetHUD — enhanced target information card.
 *
 * Improvements over original:
 * - Active potion effects display (name + amplifier + duration)
 * - Hurt time indicator (red flash on avatar when target is hurt)
 * - Ping display (color-coded: green < 50, yellow < 100, red >= 100)
 * - Dynamic card height based on visible elements
 * - Same glassmorphism style as the rest of the client
 */

public final class TargetHudModule extends Module implements HudElement {
    private static final int WIDTH = 200;

    private final BooleanSetting showArmor = new BooleanSetting("Show Armor", true);
    private final BooleanSetting showPotions = new BooleanSetting("Show Potions", true);
    private final BooleanSetting showHurtTime = new BooleanSetting("Show Hurt Time", true);
    private final BooleanSetting showDistance = new BooleanSetting("Show Distance", true);
    private final BooleanSetting showPing = new BooleanSetting("Show Ping", false);
    private final FloatSetting scale = new FloatSetting("Scale", 0.5F, 2.0F, 0.1F, 1.0F);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, 120);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, 120);

    private EntityLivingBase lastTarget;
    private long lastTargetTime;
    private String cachedHpText = "";
    private String cachedDistText = "";
    private int cachedHurtTime = 0;
    private String cachedPingText = "";
    private List<String> cachedPotionNames = new ArrayList<>();

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
        addSetting(showPotions);
        addSetting(showHurtTime);
        addSetting(showDistance);
        addSetting(showPing);
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
            cachedDistText = String.format("%.1fm", minecraft.thePlayer.getDistanceToEntity(target));
            cachedHurtTime = target.hurtTime;

            // Cache potion effect names
            cachedPotionNames.clear();
            if (target instanceof EntityPlayer) {
                for (Object effect : ((EntityPlayer) target).getActivePotionEffects()) {
                    if (effect instanceof net.minecraft.potion.PotionEffect) {
                        net.minecraft.potion.PotionEffect pe = (net.minecraft.potion.PotionEffect) effect;
                        int potionID = pe.getPotionID();
                        // Validate potion ID to prevent array out of bounds
                        if (potionID < 0 || potionID >= net.minecraft.potion.Potion.potionTypes.length ||
                            net.minecraft.potion.Potion.potionTypes[potionID] == null) {
                            continue;
                        }
                        String name = net.minecraft.potion.Potion.potionTypes[potionID].getName();
                        // Shorten common names
                        name = name.replace("potion.", "")
                                   .replace("speed", "Spd")
                                   .replace("heal", "Hl")
                                   .replace("regeneration", "Reg")
                                   .replace("fire_resistance", "Fire")
                                   .replace("strength", "Str")
                                   .replace("weakness", "Wk")
                                   .replace("slowness", "Slow")
                                   .replace("poison", "Pois")
                                   .replace("wither", "With")
                                   .replace("resistance", "Res")
                                   .replace("jump_boost", "Jump")
                                   .replace("haste", "Hat")
                                   .replace("fatigue", "Fat")
                                   .replace("invisibility", "Invis");
                        if (pe.getAmplifier() > 0) name += " " + pe.getAmplifier();
                        int duration = pe.getDuration() / 20;
                        name += " " + duration + "s";
                        cachedPotionNames.add(name);
                    }
                }
                // Cache ping
                cachedPingText = "";
                if (minecraft.getNetHandler() != null) {
                    try {
                        net.minecraft.client.network.NetworkPlayerInfo info = minecraft.getNetHandler().getPlayerInfo(((EntityPlayer) target).getUniqueID());
                        if (info != null) {
                            int ping = info.getResponseTime();
                            cachedPingText = ping + "ms";
                        }
                    } catch (Exception ignored) {}
                }
            }
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
        // Scale around the HUD position, not (0,0)
        GL11.glTranslatef(hudX.getValue(), hudY.getValue(), 0);
        GL11.glScalef(s, s, 1.0F);
        GL11.glTranslatef(-hudX.getValue(), -hudY.getValue(), 0);
        drawTargetHud(lastTarget, alpha, res, s);
        GL11.glPopMatrix();
    }

    private void drawTargetHud(EntityLivingBase target, float alpha, ScaledResolution res, float s) {
        Minecraft mc = Minecraft.getMinecraft();
        int anchorX = hudX.getValue();
        int anchorY = hudY.getValue();
        int accent = ClickGuiModule.getModernAccentColor();

        // Calculate dynamic height based on what's shown
        int dynamicHeight = 42;
        if (showPotions.isEnabled() && !cachedPotionNames.isEmpty()) dynamicHeight += 12;
        if (showHurtTime.isEnabled() && cachedHurtTime > 0) dynamicHeight += 6;
        // Account for ping text if enabled and has value
        if (showPing.isEnabled() && !cachedPingText.isEmpty()) dynamicHeight += 9;

        // 1. Card Background
        int bgAlpha = (int) (0x90 * alpha);
        int bgColor = (bgAlpha << 24) | 0x101018;
        int borderAlpha = (int) (0xCC * alpha);
        int borderColor = (borderAlpha << 24) | (accent & 0x00FFFFFF);

        net.minecraft.client.gui.Gui.drawRect(anchorX, anchorY, anchorX + WIDTH, anchorY + dynamicHeight, bgColor);
        net.minecraft.client.gui.Gui.drawRect(anchorX, anchorY, anchorX + WIDTH, anchorY + 1, borderColor);
        net.minecraft.client.gui.Gui.drawRect(anchorX, anchorY + dynamicHeight - 1, anchorX + WIDTH, anchorY + dynamicHeight, borderColor);
        net.minecraft.client.gui.Gui.drawRect(anchorX, anchorY, anchorX + 1, anchorY + dynamicHeight, borderColor);
        net.minecraft.client.gui.Gui.drawRect(anchorX + WIDTH - 1, anchorY, anchorX + WIDTH, anchorY + dynamicHeight, borderColor);

        // 2. Target Face Avatar
        int avatarX = anchorX + 8;
        int avatarY = anchorY + 9;
        if (target instanceof EntityPlayer) {
            net.minecraft.client.entity.AbstractClientPlayer player = (net.minecraft.client.entity.AbstractClientPlayer) target;
            mc.getTextureManager().bindTexture(player.getLocationSkin());
            GlStateManager.color(1, 1, 1, alpha);
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(avatarX, avatarY, 8, 8, 8, 8, 24, 24, 64, 64);
            net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect(avatarX, avatarY, 40, 8, 8, 8, 24, 24, 64, 64);
        } else {
            net.minecraft.client.gui.Gui.drawRect(avatarX, avatarY, avatarX + 24, avatarY + 24, scaleAlpha(0xFF4A9EFF, alpha));
        }

        // Hurt time indicator (red flash overlay on avatar when hurt)
        if (showHurtTime.isEnabled() && cachedHurtTime > 0 && target instanceof EntityPlayer) {
            int hurtAlpha = (int) (0x40 * alpha * (cachedHurtTime / 10.0F));
            int hurtColor = (Math.min(255, hurtAlpha) << 24) | 0xFF0000;
            net.minecraft.client.gui.Gui.drawRect(avatarX, avatarY, avatarX + 24, avatarY + 24, hurtColor);
        }

        // 3. Target Name
        net.minecraft.client.gui.FontRenderer font = mc.fontRendererObj;
        String nameText = target.getName();
        if (nameText.length() > 14) nameText = nameText.substring(0, 12) + "...";
        font.drawStringWithShadow(nameText, anchorX + 38, anchorY + 8, scaleAlpha(0xFFFFFFFF, alpha));

        // 4. Health Bar
        int barX = anchorX + 38;
        int barY = anchorY + 20;
        int barWidth = 80;
        int barHeight = 8;
        net.minecraft.client.gui.Gui.drawRect(barX, barY, barX + barWidth, barY + barHeight, scaleAlpha(0x50101010, alpha));
        float hpPercent = target.getMaxHealth() > 0.0F
            ? Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()))
            : 0.0F;
        int fillWidth = (int) (barWidth * hpPercent);
        int hpColor = ClickGuiModule.blendColor(0xFF55FF55, 0xFFFF5555, 1.0F - hpPercent);
        net.minecraft.client.gui.Gui.drawRect(barX, barY, barX + fillWidth, barY + barHeight, scaleAlpha(hpColor, alpha));

        // Health text inside bar
        GlStateManager.pushMatrix();
        GlStateManager.translate(barX + (barWidth - font.getStringWidth(cachedHpText) * 0.7F) / 2.0F, barY + 1.5F, 0);
        GlStateManager.scale(0.7F, 0.7F, 1);
        font.drawString(cachedHpText, 0, 0, scaleAlpha(0xFFFFFFFF, alpha));
        GlStateManager.popMatrix();

        // 5. Distance + Ping line
        int infoY = anchorY + 31;
        if (showDistance.isEnabled()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(anchorX + 38, infoY, 0);
            GlStateManager.scale(0.75F, 0.75F, 1);
            font.drawString(cachedDistText, 0, 0, scaleAlpha(0xFFACB7C2, alpha));
            GlStateManager.popMatrix();
            infoY += 9;
        }
        if (showPing.isEnabled() && !cachedPingText.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(anchorX + 38, infoY, 0);
            GlStateManager.scale(0.75F, 0.75F, 1);
            int pingColor;
            int ping;
            try { ping = Integer.parseInt(cachedPingText.replace("ms", "")); } catch (Exception e) { ping = 0; }
            if (ping < 50) pingColor = 0xFF44FF44;
            else if (ping < 100) pingColor = 0xFFAAAA44;
            else pingColor = 0xFFFF4444;
            font.drawString("Ping: " + cachedPingText, 0, 0, scaleAlpha(pingColor, alpha));
            GlStateManager.popMatrix();
            infoY += 9;
        }

        // 6. Potion Effects
        if (showPotions.isEnabled() && !cachedPotionNames.isEmpty()) {
            int potionX = anchorX + 38;
            int potionY = infoY;
            GlStateManager.pushMatrix();
            GlStateManager.translate(potionX, potionY, 0);
            GlStateManager.scale(0.7F, 0.7F, 1);
            int potionIdx = 0;
            for (String potionName : cachedPotionNames) {
                if (potionIdx >= 4) break; // Max 4 potion effects shown
                int potionColor = 0xFFCCAACC;
                font.drawStringWithShadow(potionName, 0, potionIdx * 8, scaleAlpha(potionColor, alpha));
                potionIdx++;
            }
            GlStateManager.popMatrix();
        }

        // 7. Armor Row on Right
        if (showArmor.isEnabled() && target instanceof EntityPlayer) {
            int armorX = anchorX + 124;
            int armorY = anchorY + 11;
            net.minecraft.client.renderer.entity.RenderItem renderItem = mc.getRenderItem();
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.color(1, 1, 1, alpha);
            int slotX = armorX;
            for (int i = 3; i >= 0; i--) {
                net.minecraft.item.ItemStack stack = target.getEquipmentInSlot(i + 1);
                if (stack == null) { slotX += 17; continue; }
                renderItem.renderItemAndEffectIntoGUI(stack, slotX, armorY);
                renderItem.renderItemOverlayIntoGUI(font, stack, slotX, armorY, null);
                if (stack.getMaxDamage() > 0) {
                    double dmgRatio = (double) (stack.getMaxDamage() - stack.getItemDamage()) / stack.getMaxDamage();
                    dmgRatio = Math.max(0, Math.min(1, dmgRatio));
                    int durColor = ClickGuiModule.blendColor(0xFF55FF55, 0xFFFF5555, 1.0F - (float) dmgRatio);
                    net.minecraft.client.gui.Gui.drawRect(slotX, armorY + 17, slotX + 16, armorY + 19, scaleAlpha(0x60000000, alpha));
                    net.minecraft.client.gui.Gui.drawRect(slotX, armorY + 17, slotX + (int) (16 * dmgRatio), armorY + 19, scaleAlpha(durColor, alpha));
                }
                slotX += 17;
            }
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
            net.minecraft.client.gui.Gui.drawRect(0, 0, 0, 0, 0);
        }
    }

    @Override
    public void renderHudPreview(ScaledResolution resolution) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            drawTargetHud(minecraft.thePlayer, 1.0F, resolution, scale.getValue());
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
        int dynamicHeight = 50;
        if (showPotions.isEnabled() && !cachedPotionNames.isEmpty()) dynamicHeight += 12;
        if (showHurtTime.isEnabled() && cachedHurtTime > 0) dynamicHeight += 6;
        if (showPing.isEnabled() && !cachedPingText.isEmpty()) dynamicHeight += 9;
        return Math.round(dynamicHeight * scale.getValue());
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
