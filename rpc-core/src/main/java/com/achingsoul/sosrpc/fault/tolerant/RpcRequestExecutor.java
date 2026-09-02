package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;

/**
 * Executes an RPC request against a specific service node.
 */
@FunctionalInterface
public interface RpcRequestExecutor {

    RpcResponse execute(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo);
}
