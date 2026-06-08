package com.lionclient.event;

public interface IEventListener<T> {
    void onEvent(T event);
}
