package com.lionclient.gui;

import net.minecraft.client.gui.ScaledResolution;

/**
 * Common contract implemented by HUD modules so the {@link HudEditorScreen}
 * can treat every on-screen element uniformly: discover its position, query
 * its rendered size and move it through drag and drop.
 */
public interface HudElement {
    /** Display name shown in the editor when an element is selected. */
    String getHudElementName();

    /** Whether this element is currently visible (typically: module enabled). */
    boolean isHudElementVisible();

    int getHudX();

    int getHudY();

    /** Width of the rendered element, in scaled pixels. */
    int getHudWidth(ScaledResolution resolution);

    /** Height of the rendered element, in scaled pixels. */
    int getHudHeight(ScaledResolution resolution);

    /**
     * Whether the element is rendered against the right edge (anchor on the
     * right of the bounding box). Most elements are left-anchored.
     */
    boolean isHudRightAligned(ScaledResolution resolution);

    /** Sets the new position. The implementation is responsible for clamping. */
    void setHudPosition(int x, int y);

    /** Scale factor (1.0 = normal size). */
    float getHudScale();

    /** Sets the new scale. */
    void setHudScale(float scale);

    /**
     * Renders a preview inside the editor. Defaults to nothing, modules with
     * dynamic content (HudModule with module list) override this so an empty
     * list still shows something draggable.
     */
    default void renderHudPreview(ScaledResolution resolution) {
    }
}
