package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.util.HumanClickTimer;
import com.lionclient.util.MouseGcdHelper;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class AutoClickerModule extends Module {
    private final Random random = new Random();
    private final HumanClickTimer clickTimer = new HumanClickTimer();
    private final Method guiClickMethod;
    private final Field leftClickCounterField;

    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.NORMAL);
    private final BooleanSetting breakBlocks = new BooleanSetting("Break Blocks", true);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", false);
    private final BooleanSetting inventoryFill = new BooleanSetting("Inventory Fill", false);
    private final NumberSetting minCps = new NumberSetting("Min CPS", 1, 25, 1, 17);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 1, 25, 1, 22);
    private final NumberSetting jitterStrength = new NumberSetting("Jitter", 0, 10, 1, 0);

    private long lastClick;
    private long holdUntil;
    private long recordNextClickTime;
    private int recordIndex;
    private boolean leftDown;
    private boolean recordNoticeShown;
    private Mode lastMode;

    public AutoClickerModule() {
        super("LeftClicker", "Automatically left-clicks for you, use mode Record for strict anticheats like Polar.", Category.COMBAT, Keyboard.KEY_NONE);
        guiClickMethod = findGuiClickMethod();
        leftClickCounterField = findLeftClickCounterField();
        minCps.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() != Mode.RECORD;
            }
        });
        maxCps.setVisibility(new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return mode.getValue() != Mode.RECORD;
            }
        });
        addSetting(mode);
        addSetting(breakBlocks);
        addSetting(weaponOnly);
        addSetting(inventoryFill);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(jitterStrength);
    }

    @Override
    protected void onEnable() {
        resetClickState();
    }

    @Override
    protected void onDisable() {
        resetClickState();
    }

    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }

        removeClickDelay(minecraft);
        normalizeRanges();

        if (mode.getValue() != lastMode) {
            resetClickState();
            lastMode = mode.getValue();
        }

        if (minecraft.currentScreen != null || !minecraft.inGameHasFocus) {
            doInventoryClick(minecraft);
            return;
        }

        Mouse.poll();
        if (!Mouse.isButtonDown(0)) {
            resetPhysicalState();
            return;
        }

        if (weaponOnly.isEnabled() && !isHoldingWeapon(minecraft)) {
            resetPhysicalState();
            return;
        }

        if (breakBlock(minecraft)) {
            return;
        }

        applyJitter(minecraft);

        if (mode.getValue() == Mode.RECORD) {
            recordClick();
            return;
        }

        normalClick();
    }

    private void normalClick() {
        long delay = clickTimer.nextDelayMs(minCps.getValue(), maxCps.getValue());
        long now = System.currentTimeMillis();

        if (now - lastClick >= delay) {
            lastClick = now;
            holdUntil = now + clickTimer.nextHoldLengthMs(delay);
            sendClick(true);
            leftDown = true;
        } else if (leftDown && now >= holdUntil) {
            sendClick(false);
            leftDown = false;
        }
    }

    private void recordClick() {
        List<Integer> delays = ClickPatternStore.getDelays();
        if (delays.isEmpty()) {
            if (!recordNoticeShown) {
                sendChat("No recorded pattern. Use ClickRecorder in CLIENT first.");
                recordNoticeShown = true;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (recordNextClickTime < 0L) {
            recordNextClickTime = now;
        }

        if (now < recordNextClickTime) {
            return;
        }

        sendClick(true);
        sendClick(false);

        recordIndex++;
        if (recordIndex >= delays.size()) {
            recordIndex = 0;
        }

        recordNextClickTime = now + Math.max(0, delays.get(recordIndex).intValue());
        recordNoticeShown = false;
    }

    private void sendClick(boolean pressed) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int key = minecraft.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.setKeyBindState(key, pressed);
        setMouseButtonState(0, pressed);
        if (pressed) {
            KeyBinding.onTick(key);
        }
    }

    private boolean breakBlock(Minecraft minecraft) {
        MovingObjectPosition hitResult = minecraft.objectMouseOver;
        if (!breakBlocks.isEnabled() || hitResult == null || hitResult.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }

        // Hand control back to vanilla while mining a block. The player's
        // physical mouse already holds LMB, so vanilla Minecraft.runTick()
        // emits the correct dig / animation packets. Re-pressing through
        // KeyBinding.onTick at render-tick rate (~60 Hz) caused a packet
        // flood ("too many packets") and stuttered breaks.
        return true;
    }

    private void doInventoryClick(Minecraft minecraft) {
        if (!inventoryFill.isEnabled()) {
            return;
        }

        if (!(minecraft.currentScreen instanceof GuiInventory) && !(minecraft.currentScreen instanceof GuiChest)) {
            return;
        }

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        if (!Mouse.isButtonDown(0) || !shiftDown) {
            resetClickState();
            return;
        }

        long now = System.currentTimeMillis();
        long delay = clickTimer.nextDelayMs(minCps.getValue(), maxCps.getValue());
        if (now - lastClick < delay) {
            return;
        }

        lastClick = now;
        inInventoryClick(minecraft.currentScreen, minecraft);
    }

    private void inInventoryClick(GuiScreen guiScreen, Minecraft minecraft) {
        int mouseX = Mouse.getX() * guiScreen.width / minecraft.displayWidth;
        int mouseY = guiScreen.height - Mouse.getY() * guiScreen.height / minecraft.displayHeight - 1;

        try {
            guiClickMethod.invoke(guiScreen, Integer.valueOf(mouseX), Integer.valueOf(mouseY), Integer.valueOf(0));
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private void applyJitter(Minecraft minecraft) {
        int strength = jitterStrength.getValue();
        if (strength <= 0) {
            return;
        }
        float yawDelta = (random.nextBoolean() ? 1 : -1) * random.nextFloat() * (strength * 0.45F);
        float pitchDelta = (random.nextBoolean() ? 1 : -1) * random.nextFloat() * (strength * 0.2F);
        MouseGcdHelper.rotateBy(minecraft, yawDelta, pitchDelta);
    }

    private boolean isHoldingWeapon(Minecraft minecraft) {
        if (minecraft.thePlayer.getHeldItem() == null) {
            return false;
        }

        String name = minecraft.thePlayer.getHeldItem().getUnlocalizedName();
        return name != null && (name.contains("sword") || name.contains("axe"));
    }

    private void setMouseButtonState(int mouseButton, boolean held) {
        MouseEvent event = new MouseEvent();
        ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Integer.valueOf(mouseButton), "button");
        ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Boolean.valueOf(held), "buttonstate");
        MinecraftForge.EVENT_BUS.post(event);

        ByteBuffer buttons = ObfuscationReflectionHelper.getPrivateValue(Mouse.class, null, "buttons");
        if (buttons != null && buttons.capacity() > mouseButton) {
            buttons.put(mouseButton, (byte) (held ? 1 : 0));
            ObfuscationReflectionHelper.setPrivateValue(Mouse.class, null, buttons, "buttons");
        }
    }

    private void removeClickDelay(Minecraft minecraft) {
        if (leftClickCounterField == null || !minecraft.inGameHasFocus || minecraft.thePlayer.capabilities.isCreativeMode) {
            return;
        }

        // Only clear the post-click cooldown when we're aiming at an entity or
        // empty space. While the player is targeting a block we must let
        // vanilla handle the timing - resetting the counter every render tick
        // turns the legitimate dig cycle into a packet flood ("too many
        // packets" on Hypixel) and breaks the mining animation.
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }

        try {
            leftClickCounterField.setInt(minecraft, 0);
        } catch (IllegalAccessException ignored) {
        }
    }

    private void resetClickState() {
        lastClick = 0L;
        holdUntil = 0L;
        recordIndex = 0;
        recordNextClickTime = -1L;
        recordNoticeShown = false;
        clickTimer.reset();
        resetPhysicalState();
    }

    private void resetPhysicalState() {
        leftDown = false;
        sendClick(false);
    }

    private void normalizeRanges() {
        if (maxCps.getValue() < minCps.getValue()) {
            maxCps.setManualValue(minCps.getValue());
        }
    }

    private float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    private void sendChat(String text) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText("[AutoClicker] " + text));
        }
    }

    private Method findGuiClickMethod() {
        try {
            Method method = ReflectionHelper.findMethod(
                GuiScreen.class,
                null,
                new String[]{"func_73864_a", "mouseClicked"},
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE
            );
            if (method != null) {
                method.setAccessible(true);
            }
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Field findLeftClickCounterField() {
        try {
            Field field = ReflectionHelper.findField(Minecraft.class, "field_71429_W", "leftClickCounter");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private enum Mode {
        NORMAL,
        RECORD
    }
}
