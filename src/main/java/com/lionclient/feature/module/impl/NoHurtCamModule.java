package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import org.lwjgl.input.Keyboard;

public final class NoHurtCamModule extends Module {
    public NoHurtCamModule() {
        super("NoHurtCam", "Removes the camera shake when taking damage.", Category.RENDER, Keyboard.KEY_NONE);
    }

    public static boolean shouldCancel() {
        LionClient client = LionClient.getInstance();
        if (client == null) {
            return false;
        }
        NoHurtCamModule module = client.getModuleManager().getModule(NoHurtCamModule.class);
        return module != null && module.isEnabled();
    }
}
