package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.combat.ClientRotationHelper;
import com.lionclient.combat.KillAuraRotationUtils;
import com.lionclient.combat.RotationState;
import com.lionclient.event.ClientRotationEvent;
import com.lionclient.event.EventBus;
import com.lionclient.event.IEventListener;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.MouseGcdHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import com.lionclient.combat.TargetPredictor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * AimAssist — anti-cheat bypass final.
 *
 * Applies client-side (PLAYER) rotations via MouseGcdHelper.rotateBy()
 * so deltas are GCD-aligned and look like real mouse input to anti-cheats.
 */
public final class AimAssistModule extends Module {

    // --- Settings ---
    private final DecimalSetting horizontalSpeed = new DecimalSetting("Horizontal Speed", 0.5, 50.0, 0.1, 8.0);
    private final DecimalSetting verticalSpeed = new DecimalSetting("Vertical Speed", 0.5, 50.0, 0.1, 6.0);
    private final NumberSetting fov = new NumberSetting("FOV", 10, 180, 1, 90);
    private final DecimalSetting range = new DecimalSetting("Range", 2.0, 8.0, 0.1, 4.5);
    private final BooleanSetting onlyOnClick = new BooleanSetting("Only On Click", false);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", true);

    // --- Checks (replaces 9 individual booleans) ---
    public enum CheckMode { OFF("Off"), BASIC("Basic"), STRICT("Strict"); private final String label; CheckMode(String l){label=l;} public String toString(){return label;} }
    private final EnumSetting<CheckMode> checkMode = new EnumSetting<>("Check Mode", CheckMode.values(), CheckMode.STRICT);
    private final BooleanSetting teamCheck = new BooleanSetting("Team Check", true);
    private final BooleanSetting friendCheck = new BooleanSetting("Friend Check", true);
    private final BooleanSetting antiBot = new BooleanSetting("AntiBot", true);
    private final BooleanSetting raycast = new BooleanSetting("RayCast Validation", true);
    private final BooleanSetting moveFix = new BooleanSetting("Move Fix", true);
    private final BooleanSetting skipFrames = new BooleanSetting("Random Skip Frames", true);
    private final NumberSetting skipChance = new NumberSetting("Skip Chance %", 0, 30, 1, 7);
    private final BooleanSetting speedRandom = new BooleanSetting("Speed Randomization", true);
    private final NumberSetting speedRandMin = new NumberSetting("Speed Rand Min %", 50, 100, 1, 50);
    private final NumberSetting speedRandMax = new NumberSetting("Speed Rand Max %", 100, 200, 1, 130);

    // --- Noise (merged axisNoise + directionNoise) ---
    private final BooleanSetting noise = new BooleanSetting("Noise", true);
    private final NumberSetting noisePercent = new NumberSetting("Noise %", 0, 50, 1, 25);

    private final BooleanSetting smoothing = new BooleanSetting("Smoothing", true);
    private final NumberSetting smoothFactor = new NumberSetting("Smooth Factor %", 10, 90, 1, 60);
    private final BooleanSetting targetInvis = new BooleanSetting("Target Invisibles", false);

    // --- Target Prediction ---
    private final BooleanSetting targetPrediction = new BooleanSetting("Target Prediction", true);
    private final NumberSetting predictionLatency = new NumberSetting("Prediction Latency (ticks)", 0, 10, 1, 2);
    private final Random rng = new Random();
    private final RotationState rotationState;
    private EntityLivingBase currentTarget;
    private long lastClickTime = 0;
    private boolean wasMouseDown = false;
    private boolean wasClicking = false;
    private boolean wasTargetLocked = false;
    private long lastStepNanos = 0;

    // --- Smoothly drifting speed multiplier (Req 3) ---
    private float speedMultiplier = 1.0F;

    // --- Per-engagement aim-point offset, normalized to half-extents (Req 5) ---
    private EntityLivingBase aimOffsetTarget = null;
    private double aimOffNormX = 0.0D, aimOffNormY = 0.0D, aimOffNormZ = 0.0D;
    private boolean aimOffsetValid = false;

