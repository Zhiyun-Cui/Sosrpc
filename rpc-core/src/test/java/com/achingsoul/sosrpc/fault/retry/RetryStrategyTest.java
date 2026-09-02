package com.achingsoul.sosrpc.fault.retry;

import com.achingsoul.sosrpc.model.RpcResponse;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry strategy tests.
 */
public class RetryStrategyTest {

    @Test
    public void noRetryShouldExecuteOnlyOnce() {
        AtomicInteger attempts = new AtomicInteger();

        try {
            new NoRetryStrategy().doRetry(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Simulated failure");
            });
            Assert.fail("Expected retry task to fail");
        } catch (Exception ignored) {
            Assert.assertEquals(1, attempts.get());
        }
    }

    @Test
    public void factoryShouldLoadAllRetryStrategiesBySpi() {
        Assert.assertTrue(RetryStrategyFactory.getInstance(
                RetryStrategyKeys.NO) instanceof NoRetryStrategy);
        Assert.assertTrue(RetryStrategyFactory.getInstance(
                RetryStrategyKeys.FIXED_INTERVAL) instanceof FixedIntervalRetryStrategy);
        Assert.assertTrue(RetryStrategyFactory.getInstance(
                RetryStrategyKeys.EXPONENTIAL_BACKOFF) instanceof ExponentialBackoffRetryStrategy);
    }

    @Test
    public void fixedIntervalShouldRetryAfterFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        long startTime = System.currentTimeMillis();

        RpcResponse response = new FixedIntervalRetryStrategy().doRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("Simulated first failure");
            }
            return RpcResponse.builder().message("ok").build();
        });

        Assert.assertEquals(2, attempts.get());
        Assert.assertEquals("ok", response.getMessage());
        Assert.assertTrue(System.currentTimeMillis() - startTime >= 2_900L);
    }

    @Test
    public void exponentialBackoffShouldRetryAfterFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        long startTime = System.currentTimeMillis();

        RpcResponse response = new ExponentialBackoffRetryStrategy().doRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("Simulated first failure");
            }
            return RpcResponse.builder().message("ok").build();
        });

        Assert.assertEquals(2, attempts.get());
        Assert.assertEquals("ok", response.getMessage());
        Assert.assertTrue(System.currentTimeMillis() - startTime >= 1_900L);
    }
}
