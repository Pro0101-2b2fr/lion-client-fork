package com.lionclient.feature.module.impl;

import com.lionclient.network.PacketDelayManager;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import org.lwjgl.input.Keyboard;

/**
 * FakeLag — holds outgoing position packets then releases them in a burst.
 *
 * Legit behavior explanation:
 * - Packets are delayed by 100-500ms to simulate real connection jitter.
 * - Position packets (C03) are the only ones held — attack/Swing packets
 *   are selectively held based on the mode.
 * - The server sees the player "freeze" then teleport to the new position,
 *   making it extremely hard for opponents to track and combo.
 *
 * Anti-cheat bypass:
 * - Most anti-cheats (Watchdog, Vulcan) tolerate up to ~500ms of lag.
 * - Packets are released in the correct order with proper timing jitter.
 * - C00 (confirm transaction) and C0F (confirm window click) are NEVER held
 *   — holding those always flags.
 * - The lagSpike mode mimics real network jitter (short burst, long pause).
 */
public final class FakeLagModule extends Module {
    private static final Random RNG = new Random();

    public enum Mode {
        /** Hold all outgoing packets for a fixed delay — simple and safe. */
        STATIC("Static"),
        /** Vary the hold time randomly between min/max — more legit-looking. */
        DYNAMIC("Dynamic"),
        /** Send normally for 2-3 seconds, then hold for 300-800ms — mimics lag spikes. */
        LAG_SPIKE("Lag Spike"),
        /** Only hold position packets (C03), let others through — transparent mode. */
        POSITION_ONLY("Position Only");

        final String displayName;
        Mode(String displayName) { this.displayName = displayName; }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.LAG_SPIKE);
    // Milliseconds to hold packets — 150-400 is the "safe" zone for most anti-cheats
    private final NumberSetting delayMs = new NumberSetting("Delay", 50, 800, 25, 250);
    private final NumberSetting minDelayMs = new NumberSetting("Min Delay", 50, 800, 25, 100);
    private final NumberSetting maxDelayMs = new NumberSetting("Max Delay", 50, 800, 25, 400);
    // For LAG_SPIKE mode: how long between spikes (ticks)
    private final NumberSetting spikeInterval = new NumberSetting("Spike Interval", 20, 200, 10, 80);
    // For LAG_SPIKE mode: how long each spike lasts (ms)
    private final NumberSetting spikeDuration = new NumberSetting("Spike Duration", 100, 1500, 50, 400);
    // Release all packets when toggling off (prevents permanent freeze)
    private final BooleanSetting releaseOnDisable = new BooleanSetting("Release On Disable", true);

    private int spikeTicksRemaining;
    private volatile boolean currentlyHolding = false;

    public FakeLagModule() {
        super("FakeLag", "Delays outgoing packets to simulate lag.", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(delayMs);
        addSetting(minDelayMs);
        addSetting(maxDelayMs);
        addSetting(spikeInterval);
        addSetting(spikeDuration);
        addSetting(releaseOnDisable);

        // Show/hide settings based on mode
        java.util.function.BooleanSupplier staticVisible = () -> mode.getValue() == Mode.STATIC;
        java.util.function.BooleanSupplier dynamicVisible = () -> mode.getValue() == Mode.DYNAMIC;
        java.util.function.BooleanSupplier spikeVisible = () -> mode.getValue() == Mode.LAG_SPIKE;
        delayMs.setVisibility(staticVisible);
        minDelayMs.setVisibility(dynamicVisible);
        maxDelayMs.setVisibility(dynamicVisible);
        spikeInterval.setVisibility(spikeVisible);
        spikeDuration.setVisibility(spikeVisible);
    }

    @Override
    protected void onEnable() {
        // Don't enable in singleplayer — there's no network to delay packets
        if (Minecraft.getMinecraft().isSingleplayer()) {
            setEnabled(false);
            return;
        }
        spikeTicksRemaining = spikeInterval.getValue();
        currentlyHolding = false;
    }

    @Override
    protected void onDisable() {
        currentlyHolding = false;
        // Immediately flush all queued packets so the player doesn't get stuck
        if (releaseOnDisable.isEnabled()) {
            PacketDelayManager manager = PacketDelayManager.getInstance();
            if (manager != null) {
                manager.flushAll();
            }
        }
    }

    @Override
    public void onClientTick() {
        if (mode.getValue() == Mode.LAG_SPIKE) {
            // LAG_SPIKE is handled per-tick: count down, then hold for spikeDuration
            if (currentlyHolding) {
                spikeTicksRemaining--;
                if (spikeTicksRemaining <= 0) {
                    currentlyHolding = false;
                    spikeTicksRemaining = spikeInterval.getValue();
                }
            } else {
                spikeTicksRemaining--;
                if (spikeTicksRemaining <= 0) {
                    currentlyHolding = true;
                    spikeTicksRemaining = (int) (spikeDuration.getValue() / 50); // convert ms to ticks (~50ms/tick)
                }
            }
        }
    }

    /**
     * Returns the outbound packet delay in ms for this module.
     * PacketDelayManager calls this for every outgoing packet.
     */
    @Override
    public int getOutboundPacketDelay(Packet<?> packet) {
        // Never hold C00 (confirm transaction) — holding this always flags on anti-cheats
        if (packet instanceof net.minecraft.network.play.client.C00PacketKeepAlive) return 0;
        if (packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction) return 0;

        switch (mode.getValue()) {
            case STATIC:
                return delayMs.getValue();

            case DYNAMIC:
                int min = minDelayMs.getValue();
                int max = maxDelayMs.getValue();
                if (min >= max) return min;
                return min + RNG.nextInt(max - min + 1);

            case LAG_SPIKE:
                return currentlyHolding ? spikeDuration.getValue() : 0;

            case POSITION_ONLY:
                // Only hold C03 (player position) packets
                if (packet instanceof C03PacketPlayer) {
                    return delayMs.getValue();
                }
                return 0;

            default:
                return 0;
        }
    }

    @Override
    public boolean isPacketDelayActive() {
        return true;
    }

    @Override
    public boolean consumeFlushRequest() {
        // Release on disable if enabled
        return isEnabled() && releaseOnDisable.isEnabled();
    }

    @Override
    public String getHudInfo() {
        switch (mode.getValue()) {
            case STATIC:
                return delayMs.getValue() + "ms";
            case DYNAMIC:
                return minDelayMs.getValue() + "-" + maxDelayMs.getValue() + "ms";
            case LAG_SPIKE:
                return currentlyHolding ? "HOLD " + spikeDuration.getValue() + "ms" : "OK";
            case POSITION_ONLY:
                return "POS " + delayMs.getValue() + "ms";
            default:
                return "";
        }
    }
}
