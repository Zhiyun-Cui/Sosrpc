package com.achingsoul.sosrpc.springboot.starter.annotation;

import com.achingsoul.sosrpc.constant.RpcConstant;
import com.achingsoul.sosrpc.fault.retry.RetryStrategyKeys;
import com.achingsoul.sosrpc.fault.tolerant.TolerantStrategyKeys;
import com.achingsoul.sosrpc.loadbalancer.LoadBalancerKeys;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects an RPC client proxy into a Spring bean field.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcReference {

    Class<?> interfaceClass() default void.class;

    String serviceVersion() default RpcConstant.DEFAULT_SERVICE_VERSION;

    String loadBalancer() default LoadBalancerKeys.ROUND_ROBIN;

    String retryStrategy() default RetryStrategyKeys.NO;

    String tolerantStrategy() default TolerantStrategyKeys.FAIL_FAST;

    boolean mock() default false;
}
