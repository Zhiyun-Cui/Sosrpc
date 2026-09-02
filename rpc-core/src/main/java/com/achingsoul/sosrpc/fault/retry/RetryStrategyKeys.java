package com.achingsoul.sosrpc.fault.retry;

/**
 * Retry strategy keys.
 */
public interface RetryStrategyKeys {

    /**
     * Do not retry.
     */
    String NO = "no";

    /**
     * Retry at a fixed interval.
     */
    String FIXED_INTERVAL = "fixedInterval";

    /**
     * Retry with exponential backoff.
     */
    String EXPONENTIAL_BACKOFF = "exponentialBackoff";
}
