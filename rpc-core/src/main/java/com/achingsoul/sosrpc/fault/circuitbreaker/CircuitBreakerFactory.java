package com.achingsoul.sosrpc.fault.circuitbreaker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches one circuit breaker for each service and configuration.
 */
public class CircuitBreakerFactory {

    private static final Map<String, CircuitBreaker> CIRCUIT_BREAKER_CACHE =
            new ConcurrentHashMap<>();

    public static CircuitBreaker getInstance(
            String serviceName,
            int failureThreshold,
            long openDurationMillis,
            int halfOpenSuccessThreshold) {
        String cacheKey = String.format("%s:%d:%d:%d",
                serviceName,
                failureThreshold,
                openDurationMillis,
                halfOpenSuccessThreshold);
        return CIRCUIT_BREAKER_CACHE.computeIfAbsent(cacheKey,
                key -> new SimpleCircuitBreaker(
                        serviceName,
                        failureThreshold,
                        openDurationMillis,
                        halfOpenSuccessThreshold));
    }
}
