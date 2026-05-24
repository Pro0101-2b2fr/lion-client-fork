package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class FullbrightModule extends Module {
    private float savedGamma = -1.0F;

    public FullbrightModule() {
        super("Fullbright", "Maximizes brightness so you can see in the dark.", Category.RENDER, Keyboard.KEY_NONE);
    }

    @Override
    protected void onEnable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings != null) {
            savedGamma = minecraft.gameSettings.gammaSetting;
            minecraft.gameSettings.gammaSetting = 100.0F;
        }
    }

    @Override
    protected void onDisable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings != null && savedGamma >= 0.0F) {
            minecraft.gameSettings.gammaSetting = savedGamma;
            savedGamma = -1.0F;
        }
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings != null && minecraft.gameSettings.gammaSetting < 100.0F) {
            minecraft.gameSettings.gammaSetting = 100.0F;
        }
    }
}
