package com.lionclient.gui;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
import com.lionclient.feature.module.impl.ClickGuiModule;
import com.lionclient.feature.setting.ActionSetting;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.FloatSetting;
import com.lionclient.feature.setting.IntRangeSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.feature.setting.Setting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Modern animated Click GUI — state, input, and lifecycle.
 * All rendering is delegated to {@link ModernClickGuiRenderer}.
 */
public final class ModernClickGuiScreen extends GuiScreen {

    // ── Package-private state (read/written by renderer) ─────────────────
    final ModuleManager moduleManager;
    final Random random = new Random();
    final List<ModernClickGuiSnowflake> snowflakes = new ArrayList<ModernClickGuiSnowflake>();
    final EnumMap<Category, Float> categoryAnimations = new EnumMap<Category, Float>(Category.class);
    final Map<Module, Float> moduleAnimations = new HashMap<Module, Float>();
    final Map<Module, Float> moduleToggleAnimations = new HashMap<Module, Float>();
    final Map<Setting, Float> booleanAnimations = new IdentityHashMap<Setting, Float>();
    final Map<Setting, Float> sliderAnimations = new IdentityHashMap<Setting, Float>();

    Category selectedCategory;
    Category previousCategory;
    Module selectedModule;
    Module previousModule;
    Module bindingModule;
    EnumSetting<?> expandedEnumSetting;
    ColorSetting expandedColorSetting;
    ColorPickerDragMode colorDragMode = ColorPickerDragMode.NONE;
    Setting draggingSetting;
    boolean draggingRangeHigh;
    Setting editingValueSetting;
    GuiTextField valueEditor;

    Integer windowX;
    Integer windowY;
    boolean draggingWindow;
    int dragOffsetX;
    int dragOffsetY;

    float openProgress;
    float moduleScroll;
    float moduleScrollTarget;
    float settingsScroll;
    float settingsScrollTarget;
    float enumPopupScroll;
    float previousModuleScroll;
    float previousSettingsScroll;

    float categoryTransitionProgress = 1.0F;
    int categoryTransitionDirection = 1;
    long lastFrameTime;

    // ── Renderer ─────────────────────────────────────────────────────────
    private final ModernClickGuiRenderer renderer;

    // ── Exposed for renderer (fontRendererObj is protected in GuiScreen) ──
    net.minecraft.client.gui.FontRenderer fontRenderer() { return this.fontRendererObj; }

    // ── Layout constants (needed for createLayout) ───────────────────────
    private static final int PANEL_MARGIN = 46;
    private static final int HEADER_HEIGHT = 54;
    private static final int INNER_PADDING = 14;
    private static final int SETTINGS_HEADER_HEIGHT = 58;
    private static final int MODULE_ROW_HEIGHT = 27;
    private static final int MODULE_ROW_GAP = 5;
    private static final int SETTING_ROW_GAP = 6;
    private static final int CATEGORY_MODULE_SLIDE_DISTANCE = 24;
    private static final int CATEGORY_SETTINGS_SLIDE_DISTANCE = 34;
    private static final int CONTROL_WIDTH = 240;
    private static final int CONTROL_HEIGHT = 28;
    private static final int CONTROL_PADDING = 12;
    private static final int VALUE_INPUT_WIDTH = 82;
    private static final int VALUE_INPUT_HEIGHT = 18;
    private static final int ENUM_OPTION_HEIGHT = 20;
    private static final int BIND_BUTTON_WIDTH = 100;
    private static final int SLIDER_TRACK_HEIGHT = 6;
    private static final int CHECKBOX_SIZE = 14;
    private static final int SWITCH_WIDTH = 28;
    private static final int SWITCH_HEIGHT = 16;
    private static final int DEFAULT_SNOWFLAKE_COUNT = 110;
    private static final float WINDOW_RADIUS = 11.0F;
    private static final float SMALL_LABEL_SCALE = 0.75F;

