package com.achingsoul.sosrpc.fault.retry;

import com.achingsoul.sosrpc.model.RpcResponse;

import java.util.concurrent.Callable;

/**
 * Retry strategy.
 */
public interface RetryStrategy {

    /**
     * Execute a retryable task.
     */
    RpcResponse doRetry(Callable<RpcResponse> callable) throws Exception;
}
