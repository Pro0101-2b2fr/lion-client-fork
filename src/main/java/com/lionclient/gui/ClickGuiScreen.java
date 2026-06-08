package com.lionclient.gui;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.IntRangeSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.Setting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public final class ClickGuiScreen extends GuiScreen {
    private final List<CategoryPanel> panels = new ArrayList<CategoryPanel>();

    public ClickGuiScreen(ModuleManager moduleManager) {
        int x = 20;
        for (Category category : Category.values()) {
            panels.add(new CategoryPanel(category, x, 30, moduleManager.getModules(category)));
            x += 115;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int accent = ClickGuiModule.getAccentColor();
        drawCenteredString(this.fontRendererObj, "LionClient", this.width / 2, 10, accent);

        for (CategoryPanel panel : panels) {
            panel.draw(mouseX, mouseY, fontRendererObj, this.width);
        }

        for (CategoryPanel panel : panels) {
            panel.drawDescriptionOverlay(fontRendererObj, this.width);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (CategoryPanel panel : panels) {
            panel.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (CategoryPanel panel : panels) {
            if (panel.handleKeyTyped(keyCode)) {
                return;
            }
        }

        if (handleCloseKeybind(keyCode)) {
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        for (CategoryPanel panel : panels) {
            panel.mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int amount = wheel > 0 ? 12 : -12;
        for (CategoryPanel panel : panels) {
            panel.offset(amount);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean handleCloseKeybind(int keyCode) {
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }

        ClickGuiModule clickGuiModule = ClickGuiModule.getInstance();
        if (clickGuiModule == null || keyCode != clickGuiModule.getKeyCode()) {
            return false;
        }

        LionClient client = LionClient.getInstance();
        if (client == null) {
            return false;
        }

        client.toggleClickGui();
        return true;
    }

    private static final class CategoryPanel {
        private static final int WIDTH = 105;
        private static final int HEADER_HEIGHT = 16;
        private static final int ROW_HEIGHT = 14;
        private static final long DESCRIPTION_HOVER_DELAY_MS = 2000L;
        private static final String KEYBIND_LABEL = "Keybind";

        private final Category category;
        private final List<Module> modules;
        private Module expandedModule;
        private Module bindingModule;
        private Module hoveredModule;
        private Setting draggingSetting;
        private boolean draggingRangeHigh;
        private long hoveredSince;
        private int hoveredRowY;
        private int x;
        private int y;
        private boolean dragging;
        private int dragOffsetX;
        private int dragOffsetY;

        private CategoryPanel(Category category, int x, int y, List<Module> modules) {
            this.category = category;
            this.x = x;
            this.y = y;
            this.modules = modules;
        }

        private void draw(int mouseX, int mouseY, net.minecraft.client.gui.FontRenderer fontRenderer, int screenWidth) {
            int accent = ClickGuiModule.getAccentColor();
            if (draggingSetting != null && !org.lwjgl.input.Mouse.isButtonDown(0)) {
                draggingSetting = null;
            }
            if (draggingSetting != null) {
                updateDragValue(mouseX);
            }
            if (dragging) {
                x = mouseX - dragOffsetX;
                y = mouseY - dragOffsetY;
            }

            Gui.drawRect(x, y, x + WIDTH, y + HEADER_HEIGHT, 0xFF000000 | accent);
            Gui.drawRect(x, y + HEADER_HEIGHT, x + WIDTH, y + getContentHeight(), 0xB0101018);
            fontRenderer.drawStringWithShadow(category.name(), x + 4, y + 4, 0xFFFFFFFF);

            int rowY = y + HEADER_HEIGHT;
            Module currentlyHoveredModule = null;
            int currentHoveredRowY = 0;
            for (Module module : modules) {
                int color = module.isEnabled() ? (0xFF000000 | accent) : 0xFF8A8F9E;
                Gui.drawRect(x + 2, rowY + 1, x + WIDTH - 2, rowY + ROW_HEIGHT - 1, 0x80262B3E);
                fontRenderer.drawString(module.getName(), x + 6, rowY + 3, color);
                fontRenderer.drawString("...", x + WIDTH - 16, rowY + 3, 0xFF000000 | accent);

                if (isHovered(mouseX, mouseY, x, rowY, WIDTH, ROW_HEIGHT)) {
                    currentlyHoveredModule = module;
                    currentHoveredRowY = rowY;
                }
                rowY += ROW_HEIGHT;

                if (expandedModule == module) {
                    for (Setting setting : module.getSettings()) {
                        if (!setting.isVisible()) {
                            continue;
                        }
                        
                        Gui.drawRect(x + 4, rowY, x + WIDTH - 4, rowY + ROW_HEIGHT, 0x9040485C);
                        
                        if (setting instanceof NumberSetting || setting instanceof DecimalSetting) {
                            double val = 0;
                            double min = 0;
                            double max = 1;
                            if (setting instanceof NumberSetting) {
                                NumberSetting number = (NumberSetting) setting;
                                val = number.getValue();
                                min = number.getMin();
                                max = number.getMax();
                            } else {
                                DecimalSetting decimal = (DecimalSetting) setting;
                                val = decimal.getValue();
                                min = decimal.getMin();
                                max = decimal.getMax();
                            }
                            float percent = (float) ((val - min) / (max - min));
                            percent = Math.max(0.0F, Math.min(1.0F, percent));
                            int filledWidth = (int) ((WIDTH - 8) * percent);
                            Gui.drawRect(x + 4, rowY, x + 4 + filledWidth, rowY + ROW_HEIGHT, 0x70000000 | accent);
                        } else if (setting instanceof IntRangeSetting) {
                            IntRangeSetting range = (IntRangeSetting) setting;
                            double min = range.getMin();
                            double max = range.getMax();
                            double low = range.getLow();
                            double high = range.getHigh();
                            float lowPercent = (float) ((low - min) / (max - min));
                            float highPercent = (float) ((high - min) / (max - min));
                            int startX = x + 4 + (int) ((WIDTH - 8) * lowPercent);
                            int endX = x + 4 + (int) ((WIDTH - 8) * highPercent);
                            Gui.drawRect(startX, rowY, endX, rowY + ROW_HEIGHT, 0x70000000 | accent);
                        }
                        
                        fontRenderer.drawString(setting.getName(), x + 6, rowY + 3, 0xFFE8EAF1);
                        fontRenderer.drawString(setting.getValueText(), x + WIDTH - 6 - fontRenderer.getStringWidth(setting.getValueText()), rowY + 3, 0xFF000000 | accent);
                        rowY += ROW_HEIGHT;
                    }

                    if (module.showsKeybindSetting()) {
                        Gui.drawRect(x + 4, rowY, x + WIDTH - 4, rowY + ROW_HEIGHT, 0x9040485C);
                        fontRenderer.drawString(KEYBIND_LABEL, x + 6, rowY + 3, 0xFFE8EAF1);
                        String keybindText = bindingModule == module ? "Press key..." : getKeybindText(module);
                        fontRenderer.drawString(keybindText, x + WIDTH - 6 - fontRenderer.getStringWidth(keybindText), rowY + 3, 0xFF000000 | accent);
                        rowY += ROW_HEIGHT;
                    }
                }
            }

            updateHoveredModule(currentlyHoveredModule);
            hoveredRowY = currentHoveredRowY;
        }

        private void drawDescriptionOverlay(net.minecraft.client.gui.FontRenderer fontRenderer, int screenWidth) {
            if (shouldShowDescription(hoveredModule)) {
                drawModuleDescription(hoveredModule, hoveredRowY, fontRenderer, screenWidth);
            }
        }

        private void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (isHovered(mouseX, mouseY, x, y, WIDTH, HEADER_HEIGHT) && mouseButton == 0) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
                return;
            }

            int rowY = y + HEADER_HEIGHT;
            for (Module module : modules) {
                if (isHovered(mouseX, mouseY, x, rowY, WIDTH, ROW_HEIGHT)) {
                    if (mouseButton == 0) {
                        module.toggle();
                        return;
                    }
                    if (mouseButton == 1) {
                        expandedModule = expandedModule == module ? null : module;
                        if (expandedModule != module) {
                            bindingModule = null;
                        }
                        return;
                    }
                }
                rowY += ROW_HEIGHT;

                if (expandedModule == module) {
                    for (Setting setting : module.getSettings()) {
                        if (!setting.isVisible()) {
                            continue;
                        }
                        if (isHovered(mouseX, mouseY, x + 4, rowY, WIDTH - 8, ROW_HEIGHT)) {
                            if (mouseButton == 0 && (setting instanceof NumberSetting || setting instanceof DecimalSetting || setting instanceof IntRangeSetting)) {
                                draggingSetting = setting;
                                if (setting instanceof IntRangeSetting) {
                                    IntRangeSetting range = (IntRangeSetting) setting;
                                    float pct = (float)(mouseX - (x + 4)) / (WIDTH - 8);
                                    double clickedVal = range.getMin() + pct * (range.getMax() - range.getMin());
                                    double distLow = Math.abs(clickedVal - range.getLow());
                                    double distHigh = Math.abs(clickedVal - range.getHigh());
                                    draggingRangeHigh = distHigh < distLow;
                                }
                                updateDragValue(mouseX);
                            } else {
                                handleSettingClick(setting, mouseButton, module);
                            }
                            return;
                        }
                        rowY += ROW_HEIGHT;
                    }

                    if (module.showsKeybindSetting() && isHovered(mouseX, mouseY, x + 4, rowY, WIDTH - 8, ROW_HEIGHT)) {
                        handleKeybindClick(module, mouseButton);
                        return;
                    }
                    if (module.showsKeybindSetting()) {
                        rowY += ROW_HEIGHT;
                    }
                }
            }
        }

        private void mouseReleased() {
            dragging = false;
            draggingSetting = null;
        }

        private void offset(int amount) {
            y += amount;
        }

        private int getContentHeight() {
            int rows = modules.size();
            if (expandedModule != null) {
                for (Setting setting : expandedModule.getSettings()) {
                    if (setting.isVisible()) {
                        rows++;
                    }
                }
                if (expandedModule.showsKeybindSetting()) {
                    rows++;
                }
            }
            return HEADER_HEIGHT + (rows * ROW_HEIGHT);
        }

        private void handleSettingClick(Setting setting, int mouseButton, Module module) {
            if (setting instanceof ActionSetting && mouseButton == 0) {
                ((ActionSetting) setting).run();
                return;
            }

            if (setting instanceof BooleanSetting && mouseButton == 0) {
                ((BooleanSetting) setting).toggle();
                return;
            }

            if (setting instanceof IntRangeSetting) {
                IntRangeSetting range = (IntRangeSetting) setting;
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                if (mouseButton == 0) {
                    if (shift) {
                        range.incrementLow();
                    } else {
                        range.incrementHigh();
                    }
                } else if (mouseButton == 1) {
                    if (shift) {
                        range.decrementLow();
                    } else {
                        range.decrementHigh();
                    }
                }
                return;
            }

            if (setting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) setting;
                if (mouseButton == 0) {
                    number.increment();
                } else if (mouseButton == 1) {
                    number.decrement();
                }

                enforceNumberBounds(module);
                return;
            }

            if (setting instanceof DecimalSetting) {
                DecimalSetting decimal = (DecimalSetting) setting;
                if (mouseButton == 0) {
                    decimal.increment();
                } else if (mouseButton == 1) {
                    decimal.decrement();
                }
                return;
            }

            if (setting instanceof EnumSetting && (mouseButton == 0 || mouseButton == 1)) {
                EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
                if (mouseButton == 0) {
                    enumSetting.cycleForward();
                } else {
                    enumSetting.cycleBackward();
                }
                refreshClickGuiStyleIfNeeded(module, setting);
            }
        }

        private void refreshClickGuiStyleIfNeeded(Module module, Setting setting) {
            if (module != ClickGuiModule.getInstance() || !"Style".equals(setting.getName())) {
                return;
            }

            LionClient client = LionClient.getInstance();
            if (client != null) {
                client.refreshClickGuiStyle();
            }
        }

        private void enforceNumberBounds(Module module) {
            NumberSetting min = null;
            NumberSetting max = null;
            for (Setting setting : module.getSettings()) {
                if (!(setting instanceof NumberSetting)) {
                    continue;
                }

                if ("Min CPS".equals(setting.getName())) {
                    min = (NumberSetting) setting;
                } else if ("Max CPS".equals(setting.getName())) {
                    max = (NumberSetting) setting;
                }
            }

            if (min != null && max != null && max.getValue() < min.getValue()) {
                max.setManualValue(min.getValue());
            }
        }

        private void handleKeybindClick(Module module, int mouseButton) {
            if (mouseButton == 0) {
                bindingModule = module;
                return;
            }

            if (mouseButton == 1 && module.canBeUnbound()) {
                module.setKeyCode(Keyboard.KEY_NONE);
                bindingModule = null;
            }
        }

        private boolean handleKeyTyped(int keyCode) {
            if (bindingModule == null) {
                return false;
            }

            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                if (bindingModule.canBeUnbound()) {
                    bindingModule.setKeyCode(Keyboard.KEY_NONE);
                }
            } else {
                bindingModule.setKeyCode(keyCode);
            }

            bindingModule = null;
            return true;
        }

        private String getKeybindText(Module module) {
            if (module.getKeyCode() == Keyboard.KEY_NONE) {
                return "NONE";
            }

            String name = Keyboard.getKeyName(module.getKeyCode());
            return name == null ? "UNKNOWN" : name.toUpperCase();
        }

        private void updateHoveredModule(Module module) {
            long now = System.currentTimeMillis();
            if (module != hoveredModule) {
                hoveredModule = module;
                hoveredSince = module == null ? 0L : now;
            }
        }

        private boolean shouldShowDescription(Module module) {
            return module != null
                && module.getDescription() != null
                && !module.getDescription().isEmpty()
                && System.currentTimeMillis() - hoveredSince >= DESCRIPTION_HOVER_DELAY_MS;
        }

        private void drawModuleDescription(Module module, int rowY, net.minecraft.client.gui.FontRenderer fontRenderer, int screenWidth) {
            String description = module.getDescription();
            int padding = 4;
            int tooltipWidth = fontRenderer.getStringWidth(description) + (padding * 2);
            int tooltipX = x + (WIDTH - tooltipWidth) / 2;
            int tooltipY = rowY - ROW_HEIGHT - 4;

            if (tooltipX < 4) {
                tooltipX = 4;
            }

            int maxX = screenWidth - tooltipWidth - 4;
            if (tooltipX > maxX) {
                tooltipX = maxX;
            }

            if (tooltipY < 4) {
                tooltipY = rowY + 2;
            }

            Gui.drawRect(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + ROW_HEIGHT, 0xE0101018);
            Gui.drawRect(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, 0xFF000000 | ClickGuiModule.getAccentColor());
            fontRenderer.drawStringWithShadow(description, tooltipX + padding, tooltipY + 3, 0xFFFFFFFF);
        }

        private void updateDragValue(int mouseX) {
            if (draggingSetting == null) {
                return;
            }
            float pct = (float) (mouseX - (x + 4)) / (WIDTH - 8);
            pct = Math.max(0.0F, Math.min(1.0F, pct));
            if (draggingSetting instanceof NumberSetting) {
                NumberSetting number = (NumberSetting) draggingSetting;
                int val = Math.round((float) (number.getMin() + pct * (number.getMax() - number.getMin())));
                number.setManualValue(val);
                enforceNumberBounds(expandedModule);
            } else if (draggingSetting instanceof DecimalSetting) {
                DecimalSetting decimal = (DecimalSetting) draggingSetting;
                double val = decimal.getMin() + pct * (decimal.getMax() - decimal.getMin());
                double step = decimal.getStep();
                val = Math.round(val / step) * step;
                val = Math.max(decimal.getMin(), Math.min(decimal.getMax(), val));
                decimal.setManualValue(val);
            } else if (draggingSetting instanceof IntRangeSetting) {
                IntRangeSetting range = (IntRangeSetting) draggingSetting;
                int val = Math.round((float) (range.getMin() + pct * (range.getMax() - range.getMin())));
                if (draggingRangeHigh) {
                    range.setHigh(Math.max(range.getLow(), val), false);
                } else {
                    range.setLow(Math.min(range.getHigh(), val), false);
                }
            }
        }

        private boolean isHovered(int mouseX, int mouseY, int rectX, int rectY, int width, int height) {
            return mouseX >= rectX && mouseX <= rectX + width && mouseY >= rectY && mouseY <= rectY + height;
        }
    }
}
