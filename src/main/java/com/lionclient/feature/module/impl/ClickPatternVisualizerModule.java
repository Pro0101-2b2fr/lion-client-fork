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
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Click Pattern Visualizer — real-time CPS graph and statistics.
 *
 * Shows:
 * - Live CPS line graph (last 20 clicks)
 * - Current CPS value
 * - Variance indicator (how "human" the pattern looks)
 * - Color-coded: green = human-like, red = too consistent
 *
 * Can be used standalone or integrated with KillAura/AutoClicker.
 */
public final class ClickPatternVisualizerModule extends Module implements HudElement {
    private final BooleanSetting showGraph = new BooleanSetting("Show Graph", true);
    private final BooleanSetting showStats = new BooleanSetting("Show Stats", true);
    private final BooleanSetting showVariance = new BooleanSetting("Show Variance", true);
    private final ColorSetting graphColor = new ColorSetting("Graph Color", 0xFF4A9EFF);
    private final ColorSetting bgColor = new ColorSetting("BG Color", 0x80101018);
    private final DecimalSetting graphHeight = new DecimalSetting("Graph Height", 10, 60, 5, 35);
    private final DecimalSetting graphWidth = new DecimalSetting("Graph Width", 40, 200, 10, 120);
    private final FloatSetting scale = new FloatSetting("Scale", 0.5F, 2.0F, 0.1F, 1.0F);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, 4);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, 4);

    // Click timing history (circular buffer)
    private static final int MAX_HISTORY = 60;
    private final long[] clickTimes = new long[MAX_HISTORY];
    private int clickCount = 0;
    private int clickIndex = 0;
    private int currentCps = 0;
    private float currentVariance = 0;
    private long lastClickTime = 0;

    public ClickPatternVisualizerModule() {
        super("ClickPattern", "Visualizes click pattern in real-time.", Category.HUD, Keyboard.KEY_NONE);
        java.util.function.BooleanSupplier hidden = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        addSetting(showGraph);
        addSetting(showStats);
        addSetting(showVariance);
        addSetting(graphColor);
        addSetting(bgColor);
        addSetting(graphHeight);
        addSetting(graphWidth);
        addSetting(scale);
        addSetting(hudX);
        addSetting(hudY);
    }

    /**
     * Record a click event. Called from KillAura, AutoClicker, etc.
     * Static so any module can report clicks.
     */
    public static void recordClick() {
        // This will be called from KillAuraModule / AutoClickerModule
        // We find the module instance and call the non-static version
        ClickPatternVisualizerModule inst = getInstance();
        if (inst != null) {
            inst.onClick();
        }
    }

    private static ClickPatternVisualizerModule getInstance() {
        com.lionclient.LionClient client = com.lionclient.LionClient.getInstance();
        if (client != null) {
            return client.getModuleManager().getModule(ClickPatternVisualizerModule.class);
        }
        return null;
    }

    // Reusable buffer for delay calculations (avoids allocation every frame)
    private final double[] delayBuffer = new double[MAX_HISTORY];

    private void onClick() {
        long now = System.currentTimeMillis();
        clickTimes[clickIndex] = now;
        clickIndex = (clickIndex + 1) % MAX_HISTORY;
        if (clickCount < MAX_HISTORY) clickCount++;
        lastClickTime = now;
    }

    private void calculateStats() {
        if (clickCount < 2) {
            currentCps = 0;
            currentVariance = 0;
            return;
        }

        // Calculate CPS from last second
        long now = System.currentTimeMillis();
        int clicksInLastSecond = 0;
        int oldestUsed = -1;

        for (int i = 0; i < clickCount; i++) {
            int idx = (clickIndex - 1 - i + MAX_HISTORY) % MAX_HISTORY;
            if (now - clickTimes[idx] <= 1000) {
                clicksInLastSecond++;
                oldestUsed = idx;
            } else {
                break;
            }
        }
        currentCps = clicksInLastSecond;

        // Calculate variance of inter-click delays
        if (clicksInLastSecond >= 3) {
            int count = clicksInLastSecond - 1;
            double sum = 0;
            for (int i = 0; i < count; i++) {
                int idx1 = (clickIndex - 1 - i + MAX_HISTORY) % MAX_HISTORY;
                int idx2 = (clickIndex - 2 - i + MAX_HISTORY) % MAX_HISTORY;
                delayBuffer[i] = Math.max(0, clickTimes[idx1] - clickTimes[idx2]);
                sum += delayBuffer[i];
            }
            double mean = sum / count;
            double varianceSum = 0;
            for (int i = 0; i < count; i++) {
                varianceSum += (delayBuffer[i] - mean) * (delayBuffer[i] - mean);
            }
            currentVariance = mean > 0.0D ? (float) (Math.sqrt(varianceSum / count) / mean) : 0.0F;
            // CV (coefficient of variation): 0 = perfectly consistent, >0.3 = human-like
        }
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.gameSettings == null || mc.gameSettings.showDebugInfo) return;

        calculateStats();

        ScaledResolution res = event.resolution;
        float s = scale.getValue();
        int graphW = (int) (graphWidth.getValue() * s);
        int graphH = (int) (graphHeight.getValue() * s);
        int x = hudX.getValue();
        int y = hudY.getValue();

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(s, s, 1.0F);
            // Undo the scale for the translate so we draw at the right position
            GL11.glTranslatef(-x / s, -y / s, 0);

            if (showGraph.isEnabled()) {
                drawGraph(x, y, (int) graphWidth.getValue(), (int) graphHeight.getValue());
            }
            if (showStats.isEnabled()) {
                drawStats(x, y, (int) graphWidth.getValue(), (int) graphHeight.getValue());
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void drawGraph(int x, int y, int w, int h) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // Background
        int bg = bgColor.getRgb();
        float bga = ((bg >> 24) & 0xFF) / 255.0F;
        float bgr = ((bg >> 16) & 0xFF) / 255.0F;
        float bgg = ((bg >> 8) & 0xFF) / 255.0F;
        float bgb = (bg & 0xFF) / 255.0F;
        GlStateManager.color(bgr, bgg, bgb, bga);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x, y);
        GL11.glVertex2i(x, y + h);
        GL11.glVertex2i(x + w, y + h);
        GL11.glVertex2i(x + w, y);
        GL11.glEnd();

        if (clickCount < 2) {
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            return;
        }

        // Draw line graph of inter-click delays
        int color = graphColor.getRgb();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        GlStateManager.color(r, g, b, 0.8F);
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINE_STRIP);

        int points = Math.min(clickCount - 1, w);
        double maxDelay = 0;
        long now = System.currentTimeMillis();

        // Find max delay for scaling
        for (int i = 0; i < points; i++) {
            int idx1 = (clickIndex - 1 - i + MAX_HISTORY) % MAX_HISTORY;
            int idx2 = (clickIndex - 2 - i + MAX_HISTORY) % MAX_HISTORY;
            double delay = clickTimes[idx1] - clickTimes[idx2];
            if (delay > maxDelay && delay < 500) maxDelay = delay; // Cap at 500ms
        }
        maxDelay = Math.max(maxDelay, 50); // Minimum scale

        for (int i = 0; i < points; i++) {
            int idx1 = (clickIndex - 1 - i + MAX_HISTORY) % MAX_HISTORY;
            int idx2 = (clickIndex - 2 - i + MAX_HISTORY) % MAX_HISTORY;
            double delay = Math.min(clickTimes[idx1] - clickTimes[idx2], 500);
            double py = y + h - (delay / maxDelay) * h;
            py = Math.max(y, Math.min(y + h, py));
            GL11.glVertex2d(x + w - i * ((double) w / points), py);
        }
        GL11.glEnd();

        // Border
        GlStateManager.color(r, g, b, 0.4F);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2i(x, y);
        GL11.glVertex2i(x, y + h);
        GL11.glVertex2i(x + w, y + h);
        GL11.glVertex2i(x + w, y);
        GL11.glEnd();

        GL11.glLineWidth(1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private void drawStats(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.fontRendererObj == null) return;

        int textX = x + w + 4;
        int textY = y;
        float s = 0.8F;

        GlStateManager.pushMatrix();
        GlStateManager.scale(s, s, 1.0F);

        // CPS
        String cpsStr = currentCps + " CPS";
        int cpsColor;
        if (currentCps >= 8 && currentCps <= 16) {
            cpsColor = 0xFF44FF44;
        } else if (currentCps > 16) {
            cpsColor = 0xFFFFAA00;
        } else {
            cpsColor = 0xFFAAAAAA;
        }
        mc.fontRendererObj.drawStringWithShadow(cpsStr, textX / s, textY / s, cpsColor);

        // Variance
        if (showVariance.isEnabled()) {
            String varStr = String.format("σ: %.2f", currentVariance);
            int varColor;
            if (currentVariance > 0.15F && currentVariance < 0.45F) {
                varColor = 0xFF44FF44; // Human-like variance
            } else if (currentVariance >= 0.45F) {
                varColor = 0xFFFFAA00; // Too erratic
            } else {
                varColor = 0xFFFF4444; // Too consistent = flagged
            }
            mc.fontRendererObj.drawStringWithShadow(varStr, textX / s, (textY + 10) / s, varColor);
        }

        GlStateManager.popMatrix();
    }

    private int getElementWidth() {
        return (int) graphWidth.getValue();
    }

    private int getElementHeight() {
        int h = (int) graphHeight.getValue();
        if (showStats.isEnabled()) h += 20;
        if (showVariance.isEnabled()) h += 12;
        return h;
    }

    // --- HudElement implementation ---

    @Override
    public String getHudElementName() {
        return "Click Pattern";
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
        drawStats(4, 4, getElementWidth(), getElementHeight());
    }
}
