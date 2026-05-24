package com.lionclient.util;

import java.util.Random;

/**
 * Generates click delays shaped like a real human clicker. Designed to defeat
 * Vulcan's statistical checks:
 * <ul>
 *   <li><b>Consistency</b> — variance must be high enough (CV > threshold).</li>
 *   <li><b>Kurtosis</b> — distribution needs heavy tails (outliers).</li>
 *   <li><b>Limit</b> — no single interval may exceed ~20-22 CPS.</li>
 *   <li><b>Spikes</b> — consecutive intervals must not differ too much.</li>
 * </ul>
 *
 * Strategy: drift the target CPS slowly across the configured range, add
 * per-click gaussian noise, and inject occasional <b>slow</b> outliers (never
 * fast ones) to build kurtosis without triggering Limit. Transitions between
 * internal states are smoothed to avoid Spikes.
 */
public final class HumanClickTimer {
    private final Random random = new Random();
    private int burstRemaining;
    private double currentCps;
    private double targetCps;
    private long previousDelay;

    /** Returns the next inter-click delay in milliseconds. */
    public long nextDelayMs(int minCps, int maxCps) {
        int low = Math.max(1, minCps);
        int high = Math.max(low, maxCps);

        // Re-sample target CPS every few clicks.
        if (burstRemaining <= 0) {
            burstRemaining = 5 + random.nextInt(8);
            targetCps = low + random.nextDouble() * (high - low);
        }
        burstRemaining--;

        // Smooth drift toward target (avoids spikes from sudden jumps).
        if (currentCps == 0.0D) {
            currentCps = targetCps;
        } else {
            double driftRate = 0.15D + random.nextDouble() * 0.10D;
            currentCps += (targetCps - currentCps) * driftRate;
        }

        // Per-click gaussian jitter on the CPS.
        double jitteredCps = currentCps + random.nextGaussian() * 0.7D;
        // Hard cap: never exceed maxCps + 2 to avoid Limit flags.
        jitteredCps = Math.max(low - 1.0D, Math.min(high + 2.0D, jitteredCps));
        jitteredCps = Math.max(1.0D, jitteredCps);

        double delayMs = 1000.0D / jitteredCps;

        // Small per-delay gaussian noise (±8% of the delay).
        delayMs += random.nextGaussian() * (delayMs * 0.08D);

        // Slow outliers only (builds kurtosis without triggering Limit).
        // ~8% chance of a moderately slow click, ~3% chance of a very slow one.
        double roll = random.nextDouble();
        if (roll < 0.03D) {
            delayMs += 100.0D + random.nextDouble() * 180.0D;
        } else if (roll < 0.11D) {
            delayMs += 20.0D + random.nextDouble() * 40.0D;
        }

        // Anti-spike: clamp the difference from the previous delay.
        long delay = Math.round(delayMs);
        long minDelay = Math.round(1000.0D / (high + 2.0D));
        delay = Math.max(minDelay, delay);
        if (previousDelay > 0L && delay < 800L) {
            long maxDiff = Math.max(25L, previousDelay / 3L);
            if (delay < previousDelay - maxDiff) {
                delay = previousDelay - maxDiff;
            }
            // Allow slow spikes (they're human-like) but cap extreme ones.
            if (delay > previousDelay + maxDiff * 3L && delay < 300L) {
                delay = previousDelay + maxDiff * 3L;
            }
        }

        delay = Math.max(minDelay, Math.min(1200L, delay));
        previousDelay = delay;
        return delay;
    }

    /**
     * Returns a press hold length in ms. Varies per click to look human.
     */
    public long nextHoldLengthMs(long interClickDelayMs) {
        double base = 35.0D + random.nextGaussian() * 12.0D;
        long hold = Math.round(base);
        return Math.max(15L, Math.min(Math.max(20L, interClickDelayMs - 8L), hold));
    }

    public Random rng() {
        return random;
    }

    public void reset() {
        burstRemaining = 0;
        currentCps = 0.0D;
        targetCps = 0.0D;
        previousDelay = 0L;
    }
}
