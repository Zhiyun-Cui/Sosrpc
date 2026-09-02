package com.achingsoul.sosrpc.proxy;

import com.achingsoul.sosrpc.RpcApplication;

import java.lang.reflect.Proxy;

/**
 * Service proxy factory.
 */
public class ServiceProxyFactory {

    /**
     * Create proxy for the given service interface.
     * @param serviceClass
     * @param <T>
     * @return
     */
    public static <T> T getProxy(Class<T> serviceClass) {
        if (RpcApplication.getRpcConfig().isMock()) {
            return getMockProxy(serviceClass);
        }

        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class<?>[]{serviceClass},
                new ServiceProxy());
    }

    /**
    * Create mock proxy for the given service interface.
    * @param serviceClass
    * @param <T>
    * @return
    */
    public static <T> T getMockProxy(Class<T> serviceClass) {

        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new MockServiceProxy());
        }
}
