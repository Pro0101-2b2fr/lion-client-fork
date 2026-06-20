package com.lionclient.gui;

/**
 * Resolved pixel layout for one frame of the modern click GUI.
 * Created each frame by {@link ModernClickGuiScreen#createLayout()}.
 */
public final class ModernClickGuiLayout {
    private final int windowX;
    private final int windowY;
    private final int windowRight;
    private final int windowBottom;
    private final ModernClickGuiBounds windowBounds;
    private final int modulePaneX;
    private final int modulePaneY;
    private final int modulePaneWidth;
    private final int modulePaneBottom;
    private final int moduleContentTop;
    private final ModernClickGuiBounds moduleScrollBounds;
    private final int settingsPaneX;
    private final int settingsPaneY;
    private final int settingsPaneRight;
    private final int settingsPaneBottom;
    private final int settingsContentTop;
    private final ModernClickGuiBounds settingsScrollBounds;

    public ModernClickGuiLayout(
        int windowX, int windowY, int windowRight, int windowBottom,
        int modulePaneX, int modulePaneY, int modulePaneWidth, int modulePaneBottom,
        int moduleContentTop, ModernClickGuiBounds moduleScrollBounds,
        int settingsPaneX, int settingsPaneY, int settingsPaneRight, int settingsPaneBottom,
        int settingsContentTop, ModernClickGuiBounds settingsScrollBounds
    ) {
        this.windowX = windowX;
        this.windowY = windowY;
        this.windowRight = windowRight;
        this.windowBottom = windowBottom;
        this.windowBounds = new ModernClickGuiBounds(windowX, windowY, windowRight, windowBottom);
        this.modulePaneX = modulePaneX;
        this.modulePaneY = modulePaneY;
        this.modulePaneWidth = modulePaneWidth;
        this.modulePaneBottom = modulePaneBottom;
        this.moduleContentTop = moduleContentTop;
        this.moduleScrollBounds = moduleScrollBounds;
        this.settingsPaneX = settingsPaneX;
        this.settingsPaneY = settingsPaneY;
        this.settingsPaneRight = settingsPaneRight;
        this.settingsPaneBottom = settingsPaneBottom;
        this.settingsContentTop = settingsContentTop;
        this.settingsScrollBounds = settingsScrollBounds;
    }

    public int getWidth() { return windowRight - windowX; }

    public int windowX() { return windowX; }
    public int windowY() { return windowY; }
    public int windowRight() { return windowRight; }
    public int windowBottom() { return windowBottom; }
    public ModernClickGuiBounds windowBounds() { return windowBounds; }
    public int modulePaneX() { return modulePaneX; }
    public int modulePaneY() { return modulePaneY; }
    public int modulePaneWidth() { return modulePaneWidth; }
    public int modulePaneBottom() { return modulePaneBottom; }
    public int moduleContentTop() { return moduleContentTop; }
    public ModernClickGuiBounds moduleScrollBounds() { return moduleScrollBounds; }
    public int settingsPaneX() { return settingsPaneX; }
    public int settingsPaneY() { return settingsPaneY; }
    public int settingsPaneRight() { return settingsPaneRight; }
    public int settingsPaneBottom() { return settingsPaneBottom; }
    public int settingsContentTop() { return settingsContentTop; }
    public ModernClickGuiBounds settingsScrollBounds() { return settingsScrollBounds; }
}
