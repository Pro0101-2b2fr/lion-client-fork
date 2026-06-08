package com.lionclient.feature.setting;

import com.lionclient.config.ConfigManager;

public final class FloatSetting extends Setting {
    private final float min;
    private final float max;
    private final float step;
    private float value;

    public FloatSetting(String name, float min, float max, float step, float value) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clampToRange(value);
    }

    public float getValue() {
        return value;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    public float getStep() {
        return step;
    }

    public void increment() {
        value = clampToRange(value + step);
        ConfigManager.saveActiveConfig();
    }

    public void decrement() {
        value = clampToRange(value - step);
        ConfigManager.saveActiveConfig();
    }

    public void setValue(float value) {
        setValue(value, true);
    }

    public void setValue(float value, boolean save) {
        this.value = clampToRange(value);
        if (save) {
            ConfigManager.saveActiveConfig();
        }
    }

    public void setManualValue(float value) {
        setManualValue(value, true);
    }

    public void setManualValue(float value, boolean save) {
        this.value = clampManual(value);
        if (save) {
            ConfigManager.saveActiveConfig();
        }
    }

    @Override
    public String getValueText() {
        return Float.toString(value);
    }

    private float clampToRange(float input) {
        return Math.max(min, Math.min(max, input));
    }

    private float clampManual(float input) {
        return Math.max(min, input);
    }
}
