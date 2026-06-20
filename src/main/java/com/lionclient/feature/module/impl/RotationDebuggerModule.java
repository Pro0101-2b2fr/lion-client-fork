package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.FloatSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Rotation Debugger — visual overlay showing rotation data.
 *
 * Features:
 * - FOV circle: shows the KillAura aim zone as a circle on screen
 * - Aim point indicator: crosshair showing where KillAura is aiming
 * - Yaw/Pitch delta display: real-time rotation delta visualization
 * - Rotation speed indicator: shows how fast rotations are changing
 *
 * Useful for:
 * - Tuning rotation settings
* - Verifying that humanization looks natural
 * - Debugging aim issues
 */
public final class RotationDebuggerModule extends Module implements HudElement {
    private final BooleanSetting showFovCircle = new BooleanSetting("FOV Circle", true);
    private final BooleanSetting showAimPoint = new BooleanSetting("Aim Point", true);
    private final BooleanSetting showDeltaBars = new BooleanSetting("Delta Bars", true);
    private final BooleanSetting showInfoText = new BooleanSetting("Info Text", true);
    private final ColorSetting fovColor = new ColorSetting("FOV Color", 0xFF4A9EFF);
    private final ColorSetting aimColor = new ColorSetting("Aim Color", 0xFFFF4444);
    private final DecimalSetting fovRadius = new DecimalSetting("FOV Radius", 10, 120, 5, 35);
    private final FloatSetting scale = new FloatSetting("Scale", 0.5F, 2.0F, 0.1F, 1.0F);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, 4);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, 4);

    // Previous rotation values for delta calculation
    private float prevYaw = Float.NaN;
    private float prevPitch = Float.NaN;
    private float lastDeltaYaw = 0;
    private float lastDeltaPitch = 0;

    public RotationDebuggerModule() {
        super("RotationDebug", "Visual rotation debugging overlay.", Category.HUD, Keyboard.KEY_NONE);
        java.util.function.BooleanSupplier hidden = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        addSetting(showFovCircle);
        addSetting(showAimPoint);
        addSetting(showDeltaBars);
        addSetting(showInfoText);
        addSetting(fovColor);
        addSetting(aimColor);
        addSetting(fovRadius);
        addSetting(scale);
        addSetting(hudX);
        addSetting(hudY);
    }

    @Override
    protected void onEnable() {
        // Reset delta tracking so the first frame after re-enabling doesn't
        // produce a spike from a stale previous rotation.
        prevYaw = Float.NaN;
        prevPitch = Float.NaN;
        lastDeltaYaw = 0;
        lastDeltaPitch = 0;
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        ScaledResolution res = event.resolution;
        int centerX = res.getScaledWidth() / 2;
        int centerY = res.getScaledHeight() / 2;

        // Update deltas
        float currentYaw = mc.thePlayer.rotationYaw;
        float currentPitch = mc.thePlayer.rotationPitch;
        if (!Float.isNaN(prevYaw)) {
            lastDeltaYaw = MathHelper.wrapAngleTo180_float(currentYaw - prevYaw);
            lastDeltaPitch = currentPitch - prevPitch;
        }
        prevYaw = currentYaw;
        prevPitch = currentPitch;

        GL11.glPushMatrix();
        try {
            if (showFovCircle.isEnabled()) {
                drawFovCircle(centerX, centerY, (float) fovRadius.getValue(), fovColor.getRgb());
            }
            if (showAimPoint.isEnabled()) {
                drawAimPoint(mc, centerX, centerY, aimColor.getRgb());
            }
            if (showDeltaBars.isEnabled()) {
                drawDeltaBars(res, centerX);
            }
            if (showInfoText.isEnabled()) {
                drawInfoText(res, centerX, centerY);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * Draws a circle representing the KillAura FOV/aim zone.
     */
    private void drawFovCircle(int cx, int cy, float radius, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = 0.35F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();

        // Draw circle outline
        GlStateManager.color(r, g, b, a);
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double angle = (2.0 * Math.PI * i) / segments;
            double px = cx + Math.cos(angle) * radius;
            double py = cy + Math.sin(angle) * radius;
            GL11.glVertex2d(px, py);
        }
        GL11.glEnd();

        // Draw crosshair lines at center (subtle)
        GlStateManager.color(r, g, b, 0.15F);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(cx - 6, cy);
        GL11.glVertex2d(cx + 6, cy);
        GL11.glVertex2d(cx, cy - 6);
        GL11.glVertex2d(cx, cy + 6);
        GL11.glEnd();

        GL11.glLineWidth(1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * Draws the aim point — where KillAura is currently aiming.
     * Shows as a small cross/dot offset from center based on rotation delta.
     */
    private void drawAimPoint(Minecraft mc, int cx, int cy, int color) {
        float aimX = cx + lastDeltaYaw * 3.0F;
        float aimY = cy + lastDeltaPitch * 3.0F;

        // Clamp to reasonable screen area
        aimX = Math.max(cx - 80, Math.min(cx + 80, aimX));
        aimY = Math.max(cy - 80, Math.min(cy + 80, aimY));

        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(r, g, b, 0.9F);

        // Draw small cross at aim point
        GL11.glLineWidth(2.0F);
        float size = 4.0F;
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(aimX - size, aimY);
        GL11.glVertex2f(aimX + size, aimY);
        GL11.glVertex2f(aimX, aimY - size);
        GL11.glVertex2f(aimX, aimY + size);
        GL11.glEnd();

        GL11.glLineWidth(1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /**
     * Draws horizontal/vertical bars showing yaw/pitch deltas.
     */
    private void drawDeltaBars(ScaledResolution res, int centerX) {
        int barWidth = 60;
        int barHeight = 4;
        int barY = res.getScaledHeight() - 30;
        int yawBarX = centerX - barWidth - 5;
        int pitchBarX = centerX + 5;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();

        // Yaw delta bar (horizontal)
        GlStateManager.color(0.2F, 0.2F, 0.2F, 0.5F);
        drawBar(yawBarX, barY, barWidth, barHeight);
        float yawFill = lastDeltaYaw / 30.0F; // Normalize to ±30 degrees
        yawFill = Math.max(-1, Math.min(1, yawFill));
        float yawAbs = Math.abs(yawFill);
        int yawColor = 0xFF000000 | ClickGuiModule.blendColor(0xFF44FF44, 0xFFFF4444, 1.0F - yawAbs);
        float yr = ((yawColor >> 16) & 0xFF) / 255.0F;
        float yg = ((yawColor >> 8) & 0xFF) / 255.0F;
        float yb = (yawColor & 0xFF) / 255.0F;
        int yawFillWidth = (int) (barWidth * Math.abs(yawFill));
        GlStateManager.color(yr, yg, yb, 0.8F);
        if (yawFill >= 0) {
            drawBar(yawBarX + barWidth / 2, barY, yawFillWidth, barHeight);
        } else {
            drawBar(yawBarX + barWidth / 2 - yawFillWidth, barY, yawFillWidth, barHeight);
        }

        // Pitch delta bar (horizontal)
        GlStateManager.color(0.2F, 0.2F, 0.2F, 0.5F);
        drawBar(pitchBarX, barY, barWidth, barHeight);
        float pitchFill = lastDeltaPitch / 20.0F;
        pitchFill = Math.max(-1, Math.min(1, pitchFill));
        float pitchAbs = Math.abs(pitchFill);
        int pitchColor = 0xFF000000 | ClickGuiModule.blendColor(0xFF44FF44, 0xFFFF4444, 1.0F - pitchAbs);
        float pr = ((pitchColor >> 16) & 0xFF) / 255.0F;
        float pg = ((pitchColor >> 8) & 0xFF) / 255.0F;
        float pb = (pitchColor & 0xFF) / 255.0F;
        int pitchFillWidth = (int) (barWidth * Math.abs(pitchFill));
        GlStateManager.color(pr, pg, pb, 0.8F);
        if (pitchFill >= 0) {
            drawBar(pitchBarX + barWidth / 2, barY, pitchFillWidth, barHeight);
        } else {
            drawBar(pitchBarX + barWidth / 2 - pitchFillWidth, barY, pitchFillWidth, barHeight);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private void drawBar(int x, int y, int w, int h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    /**
     * Draws info text near the FOV circle.
     */
    private void drawInfoText(ScaledResolution res, int centerX, int centerY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRendererObj == null) return;

        int textY = centerY + (int) fovRadius.getValue() + 10;
        int textX = centerX;

        String yawStr = String.format("%.1f", lastDeltaYaw);
        String pitchStr = String.format("%.1f", lastDeltaPitch);

        // Draw shadow text for readability
        int color = 0xFFFFFFFF;
        mc.fontRendererObj.drawStringWithShadow("§bYaw: §f" + yawStr, textX + 2, textY, color);
        mc.fontRendererObj.drawStringWithShadow("§bPitch: §f" + pitchStr, textX + 2, textY + 10, color);

        // Show KillAura target info if available
        KillAuraModule ka = getKillAuraModule();
        if (ka != null && ka.isEnabled()) {
            EntityLivingBase target = ka.getTarget();
            if (target != null) {
                String targetStr = "§bTarget: §f" + target.getName();
                mc.fontRendererObj.drawStringWithShadow(targetStr, textX + 2, textY + 20, color);
            }
        }
    }

    private KillAuraModule getKillAuraModule() {
        com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
        if (client != null) {
            return client.getModuleManager().getModule(KillAuraModule.class);
        }
        return null;
    }

    private int getElementWidth() {
        return (int) (fovRadius.getValue() * 2 + 20);
    }

    private int getElementHeight() {
        return (int) (fovRadius.getValue() * 2 + 50);
    }

    // --- HudElement implementation ---

    @Override
    public String getHudElementName() {
        return "Rotation Debug";
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
        return Math.round(getElementWidth() * scale.getValue());
    }

    @Override
    public int getHudHeight(ScaledResolution resolution) {
        return Math.round(getElementHeight() * scale.getValue());
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

    @Override
    public void renderHudPreview(ScaledResolution resolution) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRendererObj == null) return;
        int cx = getElementWidth() / 2;
        int cy = getElementHeight() / 2;
        drawFovCircle(cx, cy, (float) fovRadius.getValue(), fovColor.getRgb());
    }
}
