package com.achingsoul.sosrpc.fault.tolerant;

import com.achingsoul.sosrpc.model.RpcResponse;
import com.achingsoul.sosrpc.utils.DefaultValueUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Silently handle a non-critical RPC failure.
 */
@Slf4j
public class FailSafeTolerantStrategy implements TolerantStrategy {

    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        log.info("静默处理异常", e);
        Class<?> returnType = (Class<?>) context.get(
                TolerantStrategyContextKeys.METHOD_RETURN_TYPE);
        return RpcResponse.builder()
                .data(DefaultValueUtils.getDefaultValue(returnType))
                .dataType(returnType)
                .message("Failure handled silently")
                .build();
    }
}
