package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Holds sprint enabled while the player is actively moving forward (or in any
 * direction when {@code Omnidirectional} is enabled). Releases sprint in the
 * common cases where a real player would not be sprinting (sneaking,
 * blindness, eating, low hunger) so the module never glues a constant sprint
 * input that anti-cheats can trivially detect.
 */
public final class SprintModule extends Module {
    private static final int LOW_HUNGER_THRESHOLD = 6;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.LEGIT);
    private final BooleanSetting omnidirectional = new BooleanSetting("Omnidirectional", false);
    private final BooleanSetting allowWhenLowHunger = new BooleanSetting("Allow Low Hunger", false);

    public SprintModule() {
        super("Sprint", "Automatically sprints whenever you are moving.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(omnidirectional);
        addSetting(allowWhenLowHunger);
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null || minecraft.gameSettings == null) {
            return;
        }

        int sprintKey = minecraft.gameSettings.keyBindSprint.getKeyCode();
        if (!shouldSprint(minecraft, player)) {
            KeyBinding.setKeyBindState(sprintKey, false);
            return;
        }

        if (mode.getValue() == Mode.RAGE) {
            // Force the sprint flag on the entity directly. Reliable but
            // visible to anticheats since you keep sprinting the moment a hit
            // would normally cancel it. Keep this as an opt-in.
            KeyBinding.setKeyBindState(sprintKey, true);
            player.setSprinting(true);
            return;
        }

        // Legit: only press the keybind, vanilla decides whether sprint
        // actually engages (food, collisions, etc.).
        KeyBinding.setKeyBindState(sprintKey, true);
    }

    @Override
    protected void onDisable() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings != null) {
            KeyBinding.setKeyBindState(minecraft.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    @Override
    public String getHudInfo() {
        return mode.getValue() == Mode.RAGE ? "rage" : "";
    }

    private boolean shouldSprint(Minecraft minecraft, EntityPlayerSP player) {
        if (player.isSneaking() || player.isUsingItem() || player.isCollidedHorizontally) {
            return false;
        }
        if (!allowWhenLowHunger.isEnabled()
            && player.getFoodStats() != null
            && player.getFoodStats().getFoodLevel() <= LOW_HUNGER_THRESHOLD) {
            return false;
        }
        if (player.movementInput == null) {
            return false;
        }

        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        if (omnidirectional.isEnabled()) {
            return forward != 0.0F || strafe != 0.0F;
        }
        return forward > 0.0F;
    }

    private enum Mode {
        LEGIT("Legit"),
        RAGE("Rage");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
