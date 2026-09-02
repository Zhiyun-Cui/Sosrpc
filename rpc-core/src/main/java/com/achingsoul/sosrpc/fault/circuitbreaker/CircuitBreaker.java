package com.achingsoul.sosrpc.fault.circuitbreaker;

/**
 * Consumer-side circuit breaker.
 */
public interface CircuitBreaker {

    boolean allowRequest();

    void recordSuccess();

    void recordFailure();

    CircuitBreakerState getState();
}
