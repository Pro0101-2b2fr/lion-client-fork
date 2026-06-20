package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Jump Reset — automatically jumps to reset knockback or extend combos.
 *
 * Fixes vs previous version:
 * - Proper debounce: one jump per hurt event (not per tick while hurtTime > 0)
 * - Proper debounce: one jump per swing (not per tick while swinging)
 * - State machine: WAITING → JUMPED → COOLDOWN prevents re-triggering
 */
public final class JumpResetModule extends Module {
    private static final Random RNG = new Random();

    public enum ResetMode {
        DAMAGE("Damage"),
        HIT("Hit"),
        BOTH("Both");
        final String name;
        ResetMode(String name) { this.name = name; }
    }

    private final EnumSetting<ResetMode> mode = new EnumSetting<>("Mode", ResetMode.values(), ResetMode.DAMAGE);
    private final NumberSetting chance = new NumberSetting("Chance", 0, 100, 5, 85);
    private final NumberSetting delayTicks = new NumberSetting("Delay", 0, 5, 1, 1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Only On Ground", true);
    private final BooleanSetting onlyWhileSprinting = new BooleanSetting("Only While Sprinting", false);

    // State machine to prevent re-triggering
    private enum State { IDLE, WAITING, JUMPED, COOLDOWN }
    private State state = State.IDLE;
    private int delayCounter = 0;
    private int cooldownCounter = 0;
    private int lastHurtTick = -100;
    private int lastSwingTick = -100;

    public JumpResetModule() {
        super("JumpReset", "Auto-jumps after taking damage to reset knockback.", Category.LEGIT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(chance);
        addSetting(delayTicks);
        addSetting(onlyOnGround);
        addSetting(onlyWhileSprinting);
    }

    @Override
    protected void onEnable() {
        state = State.IDLE;
        delayCounter = 0;
        cooldownCounter = 0;
    }

    @Override
    protected void onDisable() {
        state = State.IDLE;
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // State machine
        switch (state) {
            case IDLE:
                checkTrigger(mc);
                break;
            case WAITING:
                delayCounter--;
                if (delayCounter <= 0) {
                    executeJump(mc);
                    state = State.JUMPED;
                    cooldownCounter = 3; // 3 tick cooldown before next trigger
                }
                break;
            case JUMPED:
                // Wait for cooldown before going back to idle
                cooldownCounter--;
                if (cooldownCounter <= 0) {
                    state = State.IDLE;
                }
                break;
            case COOLDOWN:
                cooldownCounter--;
                if (cooldownCounter <= 0) state = State.IDLE;
                break;
        }
    }

    private void checkTrigger(Minecraft mc) {
        boolean shouldJump = false;

        // Damage mode: trigger ONCE when hurtTime first becomes > 0
        if (mode.getValue() == ResetMode.DAMAGE || mode.getValue() == ResetMode.BOTH) {
            // hurtTime goes 10 → 9 → 8 → ... → 0 over time
            // We trigger on the FIRST tick where hurtTime > 0 AND we haven't triggered for this hurt event
            if (mc.thePlayer.hurtTime == 10 && mc.thePlayer.ticksExisted != lastHurtTick) {
                lastHurtTick = mc.thePlayer.ticksExisted;
                shouldJump = true;
            }
        }

        // Hit mode: trigger ONCE when player starts a swing at an entity
        if (mode.getValue() == ResetMode.HIT || mode.getValue() == ResetMode.BOTH) {
            if (mc.thePlayer.isSwingInProgress && mc.thePlayer.swingProgressInt == 0
                && mc.thePlayer.ticksExisted != lastSwingTick) {
                if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                    lastSwingTick = mc.thePlayer.ticksExisted;
                    shouldJump = true;
                }
            }
        }

        if (!shouldJump) return;
        if (onlyOnGround.isEnabled() && !mc.thePlayer.onGround) return;
        if (onlyWhileSprinting.isEnabled() && !mc.thePlayer.isSprinting()) return;
        if (RNG.nextInt(100) >= chance.getValue()) return;

        // Start waiting
        state = State.WAITING;
        delayCounter = delayTicks.getValue() + RNG.nextInt(2); // 0-1 tick jitter
    }

    private void executeJump(Minecraft mc) {
        if (mc.thePlayer.onGround) {
            mc.thePlayer.jump();
        }
    }

    @Override
    public String getHudInfo() {
        if (state == State.WAITING) return "WAIT " + delayCounter;
        if (state == State.JUMPED) return "JUMPED";
        return "";
    }
}
