package com.achingsoul.sosrpc.springboot.starter.annotation;

import com.achingsoul.sosrpc.constant.RpcConstant;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean as an RPC service provider.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RpcService {

    /**
     * Published service interface. The first implemented interface is used by default.
     */
    Class<?> interfaceClass() default void.class;

    /**
     * Published service version.
     */
    String serviceVersion() default RpcConstant.DEFAULT_SERVICE_VERSION;
}
