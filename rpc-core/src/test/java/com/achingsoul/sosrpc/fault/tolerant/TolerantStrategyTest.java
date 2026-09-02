package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.loadbalancer.RoundRobinLoadBalancer;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class TolerantStrategyTest {

    @Test(expected = RuntimeException.class)
    public void failFastShouldPropagateFailure() {
        new FailFastTolerantStrategy().doTolerant(
                new HashMap<>(), new RuntimeException("Simulated failure"));
    }

    @Test
    public void failSafeShouldReturnPrimitiveDefaultValue() {
        Map<String, Object> context = new HashMap<>();
        context.put(TolerantStrategyContextKeys.METHOD_RETURN_TYPE, short.class);

        RpcResponse response = new FailSafeTolerantStrategy().doTolerant(
                context, new RuntimeException("Simulated failure"));

        Assert.assertEquals((short) 0, response.getData());
    }

    @Test
    public void failBackShouldInvokeLocalFallbackTask() {
        Map<String, Object> context = new HashMap<>();
        context.put(TolerantStrategyContextKeys.FALLBACK_TASK,
                (Callable<RpcResponse>) () -> RpcResponse.builder()
                        .data("fallback-result")
                        .build());

        RpcResponse response = new FailBackTolerantStrategy().doTolerant(
                context, new RuntimeException("Simulated failure"));

        Assert.assertEquals("fallback-result", response.getData());
    }

    @Test
    public void failOverShouldInvokeAnotherServiceNode() {
        ServiceMetaInfo failedNode = buildServiceMetaInfo(8080);
        ServiceMetaInfo replacementNode = buildServiceMetaInfo(8085);
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName("testService")
                .methodName("testMethod")
                .build();
        AtomicReference<ServiceMetaInfo> invokedNode = new AtomicReference<>();

        Map<String, Object> context = new HashMap<>();
        context.put(TolerantStrategyContextKeys.RPC_REQUEST, rpcRequest);
        context.put(TolerantStrategyContextKeys.SERVICE_META_INFO_LIST,
                Arrays.asList(failedNode, replacementNode));
        context.put(TolerantStrategyContextKeys.SELECTED_SERVICE_META_INFO, failedNode);
        context.put(TolerantStrategyContextKeys.LOAD_BALANCER,
                new RoundRobinLoadBalancer());
        context.put(TolerantStrategyContextKeys.RPC_REQUEST_EXECUTOR,
                (RpcRequestExecutor) (request, serviceMetaInfo) -> {
                    invokedNode.set(serviceMetaInfo);
                    return RpcResponse.builder().data("fail-over-result").build();
                });

        RpcResponse response = new FailOverTolerantStrategy().doTolerant(
                context, new RuntimeException("Simulated failure"));

        Assert.assertEquals(replacementNode, invokedNode.get());
        Assert.assertEquals("fail-over-result", response.getData());
    }

    @Test
    public void factoryShouldLoadAllTolerantStrategiesBySpi() {
        Assert.assertTrue(TolerantStrategyFactory.getInstance(
                TolerantStrategyKeys.FAIL_BACK) instanceof FailBackTolerantStrategy);
        Assert.assertTrue(TolerantStrategyFactory.getInstance(
                TolerantStrategyKeys.FAIL_FAST) instanceof FailFastTolerantStrategy);
        Assert.assertTrue(TolerantStrategyFactory.getInstance(
                TolerantStrategyKeys.FAIL_OVER) instanceof FailOverTolerantStrategy);
        Assert.assertTrue(TolerantStrategyFactory.getInstance(
                TolerantStrategyKeys.FAIL_SAFE) instanceof FailSafeTolerantStrategy);
    }

    private static ServiceMetaInfo buildServiceMetaInfo(int port) {
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName("testService");
        serviceMetaInfo.setServiceHost("localhost");
        serviceMetaInfo.setServicePort(port);
        return serviceMetaInfo;
    }
}
