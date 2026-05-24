package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KeystrokesModule extends Module implements HudElement {
    private static final int KEY_SIZE = 22;
    private static final int KEY_GAP = 2;

    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, 8);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, 130);
    private final BooleanSetting showMouse = new BooleanSetting("Show Mouse", true);
    private final BooleanSetting showSpace = new BooleanSetting("Show Space", true);
    private final ColorSetting idleColor = new ColorSetting("Idle Color", 0x66202020);
    private final ColorSetting pressColor = new ColorSetting("Pressed Color", 0xCC4FB3FF);
    private final ColorSetting textColor = new ColorSetting("Text Color", 0xFFF0F0F0);

    public KeystrokesModule() {
        super("Keystrokes", "Renders WASD, mouse and space inputs on the HUD.", Category.RENDER, Keyboard.KEY_NONE);
        java.util.function.BooleanSupplier hidden = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        addSetting(hudX);
        addSetting(hudY);
        addSetting(showMouse);
        addSetting(showSpace);
        addSetting(idleColor);
        addSetting(pressColor);
        addSetting(textColor);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.gameSettings.showDebugInfo) {
            return;
        }

        ScaledResolution res = event.resolution;
        int anchorX = clampX(hudX.getValue(), res.getScaledWidth());
        int anchorY = clampY(hudY.getValue(), res.getScaledHeight());

        GameSettings gs = minecraft.gameSettings;
        FontRenderer font = minecraft.fontRendererObj;

        // Row 1: W
        drawKey(font, anchorX + KEY_SIZE + KEY_GAP, anchorY, "W", isPressed(gs.keyBindForward));
        // Row 2: A S D
        int row2Y = anchorY + KEY_SIZE + KEY_GAP;
        drawKey(font, anchorX, row2Y, "A", isPressed(gs.keyBindLeft));
        drawKey(font, anchorX + KEY_SIZE + KEY_GAP, row2Y, "S", isPressed(gs.keyBindBack));
        drawKey(font, anchorX + (KEY_SIZE + KEY_GAP) * 2, row2Y, "D", isPressed(gs.keyBindRight));

        int nextRowY = row2Y + KEY_SIZE + KEY_GAP;
        if (showMouse.isEnabled()) {
            int wide = KEY_SIZE * 3 + KEY_GAP * 2;
            int half = (wide - KEY_GAP) / 2;
            drawKeyWide(font, anchorX, nextRowY, half, "LMB", Mouse.isButtonDown(0));
            drawKeyWide(font, anchorX + half + KEY_GAP, nextRowY, half, "RMB", Mouse.isButtonDown(1));
            nextRowY += KEY_SIZE + KEY_GAP;
        }

        if (showSpace.isEnabled()) {
            int wide = KEY_SIZE * 3 + KEY_GAP * 2;
            drawKeyWide(font, anchorX, nextRowY, wide, "____", isPressed(gs.keyBindJump));
        }
    }

    private boolean isPressed(KeyBinding binding) {
        if (binding == null) {
            return false;
        }
        int keyCode = binding.getKeyCode();
        if (keyCode < 0) {
            return Mouse.isButtonDown(keyCode + 100);
        }
        return Keyboard.isKeyDown(keyCode);
    }

    private void drawKey(FontRenderer font, int x, int y, String label, boolean pressed) {
        drawKeyWide(font, x, y, KEY_SIZE, label, pressed);
    }

    private void drawKeyWide(FontRenderer font, int x, int y, int width, String label, boolean pressed) {
        int color = pressed ? pressColor.getArgb() : idleColor.getArgb();
        Gui.drawRect(x, y, x + width, y + KEY_SIZE, color);
        Gui.drawRect(x, y, x + width, y + 1, 0x33000000);
        Gui.drawRect(x, y + KEY_SIZE - 1, x + width, y + KEY_SIZE, 0x33000000);
        Gui.drawRect(x, y, x + 1, y + KEY_SIZE, 0x33000000);
        Gui.drawRect(x + width - 1, y, x + width, y + KEY_SIZE, 0x33000000);
        int textX = x + (width - font.getStringWidth(label)) / 2;
        int textY = y + (KEY_SIZE - font.FONT_HEIGHT) / 2 + 1;
        font.drawStringWithShadow(label, textX, textY, textColor.getArgb());
    }

    private int clampX(int value, int width) {
        int maxWidth = KEY_SIZE * 3 + KEY_GAP * 2;
        return Math.max(0, Math.min(value, width - maxWidth));
    }

    private int clampY(int value, int height) {
        int maxHeight = (KEY_SIZE + KEY_GAP) * 4;
        return Math.max(0, Math.min(value, height - maxHeight));
    }

    private int getElementWidth() {
        return KEY_SIZE * 3 + KEY_GAP * 2;
    }

    private int getElementHeight() {
        int rows = 2; // W and ASD
        if (showMouse.isEnabled()) {
            rows++;
        }
        if (showSpace.isEnabled()) {
            rows++;
        }
        return rows * (KEY_SIZE + KEY_GAP) - KEY_GAP;
    }

    @Override
    public String getHudElementName() {
        return "Keystrokes";
    }

    @Override
    public boolean isHudElementVisible() {
        return isEnabled();
    }

    @Override
    public int getHudX() {
        return hudX.getValue();
    }

    @Override
    public int getHudY() {
        return hudY.getValue();
    }

    @Override
    public int getHudWidth(ScaledResolution resolution) {
        return getElementWidth();
    }

    @Override
    public int getHudHeight(ScaledResolution resolution) {
        return getElementHeight();
    }

    @Override
    public boolean isHudRightAligned(ScaledResolution resolution) {
        return false;
    }

    @Override
    public void setHudPosition(int x, int y) {
        hudX.setManualValue(x);
        hudY.setManualValue(y);
    }
}
