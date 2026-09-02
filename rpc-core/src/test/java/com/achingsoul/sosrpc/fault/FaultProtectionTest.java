package com.achingsoul.sosrpc.fault;

import com.achingsoul.sosrpc.fault.circuitbreaker.CircuitBreakerState;
import com.achingsoul.sosrpc.fault.circuitbreaker.SimpleCircuitBreaker;
import com.achingsoul.sosrpc.fault.ratelimiter.TokenBucketRateLimiter;
import org.junit.Assert;
import org.junit.Test;

public class FaultProtectionTest {

    @Test
    public void tokenBucketShouldRejectRequestsAfterCapacityIsConsumed() {
        TokenBucketRateLimiter rateLimiter =
                new TokenBucketRateLimiter("testService", 2, 0.01D);

        Assert.assertTrue(rateLimiter.allowRequest());
        Assert.assertTrue(rateLimiter.allowRequest());
        Assert.assertFalse(rateLimiter.allowRequest());
    }

    @Test
    public void circuitBreakerShouldOpenAndRecoverThroughHalfOpen()
            throws InterruptedException {
        SimpleCircuitBreaker circuitBreaker =
                new SimpleCircuitBreaker("testService", 2, 100L, 1);

        Assert.assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();
        Assert.assertTrue(circuitBreaker.allowRequest());
        circuitBreaker.recordFailure();

        Assert.assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        Assert.assertFalse(circuitBreaker.allowRequest());

        Thread.sleep(120L);
        Assert.assertTrue(circuitBreaker.allowRequest());
        Assert.assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());

        circuitBreaker.recordSuccess();
        Assert.assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        Assert.assertTrue(circuitBreaker.allowRequest());
    }
}
