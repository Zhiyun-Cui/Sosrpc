package com.achingsoul.sosrpc.registry;

import com.achingsoul.sosrpc.spi.SpiLoader;

/**
 * RegistryFactory, to obtain registry instance.
 */
public class RegistryFactory {

    static {
        SpiLoader.load(Registry.class);
    }

    /**
     * default registry
     */
    private static final Registry DEFAULT_REGISTRY = new EtcdRegistry();

    /**
     * get instantce
     *
     * @param key
     * @return
     */
    public static Registry getInstance(String key) {
        return SpiLoader.getInstance(Registry.class, key);
    }

}
