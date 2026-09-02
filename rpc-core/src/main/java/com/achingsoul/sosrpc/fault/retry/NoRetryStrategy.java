package com.achingsoul.sosrpc.fault.retry;

import com.achingsoul.sosrpc.model.RpcResponse;

import java.util.concurrent.Callable;

/**
 * Retry strategy that executes the task only once.
 */
public class NoRetryStrategy implements RetryStrategy {

    @Override
    public RpcResponse doRetry(Callable<RpcResponse> callable) throws Exception {
        return callable.call();
    }
}
