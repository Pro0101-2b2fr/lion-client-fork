package com.lionclient.input;

import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class KeybindHandler {
    private final ModuleManager moduleManager;

    private KeybindHandler(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public static void register(ModuleManager moduleManager) {
        FMLCommonHandler.instance().bus().register(new KeybindHandler(moduleManager));
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!Keyboard.getEventKeyState()) {
            return;
        }

        int keyCode = Keyboard.getEventKey();
        // Some layouts/keys (e.g. AZERTY "&" or "!") fire a key event whose
        // LWJGL key code is KEY_NONE (0). Without this guard every UNBOUND module
        // (keyCode == KEY_NONE) would match and toggle, flipping the whole client.
        if (keyCode == Keyboard.KEY_NONE) {
            return;
        }

        // Don't fire binds while a screen is open (chat, inventory, GUI…).
        if (Minecraft.getMinecraft().currentScreen != null) {
            return;
        }

        for (Module module : moduleManager.getModules()) {
            if (module.getKeyCode() != Keyboard.KEY_NONE && module.getKeyCode() == keyCode) {
                module.toggle();
            }
        }
    }
}

