package com.achingsoul.sosrpc.proxy;

import lombok.extern.slf4j.Slf4j;
import com.achingsoul.sosrpc.utils.DefaultValueUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Mock service proxy
 */
@Slf4j
public class MockServiceProxy implements InvocationHandler {

    /**
     * proxy calling
     *
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // generate certain default object according to the return type of the method
        Class<?> methodReturnType = method.getReturnType();
        log.info("mock invoke {}", method.getName());
        return DefaultValueUtils.getDefaultValue(methodReturnType);
    }

}
