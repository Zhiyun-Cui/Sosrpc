package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.utils.DefaultValueUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Degrade to a local fallback implementation or a safe default value.
 */
@Slf4j
public class FailBackTolerantStrategy implements TolerantStrategy {

    @Override
    @SuppressWarnings("unchecked")
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        Callable<RpcResponse> fallbackTask = (Callable<RpcResponse>) context.get(
                TolerantStrategyContextKeys.FALLBACK_TASK);
        if (fallbackTask != null) {
            try {
                log.info("RPC 调用失败，执行本地降级服务");
                return fallbackTask.call();
            } catch (Exception fallbackException) {
                throw new RuntimeException("Local fallback service failed", fallbackException);
            }
        }

        Class<?> returnType = (Class<?>) context.get(
                TolerantStrategyContextKeys.METHOD_RETURN_TYPE);
        log.info("未配置本地降级服务，返回类型安全的默认值");
        return RpcResponse.builder()
                .data(DefaultValueUtils.getDefaultValue(returnType))
                .dataType(returnType)
                .message("Default fallback response")
                .exception(e)
                .build();
    }
}
