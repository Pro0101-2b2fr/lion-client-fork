package com.lionclient.gui;

import com.lionclient.LionClient;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * All drawing code for the modern click GUI.
 * Holds a reference back to the owning {@link ModernClickGuiScreen}
 * for mutable state (selection, animation, scroll, etc.).
 */
final class ModernClickGuiRenderer {

    // ── Visual constants ─────────────────────────────────────────────────
    private static final int PANEL_MARGIN = 46;
    private static final int HEADER_HEIGHT = 54;
    private static final int INNER_PADDING = 14;
    private static final int MODULE_ROW_HEIGHT = 27;
    private static final int MODULE_ROW_GAP = 5;
    private static final int SETTINGS_HEADER_HEIGHT = 58;
    static final int SETTING_ROW_GAP = 6;
    private static final int DEFAULT_SNOWFLAKE_COUNT = 110;
    private static final float WINDOW_RADIUS = 11.0F;
    private static final int ENUM_OPTION_HEIGHT = 20;
    private static final int CONTROL_WIDTH = 240;
    private static final int CONTROL_HEIGHT = 28;
    private static final int CONTROL_PADDING = 12;
    private static final int VALUE_INPUT_WIDTH = 82;
    private static final int VALUE_INPUT_HEIGHT = 18;
    private static final int SLIDER_TRACK_HEIGHT = 6;
    private static final int CHECKBOX_SIZE = 14;
    private static final int SWITCH_WIDTH = 28;
    private static final int SWITCH_HEIGHT = 16;
    private static final int BIND_BUTTON_WIDTH = 100;
    private static final int CATEGORY_MODULE_SLIDE_DISTANCE = 24;
    private static final int CATEGORY_SETTINGS_SLIDE_DISTANCE = 34;
    private static final float SMALL_LABEL_SCALE = 0.75F;
    private static final int SURFACE_WINDOW_BORDER = 0x394652;
    private static final int SURFACE_WINDOW = 0x1C252D;
    private static final int SURFACE_WINDOW_HEADER = 0x222C36;
    private static final int SURFACE_WINDOW_BODY = 0x171F27;
    private static final int SURFACE_PANEL = 0x1F2933;
    private static final int SURFACE_PANEL_OUTLINE = 0x43505D;
    private static final int SURFACE_ROW = 0x242E38;
    private static final int SURFACE_INPUT = 0x10171E;
    private static final int SURFACE_CHIP = 0x27313B;
    private static final int TEXT_PRIMARY = 0xFFF1F4F8;
    private static final int TEXT_SECONDARY = 0xFFACB7C2;
    private static final int TEXT_MUTED = 0xFF7E8995;
    private static final int TEXT_DISABLED = 0xFF76818D;
    private static final int SNOWFLAKE_COLOR = 0xEEF2F5;
    private static final int CONTROL_BACKGROUND = 0x1C1F27;
    private static final int CONTROL_BORDER = 0x2A2D35;
    private static final int CONTROL_BORDER_LIGHT = 0x3A3D45;
    private static final int SWITCH_OFF_TRACK = 0x2A2D35;
    private static final int SWITCH_OFF_THUMB = 0x8C9098;
    private static final int SMALL_LABEL_COLOR = 0xFF8A919B;
    private static final int CHECKBOX_LABEL_COLOR = 0xFFC8CDD4;

    // ── Owner ────────────────────────────────────────────────────────────
    private final ModernClickGuiScreen screen;

    ModernClickGuiRenderer(ModernClickGuiScreen screen) {
        this.screen = screen;
    }

    // ── Convenience accessors ─────────────────────────────────────────────
    private Minecraft mc() { return screen.mc; }
    private ModuleManager moduleManager() { return screen.moduleManager; }

    // ── Delta time ───────────────────────────────────────────────────────
    float getDeltaSeconds() {
        long now = System.currentTimeMillis();
        if (screen.lastFrameTime == 0L) {
            screen.lastFrameTime = now;
            return 0.016F;
        }
        float delta = (now - screen.lastFrameTime) / 1000.0F;
        screen.lastFrameTime = now;
        return clamp(delta, 0.0F, 0.05F);
    }

    // ── Public drawing entry points (called from screen) ─────────────────
    void drawBackdrop(ModernClickGuiLayout layout) {
        drawRoundedRect(layout.windowX() - 14, layout.windowY() - 14,
            layout.windowRight() + 14, layout.windowBottom() + 14,
            WINDOW_RADIUS + 6.0F, withAlpha(0x070B10, 18));
    }

    void drawWindowShadow(ModernClickGuiLayout layout) {
        drawRoundedRect(layout.windowX() - 6, layout.windowY() - 6,
            layout.windowRight() + 6, layout.windowBottom() + 6,
            WINDOW_RADIUS + 4.0F, withAlpha(0x040608, 34));
        drawRoundedRect(layout.windowX() - 2, layout.windowY() - 2,
            layout.windowRight() + 2, layout.windowBottom() + 2,
            WINDOW_RADIUS + 2.0F, withAlpha(0x0C1117, 42));
    }

    void drawWindow(ModernClickGuiLayout layout, int accent) {
        drawRoundedRect(layout.windowX(), layout.windowY(),
            layout.windowRight(), layout.windowBottom(),
            WINDOW_RADIUS + 1.0F, withAlpha(SURFACE_WINDOW_BORDER, 56));
        drawRoundedRect(layout.windowX() + 1, layout.windowY() + 1,
            layout.windowRight() - 1, layout.windowBottom() - 1,
            WINDOW_RADIUS, withAlpha(SURFACE_WINDOW, 238));
        drawRoundedRect(layout.windowX() + 1, layout.windowY() + 1,
            layout.windowRight() - 1, layout.windowY() + HEADER_HEIGHT + 10,
            WINDOW_RADIUS, withAlpha(SURFACE_WINDOW_HEADER, 244));
        drawRoundedRect(layout.windowX() + 1, layout.windowY() + HEADER_HEIGHT - 10,
            layout.windowRight() - 1, layout.windowBottom() - 1,
            WINDOW_RADIUS, withAlpha(SURFACE_WINDOW_BODY, 236));
        Gui.drawRect(layout.windowX(), layout.windowY() + HEADER_HEIGHT,
            layout.windowRight(), layout.windowY() + HEADER_HEIGHT + 1,
            withAlpha(accent, 190));
        Gui.drawRect(
            layout.modulePaneX() + layout.modulePaneWidth() + 14,
            layout.windowY() + HEADER_HEIGHT + 16,
            layout.modulePaneX() + layout.modulePaneWidth() + 15,
            layout.windowBottom() - 16,
            withAlpha(SURFACE_PANEL_OUTLINE, 120));
    }

    void drawHeader(ModernClickGuiLayout layout, int mouseX, int mouseY, int accent) {
        screen.fontRenderer().drawStringWithShadow("LionClient",
            layout.windowX() + 16, layout.windowY() + 15, TEXT_PRIMARY);
        drawScaledText(LionClient.VERSION,
            layout.windowX() + 16, layout.windowY() + 28,
            TEXT_SECONDARY, SMALL_LABEL_SCALE);

        int index = 0;
        for (Category category : Category.values()) {
            ModernClickGuiBounds bounds = getCategoryBounds(layout, index);
            boolean hovered = bounds.contains(mouseX, mouseY);
            float animation = getAnimation(screen.categoryAnimations, category,
                screen.selectedCategory == category ? 1.0F : hovered ? 0.45F : 0.0F, 10.0F);
            float fillMix = screen.selectedCategory == category ? 0.22F : animation * 0.14F;
            int fill = withAlpha(mixColor(SURFACE_PANEL, accent, fillMix),
                screen.selectedCategory == category ? 198 : 154 + (int)(48.0F * animation));
            drawRoundedRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 5.0F, fill);
            int outline = screen.selectedCategory == category
                ? withAlpha(accent, 220)
                : withAlpha(SURFACE_PANEL_OUTLINE, hovered ? 120 : 92);
            drawRoundedOutline(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 5.0F, outline);

            int labelColor = screen.selectedCategory == category
                ? accent
                : mixColor(TEXT_MUTED, TEXT_PRIMARY, hovered ? 0.35F : 0.08F);
            String label = category.getDisplayName();
            int textX = bounds.left() + (bounds.getWidth() - screen.fontRenderer().getStringWidth(label)) / 2;
            int textY = bounds.top() + (bounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2;
            screen.fontRenderer().drawString(label, textX, textY, labelColor);
            index++;
        }
    }

