package com.lionclient.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventBus {
    private static final EventBus INSTANCE = new EventBus();
    private final Map<Class<?>, List<IEventListener<?>>> listeners = new HashMap<>();

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public <T> void register(Class<T> eventClass, IEventListener<T> listener) {
        listeners.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(listener);
    }

    public <T> void unregister(Class<T> eventClass, IEventListener<T> listener) {
        List<IEventListener<?>> eventListeners = listeners.get(eventClass);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    public <T> void post(T event) {
        List<IEventListener<?>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (IEventListener<?> listener : eventListeners) {
                @SuppressWarnings("unchecked")
                IEventListener<T> typedListener = (IEventListener<T>) listener;
                typedListener.onEvent(event);
            }
        }
    }
}
