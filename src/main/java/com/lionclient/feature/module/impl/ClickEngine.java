package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.module.ModuleManager;
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
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Shared click engine used by AutoClickerModule (left) and RightClickerModule (right).
 * Handles both NORMAL and RECORD modes, jitter, weapon blocking, and inventory fill.
 *
 * <p>Thread-safety: all methods are called from the render thread only.</p>
 */
final class ClickEngine {

    // ── Reflection caches (computed once) ────────────────────────────────
    static final Method GUI_CLICK_METHOD;
    static final Field LEFT_CLICK_COUNTER_FIELD;

    static {
        Method m = null;
        try {
            m = ReflectionHelper.findMethod(
                GuiScreen.class, null,
                new String[]{"func_73864_a", "mouseClicked"},
                Integer.TYPE, Integer.TYPE, Integer.TYPE
            );
            if (m != null) m.setAccessible(true);
        } catch (Exception ignored) { }
        GUI_CLICK_METHOD = m;

        Field f = null;
        try {
            f = ReflectionHelper.findField(Minecraft.class, "field_71429_W", "leftClickCounter");
            if (f != null) f.setAccessible(true);
        } catch (Exception ignored) { }
        LEFT_CLICK_COUNTER_FIELD = f;
    }

    // ── Per-instance state ────────────────────────────────────────────────
    private final Random random = new Random();
    private final HumanClickTimer clickTimer = new HumanClickTimer();

    // Configuration
    private final Module module;
    private final int mouseButton;          // 0 = left, 1 = right
    private final String chatPrefix;

    // Settings (cached references for hot path)
    private final EnumSetting<Mode> mode;
    private final BooleanSetting breakBlocks;
    private final BooleanSetting weaponOnly;
    private final BooleanSetting onlyBlocks;
    private final BooleanSetting inventoryFill;
    private final NumberSetting minCps;
    private final NumberSetting maxCps;
    private final NumberSetting jitterStrength;

    // Tick state
    private long lastClick;
    private long holdUntil;
    private long recordNextClickTime;
    private int recordIndex;
    private boolean buttonDown;
    private boolean recordNoticeShown;
    private Mode lastMode;

    ClickEngine(Module module, int mouseButton, String chatPrefix,
                EnumSetting<Mode> mode,
                BooleanSetting breakBlocks, BooleanSetting weaponOnly,
                BooleanSetting onlyBlocks, BooleanSetting inventoryFill,
                NumberSetting minCps, NumberSetting maxCps,
                NumberSetting jitterStrength) {
        this.module = module;
        this.mouseButton = mouseButton;
        this.chatPrefix = chatPrefix;
        this.mode = mode;
        this.breakBlocks = breakBlocks;
        this.weaponOnly = weaponOnly;
        this.onlyBlocks = onlyBlocks;
        this.inventoryFill = inventoryFill;
        this.minCps = minCps;
        this.maxCps = maxCps;
        this.jitterStrength = jitterStrength;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    void onEnable() {
        resetClickState();
    }

    void onDisable() {
        resetClickState();
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        removeLeftClickDelay(mc);
        normalizeRanges();

        if (mode.getValue() != lastMode) {
            resetClickState();
            lastMode = mode.getValue();
        }

        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            doInventoryClick(mc);
            return;
        }

        Mouse.poll();
        int physicalButton = mouseButton == 0 ? 0 : 1;
        if (!Mouse.isButtonDown(physicalButton)) {
            resetPhysicalState();
            return;
        }

        // Weapon / block filter
        if (weaponOnly != null && weaponOnly.isEnabled() && !isHoldingWeapon(mc)) {
            resetPhysicalState();
            return;
        }
        if (onlyBlocks != null && onlyBlocks.isEnabled() && !isHoldingBlock(mc)) {
            stopAutoClicking(mc);
            return;
        }

        // Break-block guard (left only)
        if (mouseButton == 0 && breakBlock(mc)) return;

        applyJitter(mc);

        if (mode.getValue() == Mode.RECORD) {
            recordClick();
        } else {
            normalClick();
        }
    }

    // ── Normal click ──────────────────────────────────────────────────────

    private void normalClick() {
        long delay = clickTimer.nextDelayMs(minCps.getValue(), maxCps.getValue());
        long now = System.currentTimeMillis();

        if (now - lastClick >= delay) {
            lastClick = now;
            holdUntil = now + clickTimer.nextHoldLengthMs(delay);
            sendClick(true);
            buttonDown = true;
            ClickPatternVisualizerModule.recordClick();
        } else if (buttonDown && now >= holdUntil) {
            sendClick(false);
            buttonDown = false;
        }
    }

    // ── Record click ──────────────────────────────────────────────────────

    private void recordClick() {
        List<Integer> delays = getPatternDelays();
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
        if (now < recordNextClickTime) return;

        sendClick(true);
        sendClick(false);

        recordIndex++;
        if (recordIndex >= delays.size()) recordIndex = 0;
        recordNextClickTime = now + Math.max(0, delays.get(recordIndex).intValue());
        recordNoticeShown = false;
    }

