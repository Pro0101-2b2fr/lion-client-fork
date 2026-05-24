package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.EnumSetting;
import org.lwjgl.input.Keyboard;

public final class ClickGuiModule extends Module {
    private static final int DEFAULT_CLASSIC_ACCENT_COLOR = 0xFF305CA8;
    private static final int DEFAULT_MODERN_ACCENT_COLOR = 0xFF4A9EFF;
    private static ClickGuiModule instance;

    private final EnumSetting<GuiStyle> style = new EnumSetting<GuiStyle>("Style", GuiStyle.values(), GuiStyle.MODERN);
    private final BooleanSetting snowflakes = new BooleanSetting("Snowflakes", true);
    private final ColorSetting modernAccent = new ColorSetting("Modern Accent", DEFAULT_MODERN_ACCENT_COLOR);
    private final ColorSetting classicAccent = new ColorSetting("Classic Accent", DEFAULT_CLASSIC_ACCENT_COLOR);

    public ClickGuiModule() {
        super("ClickGUI", "Configure the ClickGUI", Category.CLIENT, Keyboard.KEY_RSHIFT);
        instance = this;
        addSetting(style);
        addSetting(snowflakes);
        addSetting(modernAccent);
        addSetting(classicAccent);

        java.util.function.BooleanSupplier classicVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.CLASSIC;
            }
        };
        java.util.function.BooleanSupplier modernVisibility = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return style.getValue() == GuiStyle.MODERN;
            }
        };

        snowflakes.setVisibility(modernVisibility);
        modernAccent.setVisibility(modernVisibility);
        classicAccent.setVisibility(classicVisibility);
    }

    @Override
    public void toggle() {
        LionClient client = LionClient.getInstance();
        if (client != null) {
            client.toggleClickGui();
        }
    }

    @Override
    public boolean canBeUnbound() {
        return false;
    }

    public static int getAccentColor() {
        if (instance == null) {
            return DEFAULT_CLASSIC_ACCENT_COLOR & 0x00FFFFFF;
        }
        return instance.classicAccent.getRgb();
    }

    public static GuiStyle getGuiStyle() {
        return instance == null ? GuiStyle.MODERN : instance.style.getValue();
    }

    public static ClickGuiModule getInstance() {
        return instance;
    }

    public static int getModernAccentColor() {
        if (instance == null) {
            return DEFAULT_MODERN_ACCENT_COLOR & 0x00FFFFFF;
        }
        return instance.modernAccent.getRgb();
    }

    public static boolean areSnowflakesEnabled() {
        return instance == null || instance.snowflakes.isEnabled();
    }

    public static int getLightAccentColor() {
        return blendColor(getModernAccentColor(), 0xFFFFFF, 0.52F);
    }

    public static int getDarkAccentColor() {
        return blendColor(getModernAccentColor(), 0x08111B, 0.48F);
    }

    public static int blendColor(int start, int end, float progress) {
        float amount = Math.max(0.0F, Math.min(1.0F, progress));
        int startR = (start >>> 16) & 255;
        int startG = (start >>> 8) & 255;
        int startB = start & 255;
        int endR = (end >>> 16) & 255;
        int endG = (end >>> 8) & 255;
        int endB = end & 255;
        int red = Math.round(startR + ((endR - startR) * amount));
        int green = Math.round(startG + ((endG - startG) * amount));
        int blue = Math.round(startB + ((endB - startB) * amount));
        return (red << 16) | (green << 8) | blue;
    }

    public enum GuiStyle {
        MODERN("Modern"),
        CLASSIC("Classic");

        private final String label;

        GuiStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
