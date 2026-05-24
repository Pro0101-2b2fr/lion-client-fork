package com.lionclient.feature.setting;

import com.lionclient.config.ConfigManager;

/**
 * Stores a color as an ARGB integer with helpers to manipulate it as HSV. The
 * GUI exposes a wheel + value square popup that mutates this setting through
 * {@link #setHsva(float, float, float, float)}.
 */
public final class ColorSetting extends Setting {
    private float hue;
    private float saturation;
    private float value;
    private float alpha;

    public ColorSetting(String name, int defaultArgb) {
        super(name);
        applyArgb(defaultArgb);
    }

    public int getArgb() {
        return hsvaToArgb(hue, saturation, value, alpha);
    }

    public int getRgb() {
        return getArgb() & 0x00FFFFFF;
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getValue() {
        return value;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setArgb(int argb) {
        applyArgb(argb);
        ConfigManager.saveActiveConfig();
    }

    public void setArgbNoSave(int argb) {
        applyArgb(argb);
    }

    public void setHsva(float hue, float saturation, float value, float alpha) {
        this.hue = clamp01(hue);
        this.saturation = clamp01(saturation);
        this.value = clamp01(value);
        this.alpha = clamp01(alpha);
        ConfigManager.saveActiveConfig();
    }

    public void setHsvaNoSave(float hue, float saturation, float value, float alpha) {
        this.hue = clamp01(hue);
        this.saturation = clamp01(saturation);
        this.value = clamp01(value);
        this.alpha = clamp01(alpha);
    }

    @Override
    public String getValueText() {
        return String.format("#%06X", getRgb());
    }

    private void applyArgb(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsv = rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.value = hsv[2];
        this.alpha = a == 0 ? 1.0F : a / 255.0F;
    }

    public static int hsvaToArgb(float hue, float saturation, float value, float alpha) {
        float h = (hue * 6.0F) % 6.0F;
        if (h < 0.0F) {
            h += 6.0F;
        }
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));

        float red;
        float green;
        float blue;
        switch (sector) {
            case 0: red = value; green = t; blue = p; break;
            case 1: red = q; green = value; blue = p; break;
            case 2: red = p; green = value; blue = t; break;
            case 3: red = p; green = q; blue = value; break;
            case 4: red = t; green = p; blue = value; break;
            default: red = value; green = p; blue = q; break;
        }

        int alphaByte = Math.round(clamp01(alpha) * 255.0F);
        int redByte = Math.round(clamp01(red) * 255.0F);
        int greenByte = Math.round(clamp01(green) * 255.0F);
        int blueByte = Math.round(clamp01(blue) * 255.0F);
        return (alphaByte << 24) | (redByte << 16) | (greenByte << 8) | blueByte;
    }

    public static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255.0F;
        float gf = g / 255.0F;
        float bf = b / 255.0F;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float hue;
        if (delta < 1.0E-5F) {
            hue = 0.0F;
        } else if (max == rf) {
            hue = ((gf - bf) / delta) % 6.0F;
        } else if (max == gf) {
            hue = ((bf - rf) / delta) + 2.0F;
        } else {
            hue = ((rf - gf) / delta) + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max < 1.0E-5F ? 0.0F : delta / max;
        return new float[] {hue, saturation, max};
    }

    private static float clamp01(float input) {
        if (input < 0.0F) {
            return 0.0F;
        }
        if (input > 1.0F) {
            return 1.0F;
        }
        return input;
    }
}
