package com.achingsoul.sosrpc.fault.ratelimiter;

/**
 * Thrown when a request is rejected by the rate limiter.
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
