package com.lionclient.gui;

import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Drag-and-drop editor for every {@link HudElement} registered in the module
 * manager. Hover an element to highlight it, click and drag to move it. The
 * element snaps to the screen edges when the cursor approaches them and shows
 * its name plus position in a label above the bounding box.
 */
public final class HudEditorScreen extends GuiScreen {
    private static final int PADDING = 3;
    private static final int SNAP_DISTANCE = 6;

    private final ModuleManager moduleManager;
    private HudElement dragged;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudEditorScreen(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackgroundOverlay();
        drawCenteredString(this.fontRendererObj, "HUD Editor", this.width / 2, 10, 0xFFFFFFFF);
        drawCenteredString(this.fontRendererObj, "Drag any element. ESC to close.", this.width / 2, 22, 0xFFB8BECF);

        ScaledResolution resolution = new ScaledResolution(this.mc);
        if (dragged != null) {
            int newX = mouseX - dragOffsetX;
            int newY = mouseY - dragOffsetY;
            int width = dragged.getHudWidth(resolution);
            int height = dragged.getHudHeight(resolution);
            newX = clamp(newX, 0, Math.max(0, resolution.getScaledWidth() - width));
            newY = clamp(newY, 0, Math.max(0, resolution.getScaledHeight() - height));
            newX = snap(newX, 0);
            newX = snap(newX, resolution.getScaledWidth() - width);
            newY = snap(newY, 0);
            newY = snap(newY, resolution.getScaledHeight() - height);
            dragged.setHudPosition(newX, newY);
        }

        List<HudElement> elements = collectElements();
        for (HudElement element : elements) {
            element.renderHudPreview(resolution);
        }

        for (HudElement element : elements) {
            drawElementBox(element, resolution, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(this.mc);
        List<HudElement> elements = collectElements();
        // Iterate in reverse so the topmost element wins when bounds overlap.
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            Bounds bounds = getElementBounds(element, resolution);
            if (bounds.contains(mouseX, mouseY)) {
                dragged = element;
                dragOffsetX = mouseX - element.getHudX();
                dragOffsetY = mouseY - element.getHudY();
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragged = null;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private List<HudElement> collectElements() {
        List<HudElement> result = new ArrayList<HudElement>();
        if (moduleManager == null) {
            return result;
        }
        for (Module module : moduleManager.getModules()) {
            if (module instanceof HudElement) {
                HudElement element = (HudElement) module;
                if (element.isHudElementVisible()) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    private void drawBackgroundOverlay() {
        Gui.drawRect(0, 0, this.width, this.height, 0x80101418);
    }

    private void drawElementBox(HudElement element, ScaledResolution resolution, int mouseX, int mouseY) {
        Bounds bounds = getElementBounds(element, resolution);
        boolean active = element == dragged;
        boolean hovered = bounds.contains(mouseX, mouseY);

        int outline;
        if (active) {
            outline = 0xFF4FB3FF;
        } else if (hovered) {
            outline = 0xFFE6EAF3;
        } else {
            outline = 0xFF8A8F9E;
        }
        int fillAlpha = active ? 0x60 : hovered ? 0x40 : 0x20;
        int fill = (fillAlpha << 24) | 0x262B3E;
        Gui.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, fill);
        Gui.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + 1, outline);
        Gui.drawRect(bounds.left, bounds.bottom - 1, bounds.right, bounds.bottom, outline);
        Gui.drawRect(bounds.left, bounds.top, bounds.left + 1, bounds.bottom, outline);
        Gui.drawRect(bounds.right - 1, bounds.top, bounds.right, bounds.bottom, outline);

        if (hovered || active) {
            String label = element.getHudElementName() + " · " + element.getHudX() + "," + element.getHudY();
            int labelY = bounds.top - this.fontRendererObj.FONT_HEIGHT - 2;
            if (labelY < 4) {
                labelY = bounds.bottom + 3;
            }
            int labelX = bounds.left;
            this.fontRendererObj.drawStringWithShadow(label, labelX, labelY, 0xFFFFFFFF);
        }
    }

    private Bounds getElementBounds(HudElement element, ScaledResolution resolution) {
        int left = element.getHudX() - PADDING;
        int top = element.getHudY() - PADDING;
        int right = element.getHudX() + element.getHudWidth(resolution) + PADDING;
        int bottom = element.getHudY() + element.getHudHeight(resolution) + PADDING;
        return new Bounds(left, top, right, bottom);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int snap(int value, int target) {
        return Math.abs(value - target) <= SNAP_DISTANCE ? target : value;
    }

    private static final class Bounds {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
        }
    }
}
