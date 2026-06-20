package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;

public final class NoDelayModule extends Module {

    private final NumberSetting delayTicks = new NumberSetting("Delay Ticks", 0, 4, 1, 0);

    public NoDelayModule() {
        super("NoDelay", "Reduces or removes the left-click attack delay.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(delayTicks);
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!mc.inGameHasFocus) return;

        // Set left click counter to custom delay (0 = no delay, 4 = vanilla)
        // Uses the same reflection approach as ClickEngine.removeLeftClickDelay
        try {
            java.lang.reflect.Field leftClickCounterField = ReflectionHelper.findField(
                Minecraft.class, "field_71429_W", "leftClickCounter");
            if (leftClickCounterField != null) {
                leftClickCounterField.setAccessible(true);
                // Only override if the current counter is higher than our setting
                // This preserves vanilla behavior when setting is 4, but removes delay when 0
                int current = leftClickCounterField.getInt(mc);
                if (current > delayTicks.getValue()) {
                    leftClickCounterField.setInt(mc, delayTicks.getValue());
                }
            }
        } catch (Exception ignored) {
        }
    }
}