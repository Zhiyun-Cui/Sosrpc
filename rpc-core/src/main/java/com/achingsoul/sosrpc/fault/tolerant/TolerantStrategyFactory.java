package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.spi.SpiLoader;

/**
 * Tolerant strategy factory.
 */
public class TolerantStrategyFactory {

    static {
        SpiLoader.load(TolerantStrategy.class);
    }

    /**
     * Default tolerant strategy.
     */
    private static final TolerantStrategy DEFAULT_TOLERANT_STRATEGY =
            new FailFastTolerantStrategy();

    public static TolerantStrategy getInstance(String key) {
        return SpiLoader.getInstance(TolerantStrategy.class, key);
    }
}