    /** Returns the pattern store. Left-clicker uses the default store;
     *  right-clicker could override — currently both share. */
    List<Integer> getPatternDelays() {
        return ClickPatternStore.getDelays();
    }

    // ── Inventory fill ────────────────────────────────────────────────────

    private void doInventoryClick(Minecraft mc) {
        if (inventoryFill == null || !inventoryFill.isEnabled()) return;
        if (!(mc.currentScreen instanceof GuiInventory) && !(mc.currentScreen instanceof GuiChest)) return;

        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        if (!Mouse.isButtonDown(mouseButton) || !shiftDown) {
            resetClickState();
            return;
        }

        long now = System.currentTimeMillis();
        long delay = clickTimer.nextDelayMs(minCps.getValue(), maxCps.getValue());
        if (now - lastClick < delay) return;
        lastClick = now;

        inInventoryClick(mc.currentScreen, mc);
    }

    private void inInventoryClick(GuiScreen gui, Minecraft mc) {
        if (GUI_CLICK_METHOD == null) return;
        int mouseX = Mouse.getX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
        try {
            GUI_CLICK_METHOD.invoke(gui, Integer.valueOf(mouseX), Integer.valueOf(mouseY), Integer.valueOf(0));
        } catch (IllegalAccessException | InvocationTargetException ignored) { }
    }

    // ── Jitter ────────────────────────────────────────────────────────────

    private void applyJitter(Minecraft mc) {
        int strength = jitterStrength.getValue();
        if (strength <= 0) return;
        float yawDelta = (random.nextBoolean() ? 1 : -1) * random.nextFloat() * (strength * 0.45F);
        float pitchDelta = (random.nextBoolean() ? 1 : -1) * random.nextFloat() * (strength * 0.2F);
        MouseGcdHelper.rotateBy(mc, yawDelta, pitchDelta);
    }

    // ── Mouse / key simulation ────────────────────────────────────────────

    void sendClick(boolean pressed) {
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding binding = mouseButton == 0
            ? mc.gameSettings.keyBindAttack
            : mc.gameSettings.keyBindUseItem;
        int key = binding.getKeyCode();
        KeyBinding.setKeyBindState(key, pressed);
        setMouseButtonState(mouseButton, pressed);
        if (pressed) KeyBinding.onTick(key);
    }

    private void setMouseButtonState(int button, boolean held) {
        MouseEvent event = new MouseEvent();
        ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Integer.valueOf(button), "button");
        ObfuscationReflectionHelper.setPrivateValue(MouseEvent.class, event, Boolean.valueOf(held), "buttonstate");
        MinecraftForge.EVENT_BUS.post(event);

        ByteBuffer buttons = ObfuscationReflectionHelper.getPrivateValue(Mouse.class, null, "buttons");
        if (buttons != null && buttons.capacity() > button) {
            buttons.put(button, (byte) (held ? 1 : 0));
            ObfuscationReflectionHelper.setPrivateValue(Mouse.class, null, buttons, "buttons");
        }
    }

    private void removeLeftClickDelay(Minecraft mc) {
        if (LEFT_CLICK_COUNTER_FIELD == null || !mc.inGameHasFocus || mc.thePlayer.capabilities.isCreativeMode) return;
        if (mouseButton != 0) return; // Only left-clicker uses this
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) return;
        try {
            LEFT_CLICK_COUNTER_FIELD.setInt(mc, 0);
        } catch (IllegalAccessException ignored) { }
    }

    // ── Guards ────────────────────────────────────────────────────────────

    private boolean breakBlock(Minecraft mc) {
        if (breakBlocks == null) return false;
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        return breakBlocks.isEnabled() && hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private boolean isHoldingWeapon(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        String name = held.getUnlocalizedName();
        return name != null && (name.contains("sword") || name.contains("axe"));
    }

    private boolean isHoldingBlock(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    private void stopAutoClicking(Minecraft mc) {
        buttonDown = false;
        if (mouseButton == 1) syncUseItemKeyWithPhysical(mc);
    }

    private void resetClickState() {
        lastClick = 0L;
        holdUntil = 0L;
        recordNextClickTime = -1L;
        recordIndex = 0;
        recordNoticeShown = false;
        clickTimer.reset();
        resetPhysicalState();
    }

    private void resetPhysicalState() {
        buttonDown = false;
        sendClick(false);
    }

    private void syncUseItemKeyWithPhysical(Minecraft mc) {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, Mouse.isButtonDown(1));
    }

    private void normalizeRanges() {
        if (maxCps.getValue() < minCps.getValue()) {
            maxCps.setManualValue(minCps.getValue());
        }
    }

    private void sendChat(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(chatPrefix + text));
        }
    }

    // ── Mode enum ─────────────────────────────────────────────────────────

    enum Mode {
        NORMAL,
        RECORD
    }
}
