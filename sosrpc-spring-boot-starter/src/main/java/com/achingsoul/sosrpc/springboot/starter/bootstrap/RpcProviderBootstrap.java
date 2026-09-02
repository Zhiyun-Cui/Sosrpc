package com.achingsoul.sosrpc.springboot.starter.bootstrap;

import com.achingsoul.sosrpc.RpcApplication;
import com.achingsoul.sosrpc.config.RpcConfig;
import com.achingsoul.sosrpc.model.ServiceMetaInfo;
import com.achingsoul.sosrpc.registry.LocalRegistry;
import com.achingsoul.sosrpc.registry.Registry;
import com.achingsoul.sosrpc.registry.RegistryConfig;
import com.achingsoul.sosrpc.registry.RegistryFactory;
import com.achingsoul.sosrpc.springboot.starter.annotation.RpcService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;

/**
 * Registers Spring beans marked with {@link RpcService} as RPC providers.
 */
public class RpcProviderBootstrap implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        Class<?> beanClass = ClassUtils.getUserClass(bean);
        RpcService rpcService = beanClass.getAnnotation(RpcService.class);
        if (rpcService == null) {
            return bean;
        }

        Class<?> interfaceClass = rpcService.interfaceClass();
        if (interfaceClass == void.class) {
            Class<?>[] interfaces = beanClass.getInterfaces();
            if (interfaces.length == 0) {
                throw new IllegalStateException("@RpcService class must implement an interface: "
                        + beanClass.getName());
            }
            interfaceClass = interfaces[0];
        }
        if (!interfaceClass.isAssignableFrom(beanClass)) {
            throw new IllegalStateException(beanClass.getName()
                    + " does not implement " + interfaceClass.getName());
        }

        String serviceName = interfaceClass.getName();
        LocalRegistry.register(serviceName, beanClass);

        RpcConfig rpcConfig = RpcApplication.getRpcConfig();
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(serviceName);
        serviceMetaInfo.setServiceVersion(rpcService.serviceVersion());
        serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
        serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
        try {
            registry.register(serviceMetaInfo);
        } catch (Exception e) {
            throw new IllegalStateException(serviceName + " service registration failed", e);
        }
        return bean;
    }
}
