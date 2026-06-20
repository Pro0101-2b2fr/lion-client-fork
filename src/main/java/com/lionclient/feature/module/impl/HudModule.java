package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.impl.HudModule;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.FloatSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.HudElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class HudModule extends Module implements HudElement {
    private static final int DEFAULT_X = 4;
    private static final int DEFAULT_Y = 4;
    private static HudModule instance;

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.MODERN);
    private final ColorSetting color = new ColorSetting("Color", 0xFFFFFFFF);
    private final BooleanSetting showFps = new BooleanSetting("FPS", true);
    private final BooleanSetting showCoords = new BooleanSetting("Coordinates", false);
    private final BooleanSetting showDirection = new BooleanSetting("Direction", false);
    private final BooleanSetting showBps = new BooleanSetting("BPS", false);
    private final FloatSetting scale = new FloatSetting("Scale", 0.5F, 2.0F, 0.1F, 1.0F);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, DEFAULT_X);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, DEFAULT_Y);

    // --- Animation settings ---
    private final BooleanSetting animated = new BooleanSetting("Animations", true);
    private final EnumSetting<AnimationStyle> animStyle = new EnumSetting<>("Anim Style", AnimationStyle.values(), AnimationStyle.SLIDE);
    private final FloatSetting animSpeed = new FloatSetting("Anim Speed", 0.05F, 1.0F, 0.05F, 0.25F);
    private final BooleanSetting showBar = new BooleanSetting("Side Bar", true);
    private final BooleanSetting showShadow = new BooleanSetting("Text Shadow", true);
    private final BooleanSetting showBackground = new BooleanSetting("Background", false);

    private double lastTickX;
    private double lastTickZ;
    private long lastTickTime;
    private double smoothedBps;
    private long lastBpsDisplayUpdate;
    private String cachedBpsText = "0.00 BPS";
    private final List<String> infoLines = new ArrayList<>();
    private final ActionSetting editor = new ActionSetting("Move HUD", new Runnable() {
        @Override
        public void run() {
            LionClient client = LionClient.getInstance();
            if (client != null) client.openHudEditor();
        }
    }, new ActionSetting.ValueProvider() {
        @Override
        public String get() { return "OPEN"; }
    });

    // --- Animation state ---
    // Per-module animation progress (0.0 = hidden, 1.0 = fully visible)
    // Key = module's stable class name, Value = progress
    private final Map<String, Float> moduleAnimProgress = new HashMap<>();
    // Maps stable key to current display name (name + hudInfo). Kept for
    // animating-out modules too, so the slide-out renders the real name.
    private final Map<String, String> moduleDisplayNames = new HashMap<>();
    // Keys of modules enabled THIS frame (drives animate-in vs animate-out).
    private final java.util.Set<String> enabledKeys = new java.util.HashSet<>();
    // Global animation time
    private long lastFrameTime = System.currentTimeMillis();

    /**
     * Gets a stable unique key for a module (its class name).
     */
    private static String getModuleKey(Module module) {
        return module.getClass().getName();
    }

    /**
     * Gets the display name for a module (Name + HudInfo).
     */
    private static String getModuleDisplayName(Module module) {
        String hudInfo = module.getHudInfo();
        return (hudInfo == null || hudInfo.isEmpty()) ? module.getName() : module.getName() + " " + hudInfo;
    }

    public HudModule() {
        super("HUD", "Displays enabled modules on screen.", Category.HUD, Keyboard.KEY_NONE);
        instance = this;
        java.util.function.BooleanSupplier hidden = () -> false;
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        java.util.function.BooleanSupplier classicVisible = () -> mode.getValue() == Mode.CLASSIC;
        java.util.function.BooleanSupplier modernVisible = () -> mode.getValue() == Mode.MODERN;
        color.setVisibility(classicVisible);
        animated.setVisibility(modernVisible);
        animStyle.setVisibility(modernVisible);
        animSpeed.setVisibility(modernVisible);
        showBar.setVisibility(modernVisible);
        showShadow.setVisibility(modernVisible);
        showBackground.setVisibility(modernVisible);

        addSetting(mode);
        addSetting(color);
        addSetting(showFps);
        addSetting(showCoords);
        addSetting(showDirection);
        addSetting(showBps);
        addSetting(animated);
        addSetting(animStyle);
        addSetting(animSpeed);
        addSetting(showBar);
        addSetting(showShadow);
        addSetting(showBackground);
        addSetting(hudX);
        addSetting(hudY);
        addSetting(editor);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.showDebugInfo) return;

        List<String> moduleNames = getEnabledModuleNames();
        updateAnimations(moduleNames);

        renderModuleList(event.resolution, moduleNames, getColor());
        renderInfoLines(event.resolution, mc);
    }

    /**
     * Updates per-module animation progress.
     * When a module is enabled, its progress animates from 0→1 (slide in).
     * When disabled, progress animates from 1→0 (slide out).
     */
    private void updateAnimations(List<String> currentDisplayNames) {
        long now = System.currentTimeMillis();
        float delta = (now - lastFrameTime) / 1000.0F;
        lastFrameTime = now;
        // Clamp delta so a long pause (alt-tab, GUI open) doesn't snap everything.
        if (delta < 0.0F) delta = 0.0F;
        if (delta > 0.1F) delta = 0.1F;
        float speed = animSpeed.getValue();
        // Frame-rate independent. animSpeed maps to "fraction per second":
        // at speed 0.25 a full in-animation takes ~0.8s, at 1.0 ~0.2s.
        float stepIn = delta * (1.0F + speed * 4.0F);
        float stepOut = stepIn * 1.4F;

        // Animate IN every enabled module.
        for (String key : enabledKeys) {
            float progress = moduleAnimProgress.getOrDefault(key, 0.0F);
            progress = Math.min(1.0F, progress + stepIn);
            moduleAnimProgress.put(key, progress);
        }

        // Animate OUT modules that are no longer enabled, then drop them.
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Float> entry : moduleAnimProgress.entrySet()) {
            if (!enabledKeys.contains(entry.getKey())) {
                float progress = entry.getValue() - stepOut;
                if (progress <= 0.0F) {
                    toRemove.add(entry.getKey());
                } else {
                    entry.setValue(progress);
                }
            }
        }
        for (String key : toRemove) {
            moduleAnimProgress.remove(key);
            moduleDisplayNames.remove(key);
        }
    }

    private void renderModuleList(ScaledResolution resolution, List<String> moduleNames, int color) {
        if (moduleNames.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        float s = scale.getValue();
        int anchorX = Math.max(0, Math.min(hudX.getValue(), resolution.getScaledWidth()));
        int anchorY = Math.max(0, Math.min(hudY.getValue(),
            Math.max(0, resolution.getScaledHeight() - mc.fontRendererObj.FONT_HEIGHT)));
        boolean rightAligned = anchorX >= resolution.getScaledWidth() / 2;
        int lineY = anchorY;
        boolean modern = mode.getValue() == Mode.MODERN;
        boolean animate = animated.isEnabled() && modern;
        AnimationStyle style = animStyle.getValue();

        // Apply scale via GL matrix (scale around the HUD position)
        GL11.glPushMatrix();
        GL11.glTranslatef(anchorX, anchorY, 0);
        GL11.glScalef(s, s, 1.0F);
        GL11.glTranslatef(-anchorX, -anchorY, 0);

        // Find max width for background/bar (scale-adjusted)
        int maxWidth = 0;
        for (String name : moduleNames) {
            maxWidth = Math.max(maxWidth, mc.fontRendererObj.getStringWidth(name));
        }

        for (int i = 0; i < moduleNames.size(); i++) {
            String displayName = moduleNames.get(i);

            // Find stable key for this display name to get animation progress
            String key = null;
            for (Map.Entry<String, String> entry : moduleDisplayNames.entrySet()) {
                if (entry.getValue().equals(displayName)) {
                    key = entry.getKey();
                    break;
                }
            }
            // Fallback to displayName if key not found (e.g., animating out modules)
            if (key == null) key = displayName;

            float progress = moduleAnimProgress.getOrDefault(key, 1.0F);

            int textWidth = mc.fontRendererObj.getStringWidth(displayName);
            int drawX = anchorX;

            if (rightAligned) {
                drawX = anchorX - textWidth;
            }

            // Apply animation offset
            if (animate && progress < 1.0F) {
                float eased = easeOutCubic(progress);
                if (style == AnimationStyle.SLIDE) {
                    int offset = (int) ((1.0F - eased) * (textWidth + 4));
                    if (rightAligned) {
                        drawX += offset;
                    } else {
                        drawX -= offset;
                    }
                } else if (style == AnimationStyle.FADE) {
                    // Fade is handled via alpha below
                } else if (style == AnimationStyle.SCALE) {
                    // Scale animation via GL
                }
            }

            // Calculate alpha for fade animation
            int alpha = 255;
            if (animate && style == AnimationStyle.FADE && progress < 1.0F) {
                alpha = (int) (easeOutCubic(progress) * 255);
                alpha = Math.max(0, Math.min(255, alpha));
            }

            // Background per line
            if (showBackground.isEnabled() && modern) {
                int bgAlpha = (int) (0x60 * (alpha / 255.0F));
                int bgColor = (bgAlpha << 24) | 0x101018;
                int bgX1 = rightAligned ? drawX - 2 : anchorX - 1;
                int bgX2 = rightAligned ? anchorX + 1 : anchorX + maxWidth + 2;
                net.minecraft.client.gui.Gui.drawRect(bgX1, lineY - 1, bgX2, lineY + mc.fontRendererObj.FONT_HEIGHT + 1, bgColor);
            }

            // Side bar (accent line on the left/right)
            if (showBar.isEnabled() && modern) {
                int barColor = getModernColor(i);
                int barAlpha = alpha << 24;
                int barColorWithAlpha = barColor | barAlpha;
                if (rightAligned) {
                    net.minecraft.client.gui.Gui.drawRect(anchorX + 1, lineY, anchorX + 2, lineY + mc.fontRendererObj.FONT_HEIGHT, barColorWithAlpha);
                } else {
                    net.minecraft.client.gui.Gui.drawRect(anchorX - 2, lineY, anchorX - 1, lineY + mc.fontRendererObj.FONT_HEIGHT, barColorWithAlpha);
                }
            }

            // Draw the text
            int textColor;
            if (modern) {
                textColor = getModernColor(i);
            } else {
                textColor = color;
            }

            // Apply alpha
            if (alpha < 255) {
                textColor = (textColor & 0x00FFFFFF) | (alpha << 24);
            }

            if (animate && style == AnimationStyle.SCALE && progress < 1.0F) {
                // Scale animation
                float scaleFactor = easeOutBack(progress);
                GL11.glPushMatrix();
                int textCenterX = drawX + textWidth / 2;
                int textCenterY = lineY + mc.fontRendererObj.FONT_HEIGHT / 2;
                GL11.glTranslatef(textCenterX, textCenterY, 0);
                GL11.glScalef(scaleFactor, scaleFactor, 1.0F);
                GL11.glTranslatef(-textCenterX, -textCenterY, 0);
                if (showShadow.isEnabled()) {
                    mc.fontRendererObj.drawStringWithShadow(displayName, drawX, lineY, textColor);
                } else {
                    mc.fontRendererObj.drawString(displayName, drawX, lineY, textColor);
                }
                GL11.glPopMatrix();
            } else {
                if (showShadow.isEnabled()) {
                    mc.fontRendererObj.drawStringWithShadow(displayName, drawX, lineY, textColor);
                } else {
                    mc.fontRendererObj.drawString(displayName, drawX, lineY, textColor);
                }
            }

            lineY += mc.fontRendererObj.FONT_HEIGHT + 2;
        }

        GL11.glPopMatrix();
    }

    private void renderInfoLines(ScaledResolution resolution, Minecraft mc) {
        if (mc.thePlayer == null) return;

        infoLines.clear();
        if (showFps.isEnabled()) infoLines.add(getDebugFPS() + " FPS");
        if (showCoords.isEnabled()) infoLines.add(String.format("XYZ: %.1f / %.1f / %.1f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        if (showDirection.isEnabled()) infoLines.add("Facing: " + getFacing(mc.thePlayer.rotationYaw));
        if (showBps.isEnabled()) infoLines.add(getBpsText(mc));
        if (infoLines.isEmpty()) return;

        int textColor = getColor();
        boolean rightAligned = isHudRightAligned(resolution);
        int anchorX = Math.max(0, Math.min(hudX.getValue(), resolution.getScaledWidth()));
        int infoY = resolution.getScaledHeight() - 4 - infoLines.size() * (mc.fontRendererObj.FONT_HEIGHT + 2);
        for (String line : infoLines) {
            int drawX = rightAligned ? anchorX - mc.fontRendererObj.getStringWidth(line) : anchorX;
            mc.fontRendererObj.drawStringWithShadow(line, drawX, infoY, textColor);
            infoY += mc.fontRendererObj.FONT_HEIGHT + 2;
        }
    }

    private String getBpsText(Minecraft mc) {
        long now = System.currentTimeMillis();
        computeBps(mc, now);
        if (now - lastBpsDisplayUpdate >= 250L) {
            lastBpsDisplayUpdate = now;
            cachedBpsText = String.format("%.1f BPS", smoothedBps);
        }
        return cachedBpsText;
    }

    private void computeBps(Minecraft mc, long now) {
        double dx = mc.thePlayer.posX - lastTickX;
        double dz = mc.thePlayer.posZ - lastTickZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double bps = 0;
        if (lastTickTime != 0L) {
            double seconds = (now - lastTickTime) / 1000.0;
            if (seconds > 0 && seconds < 1.0) bps = distance / seconds;
        }
        smoothedBps = smoothedBps * 0.92 + bps * 0.08;
        lastTickX = mc.thePlayer.posX;
        lastTickZ = mc.thePlayer.posZ;
        lastTickTime = now;
    }

    private static String getFacing(float yaw) {
        float w = ((yaw % 360) + 360) % 360;
        if (w >= 315 || w < 45) return "South (+Z)";
        if (w < 135) return "West (-X)";
        if (w < 225) return "North (-Z)";
        return "East (+X)";
    }

    private List<String> getEnabledModuleNames() {
        List<String> names = new ArrayList<>();
        LionClient client = LionClient.getInstance();
        if (client == null) return names;
        // Recompute the set of currently-enabled modules. We keep moduleDisplayNames
        // entries for animating-out modules so their real name still renders.
        enabledKeys.clear();
        for (Module module : client.getModuleManager().getModules()) {
            if (module == this) continue;
            if (!module.isEnabled()) continue;

            String key = getModuleKey(module);
            String displayName = getModuleDisplayName(module);
            moduleDisplayNames.put(key, displayName);
            enabledKeys.add(key);
            names.add(displayName);
        }

        // Add modules that are still animating out (disabled but progress > 0).
        for (Map.Entry<String, Float> entry : moduleAnimProgress.entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() > 0.0F && !enabledKeys.contains(key)) {
                String displayName = moduleDisplayNames.get(key);
                if (displayName != null) {
                    names.add(displayName);
                }
            }
        }

        sortByWidth(names);
        return names;
    }

    private List<String> getPreviewModuleNames() {
        List<String> names = getEnabledModuleNames();
        if (names.isEmpty()) {
            names.add("KillAura");
            names.add("AutoClicker");
            names.add("Sprint");
            sortByWidth(names);
        }
        return names;
    }

    private void sortByWidth(final List<String> names) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRendererObj == null) return;
        Collections.sort(names, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return mc.fontRendererObj.getStringWidth(b) - mc.fontRendererObj.getStringWidth(a);
            }
        });
    }

    private int getMaxTextWidth(Minecraft mc, List<String> names) {
        int w = 0;
        for (String n : names) w = Math.max(w, mc.fontRendererObj.getStringWidth(n));
        return w;
    }

    private int getColor() { return color.getRgb(); }

    private int getModernColor(int index) {
        double time = System.currentTimeMillis() / 320.0;
        float wave = (float) ((Math.sin(time + (index * 0.45)) + 1.0) * 0.5);
        return 0xFF000000 | ClickGuiModule.blendColor(ClickGuiModule.getLightAccentColor(), ClickGuiModule.getDarkAccentColor(), wave);
    }

    // --- Easing functions ---

    private static float easeOutCubic(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
    }

    // --- Helper: get debug FPS via reflection (1.8.9 has no getDebugFPS()) ---
    private static Integer getDebugFPS() {
        try {
            java.lang.reflect.Field debugFPSField = ReflectionHelper.findField(Minecraft.class, "field_71470_ab", "debugFPS");
            debugFPSField.setAccessible(true);
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                return (Integer) debugFPSField.get(mc);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    // --- Enums ---

    public enum AnimationStyle {
        SLIDE("Slide"), FADE("Fade"), SCALE("Scale");
        final String label;
        AnimationStyle(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum Mode {
        MODERN("Modern"), CLASSIC("Classic");
        final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    // --- HudElement implementation ---

    @Override public String getHudElementName() { return "Module List"; }
    @Override public boolean isHudElementVisible() { return isEnabled(); }
    @Override public int getHudX() { return hudX.getValue(); }
    @Override public int getHudY() { return hudY.getValue(); }
    @Override public void setHudPosition(int x, int y) { hudX.setManualValue(x); hudY.setManualValue(y); }
    @Override public boolean isHudRightAligned(ScaledResolution res) { return hudX.getValue() >= res.getScaledWidth() / 2; }
    @Override public int getHudWidth(ScaledResolution res) { return getMaxTextWidth(Minecraft.getMinecraft(), getPreviewModuleNames()); }
    @Override public int getHudHeight(ScaledResolution res) { return getPreviewModuleNames().size() * (Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 2); }
    @Override public float getHudScale() { return scale.getValue(); }
    @Override public void setHudScale(float s) { scale.setManualValue(s); }

    @Override
    public void renderHudPreview(ScaledResolution resolution) {
        List<String> previewLines = getEnabledModuleNames();
        if (previewLines.isEmpty()) {
            previewLines.add("KillAura");
            previewLines.add("AutoClicker");
            previewLines.add("Sprint");
        }
        renderModuleList(resolution, previewLines, getColor());
    }
}