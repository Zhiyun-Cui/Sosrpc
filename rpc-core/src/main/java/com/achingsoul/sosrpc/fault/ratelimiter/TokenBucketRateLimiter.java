package com.achingsoul.sosrpc.fault.ratelimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * Token-bucket rate limiter.
 */
@Slf4j
public class TokenBucketRateLimiter implements RateLimiter {

    private final String resourceName;

    private final int capacity;

    private final double refillTokensPerSecond;

    private double availableTokens;

    private long lastRefillTimeNanos;

    public TokenBucketRateLimiter(
            String resourceName, int capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limit capacity must be greater than 0");
        }
        if (refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("Rate limit refill rate must be greater than 0");
        }
        this.resourceName = resourceName;
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.availableTokens = capacity;
        this.lastRefillTimeNanos = System.nanoTime();
    }

    @Override
    public synchronized boolean allowRequest() {
        refillTokens();
        if (availableTokens < 1D) {
            log.warn("触发限流，资源：{}，当前令牌数：{}",
                    resourceName, String.format("%.2f", availableTokens));
            return false;
        }
        availableTokens -= 1D;
        return true;
    }

    private void refillTokens() {
        long currentTimeNanos = System.nanoTime();
        double elapsedSeconds = (currentTimeNanos - lastRefillTimeNanos)
                / 1_000_000_000D;
        if (elapsedSeconds > 0) {
            availableTokens = Math.min(
                    capacity, availableTokens + elapsedSeconds * refillTokensPerSecond);
            lastRefillTimeNanos = currentTimeNanos;
        }
    }

    public synchronized double getAvailableTokens() {
        refillTokens();
        return availableTokens;
    }
}
