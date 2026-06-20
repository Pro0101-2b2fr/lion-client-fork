package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.HumanClickTimer;
import com.lionclient.util.MouseButtonHelper;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Attacks the entity the player is looking at when it enters attack range.
 * Two modes:
 * <ul>
 *   <li>{@code SINGLE} — sends one vanilla attack per tick when the crosshair
 *       is on a valid target. Feels like a normal click.</li>
 *   <li>{@code AUTO} — uses the {@link HumanClickTimer} to generate human-like
 *       CPS while the crosshair stays on target. Equivalent to holding LMB
 *       with the AutoClicker but only when aiming at an enemy.</li>
 * </ul>
 *
 * Integrates with:
 * <ul>
 *   <li>{@link ReachModule} — if enabled, uses its configured reach instead of
 *       vanilla 3.0.</li>
 *   <li>{@link AntiBotModule} — ignores bots.</li>
 *   <li>Team detection — skips teammates (same scoreboard team or same name
 *       color prefix).</li>
 * </ul>
 */
public final class TriggerBotModule extends Module {
    private static final double VANILLA_REACH = 3.0D;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.AUTO);
    private final NumberSetting minCps = new NumberSetting("Min CPS", 1, 25, 1, 10);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 1, 25, 1, 14);
    private final BooleanSetting useReach = new BooleanSetting("Use Reach", true);
    private final BooleanSetting teamCheck = new BooleanSetting("Team Check", true);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", false);
    private final BooleanSetting targetInvis = new BooleanSetting("Target Invis", false);
    private final BooleanSetting predict = new BooleanSetting("Predict", true);
    private final NumberSetting reactionMs = new NumberSetting("Reaction (ms)", 0, 300, 5, 80);

    private final HumanClickTimer clickTimer = new HumanClickTimer();
    private long lastClick;
    private long holdUntil;
    private boolean leftDown;
    private long targetAcquiredAt;
    private Entity lastTarget;

    public TriggerBotModule() {
        super("TriggerBot", "Attacks when crosshair is on an enemy.", Category.COMBAT, Keyboard.KEY_NONE);
        minCps.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.AUTO;
            }
        });
        maxCps.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.AUTO;
            }
        });
        addSetting(mode);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(useReach);
        addSetting(teamCheck);
        addSetting(weaponOnly);
        addSetting(targetInvis);
        addSetting(predict);
        addSetting(reactionMs);
    }

    @Override
    protected void onEnable() {
        clickTimer.reset();
        lastClick = 0L;
        holdUntil = 0L;
        leftDown = false;
        targetAcquiredAt = 0L;
        lastTarget = null;
    }

    @Override
    protected void onDisable() {
        releaseClick();
        clickTimer.reset();
        targetAcquiredAt = 0L;
        lastTarget = null;
    }

    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.theWorld == null || minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            releaseClick();
            targetAcquiredAt = 0L;
            lastTarget = null;
            return;
        }

        if (weaponOnly.isEnabled() && !isHoldingWeapon(minecraft)) {
            releaseClick();
            targetAcquiredAt = 0L;
            lastTarget = null;
            return;
        }

        Entity target = getTarget(minecraft, player);
        if (target == null) {
            // If predict is enabled, check if a target is approaching within
            // reach + 0.5 blocks. If so, start the reaction timer early so
            // the attack fires the instant they enter real range.
            if (predict.isEnabled()) {
                Entity predicted = getPredictedTarget(minecraft, player);
                if (predicted != null) {
                    long now = System.currentTimeMillis();
                    if (predicted != lastTarget) {
                        lastTarget = predicted;
                        targetAcquiredAt = now;
                    }
                    // Don't attack yet — just warm up the timer.
                    return;
                }
            }
            releaseClick();
            targetAcquiredAt = 0L;
            lastTarget = null;
            return;
        }

        // Reaction time: don't attack instantly when a new target enters crosshair.
        long now = System.currentTimeMillis();
        if (target != lastTarget) {
            lastTarget = target;
            targetAcquiredAt = now;
        }
        if (now - targetAcquiredAt < reactionMs.getValue()) {
            return;
        }

        if (mode.getValue() == Mode.SINGLE) {
            singleClick(minecraft);
        } else {
            autoClick(minecraft, now);
        }
    }

    private Entity getTarget(Minecraft minecraft, EntityPlayerSP player) {
        double reach = getEffectiveReach(minecraft);
        // First check vanilla objectMouseOver (works for vanilla reach).
        Entity vanillaTarget = checkVanillaMouseOver(minecraft, reach);
        if (vanillaTarget != null) {
            return vanillaTarget;
        }

        // If using extended reach, do our own ray trace.
        if (useReach.isEnabled() && reach > VANILLA_REACH) {
            Entity extended = rayTraceEntity(minecraft, player, reach);
            if (extended != null && isValidTarget(minecraft, extended)) {
                return extended;
            }
        }
        return null;
    }

    /**
     * Looks for a valid target within reach + 0.5 blocks along the look
     * vector. Used by the predict feature to start the reaction timer before
     * the target actually enters attack range.
     */
    private Entity getPredictedTarget(Minecraft minecraft, EntityPlayerSP player) {
        double reach = getEffectiveReach(minecraft) + 0.5D;
        Entity entity = rayTraceEntity(minecraft, player, reach);
        if (entity != null && isValidTarget(minecraft, entity)) {
            return entity;
        }
        return null;
    }

    private Entity checkVanillaMouseOver(Minecraft minecraft, double reach) {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return null;
        }
        Entity entity = hit.entityHit;
        if (entity == null || !isValidTarget(minecraft, entity)) {
            return null;
        }
        double distance = minecraft.thePlayer.getDistanceToEntity(entity);
        if (distance > reach) {
            return null;
        }
        return entity;
    }

    private boolean isValidTarget(Minecraft minecraft, Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity == minecraft.thePlayer) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        if (living.isDead || living.deathTime != 0 || living.getHealth() <= 0.0F) {
            return false;
        }
        if (entity.isInvisible() && !targetInvis.isEnabled()) {
            return false;
        }
        if (entity instanceof EntityPlayer && AntiBotModule.shouldIgnore((EntityPlayer) entity)) {
            return false;
        }
        if (teamCheck.isEnabled() && entity instanceof EntityPlayer && isTeammate(minecraft, (EntityPlayer) entity)) {
            return false;
        }
        return true;
    }

    private boolean isTeammate(Minecraft minecraft, EntityPlayer other) {
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || other == null) {
            return false;
        }

        try {
            // Scoreboard team check - wrapped for vanilla scoreboard race condition
            Team myTeam = player.getTeam();
            Team theirTeam = other.getTeam();
            if (myTeam != null && theirTeam != null) {
                String myTeamName = myTeam.getRegisteredName();
                String theirTeamName = theirTeam.getRegisteredName();
                if (myTeamName != null && myTeamName.equals(theirTeamName)) {
                    return true;
                }
                if (myTeam == theirTeam) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Scoreboard in invalid state, fall back to color prefix
        }

        // Name color prefix check (common on Hypixel, etc.).
        try {
            String myPrefix = getColorPrefix(player);
            String theirPrefix = getColorPrefix(other);
            if (myPrefix != null && myPrefix.equals(theirPrefix) && !myPrefix.isEmpty()) {
                return true;
            }
        } catch (Exception e) {
            // Ignore formatting errors
        }

        return false;
    }

    private String getColorPrefix(EntityPlayer player) {
        if (player.getDisplayName() == null) {
            return null;
        }
        String formatted = player.getDisplayName().getFormattedText();
        if (formatted == null || formatted.length() < 2) {
            return null;
        }
        // Extract the first color code (§X) from the display name.
        for (int i = 0; i < formatted.length() - 1; i++) {
            if (formatted.charAt(i) == '\u00a7') {
                char code = formatted.charAt(i + 1);
                if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
                    return "\u00a7" + code;
                }
            }
        }
        return null;
    }

    private double getEffectiveReach(Minecraft minecraft) {
        if (!useReach.isEnabled()) {
            return VANILLA_REACH;
        }
        LionClient client = LionClient.getInstance();
        if (client == null) {
            return VANILLA_REACH;
        }
        ReachModule reachModule = client.getModuleManager().getModule(ReachModule.class);
        if (reachModule == null || !reachModule.isEnabled()) {
            return VANILLA_REACH;
        }
        return reachModule.getReachValue();
    }

    private Entity rayTraceEntity(Minecraft minecraft, EntityPlayerSP player, double reach) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        Entity closest = null;
        double closestDist = reach;

        List<?> entities = minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(
            player,
            player.getEntityBoundingBox()
                .addCoord(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach)
                .expand(1.0D, 1.0D, 1.0D)
        );

        for (Object obj : entities) {
            if (!(obj instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) obj;
            if (!entity.canBeCollidedWith() || entity == player) {
                continue;
            }
            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
            if (box.isVecInside(eyes)) {
                if (closestDist >= 0.0D) {
                    closest = entity;
                    closestDist = 0.0D;
                }
                continue;
            }
            if (intercept == null) {
                continue;
            }
            double dist = eyes.distanceTo(intercept.hitVec);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
        return closest;
    }

    private void singleClick(Minecraft minecraft) {
        int key = minecraft.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        KeyBinding.onTick(key);
        MouseButtonHelper.setButton(0, true);
    }

    private void autoClick(Minecraft minecraft, long now) {
        long delay = clickTimer.nextDelayMs(minCps.getValue(), Math.max(minCps.getValue(), maxCps.getValue()));
        if (now - lastClick >= delay) {
            lastClick = now;
            holdUntil = now + clickTimer.nextHoldLengthMs(delay);
            sendClick(minecraft, true);
            leftDown = true;
        } else if (leftDown && now >= holdUntil) {
            sendClick(minecraft, false);
            leftDown = false;
        }
    }

    private void sendClick(Minecraft minecraft, boolean pressed) {
        int key = minecraft.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, pressed);
        MouseButtonHelper.setButton(0, pressed);
        if (pressed) {
            KeyBinding.onTick(key);
        }
    }

    private void releaseClick() {
        if (leftDown) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.gameSettings != null) {
                int key = minecraft.gameSettings.keyBindAttack.getKeyCode();
                KeyBinding.setKeyBindState(key, false);
                MouseButtonHelper.setButton(0, false);
            }
            leftDown = false;
        }
    }

    private boolean isHoldingWeapon(Minecraft minecraft) {
        if (minecraft.thePlayer.getHeldItem() == null) {
            return false;
        }
        String name = minecraft.thePlayer.getHeldItem().getItem().getUnlocalizedName();
        return name != null && (name.contains("sword") || name.contains("axe"));
    }

    @Override
    public String getHudInfo() {
        return mode.getValue() == Mode.AUTO ? "auto" : "single";
    }

    private enum Mode {
        SINGLE("Single"),
        AUTO("Auto");

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
