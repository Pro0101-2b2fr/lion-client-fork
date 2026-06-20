package com.lionclient.feature.module.impl;

import com.lionclient.combat.ClientRotationHelper;
import com.lionclient.combat.KillAuraRotationUtils;
import com.lionclient.combat.RotationState;
import com.lionclient.combat.TargetPredictor;
import com.lionclient.event.ClientRotationEvent;
import com.lionclient.event.EventBus;
import com.lionclient.event.IEventListener;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.impl.FakePlayerModule;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.MouseButtonHelper;
import com.lionclient.util.HumanClickTimer;
import com.lionclient.LionClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KillAuraModule extends Module {

    private final DecimalSetting targetCps = new DecimalSetting("Target CPS", 1.0D, 20.0D, 0.5D, 17.0D);
    private final DecimalSetting minCps = new DecimalSetting("Min CPS", 1.0D, 20.0D, 0.5D, 10.0D);
    private final DecimalSetting maxCps = new DecimalSetting("Max CPS", 1.0D, 20.0D, 0.5D, 17.0D);

    // --- Point 3: Rotation randomization settings ---
    private final BooleanSetting rotationRandomization = new BooleanSetting("Rotation Randomization", true);
    private final DecimalSetting rotationRandPercent = new DecimalSetting("Rotation Rand %", 0.0D, 30.0D, 1.0D, 12.0D);

    // --- Point 4: Aim point randomization ---
    private final BooleanSetting aimPointRandomization = new BooleanSetting("Aim Point Randomization", true);
    private final DecimalSetting aimPointRandAmount = new DecimalSetting("Aim Point Rand", 0.0D, 0.3D, 0.01D, 0.12D);

    // --- Point 5: Miss simulation ---
    private final BooleanSetting missSimulation = new BooleanSetting("Miss Simulation", true);
    private final DecimalSetting missChance = new DecimalSetting("Miss Chance", 0.0D, 0.15D, 0.01D, 0.04D);
    private final DecimalSetting missMinDistance = new DecimalSetting("Miss Min Distance", 0.0D, 3.0D, 0.1D, 2.5D);

    // --- Silent rotation settings ---
    private final BooleanSetting silentAim = new BooleanSetting("Silent Aim", true);
    private final DecimalSetting silentSpeed = new DecimalSetting("Silent Speed", 1.0D, 30.0D, 1.0D, 12.0D);
    private final BooleanSetting moveFix = new BooleanSetting("Move Fix", true);

    private final DecimalSetting attackRange = new DecimalSetting("Range (Attack)", 3.0D, 6.0D, 0.05D, 4.2D);
    private final DecimalSetting swingRange = new DecimalSetting("Range (Swing)", 3.0D, 8.0D, 0.05D, 4.5D);
    private final DecimalSetting aimRange = new DecimalSetting("Range (Aim)", 3.0D, 8.0D, 0.05D, 4.5D);
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 50, 1000, 25, 50);
    private final NumberSetting targets = new NumberSetting("Targets", 1, 10, 1, 3);
    private final BooleanSetting targetInvis = new BooleanSetting("Target Invis", true);
    private final BooleanSetting hitThroughEntities = new BooleanSetting("Hit Through Entities", false);
    private final BooleanSetting disableInInventory = new BooleanSetting("Disable In Inventory", true);
    private final BooleanSetting disableWhileMining = new BooleanSetting("Disable While Mining", true);
    private final BooleanSetting notUsingItem = new BooleanSetting("Not Using Item", false);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", false);

    // --- Target Prediction ---
    private final BooleanSetting targetPrediction = new BooleanSetting("Target Prediction", true);
    private final NumberSetting predictionLatency = new NumberSetting("Prediction Latency (ticks)", 0, 10, 1, 2);

    private final Map<Integer, Integer> hitMap = new HashMap<Integer, Integer>();
    private final Random random = new Random();
    private final HumanClickTimer clickTimer = new HumanClickTimer();
    private final RotationState rotationState;
    private final java.lang.reflect.Field pointedEntityField;
    private final IEventListener<ClientRotationEvent> rotationListener = this::onClientRotation;

    private EntityLivingBase target;
    private EntityLivingBase attackingEntity;
    private double targetDistance = Double.MAX_VALUE;

    // Attack timing
    private long nextClickTime;
    private float originalYawOnAimStart = Float.NaN;
    private float originalPitchOnAimStart = Float.NaN;
    private long lastTickNanos = 0;

    public KillAuraModule() {
        super("KillAura", "Automatically attacks enemies.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(targetCps);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(rotationRandomization);
        addSetting(rotationRandPercent);
        addSetting(aimPointRandomization);
        addSetting(aimPointRandAmount);
        addSetting(missSimulation);
        addSetting(missChance);
        addSetting(missMinDistance);
        addSetting(silentAim);
        addSetting(silentSpeed);
        addSetting(moveFix);
        addSetting(attackRange);
        addSetting(swingRange);
        addSetting(aimRange);
        addSetting(switchDelay);
        addSetting(targets);
        addSetting(targetInvis);
        addSetting(hitThroughEntities);
        addSetting(disableInInventory);
        addSetting(disableWhileMining);
        addSetting(notUsingItem);
        addSetting(weaponOnly);
        addSetting(targetPrediction);
        addSetting(predictionLatency);
        pointedEntityField = findRendererField("field_78528_u", "pointedEntity");

        // Per-module rotation state — not shared with other modules
        rotationState = new RotationState(
            (float) silentSpeed.getValue(),
            (float) rotationRandPercent.getValue(),
            random
        );
    }

    @Override
    protected void onEnable() {
        hitMap.clear();
        clearTargetState();
        EventBus.getInstance().register(ClientRotationEvent.class, rotationListener);
    }

    @Override
    protected void onDisable() {
        EventBus.getInstance().unregister(ClientRotationEvent.class, rotationListener);
        hitMap.clear();
        clearTargetState();
        clickTimer.reset();
        ClientRotationHelper.get().clearRequestedRotations();
    }

    private void onClientRotation(ClientRotationEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!basicCondition(minecraft) || !settingCondition(minecraft)) {
            clearTargetState();
            return;
        }

        float deltaTime = calculateDeltaTime();

        rotationState.setSpeed((float) silentSpeed.getValue());
        rotationState.setRandomizationPercent(
            rotationRandomization.isEnabled() ? (float) rotationRandPercent.getValue() : 0.0F);

        handleTarget(minecraft);
        if (target == null) {
            attackingEntity = null;
            if (!Float.isNaN(originalYawOnAimStart) && silentAim.isEnabled()) {
                rotationState.beginReturn(originalYawOnAimStart, originalPitchOnAimStart);
                float[] returned = rotationState.step(deltaTime);
                event.yaw = Float.valueOf(returned[0]);
                event.pitch = Float.valueOf(returned[1]);
                if (rotationState.hasReturned(0.5F)) {
                    clearTargetState();
                }
            }
            return;
        }

        targetDistance = KillAuraRotationUtils.distanceFromEyeToClosestOnAABB(target);
        attackingEntity = targetDistance <= attackRange.getValue() ? target : null;

        double aimRangeValue = aimRange.getValue();
        if (targetDistance > aimRangeValue) {
            if (!Float.isNaN(originalYawOnAimStart) && silentAim.isEnabled()) {
                rotationState.beginReturn(originalYawOnAimStart, originalPitchOnAimStart);
                float[] returned = rotationState.step(deltaTime);
                event.yaw = Float.valueOf(returned[0]);
                event.pitch = Float.valueOf(returned[1]);
                if (rotationState.hasReturned(0.5F)) {
                    clearTargetState();
                }
            }
            attackingEntity = null;
            return;
        }

        float baseYaw = event.yaw != null ? event.yaw.floatValue() : resolveBaseYaw(minecraft);
        float basePitch = event.pitch != null ? event.pitch.floatValue() : resolveBasePitch(minecraft);

        float[] predictedRotations = null;
        if (targetPrediction.isEnabled() && target instanceof EntityLivingBase) {
            int latencyTicks = predictionLatency.getValue();
            if (latencyTicks > 0) {
                predictedRotations = TargetPredictor.predictRotations(
                    (EntityLivingBase) target, latencyTicks, baseYaw, basePitch
                );
            }
        }

        float[] rotations;
        if (predictedRotations != null) {
            rotations = predictedRotations;
        } else {
            rotations = KillAuraRotationUtils.getRotationsWithBackup(
                target, 100.0D, 100.0D, baseYaw, basePitch,
                aimRangeValue, false, hitThroughEntities.isEnabled(),
                aimPointRandomization.isEnabled() ? aimPointRandAmount.getValue() : 0.0D,
                rotationRandomization.isEnabled(),
                (float) rotationRandPercent.getValue(), random
            );
        }
        if (rotations == null) return;

        if (Float.isNaN(originalYawOnAimStart)) {
            originalYawOnAimStart = baseYaw;
            originalPitchOnAimStart = basePitch;
        }

        if (silentAim.isEnabled()) {
            // Silent aim: smooth rotation via RotationState, apply to server via event
            rotationState.setTarget(rotations[0], rotations[1], baseYaw, basePitch);
            float[] smooth = rotationState.step(deltaTime);
            event.yaw = Float.valueOf(smooth[0]);
            event.pitch = Float.valueOf(smooth[1]);
        }
        // Normal aim: do NOT touch event — let the client handle visible rotations naturally
    }

    @Override
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || target == null) return;

        // Recalculate distance each tick (not stale from rotation event)
        double currentDistance = KillAuraRotationUtils.distanceFromEyeToClosestOnAABB(target);
        if (currentDistance > swingRange.getValue()) return;

        // Check conditions
        if (!basicCondition(mc) || !settingCondition(mc)) return;
        if (notUsingItem.isEnabled() && mc.thePlayer.isUsingItem()) return;

        // Only land hits within attack range (swingRange is the wider "engage"
        // range used for rotations/raytrace).
        if (currentDistance > attackRange.getValue()) return;

        // Raytrace check — don't attack through walls
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 targetCenter = new Vec3(target.posX, target.posY + target.getEyeHeight() * 0.5, target.posZ);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyes, targetCenter, false, true, false);
        if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }

        // CPS-gated: one reliable attack per ready tick.
        long now = System.currentTimeMillis();
        if (nextClickTime == 0L) nextClickTime = now;
        if (now < nextClickTime) return;
        nextClickTime = now + clickTimer.nextDelayMs(
            (int) Math.round(minCps.getValue()),
            (int) Math.round(maxCps.getValue()));

        // Miss simulation — skip this hit but keep the timing pattern.
        if (missSimulation.isEnabled()
            && currentDistance >= missMinDistance.getValue()
            && random.nextFloat() < missChance.getValue()) {
            return;
        }

        // Direct attack — reliable and independent of objectMouseOver / keybind
        // state. The previous KeyBinding.onTick + mouse-over-override path was
        // what caused the aura to "not attack". Swing (C0A) then UseEntity (C02).
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        ClickPatternVisualizerModule.recordClick();
    }

    // Keep for compatibility (some mods might call it)
    public void onClientTick() {
        // Delegate to the event-based version
        onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
    }

    public boolean shouldOverrideMouseOver() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return isEnabled()
            && basicCondition(minecraft)
            && attackingEntity != null
            && target == attackingEntity
            && targetDistance <= swingRange.getValue();
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (!shouldOverrideMouseOver()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Entity viewEntity = minecraft.getRenderViewEntity();
        if (viewEntity == null) {
            return;
        }

        Vec3 eyes = viewEntity.getPositionEyes(partialTicks);
        Vec3 look = viewEntity.getLook(partialTicks);
        double reach = attackRange.getValue();
        Vec3 rayEnd = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);

        float border = attackingEntity.getCollisionBorderSize();
        AxisAlignedBB bb = attackingEntity.getEntityBoundingBox().expand(border, border, border);
        MovingObjectPosition intercept = bb.calculateIntercept(eyes, rayEnd);
        boolean inside = bb.isVecInside(eyes);
        if (!inside && intercept == null) {
            return;
        }

        Vec3 hitVec = inside ? (intercept == null ? eyes : intercept.hitVec) : intercept.hitVec;
        MovingObjectPosition blockHit = minecraft.theWorld.rayTraceBlocks(eyes, hitVec, false, false, true);
        if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        if (!hitThroughEntities.isEnabled() && KillAuraRotationUtils.isPathBlockedByEntity(eyes, hitVec, attackingEntity)) {
            return;
        }

        minecraft.objectMouseOver = new MovingObjectPosition(attackingEntity, hitVec);
        minecraft.pointedEntity = attackingEntity;
        if (pointedEntityField != null) {
            try {
                pointedEntityField.set(minecraft.entityRenderer, attackingEntity);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private void handleTarget(Minecraft minecraft) {
        double maxRange = Math.max(attackRange.getValue(), aimRange.getValue());
        List<KillAuraTarget> candidates = new ArrayList<KillAuraTarget>();
        for (Object object : minecraft.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) object;

            // Skip FakePlayer
            if (player instanceof net.minecraft.client.entity.EntityOtherPlayerMP) {
                // Check if this is the FakePlayer by entity ID
                com.lionclient.feature.module.impl.FakePlayerModule fakePlayer =
                    getFakePlayerModule();
                if (fakePlayer != null && fakePlayer.getFakePlayer() != null
                    && player.getEntityId() == fakePlayer.getFakePlayer().getEntityId()) {
                    continue;
                }
            }

            Candidate candidate = getCandidateTarget(minecraft, player, maxRange);
            if (candidate == null) {
                continue;
            }

            KillAuraTarget auraTarget = buildKillAuraTarget(candidate.entity, candidate.distance, maxRange);
            if (auraTarget != null) {
                candidates.add(auraTarget);
            }
        }

        Collections.sort(candidates, Comparator.comparingDouble(new java.util.function.ToDoubleFunction<KillAuraTarget>() {
            @Override
            public double applyAsDouble(KillAuraTarget value) {
                return value.health;
            }
        }).thenComparingDouble(new java.util.function.ToDoubleFunction<KillAuraTarget>() {
            @Override
            public double applyAsDouble(KillAuraTarget value) {
                return value.distance;
            }
        }));

        double attackRangeValue = attackRange.getValue();
        List<KillAuraTarget> attackTargets = new ArrayList<KillAuraTarget>();
        for (KillAuraTarget candidate : candidates) {
            if (candidate.distance <= attackRangeValue) {
                attackTargets.add(candidate);
            }
        }

        if (!attackTargets.isEmpty()) {
            KillAuraTarget selectedAttackTarget = selectAttackTarget(minecraft, attackTargets);
            if (selectedAttackTarget != null) {
                setTarget(selectedAttackTarget.entity);
                return;
            }
            return;
        }

        if (!candidates.isEmpty()) {
            setTarget(candidates.get(0).entity);
            return;
        }

        setTarget(null);
    }

    private Candidate getCandidateTarget(Minecraft minecraft, EntityPlayer player, double maxRange) {
        if (player == minecraft.thePlayer || player.isDead) {
            return null;
        }

        if (player.deathTime != 0 || player.getHealth() <= 0.0F || AntiBotModule.shouldIgnore(player)) {
            return null;
        }
        if (player.isInvisible() && !targetInvis.isEnabled()) {
            return null;
        }

        double distance = KillAuraRotationUtils.distanceFromEyeToClosestOnAABB(player);
        if (distance > maxRange) {
            return null;
        }

        return new Candidate(player, distance);
    }

    private KillAuraTarget buildKillAuraTarget(EntityLivingBase entity, double distanceToBoundingBox, double maxRange) {
        if (!KillAuraRotationUtils.hasValidAimPoint(entity, 100.0D, 100.0D, maxRange, false, hitThroughEntities.isEnabled())) {
            return null;
        }

        return new KillAuraTarget(entity, distanceToBoundingBox, entity.getHealth(), entity.getEntityId());
    }

    private KillAuraTarget selectAttackTarget(Minecraft minecraft, List<KillAuraTarget> attackTargets) {
        int ticksExisted = minecraft.thePlayer.ticksExisted;
        int switchDelayTicks = Math.max(1, switchDelay.getValue() / 50);

        // Purge stale entries to prevent unbounded growth.
        if (hitMap.size() > 50) {
            java.util.Iterator<java.util.Map.Entry<Integer, Integer>> it = hitMap.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Integer, Integer> entry = it.next();
                if (ticksExisted - entry.getValue().intValue() > switchDelayTicks * 10) {
                    it.remove();
                }
            }
        }

        // First pass: find a target we've already hit but whose switch delay has expired
        for (KillAuraTarget candidate : attackTargets) {
            Integer firstHitTick = hitMap.get(Integer.valueOf(candidate.entityId));
            if (firstHitTick != null && ticksExisted - firstHitTick.intValue() >= switchDelayTicks) {
                return candidate;
            }
        }

        // Second pass: find a fresh target we haven't hit recently
        for (KillAuraTarget candidate : attackTargets) {
            Integer firstHitTick = hitMap.get(Integer.valueOf(candidate.entityId));
            if (firstHitTick == null || ticksExisted - firstHitTick.intValue() >= switchDelayTicks) {
                hitMap.put(Integer.valueOf(candidate.entityId), Integer.valueOf(ticksExisted));
                return candidate;
            }
        }

        // Fallback: if ALL targets are in the hit map and none have expired,
        // pick the one that was hit longest ago. This prevents the aura
        // from doing nothing when multiple targets are in range.
        KillAuraTarget oldestTarget = null;
        int oldestTick = Integer.MAX_VALUE;
        for (KillAuraTarget candidate : attackTargets) {
            Integer firstHitTick = hitMap.get(Integer.valueOf(candidate.entityId));
            if (firstHitTick != null && firstHitTick.intValue() < oldestTick) {
                oldestTick = firstHitTick.intValue();
                oldestTarget = candidate;
            }
        }
        if (oldestTarget != null) {
            hitMap.put(Integer.valueOf(oldestTarget.entityId), Integer.valueOf(ticksExisted));
            return oldestTarget;
        }

        return null;
    }

    private boolean basicCondition(Minecraft minecraft) {
        return minecraft != null
            && minecraft.thePlayer != null
            && minecraft.theWorld != null
            && !minecraft.thePlayer.isDead;
    }

    private boolean settingCondition(Minecraft minecraft) {
        if (disableInInventory.isEnabled() && minecraft.currentScreen != null) {
            return false;
        }
        if (weaponOnly.isEnabled() && !isHoldingWeapon(minecraft)) {
            return false;
        }
        return !disableWhileMining.isEnabled() || !isMining(minecraft);
    }

    private boolean isHoldingWeapon(Minecraft minecraft) {
        if (minecraft.thePlayer == null || minecraft.thePlayer.getHeldItem() == null) {
            return false;
        }

        Item item = minecraft.thePlayer.getHeldItem().getItem();
        return item instanceof ItemSword || item == Items.stick;
    }

    private boolean isMining(Minecraft minecraft) {
        int keyCode = minecraft.gameSettings.keyBindAttack.getKeyCode();
        if (keyCode == 0) {
            return false;
        }

        boolean attackDown = keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
        if (!attackDown) {
            return false;
        }

        double reach = minecraft.playerController.getBlockReachDistance();
        Vec3 eyes = minecraft.thePlayer.getPositionEyes(1.0F);
        Vec3 look = KillAuraRotationUtils.getVectorForRotation(minecraft.thePlayer.rotationPitch, minecraft.thePlayer.rotationYaw);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        if (rayTraceEntity(minecraft, eyes, end) != null) {
            return false;
        }

        MovingObjectPosition blockHit = minecraft.theWorld.rayTraceBlocks(eyes, end, false, false, false);
        return blockHit != null
            && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && blockHit.getBlockPos() != null;
    }

    private Entity rayTraceEntity(Minecraft minecraft, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        EntityPlayer player = minecraft.thePlayer;
        AxisAlignedBB searchBox = player.getEntityBoundingBox().addCoord(delta.xCoord, delta.yCoord, delta.zCoord).expand(1.0D, 1.0D, 1.0D);
        List<?> entities = minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(player, searchBox);
        Entity closestEntity = null;
        double closestDistance = end.distanceTo(start);

        for (Object object : entities) {
            if (!(object instanceof Entity)) {
                continue;
            }

            Entity entity = (Entity) object;
            if (entity == player || !entity.canBeCollidedWith()) {
                continue;
            }

            float border = entity.getCollisionBorderSize();
            AxisAlignedBB bb = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition hit = bb.calculateIntercept(start, end);
            if (bb.isVecInside(start)) {
                return entity;
            }
            if (hit == null) {
                continue;
            }

            double distance = start.distanceTo(hit.hitVec);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    public EntityLivingBase getTarget() {
        return target;
    }

    private void setTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            clearTargetState();
            return;
        }

        target = (EntityLivingBase) entity;
    }

    private void clearTargetState() {
        target = null;
        attackingEntity = null;
        targetDistance = Double.MAX_VALUE;
        nextClickTime = 0L;
        originalYawOnAimStart = Float.NaN;
        originalPitchOnAimStart = Float.NaN;
        rotationState.reset();
    }

    private float resolveBaseYaw(Minecraft minecraft) {
        return Float.isNaN(KillAuraRotationUtils.serverRotations[0]) ? minecraft.thePlayer.rotationYaw : KillAuraRotationUtils.serverRotations[0];
    }

    private float resolveBasePitch(Minecraft minecraft) {
        return Float.isNaN(KillAuraRotationUtils.serverRotations[1]) ? minecraft.thePlayer.rotationPitch : KillAuraRotationUtils.serverRotations[1];
    }

    private float calculateDeltaTime() {
        long now = System.nanoTime();
        if (lastTickNanos == 0L) {
            lastTickNanos = now;
            return 0.016F; // Default ~60fps on first call
        }
        float dt = (now - lastTickNanos) / 1_000_000_000.0F;
        lastTickNanos = now;
        // Clamp to avoid huge jumps after alt-tab or lag spikes
        if (dt < 0.001F) dt = 0.001F;
        if (dt > 0.1F) dt = 0.1F;
        return dt;
    }

    private static java.lang.reflect.Field findRendererField(String... names) {
        try {
            java.lang.reflect.Field field = ReflectionHelper.findField(EntityRenderer.class, names);
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static FakePlayerModule getFakePlayerModule() {
        LionClient client = LionClient.getInstance();
        if (client == null) return null;
        for (Module m : client.getModuleManager().getModules()) {
            if (m instanceof FakePlayerModule) {
                return (FakePlayerModule) m;
            }
        }
        return null;
    }

    private static final class Candidate {
        private final EntityLivingBase entity;
        private final double distance;

        private Candidate(EntityLivingBase entity, double distance) {
            this.entity = entity;
            this.distance = distance;
        }
    }

    private static final class KillAuraTarget {
        private final EntityLivingBase entity;
        private final double distance;
        private final float health;
        private final int entityId;

        private KillAuraTarget(EntityLivingBase entity, double distance, float health, int entityId) {
            this.entity = entity;
            this.distance = distance;
            this.health = health;
            this.entityId = entityId;
        }
    }
}
