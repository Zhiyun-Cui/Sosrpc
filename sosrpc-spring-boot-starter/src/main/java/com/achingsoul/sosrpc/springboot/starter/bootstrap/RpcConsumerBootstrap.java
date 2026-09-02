package com.achingsoul.sosrpc.springboot.starter.bootstrap;

import com.achingsoul.sosrpc.proxy.ServiceProxyFactory;
import com.achingsoul.sosrpc.springboot.starter.annotation.RpcReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Injects RPC proxies into fields marked with {@link RpcReference}.
 */
public class RpcConsumerBootstrap implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        Class<?> beanClass = ClassUtils.getUserClass(bean);
        ReflectionUtils.doWithFields(beanClass, field -> injectReference(bean, field));
        return bean;
    }

    private void injectReference(Object bean, Field field) {
        RpcReference rpcReference = field.getAnnotation(RpcReference.class);
        if (rpcReference == null) {
            return;
        }
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            throw new IllegalStateException("@RpcReference field cannot be static or final: "
                    + field);
        }

        Class<?> interfaceClass = rpcReference.interfaceClass();
        if (interfaceClass == void.class) {
            interfaceClass = field.getType();
        }
        if (!interfaceClass.isInterface()) {
            throw new IllegalStateException("@RpcReference requires an interface: "
                    + interfaceClass.getName());
        }
        if (!field.getType().isAssignableFrom(interfaceClass)) {
            throw new IllegalStateException("RPC interface " + interfaceClass.getName()
                    + " cannot be assigned to field " + field);
        }

        Object proxy = rpcReference.mock()
                ? ServiceProxyFactory.getMockProxy(interfaceClass)
                : ServiceProxyFactory.getProxy(interfaceClass);
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, proxy);
    }
}
