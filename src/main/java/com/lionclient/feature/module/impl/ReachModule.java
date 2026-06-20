package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.MouseButtonHelper;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Keyboard;

public final class ReachModule extends Module {
    private static final double VANILLA_REACH = 3.0D;

    private final DecimalSetting reach = new DecimalSetting("Reach", VANILLA_REACH, 6.0D, 0.1D, VANILLA_REACH);
    private final NumberSetting chance = new NumberSetting("Chance", 0, 100, 1, 100);

    public ReachModule() {
        super("Reach", "Extends attack range. Patched on any decent anticheat.", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(reach);
        addSetting(chance);
    }

    public double getReachValue() {
        return Math.max(VANILLA_REACH, reach.getValue());
    }

    @Override
    public String getHudInfo() {
        double extra = Math.max(0.0D, reach.getValue() - VANILLA_REACH);
        return extra <= 0.001D ? "" : String.format("+%.1f", extra);
    }

    @Override
    public void onMouseEvent(MouseEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.button != 0 || !event.buttonstate) {
            return;
        }
        if (MouseButtonHelper.isDispatchingSyntheticEvent()) {
            return;
        }

        if (minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            return;
        }

        double configuredReach = Math.max(VANILLA_REACH, reach.getValue());
        if (configuredReach <= VANILLA_REACH) {
            return;
        }

        if (Math.random() * 100.0D >= chance.getValue()) {
            return;
        }

        Entity target = rayTraceEntity(minecraft, configuredReach);
        if (!(target instanceof EntityLivingBase) || target == minecraft.thePlayer) {
            return;
        }

        double distance = minecraft.thePlayer.getDistanceToEntity(target);
        if (distance <= VANILLA_REACH || distance > configuredReach) {
            return;
        }

        // CRITICAL FIX: Swing BEFORE attacking.
        // The old code called attackEntity() then swingItem(), which sent
        // C02PacketUseEntity BEFORE C0AAnimationPacket to the server.
        // Anti-cheats see "attack without swing" → BadPackets flag.
        //
        // Correct order:
        // 1. swingItem() → sends C0A (swing animation) to server
        // 2. sendUseEntity() → sends C02 (attack) to server
        // This matches vanilla packet order exactly.
        minecraft.thePlayer.swingItem();
        minecraft.thePlayer.sendQueue.addToSendQueue(
            new net.minecraft.network.play.client.C02PacketUseEntity(
                target, net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK
            )
        );
        event.setCanceled(true);
    }

    private Entity rayTraceEntity(Minecraft minecraft, double reachDistance) {
        EntityPlayer player = minecraft.thePlayer;
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 reachVector = eyes.addVector(look.xCoord * reachDistance, look.yCoord * reachDistance, look.zCoord * reachDistance);
        Entity pointedEntity = null;
        double bestDistance = reachDistance;
        List<?> entities = minecraft.theWorld.getEntitiesWithinAABBExcludingEntity(
            player,
            player.getEntityBoundingBox()
                .addCoord(look.xCoord * reachDistance, look.yCoord * reachDistance, look.zCoord * reachDistance)
                .expand(1.0D, 1.0D, 1.0D)
        );

        for (Object object : entities) {
            if (!(object instanceof Entity)) {
                continue;
            }

            Entity entity = (Entity) object;
            if (!entity.canBeCollidedWith() || entity == player) {
                continue;
            }

            float border = entity.getCollisionBorderSize();
            AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
            MovingObjectPosition intercept = box.calculateIntercept(eyes, reachVector);

            if (box.isVecInside(eyes)) {
                if (bestDistance >= 0.0D) {
                    pointedEntity = entity;
                    bestDistance = 0.0D;
                }
                continue;
            }

            if (intercept == null) {
                continue;
            }

            double distance = eyes.distanceTo(intercept.hitVec);
            if (distance < bestDistance) {
                pointedEntity = entity;
                bestDistance = distance;
            }
        }

        return pointedEntity;
    }
}
