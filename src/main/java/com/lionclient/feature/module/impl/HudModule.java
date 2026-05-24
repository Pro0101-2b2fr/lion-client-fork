package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.HudElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;

public final class HudModule extends Module implements HudElement {
    private static final int DEFAULT_X = 4;
    private static final int DEFAULT_Y = 4;
    private static HudModule instance;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.MODERN);
    private final ColorSetting color = new ColorSetting("Color", 0xFFFFFFFF);
    private final BooleanSetting showFps = new BooleanSetting("FPS", true);
    private final BooleanSetting showCoords = new BooleanSetting("Coordinates", false);
    private final BooleanSetting showDirection = new BooleanSetting("Direction", false);
    private final BooleanSetting showBps = new BooleanSetting("BPS", false);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, DEFAULT_X);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, DEFAULT_Y);

    private double lastTickX;
    private double lastTickZ;
    private long lastTickTime;
    private double smoothedBps;
    private long lastBpsDisplayUpdate;
    private String cachedBpsText = "0.00 BPS";
    private final ActionSetting editor = new ActionSetting("Move HUD", new Runnable() {
        @Override
        public void run() {
            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.openHudEditor();
            }
        }
    }, new ActionSetting.ValueProvider() {
        @Override
        public String get() {
            return "OPEN";
        }
    });

    public HudModule() {
        super("HUD", "Displays enabled modules on screen.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;
        color.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.CLASSIC;
            }
        });
        java.util.function.BooleanSupplier hidden = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        addSetting(mode);
        addSetting(color);
        addSetting(showFps);
        addSetting(showCoords);
        addSetting(showDirection);
        addSetting(showBps);
        addSetting(hudX);
        addSetting(hudY);
        addSetting(editor);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings.showDebugInfo) {
            return;
        }

        renderModuleList(event.resolution, getEnabledModuleNames(), getColor());
        renderInfoLines(event.resolution, minecraft);
    }

    public static HudModule getInstance() {
        return instance;
    }

    @Override
    public String getHudElementName() {
        return "Module List";
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
    public void setHudPosition(int x, int y) {
        hudX.setManualValue(x);
        hudY.setManualValue(y);
    }

    @Override
    public boolean isHudRightAligned(ScaledResolution resolution) {
        return hudX.getValue() >= resolution.getScaledWidth() / 2;
    }

    @Override
    public int getHudWidth(ScaledResolution resolution) {
        return getMaxTextWidth(Minecraft.getMinecraft(), getPreviewModuleNames());
    }

    @Override
    public int getHudHeight(ScaledResolution resolution) {
        return getPreviewModuleNames().size() * (Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT + 2);
    }

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

    private void renderModuleList(ScaledResolution resolution, List<String> moduleNames, int color) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int anchorX = Math.max(0, Math.min(hudX.getValue(), resolution.getScaledWidth()));
        int anchorY = Math.max(0, Math.min(hudY.getValue(), Math.max(0, resolution.getScaledHeight() - minecraft.fontRendererObj.FONT_HEIGHT)));
        boolean rightAligned = anchorX >= resolution.getScaledWidth() / 2;
        int lineY = anchorY;
        boolean modern = mode.getValue() == Mode.MODERN;

        for (int i = 0; i < moduleNames.size(); i++) {
            String moduleName = moduleNames.get(i);
            int drawX = rightAligned ? anchorX - minecraft.fontRendererObj.getStringWidth(moduleName) : anchorX;
            minecraft.fontRendererObj.drawStringWithShadow(moduleName, drawX, lineY, modern ? getModernColor(i) : color);
            lineY += minecraft.fontRendererObj.FONT_HEIGHT + 2;
        }
    }

    private void renderInfoLines(ScaledResolution resolution, Minecraft minecraft) {
        if (minecraft.thePlayer == null) {
            return;
        }

        List<String> info = new ArrayList<String>();
        if (showFps.isEnabled()) {
            info.add(Minecraft.getDebugFPS() + " FPS");
        }
        if (showCoords.isEnabled()) {
            info.add(String.format("XYZ: %.1f / %.1f / %.1f", minecraft.thePlayer.posX, minecraft.thePlayer.posY, minecraft.thePlayer.posZ));
        }
        if (showDirection.isEnabled()) {
            info.add("Facing: " + getFacing(minecraft.thePlayer.rotationYaw));
        }
        if (showBps.isEnabled()) {
            info.add(getBpsText(minecraft));
        }
        if (info.isEmpty()) {
            return;
        }

        int color = getColor();
        boolean rightAligned = isHudRightAligned(resolution);
        int anchorX = Math.max(0, Math.min(hudX.getValue(), resolution.getScaledWidth()));
        int infoY = resolution.getScaledHeight() - 4 - info.size() * (minecraft.fontRendererObj.FONT_HEIGHT + 2);
        for (String line : info) {
            int drawX = rightAligned ? anchorX - minecraft.fontRendererObj.getStringWidth(line) : anchorX;
            minecraft.fontRendererObj.drawStringWithShadow(line, drawX, infoY, color);
            infoY += minecraft.fontRendererObj.FONT_HEIGHT + 2;
        }
    }

    private String getBpsText(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        // Update the internal smoothed value every frame for accuracy.
        computeBps(minecraft, now);
        // But only refresh the displayed text every 250 ms so it's readable.
        if (now - lastBpsDisplayUpdate >= 250L) {
            lastBpsDisplayUpdate = now;
            cachedBpsText = String.format("%.1f BPS", smoothedBps);
        }
        return cachedBpsText;
    }

    private void computeBps(Minecraft minecraft, long now) {
        double dx = minecraft.thePlayer.posX - lastTickX;
        double dz = minecraft.thePlayer.posZ - lastTickZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double bps = 0.0D;
        if (lastTickTime != 0L) {
            double seconds = (now - lastTickTime) / 1000.0D;
            if (seconds > 0.0D && seconds < 1.0D) {
                bps = distance / seconds;
            }
        }
        // Heavy smoothing so the value drifts slowly.
        smoothedBps = smoothedBps * 0.92D + bps * 0.08D;
        lastTickX = minecraft.thePlayer.posX;
        lastTickZ = minecraft.thePlayer.posZ;
        lastTickTime = now;
    }

    private static String getFacing(float yaw) {
        float wrapped = ((yaw % 360.0F) + 360.0F) % 360.0F;
        if (wrapped >= 315.0F || wrapped < 45.0F) {
            return "South (+Z)";
        }
        if (wrapped < 135.0F) {
            return "West (-X)";
        }
        if (wrapped < 225.0F) {
            return "North (-Z)";
        }
        return "East (+X)";
    }

    private List<String> getEnabledModuleNames() {
        List<String> moduleNames = new ArrayList<String>();
        LionClient client = LionClient.getInstance();
        if (client == null) {
            return moduleNames;
        }

        for (Module module : client.getModuleManager().getModules()) {
            if (!module.isEnabled() || module == this) {
                continue;
            }
            String hudInfo = module.getHudInfo();
            moduleNames.add(hudInfo == null || hudInfo.isEmpty() ? module.getName() : module.getName() + " " + hudInfo);
        }

        sortByWidth(moduleNames);
        return moduleNames;
    }

    private List<String> getPreviewModuleNames() {
        List<String> moduleNames = getEnabledModuleNames();
        if (moduleNames.isEmpty()) {
            moduleNames.add("KillAura");
            moduleNames.add("AutoClicker");
            moduleNames.add("Sprint");
            sortByWidth(moduleNames);
        }
        return moduleNames;
    }

    private void sortByWidth(final List<String> moduleNames) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        Collections.sort(moduleNames, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return minecraft.fontRendererObj.getStringWidth(right) - minecraft.fontRendererObj.getStringWidth(left);
            }
        });
    }

    private int getMaxTextWidth(Minecraft minecraft, List<String> moduleNames) {
        int width = 0;
        for (String moduleName : moduleNames) {
            width = Math.max(width, minecraft.fontRendererObj.getStringWidth(moduleName));
        }
        return width;
    }

    private int getColor() {
        return color.getArgb();
    }

    private int getModernColor(int index) {
        double time = System.currentTimeMillis() / 320.0D;
        float wave = (float) ((Math.sin(time + (index * 0.45D)) + 1.0D) * 0.5D);
        return 0xFF000000 | ClickGuiModule.blendColor(ClickGuiModule.getLightAccentColor(), ClickGuiModule.getDarkAccentColor(), wave);
    }

    private enum Mode {
        MODERN("Modern"),
        CLASSIC("Classic");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
