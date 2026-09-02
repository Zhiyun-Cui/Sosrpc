package com.achingsoul.sosrpc.fault.tolerant;

/**
 * Keys used to pass failure context to tolerant strategies.
 */
public interface TolerantStrategyContextKeys {

    String RPC_REQUEST = "rpcRequest";

    String SERVICE_META_INFO_LIST = "serviceMetaInfoList";

    String SELECTED_SERVICE_META_INFO = "selectedServiceMetaInfo";

    String REQUEST_PARAMS = "requestParams";

    String LOAD_BALANCER = "loadBalancer";

    String METHOD_RETURN_TYPE = "methodReturnType";

    String FALLBACK_TASK = "fallbackTask";

    String RPC_REQUEST_EXECUTOR = "rpcRequestExecutor";
}
