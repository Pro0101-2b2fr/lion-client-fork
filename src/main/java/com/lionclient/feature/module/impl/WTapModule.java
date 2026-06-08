package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Automatically releases and re-presses sprint around attacks to apply extra
 * knockback (the "W-tap" technique). In vanilla, hitting while sprinting
 * applies a sprint-knockback bonus but then cancels your sprint. By releasing
 * sprint for 1 tick before the hit and re-pressing immediately after, the
 * server registers the hit as a sprint-hit every time.
 *
 * <p>This module listens for the player's attack swing and briefly releases
 * the sprint key for a configurable number of ticks, then re-presses it.
 * Completely legit — it's just automated key timing.</p>
 */
public final class WTapModule extends Module {
    private final NumberSetting releaseTicks = new NumberSetting("Release Ticks", 1, 5, 1, 1);
    private final NumberSetting chance = new NumberSetting("Chance %", 0, 100, 1, 100);

    private int releaseCounter;
    private boolean releasing;
    private int lastSwingTick;

    private static WTapModule instance;

    public WTapModule() {
        super("WTap", "Releases sprint on hit for extra knockback.", Category.COMBAT, Keyboard.KEY_NONE);
        instance = this;
        addSetting(releaseTicks);
        addSetting(chance);
    }

    /** Returns true if WTap is currently releasing sprint (other modules should not re-press). */
    public static boolean isReleasing() {
        return instance != null && instance.isEnabled() && instance.releasing;
    }

    @Override
    protected void onEnable() {
        releaseCounter = 0;
        releasing = false;
        lastSwingTick = -100;
    }

    @Override
    protected void onDisable() {
        releasing = false;
        releaseCounter = 0;
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

        // Detect a new swing (attack animation started this tick).
        if (player.isSwingInProgress && player.swingProgressInt == 0 && player.ticksExisted != lastSwingTick) {
            // Check if we are actually targeting an entity.
            if (minecraft.objectMouseOver != null && minecraft.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                lastSwingTick = player.ticksExisted;
                // Only trigger if sprinting and moving forward.
                if (player.isSprinting() && player.movementInput != null && player.movementInput.moveForward > 0.0F) {
                    if (chance.getValue() >= 100 || Math.random() * 100.0D < chance.getValue()) {
                        releasing = true;
                        releaseCounter = releaseTicks.getValue();
                    }
                }
            }
        }

        if (releasing) {
            if (releaseCounter > 0) {
                // Release sprint key to reset sprint state.
                KeyBinding.setKeyBindState(sprintKey, false);
                player.setSprinting(false);
                releaseCounter--;
            } else {
                // Re-press sprint.
                KeyBinding.setKeyBindState(sprintKey, true);
                releasing = false;
            }
        }
    }

    @Override
    public String getHudInfo() {
        return releaseTicks.getValue() + "t";
    }
}