    void drawModulePane(ModernClickGuiLayout layout, int mouseX, int mouseY, int accent, float delta) {
        drawRoundedRect(layout.modulePaneX(), layout.modulePaneY(),
            layout.modulePaneX() + layout.modulePaneWidth(), layout.modulePaneBottom(),
            8.0F, withAlpha(SURFACE_PANEL, 210));
        drawRoundedOutline(layout.modulePaneX(), layout.modulePaneY(),
            layout.modulePaneX() + layout.modulePaneWidth(), layout.modulePaneBottom(),
            8.0F, withAlpha(SURFACE_PANEL_OUTLINE, 125));

        List<Module> modules = moduleManager().getModules(screen.selectedCategory);
        float maxScroll = Math.max(0.0F,
            modules.size() * (MODULE_ROW_HEIGHT + MODULE_ROW_GAP) - MODULE_ROW_GAP
                - layout.moduleScrollBounds().getHeight());
        screen.moduleScrollTarget = clamp(screen.moduleScrollTarget, 0.0F, maxScroll);
        screen.moduleScroll = animate(screen.moduleScroll, screen.moduleScrollTarget, delta * 14.0F);

        beginScissor(layout.moduleScrollBounds());
        if (screen.isCategoryTransitionActive()) {
            float transition = easeOut(screen.categoryTransitionProgress);
            float outgoingProgress = clamp(transition / 0.42F, 0.0F, 1.0F);
            float incomingProgress = clamp((transition - 0.18F) / 0.62F, 0.0F, 1.0F);
            int previousOffset = Math.round(-screen.categoryTransitionDirection * CATEGORY_MODULE_SLIDE_DISTANCE * outgoingProgress);
            int currentOffset = Math.round(screen.categoryTransitionDirection * CATEGORY_MODULE_SLIDE_DISTANCE * (1.0F - incomingProgress));
            drawModuleList(layout, screen.previousCategory, screen.previousModuleScroll, mouseX, mouseY, accent, previousOffset, false, 1.0F - outgoingProgress);
            drawModuleList(layout, screen.selectedCategory, screen.moduleScroll, mouseX, mouseY, accent, currentOffset, false, incomingProgress);
        } else {
            drawModuleList(layout, screen.selectedCategory, screen.moduleScroll, mouseX, mouseY, accent, 0, true, 1.0F);
        }
        endScissor();
    }

    void drawSettingsPane(ModernClickGuiLayout layout, int mouseX, int mouseY, int accent, float delta) {
        drawRoundedRect(layout.settingsPaneX(), layout.settingsPaneY(),
            layout.settingsPaneRight(), layout.settingsPaneBottom(),
            8.0F, withAlpha(SURFACE_PANEL, 210));
        drawRoundedOutline(layout.settingsPaneX(), layout.settingsPaneY(),
            layout.settingsPaneRight(), layout.settingsPaneBottom(),
            8.0F, withAlpha(SURFACE_PANEL_OUTLINE, 125));

        if (screen.selectedModule != null) {
            List<Setting> visibleSettings = screen.getVisibleSettings(screen.selectedModule);
            float contentHeight = 0.0F;
            for (Setting setting : visibleSettings) {
                contentHeight += screen.getSettingHeight(setting) + SETTING_ROW_GAP;
            }
            if (screen.selectedModule.showsKeybindSetting()) {
                contentHeight += 46 + SETTING_ROW_GAP;
            }
            float maxScroll = Math.max(0.0F, contentHeight - layout.settingsScrollBounds().getHeight());
            screen.settingsScrollTarget = clamp(screen.settingsScrollTarget, 0.0F, maxScroll);
            screen.settingsScroll = animate(screen.settingsScroll, screen.settingsScrollTarget, delta * 14.0F);
        } else {
            screen.settingsScrollTarget = 0.0F;
            screen.settingsScroll = animate(screen.settingsScroll, 0.0F, delta * 14.0F);
        }

        if (screen.isCategoryTransitionActive()) {
            float transition = easeOut(screen.categoryTransitionProgress);
            float outgoingProgress = clamp(transition / 0.42F, 0.0F, 1.0F);
            float incomingProgress = clamp((transition - 0.18F) / 0.62F, 0.0F, 1.0F);
            int previousOffset = Math.round(-screen.categoryTransitionDirection * CATEGORY_SETTINGS_SLIDE_DISTANCE * outgoingProgress);
            int currentOffset = Math.round(screen.categoryTransitionDirection * CATEGORY_SETTINGS_SLIDE_DISTANCE * (1.0F - incomingProgress));
            drawSettingsPaneContent(layout, screen.previousModule, mouseX, mouseY, accent, screen.previousSettingsScroll, previousOffset, false, 1.0F - outgoingProgress);
            drawSettingsPaneContent(layout, screen.selectedModule, mouseX, mouseY, accent, screen.settingsScroll, currentOffset, false, incomingProgress);
            return;
        }
        drawSettingsPaneContent(layout, screen.selectedModule, mouseX, mouseY, accent, screen.settingsScroll, 0, true, 1.0F);
    }

    // ── Module list ──────────────────────────────────────────────────────
    private void drawModuleList(ModernClickGuiLayout layout, Category category, float scroll,
                                int mouseX, int mouseY, int accent, int xOffset,
                                boolean interactive, float alphaScale) {
        if (category == null || alphaScale <= 0.01F) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(xOffset, 0.0F, 0.0F);

        List<Module> modules = moduleManager().getModules(category);
        if (modules.isEmpty()) {
            screen.drawCenteredString(screen.fontRenderer(), "No modules",
                layout.modulePaneX() + (layout.modulePaneWidth() / 2),
                layout.modulePaneY() + 48, scaleAlpha(TEXT_SECONDARY, alphaScale));
            GlStateManager.popMatrix();
            return;
        }

        int rowY = layout.moduleContentTop() - Math.round(scroll);
        for (Module module : modules) {
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.modulePaneX() + 6, rowY,
                layout.modulePaneX() + layout.modulePaneWidth() - 6,
                rowY + MODULE_ROW_HEIGHT);
            if (rowBounds.bottom() >= layout.moduleScrollBounds().top()
                && rowBounds.top() <= layout.moduleScrollBounds().bottom()) {
                boolean hovered = interactive && rowBounds.contains(mouseX, mouseY)
                    && layout.moduleScrollBounds().contains(mouseX, mouseY);
                float selectionAnimation = getAnimation(screen.moduleAnimations, module, hovered ? 0.45F : 0.0F, 13.0F);
                float toggleAnimation = getAnimation(screen.moduleToggleAnimations, module, module.isEnabled() ? 1.0F : 0.0F, 11.0F);
                int baseRowColor = module.isEnabled()
                    ? mixColor(SURFACE_ROW, accent, 0.22F + (toggleAnimation * 0.26F))
                    : mixColor(SURFACE_ROW, SURFACE_PANEL_OUTLINE, hovered ? 0.30F : 0.08F);
                int rowColor = withAlpha(baseRowColor,
                    module.isEnabled() ? 205 + (int)(30.0F * selectionAnimation) : 172 + (int)(35.0F * selectionAnimation));
                drawRoundedRect(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(), 6.0F, scaleAlpha(rowColor, alphaScale));
                int outlineColor = module.isEnabled()
                    ? withAlpha(accent, 105 + (int)(60.0F * toggleAnimation))
                    : withAlpha(SURFACE_PANEL_OUTLINE, hovered ? 140 : 96);
                drawRoundedOutline(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(), 6.0F, scaleAlpha(outlineColor, alphaScale));
                int nameColor = module.isEnabled()
                    ? mixColor(accent, TEXT_PRIMARY, 0.16F + (selectionAnimation * 0.18F))
                    : mixColor(TEXT_DISABLED, TEXT_PRIMARY, selectionAnimation * 0.18F);
                String moduleName = module.getName();
                int textX = rowBounds.left() + (rowBounds.getWidth() - screen.fontRenderer().getStringWidth(moduleName)) / 2;
                int textY = rowBounds.top() + (rowBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2;
                screen.fontRenderer().drawString(moduleName, textX, textY, scaleAlpha(nameColor, alphaScale));
            }
            rowY += MODULE_ROW_HEIGHT + MODULE_ROW_GAP;
        }
        GlStateManager.popMatrix();
    }

