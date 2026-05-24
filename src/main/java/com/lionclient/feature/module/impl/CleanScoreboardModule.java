package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import org.lwjgl.input.Keyboard;

/**
 * Removes the red score numbers from the sidebar scoreboard. Purely visual.
 * The mixin or hook checks {@link #shouldHideNumbers()} before rendering scores.
 */
public final class CleanScoreboardModule extends Module {
    private static CleanScoreboardModule instance;

    private final BooleanSetting hideNumbers = new BooleanSetting("Hide Numbers", true);

    public CleanScoreboardModule() {
        super("CleanScoreboard", "Removes red score numbers from the sidebar.", Category.RENDER, Keyboard.KEY_NONE);
        instance = this;
        addSetting(hideNumbers);
    }

    public static boolean shouldHideNumbers() {
        return instance != null && instance.isEnabled() && instance.hideNumbers.isEnabled();
    }
}