    // --- Acquisition flag: setTarget once, updateTarget thereafter (replaces isFirstLockFrame, inverse sense) ---
    private boolean aimStateInitialized = false;

    // --- World-change detection (Req 8.3) ---
    private int lastDimensionId = Integer.MIN_VALUE;
    private net.minecraft.world.World lastWorld = null;

    // --- Aim-point randomization scale, read at use-time by resolveAimRotations (Req 5) ---
    private static final double AIM_POINT_RANDOMIZATION = 0.15D;

    // Reflection helper for PlayerControllerMP.curBlockDamageMP (field_78781_i)
    private Field curBlockDamageField = null;

    public AimAssistModule() {
        super("AimAssist", "Smoothly nudges your aim toward nearby players.", Category.LEGIT, Keyboard.KEY_NONE);
        this.rotationState = new RotationState(15.0F, 30.0F, rng);

        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(fov);
        addSetting(range);
        addSetting(onlyOnClick);
        addSetting(weaponOnly);
        addSetting(checkMode);
        addSetting(teamCheck);
        addSetting(friendCheck);
        addSetting(antiBot);
        addSetting(raycast);
        addSetting(moveFix);
        addSetting(skipFrames);
        addSetting(skipChance);
        addSetting(speedRandom);
        addSetting(speedRandMin);
        addSetting(speedRandMax);
        addSetting(noise);
        addSetting(noisePercent);
        addSetting(smoothing);
        addSetting(smoothFactor);
        addSetting(targetInvis);
        addSetting(targetPrediction);
        addSetting(predictionLatency);
    }

    @Override
    protected void onEnable() {
        // Reset all engagement/easing state BEFORE any rotation is emitted (Req 8.1).
        currentTarget = null;
        lastClickTime = 0;
        wasMouseDown = false;
        wasClicking = false;
        wasTargetLocked = false;
        lastStepNanos = 0;
        aimStateInitialized = false;
        aimOffsetValid = false;
        aimOffsetTarget = null;
        rotationState.reset();

        // Cache the current world/dimension so the first onClientTick does not spuriously
        // treat the active world as a change. If no player exists yet, leave the sentinels
        // (lastWorld = null, lastDimensionId = Integer.MIN_VALUE) so the first tick resets
        // cleanly (Req 8.3).
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null) {
            lastWorld = mc.theWorld;
            lastDimensionId = mc.thePlayer.dimension;
        }

