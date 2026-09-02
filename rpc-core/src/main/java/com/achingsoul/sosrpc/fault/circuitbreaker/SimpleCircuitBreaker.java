package com.achingsoul.sosrpc.fault.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

/**
 * Circuit breaker with closed, open, and half-open states.
 */
@Slf4j
public class SimpleCircuitBreaker implements CircuitBreaker {

    private final String resourceName;

    private final int failureThreshold;

    private final long openDurationMillis;

    private final int halfOpenSuccessThreshold;

    private CircuitBreakerState state = CircuitBreakerState.CLOSED;

    private int failureCount;

    private int halfOpenSuccessCount;

    private long openedAtMillis;

    private boolean halfOpenRequestInFlight;

    public SimpleCircuitBreaker(
            String resourceName,
            int failureThreshold,
            long openDurationMillis,
            int halfOpenSuccessThreshold) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("Failure threshold must be greater than 0");
        }
        if (openDurationMillis < 0) {
            throw new IllegalArgumentException("Open duration must not be negative");
        }
        if (halfOpenSuccessThreshold <= 0) {
            throw new IllegalArgumentException(
                    "Half-open success threshold must be greater than 0");
        }
        this.resourceName = resourceName;
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationMillis;
        this.halfOpenSuccessThreshold = halfOpenSuccessThreshold;
    }

    @Override
    public synchronized boolean allowRequest() {
        if (state == CircuitBreakerState.OPEN) {
            if (System.currentTimeMillis() - openedAtMillis < openDurationMillis) {
                log.warn("熔断器拒绝请求，资源：{}，状态：OPEN", resourceName);
                return false;
            }
            state = CircuitBreakerState.HALF_OPEN;
            halfOpenSuccessCount = 0;
            halfOpenRequestInFlight = false;
            log.info("熔断器进入 HALF_OPEN，允许一次探测请求，资源：{}", resourceName);
        }

        if (state == CircuitBreakerState.HALF_OPEN) {
            if (halfOpenRequestInFlight) {
                return false;
            }
            halfOpenRequestInFlight = true;
        }
        return true;
    }

    @Override
    public synchronized void recordSuccess() {
        if (state == CircuitBreakerState.HALF_OPEN) {
            halfOpenRequestInFlight = false;
            halfOpenSuccessCount++;
            if (halfOpenSuccessCount >= halfOpenSuccessThreshold) {
                closeCircuit();
            }
            return;
        }
        if (state == CircuitBreakerState.CLOSED) {
            failureCount = 0;
        }
    }

    @Override
    public synchronized void recordFailure() {
        if (state == CircuitBreakerState.HALF_OPEN) {
            halfOpenRequestInFlight = false;
            openCircuit();
            return;
        }
        if (state == CircuitBreakerState.CLOSED) {
            failureCount++;
            log.warn("记录调用失败，资源：{}，连续失败次数：{}",
                    resourceName, failureCount);
            if (failureCount >= failureThreshold) {
                openCircuit();
            }
        }
    }

    @Override
    public synchronized CircuitBreakerState getState() {
        return state;
    }

    private void openCircuit() {
        state = CircuitBreakerState.OPEN;
        openedAtMillis = System.currentTimeMillis();
        halfOpenSuccessCount = 0;
        log.warn("熔断器打开，资源：{}，将在 {} ms 后尝试恢复",
                resourceName, openDurationMillis);
    }

    private void closeCircuit() {
        state = CircuitBreakerState.CLOSED;
        failureCount = 0;
        halfOpenSuccessCount = 0;
        halfOpenRequestInFlight = false;
        log.info("熔断器关闭，资源恢复：{}", resourceName);
    }
}
