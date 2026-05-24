package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.MouseButtonHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Keyboard;

public final class ClickRecorderModule extends Module {
    private final EnumSetting<Source> source = new EnumSetting<Source>("Source", Source.values(), Source.LEFT);
    private final BooleanSetting showMessage = new BooleanSetting("Show Message", true);
    private final BooleanSetting clearOnEnable = new BooleanSetting("Clear On Enable", true);
    private final NumberSetting maxClicks = new NumberSetting("Max Clicks", 0, 1000, 5, 0);

    private long lastClickAt;

    public ClickRecorderModule() {
        super("ClickRecorder", "Records click timings to be replayed by AutoClicker / RightClicker.", Category.CLIENT, Keyboard.KEY_NONE);
        addSetting(source);
        addSetting(showMessage);
        addSetting(clearOnEnable);
        addSetting(maxClicks);
    }

    @Override
    public boolean showsKeybindSetting() {
        return false;
    }

    @Override
    protected void onEnable() {
        if (clearOnEnable.isEnabled()) {
            ClickPatternStore.clear();
        }
        lastClickAt = 0L;
        sendChat("Recording " + source.getValue().toString().toLowerCase() + " clicks. Pattern size: " + ClickPatternStore.size());
    }

    @Override
    protected void onDisable() {
        if (ClickPatternStore.isEmpty()) {
            sendChat("Stopped with no captured clicks.");
            return;
        }
        sendChat("Saved " + ClickPatternStore.size() + " recorded clicks.");
    }

    @Override
    public void onMouseEvent(MouseEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.currentScreen != null) {
            return;
        }
        if (!event.buttonstate) {
            return;
        }
        if (MouseButtonHelper.isDispatchingSyntheticEvent()) {
            return;
        }
        if (!matchesSource(event.button)) {
            return;
        }
        int cap = maxClicks.getValue();
        if (cap > 0 && ClickPatternStore.size() >= cap) {
            return;
        }

        long now = System.nanoTime();
        int delay = lastClickAt == 0L ? 0 : (int) ((now - lastClickAt) / 1_000_000L);
        lastClickAt = now;
        ClickPatternStore.addDelay(delay);

        if (showMessage.isEnabled()) {
            sendChat("Captured click " + ClickPatternStore.size() + " (" + delay + "ms)");
        }

        if (cap > 0 && ClickPatternStore.size() >= cap) {
            sendChat("Reached max clicks. Disabling.");
            setEnabled(false);
        }
    }

    @Override
    public String getHudInfo() {
        return ClickPatternStore.size() + "";
    }

    private boolean matchesSource(int button) {
        switch (source.getValue()) {
            case LEFT: return button == 0;
            case RIGHT: return button == 1;
            case BOTH: return button == 0 || button == 1;
            default: return false;
        }
    }

    private void sendChat(String text) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText("[ClickRecorder] " + text));
        }
    }

    private enum Source {
        LEFT("Left"),
        RIGHT("Right"),
        BOTH("Both");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
