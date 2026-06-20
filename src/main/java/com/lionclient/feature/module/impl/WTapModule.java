package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * W-Tap — releases sprint around attacks for extra knockback.
 * <p>
 * Two modes:
 * <ul>
 *   <li><b>Legit</b> — manipulates the local sprint key binding (KeyBinding.setKeyBindState).
 *       Uses swing detection + hurtTime timing (like Simp-recode).</li>
 *   <li><b>Packet</b> — sends C0BPacketEntityAction.STOP_SPRINTING directly to server
 *       when target.hurtTime == 9 (Simp-recode approach).</li>
 * </ul>
 */
public final class WTapModule extends Module {

    public enum Mode {
        LEGIT("Legit"),
        PACKET("Packet");

        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.LEGIT);
    private final NumberSetting releaseTicks = new NumberSetting("Release Ticks", 1, 5, 1, 1);
    private final NumberSetting chance = new NumberSetting("Chance %", 0, 100, 1, 100);

    // State
    private int releaseCounter;
    private boolean releasing;
    private int lastSwingTick;
    private EntityLivingBase packetTarget;
    private boolean packetPending;

    private static WTapModule instance;

    public WTapModule() {
        super("WTap", "Releases sprint on hit for extra knockback.", Category.LEGIT, Keyboard.KEY_NONE);
        instance = this;

        releaseTicks.setVisibility(() -> mode.getValue() == Mode.LEGIT);

        addSetting(mode);
        addSetting(releaseTicks);
        addSetting(chance);
    }

    public static boolean isReleasing() {
        return instance != null && instance.isEnabled() && instance.releasing;
    }

    @Override
    protected void onEnable() {
        releaseCounter = 0;
        releasing = false;
        lastSwingTick = -100;
        packetPending = false;
        packetTarget = null;
    }

    @Override
    protected void onDisable() {
        releasing = false;
        releaseCounter = 0;
        packetPending = false;
        if (instance != null) instance.packetTarget = null;
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.gameSettings == null) return;

        Mode currentMode = mode.getValue();
        int sprintKey = mc.gameSettings.keyBindSprint.getKeyCode();

        // ---------- LEGIT MODE ----------
        if (currentMode == Mode.LEGIT) {
            // Detect new swing (attack animation started this tick)
            if (player.isSwingInProgress && player.swingProgressInt == 0 && player.ticksExisted != lastSwingTick) {
                if (mc.objectMouseOver != null
                        && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                    Entity target = mc.objectMouseOver.entityHit;
                    if (target instanceof EntityLivingBase) {
                        EntityLivingBase living = (EntityLivingBase) target;
                        if (living != player && !living.isDead && living.getHealth() > 0) {
                            lastSwingTick = player.ticksExisted;

                            // Only if sprinting and moving forward
                            if (player.isSprinting() && player.movementInput != null && player.movementInput.moveForward > 0.0F) {
                                // Simp-recode uses hurtTime >= 6 for legit mode
                                if (living.hurtTime >= 6) {
                                    if (chance.getValue() >= 100 || Math.random() * 100.0D < chance.getValue()) {
                                        releasing = true;
                                        releaseCounter = releaseTicks.getValue();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Handle release/re-press
            if (releasing) {
                if (releaseCounter > 0) {
                    KeyBinding.setKeyBindState(sprintKey, false);
                    player.setSprinting(false);
                    releaseCounter--;
                } else {
                    KeyBinding.setKeyBindState(sprintKey, true);
                    releasing = false;
                }
            }
        }

        // ---------- PACKET MODE ----------
        if (currentMode == Mode.PACKET) {
            // Find target on swing start
            if (packetTarget == null && player.isSwingInProgress && player.swingProgressInt == 0 && player.ticksExisted != lastSwingTick) {
                if (mc.objectMouseOver != null
                        && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                    Entity target = mc.objectMouseOver.entityHit;
                    if (target instanceof EntityLivingBase) {
                        EntityLivingBase living = (EntityLivingBase) target;
                        if (living != player && !living.isDead && living.getHealth() > 0) {
                            packetTarget = living;
                            packetPending = true;
                        }
                    }
                }
            }

            // Apply packet when target's hurtTime == 9 (Simp-recode timing)
            if (packetPending && packetTarget != null) {
                if (packetTarget.hurtTime == 9) {
                    if (chance.getValue() >= 100 || Math.random() * 100.0D < chance.getValue()) {
                        mc.thePlayer.sendQueue.addToSendQueue(
                            new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    }
                    packetPending = false;
                    packetTarget = null;
                    lastSwingTick = player.ticksExisted;
                } else if (packetTarget.isDead || packetTarget.getHealth() <= 0) {
                    // Target died or became invalid
                    packetPending = false;
                    packetTarget = null;
                }
            }
        }
    }

    @Override
    public String getHudInfo() {
        if (mode.getValue() == Mode.PACKET) return "Packet";
        return releaseTicks.getValue() + "t";
    }
}