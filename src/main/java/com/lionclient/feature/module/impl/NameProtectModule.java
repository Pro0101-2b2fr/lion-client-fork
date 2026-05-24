package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * Replaces the player's real username with a fake name everywhere it's rendered
 * (chat, tab list, nametags, scoreboard). Works by hooking into FontRenderer
 * to substitute the name string before drawing.
 */
public final class NameProtectModule extends Module {
    private static NameProtectModule instance;
    private static final String DEFAULT_FAKE_NAME = "You";

    private String fakeName = DEFAULT_FAKE_NAME;
    private String cachedRealName;

    public NameProtectModule() {
        super("NameProtect", "Hides your real username from the screen.", Category.CLIENT, Keyboard.KEY_NONE);
        instance = this;
    }

    public static NameProtectModule getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    /**
     * Replaces occurrences of the player's real name in the given string.
     * Called by the FontRenderer mixin before every drawString.
     */
    public static String protect(String text) {
        if (instance == null || !instance.isEnabled() || text == null || text.isEmpty()) {
            return text;
        }
        String realName = instance.getRealName();
        if (realName == null || realName.isEmpty() || !text.contains(realName)) {
            return text;
        }
        return text.replace(realName, instance.fakeName);
    }

    private String getRealName() {
        if (cachedRealName != null) {
            return cachedRealName;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.getSession() == null) {
            return null;
        }
        cachedRealName = minecraft.getSession().getUsername();
        return cachedRealName;
    }

    @Override
    protected void onEnable() {
        cachedRealName = null; // Re-cache on next call.
    }

    public void setFakeName(String name) {
        this.fakeName = (name == null || name.trim().isEmpty()) ? DEFAULT_FAKE_NAME : name.trim();
    }

    public String getFakeName() {
        return fakeName;
    }
}
