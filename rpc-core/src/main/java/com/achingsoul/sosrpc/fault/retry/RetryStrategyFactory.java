package com.achingsoul.sosrpc.fault.retry;

import com.achingsoul.sosrpc.spi.SpiLoader;

/**
 * Retry strategy factory.
 */
public class RetryStrategyFactory {

    static {
        SpiLoader.load(RetryStrategy.class);
    }

    /**
     * Default retry strategy.
     */
    private static final RetryStrategy DEFAULT_RETRY_STRATEGY = new NoRetryStrategy();

    /**
     * Get a retry strategy instance by key.
     */
    public static RetryStrategy getInstance(String key) {
        return SpiLoader.getInstance(RetryStrategy.class, key);
    }
}
