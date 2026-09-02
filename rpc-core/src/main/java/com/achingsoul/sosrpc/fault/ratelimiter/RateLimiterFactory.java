package com.achingsoul.sosrpc.fault.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches one rate limiter for each service and configuration.
 */
public class RateLimiterFactory {

    private static final Map<String, RateLimiter> RATE_LIMITER_CACHE =
            new ConcurrentHashMap<>();

    public static RateLimiter getInstance(
            String serviceName, int capacity, double refillTokensPerSecond) {
        String cacheKey = String.format(
                "%s:%d:%s", serviceName, capacity, refillTokensPerSecond);
        return RATE_LIMITER_CACHE.computeIfAbsent(cacheKey,
                key -> new TokenBucketRateLimiter(
                        serviceName, capacity, refillTokensPerSecond));
    }
}