        // Initialize the drifting speed multiplier (Req 3.4): 1.0 when in range, else the
        // nearest configured bound.
        float min = (float) (speedRandMin.getValue() / 100.0);
        float max = (float) (speedRandMax.getValue() / 100.0);
        speedMultiplier = MathHelper.clamp_float(1.0F, min, max);
    }

    @Override
    protected void onDisable() {
        // Clear engagement/easing state on disable (Req 8.2).
        currentTarget = null;
        speedMultiplier = 1.0F;
        aimOffsetValid = false;
        aimOffsetTarget = null;
        aimStateInitialized = false;
        rotationState.reset();
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;

        // World / dimension change detection (Req 8.3): clear all engagement state and emit
        // no rotation on the changing tick.
        if (mc.theWorld != lastWorld || mc.thePlayer.dimension != lastDimensionId) {
            currentTarget = null;
            aimStateInitialized = false;
            aimOffsetValid = false;
            aimOffsetTarget = null;
            rotationState.reset();
            lastWorld = mc.theWorld;
            lastDimensionId = mc.thePlayer.dimension;
            return;
        }

        if (!shouldAssist(mc)) {
            currentTarget = null;
            wasClicking = false;
            return;
        }

        boolean mouseDown = Mouse.isButtonDown(0);

        if (onlyOnClick.isEnabled()) {
            if (mouseDown && !wasMouseDown) {
                lastClickTime = System.currentTimeMillis();
                wasClicking = true;
            } else if (!mouseDown && wasMouseDown) {
                // Mouse released — STOP IMMEDIATELY (no resetTime delay)
                wasClicking = false;
                currentTarget = null;
                wasTargetLocked = false;
                aimStateInitialized = false;
                lastClickTime = 0;
                wasMouseDown = mouseDown;
                return;
            }
        } else {
            // When onlyOnClick is disabled, still respect mouse release immediately
            if (!mouseDown && wasMouseDown) {
                wasClicking = false;
                currentTarget = null;
                wasTargetLocked = false;
                aimStateInitialized = false;
                lastClickTime = 0;
                wasMouseDown = mouseDown;
                return;
            }
            if (mouseDown) {
                wasClicking = true;
                if (lastClickTime == 0) {
                    lastClickTime = System.currentTimeMillis();
                }
            }
        }

        wasMouseDown = mouseDown;

        // If not clicking, stop immediately
        if (!wasClicking) {
            currentTarget = null;
            return;
        }

        if (currentTarget != null) {
            if (!isValidTarget(mc, currentTarget, range.getValue()) ||
                getAngleToTarget(mc, currentTarget) > fov.getValue() ||
                (raycast.isEnabled() && !isTargetVisible(mc, currentTarget))) {
                currentTarget = null;
                aimStateInitialized = false;
            }
        }

        if (currentTarget == null || wasClicking && !wasTargetLocked) {
            EntityLivingBase newTarget = findTarget(mc);
            if (newTarget != null) {
                currentTarget = newTarget;
                wasTargetLocked = true;
                aimStateInitialized = false;
            }
        } else {
            wasTargetLocked = true;
        }

        if (currentTarget == null) {
            return;
        }

        // --- Common prologue: resolve aim, sync settings, anchor/redirect, dispatch ---
        float[] targetRots = resolveAimRotations(mc, currentTarget);
        if (targetRots == null) return;

        updateSpeedMultiplier();
        syncRotationStateConfig();

        if (!aimStateInitialized) {
            rotationState.setTarget(targetRots[0], targetRots[1],
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            aimStateInitialized = true;
        } else {
            rotationState.updateTarget(targetRots[0], targetRots[1]);
        }

        applyPlayerRotations(mc);
    }

    /**
     * PLAYER mode: consume one easing step from {@link #rotationState} and apply it via
     * {@link MouseGcdHelper#rotateBy} so deltas are GCD-aligned and look like real mouse
     * input to anti-cheats.
     *
     * <p>All easing (smoothing, per-axis noise, acceleration limiting, micro-drift) lives in
     * {@link RotationState}; this method only converts the stepped absolute rotation into a
     * GCD-aligned delta and performs the correct render-interpolation handoff by setting
     * {@code prev*} to the pre-delta camera rotation (Req 1.1/1.2). A non-finite step output
     * skips the tick with {@code prev}/{@code current} preserved (Req 1.5/4.4).
     */
    private void applyPlayerRotations(Minecraft mc) {
        float dt = calculateDeltaTime();
        float[] out = rotationState.step(dt);
        if (out == null || isNonFinite(out[0]) || isNonFinite(out[1])) {
            // Req 1.5 / 4.4: skip this tick, preserve camera & prev rotations.
            return;
        }

        float beforeYaw = mc.thePlayer.rotationYaw;
        float beforePitch = mc.thePlayer.rotationPitch;
        float yawDelta = MathHelper.wrapAngleTo180_float(out[0] - beforeYaw);
        float pitchDelta = out[1] - beforePitch;

        // GCD-aligned camera move (anti-cheat bypass).
        MouseGcdHelper.rotateBy(mc, yawDelta, pitchDelta);

        // Correct render-interpolation handoff: prev = rotation BEFORE this tick's delta so
        // the renderer interpolates monotonically prev -> current across partialTicks (Req 1.1/1.2).
        mc.thePlayer.prevRotationYaw = beforeYaw;
        mc.thePlayer.prevRotationPitch = beforePitch;

        if (moveFix.isEnabled()) {
            ClientRotationHelper.get().fixMovementInputs();
        }
    }

    private static boolean isNonFinite(float x) {
        return Float.isNaN(x) || Float.isInfinite(x);
    }

    /**
     * Target selection by crosshair proximity (not distance).
     * Only selects targets that are visible (not behind walls) if raycast is enabled.
     */
    private EntityLivingBase findTarget(Minecraft mc) {
        double maxDist = range.getValue();
        EntityLivingBase best = null;
        double bestScore = Double.MAX_VALUE;

        for (Object obj : mc.theWorld.loadedEntityList) {
            if (!(obj instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) obj;

            if (!isValidTarget(mc, entity, maxDist)) continue;

            // Skip targets behind walls if raycast is enabled
            if (raycast.isEnabled() && !isTargetVisible(mc, entity)) continue;

            float[] rot = getRotationsToEntity(mc, entity);
            if (rot == null) continue;

            float yd = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - mc.thePlayer.rotationYaw));
            float pd = Math.abs(rot[1] - mc.thePlayer.rotationPitch);

            // Only consider targets within FOV
            float angle = (float) Math.sqrt(yd * yd + pd * pd);
            if (angle > fov.getValue()) continue;

            // Score: weighted combination of yaw and pitch distance from crosshair
            double score = yd * yd + pd * pd * 0.5D;

            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    private float[] getRotationsToEntity(Minecraft mc, EntityLivingBase entity) {
        if (entity == null || mc.thePlayer == null) return null;

        // Target prediction
        if (targetPrediction.isEnabled()) {
            int latencyTicks = predictionLatency.getValue();
            if (latencyTicks > 0) {
                float[] predicted = TargetPredictor.predictRotations(
                    entity, latencyTicks, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch
                );
                if (predicted != null) return predicted;
            }
        }

        double diffX = entity.posX - mc.thePlayer.posX;
        double diffZ = entity.posZ - mc.thePlayer.posZ;
        double diffY = entity.posY + entity.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.atan2(diffZ, diffX) * 180.0F / (float) Math.PI - 90.0F;
        float pitch = (float) (-Math.atan2(diffY, dist)) * 180.0F / (float) Math.PI;

        return new float[]{yaw, pitch};
    }

    /**
     * Resolves target rotations using a per-engagement hitbox aim point (Req 5).
     *
     * <p>The aim point is drawn once per engagement via
     * {@link KillAuraRotationUtils#getAimPoint} (gaussian hitbox randomization, Req 5.1)
     * and cached as a normalized offset relative to the box center (X/Z) and eye-center
     * height (Y). Because the normalized offset stays constant while the same entity is
     * locked, the aim-point contribution to the inter-tick yaw/pitch delta is zero, which
     * satisfies the consecutive-tick stability bound (Req 5.4). A different target
     * invalidates the cached offset and recomputes (Req 5.5).
     *
     * <p>Settings are read at use-time. Degenerate boxes or a null aim point fall back to
     * the eye center and retain the previously valid rotation (Req 5.6).
     */
    private float[] resolveAimRotations(Minecraft mc, EntityLivingBase entity) {
        if (entity == null || mc.thePlayer == null) return null;

        // Target changed -> recompute the offset for the new target (Req 5.5).
        if (entity != aimOffsetTarget) {
            aimOffsetTarget = entity;
            aimOffsetValid = false;
        }

        // Target prediction stays as-is: when enabled it short-circuits to the predicted
        // eye-line rotation just like the legacy path.
        if (targetPrediction.isEnabled()) {
            int latencyTicks = predictionLatency.getValue();
            if (latencyTicks > 0) {
                float[] predicted = TargetPredictor.predictRotations(
                    entity, latencyTicks, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch
                );
                if (predicted != null) return predicted;
            }
        }

        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        double centerX = (bb.minX + bb.maxX) / 2.0D;
        double centerZ = (bb.minZ + bb.maxZ) / 2.0D;
        double eyeCenterY = entity.posY + entity.getEyeHeight();
        double halfX = (bb.maxX - bb.minX) * 0.5D;
        double halfY = (bb.maxY - bb.minY) * 0.5D;
        double halfZ = (bb.maxZ - bb.minZ) * 0.5D;

        // Degenerate box -> eye-center fallback (Req 5.6).
        if (halfX <= 0.0D || halfY <= 0.0D || halfZ <= 0.0D) {
            return rotationsToEyeCenter(mc, bb, eyeCenterY);
        }

        if (!aimOffsetValid) {
            // One gaussian draw per engagement (Req 5.1). Multipoint 0 keeps the pre-noise
            // base at the box center / eye height, so a zero gaussian offset resolves to the
            // eye center (Req 5.3); getAimPoint already clamps the point inside bb (Req 5.2).
            Vec3 p = KillAuraRotationUtils.getAimPoint(entity, 0.0D, 0.0D, AIM_POINT_RANDOMIZATION, rng);
            if (p == null) {
                return rotationsToEyeCenter(mc, bb, eyeCenterY);
            }
            aimOffNormX = (p.xCoord - centerX) / halfX;
            aimOffNormY = (p.yCoord - eyeCenterY) / halfY;
            aimOffNormZ = (p.zCoord - centerZ) / halfZ;
            aimOffsetValid = true;
        }

        // Reconstruct the absolute aim point from the cached normalized offset so it tracks
        // the moving box, clamped inside bb (Req 5.2).
        double px = MathHelper.clamp_double(centerX + aimOffNormX * halfX, bb.minX, bb.maxX);
        double py = MathHelper.clamp_double(eyeCenterY + aimOffNormY * halfY, bb.minY, bb.maxY);
        double pz = MathHelper.clamp_double(centerZ + aimOffNormZ * halfZ, bb.minZ, bb.maxZ);

        return KillAuraRotationUtils.getRotationsToPoint(px, py, pz,
            mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    /**
     * Eye-center fallback rotation (Req 5.6): rotations to the box-center X/Z at eye-height Y.
     */
    private float[] rotationsToEyeCenter(Minecraft mc, AxisAlignedBB bb, double eyeCenterY) {
        if (mc.thePlayer == null) return null;
        double centerX = (bb.minX + bb.maxX) / 2.0D;
        double centerZ = (bb.minZ + bb.maxZ) / 2.0D;
        return KillAuraRotationUtils.getRotationsToPoint(centerX, eyeCenterY, centerZ,
            mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    private boolean isValidTarget(Minecraft mc, EntityLivingBase entity, double maxDist) {
        if (!(entity instanceof EntityPlayer)) return false;
        if (entity == mc.thePlayer) return false;
        if (entity.isDead) return false;
        if (entity.getHealth() <= 0) return false;
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).capabilities.isCreativeMode) return false;
        if (!targetInvis.isEnabled() && entity.isInvisible()) return false;
        AntiBotModule antiBotModule = LionClient.getInstance() != null ? LionClient.getInstance().getModuleManager().getModule(AntiBotModule.class) : null;
        if (antiBotModule != null && antiBotModule.isEnabled() && antiBotModule.isBot((EntityPlayer) entity)) return false;
        if (friendCheck.isEnabled() && LionClient.isFriend(entity.getName())) return false;
        if (teamCheck.isEnabled()) {
            if (entity instanceof EntityPlayer && isTeamMate(mc, (EntityPlayer) entity)) return false;
        }
        double dist = mc.thePlayer.getDistanceToEntity(entity);
        return dist <= maxDist;
    }

    private boolean isTargetVisible(Minecraft mc, EntityLivingBase entity) {
        return mc.thePlayer.canEntityBeSeen(entity);
    }

    private boolean shouldAssist(Minecraft mc) {
        // Don't assist when a GUI is open (inventory, menu, etc.)
        if (mc.currentScreen != null) return false;
        if (weaponOnly.isEnabled()) {
            ItemStack held = mc.thePlayer.getHeldItem();
            if (held == null) return false;
            Item item = held.getItem();
            if (!(item instanceof ItemSword || item instanceof ItemAxe || item instanceof ItemPickaxe || item instanceof ItemTool)) return false;
        }

        // Unified checks via CheckMode enum
        CheckMode mode = checkMode.getValue();
        if (mode != CheckMode.OFF) {
            // BASIC: sprint + onGround + not eating/sneaking
            // STRICT: all checks
            if (!mc.thePlayer.isSprinting()) return false;
            if (!mc.thePlayer.onGround) return false;
            if (mc.thePlayer.isUsingItem()) return false;
            if (mc.thePlayer.isSneaking()) return false;

            if (mode == CheckMode.STRICT) {
                if (mc.thePlayer.isInWater()) return false;
                if (mc.thePlayer.isInLava()) return false;
                if (mc.thePlayer.isOnLadder()) return false;
                boolean hasBad = mc.thePlayer.isPotionActive(Potion.poison) || mc.thePlayer.isPotionActive(Potion.wither);
                if (hasBad) return false;
                if (mc.playerController != null) {
                    try {
                        if (curBlockDamageField == null) {
                            curBlockDamageField = ReflectionHelper.findField(PlayerControllerMP.class, "field_78781_i", "curBlockDamageMP");
                            curBlockDamageField.setAccessible(true);
                        }
                        float curBlockDamage = curBlockDamageField.getFloat(mc.playerController);
                        if (curBlockDamage > 0.0F) return false;
                    } catch (Exception e) {
                        // Ignore reflection errors, fall back to no check
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns the combined angular distance from player's current look to the target.
     * Uses proper Euclidean angle, not Math.max.
     */
    private float getAngleToTarget(Minecraft mc, EntityLivingBase entity) {
        float[] rots = getRotationsToEntity(mc, entity);
        if (rots == null) return 180F;
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rots[0] - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(rots[1] - mc.thePlayer.rotationPitch);
        // Proper combined angle
        return (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    /**
     * Drifts {@link #speedMultiplier} smoothly between ticks (Req 3).
     *
     * <p>When speed randomization is disabled the multiplier is pinned to {@code 1.0}
     * (Req 3.5). Otherwise it drifts incrementally from the previous tick's value by a
     * step bounded to {@code [-0.05, +0.05]} (Req 3.1, 3.2) and is clamped into the
     * runtime {@code [min%, max%]} range, which also pulls a stale value back into range
     * when the settings change mid-engagement (Req 3.3). Settings are read at use-time.
     */
    private void updateSpeedMultiplier() {
        if (!speedRandom.isEnabled()) {
            speedMultiplier = 1.0F;
            return;
        }
        float min = (float) (speedRandMin.getValue() / 100.0);
        float max = (float) (speedRandMax.getValue() / 100.0);
        speedMultiplier += (rng.nextFloat() - 0.5F) * 0.1F;
        speedMultiplier = MathHelper.clamp_float(speedMultiplier, min, max);
    }

    /**
     * Pushes the current speed and noise settings into {@link #rotationState} each tick so an
     * in-game slider change takes effect on the next assisted tick without reconstructing or
     * restarting the helper (Req 2.3, 2.5, 7.5). All settings are read at use-time; this method
     * only calls the {@code RotationState} runtime setters and never rebuilds the helper.
     *
     * <p>Per-axis speed is {@code horizontalSpeed}/{@code verticalSpeed} (range 0.5..50.0) scaled
     * by the drifting {@link #speedMultiplier} (Req 3), then folded with the smoothing settings:
     * <ul>
     *   <li><b>Smoothing disabled</b> &rArr; hand the maximum effective speed {@code (30, 30)} so
     *       the cursor converges almost immediately, still routed through {@code RotationState}
     *       (Req 2.2 — there is no alternate non-helper smoothing path).</li>
     *   <li><b>Smoothing enabled</b> &rArr; scale the per-axis speed by
     *       {@code smoothScale = 1.0 - smoothFactor/100}, read at use-time. Higher Smooth Factor %
     *       yields a smaller effective speed (smoother/slower convergence): Smooth Factor 10%
     *       &rArr; 0.9x, 90% &rArr; 0.1x of the configured speed.</li>
     * </ul>
     *
     * <p>{@code RotationState.setSpeed} clamps each axis to {@code [1.0, 30.0]}, so a small
     * {@code speed * multiplier * smoothScale} product is floored at 1.0 — that is acceptable; the
     * helper simply converges at its minimum speed.
     */
    private void syncRotationStateConfig() {
        // Noise -> per-axis randomization percent (0 when Noise is disabled). Read at use-time.
        rotationState.setRandomizationPercent(noise.isEnabled() ? noisePercent.getValue() : 0f);

        if (!smoothing.isEnabled()) {
            // Smoothing off: max effective speed so the cursor snaps to target almost immediately,
            // still through the single RotationState easing authority (no alternate path).
            rotationState.setSpeed(30.0F, 30.0F);
            return;
        }

        // Higher Smooth Factor % => lower effective speed (smoother). smoothFactor range is 10..90,
        // so smoothScale spans 0.9 (least smoothing) down to 0.1 (most smoothing).
        float smoothScale = 1.0F - smoothFactor.getValue() / 100.0F;
        rotationState.setSpeed(
            (float) (horizontalSpeed.getValue() * speedMultiplier) * smoothScale,
            (float) (verticalSpeed.getValue() * speedMultiplier) * smoothScale);
    }

    private float calculateDeltaTime() {
        long now = System.nanoTime();
        if (lastStepNanos == 0) lastStepNanos = now;
        float dt = (now - lastStepNanos) / 1_000_000_000.0F;
        lastStepNanos = now;
        if (dt < 0.001F) dt = 0.001F;
        if (dt > 0.1F) dt = 0.1F;
        return dt;
    }

    /**
     * Team check for Hypixel BedWars — two methods:
     * 1. Scoreboard team: checks if both players are on the same scoreboard team
     * 2. Name color prefix: checks if both players have the same color code in their display name
     * 
     * Wrapped in try-catch because scoreboard can be in inconsistent state during packet processing
     * (vanilla 1.8.9 race condition in NetHandlerPlayClient).
     */
    private boolean isTeamMate(Minecraft mc, EntityPlayer other) {
        if (mc == null || mc.thePlayer == null || other == null) return false;

        try {
            // Method 1: Scoreboard team check
            net.minecraft.scoreboard.Team myTeam = mc.thePlayer.getTeam();
            net.minecraft.scoreboard.Team theirTeam = other.getTeam();
            
            // Additional null checks for scoreboard race condition
            if (myTeam != null && theirTeam != null) {
                // Check registered name to handle team object identity issues
                String myTeamName = myTeam.getRegisteredName();
                String theirTeamName = theirTeam.getRegisteredName();
                if (myTeamName != null && myTeamName.equals(theirTeamName)) {
                    return true;
                }
                // Fallback to object equality
                if (myTeam == theirTeam) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Scoreboard in invalid state (vanilla race condition), fall back to color prefix
        }

        // Method 2: Name color prefix check (Hypixel BedWars uses colored name prefixes)
        try {
            String myPrefix = getColorPrefix(mc.thePlayer);
            String theirPrefix = getColorPrefix(other);
            if (myPrefix != null && myPrefix.equals(theirPrefix) && !myPrefix.isEmpty()) {
                return true;
            }
        } catch (Exception e) {
            // Ignore formatting errors
        }

        return false;
    }

    /**
     * Extracts the first color code (§X) from a player's display name.
     * On Hypixel BedWars, teammates share the same color prefix (§c, §a, §b, etc.)
     */
    private String getColorPrefix(EntityPlayer player) {
        if (player.getDisplayName() == null) return null;
        String formatted = player.getDisplayName().getFormattedText();
        if (formatted == null || formatted.length() < 2) return null;
        for (int i = 0; i < formatted.length() - 1; i++) {
            if (formatted.charAt(i) == '\u00a7') {
                char code = formatted.charAt(i + 1);
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    return "\u00a7" + code;
                }
            }
        }
        return null;
    }
}
