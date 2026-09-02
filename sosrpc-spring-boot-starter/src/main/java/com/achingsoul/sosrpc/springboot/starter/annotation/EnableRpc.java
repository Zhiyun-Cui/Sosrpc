package com.achingsoul.sosrpc.springboot.starter.annotation;

import com.achingsoul.sosrpc.springboot.starter.bootstrap.RpcConsumerBootstrap;
import com.achingsoul.sosrpc.springboot.starter.bootstrap.RpcInitBootstrap;
import com.achingsoul.sosrpc.springboot.starter.bootstrap.RpcProviderBootstrap;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables annotation-driven ACRPC support in a Spring application.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({RpcInitBootstrap.class, RpcProviderBootstrap.class, RpcConsumerBootstrap.class})
public @interface EnableRpc {

    /**
     * Whether this application is a provider and should start the TCP server.
     */
    boolean needServer() default true;
}
