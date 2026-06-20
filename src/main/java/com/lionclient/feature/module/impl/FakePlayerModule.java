package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.FloatSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.UUID;

/**
 * FakePlayer — spawns a fake player entity for testing rotations, reach, etc.
 * The fake player mimics a real player but is fully client-side.
 */
public final class FakePlayerModule extends Module {
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.STATIC);
    private final BooleanSetting copySkin = new BooleanSetting("Copy Skin", true);
    private final BooleanSetting copyName = new BooleanSetting("Copy Name", false);
    private final NumberSetting offsetX = new NumberSetting("Offset X", -20, 20, 1, 3);
    private final NumberSetting offsetY = new NumberSetting("Offset Y", -5, 5, 1, 0);
    private final NumberSetting offsetZ = new NumberSetting("Offset Z", -20, 20, 1, 0);
    private final FloatSetting yawSpeed = new FloatSetting("Yaw Speed", 0.0F, 360.0F, 1.0F, 30.0F);
    private final FloatSetting distance = new FloatSetting("Distance", 0.5F, 10.0F, 0.1F, 3.0F);
    private final FloatSetting height = new FloatSetting("Height", -2.0F, 2.0F, 0.1F, 0.0F);
    private final BooleanSetting showArmor = new BooleanSetting("Show Armor", true);
    private final BooleanSetting invulnerable = new BooleanSetting("Invulnerable", true);

    private EntityOtherPlayerMP fakePlayer;
    private int ticksExisted = 0;
    private float rotationYaw = 0;
    private float rotationPitch = 0;

    public FakePlayerModule() {
        super("FakePlayer", "Spawns a fake player entity for testing.", Category.PLAYER, Keyboard.KEY_NONE);

        yawSpeed.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.ROTATE;
            }
        });
        distance.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.FOLLOW || mode.getValue() == Mode.CIRCLE;
            }
        });
        height.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() == Mode.FOLLOW || mode.getValue() == Mode.CIRCLE;
            }
        });

        addSetting(mode);
        addSetting(copySkin);
        addSetting(copyName);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(offsetZ);
        addSetting(yawSpeed);
        addSetting(distance);
        addSetting(height);
        addSetting(showArmor);
        addSetting(invulnerable);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            setEnabled(false);
            return;
        }

        // Create fake player
        fakePlayer = new EntityOtherPlayerMP(mc.theWorld, getGameProfile(mc));
        fakePlayer.copyLocationAndAnglesFrom(mc.thePlayer);
        fakePlayer.rotationYawHead = mc.thePlayer.rotationYawHead;

        // Store the spawn position — this is where the bot will stay
        double spawnX = mc.thePlayer.posX + offsetX.getValue();
        double spawnY = mc.thePlayer.posY + offsetY.getValue();
        double spawnZ = mc.thePlayer.posZ + offsetZ.getValue();
        float spawnYaw = mc.thePlayer.rotationYaw;
        float spawnPitch = mc.thePlayer.rotationPitch;

        fakePlayer.setPositionAndRotation(spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
        fakePlayer.inventory.copyInventory(mc.thePlayer.inventory);
        fakePlayer.setSprinting(false);
        fakePlayer.setSneaking(false);
        fakePlayer.setEntityId(mc.theWorld.getUniqueDataId("fakeplayer"));

        // Make invulnerable
        if (invulnerable.isEnabled()) {
            fakePlayer.setHealth(Float.MAX_VALUE);
        }

        mc.theWorld.addEntityToWorld(-100, fakePlayer);
        ticksExisted = 0;
        rotationYaw = spawnYaw;
        rotationPitch = spawnPitch;
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (fakePlayer != null && mc.theWorld != null) {
            mc.theWorld.removeEntity(fakePlayer);
            fakePlayer = null;
        }
    }

    @Override
    public void onClientTick() {
        if (fakePlayer == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        ticksExisted++;

        switch (mode.getValue()) {
            case STATIC:
                // Stay at the original spawn position — do NOT recalcuate from player position
                // The fake player was already placed at the correct position in onEnable
                // Just keep resetting to prevent any drift from vanilla entity physics
                fakePlayer.setPositionAndRotation(
                    fakePlayer.posX,
                    fakePlayer.posY,
                    fakePlayer.posZ,
                    rotationYaw,
                    rotationPitch
                );
                break;

            case FOLLOW:
                // Follow player at fixed distance behind
                double behindX = -Math.sin(Math.toRadians(mc.thePlayer.rotationYaw)) * distance.getValue();
                double behindZ = Math.cos(Math.toRadians(mc.thePlayer.rotationYaw)) * distance.getValue();
                fakePlayer.setPositionAndRotation(
                    mc.thePlayer.posX + behindX,
                    mc.thePlayer.posY + height.getValue(),
                    mc.thePlayer.posZ + behindZ,
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch
                );
                break;

            case CIRCLE:
                // Circle around player
                double angle = Math.toRadians(ticksExisted * yawSpeed.getValue());
                double circleX = Math.sin(angle) * distance.getValue();
                double circleZ = Math.cos(angle) * distance.getValue();
                fakePlayer.setPositionAndRotation(
                    mc.thePlayer.posX + circleX,
                    mc.thePlayer.posY + height.getValue(),
                    mc.thePlayer.posZ + circleZ,
                    (float) Math.toDegrees(angle) - 90.0F,
                    0.0F
                );
                break;

            case ROTATE:
                // Rotate in place
                rotationYaw += yawSpeed.getValue();
                rotationYaw = MathHelper.wrapAngleTo180_float(rotationYaw);
                fakePlayer.setPositionAndRotation(
                    mc.thePlayer.posX + offsetX.getValue(),
                    mc.thePlayer.posY + offsetY.getValue(),
                    mc.thePlayer.posZ + offsetZ.getValue(),
                    rotationYaw,
                    rotationPitch
                );
                break;

            case MIRROR:
                // Mirror player movements exactly
                fakePlayer.setPositionAndRotation(
                    mc.thePlayer.posX + offsetX.getValue(),
                    mc.thePlayer.posY + offsetY.getValue(),
                    mc.thePlayer.posZ + offsetZ.getValue(),
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch
                );
                fakePlayer.limbSwing = mc.thePlayer.limbSwing;
                fakePlayer.limbSwingAmount = mc.thePlayer.limbSwingAmount;
                fakePlayer.swingProgress = mc.thePlayer.swingProgress;
                break;
        }

        // Copy armor/inventory if enabled
        if (showArmor.isEnabled()) {
            fakePlayer.inventory.copyInventory(mc.thePlayer.inventory);
        }

        // Keep invulnerable
        if (invulnerable.isEnabled()) {
            fakePlayer.setHealth(Float.MAX_VALUE);
        }
    }

    private com.mojang.authlib.GameProfile getGameProfile(Minecraft mc) {
        String name = copyName.isEnabled() ? mc.thePlayer.getName() : "FakePlayer_" + mc.thePlayer.getUniqueID().toString().substring(0, 4);
        UUID uuid = UUID.randomUUID();
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, name);

        if (copySkin.isEnabled()) {
            // Copy skin from player
            net.minecraft.client.network.NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null && info.getLocationSkin() != null) {
                profile.getProperties().putAll(info.getGameProfile().getProperties());
            }
        }

        return profile;
    }

    public EntityOtherPlayerMP getFakePlayer() {
        return fakePlayer;
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }

    private enum Mode {
        STATIC("Static"),
        FOLLOW("Follow"),
        CIRCLE("Circle"),
        ROTATE("Rotate"),
        MIRROR("Mirror");

        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
}