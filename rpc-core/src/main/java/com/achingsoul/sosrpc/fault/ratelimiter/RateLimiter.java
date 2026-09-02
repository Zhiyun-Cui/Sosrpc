package com.achingsoul.sosrpc.fault.ratelimiter;

/**
 * Consumer-side rate limiter.
 */
public interface RateLimiter {

    /**
     * Try to acquire permission for one request.
     */
    boolean allowRequest();
}
