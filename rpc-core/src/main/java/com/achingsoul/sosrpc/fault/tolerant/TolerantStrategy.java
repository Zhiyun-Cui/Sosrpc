package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.model.RpcResponse;

import java.util.Map;

/**
 * Tolerant strategy.
 */
public interface TolerantStrategy {

    /**
     * Handle an RPC failure.
     *
     * @param context failure context
     * @param e original exception
     * @return tolerant response
     */
    RpcResponse doTolerant(Map<String, Object> context, Exception e);
}