    // ── Settings pane content ────────────────────────────────────────────
    private void drawSettingsPaneContent(ModernClickGuiLayout layout, Module module,
                                         int mouseX, int mouseY, int accent, float scroll,
                                         int xOffset, boolean interactive, float alphaScale) {
        if (alphaScale <= 0.01F) return;

        ModernClickGuiBounds paneBounds = new ModernClickGuiBounds(
            layout.settingsPaneX() + 2, layout.settingsPaneY() + 2,
            layout.settingsPaneRight() - 2, layout.settingsPaneBottom() - 2);

        beginScissor(paneBounds);
        GlStateManager.pushMatrix();
        GlStateManager.translate(xOffset, 0.0F, 0.0F);
        if (module == null) {
            drawEmptySettingsState(layout, alphaScale);
            GlStateManager.popMatrix();
            endScissor();
            return;
        }

        screen.fontRenderer().drawStringWithShadow(module.getName(),
            layout.settingsPaneX() + 16, layout.settingsPaneY() + 10, scaleAlpha(TEXT_PRIMARY, alphaScale));
        screen.fontRenderer().drawString(module.getDescription(),
            layout.settingsPaneX() + 16, layout.settingsPaneY() + 22, scaleAlpha(TEXT_SECONDARY, alphaScale));
        float headerToggleProgress = getAnimation(screen.moduleToggleAnimations, module, module.isEnabled() ? 1.0F : 0.0F, 14.0F);
        drawHeaderToggle(getHeaderToggleBounds(layout), module.isEnabled(), headerToggleProgress, accent, alphaScale);
        GlStateManager.popMatrix();
        endScissor();

        List<Setting> visibleSettings = screen.getVisibleSettings(module);
        beginScissor(layout.settingsScrollBounds());
        GlStateManager.pushMatrix();
        GlStateManager.translate(xOffset, 0.0F, 0.0F);
        int drawMouseX = interactive ? mouseX : Integer.MIN_VALUE;
        int drawMouseY = interactive ? mouseY : Integer.MIN_VALUE;
        int rowY = layout.settingsContentTop() - Math.round(scroll);
        for (Setting setting : visibleSettings) {
            int rowHeight = screen.getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY,
                layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (rowBounds.bottom() >= layout.settingsScrollBounds().top()
                && rowBounds.top() <= layout.settingsScrollBounds().bottom()) {
                drawSettingCard(rowBounds, setting, drawMouseX, drawMouseY, accent, alphaScale);
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        if (module.showsKeybindSetting()) {
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY,
                layout.settingsPaneRight() - 10, rowY + 46);
            if (rowBounds.bottom() >= layout.settingsScrollBounds().top()
                && rowBounds.top() <= layout.settingsScrollBounds().bottom()) {
                drawKeybindCard(rowBounds, module, accent, alphaScale);
            }
        }
        if (interactive) {
            drawExpandedEnumPopup(layout, visibleSettings, mouseX, mouseY, accent);
            drawExpandedColorPopup(layout, visibleSettings, accent);
        }
        GlStateManager.popMatrix();
        endScissor();
    }

    private void drawEmptySettingsState(ModernClickGuiLayout layout, float alphaScale) {
        screen.fontRenderer().drawStringWithShadow("Select a module",
            layout.settingsPaneX() + 16, layout.settingsPaneY() + 18, scaleAlpha(TEXT_PRIMARY, alphaScale));
        screen.fontRenderer().drawString(
            "Pick something on the left to edit its settings.",
            layout.settingsPaneX() + 16, layout.settingsPaneY() + 32, scaleAlpha(TEXT_SECONDARY, alphaScale));
    }

    // ── Setting card ─────────────────────────────────────────────────────
    private void drawSettingCard(ModernClickGuiBounds rowBounds, Setting setting,
                                 int mouseX, int mouseY, int accent, float alphaScale) {
        boolean hovered = rowBounds.contains(mouseX, mouseY);
        drawRoundedRect(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(),
            6.0F, scaleAlpha(
                hovered ? withAlpha(mixColor(SURFACE_ROW, SURFACE_PANEL_OUTLINE, 0.18F), 216)
                        : withAlpha(SURFACE_ROW, 198), alphaScale));
        drawRoundedOutline(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(),
            6.0F, scaleAlpha(
                hovered ? withAlpha(accent, 120) : withAlpha(SURFACE_PANEL_OUTLINE, 110), alphaScale));

        if (setting instanceof BooleanSetting) {
            float progress = getAnimation(screen.booleanAnimations, setting,
                ((BooleanSetting) setting).isEnabled() ? 1.0F : 0.0F, 14.0F);
            ModernClickGuiBounds checkboxBounds = getBooleanControlBounds(rowBounds);
            drawBooleanControl(checkboxBounds, progress, hovered, accent, alphaScale);
            screen.fontRenderer().drawString(setting.getName(),
                checkboxBounds.right() + 8,
                rowBounds.top() + (rowBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                scaleAlpha(CHECKBOX_LABEL_COLOR, alphaScale));
            return;
        }

        if (setting instanceof IntRangeSetting) {
            IntRangeSetting range = (IntRangeSetting) setting;
            ModernClickGuiBounds sliderBounds = getSliderBounds(rowBounds);
            ModernClickGuiBounds valueBounds = getValueInputBounds(rowBounds);
            if (screen.draggingSetting == setting) {
                applyRangeSliderValue(range, mouseX, sliderBounds, screen.draggingRangeHigh, false);
            }
            String valueText = setting.getValueText();
            boolean valueHovered = valueBounds.contains(mouseX, mouseY);
            screen.fontRenderer().drawString(setting.getName(),
                rowBounds.left() + CONTROL_PADDING, rowBounds.top() + 8,
                scaleAlpha(TEXT_PRIMARY, alphaScale));
            drawRoundedRect(valueBounds.left(), valueBounds.top(), valueBounds.right(), valueBounds.bottom(),
                4.0F, scaleAlpha(withAlpha(CONTROL_BACKGROUND, valueHovered ? 224 : 204), alphaScale));
            drawRoundedOutline(valueBounds.left(), valueBounds.top(), valueBounds.right(), valueBounds.bottom(),
                4.0F, scaleAlpha(withAlpha(valueHovered ? CONTROL_BORDER_LIGHT : CONTROL_BORDER, 255), alphaScale));
            screen.fontRenderer().drawString(valueText,
                valueBounds.right() - 6 - screen.fontRenderer().getStringWidth(valueText),
                valueBounds.top() + (valueBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                scaleAlpha(accent, alphaScale));
            drawRangeSlider(range, sliderBounds, accent, alphaScale);
            return;
        }

        if (setting instanceof NumberSetting || setting instanceof DecimalSetting || setting instanceof FloatSetting) {
            ModernClickGuiBounds sliderBounds = getSliderBounds(rowBounds);
            ModernClickGuiBounds valueBounds = getValueInputBounds(rowBounds);
            if (screen.draggingSetting == setting) {
                applySliderValue(setting, mouseX, sliderBounds, false);
            }
            float target = getSliderTarget(setting);
            float sliderProgress = getAnimation(screen.sliderAnimations, setting, target, 14.0F);
            String valueText = setting.getValueText();
            boolean editingValue = screen.editingValueSetting == setting && screen.valueEditor != null;
            boolean valueHovered = valueBounds.contains(mouseX, mouseY);
            screen.fontRenderer().drawString(setting.getName(),
                rowBounds.left() + CONTROL_PADDING, rowBounds.top() + 8,
                scaleAlpha(TEXT_PRIMARY, alphaScale));
            drawRoundedRect(valueBounds.left(), valueBounds.top(), valueBounds.right(), valueBounds.bottom(),
                4.0F, scaleAlpha(
                    editingValue ? withAlpha(SURFACE_INPUT, 236)
                                 : withAlpha(CONTROL_BACKGROUND, valueHovered ? 224 : 204), alphaScale));
            drawRoundedOutline(valueBounds.left(), valueBounds.top(), valueBounds.right(), valueBounds.bottom(),
                4.0F, scaleAlpha(
                    editingValue ? withAlpha(accent, 210)
                                 : withAlpha(valueHovered ? CONTROL_BORDER_LIGHT : CONTROL_BORDER, 255), alphaScale));
            if (editingValue) {
                syncValueEditorBounds(valueBounds);
                screen.valueEditor.drawTextBox();
            } else {
                screen.fontRenderer().drawString(valueText,
                    valueBounds.right() - 6 - screen.fontRenderer().getStringWidth(valueText),
                    valueBounds.top() + (valueBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                    scaleAlpha(accent, alphaScale));
            }
            Gui.drawRect(sliderBounds.left(), sliderBounds.top(), sliderBounds.right(), sliderBounds.bottom(),
                scaleAlpha(0xFF000000 | CONTROL_BORDER, alphaScale));
            Gui.drawRect(sliderBounds.left(), sliderBounds.top(),
                sliderBounds.left() + Math.round(sliderBounds.getWidth() * sliderProgress),
                sliderBounds.bottom(), scaleAlpha(withAlpha(accent, 205), alphaScale));
            return;
        }

        if (setting instanceof EnumSetting) {
            EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
            ModernClickGuiBounds chipBounds = getEnumChipBounds(rowBounds, enumSetting);
            drawScaledText(setting.getName(), rowBounds.left() + CONTROL_PADDING,
                rowBounds.top() + 6, scaleAlpha(SMALL_LABEL_COLOR, alphaScale), SMALL_LABEL_SCALE);
            Gui.drawRect(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
                scaleAlpha(0xFF000000 | CONTROL_BACKGROUND, alphaScale));
            drawRoundedOutline(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
                4.0F, scaleAlpha(
                    screen.expandedEnumSetting == setting ? withAlpha(accent, 180)
                                                          : 0xFF000000 | CONTROL_BORDER, alphaScale));
            screen.fontRenderer().drawString(enumSetting.getValueText(),
                chipBounds.left() + 8,
                chipBounds.top() + (chipBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                scaleAlpha(TEXT_PRIMARY, alphaScale));
            screen.fontRenderer().drawString("\u2261",
                chipBounds.right() - 12 - screen.fontRenderer().getStringWidth("\u2261"),
                chipBounds.top() + (chipBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                TEXT_SECONDARY);
            return;
        }

        if (setting instanceof ColorSetting) {
            ColorSetting colorSetting = (ColorSetting) setting;
            ModernClickGuiBounds chipBounds = getColorChipBounds(rowBounds);
            drawScaledText(setting.getName(), rowBounds.left() + CONTROL_PADDING,
                rowBounds.top() + 6, scaleAlpha(SMALL_LABEL_COLOR, alphaScale), SMALL_LABEL_SCALE);
            Gui.drawRect(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
                scaleAlpha(0xFF000000 | CONTROL_BACKGROUND, alphaScale));
            int swatch = chipBounds.left() + 6;
            int swatchSize = chipBounds.getHeight() - 8;
            int swatchTop = chipBounds.top() + 4;
            Gui.drawRect(swatch, swatchTop, swatch + swatchSize, swatchTop + swatchSize,
                0xFF000000 | (colorSetting.getRgb()));
            drawOutline(swatch, swatchTop, swatch + swatchSize, swatchTop + swatchSize,
                scaleAlpha(0xFF000000 | CONTROL_BORDER_LIGHT, alphaScale));
            drawRoundedOutline(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
                4.0F, scaleAlpha(
                    screen.expandedColorSetting == setting ? withAlpha(accent, 180)
                                                           : 0xFF000000 | CONTROL_BORDER, alphaScale));
            String label = colorSetting.getValueText();
            screen.fontRenderer().drawString(label,
                swatch + swatchSize + 8,
                chipBounds.top() + (chipBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                scaleAlpha(TEXT_PRIMARY, alphaScale));
            return;
        }

        if (setting instanceof ActionSetting) {
            String valueText = setting.getValueText();
            drawScaledText(setting.getName(), rowBounds.left() + CONTROL_PADDING,
                rowBounds.top() + 6, scaleAlpha(SMALL_LABEL_COLOR, alphaScale), SMALL_LABEL_SCALE);
            ModernClickGuiBounds chipBounds = getActionButtonBounds(rowBounds, valueText);
            Gui.drawRect(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
                scaleAlpha(0xFF000000 | CONTROL_BACKGROUND, alphaScale));
            drawRoundedOutline(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
                4.0F, scaleAlpha(0xFF000000 | CONTROL_BORDER_LIGHT, alphaScale));
            screen.fontRenderer().drawString(valueText,
                chipBounds.left() + (chipBounds.getWidth() - screen.fontRenderer().getStringWidth(valueText)) / 2,
                chipBounds.top() + (chipBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                scaleAlpha(TEXT_PRIMARY, alphaScale));
            return;
        }

        String valueText = setting.getValueText();
        screen.fontRenderer().drawString(setting.getName(),
            rowBounds.left() + CONTROL_PADDING, rowBounds.top() + 10,
            scaleAlpha(TEXT_PRIMARY, alphaScale));
        screen.fontRenderer().drawString(valueText,
            rowBounds.right() - CONTROL_PADDING - screen.fontRenderer().getStringWidth(valueText),
            rowBounds.top() + 10, scaleAlpha(accent, alphaScale));
    }

    // ── Keybind card ─────────────────────────────────────────────────────
    private void drawKeybindCard(ModernClickGuiBounds rowBounds, Module module, int accent, float alphaScale) {
        drawRoundedRect(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(),
            6.0F, scaleAlpha(withAlpha(SURFACE_ROW, 200), alphaScale));
        drawRoundedOutline(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(),
            6.0F, scaleAlpha(withAlpha(SURFACE_PANEL_OUTLINE, 110), alphaScale));
        drawScaledText("Keybind", rowBounds.left() + CONTROL_PADDING,
            rowBounds.top() + 6, scaleAlpha(SMALL_LABEL_COLOR, alphaScale), SMALL_LABEL_SCALE);
        String valueText = screen.bindingModule == module ? "Bind: ..." : "Bind: " + screen.getKeybindText(module);
        ModernClickGuiBounds chipBounds = getBindButtonBounds(rowBounds, valueText);
        Gui.drawRect(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
            scaleAlpha(0xFF000000 | CONTROL_BACKGROUND, alphaScale));
        drawRoundedOutline(chipBounds.left(), chipBounds.top(), chipBounds.right(), chipBounds.bottom(),
            4.0F, scaleAlpha(
                screen.bindingModule == module ? withAlpha(accent, 220)
                                               : 0xFF000000 | CONTROL_BORDER_LIGHT, alphaScale));
        screen.fontRenderer().drawString(valueText,
            chipBounds.left() + 8,
            chipBounds.top() + (chipBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
            scaleAlpha(TEXT_PRIMARY, alphaScale));
    }

    // ── Boolean toggle ───────────────────────────────────────────────────
    private void drawBooleanControl(ModernClickGuiBounds bounds, float progress, boolean hovered, int accent, float alphaScale) {
        int fill = 0xFF000000 | mixColor(CONTROL_BACKGROUND, accent, progress);
        int outline = 0xFF000000 | mixColor(hovered ? CONTROL_BORDER_LIGHT : CONTROL_BORDER, accent, progress * 0.72F);
        Gui.drawRect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), scaleAlpha(fill, alphaScale));
        drawOutline(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), scaleAlpha(outline, alphaScale));
    }

    // ── Header toggle ────────────────────────────────────────────────────
    private void drawHeaderToggle(ModernClickGuiBounds bounds, boolean enabled, float progress, int accent, float alphaScale) {
        String label = enabled ? "Enabled" : "Disabled";
        int trackTop = bounds.top() + (bounds.getHeight() - SWITCH_HEIGHT) / 2;
        int trackRight = bounds.left() + SWITCH_WIDTH;
        int trackColor = 0xFF000000 | mixColor(SWITCH_OFF_TRACK, accent, progress);
        Gui.drawRect(bounds.left(), trackTop, trackRight, trackTop + SWITCH_HEIGHT, scaleAlpha(trackColor, alphaScale));
        drawOutline(bounds.left(), trackTop, trackRight, trackTop + SWITCH_HEIGHT,
            scaleAlpha(0xFF000000 | mixColor(CONTROL_BORDER, accent, progress * 0.7F), alphaScale));
        int thumbSize = Math.max(4, SWITCH_HEIGHT - 2);
        int thumbTravel = Math.max(0, SWITCH_WIDTH - thumbSize - 2);
        int thumbLeft = bounds.left() + 1 + Math.round(thumbTravel * progress);
        int thumbColor = 0xFF000000 | mixColor(SWITCH_OFF_THUMB, 0xF5F8FF, progress);
        Gui.drawRect(thumbLeft, trackTop + 1, thumbLeft + thumbSize, trackTop + 1 + thumbSize, scaleAlpha(thumbColor, alphaScale));
        screen.fontRenderer().drawString(label,
            trackRight + 8, bounds.top() + (bounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
            scaleAlpha(TEXT_SECONDARY, alphaScale));
    }

    // ── Expanded enum popup ──────────────────────────────────────────────
    boolean handleExpandedEnumClick(ModernClickGuiLayout layout, List<Setting> visibleSettings,
                                    int mouseX, int mouseY, int mouseButton) {
        if (screen.expandedEnumSetting == null) return false;
        int rowY = layout.settingsContentTop() - Math.round(screen.settingsScroll);
        for (Setting setting : visibleSettings) {
            int rowHeight = screen.getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY, layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (setting == screen.expandedEnumSetting && setting instanceof EnumSetting) {
                EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
                ModernClickGuiBounds popupBounds = getEnumPopupBounds(layout, getEnumChipBounds(rowBounds, enumSetting), enumSetting);
                clampEnumScroll(popupBounds, enumSetting);
                if (!popupBounds.contains(mouseX, mouseY)) return false;
                if (mouseButton == 0) {
                    Object[] values = enumSetting.getValues();
                    for (int i = 0; i < values.length; i++) {
                        ModernClickGuiBounds optionBounds = getEnumOptionBounds(popupBounds, i);
                        if (optionBounds.bottom() <= popupBounds.top() || optionBounds.top() >= popupBounds.bottom()) {
                            continue; // scrolled out of view — not clickable
                        }
                        if (optionBounds.contains(mouseX, mouseY) && enumSetting.setIndex(i)) {
                            screen.expandedEnumSetting = null;
                            screen.ensureSelection();
                            refreshClickGuiStyleIfNeeded(setting);
                            return true;
                        }
                    }
                }
                return true;
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        screen.expandedEnumSetting = null;
        return false;
    }

    private void drawExpandedEnumPopup(ModernClickGuiLayout layout, List<Setting> visibleSettings, int mouseX, int mouseY, int accent) {
        if (screen.expandedEnumSetting == null) return;
        int rowY = layout.settingsContentTop() - Math.round(screen.settingsScroll);
        for (Setting setting : visibleSettings) {
            int rowHeight = screen.getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY, layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (setting == screen.expandedEnumSetting && setting instanceof EnumSetting) {
                EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
                ModernClickGuiBounds popupBounds = getEnumPopupBounds(layout, getEnumChipBounds(rowBounds, enumSetting), enumSetting);
                clampEnumScroll(popupBounds, enumSetting);
                Gui.drawRect(popupBounds.left(), popupBounds.top(), popupBounds.right(), popupBounds.bottom(),
                    0xFF000000 | CONTROL_BACKGROUND);
                drawRoundedOutline(popupBounds.left(), popupBounds.top(), popupBounds.right(), popupBounds.bottom(),
                    4.0F, 0xFF000000 | CONTROL_BORDER);
                beginScissor(popupBounds);
                Object[] values = enumSetting.getValues();
                for (int i = 0; i < values.length; i++) {
                    ModernClickGuiBounds optionBounds = getEnumOptionBounds(popupBounds, i);
                    if (optionBounds.bottom() <= popupBounds.top() || optionBounds.top() >= popupBounds.bottom()) {
                        continue; // scrolled out of view
                    }
                    boolean hovered = optionBounds.contains(mouseX, mouseY);
                    boolean selected = values[i] == enumSetting.getValue();
                    int fill = selected ? withAlpha(accent, 205)
                        : hovered ? withAlpha(SURFACE_PANEL_OUTLINE, 180)
                        : withAlpha(SURFACE_CHIP, 0);
                    if ((fill >>> 24) != 0) {
                        Gui.drawRect(optionBounds.left(), optionBounds.top(), optionBounds.right(), optionBounds.bottom(), fill);
                    }
                    screen.fontRenderer().drawString(values[i].toString(),
                        optionBounds.left() + 8,
                        optionBounds.top() + (optionBounds.getHeight() - screen.fontRenderer().FONT_HEIGHT) / 2,
                        selected ? TEXT_PRIMARY : hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
                }
                endScissor();
                return;
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        screen.expandedEnumSetting = null;
    }

    // ── Expanded color popup ─────────────────────────────────────────────
    boolean handleExpandedColorClick(ModernClickGuiLayout layout, List<Setting> visibleSettings,
                                     int mouseX, int mouseY, int mouseButton) {
        if (screen.expandedColorSetting == null || mouseButton != 0) return false;
        int rowY = layout.settingsContentTop() - Math.round(screen.settingsScroll);
        for (Setting setting : visibleSettings) {
            int rowHeight = screen.getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY, layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (setting == screen.expandedColorSetting) {
                ModernClickGuiBounds chipBounds = getColorChipBounds(rowBounds);
                ModernClickGuiBounds popupBounds = getColorPopupBounds(layout, chipBounds);
                if (!popupBounds.contains(mouseX, mouseY)) return false;
                ModernClickGuiBounds hueBar = getColorHueBarBounds(popupBounds);
                ModernClickGuiBounds square = getColorSquareBounds(popupBounds);
                if (hueBar.contains(mouseX, mouseY)) {
                    screen.colorDragMode = ModernClickGuiScreen.ColorPickerDragMode.HUE;
                    applyColorPickerDrag(popupBounds, mouseX, mouseY);
                } else if (square.contains(mouseX, mouseY)) {
                    screen.colorDragMode = ModernClickGuiScreen.ColorPickerDragMode.SV;
                    applyColorPickerDrag(popupBounds, mouseX, mouseY);
                }
                return true;
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        screen.expandedColorSetting = null;
        return false;
    }

    private void drawExpandedColorPopup(ModernClickGuiLayout layout, List<Setting> visibleSettings, int accent) {
        if (screen.expandedColorSetting == null) return;
        int rowY = layout.settingsContentTop() - Math.round(screen.settingsScroll);
        for (Setting setting : visibleSettings) {
            int rowHeight = screen.getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY, layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (setting == screen.expandedColorSetting) {
                ModernClickGuiBounds chipBounds = getColorChipBounds(rowBounds);
                ModernClickGuiBounds popupBounds = getColorPopupBounds(layout, chipBounds);
                Gui.drawRect(popupBounds.left(), popupBounds.top(), popupBounds.right(), popupBounds.bottom(), 0xFF1C2129);
                drawRoundedOutline(popupBounds.left(), popupBounds.top(), popupBounds.right(), popupBounds.bottom(),
                    4.0F, 0xFF000000 | CONTROL_BORDER);
                drawColorPicker((ColorSetting) setting, popupBounds);
                return;
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        screen.expandedColorSetting = null;
    }

    // ── Color picker ─────────────────────────────────────────────────────
    void applyColorPickerDrag(ModernClickGuiBounds popupBounds, int mouseX, int mouseY) {
        if (screen.expandedColorSetting == null || screen.colorDragMode == ModernClickGuiScreen.ColorPickerDragMode.NONE) return;
        if (screen.colorDragMode == ModernClickGuiScreen.ColorPickerDragMode.HUE) {
            ModernClickGuiBounds hueBar = getColorHueBarBounds(popupBounds);
            float hue = clamp((mouseX - hueBar.left()) / (float) hueBar.getWidth(), 0.0F, 1.0F);
            screen.expandedColorSetting.setHsva(hue, screen.expandedColorSetting.getSaturation(),
                screen.expandedColorSetting.getValue(), screen.expandedColorSetting.getAlpha());
            return;
        }
        ModernClickGuiBounds square = getColorSquareBounds(popupBounds);
        float saturation = clamp((mouseX - square.left()) / (float) square.getWidth(), 0.0F, 1.0F);
        float value = clamp(1.0F - (mouseY - square.top()) / (float) square.getHeight(), 0.0F, 1.0F);
        screen.expandedColorSetting.setHsva(screen.expandedColorSetting.getHue(), saturation, value,
            screen.expandedColorSetting.getAlpha());
    }

    private void drawColorPicker(ColorSetting setting, ModernClickGuiBounds popupBounds) {
        ModernClickGuiBounds hueBar = getColorHueBarBounds(popupBounds);
        ModernClickGuiBounds square = getColorSquareBounds(popupBounds);

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        int segments = 6;
        float segmentWidth = hueBar.getWidth() / (float) segments;
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float leftX = hueBar.left() + segmentWidth * i;
            float rightX = hueBar.left() + segmentWidth * (i + 1);
            int leftRgb = ColorSetting.hsvaToArgb(i / (float) segments, 1.0F, 1.0F, 1.0F);
            int rightRgb = ColorSetting.hsvaToArgb((i + 1) / (float) segments, 1.0F, 1.0F, 1.0F);
            float lr = ((leftRgb >> 16) & 0xFF) / 255.0F;
            float lg = ((leftRgb >> 8) & 0xFF) / 255.0F;
            float lb = (leftRgb & 0xFF) / 255.0F;
            float rr = ((rightRgb >> 16) & 0xFF) / 255.0F;
            float rg = ((rightRgb >> 8) & 0xFF) / 255.0F;
            float rb = (rightRgb & 0xFF) / 255.0F;
            renderer.pos(leftX, hueBar.top(), 0.0D).color(lr, lg, lb, 1.0F).endVertex();
            renderer.pos(leftX, hueBar.bottom(), 0.0D).color(lr, lg, lb, 1.0F).endVertex();
            renderer.pos(rightX, hueBar.bottom(), 0.0D).color(rr, rg, rb, 1.0F).endVertex();
            renderer.pos(rightX, hueBar.top(), 0.0D).color(rr, rg, rb, 1.0F).endVertex();
        }
        tessellator.draw();

        int hueRgb = ColorSetting.hsvaToArgb(setting.getHue(), 1.0F, 1.0F, 1.0F) & 0x00FFFFFF;
        float hr = ((hueRgb >> 16) & 0xFF) / 255.0F;
        float hg = ((hueRgb >> 8) & 0xFF) / 255.0F;
        float hb = (hueRgb & 0xFF) / 255.0F;

        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(square.left(), square.top(), 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        renderer.pos(square.left(), square.bottom(), 0.0D).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        renderer.pos(square.right(), square.bottom(), 0.0D).color(hr, hg, hb, 1.0F).endVertex();
        renderer.pos(square.right(), square.top(), 0.0D).color(hr, hg, hb, 1.0F).endVertex();
        tessellator.draw();

        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(square.left(), square.top(), 0.0D).color(0.0F, 0.0F, 0.0F, 0.0F).endVertex();
        renderer.pos(square.left(), square.bottom(), 0.0D).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
        renderer.pos(square.right(), square.bottom(), 0.0D).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
        renderer.pos(square.right(), square.top(), 0.0D).color(0.0F, 0.0F, 0.0F, 0.0F).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int hueX = Math.round(hueBar.left() + setting.getHue() * hueBar.getWidth());
        Gui.drawRect(hueX - 2, hueBar.top() - 2, hueX + 2, hueBar.bottom() + 2, 0xFF000000);
        Gui.drawRect(hueX - 1, hueBar.top() - 1, hueX + 1, hueBar.bottom() + 1, 0xFFFFFFFF);

        int svX = Math.round(square.left() + setting.getSaturation() * square.getWidth());
        int svY = Math.round(square.top() + (1.0F - setting.getValue()) * square.getHeight());
        Gui.drawRect(svX - 3, svY - 3, svX + 3, svY + 3, 0xFF000000);
        Gui.drawRect(svX - 2, svY - 2, svX + 2, svY + 2, 0xFFFFFFFF);

        int previewTop = square.bottom() + 6;
        int previewBottom = previewTop + 14;
        int previewLeft = square.left();
        int previewRight = square.right();
        Gui.drawRect(previewLeft, previewTop, previewRight, previewBottom, 0xFF000000 | setting.getRgb());
        drawOutline(previewLeft, previewTop, previewRight, previewBottom, 0xFF000000 | CONTROL_BORDER_LIGHT);
        String hex = setting.getValueText();
        int hexX = previewLeft + (previewRight - previewLeft - screen.fontRenderer().getStringWidth(hex)) / 2;
        screen.fontRenderer().drawStringWithShadow(hex, hexX,
            previewTop + (14 - screen.fontRenderer().FONT_HEIGHT) / 2 + 1, 0xFFFFFFFF);
    }

    // ── Range slider ─────────────────────────────────────────────────────
    boolean chooseRangeSliderHandle(IntRangeSetting setting, int mouseX, ModernClickGuiBounds sliderBounds) {
        float progress = clamp((mouseX - sliderBounds.left()) / (float) sliderBounds.getWidth(), 0.0F, 1.0F);
        float clickValue = setting.getMin() + progress * (setting.getMax() - setting.getMin());
        return clickValue >= (setting.getLow() + setting.getHigh()) / 2.0F;
    }

    void applyRangeSliderValue(IntRangeSetting setting, int mouseX, ModernClickGuiBounds sliderBounds, boolean highHandle, boolean save) {
        float progress = clamp((mouseX - sliderBounds.left()) / (float) sliderBounds.getWidth(), 0.0F, 1.0F);
        int value = setting.getMin() + Math.round(progress * (setting.getMax() - setting.getMin()));
        if (highHandle) {
            setting.setHigh(value, save);
        } else {
            setting.setLow(value, save);
        }
    }

    private void drawRangeSlider(IntRangeSetting setting, ModernClickGuiBounds sliderBounds, int accent, float alphaScale) {
        int range = setting.getMax() - setting.getMin();
        float lowProgress = range == 0 ? 0.0F : clamp((setting.getLow() - setting.getMin()) / (float) range, 0.0F, 1.0F);
        float highProgress = range == 0 ? 0.0F : clamp((setting.getHigh() - setting.getMin()) / (float) range, 0.0F, 1.0F);
        int lowX = sliderBounds.left() + Math.round(sliderBounds.getWidth() * lowProgress);
        int highX = sliderBounds.left() + Math.round(sliderBounds.getWidth() * highProgress);
        Gui.drawRect(sliderBounds.left(), sliderBounds.top(), sliderBounds.right(), sliderBounds.bottom(),
            scaleAlpha(0xFF000000 | CONTROL_BORDER, alphaScale));
        if (highX > lowX) {
            Gui.drawRect(lowX, sliderBounds.top(), highX, sliderBounds.bottom(),
                scaleAlpha(withAlpha(accent, 205), alphaScale));
        }
        Gui.drawRect(lowX, sliderBounds.top() - 2, lowX + 2, sliderBounds.bottom() + 2, scaleAlpha(TEXT_PRIMARY, alphaScale));
        Gui.drawRect(highX - 1, sliderBounds.top() - 2, highX + 1, sliderBounds.bottom() + 2, scaleAlpha(TEXT_PRIMARY, alphaScale));
    }

    // ── Slider helpers ────────────────────────────────────────────────────
    void applySliderValue(Setting setting, int mouseX, ModernClickGuiBounds sliderBounds, boolean save) {
        float progress = clamp((mouseX - sliderBounds.left()) / (float) sliderBounds.getWidth(), 0.0F, 1.0F);
        if (setting instanceof NumberSetting) {
            NumberSetting numberSetting = (NumberSetting) setting;
            int range = numberSetting.getMax() - numberSetting.getMin();
            int steps = Math.round((range * progress) / Math.max(1, numberSetting.getStep()));
            int value = numberSetting.getMin() + (steps * numberSetting.getStep());
            numberSetting.setValue(value, save);
            screen.normalizeNumberRanges(screen.selectedModule);
            return;
        }
        if (setting instanceof DecimalSetting) {
            DecimalSetting decimalSetting = (DecimalSetting) setting;
            double range = decimalSetting.getMax() - decimalSetting.getMin();
            double stepped = Math.round((range * progress) / decimalSetting.getStep()) * decimalSetting.getStep();
            decimalSetting.setValue(decimalSetting.getMin() + stepped, save);
            return;
        }
        if (setting instanceof FloatSetting) {
            FloatSetting floatSetting = (FloatSetting) setting;
            float range = floatSetting.getMax() - floatSetting.getMin();
            float steps = Math.round((range * progress) / Math.max(floatSetting.getStep(), 0.001F));
            float value = floatSetting.getMin() + (steps * floatSetting.getStep());
            floatSetting.setValue(value, save);
            return;
        }
    }

    private float getSliderTarget(Setting setting) {
        if (setting instanceof NumberSetting) {
            NumberSetting ns = (NumberSetting) setting;
            if (ns.getMax() == ns.getMin()) return 0.0F;
            return clamp((ns.getValue() - ns.getMin()) / (float) (ns.getMax() - ns.getMin()), 0.0F, 1.0F);
        }
        if (setting instanceof DecimalSetting) {
            DecimalSetting ds = (DecimalSetting) setting;
            if (ds.getMax() == ds.getMin()) return 0.0F;
            return clamp((float) ((ds.getValue() - ds.getMin()) / (ds.getMax() - ds.getMin())), 0.0F, 1.0F);
        }
        if (setting instanceof FloatSetting) {
            FloatSetting fs = (FloatSetting) setting;
            if (fs.getMax() == fs.getMin()) return 0.0F;
            return clamp((fs.getValue() - fs.getMin()) / (fs.getMax() - fs.getMin()), 0.0F, 1.0F);
        }
        return 0.0F;
    }

    // ── Snowflakes ───────────────────────────────────────────────────────
    void initializeSnowflakes() {
        int desiredCount = Math.max(DEFAULT_SNOWFLAKE_COUNT,
            (mc().displayWidth * mc().displayHeight) / 9500);
        screen.snowflakes.clear();
        for (int i = 0; i < desiredCount; i++) {
            screen.snowflakes.add(ModernClickGuiSnowflake.create(screen.random, mc().displayWidth, mc().displayHeight, true));
        }
    }

    void updateSnowflakes(float delta) {
        if (screen.snowflakes.isEmpty()) {
            initializeSnowflakes();
        }
        for (int i = 0; i < screen.snowflakes.size(); i++) {
            ModernClickGuiSnowflake flake = screen.snowflakes.get(i);
            flake.age += delta;
            flake.y += flake.speed * delta;
            flake.x += Math.sin(flake.age * flake.swingSpeed) * flake.swingAmount * delta;
            if (flake.y > mc().displayHeight + 12 || flake.x < -20 || flake.x > mc().displayWidth + 20) {
                screen.snowflakes.set(i, ModernClickGuiSnowflake.create(screen.random, mc().displayWidth, mc().displayHeight, false));
            }
        }
    }

    void drawSnowflakes(ModernClickGuiBounds bounds, float alphaScale) {
        for (ModernClickGuiSnowflake flake : screen.snowflakes) {
            if (flake.x < bounds.left() || flake.x > bounds.right()
                || flake.y < bounds.top() || flake.y > bounds.bottom()) {
                continue;
            }
            int alpha = (int) (flake.alpha * alphaScale);
            int color = withAlpha(SNOWFLAKE_COLOR, alpha);
            Gui.drawRect(Math.round(flake.x), Math.round(flake.y),
                Math.round(flake.x) + flake.size, Math.round(flake.y) + flake.size, color);
        }
    }

    // ── Scissor helpers ───────────────────────────────────────────────────
    void beginScissor(ModernClickGuiBounds bounds) {
        ScaledResolution resolution = new ScaledResolution(mc());
        int scaleFactor = resolution.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            bounds.left() * scaleFactor,
            mc().displayHeight - (bounds.bottom() * scaleFactor),
            bounds.getWidth() * scaleFactor,
            bounds.getHeight() * scaleFactor);
    }

    void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ── Layout (delegates to screen for mutable position state) ──────────
    ModernClickGuiLayout createLayout() {
        return screen.createLayout();
    }

    // ── Value editor helpers ──────────────────────────────────────────────
    boolean handleValueEditorMouseClick(ModernClickGuiLayout layout, int mouseX, int mouseY, int mouseButton) {
        if (screen.editingValueSetting == null || screen.valueEditor == null) return false;
        ModernClickGuiBounds valueBounds = screen.findValueInputBounds(layout, screen.editingValueSetting);
        if (valueBounds != null && valueBounds.contains(mouseX, mouseY)) {
            if (mouseButton == 0) {
                syncValueEditorBounds(valueBounds);
                screen.valueEditor.mouseClicked(mouseX, mouseY, mouseButton);
            }
            return true;
        }
        screen.commitValueEditor(true);
        return false;
    }

    private void syncValueEditorBounds(ModernClickGuiBounds valueBounds) {
        if (screen.valueEditor == null) return;
        screen.valueEditor.xPosition = valueBounds.left() + 5;
        screen.valueEditor.yPosition = valueBounds.top() + 5;
    }

    // ── Bounds helpers ────────────────────────────────────────────────────
    ModernClickGuiBounds getCategoryBounds(ModernClickGuiLayout layout, int index) {
        int tabWidth = 74;
        int gap = 6;
        int totalWidth = (Category.values().length * tabWidth) + ((Category.values().length - 1) * gap);
        int startX = layout.windowX() + (layout.getWidth() - totalWidth) / 2;
        int left = startX + index * (tabWidth + gap);
        return new ModernClickGuiBounds(left, layout.windowY() + 12, left + tabWidth, layout.windowY() + 31);
    }

    ModernClickGuiBounds getHeaderToggleBounds(ModernClickGuiLayout layout) {
        return new ModernClickGuiBounds(
            layout.settingsPaneRight() - 102, layout.settingsPaneY() + 34,
            layout.settingsPaneRight() - 14, layout.settingsPaneY() + 58);
    }

    ModernClickGuiBounds getBooleanControlBounds(ModernClickGuiBounds rowBounds) {
        int top = rowBounds.top() + (rowBounds.getHeight() - CHECKBOX_SIZE) / 2;
        return new ModernClickGuiBounds(
            rowBounds.left() + CONTROL_PADDING, top,
            rowBounds.left() + CONTROL_PADDING + CHECKBOX_SIZE, top + CHECKBOX_SIZE);
    }

    ModernClickGuiBounds getEnumChipBounds(ModernClickGuiBounds rowBounds, EnumSetting<?> setting) {
        int chipWidth = Math.min(CONTROL_WIDTH, rowBounds.getWidth() - (CONTROL_PADDING * 2));
        int top = rowBounds.top() + 14;
        return new ModernClickGuiBounds(
            rowBounds.left() + CONTROL_PADDING, top,
            rowBounds.left() + CONTROL_PADDING + chipWidth, top + CONTROL_HEIGHT);
    }

    ModernClickGuiBounds getColorChipBounds(ModernClickGuiBounds rowBounds) {
        int chipWidth = Math.min(CONTROL_WIDTH, rowBounds.getWidth() - (CONTROL_PADDING * 2));
        int top = rowBounds.top() + 14;
        return new ModernClickGuiBounds(
            rowBounds.left() + CONTROL_PADDING, top,
            rowBounds.left() + CONTROL_PADDING + chipWidth, top + CONTROL_HEIGHT);
    }

    ModernClickGuiBounds getColorPopupBounds(ModernClickGuiLayout layout, ModernClickGuiBounds chipBounds) {
        int popupWidth = Math.max(chipBounds.getWidth(), 178);
        int popupHeight = 168;
        int popupTop = chipBounds.bottom() + 4;
        if (popupTop + popupHeight > layout.settingsScrollBounds().bottom()) {
            popupTop = chipBounds.top() - 4 - popupHeight;
        }
        popupTop = Math.max(layout.settingsScrollBounds().top(), popupTop);
        return new ModernClickGuiBounds(chipBounds.left(), popupTop,
            chipBounds.left() + popupWidth, popupTop + popupHeight);
    }

    ModernClickGuiBounds getColorHueBarBounds(ModernClickGuiBounds popupBounds) {
        int left = popupBounds.left() + 10;
        int right = popupBounds.right() - 10;
        int top = popupBounds.top() + 10;
        return new ModernClickGuiBounds(left, top, right, top + 12);
    }

    ModernClickGuiBounds getColorSquareBounds(ModernClickGuiBounds popupBounds) {
        ModernClickGuiBounds hueBar = getColorHueBarBounds(popupBounds);
        int left = hueBar.left();
        int right = hueBar.right();
        int top = hueBar.bottom() + 8;
        int bottom = popupBounds.bottom() - 28;
        return new ModernClickGuiBounds(left, top, right, bottom);
    }

    ModernClickGuiBounds getEnumPopupBounds(ModernClickGuiLayout layout, ModernClickGuiBounds chipBounds, EnumSetting<?> setting) {
        int totalHeight = setting.getValues().length * ENUM_OPTION_HEIGHT;
        int paneTop = layout.settingsScrollBounds().top();
        int paneBottom = layout.settingsScrollBounds().bottom();
        // Cap the visible popup to the settings pane so long lists (e.g. shaders)
        // don't run off-screen; the options inside are then scrollable.
        int maxHeight = Math.max(ENUM_OPTION_HEIGHT, paneBottom - paneTop);
        int visibleHeight = Math.min(totalHeight, maxHeight);
        int popupTop = chipBounds.bottom() + 4;
        if (popupTop + visibleHeight > paneBottom) {
            popupTop = paneBottom - visibleHeight;
        }
        popupTop = Math.max(paneTop, popupTop);
        return new ModernClickGuiBounds(chipBounds.left(), popupTop, chipBounds.right(), popupTop + visibleHeight);
    }

    /** Popup bounds for the currently expanded enum, or null if none is open. */
    ModernClickGuiBounds getActiveEnumPopupBounds(ModernClickGuiLayout layout) {
        if (screen.expandedEnumSetting == null || screen.selectedModule == null) {
            return null;
        }
        int rowY = layout.settingsContentTop() - Math.round(screen.settingsScroll);
        for (Setting setting : screen.getVisibleSettings(screen.selectedModule)) {
            int rowHeight = screen.getSettingHeight(setting);
            ModernClickGuiBounds rowBounds = new ModernClickGuiBounds(
                layout.settingsPaneX() + 10, rowY, layout.settingsPaneRight() - 10, rowY + rowHeight);
            if (setting == screen.expandedEnumSetting && setting instanceof EnumSetting) {
                return getEnumPopupBounds(layout, getEnumChipBounds(rowBounds, (EnumSetting<?>) setting), (EnumSetting<?>) setting);
            }
            rowY += rowHeight + SETTING_ROW_GAP;
        }
        return null;
    }

    /** Clamps the popup scroll to its valid range and returns the clamped value. */
    private float clampEnumScroll(ModernClickGuiBounds popupBounds, EnumSetting<?> setting) {
        int totalHeight = setting.getValues().length * ENUM_OPTION_HEIGHT;
        float maxScroll = Math.max(0.0F, totalHeight - popupBounds.getHeight());
        screen.enumPopupScroll = clamp(screen.enumPopupScroll, 0.0F, maxScroll);
        return screen.enumPopupScroll;
    }

    ModernClickGuiBounds getEnumOptionBounds(ModernClickGuiBounds popupBounds, int index) {
        int top = popupBounds.top() + (index * ENUM_OPTION_HEIGHT) - Math.round(screen.enumPopupScroll);
        return new ModernClickGuiBounds(popupBounds.left(), top, popupBounds.right(), top + ENUM_OPTION_HEIGHT);
    }

    ModernClickGuiBounds getActionButtonBounds(ModernClickGuiBounds rowBounds, String valueText) {
        int buttonWidth = Math.max(BIND_BUTTON_WIDTH, screen.fontRenderer().getStringWidth(valueText) + 20);
        int top = rowBounds.top() + 14;
        return new ModernClickGuiBounds(
            rowBounds.left() + CONTROL_PADDING, top,
            rowBounds.left() + CONTROL_PADDING + buttonWidth, top + CONTROL_HEIGHT);
    }

    ModernClickGuiBounds getBindButtonBounds(ModernClickGuiBounds rowBounds, String valueText) {
        int buttonWidth = Math.max(BIND_BUTTON_WIDTH, screen.fontRenderer().getStringWidth(valueText) + 20);
        int top = rowBounds.top() + 14;
        return new ModernClickGuiBounds(
            rowBounds.left() + CONTROL_PADDING, top,
            rowBounds.left() + CONTROL_PADDING + buttonWidth, top + CONTROL_HEIGHT);
    }

    ModernClickGuiBounds getValueInputBounds(ModernClickGuiBounds rowBounds) {
        int right = rowBounds.right() - CONTROL_PADDING;
        int top = rowBounds.top() + 5;
        return new ModernClickGuiBounds(right - VALUE_INPUT_WIDTH, top, right, top + VALUE_INPUT_HEIGHT);
    }

    ModernClickGuiBounds getSliderBounds(ModernClickGuiBounds rowBounds) {
        int sliderWidth = Math.min(CONTROL_WIDTH, rowBounds.getWidth() - (CONTROL_PADDING * 2));
        int top = rowBounds.top() + 27;
        return new ModernClickGuiBounds(
            rowBounds.left() + CONTROL_PADDING, top,
            rowBounds.left() + CONTROL_PADDING + sliderWidth, top + SLIDER_TRACK_HEIGHT);
    }


    // ── Utility drawing ───────────────────────────────────────────────────
    private void drawOutline(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, top + 1, color);
        Gui.drawRect(left, bottom - 1, right, bottom, color);
        Gui.drawRect(left, top, left + 1, bottom, color);
        Gui.drawRect(right - 1, top, right, bottom, color);
    }

    private void drawRoundedOutline(float left, float top, float right, float bottom, float radius, int color) {
        if (((color >>> 24) & 255) == 0 || right <= left || bottom <= top) return;
        drawOutline(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom), color);
    }

    private void drawRoundedRect(float left, float top, float right, float bottom, float radius, int color) {
        if (((color >>> 24) & 255) == 0 || right <= left || bottom <= top) return;
        Gui.drawRect(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom), color);
    }

    private void drawScaledText(String text, int x, int y, int color, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        screen.fontRenderer().drawString(text, Math.round(x / scale), Math.round(y / scale), color);
        GlStateManager.popMatrix();
    }

    // ── Animation / transition (delegates to screen state) ───────────────
    void updateCategoryTransition(float delta) {
        if (screen.previousCategory == null) {
            screen.categoryTransitionProgress = 1.0F;
            return;
        }
        screen.categoryTransitionProgress = animate(screen.categoryTransitionProgress, 1.0F, delta * 10.0F);
        if (screen.categoryTransitionProgress >= 0.995F) {
            screen.clearCategoryTransition();
        }
    }

    // ── Static math / color helpers ───────────────────────────────────────
    private static float animate(float current, float target, float speed) {
        return current + ((target - current) * clamp(speed, 0.0F, 1.0F));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 255) << 24) | (color & 0x00FFFFFF);
    }

    private static int scaleAlpha(int color, float alphaScale) {
        int baseAlpha = (color >>> 24) & 255;
        if (baseAlpha == 0) baseAlpha = 255;
        return withAlpha(color, Math.round(baseAlpha * clamp(alphaScale, 0.0F, 1.0F)));
    }

    private static int mixColor(int start, int end, float progress) {
        float amount = clamp(progress, 0.0F, 1.0F);
        int startA = (start >>> 24) & 255;
        int startR = (start >>> 16) & 255;
        int startG = (start >>> 8) & 255;
        int startB = start & 255;
        int endA = (end >>> 24) & 255;
        int endR = (end >>> 16) & 255;
        int endG = (end >>> 8) & 255;
        int endB = end & 255;
        int alpha = Math.round(startA + ((endA - startA) * amount));
        int red = Math.round(startR + ((endR - startR) * amount));
        int green = Math.round(startG + ((endG - startG) * amount));
        int blue = Math.round(startB + ((endB - startB) * amount));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    private static <T> float getAnimation(Map<T, Float> map, T key, float target, float speed) {
        Float current = map.get(key);
        float value = animate(current == null ? 0.0F : current.floatValue(), target, speed * 0.016F);
        map.put(key, Float.valueOf(value));
        return value;
    }

    private void refreshClickGuiStyleIfNeeded(Setting setting) {
        if (screen.selectedModule != ClickGuiModule.getInstance() || !"Style".equals(setting.getName())) return;
        LionClient client = LionClient.getInstance();
        if (client != null) client.refreshClickGuiStyle();
    }
}
