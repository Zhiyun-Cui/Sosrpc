package com.achingsoul.sosrpc.fault.circuitbreaker;

/**
 * Circuit breaker states.
 */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
