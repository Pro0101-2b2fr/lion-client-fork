package com.lionclient.feature.module.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe store for recorded click patterns. Accessed from the render
 * thread (ClickRecorder captures), the client tick thread (AutoClicker reads),
 * and the config IO thread (save/load).
 */
public final class ClickPatternStore {
    private static final Object LOCK = new Object();
    private static final List<Integer> delays = new ArrayList<Integer>();

    private ClickPatternStore() {
    }

    public static void clear() {
        synchronized (LOCK) {
            delays.clear();
        }
    }

    public static void addDelay(int delay) {
        synchronized (LOCK) {
            delays.add(Integer.valueOf(Math.max(0, delay)));
        }
    }

    public static boolean isEmpty() {
        synchronized (LOCK) {
            return delays.isEmpty();
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return delays.size();
        }
    }

    public static List<Integer> getDelays() {
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<Integer>(delays));
        }
    }
}
