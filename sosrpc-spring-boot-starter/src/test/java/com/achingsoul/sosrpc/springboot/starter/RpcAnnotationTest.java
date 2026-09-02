package com.achingsoul.sosrpc.springboot.starter;

import com.achingsoul.sosrpc.springboot.starter.annotation.EnableRpc;
import com.achingsoul.sosrpc.springboot.starter.annotation.RpcReference;
import com.achingsoul.sosrpc.springboot.starter.annotation.RpcService;
import com.achingsoul.sosrpc.springboot.starter.bootstrap.RpcConsumerBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcAnnotationTest {

    @Test
    void enableRpcImportsBootstrapComponents() {
        Import imported = EnableRpc.class.getAnnotation(Import.class);
        assertNotNull(imported);
        assertTrue(imported.value().length == 3);
    }

    @Test
    void injectsMockProxyIntoRpcReferenceField() {
        ConsumerBean bean = new ConsumerBean();
        new RpcConsumerBootstrap().postProcessAfterInitialization(bean, "consumerBean");

        assertNotNull(bean.echoService);
        assertTrue(Proxy.isProxyClass(bean.echoService.getClass()));
        assertNull(bean.echoService.echo("hello"));
    }

    interface EchoService {
        String echo(String message);
    }

    @RpcService
    static class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String message) {
            return message;
        }
    }

    static class ConsumerBean {
        @RpcReference(mock = true)
        private EchoService echoService;
    }
}
