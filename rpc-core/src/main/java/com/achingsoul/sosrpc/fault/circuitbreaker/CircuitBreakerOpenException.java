package com.achingsoul.sosrpc.fault.circuitbreaker;

/**
 * Thrown when an open circuit rejects a request.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
