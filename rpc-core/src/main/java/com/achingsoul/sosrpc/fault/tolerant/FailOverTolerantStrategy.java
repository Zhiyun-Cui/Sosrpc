package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.loadbalancer.LoadBalancer;
import com.achingsoul.sosrpc.loadbalancer.RoundRobinLoadBalancer;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.server.tcp.VertxTcpClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transfer a failed call to another service node.
 */
@Slf4j
public class FailOverTolerantStrategy implements TolerantStrategy {

    @Override
    @SuppressWarnings("unchecked")
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        RpcRequest rpcRequest = (RpcRequest) context.get(
                TolerantStrategyContextKeys.RPC_REQUEST);
        List<ServiceMetaInfo> serviceMetaInfoList = (List<ServiceMetaInfo>) context.get(
                TolerantStrategyContextKeys.SERVICE_META_INFO_LIST);
        ServiceMetaInfo failedServiceMetaInfo = (ServiceMetaInfo) context.get(
                TolerantStrategyContextKeys.SELECTED_SERVICE_META_INFO);

        if (rpcRequest == null || serviceMetaInfoList == null
                || failedServiceMetaInfo == null) {
            throw new RuntimeException("Incomplete fail-over context", e);
        }

        String failedNodeKey = failedServiceMetaInfo.getServiceNodeKey();
        List<ServiceMetaInfo> remainingServiceMetaInfoList = serviceMetaInfoList.stream()
                .filter(serviceMetaInfo -> !failedNodeKey.equals(
                        serviceMetaInfo.getServiceNodeKey()))
                .toList();
        if (remainingServiceMetaInfoList.isEmpty()) {
            throw new RuntimeException("No alternative service node for fail-over", e);
        }

        LoadBalancer loadBalancer = (LoadBalancer) context.get(
                TolerantStrategyContextKeys.LOAD_BALANCER);
        if (loadBalancer == null) {
            loadBalancer = new RoundRobinLoadBalancer();
        }
        Map<String, Object> requestParams = (Map<String, Object>) context.get(
                TolerantStrategyContextKeys.REQUEST_PARAMS);
        if (requestParams == null) {
            requestParams = new HashMap<>();
            requestParams.put("methodName", rpcRequest.getMethodName());
        }
        ServiceMetaInfo replacementServiceMetaInfo = loadBalancer.select(
                requestParams, remainingServiceMetaInfoList);

        RpcRequestExecutor requestExecutor = (RpcRequestExecutor) context.get(
                TolerantStrategyContextKeys.RPC_REQUEST_EXECUTOR);
        if (requestExecutor == null) {
            requestExecutor = VertxTcpClient::doRequest;
        }
        log.info("故障转移：{} -> {}",
                failedServiceMetaInfo.getServiceAddress(),
                replacementServiceMetaInfo.getServiceAddress());
        return requestExecutor.execute(rpcRequest, replacementServiceMetaInfo);
    }
}