    // ── Construction ─────────────────────────────────────────────────────
    public ModernClickGuiScreen(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        this.selectedCategory = Category.COMBAT;
        this.renderer = new ModernClickGuiRenderer(this);
        for (Category category : Category.values()) {
            categoryAnimations.put(category, Float.valueOf(0.0F));
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────
    @Override
    public void initGui() {
        openProgress = 0.0F;
        moduleScroll = 0.0F;
        moduleScrollTarget = 0.0F;
        settingsScroll = 0.0F;
        settingsScrollTarget = 0.0F;
        enumPopupScroll = 0.0F;
        bindingModule = null;
        expandedEnumSetting = null;
        draggingSetting = null;
        clearValueEditor();
        draggingWindow = false;
        Keyboard.enableRepeatEvents(true);
        clearCategoryTransition();
        lastFrameTime = 0L;
        ensureSelection();
        if (ClickGuiModule.areSnowflakesEnabled()) {
            renderer.initializeSnowflakes();
        } else {
            snowflakes.clear();
        }
    }

    @Override
    public void onGuiClosed() {
        commitValueEditor(true);
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
        draggingWindow = false;
        draggingSetting = null;
        draggingRangeHigh = false;
        bindingModule = null;
        expandedEnumSetting = null;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (valueEditor != null) {
            valueEditor.updateCursorCounter();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ── Render (delegated) ───────────────────────────────────────────────
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        float delta = renderer.getDeltaSeconds();
        openProgress = animate(openProgress, 1.0F, delta * 8.0F);
        renderer.updateCategoryTransition(delta);

        boolean drawSnowflakes = ClickGuiModule.areSnowflakesEnabled();
        if (drawSnowflakes) {
            renderer.updateSnowflakes(delta);
        } else if (!snowflakes.isEmpty()) {
            snowflakes.clear();
        }

        if (draggingWindow) {
            setWindowPosition(mouseX - dragOffsetX, mouseY - dragOffsetY);
        }

        ModernClickGuiLayout layout = createLayout();
        int accent = ClickGuiModule.getModernAccentColor();

        renderer.drawBackdrop(layout);
        renderer.drawWindowShadow(layout);
        renderer.drawWindow(layout, accent);
        if (drawSnowflakes) {
            renderer.beginScissor(layout.windowBounds());
            renderer.drawSnowflakes(layout.windowBounds(), 0.55F);
            renderer.endScissor();
        }
        renderer.drawHeader(layout, mouseX, mouseY, accent);
        renderer.drawModulePane(layout, mouseX, mouseY, accent, delta);
        renderer.drawSettingsPane(layout, mouseX, mouseY, accent, delta);
        if (drawSnowflakes) {
            renderer.beginScissor(layout.windowBounds());
            renderer.drawSnowflakes(layout.windowBounds(), 0.16F);
            renderer.endScissor();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ── Mouse input ──────────────────────────────────────────────────────
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        ModernClickGuiLayout layout = createLayout();
        if (renderer.handleValueEditorMouseClick(layout, mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (!layout.windowBounds().contains(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0 && getDragBounds(layout).contains(mouseX, mouseY)) {
            draggingWindow = true;
            dragOffsetX = mouseX - layout.windowX();
            dragOffsetY = mouseY - layout.windowY();
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (handleCategoryClick(layout, mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (isCategoryTransitionActive()) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (handleModuleClick(layout, mouseX, mouseY, mouseButton)
            || handleHeaderToggleClick(layout, mouseX, mouseY, mouseButton)
            || handleSettingClick(layout, mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        draggingWindow = false;
        if (draggingSetting instanceof NumberSetting) {
            ((NumberSetting) draggingSetting).setValue(((NumberSetting) draggingSetting).getValue(), true);
            normalizeNumberRanges(selectedModule);
        } else if (draggingSetting instanceof DecimalSetting) {
            ((DecimalSetting) draggingSetting).setValue(((DecimalSetting) draggingSetting).getValue(), true);
        } else if (draggingSetting instanceof IntRangeSetting) {
            ((IntRangeSetting) draggingSetting).saveValue();
        }
        draggingSetting = null;
        draggingRangeHigh = false;
        colorDragMode = ColorPickerDragMode.NONE;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton == 0 && expandedColorSetting != null && colorDragMode != ColorPickerDragMode.NONE) {
            ModernClickGuiLayout layout = createLayout();
            int rowY = layout.settingsContentTop() - Math.round(settingsScroll);
            for (Setting setting : getVisibleSettings(selectedModule)) {
                int rowHeight = getSettingHeight(setting);
                if (setting == expandedColorSetting) {
                    ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                        layout.settingsPaneX() + 10, rowY,
                        layout.settingsPaneRight() - 10, rowY + rowHeight);
                    ModernClickGuiBounds chipBounds = renderer.getColorChipBounds(rowBounds);
                    ModernClickGuiBounds popupBounds = renderer.getColorPopupBounds(layout, chipBounds);
                    renderer.applyColorPickerDrag(popupBounds, mouseX, mouseY);
                    return;
                }
                rowY += rowHeight + SETTING_ROW_GAP;
            }
        }
    }

    // ── Keyboard input ───────────────────────────────────────────────────
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (bindingModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                if (bindingModule.canBeUnbound()) {
                    bindingModule.setKeyCode(Keyboard.KEY_NONE);
                }
            } else {
                bindingModule.setKeyCode(keyCode);
            }
            bindingModule = null;
            return;
        }

        if (editingValueSetting != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                clearValueEditor();
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitValueEditor(true);
                return;
            }
        }

        if (handleCloseKeybind(keyCode)) return;

        if (editingValueSetting != null && valueEditor != null) {
            valueEditor.textboxKeyTyped(typedChar, keyCode);
            valueEditor.setText(sanitizeValueText(valueEditor.getText(), editingValueSetting instanceof DecimalSetting));
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || isCategoryTransitionActive()) return;

        ModernClickGuiLayout layout = createLayout();
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        float amount = wheel > 0 ? -34.0F : 34.0F;

        // An open enum dropdown (e.g. the shader list) gets scroll priority so its
        // off-screen options are reachable.
        if (expandedEnumSetting != null) {
            ModernClickGuiBounds popup = renderer.getActiveEnumPopupBounds(layout);
            if (popup != null && popup.contains(mouseX, mouseY)) {
                enumPopupScroll += amount;
                return;
            }
        }

        if (layout.moduleScrollBounds().contains(mouseX, mouseY)) {
            moduleScrollTarget += amount;
        } else if (layout.settingsScrollBounds().contains(mouseX, mouseY)) {
            settingsScrollTarget += amount;
        }
    }

    // ── Selection logic ──────────────────────────────────────────────────
    private boolean handleCategoryClick(ModernClickGuiLayout layout, int mouseX, int mouseY) {
        int tabWidth = 74;
        int gap = 6;
        int totalWidth = (Category.values().length * tabWidth) + ((Category.values().length - 1) * gap);
        int startX = layout.windowX() + (layout.getWidth() - totalWidth) / 2;
        for (int i = 0; i < Category.values().length; i++) {
            Category category = Category.values()[i];
            int left = startX + i * (tabWidth + gap);
            ModernClickGuiBounds bounds = new ModernClickGuiBounds(left, layout.windowY() + 12, left + tabWidth, layout.windowY() + 31);
            if (bounds.contains(mouseX, mouseY)) {
                if (category != selectedCategory) {
                    beginCategoryTransition(selectedCategory, selectedModule, moduleScroll, settingsScroll, category);
                    selectedCategory = category;
                    selectedModule = null;
                    settingsScroll = 0.0F;
                    settingsScrollTarget = 0.0F;
                    expandedEnumSetting = null;
                    expandedColorSetting = null;
                    ensureSelection();
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleModuleClick(ModernClickGuiLayout layout, int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return false;
        List<Module> modules = moduleManager.getModules(selectedCategory);
        int rowY = layout.moduleContentTop() - Math.round(moduleScroll);
        for (Module module : modules) {
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.modulePaneX() + 6, rowY,
                layout.modulePaneX() + layout.modulePaneWidth() - 6, rowY + MODULE_ROW_HEIGHT);
            if (rowBounds.contains(mouseX, mouseY) && layout.moduleScrollBounds().contains(mouseX, mouseY)) {
                selectedModule = module;
                settingsScroll = 0.0F;
                settingsScrollTarget = 0.0F;
                draggingSetting = null;
                bindingModule = null;
                expandedEnumSetting = null;
                return true;
            }
            rowY += MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
        }
        return false;
    }

    private boolean handleHeaderToggleClick(ModernClickGuiLayout layout, int mouseX, int mouseY, int mouseButton) {
        if (selectedModule == null || mouseButton != 0) return false;
        ModernClickGuiBounds toggleBounds = new ModernClickGuiBounds(
            layout.settingsPaneRight() - 102, layout.settingsPaneY() + 34,
            layout.settingsPaneRight() - 14, layout.settingsPaneY() + 58);
        if (!toggleBounds.contains(mouseX, mouseY)) return false;
        expandedEnumSetting = null;
        selectedModule.toggle();
        return true;
    }

    private boolean handleSettingClick(ModernClickGuiLayout layout, int mouseX, int mouseY, int mouseButton) {
        if (selectedModule == null || !layout.settingsScrollBounds().contains(mouseX, mouseY)) {
            expandedEnumSetting = null;
            expandedColorSetting = null;
            return false;
        }
        List<Setting> visibleSettings = getVisibleSettings(selectedModule);
        if (renderer.handleExpandedEnumClick(layout, visibleSettings, mouseX, mouseY, mouseButton)) return true;
        if (renderer.handleExpandedColorClick(layout, visibleSettings, mouseX, mouseY, mouseButton)) return true;

        int rowY = layout.settingsContentTop() - Math.round(settingsScroll);
        for (Setting setting : visibleSettings) {
            int rowHeight = getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY,
                layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (rowBounds.contains(mouseX, mouseY)) {
                if (setting instanceof BooleanSetting && mouseButton == 0) {
                    expandedEnumSetting = null;
                    expandedColorSetting = null;
                    ((BooleanSetting) setting).toggle();
                    return true;
                }
                if (setting instanceof EnumSetting && mouseButton == 0) {
                    expandedColorSetting = null;
                    expandedEnumSetting = expandedEnumSetting == setting ? null : (EnumSetting<?>) setting;
                    enumPopupScroll = 0.0F;
                    return true;
                }
                if (setting instanceof ColorSetting && mouseButton == 0) {
                    expandedEnumSetting = null;
                    expandedColorSetting = expandedColorSetting == setting ? null : (ColorSetting) setting;
                    return true;
                }
                if (setting instanceof ActionSetting && mouseButton == 0) {
                    expandedEnumSetting = null;
                    expandedColorSetting = null;
                    ((ActionSetting) setting).run();
                    ensureSelection();
                    return true;
                }
                if (setting instanceof IntRangeSetting && mouseButton == 0) {
                    ModernClickGuiBounds sliderBounds = renderer.getSliderBounds(rowBounds);
                    if (sliderBounds.contains(mouseX, mouseY) || rowBounds.contains(mouseX, mouseY)) {
                        expandedEnumSetting = null;
                        expandedColorSetting = null;
                        clearValueEditor();
                        draggingSetting = setting;
                        draggingRangeHigh = renderer.chooseRangeSliderHandle((IntRangeSetting) setting, mouseX, sliderBounds);
                        renderer.applyRangeSliderValue((IntRangeSetting) setting, mouseX, sliderBounds, draggingRangeHigh, false);
                        return true;
                    }
                }
                if ((setting instanceof NumberSetting || setting instanceof DecimalSetting || setting instanceof FloatSetting) && mouseButton == 0) {
                    ModernClickGuiBounds valueBounds = renderer.getValueInputBounds(rowBounds);
                    if (valueBounds.contains(mouseX, mouseY)) {
                        expandedEnumSetting = null;
                        expandedColorSetting = null;
                        clearValueEditor();
                        openValueEditor(setting, valueBounds);
                        return true;
                    }
                    ModernClickGuiBounds sliderBounds = renderer.getSliderBounds(rowBounds);
                    if (sliderBounds.contains(mouseX, mouseY)) {
                        expandedEnumSetting = null;
                        expandedColorSetting = null;
                        clearValueEditor();
                        draggingSetting = setting;
                        renderer.applySliderValue(setting, mouseX, sliderBounds, false);
                        return true;
                    }
                }
                if (mouseButton == 1 && setting instanceof NumberSetting) {
                    ((NumberSetting) setting).setValue(((NumberSetting) setting).getMin());
                    normalizeNumberRanges(selectedModule);
                    return true;
                }
                if (mouseButton == 1 && (setting instanceof DecimalSetting || setting instanceof FloatSetting)) {
                    if (setting instanceof DecimalSetting) {
                        ((DecimalSetting) setting).setValue(((DecimalSetting) setting).getMin());
                    } else {
                        ((FloatSetting) setting).setValue(((FloatSetting) setting).getMin());
                    }
                    return true;
                }
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        expandedEnumSetting = null;
        expandedColorSetting = null;
        return false;
    }

    private boolean handleCloseKeybind(int keyCode) {
        if (keyCode == ClickGuiModule.getInstance().getKeyCode()) {
            this.mc.displayGuiScreen(null);
            if (this.mc.currentScreen == null) {
                this.mc.setIngameFocus();
            }
            return true;
        }
        return false;
    }

    // ── Value editor ─────────────────────────────────────────────────────
    private void openValueEditor(Setting setting, ModernClickGuiBounds valueBounds) {
        editingValueSetting = setting;
        valueEditor = new GuiTextField(0, this.fontRendererObj,
            valueBounds.left() + 5, valueBounds.top() + 5,
            VALUE_INPUT_WIDTH - 10, VALUE_INPUT_HEIGHT - 8);
        valueEditor.setEnableBackgroundDrawing(false);
        valueEditor.setCanLoseFocus(false);
        valueEditor.setMaxStringLength(24);
        valueEditor.setTextColor(0xFFF1F4F8);
        valueEditor.setDisabledTextColour(0xFFF1F4F8);
        valueEditor.setFocused(true);
        valueEditor.setText(setting.getValueText());
        valueEditor.setCursorPositionEnd();
    }

    private void clearValueEditor() {
        editingValueSetting = null;
        valueEditor = null;
    }

    void commitValueEditor(boolean save) {
        if (editingValueSetting == null || valueEditor == null) return;
        String text = valueEditor.getText().trim();
        if (!text.isEmpty() && !"-".equals(text) && !".".equals(text) && !"-.".equals(text)) {
            try {
                if (editingValueSetting instanceof NumberSetting) {
                    ((NumberSetting) editingValueSetting).setManualValue(Integer.parseInt(text), save);
                    normalizeNumberRanges(selectedModule);
                } else if (editingValueSetting instanceof DecimalSetting) {
                    ((DecimalSetting) editingValueSetting).setManualValue(Double.parseDouble(text), save);
                }
            } catch (NumberFormatException ignored) { }
        }
        clearValueEditor();
    }

    private String sanitizeValueText(String input, boolean allowDecimal) {
        StringBuilder builder = new StringBuilder();
        boolean hasDecimal = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isDigit(c)) { builder.append(c); continue; }
            if (c == '-' && builder.length() == 0) { builder.append(c); continue; }
            if (allowDecimal && c == '.' && !hasDecimal) {
                if (builder.length() == 0 || (builder.length() == 1 && builder.charAt(0) == '-')) builder.append('0');
                builder.append('.');
                hasDecimal = true;
            }
        }
        return builder.toString();
    }

    ModernClickGuiBounds findValueInputBounds(ModernClickGuiLayout layout, Setting setting) {
        if (selectedModule == null || setting == null) return null;
        int rowY = layout.settingsContentTop() - Math.round(settingsScroll);
        List<Setting> visibleSettings = getVisibleSettings(selectedModule);
        for (Setting visibleSetting : visibleSettings) {
            int rowHeight = getSettingHeight(visibleSetting);
            if (visibleSetting == setting && (visibleSetting instanceof NumberSetting || visibleSetting instanceof DecimalSetting)) {
                ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                    layout.settingsPaneX() + 10, rowY,
                    layout.settingsPaneRight() - 10, rowY + rowHeight);
                return renderer.getValueInputBounds(rowBounds);
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        return null;
    }

    // ── Settings helpers ─────────────────────────────────────────────────
    List<Setting> getVisibleSettings(Module module) {
        List<Setting> visible = new ArrayList<Setting>();
        for (Setting setting : module.getSettings()) {
            if (setting.isVisible()) visible.add(setting);
        }
        return visible;
    }

    int getSettingHeight(Setting setting) {
        if (setting instanceof EnumSetting || setting instanceof ActionSetting) return 44;
        if (setting instanceof IntRangeSetting || setting instanceof NumberSetting || setting instanceof DecimalSetting || setting instanceof FloatSetting) return 44;
        if (setting instanceof ColorSetting) return 44;
        return 34;
    }

    void normalizeNumberRanges(Module module) {
        if (module == null) return;
        NumberSetting min = null, max = null;
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof NumberSetting)) continue;
            if ("Min CPS".equals(setting.getName())) min = (NumberSetting) setting;
            else if ("Max CPS".equals(setting.getName())) max = (NumberSetting) setting;
        }
        if (min != null && max != null && max.getValue() < min.getValue()) {
            max.setManualValue(min.getValue(), false);
        }
    }

    // ── Selection state ──────────────────────────────────────────────────
    void ensureSelection() {
        if (selectedCategory == null || moduleManager.getModules(selectedCategory).isEmpty()) {
            selectedCategory = findFirstCategory();
        }
        List<Module> modules = moduleManager.getModules(selectedCategory);
        if (modules.isEmpty()) { selectedModule = null; return; }
        if (selectedModule == null || selectedModule.getCategory() != selectedCategory || !modules.contains(selectedModule)) {
            selectedModule = modules.get(0);
        }
    }

    private Category findFirstCategory() {
        for (Category category : Category.values()) {
            if (!moduleManager.getModules(category).isEmpty()) return category;
        }
        return Category.COMBAT;
    }

    String getKeybindText(Module module) {
        if (module.getKeyCode() == Keyboard.KEY_NONE) return "NONE";
        String name = Keyboard.getKeyName(module.getKeyCode());
        return name == null ? "UNKNOWN" : name.toUpperCase();
    }

    // ── Category transition ──────────────────────────────────────────────
    private void beginCategoryTransition(Category fromCategory, Module fromModule,
                                         float fromModuleScroll, float fromSettingsScroll, Category toCategory) {
        if (fromCategory == null || fromCategory == toCategory) { clearCategoryTransition(); return; }
        previousCategory = fromCategory;
        previousModule = fromModule;
        previousModuleScroll = fromModuleScroll;
        previousSettingsScroll = fromSettingsScroll;
        categoryTransitionProgress = 0.0F;
        int direction = Integer.compare(toCategory.ordinal(), fromCategory.ordinal());
        categoryTransitionDirection = direction == 0 ? 1 : direction;
    }

    void clearCategoryTransition() {
        previousCategory = null;
        previousModule = null;
        previousModuleScroll = 0.0F;
        previousSettingsScroll = 0.0F;
        categoryTransitionProgress = 1.0F;
        categoryTransitionDirection = 1;
    }

    boolean isCategoryTransitionActive() {
        return previousCategory != null && categoryTransitionProgress < 0.999F;
    }

    // ── Layout ───────────────────────────────────────────────────────────
    ModernClickGuiLayout createLayout() {
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        if (windowX == null || windowY == null) {
            windowX = Integer.valueOf((this.width - panelWidth) / 2);
            windowY = Integer.valueOf((this.height - panelHeight) / 2);
        }
        setWindowPosition(windowX.intValue(), windowY.intValue());
        int layoutWindowX = windowX.intValue();
        int baseWindowY = windowY.intValue();
        int layoutWindowY = baseWindowY + Math.round((1.0F - easeOut(openProgress)) * 18.0F);
        int windowRight = layoutWindowX + panelWidth;
        int windowBottom = layoutWindowY + panelHeight;

        int modulePaneX = layoutWindowX + INNER_PADDING;
        int modulePaneY = layoutWindowY + HEADER_HEIGHT + INNER_PADDING;
        int modulePaneWidth = 156;
        int modulePaneBottom = windowBottom - INNER_PADDING;

        int settingsPaneX = modulePaneX + modulePaneWidth + 18;
        int settingsPaneY = modulePaneY;
        int settingsPaneRight = windowRight - INNER_PADDING;
        int settingsPaneBottom = modulePaneBottom;

        return new ModernClickGuiLayout(
            layoutWindowX, layoutWindowY, windowRight, windowBottom,
            modulePaneX, modulePaneY, modulePaneWidth, modulePaneBottom,
            modulePaneY + 8, new ModernClickGuiBounds(modulePaneX + 2, modulePaneY + 6, modulePaneX + modulePaneWidth - 2, modulePaneBottom - 6),
            settingsPaneX, settingsPaneY, settingsPaneRight, settingsPaneBottom,
            settingsPaneY + SETTINGS_HEADER_HEIGHT + 8, new ModernClickGuiBounds(settingsPaneX + 2, settingsPaneY + SETTINGS_HEADER_HEIGHT + 2, settingsPaneRight - 2, settingsPaneBottom - 6)
        );
    }

    private int getPanelWidth() { return Math.min(720, this.width - PANEL_MARGIN * 2); }
    private int getPanelHeight() { return Math.min(395, this.height - PANEL_MARGIN * 2); }

    private void setWindowPosition(int nextX, int nextY) {
        int minX = 8, minY = 8;
        int maxX = Math.max(minX, this.width - getPanelWidth() - 8);
        int maxY = Math.max(minY, this.height - getPanelHeight() - 8);
        windowX = Integer.valueOf(Math.max(minX, Math.min(maxX, nextX)));
        windowY = Integer.valueOf(Math.max(minY, Math.min(maxY, nextY)));
    }

    private ModernClickGuiBounds getDragBounds(ModernClickGuiLayout layout) {
        return new ModernClickGuiBounds(layout.windowX() + 8, layout.windowY() + 8, layout.windowX() + 124, layout.windowY() + 12);
    }

    // ── Static math helpers ──────────────────────────────────────────────
    private static float animate(float current, float target, float speed) {
        return current + ((target - current) * clamp(speed, 0.0F, 1.0F));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    // ── Color picker drag mode ───────────────────────────────────────────
    enum ColorPickerDragMode { NONE, HUE, SV }
}
