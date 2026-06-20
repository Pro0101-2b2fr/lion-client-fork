package com.lionclient.gui;

/**
 * Immutable integer rectangle used for hit-testing and scissor rectangles.
 * Replaces the former private static inner class {@code Bounds}.
 */
public final class ModernClickGuiBounds {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public ModernClickGuiBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    public int getWidth() {
        return right - left;
    }

    public int getHeight() {
        return bottom - top;
    }

    public int left() { return left; }
    public int top() { return top; }
    public int right() { return right; }
    public int bottom() { return bottom; }
}
