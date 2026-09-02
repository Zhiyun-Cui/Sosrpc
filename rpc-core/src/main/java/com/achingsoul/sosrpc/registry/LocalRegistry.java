package com.achingsoul.sosrpc.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local service registry.
 */
public class LocalRegistry {

    /**
     * Registry storage.
     * Uses ConcurrentHashMap: key is service name, value is implementation class.
     */
    private static final Map<String, Class<?>> map = new ConcurrentHashMap<>();

    /**
    * Register service.
    * @param serviceName service name
    * @param implClass implementation class
    */
    public static void register(String serviceName, Class<?> implClass) {
        map.put(serviceName, implClass);
    }

    /**
    * Get service.
    * @param serviceName service name
    * @return implementation class
    */
    public static Class<?> get(String serviceName) {
        return map.get(serviceName);
    }

    /**
    * Remove service.
    * @param serviceName service name
    */
    public static void remove(String serviceName) {
        map.remove(serviceName);
    }
}
