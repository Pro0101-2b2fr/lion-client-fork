package com.lionclient.feature.module;

public enum Category {
    COMBAT("Blatant"),
    LEGIT("Legit"),
    MOVEMENT("Movement"),
    CLIENT("Client"),
    RENDER("Render"),
    PLAYER("Player"),
    HUD("HUD");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
