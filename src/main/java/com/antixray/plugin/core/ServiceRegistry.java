package com.antixray.plugin.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T instance) {
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type) {
        T instance = (T) services.get(type);
        if (instance == null) {
            throw new IllegalStateException("No service registered for: " + type.getName());
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> T resolveOrNull(Class<T> type) {
        return (T) services.get(type);
    }

    public void unregister(Class<?> type) {
        services.remove(type);
    }

    public void clear() {
        services.clear();
    }
}
