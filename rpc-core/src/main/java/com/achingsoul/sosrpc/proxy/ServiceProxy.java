package com.achingsoul.sosrpc.proxy;

import cn.hutool.core.collection.CollUtil;
import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.constant.RpcConstant;
import com.achingsoul.sosrpc.fault.circuitbreaker.CircuitBreaker;
import com.achingsoul.sosrpc.fault.circuitbreaker.CircuitBreakerFactory;
import com.achingsoul.sosrpc.fault.circuitbreaker.CircuitBreakerOpenException;
import com.achingsoul.sosrpc.fault.ratelimiter.RateLimitException;
import com.achingsoul.sosrpc.fault.ratelimiter.RateLimiter;
import com.achingsoul.sosrpc.fault.ratelimiter.RateLimiterFactory;
import com.achingsoul.sosrpc.fault.retry.RetryStrategy;
import com.achingsoul.sosrpc.fault.retry.RetryStrategyFactory;
import com.achingsoul.sosrpc.fault.tolerant.RpcRequestExecutor;
import com.achingsoul.sosrpc.fault.tolerant.TolerantStrategy;
import com.achingsoul.sosrpc.fault.tolerant.TolerantStrategyContextKeys;
import com.achingsoul.sosrpc.fault.tolerant.TolerantStrategyFactory;
import com.achingsoul.sosrpc.loadbalancer.LoadBalancer;
import com.achingsoul.sosrpc.loadbalancer.LoadBalancerFactory;
import com.achingsoul.sosrpc.model.RpcRequest;
import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.registry.Registry;
import com.achingsoul.sosrpc.registry.RegistryFactory;
import com.achingsoul.sosrpc.server.tcp.VertxTcpClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Service proxy based on JDK dynamic proxy.
 */
public class ServiceProxy implements InvocationHandler {

    /**
     * Handle proxy invocation.
     *
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // Build request.
        String serviceName = method.getDeclaringClass().getName();
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName(serviceName)
                .serviceVersion(RpcConstant.DEFAULT_SERVICE_VERSION)
                .methodName(method.getName())
                .parameterTypes(method.getParameterTypes())
                .args(args)
                .build();
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        LoadBalancer loadBalancer = LoadBalancerFactory.getInstance(rpcConfig.getLoadBalancer());
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("methodName", rpcRequest.getMethodName());

        RateLimiter rateLimiter = RateLimiterFactory.getInstance(
                serviceName,
                rpcConfig.getRateLimitCapacity(),
                rpcConfig.getRateLimitRefillRate());
        CircuitBreaker circuitBreaker = CircuitBreakerFactory.getInstance(
                serviceName,
                rpcConfig.getCircuitBreakerFailureThreshold(),
                rpcConfig.getCircuitBreakerOpenDuration(),
                rpcConfig.getCircuitBreakerHalfOpenSuccessThreshold());

        List<ServiceMetaInfo> serviceMetaInfoList = Collections.emptyList();
        ServiceMetaInfo selectedServiceMetaInfo = null;
        RpcResponse rpcResponse;
        try {
            // Protect the consumer before service discovery and network I/O.
            if (rpcConfig.isRateLimiterEnabled() && !rateLimiter.allowRequest()) {
                throw new RateLimitException(
                        "Request rejected by rate limiter: " + serviceName);
            }
            if (rpcConfig.isCircuitBreakerEnabled() && !circuitBreaker.allowRequest()) {
                throw new CircuitBreakerOpenException(
                        "Request rejected by open circuit breaker: " + serviceName);
            }

            // Discover available service providers from the configured registry.
            Registry registry = RegistryFactory.getInstance(
                    rpcConfig.getRegistryConfig().getRegistry());
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);
            serviceMetaInfoList = registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
            if (CollUtil.isEmpty(serviceMetaInfoList)) {
                throw new RuntimeException("No available service provider: " + serviceName);
            }

            selectedServiceMetaInfo = loadBalancer.select(
                    requestParams, serviceMetaInfoList);
            System.out.println("Selected service provider: "
                    + selectedServiceMetaInfo.getServiceAddress());

            // Execute the TCP request through the configured retry strategy.
            RetryStrategy retryStrategy = RetryStrategyFactory.getInstance(
                    rpcConfig.getRetryStrategy());
            ServiceMetaInfo requestServiceMetaInfo = selectedServiceMetaInfo;
            rpcResponse = retryStrategy.doRetry(() ->
                    VertxTcpClient.doRequest(rpcRequest, requestServiceMetaInfo));
            if (rpcConfig.isCircuitBreakerEnabled()) {
                circuitBreaker.recordSuccess();
            }
        } catch (Exception e) {
            if (rpcConfig.isCircuitBreakerEnabled()
                    && shouldRecordCircuitBreakerFailure(e)) {
                circuitBreaker.recordFailure();
            }

            // Execute the configured tolerant strategy after retries are exhausted.
            TolerantStrategy tolerantStrategy = TolerantStrategyFactory.getInstance(
                    rpcConfig.getTolerantStrategy());
            Map<String, Object> tolerantContext = new HashMap<>();
            tolerantContext.put(TolerantStrategyContextKeys.RPC_REQUEST, rpcRequest);
            tolerantContext.put(TolerantStrategyContextKeys.SERVICE_META_INFO_LIST,
                    serviceMetaInfoList);
            tolerantContext.put(TolerantStrategyContextKeys.SELECTED_SERVICE_META_INFO,
                    selectedServiceMetaInfo);
            tolerantContext.put(TolerantStrategyContextKeys.REQUEST_PARAMS, requestParams);
            tolerantContext.put(TolerantStrategyContextKeys.LOAD_BALANCER, loadBalancer);
            tolerantContext.put(TolerantStrategyContextKeys.METHOD_RETURN_TYPE,
                    method.getReturnType());
            tolerantContext.put(TolerantStrategyContextKeys.RPC_REQUEST_EXECUTOR,
                    (RpcRequestExecutor) VertxTcpClient::doRequest);

            if (rpcConfig.getFallbackClass() != null
                    && !rpcConfig.getFallbackClass().isBlank()) {
                tolerantContext.put(TolerantStrategyContextKeys.FALLBACK_TASK,
                        buildFallbackTask(rpcConfig.getFallbackClass(), method, args));
            }
            rpcResponse = tolerantStrategy.doTolerant(tolerantContext, e);
        }
        return rpcResponse.getData();
    }

    /**
     * Rate-limit rejection and an already-open circuit are protection results,
     * not new remote-call failures.
     */
    private boolean shouldRecordCircuitBreakerFailure(Exception e) {
        return !(e instanceof RateLimitException)
                && !(e instanceof CircuitBreakerOpenException);
    }

    /**
     * Build a local fallback task from the configured implementation class.
     */
    private Callable<RpcResponse> buildFallbackTask(
            String fallbackClassName, Method method, Object[] args) {
        return () -> {
            Class<?> fallbackClass = Class.forName(fallbackClassName);
            if (!method.getDeclaringClass().isAssignableFrom(fallbackClass)) {
                throw new IllegalArgumentException(String.format(
                        "Fallback class %s must implement %s",
                        fallbackClassName, method.getDeclaringClass().getName()));
            }
            Object fallbackService = fallbackClass.getDeclaredConstructor().newInstance();
            Object fallbackResult = method.invoke(fallbackService, args);
            return RpcResponse.builder()
                    .data(fallbackResult)
                    .dataType(method.getReturnType())
                    .message("Local fallback succeeded")
                    .build();
        };
    }
}
