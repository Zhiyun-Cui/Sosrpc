package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.model.RpcResponse;

import java.util.Map;

/**
 * Fail fast by immediately propagating the failure.
 */
public class FailFastTolerantStrategy implements TolerantStrategy {

    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        throw new RuntimeException("RPC service failed", e);
    }
}
